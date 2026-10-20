package studio.voxkin.android

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class HandsFreeTest {
    private fun set(m: VoiceModel, name: String, value: Any?) {
        @Suppress("UNCHECKED_CAST")
        val state = VoiceModel::class.java.getDeclaredField(name + "$" + "delegate")
            .apply { isAccessible = true }.get(m) as MutableState<Any?>
        state.value = value
    }
    private fun fixture(): VoiceModel = VoiceModel(ApplicationProvider.getApplicationContext<Application>(), false).apply {
        gateway = "wss://handsfree-${UUID.randomUUID()}.example.com"
        restricted = false
        set(this, "connected", true)
        set(this, "agent", "main")
    }
    // Seed active state without opening a real mic; exercise the actual production finalization paths.
    private fun activate(m: VoiceModel) {
        set(m, "conversationMode", true)
        val loop = VoiceModel::class.java.getDeclaredField("handsFree").apply { isAccessible = true }.get(m)
        loop.javaClass.getDeclaredField("active").apply { isAccessible = true }.setBoolean(loop, true)
    }
    private fun epoch(m: VoiceModel) = VoiceModel::class.java.getDeclaredField("speechGeneration")
        .apply { isAccessible = true }.getInt(m)
    private fun finish(m: VoiceModel, text: String?, generation: Int = epoch(m)) {
        VoiceModel::class.java.getDeclaredMethod("finishRecognition", Int::class.javaPrimitiveType, String::class.java)
            .apply { isAccessible = true }.invoke(m, generation, text)
    }
    private fun transport(m: VoiceModel, methods: MutableList<String>) {
        VoiceModel::class.java.getDeclaredField("transport").apply { isAccessible = true }.set(m, object : Transport {
            override suspend fun request(method: String, params: JSONObject): JSONObject {
                methods += method
                return obj()
            }
            override fun close() {}
        })
    }

    @Test fun onlyExplicitModeSendsSuccessfulFinalOnce() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val m = fixture(); val methods = mutableListOf<String>(); transport(m, methods)
            try {
                finish(m, "Normal dictation needs review")
                assertEquals(0, methods.size)
                m.clearDraft()
                activate(m)
                val old = epoch(m)
                finish(m, "Hands-free synthetic turn", old)
                assertEquals(listOf("chat.send"), methods)
                assertTrue(m.requestPending)
                assertTrue(m.conversationMode)
                finish(m, "Late duplicate", old)
                assertEquals(1, methods.size)
                assertEquals("", m.draft)
            } finally { m.forget() }
        }
    }

    @Test fun recoveredOrOverlongWordsNeverAutoSend() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val m = fixture(); val methods = mutableListOf<String>(); transport(m, methods)
            try {
                activate(m); set(m, "partial", "Palabras recuperadas")
                finish(m, null)
                assertEquals("Palabras recuperadas", m.draft)
                assertFalse(m.conversationMode)
                assertTrue(methods.isEmpty())
                m.clearDraft(); activate(m)
                finish(m, "x".repeat(4001))
                assertEquals(4001, m.draft.length)
                assertFalse(m.conversationMode)
                assertTrue(methods.isEmpty())
            } finally { m.forget() }
        }
    }

    @Test fun stopAndNavigationInvalidateFinalCallbacksAndKeepPartialForReview() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val m = fixture(); val methods = mutableListOf<String>(); transport(m, methods)
            try {
                activate(m); set(m, "partial", "Keep for review")
                val old = epoch(m)
                m.page = 1
                finish(m, "Must not send", old)
                assertEquals("Keep for review", m.draft)
                assertFalse(m.conversationMode)
                assertTrue(methods.isEmpty())
                assertFalse(m.recording)
                assertFalse(m.speaking)
            } finally { m.forget() }
        }
    }

    @Test fun backgroundInvalidatesPermissionGrantAndNeverRestartsMode() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val m = fixture()
            try {
                val token = m.handsFreeStartToken()
                activate(m); set(m, "partial", "Review later")
                m.background()
                assertNotEquals(token, m.handsFreeStartToken())
                m.startHandsFree(token)
                assertFalse(m.conversationMode)
                assertFalse(m.recording)
                assertEquals("Review later", m.draft)
                assertFalse(m.requestPending)
            } finally { m.forget() }
        }
    }

    @Test fun endStopsLoopButOnlyCancelRequestAbortsGatewayWork() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val m = fixture(); val methods = mutableListOf<String>(); transport(m, methods)
            try {
                activate(m); finish(m, "Synthetic request")
                m.endHandsFree()
                assertFalse(m.conversationMode)
                assertTrue(m.requestPending)
                assertEquals(listOf("chat.send"), methods)
                m.cancelTurn()
                assertFalse(m.requestPending)
                assertEquals(listOf("chat.send", "chat.abort"), methods)
            } finally { m.forget() }
        }
    }

    @Test fun failedGatewayTurnEndsModeWithoutRetry() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val m = fixture()
            var calls = 0
            VoiceModel::class.java.getDeclaredField("transport").apply { isAccessible = true }.set(m, object : Transport {
                override suspend fun request(method: String, params: JSONObject): JSONObject {
                    calls++
                    throw GatewayFailure("Synthetic failure")
                }
                override fun close() {}
            })
            try {
                activate(m); finish(m, "Synthetic failing turn")
                assertFalse(m.conversationMode)
                assertFalse(m.requestPending)
                assertFalse(m.recording)
                assertEquals(1, calls)
            } finally { m.forget() }
        }
    }

    @Test fun completedSilentPlaybackResumesActualMicThenStopEndsLoop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var m: VoiceModel
        // A short silent WAV exercises MediaPlayer/focus completion without sending to a real gateway.
        val samples = 1600
        val wav = java.nio.ByteBuffer.allocate(44 + samples * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples * 2).put(ByteArray(samples * 2)).array()
        instrumentation.runOnMainSync {
            m = fixture()
            m.speechConsent = true
            set(m, "gatewaySpeechAvailable", true)
            m.chooseSpeechSource("gateway")
            VoiceModel::class.java.getDeclaredField("transport").apply { isAccessible = true }.set(m, object : Transport {
                override suspend fun request(method: String, params: JSONObject): JSONObject {
                    check(method == "tts.speak") { "Unexpected synthetic request: $method" }
                    return obj("provider" to "synthetic", "mimeType" to "audio/wav",
                        "audioBase64" to java.util.Base64.getEncoder().encodeToString(wav))
                }
                override fun close() {}
            })
            activate(m)
            VoiceModel::class.java.getDeclaredMethod("speak", String::class.java, Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(m, "Synthetic silent audio", true)
        }
        var resumed = false
        try {
            val deadline = System.currentTimeMillis() + 4000
            while (!resumed && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
                instrumentation.runOnMainSync {
                    resumed = m.recording
                    if (resumed) {
                        assertFalse(m.speaking)
                        assertTrue(m.conversationMode)
                        m.endHandsFree()
                    }
                }
            }
            assertTrue("Successful playback must resume listening", resumed)
            Thread.sleep(450)
            instrumentation.runOnMainSync {
                assertFalse(m.conversationMode)
                assertFalse(m.recording)
                assertFalse(m.requestPending)
            }
        } finally { instrumentation.runOnMainSync { m.forget() } }
    }

    @Test fun consentLossEndsCaptureAndLateFinalCannotSend() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val m = fixture(); val methods = mutableListOf<String>(); transport(m, methods)
            try {
                activate(m); set(m, "partial", "Unsended words")
                val old = epoch(m)
                m.chooseSpeechConsent(false)
                finish(m, "Late words", old)
                assertFalse(m.conversationMode)
                assertEquals("Unsended words", m.draft)
                assertTrue(methods.isEmpty())
            } finally { m.forget() }
        }
    }
}
