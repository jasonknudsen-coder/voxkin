package studio.voxkin.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class ResponseReport(val reportId: String, val response: String, val reason: String, val note: String) {
    fun json(): String {
        require(UUID.fromString(reportId).toString() == reportId)
        require(response.length <= 12000 && note.length <= 2000)
        require(reason in reasons.keys)
        return JSONObject().put("reportId", reportId).put("response", response)
            .put("reason", reason).put("note", note).toString()
    }
    companion object {
        val reasons = linkedMapOf("harmful" to "Harmful or unsafe", "hate" to "Hate or harassment",
            "sexual" to "Sexual content", "inaccurate" to "Misleading or inaccurate", "other" to "Other")
    }
}

/** Separate from gateway transport: no credentials, cookies, redirects or automatic retries. */
internal class ReportClient(private val endpoint: String = "https://voxkin.builderofthings.studio/api/reports") {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(20, TimeUnit.SECONDS).build()
    suspend fun submit(report: ResponseReport): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(endpoint)
            .post(report.json().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        client.newCall(request).execute().use { result ->
            check(result.code == 200 || result.code == 201) { "Report not accepted" }
            val body = result.body ?: error("Missing receipt")
            val source = body.source()
            check(!source.request(4097)) { "Invalid receipt" }
            val receipt = JSONObject(source.readUtf8()).getString("reportId")
            check(receipt == report.reportId) { "Invalid receipt" }
            receipt
        }
    }
}

/** Limit UTF-16 units without splitting an emoji/supplementary code point. */
internal fun reportExcerpt(text: String, limit: Int): String {
    var end = minOf(text.length, limit)
    if (end > 0 && end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
    return text.substring(0, end)
}
