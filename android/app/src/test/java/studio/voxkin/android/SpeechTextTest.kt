package studio.voxkin.android

import org.junit.Assert.*
import org.junit.Test

class SpeechTextTest {
    @Test fun matchesIphoneInlineSpeechFixtures() {
        val fixtures = mapOf(
            "- **First:** about 1–3 days.\n- **Second**: usually 1–3 days." to "First: about 1–3 days.\nSecond: usually 1–3 days.",
            "**Bold**, *gentle*, __strong__, and `code`." to "Bold, gentle, strong, and code.",
            "Read [the guide](https://example.com)." to "Read the guide.",
            "2 * 3 = 6; time 12:30; unfinished **" to "2 * 3 = 6; time 12:30; unfinished **",
            "Hello—there. Yes — absolutely. Fine -- really." to "Hello, there. Yes, absolutely. Fine, really.",
            "Hola — ¿cómo estás?" to "Hola, ¿cómo estás?",
            "Done. — Next." to "Done. Next.",
            "- First\n  - Second\n---\n• Third" to "First\nSecond\n\nThird",
            "---" to "",
            "1–3 2-4 2—4 -5 2 * 3 user_name" to "1–3 2-4 2—4 -5 2 * 3 user_name",
        )
        fixtures.forEach { (input, expected) -> assertEquals(input, expected, SpeechText.prepare(input)) }
    }

    @Test fun nestedAndReferenceLinksSpeakLabelsNotDestinations() {
        assertEquals("Read the guide.", SpeechText.prepare("Read [the **guide**](https://example.com/a_(b))."))
        assertEquals("Read the guide.", SpeechText.prepare("Read [the guide][ref].\n\n[ref]: https://example.com"))
        assertEquals("Español 😀 café", SpeechText.prepare("**Español** 😀 *café*"))
        assertEquals("first\n\nsecond", SpeechText.prepare("first\n\nsecond"))
    }
}
