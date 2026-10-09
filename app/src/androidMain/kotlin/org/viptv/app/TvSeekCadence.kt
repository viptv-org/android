package org.viptv.app

/** Native key-repeat cadence; Core still clamps and accumulates the seek target. */
internal class TvSeekCadence {
    private var activeKey: Int? = null
    private var lastStepMillis = 0L
    fun step(deltaMillis: Long, key: Int, repeatCount: Int, eventMillis: Long): Long? {
        if (repeatCount == 0) {
            activeKey = key
            lastStepMillis = eventMillis
            return deltaMillis
        }
        if (activeKey != key || eventMillis - lastStepMillis < 250) return null
        lastStepMillis = eventMillis
        return if (deltaMillis < 0) -5_000L else 5_000L
    }
    fun end() { activeKey = null }
}
