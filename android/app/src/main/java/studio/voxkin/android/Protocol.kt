package studio.voxkin.android

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import org.json.JSONObject

fun obj(vararg pairs: Pair<String, Any?>) =
    JSONObject().apply { pairs.forEach { put(it.first, it.second) } }

fun JSONObject.rows(key: String): List<JSONObject> =
    optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
        ?: emptyList()

fun JSONObject.strings(key: String): List<String> =
    optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()

fun b64(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)

fun digest(data: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

object Protocol {
    fun endpoint(raw: String, restricted: Boolean = false): URI {
        val u = URI(raw.trim())
        require(u.scheme == "wss" || (restricted && u.scheme == "https")) {
            "Use a secure wss:// gateway address."
        }
        require(
            !u.host.isNullOrEmpty() &&
                u.userInfo == null &&
                u.query == null &&
                u.fragment == null &&
                (u.port == -1 || u.port in 1..65535)
        ) {
            "Gateway URL must not contain credentials, query or fragment."
        }
        if (restricted)
            require(u.path.isNullOrEmpty() || u.path == "/") {
                "Restricted access requires a gateway origin without a path."
            }
        return URI(
            "wss",
            null,
            u.host.lowercase(),
            if (u.port == 443) -1 else u.port,
            if (u.path == "/") "" else u.path,
            null,
            null,
        )
    }

    data class Setup(val gateway: String, val secret: String, val restricted: Boolean)

    fun setup(text: String): Setup {
        require(text.length <= 8192)
        val u = URI(text)
        require(
            u.scheme == "voxkin" &&
                u.host in listOf("join", "setup") &&
                u.userInfo == null &&
                u.port == -1 &&
                u.query == null &&
                u.path.isNullOrEmpty()
        )
        val fields =
            (u.rawFragment ?: error("Missing setup fields")).split("&").map { p ->
                val s = p.split("=", limit = 2)
                require(s.size == 2)
                URLDecoder.decode(s[0], "UTF-8") to URLDecoder.decode(s[1], "UTF-8")
            }
        val guest = u.host == "join"
        val name = if (guest) "secret" else "token"
        require(fields.size == 2 && fields.map { it.first }.toSet() == setOf("gateway", name))
        val map = fields.toMap()
        val gateway = map.getValue("gateway")
        endpoint(gateway, guest)
        val secret = map.getValue(name)
        require(
            if (guest) secret.matches(Regex("[A-Za-z0-9_-]{32,256}"))
            else secret.isNotEmpty() && secret.length <= 4096 && secret.none { it.isISOControl() }
        )
        return Setup(gateway, secret, guest)
    }

    fun owns(session: String, agent: String): Boolean =
        session.startsWith("agent:$agent:voxkin:") &&
            runCatching { UUID.fromString(session.removePrefix("agent:$agent:voxkin:")) }.isSuccess

    fun finalText(row: JSONObject): String =
        if (row.optString("role") == "assistant" && !row.has("errorMessage")) text(row) else ""

    fun text(row: JSONObject): String {
        if (
            row.optString("role") !in listOf("assistant", "user") ||
                row.optString("channel") == "analysis"
        )
            return ""
        val blocks = row.optJSONArray("content")
        if (
            row.optString("role") == "assistant" &&
                blocks != null &&
                (0 until blocks.length()).any {
                    blocks.optJSONObject(it)?.optString("type") !in listOf("text", "output_text")
                }
        )
            return ""
        var text =
            if (row.has("text")) row.optString("text")
            else if (row.opt("content") is String) row.optString("content")
            else
                row.rows("content")
                    .filter { it.optString("type") in listOf("text", "output_text") }
                    .joinToString("\n") { it.optString("text") }
        if (
            text.startsWith("[Voxkin reply preference for this turn only: ") &&
                text.contains("]\n\n")
        )
            text = text.substringAfter("]\n\n")
        return text.take(32000)
    }
}

/** Bounded requests without truncating speech; playback waits for each clip before the next. */
object SpeechPlan {
    /** iPhone-style short first sentence, then bounded larger clips. No text loss. */
    fun fastChunks(text: String): List<String> {
        val result = mutableListOf<String>()
        val buffer = StringBuilder()
        var count = 0
        text.codePoints().forEachOrdered { code ->
            buffer.appendCodePoint(code)
            count++
            val minimum = if (result.isEmpty()) 40 else 320
            val maximum = if (result.isEmpty()) 180 else 600
            if ((count >= minimum && code in listOf(46, 33, 63, 10)) ||
                (count >= maximum && Character.isWhitespace(code)) || count >= maximum + 100) {
                result.add(buffer.toString())
                buffer.setLength(0)
                count = 0
            }
        }
        if (buffer.isNotEmpty()) result.add(buffer.toString())
        return result.filter { it.isNotBlank() }
    }

    fun chunks(text: String, max: Int = 4000): List<String> {
        require(max > 0)
        val result = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.isNotEmpty()) {
            if (remaining.length <= max) {
                result.add(remaining)
                break
            }
            val window = remaining.take(max)
            var end = window.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', ' ')) + 1
            if (end < max / 2) end = max
            if (end < remaining.length && Character.isHighSurrogate(remaining[end - 1])) end--
            result.add(remaining.take(end).trim())
            remaining = remaining.substring(end).trimStart()
        }
        return result
    }
}
