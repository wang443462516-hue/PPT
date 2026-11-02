package com.hy.wristnav.bridge

import com.hy.wristnav.navigation.NavStore

interface WatchBridge {
    fun send(payload: String): Boolean
}

/**
 * Deliberately explicit fallback while the proprietary Xiaomi Android SDK is
 * not available. It prevents a demo button from being mistaken for real
 * interconnect success.
 */
class LoggingWatchBridge : WatchBridge {
    override fun send(payload: String): Boolean {
        NavStore.setTransport("未发送：缺少 Xiaomi Android interconnect SDK")
        NavStore.addLog("interconnect_send_blocked payload=${payload.take(160)}")
        return false
    }
}

object WatchBridgeProvider {
    val bridge: WatchBridge = LoggingWatchBridge()
}
