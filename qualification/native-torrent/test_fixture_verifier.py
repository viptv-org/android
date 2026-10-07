import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from verify_fixture_artifacts import verify


class FixtureVerifierTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.reference = self.root / "TORRENT_REF"
        self.reference.write_text("a" * 40)
        self.config = self.root / "config.json"
        self.config.write_text(json.dumps({"origin": "https://127.0.0.1:9445", "peer": "127.0.0.1:40000", "access_token": "synthetic", "sources": [{"index": 1, "stream_id": "opaque"}]}))
        self.products = self.root / "products"
        files = {}
        for name, content in {"kotlin/uniffi/playback_gateway_ffi/playback_gateway_ffi.kt": b"fun newNativeOwned(",
                              "target/release/libplayback_gateway_ffi.so": b"host fixture", "jniLibs/armeabi-v7a/libplayback_gateway_ffi.so": b"device fixture"}.items():
            path = self.products / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
            files[name] = hashlib.sha256(content).hexdigest()
        self.manifest = {"source_revision": "a" * 40, "fixture_only": True, "features": ["torrent", "test-network-policy"], "dht": False, "files": files}
        self.stamp()

    def tearDown(self):
        self.temporary.cleanup()

    def stamp(self):
        (self.products / "fixture-build.json").write_text(json.dumps(self.manifest))

    def test_exact_private_products_accept(self):
        verify(self.products, self.reference, self.config)

    def test_normal_policy_or_unpinned_source_rejected(self):
        for field, value in [("features", ["torrent"]), ("source_revision", "b" * 40), ("fixture_only", False), ("dht", True)]:
            original = self.manifest[field]
            self.manifest[field] = value
            self.stamp()
            with self.assertRaises(ValueError):
                verify(self.products, self.reference, self.config)
            self.manifest[field] = original

    def test_changed_artifact_rejected(self):
        (self.products / "jniLibs/armeabi-v7a/libplayback_gateway_ffi.so").write_bytes(b"mixed source")
        with self.assertRaises(ValueError):
            verify(self.products, self.reference, self.config)

    def test_nonliteral_control_or_peer_rejected(self):
        original = json.loads(self.config.read_text())
        for field, value in [("origin", "https://localhost:9445"), ("origin", "https://127.0.0.1:9445/provider"),
                             ("origin", "https://secret@127.0.0.1:9445"), ("peer", "example.invalid:4444"), ("peer", "127.0.0.1:0")]:
            config = dict(original, **{field: value})
            self.config.write_text(json.dumps(config))
            with self.assertRaises(ValueError):
                verify(self.products, self.reference, self.config)


if __name__ == "__main__":
    unittest.main()
