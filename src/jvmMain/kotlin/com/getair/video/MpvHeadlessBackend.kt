package com.getair.video

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal class MpvHeadlessBackendFactory(
    private val options: MpvProcessOptions = MpvProcessOptions(),
) : VideoBackendFactory {
    override val id: String = "mpv-headless"

    override suspend fun probe(): PlayerCapabilities {
        val connection = MpvIpcConnection.start(options)
        return try {
            connection.command(strings("get_property", "mpv-version"))
            MPV_BASELINE_CAPABILITIES
        } finally {
            connection.close()
        }
    }

    override fun create(): VideoPlayer = DefaultVideoPlayer(
        MpvSessionBackend(clientFactory = { MpvIpcConnection.start(options) }),
        Dispatchers.IO,
    )
}

internal class MpvSessionBackend(
    private val clientFactory: suspend () -> MpvCommandClient,
    private val openTimeoutMillis: Long = 20_000,
) : VideoBackend {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val openMutex = Mutex()
    private val eventFlow = kotlinx.coroutines.flow.MutableSharedFlow<BackendEvent>(extraBufferCapacity = 128)
    private val properties = mutableMapOf<String, JsonElement?>()
    private var client: MpvCommandClient? = null
    private var sessionId: PlaybackSessionId? = null
    private var source: PlaybackSource? = null
    private var openCompletion: CompletableDeferred<OpenedMedia>? = null
    private var seekPending = false
    private var opening = false
    private var released = false
    private var targets: Map<String, MpvTrackTarget> = emptyMap()

    override val capabilities: PlayerCapabilities = MPV_BASELINE_CAPABILITIES
    override val events = eventFlow

    override suspend fun open(
        sessionId: PlaybackSessionId,
        source: PlaybackSource,
        playWhenReady: Boolean,
    ): OpenedMedia = openMutex.withLock {
        check(!released) { "MPV backend is closed" }
        val client = ensureClient()
        this.sessionId = sessionId
        this.source = source
        properties.clear()
        targets = emptyMap()
        val completion = CompletableDeferred<OpenedMedia>()
        openCompletion = completion
        opening = true
        try {
            val headerFields = JsonArray(source.headers.map { (name, value) -> JsonPrimitive("$name: $value") })
            client.command(listOf(mpvString("set_property"), mpvString("http-header-fields"), headerFields))
            client.command(strings("set_property", "pause", if (playWhenReady) "no" else "yes"))
            client.command(strings("loadfile", source.uri, "replace"))
            withTimeout(openTimeoutMillis) { completion.await() }
        } catch (_: TimeoutCancellationException) {
            opening = false
            this.sessionId = null
            runCatching { client.command(strings("stop")) }
            throw PlaybackFailure(PlaybackError(PlaybackErrorCode.Source, "MPV timed out while opening media", true))
        } catch (error: CancellationException) {
            opening = false
            this.sessionId = null
            throw error
        } catch (error: Throwable) {
            opening = false
            this.sessionId = null
            if (error is PlaybackFailure) throw error
            throw PlaybackFailure(PlaybackError(PlaybackErrorCode.Source, "MPV could not open the media source", true))
        } finally {
            if (openCompletion === completion) openCompletion = null
        }
    }

    private suspend fun ensureClient(): MpvCommandClient {
        client?.let { return it }
        val created = clientFactory()
        client = created
        scope.launch(start = CoroutineStart.UNDISPATCHED) { created.events.collect(::handleEvent) }
        OBSERVED_PROPERTIES.forEachIndexed { index, property ->
            created.command(listOf(mpvString("observe_property"), mpvLong((index + 1).toLong()), mpvString(property)))
        }
        return created
    }

    private suspend fun handleEvent(event: JsonObject) {
        when (event.string("event")) {
            "file-loaded" -> finishOpen()
            "property-change" -> handleProperty(event.string("name"), event["data"])
            "seek" -> seekPending = true
            "playback-restart" -> {
                val active = sessionId ?: return
                eventFlow.tryEmit(BackendEvent.BufferingChanged(active, false, bufferedPositionMillis()))
                if (seekPending) {
                    seekPending = false
                    eventFlow.tryEmit(BackendEvent.SeekFinished(active, positionMillis()))
                }
            }
            "end-file" -> handleEndFile(event)
        }
    }

    private suspend fun finishOpen() {
        val completion = openCompletion ?: return
        val client = client ?: return
        source?.externalSubtitles.orEmpty().forEach { subtitle ->
            runCatching {
                client.command(
                    strings(
                        "sub-add",
                        subtitle.uri,
                        if (subtitle.isDefault) "select" else "auto",
                        subtitle.label.orEmpty(),
                        subtitle.language.orEmpty(),
                    ),
                )
            }
        }
        OBSERVED_PROPERTIES.forEach { property ->
            properties[property] = runCatching {
                client.command(strings("get_property", property))
            }.getOrNull()
        }
        val tracks = trackSnapshot(properties["track-list"])
        targets = tracks.targets
        opening = false
        if (completion.isActive) {
            completion.complete(
                OpenedMedia(
                    timeline = timeline(),
                    audioTracks = tracks.audio,
                    subtitleTracks = tracks.subtitles,
                    videoTracks = tracks.video,
                    selectedAudioTrackId = tracks.selectedAudio,
                    selectedSubtitleTrackId = tracks.selectedSubtitle,
                    selectedVideoTrackId = tracks.selectedVideo,
                ),
            )
        }
    }

    private fun handleProperty(name: String?, data: JsonElement?) {
        if (name == null) return
        if (opening) return
        properties[name] = data
        val active = sessionId ?: return
        when (name) {
            "pause", "paused-for-cache" -> emitPlaybackState(active)
            "time-pos" -> eventFlow.tryEmit(BackendEvent.PositionChanged(active, positionMillis()))
            "demuxer-cache-time" -> eventFlow.tryEmit(
                BackendEvent.BufferingChanged(active, isBuffering(), bufferedPositionMillis()),
            )
            "duration", "seekable" -> eventFlow.tryEmit(BackendEvent.TimelineChanged(active, timeline()))
            "track-list" -> {
                val tracks = trackSnapshot(data)
                targets = tracks.targets
                eventFlow.tryEmit(BackendEvent.TracksChanged(active, tracks.audio, tracks.subtitles, tracks.video))
            }
        }
    }

    private fun emitPlaybackState(active: PlaybackSessionId) {
        val paused = properties.boolean("pause") ?: true
        val buffering = isBuffering()
        eventFlow.tryEmit(BackendEvent.PlaybackChanged(active, !paused && !buffering, !paused))
        eventFlow.tryEmit(BackendEvent.BufferingChanged(active, buffering, bufferedPositionMillis()))
    }

    private fun handleEndFile(event: JsonObject) {
        val reason = event.string("reason")
        val completion = openCompletion
        if (reason == "error" || reason == "unknown") {
            opening = false
            val failure = PlaybackFailure(
                PlaybackError(PlaybackErrorCode.Decode, "MPV failed while opening or decoding media", true),
            )
            if (completion?.isActive == true) completion.completeExceptionally(failure)
            else sessionId?.let { eventFlow.tryEmit(BackendEvent.Failed(it, failure.error)) }
        } else if (reason == "eof") {
            sessionId?.let { eventFlow.tryEmit(BackendEvent.PlaybackEnded(it)) }
        }
    }

    override fun play() = launchCommand(strings("set_property", "pause", "no"))
    override fun pause() = launchCommand(strings("set_property", "pause", "yes"))
    override fun seekTo(positionMillis: Long) =
        launchCommand(listOf(mpvString("seek"), JsonPrimitive(positionMillis / 1_000.0), mpvString("absolute+exact")))

    override fun selectAudioTrack(id: String?): TrackSelectionResult = selectTrack("aid", "audio", id)
    override fun selectSubtitleTrack(id: String?): TrackSelectionResult = selectTrack("sid", "sub", id)
    override fun selectVideoTrack(id: String?): TrackSelectionResult = selectTrack("vid", "video", id)

    private fun selectTrack(property: String, type: String, id: String?): TrackSelectionResult {
        val target = id?.let(targets::get)
        if (id != null && (target == null || target.type != type)) return TrackSelectionResult.NotFound(id)
        launchCommand(
            listOf(
                mpvString("set_property"),
                mpvString(property),
                target?.nativeId ?: mpvString("no"),
            ),
        )
        return id?.let(TrackSelectionResult::Selected) ?: TrackSelectionResult.Disabled
    }

    override fun stop() {
        sessionId = null
        opening = false
        source = null
        properties.clear()
        targets = emptyMap()
        launchCommand(strings("stop"))
    }

    private fun launchCommand(command: List<JsonElement>) {
        if (released) return
        scope.launch { runCatching { client?.command(command) } }
    }

    internal suspend fun diagnosticProperty(name: String): JsonElement? {
        require(name in MPV_DIAGNOSTIC_PROPERTIES) { "Unsupported MPV diagnostic property" }
        return client?.command(strings("get_property", name))
    }

    override fun close() {
        if (released) return
        released = true
        sessionId = null
        openCompletion?.cancel()
        openCompletion = null
        client?.close()
        client = null
        scope.cancel()
    }

    private fun timeline(): PlaybackTimeline {
        val duration = properties.secondsMillis("duration")
        return when (source?.kindHint) {
            PlaybackKind.Live -> PlaybackTimeline(PlaybackKind.Live, liveEdgeMillis = duration)
            PlaybackKind.SeekableLive -> PlaybackTimeline(
                PlaybackKind.SeekableLive,
                seekableRange = duration?.let { SeekableRange(0, it) },
                liveEdgeMillis = duration,
            )
            PlaybackKind.OnDemand -> PlaybackTimeline(PlaybackKind.OnDemand, durationMillis = duration ?: 0)
            null -> if (duration == null) {
                PlaybackTimeline(PlaybackKind.Live)
            } else {
                PlaybackTimeline(PlaybackKind.OnDemand, durationMillis = duration)
            }
        }
    }

    private fun positionMillis(): Long = properties.secondsMillis("time-pos") ?: 0
    private fun bufferedPositionMillis(): Long? = properties.secondsMillis("demuxer-cache-time")
    private fun isBuffering(): Boolean = properties.boolean("paused-for-cache") == true
}

private data class MpvTrackTarget(val type: String, val nativeId: JsonPrimitive)
private data class MpvTrackSnapshot(
    val audio: List<AudioTrack>,
    val subtitles: List<SubtitleTrack>,
    val video: List<VideoTrack>,
    val selectedAudio: String?,
    val selectedSubtitle: String?,
    val selectedVideo: String?,
    val targets: Map<String, MpvTrackTarget>,
)

private fun trackSnapshot(element: JsonElement?): MpvTrackSnapshot {
    val audio = mutableListOf<AudioTrack>()
    val subtitles = mutableListOf<SubtitleTrack>()
    val video = mutableListOf<VideoTrack>()
    val targets = mutableMapOf<String, MpvTrackTarget>()
    var selectedAudio: String? = null
    var selectedSubtitle: String? = null
    var selectedVideo: String? = null
    val tracks = (element as? JsonArray).orEmpty()
    tracks.forEachIndexed { index, item ->
        val track = item as? JsonObject ?: return@forEachIndexed
        val type = track.string("type") ?: return@forEachIndexed
        if (type !in setOf("audio", "sub", "video")) return@forEachIndexed
        val nativeId = track["id"]?.jsonPrimitive ?: return@forEachIndexed
        val stableId = "mpv:$type:${nativeId.content}"
        targets[stableId] = MpvTrackTarget(type, nativeId)
        val label = track.string("title") ?: track.string("lang") ?: "${typeLabel(type)} ${index + 1}"
        val selected = track.boolean("selected") == true
        when (type) {
            "audio" -> {
                audio += AudioTrack(
                    id = stableId,
                    label = label,
                    language = track.string("lang"),
                    isDefault = track.boolean("default") == true,
                    isForced = track.boolean("forced") == true,
                    channels = track.int("demux-channel-count"),
                    codec = track.string("codec"),
                )
                if (selected) selectedAudio = stableId
            }
            "sub" -> {
                subtitles += SubtitleTrack(
                    id = stableId,
                    label = label,
                    language = track.string("lang"),
                    isDefault = track.boolean("default") == true,
                    isForced = track.boolean("forced") == true,
                    format = track.string("codec"),
                    external = track.boolean("external") == true,
                )
                if (selected) selectedSubtitle = stableId
            }
            "video" -> {
                video += VideoTrack(
                    id = stableId,
                    label = label,
                    language = track.string("lang"),
                    isDefault = track.boolean("default") == true,
                    isForced = track.boolean("forced") == true,
                    width = track.int("demux-w"),
                    height = track.int("demux-h"),
                    bitrate = track.long("demux-bitrate"),
                    codec = track.string("codec"),
                )
                if (selected) selectedVideo = stableId
            }
        }
    }
    return MpvTrackSnapshot(audio, subtitles, video, selectedAudio, selectedSubtitle, selectedVideo, targets)
}

private fun typeLabel(type: String): String = when (type) {
    "audio" -> "Audio"
    "sub" -> "Subtitles"
    else -> "Video"
}

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
private fun JsonObject.boolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull
private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull
private fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull
private fun Map<String, JsonElement?>.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.booleanOrNull
private fun Map<String, JsonElement?>.secondsMillis(name: String): Long? {
    val seconds = (this[name] as? JsonPrimitive)?.doubleOrNull ?: return null
    if (!seconds.isFinite() || seconds < 0) return null
    return (seconds * 1_000).toLong()
}

internal fun strings(vararg values: String): List<JsonElement> = values.map(::mpvString)

private val OBSERVED_PROPERTIES = listOf(
    "pause",
    "paused-for-cache",
    "time-pos",
    "duration",
    "demuxer-cache-time",
    "seekable",
    "track-list",
)

private val MPV_DIAGNOSTIC_PROPERTIES = setOf("vo-configured", "video-out-params", "hwdec-current")

internal val MPV_BASELINE_CAPABILITIES = PlayerCapabilities(
    containers = setOf("mkv", "matroska", "mpegts", "ts", "hls"),
    videoCodecs = setOf("h264", "hevc", "av1"),
    audioCodecs = setOf("aac"),
    subtitleFormats = setOf("srt", "vtt", "ass"),
    adaptiveProtocols = setOf("hls"),
    supportsAudioTrackSelection = true,
    supportsSubtitleTrackSelection = true,
    supportsVideoTrackSelection = true,
    supportsExternalSubtitles = true,
    supportsLive = true,
    supportsSeekableLive = true,
    supportsPlaybackRate = true,
    supportsHdr = false,
    supportsAudioPassthrough = false,
    hardwareAcceleration = HardwareAcceleration.Unknown,
)
