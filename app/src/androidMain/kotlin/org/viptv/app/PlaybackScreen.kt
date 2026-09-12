package org.viptv.app

import android.view.KeyEvent as NativeKeyEvent
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.getair.video.PlaybackStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private val PlayerOverlayTop = Brush.verticalGradient(listOf(Color(0xE6101112), Color.Transparent))
private val PlayerOverlayBottom = Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6101112)))
private val PlayerTrack = Color(0xFF5A5C5E)

private enum class PlayerTrackMenu { Audio, Subtitles }

/**
 * The canonical 1280×720 player overlay. MainActivity supplies the uniformly
 * scaled design frame; this screen owns only player rendering and remote input.
 */
@Composable
internal fun PlaybackScreen(
    media: Media,
    chromeVisible: Boolean,
    seekPreview: SeekPreview?,
    serverTracks: PlaybackTrackChoices,
    controller: AppController,
) {
    val playback by controller.player.state.collectAsState()
    val scope = rememberCoroutineScope()
    var timelineFocused by remember { mutableStateOf(false) }
    var repeatKey by remember { mutableIntStateOf(0) }
    var activeRepeatCode by remember { mutableIntStateOf(NativeKeyEvent.KEYCODE_UNKNOWN) }
    var commitJob by remember { mutableStateOf<Job?>(null) }
    var menu by remember { mutableStateOf<PlayerTrackMenu?>(null) }
    var trackNotice by remember { mutableStateOf<String?>(null) }
    val rootFocus = remember { FocusRequester() }
    val isLive = media.type == "live"
    // Media3 reports position in the currently opened delivery segment. The
    // controller owns the title-relative offset for resume, progress and Next.
    val absolutePositionMillis = controller.absolutePositionMillis()
    val titleDurationMillis = controller.titleDurationMillis() ?: playback.timeline?.durationMillis

    fun showChrome() = controller.showPlayerChrome()
    fun preview(delta: Long, code: Int) {
        commitJob?.cancel()
        repeatKey = if (activeRepeatCode == code) repeatKey + 1 else 0
        activeRepeatCode = code
        val multiplier = when {
            repeatKey >= 15 -> 60L
            repeatKey >= 9 -> 15L
            repeatKey >= 5 -> 6L
            repeatKey >= 2 -> 3L
            else -> 1L
        }
        controller.previewSeek(delta * multiplier)
    }
    fun releaseSeek() {
        activeRepeatCode = NativeKeyEvent.KEYCODE_UNKNOWN
        repeatKey = 0
        commitJob?.cancel()
        commitJob = scope.launch {
            delay(800)
            controller.commitSeek()
        }
    }

    LaunchedEffect(media.type, media.id, absolutePositionMillis, playback.isPlaying, titleDurationMillis) {
        controller.maybeAutoNext(
            media,
            absolutePositionMillis,
            titleDurationMillis,
            playback.isPlaying,
            playback.status == PlaybackStatus.Ended,
        )
    }
    // The controller owns the seven-second timer. Its local menu ownership
    // prevents the timer from hiding playback context under a track dialog.
    LaunchedEffect(menu) {
        controller.setPlayerMenuOpen(menu != null)
        // A dialog removes its focused menu row on close. Always restore a
        // persistent overlay owner so subsequent remote/media input is not
        // delivered to the SurfaceView instead.
        if (menu == null) rootFocus.requestFocus()
    }
    // A route/session replacement can dispose this screen while its dialog is
    // open. Release timer ownership in that path too.
    DisposableEffect(controller) {
        onDispose { controller.setPlayerMenuOpen(false) }
    }
    BackHandler(enabled = menu != null) {
        // An unavailable-track notice is dismissed before its containing menu;
        // the next Back returns focus to player chrome.
        if (trackNotice != null) trackNotice = null else menu = null
    }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val code = native.keyCode
                // MainActivity remains the single Back-policy owner. Clearing this
                // local timer prevents an already-previewed seek from committing
                // after that policy cancels the preview.
                if (code == NativeKeyEvent.KEYCODE_BACK) {
                    commitJob?.cancel()
                    return@onPreviewKeyEvent false
                }
                if (native.action == NativeKeyEvent.ACTION_DOWN && code != NativeKeyEvent.KEYCODE_BACK) showChrome()
                if (menu != null) return@onPreviewKeyEvent false
                when (native.action) {
                    NativeKeyEvent.ACTION_DOWN -> when (code) {
                        NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            // Repeat events are still consumed, but never toggle again while held.
                            if (!isLive && native.repeatCount == 0) {
                                if (playback.isPlaying) controller.pausePlayback() else controller.resumePlayback()
                            }
                            true
                        }
                        NativeKeyEvent.KEYCODE_MEDIA_PLAY -> {
                            if (!isLive && native.repeatCount == 0) controller.resumePlayback()
                            true
                        }
                        NativeKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            if (!isLive && native.repeatCount == 0) controller.pausePlayback()
                            true
                        }
                        NativeKeyEvent.KEYCODE_MEDIA_REWIND -> if (!isLive) { preview(-60_000, code); true } else true
                        NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> if (!isLive) { preview(60_000, code); true } else true
                        NativeKeyEvent.KEYCODE_DPAD_LEFT -> if (!isLive && timelineFocused) { preview(-10_000, code); true } else false
                        NativeKeyEvent.KEYCODE_DPAD_RIGHT -> if (!isLive && timelineFocused) { preview(10_000, code); true } else false
                        else -> false
                    }
                    NativeKeyEvent.ACTION_UP -> when (code) {
                        NativeKeyEvent.KEYCODE_MEDIA_REWIND,
                        NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        NativeKeyEvent.KEYCODE_DPAD_LEFT,
                        NativeKeyEvent.KEYCODE_DPAD_RIGHT -> if (!isLive && (timelineFocused || code == NativeKeyEvent.KEYCODE_MEDIA_REWIND || code == NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD)) { releaseSeek(); true } else false
                        else -> false
                    }
                    else -> false
                }
            }.focusable(),
    ) {
        AndroidView(factory = { SurfaceView(it).also(controller.player::attach) }, modifier = Modifier.fillMaxSize())
        if (chromeVisible) {
            Box(Modifier.width(1280.dp).height(210.dp).background(PlayerOverlayTop))
            Box(Modifier.offset(y = 382.dp).width(1280.dp).height(338.dp).background(PlayerOverlayBottom))
            androidx.compose.foundation.Image(
                painterResource(R.drawable.viptv_mark),
                contentDescription = "VIPTV",
                modifier = Modifier.offset(64.dp, 40.dp).width(36.dp).height(32.dp),
                contentScale = ContentScale.Fit,
            )
            Text(media.name, color = Color(0xFFF5F5F5), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(112.dp, 36.dp).width(700.dp))
            Text(playbackStatusLabel(playback.status, playback.isPlaying, playback.isBuffering, isLive), color = Color(0xFFBFC1C3), fontSize = 16.sp, textAlign = TextAlign.End, modifier = Modifier.offset(1048.dp, 36.dp).width(168.dp))
            Text(if (isLive) "LIVE NOW" else "PLAYBACK", color = Color(0xFFBFC1C3), fontSize = 16.sp, modifier = Modifier.offset(64.dp, 460.dp))
            Text(media.name, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(64.dp, 486.dp).width(1090.dp))
            Text(playerContext(media, seekPreview), color = Color(0xFFBFC1C3), fontSize = 17.sp, modifier = Modifier.offset(64.dp, 524.dp).width(1090.dp))
            if (!isLive) {
                PlaybackTimeline(
                    positionMillis = absolutePositionMillis,
                    durationMillis = titleDurationMillis,
                    preview = seekPreview,
                    modifier = Modifier.offset(64.dp, 572.dp),
                    onFocused = { timelineFocused = it },
                    onActivate = { if (playback.isPlaying) controller.pausePlayback() else controller.resumePlayback() },
                )
            }
            PlayerControls(
                media = media,
                isLive = isLive,
                isPlaying = playback.isPlaying,
                canSeek = playback.timeline?.canSeek == true,
                serverTracks = serverTracks,
                controller = controller,
                onSeek = { preview(it, if (it < 0) NativeKeyEvent.KEYCODE_MEDIA_REWIND else NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD); releaseSeek() },
                onMenu = { menu = it; trackNotice = null },
            )
        }
        menu?.let { activeMenu ->
            PlayerTrackDialog(
                menu = activeMenu,
                tracks = if (activeMenu == PlayerTrackMenu.Audio) serverTracks.audio else serverTracks.subtitles,
                canDisable = activeMenu == PlayerTrackMenu.Subtitles && serverTracks.subtitlesSupported,
                notice = trackNotice,
                onAudio = { track -> controller.selectAudioTrack(track); menu = null },
                onText = { track -> controller.selectSubtitleTrack(track); menu = null },
                onUnavailable = { trackNotice = "This track is not supported on this TV." },
                onClose = { menu = null },
            )
        }
    }
}

@Composable
private fun PlaybackTimeline(positionMillis: Long, durationMillis: Long?, preview: SeekPreview?, modifier: Modifier, onFocused: (Boolean) -> Unit, onActivate: () -> Unit) {
    val shown = preview?.targetMillis ?: positionMillis
    val fraction = if ((durationMillis ?: 0) > 0) shown.toFloat() / durationMillis!!.toFloat() else 0f
    PlayerIconButton(
        label = "Playback position",
        modifier = modifier.width(1152.dp).height(34.dp),
        onFocused = onFocused,
        onActivate = onActivate,
    ) { focused ->
        Box(Modifier.align(Alignment.TopStart).width(1152.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(PlayerTrack))
        Box(Modifier.align(Alignment.TopStart).width((1152f * fraction.coerceIn(0f, 1f)).dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
        Box(Modifier.align(Alignment.TopStart).offset(x = (1152f * fraction.coerceIn(0f, 1f) - 8f).dp, y = (-5).dp).size(16.dp).clip(RoundedCornerShape(8.dp)).background(if (focused) Color.White else Color(0xFFF5F5F5)))
        Text(formatTime(shown), color = Color(0xFFF5F5F5), fontSize = 14.sp, modifier = Modifier.align(Alignment.TopStart).offset(y = 12.dp).width(576.dp))
        Text(formatTime(durationMillis ?: 0), color = Color(0xFFA6A8AA), fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.align(Alignment.TopStart).offset(x = 576.dp, y = 12.dp).width(576.dp))
    }
}

@Composable
private fun PlayerControls(media: Media, isLive: Boolean, isPlaying: Boolean, canSeek: Boolean, serverTracks: PlaybackTrackChoices, controller: AppController, onSeek: (Long) -> Unit, onMenu: (PlayerTrackMenu) -> Unit) {
    val audio = serverTracks.audio.any { it.selectable && it.supported }
    val captions = serverTracks.subtitlesSupported
    Box(Modifier.fillMaxSize()) {
        if (!isLive) {
            if (canSeek) PlayerIconButton("Rewind 60 seconds", Modifier.offset(64.dp, 624.dp).size(64.dp), { onSeek(-60_000) }) { PlayerGlyph(Icons.Default.FastRewind, "Rewind 60 seconds", it) }
            PlayerIconButton(if (isPlaying) "Pause" else "Play", Modifier.offset(144.dp, 624.dp).size(64.dp), { if (isPlaying) controller.pausePlayback() else controller.resumePlayback() }) { PlayerGlyph(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play", it) }
            if (canSeek) PlayerIconButton("Forward 60 seconds", Modifier.offset(224.dp, 624.dp).size(64.dp), { onSeek(60_000) }) { PlayerGlyph(Icons.Default.FastForward, "Forward 60 seconds", it) }
            if (media.type == "series") PlayerIconButton("Next episode", Modifier.offset(304.dp, 624.dp).size(64.dp), { controller.nextEpisode(media) }) { PlayerGlyph(Icons.Default.SkipNext, "Next episode", it) }
        }
        if (audio) PlayerIconButton("Audio", Modifier.offset(if (isLive) 608.dp else 992.dp, 624.dp).size(64.dp), { onMenu(PlayerTrackMenu.Audio) }) { PlayerGlyph(Icons.Default.VolumeUp, "Audio", it) }
        if (captions) PlayerIconButton("Captions", Modifier.offset(if (isLive) 688.dp else 1072.dp, 624.dp).size(64.dp), { onMenu(PlayerTrackMenu.Subtitles) }) { PlayerGlyph(Icons.Default.ClosedCaption, "Captions", it) }
        // Back owns final progress persistence and session stop as one ordered
        // transition. Calling saveProgress here races a second write with stop.
        PlayerIconButton("Exit", Modifier.offset(1152.dp, 624.dp).size(64.dp), { controller.back() }) { PlayerGlyph(Icons.Default.Close, "Exit", it) }
    }
}

@Composable
private fun PlayerGlyph(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, focused: Boolean) = Icon(icon, contentDescription = description, tint = if (focused) Color(0xFF101112) else Color(0xFFF5F5F5), modifier = Modifier.size(28.dp))

@Composable
private fun PlayerIconButton(label: String, modifier: Modifier, onActivate: () -> Unit, onFocused: (Boolean) -> Unit = {}, content: @Composable BoxScope.(Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.onFocusChanged { focused = it.hasFocus; onFocused(it.hasFocus) }
            // The timeline thumb extends five pixels above its track. Do not clip
            // the button bounds or the unfocused thumb becomes a half-circle.
            .background(if (focused) Color(0xFFF5F5F5) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onActivate),
        contentAlignment = Alignment.Center,
    ) { content(focused) }
}

@Composable
private fun PlayerTrackDialog(
    menu: PlayerTrackMenu,
    tracks: List<PlaybackTrack>,
    canDisable: Boolean,
    notice: String?,
    onAudio: (PlaybackTrack) -> Unit,
    onText: (PlaybackTrack?) -> Unit,
    onUnavailable: () -> Unit,
    onClose: () -> Unit,
) {
    var page by remember(menu) { mutableIntStateOf(0) }
    val pageCount = max(1, (tracks.size + TRACKS_PER_PAGE - 1) / TRACKS_PER_PAGE)
    LaunchedEffect(pageCount) { if (page >= pageCount) page = pageCount - 1 }
    val visiblePage = page.coerceIn(0, pageCount - 1)
    val visibleTracks = tracks.drop(visiblePage * TRACKS_PER_PAGE).take(TRACKS_PER_PAGE)
    val entries = buildList<PlayerMenuEntry> {
        if (canDisable) add(PlayerMenuEntry("Off") { onText(null) })
        visibleTracks.forEach { track ->
            val title = track.title.ifBlank { track.language ?: "Track ${track.inputIndex + 1}" }
            add(PlayerMenuEntry(if (track.selectable && track.supported) title else "$title · unavailable") {
                if (!track.selectable || !track.supported) onUnavailable()
                else if (menu == PlayerTrackMenu.Audio) onAudio(track) else onText(track)
            })
        }
        if (visiblePage > 0) add(PlayerMenuEntry("Previous tracks") { page = visiblePage - 1 })
        if (visiblePage + 1 < pageCount) add(PlayerMenuEntry("More tracks") { page = visiblePage + 1 })
        // Every dialog has this entry, including an audio output with no tracks.
        add(PlayerMenuEntry("Back to player", onClose))
    }
    val requesters = remember(menu, visiblePage, canDisable, tracks) { List(entries.size) { FocusRequester() } }
    LaunchedEffect(requesters) { requesters.first().requestFocus() }
    Box(Modifier.fillMaxSize().background(Color(0xDC080909)), contentAlignment = Alignment.Center) {
        // The list begins at y=96 and can show five tracks plus paging/back rows.
        // Keep a fixed panel surface instead of letting the background end at its title.
        Box(Modifier.width(880.dp).height(640.dp).background(Color(0xFF191B1D), RoundedCornerShape(12.dp)).offset(y = (-8).dp)) {
            Text(if (menu == PlayerTrackMenu.Audio) "Audio tracks" else "Subtitles", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(44.dp, 32.dp))
            androidx.compose.foundation.layout.Column(Modifier.offset(44.dp, 96.dp).width(792.dp)) {
                entries.forEachIndexed { index, entry ->
                    PlayerMenuChoice(
                        entry.label,
                        Modifier.focusRequester(requesters[index]),
                        onActivate = entry.onActivate,
                        onDirectional = { keyCode ->
                            when (keyCode) {
                                NativeKeyEvent.KEYCODE_DPAD_UP -> if (index > 0) requesters[index - 1].requestFocus()
                                NativeKeyEvent.KEYCODE_DPAD_DOWN -> if (index + 1 < requesters.size) requesters[index + 1].requestFocus()
                            }
                        },
                    )
                }
                notice?.let { Text(it, color = Color(0xFFD5D6D7), fontSize = 16.sp, modifier = Modifier.offset(y = 12.dp)) }
            }
        }
    }
}

private const val TRACKS_PER_PAGE = 5
private data class PlayerMenuEntry(val label: String, val onActivate: () -> Unit)

@Composable
private fun PlayerMenuChoice(
    label: String,
    modifier: Modifier = Modifier,
    onActivate: () -> Unit,
    onDirectional: (Int) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.width(792.dp).height(52.dp).clip(RoundedCornerShape(10.dp)).background(if (focused) Color.White else Color.Transparent)
            .onFocusChanged { focused = it.hasFocus }
            // Consume all directional events at this dialog boundary. Up/Down move
            // among its entries; Left/Right intentionally have no player action.
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                if (event.nativeKeyEvent.action == NativeKeyEvent.ACTION_DOWN && code in setOf(
                        NativeKeyEvent.KEYCODE_DPAD_UP,
                        NativeKeyEvent.KEYCODE_DPAD_DOWN,
                        NativeKeyEvent.KEYCODE_DPAD_LEFT,
                        NativeKeyEvent.KEYCODE_DPAD_RIGHT,
                    )
                ) {
                    onDirectional(code)
                    true
                } else false
            }
            .clickable(onClick = onActivate),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, color = if (focused) Color(0xFF101112) else Color.White, fontSize = 20.sp, modifier = Modifier.offset(x = 18.dp))
    }
}

private fun formatTime(millis: Long): String {
    val total = max(0, millis / 1_000)
    val hours = total / 3_600
    val minutes = (total % 3_600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun playbackStatusLabel(status: PlaybackStatus, isPlaying: Boolean, isBuffering: Boolean, live: Boolean): String = when (status) {
    PlaybackStatus.Opening -> "LOADING"
    PlaybackStatus.Ready -> if (isBuffering) "LOADING" else if (live) "● LIVE" else if (isPlaying) "PLAYING" else "PAUSED"
    PlaybackStatus.Ended -> "ENDED"
    PlaybackStatus.Error -> "ERROR"
    PlaybackStatus.Released -> "STOPPED"
    PlaybackStatus.Idle -> "READY"
}

private fun playerContext(media: Media, seek: SeekPreview?): String = buildString {
    media.season?.let { append("S$it") }
    media.episode?.let { if (isNotEmpty()) append(" · "); append("E$it") }
    if (seek != null) { if (isNotEmpty()) append(" · "); append("Seek ${formatTime(seek.targetMillis)}") }
}
