package studio.voxkin.android

import android.Manifest
import android.content.Intent
import android.os.Bundle
import com.journeyapps.barcodescanner.ScanContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    companion object {
        internal var modelFactory: androidx.lifecycle.ViewModelProvider.Factory? = null
    }
    private val model: VoiceModel by viewModels {
        modelFactory ?: androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }

    private var setupScanPending = false
    private val setupScanner = registerForActivityResult(ScanContract()) { result ->
        if (setupScanPending) {
            setupScanPending = false
            model.reviewSetupScan(result.contents)
        }
    }

    private fun scanSetup() {
        if (!model.prepareSetupScan()) return
        setupScanPending = true
        try {
            setupScanner.launch(SetupScannerActivity.options())
        } catch (_: Exception) {
            setupScanPending = false
            model.reviewSetupScan(null)
            model.status = "Camera scanner unavailable. Paste a setup link instead."
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("setupScanPending", setupScanPending)
        outState.putBoolean("setupFlowActive", model.setupFlowActive)
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScanPending = savedInstanceState?.getBoolean("setupScanPending") ?: false
        if (setupScanPending || savedInstanceState?.getBoolean("setupFlowActive") == true) model.restoreSetupFlow()
        if (savedInstanceState == null) intent.dataString?.let { model.stage(it) }
        setContent {
            MaterialTheme(
                colorScheme =
                    darkColorScheme(
                        primary = VoxkinBrand.cyan,
                        secondary = VoxkinBrand.magenta,
                        tertiary = VoxkinBrand.purple,
                        background = VoxkinBrand.navy,
                        surface = VoxkinBrand.surface,
                        onSurface = Color.White,
                        onBackground = Color.White,
                    )
            ) {
                Voxkin(model, onScanSetup = { scanSetup() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { setupScanPending = false; model.stage(it) }
    }

    override fun onStart() {
        super.onStart()
        if (!setupScanPending) model.foreground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) model.background()
    }
}

@Composable
private fun Voxkin(m: VoiceModel, onScanSetup: () -> Unit) {
    val activity = LocalActivity.current
    val invitationVisible = m.shareInvitation.isNotEmpty()
    DisposableEffect(activity, invitationVisible) {
        val window = activity?.window
        val wasSecure = window != null && window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0
        if (invitationVisible) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (invitationVisible && !wasSecure) window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val focus = LocalFocusManager.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = VoxkinBrand.navy,
        topBar = {
            Box(
                Modifier.fillMaxWidth()
                    .statusBarsPadding()
                    .height(52.dp)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (m.page == 0) VoxkinWordmark()
                else
                    Text(
                        if (m.page == 1) "History" else "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                if (m.page == 1)
                    IconButton(
                        onClick = { m.listHistory() },
                        enabled = m.historyCapabilities.list && m.connected && !m.busy && !m.recording && !m.settling,
                        modifier =
                            Modifier.align(Alignment.CenterEnd).semantics {
                                contentDescription = "Refresh history"
                            },
                    ) {
                        VoxkinIcon(VoxkinSymbol.Refresh)
                    }
            }
        },
        bottomBar = {
            if (!keyboardVisible)
                Box(
                    Modifier.fillMaxWidth()
                        .background(VoxkinBrand.navy)
                        .navigationBarsPadding()
                        .padding(horizontal = 44.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.widthIn(max = 320.dp)
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(36.dp))
                            .background(
                                Brush.verticalGradient(listOf(Color(0xff2b3242), Color(0xff222938)))
                            )
                            .border(1.dp, Color.White.copy(alpha = .15f), RoundedCornerShape(36.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        listOf(
                                "Talk" to VoxkinSymbol.Wave,
                                "History" to VoxkinSymbol.History,
                                "Settings" to VoxkinSymbol.Settings,
                            )
                            .forEachIndexed { index, (label, symbol) ->
                                val selected = m.page == index
                                Column(
                                    Modifier.weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(30.dp))
                                        .background(
                                            if (selected) Color(0xff0b1426) else Color.Transparent
                                        )
                                        .clickable {
                                            focus.clearFocus()
                                            m.page = index
                                            if (index == 1) m.listHistory()
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    VoxkinIcon(
                                        symbol,
                                        Modifier.size(25.dp),
                                        if (selected) VoxkinBrand.cyan else Color.White,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        fontWeight =
                                            if (selected) FontWeight.SemiBold
                                            else FontWeight.Normal,
                                        color = if (selected) VoxkinBrand.cyan else Color.White,
                                    )
                                }
                            }
                    }
                }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .background(VoxkinBrand.background)
        ) {
            when (m.page) {
                0 -> TalkPage(m)
                1 -> HistoryPage(m)
                else -> SettingsPage(m, onScanSetup)
            }
        }
    }
}

@Composable
private fun TalkPage(m: VoiceModel) {
    var typing by remember { mutableStateOf(false) }
    var keyboardRequest by remember { mutableIntStateOf(0) }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    val latestUserIndex = m.messages.indexOfLast { it.role == "user" }
    val latestUser = m.messages.getOrNull(latestUserIndex)
    val latestAgent = m.messages.drop(latestUserIndex + 1).lastOrNull { it.role == "assistant" }
    LaunchedEffect(m.messages.size) {
        if (m.messages.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue)
    }
    LaunchedEffect(m.draft, m.recording, m.settling) {
        if (m.draft.isNotBlank() && !m.recording && !m.settling) typing = true
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(6.dp)
                        .then(
                            if (m.connected) Modifier.background(VoxkinBrand.cyan, CircleShape)
                            else Modifier.border(1.dp, VoxkinBrand.secondary, CircleShape)
                        )
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (m.connected) "CONNECTED" else if (m.busy) "CONNECTING" else "OFFLINE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    color = if (m.connected) VoxkinBrand.cyan else VoxkinBrand.secondary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    m.agent.replaceFirstChar { it.uppercase() },
                    fontSize = 12.sp,
                    color = VoxkinBrand.secondary,
                )
            }
            if (latestUser == null && latestAgent == null && !m.requestPending && !keyboardVisible) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    VoxkinMark(Modifier.width(184.dp).height(116.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Talk to your agent.",
                            fontSize = 30.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            if (m.gateway.isBlank() && !m.connected)
                                "Your voice. Your agent.\nAdd your gateway in Settings to get started."
                            else
                                "A little less typing. A little more talking.\nTap to record. Stop to review. Then send.",
                            fontSize = 16.sp,
                            lineHeight = 23.sp,
                            color = VoxkinBrand.secondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            latestUser?.let { MessageCard(it, false) }
            latestAgent?.let { MessageCard(it, true) }
            if (m.requestPending && m.pendingText.isNotBlank()) {
                Text("Reply in progress", color = VoxkinBrand.secondary, fontSize = 12.sp)
                MessageCard(ChatLine("assistant", m.pendingText), true)
            }
        }
        TalkControls(
            m,
            typing,
            keyboardRequest,
            onTyping = {
                if (keyboardVisible) {
                    typing = false
                    focus.clearFocus()
                    keyboard?.hide()
                } else {
                    typing = true
                    keyboardRequest++
                }
            },
        )
    }
}

@Composable
private fun MessageCard(line: ChatLine, agent: Boolean) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(if (agent) 24.dp else 22.dp))
            .background(
                if (agent) VoxkinBrand.purple.copy(alpha = .12f) else Color.White.copy(alpha = .05f)
            )
            .then(
                if (agent)
                    Modifier.border(
                        1.dp,
                        VoxkinBrand.purple.copy(alpha = .25f),
                        RoundedCornerShape(24.dp),
                    )
                else Modifier
            )
            .padding(if (agent) 20.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(if (agent) 12.dp else 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (agent) VoxkinIcon(VoxkinSymbol.Wave, Modifier.size(14.dp), VoxkinBrand.magenta)
            Text(
                if (agent) "AGENT" else "YOU",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = if (agent) VoxkinBrand.magenta else VoxkinBrand.cyan,
            )
        }
        SelectionContainer { Text(line.text, fontSize = 16.sp, lineHeight = 24.sp) }
    }
}

@Composable
private fun TalkControls(
    m: VoiceModel,
    typing: Boolean,
    keyboardRequest: Int,
    onTyping: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val draftFocus = remember { FocusRequester() }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val compact = keyboardVisible || LocalConfiguration.current.screenHeightDp < 600
    val controlSpacing = if (compact) 8.dp else 12.dp
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) m.startRecording()
            else m.status = "Allow microphone access to dictate, or type your message."
        }
    var showHandsFree by remember { mutableStateOf(false) }
    var handsFreeToken by remember { mutableStateOf<String?>(null) }
    val handsFreePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val token = handsFreeToken
        handsFreeToken = null
        if (granted && token != null) m.startHandsFree(token)
        else if (!granted) m.status = "Allow microphone access to use hands-free conversation."
    }
    if (showHandsFree) AlertDialog(
        onDismissRequest = { showHandsFree = false },
        title = { Text("Start hands-free conversation?") },
        text = { Text("Your words send automatically when you pause. After each spoken reply, the microphone opens again. Stop, leaving Talk, or leaving the app ends hands-free mode. Recognition errors keep recovered words for review instead of sending.") },
        confirmButton = { TextButton(onClick = {
            showHandsFree = false
            handsFreeToken = m.handsFreeStartToken()
            handsFreePermission.launch(Manifest.permission.RECORD_AUDIO)
        }, enabled = m.canStartHandsFree) { Text("Start hands-free") } },
        dismissButton = { TextButton(onClick = { showHandsFree = false }) { Text("Not now") } },
    )
    val capture = m.recording || m.settling
    val hasDraft = m.draft.isNotBlank()
    val working = m.busy || m.speaking
    val orbEnabled = m.conversationMode || (!working && !m.settling && (!m.recording || m.microphoneReady))
    val actionLabel =
        when {
            m.conversationMode && m.speaking -> "Interrupt reply and listen"
            m.conversationMode -> "End hands-free conversation"
            m.recording -> "Stop recording and review"
            m.settling -> "Finishing transcription"
            !m.connected -> "Connect to gateway"
            !m.conversationReady -> "Choose a conversation in History"
            hasDraft -> "Send draft to agent"
            else -> "Talk to agent"
        }
    LaunchedEffect(keyboardRequest) {
        if (keyboardRequest > 0 && typing && !capture) {
            draftFocus.requestFocus()
            keyboard?.show()
        }
    }
    Column(
        Modifier.fillMaxWidth().background(VoxkinBrand.navy),
        verticalArrangement = Arrangement.spacedBy(controlSpacing),
    ) {
        HorizontalDivider(color = VoxkinBrand.purple.copy(alpha = .2f))
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(controlSpacing),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (working) {
                    CircularProgressIndicator(
                        Modifier.size(14.dp),
                        color = VoxkinBrand.cyan,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    m.status,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = if (compact) 1 else 3,
                    color = VoxkinBrand.secondary,
                    textAlign = TextAlign.Center,
                )
            }
            if (capture) {
                Row(
                    Modifier.fillMaxWidth().testTag("recordingIndicator"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (m.microphoneReady) {
                        VoxkinIcon(
                            VoxkinSymbol.Wave,
                            Modifier.size(22.dp).alpha(.55f + .45f * m.level),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (m.partial.isNotBlank()) "Listening · words detected"
                            else "Listening · speak now",
                            fontSize = 12.sp,
                            color = VoxkinBrand.cyan,
                        )
                    } else {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (m.settling) "Finishing transcription…"
                            else "Getting microphone ready…",
                            fontSize = 12.sp,
                            color = VoxkinBrand.secondary,
                        )
                    }
                }
            }
            if (typing || capture || hasDraft) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = .07f)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            value =
                                if (capture)
                                    listOf(m.draft, m.partial)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" ")
                                else m.draft,
                            onValueChange = { m.draft = it.take(4000) },
                            modifier =
                                Modifier.weight(1f).focusRequester(draftFocus).testTag("draft"),
                            placeholder = { Text("Message your agent", fontSize = 15.sp) },
                            maxLines = 4,
                            enabled = m.connected && !working && !capture,
                            colors =
                                TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    disabledTextColor = Color.White.copy(alpha = .7f),
                                ),
                        )
                        if (hasDraft || m.partial.isNotBlank())
                            IconButton(
                                onClick = {
                                    focus.clearFocus()
                                    m.clearDraft()
                                },
                                enabled = !m.busy && !m.speaking,
                                modifier = Modifier.semantics { contentDescription = "Clear draft" },
                            ) {
                                VoxkinIcon(
                                    VoxkinSymbol.Close,
                                    Modifier.size(18.dp),
                                    VoxkinBrand.secondary,
                                )
                            }
                    }
                    if (!capture)
                        IconButton(
                            onClick = {
                                focus.clearFocus()
                                keyboard?.hide()
                                m.send()
                            },
                            enabled = m.connected && m.conversationReady && !working && hasDraft,
                            modifier =
                                Modifier.testTag("send").semantics {
                                    contentDescription = "Send message"
                                },
                        ) {
                            Box(
                                Modifier.size(34.dp)
                                    .background(
                                        if (m.connected && !working && hasDraft) VoxkinBrand.cyan
                                        else VoxkinBrand.secondary.copy(alpha = .3f),
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                VoxkinIcon(
                                    VoxkinSymbol.Arrow,
                                    Modifier.size(24.dp),
                                    VoxkinBrand.navy,
                                )
                            }
                        }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { m.endHandsFree(); onTyping() },
                    modifier =
                        Modifier.size(48.dp).testTag("typeInstead").semantics {
                            contentDescription = "Type instead"
                        },
                    enabled = !capture,
                ) {
                    VoxkinIcon(VoxkinSymbol.Keyboard, Modifier.size(26.dp))
                }
                Spacer(Modifier.width(if (compact) 20.dp else 28.dp))
                Box(
                    Modifier.size(if (compact) 68.dp else 108.dp)
                        .shadow(
                            20.dp,
                            CircleShape,
                            ambientColor = VoxkinBrand.blue.copy(alpha = .28f),
                            spotColor = VoxkinBrand.blue.copy(alpha = .28f),
                        )
                        .alpha(if (orbEnabled) 1f else .5f)
                        .clip(CircleShape)
                        .background(
                            if (m.recording)
                                Brush.linearGradient(listOf(Color(0xffff9500), Color(0xffff2d55)))
                            else VoxkinBrand.gradient
                        )
                        .border(1.dp, Color.White.copy(alpha = .2f), CircleShape)
                        .clickable(enabled = orbEnabled) {
                            focus.clearFocus()
                            keyboard?.hide()
                            when {
                                m.conversationMode && m.speaking -> m.interruptHandsFreeReply()
                                m.conversationMode -> m.endHandsFree()
                                m.recording -> m.stopRecording()
                                !m.connected -> {
                                    if (m.gateway.isBlank()) m.page = 2 else m.connect()
                                }
                                !m.conversationReady -> { m.page = 1; m.listHistory() }
                                hasDraft -> m.send()
                                else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        .testTag("talkButton")
                        .semantics { contentDescription = actionLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    VoxkinIcon(
                        when {
                            m.conversationMode && m.speaking -> VoxkinSymbol.Mic
                            m.conversationMode || m.recording || m.settling -> VoxkinSymbol.Stop
                            !m.connected -> VoxkinSymbol.Bolt
                            hasDraft -> VoxkinSymbol.Arrow
                            else -> VoxkinSymbol.Mic
                        },
                        Modifier.size(if (compact) 28.dp else 38.dp),
                        Color.White,
                    )
                }
                Spacer(Modifier.width(if (compact) 20.dp else 28.dp))
                IconButton(
                    onClick = {
                        focus.clearFocus()
                        if (m.conversationMode) m.endHandsFree() else if (capture) m.clearDraft() else m.stopAudio()
                    },
                    enabled = m.connected,
                    modifier =
                        Modifier.size(48.dp).semantics {
                            contentDescription = if (m.conversationMode) "End hands-free" else if (capture) "Cancel recording" else "Stop audio"
                        },
                ) {
                    VoxkinIcon(
                        if (capture) VoxkinSymbol.Close else VoxkinSymbol.Stop,
                        Modifier.size(26.dp),
                    )
                }
            }
            if (!m.conversationMode && !compact) TextButton(
                onClick = { focus.clearFocus(); keyboard?.hide(); showHandsFree = true },
                enabled = m.canStartHandsFree,
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("startHandsFree"),
            ) { Text("Hands-free conversation", fontSize = 12.sp) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        m.conversationMode -> if (m.speaking) "Hands-free · tap mic to interrupt" else "Hands-free · Stop ends conversation"
                        m.settling -> "Finishing…"
                        m.recording ->
                            if (m.microphoneReady) "Listening · tap Stop to review"
                            else "Opening microphone…"
                        m.connected -> if (hasDraft) "Tap to send your draft" else "Tap to talk"
                        m.gateway.isBlank() -> "Set up your gateway"
                        else -> "Tap to connect"
                    },
                    fontSize = 12.sp,
                    color = VoxkinBrand.secondary,
                    modifier = Modifier.weight(1f),
                    textAlign = if (m.requestPending) TextAlign.Start else TextAlign.Center,
                )
                if (m.requestPending)
                    TextButton(onClick = { m.cancelTurn() }) {
                        Text("Cancel request", fontSize = 12.sp)
                    }
            }
        }
    }
}

@Composable
private fun GroupSection(
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null)
            Text(
                title.uppercase(),
                fontSize = 12.sp,
                letterSpacing = .6.sp,
                color = VoxkinBrand.secondary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = .055f)),
            content = content,
        )
        if (footer != null)
            Text(
                footer,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = VoxkinBrand.secondary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), color = Color.White.copy(alpha = .08f))
}

@Composable
private fun GroupText(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = VoxkinBrand.secondary,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun GroupAction(
    label: String,
    symbol: VoxkinSymbol? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) Color(0xffff6b72) else VoxkinBrand.cyan
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp)
            .alpha(if (enabled) 1f else .4f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (symbol != null) VoxkinIcon(symbol, Modifier.size(20.dp), color)
        Text(label, fontSize = 16.sp, color = color)
    }
}

@Composable
private fun <T> GroupPicker(
    title: String,
    value: T,
    choices: List<Pair<T, String>>,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth()
                .clickable(enabled = enabled) { expanded = true }
                .padding(16.dp)
                .alpha(if (enabled) 1f else .45f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(
                choices.firstOrNull { it.first == value }?.second ?: "Not selected",
                fontSize = 15.sp,
                color = VoxkinBrand.secondary,
            )
            Spacer(Modifier.width(8.dp))
            VoxkinIcon(VoxkinSymbol.Chevron, Modifier.size(14.dp), VoxkinBrand.secondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    },
                    trailingIcon = {
                        if (id == value) VoxkinIcon(VoxkinSymbol.Check, Modifier.size(18.dp))
                    },
                )
            }
        }
    }
}

@Composable
private fun HistoryPage(m: VoiceModel) {
    var rename by remember(m.connectionEpoch, m.connected, m.agent, m.gateway, m.restricted) { mutableStateOf<Conversation?>(null) }
    var deleting by remember(m.connectionEpoch, m.connected, m.agent, m.gateway, m.restricted) { mutableStateOf<Conversation?>(null) }
    var title by remember { mutableStateOf("") }
    val enabled = m.connected && !m.busy && !m.recording && !m.settling
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (!m.connected)
            GroupSection {
                GroupText("Connect to your gateway to view history.")
                GroupDivider()
                GroupAction("Open Settings") { m.page = 2 }
            }
        else
            GroupSection(
                "Conversations · ${m.agent}",
                "Stored on your gateway. Only Voxkin conversations are shown. Opening history never plays audio. Gateway retention policies apply.",
            ) {
                GroupAction("New conversation", VoxkinSymbol.Plus, enabled && m.historyCapabilities.create) { m.newConversation() }
                if (m.conversations.isEmpty() && !m.busy) {
                    GroupDivider()
                    GroupText(if (m.historyCapabilities.list) "No saved conversations yet." else "This gateway does not provide a conversation list.")
                }
                m.conversations.forEach { c ->
                    GroupDivider()
                    var menu by remember(c.id) { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = enabled && m.historyCapabilities.read) { m.openHistory(c) }
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (c.id == m.selectedConversationId)
                                Text("Current conversation", color = VoxkinBrand.cyan, fontSize = 12.sp)
                            Text(c.title.ifEmpty { "Conversation" }, fontSize = 16.sp, maxLines = 2)
                            if (c.preview.isNotBlank())
                                Text(c.preview, color = VoxkinBrand.secondary, fontSize = 13.sp, maxLines = 2)
                            if (c.updatedAt > 0)
                                Text("Updated " + java.text.DateFormat.getDateTimeInstance(
                                    java.text.DateFormat.SHORT, java.text.DateFormat.SHORT
                                ).format(java.util.Date(c.updatedAt)), color = VoxkinBrand.secondary, fontSize = 12.sp)
                        }
                        Box {
                            IconButton(
                                onClick = { menu = true },
                                enabled = m.canRename(c) || m.canDelete(c),
                                modifier =
                                    Modifier.semantics {
                                        contentDescription = "Actions for ${c.title}"
                                    },
                            ) {
                                VoxkinIcon(
                                    VoxkinSymbol.More,
                                    Modifier.size(22.dp),
                                    VoxkinBrand.secondary,
                                )
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    enabled = m.canRename(c),
                                    onClick = {
                                        menu = false
                                        rename = c
                                        title = c.title
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = Color(0xffff6b72)) },
                                    enabled = m.canDelete(c),
                                    onClick = {
                                        menu = false
                                        deleting = c
                                    },
                                )
                            }
                        }
                    }
                }
            }
        if (m.connected && m.conversationsHaveMore)
            GroupAction("Load more conversations", enabled = enabled && m.historyCapabilities.list) { m.listHistory(more = true) }
        if (m.busy)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Loading history…", color = VoxkinBrand.secondary)
            }
        if (m.connected)
            Text(
                "Conversations loaded: ${m.conversations.size}. Up to 500 per view.",
                fontSize = 12.sp,
                color = VoxkinBrand.secondary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        if (m.connected && m.conversationReady) {
            GroupSection(
                "Selected conversation",
                "Messages loaded: ${m.messages.size}. Up to 500 per view; refresh returns to the latest page. Opening history never plays audio.",
            ) {
                GroupAction("Continue in Talk", enabled = enabled) { m.page = 0 }
                GroupDivider()
                GroupAction("Refresh conversation", enabled = enabled && m.historyCapabilities.read) { m.refreshConversation() }
                if (m.messagesHaveMore) {
                    GroupDivider()
                    GroupAction("Load older messages", enabled = enabled && m.historyCapabilities.read) { m.loadOlderMessages() }
                }
            }
            m.messages
                .filter { it.role == "user" || it.role == "assistant" }
                .forEach { line -> MessageCard(line, agent = line.role == "assistant") }
        }
        Text(
            m.status,
            fontSize = 12.sp,
            color = VoxkinBrand.secondary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    rename?.let { c ->
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text("Rename conversation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(80) },
                        label = { Text("Conversation name") },
                    )
                    Text("Choose a short name, up to 80 characters. It is saved on your gateway.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        m.rename(c, title)
                        rename = null
                    },
                    enabled = title.isNotBlank() && m.canRename(c),
                ) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete conversation?") },
            text = {
                Text(
                    "Remove this conversation from History? You cannot undo this in Voxkin. The gateway may retain agent transcripts or backups."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        m.delete(c)
                        deleting = null
                    },
                    enabled = m.canDelete(c),
                ) {
                    Text("Delete conversation", color = Color(0xffff6b72))
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AccessPanel(m: VoiceModel) {
    val context = LocalContext.current
    val idle = !m.busy && !m.recording && !m.settling && !m.speaking
    val pending = m.accessConfirmation
    if (pending != null) AlertDialog(
        onDismissRequest = { m.dismissAccessAction() },
        title = { Text(when (pending.action) { "invite" -> "Create phone invitation?"; "approve" -> "Approve phone?"; "revoke" -> "Revoke phone access?"; else -> "Cancel invitation?" }) },
        text = { Text(buildString {
            append("${pending.label} · ${pending.agent}")
            if (pending.id.isNotEmpty()) append("\nID: ${pending.id.takeLast(8)}")
            append(when (pending.action) {
                "invite" -> "\nA single-use invitation will be created on this gateway. Share it privately; enrollment still needs owner approval."
                "approve" -> "\nCompare this ID with the enrolled phone. Approval grants access to the displayed agent."
                "revoke" -> "\nThis removes the phone's gateway access. Its owner will need a new invitation to join again."
                else -> "\nThe invitation will no longer work for enrollment."
            })
        }) },
        confirmButton = { TextButton(onClick = { m.confirmAccessAction() }, enabled = idle) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = { m.dismissAccessAction() }) { Text("Cancel") } },
    )
    GroupSection("Manage phone access", "Compare phone IDs before approving. Access changes are made on the connected gateway.") {
        GroupAction("Refresh phones and invitations", enabled = idle) { m.refreshAccess() }
        if (!m.accessLoaded) GroupText("Refresh to load current access before making changes.")
        GroupPicker("Allowed agent", m.inviteAgent, m.accessAgents.map { it to it }, enabled = idle && m.accessLoaded) { m.inviteAgent = it }
        SetupField(m.inviteLabel, { m.inviteLabel = it.take(80) }, "Phone label", enabled = idle && m.accessLoaded)
        GroupAction("Add phone", enabled = idle && m.accessLoaded && m.inviteAgent in m.accessAgents && AccessPolicy.label(m.inviteLabel.trim())) { m.prepareAccessAction("invite") }
        if (m.shareInvitation.isNotEmpty()) {
            val link = m.shareInvitation
            val bitmap = remember(link) { runCatching {
                com.journeyapps.barcodescanner.BarcodeEncoder().encodeBitmap(link, com.google.zxing.BarcodeFormat.QR_CODE, 640, 640)
            }.getOrNull() }
            if (bitmap != null) Image(bitmap.asImageBitmap(), "Single-use phone invitation QR", Modifier.fillMaxWidth().height(240.dp).padding(16.dp))
            GroupText("Single-use invitation. Expires ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(m.invitationExpiresAt))}. Share privately; approve the matching phone after enrollment.")
            GroupAction("Share invitation", enabled = idle) {
                if (m.shareInvitation == link && System.currentTimeMillis() < m.invitationExpiresAt) {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link) }
                    runCatching { context.startActivity(Intent.createChooser(send, "Share phone invitation privately")) }
                        .onFailure { m.status = "No sharing app available. Use the invitation QR instead." }
                } else m.hideInvitation()
            }
            GroupAction("Hide invitation QR") { m.hideInvitation() }
        }
        if (m.accessLoaded && m.accessDevices.isEmpty()) GroupText("No enrolled phones.")
        m.accessDevices.forEach { phone ->
            GroupDivider()
            GroupText("${phone.label} · ${phone.status}\n${phone.id.takeLast(8)} · ${phone.agent}")
            if (phone.status == "pending") GroupAction("Approve phone", enabled = idle && m.accessLoaded && phone.agent in m.accessAgents) { m.prepareAccessAction("approve", phone.id) }
            if (phone.status != "revoked") GroupAction("Revoke phone", enabled = idle && m.accessLoaded, destructive = true) { m.prepareAccessAction("revoke", phone.id) }
        }
        if (m.accessLoaded && m.accessInvites.isEmpty()) GroupText("No pending invitations.")
        m.accessInvites.forEach { invite ->
            GroupDivider()
            GroupText("${invite.label} · ${invite.agent}\nInvitation ${invite.id.takeLast(8)}")
            GroupAction("Cancel invitation", enabled = idle && m.accessLoaded, destructive = true) { m.prepareAccessAction("cancel", invite.id) }
        }
    }
}

@Composable
private fun SettingsPage(m: VoiceModel, onScanSetup: () -> Unit) {
    val scanned = m.scannedSetup
    if (scanned != null) AlertDialog(
        onDismissRequest = { m.discardSetupScan() },
        title = { Text(if (scanned.restricted) "Review phone invitation" else "Review owner setup") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Gateway: ${Protocol.endpoint(scanned.gateway, scanned.restricted)}")
            Text(if (scanned.restricted) "Restricted phone access. Enrollment still requires owner approval."
                else "Owner access grants broader gateway permissions. Only use your own trusted gateway.")
            Text("The credential is hidden. Use setup only fills the fields; Connect or Enroll remains a separate action.")
        } },
        confirmButton = { TextButton(onClick = { m.useScannedSetup() }) { Text("Use scanned setup") } },
        dismissButton = { TextButton(onClick = { m.discardSetupScan() }) { Text("Discard") } },
    )
    var setup by remember { mutableStateOf("") }
    var confirmForget by remember { mutableStateOf(false) }
    var troubleshootingExpanded by remember { mutableStateOf(false) }
    val idle = !m.busy && !m.recording && !m.settling && !m.speaking
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VoxkinMark(Modifier.width(68.dp).height(44.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VoxkinWordmark()
                Text("Talk to your agent.", fontSize = 14.sp, color = VoxkinBrand.secondary)
            }
        }
        if (!m.connected)
            GroupSection(
                "Set up this phone",
                "Only import a gateway you trust. Setup links contain a credential; keep them private. Staging only fills the fields. Review them, then Connect or Enroll.",
            ) {
                GroupAction("Scan setup QR", enabled = !m.busy, onClick = onScanSetup)
                GroupText("Scan a Voxkin owner setup or phone invitation. Camera decoding stays on this phone; you review before using it.")
                GroupDivider()
                SetupField(
                    setup,
                    { setup = it },
                    "Paste Voxkin setup or invitation",
                    secret = true,
                    enabled = !m.busy,
                )
                GroupDivider()
                GroupAction("Use setup link", enabled = setup.isNotBlank() && !m.busy) {
                    m.stage(setup)
                    setup = ""
                }
            }
        GroupSection(
            if (m.restricted) "Restricted gateway" else "Owner gateway",
            "Tailscale must be connected separately. Microphone and messages always wait for you. Returning reconnects your saved approved gateway and restores the conversation; nothing is sent, recorded, or replayed.",
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Restricted guest access", Modifier.weight(1f), fontSize = 16.sp)
                Switch(
                    checked = m.restricted,
                    onCheckedChange = {
                        m.chooseRequestAdmin(false)
                        m.restricted = it
                        m.token = ""
                        m.invitation = ""
                    },
                    enabled = !m.connected && !m.busy,
                )
            }
            GroupDivider()
            SetupField(
                m.gateway,
                { m.updateGateway(it) },
                "wss://your-gateway",
                enabled = !m.connected && !m.busy,
                url = true,
            )
            GroupDivider()
            SetupField(
                m.token,
                { m.token = it },
                if (m.restricted) "Saved device token (or paste)"
                else "Pairing token or saved grant",
                secret = true,
                enabled = !m.connected && !m.busy,
            )
            if (m.restricted && !m.connected) {
                GroupDivider()
                SetupField(
                    m.invitation,
                    { m.invitation = it },
                    "Invitation secret",
                    secret = true,
                    enabled = !m.busy,
                )
                GroupDivider()
                GroupAction("Enroll this phone", enabled = m.invitation.isNotBlank() && !m.busy) {
                    m.enroll()
                }
                GroupText(
                    "Enrollment sends this invitation to the displayed gateway. Owner approval is required before access activates."
                )
            }
            if (!m.restricted) {
                GroupDivider()
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Request access-management permission", Modifier.weight(1f), fontSize = 15.sp)
                    Switch(checked = m.requestAdmin, onCheckedChange = { m.chooseRequestAdmin(it) }, enabled = !m.connected && !m.busy)
                }
                GroupText("Requires gateway approval for operator.admin. A shared token alone does not grant access. Disconnect before changing this request.")
                if (m.connected) {
                    GroupText(if (m.adminPermissionGranted) "Admin permission: granted for this connection." else "Admin permission: not granted.")
                    GroupText(if (m.supportsAccessManagement) "Phone-management API: available." else "Phone-management API: unavailable. Enable a compatible Voxkin access plugin on the gateway, then reconnect.")
                }
            } else if (m.enrolledPhoneId.isNotEmpty()) {
                GroupText("Phone ID: ${m.enrolledPhoneId.takeLast(8)}. Compare this with the owner before approval.")
            }
            GroupDivider()
            GroupAction(
                if (m.connected) "Disconnect" else if (m.busy) "Cancel connection" else "Connect"
            ) {
                if (m.connected || m.busy) m.disconnect() else m.connect()
            }
            GroupText(m.status)
        }
        if (m.canManageAccess) AccessPanel(m)
        if (m.connected && m.agents.isNotEmpty())
            GroupSection("Agent") {
                GroupPicker("Talk to", m.agent, m.agents, enabled = idle && !m.restricted) {
                    m.chooseAgent(it)
                }
            }
        GroupSection("Voice") {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Allow gateway speech", Modifier.weight(1f), fontSize = 16.sp)
                Switch(
                    checked = m.speechConsent,
                    onCheckedChange = { m.chooseSpeechConsent(it) },
                    enabled = m.connected && idle,
                )
            }
            GroupText(
                "Allows reply audio from your selected speech source on the gateway. Gateway default uses its configured voice and fallback providers; Local voices uses the gateway’s local voice catalog, not Android text-to-speech. Reply text may be sent to configured providers. Enable only if you accept this gateway’s policy."
            )
            GroupDivider()
            GroupPicker(
                "Speech source",
                m.speechSource,
                m.sourceOptions,
                enabled = m.connected && idle && m.sourceOptions.isNotEmpty(),
            ) {
                m.chooseSpeechSource(it)
            }
            GroupDivider()
            GroupPicker(
                "Language",
                m.language,
                listOf("en-US" to "English", "es-ES" to "Español"),
                enabled = idle,
            ) {
                m.chooseLanguage(it)
            }
            if (m.speechSource == "local" && m.voices.isNotEmpty()) {
                GroupDivider()
                GroupPicker(
                    "Voice",
                    m.voice,
                    m.voices
                        .filter { it.language == m.language.substringBefore('-') }
                        .map { it.id to it.name },
                    enabled = idle,
                ) {
                    m.chooseVoice(it)
                }
            }
            if (
                m.speechSource == "local" &&
                    m.voices.none { it.language == m.language.substringBefore('-') }
            ) {
                GroupText(
                    "No local voice is available for this language. Choose another language or restore the gateway’s voice plugin; no automatic fallback is used."
                )
            }
            GroupDivider()
            GroupAction(
                "Preview voice",
                VoxkinSymbol.Play,
                enabled = m.connected && m.speechConsent && idle,
            ) {
                m.previewVoice()
            }
            if (m.speaking) {
                GroupDivider()
                GroupAction("Stop audio", VoxkinSymbol.Stop) { m.stopAudio() }
            }
            GroupText(
                "Language sets on-device dictation and the reply preference, independently of the speech source. Local voice selection applies to this phone, not other gateway users."
            )
        }
        GroupSection(
            "Replies",
            "You can always ask for more or less detail. Applies to new messages from this app; saved on this phone.",
        ) {
            Row(
                Modifier.padding(12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = .07f))
            ) {
                ReplyLength.entries.forEach { length ->
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (m.replyLength == length) Color.White.copy(alpha = .16f)
                                else Color.Transparent
                            )
                            .clickable(enabled = idle) { m.chooseReplyLength(length) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            length.title,
                            fontSize = 13.sp,
                            fontWeight =
                                if (m.replyLength == length) FontWeight.SemiBold
                                else FontWeight.Normal,
                        )
                    }
                }
            }
            GroupText(m.replyLength.detail)
        }
        GroupSection("Last response timing") {
            val rows = listOf(
                "First text update" to m.timing.firstUpdate,
                "Complete reply" to m.timing.reply,
                "Gateway audio request" to m.timing.speechRequest,
                "Playback setup" to m.timing.playbackSetup,
                "Send to answer audio" to m.timing.firstAudio,
            )
            if (rows.all { it.second == null })
                GroupText("Send a message to see where the wait happens.")
            rows.forEach { (label, millis) ->
                if (millis != null) GroupText("$label: ${String.format(java.util.Locale.getDefault(), "%.2f", millis / 1000.0)} s")
            }
            if (m.speechProvider.isNotBlank()) GroupText("Spoken provider: ${m.speechProvider}")
            GroupText("Reply/update times start at Send. Gateway audio includes preparation and transfer; playback setup measures the local wait before audio starts. Durations stay in memory on this phone and reset on disconnect.")
        }
        GroupSection("Troubleshooting") {
            GroupAction(
                if (troubleshootingExpanded) "Hide on-device dictation details"
                else "Show on-device dictation details",
                VoxkinSymbol.Chevron,
            ) {
                troubleshootingExpanded = !troubleshootingExpanded
            }
            if (troubleshootingExpanded) {
                GroupDivider()
                GroupText("On-device dictation: ${m.languageStatus}")
                GroupText(
                    "Dictation uses Android’s on-device recognizer only. If your selected language model is unavailable, type instead; no cloud transcription fallback is used."
                )
                GroupDivider()
                GroupAction("Check language model", enabled = idle) { m.checkLanguage() }
                GroupDivider()
                GroupAction("Download selected language", enabled = idle) { m.downloadLanguage() }
                GroupText(
                    "Downloads the selected speech model to this phone. No microphone audio is uploaded."
                )
            }
        }
        GroupSection("Privacy & connection") {
            GroupText(
                "Address and credentials are encrypted on this phone using Android Keystore. Dictation is on-device. No conversation history is saved locally. Temporary reply audio is deleted after playback. Gateway history follows its own settings."
            )
            GroupDivider()
            GroupText(
                "Leaving the app stops the microphone, audio and connection. Stop audio does not cancel agent actions; Cancel request asks the gateway to stop remaining work."
            )
            GroupDivider()
            GroupAction("Forget this gateway", enabled = idle, destructive = true) {
                confirmForget = true
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (confirmForget)
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget this gateway on this phone?") },
            text = {
                Text(
                    "Removes this mode’s saved credentials. It does not revoke the device on the gateway."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        m.forget()
                        confirmForget = false
                    }
                ) {
                    Text("Forget gateway", color = Color(0xffff6b72))
                }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } },
        )
}

@Composable
private fun SetupField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    secret: Boolean = false,
    enabled: Boolean = true,
    url: Boolean = false,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, fontSize = 15.sp) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation =
            if (secret) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    if (url) KeyboardType.Uri
                    else if (secret) KeyboardType.Password else KeyboardType.Text,
                autoCorrectEnabled = false,
            ),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
    )
}
