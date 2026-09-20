from __future__ import annotations

import http.client
import io
from pathlib import Path
import sys
import tempfile
import threading
import time
import unittest
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


CORPUS_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(CORPUS_DIR))

from live_hls_fixture import (  # noqa: E402
    FixtureConfigurationError,
    LiveHlsFixture,
    MAX_MANIFEST_BYTES,
)
from live_hls_server import LiveHlsFixtureServer  # noqa: E402


class LiveHlsServerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.corpus_dir = Path(self.temporary_directory.name)
        self.hls_dir = self.corpus_dir / "output" / "hls"
        self.hls_dir.mkdir(parents=True)
        self.segment_bytes = {
            f"segment-{index:03}.ts": bytes((index, 10, 20, 30, 40, 50))
            for index in range(5)
        }
        for name, payload in self.segment_bytes.items():
            (self.hls_dir / name).write_bytes(payload)
        self.event_bytes = self._event_manifest(self.segment_bytes)
        (self.hls_dir / "event.m3u8").write_bytes(self.event_bytes)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    @staticmethod
    def _event_manifest(segments: dict[str, bytes]) -> bytes:
        lines = [
            "#EXTM3U",
            "#EXT-X-VERSION:3",
            "#EXT-X-TARGETDURATION:1",
            "#EXT-X-PLAYLIST-TYPE:EVENT",
        ]
        for name in segments:
            lines.extend(("#EXTINF:1.000000,", name))
        lines.append("#EXT-X-ENDLIST")
        return ("\n".join(lines) + "\n").encode("ascii")

    @staticmethod
    def _get(server: LiveHlsFixtureServer, path: str, **kwargs: object) -> bytes:
        request = Request(f"http://127.0.0.1:{server.port}{path}", **kwargs)
        with urlopen(request, timeout=2) as response:
            return response.read()

    def test_serves_exact_event_segment_and_byte_range(self) -> None:
        fixture = LiveHlsFixture(self.corpus_dir, live_window=3)
        with LiveHlsFixtureServer(fixture) as server:
            self.assertEqual(self._get(server, "/hls/event.m3u8"), self.event_bytes)
            self.assertEqual(
                self._get(server, "/hls/segment-002.ts"),
                self.segment_bytes["segment-002.ts"],
            )
            request = Request(
                f"http://127.0.0.1:{server.port}/hls/segment-002.ts",
                headers={"Range": "bytes=1-3"},
            )
            with urlopen(request, timeout=2) as response:
                self.assertEqual(response.status, 206)
                self.assertEqual(response.read(), bytes((10, 20, 30)))

    def test_live_playlist_refreshes_sliding_window_and_marks_discontinuity(self) -> None:
        fixture = LiveHlsFixture(
            self.corpus_dir,
            live_window=3,
            discontinuity_before=3,
        )
        with LiveHlsFixtureServer(fixture) as server:
            first = self._get(server, "/hls/live.m3u8").decode("ascii")
            second = self._get(server, "/hls/live.m3u8").decode("ascii")
            third = self._get(server, "/hls/live.m3u8").decode("ascii")

        self.assertIn("#EXT-X-MEDIA-SEQUENCE:0", first)
        self.assertIn("segment-000.ts", first)
        self.assertNotIn("segment-003.ts", first)
        self.assertNotIn("#EXT-X-DISCONTINUITY\n", first)

        self.assertIn("#EXT-X-MEDIA-SEQUENCE:1", second)
        self.assertNotIn("segment-000.ts", second)
        self.assertIn(
            "#EXT-X-DISCONTINUITY\n#EXTINF:1.000000,\nsegment-003.ts",
            second,
        )

        self.assertIn("#EXT-X-MEDIA-SEQUENCE:2", third)
        self.assertNotIn("segment-001.ts", third)
        self.assertIn("segment-004.ts", third)

    def test_segment_delay_is_applied_with_reasonable_tolerance(self) -> None:
        fixture = LiveHlsFixture(
            self.corpus_dir,
            live_window=3,
            segment_delay_millis=200,
            delay_segments=("segment-002.ts",),
        )
        with LiveHlsFixtureServer(fixture) as server:
            started = time.monotonic()
            actual = self._get(server, "/hls/segment-002.ts")
            elapsed = time.monotonic() - started

        self.assertEqual(actual, self.segment_bytes["segment-002.ts"])
        self.assertGreaterEqual(elapsed, 0.17)
        self.assertLess(elapsed, 1.0)
        self.assertEqual(fixture.state.snapshot()["delayedRequests"], 1)

    def test_jitter_is_seeded_bounded_and_repeatable(self) -> None:
        fixture_one = LiveHlsFixture(
            self.corpus_dir,
            live_window=3,
            segment_delay_millis=1_000,
            jitter_millis=250,
            jitter_seed="repeatable-test",
        )
        fixture_two = LiveHlsFixture(
            self.corpus_dir,
            live_window=3,
            segment_delay_millis=1_000,
            jitter_millis=250,
            jitter_seed="repeatable-test",
        )

        action_one, delay_one = fixture_one.segment_plan("segment-003.ts")
        action_two, delay_two = fixture_two.segment_plan("segment-003.ts")

        self.assertEqual(action_one, "serve")
        self.assertEqual(action_two, "serve")
        self.assertEqual(delay_one, delay_two)
        self.assertGreaterEqual(delay_one, 750)
        self.assertLessEqual(delay_one, 1_250)

    def test_one_shot_http_failure_and_disconnect_recover_on_retry(self) -> None:
        fixture = LiveHlsFixture(
            self.corpus_dir,
            live_window=3,
            http_fail_once="segment-001.ts",
            disconnect_once="segment-002.ts",
        )
        with LiveHlsFixtureServer(fixture) as server:
            with self.assertRaises(HTTPError) as failure:
                self._get(server, "/hls/segment-001.ts")
            self.assertEqual(failure.exception.code, 503)
            failure.exception.close()
            self.assertEqual(
                self._get(server, "/hls/segment-001.ts"),
                self.segment_bytes["segment-001.ts"],
            )

            connection = http.client.HTTPConnection("127.0.0.1", server.port, timeout=2)
            connection.request("GET", "/hls/segment-002.ts")
            with self.assertRaises(
                (http.client.RemoteDisconnected, ConnectionResetError)
            ):
                connection.getresponse()
            connection.close()
            self.assertEqual(
                self._get(server, "/hls/segment-002.ts"),
                self.segment_bytes["segment-002.ts"],
            )

        snapshot = fixture.state.snapshot()
        self.assertEqual(snapshot["httpFailures"], 1)
        self.assertEqual(snapshot["disconnects"], 1)

    def test_shutdown_cancels_an_in_flight_delay(self) -> None:
        fixture = LiveHlsFixture(
            self.corpus_dir,
            live_window=3,
            segment_delay_millis=5_000,
            delay_segments=("segment-002.ts",),
        )
        server = LiveHlsFixtureServer(fixture).start()
        request_finished = threading.Event()

        def request_delayed_segment() -> None:
            try:
                self._get(server, "/hls/segment-002.ts")
            except (HTTPError, URLError, OSError, http.client.HTTPException):
                pass
            finally:
                request_finished.set()

        request_thread = threading.Thread(target=request_delayed_segment)
        request_thread.start()
        deadline = time.monotonic() + 1
        while (
            fixture.state.snapshot()["delayedRequests"] == 0
            and time.monotonic() < deadline
        ):
            time.sleep(0.01)

        started = time.monotonic()
        server.close()
        elapsed = time.monotonic() - started
        request_thread.join(timeout=1)

        self.assertTrue(request_finished.is_set())
        self.assertLess(elapsed, 1.0)
        self.assertEqual(fixture.state.snapshot()["activeRequests"], 0)

    def test_shutdown_closes_an_idle_keep_alive_connection(self) -> None:
        fixture = LiveHlsFixture(self.corpus_dir, live_window=3)
        server = LiveHlsFixtureServer(fixture).start()
        connection = http.client.HTTPConnection("127.0.0.1", server.port, timeout=2)
        connection.request("GET", "/healthz", headers={"Connection": "keep-alive"})
        response = connection.getresponse()
        self.assertEqual(response.read(), b"ok\n")

        started = time.monotonic()
        server.close()
        elapsed = time.monotonic() - started
        connection.close()

        self.assertLess(elapsed, 1.0)

    def test_concurrent_request_handlers_are_bounded(self) -> None:
        fixture = LiveHlsFixture(
            self.corpus_dir,
            live_window=3,
            segment_delay_millis=250,
        )
        outcomes: list[str] = []
        outcomes_lock = threading.Lock()
        start = threading.Barrier(7)

        with LiveHlsFixtureServer(fixture, max_clients=2) as server:
            def request_segment(index: int) -> None:
                start.wait()
                try:
                    self._get(server, f"/hls/segment-{index % 5:03}.ts")
                    outcome = "served"
                except HTTPError as error:
                    error.close()
                    outcome = "rejected"
                except (URLError, OSError, http.client.HTTPException):
                    outcome = "rejected"
                with outcomes_lock:
                    outcomes.append(outcome)

            threads = [
                threading.Thread(target=request_segment, args=(index,))
                for index in range(6)
            ]
            for thread in threads:
                thread.start()
            start.wait()
            for thread in threads:
                thread.join(timeout=2)

        self.assertEqual(len(outcomes), 6)
        self.assertLessEqual(fixture.state.snapshot()["maxActiveRequests"], 2)
        self.assertIn("rejected", outcomes)

    def test_logs_never_include_unknown_paths_queries_or_headers(self) -> None:
        logs = io.StringIO()
        fixture = LiveHlsFixture(
            self.corpus_dir,
            live_window=3,
            log_sink=lambda message: print(message, file=logs),
        )
        with LiveHlsFixtureServer(fixture) as server:
            request = Request(
                f"http://127.0.0.1:{server.port}/private-token?auth=secret-value",
                headers={"Authorization": "Bearer forbidden-value"},
            )
            with self.assertRaises(HTTPError) as failure:
                urlopen(request, timeout=2)
            failure.exception.close()

        emitted = logs.getvalue()
        self.assertIn("event=not_found resource=fixture", emitted)
        self.assertNotIn("private-token", emitted)
        self.assertNotIn("secret-value", emitted)
        self.assertNotIn("forbidden-value", emitted)

    def test_rejects_oversized_manifest_before_reading_it(self) -> None:
        (self.hls_dir / "event.m3u8").write_bytes(b"x" * (MAX_MANIFEST_BYTES + 1))
        with self.assertRaises(FixtureConfigurationError):
            LiveHlsFixture(self.corpus_dir, live_window=3)


if __name__ == "__main__":
    unittest.main()
