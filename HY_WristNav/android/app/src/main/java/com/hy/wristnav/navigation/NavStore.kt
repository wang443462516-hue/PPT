package com.hy.wristnav.navigation

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

data class NotificationHealth(
    var connected: Boolean = false,
    var lastCallbackMs: Long? = null,
    var lastPackage: String? = null,
    var lastRule: String? = null,
    var lastFailure: String? = null,
    var lastRedactedText: String? = null
)

object NavStore {
    private const val TAG = "HY_WristNav"
    private val seq = AtomicLong(0)
    private val logs = CopyOnWriteArrayList<String>()
    val health = NotificationHealth()
    @Volatile var current: NavSnapshot? = null
        private set
    @Volatile var lastTransport: String = "未接入 Xiaomi interconnect SDK"
        private set

    fun nextSeq(): Long = seq.incrementAndGet()

    fun setCurrent(snapshot: NavSnapshot) {
        current = snapshot
        addLog("nav_snapshot seq=${snapshot.seq} live=${snapshot.live} state=${snapshot.state}")
    }

    fun setTransport(status: String) {
        lastTransport = status
        addLog("transport $status")
    }

    fun addLog(message: String) {
        val line = "${time()} $message"
        logs.add(line)
        while (logs.size > 80) logs.removeAt(0)
        Log.i(TAG, message)
    }

    fun recentLogs(): List<String> = logs.toList()

    private fun time(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
}
