package studio.voxkin.android

import kotlinx.coroutines.*

/** One playing clip and at most one prefetched clip; cancellation owns both. */
object SpeechPlayback {
    suspend fun <T> play(
        chunks: List<String>,
        synthesize: suspend (String) -> T,
        playback: suspend (T) -> Unit,
    ) = supervisorScope {
        if (chunks.isEmpty()) return@supervisorScope
        ensureActive()
        var clip = synthesize(chunks.first())
        var next: Deferred<T>? = null
        try {
            for (index in chunks.indices) {
                ensureActive()
                next = chunks.getOrNull(index + 1)?.let { chunk ->
                    async { ensureActive(); synthesize(chunk) }
                }
                playback(clip)
                ensureActive()
                next?.let { clip = it.await() }
                next = null
            }
        } finally {
            next?.cancel()
        }
    }
}
