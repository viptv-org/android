package org.viptv.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/** An owned child can be killed without taking down Media3 or the application process. */
internal interface TorrentRuntimePort : AutoCloseable {
    fun call(command: JSONObject, timeoutMillis: Long = 3_000): JSONObject
    fun terminate(): Boolean
    fun isAlive(): Boolean
}
internal class TorrentRuntimeWorker(private val context: Context) : TorrentRuntimePort {
    private val connected = CountDownLatch(1)
    private val dead = CountDownLatch(1)
    private val closed = AtomicBoolean()
    @Volatile private var binder: IBinder? = null
    @Volatile private var pid = 0
    internal val ownedPid: Int get() = pid
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var readPipe: ParcelFileDescriptor? = null
    private var writePipe: ParcelFileDescriptor? = null
    private val ioLock = Any()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service
            try { service.linkToDeath({ dead.countDown() }, 0) } catch (_: Exception) { dead.countDown() }
            connected.countDown()
        }
        override fun onServiceDisconnected(name: ComponentName) { dead.countDown() }
        override fun onBindingDied(name: ComponentName) { dead.countDown(); connected.countDown() }
        override fun onNullBinding(name: ComponentName) { dead.countDown(); connected.countDown() }
    }
    init {
        if (!context.bindService(Intent(context, TorrentRuntimeService::class.java), connection, Context.BIND_AUTO_CREATE)) throw NativeTorrentCoordinatorUnavailable()
        try {
            if (!connected.await(8, TimeUnit.SECONDS)) throw NativeTorrentCoordinatorUnavailable()
            val service = requireNotNull(binder)
            val request = ParcelFileDescriptor.createPipe()
            val response = ParcelFileDescriptor.createPipe()
            writePipe = request[1]; readPipe = response[0]
            val data = Parcel.obtain(); val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(TorrentRuntimeService.DESCRIPTOR)
                request[0].writeToParcel(data, 0)
                response[1].writeToParcel(data, 0)
                if (!service.transact(TorrentRuntimeService.CONNECT, data, reply, 0)) throw NativeTorrentCoordinatorUnavailable()
                reply.readException()
                pid = reply.readInt().takeIf { it > 0 && it != Process.myPid() } ?: throw NativeTorrentCoordinatorUnavailable()
            } finally { data.recycle(); reply.recycle(); request[0].close(); response[1].close() }
            input = DataInputStream(ParcelFileDescriptor.AutoCloseInputStream(response[0]))
            output = DataOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(request[1]))
        } catch (_: Exception) { terminate(); throw NativeTorrentCoordinatorUnavailable() }
    }
    override fun call(command: JSONObject, timeoutMillis: Long): JSONObject {
        if (closed.get() || dead.count == 0L) throw NativeTorrentCoordinatorUnavailable()
        val bytes = command.put("version", 2).toString().toByteArray(Charsets.UTF_8)
        val task = effects.submit<JSONObject> {
            synchronized(ioLock) {
                if (closed.get()) throw NativeTorrentCoordinatorUnavailable()
                TorrentRuntimeFrames.write(requireNotNull(output), bytes)
                val response = JSONObject(TorrentRuntimeFrames.read(requireNotNull(input)).toString(Charsets.UTF_8))
                if (response.optInt("version") != 2) throw NativeTorrentCoordinatorUnavailable()
                response
            }
        }
        val response = try { task.get(timeoutMillis.coerceAtLeast(1), TimeUnit.MILLISECONDS) }
            catch (_: Exception) { terminate(); throw NativeTorrentCoordinatorUnavailable() }
        if (response.optBoolean("terminate")) { terminate(); return response }
        return response
    }
    /** Only the PID supplied by this private binding can be terminated. */
    override fun terminate(): Boolean {
        if (closed.compareAndSet(false, true)) {
            if (pid > 0 && pid != Process.myPid() && binder?.isBinderAlive == true && dead.count != 0L) Process.killProcess(pid)
            runCatching { writePipe?.close() }; runCatching { readPipe?.close() }
            runCatching { context.unbindService(connection) }
        }
        val service = binder ?: return true
        val deadline = SystemClock.elapsedRealtime() + 2_000
        while (service.isBinderAlive && dead.count != 0L && SystemClock.elapsedRealtime() < deadline) dead.await(10, TimeUnit.MILLISECONDS)
        return !service.isBinderAlive || dead.count == 0L
    }
    override fun isAlive(): Boolean = !closed.get() && dead.count != 0L && binder?.isBinderAlive == true
    override fun close() { terminate() }
    override fun toString() = "TorrentRuntimeWorker(<redacted>)"
    companion object { private val effects = Executors.newCachedThreadPool { Thread(it, "torrent-runtime-pipe").apply { isDaemon = true } } }
}
