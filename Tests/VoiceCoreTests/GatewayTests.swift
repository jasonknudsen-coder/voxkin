import XCTest
@testable import VoiceCore

@MainActor final class ScriptedSocket: WireSocket {
    var currentURL: URL? = URL(string: "wss://synthetic.invalid")!
    var writes: [[String: Any]] = []
    var queued: [Data] = []
    var waiting: CheckedContinuation<Data, Error>?
    var reply = true
    var pairingDetails: [String:Any]?
    var reject = false
    var invalidChallenge = false
    var stopped = false
    func start() { emit(["type": "event", "event": "connect.challenge", "payload": ["nonce": "synthetic-nonce", "ts": invalidChallenge ? -1 : 123]]) }
    func read() async throws -> Data {
        if !queued.isEmpty { return queued.removeFirst() }
        if stopped { throw VoiceError.invalid("Closed") }
        return try await withCheckedThrowingContinuation { waiting = $0 }
    }
    func write(_ data: Data) async throws {
        let frame = try JSONSerialization.jsonObject(with: data) as! [String: Any]; writes.append(frame)
        guard reply else { return }
        if let details = pairingDetails { emit(["type":"res","id":frame["id"]!,"ok":false,"error":["code":"NOT_PAIRED","message":"SECRET","details":details]]); return }
        if reject { emit(["type": "res", "id": frame["id"]!, "ok": false, "error": ["code": "FORBIDDEN", "message": "synthetic-secret-must-not-escape"]]); return }
        let payload: [String: Any] = frame["method"] as? String == "connect" ? ["type": "hello-ok", "protocol": 4] : ["synthetic": true]
        emit(["type": "res", "id": frame["id"]!, "ok": true, "payload": payload])
    }
    func emit(_ object: [String: Any]) {
        let data = try! JSONSerialization.data(withJSONObject: object)
        if let waiting { self.waiting = nil; waiting.resume(returning: data) } else { queued.append(data) }
    }
    func stop() { stopped = true; waiting?.resume(throwing: VoiceError.invalid("Closed")); waiting = nil }
}
@MainActor final class GatewayTests: XCTestCase {
    func connect(_ socket: ScriptedSocket, timeout: UInt64 = 1_000_000_000) async throws -> Gateway {
        let gateway = Gateway(timeout: timeout, makeSocket: { _ in socket })
        _ = try await gateway.connect(url: URL(string: "wss://synthetic.invalid")!, identity: DeviceIdentity(), token: "synthetic-token", scopes: ["operator.read", "operator.write"])
        return gateway
    }
    func testChallengeSignedBeforeConnectAndResponseCorrelation() async throws {
        let socket = ScriptedSocket(); let gateway = try await connect(socket)
        let frame = try XCTUnwrap(socket.writes.first), params = frame["params"] as! [String: Any], device = params["device"] as! [String: Any]
        XCTAssertEqual(frame["method"] as? String, "connect"); XCTAssertEqual(device["nonce"] as? String, "synthetic-nonce"); XCTAssertEqual(device["signedAt"] as? Int, 123)
        let result = try await gateway.request("agents.list", [:]); XCTAssertEqual(result["synthetic"] as? Bool, true)
        XCTAssertEqual(socket.writes.count, 2); gateway.close()
    }
    func testInvalidChallengeDoesNotSendCredential() async throws {
        let socket = ScriptedSocket(); socket.invalidChallenge = true
        do { _ = try await connect(socket); XCTFail("Accepted invalid challenge") } catch {}
        XCTAssertTrue(socket.writes.isEmpty); XCTAssertTrue(socket.stopped)
    }
    func testRedirectDoesNotSendCredential() async throws {
        let socket = ScriptedSocket(); socket.currentURL = URL(string: "wss://other.invalid")
        do { _ = try await connect(socket); XCTFail("Accepted redirect") } catch {}
        XCTAssertTrue(socket.writes.isEmpty)
    }
    func testURLSessionHTTPSRepresentationCompletesHandshake() async throws {
        let socket = ScriptedSocket(); socket.currentURL = URL(string: "https://synthetic.invalid/")
        let gateway = try await connect(socket)
        XCTAssertEqual(socket.writes.first?["method"] as? String, "connect")
        gateway.close()
    }
    func testEndpointNormalizationRejectsChangedDestinations() async throws {
        let expected = URL(string: "wss://synthetic.invalid")!
        XCTAssertTrue(sameWebSocketEndpoint(URL(string: "https://SYNTHETIC.invalid:443/"), expected))
        for value in ["https://other.invalid/", "http://synthetic.invalid/", "ws://synthetic.invalid/",
                      "https://synthetic.invalid:8443/", "https://synthetic.invalid/other",
                      "https://synthetic.invalid/?q=1", "https://user@synthetic.invalid/",
                      "https://synthetic.invalid/#fragment"] {
            let socket = ScriptedSocket(); socket.currentURL = URL(string: value)
            do { _ = try await connect(socket); XCTFail("Accepted changed endpoint: \(value)") } catch {}
            XCTAssertTrue(socket.writes.isEmpty)
            XCTAssertTrue(socket.stopped)
        }
        XCTAssertFalse(sameWebSocketEndpoint(nil, expected))
        XCTAssertTrue(sameWebSocketEndpoint(URL(string: "https://synthetic.invalid/gateway?q=1"), URL(string: "wss://synthetic.invalid/gateway?q=1")!))
    }
    func testPairingFailurePreservesOnlyValidatedRequestID() async throws {
        let socket = ScriptedSocket(); socket.pairingDetails = ["code":"PAIRING_REQUIRED","requestId":"7a56aa63-090b-48bf-a317-51c05201d72d"]
        do { _ = try await connect(socket); XCTFail("Connected before approval") }
        catch let error as GatewayPairingRequired {
            XCTAssertEqual(error.approvalCommand,"openclaw devices approve 7a56aa63-090b-48bf-a317-51c05201d72d")
            XCTAssertFalse(error.localizedDescription.contains("SECRET"))
        }
        XCTAssertNil(GatewayPairingRequired(requestID:"$(secret)").approvalCommand)
        XCTAssertNil(GatewayPairingRequired(requestID:nil).approvalCommand)
    }
    func testDeniedScopeDoesNotLeakServerError() async throws {
        let socket = ScriptedSocket(); let gateway = try await connect(socket); socket.reject = true
        do { _ = try await gateway.request("chat.send", [:]); XCTFail("Accepted denial") }
        catch { XCTAssertTrue(error.localizedDescription.contains("FORBIDDEN")); XCTAssertFalse(error.localizedDescription.contains("synthetic-secret")) }
        gateway.close()
    }
    func testTimeoutIgnoresLateResponseAndNeverReplays() async throws {
        let socket = ScriptedSocket(); let gateway = try await connect(socket, timeout: 20_000_000); socket.reply = false
        do { _ = try await gateway.request("chat.send", [:]); XCTFail("Did not timeout") } catch { XCTAssertTrue(error.localizedDescription.contains("unknown")) }
        let sent = socket.writes.last!
        socket.emit(["type": "res", "id": sent["id"]!, "ok": true, "payload": [:]])
        socket.reply = true
        _ = try await gateway.request("agents.list", [:])
        XCTAssertEqual(socket.writes.filter { $0["method"] as? String == "chat.send" }.count, 1)
        gateway.close()
    }
    func testCancelledBeforeSubmissionNeverWrites() async throws {
        let socket = ScriptedSocket(); let gateway = try await connect(socket)
        let request = Task { try await gateway.request("chat.send", [:]) }
        request.cancel()
        do { _ = try await request.value; XCTFail("Cancelled request succeeded") } catch {}
        XCTAssertEqual(socket.writes.count, 1)
        gateway.close()
    }
    func testCancelledPendingRequestReturnsPromptlyWithoutReplay() async throws {
        let socket = ScriptedSocket(); let gateway = try await connect(socket); socket.reply = false
        let request = Task { try await gateway.request("chat.send", [:]) }
        for _ in 0..<10 { await Task.yield() }
        request.cancel()
        do { _ = try await request.value; XCTFail("Cancelled request succeeded") } catch { XCTAssertTrue(error is CancellationError) }
        XCTAssertLessThanOrEqual(socket.writes.filter { $0["method"] as? String == "chat.send" }.count, 1)
        gateway.close()
    }
    func testDisconnectFailsPendingAndDoesNotResend() async throws {
        let socket = ScriptedSocket(); let gateway = try await connect(socket); socket.reply = false
        let task = Task { try await gateway.request("chat.send", [:]) }
        for _ in 0..<10 { await Task.yield() }
        gateway.close()
        do { _ = try await task.value; XCTFail("Pending request survived close") } catch {}
        XCTAssertLessThanOrEqual(socket.writes.filter { $0["method"] as? String == "chat.send" }.count, 1)
    }
}
