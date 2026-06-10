package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.MatrixSetting
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Date
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Authenticator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test

class MatrixUtilsTest {

    @Test
    fun buildSendUrl_encodesRoomIdAndTransactionId() {
        val url = MatrixUtils.buildSendUrl(
            homeserver = "https://matrix.example.com/",
            roomId = "!room/id:matrix.example.com",
            transactionId = "txn 1",
        )

        assertEquals(
            "https://matrix.example.com/_matrix/client/v3/rooms/!room%2Fid:matrix.example.com/send/m.room.message/txn%201",
            url,
        )
    }

    @Test
    fun buildMessageJson_textMode_usesPlainMatrixMessage() {
        val json = MatrixUtils.buildMessageJson(
            setting = MatrixSetting(
                messageType = "text",
                titleTemplate = "Relay",
            ),
            msgInfo = MsgInfo(
                type = "sms",
                from = "10086",
                content = "code 123456",
                date = Date(),
                simInfo = "SIM1",
            ),
        )

        assertEquals("""{"msgtype":"m.text","body":"Relay\ncode 123456"}""", json)
    }

    @Test
    fun buildMessageJson_markdownMode_addsFormattedBody() {
        val json = MatrixUtils.buildMessageJson(
            setting = MatrixSetting(
                messageType = "markdown",
                titleTemplate = "Relay <SMS>",
            ),
            msgInfo = MsgInfo(
                type = "sms",
                from = "10086",
                content = "**code** `123456` <raw>",
                date = Date(),
                simInfo = "SIM1",
            ),
        )

        assertEquals(
            SenderWireJson.encode(
                buildJsonObject {
                    put("msgtype", "m.text")
                    put("body", "Relay <SMS>\n**code** `123456` <raw>")
                    put("format", "org.matrix.custom.html")
                    put(
                        "formatted_body",
                        "<strong>Relay &lt;SMS&gt;</strong><br />" +
                            "<strong>code</strong> <code>123456</code> &lt;raw&gt;",
                    )
                },
            ),
            json,
        )
    }

    @Test
    fun buildClient_appliesProxySetting() {
        val client = MatrixUtils.buildClient(
            MatrixSetting(
                proxyType = Proxy.Type.SOCKS,
                proxyHost = "127.0.0.1",
                proxyPort = "7890",
                proxyAuthenticator = true,
                proxyUsername = "user",
                proxyPassword = "pass",
            ),
        )

        val proxy = client.proxy
        val address = proxy?.address() as InetSocketAddress
        assertEquals(Proxy.Type.SOCKS, proxy.type())
        assertEquals("127.0.0.1", address.hostString)
        assertEquals(7890, address.port)
        assertNotSame(Authenticator.NONE, client.proxyAuthenticator)
    }
}
