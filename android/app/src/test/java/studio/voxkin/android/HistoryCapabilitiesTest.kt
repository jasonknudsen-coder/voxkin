package studio.voxkin.android

import org.junit.Assert.*
import org.junit.Test

class HistoryCapabilitiesTest {
    @Test fun ownerActionsRequireTheirActualMethods() {
        val patch = HistoryCapabilities.owner(listOf("sessions.patch"))
        assertTrue(patch.rename)
        assertFalse(patch.delete)
        assertTrue(patch.create)
        assertFalse(patch.list)
        assertFalse(patch.read)
        assertFalse(HistoryCapabilities.owner(listOf("sessions.delete")).delete)
        assertTrue(HistoryCapabilities.owner(listOf("sessions.patch", "sessions.delete")).delete)
        val read = HistoryCapabilities.owner(listOf("chat.history"))
        assertTrue(read.read)
        assertFalse(read.list)
    }
    @Test fun restrictedFlagsFailClosed() {
        assertEquals(HistoryCapabilities(), HistoryCapabilities.restricted(false, true))
        val read = HistoryCapabilities.restricted(true, false)
        assertTrue(read.read && read.list && read.create)
        assertFalse(read.rename || read.delete)
        assertTrue(HistoryCapabilities.restricted(true, true).delete)
    }
}
