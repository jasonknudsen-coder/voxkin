package studio.voxkin.android
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
class ProgressNoticesTest {
    private fun message() = obj("role" to "assistant", "phase" to "commentary", "content" to "Working")
    private fun event(id: String = "one") = obj("sessionKey" to "session", "runId" to "run", "messageId" to id, "message" to message())
    @Test fun markersAndSignatures() {
        assertEquals("Working", ProgressText.parse(message()))
        val plain = message().apply { remove("phase") }
        assertNull(ProgressText.parse(plain))
        assertEquals("Working", ProgressText.parse(plain.put("openclawDeliveryMirror", obj("kind" to "message-tool-source-reply", "final" to false))))
        assertNull(ProgressText.parse(plain.put("openclawDeliveryMirror", obj("kind" to "message-tool-source-reply", "final" to "false"))))
        val signed = obj("type" to "text", "text" to "Signed", "textSignature" to "{\"v\":1,\"phase\":\"commentary\"}")
        val row = obj("role" to "assistant", "content" to JSONArray().put(signed))
        assertEquals("Signed", ProgressText.parse(row))
        row.getJSONArray("content").put(obj("type" to "text", "text" to "Unsigned"))
        assertNull(ProgressText.parse(row))
    }
    @Test fun privateMalformedTerminalAndMixedRejected() {
        for ((key, value) in listOf("phase" to "final", "channel" to "analysis", "role" to "tool", "errorMessage" to "", "stopReason" to "toolUse", "__openclaw" to obj("runTerminal" to true), "openclawStreamFallback" to obj("source" to "segment", "itemId" to " "), "openclawDeliveryMirror" to "invalid"))
            assertNull("$key", ProgressText.parse(message().put(key, value)))
        for (signature in listOf("{bad", "{\"v\":1.0,\"phase\":\"commentary\"}", "{\"v\":1,\"phase\":\"analysis\"}"))
            assertNull(ProgressText.parse(message().put("content", JSONArray().put(obj("type" to "text", "text" to "Private", "textSignature" to signature)))))
        assertNull(ProgressText.parse(message().put("content", JSONArray().put(obj("type" to "text", "text" to "Visible")).put(obj("type" to "thinking", "text" to "Private")))))
    }
    @Test fun contextAndDedupe() {
        val n = ProgressNotices()
        assertFalse(n.accept(event(), "session", null))
        assertFalse(n.accept(event(), "other", "run"))
        assertFalse(n.accept(event(), "session", "other"))
        val missing = event().apply { remove("runId") }
        assertFalse(n.accept(missing, "session", "run"))
        missing.getJSONObject("message").put("__openclaw", obj("runId" to "run"))
        assertTrue(n.accept(missing, "session", "run"))
        assertFalse(n.accept(event(), "session", "run"))
        val conflict = event("two")
        conflict.getJSONObject("message").put("__openclaw", obj("runId" to "other"))
        assertFalse(n.accept(conflict, "session", "run"))
        for (id in listOf("a", "b")) {
            val e = event(id)
            e.getJSONObject("message").put("openclawStreamFallback", obj("source" to "segment", "itemId" to "segment"))
            assertEquals(id == "a", n.accept(e, "session", "run"))
        }
        assertEquals("Working\n\nWorking\n\nStream", n.display("Stream"))
        n.clear(); assertEquals("", n.display(""))
    }
    @Test fun bounds() {
        val n = ProgressNotices()
        repeat(100) { assertTrue(n.accept(event("$it"), "session", "run")) }
        assertFalse(n.accept(event("overflow"), "session", "run"))
        n.clear()
        repeat(4) {
            val e = event("$it")
            e.getJSONObject("message").put("content", "x".repeat(5000))
            assertTrue(n.accept(e, "session", "run"))
            if (it == 0) assertEquals(4000, n.display("").length)
        }
        assertEquals(12000, n.display("").length)
        assertEquals(16000, n.display("s".repeat(20000)).length)
    }
}
