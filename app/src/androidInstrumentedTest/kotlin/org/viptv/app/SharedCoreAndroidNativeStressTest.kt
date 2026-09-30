package org.viptv.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.viptv.core.wire.Phase
import uniffi.viptv_core.CoreBridge
import uniffi.viptv_core.CoreException
import uniffi.viptv_core.normalize

/** Real Android JNA/JNI + Core8. No Activity, backend, media or default origin. */
class SharedCoreAndroidNativeStressTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val activities = CopyOnWriteArrayList<String>()
    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: Bundle?) { activities.add(activity.javaClass.name) }
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
    @Before fun monitorNoActivities() {
        assertEquals("org.viptv.app", instrumentation.targetContext.packageName)
        instrumentation.runOnMainSync {
            (instrumentation.targetContext.applicationContext as Application).registerActivityLifecycleCallbacks(callbacks)
        }
    }
    @After fun verifyNoActivityWasLaunched() {
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            (instrumentation.targetContext.applicationContext as Application).unregisterActivityLifecycleCallbacks(callbacks)
        }
        assertTrue(activities.isEmpty(), "An app Activity was launched during native-only instrumentation")
        println("ANDROID_NATIVE_STRESS_ACTIVITIES=0")
    }

    private fun begin(core: CoreBridge) = JSONArray(core.update(
        """{"Begin":{"origin":"https://127.0.0.1:1","allowInsecurePreview":false}}"""))
    private fun effect(requests: JSONArray, kind: String): JSONObject =
        (0 until requests.length()).map(requests::getJSONObject).single { it.getJSONObject("effect").has(kind) }

    @Test(timeout = 60_000) fun androidJniBuffersAndNativeHandleCycles() {
        repeat(200) { index ->
            CoreBridge().use { core ->
                val load = effect(begin(core), "Storage")
                assertFailsWith<CoreException> { core.update("{") }
                core.resolve(load.getLong("id").toUInt(), """{"Ok":null}""")
                assertEquals("Pairing", JSONObject(core.view()).getString("phase"))
                core.close(); core.close()
                assertFailsWith<IllegalStateException> { core.view() }
            }
            val input = JSONObject().put("id", "jni-$index").put("type", "movie")
                .put("name", "Android λ $index").put("description", "λ".repeat(1024)).toString()
            assertEquals("jni-$index", JSONObject(normalize("media", input, "")).getString("id"))
            assertFailsWith<CoreException> { normalize("media", "{", "") }
        }
        println("ANDROID_NATIVE_STRESS_HANDLES=200")
    }

    @Test(timeout = 60_000) fun actualMainLooperCoreSessionQueuedAndSuspendedRenderCancellation() {
        val reads = AtomicInteger()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("native_ffi_test_only", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear().commit())
        // Only a storage effect is substituted. CoreSession/native/Looper stay real.
        // Failure occurs before any HTTP request can be produced or executed.
        val failingStore = object : SharedPreferences by prefs {
            override fun getString(key: String?, default: String?): String? {
                reads.incrementAndGet()
                throw IllegalStateException("Test-only storage unavailable")
            }
        }
        try {
            repeat(50) {
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                val renders = AtomicInteger()
                instrumentation.runOnMainSync {
                    val session = CoreSession("https://127.0.0.1:1", failingStore, scope,
                        { error("Queued session must not change credentials") }, { renders.incrementAndGet() })
                    session.begin()
                    session.close()
                    session.retry()
                }
                instrumentation.waitForIdleSync()
                scope.cancel()
                assertEquals(0, renders.get(), "Queued work rendered after close")
            }
            assertEquals(0, reads.get(), "Queued work accessed storage after close")
            repeat(20) {
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                val stalled = CountDownLatch(1)
                val cancelled = CountDownLatch(1)
                val failure = AtomicReference<Throwable>()
                val renderCount = AtomicInteger()
                val session = AtomicReference<CoreSession>()
                try {
                    instrumentation.runOnMainSync {
                        session.set(CoreSession("https://127.0.0.1:1", failingStore, scope,
                            { failure.compareAndSet(null, AssertionError("Credentials callback unexpectedly ran")) }, { view ->
                                if (Looper.myLooper() !== Looper.getMainLooper())
                                    failure.compareAndSet(null, AssertionError("Render not on actual Android main looper"))
                                renderCount.incrementAndGet()
                                if (view.phase == Phase.ERROR) {
                                    if (view.error != "Could not access saved session. Retry to stay paired.")
                                        failure.compareAndSet(null, AssertionError("Expected native storage-error projection"))
                                    stalled.countDown()
                                    try { awaitCancellation() } finally { cancelled.countDown() }
                                } else if (view.phase != Phase.RESTORING)
                                    failure.compareAndSet(null, AssertionError("Unexpected native phase before storage refusal"))
                            }))
                        session.get().begin()
                    }
                    assertTrue(stalled.await(5, TimeUnit.SECONDS), "Native error render did not suspend")
                    instrumentation.runOnMainSync { session.get().close() }
                    assertTrue(cancelled.await(5, TimeUnit.SECONDS), "Suspended render did not actually cancel")
                    instrumentation.waitForIdleSync()
                    val stoppedAt = renderCount.get()
                    instrumentation.runOnMainSync { session.get().begin(); session.get().retry(); session.get().close() }
                    instrumentation.waitForIdleSync()
                    assertEquals(stoppedAt, renderCount.get(), "Post-close render published")
                    assertNull(failure.get())
                } finally {
                    instrumentation.runOnMainSync { session.get()?.close() }
                    scope.cancel()
                }
            }
            assertEquals(20, reads.get(), "Native session storage path was not exercised")
            println("ANDROID_NATIVE_STRESS_MAIN_LOOPER queued=50 suspendedCancelled=20 storageReads=20")
        } finally { assertTrue(prefs.edit().clear().commit()) }
    }

    @Test(timeout = 60_000) fun androidJniConcurrentCloseAndSupersededFixtureEffects() {
        val executor = Executors.newFixedThreadPool(5)
        val successes = AtomicInteger()
        val closed = AtomicInteger()
        try {
            repeat(20) {
                CoreBridge().use { core ->
                    val oldLoad = effect(begin(core), "Storage")
                    val newLoad = effect(begin(core), "Storage")
                    assertEquals(0, JSONArray(core.resolve(oldLoad.getLong("id").toUInt(), """{"Ok":null}""")).length())
                    core.resolve(newLoad.getLong("id").toUInt(), """{"Ok":null}""")
                    assertEquals("Pairing", JSONObject(core.view()).getString("phase"))
                    val started = CountDownLatch(1)
                    val entered = CountDownLatch(4)
                    val readers = List(4) {
                        executor.submit {
                            assertTrue(started.await(5, TimeUnit.SECONDS))
                            assertEquals("Pairing", JSONObject(core.view()).getString("phase"))
                            successes.incrementAndGet(); entered.countDown()
                            repeat(100) {
                                try { assertEquals("Pairing", JSONObject(core.view()).getString("phase")); successes.incrementAndGet() }
                                catch (refusal: IllegalStateException) {
                                    assertTrue(refusal.message.orEmpty().contains("object has already been destroyed"))
                                    closed.incrementAndGet()
                                }
                            }
                        }
                    }
                    val closer = executor.submit { assertTrue(entered.await(5, TimeUnit.SECONDS)); repeat(3) { core.close() } }
                    started.countDown()
                    readers.forEach { it.get(10, TimeUnit.SECONDS) }; closer.get(10, TimeUnit.SECONDS)
                    assertFailsWith<IllegalStateException> { core.view() }
                }
            }
            assertTrue(successes.get() >= 80 && closed.get() > 0)
            assertEquals(8080, successes.get() + closed.get())
            println("ANDROID_NATIVE_STRESS_RACE nativeViews=${successes.get()} expectedClosed=${closed.get()}")
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "JNI reader workers not reclaimed")
        }
    }
}
