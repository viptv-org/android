package org.viptv.app

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import org.playbackgateway.runtime.NativeRuntime

/** This non-exported app-private process is the only Android host of the Go library. */
class TorrentRuntimeService : Service() {
    private val connected = AtomicBoolean()
    private val executor = Executors.newSingleThreadExecutor()
    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != CONNECT || Binder.getCallingUid() != Process.myUid() || reply == null || !connected.compareAndSet(false, true)) return false
            data.enforceInterface(DESCRIPTOR)
            val incoming = ParcelFileDescriptor.CREATOR.createFromParcel(data)
            val outgoing = ParcelFileDescriptor.CREATOR.createFromParcel(data)
            reply.writeNoException()
            reply.writeInt(Process.myPid())
            executor.execute {
                try {
                    DataInputStream(ParcelFileDescriptor.AutoCloseInputStream(incoming)).use { input ->
                        DataOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(outgoing)).use { output ->
                            while (true) {
                                val request = TorrentRuntimeFrames.read(input).toString(Charsets.UTF_8)
                                val response = NativeRuntime.call(request)
                                TorrentRuntimeFrames.write(output, response.toByteArray(Charsets.UTF_8))
                                val value = JSONObject(response)
                                if (value.optBoolean("terminate") || JSONObject(request).optString("op") == "shutdown") break
                            }
                        }
                    }
                } catch (_: Exception) { /* Pipe death retires this process and every media capability. */ }
                finally { Process.killProcess(Process.myPid()) }
            }
            return true
        }
    }
    override fun onBind(intent: Intent?): IBinder = binder
    override fun onUnbind(intent: Intent?): Boolean { Process.killProcess(Process.myPid()); return false }
    companion object {
        internal const val DESCRIPTOR = "org.viptv.app.private.TorrentRuntime"
        internal const val CONNECT = IBinder.FIRST_CALL_TRANSACTION
    }
}
