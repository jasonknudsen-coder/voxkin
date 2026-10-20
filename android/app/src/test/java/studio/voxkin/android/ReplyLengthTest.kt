package studio.voxkin.android

import org.junit.Assert.*
import org.junit.Test

class ReplyLengthTest {
    @Test fun commandsAreNeverWrapped() {
        for (length in ReplyLength.entries) {
            assertEquals("/status", length.requestText("/status", "es-ES"))
            assertEquals("!stop", length.requestText("!stop", "en-US"))
        }
    }

    @Test fun perTurnPreferencePreservesMessageAndLanguage() {
        val result = ReplyLength.BRIEF.requestText("Hola", "es-ES")
        assertTrue(result.contains("30–50 words"))
        assertTrue(result.contains("Respond in Spanish unless"))
        assertTrue(result.endsWith("\n\nHola"))
        assertTrue(ReplyLength.FULL.requestText("Hello", "en-US").contains("earlier concise-reply preference is off"))
    }
}
