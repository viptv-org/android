package org.viptv.app.hero

import org.viptv.app.SharedPresentation
import org.viptv.core.wire.CoreJson
import org.viptv.core.wire.HeroEdgePool
import org.viptv.core.wire.HeroEdgePoolInput
import uniffi.viptv_core.normalize
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HeroMotionPolicyTest {
    private val assets = File("src/androidMain/assets")
    private val library = HeroShaderLibrary { path -> File(assets, path).readText() }
    private fun pool(type: String, vararg genres: String) =
        SharedPresentation.heroEdgePool(type, genres.toList(), library.index.edges.map { it.id })

    @Test fun everyIndexedShaderAssemblesWithAnEntryPoint() {
        val index = library.index
        assertTrue(index.transitions.isNotEmpty() && index.edges.isNotEmpty())
        index.transitions.forEach { assertTrue("vec4 transition(" in library.transitionSource(it.id), it.id) }
        index.edges.forEach { assertTrue("vec3 edge(" in library.edgeSource(it.id) && "void main()" in library.edgeSource(it.id), it.id) }
        assertTrue("ambient(" in library.ambientSource())
    }

    @Test fun coreChoosesTheCategoryAndPoolFromTheShippedEdges() {
        val ids = library.index.edges.map { it.id }.toSet()
        assertEquals("Anime", pool("series", "Action", "Animation").category)
        assertEquals("Animation", pool("movie", "Animation", "Comedy").category)
        assertEquals("Horror", pool("movie", "Unlisted", "horror", "Sci-Fi").category)
        assertTrue(pool("movie", "Horror").edges.let { it.isNotEmpty() && it.all(ids::contains) && HeroMotionPolicy.BASELINE_EDGE !in it })
        val uncategorised = pool("movie", "Unlisted")
        assertEquals(null, uncategorised.category)
        assertEquals(ids - HeroMotionPolicy.BASELINE_EDGE, uncategorised.edges.toSet())
    }

    @Test fun aTitleWithoutGenresGetsTheUncategorisedPool() {
        val ids = library.index.edges.map { it.id }
        val explicit = pool("series")
        assertEquals(null, explicit.category)
        assertEquals(ids.toSet() - HeroMotionPolicy.BASELINE_EDGE, explicit.edges.toSet())
        // The generated codec omits the empty genres list; core treats the absent list as empty.
        val generated: HeroEdgePool = CoreJson.decode(normalize("heroEdgePool", CoreJson.encode(HeroEdgePoolInput("series", emptyList(), ids)), ""))
        assertEquals(explicit, generated)
        assertTrue(HeroMotionPolicy(library.index, Random(2)).nextEdge(explicit, null) in explicit.edges)
    }

    @Test fun categoryBagPlaysEveryPoolEdgeBeforeRepeatingAndNeverRepeatsTheCurrentEdge() {
        val policy = HeroMotionPolicy(library.index, Random(7))
        val horror = pool("movie", "Horror")
        var current: String? = null
        val firstRound = horror.edges.indices.map { policy.nextEdge(horror, current).also { pick -> assertNotEquals(current, pick); current = pick } }
        assertEquals(horror.edges.toSet(), firstRound.toSet())
        repeat(30) { policy.nextEdge(horror, current).also { pick -> assertNotEquals(current, pick); assertTrue(pick in horror.edges); current = pick } }
    }

    @Test fun anEdgeShownFromAnotherCategoryIsNotRepeatedByTheLastBagMember() {
        val policy = HeroMotionPolicy(library.index, Random(5))
        val pair = HeroEdgePool("Pair", listOf("smoke", "fog"))
        val first = policy.nextEdge(pair, null)
        val remaining = pair.edges.single { it != first }
        assertEquals(first, policy.nextEdge(pair, remaining))
    }

    @Test fun uncategorisedTitlesShuffleEveryEdgeExceptTheBaseline() {
        val policy = HeroMotionPolicy(library.index, Random(3))
        val uncategorised = pool("movie")
        val picks = (0 until library.index.edges.size * 2).map { policy.nextEdge(uncategorised, null) }.toSet()
        assertTrue(HeroMotionPolicy.BASELINE_EDGE !in picks)
        assertEquals(library.index.edges.size - 1, picks.size)
    }

    @Test fun anEmptyPoolShowsTheBaselineEdge() {
        val policy = HeroMotionPolicy(library.index, Random(1))
        val onlyBaseline = SharedPresentation.heroEdgePool("movie", listOf("Horror"), listOf(HeroMotionPolicy.BASELINE_EDGE))
        assertEquals(emptyList(), onlyBaseline.edges)
        assertEquals(HeroMotionPolicy.BASELINE_EDGE, policy.nextEdge(onlyBaseline, null))
        assertEquals(HeroMotionPolicy.BASELINE_EDGE, policy.nextEdge(HeroEdgePool("Horror", emptyList()), HeroMotionPolicy.BASELINE_EDGE))
        assertEquals(HeroMotionPolicy.BASELINE_EDGE, policy.nextEdge(HeroEdgePool(), "fog"))
    }

    @Test fun transitionsSkipThePlainCrossfade() {
        val policy = HeroMotionPolicy(library.index, Random(11))
        var current: String? = null
        repeat(40) { policy.nextTransition(current).also { assertNotEquals(HeroMotionPolicy.BASELINE_TRANSITION, it.id); assertNotEquals(current, it.id); current = it.id } }
    }
}
