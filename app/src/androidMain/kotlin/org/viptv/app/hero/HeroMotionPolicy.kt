package org.viptv.app.hero

import kotlin.random.Random

/**
 * Chooses the transition and edge fade for each hero change. Edges come from
 * the title's category pool; transitions from every effect except the plain
 * crossfade. Shuffle bags play each pool member once before repeating and never
 * pick the style already on screen when another is available.
 */
internal class HeroMotionPolicy(private val index: HeroShaderIndex, private val random: Random = Random.Default) {
    private val edgeIds = index.edges.map { it.id }.toSet()
    private val transitions = index.transitions.filter { it.id != BASELINE_TRANSITION }.ifEmpty { index.transitions }
    private val bags = mutableMapOf<String, ArrayDeque<String>>()

    /** Animated series read as anime; otherwise the first genre that has a pool. */
    fun category(type: String, genres: List<String>): String? {
        if (genres.any { it.equals("Animation", ignoreCase = true) || it.equals("Anime", ignoreCase = true) }) {
            return if (type == "series") "Anime" else "Animation"
        }
        return genres.firstOrNull { pool(it).isNotEmpty() }
    }

    fun pool(category: String?): List<String> = category?.let(index.genreEdges::get).orEmpty().filter { it in edgeIds }

    fun nextEdge(category: String?, current: String?): String {
        val pool = pool(category).ifEmpty { index.edges.map { it.id }.filter { it != BASELINE_EDGE } }
        return draw(category ?: "*", pool, current)
    }

    fun nextTransition(current: String?): HeroTransitionSpec {
        val id = draw(TRANSITION_BAG, transitions.map { it.id }, current)
        return transitions.first { it.id == id }
    }

    private fun draw(key: String, pool: List<String>, current: String?): String {
        val bag = bags.getOrPut(key) { ArrayDeque() }
        if (bag.isEmpty()) bag.addAll(pool.filter { it != current }.ifEmpty { pool }.shuffled(random))
        var pick = bag.removeAt(bag.lastIndex)
        if (pick == current && bag.isNotEmpty()) {
            val swap = bag.removeAt(bag.lastIndex)
            bag.addFirst(pick)
            pick = swap
        }
        return pick
    }

    companion object {
        /** The design's linear scrim, kept for comparison rather than rotation. */
        const val BASELINE_EDGE = "linear"
        const val BASELINE_TRANSITION = "fade"
        private const val TRANSITION_BAG = "\u0000transitions"
    }
}
