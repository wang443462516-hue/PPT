package com.hy.wristnav.navigation

import org.json.JSONObject

object NavProtocol {
    fun hello(seq: Long): String = JSONObject().apply {
        put("v", 1)
        put("t", "hello")
        put("seq", seq)
        put("ts", System.currentTimeMillis())
        put("source", "android")
    }.toString()

    fun requestSnapshot(seq: Long): String = JSONObject().apply {
        put("v", 1)
        put("t", "request_snapshot")
        put("seq", seq)
        put("ts", System.currentTimeMillis())
        put("source", "watch")
    }.toString()

    fun stop(seq: Long): String = JSONObject().apply {
        put("v", 1)
        put("t", "stop")
        put("seq", seq)
        put("ts", System.currentTimeMillis())
        put("source", "android")
        put("live", false)
        put("state", "stop")
    }.toString()
}
