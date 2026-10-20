package studio.voxkin.android

enum class HapticCue { SEND, STOP, HANDS_FREE_START, MICROPHONE_READY }

/** Synchronous, optional and nonreplaying. Only the current foreground UI owns delivery. */
class HapticEvents {
    private var sink: ((HapticCue) -> Unit)? = null
    fun attach(callback: (HapticCue) -> Unit) { sink = callback }
    fun detach(callback: (HapticCue) -> Unit) { if (sink === callback) sink = null }
    fun emit(cue: HapticCue) { runCatching { sink?.invoke(cue) } }
}
