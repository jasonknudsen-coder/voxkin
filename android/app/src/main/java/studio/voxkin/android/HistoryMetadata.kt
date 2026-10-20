package studio.voxkin.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

/** Bounded gateway metadata enrichment; never reads arbitrary or cross-agent sessions. */
internal object HistoryMetadata {
    private val trivial = Regex("^(?:(?:hi|hello|hey)(?: there)?|hola|test(?:ing)?[ 0-9]*|[0-9 ]+|can you hear me|are you there)[.!?]*$", RegexOption.IGNORE_CASE)
    fun clean(raw: String): String = Protocol.text(obj("role" to "user", "text" to raw))
        .takeUnless { it.startsWith("[Voxkin reply preference") }.orEmpty().replace(Regex("\\s+"), " ").trim()
    fun clip(text: String, count: Int): String = text.substring(0, text.offsetByCodePoints(0, minOf(count, text.codePointCount(0, text.length))))
    private fun visibleText(row: JSONObject): String {
        if (row.optString("channel") !in listOf("", "final", "commentary") || row.optString("phase") !in listOf("", "final", "commentary")) return ""
        return Protocol.text(row)
    }
    fun automaticTitle(messages: List<JSONObject>): String? {
        val texts = messages.filter { it.optString("role") == "user" }.take(3)
            .map { clean(visibleText(it)) }.filter { it.isNotBlank() }
        val title = texts.firstOrNull { !trivial.matches(it) } ?: texts.firstOrNull() ?: return null
        return if (title.codePointCount(0, title.length) > 80) clip(title, 79) + "…" else title
    }

    suspend fun enrich(
        items: List<Conversation>, rows: List<JSONObject>, agent: String, canPatch: Boolean,
        current: () -> Boolean, canRead: Boolean = true, request: suspend (String, JSONObject) -> JSONObject,
    ): List<Conversation> {
        val eligible = rows.filter { row ->
            val key = row.optString("key")
            Protocol.owns(key, agent) && items.any { it.id == key } &&
                row.opt("label") !is String && row.opt("customTitle") !is String && clean(row.optString("autoLabel")).isBlank()
        }.associateBy { it.optString("key") }.keys.take(50)
        if (eligible.isEmpty()) return items
        val output = items.toMutableList()
        var fallbackReads = 0
        suspend fun checked(method: String, params: JSONObject): JSONObject? {
            currentCoroutineContext().ensureActive()
            if (!current()) throw CancellationException("History context changed")
            val result = try { request(method, params) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            currentCoroutineContext().ensureActive()
            if (!current()) throw CancellationException("History context changed")
            return result
        }
        // Gateway preview schema allows at most32 keys. Android lists at most50 rows/page.
        for (keys in eligible.chunked(32)) {
            val previews = checked("sessions.preview", obj("keys" to JSONArray(keys), "limit" to 8, "maxChars" to 800)) ?: continue
            val seen = mutableSetOf<String>()
            for (preview in previews.rows("previews")) {
                val key = preview.optString("key")
                if (key !in keys || !seen.add(key) || preview.optString("status") != "ok") continue
                val index = output.indexOfFirst { it.id == key }
                if (index < 0) continue
                var messages = preview.rows("items").take(8)
                if (canRead && messages.any { it.optString("role") == "user" && it.optString("text").startsWith("[Voxkin reply preference for this turn only: ") && it.optString("text").endsWith("...") } && fallbackReads < 4) {
                    fallbackReads++
                    checked("chat.history", obj("sessionKey" to key, "agentId" to agent, "limit" to 8, "offset" to 0, "maxBytes" to 32000))
                        ?.let { messages = it.rows("messages").take(8) }
                }
                val title = automaticTitle(messages)
                val snippet = messages.asReversed().firstNotNullOfOrNull { message ->
                    val text = clean(visibleText(message))
                    text.takeIf { it.isNotEmpty() }
                }?.let { clip(it, 160) }.orEmpty()
                val old = output[index]
                output[index] = old.copy(title = title ?: old.title, preview = old.preview.ifBlank { snippet })
                // Persist a meaningful topic only after a real exchange. Manual and stable names never enter this path.
                if (canPatch && title != null && !trivial.matches(title) && messages.count { it.optString("role") == "user" && clean(visibleText(it)).isNotBlank() } >= 2 &&
                    old.sessionId.matches(Regex("[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))) {
                    checked("sessions.patch", obj("key" to key, "agentId" to agent, "expectedSessionId" to old.sessionId, "autoLabel" to title))
                }
            }
        }
        return output
    }
}
