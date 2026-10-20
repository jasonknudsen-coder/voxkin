package studio.voxkin.android

import java.util.Base64
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    @Test
    fun ownerSetupRejectsHttpsAndEndpointsCanonicalize() {
        assertTrue(
            runCatching {
                    Protocol.setup("voxkin://setup#gateway=https://example.com&token=synthetic")
                }
                .isFailure
        )
        assertEquals(
            "wss://example.com",
            Protocol.endpoint("wss://EXAMPLE.com:443/", true).toString(),
        )
        assertEquals(
            "wss://example.com",
            Protocol.endpoint("https://example.com/", true).toString(),
        )
    }

    @Test
    fun finalSpeechRequiresAssistantRole() {
        assertEquals("", Protocol.finalText(obj("role" to "user", "content" to "do not speak")))
        assertEquals("", Protocol.finalText(obj("content" to "missing role")))
        assertEquals(
            "",
            Protocol.finalText(
                obj("role" to "assistant", "channel" to "analysis", "content" to "private")
            ),
        )
        assertEquals(
            "",
            Protocol.finalText(
                obj("role" to "assistant", "errorMessage" to "bad", "content" to "error")
            ),
        )
        assertEquals("safe", Protocol.finalText(obj("role" to "assistant", "content" to "safe")))
    }

    @Test
    fun longSpeechChunksPreserveWordsAndRespectProviderBound() {
        val words = (1..2400).joinToString(" ") { "word$it" }
        val chunks = SpeechPlan.chunks(words)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 4000 && it.isNotBlank() })
        assertEquals(words, chunks.joinToString(" "))
        assertTrue(SpeechPlan.chunks(" ").isEmpty())
        val emoji = "a".repeat(3999) + "😀" + "b".repeat(5000)
        assertEquals(emoji, SpeechPlan.chunks(emoji).joinToString(""))
        assertTrue(SpeechPlan.chunks(emoji).all { !Character.isHighSurrogate(it.last()) })
    }

    @Test
    fun secureEndpointsOnly() {
        listOf(
                "ws://example.com",
                "https://example.com",
                "wss://user:password@example.com",
                "wss://example.com?token=x",
                "wss://example.com#secret",
            )
            .forEach { assertTrue(runCatching { Protocol.endpoint(it) }.isFailure) }
        assertEquals("example.com", Protocol.endpoint("wss://example.com").host)
        assertTrue(runCatching { Protocol.endpoint("wss://example.com/path", true) }.isFailure)
    }

    @Test
    fun invitationsAreStagedAndStrict() {
        val token = "a".repeat(32)
        val parsed = Protocol.setup("voxkin://join#gateway=wss%3A%2F%2Fexample.com&secret=$token")
        assertTrue(parsed.restricted)
        assertEquals(token, parsed.secret)
        listOf(
                "voxkin://join#gateway=wss://example.com&secret=$token&secret=$token",
                "voxkin://join?other=x#gateway=wss://example.com&secret=$token",
                "voxkin://setup#gateway=wss://example.com&token=%0A",
            )
            .forEach { assertTrue(runCatching { Protocol.setup(it) }.isFailure) }
    }

    @Test
    fun historyDoesNotShowReasoningOrToolPayloads() {
        assertEquals(
            "",
            Protocol.text(
                obj(
                    "role" to "assistant",
                    "channel" to "analysis",
                    "content" to "private reasoning",
                )
            ),
        )
        assertEquals(
            "",
            Protocol.text(
                obj(
                    "role" to "assistant",
                    "content" to
                        JSONArray()
                            .put(obj("type" to "text", "text" to "visible"))
                            .put(obj("type" to "toolCall", "text" to "private")),
                )
            ),
        )
        assertEquals(
            "hello",
            Protocol.text(
                obj(
                    "role" to "assistant",
                    "content" to JSONArray().put(obj("type" to "text", "text" to "hello")),
                )
            ),
        )
    }

    @Test
    fun historyOwnershipIsAgentAndUuidBound() {
        val id = "11111111-2222-4333-8444-555555555555"
        assertTrue(Protocol.owns("agent:guest:voxkin:$id", "guest"))
        assertFalse(Protocol.owns("agent:other:voxkin:$id", "guest"))
        assertFalse(Protocol.owns("agent:guest:voxkin:not-id", "guest"))
    }

    @Test
    fun androidSignatureMatchesDeclaredClientAndDevice() {
        val i = Identity(ByteArray(32) { it.toByte() })
        val scopes = listOf("operator.read", "operator.write")
        val d = i.device("challenge", 1234, "bootstrap", scopes)
        val raw = Base64.getUrlDecoder().decode(d.getString("publicKey"))
        assertEquals(digest(raw), d.getString("id"))
        val bytes =
            listOf(
                    "v3",
                    i.id,
                    "openclaw-android",
                    "ui",
                    "operator",
                    scopes.joinToString(","),
                    "1234",
                    "bootstrap",
                    "challenge",
                    "android",
                    "phone",
                )
                .joinToString("|")
                .toByteArray()
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(raw, 0))
        verifier.update(bytes, 0, bytes.size)
        assertTrue(
            verifier.verifySignature(Base64.getUrlDecoder().decode(d.getString("signature")))
        )
    }
}
