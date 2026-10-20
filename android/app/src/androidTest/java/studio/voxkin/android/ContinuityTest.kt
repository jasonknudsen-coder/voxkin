package studio.voxkin.android

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ContinuityTest {
    private fun state(model: VoiceModel, name: String, value: Any) {
        @Suppress("UNCHECKED_CAST")
        val field = VoiceModel::class.java.getDeclaredField(name + "$" + "delegate")
            .apply { isAccessible = true }.get(model) as MutableState<Any>
        field.value = value
    }
    private fun transport(model: VoiceModel, request: (String, JSONObject) -> JSONObject) {
        VoiceModel::class.java.getDeclaredField("transport").apply { isAccessible = true }
            .set(model, object : Transport {
                override suspend fun request(method: String, params: JSONObject) = request(method, params)
                override fun close() {}
            })
    }
    private fun fixture(block: (VoiceModel, SecureStore, String) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val model = VoiceModel(app)
            val store = SecureStore(app)
            val gateway = "wss://continuity-${UUID.randomUUID()}.example.com"
            model.gateway = gateway
            model.restricted = false
            state(model, "agent", "main")
            state(model, "connected", true)
            model.syntheticHistory()
            try { block(model, store, gateway) } finally {
                model.forget()
                model.restricted = true
                model.forget()
            }
        }
    }

    @Test fun missingReadStartsFreshOwnerWithoutOverwritingSavedPointer() = fixture { model, store, gateway ->
        val saved = UUID.randomUUID().toString()
        store.put("history:owner:$gateway", obj("main" to saved).toString())
        model.syntheticHistory(HistoryCapabilities.owner(emptyList()))
        transport(model) { _, _ -> error("No unsupported request allowed") }
        runBlocking { model.restoreConversation() }
        assertTrue(model.conversationReady)
        assertNotEquals("agent:main:voxkin:$saved", model.selectedConversationId)
        assertEquals(saved, JSONObject(store.get("history:owner:$gateway")!!).getString("main"))
        assertTrue(model.status.contains("Saved history is unavailable"))
    }

    @Test fun restrictedMissingHistoryCannotCreateReadOrSend() = fixture { model, _, _ ->
        model.restricted = true
        model.syntheticHistory(HistoryCapabilities.restricted(false, false))
        var calls = 0
        transport(model) { _, _ -> calls++; obj() }
        runBlocking { model.restoreConversation() }
        assertFalse(model.conversationReady)
        model.newConversation()
        model.listHistory()
        model.draft = "Do not send"
        model.send()
        assertEquals(0, calls)
        model.disconnect()
        assertEquals(HistoryCapabilities(), model.historyCapabilities)
    }

    @Test fun restoreReadsHistoryWithoutSendingOrSpeakingAndPreservesDraft() = fixture { model, store, gateway ->
        val id = UUID.randomUUID().toString()
        store.put("history:owner:$gateway", obj("main" to id).toString())
        VoiceModel::class.java.getDeclaredField("sessionId").apply { isAccessible = true }.set(model, id)
        model.draft = "Unsent draft"
        model.speechConsent = true
        val calls = mutableListOf<String>()
        transport(model) { method, params ->
            calls += method
            assertEquals("agent:main:voxkin:$id", params.getString("sessionKey"))
            obj("messages" to JSONArray().put(obj("role" to "user", "text" to "Question"))
                .put(obj("role" to "assistant", "text" to "Existing answer")))
        }
        runBlocking { model.restoreConversation() }
        assertEquals(listOf("chat.history"), calls)
        assertEquals("Existing answer", model.messages.last().text)
        assertEquals("Unsent draft", model.draft)
        assertTrue(model.conversationReady)
        assertFalse(model.speaking)
        assertFalse(model.recording)
        assertFalse(model.requestPending)
    }

    @Test fun historyIsScopedToEndpointModeAndAgent() = fixture { model, store, gateway ->
        val owner = UUID.randomUUID().toString()
        val guest = UUID.randomUUID().toString()
        val alternate = UUID.randomUUID().toString()
        store.put("history:owner:$gateway", obj("main" to owner, "alternate" to alternate).toString())
        store.put("history:guest:$gateway", obj("main" to guest).toString())
        val targets = mutableListOf<String>()
        transport(model) { method, params ->
            targets += if (method == "chat.history") params.getString("sessionKey") else params.getString("conversationId")
            obj("messages" to JSONArray())
        }
        runBlocking {
            model.restoreConversation()
            state(model, "agent", "alternate")
            model.restoreConversation()
            state(model, "agent", "main")
            model.restricted = true
            model.restoreConversation()
            // Another endpoint has no saved owner history; it must not read the old ID.
            model.restricted = false
            model.gateway = "wss://other-${UUID.randomUUID()}.example.com"
            model.restoreConversation()
            model.forget()
            model.gateway = gateway
        }
        assertEquals(listOf("agent:main:voxkin:$owner", "agent:alternate:voxkin:$alternate", guest), targets)
    }

    @Test fun deniedOrMalformedHistoryNeverFallsThroughToSend() = fixture { model, store, gateway ->
        val saved = UUID.randomUUID().toString()
        store.put("history:owner:$gateway", obj("main" to saved).toString())
        val calls = mutableListOf<String>()
        transport(model) { method, _ -> calls += method; throw GatewayFailure("Denied") }
        model.draft = "Keep these words"
        runBlocking { model.restoreConversation() }
        model.send()
        assertFalse(model.conversationReady)
        assertTrue(model.messages.isEmpty())
        assertEquals("Keep these words", model.draft)
        assertEquals(listOf("chat.history"), calls)
        assertEquals(saved, JSONObject(store.get("history:owner:$gateway")!!).getString("main"))
        transport(model) { _, _ -> obj() }
        runBlocking { model.restoreConversation() }
        assertFalse(model.conversationReady)
        store.put("history:owner:$gateway", "malformed saved state")
        runBlocking { model.restoreConversation() }
        assertFalse(model.conversationReady)
        // A deliberately new owner conversation is the explicit recovery path.
        model.newConversation()
        assertTrue(model.conversationReady)
        assertNotEquals(saved, JSONObject(store.get("history:owner:$gateway")!!).getString("main"))
    }

    @Test fun restoredContextChangeClearsDraftInsteadOfMovingItToAnotherAgent() = fixture { model, store, gateway ->
        val old = UUID.randomUUID().toString()
        val next = UUID.randomUUID().toString()
        VoiceModel::class.java.getDeclaredField("sessionId").apply { isAccessible = true }.set(model, old)
        model.draft = "Private draft for main"
        store.put("history:owner:$gateway", obj("alternate" to next).toString())
        state(model, "agent", "alternate")
        transport(model) { _, _ -> obj("messages" to JSONArray()) }
        runBlocking { model.restoreConversation() }
        assertEquals("", model.draft)
        model.draft = "Private draft for this endpoint"
        model.background()
        model.updateGateway("wss://other.example.com")
        assertEquals("", model.draft)
        model.gateway = gateway
    }

    @Test fun restrictedRestoreDoesNotCreateAnExtraConversation() = fixture { model, store, gateway ->
        model.restricted = true
        val id = UUID.randomUUID().toString()
        val calls = mutableListOf<String>()
        transport(model) { method, _ ->
            calls += method
            if (method == "voxkin.history.create") obj("id" to id)
            else obj("messages" to JSONArray().put(obj("role" to "assistant", "text" to "Saved reply")))
        }
        runBlocking {
            model.restoreConversation()
            model.restoreConversation()
        }
        assertEquals(listOf("voxkin.history.create", "voxkin.history.messages"), calls)
        assertEquals(id, JSONObject(store.get("history:guest:$gateway")!!).getString("main"))
        assertFalse(model.speaking)
    }

    @Test fun backgroundDuringSendNeverReplaysRequestOrLateAudio() = fixture { model, store, gateway ->
        val id = UUID.randomUUID().toString()
        VoiceModel::class.java.getDeclaredField("sessionId").apply { isAccessible = true }.set(model, id)
        val calls = mutableListOf<String>()
        val pending = CompletableDeferred<JSONObject>()
        var run = ""
        VoiceModel::class.java.getDeclaredField("transport").apply { isAccessible = true }
            .set(model, object : Transport {
                override suspend fun request(method: String, params: JSONObject): JSONObject {
                    calls += method
                    run = params.getString("idempotencyKey")
                    return pending.await()
                }
                override fun close() {}
            })
        model.draft = "Synthetic request"
        model.send()
        assertTrue(model.requestPending)
        model.background()
        assertFalse(model.requestPending)
        assertEquals("", model.draft)
        pending.complete(obj("runId" to run))
        state(model, "connected", true)
            model.syntheticHistory()
        model.speechConsent = true
        transport(model) { method, _ ->
            calls += method
            obj("messages" to JSONArray().put(obj("role" to "assistant", "text" to "Finished while away")))
        }
        runBlocking { model.restoreConversation() }
        val event = VoiceModel::class.java.getDeclaredMethod("event", String::class.java, JSONObject::class.java)
            .apply { isAccessible = true }
        event.invoke(model, "chat", obj("runId" to run, "sessionKey" to "agent:main:voxkin:$id",
            "state" to "final", "message" to obj("role" to "assistant", "text" to "Late callback")))
        assertEquals(listOf("chat.send", "chat.history"), calls)
        assertEquals(listOf(ChatLine("assistant", "Finished while away")), model.messages)
        assertFalse(model.requestPending)
        assertFalse(model.speaking)
        assertFalse(model.recording)
    }

    @Test fun reconnectRequiresApprovedSavedAccessAndRespectsExplicitDisconnectAndStaging() = fixture { model, store, gateway ->
        model.background()
        assertFalse(model.canReconnect())
        store.put("owner:$gateway", "synthetic-secret")
        assertFalse(model.canReconnect())
        store.put("resume:owner:$gateway", "true")
        assertTrue(model.canReconnect())
        model.token = "new-staged-token"
        assertFalse(model.canReconnect())
        model.token = ""
        model.disconnect()
        model.background()
        assertFalse(model.canReconnect())
        val fresh = VoiceModel(ApplicationProvider.getApplicationContext())
        fresh.gateway = gateway
        fresh.restricted = false
        assertTrue(fresh.canReconnect())
        fresh.stage("voxkin://setup#gateway=wss%3A%2F%2Fexample.com&token=synthetic")
        fresh.background()
        assertFalse(fresh.canReconnect())
        fresh.disconnect()
        model.forget()
        assertNull(store.get("resume:owner:$gateway"))
    }
}
