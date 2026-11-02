package com.hy.wristnav.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.hy.wristnav.bridge.WatchBridgeProvider
import com.hy.wristnav.navigation.NavSnapshot
import com.hy.wristnav.navigation.NavStore

class AmapNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        NavStore.health.connected = true
        NavStore.addLog("notification_listener_bound")
    }

    override fun onListenerDisconnected() {
        NavStore.health.connected = false
        NavStore.addLog("notification_listener_disconnected")
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val focus = extras?.get("miui.focus.param")?.toString()
        val input = NotificationInput(
            packageName = sbn.packageName,
            id = sbn.id,
            postTime = sbn.postTime,
            title = title,
            text = text,
            bigText = bigText,
            focusParam = focus,
            extrasKeys = extras?.keySet()?.toList().orEmpty().sorted()
        )
        if (input.packageName != "com.autonavi.minimap") return

        NavStore.health.lastCallbackMs = System.currentTimeMillis()
        NavStore.health.lastPackage = input.packageName
        NavStore.health.lastRedactedText = redact(listOfNotNull(title, text, bigText).joinToString(" | "))
        NavStore.addLog("notification_received id=${input.id} keys=${input.extrasKeys.joinToString(",")}")

        val result = AmapParser.parse(input)
        NavStore.health.lastRule = result.rule
        NavStore.health.lastFailure = result.failure
        if (result.snapshotFields == null) {
            NavStore.addLog("notification_parser_rejected reason=${result.failure} rule=${result.rule}")
            val noData = NavSnapshot(
                seq = NavStore.nextSeq(),
                ts = System.currentTimeMillis(),
                source = "amap_notification",
                live = true,
                state = "no_data",
                maneuver = null,
                metersToTurn = null,
                road = null,
                parserRule = result.failure
            )
            NavStore.setCurrent(noData)
            WatchBridgeProvider.bridge.send(noData.toJson().toString())
            return
        }

        val fields = result.snapshotFields
        val snapshot = NavSnapshot(
            seq = NavStore.nextSeq(),
            ts = System.currentTimeMillis(),
            source = "amap_notification",
            live = true,
            state = if (fields.maneuver == "arrive") "arrive" else "navigating",
            maneuver = fields.maneuver,
            metersToTurn = fields.metersToTurn,
            road = fields.road,
            etaMinutes = fields.etaMinutes,
            parserRule = result.rule
        )
        NavStore.setCurrent(snapshot)
        val payload = snapshot.toJson().toString()
        NavStore.addLog("interconnect_send_attempt seq=${snapshot.seq}")
        if (!WatchBridgeProvider.bridge.send(payload)) {
            NavStore.addLog("interconnect_send_failure seq=${snapshot.seq}")
        }
    }

    private fun redact(value: String): String = value
        .replace(Regex("\\d{5,}"), "<number>")
        .replace(Regex("(地址|目的地|起点)[:：]?[^ |]+"), "$1:<redacted>")
        .take(240)
}
