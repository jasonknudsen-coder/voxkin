package studio.voxkin.android

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.client.android.Intents
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SetupScanTest {
    private val owner = "voxkin://setup#gateway=wss%3A%2F%2Fscanned.example.com&token=synthetic-owner-token"
    private val guest = "voxkin://join#gateway=https%3A%2F%2Fscanned.example.com&secret=" + "a".repeat(32)
    private fun fixture() = VoiceModel(ApplicationProvider.getApplicationContext<Application>(), false).apply {
        gateway = "wss://scan-test-${UUID.randomUUID()}.example.com"
        restricted = false
        token = "previous-token"
        invitation = "previous-invitation"
    }

    @Test fun scansOnlyReviewAndExplicitUseOnlyStagesOwnerOrGuest() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (code in listOf(owner, guest)) {
                val m = fixture(); val previous = m.gateway
                try {
                    assertTrue(m.prepareSetupScan())
                    m.reviewSetupScan(code)
                    assertEquals(previous, m.gateway)
                    assertEquals("previous-token", m.token)
                    assertNotNull(m.scannedSetup)
                    assertFalse(m.connected)
                    assertFalse(m.busy)
                    assertFalse(m.canReconnect())
                    m.useScannedSetup()
                    assertNull(m.scannedSetup)
                    assertEquals("wss://scanned.example.com", m.gateway)
                    assertEquals(code == guest, m.restricted)
                    assertEquals(if (code == guest) "" else "synthetic-owner-token", m.token)
                    assertEquals(if (code == guest) "a".repeat(32) else "", m.invitation)
                    assertFalse(m.connected)
                    assertFalse(m.busy)
                    assertFalse(m.canReconnect())
                } finally { m.disconnect() }
            }
        }
    }

    @Test fun invalidCancelAndDiscardPreserveFieldsAndNeverExposePayloadInStatus() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val m = fixture(); val previous = m.gateway
            try {
                for (code in listOf(null, "https://example.com/private-secret", "x".repeat(8193))) {
                    assertTrue(m.prepareSetupScan())
                    m.reviewSetupScan(code)
                    assertNull(m.scannedSetup)
                    assertEquals(previous, m.gateway)
                    assertEquals("previous-token", m.token)
                    assertEquals("previous-invitation", m.invitation)
                    assertFalse(m.status.contains("private-secret"))
                }
                m.reviewSetupScan(owner)
                m.discardSetupScan()
                m.useScannedSetup()
                assertEquals(previous, m.gateway)
                assertEquals("previous-token", m.token)
                assertNull(m.scannedSetup)
            } finally { m.disconnect() }
        }
    }

    @Test fun backgroundClearsReviewAndDeepLinkReplacesPendingReview() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val m = fixture()
            try {
                m.reviewSetupScan(owner)
                m.background()
                assertNull(m.scannedSetup)
                m.reviewSetupScan(owner)
                m.stage(guest)
                assertNull(m.scannedSetup)
                m.useScannedSetup()
                assertTrue(m.restricted)
                assertEquals("", m.token)
                assertFalse(m.connected)
            } finally { m.disconnect() }
        }
    }

    @Test fun restoredSetupFlowSuppressesPreviouslyApprovedAutomaticConnection() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val store = SecureStore(app)
            val gateway = "wss://scan-restore-${UUID.randomUUID()}.example.com"
            val key = "owner:$gateway"
            store.put(key, "synthetic-saved-token")
            store.put("resume:$key", "true")
            val m = VoiceModel(app, true).apply { this.gateway = gateway; restricted = false }
            try {
                assertTrue(m.canReconnect())
                m.restoreSetupFlow()
                assertTrue(m.setupFlowActive)
                assertEquals(2, m.page)
                assertTrue(m.status.contains("Setup paused"))
                assertFalse(m.canReconnect())
                m.foreground()
                assertFalse(m.busy)
                assertFalse(m.connected)
                assertNull(m.scannedSetup) // No credential-bearing review is persisted.
            } finally {
                m.disconnect()
                store.remove(key); store.remove("resume:$key")
            }
        }
    }

    @Test fun scanContractIsInternalQrOnlyAndNeverSavesImages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = SetupScannerActivity.options().createScanIntent(context)
        assertEquals(SetupScannerActivity::class.java.name, intent.component?.className)
        assertEquals("QR_CODE", intent.getStringExtra(Intents.Scan.FORMATS))
        assertFalse(intent.getBooleanExtra(Intents.Scan.BARCODE_IMAGE_ENABLED, true))
        assertFalse(intent.getBooleanExtra(Intents.Scan.BEEP_ENABLED, true))
        val info = context.packageManager.getActivityInfo(intent.component!!, 0)
        assertFalse(info.exported)
    }
}
