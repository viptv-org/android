#!/usr/bin/env python3
"""Private TLS ingress for the test-only real backend, binding literal loopback."""
import argparse
import http.client
import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ssl
import subprocess
from pathlib import Path
from owned_fixture import atomic_json


def certificate(directory):
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    key = directory / "key.pem"
    cert = directory / "cert.pem"
    if key.exists() or cert.exists():
        raise SystemExit("use a fresh private certificate directory")
    subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                    "-subj", "/CN=Owned Android fixture", "-addext", "subjectAltName=IP:127.0.0.1",
                    "-keyout", str(key), "-out", str(cert)], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    key.chmod(0o600)
    return cert, key


class Ingress(BaseHTTPRequestHandler):
    def end_headers(self):
        # Finite fixture requests use independent TLS sockets. Declare closure
        # explicitly so strict clients cannot reuse a socket the server closed.
        self.send_header("Connection", "close")
        self.close_connection = True
        super().end_headers()

    def log_message(self, *_):
        pass  # Request paths, bearer headers and grant bodies must never enter logs.

    def forward(self):
        if self.path == "/__fixture/control" and self.command == "POST" and self.server.owned_directory:
            self.control()
            return
        if self.path.startswith("/owned/") and self.command in ("GET", "HEAD") and self.server.owned_directory:
            self.media()
            return
        if self.path == "/api/v2/playback-protocol" and self.server.owned_directory:
            try:
                legacy = json.loads((self.server.owned_directory / "legacy-protocol.json").read_text()) is True
            except (OSError, ValueError):
                legacy = False
            if legacy:
                self.send_response(404)
                self.send_header("Cache-Control", "no-store")
                self.send_header("Content-Length", "2")
                self.end_headers()
                self.wfile.write(b"{}")
                return
        if not self.path.startswith("/api/") or "\r" in self.path or "\n" in self.path:
            self.send_error(404)
            return
        if self.headers.get("Transfer-Encoding"):
            self.send_error(400)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self.send_error(400)
            return
        if length < 0 or length > 16384:
            self.send_error(413)
            return
        body = self.rfile.read(length)
        upstream = http.client.HTTPConnection("127.0.0.1", self.server.backend_port, timeout=10)
        try:
            headers = {key: value for key, value in self.headers.items() if key.lower() not in {"host", "connection", "transfer-encoding"}}
            headers["Host"] = self.headers.get("Host", "127.0.0.1")
            upstream.request(self.command, self.path, body=body, headers=headers)
            response = upstream.getresponse()
            payload = response.read(6_291_457)
            if len(payload) > 6_291_456:
                self.send_error(502)
                return
            self.send_response(response.status)
            for key, value in response.getheaders():
                if key.lower() not in {"connection", "transfer-encoding", "content-length"}:
                    self.send_header(key, value)
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        except (OSError, http.client.HTTPException):
            self.send_error(502)
        finally:
            upstream.close()

    def media(self):
        if self.path == "/owned/episode.mp4":
            path = self.server.owned_directory / "owned-episodes/01-episode.mp4"
            content_type = "video/mp4"
        elif self.path == "/owned/hls/index.m3u8" or re.fullmatch(r"/owned/hls/segment[0-9]{3}\.ts", self.path):
            path = self.server.owned_directory / "hls" / self.path.rsplit("/", 1)[1]
            content_type = "application/vnd.apple.mpegurl" if path.suffix == ".m3u8" else "video/mp2t"
        else:
            self.send_error(404)
            return
        try:
            size = path.stat().st_size
            start, end = 0, size - 1
            value = self.headers.get("Range")
            if value:
                match = re.fullmatch(r"bytes=([0-9]+)-([0-9]*)", value)
                if not match:
                    raise ValueError()
                start = int(match[1])
                end = min(size - 1, int(match[2])) if match[2] else size - 1
                if start > end:
                    raise ValueError()
            self.send_response(206 if value else 200)
            self.send_header("Content-Type", content_type)
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Length", str(end - start + 1))
            if value:
                self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
            self.end_headers()
            if self.command == "HEAD":
                return
            with path.open("rb") as stream:
                stream.seek(start)
                remaining = end - start + 1
                while remaining:
                    data = stream.read(min(65536, remaining))
                    if not data:
                        break
                    self.wfile.write(data)
                    remaining -= len(data)
        except (OSError, ValueError):
            self.close_connection = True

    def control(self):
        directory = self.server.owned_directory
        try:
            token = json.loads((directory / "backend-ready.json").read_text())["access_token"]
            if self.headers.get("Authorization") != "Bearer " + token:
                self.send_error(401)
                return
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 4096 or self.headers.get("Transfer-Encoding"):
                raise ValueError()
            value = json.loads(self.rfile.read(length))
            if not isinstance(value, dict) or set(value) - {"held_pieces", "hold_metadata", "disable_source", "revoke", "legacy_protocol"}:
                raise ValueError()
            for name in ("hold_metadata", "disable_source", "revoke", "legacy_protocol"):
                if name in value and type(value[name]) is not bool:
                    raise ValueError()
            if "held_pieces" in value:
                pieces = value["held_pieces"]
                if not isinstance(pieces, list) or len(pieces) > 4096 or any(type(piece) is not int or piece < 0 for piece in pieces):
                    raise ValueError()
                atomic_json(directory / "hold-pieces.json", pieces)
            if "hold_metadata" in value:
                atomic_json(directory / "hold-metadata.json", value["hold_metadata"])
            if "legacy_protocol" in value:
                atomic_json(directory / "legacy-protocol.json", value["legacy_protocol"])
            if "disable_source" in value or "revoke" in value:
                atomic_json(directory / "backend-command.json", value)
            self.send_response(200)
            self.send_header("Content-Length", "2")
            self.end_headers()
            self.wfile.write(b"{}")
        except (OSError, ValueError, KeyError, TypeError):
            self.send_error(400)

    do_GET = do_HEAD = do_POST = do_DELETE = do_PATCH = forward


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--backend-port", type=int, required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--owned-directory", type=Path)
    args = parser.parse_args()
    cert, key = certificate(args.directory)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Ingress)
    server.backend_port = args.backend_port
    server.owned_directory = args.owned_directory
    server.daemon_threads = True
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.minimum_version = ssl.TLSVersion.TLSv1_2
    tls.load_cert_chain(cert, key)
    server.socket = tls.wrap_socket(server.socket, server_side=True)
    try:
        server.serve_forever(poll_interval=0.2)
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
