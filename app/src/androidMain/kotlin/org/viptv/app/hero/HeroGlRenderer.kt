package org.viptv.app.hero

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.TextureView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws the TV Home backdrop on a [TextureView] from a dedicated GL thread:
 *  1. the active transition (or the resting slide, with a slow Ken Burns
 *     drift) renders into an art-sized scene texture;
 *  2. the ambient pass fills the whole view with the scene, cover-fitted,
 *     blurred through its mip chain and laid at 0.6 over the page ground;
 *  3. the edge pass dissolves the scene into that ambient fill inside the art
 *     rectangle (top-right). While the edge style changes, the incoming style
 *     is blended over the outgoing one through a noise wipe.
 * A change that arrives mid-transition starts at once from the frame on screen,
 * and changes closer together than [BROWSE_MS] use the quick baseline dissolve.
 * Every public method may be called from the main thread.
 */
internal class HeroGlRenderer(
    private val library: HeroShaderLibrary,
    ground: Int,
    private val onUnavailable: () -> Unit,
) : TextureView.SurfaceTextureListener {
    /** Page ground as ARGB; follows the theme (including OLED black). */
    @Volatile var ground: Int = ground
    private val thread = HandlerThread("hero-gl").apply { start() }
    private val handler = Handler(thread.looper)

    // ---- GL-thread state ----
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null
    private var mips = false
    private var viewW = 0
    private var viewH = 0
    private var artW = 0
    private var artH = 0
    private var layoutScale = 1f
    private var quad = 0
    private var fbo = 0
    private var scene = 0
    private val programs = HashMap<String, Program?>()
    private val warmQueue = ArrayDeque<String>()
    private var current: Slide? = null
    private var anim: Anim? = null
    private var pending: Bitmap? = null
    private var edgeA = "cinematic"
    private var edgeB = "cinematic"
    private var edgeStart = 0L
    private var edgeDuration = 0L
    private var running = false
    private var frameCount = 0L
    private val t0 = SystemClock.uptimeMillis()

    /** Edge style currently targeted; read on the main thread to avoid repeats. */
    @Volatile var edge: String = "cinematic"
        private set
    @Volatile var transition: String? = null
        private set

    private class Slide(val tex: Int, val w: Int, val h: Int, val start: Long, val driftX: Float, val driftY: Float)
    private class Anim(val from: Slide, val to: Slide, val spec: HeroTransitionSpec, val start: Long, val duration: Long)
    private class Change(val slide: Slide, val spec: HeroTransitionSpec, val edge: String)
    private class Program(val id: Int, val aPos: Int, val uniforms: Map<String, Int>) {
        operator fun get(name: String) = uniforms[name] ?: -1
    }

    /** Art rectangle size in pixels and the layout's px-per-design-unit scale. */
    fun setArt(width: Int, height: Int, scale: Float) = handler.post {
        if (width == artW && height == artH) return@post
        artW = width; artH = height; layoutScale = scale
        if (surface != EGL14.EGL_NO_SURFACE) allocateScene()
    }

    fun show(bitmap: Bitmap, spec: HeroTransitionSpec, edgeId: String) {
        lastBitmap = bitmap
        edge = edgeId
        transition = spec.id
        handler.post {
            if (surface == EGL14.EGL_NO_SURFACE) {
                pending = bitmap
                return@post
            }
            val slide = upload(bitmap) ?: return@post
            val shown = current
            val now = SystemClock.uptimeMillis()
            val browsing = now - lastChange < BROWSE_MS
            lastChange = now
            if (shown == null) {
                // The first art appears without a transition or an edge wipe.
                current = slide; edgeA = edgeId; edgeB = edgeId; edgeDuration = 0
                return@post
            }
            // Browsing keeps changes calm; the catalog transition plays once focus rests.
            val chosen = if (browsing) library.index.transitions.firstOrNull { it.id == HeroMotionPolicy.BASELINE_TRANSITION } ?: spec else spec
            start(anim?.let(::interrupt) ?: shown, Change(slide, chosen, edgeId))
        }
    }

    private var lastChange = Long.MIN_VALUE / 2

    /** The latest art, re-uploaded when the surface is recreated (e.g. returning from background). */
    @Volatile private var lastBitmap: Bitmap? = null

    fun release() {
        handler.post {
            running = false
            teardown()
            thread.quitSafely()
        }
    }

    // ---- SurfaceTextureListener (main thread) ----
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        handler.post { if (!attach(texture, width, height)) onUnavailableMain() }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        handler.post { viewW = width; viewH = height }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        handler.post { running = false; teardown() }
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    private fun onUnavailableMain() = android.os.Handler(android.os.Looper.getMainLooper()).post(onUnavailable)

    // ---- GL thread ----
    private fun attach(texture: SurfaceTexture, width: Int, height: Int): Boolean = try {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }
        createContext()
        surface = EGL14.eglCreateWindowSurface(display, config, texture, intArrayOf(EGL14.EGL_NONE), 0)
        check(surface != EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent" }
        viewW = width; viewH = height
        setupResources()
        // The latest art and edge return without a transition or wipe.
        (pending ?: lastBitmap)?.let { current = upload(it) }
        pending = null
        edgeA = edge; edgeB = edge; edgeDuration = 0
        // Compile the resting programs now; everything else warms up one per idle frame.
        program("fx:fade"); program("ambient"); program("edge:$edgeA")
        warmQueue.clear()
        library.index.transitions.forEach { warmQueue.add("fx:${it.id}") }
        library.index.edges.forEach { warmQueue.add("edge:${it.id}") }
        running = true
        Choreographer.getInstance().postFrameCallback(frame)
        true
    } catch (e: Throwable) {
        Log.w(TAG, "Hero shader backdrop unavailable", e)
        teardown()
        false
    }

    private fun createContext() {
        for (es3 in listOf(true, false)) {
            val renderable = if (es3) EGLExt.EGL_OPENGL_ES3_BIT_KHR else EGL14.EGL_OPENGL_ES2_BIT
            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, renderable, EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) continue
            val ctx = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, if (es3) 3 else 2, EGL14.EGL_NONE), 0)
            if (ctx == EGL14.EGL_NO_CONTEXT) continue
            config = configs[0]; context = ctx
            // ES 3 allows mipmaps on non-power-of-two textures, which blurred() relies on.
            mips = es3
            return
        }
        error("no GLES context")
    }

    private fun setupResources() {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0); quad = ids[0]
        val data = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            .put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).also { it.position(0) }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quad)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 8 * 4, data, GLES20.GL_STATIC_DRAW)
        GLES20.glGenFramebuffers(1, ids, 0); fbo = ids[0]
        if (artW > 0) allocateScene()
    }

    private fun allocateScene() {
        if (scene != 0) GLES20.glDeleteTextures(1, intArrayOf(scene), 0)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0); scene = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, scene)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, artW, artH, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        params(mips)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, scene, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun params(withMips: Boolean) {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, if (withMips) GLES20.GL_LINEAR_MIPMAP_LINEAR else GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun texture(bitmap: Bitmap, withMips: Boolean): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        if (withMips) GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        params(withMips)
        return ids[0]
    }

    private fun upload(bitmap: Bitmap): Slide? = runCatching {
        val a = Random.nextDouble(0.0, 2 * PI)
        Slide(texture(bitmap, mips), bitmap.width, bitmap.height, SystemClock.uptimeMillis(), cos(a).toFloat(), sin(a).toFloat())
    }.onFailure { Log.w(TAG, "Hero art upload failed", it) }.getOrNull()

    private fun setEdgeOnGl(id: String, duration: Long) {
        val now = SystemClock.uptimeMillis()
        // A wipe that is running, or finished but not yet folded by a frame, leaves its incoming style as the base.
        if (edgeDuration > 0) edgeA = edgeB
        edgeB = id
        edgeStart = now
        edgeDuration = if (id == edgeA) 0 else duration
    }

    /**
     * Freezes the frame on screen as a slide so an interrupting change starts from
     * exactly what is visible; the interrupted transition's art is released.
     */
    private fun interrupt(a: Anim): Slide {
        anim = null
        val frozen = snapshot()
        if (frozen == null) {
            GLES20.glDeleteTextures(1, intArrayOf(a.from.tex), 0)
            current = a.to
            return a.to
        }
        GLES20.glDeleteTextures(2, intArrayOf(a.from.tex, a.to.tex), 0)
        current = frozen
        return frozen
    }

    /**
     * Copies the scene through the baseline program at rest, which flips it to the
     * uploaded-art orientation. Without drift or age, the copy maps 1:1.
     */
    private fun snapshot(): Slide? = runCatching {
        val fx = program("fx:${HeroMotionPolicy.BASELINE_TRANSITION}") ?: return null
        if (scene == 0 || artW == 0) return null
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, artW, artH, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        params(false)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, ids[0], 0)
        GLES20.glViewport(0, 0, artW, artH)
        use(fx)
        bind(0, scene, fx["uFrom"]); bind(1, scene, fx["uTo"])
        GLES20.glUniform2f(fx["uRes"], artW.toFloat(), artH.toFloat())
        GLES20.glUniform2f(fx["uFromSize"], artW.toFloat(), artH.toFloat())
        GLES20.glUniform2f(fx["uToSize"], artW.toFloat(), artH.toFloat())
        GLES20.glUniform2f(fx["uFromDrift"], 0f, 0f)
        GLES20.glUniform2f(fx["uToDrift"], 0f, 0f)
        GLES20.glUniform1f(fx["uFromT"], 0f)
        GLES20.glUniform1f(fx["uToT"], 0f)
        GLES20.glUniform1f(fx["uProgress"], 1f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, scene, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        Slide(ids[0], artW, artH, SystemClock.uptimeMillis(), 0f, 0f)
    }.onFailure { Log.w(TAG, "Hero frame snapshot failed", it) }.getOrNull()

    private fun start(from: Slide, change: Change) {
        val duration = (change.spec.duration * 1000).toLong()
        setEdgeOnGl(change.edge, duration)
        anim = Anim(from, change.slide, change.spec, SystemClock.uptimeMillis(), duration)
    }

    private fun program(key: String): Program? = programs.getOrPut(key) {
        val (source, uniforms) = when {
            key == "ambient" -> library.ambientSource() to EDGE_UNIFORMS
            key.startsWith("fx:") -> library.transitionSource(key.removePrefix("fx:")) to TRANSITION_UNIFORMS
            else -> library.edgeSource(key.removePrefix("edge:")) to EDGE_UNIFORMS
        }
        runCatching { link(source, uniforms) }.onFailure { Log.w(TAG, "Hero shader $key failed: ${it.message}") }.getOrNull()
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error(log)
        }
        return shader
    }

    private fun link(fragment: String, uniforms: List<String>): Program {
        val p = GLES20.glCreateProgram()
        val vs = compile(GLES20.GL_VERTEX_SHADER, HeroShaderLibrary.VERTEX)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) error(GLES20.glGetProgramInfoLog(p).also { GLES20.glDeleteProgram(p) })
        return Program(p, GLES20.glGetAttribLocation(p, "aPos"), uniforms.associateWith { GLES20.glGetUniformLocation(p, it) })
    }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            Choreographer.getInstance().postFrameCallback(this)
            frameCount++
            val now = SystemClock.uptimeMillis()
            val busy = anim != null || (edgeDuration > 0 && now - edgeStart < edgeDuration)
            // Resting edges still animate, but at half rate to leave headroom for focus motion.
            if (!busy && frameCount % 2L != 0L) {
                warmQueue.removeFirstOrNull()?.let(::program)
                return
            }
            runCatching { render(now) }.onFailure {
                Log.w(TAG, "Hero render failed", it)
                running = false
                onUnavailableMain()
            }
        }
    }

    private fun render(now: Long) {
        val slideNow = current ?: return
        if (artW == 0 || viewW == 0 || scene == 0) return
        var from = slideNow; var to = slideNow; var progress = 1f; var spec: HeroTransitionSpec? = null
        anim?.let { a ->
            progress = min(1f, (now - a.start).toFloat() / a.duration)
            from = a.from; to = a.to; spec = a.spec
            if (progress >= 1f) {
                GLES20.glDeleteTextures(1, intArrayOf(a.from.tex), 0)
                current = a.to
                anim = null
                from = a.to; to = a.to; spec = null
            }
        }
        val t = (now - t0) / 1000f

        // Pass 1: transition → scene.
        val fx = spec?.let { program("fx:${it.id}") } ?: program("fx:fade") ?: return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, artW, artH)
        use(fx)
        bind(0, from.tex, fx["uFrom"]); bind(1, to.tex, fx["uTo"])
        GLES20.glUniform2f(fx["uRes"], artW.toFloat(), artH.toFloat())
        GLES20.glUniform2f(fx["uFromSize"], from.w.toFloat(), from.h.toFloat())
        GLES20.glUniform2f(fx["uToSize"], to.w.toFloat(), to.h.toFloat())
        GLES20.glUniform2f(fx["uFromDrift"], from.driftX, from.driftY)
        GLES20.glUniform2f(fx["uToDrift"], to.driftX, to.driftY)
        GLES20.glUniform2f(fx["uFocus"], FOCUS_X, FOCUS_Y)
        GLES20.glUniform1f(fx["uProgress"], progress)
        GLES20.glUniform1f(fx["uTime"], t)
        GLES20.glUniform1f(fx["uFromT"], (now - from.start) / 1000f)
        GLES20.glUniform1f(fx["uToT"], (now - to.start) / 1000f)
        GLES20.glUniform1f(fx["uDpr"], layoutScale)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (mips) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, scene)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        }

        // Pass 2: ambient fill over the whole view.
        GLES20.glViewport(0, 0, viewW, viewH)
        program("ambient")?.let { edgeUniforms(it, t, -1f); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4) }

        // Pass 3: edge fade(s) inside the art rectangle (top-right).
        var morph = -1f
        if (edgeDuration > 0) {
            morph = (now - edgeStart).toFloat() / edgeDuration
            if (morph >= 1f) { edgeA = edgeB; edgeDuration = 0; morph = -1f }
        }
        GLES20.glViewport(viewW - artW, viewH - artH, artW, artH)
        (program("edge:$edgeA") ?: program("edge:linear"))?.let { edgeUniforms(it, t, -1f); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4) }
        if (morph >= 0f) (program("edge:$edgeB") ?: program("edge:linear"))?.let {
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            edgeUniforms(it, t, morph)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisable(GLES20.GL_BLEND)
        }
        EGL14.eglSwapBuffers(display, surface)
    }

    private fun edgeUniforms(p: Program, t: Float, morph: Float) {
        use(p)
        bind(0, scene, p["uScene"])
        GLES20.glUniform2f(p["uRes"], artW.toFloat(), artH.toFloat())
        GLES20.glUniform2f(p["uFocus"], FOCUS_X, FOCUS_Y)
        GLES20.glUniform1f(p["uTime"], t)
        GLES20.glUniform1f(p["uDpr"], layoutScale)
        GLES20.glUniform1f(p["uMorph"], morph)
        val ground = ground
        GLES20.glUniform3f(p["uGround"], Color.red(ground) / 255f, Color.green(ground) / 255f, Color.blue(ground) / 255f)
        GLES20.glUniform2f(p["uView"], viewW.toFloat(), viewH.toFloat())
        GLES20.glUniform2f(p["uOrigin"], (viewW - artW).toFloat(), (viewH - artH).toFloat())
    }

    private fun use(p: Program) {
        GLES20.glUseProgram(p.id)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quad)
        GLES20.glEnableVertexAttribArray(p.aPos)
        GLES20.glVertexAttribPointer(p.aPos, 2, GLES20.GL_FLOAT, false, 0, 0)
    }

    private fun bind(unit: Int, tex: Int, location: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(location, unit)
    }

    private fun teardown() {
        if (display == EGL14.EGL_NO_DISPLAY) return
        if (surface != EGL14.EGL_NO_SURFACE && context != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglMakeCurrent(display, surface, surface, context)
            programs.values.filterNotNull().forEach { GLES20.glDeleteProgram(it.id) }
            val textures = listOfNotNull(current?.tex, anim?.from?.tex, anim?.to?.tex, scene.takeIf { it != 0 })
            if (textures.isNotEmpty()) GLES20.glDeleteTextures(textures.size, textures.toIntArray(), 0)
            if (fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            if (quad != 0) GLES20.glDeleteBuffers(1, intArrayOf(quad), 0)
        }
        programs.clear(); current = null; anim = null; scene = 0; fbo = 0; quad = 0
        edgeA = edgeB; edgeDuration = 0
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
        display = EGL14.EGL_NO_DISPLAY; context = EGL14.EGL_NO_CONTEXT; surface = EGL14.EGL_NO_SURFACE
    }

    private companion object {
        const val TAG = "HeroGlRenderer"
        const val FOCUS_X = 0.62f
        const val FOCUS_Y = 0.55f
        /** Changes closer together than this are browsing and use the baseline dissolve. */
        const val BROWSE_MS = 450L
        val TRANSITION_UNIFORMS = listOf("uFrom", "uTo", "uRes", "uFromSize", "uToSize", "uFromDrift", "uToDrift",
            "uFocus", "uProgress", "uTime", "uFromT", "uToT", "uDpr")
        val EDGE_UNIFORMS = listOf("uScene", "uRes", "uFocus", "uTime", "uDpr", "uMorph", "uGround", "uView", "uOrigin")
    }
}
