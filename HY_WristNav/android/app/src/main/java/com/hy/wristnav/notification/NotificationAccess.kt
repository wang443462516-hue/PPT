package com.hy.wristnav.notification

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.hy.wristnav.navigation.NavStore

object NotificationAccess {
    private var lastRebindAt = -15000L

    fun isGranted(context: Context): Boolean {
        val component = ComponentName(context, AmapNotificationListener::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    fun recover(context: Context) {
        if (!isGranted(context)) {
            NavStore.notificationHint = "未获通知使用权；真实导航需要授权，DEMO 不受影响"
            return
        }
        if (NavStore.health.connected) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRebindAt < 15000) return
        lastRebindAt = now
        runCatching {
            NotificationListenerService.requestRebind(ComponentName(context, AmapNotificationListener::class.java))
        }.onSuccess {
            NavStore.notificationHint = "已请求系统重新绑定；若持续未连接，请在通知监听设置关闭后重新开启"
            NavStore.addLog("notification_rebind_requested")
        }.onFailure {
            NavStore.notificationHint = "重新绑定失败：${it.javaClass.simpleName}；请重新开启通知使用权"
        }
    }
}
