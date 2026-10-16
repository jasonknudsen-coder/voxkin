import XCTest
@testable import VoiceCore

final class RestrictedGatewayTests: XCTestCase {
    func testCameraAcceptanceIncludesOwnerAndRestrictedSetupOnly() {
        XCTAssertTrue(VoxkinSetupCode.accepts("voxkin://setup#gateway=wss%3A%2F%2Fexample.com&token=synthetic"))
        XCTAssertTrue(VoxkinSetupCode.accepts("voxkin://join#gateway=wss%3A%2F%2Fexample.com&secret=" + String(repeating:"A",count:43)))
        XCTAssertFalse(VoxkinSetupCode.accepts("https://example.com"))
        XCTAssertFalse(VoxkinSetupCode.accepts("voxkin://setup#gateway=wss%3A%2F%2Fexample.com&token="))
        XCTAssertFalse(VoxkinSetupCode.accepts("voxkin://join#gateway=wss%3A%2F%2Fexample.com&secret=short"))
    }

    func testOwnerSetupDecodesReservedTokenCharactersExactlyOnce() throws {
        let setup = try VoxkinOwnerSetup("voxkin://setup#gateway=wss%3A%2F%2Fexample.com&token=a%26b%25c%2Bd%3De")
        XCTAssertEqual(setup.gateway.absoluteString, "wss://example.com")
        XCTAssertEqual(setup.token, "a&b%c+d=e")
    }
    func testOwnerSetupRejectsUnsafeAndAmbiguousInput() {
        for raw in [
            "voxkin://setup#gateway=ws%3A%2F%2Fexample.com&token=abc",
            "voxkin://setup#gateway=wss%3A%2F%2Fexample.com%2Fpath&token=abc",
            "voxkin://setup#gateway=wss%3A%2F%2Fexample.com&token=",
            "voxkin://setup#gateway=wss%3A%2F%2Fexample.com&token=a%0Ab",
            "voxkin://setup#gateway=wss%3A%2F%2Fexample.com&token=a&token=b",
            "voxkin://setup?token=abc#gateway=wss%3A%2F%2Fexample.com&token=a",
            "voxkin://join#gateway=wss%3A%2F%2Fexample.com&token=a"
        ] { XCTAssertThrowsError(try VoxkinOwnerSetup(raw)) }
    }

    func testRestrictedRouteUsesOnlySameSecureOrigin() throws {
        XCTAssertEqual(try RestrictedGateway.route(URL(string:"wss://example.com:443")!).absoluteString,"https://example.com:443/voxkin/v1")
        for raw in ["http://example.com", "ws://example.com", "https://user:secret@example.com", "https://example.com/path", "https://example.com?token=secret", "https://example.com#fragment"] {
            XCTAssertThrowsError(try RestrictedGateway.route(URL(string:raw)!))
        }
    }
    func testInvitationFragmentParsesWithoutAutomaticNetworkRequest() throws {
        let secret = String(repeating:"A",count:43)
        let invite = try VoxkinInvitation("voxkin://join#gateway=wss%3A%2F%2Fexample.com&secret="+secret)
        XCTAssertEqual(invite.gateway.absoluteString,"wss://example.com")
        XCTAssertEqual(invite.secret,secret)
    }
    func testRejectsAmbiguousAndUnsafeInvitations() {
        let secret = String(repeating:"A",count:43)
        for raw in [
            "https://join#gateway=wss%3A%2F%2Fexample.com&secret="+secret,
            "voxkin://join?gateway=wss%3A%2F%2Fexample.com&secret="+secret,
            "voxkin://join#gateway=http%3A%2F%2Fexample.com&secret="+secret,
            "voxkin://join#gateway=wss%3A%2F%2Fexample.com&secret="+secret+"&secret="+secret,
            "voxkin://join#gateway=wss%3A%2F%2Fexample.com&secret=short",
            "voxkin://join#gateway=wss%3A%2F%2Fexample.com&secret="+secret+"&agentId=main"
        ] { XCTAssertThrowsError(try VoxkinInvitation(raw)) }
    }
    @MainActor func testRestrictedTransportRejectsOperatorMethodsWithoutNetwork() async throws {
        let client = try RestrictedGateway(url:URL(string:"wss://example.invalid")!,token:"unused")
        defer { client.close() }
        for method in ["config.set","sessions.list","sessions.messages.subscribe","tts.speak","voxkin.access.invite"] {
            do { _ = try await client.request(method,[:]); XCTFail("Should reject operator method") } catch {}
        }
    }
}
