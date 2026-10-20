package studio.voxkin.android

import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder

class SetupQrTest {
    private fun scan(text: String): String {
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 600, 600)
        val pixels = IntArray(bits.width * bits.height) { i -> if (bits[i % bits.width, i / bits.width]) 0xff000000.toInt() else 0xffffffff.toInt() }
        return MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bits.width, bits.height, pixels))),
            mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))).text
    }

    @Test fun actualQrDecoderRoundTripsOwnerAndRestrictedSetup() {
        val ownerSecret = "synthetic+token/with=punctuation"
        val owner = Protocol.setup(scan("voxkin://setup#gateway=wss%3A%2F%2Fexample.com&token=" + URLEncoder.encode(ownerSecret, "UTF-8")))
        assertFalse(owner.restricted)
        assertEquals(ownerSecret, owner.secret)
        val guest = Protocol.setup(scan("voxkin://join#gateway=https%3A%2F%2Fexample.com&secret=" + "a".repeat(32)))
        assertTrue(guest.restricted)
        assertEquals("https://example.com", guest.gateway)
    }

    @Test fun decodedWebLinksAndMalformedInvitationsAreNotSetup() {
        listOf("https://example.com", "voxkin://join#gateway=https://example.com&secret=short",
            "voxkin://setup#gateway=ws://example.com&token=synthetic",
            "voxkin://setup#gateway=wss://example.com&token=one&token=two").forEach {
            assertTrue(runCatching { Protocol.setup(scan(it)) }.isFailure)
        }
    }
}
