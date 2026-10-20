package studio.voxkin.android

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val model: VoiceModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) intent.dataString?.let { model.stage(it) }
        setContent {
            MaterialTheme(
                colorScheme =
                    darkColorScheme(
                        primary = Color(0xff59d7d0),
                        background = Color(0xff101827),
                        surface = Color(0xff1d293c),
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

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) model.background()
    }
}

@Composable
private fun Voxkin(m: VoiceModel) {
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) m.startRecording()
            else m.status = "Allow microphone access to dictate, or type your message."
        }
    var setup by remember { mutableStateOf("") }
    var rename by remember { mutableStateOf<Conversation?>(null) }
    var title by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Conversation?>(null) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Talk", "History", "Settings").forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = m.page == i,
                        onClick = {
                            m.page = i
                            if (i == 1) m.listHistory()
                        },
                        icon = { Text(listOf("◉", "◷", "⚙")[i]) },
                        label = { Text(label) },
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Voxkin", style = MaterialTheme.typography.headlineLarge)
            Text(
                m.status,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
            when (m.page) {
                0 -> {
                    if (!m.connected) Button(onClick = { m.page = 2 }) { Text("Set up gateway") }
                    if (m.connected) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { m.newConversation() }, enabled = !m.busy) {
                                Text("New conversation")
                            }
                            TextButton(onClick = { m.cancelTurn() }) { Text("Stop audio / reply") }
                        }
                    }
                    m.messages.forEach { line ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    if (line.role == "user") "You" else "Assistant",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(line.text)
                            }
                        }
                    }
                    if (m.recording || m.settling) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    if (m.microphoneReady) "● Listening"
                                    else if (m.settling) "Finishing your words…"
                                    else "Preparing microphone…"
                                )
                                LinearProgressIndicator(
                                    progress = { m.level },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    m.partial.ifEmpty {
                                        if (m.microphoneReady) "Speak now"
                                        else "Wait for Listening before speaking"
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = m.draft,
                        onValueChange = { m.draft = it.take(16000) },
                        label = { Text("Message — review before sending") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        enabled = !m.recording && !m.settling && !m.busy,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (m.recording) m.stopRecording()
                                else permission.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            enabled = m.connected && !m.busy && !m.settling,
                        ) {
                            Text(if (m.recording) "Stop recording" else "Record")
                        }
                        Button(
                            onClick = { m.send() },
                            enabled =
                                m.connected &&
                                    !m.busy &&
                                    !m.recording &&
                                    !m.settling &&
                                    m.draft.isNotBlank(),
                        ) {
                            Text("Send ↑")
                        }
                        TextButton(
                            onClick = {
                                m.stopRecording(true)
                                m.draft = ""
                            },
                            enabled = !m.busy,
                        ) {
                            Text("Clear")
                        }
                    }
                    Text(
                        "Record → Stop → Review → Send. Speech stays on your phone until you send the transcript.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                1 -> {
                    Row {
                        Button(onClick = { m.listHistory() }, enabled = m.connected && !m.busy) {
                            Text("Refresh")
                        }
                        TextButton(
                            onClick = { m.newConversation() },
                            enabled = m.connected && !m.busy,
                        ) {
                            Text("New")
                        }
                    }
                    Text(
                        "Latest ${if (m.restricted) 20 else 50} conversations. History stays on your gateway.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    m.conversations.forEach { c ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                TextButton(onClick = { m.openHistory(c) }, enabled = !m.busy) {
                                    Text(c.title)
                                }
                                Row {
                                    TextButton(
                                        onClick = {
                                            rename = c
                                            title = c.title
                                        },
                                        enabled = !m.busy,
                                    ) {
                                        Text("Rename")
                                    }
                                    TextButton(onClick = { deleting = c }, enabled = !m.busy) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Text("Gateway connection", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = setup,
                        onValueChange = { setup = it },
                        label = { Text("Paste setup or invitation link") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !m.connected && !m.busy,
                    )
                    TextButton(
                        onClick = {
                            m.stage(setup)
                            setup = ""
                        },
                        enabled = setup.isNotBlank() && !m.connected && !m.busy,
                    ) {
                        Text("Stage link (does not connect)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = m.restricted,
                            onCheckedChange = {
                                m.restricted = it
                                m.token = ""
                                m.invitation = ""
                            },
                            enabled = !m.connected && !m.busy,
                        )
                        Text("Restricted guest access")
                    }
                    OutlinedTextField(
                        value = m.gateway,
                        onValueChange = { m.updateGateway(it) },
                        label = { Text("wss:// gateway") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !m.connected && !m.busy,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = m.token,
                        onValueChange = { m.token = it },
                        label = {
                            Text(
                                if (m.restricted) "Device token (saved after enrollment)"
                                else "Gateway token (or use saved grant)"
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !m.connected && !m.busy,
                    )
                    if (m.restricted) {
                        OutlinedTextField(
                            value = m.invitation,
                            onValueChange = { m.invitation = it },
                            label = { Text("Invitation secret") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !m.connected && !m.busy,
                        )
                        Button(
                            onClick = { m.enroll() },
                            enabled = m.invitation.isNotBlank() && !m.connected && !m.busy,
                        ) {
                            Text("Enroll this phone")
                        }
                        Text(
                            "Only enroll with an owner you trust. Owner approval is required before connection.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { if (m.connected) m.disconnect() else m.connect() },
                            enabled = !m.busy,
                        ) {
                            Text(if (m.connected) "Disconnect" else "Connect")
                        }
                        TextButton(onClick = { m.forget() }, enabled = !m.busy) {
                            Text("Forget access")
                        }
                    }
                    if (m.agents.size > 1) {
                        Text("Agent")
                        m.agents.forEach { a ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = m.agent == a.first,
                                    onClick = { m.chooseAgent(a.first) },
                                    enabled = !m.busy,
                                )
                                Text(a.second)
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Voice", style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = m.language == "en-US",
                            onClick = { m.chooseLanguage("en-US") },
                            enabled = !m.recording && !m.settling,
                        )
                        Text("English")
                        RadioButton(
                            selected = m.language == "es-ES",
                            onClick = { m.chooseLanguage("es-ES") },
                            enabled = !m.recording && !m.settling,
                        )
                        Text("Español")
                    }
                    Text(
                        "Only the Android on-device speech recognizer is used. If your language model is unavailable, type instead. No cloud transcription fallback.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(m.languageStatus, style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(
                            onClick = { m.checkLanguage() },
                            enabled = !m.recording && !m.settling,
                        ) {
                            Text("Check model")
                        }
                        TextButton(
                            onClick = { m.downloadLanguage() },
                            enabled = !m.recording && !m.settling,
                        ) {
                            Text("Download language")
                        }
                    }
                    Text(
                        "Downloads the selected speech model to this phone; no microphone audio is uploaded.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = m.speechConsent,
                            onCheckedChange = {
                                m.speechConsent = it
                                if (!it) m.stopAudio()
                            },
                            enabled = m.connected,
                        )
                        Text("Allow gateway speech")
                    }
                    Text(
                        "When enabled, reply text is sent to your gateway’s configured speech provider and any configured fallback. Enable only if you accept that gateway’s policy. Permission resets on disconnect.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (m.voices.isNotEmpty()) {
                        Text("Gateway voice")
                        m.voices
                            .filter { it.language == m.language.substringBefore('-') }
                            .forEach { v ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = m.voice == v.id,
                                        onClick = { m.voice = v.id },
                                    )
                                    Text(v.name)
                                }
                            }
                    }
                    Text(
                        "Native Android preview • No Android Auto integration. Leaving the app stops microphone, playback and connection; reconnect explicitly.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    rename?.let { c ->
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text("Rename conversation") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it.take(80) }) },
            confirmButton = {
                TextButton(
                    onClick = {
                        m.rename(c, title)
                        rename = null
                    }
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
                Text("This deletes the selected history on your gateway. It cannot be undone here.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        m.delete(c)
                        deleting = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
