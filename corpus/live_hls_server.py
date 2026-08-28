#!/usr/bin/env python3
"""Deterministic, bounded HLS impairment server for Air player tests."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import signal
import socket
from socketserver import ThreadingMixIn
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import BinaryIO, Callable, Iterable
from urllib.parse import urlsplit


MAX_MANIFEST_BYTES = 256 * 1024
MAX_CLIENTS = 32
MAX_CHUNK_BYTES = 64 * 1024
MAX_DELAY_MILLIS = 60_000


class FixtureConfigurationError(ValueError):
    pass


class Segment:
    __slots__ = ("name", "duration_tag", "path", "size")

    def __init__(self, name: str, duration_tag: str, path: Path) -> None:
        self.name = name
        self.duration_tag = duration_tag
        self.path = path
        self.size = path.stat().st_size
        if self.size == 0:
            raise FixtureConfigurationError("generated segment is empty")


class FixtureState:
    """Fixed-shape counters; request history is intentionally never retained."""

    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.playlist_requests = 0
        self.segment_requests = 0
        self.http_failures = 0
        self.disconnects = 0
        self.delayed_requests = 0
        self.bytes_served = 0
        self.active_requests = 0
        self.max_active_requests = 0
        self.http_failure_consumed = False
        self.disconnect_consumed = False

    def enter_request(self) -> None:
        with self.lock:
            self.active_requests += 1
            self.max_active_requests = max(
                self.max_active_requests, self.active_requests
            )

    def leave_request(self) -> None:
        with self.lock:
            self.active_requests -= 1

    def snapshot(self) -> dict[str, int]:
        with self.lock:
            return {
                "playlistRequests": self.playlist_requests,
                "segmentRequests": self.segment_requests,
                "httpFailures": self.http_failures,
                "disconnects": self.disconnects,
                "delayedRequests": self.delayed_requests,
                "bytesServed": self.bytes_served,
                "activeRequests": self.active_requests,
                "maxActiveRequests": self.max_active_requests,
            }


class LiveHlsFixture:
    def __init__(
        self,
        corpus_dir: Path,
        *,
        live_window: int = 12,
        segment_delay_millis: int = 0,
        jitter_millis: int = 0,
        delay_segments: Iterable[str] = (),
        jitter_seed: str = "air-live-fixture-v1",
        http_fail_once: str | None = None,
        http_fail_status: int = 503,
        disconnect_once: str | None = None,
        discontinuity_before: int | None = None,
        stream_chunk_bytes: int = MAX_CHUNK_BYTES,
        log_sink: Callable[[str], None] | None = None,
    ) -> None:
        if live_window < 2:
            raise FixtureConfigurationError("live_window must be at least 2")
        if not 0 <= segment_delay_millis <= MAX_DELAY_MILLIS:
            raise FixtureConfigurationError("segment delay is out of bounds")
        if not 0 <= jitter_millis <= MAX_DELAY_MILLIS:
            raise FixtureConfigurationError("jitter is out of bounds")
        if not 1 <= stream_chunk_bytes <= MAX_CHUNK_BYTES:
            raise FixtureConfigurationError("stream chunk size is out of bounds")
        if http_fail_status < 400 or http_fail_status > 599:
            raise FixtureConfigurationError("HTTP failure status must be 4xx or 5xx")

        self.corpus_dir = corpus_dir.resolve()
        self.hls_dir = self.corpus_dir / "output" / "hls"
        self.event_manifest = self.hls_dir / "event.m3u8"
        self.manifest_headers, self.segments = self._load_event_manifest()
        self.segment_by_name = {segment.name: segment for segment in self.segments}
        if live_window > len(self.segments):
            raise FixtureConfigurationError(
                "live_window exceeds the generated segment count"
            )

        requested_delay_segments = frozenset(delay_segments)
        self._validate_segment_names(requested_delay_segments)
        self._validate_segment_names(
            name for name in (http_fail_once, disconnect_once) if name is not None
        )
        if discontinuity_before is not None and not (
            0 <= discontinuity_before < len(self.segments)
        ):
            raise FixtureConfigurationError("discontinuity index is out of bounds")

        self.live_window = live_window
        self.segment_delay_millis = segment_delay_millis
        self.jitter_millis = jitter_millis
        self.delay_segments = requested_delay_segments
        self.jitter_seed = jitter_seed
        self.http_fail_once = http_fail_once
        self.http_fail_status = http_fail_status
        self.disconnect_once = disconnect_once
        self.discontinuity_before = discontinuity_before
        self.stream_chunk_bytes = stream_chunk_bytes
        self.log_sink = log_sink
        self.state = FixtureState()
        self.stop_event = threading.Event()

    def _load_event_manifest(self) -> tuple[tuple[str, ...], tuple[Segment, ...]]:
        try:
            manifest_size = self.event_manifest.stat().st_size
        except FileNotFoundError as error:
            raise FixtureConfigurationError(
                "generate the playback corpus before starting the fixture"
            ) from error
        if manifest_size > MAX_MANIFEST_BYTES:
            raise FixtureConfigurationError("event manifest exceeds the size limit")

        lines = self.event_manifest.read_text(encoding="utf-8").splitlines()
        if not lines or lines[0] != "#EXTM3U":
            raise FixtureConfigurationError("event manifest is not valid HLS")

        allowed_header_prefixes = (
            "#EXT-X-VERSION:",
            "#EXT-X-TARGETDURATION:",
            "#EXT-X-INDEPENDENT-SEGMENTS",
        )
        headers = [line for line in lines if line.startswith(allowed_header_prefixes)]
        segments: list[Segment] = []
        pending_duration: str | None = None
        for line in lines[1:]:
            if line.startswith("#EXTINF:"):
                pending_duration = line
            elif line and not line.startswith("#"):
                if pending_duration is None:
                    raise FixtureConfigurationError("segment has no EXTINF duration")
                if Path(line).name != line or urlsplit(line).scheme or "?" in line:
                    raise FixtureConfigurationError(
                        "only local generated segment names are accepted"
                    )
                segment_path = (self.hls_dir / line).resolve()
                if segment_path.parent != self.hls_dir.resolve():
                    raise FixtureConfigurationError("segment escaped the HLS directory")
                if not segment_path.is_file():
                    raise FixtureConfigurationError("generated segment is missing")
                segments.append(Segment(line, pending_duration, segment_path))
                pending_duration = None

        if len(segments) < 2:
            raise FixtureConfigurationError("event manifest has too few segments")
        return tuple(headers), tuple(segments)

    def _validate_segment_names(self, names: Iterable[str]) -> None:
        if any(name not in self.segment_by_name for name in names):
            raise FixtureConfigurationError("fault target is not a generated segment")

    def emit(self, event: str, resource: str | None = None) -> None:
        if self.log_sink is None:
            return
        safe_resource = resource if resource in self.segment_by_name else "fixture"
        self.log_sink(f"air-live-fixture event={event} resource={safe_resource}")

    def live_manifest(self, *, advance: bool = True) -> bytes:
        with self.state.lock:
            request_number = self.state.playlist_requests
            if advance:
                self.state.playlist_requests += 1

        published = min(
            len(self.segments), self.live_window + request_number
        )
        start = max(0, published - self.live_window)
        lines = ["#EXTM3U", *self.manifest_headers]
        if not any(line.startswith("#EXT-X-TARGETDURATION:") for line in lines):
            lines.append("#EXT-X-TARGETDURATION:1")
        lines.append(f"#EXT-X-MEDIA-SEQUENCE:{start}")

        discontinuity_before = self.discontinuity_before
        if discontinuity_before is not None:
            removed_discontinuities = 1 if discontinuity_before <= start else 0
            lines.append(
                f"#EXT-X-DISCONTINUITY-SEQUENCE:{removed_discontinuities}"
            )

        for index in range(start, published):
            if discontinuity_before == index and index > start:
                lines.append("#EXT-X-DISCONTINUITY")
            segment = self.segments[index]
            lines.extend((segment.duration_tag, segment.name))
        return ("\n".join(lines) + "\n").encode("utf-8")

    def segment_plan(self, name: str) -> tuple[str, int]:
        """Atomically consume one-shot faults and calculate deterministic delay."""
        with self.state.lock:
            self.state.segment_requests += 1
            if name == self.disconnect_once and not self.state.disconnect_consumed:
                self.state.disconnect_consumed = True
                self.state.disconnects += 1
                return "disconnect", 0
            if name == self.http_fail_once and not self.state.http_failure_consumed:
                self.state.http_failure_consumed = True
                self.state.http_failures += 1
                return "http-failure", 0

        if self.segment_delay_millis == 0:
            return "serve", 0
        if self.delay_segments and name not in self.delay_segments:
            return "serve", 0

        jitter = 0
        if self.jitter_millis:
            digest = hashlib.sha256(
                f"{self.jitter_seed}\0{name}".encode("utf-8")
            ).digest()
            bucket = int.from_bytes(digest[:8], "big") / ((1 << 64) - 1)
            jitter = round((bucket * 2 - 1) * self.jitter_millis)
        delay = max(0, min(MAX_DELAY_MILLIS, self.segment_delay_millis + jitter))
        with self.state.lock:
            self.state.delayed_requests += 1
        return "serve", delay


class _BoundedThreadingHttpServer(ThreadingMixIn, HTTPServer):
    daemon_threads = False
    block_on_close = True
    allow_reuse_address = True

    def __init__(
        self,
        server_address: tuple[str, int],
        fixture: LiveHlsFixture,
        max_clients: int,
    ) -> None:
        self.fixture = fixture
        self.client_slots = threading.BoundedSemaphore(max_clients)
        self.active_sockets: set[socket.socket] = set()
        self.active_sockets_lock = threading.Lock()
        self.request_queue_size = max_clients
        super().__init__(server_address, _FixtureRequestHandler)

    def process_request(self, request: socket.socket, client_address: object) -> None:
        if not self.client_slots.acquire(blocking=False):
            try:
                request.sendall(
                    b"HTTP/1.1 503 Service Unavailable\r\n"
                    b"Content-Length: 0\r\nConnection: close\r\n\r\n"
                )
            except OSError:
                pass
            finally:
                self.shutdown_request(request)
            self.fixture.emit("capacity_rejected")
            return
        with self.active_sockets_lock:
            self.active_sockets.add(request)
        try:
            super().process_request(request, client_address)
        except BaseException:
            with self.active_sockets_lock:
                self.active_sockets.discard(request)
            self.client_slots.release()
            raise

    def process_request_thread(
        self, request: socket.socket, client_address: object
    ) -> None:
        try:
            super().process_request_thread(request, client_address)
        finally:
            with self.active_sockets_lock:
                self.active_sockets.discard(request)
            self.client_slots.release()

    def close_active_requests(self) -> None:
        with self.active_sockets_lock:
            active_sockets = tuple(self.active_sockets)
        for active_socket in active_sockets:
            try:
                active_socket.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            try:
                active_socket.close()
            except OSError:
                pass


class _FixtureRequestHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "AirLiveFixture/1"
    sys_version = ""

    @property
    def fixture(self) -> LiveHlsFixture:
        return self.server.fixture  # type: ignore[attr-defined,no-any-return]

    def log_message(self, format: str, *args: object) -> None:
        # BaseHTTPRequestHandler includes the raw request target. Never emit it.
        return

    def do_HEAD(self) -> None:
        self._handle(send_body=False)

    def do_GET(self) -> None:
        self._handle(send_body=True)

    def _handle(self, *, send_body: bool) -> None:
        self.fixture.state.enter_request()
        try:
            path = urlsplit(self.path).path
            if path == "/healthz":
                self._send_bytes(b"ok\n", "text/plain", send_body=send_body)
                return
            if path == "/__air/status":
                payload = json.dumps(
                    self.fixture.state.snapshot(),
                    separators=(",", ":"),
                    sort_keys=True,
                ).encode("ascii")
                self._send_bytes(payload, "application/json", send_body=send_body)
                return
            if path == "/hls/live.m3u8":
                payload = self.fixture.live_manifest(advance=send_body)
                self._send_bytes(
                    payload,
                    "application/vnd.apple.mpegurl",
                    send_body=send_body,
                )
                return
            if path == "/hls/event.m3u8":
                self._send_file(
                    self.fixture.event_manifest,
                    "application/vnd.apple.mpegurl",
                    send_body=send_body,
                )
                return
            if path.startswith("/hls/"):
                name = path.removeprefix("/hls/")
                segment = self.fixture.segment_by_name.get(name)
                if segment is not None:
                    self._send_segment(segment, send_body=send_body)
                    return
            self.fixture.emit("not_found")
            self.send_error(404, "fixture resource not found")
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            self.fixture.emit("client_closed")
        finally:
            self.fixture.state.leave_request()

    def _send_segment(self, segment: Segment, *, send_body: bool) -> None:
        if not send_body:
            self._send_file(
                segment.path,
                "video/mp2t",
                send_body=False,
            )
            return

        action, delay_millis = self.fixture.segment_plan(segment.name)
        if action == "disconnect":
            self.fixture.emit("disconnect_once", segment.name)
            self.close_connection = True
            try:
                self.connection.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            self.connection.close()
            return
        if action == "http-failure":
            self.fixture.emit("http_fail_once", segment.name)
            self.close_connection = True
            self.send_response(self.fixture.http_fail_status)
            self.send_header("Content-Length", "0")
            self.send_header("Connection", "close")
            self.end_headers()
            return
        if delay_millis:
            self.fixture.emit("delay", segment.name)
            if self.fixture.stop_event.wait(delay_millis / 1000):
                self.close_connection = True
                return
        self._send_file(
            segment.path,
            "video/mp2t",
            send_body=True,
        )

    def _send_file(
        self,
        path: Path,
        content_type: str,
        *,
        send_body: bool,
    ) -> None:
        size = path.stat().st_size
        start, end = self._requested_range(size)
        if start is None or end is None:
            return
        length = end - start + 1
        status = 206 if self.headers.get("Range") is not None else 200
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(length))
        if status == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        if not send_body:
            return
        with path.open("rb") as source:
            source.seek(start)
            self._copy_bounded(source, length)

    def _requested_range(self, size: int) -> tuple[int | None, int | None]:
        raw_range = self.headers.get("Range")
        if raw_range is None:
            return 0, size - 1
        try:
            unit, value = raw_range.split("=", 1)
            if unit != "bytes" or "," in value:
                raise ValueError
            start_text, end_text = value.split("-", 1)
            if not start_text:
                suffix = int(end_text)
                if suffix <= 0:
                    raise ValueError
                return max(0, size - suffix), size - 1
            start = int(start_text)
            end = int(end_text) if end_text else size - 1
            if start < 0 or start >= size or end < start:
                raise ValueError
            return start, min(end, size - 1)
        except (ValueError, TypeError):
            self.send_response(416)
            self.send_header("Content-Range", f"bytes */{size}")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return None, None

    def _copy_bounded(self, source: BinaryIO, remaining: int) -> None:
        while remaining and not self.fixture.stop_event.is_set():
            chunk = source.read(min(remaining, self.fixture.stream_chunk_bytes))
            if not chunk:
                break
            self.wfile.write(chunk)
            remaining -= len(chunk)
            with self.fixture.state.lock:
                self.fixture.state.bytes_served += len(chunk)

    def _send_bytes(
        self, payload: bytes, content_type: str, *, send_body: bool
    ) -> None:
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        if send_body:
            self.wfile.write(payload)


class LiveHlsFixtureServer:
    def __init__(
        self,
        fixture: LiveHlsFixture,
        *,
        bind: str = "127.0.0.1",
        port: int = 0,
        max_clients: int = 8,
    ) -> None:
        if not 1 <= max_clients <= MAX_CLIENTS:
            raise FixtureConfigurationError("max_clients is out of bounds")
        self.fixture = fixture
        self.httpd = _BoundedThreadingHttpServer((bind, port), fixture, max_clients)
        self.httpd.timeout = 0.1
        self._thread: threading.Thread | None = None

    @property
    def port(self) -> int:
        return int(self.httpd.server_address[1])

    def start(self) -> "LiveHlsFixtureServer":
        if self._thread is not None:
            raise RuntimeError("fixture server was already started")
        self._thread = threading.Thread(
            target=self._serve,
            name="air-live-fixture",
            daemon=False,
        )
        self._thread.start()
        return self

    def _serve(self) -> None:
        while not self.fixture.stop_event.is_set():
            self.httpd.handle_request()

    def close(self) -> None:
        self.fixture.stop_event.set()
        self.httpd.close_active_requests()
        if self._thread is not None:
            self._thread.join(timeout=2)
            if self._thread.is_alive():
                raise RuntimeError("fixture accept loop did not stop")
        self.httpd.server_close()

    def __enter__(self) -> "LiveHlsFixtureServer":
        return self.start()

    def __exit__(self, *unused: object) -> None:
        self.close()


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Serve Air's generated HLS corpus with deterministic impairments."
    )
    parser.add_argument(
        "--corpus-dir",
        type=Path,
        default=Path(__file__).resolve().parent,
    )
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18080)
    parser.add_argument("--live-window", type=int, default=12)
    parser.add_argument("--segment-delay-ms", type=int, default=0)
    parser.add_argument("--jitter-ms", type=int, default=0)
    parser.add_argument("--jitter-seed", default="air-live-fixture-v1")
    parser.add_argument("--delay-segment", action="append", default=[])
    parser.add_argument("--http-fail-once")
    parser.add_argument("--http-fail-status", type=int, default=503)
    parser.add_argument("--disconnect-once")
    parser.add_argument("--discontinuity-before", type=int)
    parser.add_argument("--max-clients", type=int, default=8)
    parser.add_argument("--quiet", action="store_true")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    log_sink = None if args.quiet else lambda message: print(message, file=sys.stderr)
    try:
        fixture = LiveHlsFixture(
            args.corpus_dir,
            live_window=args.live_window,
            segment_delay_millis=args.segment_delay_ms,
            jitter_millis=args.jitter_ms,
            delay_segments=args.delay_segment,
            jitter_seed=args.jitter_seed,
            http_fail_once=args.http_fail_once,
            http_fail_status=args.http_fail_status,
            disconnect_once=args.disconnect_once,
            discontinuity_before=args.discontinuity_before,
            log_sink=log_sink,
        )
        server = LiveHlsFixtureServer(
            fixture,
            bind=args.bind,
            port=args.port,
            max_clients=args.max_clients,
        )
    except (FixtureConfigurationError, OSError) as error:
        print(f"Air live fixture could not start: {error}", file=sys.stderr)
        return 2

    def request_stop(unused_signum: int, unused_frame: object) -> None:
        fixture.stop_event.set()

    signal.signal(signal.SIGINT, request_stop)
    signal.signal(signal.SIGTERM, request_stop)
    server.start()
    print(f"AIR_LIVE_FIXTURE_READY host={args.bind} port={server.port}", flush=True)
    try:
        while server._thread is not None and server._thread.is_alive():
            server._thread.join(timeout=0.5)
    finally:
        server.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
