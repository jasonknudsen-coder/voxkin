package studio.voxkin.android

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SpeechPlaybackTest {
    @Test fun shortFirstChunksPreserveTextAndUnicode() {
        val text = "Hola, esta es una respuesta con varias frases. " + "Palabra 😀 ".repeat(200)
        val chunks = SpeechPlan.fastChunks(text)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.first().codePointCount(0, chunks.first().length) <= 280)
        assertTrue(chunks.all { it.codePointCount(0, it.length) <= 700 })
        assertTrue(chunks.none { Character.isHighSurrogate(it.last()) })
        val unbroken = "😀".repeat(900)
        assertEquals(unbroken, SpeechPlan.fastChunks(unbroken).joinToString(""))
        assertTrue(SpeechPlan.fastChunks(" ").isEmpty())
    }

    @Test fun prefetchIsOnlyOneClipAheadAndPlaybackStaysOrdered() = runBlocking {
        val synthesized = mutableListOf<String>()
        val played = mutableListOf<String>()
        SpeechPlayback.play(listOf("one", "two", "three"), {
            synthesized += it
            it
        }, {
            played += it
            yield()
            assertEquals(minOf(played.size + 1, 3), synthesized.size)
        })
        assertEquals(listOf("one", "two", "three"), played)
    }

    @Test fun cancellationStopsPlayingAndPrefetchedWork() = runBlocking {
        val prefetched = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val played = mutableListOf<String>()
        val task = launch {
            SpeechPlayback.play(listOf("one", "two", "three"), {
                if (it == "two") {
                    prefetched.complete(Unit)
                    try { awaitCancellation() } finally { cancelled.complete(Unit) }
                }
                it
            }, {
                played += it
                awaitCancellation()
            })
        }
        prefetched.await()
        task.cancelAndJoin()
        assertTrue(cancelled.isCompleted)
        assertEquals(listOf("one"), played)
    }

    @Test fun failedPrefetchDoesNotSkipOrPlayLaterClips() = runBlocking {
        val played = mutableListOf<String>()
        val result = runCatching {
            SpeechPlayback.play(listOf("one", "two", "three"), {
                if (it == "two") error("Synthetic synthesis failure")
                it
            }, { played += it; yield() })
        }
        assertTrue(result.isFailure)
        assertEquals(listOf("one"), played)
    }
}
