package com.hy.wristnav.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.hy.wristnav.bridge.WatchBridgeProvider
import com.hy.wristnav.navigation.NavSnapshot
import com.hy.wristnav.navigation.NavStore
import com.hy.wristnav.notification.NotificationAccess

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var details: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var startAfterSettings = false
    private val refresh = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startAfterSettings = savedInstanceState?.getBoolean("startAfterSettings") ?: false
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "HY_WristNav 0.1.1"
            textSize = 24f
            setPadding(0, 0, 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "先在小米运动健康连接手表，再点击启动。首次使用按提示授权。"
            textSize = 16f
            setPadding(0, 0, 0, dp(12))
        })
        root.addView(button("启动 · 自动连接手表") {
            if (!NotificationAccess.isGranted(this)) {
                startAfterSettings = true
                openNotificationSettings()
            } else startConnection()
        })
        status = TextView(this).apply { textSize = 16f; setPadding(0, dp(12), 0, dp(12)) }
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(button("发送 DEMO · 左转 90 米") { sendDemo() })
        root.addView(button("发送 STOP · 清除导航") {
            val stopped = NavSnapshot(NavStore.nextSeq(), System.currentTimeMillis(), "manual_control", false, "stop", null, null, null)
            NavStore.setCurrent(stopped)
            send(stopped.toJson().toString())
        })
        root.addView(button("打开通知监听设置") { openNotificationSettings() })
        root.addView(button("复制诊断信息") {
            val text = diagnosticText()
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("HY_WristNav 诊断", text))
            Toast.makeText(this, "已复制诊断信息，导航通知内容默认遮蔽", Toast.LENGTH_SHORT).show()
        })
        details = TextView(this).apply { textSize = 14f; setTextIsSelectable(true); setPadding(0, dp(12), 0, 0) }
        root.addView(details)
        val scroll = ScrollView(this).apply { addView(root) }
        // Android 15 enforces edge-to-edge for target 35. Apply system bar insets explicitly.
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("startAfterSettings", startAfterSettings)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        NotificationAccess.recover(this)
        if (startAfterSettings) { startAfterSettings = false; startConnection() }
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun startConnection() {
        NotificationAccess.recover(this)
        WatchBridgeProvider.bridge.reconnect()
    }

    private fun openNotificationSettings() {
        runCatching { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
            .onFailure {
                startAfterSettings = false
                NavStore.notificationHint = "无法打开设置，请从系统设置搜索“通知使用权”"
                Toast.makeText(this, NavStore.notificationHint, Toast.LENGTH_LONG).show()
            }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setOnClickListener { action() }
    }

    private fun sendDemo() {
        val snapshot = NavSnapshot(
            seq = NavStore.nextSeq(), ts = System.currentTimeMillis(), source = "manual_demo",
            live = false, state = "navigating", maneuver = "left", metersToTurn = 90,
            road = "示例道路（DEMO）", parserRule = null
        )
        NavStore.setCurrent(snapshot)
        send(snapshot.toJson().toString())
    }

    private fun send(payload: String) {
        if (!WatchBridgeProvider.bridge.send(payload)) NavStore.addLog("send_not_confirmed")
    }

    private fun render() {
        val heartbeatAge = (System.currentTimeMillis() - NavStore.lastHeartbeatMs) / 1000
        status.text = buildString {
            appendLine("识别设备：${NavStore.deviceName}")
            appendLine("小米互联授权：${NavStore.interconnectPermission}")
            appendLine("通信：${NavStore.lastTransport}")
            appendLine("手表应用：${NavStore.watchAppStatus}")
            appendLine("手表回应：${if (NavStore.lastHeartbeatMs == 0L) "尚未收到" else "${heartbeatAge} 秒前"}")
            appendLine("通知使用权：${if (NotificationAccess.isGranted(this@MainActivity)) "已授权" else "未授权"}")
            appendLine("通知监听：${if (NavStore.health.connected) "已连接" else "未连接"}")
            if (!NavStore.health.connected) appendLine(NavStore.notificationHint)
            appendLine("手表确认 seq：${NavStore.lastAck} / 最近发送：${NavStore.lastSentSeq}")
            NavStore.current?.let {
                appendLine("当前数据：${if (it.live) "LIVE" else "DEMO"} / ${it.state} / ${it.metersToTurn ?: "--"} m")
            }
        }
        details.text = diagnosticText()
    }

    private fun diagnosticText(): String = buildString {
        appendLine("HY_WristNav 0.1.1")
        appendLine("手机：${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}")
        appendLine(status.text)
        appendLine("高德最后回调：${NavStore.health.lastCallbackMs ?: "--"}")
        appendLine("解析规则：${NavStore.health.lastRule ?: "--"}")
        appendLine("解析失败：${NavStore.health.lastFailure ?: "--"}")
        appendLine("最近通知（脱敏）：${NavStore.health.lastRedactedText ?: "--"}")
        appendLine("日志：")
        NavStore.recentLogs().takeLast(20).forEach { appendLine(it) }
    }
}
