package studio.voxkin.android

import android.app.Application
import android.content.Intent
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    private fun setPartial(model: VoiceModel, text: String) {
        @Suppress("UNCHECKED_CAST")
        val state = VoiceModel::class.java.getDeclaredField("partial" + "$" + "delegate")
            .apply { isAccessible = true }.get(model) as MutableState<String>
        state.value = text
    }

    @Test
    fun settleTimeoutRecoversOnlyTheStillActiveCapture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var active: VoiceModel
        lateinit var cancelled: VoiceModel
        val timeout = VoiceModel::class.java.getDeclaredMethod(
            "startSettleTimeout", Int::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val generation = VoiceModel::class.java.getDeclaredField("speechGeneration")
            .apply { isAccessible = true }
        instrumentation.runOnMainSync {
            active = VoiceModel(ApplicationProvider.getApplicationContext<Application>())
            cancelled = VoiceModel(ApplicationProvider.getApplicationContext<Application>())
            setPartial(active, "Recovered after timeout.")
            setPartial(cancelled, "Must never return.")
            timeout.invoke(active, generation.getInt(active))
            timeout.invoke(cancelled, generation.getInt(cancelled))
            cancelled.stopRecording(discard = true)
        }
        // Exercise the real five-second settle job on the Android main dispatcher.
        Thread.sleep(5500)
        instrumentation.runOnMainSync {
            try {
                assertEquals("Recovered after timeout.", active.draft)
                assertTrue(active.status.contains("may be incomplete"))
                assertFalse(active.requestPending)
                assertEquals("", cancelled.draft)
                assertEquals("", cancelled.partial)
                assertFalse(cancelled.requestPending)
            } finally {
                active.disconnect()
                cancelled.disconnect()
            }
        }
    }

    @Test
    fun failedRecognitionRetainsWordsForReviewWithoutSending() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = VoiceModel(ApplicationProvider.getApplicationContext<Application>())
            val finish = VoiceModel::class.java.getDeclaredMethod(
                "finishRecognition", Int::class.javaPrimitiveType, String::class.java
            ).apply { isAccessible = true }
            val generation = VoiceModel::class.java.getDeclaredField("speechGeneration")
                .apply { isAccessible = true }
            model.draft = "Existing draft."
            setPartial(model, "Hola, estas son mis palabras.")
            finish.invoke(model, generation.getInt(model), null)
            assertEquals("Existing draft. Hola, estas son mis palabras.", model.draft)
            assertEquals("", model.partial)
            assertTrue(model.status.contains("may be incomplete"))
            assertFalse(model.requestPending)
            assertFalse(model.recording)
            assertFalse(model.settling)
            // A late final callback cannot append duplicate words.
            finish.invoke(model, generation.getInt(model) - 1, "late duplicate")
            assertEquals("Existing draft. Hola, estas son mis palabras.", model.draft)
            model.disconnect()
        }
    }

    @Test
    fun finalRecognitionWinsAndCancelNeverRecoversDiscardedWords() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = VoiceModel(ApplicationProvider.getApplicationContext<Application>())
            val finish = VoiceModel::class.java.getDeclaredMethod(
                "finishRecognition", Int::class.javaPrimitiveType, String::class.java
            ).apply { isAccessible = true }
            val generation = VoiceModel::class.java.getDeclaredField("speechGeneration")
                .apply { isAccessible = true }
            setPartial(model, "unfinished")
            finish.invoke(model, generation.getInt(model), "Final words.")
            assertEquals("Final words.", model.draft)
            assertFalse(model.status.contains("incomplete"))
            setPartial(model, "discard this")
            val oldGeneration = generation.getInt(model)
            model.stopRecording(discard = true)
            finish.invoke(model, oldGeneration, "late final")
            assertEquals("Final words.", model.draft)
            assertEquals("", model.partial)
            assertFalse(model.requestPending)
            finish.invoke(model, generation.getInt(model), " ")
            assertTrue(model.status.contains("No speech recognized"))
            model.disconnect()
        }
    }

    @Test
    fun mismatchedRunIdStopsWithoutReplayingOrAdoptingRun() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = VoiceModel(ApplicationProvider.getApplicationContext<Application>())
            @Suppress("UNCHECKED_CAST")
            val state = VoiceModel::class.java.getDeclaredField("connected" + "$" + "delegate")
                .apply { isAccessible = true }.get(model) as MutableState<Boolean>
            state.value = true
            var calls = 0
            VoiceModel::class.java.getDeclaredField("transport").apply { isAccessible = true }.set(model,
                object : Transport {
                    override suspend fun request(method: String, params: JSONObject): JSONObject {
                        calls++
                        return obj("runId" to "unexpected-run")
                    }
                    override fun close() {}
                })
            model.draft = "Synthetic request"
            model.send()
            assertEquals(1, calls)
            assertFalse(model.requestPending)
            assertTrue(model.status.contains("Outcome unknown"))
            model.disconnect()
        }
    }

    @Test
    fun speechSourcesRouteExplicitlyAndRestrictedCannotChooseGateway() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val gateway = "wss://source-${UUID.randomUUID()}.example.com"
        instrumentation.runOnMainSync {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val model = VoiceModel(app)
            model.gateway = gateway
            fun set(name: String, value: Any) {
                @Suppress("UNCHECKED_CAST")
                val state = VoiceModel::class.java.getDeclaredField(name + "$" + "delegate")
                    .apply { isAccessible = true }.get(model) as MutableState<Any>
                state.value = value
            }
            set("connected", true)
            set("gatewaySpeechAvailable", true)
            set("voices", listOf(GatewayVoice("test-voice", "Test", "en")))
            VoiceModel::class.java.getDeclaredField("localVoice").apply { isAccessible = true }.set(model, true)
            model.voice = "test-voice"
            model.speechConsent = true
            val methods = mutableListOf<String>()
            VoiceModel::class.java.getDeclaredField("transport").apply { isAccessible = true }.set(model,
                object : Transport {
                    override suspend fun request(method: String, params: JSONObject): JSONObject {
                        methods.add(method)
                        throw GatewayFailure("Synthetic response ends preview before audio playback")
                    }
                    override fun close() {}
                })
            try {
                model.chooseSpeechSource("gateway")
                model.previewVoice()
                assertEquals(listOf("tts.speak"), methods)
                model.chooseSpeechSource("local")
                model.previewVoice()
                assertEquals(listOf("tts.speak", "voxkin.speak"), methods)
                model.restricted = true
                model.chooseSpeechSource("gateway")
                assertEquals("local", model.speechSource)
                assertFalse(model.sourceOptions.any { it.first == "gateway" })
            } finally {
                model.disconnect()
                SecureStore(app).remove("speech-source:owner:$gateway")
            }
        }
    }

    @Test
    fun clearDraftDiscardsPartialCaptureWithoutSending() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = VoiceModel(ApplicationProvider.getApplicationContext<Application>())
            model.draft = "Synthetic draft"
            for (name in listOf("recording", "microphoneReady")) {
                @Suppress("UNCHECKED_CAST")
                val state = VoiceModel::class.java.getDeclaredField(name + "$" + "delegate")
                    .apply { isAccessible = true }.get(model) as MutableState<Boolean>
                state.value = true
            }
            model.clearDraft()
            assertEquals("", model.draft)
            assertFalse(model.recording)
            assertFalse(model.microphoneReady)
            assertFalse(model.requestPending)
            assertTrue(model.messages.isEmpty())
            model.disconnect()
        }
    }

    @Test
    fun previewRequiresConnectionAndConsent() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = VoiceModel(ApplicationProvider.getApplicationContext<Application>())
            model.previewVoice()
            assertFalse(model.speaking)
            @Suppress("UNCHECKED_CAST")
            val state = VoiceModel::class.java.getDeclaredField("connected" + "$" + "delegate")
                .apply { isAccessible = true }.get(model) as MutableState<Boolean>
            state.value = true
            model.speechConsent = false
            model.previewVoice()
            assertFalse(model.speaking)
            assertFalse(model.requestPending)
            model.disconnect()
        }
    }

    @Test
    fun guestForgetPreservesOwnerIdentityAndGrantAtSameOrigin() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val store = SecureStore(app)
            val gateway = "wss://synthetic-${UUID.randomUUID()}.example.com"
            val identityKey = "identity:$gateway"
            val ownerKey = "owner:$gateway"
            val guestKey = "guest:$gateway"
            try {
                val originalIdentity = store.identity(gateway).id
                store.put(ownerKey, "synthetic-owner-grant")
                store.put(guestKey, "synthetic-guest-grant")
                val model = VoiceModel(app)
                model.gateway = gateway
                model.restricted = true
                model.forget()
                assertNull(store.get(guestKey))
                assertEquals("synthetic-owner-grant", store.get(ownerKey))
                assertEquals(originalIdentity, store.identity(gateway).id)
                model.restricted = false
                model.forget()
                assertNull(store.get(ownerKey))
                assertNull(store.get(identityKey))
            } finally {
                store.remove(identityKey)
                store.remove(ownerKey)
                store.remove(guestKey)
            }
        }
    }

    @Test
    fun restrictedHistoryListUsesPluginLimitTwenty() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = VoiceModel(ApplicationProvider.getApplicationContext<Application>())
            @Suppress("UNCHECKED_CAST")
            val connected =
                VoiceModel::class
                    .java
                    .getDeclaredField("connected" + "$" + "delegate")
                    .apply { isAccessible = true }
                    .get(model) as MutableState<Boolean>
            connected.value = true
            model.restricted = true
            var method = ""
            var limit = 0
            VoiceModel::class
                .java
                .getDeclaredField("transport")
                .apply { isAccessible = true }
                .set(
                    model,
                    object : Transport {
                        override suspend fun request(name: String, params: JSONObject): JSONObject {
                            method = name
                            limit = params.getInt("limit")
                            return obj("conversations" to org.json.JSONArray())
                        }

                        override fun close() {}
                    },
                )
            model.listHistory()
            assertEquals("voxkin.history.list", method)
            assertEquals(20, limit)
            assertFalse(model.busy)
            model.disconnect()
        }
    }

    @Test
    fun deletingActiveRestrictedConversationCreatesOwnedHistoryBeforeNextSend() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sent = CountDownLatch(1)
        val calls = mutableListOf<String>()
        val old = "11111111-2222-4333-8444-555555555555"
        val created = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        lateinit var model: VoiceModel
        var sentSession = ""
        instrumentation.runOnMainSync {
            model = VoiceModel(ApplicationProvider.getApplicationContext<Application>())
            @Suppress("UNCHECKED_CAST")
            fun <T> flag(name: String, value: T) {
                (VoiceModel::class
                        .java
                        .getDeclaredField(name + "$" + "delegate")
                        .apply { isAccessible = true }
                        .get(model) as MutableState<T>)
                    .value = value
            }
            flag("connected", true)
            flag("agent", "guest")
            model.restricted = true
            VoiceModel::class
                .java
                .getDeclaredField("sessionId")
                .apply { isAccessible = true }
                .set(model, old)
            VoiceModel::class
                .java
                .getDeclaredField("transport")
                .apply { isAccessible = true }
                .set(
                    model,
                    object : Transport {
                        override suspend fun request(
                            method: String,
                            params: JSONObject,
                        ): JSONObject {
                            calls.add(method)
                            return when (method) {
                                "voxkin.history.create" -> obj("id" to created)
                                "chat.send" -> {
                                    sentSession = params.getString("sessionKey")
                                    sent.countDown()
                                    obj("runId" to params.getString("idempotencyKey"))
                                }
                                else -> obj()
                            }
                        }

                        override fun close() {}
                    },
                )
            model.delete(Conversation(old, "Synthetic"))
            model.draft = "New synthetic message"
            model.send()
        }
        assertTrue(sent.await(3, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            assertEquals(
                listOf("voxkin.history.delete", "voxkin.history.create", "chat.send"),
                calls.take(3),
            )
            assertEquals("agent:guest:voxkin:$created", sentSession)
            model.disconnect()
        }
    }

    @Test
    fun stoppingAudioDoesNotUnlockPendingHistory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val requested = CountDownLatch(1)
        val result = CompletableDeferred<JSONObject>()
        lateinit var model: VoiceModel
        var calls = 0
        instrumentation.runOnMainSync {
            model = VoiceModel(ApplicationProvider.getApplicationContext<Application>())
            @Suppress("UNCHECKED_CAST")
            val connected =
                VoiceModel::class
                    .java
                    .getDeclaredField("connected" + "$" + "delegate")
                    .apply { isAccessible = true }
                    .get(model) as MutableState<Boolean>
            connected.value = true
            VoiceModel::class
                .java
                .getDeclaredField("transport")
                .apply { isAccessible = true }
                .set(
                    model,
                    object : Transport {
                        override suspend fun request(
                            method: String,
                            params: JSONObject,
                        ): JSONObject {
                            calls++
                            requested.countDown()
                            return result.await()
                        }

                        override fun close() {}
                    },
                )
            model.listHistory()
        }
        assertTrue(requested.await(3, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            assertTrue(model.busy)
            model.cancelTurn()
            assertTrue("Stop audio must preserve history lock", model.busy)
            model.draft = "Unsent"
            model.send()
            model.chooseAgent("other")
            model.newConversation()
            assertEquals(1, calls)
            assertEquals("", model.agent)
            assertEquals("Unsent", model.draft)
            model.disconnect()
        }
        result.complete(obj())
    }

    @Test
    fun stopAudioCancelsInFlightSynthesisBeforePlayback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val requested = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val result = CompletableDeferred<JSONObject>()
        lateinit var model: VoiceModel
        instrumentation.runOnMainSync {
            model = VoiceModel(ApplicationProvider.getApplicationContext<Application>())
            val fake =
                object : Transport {
                    override suspend fun request(method: String, params: JSONObject): JSONObject {
                        requested.countDown()
                        try {
                            return result.await()
                        } finally {
                            cancelled.countDown()
                        }
                    }

                    override fun close() {}
                }
            VoiceModel::class
                .java
                .getDeclaredField("transport")
                .apply { isAccessible = true }
                .set(model, fake)
            model.speechConsent = true
            @Suppress("UNCHECKED_CAST")
            val source = VoiceModel::class.java.getDeclaredField("speechSource" + "$" + "delegate")
                .apply { isAccessible = true }.get(model) as MutableState<String>
            source.value = "gateway"
            @Suppress("UNCHECKED_CAST")
            val available = VoiceModel::class.java.getDeclaredField("gatewaySpeechAvailable" + "$" + "delegate")
                .apply { isAccessible = true }.get(model) as MutableState<Boolean>
            available.value = true
            VoiceModel::class
                .java
                .getDeclaredMethod("speak", String::class.java)
                .apply { isAccessible = true }
                .invoke(model, "Synthetic speech")
        }
        assertTrue(requested.await(3, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { model.stopAudio() }
        assertTrue("Stop must cancel pending synthesis", cancelled.await(3, TimeUnit.SECONDS))
        result.complete(obj("audioBase64" to "not-a-real-clip"))
        instrumentation.runOnMainSync {
            assertNull(
                VoiceModel::class
                    .java
                    .getDeclaredField("player")
                    .apply { isAccessible = true }
                    .get(model)
            )
            assertFalse(model.status == "Speaking…")
            model.disconnect()
        }
    }

    @Test
    fun captureContextCannotBeChangedOrSentWhileRecordingOrSettling() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val model = VoiceModel(ApplicationProvider.getApplicationContext<Application>())
            @Suppress("UNCHECKED_CAST")
            fun flag(name: String, value: Boolean) {
                (VoiceModel::class
                        .java
                        .getDeclaredField(name + "$" + "delegate")
                        .apply { isAccessible = true }
                        .get(model) as MutableState<Boolean>)
                    .value = value
            }
            var calls = 0
            VoiceModel::class
                .java
                .getDeclaredField("transport")
                .apply { isAccessible = true }
                .set(
                    model,
                    object : Transport {
                        override suspend fun request(
                            method: String,
                            params: JSONObject,
                        ): JSONObject {
                            calls++
                            return obj()
                        }

                        override fun close() {}
                    },
                )
            flag("connected", true)
            model.draft = "Unsent words"
            for (state in listOf("recording", "settling")) {
                flag(state, true)
                model.newConversation()
                model.openHistory(Conversation("11111111-2222-4333-8444-555555555555", "Synthetic"))
                model.chooseAgent("different")
                model.send()
                assertEquals("Unsent words", model.draft)
                assertEquals(0, calls)
                assertEquals("", model.agent)
                flag(state, false)
            }
            model.disconnect()
        }
    }

    @Test
    fun encryptedCredentialsRoundTripAndForget() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = SecureStore(context)
        val name = "test-only:${UUID.randomUUID()}"
        val value = "synthetic-test-secret"
        try {
            store.put(name, value)
            assertEquals(value, store.get(name))
            val prefs = context.getSharedPreferences("encrypted_gateway", 0)
            assertFalse(prefs.getString(digest(name.toByteArray()), "").orEmpty().contains(value))
            store.remove(name)
            assertNull(store.get(name))
        } finally {
            store.remove(name)
        }
    }

    @Test
    fun launchAndBackgroundDoNotConnectOrRecord() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[VoiceModel::class.java]
                assertFalse(model.connected)
                assertFalse(model.recording)
                assertFalse(model.speechConsent)
                model.draft = "Synthetic unsent draft"
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[VoiceModel::class.java]
                assertFalse(model.connected)
                assertFalse(model.recording)
                assertFalse(model.speechConsent)
                assertEquals("Synthetic unsent draft", model.draft)
            }
        }
    }

    @Test
    fun onDeviceEnglishSpanishSupportProbe() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(
            "Pixel on-device recognizer must be available",
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context),
        )
        if (Build.VERSION.SDK_INT < 33) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (language in listOf("en-US", "es-ES")) {
            val done = CountDownLatch(1)
            var reported = false
            var recognizer: SpeechRecognizer? = null
            instrumentation.runOnMainSync {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                recognizer!!.checkRecognitionSupport(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true),
                    context.mainExecutor,
                    object : RecognitionSupportCallback {
                        override fun onSupportResult(support: RecognitionSupport) {
                            Log.i(
                                "VoxkinQualification",
                                "$language installed=${support.installedOnDeviceLanguages} pending=${support.pendingOnDeviceLanguages} supported=${support.supportedOnDeviceLanguages}",
                            )
                            reported = true
                            done.countDown()
                        }

                        override fun onError(error: Int) {
                            Log.i(
                                "VoxkinQualification",
                                "$language support check error=$error (no cloud fallback)",
                            )
                            reported = true
                            done.countDown()
                        }
                    },
                )
            }
            assertTrue(
                "Recognizer support callback must complete",
                done.await(15, TimeUnit.SECONDS),
            )
            assertTrue(reported)
            instrumentation.runOnMainSync { recognizer?.destroy() }
        }
    }
}
