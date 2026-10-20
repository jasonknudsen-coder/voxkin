package studio.voxkin.android

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReportResponseTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun previewCancelNeverSubmitsAndExplicitSubmitUsesEditedText() {
        val reports=mutableListOf<ResponseReport>()
        compose.runOnUiThread { compose.activity.setContent { ReportResponseAction("Original private text"); ReportResponseDialog(submit = { reports.add(it); it.reportId }) } }
        compose.onNodeWithText("Report response").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(reports.isEmpty())
        compose.onNodeWithText("Report response").performClick()
        compose.onNodeWithText("Response (editable or removable)").performTextReplacement("Reviewed excerpt")
        compose.onNodeWithText("Submit report").performClick()
        compose.waitForIdle()
        assertEquals(1,reports.size); assertEquals("Reviewed excerpt",reports.single().response)
        compose.onNodeWithText("Report received").assertExists()
    }
    @Test fun inFlightReferenceAndReceiptSurviveActivityRecreation() {
        val delivered = kotlinx.coroutines.CompletableDeferred<Unit>()
        var sent: ResponseReport? = null
        compose.runOnUiThread { compose.activity.setContent {
            ReportResponseAction("Rotation reply")
            ReportResponseDialog(submit = { sent = it; delivered.await(); it.reportId })
        } }
        compose.onNodeWithText("Report response").performClick()
        compose.onNodeWithText("Submit report").performClick()
        compose.waitForIdle()
        assertNotNull(sent)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Submitting…").assertExists()
        delivered.complete(Unit)
        compose.waitUntil(5000) { compose.onAllNodesWithText("Report received").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Thank you. Reference: ${sent!!.reportId}").assertExists()
    }
    @Test fun failedSubmissionOnlyRetriesOnTapWithSameIdAndPayload() {
        val reports=mutableListOf<ResponseReport>()
        compose.runOnUiThread { compose.activity.setContent { ReportResponseAction("Reply"); ReportResponseDialog(submit = { reports.add(it); throw java.io.IOException() }) } }
        compose.onNodeWithText("Report response").performClick()
        compose.onNodeWithText("Submit report").performClick()
        compose.waitForIdle(); assertEquals(1,reports.size)
        compose.onNodeWithText("Retry report").performClick()
        compose.waitForIdle(); assertEquals(2,reports.size)
        assertEquals(reports[0],reports[1])
    }
}
