package com.hy.wristnav.bridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.hy.wristnav.bridge.ConnectionGate.Stage
import com.hy.wristnav.navigation.NavStore
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import com.xiaomi.xms.wearable.service.OnServiceConnectionListener
import org.json.JSONObject

/** Official XMS 1.4: discover -> DEVICE_MANAGER authorization -> message listener. */
class XiaomiWatchBridge(context: Context) : WatchBridge {
    private val main = Handler(Looper.getMainLooper())
    private val nodeApi = Wearable.getNodeApi(context.applicationContext)
    private val authApi = Wearable.getAuthApi(context.applicationContext)
    private val messageApi = Wearable.getMessageApi(context.applicationContext)
    private val serviceApi = Wearable.getServiceApi(context.applicationContext)
    private val gate = ConnectionGate()
    private var enabled = false
    private var nodeId: String? = null
    private var pending: String? = null
    private var sending = false
    private var sendToken = 0

    private val incoming = OnMessageReceivedListener { from, bytes ->
        main.post {
            if (from != nodeId || gate.stage != Stage.READY || bytes == null || bytes.size > 8192) return@post
            val raw = String(bytes, Charsets.UTF_8)
            val message = runCatching { JSONObject(raw) }.getOrNull() ?: return@post
            if (message.optInt("v") != 1) return@post
            NavStore.lastHeartbeatMs = System.currentTimeMillis()
            if (message.optString("t") == "ack") {
                NavStore.lastAck = message.optLong("seq", -1)
                NavStore.addLog("watch_render_ack seq=${NavStore.lastAck}")
            } else WatchMessageHandler.handle(raw)?.let { send(it) }
        }
    }

    init {
        NavStore.setTransport("点击启动，自动识别运动健康已连接的手表")
        serviceApi.registerServiceConnectionListener(object : OnServiceConnectionListener {
            override fun onServiceConnected() {
                main.post { if (enabled) discover(false) }
            }
            override fun onServiceDisconnected() {
                main.post {
                    gate.disconnected()
                    nodeId = null
                    sending = false
                    NavStore.transportReady = false
                    NavStore.lastHeartbeatMs = 0
                    NavStore.setTransport("运动健康服务已断开；连接恢复后自动重试，也可点击启动")
                }
            }
        })
    }

    override fun reconnect() {
        main.post { enabled = true; discover(true) }
    }

    override fun send(payload: String): Boolean {
        if (payload.toByteArray(Charsets.UTF_8).size > 8192) return false
        main.post {
            pending = payload
            when {
                !enabled -> NavStore.setTransport("数据已准备；请先点击启动")
                gate.stage == Stage.READY -> flush()
                gate.stage != Stage.FAILED -> discover(false)
            }
        }
        return true // Queued, not delivered. Only SDK success and watch ACK confirm delivery.
    }

    private fun discover(userInitiated: Boolean) {
        val attempt = gate.start(userInitiated) ?: return
        nodeId?.let { runCatching { messageApi.removeListener(it) } }
        nodeId = null
        sending = false
        NavStore.transportReady = false
        NavStore.lastHeartbeatMs = 0
        NavStore.deviceName = "正在识别"
        NavStore.interconnectPermission = "待检查"
        NavStore.watchAppStatus = "等待通信授权"
        status(attempt, Stage.DISCOVERING, "正在查找运动健康已连接的手表")
        nodeApi.connectedNodes.addOnSuccessListener { nodes ->
            main.post {
                if (!gate.matches(attempt, Stage.DISCOVERING)) return@post
                if (nodes.size != 1) {
                    NavStore.deviceName = if (nodes.isEmpty()) "未发现设备" else "检测到 ${nodes.size} 个设备"
                    fail(attempt, "设备查询", if (nodes.isEmpty()) "请在小米运动健康确认手表已连接" else "请仅连接要使用的手表")
                    return@post
                }
                nodeId = nodes[0].id
                NavStore.deviceName = nodes[0].name?.takeIf { it.isNotBlank() } ?: "已识别一个设备"
                gate.advance(attempt, Stage.DISCOVERING, Stage.CHECKING)
                checkPermission(attempt, userInitiated)
            }
        }.addOnFailureListener { error -> main.post { fail(attempt, "设备查询", detail(error)) } }
    }

    private fun checkPermission(attempt: Int, userInitiated: Boolean) {
        val id = nodeId ?: return
        status(attempt, Stage.CHECKING, "已识别设备；正在检查小米互联授权")
        authApi.checkPermission(id, Permission.DEVICE_MANAGER).addOnSuccessListener { granted ->
            main.post {
                if (!gate.matches(attempt, Stage.CHECKING)) return@post
                if (granted == true) {
                    NavStore.interconnectPermission = "已授权"
                    gate.advance(attempt, Stage.CHECKING, Stage.REGISTERING)
                    register(attempt, userInitiated)
                } else if (userInitiated) {
                    gate.advance(attempt, Stage.CHECKING, Stage.AUTHORIZING)
                    requestPermission(attempt)
                } else {
                    NavStore.interconnectPermission = "未授权"
                    fail(attempt, "小米互联授权", "请回到此页面点击启动完成授权")
                }
            }
        }.addOnFailureListener { error -> main.post { fail(attempt, "授权查询", detail(error)) } }
    }

    private fun requestPermission(attempt: Int) {
        val id = nodeId ?: return
        NavStore.interconnectPermission = "正在申请"
        status(attempt, Stage.AUTHORIZING, "正在申请小米互联授权；如出现提示请确认", 60000)
        authApi.requestPermission(id, Permission.DEVICE_MANAGER).addOnSuccessListener { granted ->
            main.post {
                if (!gate.matches(attempt, Stage.AUTHORIZING)) return@post
                if (granted?.contains(Permission.DEVICE_MANAGER) == true) {
                    NavStore.interconnectPermission = "已授权"
                    gate.advance(attempt, Stage.AUTHORIZING, Stage.REGISTERING)
                    register(attempt, true)
                } else {
                    NavStore.interconnectPermission = "未获授权"
                    fail(attempt, "小米互联授权", "运动健康未授予设备管理权限；确认后点击启动重试")
                }
            }
        }.addOnFailureListener { error -> main.post {
            if (gate.matches(attempt, Stage.AUTHORIZING)) {
                NavStore.interconnectPermission = "申请失败"
                fail(attempt, "授权申请", detail(error))
            }
        } }
    }

    private fun register(attempt: Int, openWatch: Boolean) {
        val id = nodeId ?: return
        status(attempt, Stage.REGISTERING, "小米互联已授权；正在注册消息接收")
        messageApi.addListener(id, incoming).addOnSuccessListener {
            main.post {
                if (!gate.advance(attempt, Stage.REGISTERING, Stage.READY)) return@post
                NavStore.transportReady = true
                NavStore.setTransport("接收已注册；等待手表回应")
                if (openWatch) openWatchApp(attempt, id)
                if (pending == null) pending = WatchMessageHandler.handle("{\"t\":\"request_snapshot\"}")
                flush()
            }
        }.addOnFailureListener { error -> main.post {
            if (gate.matches(attempt, Stage.REGISTERING)) {
                if (error.javaClass.simpleName == "PermissionDeniedException") NavStore.interconnectPermission = "SDK 已返回授权，但消息接口仍拒绝"
                fail(attempt, "注册接收", detail(error))
            }
        } }
    }

    private fun openWatchApp(attempt: Int, id: String) {
        NavStore.watchAppStatus = "正在检查安装状态"
        nodeApi.isWearAppInstalled(id).addOnSuccessListener { installed -> main.post {
            if (!gate.matches(attempt, Stage.READY)) return@post
            if (installed != true) {
                NavStore.watchAppStatus = "未检测到配套应用，请安装本次 RPK"
            } else {
                NavStore.watchAppStatus = "已安装；正在打开"
                nodeApi.launchWearApp(id, "/pages/index").addOnSuccessListener { main.post {
                    if (gate.matches(attempt, Stage.READY)) NavStore.watchAppStatus = "已发送打开请求；等待手表回应"
                } }.addOnFailureListener { error -> main.post {
                    if (gate.matches(attempt, Stage.READY)) NavStore.watchAppStatus = "请手动打开 HY_WristNav：${detail(error)}"
                } }
            }
        } }.addOnFailureListener { error -> main.post {
            if (gate.matches(attempt, Stage.READY)) NavStore.watchAppStatus = "安装检查失败，请手动打开：${detail(error)}"
        } }
        main.postDelayed({
            if (gate.matches(attempt, Stage.READY) && NavStore.lastHeartbeatMs == 0L)
                NavStore.watchAppStatus = "未收到手表回应，请手动打开 HY_WristNav 后发送 DEMO"
        }, 15000)
    }

    private fun flush() {
        if (gate.stage != Stage.READY || sending) return
        val id = nodeId ?: return
        val payload = pending ?: return
        pending = null
        sending = true
        val token = ++sendToken
        val attempt = gate.generation
        val seq = runCatching { JSONObject(payload).optLong("seq", -1) }.getOrDefault(-1)
        NavStore.addLog("interconnect_send_attempt seq=$seq")
        main.postDelayed({
            if (gate.matches(attempt, Stage.READY) && sending && token == sendToken) {
                if (pending == null) pending = payload
                fail(attempt, "发送", "等待 SDK 回调超时，请点击启动重试")
            }
        }, 15000)
        messageApi.sendMessage(id, payload.toByteArray(Charsets.UTF_8)).addOnSuccessListener {
            main.post {
                if (!gate.matches(attempt, Stage.READY)) return@post
                sending = false
                NavStore.lastSentSeq = seq
                NavStore.setTransport("发送成功；等待手表确认")
                flush()
            }
        }.addOnFailureListener { error -> main.post {
            if (!gate.matches(attempt, Stage.READY)) return@post
            if (pending == null) pending = payload
            fail(attempt, "发送", detail(error))
        } }
    }

    private fun status(attempt: Int, stage: Stage, text: String, timeout: Long = 15000) {
        NavStore.setTransport(text)
        main.postDelayed({
            if (gate.matches(attempt, stage)) fail(attempt, text, "等待超时；请点击启动重试")
        }, timeout)
    }

    private fun fail(attempt: Int, stage: String, reason: String) {
        if (!gate.fail(attempt)) return
        sending = false
        NavStore.transportReady = false
        NavStore.lastHeartbeatMs = 0
        NavStore.setTransport("$stage 失败：$reason")
    }

    private fun detail(error: Exception): String = error.javaClass.simpleName +
        (error.message?.replace(Regex("[\\r\\n\\t]+"), " ")?.take(180)?.let { ": $it" } ?: "（SDK 未提供详细原因）")
}
