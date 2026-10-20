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

    private fun model(): VoiceModel = ViewModelProvider(ui.activity)[VoiceModel::class.java].apply { syntheticHistory() }

    @Test fun invitationQrProtectsWindowAndHidingClearsProtection() {
        ui.runOnIdle {
            val m = model()
            m.restricted = false; m.chooseRequestAdmin(true)
            flag(m, "connected", true); flag(m, "adminPermissionGranted", true)
            flag(m, "supportsAccessManagement", true); flag(m, "accessLoaded", true)
            @Suppress("UNCHECKED_CAST")
            fun set(name: String, value: Any) {
                (VoiceModel::class.java.getDeclaredField(name + "$" + "delegate").apply { isAccessible = true }.get(m) as MutableState<Any>).value = value
            }
            m.page = 2
            set("shareInvitation", AccessPolicy.invitation("wss://qr.example.com", "s".repeat(43)))
            set("invitationExpiresAt", System.currentTimeMillis() + 600000)
        }
        ui.onNodeWithContentDescription("Single-use phone invitation QR").performScrollTo().assertIsDisplayed()
        ui.runOnIdle { assertTrue(ui.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0) }
        ui.onNodeWithText("Hide invitation QR").performScrollTo().performClick()
        ui.runOnIdle {
            assertEquals("", model().shareInvitation)
            assertEquals(0, ui.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    @Test fun accessApprovalDialogNamesPhoneAndCancelDoesNotGrantAccess() {
        ui.runOnIdle {
            val m = model()
            m.restricted = false
            m.chooseRequestAdmin(true)
            flag(m, "connected", true); flag(m, "adminPermissionGranted", true)
            flag(m, "supportsAccessManagement", true); flag(m, "accessLoaded", true)
            @Suppress("UNCHECKED_CAST")
            fun set(name: String, value: Any) {
                (VoiceModel::class.java.getDeclaredField(name + "$" + "delegate").apply { isAccessible = true }.get(m) as MutableState<Any>).value = value
            }
            set("accessAgents", listOf("guest"))
            set("accessDevices", listOf(AccessRow("a".repeat(32), "Family phone", "guest", "pending", System.currentTimeMillis() + 600000)))
            m.page = 2
            m.prepareAccessAction("approve", "a".repeat(32))
        }
        ui.onNodeWithText("Approve phone?").assertIsDisplayed()
        ui.onNodeWithText("Family phone · guest", substring = true).assertIsDisplayed()
        ui.onNodeWithText("ID: aaaaaaaa", substring = true).assertIsDisplayed()
        capture("access-approval", dialog = true)
        ui.onNodeWithText("Cancel").performClick()
        ui.runOnIdle { assertNull(model().accessConfirmation); assertFalse(model().busy) }
        ui.onNodeWithText("Refresh phones and invitations").performScrollTo().assertIsDisplayed()
    }

    @Test fun scannedSetupReviewHidesCredentialAndRequiresSeparateUse() {
        ui.runOnIdle {
            model().reviewSetupScan("voxkin://setup#gateway=wss%3A%2F%2Fqr.example.com&token=synthetic-hidden-credential")
        }
        ui.onNodeWithText("Review owner setup").assertIsDisplayed()
        ui.onNodeWithText("Gateway: wss://qr.example.com").assertIsDisplayed()
        ui.onNodeWithText("synthetic-hidden-credential", substring = true).assertDoesNotExist()
        capture("qr-review", dialog = true)
        ui.onNodeWithText("Use scanned setup").performClick()
        ui.runOnIdle {
            assertEquals("wss://qr.example.com", model().gateway)
            assertEquals("synthetic-hidden-credential", model().token)
            assertFalse(model().connected)
            assertFalse(model().busy)
            assertTrue(model().setupFlowActive)
        }
        ui.onNodeWithText("Setup staged only.", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun handsFreeRequiresExplicitExplanationAndNormalMicStillReviews() {
        ui.runOnIdle {
            val m = model()
            flag(m, "connected", true)
            flag(m, "gatewaySpeechAvailable", true)
            m.restricted = false
            m.speechConsent = true
            m.chooseSpeechSource("gateway")
        }
        ui.onNodeWithTag("startHandsFree").assertIsEnabled().performClick()
        ui.onNodeWithText("Start hands-free conversation?").assertIsDisplayed()
        ui.onNodeWithText("Your words send automatically", substring = true).assertIsDisplayed()
        capture("handsfree-optin", dialog = true)
        ui.onNodeWithText("Not now").performClick()
        ui.runOnIdle { assertFalse(model().conversationMode); assertFalse(model().recording) }
        ui.onNodeWithContentDescription("Talk to agent").assertIsDisplayed()
    }

    @Test fun unsupportedHistoryDisablesRefreshButOwnerCanStartNew() {
        ui.runOnIdle {
            val m = model()
            flag(m, "connected", true)
            m.syntheticHistory(HistoryCapabilities.owner(emptyList()))
            m.page = 1
        }
        ui.onNodeWithContentDescription("Refresh history").assertIsNotEnabled()
        ui.onNodeWithText("New conversation").assertIsEnabled()
        ui.onNodeWithText("This gateway does not provide a conversation list.").assertIsDisplayed()
        ui.onNodeWithText("Refresh conversation").performScrollTo().assertIsNotEnabled()
    }

    @Test fun sameContextReconnectDismissesPendingRename() {
        lateinit var m: VoiceModel
        ui.runOnIdle {
            m = model()
            flag(m, "connected", true)
            @Suppress("UNCHECKED_CAST")
            fun set(name: String, value: Any) {
                (VoiceModel::class.java.getDeclaredField(name + "$" + "delegate")
                    .apply { isAccessible = true }.get(m) as MutableState<Any>).value = value
            }
            set("agent", "main")
            val id = java.util.UUID.randomUUID().toString()
            set("conversations", listOf(Conversation("agent:main:voxkin:$id", "Synthetic topic", id)))
            m.page = 1
        }
        ui.onNodeWithContentDescription("Actions for Synthetic topic").performClick()
        ui.onNodeWithText("Rename").performClick()
        ui.onNodeWithText("Rename conversation").assertIsDisplayed()
        ui.runOnIdle {
            m.disconnect()
            flag(m, "connected", true)
            m.syntheticHistory()
        }
        ui.onNodeWithText("Rename conversation").assertDoesNotExist()
    }

    @Test fun historyShowsSelectedSummaryAndPagingControls() {
        ui.runOnIdle {
            val m = model()
            flag(m, "connected", true)
            flag(m, "conversationsHaveMore", true)
            flag(m, "messagesHaveMore", true)
            @Suppress("UNCHECKED_CAST")
            fun set(name: String, value: Any) {
                val field = VoiceModel::class.java.getDeclaredField(name + "$" + "delegate")
                    .apply { isAccessible = true }.get(m) as MutableState<Any>
                field.value = value
            }
            set("agent", "main")
            val id = java.util.UUID.randomUUID().toString()
            VoiceModel::class.java.getDeclaredField("sessionId").apply { isAccessible = true }.set(m, id)
            set("conversations", listOf(Conversation("agent:main:voxkin:$id", "Weekend plans",
                preview = "A walk followed by lunch.", updatedAt = 1792521000000)))
            set("messages", listOf(ChatLine("assistant", "A quiet afternoon sounds good.", "reply")))
            m.page = 1
        }
        ui.onNodeWithText("Current conversation").assertIsDisplayed()
        ui.onNodeWithText("A walk followed by lunch.").assertIsDisplayed()
        ui.onNodeWithText("Updated ", substring = true).assertIsDisplayed()
        ui.onNodeWithText("Load more conversations").performScrollTo().assertIsDisplayed()
        capture("history-details")
        ui.onNodeWithText("Refresh conversation").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Load older messages").performScrollTo().assertIsDisplayed()
    }

    @Test fun streamingReplyIsClearlyLabelledAndDisappearsOnCancel() {
        ui.runOnIdle {
            val m = model()
            flag(m, "connected", true)
            flag(m, "busy", true)
            @Suppress("UNCHECKED_CAST")
            val run = VoiceModel::class.java.getDeclaredField("activeRun" + "$" + "delegate")
                .apply { isAccessible = true }.get(m) as MutableState<String?>
            run.value = "synthetic-stream"
            m.status = "Your agent is replying…"
            @Suppress("UNCHECKED_CAST")
            val messages = VoiceModel::class.java.getDeclaredField("messages" + "$" + "delegate")
                .apply { isAccessible = true }.get(m) as MutableState<List<ChatLine>>
            messages.value = listOf(ChatLine("user", "Tell me something interesting."))
            @Suppress("UNCHECKED_CAST")
            val text = VoiceModel::class.java.getDeclaredField("streamingText" + "$" + "delegate")
                .apply { isAccessible = true }.get(m) as MutableState<String>
            text.value = "This answer is still arriving."
        }
        ui.onNodeWithText("Reply in progress").assertIsDisplayed()
        ui.onNodeWithText("This answer is still arriving.").assertIsDisplayed()
        capture("streaming")
        ui.runOnIdle { model().cancelTurn() }
        ui.onNodeWithText("Reply in progress").assertDoesNotExist()
        ui.onNodeWithText("This answer is still arriving.").assertDoesNotExist()
    }

    private fun flag(model: VoiceModel, name: String, value: Boolean) {
        @Suppress("UNCHECKED_CAST")
        val state = VoiceModel::class.java.getDeclaredField(name + "$" + "delegate")
            .apply { isAccessible = true }.get(model) as MutableState<Boolean>
        state.value = value
    }

    private fun capture(name: String, dialog: Boolean = false) {
        val bitmap = (if (dialog) ui.onNode(isDialog()) else ui.onRoot()).captureToImage().asAndroidBitmap()
        File(ui.activity.cacheDir, "parity-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun historyShowsEarlierLoadedMessagesNotJustLatestPair() {
        ui.runOnIdle {
            val m = model()
            flag(m, "connected", true)
            @Suppress("UNCHECKED_CAST")
            val state = VoiceModel::class.java.getDeclaredField("messages" + "$" + "delegate")
                .apply { isAccessible = true }.get(m) as MutableState<List<ChatLine>>
            state.value = listOf(ChatLine("user", "Earlier question"), ChatLine("assistant", "Earlier answer"),
                ChatLine("tool", "Hidden technical payload"), ChatLine("user", "Latest question"), ChatLine("assistant", "Latest answer"))
            m.page = 1
        }
        ui.onNodeWithText("Earlier answer").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Hidden technical payload").assertDoesNotExist()
        ui.onNodeWithText("Continue in Talk").performScrollTo().performClick()
        ui.onNodeWithText("Latest answer").assertIsDisplayed()
    }

    @Test fun dictationDiagnosticsAreCollapsedUntilRequested() {
        ui.runOnIdle { model().page = 2 }
        ui.onNodeWithText("Check language model").assertDoesNotExist()
        ui.onNodeWithText("Show on-device dictation details").performScrollTo().performClick()
        ui.onNodeWithText("Check language model").performScrollTo().assertIsDisplayed()
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
