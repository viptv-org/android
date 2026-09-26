package org.viptv.app

import androidx.activity.compose.LocalActivity
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import android.view.SurfaceView
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.viptv.video.PlaybackStatus
import org.viptv.app.theme.ViptvColor as C

private enum class TrackMenu { Audio, Subtitles }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable internal fun PlaybackScreen(media: Media, chromeVisible: Boolean, seekPreview: SeekPreview?, serverTracks: PlaybackTrackChoices, controller: AppController) {
    val tv = LocalTv.current
    val playback by controller.player.state.collectAsState()
    val videoTracks by controller.player.videoTracks.collectAsState()
    val app by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val live = media.type == "live"
    val position = controller.absolutePositionMillis()
    val duration = controller.titleDurationMillis() ?: playback.timeline?.durationMillis ?: 0
    val hasNext = media.seriesId != null && media.season != null && !live
    val order = if (live) listOf(3, 4, 5) else if (hasNext) listOf(0, 1, 2, 6, 3, 4, 5) else listOf(0, 1, 2, 3, 4, 5)
    var row by remember(media.id) { mutableIntStateOf(1) }
    var button by remember(media.id) { mutableIntStateOf(if (live) 3 else 1) }
    var seekKey by remember { mutableIntStateOf(0) }
    var repeat by remember { mutableIntStateOf(0) }
    var activateOnRelease by remember { mutableStateOf(false) }
    var seekJob by remember { mutableStateOf<Job?>(null) }
    var menu by remember { mutableStateOf<TrackMenu?>(null) }
    var info by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val buffering = playback.status == PlaybackStatus.Opening || playback.isBuffering
    val shown = chromeVisible || buffering
    fun cancelSeek() { seekJob?.cancel(); controller.cancelSeek(); seekKey = 0; repeat = 0 }
    fun seek(delta: Long, key: Int) {
        if (live || duration <= 0) return
        seekJob?.cancel()
        repeat = if (seekKey == key) repeat + 1 else 0; seekKey = key
        val multiplier = when { repeat >= 15 -> 60; repeat >= 9 -> 15; repeat >= 5 -> 6; repeat >= 2 -> 3; else -> 1 }
        controller.previewSeek(delta * multiplier); row = 0
    }
    fun releaseSeek() { seekKey = 0; repeat = 0; seekJob?.cancel(); seekJob = scope.launch { delay(800); if (controller.state.value.seekPreview != null) controller.commitSeek() } }
    fun toggle() { cancelSeek(); if (!live) { if (playback.isPlaying) controller.pausePlayback() else controller.resumePlayback() } }
    fun activate(index: Int) {
        when (index) {
            0 -> { seek(-10_000, KeyEvent.KEYCODE_DPAD_CENTER); releaseSeek() }
            1 -> toggle()
            2 -> { seek(30_000, KeyEvent.KEYCODE_DPAD_CENTER); releaseSeek() }
            3 -> { cancelSeek(); menu = TrackMenu.Audio }
            4 -> { cancelSeek(); menu = TrackMenu.Subtitles }
            5 -> controller.exitPlayback()
            6 -> controller.nextEpisode(media)
        }
    }
    LaunchedEffect(media.id, position, duration, playback.isPlaying, playback.status) { controller.maybeAutoNext(media, position, duration.takeIf { it > 0 }, playback.isPlaying, playback.status == PlaybackStatus.Ended) }
    LaunchedEffect(menu, info) {
        controller.setPlayerMenuOpen(menu != null || info)
        if (tv && menu == null && !info) { withFrameNanos {}; runCatching { focus.requestFocus() } }
    }
    DisposableEffect(controller) {
        onDispose {
            seekJob?.cancel(); controller.setPlayerMenuOpen(false)
            if (!tv && activity?.isChangingConfigurations == false) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
        if (!tv || menu != null || info) return@onPreviewKeyEvent false
        val key = event.nativeKeyEvent; val code = key.keyCode
        if (code == KeyEvent.KEYCODE_BACK) { seekJob?.cancel(); return@onPreviewKeyEvent false }
        if (key.action == KeyEvent.ACTION_UP) {
            if (code == seekKey) releaseSeek()
            if ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) && activateOnRelease) {
                activateOnRelease = false
                if (seekPreview != null && row == 0) { seekJob?.cancel(); controller.commitSeek() }
                else if (shown) { if (row == 0) toggle() else activate(button) }
            }
            return@onPreviewKeyEvent true
        }
        if (key.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
        controller.showPlayerChrome()
        when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> if (key.repeatCount == 0) toggle()
            KeyEvent.KEYCODE_MEDIA_PLAY -> if (!live && key.repeatCount == 0) controller.resumePlayback()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> if (!live && key.repeatCount == 0) controller.pausePlayback()
            KeyEvent.KEYCODE_DPAD_DOWN -> { cancelSeek(); row = 1 }
            KeyEvent.KEYCODE_DPAD_UP -> if (!live) row = 0
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val left = code == KeyEvent.KEYCODE_DPAD_LEFT
                if (row == 1 || live) {
                    val index = order.indexOf(button).coerceAtLeast(0)
                    button = order[(index + if (left) order.size - 1 else 1) % order.size]
                } else seek(if (left) -10_000 else 10_000, code)
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> seek(-60_000, code)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek(60_000, code)
            KeyEvent.KEYCODE_MEDIA_NEXT -> if (hasNext && key.repeatCount == 0) controller.nextEpisode(media)
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU -> { cancelSeek(); row = 1; button = 3 }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (key.repeatCount == 0) activateOnRelease = true
            else -> return@onPreviewKeyEvent false
        }; true
    }.then(if (tv) Modifier.focusRequester(focus).focusable() else Modifier)) {
        val portrait = !tv && maxHeight > maxWidth
        LaunchedEffect(portrait) {
            if (!tv && activity != null) WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (portrait) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        val track = videoTracks.firstOrNull { it.id == playback.selectedVideoTrackId } ?: videoTracks.firstOrNull()
        val aspect = if ((track?.width ?: 0) > 0 && (track?.height ?: 0) > 0) track!!.width!!.toFloat() / track.height!! else 16f / 9
        val videoModifier = Modifier.align(Alignment.Center).then(
            if (maxWidth / maxHeight > aspect) Modifier.fillMaxHeight().aspectRatio(aspect)
            else Modifier.fillMaxWidth().aspectRatio(aspect))
        AndroidView(factory = { SurfaceView(it).also { view -> view.keepScreenOn = true; controller.player.attach(view) } }, modifier = videoModifier)
        Box(Modifier.matchParentSize().pointerInput(chromeVisible) { detectTapGestures {
            if (chromeVisible) controller.hidePlayerChrome() else controller.showPlayerChrome()
        } })
        if (shown) {
            if (!portrait) Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .6f), Color.Transparent, Color.Black.copy(alpha = .85f)))))
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().then(if (tv) Modifier.padding(96.dp, 76.dp) else Modifier.statusBarsPadding().padding(16.dp)), verticalAlignment = Alignment.CenterVertically) {
                if (!tv) { AppIconButton("back", "Back", controller::exitPlayback, Modifier.size(44.dp)); Spacer(Modifier.width(12.dp)) }
                Column(Modifier.weight(1f)) {
                    VText(media.name, if (tv) 24 else 17, bold = true, lines = 1)
                    if (!tv && media.season != null) VText("S" + media.season + " · E" + media.episode + " · " + media.episodeTitle.orEmpty(), 13, color = C.textSecondary, lines = 1)
                }
                VText(if (live) "● LIVE" else if (buffering) "BUFFERING" else if (playback.isPlaying) "PLAYING" else "PAUSED", if (tv) 20 else 11, color = if (live) C.statusLive else C.textPrimary, bold = true)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(if (tv) Modifier.padding(horizontal = 96.dp, vertical = 96.dp) else Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp))) {
                if (tv) {
                    VText(if (live) "LIVE TV" else "NOW PLAYING", 20, color = C.textSecondary, bold = true)
                    if (media.season != null) VText("S" + media.season + " · E" + media.episode + " · " + media.episodeTitle.orEmpty(), 26, Modifier.padding(top = 18.dp), lines = 1)
                    VText(media.name, 56, Modifier.padding(top = 18.dp, bottom = 32.dp), display = true, lines = 1)
                }
                if (!live) {
                    val displayPosition = seekPreview?.targetMillis ?: position
                    if (tv) {
                        ProgressLine(if (duration > 0) displayPosition.toFloat() / duration else 0f, Modifier.padding(vertical = 14.dp))
                        if (row == 0) VText(if (seekPreview != null) "Seeking to " + formatTime(displayPosition) else "Use left or right to seek", 20, color = C.textSecondary)
                    } else Slider(
                        value = if (duration > 0) (displayPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { value -> controller.previewSeek((duration * value).toLong() - (controller.state.value.seekPreview?.targetMillis ?: controller.absolutePositionMillis())) },
                        onValueChangeFinished = { controller.commitSeek() },
                        enabled = duration > 0 && playback.timeline?.canSeek == true,
                        modifier = Modifier.height(32.dp),
                        thumb = { Box(Modifier.size(12.dp).clip(CircleShape).background(C.textPrimary)) },
                        track = { ProgressLine(if (duration > 0) displayPosition.toFloat() / duration else 0f) },
                        colors = SliderDefaults.colors(thumbColor = C.textPrimary, activeTrackColor = LocalAccent.current, inactiveTrackColor = C.lineStrong))
                    Row(Modifier.fillMaxWidth().padding(bottom = measure(32, 20)), horizontalArrangement = Arrangement.SpaceBetween) {
                        VText(formatTime(displayPosition), if (tv) 22 else 13, bold = true)
                        VText(formatTime(duration), if (tv) 22 else 13, color = C.textSecondary)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (tv) Arrangement.spacedBy(18.dp) else Arrangement.SpaceEvenly) {
                    if (!live) {
                        PlayerControl("back10", "Back 10 seconds", tv && row == 1 && button == 0, { activate(0) })
                        PlayerControl(if (playback.isPlaying) "pause" else "play", if (playback.isPlaying) "Pause" else "Play", tv && row == 1 && button == 1, { activate(1) }, primary = !tv)
                        PlayerControl("forward30", "Forward 30 seconds", tv && row == 1 && button == 2, { activate(2) })
                        if (hasNext) PlayerControl("next", "Next episode", tv && row == 1 && button == 6, { activate(6) })
                    }
                    if (tv) Spacer(Modifier.weight(1f))
                    if (tv || live) {
                        PlayerControl("audio", "Audio", tv && row == 1 && button == 3, { activate(3) })
                        PlayerControl("captions", "Subtitles", tv && row == 1 && button == 4, { activate(4) })
                        PlayerControl("exit", "Exit player", tv && row == 1 && button == 5, { activate(5) })
                    }
                }
                if (!tv && !live) Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AppIconButton("audio", "Audio", { activate(3) }, Modifier.size(44.dp))
                    AppIconButton("captions", "Subtitles", { activate(4) }, Modifier.size(44.dp))
                    AppIconButton("info", "Playback info", { info = true }, Modifier.size(44.dp))
                    AppIconButton("expand", "Fullscreen", { activity?.requestedOrientation = if (portrait) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT }, Modifier.size(44.dp))
                }
            }
        }
        if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center).size(measure(64, 44)), color = LocalAccent.current)
        menu?.let { kind -> TrackPanel(kind, if (kind == TrackMenu.Audio) serverTracks.audio else serverTracks.subtitles, kind == TrackMenu.Subtitles && serverTracks.subtitlesSupported,
            onChoose = { track -> if (kind == TrackMenu.Audio && track != null) controller.selectAudioTrack(track) else controller.selectSubtitleTrack(track); menu = null },
            onClose = { menu = null }) }
        if (info) FullInfo("Playback info", "Decoder · Media3\nDelivery · " + app.playbackDeliveryMode + "\nMedia · " + (playback.timeline?.kind?.name ?: "Unknown"), { info = false })
    }
}

@Composable private fun PlayerControl(icon: String, label: String, focused: Boolean, onClick: () -> Unit, primary: Boolean = false) {
    val tv = LocalTv.current
    Box(Modifier.size(measure(72, if (primary) 54 else 44)).clip(CircleShape)
        .background(if (focused) C.textPrimary else if (primary) LocalAccent.current else C.surfaceN3)
        .clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        VIcon(icon, modifier = Modifier.size(measure(32, 24)), color = if (focused || primary) C.onLight else C.textPrimary)
    }
}

@Composable private fun TrackPanel(kind: TrackMenu, tracks: List<PlaybackTrack>, off: Boolean, onChoose: (PlaybackTrack?) -> Unit, onClose: () -> Unit) {
    val tv = LocalTv.current
    val entries = if (off) listOf<PlaybackTrack?>(null) + tracks else tracks
    val selected = entries.indexOfFirst { it?.selected == true }.coerceAtLeast(0)
    val list = rememberLazyListState()
    val focus = remember { FocusRequester() }
    var notice by remember { mutableStateOf<String?>(null) }
    AppOverlay(if (kind == TrackMenu.Audio) "Audio" else "Subtitles", onClose) {
        if (entries.isEmpty()) EmptyState("No selectable tracks", "This stream supplies no available tracks.", if (kind == TrackMenu.Audio) "audio" else "captions")
        else LazyColumn(state = list, modifier = Modifier.heightIn(max = measure(800, 480)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(entries) { index, track ->
                val unavailable = track != null && (!track.selectable || !track.supported)
                val label = track?.title?.ifBlank { track.language ?: "Track " + (track.inputIndex + 1) } ?: "Off"
                AppButton((if (track?.selected == true) "✓ " else "") + label + if (unavailable) " · Unavailable" else "", {
                    if (unavailable) notice = "This track is not supported on this device." else onChoose(track)
                }, Modifier.fillMaxWidth().then(if (index == selected && tv) Modifier.focusRequester(focus) else Modifier), selected = track?.selected == true)
            }
        }
        notice?.let { VText(it, if (tv) 22 else 14, Modifier.padding(top = 20.dp), C.textSecondary) }
        LaunchedEffect(kind) { if (tv && entries.isNotEmpty()) { list.scrollToItem(selected); withFrameNanos {}; runCatching { focus.requestFocus() } } }
    }
}
