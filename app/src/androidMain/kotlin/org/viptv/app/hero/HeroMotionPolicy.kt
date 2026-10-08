package org.viptv.app.hero

import org.viptv.core.wire.HeroEdgePool
import kotlin.random.Random

/**
 * Draws the transition and edge fade for each hero change (TV-042). Core's
 * `heroEdgePool` decides the title's category and edge pool; this class only
 * shuffles. Transitions use one bag of every catalog effect except the plain
 * crossfade; edges use one bag per category. A bag plays each member once before
 * refilling and never returns the style on screen while another member exists.
 */
internal class HeroMotionPolicy(index: HeroShaderIndex, private val random: Random = Random.Default) {
    /** Edge ids this renderer ships, passed to core as `availableEdges`. */
    val edgeIds: List<String> = index.edges.map { it.id }
    private val transitions = index.transitions.filter { it.id != BASELINE_TRANSITION }.ifEmpty { index.transitions }
    private val bags = mutableMapOf<String, ArrayDeque<String>>()

    /** An empty pool (no shipped edge qualifies) shows the [BASELINE_EDGE] ramp. */
    fun nextEdge(pool: HeroEdgePool, current: String?): String =
        if (pool.edges.isEmpty()) BASELINE_EDGE else draw(pool.category ?: UNCATEGORISED_BAG, pool.edges, current)

    fun nextTransition(current: String?): HeroTransitionSpec {
        val id = draw(TRANSITION_BAG, transitions.map { it.id }, current)
        return transitions.first { it.id == id }
    }

    private fun draw(key: String, pool: List<String>, current: String?): String {
        val bag = bags.getOrPut(key) { ArrayDeque() }
        if (bag.isEmpty()) bag.addAll(pool.filter { it != current }.ifEmpty { pool }.shuffled(random))
        var pick = bag.removeAt(bag.lastIndex)
        if (pick == current) {
            // The style on screen can come from another category's bag; start the next round instead of repeating it.
            if (bag.isEmpty()) bag.addAll(pool.filter { it != current }.shuffled(random))
            if (bag.isNotEmpty()) {
                val swap = bag.removeAt(bag.lastIndex)
                bag.addFirst(pick)
                pick = swap
            }
        }
        return pick
    }

    companion object {
        /** The design's linear scrim: a fallback, never drawn into rotation. */
        const val BASELINE_EDGE = "linear"
        const val BASELINE_TRANSITION = "fade"
        private const val UNCATEGORISED_BAG = "*"
        private const val TRANSITION_BAG = "\u0000transitions"
    }
}
