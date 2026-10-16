import XCTest
import CryptoKit
@testable import VoiceCore

final class VoiceCoreTests: XCTestCase {
    func testSpeechStripsRenderedMarkdownAroundLabelsAndColons() {
        XCTAssertEqual(SpeechText.prepare("- **First:** about 1–3 days.\n- **Second**: usually 1–3 days."), "First: about 1–3 days.\nSecond: usually 1–3 days.")
        XCTAssertEqual(SpeechText.prepare("**Bold**, *gentle*, __strong__, and `code`."), "Bold, gentle, strong, and code.")
        XCTAssertEqual(SpeechText.prepare("Read [the guide](https://example.com)."), "Read the guide.")
        XCTAssertEqual(SpeechText.prepare("2 * 3 = 6; time 12:30; unfinished **"), "2 * 3 = 6; time 12:30; unfinished **")
    }
    func testSpeechDashesBecomePausesWithoutChangingWords() {
        XCTAssertEqual(SpeechText.prepare("Hello—there. Yes — absolutely. Fine -- really."), "Hello, there. Yes, absolutely. Fine, really.")
        XCTAssertEqual(SpeechText.prepare("Hola — ¿cómo estás?"), "Hola, ¿cómo estás?")
        XCTAssertEqual(SpeechText.prepare("Done. — Next."), "Done. Next.")
    }
    func testSpeechListMarkersAndSeparatorsAreNotSpoken() {
        XCTAssertEqual(SpeechText.prepare("- First\n  - Second\n---\n• Third"), "First\nSecond\n\nThird")
        XCTAssertEqual(SpeechText.prepare("---"), "")
    }
    func testSpeechKeepsMeaningfulHyphensAndNumbers() {
        let text = "well-known, on-device, -5 degrees, −3, 3–5, 2025-2026, 5 - 3, x - y, 3 – 5, Monday–Friday, --flag"
        XCTAssertEqual(SpeechText.prepare(text), text)
    }

    func testEndpointRejectsInsecureAndCredentialBearingURLs() throws {
        for url in ["ws://gateway", "http://gateway", "wss://u:p@gateway", "wss://gateway?token=secret", "wss://gateway#x", "", "wss://"] { XCTAssertThrowsError(try Endpoint.validate(url), url) }
        XCTAssertEqual(try Endpoint.validate("wss://gateway.example/path").scheme, "wss")
    }
    func testSignatureBindsChallengeTokenAndScopes() throws {
        let identity = try DeviceIdentity(raw: Data(repeating: 7, count: 32))
        let scopes = ["operator.read", "operator.write"]
        let device = try identity.device(nonce: "synthetic-nonce", timestamp: 123, token: "synthetic-token", scopes: scopes)
        var encoded = device["signature"] as! String
        encoded = encoded.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        encoded += String(repeating: "=", count: (4 - encoded.count % 4) % 4)
        let signature = try XCTUnwrap(Data(base64Encoded: encoded))
        let publicKey = try Curve25519.Signing.PrivateKey(rawRepresentation: identity.raw).publicKey
        let text = "v3|\(identity.id)|openclaw-ios|ui|operator|operator.read,operator.write|123|synthetic-token|synthetic-nonce|ios|iphone"
        XCTAssertTrue(publicKey.isValidSignature(signature, for: Data(text.utf8)))
        XCTAssertFalse(publicKey.isValidSignature(signature, for: Data(text.replacingOccurrences(of: "synthetic-nonce", with: "replayed-nonce").utf8)))
        XCTAssertEqual(identity.id.count, 64)
        XCTAssertThrowsError(try identity.device(nonce: "", timestamp: 123, token: "", scopes: scopes))
    }
    func testDisconnectInvalidatesLateSpeechAndRequiresNewTurn() throws {
        var gate = TurnGate(); gate.connected()
        let old = try gate.begin(session: "agent:a:test", run: "r1")
        gate.invalidate(.disconnected)
        XCTAssertFalse(gate.speaking(epoch: old))
        XCTAssertFalse(gate.accepts(session: "agent:a:test", run: "r1"))
        XCTAssertThrowsError(try gate.begin(session: "agent:a:test", run: "r2"))
        gate.connected(); XCTAssertNil(gate.runID)
    }
    func testCrossSessionAndDuplicateFinalFences() throws {
        var gate = TurnGate(); gate.connected()
        let epoch = try gate.begin(session: "a", run: "r")
        XCTAssertFalse(gate.accepts(session: "b", run: "r"))
        XCTAssertFalse(gate.accepts(session: "a", run: "other"))
        XCTAssertTrue(gate.accepts(session: "a", run: "r"))
        XCTAssertTrue(gate.speaking(epoch: epoch))
        XCTAssertFalse(gate.accepts(session: "a", run: "r"))
        XCTAssertFalse(gate.speaking(epoch: epoch))
    }
    func testStopCannotLetOldAudioIntoNextTurn() throws {
        var gate = TurnGate(); gate.connected()
        let first = try gate.begin(session: "a", run: "r1")
        gate.invalidate(.interrupted)
        let second = try gate.begin(session: "a", run: "r2")
        XCTAssertFalse(gate.speaking(epoch: first)); XCTAssertTrue(gate.speaking(epoch: second))
    }
    func testOnlyFinalAssistantTextIsSpoken() {
        XCTAssertNil(ChatText.final(["state": "delta", "message": ["role": "assistant", "content": "partial"]]))
        XCTAssertNil(ChatText.final(["state": "final", "message": ["role": "tool", "content": "private tool output"]]))
        XCTAssertEqual(ChatText.final(["state": "final", "message": ["role": "assistant", "content": [["type": "thinking", "text": "hidden"], ["type": "text", "text": "answer"]]]]), "answer")
    }
    func testAudioRejectsMalformedUnsupportedAndOversizeClips() throws {
        let good: [String: Any] = ["provider": "synthetic", "mimeType": "audio/wav", "audioBase64": Data([1,2]).base64EncodedString()]
        XCTAssertEqual(try AudioClip(payload: good).data.count, 2)
        for value: [String: Any] in [[:], good.merging(["mimeType": "audio/ogg"]) { _, b in b }, good.merging(["audioBase64": "invalid!"]) { _, b in b }, good.merging(["audioBase64": String(repeating: "A", count: 8_000_001)]) { _, b in b }] {
            XCTAssertThrowsError(try AudioClip(payload: value))
        }
    }
    @MainActor func testTenSyntheticTurnsAndNoReplayOnDisconnect() async throws {
        let mock = MockGateway()
        for index in 0..<10 {
            let event = expectation(description: "synthetic final \(index)")
            mock.onEvent = { name, payload in
                XCTAssertEqual(name, "chat"); XCTAssertEqual(payload["runId"] as? String, "r\(index)")
                XCTAssertNotNil(ChatText.final(payload)); event.fulfill()
            }
            _ = try await mock.request("chat.send", ["sessionKey": "agent:synthetic:test", "idempotencyKey": "r\(index)", "message": "synthetic"])
            await fulfillment(of: [event], timeout: 2)
            let clip = try AudioClip(payload: await mock.request("tts.speak", ["text": "synthetic"]))
            XCTAssertEqual(String(data: clip.data.prefix(4), encoding: .ascii), "RIFF")
        }
        let count = mock.calls.count
        mock.close()
        do { _ = try await mock.request("chat.send", [:]); XCTFail("Closed transport accepted request") } catch {}
        XCTAssertEqual(mock.calls.count, count)
    }
    @MainActor func testMockAbortDiscardsPendingCompletion() async throws {
        let mock = MockGateway(), event = expectation(description: "No stale completion"); event.isInverted = true
        mock.onEvent = { _, _ in event.fulfill() }
        _ = try await mock.request("chat.send", ["sessionKey": "a", "idempotencyKey": "r"])
        _ = try await mock.request("chat.abort", ["sessionKey": "a", "runId": "r"])
        await fulfillment(of: [event], timeout: 0.5)
    }
}

final class SpeechChunkTests: XCTestCase {
    func testOrdinaryReplyStartsWithSmallCompleteSentence() {
        let opening = "Here is a short opening answer you can hear promptly."
        let text = opening + " " + String(repeating: "More useful detail follows. ", count: 8)
        XCTAssertLessThan(text.count, 500)
        let chunks = SpeechChunks.split(text)
        XCTAssertEqual(chunks.first, opening)
        XCTAssertEqual(chunks.joined(), text)
        XCTAssertLessThan(chunks[0].count, text.count / 2)
    }

    func testBoundsAndExactUnicodePreservation() {
        for text in ["Short reply.", String(repeating: "Hello 🌲. This is a sentence! ", count: 100), String(repeating: "x", count: 4000)] {
            let chunks = SpeechChunks.split(text)
            XCTAssertEqual(chunks.joined(), text)
            XCTAssertTrue(chunks.allSatisfy { !$0.isEmpty && $0.count <= 700 })
            XCTAssertLessThanOrEqual(chunks.count, 15)
        }
    }
}
