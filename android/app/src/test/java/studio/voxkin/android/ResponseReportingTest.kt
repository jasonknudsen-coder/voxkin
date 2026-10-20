package studio.voxkin.android

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ResponseReportingTest {
    @Test fun truncationPreservesWholeEmoji() {
        assertEquals("a", reportExcerpt("a😀z", 2))
        assertEquals("a😀", reportExcerpt("a😀z", 3))
        assertEquals("", reportExcerpt("😀", 1))
    }
    private fun report() = ResponseReport(UUID.randomUUID().toString(), "Selected reply only", "other", "")
    @Test fun payloadContainsOnlyReviewedFieldsAndAllowsRemovedText() {
        val payload = JSONObject(report().copy(response = "").json())
        assertEquals(setOf("reportId", "response", "reason", "note"), payload.keys().asSequence().toSet())
        assertEquals("", payload.getString("response"))
    }
    @Test fun rejectsOversizedOrInvalidInputs() {
        for (r in listOf(report().copy(response="x".repeat(12001)), report().copy(note="x".repeat(2001)),
            report().copy(reason="unknown"), report().copy(reportId="not-a-uuid"))) {
            assertThrows(IllegalArgumentException::class.java) { r.json() }
        }
    }
    @Test fun onlyMatchingAcceptanceIsSuccessAndNoCredentialsAreSent() = runBlocking {
        MockWebServer().use { server ->
            val r=report()
            server.enqueue(MockResponse().setResponseCode(201).setBody("{\"reportId\":\"${r.reportId}\"}"))
            assertEquals(r.reportId, ReportClient(server.url("/api/reports").toString()).submit(r))
            val request=server.takeRequest()
            assertNull(request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
            assertEquals(r.json(),request.body.readUtf8())
        }
    }
    @Test fun redirectsAndMalformedReceiptsFailWithoutFollowup() = runBlocking {
        for (reply in listOf(MockResponse().setResponseCode(302).addHeader("Location", "/other"),
            MockResponse().setResponseCode(200).setBody("{\"reportId\":\"wrong\"}"),
            MockResponse().setResponseCode(503), MockResponse().setBody("x".repeat(5000)))) {
            MockWebServer().use { server ->
                server.enqueue(reply)
                var failed=false
                try { ReportClient(server.url("/api/reports").toString()).submit(report()) } catch (_: Exception) { failed=true }
                assertTrue(failed); assertEquals(1,server.requestCount)
            }
        }
    }
}
