package studio.voxkin.android
import org.junit.Assert.*
import org.junit.Test
class CaptureTimingTest {
    @Test fun duplicateRecognitionAndReviewEditsDoNotMoveLastWordTime() {
        val c=CaptureTiming(); c.begin("a"); c.changed("hello",10); c.changed("hello",20)
        c.finishing(25); c.finishing(30); c.complete("hello",40)
        c.draftChanged("edited hello","a")
        val t=TurnTiming().sent(100,c.consume("a"))
        assertEquals(90L,t.lastWordToSend); assertEquals(15L,t.finishingDictation)
        assertNull(c.consume("a"))
    }
    @Test fun changedFinalMovesTimestampAndNaturalFinishHasNoExplicitTail() {
        val c=CaptureTiming(); c.begin("a"); c.changed("one",10); c.complete("two",30); c.complete("late",50)
        val t=TurnTiming().sent(100,c.consume("a"))
        assertEquals(70L,t.lastWordToSend); assertNull(t.finishingDictation)
    }
    @Test fun recoveredPartialCanKeepTimingButClearedOrWrongContextCannot() {
        val c=CaptureTiming(); c.begin("a"); c.changed("partial",10); c.finishing(20); c.complete(null,40)
        assertEquals(CaptureTiming.Metrics(10,20),c.consume("a"))
        c.begin("a"); c.changed("one",10); c.complete("one",20); c.draftChanged("","a")
        assertNull(c.consume("a"))
        c.begin("a"); c.changed("one",10); c.complete("one",20); assertNull(c.consume("b"))
        c.begin("a"); c.complete(null,20); assertNull(c.consume("a"))
    }
    @Test fun receiptStartAndPreparationAreIndependentFirstMeasurements() {
        val t=TurnTiming().sent(100).acknowledged(130).final(200).received(250).audio(280)
        assertEquals(30L,t.receiptCue); assertEquals(80L,t.speechPreparation)
        assertEquals(t,t.acknowledged(180).received(300).audio(400))
        assertNull(TurnTiming().acknowledged(10).receiptCue)
        assertNull(t.sent(500).receiptCue); assertNull(t.sent(500).speechPreparation)
    }
}
