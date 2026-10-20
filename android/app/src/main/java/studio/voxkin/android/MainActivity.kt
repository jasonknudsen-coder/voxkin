package studio.voxkin.android

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.*
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                Voxkin(model)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { model.stage(it) }
    }

    override fun onStart() {
        super.onStart()
        model.foreground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) model.background()
    }
}

@Composable
private fun Voxkin(m: VoiceModel) {
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
                        enabled = m.connected && !m.busy && !m.recording && !m.settling,
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
                else -> SettingsPage(m)
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
            if (latestUser == null && latestAgent == null && !keyboardVisible) {
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
    val capture = m.recording || m.settling
    val hasDraft = m.draft.isNotBlank()
    val working = m.busy || m.speaking
    val orbEnabled = !working && !m.settling && (!m.recording || m.microphoneReady)
    val actionLabel =
        when {
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
                    onClick = onTyping,
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
                            m.recording || m.settling -> VoxkinSymbol.Stop
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
                        if (capture) m.clearDraft() else m.stopAudio()
                    },
                    enabled = m.connected,
                    modifier =
                        Modifier.size(48.dp).semantics {
                            contentDescription = if (capture) "Cancel recording" else "Stop audio"
                        },
                ) {
                    VoxkinIcon(
                        if (capture) VoxkinSymbol.Close else VoxkinSymbol.Stop,
                        Modifier.size(26.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
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
    var rename by remember { mutableStateOf<Conversation?>(null) }
    var deleting by remember { mutableStateOf<Conversation?>(null) }
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
                GroupAction("New conversation", VoxkinSymbol.Plus, enabled) { m.newConversation() }
                if (m.conversations.isEmpty() && !m.busy) {
                    GroupDivider()
                    GroupText("No saved conversations yet.")
                }
                m.conversations.forEach { c ->
                    GroupDivider()
                    var menu by remember(c.id) { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = enabled) { m.openHistory(c) }
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            c.title.ifEmpty { "Conversation" },
                            fontSize = 16.sp,
                            maxLines = 2,
                            modifier = Modifier.weight(1f).padding(vertical = 17.dp),
                        )
                        Box {
                            IconButton(
                                onClick = { menu = true },
                                enabled = enabled,
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
                                    onClick = {
                                        menu = false
                                        rename = c
                                        title = c.title
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = Color(0xffff6b72)) },
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
                "Latest ${if(m.restricted)20 else 50} conversations.",
                fontSize = 12.sp,
                color = VoxkinBrand.secondary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        if (m.connected && m.messages.any { it.role == "user" || it.role == "assistant" }) {
            GroupSection(
                "Selected conversation",
                "Latest 50 loaded messages. Opening history never plays audio.",
            ) {
                GroupAction("Continue in Talk", enabled = enabled) { m.page = 0 }
            }
            m.messages
                .filter { it.role == "user" || it.role == "assistant" }
                .takeLast(50)
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
                    enabled = title.isNotBlank() && enabled,
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
                    enabled = enabled,
                ) {
                    Text("Delete conversation", color = Color(0xffff6b72))
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsPage(m: VoiceModel) {
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
            GroupDivider()
            GroupAction(
                if (m.connected) "Disconnect" else if (m.busy) "Cancel connection" else "Connect"
            ) {
                if (m.connected || m.busy) m.disconnect() else m.connect()
            }
            GroupText(m.status)
        }
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
