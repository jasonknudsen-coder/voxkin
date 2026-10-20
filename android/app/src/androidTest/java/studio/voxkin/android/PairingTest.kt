package studio.voxkin.android

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PairingTest {
    private fun fixture(block: (VoiceModel, SecureStore, String) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val model = VoiceModel(app, true)
            val store = SecureStore(app)
            val gateway = "wss://pairing-${UUID.randomUUID()}.example.com"
            model.gateway = gateway; model.restricted = false
            try { block(model, store, gateway) } finally {
                model.disconnect()
                model.gateway = gateway; model.restricted = false; model.forget()
            }
        }
    }
    @Test fun pendingApprovalPreservesCredentialsAndIdentityWithoutForegroundRetry() = fixture { m, store, gateway ->
        store.put("owner:$gateway", "synthetic-token"); store.put("resume:owner:$gateway", "true")
        val identity = store.identity(gateway).id
        assertTrue(m.canReconnect())
        m.connectionFailed(GatewayPairingRequired(UUID.randomUUID().toString()))
        assertNotNull(m.pairingApproval); assertFalse(m.canReconnect())
        m.background(); m.foreground()
        assertFalse(m.busy); assertFalse(m.connected); assertNotNull(m.pairingApproval)
        assertTrue(m.status.contains("Waiting for gateway approval"))
        assertEquals("synthetic-token",store.get("owner:$gateway")); assertEquals(identity,store.identity(gateway).id)
    }
    @Test fun explicitRetryClearsPanelWithoutClaimingNetworkSuccess() = fixture { m, _, _ ->
        m.connectionFailed(GatewayPairingRequired(null))
        m.connect() // no stored credential, so fails locally before network creation
        assertNull(m.pairingApproval); assertFalse(m.connected); assertFalse(m.busy)
    }
    @Test fun contextChangesClearApprovalAndStagingNeverConnects() = fixture { m, _, gateway ->
        m.connectionFailed(GatewayPairingRequired(null))
        m.updateGateway("$gateway/new")
        assertNull(m.pairingApproval)
        m.connectionFailed(GatewayPairingRequired(null))
        m.chooseRestrictedMode(true)
        assertNull(m.pairingApproval); assertTrue(m.restricted)
        m.chooseRestrictedMode(false)
        m.connectionFailed(GatewayPairingRequired(null))
        m.stage("voxkin://setup#gateway=wss%3A%2F%2Fstaged.example.com&token=synthetic")
        assertNull(m.pairingApproval); assertFalse(m.connected); assertFalse(m.busy)
        m.connectionFailed(GatewayPairingRequired(null)); m.forget()
        assertNull(m.pairingApproval)
    }
}
