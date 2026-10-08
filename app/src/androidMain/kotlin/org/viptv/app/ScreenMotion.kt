package org.viptv.app

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** How a newly shown screen arrives. Only the entering screen animates; the previous one is already gone. */
internal enum class ScreenMotion { None, Forward, Back, Next, Previous, Fade }

/**
 * Drilling in (Home → Details → Sources) settles from slightly larger, Back from slightly
 * smaller, and moving between rail destinations travels along the rail's order. Playback
 * fades. Changes within one destination (a Guide channel, the same tab) do not animate.
 */
internal fun screenMotion(from: Route?, to: Route): ScreenMotion {
    if (from == null || from == to) return ScreenMotion.None
    if (from is Route.Player || to is Route.Player) return ScreenMotion.Fade
    val a = railOrder(from)
    val b = railOrder(to)
    if (a != null && b != null) return when {
        b > a -> ScreenMotion.Next
        b < a -> ScreenMotion.Previous
        else -> ScreenMotion.None
    }
    val da = depth(from)
    val db = depth(to)
    return when {
        db > da -> ScreenMotion.Forward
        db < da -> ScreenMotion.Back
        else -> ScreenMotion.Fade
    }
}

/** Top-to-bottom order of the TV rail (left-to-right on the phone bar). */
private fun railOrder(route: Route): Int? = when (route) {
    Route.Search -> 0
    is Route.Browse -> when (route.destination) {
        Destination.Search -> 0
        Destination.Home -> 1
        Destination.Discover -> 2
        Destination.Live -> 3
        Destination.MyList -> 4
        Destination.Settings -> 5
        Destination.Profile -> null
    }
    is Route.Guide -> 3
    Route.Settings -> 5
    else -> null
}

private fun depth(route: Route): Int = when (route) {
    is Route.Details, Route.Addons, is Route.ProfileEditor -> 1
    is Route.Sources -> 2
    else -> 0
}

/** Whether the system allows animation; developer options and accessibility can turn it off. */
internal fun systemAnimationsEnabled(context: Context): Boolean {
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale > 0f && (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())
}

/** The entering screen's motion and its 0–1 progress. */
internal class ScreenEntrance(val motion: ScreenMotion, val progress: Animatable<Float, *>)

/**
 * Tracks route changes for the shell. Call it outside the per-screen state holder, whose
 * content restarts for every screen key and would forget the previous route.
 */
@Composable internal fun rememberScreenEntrance(route: Route, key: Any): ScreenEntrance {
    val context = LocalContext.current
    val enabled = remember(context) { systemAnimationsEnabled(context) }
    val last = remember { arrayOfNulls<Route>(1) }
    val entrance = remember(key) {
        val motion = screenMotion(last[0], route).also { last[0] = route }
        ScreenEntrance(motion, Animatable(if (enabled && motion != ScreenMotion.None) 0f else 1f))
    }
    LaunchedEffect(entrance) {
        if (entrance.progress.value < 1f) {
            entrance.progress.animateTo(1f, tween(if (entrance.motion == ScreenMotion.Fade) 220 else 280, easing = LinearEasing))
        }
    }
    return entrance
}

/**
 * Short enough never to delay input: focus moves immediately and the content is fully
 * opaque within the first ~170 ms.
 */
@Composable internal fun Modifier.screenEntrance(entrance: ScreenEntrance, vertical: Boolean): Modifier {
    val travel = with(LocalDensity.current) { 40.dp.toPx() }
    return graphicsLayer {
        val p = entrance.progress.value
        if (p >= 1f) return@graphicsLayer
        val e = EaseOutCubic.transform(p)
        alpha = (p / 0.6f).coerceAtMost(1f)
        when (entrance.motion) {
            ScreenMotion.Forward -> { scaleX = 1.035f - 0.035f * e; scaleY = scaleX }
            ScreenMotion.Back -> { scaleX = 0.97f + 0.03f * e; scaleY = scaleX }
            ScreenMotion.Next -> if (vertical) translationY = travel * (1f - e) else translationX = travel * (1f - e)
            ScreenMotion.Previous -> if (vertical) translationY = -travel * (1f - e) else translationX = -travel * (1f - e)
            ScreenMotion.Fade, ScreenMotion.None -> Unit
        }
    }
}
