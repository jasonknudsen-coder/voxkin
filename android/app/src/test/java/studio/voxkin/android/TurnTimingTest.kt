package studio.voxkin.android

import org.junit.Assert.*
import org.junit.Test

class TurnTimingTest {
    @Test fun separatesGatewayAndPlaybackWaits() {
        val t = TurnTiming().sent(100).update(120).final(200).received(270).audio(300)
        assertEquals(20L, t.firstUpdate)
        assertEquals(100L, t.reply)
        assertEquals(70L, t.speechRequest)
        assertEquals(30L, t.playbackSetup)
        assertEquals(200L, t.firstAudio)
    }
    @Test fun laterChunksDoNotOverwriteFirstMeasurements() {
        val t = TurnTiming().sent(100).update(120).final(200).received(270).audio(300)
        assertEquals(t, t.update(400).final(500).received(600).audio(700))
    }
    @Test fun newTurnResetsAndOutOfOrderCallsDoNotInventAudio() {
        assertEquals(TurnTiming(), TurnTiming().update(10).final(20).received(30).audio(40))
        val t = TurnTiming().sent(100).final(200).received(300).audio(400).sent(500)
        assertNull(t.reply)
        assertNull(t.firstAudio)
        assertNull(t.audio(600).firstAudio)
        assertEquals(0L, t.update(400).firstUpdate)
    }
}
