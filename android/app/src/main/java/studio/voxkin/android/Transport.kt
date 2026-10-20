package studio.voxkin.android

import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject

class GatewayFailure(message: String) : IOException(message)

interface Transport {
    suspend fun request(method: String, params: JSONObject = obj()): JSONObject

    fun close()
}

fun httpClient() =
    OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

class OwnerTransport(
    private val event: (String, JSONObject) -> Unit,
    private val lost: () -> Unit,
) : Transport {
    internal var client = httpClient()
    private var socket: WebSocket? = null
    private val challenge = CompletableDeferred<JSONObject>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private var active = true

    suspend fun connect(url: String, identity: Identity, token: String): JSONObject {
        Protocol.endpoint(url)
        socket =
            client.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onMessage(ws: WebSocket, text: String) {
                        receive(text)
                    }

                    override fun onMessage(ws: WebSocket, bytes: ByteString) {
                        if (bytes.size > 9_000_000) close() else receive(bytes.utf8())
                    }

                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                        close()
                    }

                    override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                        close()
                    }
                },
            )
        val c = withTimeout(15000) { challenge.await() }
        val ts = c.opt("ts")
        require(ts is Number && ts.toDouble() >= 0 && ts.toDouble() % 1.0 == 0.0)
        val scopes = listOf("operator.read", "operator.write")
        val h =
            request(
                "connect",
                obj(
                    "minProtocol" to 4,
                    "maxProtocol" to 4,
                    "client" to
                        obj(
                            "id" to "openclaw-android",
                            "displayName" to "Voxkin Android",
                            "version" to "0.1.0",
                            "platform" to "android",
                            "deviceFamily" to "phone",
                            "mode" to "ui",
                        ),
                    "role" to "operator",
                    "scopes" to JSONArray(scopes),
                    "caps" to JSONArray(),
                    "auth" to obj("token" to token),
                    "device" to identity.device(c.getString("nonce"), ts.toLong(), token, scopes),
                ),
            )
        require(h.optString("type") == "hello-ok" && h.optInt("protocol") == 4)
        val a = h.optJSONObject("auth") ?: error("Missing grant")
        require(a.optString("role") == "operator" && a.strings("scopes").containsAll(scopes))
        return h
    }

    private fun receive(text: String) {
        if (!active) return
        if (text.length > 9_000_000) {
            close()
            return
        }
        try {
            val f = JSONObject(text)
            if (f.optString("type") == "event") {
                val n = f.optString("event")
                val p = f.optJSONObject("payload") ?: obj()
                if (n == "connect.challenge") challenge.complete(p) else event(n, p)
            }
            if (f.optString("type") == "res")
                pending.remove(f.optString("id"))?.let { p ->
                    if (f.optBoolean("ok")) p.complete(f.optJSONObject("payload") ?: obj())
                    else {
                        val e = f.optJSONObject("error")
                        val code =
                            e?.optJSONObject("details")?.optString("code") ?: e?.optString("code")
                        p.completeExceptionally(
                            GatewayFailure(
                                if (code in listOf("PAIRING_REQUIRED", "NOT_PAIRED"))
                                    "Approve this phone in the gateway device list, then reconnect."
                                else
                                    "Gateway rejected request. Check access and method support. Nothing was replayed."
                            )
                        )
                    }
                }
        } catch (_: Exception) {
            close()
        }
    }

    override suspend fun request(method: String, params: JSONObject): JSONObject {
        check(active && pending.size < 8)
        val id = UUID.randomUUID().toString()
        val p = CompletableDeferred<JSONObject>()
        val wire =
            obj("type" to "req", "id" to id, "method" to method, "params" to params).toString()
        require(wire.toByteArray().size < 64000)
        pending[id] = p
        try {
            check(socket?.send(wire) == true)
            return withTimeout(60000) { p.await() }
        } finally {
            pending.remove(id)
        }
    }

    override fun close() {
        if (!active) return
        active = false
        socket?.cancel()
        socket = null
        challenge.completeExceptionally(GatewayFailure("Connection ended before handshake."))
        pending.values.forEach {
            it.completeExceptionally(
                GatewayFailure("Connection lost; reconnect manually. Nothing was replayed.")
            )
        }
        pending.clear()
        client.dispatcher.cancelAll()
        lost()
    }
}

class RestrictedTransport(
    url: String,
    private val token: String,
    private val scope: CoroutineScope,
    private val event: (String, JSONObject) -> Unit,
) : Transport {
    private val origin: java.net.URI = studio.voxkin.android.Protocol.endpoint(url, true)
    private val endpoint: String =
        java.net.URI("https", null, origin.host, origin.port, "/voxkin/v1", null, null).toString()
    internal var client = httpClient()
    private var closed = false
    private var poll: Job? = null
    private val runs = mutableMapOf<String, String>()
    var agent = ""
        private set

    suspend fun call(body: JSONObject): JSONObject {
        check(!closed)
        val call =
            client.newCall(
                Request.Builder()
                    .url(endpoint)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }
                    .build()
            )
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive)
                            cont.resumeWithException(
                                GatewayFailure("Connection failed; nothing was replayed.")
                            )
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { r ->
                            try {
                                check(
                                    r.code == 200 && r.request.url.toString() == endpoint && !closed
                                )
                                val body = r.body ?: error("Missing response")
                                require(body.contentLength() < 9_000_000)
                                val source = body.source()
                                require(!source.request(9_000_001))
                                val bytes = source.readByteArray()
                                require(bytes.size <= 9_000_000)
                                val result = JSONObject(String(bytes))
                                if (cont.isActive) cont.resume(result)
                            } catch (_: Exception) {
                                if (cont.isActive)
                                    cont.resumeWithException(
                                        GatewayFailure(
                                            "Restricted request rejected. Check owner approval and plugin availability. Nothing was replayed."
                                        )
                                    )
                            }
                        }
                    }
                }
            )
        }
    }

    suspend fun connect(): JSONObject {
        val h = call(obj("op" to "hello"))
        require(h.optInt("version") == 1 && h.optString("agentId").isNotBlank())
        agent = h.getString("agentId")
        return h
    }

    override suspend fun request(method: String, params: JSONObject): JSONObject =
        when (method) {
            "agents.list" -> obj("agents" to JSONArray().put(obj("id" to agent)))
            "chat.send" -> {
                require(params.getString("agentId") == agent)
                val session = params.getString("sessionKey")
                require(Protocol.owns(session, agent))
                val run = params.getString("idempotencyKey")
                val r =
                    call(
                        obj(
                            "op" to "send",
                            "text" to params.getString("message"),
                            "requestId" to run,
                            "conversationId" to session.substringAfterLast(':'),
                        )
                    )
                val server = r.getString("runId")
                runs[run] = server
                poll?.cancel()
                poll =
                    scope.launch {
                        try {
                            withTimeout(120000) {
                                while (isActive) {
                                    val p = call(obj("op" to "poll", "runId" to server))
                                    val state = p.optString("state")
                                    event(
                                        "chat",
                                        obj(
                                            "runId" to run,
                                            "sessionKey" to session,
                                            "state" to state,
                                            "message" to
                                                obj(
                                                    "role" to "assistant",
                                                    "content" to p.optString("text"),
                                                ),
                                        ),
                                    )
                                    if (state in listOf("final", "error", "aborted")) break
                                    delay(750)
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            event(
                                "chat",
                                obj("runId" to run, "sessionKey" to session, "state" to "error"),
                            )
                        } finally {
                            runs.remove(run)
                        }
                    }
                obj("runId" to run)
            }
            "chat.abort" ->
                call(
                    obj(
                        "op" to "abort",
                        "runId" to (runs[params.getString("runId")] ?: error("No active run")),
                    )
                )
            "voxkin.catalog",
            "voxkin.speak",
            "voxkin.history.list",
            "voxkin.history.create",
            "voxkin.history.messages",
            "voxkin.history.rename",
            "voxkin.history.delete" ->
                call(JSONObject(params.toString()).put("op", method.removePrefix("voxkin.")))
            else -> error("Operation unavailable to restricted phones")
        }

    override fun close() {
        closed = true
        poll?.cancel()
        client.dispatcher.cancelAll()
        runs.clear()
    }
}
