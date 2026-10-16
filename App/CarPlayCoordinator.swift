import CarPlay
import Combine
import UIKit

/// CarPlay is a voice-only surface over the same authorized model as the phone.
/// It never connects with a different credential, exposes transcripts, or starts a microphone on attachment.
@MainActor final class CarPlayCoordinator: ObservableObject {
    static let shared = CarPlayCoordinator()
    @Published private(set) var isConnected = false
    @Published private(set) var ownsVoiceSession = false

    private weak var model: VoiceModel?
    private var observation: AnyCancellable?
    private var unlockObservation: AnyCancellable?
    private weak var controller: CPInterfaceController?
    private var template: CPVoiceControlTemplate?
    private var startButtons: [CPButton] = []
    private var reconnectButtons: [CPButton] = []
    private var stopButtons: [CPButton] = []
    private var cancelButtons: [CPButton] = []
    private var voiceStates: [String: CPVoiceControlState] = [:]
    private var stateButtons: [String: [(CarPlayAction, CPButton)]] = [:]
    private var presented = false
    private var startTask: Task<Void, Never>?
    private var startGeneration = UUID()
    private var sceneActive = false

    init() {
        unlockObservation = NotificationCenter.default.publisher(for: UIApplication.protectedDataDidBecomeAvailableNotification)
            .sink { [weak self] _ in
                Task { @MainActor [weak self] in self?.reconnectIfActive() }
            }
    }

    func attach(model: VoiceModel) {
        guard self.model !== model else { return }
        self.model = model
        observation = model.objectWillChange.sink { [weak self] _ in
            // ObservableObject sends before mutation; render after the model's update.
            Task { @MainActor [weak self] in self?.refresh() }
        }
        reconnectIfActive()
        refresh()
    }

    @available(iOS 26.4, *)
    func connect(_ controller: CPInterfaceController) {
        self.controller = controller
        isConnected = true
        startButtons = []; stopButtons = []; cancelButtons = []; reconnectButtons = []
        let states = makeVoiceStates()
        let voiceTemplate = CPVoiceControlTemplate(voiceControlStates: states)
        template = voiceTemplate
        presented = false
        controller.setRootTemplate(voiceTemplate, animated: false) { [weak self, weak voiceTemplate] success, _ in
            Task { @MainActor in
                guard let self, self.template === voiceTemplate else { return }
                self.presented = success
                if success { self.refresh() }
                else { self.stop() }
            }
        }
        refresh()
    }

    enum CarPlayAction: String { case start, stop, cancel, reconnect }

    static func visibleActions(for state: String, hasRun: Bool, canStop: Bool) -> [CarPlayAction] {
        switch state {
        case "ready": return [.start]
        case "offline": return [.reconnect]
        case "listening", "speaking", "finishing", "preparing", "resuming": return canStop ? [.stop] : []
        case "thinking": return (canStop ? [.stop] : []) + (hasRun ? [.cancel] : [])
        default: return []
        }
    }

    /// Apple supports at most five presentation states. Several internal phases
    /// share neutral Working/Not ready artwork, but retain distinct action policy.
    static func templateState(for phase: String) -> String {
        switch phase {
        case "ready", "listening", "speaking": return phase
        case "connecting", "thinking", "finishing", "preparing", "resuming": return "thinking"
        default: return "attention"
        }
    }

    /// Status artwork, not a pretend central touch target. Apple owns button layout.
    static func statusImage(_ symbol: String) -> UIImage? {
        guard let image = UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 88, weight: .medium)) else { return nil }
        let canvas = CGSize(width: 128, height: 128)
        let scale = min(112 / image.size.width, 112 / image.size.height)
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: canvas)
        return renderer.image { _ in
            image.withTintColor(.white, renderingMode: .alwaysOriginal).draw(in: CGRect(
                x: (canvas.width - size.width) / 2, y: (canvas.height - size.height) / 2,
                width: size.width, height: size.height))
        }.withRenderingMode(.alwaysTemplate)
    }

    @available(iOS 26.4, *)
    func makeVoiceStates() -> [CPVoiceControlState] {
        voiceStates = [:]; stateButtons = [:]
        startButtons = []; stopButtons = []; cancelButtons = []; reconnectButtons = []
        let descriptions: [(String, String, String)] = [
            ("attention", "Not ready to talk", "iphone"),
            ("ready", "Ready to talk", "mic.fill"),
            ("listening", "Listening", "waveform"),
            ("thinking", "Working", "ellipsis"),
            ("speaking", "Speaking", "speaker.wave.2.fill")
        ]
        return descriptions.map { identifier, title, symbol in
            let state = CPVoiceControlState(identifier: identifier, titleVariants: [title],
                                            image: Self.statusImage(symbol), repeats: false)
            // Build only meaningful controls; refresh removes those not currently applicable.
            let possible: [CarPlayAction] = identifier == "attention" ? [.reconnect] : Self.visibleActions(for: identifier, hasRun: true, canStop: true)
            let buttons = possible.map { action -> (CarPlayAction, CPButton) in
                let symbol: String
                let title: String
                switch action {
                case .start: symbol = "mic.fill"; title = "Start conversation"
                case .stop: symbol = "stop.fill"; title = "Stop voice"
                case .cancel: symbol = "xmark.circle.fill"; title = "Cancel request"
                case .reconnect: symbol = "arrow.clockwise"; title = "Reconnect"
                }
                let button = CPButton(image: UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 28, weight: .semibold))!) { [weak self] _ in
                    Task { @MainActor in
                        guard let self else { return }
                        switch action {
                        case .start: self.start()
                        case .stop: self.stop()
                        case .reconnect: self.reconnectIfActive()
                        case .cancel:
                            self.stop()
                            await self.model?.cancelAgent()
                            self.refresh()
                        }
                    }
                }
                button.title = title
                switch action {
                case .start: startButtons.append(button)
                case .stop: stopButtons.append(button)
                case .cancel: cancelButtons.append(button)
                case .reconnect: reconnectButtons.append(button)
                }
                return (action, button)
            }
            voiceStates[identifier] = state; stateButtons[identifier] = buttons
            // Begin without destructive actions. refresh() installs the active phase's controls.
            state.actionButtons = identifier == "ready" ? buttons.map { $0.1 } : []
            return state
        }
    }

    func disconnect() {
        suspend()
        // Also stop any voice the phone started while CarPlay was attached.
        model?.interrupt()
        isConnected = false
        presented = false
        controller = nil
        template = nil
        voiceStates = [:]; stateButtons = [:]
        startButtons = []; stopButtons = []; cancelButtons = []; reconnectButtons = []
        if UIApplication.shared.applicationState != .active { model?.background() }
    }

    func sceneBecameActive() {
        sceneActive = true
        reconnectIfActive()
        refresh() // Never restart voice automatically when returning to CarPlay.
    }

    private func reconnectIfActive() {
        guard sceneActive, let model else { return }
        Task { @MainActor [weak self, weak model] in
            guard let self, self.sceneActive, self.model === model else { return }
            await model?.foreground() // Connection only; microphone still requires Start.
            self.refresh()
        }
    }

    func suspend() {
        sceneActive = false
        stop(discardOwnedDraft: false)
    }

    private func start() {
        guard isConnected, presented, sceneActive, let model, model.canStartConversation,
              !model.conversationMode, startTask == nil else { return }
        let generation = UUID(); startGeneration = generation
        startTask = Task { @MainActor [weak self, weak model] in
            guard let self, let model else { return }
            defer {
                if self.startGeneration == generation { self.startTask = nil; self.refresh() }
            }
            guard !Task.isCancelled, self.isConnected, self.sceneActive, model.canStartConversation else { return }
            self.ownsVoiceSession = true
            await model.startConversation()
        }
    }

    private func stop(discardOwnedDraft: Bool = true) {
        // An explicit car Stop discards only this car-started, unsent capture.
        // System suspension preserves drafts; phone-authored drafts are never discarded.
        let discard = discardOwnedDraft && ownsVoiceSession && model?.activeRun == nil &&
            (model?.gate.state == .recording || model?.gate.state == .transcribing)
        startGeneration = UUID()
        startTask?.cancel(); startTask = nil
        model?.endConversation()
        model?.interrupt()
        if discard { model?.clearDraft() }
        ownsVoiceSession = false
        refresh()
    }

    private func refresh() {
        guard let model else { return }
        if !model.conversationMode && startTask == nil { ownsVoiceSession = false }
        let canStart = sceneActive && model.canStartConversation && !model.conversationMode && startTask == nil
        reconnectButtons.forEach { $0.isEnabled = sceneActive && !model.busy && !model.connected }
        startButtons.forEach { $0.isEnabled = canStart }
        let canStop = model.conversationMode || model.gate.state == .recording || model.gate.state == .speaking || model.gate.state == .waiting
        stopButtons.forEach { $0.isEnabled = sceneActive && canStop }
        cancelButtons.forEach { $0.isEnabled = sceneActive && model.activeRun != nil }
        guard presented, let template else { return }
        let state: String
        if !sceneActive { state = "paused" }
        else if !model.connected { state = model.busy ? "connecting" : model.hasSavedCredential ? "offline" : "setup" }
        else if model.activeRun != nil { state = "thinking" }
        else {
            switch model.gate.state {
            case .recording: state = model.finishingSpeech ? "finishing" : "listening"
            case .transcribing: state = "finishing"
            case .waiting: state = "preparing"
            case .speaking: state = "speaking"
            case .ready, .interrupted: state = canStart ? "ready" : model.conversationMode ? "resuming" : "phone"
            case .disconnected, .failed: state = "setup"
            }
        }
        let displayState = Self.templateState(for: state)
        if #available(iOS 26.4, *), let voiceState = voiceStates[displayState] {
            let actions = Self.visibleActions(for: state, hasRun: model.activeRun != nil, canStop: canStop)
            let buttons = (stateButtons[displayState] ?? []).filter { actions.contains($0.0) }.map { $0.1 }
            if voiceState.actionButtons.map(\.title) != buttons.map(\.title) { voiceState.actionButtons = buttons }
        }
        if template.activeStateIdentifier != displayState { template.activateVoiceControlState(withIdentifier: displayState) }
    }
}

@MainActor final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {
    func sceneDidBecomeActive(_ scene: UIScene) {
        CarPlayCoordinator.shared.sceneBecameActive()
    }

    func sceneWillResignActive(_ scene: UIScene) {
        CarPlayCoordinator.shared.suspend()
    }

    func sceneDidEnterBackground(_ scene: UIScene) {
        CarPlayCoordinator.shared.suspend()
    }

    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene,
                                  didConnect interfaceController: CPInterfaceController) {
        if #available(iOS 26.4, *) { CarPlayCoordinator.shared.connect(interfaceController) }
    }

    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene,
                                  didDisconnectInterfaceController interfaceController: CPInterfaceController) {
        CarPlayCoordinator.shared.disconnect()
    }
}
