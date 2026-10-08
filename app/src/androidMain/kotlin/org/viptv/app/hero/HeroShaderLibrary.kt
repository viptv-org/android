package org.viptv.app.hero

import android.content.res.AssetManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable internal data class HeroShaderIndex(
    val transitions: List<HeroTransitionSpec>,
    val edges: List<HeroEdgeSpec>,
    /** Category → edge ids suited to it. Ids absent from [edges] are ignored. */
    val genreEdges: Map<String, List<String>>,
)

@Serializable internal data class HeroTransitionSpec(val id: String, val name: String, val duration: Double)

@Serializable internal data class HeroEdgeSpec(val id: String, val name: String)

/**
 * GLSL ES 1.00 sources for the TV Home backdrop, stored under `assets/hero/`.
 * A transition program is `transition_common` + `transitions/<id>` + [TRANSITION_MAIN];
 * an edge program is `edge_common` + `edges/<id>` + `edge_main`; the ambient
 * program is `edge_common` + `ambient_main`.
 */
internal class HeroShaderLibrary(private val read: (String) -> String) {
    val index: HeroShaderIndex = json.decodeFromString(read("hero/index.json"))
    private val transitionCommon by lazy { read("hero/transition_common.glsl") }
    private val edgeCommon by lazy { read("hero/edge_common.glsl") }
    private val edgeMain by lazy { read("hero/edge_main.glsl") }

    fun transitionSource(id: String) = "$transitionCommon\n${read("hero/transitions/$id.glsl")}\n$TRANSITION_MAIN"
    fun edgeSource(id: String) = "$edgeCommon\n${read("hero/edges/$id.glsl")}\n$edgeMain"
    fun ambientSource() = "$edgeCommon\n${read("hero/ambient_main.glsl")}"

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        const val VERTEX = "attribute vec2 aPos;\nvarying vec2 vUv;\n" +
            "void main() { vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }"
        const val TRANSITION_MAIN = "void main() { gl_FragColor = vec4(transition(vUv, uProgress).rgb, 1.0); }"

        fun fromAssets(assets: AssetManager) = HeroShaderLibrary { path -> assets.open(path).bufferedReader().use { it.readText() } }
    }
}
