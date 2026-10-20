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

data class Conversation(val id: String, val title: String, val sessionId: String = "", val preview: String = "", val updatedAt: Long = 0)

data class GatewayVoice(val id: String, val name: String, val language: String)

data class ChatLine(val role: String, val text: String, val id: String = "")

class VoiceModel @JvmOverloads constructor(
    app: Application, private val automaticConnection: Boolean = true
) : AndroidViewModel(app) {
    private val store = SecureStore(app)
    var gateway by mutableStateOf(store.get("lastGateway") ?: "")
    var token by mutableStateOf("")
    var invitation by mutableStateOf("")
    var restricted by mutableStateOf(store.get("lastMode") == "restricted")
    var status by
        mutableStateOf("Connect to your own gateway. Tailscale must be connected separately.")
    var pairingApproval by mutableStateOf<GatewayPairingRequired?>(null)
        private set
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

    private val spokenDraft = SpokenDraft()
    private var draftRevision = 0L
    private var captureRevision = -1L
    private var captureContext = ""
    private var captureTrusted = false
    private var draftValue by mutableStateOf("")
    private var draftContext: String? = null
    var draft: String
        get() = draftValue
        set(value) {
            spokenDraft.clear()
            draftRevision++
            draftValue = value
            draftContext = if (value.isBlank()) null else conversationContext()
        }
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

    var timing by mutableStateOf(TurnTiming())
        private set
    var speechProvider by mutableStateOf("")
        private set
    private val progressNotices = ProgressNotices()
    var progressText by mutableStateOf("")
        private set
    val pendingText: String get() = HistoryMetadata.clip(listOf(progressText, streamingText).filter { it.isNotBlank() }.joinToString("\n\n"), 16000)
    var streamingText by mutableStateOf("")
        private set

    var messages by mutableStateOf<List<ChatLine>>(emptyList())
        private set

    var conversationsHaveMore by mutableStateOf(false)
        private set
    var messagesHaveMore by mutableStateOf(false)
        private set
    private var historyCursor: String? = null
    private var historyOffset = 0
    private var messageCursor: String? = null
    private var messageOffset = 0
    val selectedConversationId: String get() = if (!connected || !conversationReady) "" else if (restricted) sessionId else session
    private var pageValue by mutableIntStateOf(0)
    var page: Int
        get() = pageValue
        set(value) {
            if (value != pageValue) endHandsFree()
            pageValue = value
        }
    private var transport: Transport? = null
    private var automaticConnectionSuppressed = false
    var conversationReady by mutableStateOf(true)
        private set
    var connectionEpoch by mutableStateOf(0)
        private set
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

    var conversationalFeedback by mutableStateOf(store.get("conversationalFeedback") != "false")
        private set
    var feedbackPlaying by mutableStateOf(false)
        private set
    private val feedback = TurnFeedback()
    private var feedbackJob: Job? = null
    private var feedbackTurn: FeedbackTurn? = null
    private var feedbackAudio: FeedbackPlayback = FeedbackAudio(app, { feedbackPlaying = it }, { feedbackFocusLost() })
    private data class FeedbackTurn(
        val token: FeedbackToken, val run: String, var session: String, val generation: Int,
        val transport: Transport, val source: String, val voice: String, val language: String,
        val localAvailable: Boolean, val gatewayAvailable: Boolean,
        val accepted: CompletableDeferred<Unit> = CompletableDeferred(),
        val notice: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    private var speechGeneration = 0
    private var settleJob: Job? = null
    private var player: MediaPlayer? = null
    private var audioGeneration = 0
    private var speechJob: Job? = null
    private var focus: AudioFocusRequest? = null
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFile: File? = null
    var historyCapabilities by mutableStateOf(HistoryCapabilities())
        private set
    private fun validHistoryTarget(c: Conversation): Boolean =
        if (restricted) runCatching { UUID.fromString(c.id).toString() == c.id.lowercase() }.getOrDefault(false)
        else Protocol.owns(c.id, agent) && runCatching { UUID.fromString(c.sessionId).toString() == c.sessionId.lowercase() }.getOrDefault(false)
    private val historyIdle get() = connected && !busy && !recording && !settling
    fun canRename(c: Conversation) = historyIdle && historyCapabilities.rename && validHistoryTarget(c)
    fun canDelete(c: Conversation) = historyIdle && historyCapabilities.delete && validHistoryTarget(c)
    private var progressSubscriptionAvailable = false
    private var subscriptionJob: Job? = null
    private var subscriptionTarget: String? = null
    private var subscribedSession: String? = null
    private var subscriptionGeneration = 0
    private var historyPreviewAvailable = false
    private var historyAutoLabelAvailable = false
    private var localVoice = false
    private val session
        get() = "agent:$agent:voxkin:$sessionId"

    var conversationMode by mutableStateOf(false)
        private set
    private var handsFreePermissionEpoch = 0
    private val handsFree = HandsFreeSession(
        scope = viewModelScope,
        canListen = { canStartHandsFree },
        listen = { startRecording() },
        finish = { if (recording && partial.isNotBlank()) stopRecording() },
        changed = { conversationMode = it },
    )
    val canStartHandsFree: Boolean get() = connected && page == 0 && conversationReady &&
        !busy && !requestPending && !recording && !settling && !speaking && draft.isBlank() &&
        speechConsent && (if (speechSource == "local") localVoice && voices.any {
            it.id == voice && it.language == language.substringBefore('-')
        } else !restricted && gatewaySpeechAvailable)

    fun handsFreeStartToken(): String = "$generation|$handsFreePermissionEpoch|${conversationContext()}"

    fun startHandsFree(token: String) {
        if (token != handsFreeStartToken() || !canStartHandsFree) return
        handsFree.start()
    }

    fun endHandsFree() {
        handsFreePermissionEpoch++
        if (!conversationMode) return
        handsFree.stop() // Invalidate auto-send/resume before stopping capture or playback.
        val words = partial
        stopRecording(discard = true)
        if (words.isNotBlank()) draft = listOf(draft, words).filter { it.isNotBlank() }.joinToString(" ")
        stopAudio(endHandsFree = false)
        status = if (requestPending) "Hands-free ended. Your agent may still be working; use Cancel request to cancel."
            else if (draft.isNotBlank()) "Hands-free ended. Review your words, then tap Send. Nothing was sent."
            else "Hands-free ended."
    }

    fun interruptHandsFreeReply() {
        if (!conversationMode || !speaking || requestPending) return
        stopAudio(endHandsFree = false)
        handsFree.replyFinished()
    }

    private fun conversationContext() = "${credentialKey()}|$agent|$sessionId"

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
            pairingApproval = null
            requestAdmin = false
            token = ""
            invitation = ""
            draft = ""
            automaticConnectionSuppressed = true
        }
        gateway = value
    }

    fun chooseRestrictedMode(value: Boolean) {
        if (connected || busy || value == restricted) return
        chooseRequestAdmin(false)
        pairingApproval = null
        restricted = value
        token = ""
        invitation = ""
        draft = ""
        automaticConnectionSuppressed = true
    }

    fun chooseLanguage(value: String) {
        if (recording || settling) return
        endHandsFree()
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

    fun chooseConversationalFeedback(value: Boolean) {
        conversationalFeedback = value
        store.put("conversationalFeedback", value.toString())
        if (!value) stopFeedback()
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
        endHandsFree()
        if (!connected || busy || recording || settling || !speechConsent) return
        speak(if (language.startsWith("es")) "Hola. Esta es tu voz de Voxkin." else "Hello. This is your Voxkin voice.", answer = false)
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

    var requestAdmin by mutableStateOf(false)
        private set
    var adminPermissionGranted by mutableStateOf(false)
        private set
    var supportsAccessManagement by mutableStateOf(false)
        private set
    val canManageAccess: Boolean get() = connected && !restricted && requestAdmin && adminPermissionGranted && supportsAccessManagement
    var accessLoaded by mutableStateOf(false)
        private set
    var accessDevices by mutableStateOf<List<AccessRow>>(emptyList())
        private set
    var accessInvites by mutableStateOf<List<AccessRow>>(emptyList())
        private set
    var accessAgents by mutableStateOf<List<String>>(emptyList())
        private set
    var inviteAgent by mutableStateOf("")
    var inviteLabel by mutableStateOf("")
    var accessConfirmation by mutableStateOf<AccessConfirmation?>(null)
        private set
    var shareInvitation by mutableStateOf("")
        private set
    var invitationExpiresAt by mutableStateOf(0L)
        private set
    private var sharedInviteId = ""
    private var inviteExpiryJob: Job? = null
    private var accessRevision = 0

    fun chooseRequestAdmin(value: Boolean) {
        if (connected || busy || restricted) return
        requestAdmin = value
    }

    fun hideInvitation() {
        shareInvitation = ""
        sharedInviteId = ""
        invitationExpiresAt = 0
        inviteExpiryJob?.cancel()
        inviteExpiryJob = null
    }

    private fun clearAccess() {
        adminPermissionGranted = false
        supportsAccessManagement = false
        accessLoaded = false
        accessDevices = emptyList(); accessInvites = emptyList(); accessAgents = emptyList()
        accessConfirmation = null
        inviteAgent = ""; inviteLabel = ""
        accessRevision++
        hideInvitation()
    }

    private suspend fun readAccess(t: Transport, current: Int) {
        val result = t.request("voxkin.access.list")
        currentCoroutineContext().ensureActive()
        if (generation != current || !canManageAccess) return
        val agents = result.strings("agents")
        require(agents.size <= 500 && agents.all { AccessPolicy.agent(it) })
        val devices = result.rows("devices"); val invites = result.rows("invites")
        require(devices.size <= 500 && invites.size <= 500)
        val parsedDevices = devices.map { AccessRow.parse(it, true) }
        val parsedInvites = invites.map { AccessRow.parse(it, false) }
        require(parsedDevices.map { it.id }.distinct().size == parsedDevices.size && parsedInvites.map { it.id }.distinct().size == parsedInvites.size)
        accessDevices = parsedDevices; accessInvites = parsedInvites; accessAgents = agents.distinct()
        if (inviteAgent !in accessAgents) inviteAgent = accessAgents.firstOrNull().orEmpty()
        if (sharedInviteId.isNotEmpty() && accessInvites.none { it.id == sharedInviteId }) hideInvitation()
        accessRevision++
        accessConfirmation = null
        accessLoaded = true
    }

    fun refreshAccess() {
        if (!canManageAccess || busy || recording || settling || speaking) return
        val t = transport ?: return
        val current = generation
        accessLoaded = false
        accessConfirmation = null
        busy = true
        launchTask {
            try {
                readAccess(t, current)
                if (current == generation) status = "Phone access refreshed."
            } finally { if (current == generation) busy = false }
        }
    }

    fun prepareAccessAction(action: String, id: String = "") {
        if (!canManageAccess || !accessLoaded || busy || recording || settling || speaking) return
        val row = when (action) {
            "invite" -> {
                if (inviteAgent !in accessAgents || !AccessPolicy.label(inviteLabel.trim())) return
                AccessRow("", inviteLabel.trim(), inviteAgent, "", 0)
            }
            "approve" -> accessDevices.firstOrNull { it.id == id && it.status == "pending" && it.agent in accessAgents && it.expiresAt > System.currentTimeMillis() }
            "revoke" -> accessDevices.firstOrNull { it.id == id && it.status in listOf("pending", "active") }
            "cancel" -> accessInvites.firstOrNull { it.id == id && it.expiresAt > System.currentTimeMillis() }
            else -> null
        } ?: return
        accessConfirmation = AccessConfirmation(action, row.id, row.label, row.agent, generation, accessRevision)
    }

    fun dismissAccessAction() { accessConfirmation = null }

    fun confirmAccessAction() {
        val pending = accessConfirmation ?: return
        accessConfirmation = null
        if (!canManageAccess || !accessLoaded || busy || generation != pending.generation || accessRevision != pending.revision) return
        // Recheck row state/expiry immediately before sending a confirmed mutation.
        if (pending.action == "approve" && accessDevices.none { it.id == pending.id && it.status == "pending" && it.agent in accessAgents && it.expiresAt > System.currentTimeMillis() }) return
        if (pending.action == "cancel" && accessInvites.none { it.id == pending.id && it.expiresAt > System.currentTimeMillis() }) return
        val t = transport ?: return
        val current = generation
        val origin = gateway
        val params = when (pending.action) {
            "invite" -> obj("agentId" to pending.agent, "label" to pending.label)
            "approve", "revoke" -> obj("deviceId" to pending.id)
            "cancel" -> obj("inviteId" to pending.id)
            else -> return
        }
        busy = true
        accessLoaded = false
        hideInvitation()
        launchTask {
            try {
                val result = t.request("voxkin.access.${pending.action}", params)
                currentCoroutineContext().ensureActive()
                if (current != generation || !canManageAccess) return@launchTask
                if (pending.action == "invite") {
                    val row = AccessRow.parse(result, false)
                    require(row.agent == pending.agent && row.label == pending.label)
                    val remaining = row.expiresAt - System.currentTimeMillis()
                    require(remaining in 1..3_600_000)
                    val link = AccessPolicy.invitation(origin, result.getString("inviteSecret"))
                    shareInvitation = link; sharedInviteId = row.id; invitationExpiresAt = row.expiresAt
                    inviteExpiryJob = viewModelScope.launch {
                        delay(remaining)
                        if (current == generation && sharedInviteId == row.id) hideInvitation()
                    }
                }
                status = "Access action completed. Refreshing…"
                readAccess(t, current)
                if (current == generation) status = "Phone access updated."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (current == generation) {
                    hideInvitation()
                    status = "Access action could not be verified; outcome may be unknown. Refresh before retrying. Nothing was replayed."
                }
            } finally { if (current == generation) busy = false }
        }
    }

    var scannedSetup by mutableStateOf<Protocol.Setup?>(null)
        private set

    var setupFlowActive = false
        private set

    fun restoreSetupFlow() {
        if (!setupFlowActive) {
            page = 2
            status = "Setup paused. Scan or paste a setup link, or explicitly Connect using the displayed fields."
        }
        setupFlowActive = true
        automaticConnectionSuppressed = true
    }

    fun prepareSetupScan(): Boolean {
        if (connected || busy || recording || settling || speaking) return false
        restoreSetupFlow()
        scannedSetup = null
        return true
    }

    fun reviewSetupScan(text: String?) {
        // Even cancellation must not reconnect saved access underneath the setup flow.
        restoreSetupFlow()
        scannedSetup = null
        page = 2
        if (text == null) {
            status = "Scan cancelled. You can paste a setup link instead."
            return
        }
        if (connected || busy) {
            status = "Setup changed while scanning. Disconnect and scan again."
            return
        }
        scannedSetup = runCatching { Protocol.setup(text) }.getOrNull()
        status = if (scannedSetup == null) "This QR is not a valid Voxkin setup or invitation. Nothing changed."
            else "QR scanned. Review the gateway and access type before using it."
    }

    fun discardSetupScan() {
        scannedSetup = null
        status = "Scanned setup discarded. Nothing changed."
    }

    fun useScannedSetup() {
        val setup = scannedSetup ?: return
        scannedSetup = null
        if (connected || busy) {
            status = "Setup changed. Disconnect and scan again."
            return
        }
        applySetup(setup)
    }

    private fun applySetup(setup: Protocol.Setup) {
        requestAdmin = false
        restoreSetupFlow()
        disconnect()
        gateway = Protocol.endpoint(setup.gateway, setup.restricted).toString()
        restricted = setup.restricted
        token = if (setup.restricted) "" else setup.secret
        invitation = if (setup.restricted) setup.secret else ""
        page = 2
        status = "Setup staged only. Check the gateway, then explicitly ${if(restricted)"Enroll" else "Connect"}."
    }

    fun stage(text: String) {
        scannedSetup = null
        runCatching { Protocol.setup(text) }
            .onSuccess { applySetup(it) }
            .onFailure { status = "Invalid Voxkin setup link." }
    }

    fun connect() {
        setupFlowActive = false
        disconnect(allowResume = true)
        automaticConnectionSuppressed = false
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
                        val hello = t.connect()
                        if (current != generation) return@launch
                        historyCapabilities = HistoryCapabilities.restricted(hello.optBoolean("history"), hello.optBoolean("historyActions"))
                        val phoneId = hello.optString("deviceId")
                        if (AccessPolicy.id(phoneId)) store.put("phone-id:$key", phoneId)
                        agent = t.agent
                        localVoice = true
                    } else {
                        val t =
                            OwnerTransport(events) {
                                viewModelScope.launch {
                                    if (generation == current && connected) {
                                        disconnect(allowResume = true)
                                        status =
                                            "Connection lost. Return to the app or tap Connect to retry; nothing was replayed."
                                    }
                                }
                            }
                        transport = t
                        val h = t.connect(gateway, store.identity(gateway), secret, requestAdmin)
                        if (current != generation) return@launch
                        adminPermissionGranted = h.optJSONObject("auth")?.strings("scopes")?.contains("operator.admin") == true
                        val grant = h.optJSONObject("auth")?.optString("deviceToken").orEmpty()
                        store.put(key, grant.ifBlank { secret })
                        val methods = h.optJSONObject("features")?.strings("methods").orEmpty()
                        progressSubscriptionAvailable = "sessions.messages.subscribe" in methods
                        historyCapabilities = HistoryCapabilities.owner(methods)
                        supportsAccessManagement = methods.containsAll(AccessPolicy.methods)
                        historyPreviewAvailable = methods.contains("sessions.preview")
                        historyAutoLabelAvailable = methods.contains("sessions.patch")
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
                    if (!restricted) agent = store.get("agent:${credentialKey()}")
                        ?.takeIf { saved -> agents.any { it.first == saved } }
                        ?: agents.firstOrNull()?.first ?: error("No agents")
                    speechSource = if (restricted) "local" else
                        store.get("speech-source:${credentialKey()}")
                            ?: if (localVoice) "local" else "gateway"
                    connected = true
                    speechConsent = store.get("speech-consent:${gateway.trim()}") == "true"
                    status = "Connected. Tap Record or type a message."
                    restoreConversation()
                    subscribeProgress()
                    store.put("resume:${credentialKey()}", "true")
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
                        connectionFailed(e)
                    }
                } finally {
                    if (current == generation) busy = false
                }
            }
    }

    internal fun connectionFailed(error: Exception) {
        transport?.close()
        transport = null
        connected = false
        pairingApproval = error as? GatewayPairingRequired
        status = if (error is GatewayFailure) error.message ?: "Connection failed"
            else "Unable to connect. Check URL, token and gateway support."
    }

    val enrolledPhoneId: String get() = if (restricted) store.get("phone-id:${credentialKey()}").orEmpty() else ""

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
                    currentCoroutineContext().ensureActive()
                    if (current != generation) return@launchTask
                    val secret = d.getString("deviceSecret")
                    val phoneId = d.getString("id")
                    require(AccessPolicy.id(phoneId) && secret.matches(Regex("[A-Za-z0-9_-]{43}")))
                    store.put("phone-id:guest:${gateway.trim()}", phoneId)
                    store.remove("history:guest:${gateway.trim()}")
                    store.remove("agent:guest:${gateway.trim()}")
                    store.remove("resume:guest:${gateway.trim()}")
                    store.put("guest:${gateway.trim()}", secret)
                    store.put("lastGateway", gateway.trim())
                    store.put("lastMode", "restricted")
                    token = ""
                    invitation = ""
                    status =
                        "Enrollment sent. Phone ID ${phoneId.takeLast(8)}. Ask the owner to approve, then Connect."
                } finally {
                    t.close()
                }
            } finally {
                if (current == generation) busy = false
            }
        }
    }

    fun disconnect(allowResume: Boolean = false) {
        pairingApproval = null
        clearAccess()
        progressSubscriptionAvailable = false
        subscriptionGeneration++
        subscriptionJob?.cancel()
        subscriptionJob = null
        subscriptionTarget = null
        subscribedSession = null
        historyCapabilities = HistoryCapabilities()
        historyPreviewAvailable = false
        historyAutoLabelAvailable = false
        scannedSetup = null
        endHandsFree()
        if (!allowResume) {
            automaticConnectionSuppressed = true
            draft = ""
        }
        generation++
        connectionEpoch++
        operations.toList().forEach { it.cancel() }
        operations.clear()
        scopeJob?.cancel()
        turnJob?.cancel()
        activeRun = null
        clearProgress()
        timing = TurnTiming()
        speechProvider = ""
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
        conversationsHaveMore = false
        historyCursor = null
        historyOffset = 0
        resetMessagePage()
        messages = emptyList()
        agents = emptyList()
        status = "Disconnected. Nothing will be replayed."
    }

    fun background() {
        val approval = pairingApproval
        endHandsFree()
        disconnect(allowResume = true)
        pairingApproval = approval
        if (approval != null) status = approval.message.orEmpty()
    }

    fun foreground() {
        if (canReconnect()) connect()
    }

    internal fun canReconnect(): Boolean {
        if (pairingApproval != null || !automaticConnection || automaticConnectionSuppressed || scannedSetup != null || connected || busy ||
            token.isNotBlank() || invitation.isNotBlank()) return false
        val endpoint = runCatching { Protocol.endpoint(gateway, restricted).toString() }.getOrNull() ?: return false
        return endpoint == gateway && store.get("resume:${credentialKey()}") == "true" &&
            !store.get(credentialKey()).isNullOrBlank()
    }

    private fun rememberConversation() {
        val key = "history:${credentialKey()}"
        val saved = runCatching { JSONObject(store.get(key) ?: "{}") }.getOrDefault(JSONObject())
        saved.put(agent, sessionId)
        store.put(key, saved.toString())
        store.put("agent:${credentialKey()}", agent)
        subscribeProgress()
    }

    private fun resetMessagePage() {
        messagesHaveMore = false
        messageCursor = null
        messageOffset = 0
    }

    private suspend fun readConversation(id: String, more: Boolean = false): List<ChatLine> {
        check(historyCapabilities.read) { "Gateway history reading is unavailable." }
        require(if (restricted) runCatching { UUID.fromString(id) }.isSuccess else Protocol.owns(id, agent))
        val current = generation
        val chosenAgent = agent
        val before = messageCursor
        val offset = if (more) messageOffset else 0
        val params = if (restricted) obj("conversationId" to id, "limit" to 50).apply {
            if (more && before != null) put("before", before)
        } else obj("sessionKey" to id, "agentId" to agent, "limit" to 50, "offset" to offset, "maxBytes" to 128000)
        val result = transport!!.request(if (restricted) "voxkin.history.messages" else "chat.history", params)
        currentCoroutineContext().ensureActive()
        check(current == generation && chosenAgent == agent)
        require(result.optJSONArray("messages") != null) { "Missing history messages" }
        val rows = result.rows("messages")
        val nextCursor = result.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" }
        val nextOffset = result.optInt("nextOffset", offset + rows.size)
        messageCursor = nextCursor
        messageOffset = nextOffset
        messagesHaveMore = if (restricted) nextCursor != null && (!more || nextCursor != before)
            else result.optBoolean("hasMore", false) && nextOffset > offset
        return rows.mapNotNull {
            val text = Protocol.text(it)
            if (text.isBlank()) null else ChatLine(it.optString("role"), text, it.optString("id"))
        }
    }

    fun loadOlderMessages() {
        if (!messagesHaveMore || !conversationReady || messages.size >= 500) return
        loadConversationPage(true)
    }

    fun refreshConversation() {
        if (!conversationReady) return
        loadConversationPage(false)
    }

    private fun loadConversationPage(more: Boolean) {
        if (!historyCapabilities.read) return
        launchHistory {
            val id = selectedConversationId
            val loaded = readConversation(id, more)
            if (id != selectedConversationId) return@launchHistory
            val currentIds = messages.mapNotNull { it.id.takeIf(String::isNotBlank) }.toSet()
            val combined = if (more) loaded.filter { it.id.isBlank() || it.id !in currentIds } + messages else loaded
            val seen = mutableSetOf<String>()
            messages = combined.filter { it.id.isBlank() || seen.add(it.id) }.takeLast(500)
            if (messages.size >= 500) messagesHaveMore = false
            status = if (more) "Older messages loaded. Nothing was played." else "Conversation refreshed. Nothing was played."
        }
    }

    internal suspend fun restoreConversation() {
        val current = generation
        val selectedAgent = agent
        conversationReady = false
        resetMessagePage()
        messages = emptyList()
        if (!historyCapabilities.read) {
            if (restricted) {
                status = "This gateway does not support restricted conversation history. Update its Voxkin plugin before sending."
            } else {
                sessionId = UUID.randomUUID().toString()
                draft = ""
                conversationReady = true
                status = "Connected. Saved history is unavailable; a fresh conversation is ready."
            }
            return
        }
        try {
            val saved = JSONObject(store.get("history:${credentialKey()}") ?: "{}").optString(agent)
            if (saved.isNotEmpty()) {
                require(runCatching { UUID.fromString(saved) }.isSuccess)
                val loaded = readConversation(if (restricted) saved else "agent:$agent:voxkin:$saved")
                currentCoroutineContext().ensureActive()
                if (generation != current || agent != selectedAgent) return
                sessionId = saved
                messages = loaded
                status = "Conversation restored. Nothing was sent or played."
            } else {
                val created = if (restricted) {
                    val r = transport!!.request("voxkin.history.create")
                    r.optJSONObject("conversation")?.getString("id") ?: r.getString("id")
                } else UUID.randomUUID().toString()
                require(runCatching { UUID.fromString(created) }.isSuccess)
                currentCoroutineContext().ensureActive()
                if (generation != current || agent != selectedAgent) return
                sessionId = created
                status = "Connected. New conversation ready."
            }
            if (draft.isNotBlank() && draftContext != conversationContext()) draft = ""
            conversationReady = true
            rememberConversation()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (generation == current && agent == selectedAgent)
                status = "Could not restore the conversation. Open History or choose New conversation before sending. Nothing was replayed."
        }
    }

    fun forget() {
        disconnect()
        store.remove("phone-id:${credentialKey()}")
        store.remove("history:${credentialKey()}")
        store.remove("agent:${credentialKey()}")
        store.remove("resume:${credentialKey()}")
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
        launchHistory {
            agent = id
            conversations = emptyList()
            conversationsHaveMore = false
            historyCursor = null
            historyOffset = 0
            draft = ""
            restoreConversation()
            subscribeProgress()
        }
    }

    private fun clearProgress() {
        stopFeedback()
        streamingText = ""
        progressNotices.clear()
        progressText = ""
    }

    private fun subscribeProgress() {
        if (!connected || restricted || !conversationReady || !progressSubscriptionAvailable) return
        val t = transport ?: return
        val target = session
        if (subscribedSession == target || (subscriptionTarget == target && subscriptionJob?.isActive == true)) return
        subscriptionJob?.cancel()
        subscribedSession = null
        val epoch = ++subscriptionGeneration
        val connection = generation
        val selectedAgent = agent
        subscriptionTarget = target
        subscriptionJob = viewModelScope.launch {
            try {
                t.request("sessions.messages.subscribe", obj("key" to target, "agentId" to selectedAgent, "subscriptionId" to "voxkin"))
                if (epoch == subscriptionGeneration && connection == generation && transport === t && connected && session == target)
                    subscribedSession = target
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Optional: retry on later Send or context change. */ }
            finally { if (epoch == subscriptionGeneration) subscriptionTarget = null }
        }
    }

    fun send() {
        if (!connected || busy || recording || settling || draft.isBlank()) return
        if (!conversationReady) {
            status = "Open History or choose New conversation before sending."
            return
        }
        val text = draft.trim()
        if (text.length > 4000) {
            status = "Keep messages under 4,000 characters."
            return
        }
        val t = transport ?: return
        subscribeProgress()
        val spoken = spokenDraft.consume(draft, conversationContext())
        val feedbackEligible = spoken && speechConsent && conversationalFeedback
        val requestText = replyLength.requestText(text, language, feedbackEligible && !restricted && progressSubscriptionAvailable)
        stopAudio(endHandsFree = false)
        val current = generation
        val run = UUID.randomUUID().toString()
        activeRun = run
        timing = TurnTiming().sent(android.os.SystemClock.elapsedRealtime())
        speechProvider = ""
        clearProgress()
        beginFeedback(run, t, feedbackEligible)
        busy = true
        resetMessagePage() // A new turn shifts offset-based history; refresh before loading older pages.
        status = "Waiting for reply…"
        messages = messages + ChatLine("user", text)
        draft = ""
        turnJob =
            viewModelScope.launch {
                try {
                    withTimeout(120000) {
                        if (restricted && sessionId.isEmpty()) {
                            val created = t.request("voxkin.history.create")
                            currentCoroutineContext().ensureActive()
                            if (current != generation || activeRun != run) return@withTimeout
                            sessionId =
                                created.optJSONObject("conversation")?.getString("id")
                                    ?: created.getString("id")
                            // Bind the newly created owned conversation, keeping Send-time voice settings.
                            feedbackTurn?.takeIf { it.run == run && it.generation == current }?.session = session
                        }
                        rememberConversation()
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
                        feedbackTurn?.takeIf { it.run == run && feedbackValid(it) }?.let {
                            if (feedback.accept(it.token, result.opt("runId") as? String)) it.accepted.complete(Unit)
                        }
                        if (result.optString("runId", run) != run)
                            throw GatewayFailure("Gateway returned an unexpected request ID. Outcome unknown; nothing was replayed.")
                        while (current == generation && activeRun != null) delay(200)
                    }
                } catch (e: CancellationException) {
                    if (current == generation && activeRun != null) {
                        activeRun = null
                        clearProgress()
                        busy = false
                        handsFree.stop()
                        status = "Turn stopped or timed out; nothing was replayed."
                    }
                } catch (e: Exception) {
                    if (current == generation) {
                        activeRun = null
                        clearProgress()
                        busy = false
                        handsFree.stop()
                        status =
                            if (e is GatewayFailure) e.message ?: "Request failed"
                            else "Reply failed; outcome may be unknown. Nothing was replayed."
                    }
                }
            }
    }

    private fun event(name: String, p: JSONObject) {
        if (name == "session.message") {
            if (connected && !restricted && progressSubscriptionAvailable && progressNotices.accept(p, session, activeRun)) {
                progressText = progressNotices.display("")
                val text = p.optJSONObject("message")?.let { ProgressText.parse(it) }
                feedbackTurn?.takeIf { feedbackValid(it) }?.let {
                    if (text != null && feedback.offer(it.token, text)) it.notice.complete(Unit)
                }
                timing = timing.update(android.os.SystemClock.elapsedRealtime())
                status = "Your agent is working…"
            }
            return
        }
        if (
            name != "chat" ||
                p.optString("runId") != activeRun ||
                p.optString("sessionKey") != session
        )
            return
        when (p.optString("state")) {
            "delta" -> {
                if (p.optString("channel") !in listOf("", "final", "commentary") ||
                    p.optString("phase") !in listOf("", "final", "commentary")) return
                val message = p.optJSONObject("message")
                // Snapshots must be displayable assistant text, never reasoning or tools.
                if (message != null) {
                    if (message.optString("phase") !in listOf("", "final", "commentary") ||
                        message.optString("channel") !in listOf("", "final", "commentary")) return
                    val text = Protocol.finalText(message)
                    if (text.isBlank()) return
                    streamingText = text.take(16000)
                } else if (p.opt("deltaText") is String) {
                    val delta = p.getString("deltaText")
                    streamingText = (if (p.optBoolean("replace")) delta else streamingText + delta).take(16000)
                } else return
                if (streamingText.isNotBlank()) timing = timing.update(android.os.SystemClock.elapsedRealtime())
                status = "Your agent is replying…"
            }
            "status" -> status = when (p.optString("phase")) {
                "waiting_for_state" -> "Waiting for the gateway…"
                "starting_model" -> "Your agent is thinking…"
                "preparing_context", "memory_flushing" -> "Getting ready…"
                else -> "Your agent is working…"
            }
            "final" -> {
                timing = timing.final(android.os.SystemClock.elapsedRealtime())
                activeRun = null
                clearProgress()
                busy = false
                val text = p.optJSONObject("message")?.let { Protocol.finalText(it) }.orEmpty()
                if (text.isNotBlank()) {
                    messages = messages + ChatLine("assistant", text)
                    status = "Reply ready."
                    if (speechConsent) speak(text) else handsFree.stop()
                } else {
                    handsFree.stop()
                    status = "Reply contained no displayable text."
                }
            }
            "error",
            "aborted" -> {
                activeRun = null
                clearProgress()
                busy = false
                handsFree.stop()
                status = "Turn ended without a reply. Nothing was replayed."
            }
        }
    }

    fun cancelTurn() {
        val run = activeRun
        val t = transport
        activeRun = null
        clearProgress()
        turnJob?.cancel()
        if (run != null) busy = false
        stopAudio()
        status =
            if (busy) "Audio stopped. Gateway operation still in progress."
            else "Stopped. Nothing was replayed."
        if (run != null && t != null)
            launchTask { t.request("chat.abort", obj("sessionKey" to session, "runId" to run)) }
    }

    private fun feedbackFocusLost() { stopAudio() }

    private fun stopFeedback() {
        feedback.invalidate()
        feedbackTurn = null
        feedbackJob?.cancel()
        feedbackJob = null
        feedbackAudio.stop()
    }

    private fun feedbackValid(turn: FeedbackTurn): Boolean = feedback.valid(turn.token) &&
        feedbackTurn === turn && generation == turn.generation && transport === turn.transport &&
        connected && activeRun == turn.run && session == turn.session && speechConsent &&
        conversationalFeedback && !recording && !settling

    private fun beginFeedback(run: String, t: Transport, eligible: Boolean) {
        val token = feedback.begin(run, eligible)
        if (!eligible) return
        val selectedLanguage = language.substringBefore('-')
        val turn = FeedbackTurn(token, run, session, generation, t, speechSource, voice, selectedLanguage,
            localVoice && voices.any { it.id == voice && it.language == selectedLanguage },
            !restricted && gatewaySpeechAvailable)
        feedbackTurn = turn
        feedbackJob = viewModelScope.launch {
            try {
                turn.accepted.await()
                if (!feedbackValid(turn) || !feedback.claimReceipt(token)) return@launch
                try { feedbackAudio.play(ReceiptTone.wav()) { feedbackValid(turn) } }
                catch (_: Exception) { currentCoroutineContext().ensureActive() }
                if (!feedbackValid(turn)) return@launch
                feedback.receiptSettled(token)
                turn.notice.await()
                val notice = feedback.claimProgress(token) ?: return@launch
                delay(800)
                if (!feedbackValid(turn)) return@launch
                val useLocal = turn.source == "local"
                if (if (useLocal) !turn.localAvailable else !turn.gatewayAvailable) return@launch
                val cleaned = SpeechText.prepare(notice)
                if (cleaned.isBlank()) return@launch
                val response = turn.transport.request(if (useLocal) "voxkin.speak" else "tts.speak",
                    if (useLocal) obj("text" to cleaned, "voice" to turn.voice, "language" to turn.language)
                    else obj("text" to cleaned))
                currentCoroutineContext().ensureActive()
                if (!feedbackValid(turn)) return@launch
                if (useLocal) require(response.optString("provider") == "voxkin-local" &&
                    response.optString("voice") == turn.voice && response.optString("language") == turn.language)
                require(response.optString("provider").isNotBlank())
                require(response.optString("mimeType") in listOf("audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/mp4", "audio/aac"))
                val encoded = response.optString("audioBase64")
                require(encoded.length <= 8_000_000)
                val bytes = Base64.getDecoder().decode(encoded)
                require(bytes.size in 1..6_000_000)
                feedbackAudio.play(bytes) { feedbackValid(turn) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Optional feedback never changes the main turn. */ }
        }
    }

    private fun speak(text: String, answer: Boolean = true) {
        val t = transport ?: run { handsFree.stop(); return }
        stopAudio(endHandsFree = false)
        val current = generation
        val audioEpoch = audioGeneration
        val useLocal = speechSource == "local"
        val selectedVoice = voice
        val selectedLanguage = language.substringBefore('-')
        speaking = true
        speechJob = launchTask {
            var completed = false
            try {
                if (useLocal)
                    require(localVoice && voices.any { it.id == selectedVoice && it.language == selectedLanguage })
                else require(!restricted && gatewaySpeechAvailable) { "Gateway speech source is unavailable." }
                SpeechPlayback.play(
                    SpeechPlan.fastChunks(SpeechText.prepare(text)),
                    synthesize = { chunk ->
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
                            throw CancellationException("Speech context changed")
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
                        if (answer) timing = timing.received(android.os.SystemClock.elapsedRealtime())
                        ReplyAudio(bytes, response.optString("provider").take(120))
                    },
                    playback = { clip ->
                        currentCoroutineContext().ensureActive()
                        if (current != generation || audioEpoch != audioGeneration || !speechConsent || recording || settling)
                            throw CancellationException("Speech context changed")
                        playClip(clip.bytes, current, audioEpoch, clip.provider, answer)
                    },
                )
                if (current == generation && audioEpoch == audioGeneration) {
                    status = "Reply ready."
                    completed = true
                }
            } finally {
                if (audioEpoch == audioGeneration) {
                    speaking = false
                    if (completed) handsFree.replyFinished() else handsFree.stop()
                }
            }
        }
    }

    private data class ReplyAudio(val bytes: ByteArray, val provider: String)

    private suspend fun playClip(bytes: ByteArray, current: Int, audioEpoch: Int, provider: String, answer: Boolean) {
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
                        if (answer) {
                            timing = timing.audio(android.os.SystemClock.elapsedRealtime())
                            speechProvider = provider
                        }
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

    fun stopAudio(endHandsFree: Boolean = true) {
        stopFeedback()
        spokenDraft.clear()
        captureTrusted = false
        if (endHandsFree) this.endHandsFree()
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

    private fun prepareCaptureProvenance() {
        val trustedBase = draft.isBlank() || spokenDraft.matches(draft, conversationContext())
        stopAudio(endHandsFree = false)
        captureContext = conversationContext()
        captureRevision = draftRevision
        captureTrusted = trustedBase
    }

    fun startRecording() {
        if (!connected || busy || recording || settling) return
        val context = getApplication<Application>()
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            handsFree.stop()
            status =
                "On-device dictation is unavailable. Install your device speech model or type; cloud fallback is disabled."
            return
        }
        prepareCaptureProvenance()
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
                                    status = if (conversationMode)
                                        "Hands-free listening. Pause to send automatically. Stop ends conversation."
                                    else "Listening — speak now. Tap Stop when finished."
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
                                    handsFree.finishing()
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
                                    if (!hypothesis.isNullOrBlank() && hypothesis != partial) {
                                        partial = hypothesis
                                        handsFree.wordsChanged()
                                    }
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
            spokenDraft.clear()
            captureTrusted = false
            handsFree.stop()
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
            handsFree.finishing()
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
        val autoSend = conversationMode && !text.isNullOrBlank()
        handsFree.finishing()
        if (!autoSend) handsFree.stop()
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
            val trusted = !text.isNullOrBlank() && captureTrusted && captureRevision == draftRevision && captureContext == conversationContext()
            draft = listOf(draft, captured).filter { it.isNotBlank() }.joinToString(" ")
            if (trusted) spokenDraft.markFinal(draft, conversationContext())
            status = if (recovered)
                "Final transcription unavailable. Recovered words may be incomplete — review or edit, then tap Send. Nothing was sent."
            else "Review or edit your words, then tap Send."
        } else status = "No speech recognized. Tap Record to try again or type."
        if (autoSend) {
            if (connected && conversationReady && !busy && draft.length <= 4000 && transport != null) send()
            else {
                handsFree.stop()
                status = "Hands-free paused. Review your draft (maximum 4,000 characters); nothing was sent."
            }
        }
        return captured.isNotBlank()
    }

    fun listHistory(more: Boolean = false) {
        if (!historyCapabilities.list) return
        if (!connected || busy || recording || settling || (more && (!conversationsHaveMore || conversations.size >= 500))) return
        launchHistory {
            val current = generation
            val chosenAgent = agent
            val before = historyCursor
            val offset = if (more) historyOffset else 0
            val r =
                transport!!.request(
                    if (restricted) "voxkin.history.list" else "sessions.list",
                    if (restricted) obj("limit" to 20).apply { if (more && before != null) put("before", before) }
                    else
                        obj(
                            "agentId" to agent,
                            "search" to "agent:$agent:voxkin:",
                            "limit" to 50,
                            "offset" to offset,
                            "includeDerivedTitles" to true,
                            "archived" to false,
                            "includeGlobal" to false,
                            "includeUnknown" to false,
                            "excludeSubagents" to true,
                            "excludeCron" to true,
                            "excludeSystem" to true,
                        ),
                )
            currentCoroutineContext().ensureActive()
            if (current != generation || agent != chosenAgent) return@launchHistory
            val rows = r.rows(if (restricted) "conversations" else "sessions")
            var loaded = rows.mapNotNull { row ->
                    val id = row.optString(if (restricted) "id" else "key")
                    if (
                        if (restricted) runCatching { UUID.fromString(id) }.isFailure
                        else !Protocol.owns(id, agent)
                    )
                        null
                    else
                        Conversation(
                            id,
                            listOf("label", "customTitle", "autoLabel", "title", "derivedTitle", "displayName")
                                .map { row.optString(it) }
                                .firstOrNull { it.isNotBlank() }
                                ?.let { HistoryMetadata.clip(HistoryMetadata.clean(it), 80).takeIf { title -> title.isNotBlank() } }
                                ?.takeUnless { it.startsWith("[Voxkin reply preference") }
                                ?: "Conversation",
                            row.optString("sessionId"),
                            Protocol.text(obj("role" to "user", "text" to row.optString("preview")))
                                .takeUnless { it.startsWith("[Voxkin reply preference") }.orEmpty().take(160),
                            row.optLong("updatedAt", 0),
                        )
                }
            if (!restricted && historyPreviewAvailable) {
                val t = transport ?: return@launchHistory
                loaded = HistoryMetadata.enrich(loaded, rows, chosenAgent, historyAutoLabelAvailable, canRead = historyCapabilities.read,
                    current = { generation == current && connected && agent == chosenAgent && transport === t },
                    request = { method, params -> t.request(method, params) })
            }
            currentCoroutineContext().ensureActive()
            if (current != generation || agent != chosenAgent) return@launchHistory
            val seen = mutableSetOf<String>()
            conversations = (if (more) conversations + loaded else loaded).filter { seen.add(it.id) }.take(500)
            val next = r.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" }
            historyCursor = next
            historyOffset = offset + rows.size
            conversationsHaveMore = conversations.size < 500 && if (restricted)
                next != null && (!more || next != before)
            else rows.size == 50
        }
    }

    fun newConversation() {
        if (!historyCapabilities.create) return
        if (!connected || busy || recording || settling) return
        launchHistory {
            stopAudio()
            sessionId =
                if (restricted) {
                    val r = transport!!.request("voxkin.history.create")
                    r.optJSONObject("conversation")?.getString("id") ?: r.getString("id")
                } else UUID.randomUUID().toString()
            conversationReady = true
            rememberConversation()
            resetMessagePage()
            messages = emptyList()
            draft = ""
            page = 0
            status = "New conversation ready."
        }
    }

    fun openHistory(c: Conversation) {
        if (!historyCapabilities.read) return
        if (busy || recording || settling) return
        launchHistory {
            stopAudio()
            val current = generation
            val selectedAgent = agent
            val loaded = readConversation(c.id)
            currentCoroutineContext().ensureActive()
            if (current != generation || agent != selectedAgent) return@launchHistory
            sessionId = c.id.substringAfterLast(':')
            messages = loaded
            conversationReady = true
            rememberConversation()
            draft = ""
            page = 1
            status = "Conversation loaded (latest page)."
        }
    }

    fun rename(c: Conversation, title: String) {
        if (title.isBlank() || !canRename(c)) return
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
        if (!canDelete(c)) return
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
                resetMessagePage()
                draft = ""
                sessionId = if (restricted) "" else UUID.randomUUID().toString()
                val key = "history:${credentialKey()}"
                val saved = runCatching { JSONObject(store.get(key) ?: "{}") }.getOrDefault(JSONObject())
                saved.remove(agent)
                store.put(key, saved.toString())
                conversationReady = true
                clearProgress()
                subscribeProgress()
            }
            conversations = conversations.filter { it.id != c.id }
            // Deletion invalidates both owner offsets and restricted before cursors.
            historyCursor = null
            historyOffset = 0
            conversationsHaveMore = false
            status = "Conversation deleted. Refresh History to reload the list."
        }
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}
