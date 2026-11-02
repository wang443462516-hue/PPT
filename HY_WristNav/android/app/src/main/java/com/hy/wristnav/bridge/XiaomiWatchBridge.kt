package com.hy.wristnav.bridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.hy.wristnav.navigation.NavStore
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import com.xiaomi.xms.wearable.service.OnServiceConnectionListener
import org.json.JSONObject

/** Official Xiaomi XMS 1.4 adapter. True from send means queued, not delivered. */
class XiaomiWatchBridge(context: Context) : WatchBridge {
    private val main = Handler(Looper.getMainLooper())
    private val nodeApi = Wearable.getNodeApi(context.applicationContext)
    private val messageApi = Wearable.getMessageApi(context.applicationContext)
    private val serviceApi = Wearable.getServiceApi(context.applicationContext)
    private var nodeId: String? = null
    private var discovering = false
    private var pending: String? = null
    private var registering = false
    private var generation = 0
    private val incoming = OnMessageReceivedListener { from, bytes ->
        main.post {
            if (from != nodeId || bytes == null || bytes.size > 8192) return@post
            val raw = String(bytes, Charsets.UTF_8)
            val message = runCatching { JSONObject(raw) }.getOrNull() ?: return@post
            if (message.optString("t") == "ack") {
                NavStore.lastAck = message.optLong("seq", -1)
                NavStore.lastHeartbeatMs = System.currentTimeMillis()
                NavStore.addLog("watch_render_ack seq=${NavStore.lastAck}")
            } else WatchMessageHandler.handle(raw)?.let { send(it) }
        }
    }

    init {
        serviceApi.registerServiceConnectionListener(object : OnServiceConnectionListener {
            override fun onServiceConnected() { reconnect() }
            override fun onServiceDisconnected() {
                main.post {
                    generation++
                    nodeId = null
                    discovering = false
                    registering = false
                    NavStore.setTransport("小米运动健康服务已断开")
                }
            }
        })
        reconnect()
    }

    override fun reconnect() { main.post { discover() } }

    override fun send(payload: String): Boolean {
        if (payload.toByteArray(Charsets.UTF_8).size > 8192) {
            NavStore.setTransport("发送拒绝：消息超过诊断版大小上限")
            return false
        }
        main.post {
            pending = payload
            if (nodeId == null || registering) discover() else flush()
        }
        return true
    }

    private fun discover() {
        if (discovering || registering) return
        discovering = true
        val attempt = ++generation
        NavStore.setTransport("正在查找已连接手表")
        main.postDelayed({
            if ((discovering || registering) && generation == attempt) {
                discovering = false
                registering = false
                nodeId = null
                generation++
                NavStore.setTransport("设备查询超时；请重连")
            }
        }, 10000)
        nodeApi.connectedNodes.addOnSuccessListener { nodes ->
            main.post discovered@ {
                if (attempt != generation) return@discovered
                discovering = false
                if (nodes.size != 1) {
                    nodeId = null
                    NavStore.setTransport(if (nodes.isEmpty()) "没有已连接手表" else "多个设备在线，请只保留目标手表连接")
                    return@discovered
                }
                val next = nodes[0].id
                nodeId?.takeIf { it != next }?.let { messageApi.removeListener(it) }
                nodeId = next
                registering = true
                messageApi.addListener(next, incoming).addOnSuccessListener {
                    main.post registered@ {
                        if (attempt != generation || nodeId != next) return@registered
                        registering = false
                        NavStore.setTransport("接收已注册；等待手表确认")
                        flush()
                    }
                }.addOnFailureListener { error ->
                    main.post failed@ {
                        if (attempt != generation) return@failed
                        registering = false
                        nodeId = null
                        fail("注册接收", error)
                    }
                }
            }
        }.addOnFailureListener { error ->
            main.post {
                if (attempt != generation) return@post
                discovering = false
                nodeId = null
                fail("设备查询", error)
            }
        }
    }

    private fun flush() {
        val id = nodeId ?: return
        val payload = pending ?: return
        pending = null
        val seq = runCatching { JSONObject(payload).optLong("seq", -1) }.getOrDefault(-1)
        NavStore.addLog("interconnect_send_attempt seq=$seq")
        messageApi.sendMessage(id, payload.toByteArray(Charsets.UTF_8)).addOnSuccessListener {
            NavStore.lastSentSeq = seq
            NavStore.setTransport("发送成功 seq=$seq；等待手表 ACK")
        }.addOnFailureListener { error ->
            main.post { nodeId = null; fail("发送 seq=$seq", error) }
        }
    }

    private fun fail(stage: String, error: Exception) {
        NavStore.setTransport("$stage 失败：${error.javaClass.simpleName}")
    }
}
