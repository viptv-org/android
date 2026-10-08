package org.viptv.app.hero

import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HeroMotionPolicyTest {
    private val assets = File("src/androidMain/assets")
    private val library = HeroShaderLibrary { path -> File(assets, path).readText() }

    @Test fun everyIndexedShaderAssemblesWithAnEntryPoint() {
        val index = library.index
        assertTrue(index.transitions.isNotEmpty() && index.edges.isNotEmpty())
        index.transitions.forEach { assertTrue("vec4 transition(" in library.transitionSource(it.id), it.id) }
        index.edges.forEach { assertTrue("vec3 edge(" in library.edgeSource(it.id) && "void main()" in library.edgeSource(it.id), it.id) }
        assertTrue("ambient(" in library.ambientSource())
    }

    @Test fun genrePoolsOnlyNameKnownEdges() {
        val ids = library.index.edges.map { it.id }.toSet()
        library.index.genreEdges.forEach { (genre, pool) -> assertTrue(pool.isNotEmpty() && pool.all { it in ids }, genre) }
    }

    @Test fun animatedSeriesAreAnimeAndOtherTitlesUseTheirFirstPooledGenre() {
        val policy = HeroMotionPolicy(library.index)
        assertEquals("Anime", policy.category("series", listOf("Action", "Animation")))
        assertEquals("Animation", policy.category("movie", listOf("Animation", "Comedy")))
        assertEquals("Horror", policy.category("movie", listOf("Unlisted", "Horror", "Sci-Fi")))
        assertEquals(null, policy.category("movie", listOf("Unlisted")))
    }

    @Test fun categoryBagPlaysEveryPoolEdgeBeforeRepeatingAndNeverRepeatsTheCurrentEdge() {
        val policy = HeroMotionPolicy(library.index, Random(7))
        val pool = policy.pool("Horror")
        var current: String? = null
        val firstRound = (pool.indices).map { policy.nextEdge("Horror", current).also { pick -> assertNotEquals(current, pick); current = pick } }
        assertEquals(pool.toSet(), firstRound.toSet())
        repeat(30) { policy.nextEdge("Horror", current).also { pick -> assertNotEquals(current, pick); assertTrue(pick in pool); current = pick } }
    }

    @Test fun uncategorisedTitlesShuffleEveryEdgeExceptTheBaseline() {
        val policy = HeroMotionPolicy(library.index, Random(3))
        val picks = (0 until library.index.edges.size * 2).map { policy.nextEdge(null, null) }.toSet()
        assertTrue(HeroMotionPolicy.BASELINE_EDGE !in picks)
        assertEquals(library.index.edges.size - 1, picks.size)
    }

    @Test fun transitionsSkipThePlainCrossfade() {
        val policy = HeroMotionPolicy(library.index, Random(11))
        var current: String? = null
        repeat(40) { policy.nextTransition(current).also { assertNotEquals(HeroMotionPolicy.BASELINE_TRANSITION, it.id); assertNotEquals(current, it.id); current = it.id } }
    }
}
