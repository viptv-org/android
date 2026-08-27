package com.getair.video

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlaybackRequirements(
    val container: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val subtitleFormat: String? = null,
    val adaptiveProtocol: String? = null,
    val drmScheme: String? = null,
    val live: Boolean = false,
    val seekableLive: Boolean = false,
    val audioTrackSelection: Boolean = false,
    val subtitleTrackSelection: Boolean = false,
    val videoTrackSelection: Boolean = false,
    val externalSubtitles: Boolean = false,
    val hdr: Boolean = false,
    val hardwareAcceleration: Boolean = false,
)

enum class BackendRejection {
    ProbeFailed,
    Container,
    VideoCodec,
    AudioCodec,
    SubtitleFormat,
    AdaptiveProtocol,
    Drm,
    Live,
    SeekableLive,
    AudioTrackSelection,
    SubtitleTrackSelection,
    VideoTrackSelection,
    ExternalSubtitles,
    Hdr,
    HardwareAcceleration,
}

data class BackendCandidate(
    val id: String,
    val capabilities: PlayerCapabilities? = null,
    val rejections: Set<BackendRejection> = emptySet(),
)

sealed interface BackendSelection {
    val candidates: List<BackendCandidate>

    data class Selected(
        val factory: VideoBackendFactory,
        val capabilities: PlayerCapabilities,
        override val candidates: List<BackendCandidate>,
    ) : BackendSelection {
        override fun toString(): String = "BackendSelection.Selected(factory=${factory.id})"
    }

    data class Unavailable(
        override val candidates: List<BackendCandidate>,
    ) : BackendSelection
}

/**
 * Probes lazily in the caller's priority order so a lightweight platform backend
 * does not load an optional native fallback. Probe exceptions become capability
 * misses; cancellation still propagates.
 */
class VideoBackendRouter(factories: List<VideoBackendFactory>) {
    private val factories = factories.toList()
    private val probeMutex = Mutex()
    private val cachedProbes = mutableMapOf<String, PlayerCapabilities?>()

    init {
        require(this.factories.isNotEmpty()) { "At least one video backend is required" }
        require(this.factories.map(VideoBackendFactory::id).distinct().size == this.factories.size) {
            "Video backend IDs must be unique"
        }
        require(this.factories.all { BACKEND_ID.matches(it.id) }) {
            "Video backend IDs must be lowercase stable identifiers"
        }
    }

    suspend fun select(
        requirements: PlaybackRequirements = PlaybackRequirements(),
        refreshProbes: Boolean = false,
    ): BackendSelection {
        val candidates = mutableListOf<BackendCandidate>()
        for (factory in factories) {
            val capabilities = probe(factory, refreshProbes)
            val candidate = BackendCandidate(
                id = factory.id,
                capabilities = capabilities,
                rejections = capabilities?.rejections(requirements)
                    ?: setOf(BackendRejection.ProbeFailed),
            )
            candidates += candidate
            if (capabilities != null && candidate.rejections.isEmpty()) {
                return BackendSelection.Selected(factory, capabilities, candidates.toList())
            }
        }
        return BackendSelection.Unavailable(candidates)
    }

    suspend fun invalidateProbes() = probeMutex.withLock { cachedProbes.clear() }

    private suspend fun probe(factory: VideoBackendFactory, refresh: Boolean): PlayerCapabilities? =
        probeMutex.withLock {
            if (!refresh && factory.id in cachedProbes) return@withLock cachedProbes[factory.id]
            val capabilities = try {
                factory.probe()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
            cachedProbes[factory.id] = capabilities
            capabilities
        }
}

private fun PlayerCapabilities.rejections(requirements: PlaybackRequirements): Set<BackendRejection> = buildSet {
    if (!containers.supports(requirements.container)) add(BackendRejection.Container)
    if (!videoCodecs.supports(requirements.videoCodec)) add(BackendRejection.VideoCodec)
    if (!audioCodecs.supports(requirements.audioCodec)) add(BackendRejection.AudioCodec)
    if (!subtitleFormats.supports(requirements.subtitleFormat)) add(BackendRejection.SubtitleFormat)
    if (!adaptiveProtocols.supports(requirements.adaptiveProtocol)) add(BackendRejection.AdaptiveProtocol)
    if (!drmSchemes.supports(requirements.drmScheme)) add(BackendRejection.Drm)
    if ((requirements.live || requirements.seekableLive) && !supportsLive) add(BackendRejection.Live)
    if (requirements.seekableLive && !supportsSeekableLive) add(BackendRejection.SeekableLive)
    if (requirements.audioTrackSelection && !supportsAudioTrackSelection) add(BackendRejection.AudioTrackSelection)
    if (requirements.subtitleTrackSelection && !supportsSubtitleTrackSelection) {
        add(BackendRejection.SubtitleTrackSelection)
    }
    if (requirements.videoTrackSelection && !supportsVideoTrackSelection) add(BackendRejection.VideoTrackSelection)
    if (requirements.externalSubtitles && !supportsExternalSubtitles) add(BackendRejection.ExternalSubtitles)
    if (requirements.hdr && !supportsHdr) add(BackendRejection.Hdr)
    if (
        requirements.hardwareAcceleration &&
        hardwareAcceleration !in setOf(HardwareAcceleration.Decode, HardwareAcceleration.DecodeAndRender)
    ) {
        add(BackendRejection.HardwareAcceleration)
    }
}

private fun Set<String>.supports(required: String?): Boolean =
    required == null || any { it.equals(required, ignoreCase = true) }

private val BACKEND_ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
