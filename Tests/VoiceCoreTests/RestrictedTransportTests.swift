import XCTest
import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
@testable import VoiceCore

private final class RestrictedProtocolStub: URLProtocol {
    private static let lock = NSLock()
    private static var callback: ((RestrictedProtocolStub) -> Void)?
    static func install(_ callback: @escaping (RestrictedProtocolStub) -> Void) {
        lock.lock(); self.callback = callback; lock.unlock()
    }
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.lock.lock(); let callback = Self.callback; Self.lock.unlock()
        callback?(self)
    }
    override func stopLoading() {}
    func respond(_ body: [String: Any], status: Int = 200, headers: [String: String] = [:]) {
        let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: "HTTP/1.1", headerFields: headers)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: try! JSONSerialization.data(withJSONObject: body))
        client?.urlProtocolDidFinishLoading(self)
    }
    func json() -> [String: Any] {
        var data = request.httpBody ?? Data()
        if let stream = request.httpBodyStream {
            stream.open(); defer { stream.close() }
            var buffer = [UInt8](repeating: 0, count: 4096)
            while stream.hasBytesAvailable {
                let count = stream.read(&buffer, maxLength: buffer.count)
                if count <= 0 { break }
                data.append(contentsOf: buffer.prefix(count))
            }
        }
        return (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
    }
}

final class RestrictedTransportTests: XCTestCase {
    @MainActor private func gateway(token: String = "test-device-secret") throws -> RestrictedGateway {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [RestrictedProtocolStub.self]
        return try RestrictedGateway(url: URL(string: "wss://gateway.example:8443")!, token: token, configuration: config)
    }

    @MainActor func testExactHTTPSRouteAndBearerHeaderWithoutCredentialInBodyOrURL() async throws {
        RestrictedProtocolStub.install { stub in
            XCTAssertEqual(stub.request.url?.absoluteString, "https://gateway.example:8443/voxkin/v1")
            XCTAssertEqual(stub.request.httpMethod, "POST")
            XCTAssertEqual(stub.request.value(forHTTPHeaderField: "Authorization"), "Bearer test-device-secret")
            XCTAssertEqual(stub.request.value(forHTTPHeaderField: "Content-Type"), "application/json")
            XCTAssertEqual(stub.json()["op"] as? String, "hello")
            XCTAssertEqual(stub.json().count, 1)
            XCTAssertNil(stub.request.url?.query)
            stub.respond(["version": 1, "agentId": "alice"])
        }
        let client = try gateway(); defer { client.close() }
        try await client.connect()
        let agents = try await client.request("agents.list", [:])
        XCTAssertEqual((agents["agents"] as? [[String:String]])?.first?["id"], "alice")
    }

    @MainActor func testHistoryActionsRequireAdvertisedCapability() async throws {
        RestrictedProtocolStub.install { stub in
            XCTAssertEqual(stub.json()["op"] as? String,"hello")
            stub.respond(["version":1,"agentId":"alice","history":true])
        }
        let client=try gateway(); defer { client.close() }; try await client.connect()
        XCTAssertFalse(client.supportsHistoryActions)
        for method in ["voxkin.history.rename","voxkin.history.delete"] {
            do { _ = try await client.request(method,["conversationId":"not-sent"]); XCTFail("Unadvertised mutation") } catch { }
        }
    }
    @MainActor func testHistoryActionsUseRestrictedWireNotOperatorRPC() async throws {
        RestrictedProtocolStub.install { stub in
            let body=stub.json()
            switch body["op"] as? String {
            case "hello": stub.respond(["version":1,"agentId":"alice","history":true,"historyActions":true])
            case "history.rename":
                XCTAssertEqual(Set(body.keys),Set(["op","conversationId","title"]))
                XCTAssertEqual(body["title"] as? String,"Weekend plans"); stub.respond(["title":"Weekend plans"])
            case "history.delete":
                XCTAssertEqual(Set(body.keys),Set(["op","conversationId"])); stub.respond(["deleted":true])
            default: XCTFail("Unexpected wire operation"); stub.respond([:],status:403)
            }
        }
        let client=try gateway(); defer { client.close() }; try await client.connect()
        XCTAssertTrue(client.supportsHistoryActions)
        _ = try await client.request("voxkin.history.rename",["conversationId":"owned","title":"Weekend plans"])
        _ = try await client.request("voxkin.history.delete",["conversationId":"owned"])
    }

    @MainActor func testEnrollmentHasNoBearerAndOnlyExplicitRedemptionPayload() async throws {
        RestrictedProtocolStub.install { stub in
            XCTAssertNil(stub.request.value(forHTTPHeaderField: "Authorization"))
            XCTAssertNil(stub.request.url?.query)
            XCTAssertEqual(Set(stub.json().keys), Set(["op", "secret"]))
            XCTAssertEqual(stub.json()["op"] as? String, "redeem")
            stub.respond(["id": "device", "deviceSecret": "new-device-secret", "status": "pending"])
        }
        let client = try gateway(token: ""); defer { client.close() }
        let result = try await client.call(["op": "redeem", "secret": "invitation-secret"])
        XCTAssertEqual(result["status"] as? String, "pending")
    }

    @MainActor func testChatWireOmitsClientAgentAndSessionAndMapsFinalBackToLocalIdentity() async throws {
        let final = expectation(description: "final mapped event")
        RestrictedProtocolStub.install { stub in
            let body = stub.json()
            XCTAssertNil(body["agentId"]); XCTAssertNil(body["sessionKey"]); XCTAssertNil(body["deviceId"])
            switch body["op"] as? String {
            case "hello": stub.respond(["version": 1, "agentId": "alice"])
            case "send":
                XCTAssertEqual(Set(body.keys), Set(["op", "text", "requestId", "conversationId"]))
                XCTAssertEqual(body["text"] as? String, "Hola")
                XCTAssertEqual(body["requestId"] as? String, "local-request-000001")
                stub.respond(["runId": "server-owned-run"])
            case "poll":
                XCTAssertEqual(Set(body.keys), Set(["op", "runId"]))
                XCTAssertEqual(body["runId"] as? String, "server-owned-run")
                stub.respond(["runId": "server-owned-run", "state": "final", "text": "Buenos días"])
            default: XCTFail("Unexpected wire operation"); stub.respond([:], status: 403)
            }
        }
        let client = try gateway(); defer { client.close() }
        try await client.connect()
        client.onEvent = { name, event in
            XCTAssertEqual(name, "chat")
            XCTAssertEqual(event["runId"] as? String, "local-request-000001")
            XCTAssertEqual(event["sessionKey"] as? String, "agent:alice:voxkin:11111111-2222-4333-8444-555555555555")
            XCTAssertEqual(event["state"] as? String, "final")
            final.fulfill()
        }
        let result = try await client.request("chat.send", ["agentId": "alice", "message": "Hola", "idempotencyKey": "local-request-000001", "sessionKey": "agent:alice:voxkin:11111111-2222-4333-8444-555555555555"])
        XCTAssertEqual(result["runId"] as? String, "local-request-000001")
        await fulfillment(of: [final], timeout: 2)
    }

    @MainActor func testOperatorMethodsAndWrongAgentAreRejectedWithoutAnyNetworkFallback() async throws {
        RestrictedProtocolStub.install { stub in
            XCTAssertEqual(stub.json()["op"] as? String, "hello")
            stub.respond(["version": 1, "agentId": "alice"])
        }
        let client = try gateway(); defer { client.close() }
        try await client.connect()
        // If any unsupported request leaks to the stub it fails the op assertion above.
        for method in ["sessions.list", "chat.history", "voxkin.access.list", "tts.speak", "connect"] {
            do { _ = try await client.request(method, [:]); XCTFail("Unsupported method succeeded: \(method)") }
            catch {}
        }
        do {
            _ = try await client.request("chat.send", ["agentId": "main", "message": "private", "idempotencyKey": "local-request-000001", "sessionKey": "agent:main:main"])
            XCTFail("Wrong agent accepted")
        } catch {}
    }

    @MainActor func testHTTPFailureAndRedirectStatusesNeverBecomeSuccessfulResponses() async throws {
        for status in [301, 302, 401, 403, 429, 500] {
            RestrictedProtocolStub.install { stub in
                stub.respond(["version": 1, "agentId": "main"], status: status, headers: ["Location": "https://other.example/voxkin/v1"])
            }
            let client = try gateway()
            do { try await client.connect(); XCTFail("Accepted HTTP \(status)") }
            catch {}
            client.close()
        }
    }

    @MainActor func testAdvertisedOversizedResponseIsRejected() async throws {
        RestrictedProtocolStub.install { stub in
            stub.respond(["version": 1, "agentId": "alice"], headers: ["Content-Length": "9000001"])
        }
        let client = try gateway(); defer { client.close() }
        do { try await client.connect(); XCTFail("Accepted oversized response") }
        catch {}
    }

    @MainActor func testCloseDuringRequestPreventsSuccessfulCompletion() async throws {
        let began = expectation(description: "request began")
        RestrictedProtocolStub.install { _ in began.fulfill() /* Deliberately keep response pending. */ }
        let client = try gateway()
        let task = Task { try await client.connect() }
        await fulfillment(of: [began], timeout: 2)
        client.close()
        do { try await task.value; XCTFail("Closed connection completed successfully") }
        catch {}
    }
}
