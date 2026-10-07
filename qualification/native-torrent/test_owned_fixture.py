import hashlib
import json
from pathlib import Path
import socket
import struct
import tempfile
import threading
import unittest

from owned_fixture import OwnedPeer, atomic_json, decode, encode, pad_owned_payload


class OwnedFixtureTest(unittest.TestCase):
    def test_padding_preserves_episode_indices_and_exceeds_admitted_budget(self):
        with tempfile.TemporaryDirectory() as directory:
            payload = Path(directory)
            for name in ["00-readme.txt", "01-episode.mp4", "02-episode.mp4"]:
                (payload / name).write_bytes(b"owned")
            pad_owned_payload(payload, 1025)
            files = sorted(payload.iterdir())
            self.assertEqual([path.name for path in files[:3]],
                             ["00-readme.txt", "01-episode.mp4", "02-episode.mp4"])
            self.assertEqual(sum(path.stat().st_size for path in files), 1025)
            self.assertEqual((payload / "03-owned-padding.bin").read_bytes(), bytes(1010))

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        payload = self.root / "episodes"
        payload.mkdir()
        self.content = b"owned episode bytes" * 2000
        (payload / "episode.mp4").write_bytes(self.content)
        info = {b"name": b"episodes", b"files": [{b"length": len(self.content), b"path": [b"episode.mp4"]}],
                b"piece length": 16384, b"pieces": b"".join(hashlib.sha1(self.content[i:i + 16384]).digest() for i in range(0, len(self.content), 16384))}
        (self.root / "owned.torrent").write_bytes(encode({b"info": info}))
        atomic_json(self.root / "hold-pieces.json", [])
        self.peer = OwnedPeer(self.root)
        self.thread = threading.Thread(target=self.peer.run)
        self.thread.start()

    def tearDown(self):
        self.peer.stop()
        self.thread.join(2)
        self.assertFalse(self.thread.is_alive())
        self.directory.cleanup()

    def connect(self, info_hash=None):
        client = socket.create_connection(("127.0.0.1", self.peer.port), timeout=2)
        client.sendall(b"\x13BitTorrent protocol" + bytes(8) + (info_hash or self.peer.hash) + b"-TEST00-000000000000")
        return client

    def message(self, client):
        length = struct.unpack("!I", self.peer.receive(client, 4))[0]
        return self.peer.receive(client, length)

    def ready(self, client):
        handshake = self.peer.receive(client, 68)
        self.assertEqual(self.peer.hash, handshake[28:48])
        self.assertEqual(5, self.message(client)[0])
        self.assertEqual(b"\x01", self.message(client))
        self.assertEqual(b"\x14\x00", self.message(client)[:2])

    def test_metadata_hash_and_exact_piece_bytes(self):
        with self.connect() as client:
            self.ready(client)
            self.peer.send(client, b"\x14\x00" + encode({b"m": {b"ut_metadata": 7}}))
            self.peer.send(client, b"\x14\x01" + encode({b"msg_type": 0, b"piece": 0}))
            response = self.message(client)
            self.assertEqual(b"\x14\x07", response[:2])
            metadata, end = decode(response[2:])
            self.assertEqual(1, metadata[b"msg_type"])
            self.assertEqual(self.peer.metadata, response[2 + end:])
            self.peer.send(client, b"\x06" + struct.pack("!III", 1, 20, 31))
            self.assertEqual(b"\x07" + struct.pack("!II", 1, 20) + self.content[16404:16435], self.message(client))

    def test_missing_piece_wait_can_be_cancelled_without_reply(self):
        atomic_json(self.root / "hold-pieces.json", [1])
        with self.connect() as client:
            self.ready(client)
            request = struct.pack("!III", 1, 0, 32)
            self.peer.send(client, b"\x06" + request)
            client.settimeout(0.15)
            with self.assertRaises(socket.timeout):
                client.recv(1)
            self.peer.send(client, b"\x08" + request)
            atomic_json(self.root / "hold-pieces.json", [])
            with self.assertRaises(socket.timeout):
                client.recv(1)
        self.assertEqual(0, self.peer.metrics()["served_bytes"])

    def test_metadata_wait_release_and_socket_shutdown_are_real(self):
        atomic_json(self.root / "hold-metadata.json", True)
        with self.connect() as client:
            self.ready(client)
            self.peer.send(client, b"\x14\x01" + encode({b"msg_type": 0, b"piece": 0}))
            client.settimeout(0.15)
            with self.assertRaises(socket.timeout):
                client.recv(1)
            atomic_json(self.root / "hold-metadata.json", False)
            client.settimeout(1)
            response = self.message(client)
            self.assertEqual(b"\x14\x01", response[:2])
            self.peer.stop()
            self.assertEqual(b"", client.recv(1))

    def test_wrong_hash_is_denied_and_listener_is_literal_loopback(self):
        self.assertEqual("127.0.0.1", self.peer.listener.getsockname()[0])
        with self.connect(bytes(20)) as client:
            self.assertEqual(b"", client.recv(68))

    def test_corrupt_payload_cannot_be_served(self):
        (self.root / "episodes/episode.mp4").write_bytes(b"corrupted")
        with self.assertRaises(ValueError):
            OwnedPeer(self.root)


if __name__ == "__main__":
    unittest.main()
