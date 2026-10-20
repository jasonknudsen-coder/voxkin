package studio.voxkin.android

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PresentationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun model(): VoiceModel = ViewModelProvider(ui.activity)[VoiceModel::class.java]

    private fun flag(model: VoiceModel, name: String, value: Boolean) {
        @Suppress("UNCHECKED_CAST")
        val state = VoiceModel::class.java.getDeclaredField(name + "$" + "delegate")
            .apply { isAccessible = true }.get(model) as MutableState<Boolean>
        state.value = value
    }

    private fun capture(name: String) {
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        File(ui.activity.cacheDir, "parity-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun recordingShowsStopNotSendAndCancelDiscardsDraft() {
        ui.runOnIdle {
            model().apply {
                page = 0
                draft = "Synthetic captured words"
                status = "Listening… Tap Stop to review your words."
                flag(this, "connected", true)
                flag(this, "recording", true)
                flag(this, "microphoneReady", true)
            }
        }
        ui.onNodeWithContentDescription("Stop recording and review").assertIsDisplayed()
        ui.onNodeWithContentDescription("Send draft to agent").assertDoesNotExist()
        ui.onNodeWithContentDescription("Send message").assertDoesNotExist()
        capture("recording")
        ui.onNodeWithContentDescription("Cancel recording").performClick()
        ui.runOnIdle {
            assertFalse(model().recording)
            assertEquals("", model().draft)
            assertFalse(model().requestPending)
        }
        ui.onNodeWithContentDescription("Talk to agent").assertIsDisplayed()
    }

    @Test fun finalizedDraftShowsExplicitSendAndKeyboardToggle() {
        ui.runOnIdle {
            model().apply {
                page = 0
                draft = "Synthetic reviewed draft"
                status = "Review or edit your words, then tap Send."
                flag(this, "connected", true)
            }
        }
        ui.onNodeWithContentDescription("Send draft to agent").assertIsDisplayed()
        ui.onNodeWithContentDescription("Type instead").assertIsDisplayed()
        capture("review")
        ui.onNodeWithContentDescription("Type instead").performClick()
        ui.waitForIdle()
        ui.onNodeWithTag("draft").performTextInput(" edited")
        ui.onNodeWithText("Vox").assertIsDisplayed()
        ui.onNodeWithContentDescription("Send draft to agent").assertIsDisplayed()
        capture("keyboard")
        ui.runOnIdle { assertFalse(model().requestPending) }
    }

    @Test fun tooLongDraftStaysAvailableForEditing() {
        ui.runOnIdle {
            model().apply {
                flag(this, "connected", true)
                draft = "x".repeat(4001)
                send()
                assertEquals(4001, draft.length)
                assertFalse(requestPending)
                assertEquals("Keep messages under 4,000 characters.", status)
            }
        }
    }
}
