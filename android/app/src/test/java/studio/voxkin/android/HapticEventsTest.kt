package studio.voxkin.android
import org.junit.Assert.*
import org.junit.Test
class HapticEventsTest {
    @Test fun droppedEventsNeverReplayAndOldOwnerCannotDetachReplacement() {
        val events = HapticEvents(); val old = mutableListOf<HapticCue>(); val current = mutableListOf<HapticCue>()
        val a: (HapticCue) -> Unit = { old.add(it) }; val b: (HapticCue) -> Unit = { current.add(it) }
        events.emit(HapticCue.SEND); events.attach(a); assertTrue(old.isEmpty())
        events.emit(HapticCue.STOP); events.attach(b); events.detach(a); events.emit(HapticCue.MICROPHONE_READY)
        assertEquals(listOf(HapticCue.STOP),old); assertEquals(listOf(HapticCue.MICROPHONE_READY),current)
        events.detach(b); events.emit(HapticCue.SEND); events.attach(b)
        assertEquals(1,current.size)
    }
    @Test fun optionalFailureNeverBreaksMainAction() {
        val events = HapticEvents(); events.attach { error("No haptic device") }
        events.emit(HapticCue.SEND)
    }
}
