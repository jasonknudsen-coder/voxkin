package studio.voxkin.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel

internal class ReportState : ViewModel() {
    var open by mutableStateOf(false)
    var response by mutableStateOf("")
    var note by mutableStateOf("")
    var reason by mutableStateOf("other")
    var busy by mutableStateOf(false)
    var receipt by mutableStateOf<String?>(null)
    var error by mutableStateOf(false)
    var attempted by mutableStateOf(false)
    var truncated by mutableStateOf(false)
    var id = ""
    val submitScope get() = viewModelScope
    fun begin(text: String) {
        if (open || busy) return
        id = UUID.randomUUID().toString()
        response = reportExcerpt(text, 12000); truncated = text.length > 12000
        note = ""; reason = "other"; receipt = null; error = false; attempted = false; open = true
    }
    fun close() {
        if (busy) return
        open = false; response = ""; note = ""; receipt = null; id = ""
    }
}

@Composable
internal fun ReportResponseAction(text: String, state: ReportState = viewModel()) {
    TextButton(onClick = { state.begin(text) }) { Text("Report response") }
}

@Composable
internal fun ReportResponseDialog(state: ReportState = viewModel(), submit: suspend (ResponseReport) -> String = { ReportClient().submit(it) }) {
    if (state.open) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) state.close() },
            title = { Text(if (state.receipt != null) "Report received" else "Report response") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.receipt != null) Text("Thank you. Reference: ${state.receipt}") else {
                        Text("Only the text, reason and note below are sent to Builder of Things Studio, not your gateway. Remove anything private. No other messages, audio or gateway credentials are attached.")
                        Text("Reports are retained for up to 30 days for review. Reporting does not change your conversation.")
                        if (state.truncated) Text("This response was shortened to 12,000 characters. Edit it to include the part you want to report.")
                        OutlinedTextField(state.response, { state.response = reportExcerpt(it, 12000) }, enabled = !state.busy && !state.attempted,
                            label = { Text("Response (editable or removable)") }, modifier = Modifier.fillMaxWidth(), maxLines = 6)
                        ResponseReport.reasons.forEach { (key, label) ->
                            Row {
                                RadioButton(selected = state.reason == key, onClick = { state.reason = key }, enabled = !state.busy && !state.attempted)
                                TextButton(onClick = { state.reason = key }, enabled = !state.busy && !state.attempted) { Text(label) }
                            }
                        }
                        OutlinedTextField(state.note, { state.note = reportExcerpt(it, 2000) }, enabled = !state.busy && !state.attempted,
                            label = { Text("Optional note") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                        if (state.error) Text("Delivery could not be confirmed. Retry sends the same report without creating a duplicate if it was already received. Nothing retries automatically.")
                    }
                }
            },
            confirmButton = {
                if (state.receipt != null) TextButton(onClick = { state.close() }) { Text("Done") }
                else TextButton(enabled = !state.busy, onClick = {
                    state.busy = true; state.error = false; state.attempted = true
                    val report = ResponseReport(state.id, state.response, state.reason, state.note)
                    state.submitScope.launch {
                        try { state.receipt = submit(report) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { state.error = true }
                        finally { state.busy = false }
                    }
                }) { Text(if (state.busy) "Submitting…" else if (state.attempted) "Retry report" else "Submit report") }
            },
            dismissButton = { if (state.receipt == null) TextButton(enabled = !state.busy, onClick = { state.close() }) { Text("Cancel") } },
        )
    }
}
