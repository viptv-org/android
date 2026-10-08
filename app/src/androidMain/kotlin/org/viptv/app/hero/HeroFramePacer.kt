package org.viptv.app.hero

/** Caps decoration work independently of a TV's 30/60/120 Hz display clock. */
internal class HeroFramePacer {
    private var lastFrame = Long.MIN_VALUE

    fun reset() { lastFrame = Long.MIN_VALUE }

    fun shouldRender(frameTimeNanos: Long, changing: Boolean): Boolean {
        // Allow a little vsync timestamp jitter without dropping a whole display frame.
        val interval = if (changing) 16_000_000L else 66_000_000L
        if (lastFrame != Long.MIN_VALUE && frameTimeNanos - lastFrame < interval) return false
        lastFrame = frameTimeNanos
        return true
    }
}
