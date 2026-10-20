package studio.voxkin.android

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProgressTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private fun field(m: VoiceModel, name: String, value: Any?) {
        VoiceModel::class.java.getDeclaredField(name).apply { isAccessible = true }.set(m, value)
    }
    private fun state(m: VoiceModel, name: String, value: Any) {
        @Suppress("UNCHECKED_CAST")
        val s = VoiceModel::class.java.getDeclaredField(name + "$" + "delegate").apply { isAccessible = true }.get(m) as MutableState<Any>
        s.value = value
    }
    private fun invoke(m: VoiceModel, name: String) = VoiceModel::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(m)
    private fun event(m: VoiceModel, name: String, p: JSONObject) = VoiceModel::class.java.getDeclaredMethod("event", String::class.java, JSONObject::class.java).apply { isAccessible = true }.invoke(m, name, p)
    private fun model(): VoiceModel = VoiceModel(ApplicationProvider.getApplicationContext<Application>(), false).also {
        it.gateway = "wss://progress-${UUID.randomUUID()}.example.com"
        state(it, "agent", "main"); state(it, "connected", true)
        field(it, "sessionId", UUID.randomUUID().toString())
        field(it, "progressSubscriptionAvailable", true)
    }
    @Test fun persistedNoticesAreTransientAndFinalCancelDisconnectRejectLateEvents() {
        instrument.runOnMainSync {
            val m = model()
            try {
                state(m, "activeRun", "run"); state(m, "busy", true)
                state(m, "timing", TurnTiming().sent(android.os.SystemClock.elapsedRealtime()))
                val p = obj("sessionKey" to m.selectedConversationId, "runId" to "run", "messageId" to "one", "message" to obj("role" to "assistant", "phase" to "commentary", "content" to "Working"))
                event(m, "session.message", p)
                assertEquals("Working", m.pendingText); assertNotNull(m.timing.firstUpdate)
                assertTrue(m.messages.isEmpty()); assertFalse(m.speaking)
                event(m, "chat", obj("sessionKey" to m.selectedConversationId, "runId" to "run", "state" to "delta", "deltaText" to "Answer"))
                assertEquals("Working\n\nAnswer", m.pendingText)
                event(m, "chat", obj("sessionKey" to m.selectedConversationId, "runId" to "run", "state" to "final", "message" to obj("role" to "assistant", "text" to "Final")))
                event(m, "session.message", p.put("messageId", "late"))
                assertEquals("", m.pendingText); assertEquals(1, m.messages.size)
                state(m, "activeRun", "run"); event(m, "session.message", p)
                m.cancelTurn(); event(m, "session.message", p.put("messageId", "cancelled"))
                assertEquals("", m.pendingText)
                m.disconnect(); event(m, "session.message", p.put("messageId", "disconnected"))
                assertEquals("", m.pendingText)
            } finally { m.disconnect() }
        }
    }
    @Test fun hangingSubscriptionDoesNotBlockSendAndDuplicateTargetIsSuppressed() {
        lateinit var m: VoiceModel
        val gate = CompletableDeferred<JSONObject>()
        var subscriptions = 0
        var sends = 0
        instrument.runOnMainSync {
            m = model()
            field(m, "transport", object : Transport {
                override suspend fun request(method: String, params: JSONObject): JSONObject {
                    if (method == "sessions.messages.subscribe") { subscriptions++; return gate.await() }
                    if (method == "chat.send") { sends++; return obj("runId" to params.getString("idempotencyKey")) }
                    return obj()
                }
                override fun close() {}
            })
            invoke(m, "subscribeProgress"); invoke(m, "subscribeProgress")
            assertEquals(1, subscriptions); assertFalse(m.busy)
            m.draft = "Hello"; m.send()
            assertEquals(1, sends); assertEquals(1, subscriptions)
            m.disconnect()
        }
        gate.complete(obj())
        instrument.waitForIdleSync()
        instrument.runOnMainSync {
            val subscribed = VoiceModel::class.java.getDeclaredField("subscribedSession").apply { isAccessible = true }.get(m)
            assertNull(subscribed)
        }
    }
    @Test fun replacingSubscriptionRequiresResubscribeWhenReturningToEarlierTarget() {
        instrument.runOnMainSync {
            val m = model()
            val first = UUID.randomUUID().toString()
            val hanging = CompletableDeferred<JSONObject>()
            var calls = 0
            field(m, "transport", object : Transport {
                override suspend fun request(method: String, params: JSONObject): JSONObject {
                    calls++
                    return if (calls == 2) hanging.await() else obj()
                }
                override fun close() {}
            })
            try {
                field(m, "sessionId", first); invoke(m, "subscribeProgress")
                field(m, "sessionId", UUID.randomUUID().toString()); invoke(m, "subscribeProgress")
                field(m, "sessionId", first); invoke(m, "subscribeProgress")
                assertEquals(3, calls)
                field(m, "progressSubscriptionAvailable", false)
                field(m, "sessionId", UUID.randomUUID().toString()); invoke(m, "subscribeProgress")
                assertEquals(3, calls)
            } finally { m.disconnect() }
        }
    }
}
