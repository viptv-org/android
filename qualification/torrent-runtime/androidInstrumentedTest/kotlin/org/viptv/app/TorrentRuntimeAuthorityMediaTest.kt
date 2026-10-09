package org.viptv.app

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.viptv.video.AndroidMedia3BackendFactory
import org.viptv.video.PlaybackEvent
import org.viptv.video.PlaybackKind
import org.viptv.video.PlaybackOptions
import org.viptv.video.PlaybackSource
import kotlin.test.*

/** Private ephemeral backend configuration; only included in the explicit QA package. */
class TorrentRuntimeAuthorityMediaTest {
    @Test fun realBackendCoreGoAndMedia3DecodeWithoutGatewayFallback() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val input=File(context.noBackupFilesDir,"torrent-runtime-authority-qa.json")
        val config=JSONObject(input.readText())
        val result=JSONObject().put("passed",false)
        val jobs=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val player=AndroidMedia3BackendFactory(context).createAndroidPlayer()
        val gateway=VipTvHttpGateway(config.getString("origin"),config.getString("access_token"),television=false)
        val parent=File(context.noBackupFilesDir,"torrent-authority-qa").apply {mkdirs()}
        val cache=NativeTorrentCache.open(parent,{directory,capacity->NativeTorrentEngineCacheManager(directory,capacity){TorrentRuntimeWorker(context)}},nowNanos=SystemClock::elapsedRealtimeNanos,retainContent=true)
        val coordinator=NativeTorrentCoordinator(cache,SystemClock::elapsedRealtimeNanos,{true},{player.stop()})
        lateinit var control:NativePlaybackControl
        control=gateway.nativePlaybackControl("owned_runtime_auth",1,cache,jobs,{
            player.stop();if(control.isLocallyRetired())coordinator.cancelNative(control)
        },{})
        val frame=CompletableDeferred<Unit>()
        var candidate:NativeTorrentCoordinator.Candidate?=null
        val observer=launch {player.events.collect {event->
            if(event is PlaybackEvent.FirstFrame){candidate?.work?.firstFrame();frame.complete(Unit)}
            if(event is PlaybackEvent.Failed)frame.completeExceptionally(IllegalStateException("decoder_failed"))
        }}
        val started=SystemClock.elapsedRealtime()
        try {
            assertNotNull(gateway.foregroundIdentity())
            val request=JSONObject().put("requestId",UUID.randomUUID().toString()).put("streamId",config.getString("stream_id")).put("position",0)
                .put("client",JSONObject().put("platform","android").put("canPlayDirect",true).put("maxWidth",1920).put("maxHeight",1080)
                    .put("videoCodecs",JSONArray().put("h264")).put("audioCodecs",JSONArray().put("aac")))
            coordinator.ownControl(control,1)
            assertIs<NativePlaybackStart.Native>(control.start(request,true,true,cache))
            val prepared=coordinator.prepare(control,1);candidate=prepared
            ActivityScenario.launch(TorrentRuntimeQaActivity::class.java).use {scenario->
                lateinit var activity:TorrentRuntimeQaActivity
                scenario.onActivity {activity=it}
                withTimeout(10000){while(!activity.texture.isAvailable)delay(20)}
                withContext(Dispatchers.Main.immediate){player.attach(activity.texture)}
                coordinator.accept(prepared){capability->
                    withContext(Dispatchers.Main.immediate){player.open(PlaybackSource(capability.url,kindHint=PlaybackKind.OnDemand,
                        options=PlaybackOptions(openTimeoutMillis=control.startupRemainingMillis(),httpReadTimeoutMillis=60000)),true)}
                }
                withTimeout(control.startupRemainingMillis().coerceAtLeast(1)){frame.await()}
                result.put("first_frame_ms",SystemClock.elapsedRealtime()-started)
                assertEquals("ready",control.state().status)
                withContext(Dispatchers.Main.immediate){player.stop();player.close()}
                result.put("retired",coordinator.retire(prepared))
            }
            assertTrue(result.getBoolean("retired"))
            result.put("passed",true)
        } finally {
            observer.cancel()
            withContext(Dispatchers.Main.immediate){player.close()}
            runCatching {coordinator.retireControl(control)}
            result.put("cache_settled",cache.closeScope())
            jobs.cancel()
            File(context.noBackupFilesDir,"torrent-runtime-authority-result.json").writeText(result.toString())
            input.delete()
        }
        assertTrue(result.getBoolean("passed"))
        assertTrue(result.getBoolean("cache_settled"))
    }
}
