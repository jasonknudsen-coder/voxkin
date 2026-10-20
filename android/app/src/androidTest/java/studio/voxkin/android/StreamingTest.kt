package studio.voxkin.android

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StreamingTest {
    private fun state(model: VoiceModel, name: String, value: Any) {
        @Suppress("UNCHECKED_CAST")
        val state = VoiceModel::class.java.getDeclaredField(name + "$" + "delegate")
            .apply { isAccessible = true }.get(model) as MutableState<Any>
        state.value = value
    }
    private fun fixture(block: (VoiceModel, (JSONObject) -> Unit) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = VoiceModel(ApplicationProvider.getApplicationContext<Application>(), automaticConnection = false)
            model.gateway = "wss://stream-${UUID.randomUUID()}.example.com"
            val id = UUID.randomUUID().toString()
            VoiceModel::class.java.getDeclaredField("sessionId").apply { isAccessible = true }.set(model, id)
            state(model, "agent", "main")
            state(model, "activeRun", "test-run")
            state(model, "connected", true)
            state(model, "busy", true)
            val event = VoiceModel::class.java.getDeclaredMethod("event", String::class.java, JSONObject::class.java)
                .apply { isAccessible = true }
            try {
                block(model) { payload ->
                    if (!payload.has("runId")) payload.put("runId", "test-run")
                    if (!payload.has("sessionKey")) payload.put("sessionKey", "agent:main:voxkin:$id")
                    event.invoke(model, "chat", payload)
                }
            } finally { model.disconnect() }
        }
    }

    @Test fun previewDoesNotFillPriorAnswerTimingOrProvider() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var model: VoiceModel
        val before = TurnTiming().sent(100).final(200)
        instrumentation.runOnMainSync {
            model = VoiceModel(ApplicationProvider.getApplicationContext<Application>(), false)
            state(model, "connected", true)
            state(model, "gatewaySpeechAvailable", true)
            state(model, "speechProvider", "prior-answer")
            state(model, "timing", before)
            model.speechConsent = true
            state(model, "speechSource", "gateway")
            val wav = java.nio.ByteBuffer.allocate(4844).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            wav.put("RIFF".toByteArray()).putInt(4836).put("WAVEfmt ".toByteArray())
            wav.putInt(16).putShort(1).putShort(1).putInt(24000).putInt(48000)
            wav.putShort(2).putShort(16).put("data".toByteArray()).putInt(4800)
            VoiceModel::class.java.getDeclaredField("transport").apply { isAccessible = true }.set(model,
                object : Transport {
                    override suspend fun request(method: String, params: JSONObject) = obj(
                        "provider" to "synthetic-preview", "mimeType" to "audio/wav",
                        "audioBase64" to java.util.Base64.getEncoder().encodeToString(wav.array()))
                    override fun close() {}
                })
            model.previewVoice()
        }
        try {
            val deadline = android.os.SystemClock.elapsedRealtime() + 5000
            var playing = true
            while (playing && android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(50)
                instrumentation.runOnMainSync { playing = model.speaking }
            }
            instrumentation.runOnMainSync {
                assertFalse(model.speaking)
                assertEquals("Reply ready.", model.status)
                assertEquals(before, model.timing)
                assertEquals("prior-answer", model.speechProvider)
            }
        } finally { instrumentation.runOnMainSync { model.disconnect() } }
    }

    @Test fun timingAcceptsOnlyCurrentVisibleEventsAndClearsOnDisconnect() = fixture { model, emit ->
        state(model, "timing", TurnTiming().sent(android.os.SystemClock.elapsedRealtime()))
        emit(obj("state" to "delta", "runId" to "other", "deltaText" to "Wrong"))
        emit(obj("state" to "delta", "phase" to "analysis", "deltaText" to "Private"))
        emit(obj("state" to "delta", "deltaText" to " "))
        assertNull(model.timing.firstUpdate)
        emit(obj("state" to "delta", "deltaText" to "Visible"))
        assertNotNull(model.timing.firstUpdate)
        model.speechConsent = false
        emit(obj("state" to "final", "message" to obj("role" to "assistant", "text" to "Answer")))
        assertNotNull(model.timing.reply)
        assertNull(model.timing.firstAudio)
        val finished = model.timing
        emit(obj("state" to "final", "message" to obj("role" to "assistant", "text" to "Late")))
        assertEquals(finished, model.timing)
        model.disconnect()
        assertEquals(TurnTiming(), model.timing)
        assertEquals("", model.speechProvider)
    }

    @Test fun deltasAreTransientBoundedAndFinalReplacesThemWithoutDuplicates() = fixture { model, emit ->
        emit(obj("state" to "status", "phase" to "starting_model"))
        assertEquals("Your agent is thinking…", model.status)
        emit(obj("state" to "delta", "deltaText" to "Hola"))
        emit(obj("state" to "delta", "deltaText" to " mundo"))
        assertEquals("Hola mundo", model.streamingText)
        assertTrue(model.messages.isEmpty())
        assertFalse(model.speaking)
        emit(obj("state" to "delta", "replace" to true, "deltaText" to "Revised"))
        assertEquals("Revised", model.streamingText)
        emit(obj("state" to "delta", "message" to obj("role" to "assistant", "content" to "Snapshot")))
        assertEquals("Snapshot", model.streamingText)
        emit(obj("state" to "delta", "deltaText" to "x".repeat(20000)))
        assertEquals(16000, model.streamingText.length)
        model.speechConsent = false
        emit(obj("state" to "final", "message" to obj("role" to "assistant", "text" to "Final answer")))
        emit(obj("state" to "final", "message" to obj("role" to "assistant", "text" to "Duplicate")))
        emit(obj("state" to "delta", "deltaText" to "Too late"))
        assertEquals("", model.streamingText)
        assertEquals(listOf(ChatLine("assistant", "Final answer")), model.messages)
        assertFalse(model.requestPending)
    }

    @Test fun wrongContextPrivatePayloadsAndCancelledStreamsAreIgnored() = fixture { model, emit ->
        emit(obj("state" to "delta", "deltaText" to "Visible"))
        emit(obj("state" to "delta", "runId" to "other", "deltaText" to "Wrong run"))
        emit(obj("state" to "delta", "sessionKey" to "other", "deltaText" to "Wrong session"))
        emit(obj("state" to "delta", "phase" to "analysis", "deltaText" to "Private raw delta"))
        for (message in listOf(
            obj("role" to "tool", "text" to "Tool output"),
            obj("role" to "assistant", "channel" to "analysis", "text" to "Private"),
            obj("role" to "assistant", "phase" to "analysis", "text" to "Private"),
            obj("role" to "assistant", "content" to JSONArray().put(obj("type" to "thinking", "text" to "Private"))),
        )) emit(obj("state" to "delta", "message" to message))
        assertEquals("Visible", model.streamingText)
        model.cancelTurn()
        emit(obj("state" to "delta", "deltaText" to "Late callback"))
        assertEquals("", model.streamingText)
        assertTrue(model.messages.isEmpty())
        assertFalse(model.speaking)
    }
}
