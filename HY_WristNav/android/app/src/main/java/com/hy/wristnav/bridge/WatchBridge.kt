package com.hy.wristnav.bridge

import android.content.Context

interface WatchBridge {
    fun send(payload: String): Boolean
    fun reconnect()
}

object WatchBridgeProvider {
    lateinit var bridge: WatchBridge
        private set
    fun initialize(context: Context) { bridge = XiaomiWatchBridge(context) }
}
