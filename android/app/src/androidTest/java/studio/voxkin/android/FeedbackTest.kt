package studio.voxkin.android

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class FeedbackTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private fun field(m: VoiceModel, name: String, value: Any?) = VoiceModel::class.java.getDeclaredField(name).apply { isAccessible = true }.set(m, value)
    private fun get(m: VoiceModel, name: String): Any? = VoiceModel::class.java.getDeclaredField(name).apply { isAccessible = true }.get(m)
    private fun state(m: VoiceModel, name: String, value: Any) {
        @Suppress("UNCHECKED_CAST")
        val s = get(m, name + "$" + "delegate") as MutableState<Any>; s.value = value
    }
    private class Player : FeedbackPlayback {
        var calls = 0; var stops = 0
        var gate: CompletableDeferred<Unit>? = null
        override fun stop() { stops++ }
        override suspend fun play(bytes: ByteArray, valid: () -> Boolean) { if (valid()) { calls++; gate?.await() } }
    }
    private class Fixture(val m: VoiceModel, val p: Player) {
        val savedOption = SecureStore(ApplicationProvider.getApplicationContext<Application>()).get("conversationalFeedback")
        var sends = 0; var syntheses = 0; var request = ""; var run = ""
        var ack: JSONObject? = null
        var sendGate: CompletableDeferred<JSONObject>? = null
        var synthesisGate: CompletableDeferred<JSONObject>? = null
        var synthParams: JSONObject? = null
    }
    private fun dispose(f: Fixture) {
        f.m.disconnect()
        val store = SecureStore(ApplicationProvider.getApplicationContext<Application>())
        if (f.savedOption == null) store.remove("conversationalFeedback") else store.put("conversationalFeedback", f.savedOption)
    }
    private fun create(): Fixture {
        val m = VoiceModel(ApplicationProvider.getApplicationContext<Application>(), false)
        m.gateway = "wss://feedback-${UUID.randomUUID()}.example.com"
        state(m,"agent","main"); state(m,"connected",true); state(m,"speechSource","gateway")
        state(m,"gatewaySpeechAvailable",true); state(m,"conversationalFeedback",true)
        field(m,"progressSubscriptionAvailable",true)
        m.speechConsent = true
        val p = Player(); field(m,"feedbackAudio",p)
        val f = Fixture(m,p)
        field(m,"transport",object: Transport {
            override suspend fun request(method: String, params: JSONObject): JSONObject {
                if (method == "voxkin.history.create") return obj("id" to UUID.randomUUID().toString())
                if (method == "chat.send") {
                    f.sends++; f.request = params.getString("message"); f.run = params.getString("idempotencyKey")
                    return f.sendGate?.await() ?: f.ack ?: obj("runId" to f.run)
                }
                if (method == "tts.speak" || method == "voxkin.speak") {
                    f.syntheses++; f.synthParams = params
                    return f.synthesisGate?.await() ?: obj("provider" to "synthetic", "mimeType" to "audio/wav", "audioBase64" to java.util.Base64.getEncoder().encodeToString(ReceiptTone.wav()))
                }
                return obj()
            }
            override fun close() {}
        })
        return f
    }
    private fun mark(m: VoiceModel) {
        val context = VoiceModel::class.java.getDeclaredMethod("conversationContext").apply { isAccessible = true }.invoke(m) as String
        (get(m,"spokenDraft") as SpokenDraft).markFinal(m.draft,context)
    }
    private fun emit(f: Fixture, name: String, payload: JSONObject) {
        payload.put("runId", f.run).put("sessionKey",f.m.selectedConversationId)
        VoiceModel::class.java.getDeclaredMethod("event",String::class.java,JSONObject::class.java).apply { isAccessible = true }.invoke(f.m,name,payload)
    }
    private fun notice(f: Fixture, id: String = "notice") = emit(f,"session.message",obj("messageId" to id,"message" to obj("role" to "assistant","phase" to "commentary","content" to "Checking the result.")))
    private fun final(f: Fixture) = emit(f,"chat",obj("state" to "final","message" to obj("role" to "assistant","text" to "Done")))
    @Test fun realFinalizationMarksOnlyUneditedFinalDictationWithoutTypedPrefix() {
        inst.runOnMainSync {
            for (mode in listOf("final", "partial", "typed-prefix", "edited-during", "stop")) {
                val f = create()
                try {
                    f.m.draft = if (mode == "typed-prefix") "Typed" else ""
                    VoiceModel::class.java.getDeclaredMethod("prepareCaptureProvenance").apply { isAccessible = true }.invoke(f.m)
                    if (mode == "edited-during") { f.m.draft = "Edit"; f.m.draft = "" }
                    if (mode == "stop") f.m.stopAudio()
                    if (mode == "partial") state(f.m, "partial", "Recovered")
                    val generation = get(f.m, "speechGeneration") as Int
                    VoiceModel::class.java.getDeclaredMethod("finishRecognition", Int::class.javaPrimitiveType, String::class.java)
                        .apply { isAccessible = true }.invoke(f.m, generation, if (mode == "partial") null else "Spoken")
                    f.m.send()
                    assertEquals(mode, if (mode == "final") 1 else 0, f.p.calls)
                } finally { dispose(f) }
            }
        }
    }
    @Test fun focusLossDuringFeedbackDisarmsHandsFreeWithoutCancellingTurn() {
        inst.runOnMainSync {
            val f = create()
            try {
                f.m.draft="Hello"; mark(f.m); f.m.send()
                state(f.m, "conversationMode", true)
                VoiceModel::class.java.getDeclaredMethod("feedbackFocusLost").apply { isAccessible = true }.invoke(f.m)
                assertFalse(f.m.conversationMode); assertTrue(f.m.requestPending)
                notice(f); assertEquals(1, f.p.calls)
            } finally { dispose(f) }
        }
    }
    @Test fun restrictedReplacementGetsReceiptWithoutProgressGuidance() {
        inst.runOnMainSync {
            val f = create()
            try {
                f.m.restricted = true; field(f.m, "sessionId", "")
                f.m.draft = "Hello"; mark(f.m); f.m.send(); notice(f)
                assertEquals(1, f.p.calls); assertFalse(f.request.contains("source-reply"))
                assertTrue(f.m.selectedConversationId.isNotBlank())
            } finally { dispose(f) }
        }
    }
    @Test fun synthesisUsesSendTimeVoiceLanguageEvenIfCurrentSettingsChange() {
        lateinit var f: Fixture
        inst.runOnMainSync {
            f = create()
            state(f.m, "speechSource", "local"); field(f.m, "localVoice", true)
            state(f.m, "voices", listOf(GatewayVoice("original", "Original", "en")))
            f.m.voice = "original"; f.m.language = "en-US"
            f.m.draft="Hello"; mark(f.m); f.m.send(); notice(f)
            f.m.voice = "changed"; f.m.language = "es-ES"
        }
        Thread.sleep(950)
        try { inst.runOnMainSync {
            assertEquals("original", f.synthParams!!.getString("voice"))
            assertEquals("en", f.synthParams!!.getString("language"))
        } } finally { inst.runOnMainSync { dispose(f) } }
    }
    @Test fun finalDuringProgressPlaybackStopsItWithoutAudioDiagnostics() {
        lateinit var f: Fixture
        inst.runOnMainSync {
            f = create(); f.m.draft="Hello"; mark(f.m); f.m.send()
            f.p.gate = CompletableDeferred(); notice(f)
        }
        Thread.sleep(950)
        try { inst.runOnMainSync {
            assertEquals(2, f.p.calls)
            f.m.speechConsent = false; final(f)
            f.p.gate!!.complete(Unit)
            assertNull(f.m.timing.firstAudio); assertFalse(f.m.requestPending)
            assertEquals("Done", f.m.messages.last().text)
        } } finally { inst.runOnMainSync { dispose(f) } }
    }
    @Test fun realLocalTonePlaybackCompletesWithOwnedResources() {
        val changes = mutableListOf<Boolean>()
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val audio = FeedbackAudio(ApplicationProvider.getApplicationContext<Application>(), { changes.add(it) }, {})
                try { audio.play(ReceiptTone.wav()) { true } } finally { audio.stop() }
            }
        }
        assertEquals(listOf(true, false), changes)
    }
    @Test fun realReceiptPreemptionReleasesFocusForFinalAnswer() {
        lateinit var f: Fixture
        inst.runOnMainSync {
            f = create()
            field(f.m, "feedbackAudio", FeedbackAudio(ApplicationProvider.getApplicationContext<Application>(),
                { state(f.m, "feedbackPlaying", it) }, { f.m.stopAudio() }))
            f.m.draft = "Hello"; mark(f.m); f.m.send()
        }
        try {
            var playing = false
            val deadline = android.os.SystemClock.elapsedRealtime() + 5000
            while (!playing && android.os.SystemClock.elapsedRealtime() < deadline) {
                inst.runOnMainSync { playing = f.m.feedbackPlaying }
                if (!playing) Thread.sleep(5)
            }
            assertTrue("Receipt reached real playback", playing)
            inst.runOnMainSync { final(f); assertFalse(f.m.feedbackPlaying) }
            var complete = false
            val end = android.os.SystemClock.elapsedRealtime() + 5000
            while (!complete && android.os.SystemClock.elapsedRealtime() < end) {
                inst.runOnMainSync { complete = !f.m.speaking && f.m.timing.firstAudio != null }
                if (!complete) Thread.sleep(20)
            }
            assertTrue("Final answer played after feedback focus release", complete)
            inst.runOnMainSync { assertEquals("synthetic", f.m.speechProvider) }
        } finally { inst.runOnMainSync { dispose(f) } }
    }
    @Test fun typedEditedAndMissingAcceptanceStaySilent() {
        inst.runOnMainSync {
            for (mode in listOf("typed","edited","missing","mismatch")) {
                val f=create()
                try {
                    f.m.draft="Hello"
                    if(mode!="typed") mark(f.m)
                    if(mode=="edited") { f.m.draft="Edit"; f.m.draft="Hello" }
                    if(mode=="missing") f.ack=obj()
                    if(mode=="mismatch") f.ack=obj("runId" to "other")
                    f.m.send(); notice(f)
                    assertEquals(mode,0,f.p.calls)
                    if(mode in listOf("typed","edited")) assertFalse(f.request.contains("source-reply"))
                } finally { dispose(f) }
            }
        }
    }
    @Test fun queuedNoticeWaitsForAcceptanceAndReceiptThenOneProgress() {
        lateinit var f:Fixture
        inst.runOnMainSync {
            f=create(); f.sendGate=CompletableDeferred(); f.p.gate=CompletableDeferred()
            f.m.draft="Hello"; mark(f.m); f.m.send(); notice(f)
            assertEquals(0,f.p.calls)
            f.sendGate!!.complete(obj("runId" to f.run))
        }
        inst.waitForIdleSync()
        inst.runOnMainSync { assertEquals(1,f.p.calls); assertEquals(0,f.syntheses); f.p.gate!!.complete(Unit) }
        Thread.sleep(1000); inst.waitForIdleSync()
        try { inst.runOnMainSync {
            assertEquals(1,f.syntheses); assertEquals(2,f.p.calls)
            notice(f,"second"); assertEquals(1,f.syntheses)
            assertNull(f.m.timing.speechRequest); assertNull(f.m.timing.firstAudio)
            assertEquals("",f.m.speechProvider); assertFalse(f.m.speaking); assertTrue(f.m.requestPending)
            assertTrue(f.request.contains("source-reply"))
        } } finally { inst.runOnMainSync { dispose(f) } }
    }
    @Test fun finalDuringReceiptPreemptsFeedbackAndStillPlaysAnswer() {
        lateinit var f:Fixture
        inst.runOnMainSync {
            f=create(); f.p.gate=CompletableDeferred()
            f.m.draft="Hello"; mark(f.m); f.m.send(); notice(f)
            assertEquals(1,f.p.calls)
            final(f)
            assertTrue(f.p.stops>0); assertEquals(1,f.syntheses) // answer only
            f.p.gate!!.complete(Unit)
        }
        Thread.sleep(1000)
        try { inst.runOnMainSync { assertEquals(1,f.p.calls); assertEquals(1,f.syntheses); assertFalse(f.m.requestPending) } }
        finally { inst.runOnMainSync { dispose(f) } }
    }
    @Test fun disableConsentStopAndFinalCancelDelayedProgressPermanently() {
        for(mode in listOf("option","consent","stop","final")) {
            lateinit var f:Fixture
            inst.runOnMainSync {
                f=create(); f.m.draft="Hello"; mark(f.m); f.m.send(); notice(f)
                when(mode) {
                    "option" -> { f.m.chooseConversationalFeedback(false); f.m.chooseConversationalFeedback(true) }
                    "consent" -> { f.m.chooseSpeechConsent(false); f.m.chooseSpeechConsent(true) }
                    "stop" -> f.m.stopAudio()
                    else -> { f.m.speechConsent=false; final(f) }
                }
            }
            Thread.sleep(950)
            try { inst.runOnMainSync { assertEquals(mode,0,f.syntheses); assertEquals(1,f.p.calls) } }
            finally { inst.runOnMainSync { dispose(f) } }
        }
    }
    @Test fun finalDuringSynthesisRejectsStaleAudio() {
        lateinit var f:Fixture
        inst.runOnMainSync {
            f=create(); f.synthesisGate=CompletableDeferred()
            f.m.draft="Hello"; mark(f.m); f.m.send(); notice(f)
        }
        Thread.sleep(950)
        inst.runOnMainSync {
            assertEquals(1,f.syntheses); f.m.speechConsent=false; final(f)
            f.synthesisGate!!.complete(obj("provider" to "synthetic","mimeType" to "audio/wav","audioBase64" to java.util.Base64.getEncoder().encodeToString(ReceiptTone.wav())))
        }
        inst.waitForIdleSync()
        try { inst.runOnMainSync { assertEquals(1,f.p.calls); assertNull(f.m.timing.firstAudio) } }
        finally { inst.runOnMainSync { dispose(f) } }
    }
}
