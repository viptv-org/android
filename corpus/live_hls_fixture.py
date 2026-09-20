"""Deterministic, bounded live-HLS fixture for Air player tests."""

from __future__ import annotations

import hashlib
from pathlib import Path
import threading
from typing import Callable, Iterable
from urllib.parse import urlsplit


MAX_MANIFEST_BYTES = 256 * 1024
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
