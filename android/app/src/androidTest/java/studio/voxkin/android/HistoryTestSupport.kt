package studio.voxkin.android

import androidx.compose.runtime.MutableState

/** Synthetic transport fixtures explicitly advertise full history support. */
internal fun VoiceModel.syntheticHistory(capabilities: HistoryCapabilities = HistoryCapabilities(true, true, true, true, true)) {
    @Suppress("UNCHECKED_CAST")
    val state = VoiceModel::class.java.getDeclaredField("historyCapabilities\$delegate")
        .apply { isAccessible = true }.get(this) as MutableState<HistoryCapabilities>
    state.value = capabilities
}
