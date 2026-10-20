package studio.voxkin.android
import org.junit.Assert.*
import org.junit.Test
class GatewayPairingTest {
    @Test fun canonicalIdsOnlyAndNoServerMessage() {
        val id = "7A56AA63-090B-48BF-A317-51C05201D72D"
        val e = GatewayErrors.owner(obj("code" to "NOT_PAIRED", "message" to "SECRET", "details" to obj("requestId" to id)))
        assertTrue(e is GatewayPairingRequired)
        assertEquals("openclaw devices approve ${id.lowercase()}", (e as GatewayPairingRequired).approvalCommand)
        assertFalse(e.message!!.contains("SECRET"))
        for (bad in listOf(null, "", "1-1-1-1-1", "$id; command", " $id", "$(secret)")) assertNull(GatewayPairingRequired(bad).approvalCommand)
    }
    @Test fun errorCodeFallbackAndMalformedDetails() {
        for (details in listOf(obj(),obj("code" to ""),obj("code" to 42),obj("requestId" to 123)))
            assertTrue(GatewayErrors.owner(obj("code" to "PAIRING_REQUIRED", "details" to details)) is GatewayPairingRequired)
        assertTrue(GatewayErrors.owner(obj("details" to obj("code" to "NOT_PAIRED"))) is GatewayPairingRequired)
        assertFalse(GatewayErrors.owner(obj("code" to "UNKNOWN", "message" to "SECRET")) is GatewayPairingRequired)
    }
}
