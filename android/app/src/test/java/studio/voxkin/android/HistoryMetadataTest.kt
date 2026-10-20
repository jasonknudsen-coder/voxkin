package studio.voxkin.android

import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class HistoryMetadataTest {
    private val session = UUID.randomUUID().toString()
    private val key = "agent:main:voxkin:$session"
    private fun row() = obj("key" to key, "sessionId" to session)
    private fun item() = Conversation(key, "Conversation", session)
    private fun messages() = listOf(obj("role" to "user", "text" to "Hola!"), obj("role" to "assistant", "text" to "Hello"), obj("role" to "user", "text" to "Plan a mountain picnic"))
    private fun preview(rows: List<JSONObject> = messages(), id: String = key) = obj("previews" to JSONArray(listOf(obj("key" to id, "status" to "ok", "items" to JSONArray(rows)))))

    @Test fun titlesMatchGreetingSkipWrapperAndUnicodeRules() {
        assertEquals("Plan a mountain picnic", HistoryMetadata.automaticTitle(messages()))
        assertEquals("Real question", HistoryMetadata.clean("[Voxkin reply preference for this turn only: brief]\n\nReal\n question"))
        assertEquals("", HistoryMetadata.clean("[Voxkin reply preference truncated..."))
        val long = HistoryMetadata.automaticTitle(listOf(obj("role" to "user", "text" to "😀".repeat(100))))!!
        assertEquals(80, long.codePointCount(0, long.length)); assertTrue(long.endsWith("…"))
        assertNull(HistoryMetadata.automaticTitle(listOf(obj("role" to "user", "phase" to "analysis", "text" to "hidden"))))
    }

    @Test fun unnamedTopicAndPreviewAreBoundedAndSavedWithExpectedIdentity() = runBlocking {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        val result = HistoryMetadata.enrich(listOf(item()), listOf(row()), "main", true, { true }) { method, params ->
            calls += method to params
            if (method == "sessions.preview") preview() else obj()
        }
        assertEquals("Plan a mountain picnic", result.single().title)
        assertEquals("Plan a mountain picnic", result.single().preview)
        assertEquals(listOf("sessions.preview", "sessions.patch"), calls.map { it.first })
        assertEquals(session, calls.last().second.getString("expectedSessionId"))
        assertEquals(key, calls.last().second.getString("key"))
        assertEquals(8, calls.first().second.getInt("limit")); assertEquals(800, calls.first().second.getInt("maxChars"))
    }

    @Test fun manualAndStableNamesNeverFetchOrMutate() = runBlocking {
        for (field in listOf("label", "customTitle", "autoLabel")) {
            val r = row().put(field, "Chosen title")
            val result = HistoryMetadata.enrich(listOf(item()), listOf(r), "main", true, { true }) { _, _ -> error("Must not fetch or patch") }
            assertEquals(listOf(item()), result)
        }
    }

    @Test fun unavailablePreviewWrongKeysAndReadOnlyCapabilityNeverPatch() = runBlocking {
        val noPreview = HistoryMetadata.enrich(listOf(item()), listOf(row()), "main", true, { true }) { _, _ -> throw GatewayFailure("unsupported") }
        assertEquals(listOf(item()), noPreview)
        val methods = mutableListOf<String>()
        val wrong = HistoryMetadata.enrich(listOf(item()), listOf(row()), "main", true, { true }) { m, _ -> methods += m; preview(id = "agent:other:voxkin:$session") }
        assertEquals(listOf(item()), wrong); assertEquals(listOf("sessions.preview"), methods)
        methods.clear()
        val readOnly = HistoryMetadata.enrich(listOf(item()), listOf(row()), "main", false, { true }) { m, _ -> methods += m; preview() }
        assertEquals("Plan a mountain picnic", readOnly.single().title)
        assertEquals(listOf("sessions.preview"), methods)
    }

    @Test fun singleUserAndInvalidSessionIdentityAreNeverPersisted() = runBlocking {
        for (entry in listOf(item(), item().copy(sessionId = ""))) {
            val rows = if (entry.sessionId.isEmpty()) messages() else messages().take(2)
            val methods = mutableListOf<String>()
            HistoryMetadata.enrich(listOf(entry), listOf(row()), "main", true, { true }) { m, _ -> methods += m; preview(rows) }
            assertEquals(listOf("sessions.preview"), methods)
        }
    }

    @Test fun incompleteWrapperCannotCountAsSecondUserForPersistence() = runBlocking {
        val calls = mutableListOf<String>()
        val rows = listOf(obj("role" to "user", "text" to "Plan a mountain picnic"),
            obj("role" to "user", "text" to "[Voxkin reply preference for this turn only: ..."))
        HistoryMetadata.enrich(listOf(item()), listOf(row()), "main", true, { true }) { method, _ ->
            calls += method
            if (method == "chat.history") throw GatewayFailure("Unavailable")
            preview(rows)
        }
        assertFalse(calls.contains("sessions.patch"))
    }

    @Test fun fiftyRowsUseAtMostTwoPreviewBatchesAndFourFallbackReads() = runBlocking {
        val items = (1..50).map { val id = UUID.randomUUID().toString(); Conversation("agent:main:voxkin:$id", "Conversation", id) }
        val rows = items.map { obj("key" to it.id, "sessionId" to it.sessionId) }
        val calls = mutableListOf<String>()
        HistoryMetadata.enrich(items, rows, "main", true, { true }) { method, params ->
            calls += method
            if (method == "chat.history") throw GatewayFailure("Unavailable")
            assertEquals("sessions.preview", method)
            val keys = params.strings("keys")
            assertTrue(keys.size <= 32)
            obj("previews" to JSONArray(keys.map { key -> obj("key" to key, "status" to "ok", "items" to JSONArray(listOf(obj("role" to "user", "text" to "[Voxkin reply preference for this turn only: ...")))) }))
        }
        assertEquals(2, calls.count { it == "sessions.preview" })
        assertEquals(4, calls.count { it == "chat.history" })
    }

    @Test fun changedContextAfterPreviewPreventsPatchOrPublishing() = runBlocking {
        var current = true
        val calls = mutableListOf<String>()
        val result = runCatching { HistoryMetadata.enrich(listOf(item()), listOf(row()), "main", true, { current }) { method, _ -> calls += method; current = false; preview() } }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(listOf("sessions.preview"), calls)
    }

    @Test fun truncatedWrapperUsesBoundedFallbackAndNeverDisplaysReasoning() = runBlocking {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        val result = HistoryMetadata.enrich(listOf(item()), listOf(row()), "main", false, { true }) { method, params ->
            calls += method to params
            if (method == "sessions.preview") preview(listOf(obj("role" to "user", "text" to "[Voxkin reply preference for this turn only: ...")))
            else obj("messages" to JSONArray(messages() + obj("role" to "assistant", "phase" to "analysis", "text" to "NEVER DISPLAY")))
        }
        assertEquals(listOf("sessions.preview", "chat.history"), calls.map { it.first })
        assertEquals(32000, calls.last().second.getInt("maxBytes"))
        assertEquals("Plan a mountain picnic", result.single().preview)
    }
    @Test fun missingReadCapabilityNeverFallsBackToTranscript() = runBlocking {
        val calls = mutableListOf<String>()
        HistoryMetadata.enrich(listOf(item()), listOf(row()), "main", false, { true }, canRead = false) { method, _ ->
            calls += method
            preview(listOf(obj("role" to "user", "text" to "[Voxkin reply preference for this turn only: ...")))
        }
        assertEquals(listOf("sessions.preview"), calls)
    }

}
