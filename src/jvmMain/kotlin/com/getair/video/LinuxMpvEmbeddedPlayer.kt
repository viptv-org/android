package com.getair.video

import kotlinx.coroutines.Dispatchers
import java.awt.Canvas
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal data class LinuxMpvOptions(
    val mpvExecutable: String = "mpv",
    val jawtBridge: Path,
    val extraMpvArguments: List<String> = emptyList(),
    val openTimeoutMillis: Long = 20_000,
) {
    init {
        require(mpvExecutable.isNotBlank())
        require(openTimeoutMillis > 0)
    }

    override fun toString(): String =
        "LinuxMpvOptions(mpvExecutable=<redacted>, jawtBridge=<redacted>, " +
            "extraMpvArguments=${extraMpvArguments.size}, openTimeoutMillis=$openTimeoutMillis)"
}

internal class LinuxMpvBackendFactory(
    private val options: LinuxMpvOptions,
) : VideoBackendFactory {
    override val id: String = "mpv"

    override suspend fun probe(): PlayerCapabilities {
        check(System.getProperty("os.name").contains("linux", ignoreCase = true)) {
            "The Linux MPV backend is only available on Linux"
        }
        check(Files.isRegularFile(options.jawtBridge)) { "The JAWT video bridge is unavailable" }
        JawtWindowHandle.load(options.jawtBridge)
        val connection = MpvIpcConnection.start(
            MpvProcessOptions(executable = options.mpvExecutable, headless = true),
        )
        return try {
            connection.command(strings("get_property", "mpv-version"))
            MPV_BASELINE_CAPABILITIES
        } finally {
            connection.close()
        }
    }

    override fun create(): VideoPlayer = createLinuxPlayer()

    fun createLinuxPlayer(): LinuxMpvVideoPlayer {
        JawtWindowHandle.load(options.jawtBridge)
        val surface = LinuxMpvSurfaceBinding()
        val backend = MpvSessionBackend(
            clientFactory = {
                val windowHandle = surface.requireAttachedHandle()
                surface.markPlayerStarted()
                MpvIpcConnection.start(
                    MpvProcessOptions(
                        executable = options.mpvExecutable,
                        headless = false,
                        extraArguments = buildList {
                            add("--wid=$windowHandle")
                            add("--vo=gpu-next")
                            add("--hwdec=auto-safe")
                            add("--ao=null")
                            addAll(options.extraMpvArguments)
                        },
                    ),
                )
            },
            openTimeoutMillis = options.openTimeoutMillis,
        )
        return LinuxMpvVideoPlayer(surface, backend)
    }
}

internal class LinuxMpvVideoPlayer internal constructor(
    private val surface: LinuxMpvSurfaceBinding,
    private val backend: MpvSessionBackend,
) : VideoPlayer by DefaultVideoPlayer(backend, Dispatchers.IO) {
    fun attach(canvas: Canvas) {
        require(canvas.isDisplayable) { "The MPV canvas must be attached to a displayable window" }
        surface.attach(JawtWindowHandle.get(canvas))
    }

    internal suspend fun diagnosticProperty(name: String) = backend.diagnosticProperty(name)
}

internal class LinuxMpvSurfaceBinding {
    private val handle = AtomicLong(0)
    private val playerStarted = AtomicBoolean(false)

    fun attach(windowHandle: Long) {
        require(windowHandle > 0) { "The MPV canvas has no native window handle" }
        val current = handle.get()
        check(!playerStarted.get() || current == windowHandle) {
            "The MPV surface cannot be replaced after playback starts; create a new player for the new window"
        }
        handle.set(windowHandle)
    }

    fun requireAttachedHandle(): Long = handle.get().takeIf { it > 0 }
        ?: throw PlaybackFailure(
            PlaybackError(
                PlaybackErrorCode.Source,
                "Attach a displayable MPV canvas before opening media",
                recoverable = true,
            ),
        )

    fun markPlayerStarted() {
        playerStarted.set(true)
    }
}

internal object JawtWindowHandle {
    private val loaded = AtomicBoolean(false)

    fun load(library: Path) {
        if (loaded.get()) return
        synchronized(this) {
            if (loaded.get()) return
            check(Files.isRegularFile(library)) { "The JAWT video bridge is unavailable" }
            System.loadLibrary("jawt")
            System.load(library.toAbsolutePath().normalize().toString())
            loaded.set(true)
        }
    }

    fun get(canvas: Canvas): Long = nativeGetWindowHandle(canvas).also {
        check(it > 0) { "The JAWT video surface has no native window handle" }
    }

    @JvmStatic
    private external fun nativeGetWindowHandle(component: java.awt.Component): Long
}
