package studio.voxkin.android

import android.graphics.Bitmap
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Real Compose UI, fictional offline content; exists only in the test APK. */
class StoreScreenshotsTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun set(model: VoiceModel, name: String, value: Any) {
        @Suppress("UNCHECKED_CAST")
        val state = VoiceModel::class.java.getDeclaredField(name + "$" + "delegate")
            .apply { isAccessible = true }.get(model) as MutableState<Any>
        state.value = value
    }
    private fun capture(name: String) {
        ui.waitForIdle()
        ui.onRoot().captureToImage() // Synchronize a rendered frame, not only Compose semantics.
        Thread.sleep(1500)
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(ui.activity.cacheDir, "play-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    @Test fun captureStorefront() {
        lateinit var model: VoiceModel
        ui.runOnIdle {
            model = ViewModelProvider(ui.activity)[VoiceModel::class.java]
            model.syntheticHistory()
            set(model, "connected", true)
            set(model, "agent", "personal")
            set(model, "agents", listOf("personal"))
            set(model, "speechSource", "gateway")
            model.speechConsent = true
            set(model, "gatewaySpeechAvailable", true)
            model.page = 0
            model.status = "Connected."
        }
        ui.onNodeWithText("Talk to your agent.").assertIsDisplayed()
        capture("01-talk")
        ui.runOnIdle {
            set(model, "messages", listOf(
                ChatLine("user", "Suggest a simple way to unwind after a busy day."),
                ChatLine("assistant", "Take a short walk, put your phone aside, and make something warm to drink. Pick one small thing you enjoy—music, a few pages of a book, or a quiet chat—and let that be enough for tonight.")
            ))
            model.status = "Ready."
        }
        ui.onNodeWithText("Suggest a simple way to unwind after a busy day.").assertIsDisplayed()
        capture("02-conversation")
        ui.runOnIdle { set(model, "messages", emptyList<ChatLine>()); model.status = "Connected." }
        capture("01-talk")
    }
}
