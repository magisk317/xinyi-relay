package io.github.magisk317.relay.sender

import android.text.TextUtils
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.SocketSetting
import io.github.magisk317.relay.sender.SenderSettingSanitizer
import io.github.magisk317.xposed.logging.MagiskOtel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.nio.charset.Charset
import java.util.UUID
import java.util.Locale

object SocketUtils {
    private const val TAG = "SocketUtils"

    private fun emitSocket(method: String, statusOk: Boolean, reason: String, startedAt: Long) {
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        MagiskOtel.event(
            name = "sms.forward",
            attributes = mapOf(
                "result" to if (statusOk) "ok" else "error",
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to "socket_${method.lowercase()}",
                "reason" to reason,
            ),
            statusOk = statusOk,
        )
    }

    suspend fun sendMsg(setting: SocketSetting, msgInfo: MsgInfo) {
        val safeSetting = SenderSettingSanitizer.sanitizeSocketSetting(setting)
        val message = buildMessage(safeSetting, msgInfo)
        when (safeSetting.method.uppercase(Locale.ROOT)) {
            "TCP" -> sendTcp(safeSetting, message)
            "UDP" -> sendUdp(safeSetting, message)
            else -> sendMqtt(safeSetting, message)
        }
    }

    private suspend fun sendTcp(setting: SocketSetting, message: String) = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        runCatching {
            Socket(setting.address, setting.port).use { socket ->
                BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charset.forName(outputCharset(setting)))).use { out ->
                    out.write(message)
                    out.newLine()
                    out.flush()
                }
            }
            SLog.i(TAG, "TCP send success")
            emitSocket(method = "TCP", statusOk = true, reason = "sent", startedAt = startedAt)
        }.onFailure {
            SLog.e(TAG, "TCP send failed", it)
            emitSocket(
                method = "TCP",
                statusOk = false,
                reason = it.javaClass.simpleName.ifBlank { "send_failed" },
                startedAt = startedAt,
            )
        }.getOrElse { throw it }
    }

    private suspend fun sendUdp(setting: SocketSetting, message: String) = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        runCatching {
            DatagramSocket().use { socket ->
                val data = message.toByteArray(Charset.forName(outputCharset(setting)))
                val packet = DatagramPacket(data, data.size, InetAddress.getByName(setting.address), setting.port)
                socket.send(packet)
            }
            SLog.i(TAG, "UDP send success")
            emitSocket(method = "UDP", statusOk = true, reason = "sent", startedAt = startedAt)
        }.onFailure {
            SLog.e(TAG, "UDP send failed", it)
            emitSocket(
                method = "UDP",
                statusOk = false,
                reason = it.javaClass.simpleName.ifBlank { "send_failed" },
                startedAt = startedAt,
            )
        }.getOrElse { throw it }
    }

    private suspend fun sendMqtt(setting: SocketSetting, message: String) = withContext(Dispatchers.IO) {
        val uriType = if (setting.uriType.isBlank()) "tcp" else setting.uriType
        val path = setting.path
        val brokerUrl = if (path.isBlank()) {
            "$uriType://${setting.address}:${setting.port}"
        } else {
            "$uriType://${setting.address}:${setting.port}$path"
        }

        val clientId = if (setting.clientId.isBlank()) UUID.randomUUID().toString() else setting.clientId
        val client = MqttClient(brokerUrl, clientId, MemoryPersistence())

        val startedAt = System.nanoTime()
        runCatching {
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                if (setting.username.isNotBlank()) userName = setting.username
                if (setting.password.isNotBlank()) password = setting.password.toCharArray()
            }
            client.connect(options)
            val topic = setting.outMessageTopic.ifBlank { "relay/default" }
            val payload = message.toByteArray(Charset.forName(outputCharset(setting)))
            client.publish(topic, MqttMessage(payload).apply {
                qos = setting.qos
                isRetained = setting.retained
            })
            client.disconnect()
            SLog.i(TAG, "MQTT send success")
            emitSocket(method = "MQTT", statusOk = true, reason = "sent", startedAt = startedAt)
        }.onFailure {
            SLog.e(TAG, "MQTT send failed", it)
            runCatching { if (client.isConnected) client.disconnect() }
            emitSocket(
                method = "MQTT",
                statusOk = false,
                reason = it.javaClass.simpleName.ifBlank { "send_failed" },
                startedAt = startedAt,
            )
        }.getOrElse { throw it }
    }

    private fun buildMessage(setting: SocketSetting, msgInfo: MsgInfo): String {
        val template = if (TextUtils.isEmpty(setting.msgTemplate)) "{\"msg\":\"[msg]\"}" else setting.msgTemplate
        return SenderTemplateRenderer.render(template, msgInfo)
    }

    private fun outputCharset(setting: SocketSetting): String = if (setting.outCharset.isBlank()) "UTF-8" else setting.outCharset
}
