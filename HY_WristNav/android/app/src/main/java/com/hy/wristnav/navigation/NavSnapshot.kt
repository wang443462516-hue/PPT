package com.hy.wristnav.navigation

import org.json.JSONObject

data class NavSnapshot(
    val seq: Long,
    val ts: Long,
    val source: String,
    val live: Boolean,
    val state: String,
    val maneuver: String?,
    val metersToTurn: Int?,
    val road: String?,
    val remainingMeters: Int? = null,
    val etaMinutes: Int? = null,
    val speedKph: Int? = null,
    val headingDegrees: Double? = null,
    val parserRule: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("v", 1)
        put("t", "nav_snapshot")
        put("seq", seq)
        put("ts", ts)
        put("source", source)
        put("live", live)
        put("state", state)
        putNullable("maneuver", maneuver)
        putNullable("metersToTurn", metersToTurn)
        putNullable("road", road)
        putNullable("remainingMeters", remainingMeters)
        putNullable("etaMinutes", etaMinutes)
        putNullable("speedKph", speedKph)
        putNullable("headingDegrees", headingDegrees)
        putNullable("parserRule", parserRule)
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }
}
