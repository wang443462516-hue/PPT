package com.hy.wristnav.notification

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class NotificationInput(
    val packageName: String,
    val id: Int,
    val postTime: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val focusParam: String?,
    val extrasKeys: List<String>
)

data class ParseResult(
    val snapshotFields: ParsedFields? = null,
    val rule: String? = null,
    val failure: String? = null
)

data class ParsedFields(
    val maneuver: String?,
    val metersToTurn: Int?,
    val road: String?,
    val etaMinutes: Int?
)

object AmapParser {
    private val distance = Regex("(\\d+(?:\\.\\d+)?)\\s*(公里|千米|km|KM|米|m)\\s*(?:后|以后)?")
    private val eta = Regex("(?:预计|到达|ETA)[^0-9]*(\\d{1,2}):(\\d{2})", RegexOption.IGNORE_CASE)
    private val road = Regex("(?:进入|沿|驶入|开往)\\s*([\\u4e00-\\u9fa5A-Za-z0-9·-]{2,24}(?:路|街|大道|大街|高速|隧道|桥))")

    fun parse(input: NotificationInput): ParseResult {
        if (input.packageName != "com.autonavi.minimap") {
            return ParseResult(failure = "NOT_AMAP")
        }

        val focusRawPresent = !input.focusParam.isNullOrBlank()
        val focus = parseFocus(input.focusParam)
        val combined = listOfNotNull(
            input.title,
            input.text,
            input.bigText,
            focus?.optString("text", null),
            focus?.optString("content", null),
            focus?.optString("bigText", null)
        ).joinToString(" ").trim()

        val parsedDistance = distance.find(combined)?.let { match ->
            val value = match.groupValues[1].toDouble()
            if (match.groupValues[2].lowercase(Locale.US) in setOf("公里", "千米", "km")) {
                (value * 1000).toInt()
            } else value.toInt()
        } ?: focus?.optIntNullable("metersToTurn")

        val maneuver = maneuverFor(combined)
            ?: focus?.optString("maneuver", null)?.lowercase(Locale.US)
        val roadName = focus?.optString("road", null)?.takeIf { it.isNotBlank() }
            ?: road.find(combined)?.groupValues?.getOrNull(1)
        val etaMinutes = eta.find(combined)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                if (timeInMillis < now.timeInMillis) add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            ((target.timeInMillis - now.timeInMillis) / 60_000L).toInt()
        } ?: focus?.optIntNullable("etaMinutes")

        if (maneuver == null && parsedDistance == null) {
            val failure = if (focusRawPresent && focus == null) "FOCUS_UNREADABLE" else "NO_NAV_FIELDS"
            return ParseResult(rule = if (focus != null) "amap_focus_unmatched" else "amap_text_unmatched", failure = failure)
        }

        return ParseResult(
            snapshotFields = ParsedFields(maneuver, parsedDistance, roadName, etaMinutes),
            rule = if (focus != null) "amap_text_plus_focus" else "amap_text"
        )
    }

    private fun maneuverFor(text: String): String? = when {
        text.contains("掉头") || text.contains("调头") || text.contains("U型") -> "uturn"
        text.contains("到达目的地") || text.contains("抵达目的地") || text.contains("目的地") -> "arrive"
        text.contains("左前方") || text.contains("向左") || text.contains("靠左") -> "slight_left"
        text.contains("右前方") || text.contains("向右") || text.contains("靠右") -> "slight_right"
        text.contains("左转") -> "left"
        text.contains("右转") -> "right"
        text.contains("直行") || text.contains("继续") -> "straight"
        else -> null
    }

    private fun parseFocus(raw: String?): JSONObject? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank() || !value.startsWith("{")) return null
        return runCatching { JSONObject(value) }.getOrNull()
    }

    private fun JSONObject.optIntNullable(key: String): Int? = when {
        !has(key) || isNull(key) -> null
        else -> optInt(key).takeIf { it >= 0 }
    }
}
