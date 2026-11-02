package com.hy.wristnav.bridge

import com.hy.wristnav.navigation.NavProtocol
import com.hy.wristnav.navigation.NavSnapshot
import com.hy.wristnav.navigation.NavStore
import org.json.JSONObject

/** Pure protocol handling kept separate from the proprietary transport SDK. */
object WatchMessageHandler {
    fun handle(payload: String): String? {
        val message = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        return when (message.optString("t")) {
            "hello" -> NavProtocol.hello(NavStore.nextSeq())
            "request_snapshot" -> NavStore.current?.toJson()?.toString()
                ?: NavSnapshot(
                    seq = NavStore.nextSeq(),
                    ts = System.currentTimeMillis(),
                    source = "android",
                    live = false,
                    state = "no_data",
                    maneuver = null,
                    metersToTurn = null,
                    road = null,
                    parserRule = "NO_SNAPSHOT"
                ).toJson().toString()
            else -> null
        }
    }
}
