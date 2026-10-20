package studio.voxkin.android

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AccessManagementTest {
    private val phone = "a".repeat(32)
    private val invite = "b".repeat(32)
    private fun set(m: VoiceModel, name: String, value: Any?) {
        @Suppress("UNCHECKED_CAST")
        val state = VoiceModel::class.java.getDeclaredField(name + "$" + "delegate").apply { isAccessible = true }.get(m) as MutableState<Any?>
        state.value = value
    }
    private fun transport(m: VoiceModel, t: Transport) { VoiceModel::class.java.getDeclaredField("transport").apply { isAccessible = true }.set(m, t) }
    private fun fixture(t: Transport): VoiceModel = VoiceModel(ApplicationProvider.getApplicationContext<Application>(), false).apply {
        gateway = "wss://access-test-${UUID.randomUUID()}.example.com"
        restricted = false
        chooseRequestAdmin(true)
        set(this, "connected", true); set(this, "adminPermissionGranted", true); set(this, "supportsAccessManagement", true)
        transport(this, t)
    }
    private inner class Fake : Transport {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        var fail = false
        var expiry = System.currentTimeMillis() + 600000
        override suspend fun request(method: String, params: JSONObject): JSONObject {
            calls += method to params
            if (fail) throw GatewayFailure("Synthetic access failure")
            return if (method == "voxkin.access.list") obj("agents" to JSONArray(listOf("guest")),
                "devices" to JSONArray(listOf(obj("id" to phone, "label" to "Test phone", "agentId" to "guest", "status" to "pending", "expiresAt" to expiry))),
                "invites" to JSONArray(listOf(obj("id" to invite, "label" to "New phone", "agentId" to "guest", "expiresAt" to expiry))))
            else if (method == "voxkin.access.invite") obj("id" to invite, "label" to "New phone", "agentId" to "guest", "expiresAt" to expiry, "inviteSecret" to "s".repeat(43))
            else obj()
        }
        override fun close() {}
    }

    @Test fun managementRequiresExplicitOwnerOptInGrantedScopeAndApi() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val t = Fake(); val m = fixture(t)
            try {
                for (flag in listOf("requestAdmin", "adminPermissionGranted", "supportsAccessManagement", "connected")) {
                    set(m, flag, false)
                    m.refreshAccess(); m.prepareAccessAction("invite"); m.confirmAccessAction()
                    assertFalse(m.canManageAccess); assertTrue(t.calls.isEmpty())
                    set(m, flag, true)
                }
                m.restricted = true
                m.refreshAccess()
                assertFalse(m.canManageAccess); assertTrue(t.calls.isEmpty())
            } finally { m.disconnect() }
        }
    }

    @Test fun invitationNeedsConfirmationAndNeverReplaysDuplicateConfirm() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val t = Fake(); val m = fixture(t)
            try {
                m.refreshAccess()
                m.inviteLabel = "New phone"
                m.prepareAccessAction("invite")
                assertEquals(1, t.calls.size)
                assertNotNull(m.accessConfirmation)
                m.confirmAccessAction(); m.confirmAccessAction()
                assertEquals(listOf("voxkin.access.list", "voxkin.access.invite", "voxkin.access.list"), t.calls.map { it.first })
                assertEquals("guest", t.calls[1].second.getString("agentId"))
                assertEquals("New phone", t.calls[1].second.getString("label"))
                assertEquals(m.gateway, Protocol.setup(m.shareInvitation).gateway)
                assertTrue(Protocol.setup(m.shareInvitation).restricted)
                m.background()
                assertEquals("", m.shareInvitation); assertNull(m.accessConfirmation)
                assertTrue(m.accessDevices.isEmpty()); assertFalse(m.canManageAccess)
            } finally { m.disconnect() }
        }
    }

    @Test fun approveRevokeAndCancelUseConfirmedIdsAndDismissNeverMutates() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val t = Fake(); val m = fixture(t)
            try {
                m.refreshAccess()
                m.prepareAccessAction("approve", phone); m.dismissAccessAction(); m.confirmAccessAction()
                assertEquals(1, t.calls.size)
                for (action in listOf("approve", "revoke", "cancel")) {
                    val id = if (action == "cancel") invite else phone
                    m.prepareAccessAction(action, id)
                    assertNotNull(m.accessConfirmation)
                    val before = t.calls.size
                    m.confirmAccessAction()
                    assertEquals("voxkin.access.$action", t.calls[before].first)
                    assertEquals(id, t.calls[before].second.getString(if (action == "cancel") "inviteId" else "deviceId"))
                }
                val count = t.calls.size
                m.prepareAccessAction("approve", "unknown"); m.confirmAccessAction()
                m.prepareAccessAction("delete", phone); m.confirmAccessAction()
                assertEquals(count, t.calls.size)
            } finally { m.disconnect() }
        }
    }

    @Test fun failedMutationRequiresRefreshBeforeAnotherConfirmation() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val t = Fake(); val m = fixture(t)
            try {
                m.refreshAccess(); m.prepareAccessAction("approve", phone)
                t.fail = true
                m.confirmAccessAction()
                assertFalse(m.accessLoaded); assertFalse(m.busy)
                assertTrue(m.status.contains("outcome may be unknown"))
                val count = t.calls.size
                m.prepareAccessAction("approve", phone); m.confirmAccessAction()
                assertEquals(count, t.calls.size)
                assertEquals("", m.shareInvitation)
            } finally { m.disconnect() }
        }
    }

    @Test fun disconnectAndRefreshInvalidateOpenConfirmation() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val t = Fake(); val m = fixture(t)
            try {
                m.refreshAccess(); m.prepareAccessAction("approve", phone)
                m.refreshAccess(); m.confirmAccessAction()
                assertEquals(2, t.calls.size)
                m.prepareAccessAction("approve", phone)
                m.disconnect(); m.confirmAccessAction()
                assertEquals(2, t.calls.size)
            } finally { m.disconnect() }
        }
    }

    @Test fun expiredInvitationsLoseTheirShareSecret() {
        val inst = InstrumentationRegistry.getInstrumentation()
        lateinit var m: VoiceModel
        inst.runOnMainSync {
            val t = Fake(); t.expiry = System.currentTimeMillis() + 250
            m = fixture(t)
            m.refreshAccess(); m.inviteLabel = "New phone"; m.prepareAccessAction("invite"); m.confirmAccessAction()
            assertTrue(m.shareInvitation.isNotEmpty())
        }
        Thread.sleep(500)
        inst.runOnMainSync {
            try { assertEquals("", m.shareInvitation); assertEquals(0L, m.invitationExpiresAt) }
            finally { m.disconnect() }
        }
    }

    @Test fun lateListAfterDisconnectCannotRestoreAccessData() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val response = CompletableDeferred<JSONObject>()
        lateinit var m: VoiceModel
        inst.runOnMainSync {
            m = fixture(object : Transport {
                override suspend fun request(method: String, params: JSONObject): JSONObject = withContext(NonCancellable) { response.await() }
                override fun close() {}
            })
            m.refreshAccess()
            m.disconnect()
            response.complete(obj("agents" to JSONArray(listOf("guest")), "devices" to JSONArray(), "invites" to JSONArray()))
        }
        inst.waitForIdleSync()
        inst.runOnMainSync {
            assertFalse(m.accessLoaded); assertTrue(m.accessAgents.isEmpty()); assertFalse(m.busy)
        }
    }

    @Test fun phoneIdIsEndpointScopedAndForgetRemovesOnlySelectedId() {
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.runOnMainSync {
            val t = Fake(); val m = fixture(t); m.disconnect(); m.restricted = true
            val store = SecureStore(ApplicationProvider.getApplicationContext())
            val key = "phone-id:guest:${m.gateway}"
            val other = "$key-other"
            store.put(key, phone); store.put(other, invite)
            try {
                assertEquals(phone, m.enrolledPhoneId)
                m.forget()
                assertEquals("", m.enrolledPhoneId)
                assertEquals(invite, store.get(other))
            } finally { store.remove(key); store.remove(other) }
        }
    }
}
