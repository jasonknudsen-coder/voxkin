package studio.voxkin.android

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HistoryPagingTest {
    private fun state(model: VoiceModel, name: String, value: Any) {
        @Suppress("UNCHECKED_CAST")
        val field = VoiceModel::class.java.getDeclaredField(name + "$" + "delegate")
            .apply { isAccessible = true }.get(model) as MutableState<Any>
        field.value = value
    }
    private fun fixture(restricted: Boolean, block: (VoiceModel, (suspend (String, JSONObject) -> JSONObject) -> Unit) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = VoiceModel(ApplicationProvider.getApplicationContext<Application>(), automaticConnection = false)
            model.gateway = "wss://history-${UUID.randomUUID()}.example.com"
            model.restricted = restricted
            state(model, "connected", true)
            model.syntheticHistory()
            state(model, "agent", "main")
            try {
                block(model) { request ->
                    VoiceModel::class.java.getDeclaredField("transport").apply { isAccessible = true }
                        .set(model, object : Transport {
                            override suspend fun request(method: String, params: JSONObject) = request(method, params)
                            override fun close() {}
                        })
                }
            } finally { model.forget() }
        }
    }

    @Test fun mutationGatesRejectWrongScopeMalformedIdsAndUnsupportedMethods() = fixture(false) { model, install ->
        var calls = 0
        install { _, _ -> calls++; obj() }
        val id = UUID.randomUUID().toString()
        val valid = Conversation("agent:main:voxkin:$id", "Synthetic", id)
        model.syntheticHistory(HistoryCapabilities.owner(listOf("sessions.patch")))
        assertTrue(model.canRename(valid))
        assertFalse(model.canDelete(valid))
        model.delete(valid)
        for (invalid in listOf(valid.copy(id = "agent:other:voxkin:$id"), valid.copy(sessionId = "bad"))) {
            model.rename(invalid, "No")
            model.delete(invalid)
            assertFalse(model.canRename(invalid))
        }
        state(model, "agent", "other")
        model.rename(valid, "Stale dialog")
        assertEquals(0, calls)
    }

    @Test fun ownerMetadataCapabilitiesEnrichAndPersistOnlyScopedTopic() = fixture(false) { model, install ->
        val id = UUID.randomUUID().toString()
        val key = "agent:main:voxkin:$id"
        for (name in listOf("historyPreviewAvailable", "historyAutoLabelAvailable"))
            VoiceModel::class.java.getDeclaredField(name).apply { isAccessible = true }.setBoolean(model, true)
        val calls = mutableListOf<String>()
        install { method, params ->
            calls += method
            when (method) {
                "sessions.list" -> obj("sessions" to JSONArray(listOf(obj("key" to key, "sessionId" to id))))
                "sessions.preview" -> {
                    assertEquals(listOf(key), params.strings("keys"))
                    obj("previews" to JSONArray(listOf(obj("key" to key, "status" to "ok", "items" to JSONArray(listOf(
                        obj("role" to "user", "text" to "Hola"), obj("role" to "user", "text" to "Plan a picnic"), obj("role" to "assistant", "text" to "Bring sandwiches")
                    ))))))
                }
                "sessions.patch" -> { assertEquals(id, params.getString("expectedSessionId")); assertEquals(key, params.getString("key")); obj() }
                else -> error("Unexpected method")
            }
        }
        model.listHistory()
        assertEquals(listOf("sessions.list", "sessions.preview", "sessions.patch"), calls)
        assertEquals("Plan a picnic", model.conversations.single().title)
        assertEquals("Bring sandwiches", model.conversations.single().preview)
        assertFalse(model.busy)
    }

    @Test fun missingPreviewCapabilityKeepsSafeFallbackTitleWithoutExtraRequests() = fixture(false) { model, install ->
        val id = UUID.randomUUID().toString()
        var calls = 0
        install { method, _ ->
            calls++
            assertEquals("sessions.list", method)
            obj("sessions" to JSONArray(listOf(obj("key" to "agent:main:voxkin:$id", "derivedTitle" to "[Voxkin reply preference truncated..."))))
        }
        model.listHistory()
        assertEquals(1, calls)
        assertEquals("Conversation", model.conversations.single().title)
    }

    @Test fun restrictedConversationCursorAppendsDedupesAndKeepsMetadata() = fixture(true) { model, install ->
        val a = UUID.randomUUID().toString()
        val b = UUID.randomUUID().toString()
        var count = 0
        install { method, params ->
            assertEquals("voxkin.history.list", method)
            assertEquals(20, params.getInt("limit"))
            count++
            if (count == 1) {
                assertFalse(params.has("before"))
                obj("conversations" to JSONArray().put(obj("id" to a, "title" to "First", "preview" to "Preview", "updatedAt" to 1000L)), "nextCursor" to a)
            } else {
                assertEquals(a, params.getString("before"))
                obj("conversations" to JSONArray().put(obj("id" to a, "title" to "Duplicate")).put(obj("id" to b, "title" to "Older")))
            }
        }
        model.listHistory()
        assertTrue(model.conversationsHaveMore)
        assertEquals("Preview", model.conversations.first().preview)
        assertEquals(1000L, model.conversations.first().updatedAt)
        model.listHistory(more = true)
        assertEquals(listOf(a, b), model.conversations.map { it.id })
        assertEquals("First", model.conversations.first().title)
        assertFalse(model.conversationsHaveMore)
        model.listHistory(more = true)
        assertEquals(2, count)
    }

    @Test fun ownerListOffsetsCountRawRowsAndRefreshStartsAtZero() = fixture(false) { model, install ->
        val offsets = mutableListOf<Int>()
        install { _, params ->
            val offset = params.getInt("offset")
            offsets += offset
            assertFalse(params.getBoolean("archived"))
            val rows = JSONArray()
            if (offset == 0) repeat(50) {
                rows.put(obj("key" to "agent:${if(it == 0) "other" else "main"}:voxkin:${UUID.randomUUID()}", "label" to "Item"))
            }
            obj("sessions" to rows)
        }
        model.listHistory()
        assertEquals(49, model.conversations.size)
        model.listHistory(more = true)
        assertFalse(model.conversationsHaveMore)
        model.listHistory()
        assertEquals(listOf(0, 50, 0), offsets)
    }

    @Test fun ownerOlderMessagesPrependWithoutReplacingCurrentDuplicateOrLatestReply() = fixture(false) { model, install ->
        val conversation = "agent:main:voxkin:${UUID.randomUUID()}"
        val offsets = mutableListOf<Int>()
        install { method, params ->
            assertEquals("chat.history", method)
            val offset = params.getInt("offset")
            offsets += offset
            if (offset == 0) obj("messages" to JSONArray()
                .put(obj("id" to "two", "role" to "user", "text" to "Current user"))
                .put(obj("id" to "three", "role" to "assistant", "text" to "Latest answer")),
                "nextOffset" to 2, "hasMore" to true)
            else obj("messages" to JSONArray()
                .put(obj("id" to "one", "role" to "user", "text" to "Older user"))
                .put(obj("id" to "two", "role" to "user", "text" to "Stale duplicate")),
                "hasMore" to false, "nextOffset" to 4)
        }
        model.openHistory(Conversation(conversation, "Synthetic"))
        assertEquals(conversation, model.selectedConversationId)
        model.draft = "Unsent draft"
        model.loadOlderMessages()
        assertEquals(listOf("one", "two", "three"), model.messages.map { it.id })
        assertEquals("Current user", model.messages[1].text)
        assertEquals("Latest answer", model.messages.last().text)
        assertEquals("Unsent draft", model.draft)
        assertFalse(model.messagesHaveMore)
        model.refreshConversation()
        assertEquals(listOf(0, 2, 0), offsets)
        assertEquals("Unsent draft", model.draft)
        assertFalse(model.speaking)
    }

    @Test fun restrictedOlderCursorStopsWhenGatewayDoesNotAdvance() = fixture(true) { model, install ->
        val id = UUID.randomUUID().toString()
        val cursor = UUID.randomUUID().toString()
        var calls = 0
        install { _, params ->
            calls++
            if (calls == 1) assertFalse(params.has("before")) else assertEquals(cursor, params.getString("before"))
            obj("messages" to JSONArray().put(obj("id" to "same", "role" to "assistant", "text" to "Reply")), "nextCursor" to cursor)
        }
        model.openHistory(Conversation(id, "Synthetic"))
        model.loadOlderMessages()
        assertFalse(model.messagesHaveMore)
        assertEquals(1, model.messages.size)
        model.loadOlderMessages()
        assertEquals(2, calls)
        assertFalse(model.speaking)
    }

    @Test fun deletionInvalidatesPaginationBeforeAnyFurtherPageRequest() {
        for (restricted in listOf(false, true)) fixture(restricted) { model, install ->
            val id = if (restricted) UUID.randomUUID().toString() else "agent:main:voxkin:${UUID.randomUUID()}"
            val calls = mutableListOf<String>()
            install { method, params ->
                calls += method
                if (method == "sessions.list") {
                    assertEquals(0, params.getInt("offset"))
                    obj("sessions" to JSONArray().apply { repeat(50) { put(obj("key" to id, "sessionId" to "11111111-2222-4333-8444-555555555555")) } })
                } else if (method == "voxkin.history.list") {
                    assertFalse(params.has("before"))
                    obj("conversations" to JSONArray().put(obj("id" to id)), "nextCursor" to id)
                } else obj()
            }
            model.listHistory()
            assertTrue(model.conversationsHaveMore)
            model.delete(model.conversations.first())
            assertFalse(model.conversationsHaveMore)
            val afterDelete = calls.size
            model.listHistory(more = true)
            assertEquals(afterDelete, calls.size)
            model.listHistory()
            assertEquals(afterDelete + 1, calls.size)
        }
    }

    @Test fun backgroundDiscardsLateHistoryPageAndPagingState() = fixture(true) { model, install ->
        val response = CompletableDeferred<JSONObject>()
        install { _, _ -> response.await() }
        model.listHistory()
        assertTrue(model.busy)
        model.background()
        val id = UUID.randomUUID().toString()
        response.complete(obj("conversations" to JSONArray().put(obj("id" to id)), "nextCursor" to id))
        assertTrue(model.conversations.isEmpty())
        assertFalse(model.conversationsHaveMore)
        assertFalse(model.messagesHaveMore)
        assertFalse(model.busy)
    }
}
