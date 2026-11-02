package com.hy.wristnav.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.hy.wristnav.bridge.WatchBridgeProvider
import com.hy.wristnav.navigation.NavProtocol
import com.hy.wristnav.navigation.NavSnapshot
import com.hy.wristnav.navigation.NavStore

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }
        val title = TextView(this).apply {
            text = "HY_WristNav V0.1"
            textSize = 24f
            setPadding(0, 0, 0, 18)
        }
        root.addView(title)
        status = TextView(this).apply { textSize = 14f }
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        buttons.addView(button("发送 DEMO") { sendDemo() })
        buttons.addView(button("发送 STOP") {
            val stopped = NavSnapshot(NavStore.nextSeq(), System.currentTimeMillis(), "manual_control", false, "stop", null, null, null)
            NavStore.setCurrent(stopped)
            send(stopped.toJson().toString())
        })
        buttons.addView(button("重连手表") { WatchBridgeProvider.bridge.reconnect() })
        buttons.addView(button("重发当前快照") { NavStore.current?.let { send(it.toJson().toString()) } })
        buttons.addView(button("打开通知监听设置") {
            startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        })
        root.addView(buttons)

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        gravity = Gravity.CENTER
        setOnClickListener { action() }
    }

    private fun sendDemo() {
        val snapshot = NavSnapshot(
            seq = NavStore.nextSeq(),
            ts = System.currentTimeMillis(),
            source = "manual_demo",
            live = false,
            state = "navigating",
            maneuver = "left",
            metersToTurn = 90,
            road = "示例道路（DEMO）",
            parserRule = null
        )
        NavStore.setCurrent(snapshot)
        send(snapshot.toJson().toString())
    }

    private fun send(payload: String) {
        if (!WatchBridgeProvider.bridge.send(payload)) {
            NavStore.addLog("send_not_confirmed")
        }
    }

    private fun render() {
        val enabled = runCatching {
            val raw = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
            raw.contains(packageName)
        }.getOrDefault(false)
        val snapshot = NavStore.current
        val age = snapshot?.let { (System.currentTimeMillis() - it.ts) / 1000 }
        status.text = buildString {
            appendLine("通知权限：${if (enabled) "已授权" else "未授权"}")
            appendLine("监听服务：${if (NavStore.health.connected) "已连接" else "未连接/未启动"}")
            appendLine("最后回调：${NavStore.health.lastCallbackMs ?: "--"}")
            appendLine("最近规则：${NavStore.health.lastRule ?: "--"}")
            appendLine("最近解析失败：${NavStore.health.lastFailure ?: "--"}")
            appendLine("最近通知（脱敏）：${NavStore.health.lastRedactedText ?: "--"}")
            appendLine("通信：${NavStore.lastTransport}")
            appendLine("手表渲染确认 seq：${NavStore.lastAck} / 最近发送：${NavStore.lastSentSeq}")
            appendLine("当前快照：${snapshot?.let { "${if (it.live) "LIVE" else "DEMO"} ${it.maneuver ?: "--"} ${it.metersToTurn ?: "--"}m" } ?: "无"}")
            appendLine("新鲜度：${age?.let { "${it}s" } ?: "--"}")
            appendLine()
            appendLine("日志：")
            NavStore.recentLogs().takeLast(20).forEach { appendLine(it) }
        }
    }
}
