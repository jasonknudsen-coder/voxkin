package studio.voxkin.android

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.*
import org.json.JSONObject

data class Conversation(val id: String, val title: String, val sessionId: String = "")

data class GatewayVoice(val id: String, val name: String, val language: String)

data class ChatLine(val role: String, val text: String)

class VoiceModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app)
    var gateway by mutableStateOf(store.get("lastGateway") ?: "")
    var token by mutableStateOf("")
    var invitation by mutableStateOf("")
    var restricted by mutableStateOf(store.get("lastMode") == "restricted")
    var status by
        mutableStateOf("Connect to your own gateway. Tailscale must be connected separately.")
    var connected by mutableStateOf(false)
        private set

    var busy by mutableStateOf(false)
        private set

    var recording by mutableStateOf(false)
        private set

    var settling by mutableStateOf(false)
        private set

    var microphoneReady by mutableStateOf(false)
        private set

    var level by mutableFloatStateOf(0f)
        private set

    var draft by mutableStateOf("")
    var partial by mutableStateOf("")
        private set

    var language by mutableStateOf(store.get("language") ?: "en-US")
    var speechConsent by mutableStateOf(false)
    var voice by mutableStateOf("")
    var speechSource by mutableStateOf("local")
        private set
    private var gatewaySpeechAvailable by mutableStateOf(false)
    val sourceOptions: List<Pair<String, String>> get() = buildList {
        if (!restricted && gatewaySpeechAvailable) add("gateway" to "Gateway default")
        if (voices.isNotEmpty() || speechSource == "local") add("local" to "Local voices")
    }
    var voices by mutableStateOf<List<GatewayVoice>>(emptyList())
        private set

    var agents by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set

    var agent by mutableStateOf("")
        private set

    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set

    var messages by mutableStateOf<List<ChatLine>>(emptyList())
        private set

    var page by mutableIntStateOf(0)
    private var transport: Transport? = null
    private var generation = 0
    private var scopeJob: Job? = null
    private val operations = mutableSetOf<Job>()
    private var turnJob: Job? = null
    private var activeRun by mutableStateOf<String?>(null)
    val requestPending: Boolean get() = activeRun != null
    var speaking by mutableStateOf(false)
        private set
    var replyLength by mutableStateOf(runCatching { ReplyLength.valueOf(store.get("replyLength") ?: "BRIEF") }.getOrDefault(ReplyLength.BRIEF))
        private set
    private var sessionId = UUID.randomUUID().toString()
    private var recognizer: SpeechRecognizer? = null
    private var languageRecognizer: SpeechRecognizer? = null
    var languageStatus by
        mutableStateOf("Check that your selected on-device language model is installed.")
        private set

    private var speechGeneration = 0
    private var settleJob: Job? = null
    private var player: MediaPlayer? = null
    private var audioGeneration = 0
    private var speechJob: Job? = null
    private var focus: AudioFocusRequest? = null
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFile: File? = null
    private var localVoice = false
    private val session
        get() = "agent:$agent:voxkin:$sessionId"

    private fun credentialKey() = "${if(restricted)"guest" else "owner"}:${gateway.trim()}"

    private fun launchTask(block: suspend () -> Unit): Job {
        val current = generation
        val job =
            viewModelScope.launch(start = CoroutineStart.LAZY) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (current == generation)
                        status =
                            if (e is GatewayFailure) e.message ?: "Request failed."
                            else
                                "Request failed. Check connection, permissions and gateway compatibility. Nothing was replayed."
                }
            }
        operations.add(job)
        job.invokeOnCompletion { operations.remove(job) }
        job.start()
        return job
    }

    fun updateGateway(value: String) {
        if (connected || busy) return
        if (value != gateway) {
            token = ""
            invitation = ""
        }
        gateway = value
    }

    fun chooseLanguage(value: String) {
        if (recording || settling) return
        language = value
        store.put("language", value)
        val savedVoice = store.get("voice:${gateway.trim()}:$value")
        voice = savedVoice ?: voices.firstOrNull { it.language == value.substringBefore('-') }?.id.orEmpty()
        checkLanguage()
    }

    fun chooseSpeechSource(value: String) {
        if (!connected || busy || recording || settling || speaking) return
        if (sourceOptions.none { it.first == value }) return
        speechSource = value
        store.put("speech-source:${credentialKey()}", value)
    }

    fun chooseVoice(value: String) {
        if (busy || recording || settling || speaking) return
        if (voices.none { it.id == value && it.language == language.substringBefore('-') }) return
        voice = value
        store.put("voice:${gateway.trim()}:$language", value)
    }

    fun chooseSpeechConsent(value: Boolean) {
        speechConsent = value
        if (gateway.isNotBlank()) store.put("speech-consent:${gateway.trim()}", value.toString())
        if (!value) stopAudio()
    }

    fun chooseReplyLength(value: ReplyLength) {
        replyLength = value
        store.put("replyLength", value.name)
    }

    fun clearDraft() {
        if (busy) return
        stopRecording(true)
        draft = ""
    }

    fun previewVoice() {
        if (!connected || busy || recording || settling || !speechConsent) return
        speak(if (language.startsWith("es")) "Hola. Esta es tu voz de Voxkin." else "Hello. This is your Voxkin voice.")
    }

    private fun launchHistory(block: suspend () -> Unit) {
        if (!connected || busy || recording || settling) return
        busy = true
        val current = generation
        launchTask {
            try {
                block()
            } finally {
                if (current == generation) busy = false
            }
        }
    }

    fun stage(text: String) {
        runCatching { Protocol.setup(text) }
            .onSuccess {
                disconnect()
                gateway = Protocol.endpoint(it.gateway, it.restricted).toString()
                restricted = it.restricted
                token = if (it.restricted) "" else it.secret
                invitation = if (it.restricted) it.secret else ""
                page = 2
                status =
                    "Setup staged only. Check the gateway, then explicitly ${if(restricted)"Enroll" else "Connect"}."
            }
            .onFailure { status = "Invalid Voxkin setup link." }
    }

    fun connect() {
        disconnect()
        val current = generation
        busy = true
        status = "Connecting…"
        scopeJob =
            viewModelScope.launch {
                try {
                    gateway = Protocol.endpoint(gateway, restricted).toString()
                    val key = credentialKey()
                    val secret = token.ifBlank { store.get(key) ?: "" }
                    require(secret.isNotEmpty())
                    val events: (String, JSONObject) -> Unit = { n, p ->
                        viewModelScope.launch { if (generation == current) event(n, p) }
                    }
                    if (restricted) {
                        val t = RestrictedTransport(gateway, secret, viewModelScope, events)
                        transport = t
                        t.connect()
                        agent = t.agent
                        localVoice = true
                    } else {
                        val t =
                            OwnerTransport(events) {
                                viewModelScope.launch {
                                    if (generation == current && connected) {
                                        disconnect()
                                        status =
                                            "Disconnected. Reconnect manually; nothing was replayed."
                                    }
                                }
                            }
                        transport = t
                        val h = t.connect(gateway, store.identity(gateway), secret)
                        val grant = h.optJSONObject("auth")?.optString("deviceToken").orEmpty()
                        store.put(key, grant.ifBlank { secret })
                        val methods = h.optJSONObject("features")?.strings("methods").orEmpty()
                        localVoice = methods.contains("voxkin.speak")
                        gatewaySpeechAvailable = methods.contains("tts.speak")
                    }
                    if (current != generation) return@launch
                    if (restricted) store.put(key, secret)
                    store.put("lastGateway", gateway)
                    store.put("lastMode", if (restricted) "restricted" else "owner")
                    token = ""
                    agents =
                        transport!!.request("agents.list").rows("agents").map {
                            it.getString("id") to it.optString("name", it.getString("id"))
                        }
                    if (!restricted) agent = agents.firstOrNull()?.first ?: error("No agents")
                    speechSource = if (restricted) "local" else
                        store.get("speech-source:${credentialKey()}")
                            ?: if (localVoice) "local" else "gateway"
                    connected = true
                    speechConsent = store.get("speech-consent:${gateway.trim()}") == "true"
                    status = "Connected. Tap Record or type a message."
                    sessionId =
                        if (restricted) {
                            val created = transport!!.request("voxkin.history.create")
                            created.optJSONObject("conversation")?.getString("id")
                                ?: created.getString("id")
                        } else UUID.randomUUID().toString()
                    messages = emptyList()
                    if (localVoice)
                        runCatching { transport!!.request("voxkin.catalog") }
                            .onSuccess { c ->
                                require(
                                    c.optInt("version") == 1 &&
                                        c.optString("provider") == "voxkin-local" &&
                                        c.optBoolean("local") &&
                                        c.optBoolean("ready")
                                )
                                voices =
                                    c.rows("voices").take(50).map {
                                        GatewayVoice(
                                            it.getString("id"),
                                            it.optString("name", it.getString("id")),
                                            it.getString("language"),
                                        )
                                    }
                                chooseLanguage(language)
                            }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (current == generation) {
                        transport?.close()
                        transport = null
                        connected = false
                        status =
                            if (e is GatewayFailure) e.message ?: "Connection failed"
                            else "Unable to connect. Check URL, token and gateway support."
                    }
                } finally {
                    if (current == generation) busy = false
                }
            }
    }

    fun enroll() {
        if (busy) return
        val current = generation
        launchTask {
            gateway = Protocol.endpoint(gateway, true).toString()
            require(invitation.matches(Regex("[A-Za-z0-9_-]{32,256}")))
            busy = true
            try {
                val t = RestrictedTransport(gateway, "", viewModelScope) { _, _ -> }
                try {
                    val r = t.call(obj("op" to "redeem", "secret" to invitation))
                    val d = r.optJSONObject("device") ?: r
                    val secret = d.getString("deviceSecret")
                    store.put("guest:${gateway.trim()}", secret)
                    store.put("lastGateway", gateway.trim())
                    store.put("lastMode", "restricted")
                    token = ""
                    invitation = ""
                    status =
                        "Enrollment sent. Phone ID ${d.optString("id").take(8)}. Ask the owner to approve, then Connect."
                } finally {
                    t.close()
                }
            } finally {
                if (current == generation) busy = false
            }
        }
    }

    fun disconnect() {
        generation++
        operations.toList().forEach { it.cancel() }
        operations.clear()
        scopeJob?.cancel()
        turnJob?.cancel()
        activeRun = null
        transport?.close()
        transport = null
        connected = false
        busy = false
        stopRecording(true)
        languageRecognizer?.destroy()
        languageRecognizer = null
        stopAudio()
        speechConsent = false
        gatewaySpeechAvailable = false
        voices = emptyList()
        conversations = emptyList()
        messages = emptyList()
        agents = emptyList()
        status = "Disconnected. Nothing will be replayed."
    }

    fun background() {
        disconnect()
    }

    fun forget() {
        disconnect()
        store.remove("speech-source:${credentialKey()}")
        store.remove(credentialKey())
        store.remove("speech-consent:${gateway.trim()}")
        if (!restricted) store.remove("identity:${gateway.trim()}")
        token = ""
        invitation = ""
        status = "Local access forgotten. This does not revoke access on the gateway."
    }

    fun chooseAgent(id: String) {
        if (
            busy ||
                !connected ||
                recording ||
                settling ||
                restricted ||
                agents.none { it.first == id }
        )
            return
        stopAudio()
        agent = id
        sessionId = UUID.randomUUID().toString()
        messages = emptyList()
        conversations = emptyList()
        draft = ""
    }

    fun send() {
        if (!connected || busy || recording || settling || draft.isBlank()) return
        val text = draft.trim()
        if (text.length > 4000) {
            status = "Keep messages under 4,000 characters."
            return
        }
        val t = transport ?: return
        val requestText = replyLength.requestText(text, language)
        stopAudio()
        val current = generation
        val run = UUID.randomUUID().toString()
        activeRun = run
        busy = true
        status = "Waiting for reply…"
        messages = messages + ChatLine("user", text)
        draft = ""
        turnJob =
            viewModelScope.launch {
                try {
                    withTimeout(120000) {
                        if (restricted && sessionId.isEmpty()) {
                            val created = t.request("voxkin.history.create")
                            sessionId =
                                created.optJSONObject("conversation")?.getString("id")
                                    ?: created.getString("id")
                        }
                        val result =
                            t.request(
                                "chat.send",
                                obj(
                                    "agentId" to agent,
                                    "sessionKey" to session,
                                    "message" to requestText,
                                    "idempotencyKey" to run,
                                    "deliver" to false,
                                ),
                            )
                        if (result.optString("runId", run) != run)
                            throw GatewayFailure("Gateway returned an unexpected request ID. Outcome unknown; nothing was replayed.")
                        while (current == generation && activeRun != null) delay(200)
                    }
                } catch (e: CancellationException) {
                    if (current == generation && activeRun != null) {
                        activeRun = null
                        busy = false
                        status = "Turn stopped or timed out; nothing was replayed."
                    }
                } catch (e: Exception) {
                    if (current == generation) {
                        activeRun = null
                        busy = false
                        status =
                            if (e is GatewayFailure) e.message ?: "Request failed"
                            else "Reply failed; outcome may be unknown. Nothing was replayed."
                    }
                }
            }
    }

    private fun event(name: String, p: JSONObject) {
        if (
            name != "chat" ||
                p.optString("runId") != activeRun ||
                p.optString("sessionKey") != session
        )
            return
        when (p.optString("state")) {
            "final" -> {
                activeRun = null
                busy = false
                val text = p.optJSONObject("message")?.let { Protocol.finalText(it) }.orEmpty()
                if (text.isNotBlank()) {
                    messages = messages + ChatLine("assistant", text)
                    status = "Reply ready."
                    if (speechConsent) speak(text)
                } else status = "Reply contained no displayable text."
            }
            "error",
            "aborted" -> {
                activeRun = null
                busy = false
                status = "Turn ended without a reply. Nothing was replayed."
            }
        }
    }

    fun cancelTurn() {
        val run = activeRun
        val t = transport
        activeRun = null
        turnJob?.cancel()
        if (run != null) busy = false
        stopAudio()
        status =
            if (busy) "Audio stopped. Gateway operation still in progress."
            else "Stopped. Nothing was replayed."
        if (run != null && t != null)
            launchTask { t.request("chat.abort", obj("sessionKey" to session, "runId" to run)) }
    }

    private fun speak(text: String) {
        val t = transport ?: return
        stopAudio()
        val current = generation
        val audioEpoch = audioGeneration
        val useLocal = speechSource == "local"
        val selectedVoice = voice
        val selectedLanguage = language.substringBefore('-')
        speaking = true
        speechJob = launchTask {
            try {
                if (useLocal)
                    require(localVoice && voices.any { it.id == selectedVoice && it.language == selectedLanguage })
                else require(!restricted && gatewaySpeechAvailable) { "Gateway speech source is unavailable." }
                for (chunk in SpeechPlan.chunks(SpeechText.prepare(text))) {
                    currentCoroutineContext().ensureActive()
                    val response =
                        t.request(
                            if (useLocal) "voxkin.speak" else "tts.speak",
                            if (useLocal)
                                obj(
                                    "text" to chunk,
                                    "language" to selectedLanguage,
                                    "voice" to selectedVoice,
                                )
                            else obj("text" to chunk),
                        )
                    if (
                        current != generation ||
                            audioEpoch != audioGeneration ||
                            !speechConsent ||
                            recording ||
                            settling
                    )
                        return@launchTask
                    if (useLocal)
                        require(
                            response.optString("provider") == "voxkin-local" &&
                                response.optString("voice") == selectedVoice &&
                                response.optString("language") == selectedLanguage
                        )
                    require(response.optString("provider").isNotBlank())
                    val encoded = response.optString("audioBase64")
                    require(encoded.length <= 8_000_000)
                    val bytes = Base64.getDecoder().decode(encoded)
                    require(bytes.size in 1..6_000_000)
                    require(
                        response.optString("mimeType") in
                            listOf(
                                "audio/mpeg",
                                "audio/mp3",
                                "audio/wav",
                                "audio/x-wav",
                                "audio/mp4",
                                "audio/aac",
                            )
                    )
                    playClip(bytes, current, audioEpoch)
                }
                if (current == generation && audioEpoch == audioGeneration) status = "Reply ready."
            } finally {
                if (audioEpoch == audioGeneration) speaking = false
            }
        }
    }

    private suspend fun playClip(bytes: ByteArray, current: Int, audioEpoch: Int) {
        val attributes =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        val focusRequest =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { change ->
                    if (change < 0 && audioEpoch == audioGeneration) {
                        stopAudio()
                        status = "Audio interrupted."
                    }
                }
                .build()
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            throw GatewayFailure("Audio focus unavailable. Reply remains as text.")
        focus = focusRequest
        val file = File.createTempFile("voice-", ".audio", getApplication<Application>().cacheDir)
        audioFile = file
        val p = MediaPlayer()
        try {
            file.writeBytes(bytes)
            suspendCancellableCoroutine<Unit> { cont ->
                player = p
                p.setAudioAttributes(attributes)
                p.setDataSource(file.path)
                p.setOnPreparedListener {
                    if (
                        cont.isActive &&
                            current == generation &&
                            audioEpoch == audioGeneration &&
                            speechConsent &&
                            player === it
                    ) {
                        it.start()
                        status = "Speaking…"
                    } else if (cont.isActive) cont.cancel()
                }
                p.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) {} }
                p.setOnErrorListener { _, _, _ ->
                    if (cont.isActive)
                        cont.resumeWith(
                            Result.failure(
                                GatewayFailure("Audio playback failed. Reply remains as text.")
                            )
                        )
                    true
                }
                p.prepareAsync()
            }
        } finally {
            p.release()
            if (player === p) player = null
            file.delete()
            if (audioFile == file) audioFile = null
            audioManager.abandonAudioFocusRequest(focusRequest)
            if (focus === focusRequest) focus = null
        }
    }

    fun stopAudio() {
        audioGeneration++
        speaking = false
        speechJob?.cancel()
        speechJob = null
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
        player?.runCatching { stop() }
        player?.release()
        player = null
        audioFile?.delete()
        audioFile = null
    }

    private fun recognitionIntent() =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

    fun checkLanguage() {
        if (Build.VERSION.SDK_INT < 33) {
            languageStatus =
                "This Android version cannot query language models. Try on-device recording or type."
            return
        }
        val context = getApplication<Application>()
        val chosen = language
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            languageStatus = "On-device recognition is unavailable; type instead."
            return
        }
        languageStatus = "Checking on-device language model…"
        languageRecognizer?.destroy()
        try {
            languageRecognizer =
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { r ->
                    r.checkRecognitionSupport(
                        recognitionIntent(),
                        context.mainExecutor,
                        object : RecognitionSupportCallback {
                            override fun onSupportResult(s: RecognitionSupport) {
                                if (language != chosen || languageRecognizer !== r) return
                                languageStatus =
                                    when {
                                        s.installedOnDeviceLanguages.any {
                                            it.equals(chosen, true)
                                        } -> "$chosen on-device model is installed."
                                        s.pendingOnDeviceLanguages.any {
                                            it.equals(chosen, true)
                                        } -> "$chosen model download is pending."
                                        s.supportedOnDeviceLanguages.any {
                                            it.equals(chosen, true)
                                        } -> "$chosen is supported but needs downloading."
                                        else ->
                                            "$chosen is not reported as available by this on-device service."
                                    }
                            }

                            override fun onError(error: Int) {
                                if (language == chosen && languageRecognizer === r)
                                    languageStatus =
                                        "Could not check language model availability. No cloud fallback is enabled."
                            }
                        },
                    )
                }
        } catch (_: Exception) {
            languageStatus = "Language check unavailable. No cloud fallback is enabled."
        }
    }

    fun downloadLanguage() {
        if (Build.VERSION.SDK_INT < 33) {
            languageStatus = "Download speech models in Android speech settings."
            return
        }
        val context = getApplication<Application>()
        val chosen = language
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            languageStatus = "On-device recognition is unavailable."
            return
        }
        languageRecognizer?.destroy()
        try {
            languageRecognizer =
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { r ->
                    languageStatus = "Requesting $chosen on-device model download…"
                    if (Build.VERSION.SDK_INT >= 34)
                        r.triggerModelDownload(
                            recognitionIntent(),
                            context.mainExecutor,
                            object : ModelDownloadListener {
                                override fun onProgress(completedPercent: Int) {
                                    if (language == chosen && languageRecognizer === r)
                                        languageStatus =
                                            "Downloading $chosen model: $completedPercent%"
                                }

                                override fun onSuccess() {
                                    if (language == chosen && languageRecognizer === r) {
                                        languageStatus = "Model downloaded. Checking availability…"
                                        checkLanguage()
                                    }
                                }

                                override fun onScheduled() {
                                    if (language == chosen && languageRecognizer === r)
                                        languageStatus =
                                            "Model download scheduled by Android. Check again once connected to Wi-Fi."
                                }

                                override fun onError(error: Int) {
                                    if (language == chosen && languageRecognizer === r)
                                        languageStatus =
                                            "Model download could not complete. Check Android speech settings or type instead."
                                }
                            },
                        )
                    else {
                        r.triggerModelDownload(recognitionIntent())
                        languageStatus =
                            "Download requested. Tap Check model to verify when complete."
                    }
                }
        } catch (_: Exception) {
            languageStatus = "Unable to request model download. Check Android speech settings."
        }
    }

    fun startRecording() {
        if (!connected || busy || recording || settling) return
        val context = getApplication<Application>()
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            status =
                "On-device dictation is unavailable. Install your device speech model or type; cloud fallback is disabled."
            return
        }
        stopAudio()
        speechGeneration++
        val current = speechGeneration
        partial = ""
        microphoneReady = false
        recording = true
        status = "Preparing microphone…"
        try {
            recognizer =
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { r ->
                    r.setRecognitionListener(
                        object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                if (current == speechGeneration) {
                                    microphoneReady = true
                                    status = "Listening — speak now. Tap Stop when finished."
                                }
                            }

                            override fun onBeginningOfSpeech() {}

                            override fun onRmsChanged(rmsdB: Float) {
                                if (current == speechGeneration)
                                    level = ((rmsdB + 2) / 12).coerceIn(0f, 1f)
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                if (current == speechGeneration) {
                                    recording = false
                                    microphoneReady = false
                                    settling = true
                                    status = "Finishing transcription…"
                                    startSettleTimeout(current)
                                }
                            }

                            override fun onError(error: Int) {
                                if (current == speechGeneration) {
                                    val recovered = finishRecognition(current, null)
                                    if (!recovered) status =
                                        when (error) {
                                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                                "Microphone permission required."
                                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                                                "On-device language model unavailable. Install it in Android speech settings or type."
                                            SpeechRecognizer.ERROR_NO_MATCH,
                                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                                "No speech recognized. Tap Record to try again or type."
                                            else ->
                                                "On-device dictation failed. You can type instead; no cloud fallback was used."
                                        }
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                if (current == speechGeneration)
                                    finishRecognition(
                                        current,
                                        results
                                            ?.getStringArrayList(
                                                SpeechRecognizer.RESULTS_RECOGNITION
                                            )
                                            ?.firstOrNull(),
                                    )
                            }

                            override fun onPartialResults(results: Bundle?) {
                                if (current == speechGeneration) {
                                    val hypothesis = results
                                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                        ?.firstOrNull()
                                    // Empty callbacks must not erase useful words from this capture.
                                    if (!hypothesis.isNullOrBlank()) partial = hypothesis
                                }
                            }

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        }
                    )
                    r.startListening(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            )
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    )
                }
        } catch (_: Exception) {
            stopRecording(true)
            status =
                "On-device dictation unavailable. Check microphone permission and speech model, or type instead."
        }
    }

    private fun startSettleTimeout(current: Int) {
        settleJob?.cancel()
        settleJob =
            viewModelScope.launch {
                delay(5000)
                if (current == speechGeneration) {
                    if (!finishRecognition(current, null))
                        status = "No speech recognized. Please record again or type; nothing was sent."
                }
            }
    }

    fun stopRecording(discard: Boolean = false) {
        if (discard) {
            speechGeneration++
            settleJob?.cancel()
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
            recording = false
            settling = false
            microphoneReady = false
            partial = ""
            level = 0f
        } else if (recording) {
            recording = false
            microphoneReady = false
            settling = true
            status = "Finishing transcription…"
            recognizer?.stopListening()
            startSettleTimeout(speechGeneration)
        }
    }

    private fun finishRecognition(current: Int, text: String?): Boolean {
        if (current != speechGeneration) return false
        val recovered = text.isNullOrBlank() && partial.isNotBlank()
        val captured = text?.takeIf { it.isNotBlank() } ?: partial
        speechGeneration++
        settleJob?.cancel()
        recognizer?.destroy()
        recognizer = null
        recording = false
        settling = false
        microphoneReady = false
        level = 0f
        partial = ""
        if (captured.isNotBlank()) {
            draft = listOf(draft, captured).filter { it.isNotBlank() }.joinToString(" ")
            status = if (recovered)
                "Final transcription unavailable. Recovered words may be incomplete — review or edit, then tap Send. Nothing was sent."
            else "Review or edit your words, then tap Send."
        } else status = "No speech recognized. Tap Record to try again or type."
        return captured.isNotBlank()
    }

    fun listHistory() {
        if (!connected || busy || recording || settling) return
        launchHistory {
            val r =
                transport!!.request(
                    if (restricted) "voxkin.history.list" else "sessions.list",
                    if (restricted) obj("limit" to 20)
                    else
                        obj(
                            "agentId" to agent,
                            "search" to "agent:$agent:voxkin:",
                            "limit" to 50,
                            "includeDerivedTitles" to true,
                            "archived" to false,
                            "includeGlobal" to false,
                            "includeUnknown" to false,
                            "excludeSubagents" to true,
                            "excludeCron" to true,
                            "excludeSystem" to true,
                        ),
                )
            conversations =
                r.rows(if (restricted) "conversations" else "sessions").mapNotNull { row ->
                    val id = row.optString(if (restricted) "id" else "key")
                    if (
                        if (restricted) runCatching { UUID.fromString(id) }.isFailure
                        else !Protocol.owns(id, agent)
                    )
                        null
                    else
                        Conversation(
                            id,
                            listOf("label", "autoLabel", "title", "derivedTitle", "displayName")
                                .map { row.optString(it) }
                                .firstOrNull { it.isNotBlank() }
                                ?.let { Protocol.text(obj("role" to "user", "text" to it)) }
                                ?.takeUnless { it.startsWith("[Voxkin reply preference") }
                                ?: "Conversation",
                            row.optString("sessionId"),
                        )
                }
        }
    }

    fun newConversation() {
        if (!connected || busy || recording || settling) return
        launchHistory {
            stopAudio()
            sessionId =
                if (restricted) {
                    val r = transport!!.request("voxkin.history.create")
                    r.optJSONObject("conversation")?.getString("id") ?: r.getString("id")
                } else UUID.randomUUID().toString()
            messages = emptyList()
            draft = ""
            page = 0
            status = "New conversation ready."
        }
    }

    fun openHistory(c: Conversation) {
        if (busy || recording || settling) return
        launchHistory {
            stopAudio()
            val r =
                transport!!.request(
                    if (restricted) "voxkin.history.messages" else "chat.history",
                    if (restricted) obj("conversationId" to c.id, "limit" to 50)
                    else
                        obj(
                            "sessionKey" to c.id,
                            "agentId" to agent,
                            "limit" to 50,
                            "offset" to 0,
                            "maxBytes" to 128000,
                        ),
                )
            sessionId = c.id.substringAfterLast(':')
            messages =
                r.rows("messages").mapNotNull {
                    val text = Protocol.text(it)
                    if (text.isBlank()) null else ChatLine(it.optString("role"), text)
                }
            draft = ""
            page = 1
            status = "Conversation loaded (latest page)."
        }
    }

    fun rename(c: Conversation, title: String) {
        if (busy || title.isBlank() || !connected || (!restricted && c.sessionId.isEmpty())) return
        launchHistory {
            transport!!.request(
                if (restricted) "voxkin.history.rename" else "sessions.patch",
                if (restricted) obj("conversationId" to c.id, "title" to title.take(80))
                else
                    obj(
                        "key" to c.id,
                        "agentId" to agent,
                        "expectedSessionId" to c.sessionId,
                        "label" to title.take(80),
                    ),
            )
            conversations =
                conversations.map { if (it.id == c.id) it.copy(title = title.take(80)) else it }
        }
    }

    fun delete(c: Conversation) {
        if (busy || !connected || (!restricted && c.sessionId.isEmpty())) return
        launchHistory {
            val t = transport ?: return@launchHistory
            if (restricted) t.request("voxkin.history.delete", obj("conversationId" to c.id))
            else {
                val target =
                    obj("key" to c.id, "agentId" to agent, "expectedSessionId" to c.sessionId)
                t.request("sessions.patch", JSONObject(target.toString()).put("archived", true))
                try {
                    t.request(
                        "sessions.delete",
                        JSONObject(target.toString())
                            .put("archivedOnly", true)
                            .put("deleteTranscript", true),
                    )
                } catch (e: Exception) {
                    if (e !is CancellationException)
                        runCatching {
                            t.request(
                                "sessions.patch",
                                JSONObject(target.toString()).put("archived", false),
                            )
                        }
                    throw e
                }
            }
            if (sessionId == c.id.substringAfterLast(':')) {
                stopAudio()
                messages = emptyList()
                draft = ""
                sessionId = if (restricted) "" else UUID.randomUUID().toString()
            }
            conversations = conversations.filter { it.id != c.id }
        }
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}
