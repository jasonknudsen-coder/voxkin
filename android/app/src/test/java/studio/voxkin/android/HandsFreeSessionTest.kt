package studio.voxkin.android

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class HandsFreeSessionTest {
    @Test fun explicitStartNeedsReadinessAndDoesNotDoubleStart() = runBlocking {
        var ready = false
        var listens = 0
        val loop = HandsFreeSession(this, { ready }, { listens++ }, {}, {}, 20, 20)
        assertFalse(loop.start())
        ready = true
        assertTrue(loop.start())
        assertFalse(loop.start())
        assertEquals(1, listens)
        loop.stop()
    }

    @Test fun changedWordsDebounceAndFinishOnlyOnce() = runBlocking {
        var finishes = 0
        val done = CompletableDeferred<Unit>()
        val loop = HandsFreeSession(this, { true }, {}, { finishes++; done.complete(Unit) }, {}, 40, 20)
        loop.start()
        loop.wordsChanged()
        yield()
        loop.wordsChanged()
        withTimeout(1000) { done.await() }
        assertEquals(1, finishes)
        loop.stop()
    }

    @Test fun stopCancelsPendingPauseAndResumeEvenAfterRestart() = runBlocking {
        var listens = 0
        var finishes = 0
        val loop = HandsFreeSession(this, { true }, { listens++ }, { finishes++ }, {}, 20, 20)
        loop.start()
        loop.wordsChanged()
        loop.replyFinished()
        yield()
        loop.stop()
        loop.start()
        delay(80)
        assertEquals(2, listens)
        assertEquals(0, finishes)
        loop.stop()
    }

    @Test fun successfulReplyResumesOnlyWhenReadyAndStopAlwaysWins() = runBlocking {
        var ready = true
        var listens = 0
        val resumed = CompletableDeferred<Unit>()
        val loop = HandsFreeSession(this, { ready }, { if (++listens == 2) resumed.complete(Unit) }, {}, {}, 20, 20)
        loop.start()
        loop.replyFinished()
        withTimeout(1000) { resumed.await() }
        assertTrue(loop.active)
        ready = false
        loop.replyFinished()
        delay(80)
        assertFalse(loop.active)
        assertEquals(2, listens)
    }

    @Test fun recognizerCompletionCancelsPauseTimer() = runBlocking {
        var finishes = 0
        val loop = HandsFreeSession(this, { true }, {}, { finishes++ }, {}, 20, 20)
        loop.start()
        loop.wordsChanged()
        yield()
        loop.finishing()
        delay(80)
        assertEquals(0, finishes)
        loop.stop()
    }
}
