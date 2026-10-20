package studio.voxkin.android

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class TurnFeedbackTest {
    @Test fun spokenDraftIsExactScopedAndConsumable() {
        val d = SpokenDraft()
        d.markFinal("Hello ", "a")
        assertFalse(d.matches("Hello", "a")); assertFalse(d.matches("Hello ", "b"))
        assertTrue(d.consume("Hello ", "a")); assertFalse(d.consume("Hello ", "a"))
        d.markFinal("Hello", "a"); d.clear(); assertFalse(d.matches("Hello", "a"))
    }
    @Test fun acceptanceAndReceiptOrderingAreStrict() {
        val f = TurnFeedback(); val t = f.begin("run", true)
        assertTrue(f.offer(t, "Checking the result."))
        assertNull(f.claimProgress(t)); assertFalse(f.claimReceipt(t))
        assertFalse(f.accept(t, null)); assertFalse(f.accept(t, "other"))
        assertFalse(f.claimReceipt(t)); assertTrue(f.accept(t, "run"))
        assertTrue(f.claimReceipt(t)); assertFalse(f.claimReceipt(t)); assertNull(f.claimProgress(t))
        f.receiptSettled(t)
        assertEquals("Checking the result.", f.claimProgress(t)); assertNull(f.claimProgress(t))
    }
    @Test fun eligibilityBoundsAndRevocation() {
        val f = TurnFeedback(); val off = f.begin("run", false)
        assertFalse(f.accept(off, "run")); assertFalse(f.offer(off, "No"))
        val t = f.begin("run", true)
        for (s in listOf(" ", "x".repeat(181), "```code```", "https://example.com")) assertFalse(f.offer(t, s))
        assertTrue(f.offer(t, "😀".repeat(180))); assertFalse(f.offer(t, "Second"))
        f.accept(t, "run"); f.claimReceipt(t); f.receiptSettled(t)
        f.invalidate()
        assertNull(f.claimProgress(t)); assertFalse(f.valid(t)); assertFalse(f.accept(t, "run"))
        f.begin("run", true); assertFalse(f.valid(t))
    }
    @Test fun localToneHasExpectedDurationFadeAndAmplitude() {
        val bytes = ReceiptTone.wav(); val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(5804, bytes.size); assertEquals("RIFF", String(bytes.copyOfRange(0,4)))
        assertEquals(24000, b.getInt(24)); assertEquals(5760, b.getInt(40))
        assertEquals(0, b.getShort(44).toInt()); assertEquals(0, b.getShort(bytes.size-2).toInt())
        val samples = (44 until bytes.size step 2).map { b.getShort(it).toInt() }
        assertTrue(samples.all { kotlin.math.abs(it) <= 1800 }); assertTrue(samples.any { it > 1000 })
    }
    @Test fun guidanceOnlyWhenRequestedAndCommandsUnchanged() {
        assertFalse(ReplyLength.BRIEF.requestText("Hello", "en-US").contains("source-reply"))
        val guided = ReplyLength.BRIEF.requestText("Hello", "en-US", true)
        assertTrue(guided.contains("source-reply")); assertTrue(guided.endsWith("]\n\nHello"))
        for (command in listOf("/new", "!status")) assertEquals(command, ReplyLength.BRIEF.requestText(command, "es-ES", true))
    }
}
