package studio.voxkin.android

import org.junit.Assert.*
import org.junit.Test

class AccessPolicyTest {
    @Test fun adminIsExplicitAndDedicatedAgentsAndLabelsAreBounded() {
        assertEquals(listOf("operator.read", "operator.write"), AccessPolicy.scopes(false))
        assertEquals(listOf("operator.read", "operator.write", "operator.admin"), AccessPolicy.scopes(true))
        assertFalse(AccessPolicy.agent("main")); assertTrue(AccessPolicy.agent("guest-1"))
        assertFalse(AccessPolicy.agent("../guest")); assertFalse(AccessPolicy.label(" phone"))
        assertFalse(AccessPolicy.label("x".repeat(81))); assertFalse(AccessPolicy.label("line\nbreak"))
        assertTrue(AccessPolicy.label("Family phone"))
    }
    @Test fun sharedInvitationsUseTheValidatedSetupContract() {
        val secret = "a".repeat(43)
        val code = AccessPolicy.invitation("wss://example.com", secret)
        val decoded = Protocol.setup(code)
        assertTrue(decoded.restricted); assertEquals(secret, decoded.secret)
        assertEquals("wss://example.com", decoded.gateway)
        assertTrue(runCatching { AccessPolicy.invitation("ws://example.com", secret) }.isFailure)
        assertTrue(runCatching { AccessPolicy.invitation("wss://example.com", "short") }.isFailure)
    }
}
