package studio.voxkin.android

import org.json.JSONArray
import org.json.JSONObject

/** Explicitly public persisted commentary only; never infer visibility from prose. */
object ProgressText {
    fun parse(row: JSONObject): String? {
        if (row.opt("role") != "assistant" || row.has("errorMessage") ||
            row.optJSONObject("__openclaw")?.opt("runTerminal") == true) return null
        if (row.has("stopReason") && row.opt("stopReason") != "stop") return null
        if (row.has("channel") && row.opt("channel") != "commentary") return null
        var visible = false
        if (row.has("phase")) {
            if (row.opt("phase") != "commentary") return null
            visible = true
        }
        val text = when (val content = row.opt("content")) {
            is String -> content
            is JSONArray -> {
                if (content.length() == 0) return null
                var signed = 0
                val parts = mutableListOf<String>()
                for (i in 0 until content.length()) {
                    val block = content.optJSONObject(i) ?: return null
                    if (block.opt("type") !in listOf("text", "input_text", "output_text") ||
                        block.opt("text") !is String) return null
                    if (block.has("channel") && block.opt("channel") != "commentary") return null
                    if (block.has("phase") && block.opt("phase") != "commentary") return null
                    val signature = block.opt("textSignature") as? String
                    if (signature?.startsWith("{") == true) {
                        val parsed = runCatching { JSONObject(signature) }.getOrNull() ?: return null
                        if (parsed.opt("v") !is Int || parsed.opt("v") != 1) return null
                        if (parsed.has("phase")) {
                            if (parsed.opt("phase") != "commentary") return null
                            signed++
                            visible = true
                        }
                    }
                    parts.add(block.getString("text"))
                }
                if (signed != 0 && signed != content.length()) return null
                parts.joinToString("\n")
            }
            else -> return null
        }
        if (row.has("openclawDeliveryMirror")) {
            val mirror = row.optJSONObject("openclawDeliveryMirror") ?: return null
            if (mirror.opt("kind") != "message-tool-source-reply" || mirror.opt("final") != false) return null
            visible = true
        }
        if (row.has("openclawStreamFallback")) {
            val fallback = row.optJSONObject("openclawStreamFallback") ?: return null
            if (fallback.opt("source") != "segment" ||
                (fallback.opt("itemId") as? String).isNullOrBlank()) return null
            visible = true
        }
        return if (visible && text.isNotBlank()) HistoryMetadata.clip(text, 32000) else null
    }
}

class ProgressNotices {
    private val ids = mutableSetOf<String>()
    private var text = ""
    fun clear() { ids.clear(); text = "" }
    fun accept(payload: JSONObject, session: String, run: String?): Boolean {
        if (run == null || payload.opt("sessionKey") != session || ids.size >= 100) return false
        if (payload.has("channel") && payload.opt("channel") != "commentary") return false
        if (payload.has("phase") && payload.opt("phase") != "commentary") return false
        val message = payload.optJSONObject("message") ?: return false
        val outer = payload.opt("runId") as? String
        val meta = message.optJSONObject("__openclaw")
        val inner = meta?.opt("runId") as? String
        if ((payload.has("runId") && outer == null) || (meta?.has("runId") == true && inner == null)) return false
        if (outer != null && inner != null && outer != inner) return false
        if ((outer ?: inner) != run) return false
        val id = payload.opt("messageId") as? String ?: return false
        if (id.isBlank()) return false
        val notice = ProgressText.parse(message) ?: return false
        val segment = message.optJSONObject("openclawStreamFallback")?.getString("itemId")
        val key = if (segment != null) "segment:$segment" else "message:$id"
        if (!ids.add(key)) return false
        text = HistoryMetadata.clip(listOf(text, HistoryMetadata.clip(notice, 4000)).filter { it.isNotEmpty() }.joinToString("\n\n"), 12000)
        return true
    }
    fun display(stream: String) = HistoryMetadata.clip(listOf(text, stream).filter { it.isNotBlank() }.joinToString("\n\n"), 16000)
}
