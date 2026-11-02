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
    private val road = Regex("(?:进入|沿|驶入|开往)\\s*([\\u4e00-\\u9fa5A-Za-z0-9·-]{1,24}(?:路|街|大道|大街|高速|隧道|桥))")

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
        ).plus(focusStrings(focus)).joinToString(" ").take(16384).trim()

        val parsedDistance = distance.find(combined)?.let { match ->
            val value = match.groupValues[1].toDouble()
            if (match.groupValues[2].lowercase(Locale.US) in setOf("公里", "千米", "km")) {
                (value * 1000).toInt()
            } else value.toInt()
        } ?: focus?.optIntNullable("metersToTurn")

        val maneuver = maneuverFor(combined)
            ?: focus?.optString("maneuver", null)?.lowercase(Locale.US)?.takeIf {
                it in setOf("left", "right", "straight", "slight_left", "slight_right", "uturn", "arrive")
            }
        val roadName = focus?.optString("road", null)?.takeIf { it.isNotBlank() }
            ?: road.find(combined)?.groupValues?.getOrNull(1)
        // An arrival clock time is ambiguous at midnight; do not infer a day.
        val etaMinutes = Regex("(?:剩余|还需|预计用时)\\s*(\\d{1,3})\\s*分钟").find(combined)
            ?.groupValues?.get(1)?.toIntOrNull() ?: focus?.optIntNullable("etaMinutes")

        if (maneuver == null) {
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
        text.contains("已到达目的地") || text.contains("已抵达目的地") -> "arrive"
        text.contains("左前方") || text.contains("靠左") -> "slight_left"
        text.contains("右前方") || text.contains("靠右") -> "slight_right"
        text.contains("左转") -> "left"
        text.contains("右转") -> "right"
        text.contains("直行") -> "straight"
        else -> null
    }

    private fun parseFocus(raw: String?): JSONObject? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank() || value.length > 65536 || !value.startsWith("{")) return null
        return runCatching { JSONObject(value) }.getOrNull()
    }

    private fun JSONObject.optIntNullable(key: String): Int? = when {
        !has(key) || isNull(key) -> null
        else -> opt(key).toString().toIntOrNull()?.takeIf { it in 0..100000 }
    }

    private fun focusStrings(value: Any?, depth: Int = 0): List<String> {
        if (depth > 5 || value == null || value == JSONObject.NULL) return emptyList()
        return when (value) {
            is String -> listOf(value.take(512))
            is JSONObject -> value.keys().asSequence().take(32).flatMap {
                focusStrings(value.opt(it), depth + 1).asSequence()
            }.toList()
            is JSONArray -> (0 until minOf(32, value.length())).flatMap { focusStrings(value.opt(it), depth + 1) }
            else -> emptyList()
        }
    }
}
