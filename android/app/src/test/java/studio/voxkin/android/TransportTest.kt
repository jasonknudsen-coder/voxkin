package studio.voxkin.android

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TransportTest {
    private fun fixture(block: (MockWebServer, OkHttpClient, String) -> Unit) {
        val cert =
            HeldCertificate.Builder()
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .build()
        val server = MockWebServer()
        server.useHttps(
            HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory(),
            false,
        )
        server.start()
        val trusted =
            HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val client =
            httpClient()
                .newBuilder()
                .sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager)
                .build()
        try {
            block(server, client, "wss://localhost:${server.port}")
        } finally {
            server.shutdown()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun restrictedRedirectDoesNotForwardBearerOrReplay() = fixture { server, client, url ->
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "https://localhost:${server.port}/elsewhere")
            )
            val t = RestrictedTransport(url, "synthetic", this) { _, _ -> }
            t.client = client
            assertTrue(runCatching { t.call(obj("op" to "hello")) }.isFailure)
            val r = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/voxkin/v1", r.path)
            assertEquals("Bearer synthetic", r.getHeader("Authorization"))
            assertEquals(1, server.requestCount)
            t.close()
        }
    }

    @Test
    fun restrictedPollingEmitsOnlyMatchingRunAndSession() = fixture { server, client, url ->
        runBlocking {
            server.enqueue(
                MockResponse().setBody("{\"version\":1,\"agentId\":\"guest\",\"history\":true}")
            )
            server.enqueue(MockResponse().setBody("{\"runId\":\"server-owned-run\"}"))
            server.enqueue(MockResponse().setBody("{\"state\":\"final\",\"text\":\"Safe answer\"}"))
            val event = CompletableDeferred<JSONObject>()
            val t =
                RestrictedTransport(url, "synthetic", this) { name, p ->
                    if (name == "chat") event.complete(p)
                }
            t.client = client
            t.connect()
            val session = "agent:guest:voxkin:11111111-2222-4333-8444-555555555555"
            t.request(
                "chat.send",
                obj(
                    "agentId" to "guest",
                    "sessionKey" to session,
                    "idempotencyKey" to "client-run",
                    "message" to "hello",
                ),
            )
            val p = withTimeout(3000) { event.await() }
            assertEquals("client-run", p.getString("runId"))
            assertEquals(session, p.getString("sessionKey"))
            assertEquals("Safe answer", Protocol.finalText(p.getJSONObject("message")))
            val requests = (1..3).map { server.takeRequest(2, TimeUnit.SECONDS)!! }
            val send = JSONObject(requests[1].body.readUtf8())
            assertEquals("11111111-2222-4333-8444-555555555555", send.getString("conversationId"))
            assertFalse(send.has("agentId"))
            assertEquals(
                "server-owned-run",
                JSONObject(requests[2].body.readUtf8()).getString("runId"),
            )
            t.close()
        }
    }

    @Test
    fun restrictedRejectsCrossAgentSendBeforeNetwork() = fixture { server, client, url ->
        runBlocking {
            server.enqueue(MockResponse().setBody("{\"version\":1,\"agentId\":\"guest\"}"))
            val t = RestrictedTransport(url, "synthetic", this) { _, _ -> }
            t.client = client
            t.connect()
            assertTrue(
                runCatching {
                        t.request(
                            "chat.send",
                            obj(
                                "agentId" to "other",
                                "sessionKey" to
                                    "agent:other:voxkin:11111111-2222-4333-8444-555555555555",
                                "idempotencyKey" to "run",
                                "message" to "hello",
                            ),
                        )
                    }
                    .isFailure
            )
            assertEquals(1, server.requestCount)
            t.close()
        }
    }

    @Test fun restrictedMissingFlagsRejectHistoryWithoutHttp() = fixture { server, client, url ->
        runBlocking {
            server.enqueue(MockResponse().setBody("{\"version\":1,\"agentId\":\"guest\"}"))
            val t = RestrictedTransport(url, "synthetic", this) { _, _ -> }
            t.client = client
            t.connect()
            for (method in listOf("list", "create", "messages", "rename", "delete"))
                assertTrue(runCatching { t.request("voxkin.history.$method", obj()) }.exceptionOrNull() is GatewayFailure)
            assertEquals(1, server.requestCount)
            t.close()
        }
    }

    @Test fun ownerHandshakeUsesAndroidIdentityAndHasNoReplay() = ownerHandshake(false)
    @Test fun adminScopeIsRequestedOnlyWhenExplicitAndReadWriteFallbackStillConnects() = ownerHandshake(true)
    private fun ownerHandshake(admin: Boolean) = fixture { server, client, url ->
        runBlocking {
            val received = CompletableDeferred<JSONObject>()
            server.enqueue(
                MockResponse()
                    .withWebSocketUpgrade(
                        object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                webSocket.send(
                                    obj(
                                            "type" to "event",
                                            "event" to "connect.challenge",
                                            "payload" to obj("nonce" to "test-nonce", "ts" to 1234),
                                        )
                                        .toString()
                                )
                            }

                            override fun onMessage(webSocket: WebSocket, text: String) {
                                val r = JSONObject(text)
                                received.complete(r)
                                webSocket.send(
                                    obj(
                                            "type" to "res",
                                            "id" to r.getString("id"),
                                            "ok" to true,
                                            "payload" to
                                                obj(
                                                    "type" to "hello-ok",
                                                    "protocol" to 4,
                                                    "auth" to
                                                        obj(
                                                            "role" to "operator",
                                                            "scopes" to
                                                                JSONArray(
                                                                    listOf(
                                                                        "operator.read",
                                                                        "operator.write",
                                                                    )
                                                                ),
                                                        ),
                                                ),
                                        )
                                        .toString()
                                )
                            }
                        }
                    )
            )
            val t = OwnerTransport({ _, _ -> }, {})
            t.client = client
            t.connect(url, Identity(ByteArray(32) { it.toByte() }), "synthetic", requestAdmin = admin)
            val p = withTimeout(3000) { received.await() }.getJSONObject("params")
            assertEquals("openclaw-android", p.getJSONObject("client").getString("id"))
            assertEquals("android", p.getJSONObject("client").getString("platform"))
            assertEquals(4, p.getInt("maxProtocol"))
            assertEquals(admin, p.getJSONArray("scopes").toString().contains("operator.admin"))
            t.close()
            assertTrue(runCatching { t.request("chat.send") }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }
}
