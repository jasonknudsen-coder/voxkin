package studio.voxkin.android

import kotlinx.coroutines.*

/** Foreground-only scheduling. Never persists an opt-in or retries a failed turn. */
internal class HandsFreeSession(
    private val scope: CoroutineScope,
    private val canListen: () -> Boolean,
    private val listen: () -> Unit,
    private val finish: () -> Unit,
    private val changed: (Boolean) -> Unit,
    private val pauseMs: Long = 2000,
    private val resumeMs: Long = 350,
) {
    var active = false
        private set
    private var epoch = 0
    private var pause: Job? = null
    private var resume: Job? = null

    fun start(): Boolean {
        if (active || !canListen()) return false
        active = true
        epoch++
        changed(true)
        listen()
        return active
    }

    fun wordsChanged() {
        if (!active) return
        pause?.cancel()
        val current = epoch
        pause = scope.launch {
            delay(pauseMs)
            if (active && current == epoch) finish()
        }
    }

    fun finishing() { pause?.cancel(); pause = null }

    fun replyFinished() {
        if (!active) return
        resume?.cancel()
        val current = epoch
        resume = scope.launch {
            delay(resumeMs)
            if (active && current == epoch) {
                if (canListen()) listen() else stop()
            }
        }
    }

    fun stop() {
        epoch++
        finishing()
        resume?.cancel()
        resume = null
        active = false
        changed(false)
    }
}
