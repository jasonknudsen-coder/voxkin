import XCTest
import Speech
import UIKit
import CarPlay
import AVFoundation
import Security
import VoiceCore
@testable import Voxkin

@MainActor final class ControlledGateway: GatewayTransport {
    var onEvent: ((String, [String: Any]) -> Void)?
    var onClose: (() -> Void)?
    var calls: [String] = []
    var localParams: [[String: Any]] = []
    var catalogFails = false
    var blockCatalog = false
    var pendingCatalog: CheckedContinuation<[String:Any], Error>?
    var blockLocalSpeech = false
    var localSpeech: CheckedContinuation<[String: Any], Error>?
    static let catalog: [String: Any] = ["version":1,"provider":"voxkin-local","local":true,"ready":true,"voices":[["id":"af_heart","name":"Heart","language":"en","locale":"en-US"],["id":"ef_dora","name":"Dora","language":"es","locale":"es-ES"]]]
    var speechTexts: [String] = []
    var blockAcceptance = false
    var pendingAcceptance: CheckedContinuation<[String: Any], Error>?
    var chatMessages: [String] = []
    var historyParams: [[String: Any]] = []
    var historyResult: [String: Any] = ["messages": []]
    var listResult: [String: Any] = ["sessions": []]
    var blockHistory = false
    var pendingHistory: CheckedContinuation<[String: Any], Error>?
    var historyFails = false
    var mutationParams: [[String: Any]] = []
    var mutationMethods: [String] = []
    var deleteFails = false
    var renameFails = false
    var blockMutation = false
    var pendingMutation: CheckedContinuation<[String: Any], Error>?
    var previewResult: [String: Any] = ["previews":[]]
    var blockProgress = false
    var pendingProgress: CheckedContinuation<[String: Any], Error>?
    var blockSecondSpeech = false
    var blockSubscription = false
    var pairingFailure = false
    var closeDuringAgentList = false
    var subscription: CheckedContinuation<[String: Any], Error>?
    var pendingSpeech: CheckedContinuation<[String: Any], Error>?
    var abort: CheckedContinuation<[String: Any], Error>?
    func request(_ method: String, _ params: [String: Any]) async throws -> [String: Any] {
        calls.append(method)
        if pairingFailure { throw GatewayPairingRequired(requestID:"7a56aa63-090b-48bf-a317-51c05201d72d") }
        if method == "sessions.preview" { historyParams.append(params); return previewResult }
        if method == "sessions.patch" || method == "sessions.delete" {
            mutationParams.append(params); mutationMethods.append(method)
            if deleteFails && method == "sessions.delete" || renameFails && params["label"] != nil { throw VoiceError.invalid("Rejected") }
            if blockMutation { return try await withCheckedThrowingContinuation { pendingMutation = $0 } }
            return ["ok":true,"deleted":true]
        }
        if method == "sessions.list" { historyParams.append(params); return listResult }
        if method == "chat.history" {
            historyParams.append(params)
            if historyFails { throw VoiceError.invalid("History denied") }
            if blockHistory { return try await withCheckedThrowingContinuation { pendingHistory = $0 } }
            return historyResult
        }
        if method == "voxkin.catalog" {
            if blockCatalog { return try await withCheckedThrowingContinuation { pendingCatalog = $0 } }
            if catalogFails { throw VoiceError.invalid("Unavailable") }
            return Self.catalog
        }
        if method == "voxkin.speak" {
            localParams.append(params)
            if blockLocalSpeech { return try await withCheckedThrowingContinuation { localSpeech = $0 } }
            return ["audioBase64":MockGateway.tone().base64EncodedString(),"provider":"voxkin-local","mimeType":"audio/wav","voice":params["voice"]!,"language":params["language"]!]
        }
        if method == "sessions.messages.subscribe" && blockSubscription { return try await withCheckedThrowingContinuation { subscription = $0 } }
        if method == "agents.list" { if closeDuringAgentList { onClose?() }; return ["agents": [["id": "synthetic"]]] }
        if method == "chat.abort" { return try await withCheckedThrowingContinuation { abort = $0 } }
        if method == "tts.speak" {
            speechTexts.append(params["text"] as? String ?? "")
            if blockProgress && params["text"] as? String == "Checking the forecast." { return try await withCheckedThrowingContinuation { pendingProgress = $0 } }
            if blockSecondSpeech && speechTexts.count == 2 { return try await withCheckedThrowingContinuation { pendingSpeech = $0 } }
            return ["audioBase64": MockGateway.tone().base64EncodedString(), "provider": "test", "mimeType": "audio/wav"]
        }
        if method == "chat.send" { chatMessages.append(params["message"] as? String ?? ""); if blockAcceptance { return try await withCheckedThrowingContinuation { pendingAcceptance = $0 } }; return ["runId": params["idempotencyKey"]!] }
        return [:]
    }
    func close() {}
    func terminal(_ active: (session: String, run: String)) {
        onEvent?("chat", ["sessionKey": active.session, "runId": active.run, "state": "aborted"])
    }
}
@MainActor final class ControlledCapture: SpeechCapturing {
    var text: ((String) -> Void)?
    var end: ((String?) -> Void)?
    var finishCount = 0
    var startCount = 0
    var locale: Locale?
    func start(locale: Locale, onText: @escaping (String) -> Void, onEnd: @escaping (String?) -> Void) async throws { startCount += 1; self.locale = locale; text = onText; end = onEnd }
    func finish() { finishCount += 1 }
    func stop() { text = nil; end = nil }
}
@MainActor final class VoiceModelTests: XCTestCase {
    func testCaptureWindowSeparatesIdleFromSpeechWithoutExtendingRepeatedWords() {
        var window = CaptureWindow()
        XCTAssertEqual(CaptureWindow.idleSeconds,60)
        XCTAssertNil(window.noteTranscript("  "))
        XCTAssertEqual(window.noteTranscript("Hello"),30)
        XCTAssertNil(window.noteTranscript("Hello again"))
        XCTAssertNil(window.noteTranscript(""))
        window = CaptureWindow()
        XCTAssertEqual(window.noteTranscript("New turn"),30)
    }

    func testPairingApprovalRemainsVisibleUntilExplicitRetry() async {
        let gateway = ControlledGateway(); gateway.pairingFailure = true
        let model = VoiceModel(restoreConnection:false,mockFactory:{ gateway })
        await model.connect()
        XCTAssertNotNil(model.pairingApproval); XCTAssertFalse(model.connected); XCTAssertFalse(model.busy)
        XCTAssertTrue(model.status.contains("Waiting for gateway approval"))
        let count = gateway.calls.count
        model.background(); XCTAssertNotNil(model.pairingApproval)
        await model.foreground(); XCTAssertEqual(gateway.calls.count,count)
        gateway.pairingFailure = false
        await model.connect(); XCTAssertTrue(model.connected); XCTAssertNil(model.pairingApproval)
        model.disconnect()
    }

    func testOwnerSetupOnlyStagesFieldsWithoutConnectingOrPersisting() throws {
        let model = VoiceModel(restoreConnection: false)
        let host = "setup-" + UUID().uuidString.lowercased() + ".invalid"
        model.invitationText = "voxkin://setup#gateway=wss%3A%2F%2F" + host + "&token=synthetic%26token"
        model.importOwnerSetup()
        XCTAssertEqual(model.endpoint, "wss://" + host)
        XCTAssertEqual(model.bootstrap, "synthetic&token")
        XCTAssertTrue(model.invitationText.isEmpty)
        XCTAssertFalse(model.connected); XCTAssertFalse(model.busy); XCTAssertFalse(model.requestAdmin)
        XCTAssertNil(try CredentialStore.load("bootstrap|" + model.endpoint))
    }
    func testOwnerSetupCannotReplaceSavedRestrictedCredential() throws {
        let target = "wss://setup-" + UUID().uuidString.lowercased() + ".invalid"
        try CredentialStore.save(Data("existing".utf8), "restricted|" + target)
        defer { try? CredentialStore.remove("restricted|" + target) }
        let model = VoiceModel(restoreConnection: false)
        var fragment = URLComponents(); fragment.queryItems = [URLQueryItem(name:"gateway",value:target), URLQueryItem(name:"token",value:"replacement")]
        model.invitationText = "voxkin://setup#" + fragment.percentEncodedQuery!
        model.importOwnerSetup()
        XCTAssertTrue(model.bootstrap.isEmpty); XCTAssertFalse(model.connected)
        XCTAssertTrue(model.status.contains("already has saved access"))
        XCTAssertEqual(try CredentialStore.load("restricted|" + target), Data("existing".utf8))
    }

    func testCaptureStopDuringPermissionNeverRequestsMicrophone() async {
        var authorization: CheckedContinuation<SFSpeechRecognizerAuthorizationStatus, Never>?
        var microphoneRequests = 0, callbacks = 0
        let capture = SpeechCapture(authorizeSpeech: { await withCheckedContinuation { authorization = $0 } },
            authorizeMicrophone: { microphoneRequests += 1; return true })
        let attempt = Task { @MainActor in
            do { try await capture.start(locale: .current, onText: { _ in callbacks += 1 }, onEnd: { _ in callbacks += 1 }); XCTFail("Canceled capture started") }
            catch { XCTAssertTrue(error is CancellationError) }
        }
        await eventually { authorization != nil }
        capture.stop(); authorization?.resume(returning: .authorized)
        await attempt.value
        XCTAssertEqual(microphoneRequests, 0); XCTAssertEqual(callbacks, 0)
    }
    func testOldPermissionAttemptCannotCancelNewAttempt() async {
        var authorizations: [CheckedContinuation<SFSpeechRecognizerAuthorizationStatus, Never>] = []
        var microphoneRequests = 0
        let capture = SpeechCapture(authorizeSpeech: { await withCheckedContinuation { authorizations.append($0) } },
            authorizeMicrophone: { microphoneRequests += 1; return false })
        let first = Task { @MainActor in
            do { try await capture.start(locale: .current, onText: { _ in }, onEnd: { _ in }); XCTFail() }
            catch { XCTAssertTrue(error is CancellationError) }
        }
        await eventually { authorizations.count == 1 }
        let second = Task { @MainActor in
            do { try await capture.start(locale: .current, onText: { _ in }, onEnd: { _ in }); XCTFail() }
            catch { XCTAssertFalse(error is CancellationError) }
        }
        await eventually { authorizations.count == 2 }
        authorizations[0].resume(returning: .authorized); await first.value
        authorizations[1].resume(returning: .authorized); await second.value
        XCTAssertEqual(microphoneRequests, 1)
    }
    func testTaskCancellationDuringMicrophonePermissionCannotStartCapture() async {
        var permission: CheckedContinuation<Bool, Never>?
        let capture = SpeechCapture(authorizeSpeech: { .authorized }, authorizeMicrophone: { await withCheckedContinuation { permission = $0 } })
        let attempt = Task { @MainActor in
            do { try await capture.start(locale: .current, onText: { _ in }, onEnd: { _ in }); XCTFail() }
            catch { XCTAssertTrue(error is CancellationError) }
        }
        await eventually { permission != nil }
        attempt.cancel(); permission?.resume(returning: true); await attempt.value
    }
    func testCarPlayControlsOnlyOfferApplicableActions() {
        typealias Coordinator = CarPlayCoordinator
        XCTAssertEqual(Coordinator.visibleActions(for: "ready", hasRun: false, canStop: false), [.start])
        XCTAssertEqual(Coordinator.visibleActions(for: "offline", hasRun: false, canStop: false), [.reconnect])
        for state in ["setup", "connecting", "paused", "phone"] {
            XCTAssertTrue(Coordinator.visibleActions(for: state, hasRun: false, canStop: false).isEmpty)
        }
        for state in ["listening", "finishing", "preparing", "speaking", "resuming"] {
            XCTAssertEqual(Coordinator.visibleActions(for: state, hasRun: false, canStop: true), [.stop])
        }
        XCTAssertEqual(Coordinator.visibleActions(for: "thinking", hasRun: true, canStop: true), [.stop, .cancel])
        // Local Stop must not hide remote Cancel while the agent is still running.
        XCTAssertEqual(Coordinator.visibleActions(for: "thinking", hasRun: true, canStop: false), [.cancel])
        XCTAssertTrue(Coordinator.visibleActions(for: "thinking", hasRun: false, canStop: false).isEmpty)
    }
    func testCarPlayReadyTemplateHasOnlyMicAndNoSetupButtons() throws {
        guard #available(iOS 26.4, *) else { return }
        let states = CarPlayCoordinator().makeVoiceStates()
        XCTAssertEqual(Set(states.map(\.identifier)).count, states.count)
        XCTAssertEqual(states.first { $0.identifier == "ready" }?.actionButtons.map(\.title), ["Start conversation"])
        XCTAssertEqual(states.count, 5)
        let identifiers = Set(states.map(\.identifier))
        for phase in ["setup", "connecting", "offline", "ready", "listening", "finishing", "thinking", "preparing", "speaking", "resuming", "paused", "phone"] {
            XCTAssertTrue(identifiers.contains(CarPlayCoordinator.templateState(for: phase)))
        }
        for state in states where state.identifier != "ready" {
            XCTAssertTrue(state.actionButtons.isEmpty)
        }
        for state in states {
            let image = try XCTUnwrap(state.image)
            XCTAssertEqual(image.size, CGSize(width: 128, height: 128))
            XCTAssertEqual(image.renderingMode, .alwaysTemplate)
        }
    }
    func testCarPlayArtworkVisualContactSheet() throws {
        let symbols = ["mic.fill", "waveform", "ellipsis", "speaker.wave.2.fill"]
        let images = try symbols.map { try XCTUnwrap(CarPlayCoordinator.statusImage($0)) }
        let sheet = UIGraphicsImageRenderer(size: CGSize(width: 640, height: 190)).image { context in
            UIColor(white: 0.06, alpha: 1).setFill(); context.fill(CGRect(x: 0, y: 0, width: 640, height: 190))
            for (index, image) in images.enumerated() {
                image.withTintColor(.white, renderingMode: .alwaysOriginal).draw(in: CGRect(x: index * 160 + 16, y: 12, width: 128, height: 128))
                (["Ready", "Listening", "Working", "Speaking"][index] as NSString).draw(at: CGPoint(x: index * 160 + 35, y: 155), withAttributes: [.foregroundColor: UIColor.white, .font: UIFont.systemFont(ofSize: 17)])
            }
        }
        let attachment = XCTAttachment(image: sheet); attachment.name = "CarPlay status artwork (not head-unit layout)"; attachment.lifetime = .keepAlways; add(attachment)
    }
    func testTapToTalkAcknowledgesButTypedTurnDoesNot() async throws {
        let (model, transport, capture) = await conversationModel()
        await model.record(); await eventually { model.acknowledgmentReady }
        capture.text?("Tomorrow weather"); model.finishAndSend(); capture.end?(nil)
        await eventually { model.timing.acknowledgment != nil }
        XCTAssertFalse(model.conversationMode); XCTAssertEqual(model.gate.state,.waiting)
        model.disconnect()
        let (typed, _, _) = await conversationModel()
        typed.draft = "Typed weather"; typed.send()
        for _ in 0..<10 { await Task.yield() }
        XCTAssertNil(typed.timing.acknowledgment); XCTAssertFalse(typed.isAcknowledging)
        typed.disconnect()
        XCTAssertFalse(transport.calls.contains("tts.speak"))
        XCTAssertFalse(transport.calls.contains("voxkin.speak"))
    }
    func testExplicitProgressIsScopedDeduplicatedAndNeverUsesDeltas() async throws {
        let (model, transport, capture) = await conversationModel()
        await model.record(); await eventually { model.acknowledgmentReady }
        capture.text?("Forecast"); model.finishAndSend(); capture.end?(nil)
        await eventually { model.timing.acknowledgment != nil }
        let run = try XCTUnwrap(model.activeRun)
        let message:[String:Any] = ["role":"assistant","content":"Checking the forecast.","openclawDeliveryMirror":["kind":"message-tool-source-reply","final":false]]
        var event:[String:Any] = ["sessionKey":run.session,"runId":"wrong","messageId":"progress-1","message":message]
        transport.onEvent?("session.message",event)
        transport.onEvent?("chat",["sessionKey":run.session,"runId":run.run,"state":"delta","deltaText":"Private partial text"])
        for _ in 0..<5 { await Task.yield() }
        XCTAssertTrue(transport.speechTexts.isEmpty)
        event["runId"] = run.run
        transport.onEvent?("session.message",event); transport.onEvent?("session.message",event)
        event["messageId"] = "progress-2"; transport.onEvent?("session.message",event)
        await eventually { transport.speechTexts.count == 1 }
        XCTAssertEqual(transport.speechTexts,["Checking the forecast."])
        XCTAssertEqual(model.gate.state,.waiting); XCTAssertNil(model.timing.firstAudio)
        model.stopPlayback(); XCTAssertFalse(model.isAcknowledging)
        model.disconnect()
    }
    func testPersistedCommentaryWithoutEnvelopeRunIDIsSpokenOnce() async throws {
        let (model, transport, capture) = await conversationModel()
        await model.record(); await eventually { model.acknowledgmentReady }
        capture.text?("Forecast"); model.finishAndSend(); capture.end?(nil)
        await eventually { model.timing.acknowledgment != nil }
        let run = try XCTUnwrap(model.activeRun)
        let text = "Checking the forecast."
        let message: [String: Any] = [
            "role": "assistant", "content": [["type": "text", "text": text]],
            "__openclaw": ["mirrorOrigin": "codex-app-server", "mirrorIdentity": "turn:commentary:item", "runId": run.run],
            "openclawStreamFallback": ["source": "segment", "itemId": "item", "replacementText": text]
        ]
        var event: [String: Any] = ["sessionKey": run.session, "messageId": "row-1", "message": message]
        transport.onEvent?("session.message", event)
        event["messageId"] = "row-2"
        transport.onEvent?("session.message", event)
        XCTAssertEqual(model.interim, text)
        await eventually { transport.speechTexts.count == 1 }
        XCTAssertEqual(transport.speechTexts, [text])
        XCTAssertTrue(model.reply.isEmpty)
        model.disconnect()
    }
    func testPersistedCommentaryRejectsWrongMissingAndConflictingRunIDs() async {
        let (model, transport) = await model()
        model.send(); let run = model.activeRun!
        for _ in 0..<5 { await Task.yield() }
        let base: [String: Any] = [
            "role": "assistant", "content": [["type": "text", "text": "Do not speak this."]],
            "openclawStreamFallback": ["source": "segment", "itemId": "item"]
        ]
        for (top, nested) in [(nil, "wrong"), (run.run, "wrong"), ("wrong", run.run), (nil, nil)] as [(String?, String?)] {
            var message = base
            if let nested { message["__openclaw"] = ["runId": nested] }
            var event: [String: Any] = ["sessionKey": run.session, "messageId": UUID().uuidString, "message": message,
                                        "lastRunId": run.run, "activeRunIds": [run.run]]
            if let top { event["runId"] = top }
            transport.onEvent?("session.message", event)
            XCTAssertTrue(model.interim.isEmpty)
        }
        XCTAssertTrue(transport.speechTexts.isEmpty)
        model.disconnect()
    }
    func testPersistedCommentaryRejectsFinalTerminalAndNonTextPayloads() async {
        let (model, transport) = await model()
        model.send(); let run = model.activeRun!
        for _ in 0..<5 { await Task.yield() }
        let base: [String: Any] = [
            "role": "assistant", "content": [["type": "text", "text": "Not interim speech."]],
            "openclawStreamFallback": ["source": "segment", "itemId": "item"]
        ]
        let invalid: [[String: Any]] = [
            ["phase": "final"], ["phase": "final_answer"], ["phase": "analysis"],
            ["__openclaw": ["runTerminal": true, "runId": run.run]],
            ["stopReason": "error"], ["stopReason": "aborted"], ["errorMessage": "Failed before completion"],
            ["content": [["type": "text", "text": "Final", "textSignature": "{\"v\":1,\"phase\":\"final_answer\",\"id\":\"final\"}"]]],
            ["content": [["type": "text", "text": "Analysis", "textSignature": "{\"v\":1,\"phase\":\"analysis\",\"id\":\"analysis\"}"]]],
            ["content": [["type": "text", "text": "Opening", "textSignature": "{\"v\":1,\"phase\":\"commentary\"}"], ["type": "text", "text": "Unclassified"]]],
            ["role": "toolResult"],
            ["content": [["type": "thinking", "thinking": "Private reasoning"]]],
            ["content": [["type": "text", "text": "Visible"], ["type": "thinking", "thinking": "Private"]]],
            ["content": [["type": "text", "text": "Visible"], ["type": "toolCall", "name": "exec"]]]
        ]
        for override in invalid {
            let message = base.merging(override) { _, new in new }
            transport.onEvent?("session.message", ["sessionKey": run.session, "runId": run.run,
                "messageId": UUID().uuidString, "message": message])
            XCTAssertTrue(model.interim.isEmpty, "Accepted unsafe commentary: \(override.keys.sorted())")
        }
        XCTAssertTrue(transport.speechTexts.isEmpty)
        model.disconnect()
    }
    func testFastFinalSkipsPendingProgressSynthesis() async throws {
        let (model, transport, capture) = await conversationModel()
        await model.record()
        capture.text?("Forecast"); model.finishAndSend(); capture.end?(nil)
        await eventually { model.timing.acknowledgment != nil }
        let run = try XCTUnwrap(model.activeRun)
        transport.onEvent?("session.message", ["sessionKey":run.session,"runId":run.run,"messageId":"progress","message":["role":"assistant","content":"Checking the forecast.","openclawDeliveryMirror":["kind":"message-tool-source-reply","final":false]]])
        // Let the progress task enter its grace window before the final arrives.
        for _ in 0..<10 { await Task.yield() }
        XCTAssertTrue(transport.speechTexts.isEmpty)
        transport.onEvent?("chat", ["sessionKey":run.session,"runId":run.run,"state":"final","message":["role":"assistant","content":"Sunny."]])
        await eventually { transport.speechTexts == ["Sunny."] }
        try? await Task.sleep(nanoseconds: 900_000_000)
        XCTAssertEqual(transport.speechTexts, ["Sunny."])
        XCTAssertFalse(model.isAcknowledging)
        model.disconnect()
    }
    func testLateProgressCannotSpeakAfterStopOrFinal() async throws {
        for final in [false,true] {
            let (model, transport, capture) = await conversationModel()
            transport.blockProgress = true
            await model.record(); await eventually { model.acknowledgmentReady }
            capture.text?("Weather"); model.finishAndSend(); capture.end?(nil)
            await eventually { model.timing.acknowledgment != nil }
            let run = try XCTUnwrap(model.activeRun)
            transport.onEvent?("session.message",["sessionKey":run.session,"runId":run.run,"messageId":"progress","message":["role":"assistant","content":"Checking the forecast.","openclawDeliveryMirror":["kind":"message-tool-source-reply","final":false]]])
            await eventually { transport.pendingProgress != nil }
            if final { transport.onEvent?("chat",["sessionKey":run.session,"runId":run.run,"state":"final","message":["role":"assistant","content":"Sunny."]]) }
            else { model.stopPlayback() }
            transport.pendingProgress?.resume(returning:["audioBase64":MockGateway.tone().base64EncodedString(),"provider":"test","mimeType":"audio/wav"]); transport.pendingProgress = nil
            for _ in 0..<20 { await Task.yield() }
            XCTAssertFalse(model.isAcknowledging)
            if final { XCTAssertEqual(model.reply,"Sunny.") }
            else { XCTAssertNil(model.timing.firstAudio) }
            model.disconnect()
        }
    }
    func testAcknowledgmentWaitsForAcceptanceAndDoesNotMaskAnswerTiming() async throws {
        let (model, transport, capture) = await conversationModel()
        transport.blockAcceptance = true
        await model.startConversation()
        await eventually { model.acknowledgmentReady }
        XCTAssertFalse(model.isAcknowledging)
        capture.text?("Weather please"); capture.end?(nil)
        await eventually { transport.pendingAcceptance != nil }
        XCTAssertNil(model.timing.acknowledgment)
        let run = try XCTUnwrap(model.activeRun)
        transport.pendingAcceptance?.resume(returning: ["runId": run.run]); transport.pendingAcceptance = nil
        await eventually { model.timing.acknowledgment != nil }
        XCTAssertEqual(model.gate.state, .waiting)
        XCTAssertNil(model.timing.firstAudio)
        transport.onEvent?("chat", ["sessionKey": run.session, "runId": run.run, "state": "final", "message": ["role": "assistant", "content": "It is sunny."]])
        XCTAssertFalse(model.isAcknowledging)
        await eventually { model.timing.firstAudio != nil }
        XCTAssertEqual(model.reply, "It is sunny.")
        XCTAssertNotNil(model.timing.endOfTurn)
        model.disconnect()
    }
    func testLateAcceptanceAfterFinalNeverPlaysAcknowledgment() async throws {
        let (model, transport, capture) = await conversationModel()
        transport.blockAcceptance = true
        await model.startConversation(); await eventually { model.acknowledgmentReady }
        capture.text?("Hi"); capture.end?(nil)
        await eventually { transport.pendingAcceptance != nil }
        let run = try XCTUnwrap(model.activeRun)
        transport.onEvent?("chat", ["sessionKey": run.session, "runId": run.run, "state": "final", "message": ["role": "assistant", "content": "Hello."]])
        transport.pendingAcceptance?.resume(returning: ["runId": run.run]); transport.pendingAcceptance = nil
        await eventually { model.timing.firstAudio != nil }
        XCTAssertNil(model.timing.acknowledgment)
        model.disconnect()
    }
    func testLocalCuePreparationRequiresNoSpeechRequestAndStopClearsIt() async {
        let (model, transport, _) = await conversationModel()
        await model.startConversation()
        XCTAssertTrue(model.acknowledgmentReady)
        XCTAssertFalse(transport.calls.contains("tts.speak"))
        XCTAssertFalse(transport.calls.contains("voxkin.speak"))
        model.endConversation()
        XCTAssertFalse(model.acknowledgmentReady); XCTAssertFalse(model.isAcknowledging)
        model.disconnect()
    }
    func testLocalCueDoesNotNeedSpeechProviderToAcceptTurn() async {
        let (model, transport, capture) = await conversationModel()
        transport.blockLocalSpeech = true
        await model.startConversation()
        capture.text?("Hello"); capture.end?(nil)
        await eventually { model.timing.acknowledgment != nil }
        XCTAssertEqual(transport.chatMessages.count, 1)
        XCTAssertEqual(model.gate.state, .waiting)
        XCTAssertFalse(transport.calls.contains("tts.speak"))
        XCTAssertFalse(transport.calls.contains("voxkin.speak"))
        model.disconnect()
    }
    func testStopAndConsentWithdrawalClearAcknowledgment() async {
        let (model, transport, capture) = await conversationModel()
        await model.startConversation(); await eventually { model.acknowledgmentReady }
        capture.text?("Hello"); capture.end?(nil)
        await eventually { model.timing.acknowledgment != nil }
        model.stopPlayback()
        XCTAssertFalse(model.isAcknowledging); XCTAssertFalse(model.acknowledgmentReady)
        XCTAssertNotNil(model.activeRun); XCTAssertEqual(transport.chatMessages.count, 1)
        model.disconnect()
        let (second, _, _) = await conversationModel()
        await second.startConversation(); await eventually { second.acknowledgmentReady }
        second.acceptedFallback = false
        XCTAssertFalse(second.acknowledgmentReady)
        second.disconnect()
    }
    func testSpanishVoiceUsesLocalNonverbalCueWithoutSynthesis() async throws {
        let transport = ControlledGateway(), capture = ControlledCapture()
        let prefs = UserDefaults(suiteName: UUID().uuidString)!
        let model = VoiceModel(restoreConnection: false, preferences: prefs, capture: capture,
            mockMethods: ["voxkin.catalog", "voxkin.speak"], mockFactory: { transport })
        await model.connect()
        await eventually { model.localVoices.count == 2 }
        model.selectVoice("ef_dora")
        await model.startConversation(); await eventually { model.acknowledgmentReady }
        XCTAssertTrue(transport.localParams.isEmpty)
        XCTAssertFalse(transport.calls.contains("tts.speak"))
        model.disconnect(); XCTAssertFalse(model.acknowledgmentReady)
    }
    func testDuplicateDictationUpdatesDoNotExtendPause() async {
        let (model, _, capture) = await conversationModel()
        await model.startConversation()
        capture.text?("Same words")
        for _ in 0..<10 {
            try? await Task.sleep(nanoseconds: 10_000_000)
            capture.text?("Same words")
        }
        XCTAssertEqual(capture.finishCount, 1)
        model.disconnect()
    }
    func testAcknowledgmentCanBeDisabledWithoutSynthesis() async {
        let (model, transport, capture) = await conversationModel()
        model.spokenAcknowledgment = false
        await model.startConversation()
        capture.text?("Hello"); capture.end?(nil)
        await eventually { transport.chatMessages.count == 1 }
        XCTAssertFalse(transport.calls.contains("tts.speak"))
        XCTAssertFalse(transport.calls.contains("voxkin.speak"))
        XCTAssertNil(model.timing.acknowledgment)
        model.disconnect()
    }
    func testAcknowledgmentTimingIsSeparateAndResets() {
        var timing = TurnTiming()
        timing.sent(at: 100, transcription: 0.2, endOfTurn: 2.2)
        timing.acknowledged(at: 100.5); timing.acknowledged(at: 101)
        XCTAssertEqual(timing.acknowledgment, 0.5); XCTAssertNil(timing.firstAudio)
        timing.final(at: 103); timing.speechReceived(at: 104); timing.audio(at: 104.2)
        XCTAssertEqual(timing.firstAudio!, 4.2, accuracy: 0.001)
        timing.sent(at: 110)
        XCTAssertNil(timing.acknowledgment); XCTAssertNil(timing.endOfTurn)
    }
    func testCredentialAccessibilityMigratesExistingItemsWithoutChangingData() throws {
        for migrateBySave in [false, true] {
            let account = "migration-test|" + UUID().uuidString
            let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: "studio.builderofthings.voxkin.credentials", kSecAttrAccount as String: account,
                kSecAttrSynchronizable as String: false]
            defer { try? CredentialStore.remove(account) }
            let original = Data("synthetic-migration-value".utf8)
            var old = query
            old[kSecValueData as String] = original
            old[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            XCTAssertEqual(SecItemAdd(old as CFDictionary, nil), errSecSuccess)
            if migrateBySave { try CredentialStore.save(original, account) }
            else { XCTAssertEqual(try CredentialStore.load(account), original) }
            var attributes = query
            attributes[kSecReturnAttributes as String] = true
            var item: CFTypeRef?
            XCTAssertEqual(SecItemCopyMatching(attributes as CFDictionary, &item), errSecSuccess)
            let result = try XCTUnwrap(item as? [String: Any])
            XCTAssertEqual(result[kSecAttrAccessible as String] as? String, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly as String)
            XCTAssertEqual(try CredentialStore.load(account), original)
        }
    }
    func testCarPlayActivationReconnectsWithoutStartingVoice() async {
        let transport = ControlledGateway()
        let capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, capture: capture, mockFactory: { transport })
        let coordinator = CarPlayCoordinator()
        coordinator.attach(model: model)
        XCTAssertFalse(model.connected)
        coordinator.sceneBecameActive()
        for _ in 0..<100 where !model.connected { await Task.yield() }
        XCTAssertTrue(model.connected)
        XCTAssertEqual(capture.startCount, 0)
        XCTAssertFalse(transport.calls.contains("chat.send"))
        coordinator.suspend(); model.disconnect()
        coordinator.sceneBecameActive()
        for _ in 0..<100 where !model.connected { await Task.yield() }
        XCTAssertTrue(model.connected)
        XCTAssertEqual(capture.startCount, 0)
        coordinator.suspend(); model.disconnect()
    }
    func testUnlockNotificationReconnectsActiveCarWithoutCapture() async {
        let transport = ControlledGateway()
        let capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, capture: capture, mockFactory: { transport })
        let coordinator = CarPlayCoordinator()
        coordinator.attach(model: model); coordinator.sceneBecameActive()
        for _ in 0..<100 where !model.connected { await Task.yield() }
        XCTAssertTrue(model.connected)
        model.disconnect()
        NotificationCenter.default.post(name: UIApplication.protectedDataDidBecomeAvailableNotification, object: nil)
        for _ in 0..<100 where !model.connected { await Task.yield() }
        XCTAssertTrue(model.connected)
        XCTAssertEqual(capture.startCount, 0)
        XCTAssertFalse(transport.calls.contains("chat.send"))
        coordinator.suspend(); model.disconnect()
        NotificationCenter.default.post(name: UIApplication.protectedDataDidBecomeAvailableNotification, object: nil)
        for _ in 0..<20 { await Task.yield() }
        XCTAssertFalse(model.connected)
    }
    func testCarPlayActivationBeforeModelAttachmentReconnects() async {
        let transport = ControlledGateway()
        let capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, capture: capture, mockFactory: { transport })
        let coordinator = CarPlayCoordinator()
        coordinator.sceneBecameActive()
        coordinator.attach(model: model)
        for _ in 0..<100 where !model.connected { await Task.yield() }
        XCTAssertTrue(model.connected)
        XCTAssertEqual(capture.startCount, 0)
        coordinator.suspend(); model.disconnect()
    }
    func testCarPlayAttachAndSceneActivationNeverStartMicrophone() async {
        let (model, transport, capture) = await conversationModel()
        let coordinator = CarPlayCoordinator()
        coordinator.attach(model:model); coordinator.sceneBecameActive()
        await Task.yield()
        XCTAssertEqual(capture.startCount,0); XCTAssertFalse(coordinator.ownsVoiceSession)
        XCTAssertFalse(transport.calls.contains("chat.send"))
        coordinator.suspend(); coordinator.sceneBecameActive()
        await Task.yield(); XCTAssertEqual(capture.startCount,0)
        model.disconnect()
    }
    func testCarPlaySuspensionStopsVoiceAndPreservesPhoneDraft() async {
        let (model, transport, capture) = await conversationModel()
        let coordinator = CarPlayCoordinator(); coordinator.attach(model:model)
        await model.startConversation(); capture.text?("Unsent phone words")
        coordinator.suspend()
        XCTAssertFalse(model.conversationMode); XCTAssertEqual(model.draft,"Unsent phone words")
        XCTAssertNil(capture.text); XCTAssertFalse(transport.calls.contains("chat.send"))
        coordinator.sceneBecameActive(); await Task.yield()
        XCTAssertEqual(capture.startCount,1)
        model.disconnect()
    }
    func testCarPlayDisconnectNeverReplaysOrRestartsCapture() async {
        let (model, transport, capture) = await conversationModel()
        let coordinator = CarPlayCoordinator(); coordinator.attach(model:model)
        await model.startConversation(); coordinator.disconnect()
        await Task.yield()
        XCTAssertFalse(coordinator.isConnected); XCTAssertFalse(coordinator.ownsVoiceSession)
        XCTAssertFalse(model.conversationMode); XCTAssertNil(capture.text)
        XCTAssertEqual(capture.startCount,1); XCTAssertFalse(transport.calls.contains("chat.send"))
        model.disconnect()
    }

    private func conversationModel() async -> (VoiceModel, ControlledGateway, ControlledCapture) {
        let transport = ControlledGateway(), capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, preferences: UserDefaults(suiteName: UUID().uuidString)!,
                               capture: capture, conversationPauseNanoseconds: 30_000_000,
                               conversationResumeNanoseconds: 30_000_000, mockFactory: { transport })
        await model.connect()
        return (model, transport, capture)
    }
    private func eventually(_ condition: () -> Bool, file: StaticString = #filePath, line: UInt = #line) async {
        for _ in 0..<250 {
            if condition() { return }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
        XCTAssertTrue(condition(), file: file, line: line)
    }
    func testConversationPauseFinalizesThenSendsFinalWordsExactlyOnce() async {
        let (model, transport, capture) = await conversationModel()
        await model.startConversation()
        XCTAssertTrue(model.conversationMode)
        XCTAssertEqual(capture.startCount, 1)
        capture.text?("Tell me a")
        await eventually { capture.finishCount == 1 }
        XCTAssertTrue(transport.chatMessages.isEmpty)
        let final = capture.end
        capture.text?("Tell me a joke")
        final?(nil); final?(nil)
        await eventually { transport.chatMessages.count == 1 }
        XCTAssertEqual(model.sentText, "Tell me a joke")
        XCTAssertEqual(capture.startCount, 1)
        XCTAssertEqual(capture.finishCount, 1)
        XCTAssertEqual(model.gate.state, .waiting)
        model.disconnect()
    }
    func testSpeechCleansDashesButDisplayedReplyStaysOriginal() async throws {
        let (model, transport, _) = await conversationModel()
        model.draft = "Hello"; model.send()
        await eventually { model.activeRun != nil }
        let run = try XCTUnwrap(model.activeRun)
        let reply = "Hello—friend.\n- **Welcome:** friend."
        transport.onEvent?("chat", ["sessionKey": run.session, "runId": run.run, "state": "final", "message": ["role": "assistant", "content": reply]])
        await eventually { !transport.speechTexts.isEmpty }
        XCTAssertEqual(model.reply, reply)
        XCTAssertEqual(transport.speechTexts, ["Hello, friend.\nWelcome: friend."])
        model.disconnect()
    }
    func testConversationRearmsOnlyAfterAllReplyChunksFinish() async throws {
        let (model, transport, capture) = await conversationModel()
        transport.blockSecondSpeech = true
        await model.startConversation()
        capture.text?("A long answer please"); capture.end?(nil)
        await eventually { transport.chatMessages.count == 1 }
        let run = try XCTUnwrap(model.activeRun)
        let reply = String(repeating: "A friendly voice tells a story. ", count: 35)
        transport.onEvent?("chat", ["sessionKey": run.session, "runId": run.run, "state": "final", "message": ["role": "assistant", "content": reply]])
        await eventually { transport.pendingSpeech != nil }
        // Let the first clip finish while the next is still unavailable.
        try? await Task.sleep(nanoseconds: 500_000_000)
        XCTAssertEqual(capture.startCount, 1)
        XCTAssertEqual(model.gate.state, .speaking)
        transport.pendingSpeech?.resume(returning: ["audioBase64": MockGateway.tone().base64EncodedString(), "provider": "test", "mimeType": "audio/wav"])
        transport.pendingSpeech = nil
        await eventually { capture.startCount == 2 }
        XCTAssertEqual(transport.speechTexts, SpeechChunks.split(reply))
        XCTAssertTrue(model.conversationMode)
        XCTAssertEqual(model.gate.state, .recording)
        XCTAssertTrue(model.draft.isEmpty)
        model.disconnect()
    }
    func testConversationExitPathsRejectLateRecognitionAndDoNotRestart() async {
        for exit in ["stop", "end", "background", "disconnect", "agent"] {
            let (model, transport, capture) = await conversationModel()
            await model.startConversation(); capture.text?("Do not send this")
            let lateText = capture.text, lateEnd = capture.end
            switch exit {
            case "stop": model.stopPlayback()
            case "end": model.endConversation()
            case "background": model.background(); await model.foreground()
            case "disconnect": model.disconnect()
            default: model.changeAgent()
            }
            lateText?("Late final words"); lateEnd?(nil)
            try? await Task.sleep(nanoseconds: 100_000_000)
            XCTAssertFalse(model.conversationMode, exit)
            XCTAssertEqual(capture.startCount, 1, exit)
            XCTAssertEqual(capture.finishCount, 0, exit)
            XCTAssertTrue(transport.chatMessages.isEmpty, exit)
            model.disconnect()
        }
    }
    func testConversationEmptyRecognitionAndRecognitionErrorExitWithoutSending() async {
        for recognitionError: String? in [nil, "Recognition unavailable"] {
            let (model, transport, capture) = await conversationModel()
            await model.startConversation()
            if recognitionError != nil { capture.text?("Incomplete words") }
            capture.end?(recognitionError)
            try? await Task.sleep(nanoseconds: 100_000_000)
            XCTAssertFalse(model.conversationMode)
            XCTAssertEqual(capture.startCount, 1)
            XCTAssertTrue(transport.chatMessages.isEmpty)
            model.disconnect()
        }
    }
    func testConversationEmptyOrFailedAgentReplyDoesNotRearm() async throws {
        for terminal in ["final", "error"] {
            let (model, transport, capture) = await conversationModel()
            await model.startConversation()
            capture.text?("Hello"); capture.end?(nil)
            await eventually { transport.chatMessages.count == 1 }
            let run = try XCTUnwrap(model.activeRun)
            transport.onEvent?("chat", ["sessionKey": run.session, "runId": run.run, "state": terminal, "message": ["role": "assistant", "content": "  "]])
            try? await Task.sleep(nanoseconds: 100_000_000)
            XCTAssertFalse(model.conversationMode)
            XCTAssertEqual(capture.startCount, 1)
            XCTAssertTrue(transport.speechTexts.isEmpty)
            model.disconnect()
        }
    }
    func testConversationInterruptReplyResumesCaptureAndDiscardsLateSpeech() async throws {
        let (model, transport, capture) = await conversationModel()
        transport.blockSecondSpeech = true
        await model.startConversation(); capture.text?("Tell a story"); capture.end?(nil)
        await eventually { transport.chatMessages.count == 1 }
        await model.interruptConversationReply()
        XCTAssertEqual(capture.startCount, 1, "Waiting for an agent is not interruptible playback")
        let run = try XCTUnwrap(model.activeRun)
        transport.onEvent?("chat", ["sessionKey": run.session, "runId": run.run, "state": "final", "message": ["role": "assistant", "content": String(repeating: "Tell a friendly story. ", count: 50)]])
        await eventually { transport.pendingSpeech != nil }
        await model.interruptConversationReply()
        XCTAssertTrue(model.conversationMode)
        XCTAssertEqual(capture.startCount, 2)
        XCTAssertEqual(model.gate.state, .recording)
        transport.pendingSpeech?.resume(returning: ["audioBase64": MockGateway.tone().base64EncodedString(), "provider": "test", "mimeType": "audio/wav"])
        transport.pendingSpeech = nil
        try? await Task.sleep(nanoseconds: 150_000_000)
        XCTAssertEqual(capture.startCount, 2)
        XCTAssertEqual(model.gate.state, .recording)
        XCTAssertEqual(transport.speechTexts.count, 2)
        model.disconnect()
    }
    func testConversationStopDuringResumeDelayPreventsMicrophoneReopening() async throws {
        let transport = ControlledGateway(), capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, capture: capture,
                               conversationPauseNanoseconds: 30_000_000,
                               conversationResumeNanoseconds: 500_000_000, mockFactory: { transport })
        await model.connect(); await model.startConversation()
        capture.text?("Hello"); capture.end?(nil)
        await eventually { transport.chatMessages.count == 1 }
        let run = try XCTUnwrap(model.activeRun)
        transport.onEvent?("chat", ["sessionKey": run.session, "runId": run.run, "state": "final", "message": ["role": "assistant", "content": "Hello back."]])
        await eventually { model.gate.state == .ready && model.timing.firstAudio != nil }
        model.endConversation()
        try? await Task.sleep(nanoseconds: 600_000_000)
        XCTAssertFalse(model.conversationMode)
        XCTAssertEqual(capture.startCount, 1)
        model.disconnect()
    }
    func testTypingDuringConversationResumeDelayPreservesDraftAndExitsMode() async throws {
        let transport = ControlledGateway(), capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, capture: capture,
                               conversationPauseNanoseconds: 30_000_000,
                               conversationResumeNanoseconds: 500_000_000, mockFactory: { transport })
        await model.connect(); await model.startConversation()
        capture.text?("Hello"); capture.end?(nil)
        await eventually { transport.chatMessages.count == 1 }
        let run = try XCTUnwrap(model.activeRun)
        transport.onEvent?("chat", ["sessionKey": run.session, "runId": run.run, "state": "final", "message": ["role": "assistant", "content": "Hello back."]])
        await eventually { model.gate.state == .ready && model.timing.firstAudio != nil }
        XCTAssertTrue(model.conversationMode)
        model.draft = "I want to type this one"
        await eventually { !model.conversationMode }
        XCTAssertEqual(capture.startCount, 1)
        XCTAssertEqual(model.draft, "I want to type this one")
        XCTAssertEqual(transport.chatMessages.count, 1)
        model.disconnect()
    }
    func testStopDuringConversationInterruptCooldownPreventsMicrophoneReopening() async throws {
        let transport = ControlledGateway(), capture = ControlledCapture()
        transport.blockSecondSpeech = true
        let model = VoiceModel(restoreConnection: false, capture: capture,
                               conversationPauseNanoseconds: 30_000_000,
                               conversationResumeNanoseconds: 500_000_000, mockFactory: { transport })
        await model.connect(); await model.startConversation()
        capture.text?("Tell a story"); capture.end?(nil)
        await eventually { transport.chatMessages.count == 1 }
        let run = try XCTUnwrap(model.activeRun)
        transport.onEvent?("chat", ["sessionKey": run.session, "runId": run.run, "state": "final", "message": ["role": "assistant", "content": String(repeating: "Tell a friendly story. ", count: 50)]])
        await eventually { transport.pendingSpeech != nil }
        let interruption = Task { await model.interruptConversationReply() }
        await eventually { model.gate.state == .ready }
        XCTAssertEqual(capture.startCount, 1, "Cooldown must keep the microphone off")
        model.endConversation()
        transport.pendingSpeech?.resume(returning: ["audioBase64": MockGateway.tone().base64EncodedString(), "provider": "test", "mimeType": "audio/wav"])
        transport.pendingSpeech = nil
        await interruption.value
        XCTAssertFalse(model.conversationMode)
        XCTAssertEqual(capture.startCount, 1)
        XCTAssertEqual(model.gate.state, .interrupted)
        model.disconnect()
    }
    func testConversationCannotStartWithUnavailableSavedVoice() async {
        let transport = ControlledGateway(), capture = ControlledCapture()
        let prefs = UserDefaults(suiteName: UUID().uuidString)!
        prefs.set("missing-voice", forKey: "test-local-voice")
        let model = await pluginModel(transport, prefs: prefs, capture: capture)
        XCTAssertFalse(model.voiceSelectionValid)
        await model.startConversation()
        XCTAssertFalse(model.conversationMode)
        XCTAssertEqual(capture.startCount, 0)
        model.disconnect()
    }
    func testConversationStartPreservesDraftAndRequiresConsentConnectionAndIdleState() async {
        let (model, transport, capture) = await conversationModel()
        model.draft = "Keep this draft"
        await model.startConversation()
        XCTAssertFalse(model.conversationMode); XCTAssertEqual(model.draft, "Keep this draft")
        model.draft = ""; model.acceptedFallback = false
        await model.startConversation()
        XCTAssertFalse(model.conversationMode); XCTAssertEqual(capture.startCount, 0)
        model.acceptedFallback = true
        model.draft = "Pending question"; model.send()
        await model.startConversation()
        XCTAssertFalse(model.conversationMode); XCTAssertEqual(capture.startCount, 0)
        model.disconnect()
        await model.startConversation()
        XCTAssertFalse(model.conversationMode); XCTAssertEqual(capture.startCount, 0)
        XCTAssertLessThanOrEqual(transport.chatMessages.count, 1)
    }
    func testSpanishDictationCandidatesPreferDeviceLanguageWithoutCrossLanguageFallback() {
        let result = DictationLocales.candidates(requested: Locale(identifier:"es-ES"),
            preferred:["en-US","es-MX"], supported:[Locale(identifier:"en-US"),Locale(identifier:"es-ES"),Locale(identifier:"es-MX")])
        XCTAssertEqual(result.map { $0.identifier },["es-MX","es-ES"])
        XCTAssertEqual(result.first { $0.identifier == "es-MX" }?.language.languageCode?.identifier,"es")
    }
    func testSpanishDictationCanUseAnotherRegionAndNeverEnglish() {
        let result=DictationLocales.candidates(requested:Locale(identifier:"es-ES"),preferred:["en-US"],supported:[Locale(identifier:"en-US"),Locale(identifier:"es-MX")])
        XCTAssertEqual(result.map { $0.identifier },["es-MX"])
        XCTAssertTrue(DictationLocales.candidates(requested:Locale(identifier:"es-ES"),preferred:["en-US"],supported:[Locale(identifier:"en-US")]).isEmpty)
    }
    func testDictationCandidateDeduplicationAndSetupGuidance() {
        let result=DictationLocales.candidates(requested:Locale(identifier:"en_US"),preferred:["en-US","en_US"],supported:[Locale(identifier:"en-US")])
        XCTAssertEqual(result.count,1)
        let message=DictationLocales.unavailableMessage(Locale(identifier:"es-ES"))
        XCTAssertTrue(message.contains("Keyboard")); XCTAssertTrue(message.contains("no cloud fallback"))
    }
    func pluginModel(_ transport: ControlledGateway, prefs: UserDefaults, capture: ControlledCapture? = nil) async -> VoiceModel {
        let model = VoiceModel(restoreConnection:false, preferences:prefs, capture:capture, mockMethods:["voxkin.catalog","voxkin.speak"], mockFactory:{transport})
        await model.connect(); for _ in 0..<10 { await Task.yield() }; return model
    }
    func testLocalSpanishSelectionSetsDictationPromptAndExplicitVoice() async throws {
        let prefs = UserDefaults(suiteName: UUID().uuidString)!, transport = ControlledGateway(), capture = ControlledCapture()
        let model = await pluginModel(transport, prefs:prefs, capture:capture)
        XCTAssertEqual(model.localVoices.count,2); XCTAssertEqual(model.selectedVoiceID, "")
        model.selectVoice("ef_dora"); await model.record()
        XCTAssertEqual(capture.locale?.identifier,"es-ES")
        capture.text?("Hola"); capture.end?(nil); model.send()
        for _ in 0..<10 { await Task.yield() }
        XCTAssertTrue(transport.chatMessages.first?.contains("Respond in Spanish") == true)
        XCTAssertEqual(model.sentText,"Hola")
        let run = try XCTUnwrap(model.activeRun)
        model.selectVoice("af_heart"); XCTAssertEqual(model.selectedVoiceID,"ef_dora")
        transport.onEvent?("chat",["sessionKey":run.session,"runId":run.run,"state":"final","message":["role":"assistant","content":"Hola, ¿cómo estás?"]])
        for _ in 0..<20 { await Task.yield() }
        XCTAssertEqual(transport.localParams.first?["voice"] as? String,"ef_dora")
        XCTAssertFalse(transport.calls.contains("tts.speak")); model.disconnect()
    }
    func testMissingSavedVoiceNeverSilentlyFallsBack() async {
        let prefs=UserDefaults(suiteName:UUID().uuidString)!, transport=ControlledGateway()
        prefs.set("ef_dora",forKey:"test-local-voice"); transport.catalogFails=true
        let model=await pluginModel(transport,prefs:prefs)
        model.draft="Hola"; model.send()
        XCTAssertFalse(model.voiceSelectionValid); XCTAssertNil(model.activeRun)
        XCTAssertEqual(model.draft,"Hola"); XCTAssertFalse(transport.calls.contains("chat.send"))
        model.selectVoice(""); XCTAssertTrue(model.voiceSelectionValid); model.disconnect()
    }
    func testPreviewIsNotChatAndStopRejectsLateAudio() async throws {
        let prefs=UserDefaults(suiteName:UUID().uuidString)!, transport=ControlledGateway()
        transport.blockLocalSpeech=true
        let model=await pluginModel(transport,prefs:prefs)
        model.selectVoice("ef_dora"); model.draft="Keep my draft"; model.previewVoice()
        for _ in 0..<10 { await Task.yield() }
        XCTAssertTrue(model.previewing); XCTAssertNil(model.activeRun)
        XCTAssertFalse(transport.calls.contains("chat.send")); XCTAssertEqual(model.draft,"Keep my draft")
        model.stopPlayback()
        transport.localSpeech?.resume(returning:["audioBase64":MockGateway.tone().base64EncodedString(),"provider":"voxkin-local","mimeType":"audio/wav","voice":"ef_dora","language":"es"]); transport.localSpeech=nil
        for _ in 0..<10 { await Task.yield() }
        XCTAssertFalse(model.previewing); XCTAssertEqual(model.gate.state,.interrupted); XCTAssertNil(model.timing.firstAudio)
        model.disconnect()
    }
    func testSlowCatalogDoesNotBlockConnectionAndLateCatalogIsDiscarded() async {
        let prefs=UserDefaults(suiteName:UUID().uuidString)!, transport=ControlledGateway()
        transport.blockCatalog=true
        let model=await pluginModel(transport,prefs:prefs)
        XCTAssertTrue(model.connected); XCTAssertFalse(model.busy)
        model.draft="Hello"; model.send()
        for _ in 0..<10 { await Task.yield() }
        XCTAssertEqual(transport.chatMessages.count,1)
        model.disconnect()
        transport.pendingCatalog?.resume(returning:ControlledGateway.catalog); transport.pendingCatalog=nil
        for _ in 0..<10 { await Task.yield() }
        XCTAssertTrue(model.localVoices.isEmpty); XCTAssertFalse(model.connected)
    }
    func testCatalogAcceptsBritishEnglishWithoutChangingSavedVoice() async throws {
        var catalog=ControlledGateway.catalog
        catalog["voices"]=[["id":"bf_emma","name":"Emma (UK)","language":"en","locale":"en-GB"]]
        let voices=try LocalVoice.catalog(catalog)
        XCTAssertEqual(voices.first?.locale,"en-GB")
        let prefs=UserDefaults(suiteName:UUID().uuidString)!, transport=ControlledGateway()
        prefs.set("af_heart",forKey:"test-local-voice")
        let model=await pluginModel(transport,prefs:prefs)
        XCTAssertEqual(model.selectedVoiceID,"af_heart");model.disconnect()
    }
    func testCatalogRejectsUnknownLanguageAndDuplicateVoices() throws {
        var catalog=ControlledGateway.catalog
        let rows=catalog["voices"] as! [[String:Any]]
        catalog["voices"]=[rows[0],rows[0]]; XCTAssertThrowsError(try LocalVoice.catalog(catalog))
        catalog["voices"]=[["id":"unknown","name":"Unknown","language":"de","locale":"de-DE"]]
        XCTAssertThrowsError(try LocalVoice.catalog(catalog))
    }
    func testHistoryScopedListAndBoundedMessagesNeverSpeak() async {
        let gateway = ControlledGateway()
        let key = VoxkinHistory.session(agent: "synthetic", id: UUID().uuidString.lowercased())
        gateway.listResult = ["sessions":[["key":key,"derivedTitle":"A saved chat"],["key":"agent:synthetic:discord:123","derivedTitle":"Not ours"]]]
        gateway.historyResult = ["messages":[["id":"1","role":"user","content":"Hello"],["id":"2","role":"assistant","content":"Hi"]],"hasMore":true,"nextOffset":50]
        let prefs = UserDefaults(suiteName: UUID().uuidString)!
        let model = VoiceModel(restoreConnection:false,preferences:prefs,mockMethods:["chat.history"],mockFactory:{gateway})
        await model.connect(); await model.loadConversations()
        XCTAssertEqual(model.conversations.map(\.id), [key])
        XCTAssertEqual(gateway.historyParams[0]["limit"] as? Int, 20)
        XCTAssertEqual(gateway.historyParams[0]["search"] as? String, "agent:synthetic:voxkin:")
        await model.openConversation(model.conversations[0])
        XCTAssertEqual(model.reply,"Hi"); XCTAssertEqual(model.sentText,"Hello")
        XCTAssertTrue(model.messagesHaveMore); XCTAssertTrue(model.historyReady)
        XCTAssertEqual(gateway.historyParams.last?["maxBytes"] as? Int, 128_000)
        XCTAssertTrue(gateway.speechTexts.isEmpty)
        gateway.historyResult = ["messages":[],"hasMore":false]
        await model.loadHistoryMessages(more:true)
        XCTAssertEqual(gateway.historyParams.last?["offset"] as? Int,50)
        model.disconnect()
    }
    func testBatchTitlesSkipTestsAndPreserveManualNames() async {
        let gateway = ControlledGateway(), id = VoxkinHistory.session(agent:"synthetic",id:UUID().uuidString.lowercased())
        gateway.listResult = ["sessions":[["key":id,"sessionId":"s","derivedTitle":"[Voxkin reply preference truncated"]]]
        gateway.previewResult = ["previews":[["key":id,"status":"ok","items":[["role":"user","text":"Test 1234"],["role":"assistant","text":"Hi"],["role":"user","text":"Weather in Highlands Ranch?"]]]]]
        let model = VoiceModel(restoreConnection:false,mockMethods:["chat.history","sessions.preview"],mockFactory:{gateway})
        await model.connect(); await model.loadConversations()
        XCTAssertEqual(model.conversations.first?.title,"Weather in Highlands Ranch?")
        XCTAssertEqual(gateway.historyParams.last?["limit"] as? Int,8)
        XCTAssertEqual(gateway.historyParams.last?["maxChars"] as? Int,800)
        gateway.listResult = ["sessions":[["key":id,"sessionId":"s","label":"My custom name"]]]
        await model.loadConversations()
        XCTAssertEqual(model.conversations.first?.title,"My custom name")
        XCTAssertEqual(gateway.calls.filter{$0 == "sessions.preview"}.count,1)
        XCTAssertTrue(gateway.speechTexts.isEmpty); model.disconnect()
    }
    func testTruncatedVoicePreferencesUseBoundedHistoryForTitle() async {
        let gateway = ControlledGateway(), id = VoxkinHistory.session(agent:"synthetic",id:UUID().uuidString.lowercased())
        let wrapper = "[Voxkin reply preference for this turn only: " + String(repeating:"voice guidance ", count:70)
        gateway.listResult = ["sessions":[["key":id,"sessionId":"s"]]]
        gateway.previewResult = ["previews":[["key":id,"status":"ok","items":[["role":"user","text":String(wrapper.prefix(797)) + "..."]]]]]
        gateway.historyResult = ["messages":[["role":"user","content":wrapper + "]\n\nIdeas for an Australia trip?"]]]
        let model = VoiceModel(restoreConnection:false,mockMethods:["chat.history","sessions.preview"],mockFactory:{gateway})
        await model.connect(); await model.loadConversations()
        XCTAssertEqual(model.conversations.first?.title,"Ideas for an Australia trip?")
        XCTAssertEqual(gateway.historyParams.last?["sessionKey"] as? String,id)
        XCTAssertEqual(gateway.historyParams.last?["limit"] as? Int,8)
        XCTAssertEqual(gateway.historyParams.last?["maxBytes"] as? Int,32_000)
        gateway.historyFails = true
        await model.loadConversations()
        XCTAssertEqual(model.conversations.first?.title,"Conversation")
        XCTAssertTrue(gateway.speechTexts.isEmpty)
        model.disconnect()
    }
    func testRenameAndDeleteUseExpectedIdentityAndResetSelectedConversation() async {
        let gateway = ControlledGateway(), id = VoxkinHistory.session(agent:"synthetic",id:UUID().uuidString.lowercased())
        let item = ConversationSummary(id:id,title:"Old",sessionID:"session-original")
        let model = VoiceModel(restoreConnection:false,mockMethods:["chat.history","sessions.patch","sessions.delete"],mockFactory:{gateway})
        await model.connect(); model.conversations=[item]; await model.openConversation(item)
        await model.renameConversation(item,to:"  New name  ")
        XCTAssertEqual(model.conversations.first?.title,"New name")
        XCTAssertEqual(gateway.mutationParams.first?["expectedSessionId"] as? String,"session-original")
        await model.deleteConversation(model.conversations[0])
        XCTAssertEqual(gateway.mutationMethods,["sessions.patch","sessions.patch","sessions.delete"])
        XCTAssertEqual(gateway.mutationParams[1]["archived"] as? Bool,true)
        XCTAssertEqual(gateway.mutationParams[2]["archivedOnly"] as? Bool,true)
        XCTAssertEqual(gateway.mutationParams[2]["deleteTranscript"] as? Bool,true)
        XCTAssertTrue(model.conversations.isEmpty); XCTAssertTrue(model.historySelection.isEmpty)
        XCTAssertTrue(model.historyReady); XCTAssertTrue(model.historyMessages.isEmpty); model.disconnect()
    }
    func testDeleteRefreshesFirstPageAndRetainsOlderPagination() async {
        let gateway = ControlledGateway()
        let item = ConversationSummary(id:VoxkinHistory.session(agent:"synthetic",id:UUID().uuidString.lowercased()),title:"Delete",sessionID:"s")
        gateway.listResult=["sessions":(0..<20).map { ["key":VoxkinHistory.session(agent:"synthetic",id:UUID().uuidString.lowercased()),"sessionId":"s\($0)","label":"Item \($0)"] }]
        let model = VoiceModel(restoreConnection:false,mockMethods:["chat.history","sessions.patch","sessions.delete"],mockFactory:{gateway})
        await model.connect(); model.conversations=[item]; model.historyHasMore=true
        await model.deleteConversation(item)
        XCTAssertEqual(model.conversations.count,20); XCTAssertTrue(model.historyHasMore)
        XCTAssertEqual(gateway.historyParams.last?["offset"] as? Int,0)
        model.disconnect()
    }
    func testAutomaticNamePersistsAfterRealExchangeAndExistingAutoNameWins() async {
        let gateway=ControlledGateway(), id=VoxkinHistory.session(agent:"synthetic",id:UUID().uuidString.lowercased())
        gateway.listResult=["sessions":[["key":id,"sessionId":"s"]]]
        gateway.previewResult=["previews":[["key":id,"status":"ok","items":[["role":"user","text":"Test 1234"],["role":"user","text":"A weekend hike"]]]]]
        let model=VoiceModel(restoreConnection:false,mockMethods:["chat.history","sessions.preview","sessions.patch","sessions.delete"],mockFactory:{gateway})
        await model.connect(); await model.loadConversations()
        XCTAssertEqual(gateway.mutationParams.first?["autoLabel"] as? String,"A weekend hike")
        gateway.listResult=["sessions":[["key":id,"sessionId":"s","autoLabel":"Stable title"]]]
        await model.loadConversations()
        XCTAssertEqual(model.conversations.first?.title,"Stable title")
        XCTAssertEqual(gateway.calls.filter{$0 == "sessions.preview"}.count,1)
        model.disconnect()
    }
    func testDeleteFailureRestoresArchiveAndKeepsSelection() async {
        let gateway = ControlledGateway(); gateway.deleteFails=true
        let item = ConversationSummary(id:VoxkinHistory.session(agent:"synthetic",id:UUID().uuidString.lowercased()),title:"Keep",sessionID:"s")
        let model = VoiceModel(restoreConnection:false,mockMethods:["chat.history","sessions.patch","sessions.delete"],mockFactory:{gateway})
        await model.connect(); model.conversations=[item]; await model.openConversation(item)
        await model.deleteConversation(item)
        XCTAssertEqual(model.conversations,[item]); XCTAssertEqual(model.historySelection,item.id)
        XCTAssertEqual(gateway.mutationMethods,["sessions.patch","sessions.delete","sessions.patch"])
        XCTAssertEqual(gateway.mutationParams.last?["archived"] as? Bool,false)
        XCTAssertFalse(model.historyError.isEmpty); model.disconnect()
    }
    func testLateRenameDoesNotRepopulateAfterDisconnect() async {
        let gateway = ControlledGateway(); gateway.blockMutation=true
        let item = ConversationSummary(id:VoxkinHistory.session(agent:"synthetic",id:UUID().uuidString.lowercased()),title:"Old",sessionID:"s")
        let model = VoiceModel(restoreConnection:false,mockMethods:["chat.history","sessions.patch","sessions.delete"],mockFactory:{gateway})
        await model.connect(); model.conversations=[item]
        let task = Task { await model.renameConversation(item,to:"New") }
        for _ in 0..<100 { if gateway.pendingMutation != nil { break }; await Task.yield() }
        XCTAssertNotNil(gateway.pendingMutation); model.disconnect()
        gateway.pendingMutation?.resume(returning:["ok":true]); gateway.pendingMutation=nil
        await task.value
        XCTAssertTrue(model.conversations.isEmpty); XCTAssertTrue(model.historyError.isEmpty)
    }
    func testMutationsRejectForeignMissingIdentityAndBusyTargets() async {
        let gateway = ControlledGateway()
        let model = VoiceModel(restoreConnection:false,mockMethods:["chat.history","sessions.patch","sessions.delete"],mockFactory:{gateway})
        await model.connect()
        let foreign=ConversationSummary(id:VoxkinHistory.session(agent:"other",id:UUID().uuidString.lowercased()),title:"Foreign",sessionID:"s")
        await model.renameConversation(foreign,to:"Wrong"); await model.deleteConversation(foreign)
        let noIdentity=ConversationSummary(id:VoxkinHistory.session(agent:"synthetic",id:UUID().uuidString.lowercased()),title:"Unknown")
        await model.deleteConversation(noIdentity)
        let owned=noIdentity.named("Own"); model.activeRun=(owned.id,"running")
        await model.renameConversation(owned,to:"Busy")
        XCTAssertTrue(gateway.mutationParams.isEmpty); model.disconnect()
    }
    func testLateHistoryCannotPersistSelectionAfterDisconnect() async {
        let gateway = ControlledGateway(); gateway.blockHistory = true
        let prefs = UserDefaults(suiteName: UUID().uuidString)!
        let model = VoiceModel(restoreConnection:false,preferences:prefs,mockMethods:["chat.history"],mockFactory:{gateway})
        await model.connect()
        let item = ConversationSummary(id: VoxkinHistory.session(agent:"synthetic",id:UUID().uuidString.lowercased()),title:"Old")
        let task = Task { await model.openConversation(item) }
        for _ in 0..<100 { if gateway.pendingHistory != nil { break }; await Task.yield() }
        XCTAssertNotNil(gateway.pendingHistory)
        model.disconnect(); gateway.pendingHistory?.resume(returning:["messages":[["role":"assistant","text":"Stale"]]]); gateway.pendingHistory=nil
        await task.value
        XCTAssertTrue(model.reply.isEmpty); XCTAssertTrue(model.historyMessages.isEmpty)
        XCTAssertFalse(prefs.dictionaryRepresentation().keys.contains { $0.hasPrefix("voxkin-last-conversation|") })
    }
    func testFailedHistoryReadCannotBeSentToAfterListRefresh() async {
        let gateway = ControlledGateway(); gateway.historyFails = true
        let model = VoiceModel(restoreConnection:false,preferences:UserDefaults(suiteName:UUID().uuidString)!,mockMethods:["chat.history"],mockFactory:{gateway})
        await model.connect()
        await model.openConversation(ConversationSummary(id:VoxkinHistory.session(agent:"synthetic",id:UUID().uuidString.lowercased()),title:"Denied"))
        XCTAssertFalse(model.historyReady)
        await model.loadConversations(); model.draft="Do not send"; model.send()
        await Task.yield(); XCTAssertTrue(gateway.chatMessages.isEmpty)
        await model.newConversation(); XCTAssertTrue(model.historyReady)
        model.disconnect()
    }
    func testDisconnectDuringConnectHistoryRestoreKeepsDisconnectedStatus() async {
        let prefs = UserDefaults(suiteName:UUID().uuidString)!, gateway = ControlledGateway()
        let first = VoiceModel(restoreConnection:false,preferences:prefs,mockMethods:["chat.history"],mockFactory:{gateway})
        await first.connect(); await first.newConversation(); first.disconnect()
        gateway.blockHistory = true
        let model = VoiceModel(restoreConnection:false,preferences:prefs,mockMethods:["chat.history"],mockFactory:{gateway})
        let task = Task { await model.connect() }
        for _ in 0..<100 { if gateway.pendingHistory != nil { break }; await Task.yield() }
        XCTAssertNotNil(gateway.pendingHistory)
        model.disconnect(); let status = model.status
        gateway.pendingHistory?.resume(returning:["messages":[]]); gateway.pendingHistory=nil
        await task.value
        XCTAssertFalse(model.connected); XCTAssertEqual(model.status,status)
    }
    func testLastConversationRestoresAfterModelRelaunchWithoutSpeaking() async {
        let prefs = UserDefaults(suiteName:UUID().uuidString)!, gateway = ControlledGateway()
        let first = VoiceModel(restoreConnection:false,preferences:prefs,mockMethods:["chat.history"],mockFactory:{gateway})
        await first.connect(); await first.newConversation(); let selected = first.historySelection; first.disconnect()
        gateway.historyResult = ["messages":[["id":"saved","role":"assistant","text":"Welcome back"]]]
        let second = VoiceModel(restoreConnection:false,preferences:prefs,mockMethods:["chat.history"],mockFactory:{gateway})
        await second.connect()
        XCTAssertEqual(second.historySelection,selected); XCTAssertEqual(second.reply,"Welcome back")
        XCTAssertTrue(gateway.speechTexts.isEmpty); second.disconnect()
    }
    func model() async -> (VoiceModel, ControlledGateway) {
        let transport = ControlledGateway(), model = VoiceModel(restoreConnection: false, mockFactory: { transport })
        await model.connect(); model.draft = "synthetic"; return (model, transport)
    }
    func testClearDraftDuringFinalizationRejectsLateWordsAndNeverSends() async {
        let transport = ControlledGateway(), capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, capture: capture, mockFactory: { transport })
        await model.connect(); await model.record()
        capture.text?("Wrong words")
        let lateText = capture.text, lateEnd = capture.end
        model.finishAndSend(); model.clearDraft()
        lateText?("Wrong final words"); lateEnd?(nil)
        for _ in 0..<10 { await Task.yield() }
        XCTAssertTrue(model.draft.isEmpty); XCTAssertFalse(model.finishingSpeech)
        XCTAssertEqual(model.gate.state, .ready); XCTAssertTrue(transport.chatMessages.isEmpty)
        await model.record(); capture.text?("Correct words"); model.finishAndSend(); capture.end?(nil)
        for _ in 0..<10 { await Task.yield() }
        XCTAssertEqual(model.sentText, "Correct words"); XCTAssertEqual(transport.chatMessages.count, 1)
        model.disconnect()
    }
    func testOldDictationCallbacksCannotChangeNewCapture() async {
        let transport = ControlledGateway(), capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, capture: capture, mockFactory: { transport })
        await model.connect(); await model.record()
        capture.text?("Old words")
        let oldText = capture.text, oldEnd = capture.end
        model.stopPlayback(); await model.record()
        capture.text?("New final words")
        oldText?("Stale words"); oldEnd?(nil)
        XCTAssertEqual(model.draft, "New final words")
        XCTAssertEqual(model.gate.state, .recording)
        model.finishAndSend(); capture.end?(nil)
        for _ in 0..<10 { await Task.yield() }
        XCTAssertEqual(model.sentText, "New final words")
        XCTAssertEqual(transport.chatMessages.count, 1)
        model.disconnect()
    }
    func testCloseDuringAgentListingCannotResurrectConnection() async {
        let transport = ControlledGateway(); transport.closeDuringAgentList = true
        let model = VoiceModel(restoreConnection: false, mockFactory: { transport })
        await model.connect()
        XCTAssertFalse(model.connected); XCTAssertFalse(model.busy)
        XCTAssertEqual(model.gate.state, .disconnected)
        XCTAssertTrue(model.agents.isEmpty)
        transport.closeDuringAgentList = false
        await model.foreground()
        XCTAssertTrue(model.connected)
        XCTAssertFalse(transport.calls.contains("chat.send"))
        model.disconnect()
    }
    func testEmptyFinalSettlesWithoutSpeechAndAllowsNextTurn() async {
        for message: [String: Any] in [[:], ["message": ["role": "assistant", "content": "  "]]] {
            let (model, transport) = await model(); model.send()
            let run = model.activeRun!
            for _ in 0..<5 { await Task.yield() }
            var event = message
            event["sessionKey"] = run.session; event["runId"] = run.run; event["state"] = "final"
            transport.onEvent?("chat", event)
            XCTAssertNil(model.activeRun); XCTAssertEqual(model.gate.state, .ready)
            XCTAssertFalse(transport.calls.contains("tts.speak"))
            model.draft = "Next question"; model.send()
            XCTAssertNotNil(model.activeRun)
            model.disconnect()
        }
    }
    func testReplyRendersInlineMarkdownWithoutLosingParagraphs() {
        let text = VoiceView.formattedReply("A **bold** fact.\n\nAnother *thought*.")
        XCTAssertEqual(String(text.characters), "A bold fact.\n\nAnother thought.")
        XCTAssertTrue(text.runs.contains { $0.inlinePresentationIntent?.contains(.stronglyEmphasized) == true })
    }
    func testSpeechTimingSeparatesRequestAndLocalSetupAndResets() {
        var timing = TurnTiming()
        timing.sent(at: 100); timing.final(at: 110)
        timing.speechReceived(at: 114); timing.audio(at: 115)
        XCTAssertEqual(timing.speechRequest, 4)
        XCTAssertEqual(timing.playbackSetup, 1)
        XCTAssertEqual(timing.speech, 5)
        timing.speechReceived(at: 118); timing.audio(at: 119)
        XCTAssertEqual(timing.speechRequest, 4)
        timing.sent(at: 120)
        XCTAssertNil(timing.speechRequest); XCTAssertNil(timing.playbackSetup)
    }
    func testConcisePreferencePreservesCommandsVerbatim() async {
        for command in ["/status", "  /new", "!help"] {
            let (model, transport) = await model()
            model.draft = command; model.send()
            for _ in 0..<10 { await Task.yield() }
            XCTAssertEqual(transport.chatMessages, [command])
            model.disconnect()
        }
    }
    func testReplyLengthMigrationAndAllModesPersistAndReachGateway() async {
        let suite = "voxkin-reply-length-" + UUID().uuidString
        let prefs = UserDefaults(suiteName: suite)!
        defer { prefs.removePersistentDomain(forName: suite) }
        for old in [true, false] {
            prefs.removeObject(forKey: "reply-length")
            prefs.set(old, forKey: "concise-replies")
            let model = VoiceModel(restoreConnection: false, preferences: prefs)
            XCTAssertEqual(model.replyLength, old ? .brief : .full)
        }
        for length in ReplyLength.allCases {
            let transport = ControlledGateway()
            let model = VoiceModel(restoreConnection: false, preferences: prefs, mockFactory: { transport })
            model.replyLength = length
            let restored = VoiceModel(restoreConnection: false, preferences: prefs)
            XCTAssertEqual(restored.replyLength, length)
            await model.connect(); model.draft = "Tell me about trees"; model.send()
            for _ in 0..<10 { await Task.yield() }
            XCTAssertEqual(transport.chatMessages, ["[Voxkin reply preference for this turn only: \(length.guidance)]\n\nTell me about trees"])
            model.disconnect()
        }
    }
    func testConcisePreferencePersistsAndOffUsesNormalLength() async {
        let suite = "voxkin-tests-" + UUID().uuidString
        let preferences = UserDefaults(suiteName: suite)!
        defer { preferences.removePersistentDomain(forName: suite) }
        let transport = ControlledGateway()
        let model = VoiceModel(restoreConnection: false, preferences: preferences, mockFactory: { transport })
        XCTAssertEqual(model.replyLength, .brief)
        await model.connect(); model.draft = "Explain in detail"; model.send()
        // Changing Settings after Send must not alter the queued turn.
        model.replyLength = .full
        for _ in 0..<10 { await Task.yield() }
        XCTAssertEqual(model.sentText, "Explain in detail")
        XCTAssertTrue(transport.chatMessages[0].contains("30–50 words"))
        XCTAssertTrue(transport.chatMessages[0].contains("Give more detail when the user asks"))
        XCTAssertTrue(transport.chatMessages[0].hasSuffix("Explain in detail"))
        model.disconnect()
        let restoredTransport = ControlledGateway()
        let restored = VoiceModel(restoreConnection: false, preferences: preferences, mockFactory: { restoredTransport })
        XCTAssertEqual(restored.replyLength, .full)
        await restored.connect(); restored.draft = "Hello"; restored.send()
        for _ in 0..<10 { await Task.yield() }
        XCTAssertTrue(restoredTransport.chatMessages[0].contains("earlier concise-reply preference is off"))
        XCTAssertFalse(restoredTransport.chatMessages[0].contains("30–50 words"))
        restored.replyLength = .brief
        XCTAssertEqual(preferences.string(forKey: "reply-length"), "brief")
        restored.disconnect()
    }
    func testStopAndReviewPreservesFinalWordsWithoutSending() async {
        let transport = ControlledGateway(), capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, capture: capture, mockFactory: { transport })
        await model.connect(); await model.record()
        XCTAssertTrue(model.microphoneReady)
        capture.text?("The final word is")
        model.finishRecording(); model.finishRecording()
        XCTAssertFalse(model.microphoneReady)
        XCTAssertTrue(model.finishingSpeech)
        XCTAssertEqual(capture.finishCount, 1)
        capture.text?("The final word is pineapple"); capture.end?(nil)
        for _ in 0..<10 { await Task.yield() }
        XCTAssertFalse(model.finishingSpeech)
        XCTAssertEqual(model.draft, "The final word is pineapple")
        XCTAssertFalse(transport.calls.contains("chat.send"))
        model.send()
        for _ in 0..<10 { await Task.yield() }
        XCTAssertEqual(transport.calls.filter { $0 == "chat.send" }.count, 1)
        model.disconnect()
    }
    func testDiscardWhileFinalizingReviewIgnoresLateRecognition() async {
        let transport = ControlledGateway(), capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, capture: capture, mockFactory: { transport })
        await model.connect(); await model.record(); capture.text?("Discard me")
        let lateText = capture.text, lateEnd = capture.end
        model.finishRecording(); model.clearDraft()
        lateText?("Late words"); lateEnd?(nil)
        XCTAssertTrue(model.draft.isEmpty)
        XCTAssertFalse(model.microphoneReady); XCTAssertFalse(model.finishingSpeech)
        XCTAssertFalse(transport.calls.contains("chat.send"))
        model.disconnect()
    }
    func testExplicitDictationSendWaitsForFinalWordsAndSubmitsOnce() async {
        let transport = ControlledGateway(), capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, capture: capture, mockFactory: { transport })
        await model.connect(); await model.record()
        capture.text?("The final word is")
        model.finishAndSend(); model.finishAndSend()
        XCTAssertEqual(capture.finishCount, 1)
        XCTAssertFalse(transport.calls.contains("chat.send"))
        capture.text?("The final word is pineapple")
        capture.end?(nil)
        for _ in 0..<10 { await Task.yield() }
        XCTAssertEqual(model.sentText, "The final word is pineapple")
        XCTAssertEqual(transport.calls.filter { $0 == "chat.send" }.count, 1)
        model.disconnect()
    }
    func testEmptyFinalDoesNotEraseWordsOnExplicitSend() async {
        let transport = ControlledGateway(), capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, capture: capture, mockFactory: { transport })
        await model.connect(); await model.record()
        capture.text?("Keep my spoken prompt")
        model.finishAndSend(); capture.text?("  "); capture.end?(nil)
        for _ in 0..<10 { await Task.yield() }
        XCTAssertEqual(model.sentText, "Keep my spoken prompt")
        XCTAssertEqual(transport.calls.filter { $0 == "chat.send" }.count, 1)
        model.disconnect()
    }
    func testEmptyNaturalFinalPreservesDraftWithoutSending() async {
        let transport = ControlledGateway(), capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, capture: capture, mockFactory: { transport })
        await model.connect(); await model.record()
        capture.text?("Keep my draft"); capture.text?(""); capture.end?(nil)
        XCTAssertEqual(model.draft, "Keep my draft")
        XCTAssertFalse(transport.calls.contains("chat.send"))
        model.disconnect()
    }
    func testEmptyRecognitionOffersRetryWithoutSending() async {
        let transport = ControlledGateway(), capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, capture: capture, mockFactory: { transport })
        await model.connect(); await model.record()
        model.finishAndSend(); capture.text?(""); capture.end?(nil)
        XCTAssertTrue(model.draft.isEmpty)
        XCTAssertEqual(model.status, "No words captured. Tap the mic to try again, or type a message.")
        XCTAssertFalse(transport.calls.contains("chat.send"))
        model.disconnect()
    }
    func testNaturalDictationEndLeavesDraftUntilExplicitSend() async {
        let transport = ControlledGateway(), capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, capture: capture, mockFactory: { transport })
        await model.connect(); await model.record()
        capture.text?("Keep these words"); capture.end?(nil)
        XCTAssertEqual(model.draft, "Keep these words")
        XCTAssertFalse(transport.calls.contains("chat.send"))
        model.send()
        for _ in 0..<10 { await Task.yield() }
        XCTAssertEqual(model.sentText, "Keep these words")
        XCTAssertEqual(transport.calls.filter { $0 == "chat.send" }.count, 1)
        model.disconnect()
    }
    func testStopDuringSpeechFinalizationRejectsLateCompletion() async {
        let transport = ControlledGateway(), capture = ControlledCapture()
        let model = VoiceModel(restoreConnection: false, capture: capture, mockFactory: { transport })
        await model.connect(); await model.record()
        capture.text?("Do not submit")
        let late = capture.end
        model.finishAndSend(); model.stopPlayback(); late?(nil)
        for _ in 0..<10 { await Task.yield() }
        XCTAssertFalse(transport.calls.contains("chat.send"))
        model.disconnect()
    }
    func testSlowOptionalSubscriptionDoesNotDelaySend() async {
        let transport = ControlledGateway(); transport.blockSubscription = true
        let model = VoiceModel(restoreConnection: false, mockFactory: { transport })
        await model.connect()
        for _ in 0..<10 { await Task.yield() }
        XCTAssertNotNil(transport.subscription)
        model.draft = "Send without waiting for optional updates"; model.send()
        for _ in 0..<10 { await Task.yield() }
        XCTAssertTrue(transport.calls.contains("chat.send"))
        model.disconnect()
        transport.subscription?.resume(returning: [:]); transport.subscription = nil
        for _ in 0..<5 { await Task.yield() }
        XCTAssertFalse(model.connected)
    }
    func testTimingMeasuresStagesAndResetsWithoutContent() {
        var timing = TurnTiming()
        timing.sent(at: 100, transcription: 0.25)
        timing.update(at: 102); timing.update(at: 103)
        timing.final(at: 105); timing.audio(at: 107); timing.audio(at: 110)
        XCTAssertEqual(timing.firstUpdate, 2); XCTAssertEqual(timing.reply, 5)
        XCTAssertEqual(timing.speech, 2); XCTAssertEqual(timing.firstAudio, 7)
        XCTAssertEqual(timing.transcription, 0.25)
        timing.sent(at: 200)
        XCTAssertNil(timing.firstAudio); XCTAssertNil(timing.transcription)
    }
    func testChunkedSpeechPreservesOrderAndCompletes() async {
        let (model, transport) = await model()
        let text = String(repeating: "A", count: 120) + "." + String(repeating: "B", count: 320) + "." + String(repeating: "C", count: 320) + ".\n"
        model.send(); let run = model.activeRun!
        for _ in 0..<5 { await Task.yield() }
        transport.onEvent?("chat", ["sessionKey": run.session, "runId": run.run, "state": "final", "message": ["role": "assistant", "content": text]])
        for _ in 0..<100 {
            if model.gate.state == .ready { break }
            try? await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTAssertEqual(model.gate.state, .ready)
        XCTAssertEqual(transport.speechTexts, SpeechChunks.split(text).filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty })
        XCTAssertEqual(transport.speechTexts.joined().trimmingCharacters(in: .whitespacesAndNewlines), text.trimmingCharacters(in: .whitespacesAndNewlines))
        XCTAssertNotNil(model.timing.firstAudio)
        XCTAssertEqual(model.reply, text)
        model.disconnect()
    }
    func testStopDiscardsPrefetchedAudioAndNeverRequestsThirdChunk() async { await checkPendingSpeech(background: false) }
    func testBackgroundDiscardsPrefetchedAudioAndReconnectDoesNotReplay() async { await checkPendingSpeech(background: true) }
    private func checkPendingSpeech(background: Bool) async {
        let (model, transport) = await model(); transport.blockSecondSpeech = true
        model.send(); let run = model.activeRun!
        for _ in 0..<5 { await Task.yield() }
        let text = String(repeating: "The forest is quiet and the stream is flowing. ", count: 30)
        transport.onEvent?("chat", ["sessionKey": run.session, "runId": run.run, "state": "final", "message": ["role": "assistant", "content": text]])
        for _ in 0..<50 {
            if transport.pendingSpeech != nil { break }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
        XCTAssertNotNil(transport.pendingSpeech)
        if background { model.background(); await model.foreground() } else { model.stopPlayback() }
        transport.pendingSpeech?.resume(returning: ["audioBase64": MockGateway.tone().base64EncodedString(), "provider": "test", "mimeType": "audio/wav"])
        transport.pendingSpeech = nil
        try? await Task.sleep(nanoseconds: 400_000_000)
        XCTAssertEqual(model.gate.state, background ? .ready : .interrupted)
        XCTAssertEqual(transport.speechTexts.count, 2)
        XCTAssertEqual(model.reply, background ? "" : text)
        model.disconnect()
    }
    func testForegroundConnectsOnceAndNeverRecordsOrReplays() async {
        let transport = ControlledGateway()
        let model = VoiceModel(restoreConnection: false, mockFactory: { transport })
        await model.foreground(); await model.foreground()
        XCTAssertTrue(model.connected)
        XCTAssertEqual(transport.calls.filter { $0 == "agents.list" }.count, 1)
        XCTAssertEqual(model.gate.state, .ready)
        XCTAssertFalse(transport.calls.contains("chat.send"))
        model.draft = "Do not replay"
        model.background(); await model.foreground()
        XCTAssertTrue(model.connected)
        XCTAssertTrue(model.draft.isEmpty)
        XCTAssertEqual(model.gate.state, .ready)
        XCTAssertFalse(transport.calls.contains("chat.send"))
        model.disconnect()
    }
    func testInterimMessagesAreCorrelatedDeduplicatedAndNeverSpoken() async {
        let (model, transport) = await model()
        model.send(); let run = model.activeRun!
        for _ in 0..<5 { await Task.yield() }
        let message: [String: Any] = ["role": "assistant", "content": "One.", "openclawDeliveryMirror": ["kind": "message-tool-source-reply", "final": false]]
        var event: [String: Any] = ["sessionKey": run.session, "runId": "wrong", "messageId": "m1", "message": message]
        transport.onEvent?("session.message", event)
        XCTAssertTrue(model.interim.isEmpty)
        event["runId"] = run.run
        transport.onEvent?("session.message", event); transport.onEvent?("session.message", event)
        XCTAssertEqual(model.interim, "One.")
        transport.onEvent?("chat", ["sessionKey": run.session, "runId": run.run, "state": "delta", "message": ["role": "assistant", "content": "Partial"]])
        transport.onEvent?("chat", ["sessionKey": run.session, "runId": run.run, "state": "delta", "message": ["role": "assistant", "content": "Partial reply"]])
        XCTAssertEqual(model.interim, "One.\n\nPartial reply")
        XCTAssertTrue(model.reply.isEmpty)
        XCTAssertFalse(transport.calls.contains("tts.speak"))
        model.stopPlayback()
        event["messageId"] = "late"
        transport.onEvent?("session.message", event)
        XCTAssertEqual(model.interim, "One.\n\nPartial reply")
        model.disconnect()
    }
    func testSpeechAcceptancePersistsOnlyForItsGatewayAndCanBeRevoked() throws {
        let endpoint = "wss://consent-test.invalid"
        defer { try? CredentialStore.remove("speech-consent|" + endpoint) }
        let first = VoiceModel(restoreConnection: false)
        first.endpoint = endpoint
        XCTAssertFalse(first.acceptedFallback)
        first.acceptedFallback = true
        first.disconnect()
        XCTAssertTrue(first.acceptedFallback)
        let relaunched = VoiceModel(restoreConnection: false)
        relaunched.endpoint = endpoint
        XCTAssertTrue(relaunched.acceptedFallback)
        relaunched.endpoint = "wss://other-consent-test.invalid"
        XCTAssertFalse(relaunched.acceptedFallback)
        relaunched.endpoint = endpoint
        XCTAssertTrue(relaunched.acceptedFallback)
        relaunched.acceptedFallback = false
        first.restoreSpeechAcceptance()
        XCTAssertFalse(first.acceptedFallback)
    }
    func testReplacementBootstrapSurvivesRetryAndIsEndpointBound() throws {
        let url = URL(string: "wss://replacement-test.invalid")!
        defer {
            try? CredentialStore.remove("grant|" + url.absoluteString)
            try? CredentialStore.remove("bootstrap|" + url.absoluteString)
        }
        try CredentialStore.save(JSONEncoder().encode(SavedGrant(token: "old-test-grant", scopes: ["operator.read"])), "grant|" + url.absoluteString)
        _ = try CredentialStore.connectionCredential(url, enteredToken: "replacement-test-token")
        let retried = try CredentialStore.connectionCredential(url, enteredToken: "")
        XCTAssertEqual(retried?.token, "replacement-test-token")
        XCTAssertEqual(retried?.scopes, ["operator.read", "operator.write"])
        XCTAssertNil(try CredentialStore.connectionCredential(URL(string: "wss://other-replacement-test.invalid")!, enteredToken: ""))
        try CredentialStore.save(JSONEncoder().encode(SavedGrant(token: "new-test-grant", scopes: ["operator.read", "operator.write"])), "grant|" + url.absoluteString)
        try CredentialStore.remove("bootstrap|" + url.absoluteString)
        XCTAssertEqual(try CredentialStore.connectionCredential(url, enteredToken: "")?.token, "new-test-grant")
    }
    func testSavedConnectionRestoresWithoutConnectingOrRevealingToken() throws {
        let endpoint = "wss://persistence-test.invalid"
        let previous = try CredentialStore.load("last-endpoint")
        defer {
            try? CredentialStore.remove("grant|" + endpoint)
            try? CredentialStore.remove("speech-consent|" + endpoint)
            if let previous { try? CredentialStore.save(previous, "last-endpoint") }
            else { try? CredentialStore.remove("last-endpoint") }
        }
        try CredentialStore.save(Data(endpoint.utf8), "last-endpoint")
        try CredentialStore.save(Data([1]), "speech-consent|" + endpoint)
        try CredentialStore.save(JSONEncoder().encode(SavedGrant(token: "synthetic-test-token", scopes: ["operator.read", "operator.write"])), "grant|" + endpoint)
        let model = VoiceModel()
        XCTAssertEqual(model.endpoint, endpoint)
        XCTAssertTrue(model.acceptedFallback)
        XCTAssertTrue(model.hasSavedCredential)
        XCTAssertTrue(model.bootstrap.isEmpty)
        XCTAssertFalse(model.connected)
        XCTAssertFalse(model.busy)
        model.bootstrap = "unsent-test-token"
        model.endpoint = "wss://different.invalid"
        XCTAssertTrue(model.bootstrap.isEmpty)
        XCTAssertFalse(model.hasSavedCredential)
        model.disconnect()
    }
    func testForgetRemovesPendingTokenGrantIdentityAndRememberedAddress() throws {
        let endpoint = "wss://forget-test.invalid"
        let previous = try CredentialStore.load("last-endpoint")
        defer {
            for prefix in ["grant|", "bootstrap|", "identity|", "speech-consent|"] { try? CredentialStore.remove(prefix + endpoint) }
            if let previous { try? CredentialStore.save(previous, "last-endpoint") }
            else { try? CredentialStore.remove("last-endpoint") }
        }
        try CredentialStore.save(Data(endpoint.utf8), "last-endpoint")
        for prefix in ["grant|", "bootstrap|", "identity|", "speech-consent|"] { try CredentialStore.save(Data("synthetic-test-value".utf8), prefix + endpoint) }
        let model = VoiceModel()
        XCTAssertTrue(model.hasSavedCredential)
        model.forget()
        XCTAssertTrue(model.endpoint.isEmpty)
        XCTAssertFalse(model.hasSavedCredential)
        XCTAssertNil(try CredentialStore.load("last-endpoint"))
        for prefix in ["grant|", "bootstrap|", "identity|", "speech-consent|"] { XCTAssertNil(try CredentialStore.load(prefix + endpoint)) }
    }
    func testStopBeforeQueuedSendSuppressesRequestAndKeepsCancelHandle() async {
        let (model, transport) = await model()
        model.send(); let run = model.activeRun?.run
        model.stopPlayback()
        for _ in 0..<10 { await Task.yield() }
        XCTAssertFalse(transport.calls.contains("chat.send"))
        XCTAssertEqual(model.activeRun?.run, run)
        XCTAssertEqual(model.gate.state, .interrupted)
        model.disconnect()
    }
    func testInterruptionWhileWaitingDiscardsPendingPlayback() async {
        let (model, transport) = await model()
        model.send(); let run = model.activeRun!
        for _ in 0..<5 { await Task.yield() }
        model.interrupt()
        transport.onEvent?("chat", ["sessionKey": run.session, "runId": run.run, "state": "final", "message": ["role": "assistant", "content": "synthetic"]])
        for _ in 0..<5 { await Task.yield() }
        XCTAssertEqual(model.gate.state, .interrupted)
        XCTAssertFalse(transport.calls.contains("tts.speak"))
        XCTAssertNil(model.activeRun)
        model.disconnect()
    }
    func testOldAbortResponseCannotClearNewRun() async {
        let (model, transport) = await model()
        model.send(); let old = model.activeRun!
        for _ in 0..<5 { await Task.yield() }
        let cancelling = Task { await model.cancelAgent() }
        for _ in 0..<10 { await Task.yield() }
        XCTAssertNotNil(transport.abort)
        transport.terminal(old)
        model.draft = "A new turn"
        model.send(); let new = model.activeRun!.run
        XCTAssertNotEqual(new, old.run)
        transport.abort?.resume(returning: [:]); transport.abort = nil
        await cancelling.value
        XCTAssertEqual(model.activeRun?.run, new)
        model.disconnect()
    }
    func testCategoryChangeDoesNotInterruptButHeadsetRemovalDoes() async {
        let (model, _) = await model(); model.send()
        NotificationCenter.default.post(name: AVAudioSession.routeChangeNotification, object: nil, userInfo: [AVAudioSessionRouteChangeReasonKey: AVAudioSession.RouteChangeReason.categoryChange.rawValue])
        for _ in 0..<10 { await Task.yield() }
        XCTAssertEqual(model.gate.state, .waiting)
        NotificationCenter.default.post(name: AVAudioSession.routeChangeNotification, object: nil, userInfo: [AVAudioSessionRouteChangeReasonKey: AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue])
        for _ in 0..<10 { await Task.yield() }
        XCTAssertEqual(model.gate.state, .interrupted)
        model.disconnect()
    }
}
