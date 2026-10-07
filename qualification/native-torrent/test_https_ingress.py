import http.client
import json
from pathlib import Path
import ssl
import tempfile
import threading
import unittest
from http.server import ThreadingHTTPServer

from https_ingress import Ingress, certificate


class HttpsIngressTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temporary.name)
        cert, key = certificate(cls.root / "tls")
        cls.trust = ssl.create_default_context(cafile=str(cert))
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Ingress)
        cls.server.backend_port = 0
        cls.server.owned_directory = cls.root
        cls.server.daemon_threads = True
        tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        tls.load_cert_chain(cert, key)
        cls.server.socket = tls.wrap_socket(cls.server.socket, server_side=True)
        cls.thread = threading.Thread(target=cls.server.serve_forever)
        cls.thread.start()
        (cls.root / "backend-ready.json").write_text(json.dumps({"access_token": "synthetic-owned-bearer"}))
        (cls.root / "owned-episodes").mkdir()
        cls.payload = b"owned media bytes" * 300
        (cls.root / "owned-episodes/01-episode.mp4").write_bytes(cls.payload)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(2)
        cls.temporary.cleanup()

    def request(self, method, path, body=None, headers=None):
        connection = http.client.HTTPSConnection("127.0.0.1", self.server.server_port, context=self.trust, timeout=2)
        try:
            connection.request(method, path, body=body, headers=headers or {})
            response = connection.getresponse()
            return response.status, dict(response.getheaders()), response.read()
        finally:
            connection.close()

    def test_real_tls_range_and_head_have_exact_owned_bytes(self):
        status, headers, payload = self.request("GET", "/owned/episode.mp4", headers={"Range": "bytes=42-99"})
        self.assertEqual(206, status)
        self.assertEqual(self.payload[42:100], payload)
        self.assertEqual(f"bytes 42-99/{len(self.payload)}", headers["Content-Range"])
        status, headers, payload = self.request("HEAD", "/owned/episode.mp4")
        self.assertEqual(200, status)
        self.assertEqual(str(len(self.payload)), headers["Content-Length"])
        self.assertEqual(b"", payload)

    def test_fixture_controls_require_bearer_and_closed_input(self):
        control = json.dumps({"hold_metadata": True, "held_pieces": [7]})
        self.assertEqual(401, self.request("POST", "/__fixture/control", control)[0])
        headers = {"Authorization": "Bearer synthetic-owned-bearer", "Content-Type": "application/json"}
        self.assertEqual(200, self.request("POST", "/__fixture/control", control, headers)[0])
        self.assertEqual([7], json.loads((self.root / "hold-pieces.json").read_text()))
        self.assertTrue(json.loads((self.root / "hold-metadata.json").read_text()))
        invalid = json.dumps({"hold_metadata": False, "provider_url": "https://fixture.invalid"})
        self.assertEqual(400, self.request("POST", "/__fixture/control", invalid, headers)[0])
        self.assertTrue(json.loads((self.root / "hold-metadata.json").read_text()))

    def test_no_arbitrary_file_or_upstream_route_is_served(self):
        self.assertEqual(404, self.request("GET", "/owned/../tls/key.pem")[0])
        self.assertEqual(404, self.request("GET", "/provider")[0])
        self.assertEqual("127.0.0.1", self.server.server_address[0])


if __name__ == "__main__":
    unittest.main()
