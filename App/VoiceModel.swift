import SwiftUI
import AVFoundation
import UIKit
import VoiceCore

enum ReplyLength: String, CaseIterable, Identifiable {
    case brief, balanced, full
    var id: String { rawValue }
    var title: String { rawValue.capitalized }
    var guidance: String {
        switch self {
        case .brief: return "Prefer about 30–50 words total in 1–3 short, natural sentences. Answer directly without an introductory recap or extra trivia. Give more detail when the user asks for it or it is necessary for a correct, useful answer. Do not omit essential information."
        case .balanced: return "Prefer 2–3 short, natural sentences. Give more detail when the user asks for it or it is necessary for a correct, useful answer. Do not omit essential information."
        case .full: return "Use your normal response length; any earlier concise-reply preference is off."
        }
    }
    var detail: String {
        switch self {
        case .brief: return "Quick, focused answers—the current concise style."
        case .balanced: return "A little more context—the original concise style, often around 100 words."
        case .full: return "Normal-length answers, without a brevity preference."
        }
    }
}

@MainActor final class VoiceModel: NSObject, ObservableObject, AVAudioPlayerDelegate {
    private let preferences: UserDefaults
    @Published private(set) var localVoices: [LocalVoice] = []
    @Published private(set) var selectedVoiceID = ""
    @Published private(set) var voiceCatalogStatus = "Connect to discover optional local voices."
    @Published private(set) var previewing = false
    private var turnVoice: LocalVoice?
    private var supportsLocalVoice = false
    private var catalogTask: Task<Void, Never>?
    private let mockMethods: [String]
    var selectedVoice: LocalVoice? { localVoices.first { $0.id == selectedVoiceID } }
    var canSelectVoice: Bool { connected && !busy && activeRun == nil && (gate.state == .ready || gate.state == .interrupted) }
    var voiceSelectionValid: Bool { selectedVoiceID.isEmpty || selectedVoice != nil }
    func selectVoice(_ id: String) {
        guard !restrictedMode || !id.isEmpty else { return }
        guard canSelectVoice, id.isEmpty || localVoices.contains(where: { $0.id == id }) else { return }
        clearAcknowledgment()
        selectedVoiceID = id
        if mockFactory != nil { preferences.set(id, forKey: "test-local-voice"); return }
        guard let url = try? Endpoint.validate(endpoint) else { return }
        do { try CredentialStore.save(Data(id.utf8), "local-voice|" + url.absoluteString) }
        catch { status = "Voice selected for this connection, but could not be saved." }
    }
    private func restoreVoiceSelection() {
        if mockFactory != nil { selectedVoiceID = preferences.string(forKey: "test-local-voice") ?? ""; return }
        guard let url = try? Endpoint.validate(endpoint) else { selectedVoiceID = ""; return }
        selectedVoiceID = (try? CredentialStore.load("local-voice|" + url.absoluteString)).flatMap { String(data: $0, encoding: .utf8) } ?? ""
    }
    func previewVoice() {
        guard canSelectVoice, acceptedFallback, let voice = selectedVoice, let transport else { return }
        do {
            let epoch = try gate.begin(session: "voice-preview", run: UUID().uuidString)
            previewing = true; status = "Preparing voice preview…"
            turnTask = Task {
                do {
                    let clip = try await LocalVoice.synthesize(voice.sample, voice: voice, transport: transport)
                    guard !Task.isCancelled, self.gate.epoch == epoch else { return }
                    try await self.play(clip, epoch: epoch)
                    guard self.gate.epoch == epoch else { return }
                    self.previewing = false; self.gate.invalidate(.ready)
                    try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
                    self.status = "Preview finished. Ready when you are."
                } catch { self.fail(error, epoch: epoch) }
            }
        } catch { status = safe(error) }
    }
    @Published var replyLength: ReplyLength = .brief {
        didSet { preferences.set(replyLength.rawValue, forKey: "reply-length") }
    }
    @Published var spokenAcknowledgment = true {
        didSet {
            preferences.set(spokenAcknowledgment, forKey: "spoken-acknowledgment")
            if !spokenAcknowledgment { clearAcknowledgment() }
        }
    }
    var acknowledgmentReady: Bool { acknowledgmentClip != nil }
    var isAcknowledging: Bool { acknowledgmentPlayer != nil }
    private var acknowledgmentClip: AudioClip?
    private var acknowledgmentVoice: LocalVoice?
    private var acknowledgmentPreparation: Task<Void, Never>?
    private var acknowledgmentPlayer: AVAudioPlayer?
    private var acknowledgmentGeneration = UUID()
    private var spokenDraft = ""
    private var turnWasSpoken = false
    private var turnAccepted = false
    private var pendingProgressText: String?
    private var progressStarted = false
    private var progressSpeechTask: Task<Void, Never>?
    private var lastRecognitionAt: TimeInterval?

    /// A quiet, local receipt sound: no canned speech, network request, or cached user audio.
    private func prepareAcknowledgment() {
        guard spokenAcknowledgment, acceptedFallback, voiceSelectionValid,
              acknowledgmentClip == nil else { return }
        let rate = 24_000, count = 2_880 // 120 ms, gently faded at both ends.
        var wav = Data()
        func ascii(_ value: String) { wav.append(contentsOf: value.utf8) }
        func word(_ value: UInt16) {
            wav.append(UInt8(value & 255)); wav.append(UInt8(value >> 8))
        }
        func dword(_ value: UInt32) {
            word(UInt16(value & 65535)); word(UInt16(value >> 16))
        }
        ascii("RIFF"); dword(UInt32(36 + count * 2)); ascii("WAVEfmt ")
        dword(16); word(1); word(1); dword(UInt32(rate)); dword(UInt32(rate * 2))
        word(2); word(16); ascii("data"); dword(UInt32(count * 2))
        for index in 0..<count {
            let t = Double(index) / Double(rate)
            let envelope = pow(sin(Double.pi * Double(index) / Double(count - 1)), 2)
            let sample = Int16(1_800 * envelope * sin(2 * Double.pi * 660 * t))
            word(UInt16(bitPattern: sample))
        }
        acknowledgmentClip = try? AudioClip(payload: ["audioBase64": wav.base64EncodedString(),
            "mimeType": "audio/wav", "provider": "local-cue"])
        acknowledgmentVoice = selectedVoice
    }
    private func stopAcknowledgment() {
        let wasPlaying = acknowledgmentPlayer != nil
        acknowledgmentPlayer?.stop(); acknowledgmentPlayer = nil
        if wasPlaying && player == nil { try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation) }
    }
    private func cancelAcknowledgmentPreparation() {
        progressSpeechTask?.cancel(); progressSpeechTask = nil
        acknowledgmentGeneration = UUID()
        acknowledgmentPreparation?.cancel(); acknowledgmentPreparation = nil
    }
    private func clearAcknowledgment() {
        cancelAcknowledgmentPreparation()
        acknowledgmentClip = nil; acknowledgmentVoice = nil
        stopAcknowledgment()
    }
    private func acknowledgeAcceptedTurn(epoch: UUID) {
        guard gate.epoch == epoch, gate.state == .waiting, !seenFinal, activeRun != nil,
              (conversationMode || turnWasSpoken), spokenAcknowledgment, acceptedFallback,
              acknowledgmentVoice == turnVoice, let clip = acknowledgmentClip else { return }
        do {
            let audio = AVAudioSession.sharedInstance()
            let player = try AVAudioPlayer(data: clip.data)
            guard player.duration <= 3 else { return }
            try audio.setCategory(.playback, mode: .spokenAudio); try audio.setActive(true)
            acknowledgmentPlayer = player; player.delegate = self
            if player.play() { timing.acknowledged() }
            else { stopAcknowledgment() }
        } catch { stopAcknowledgment() }
    }
    /// Speak only explicit user-visible, non-final agent updates, never tool payloads or model deltas.
    private func speakProgressIfReady() {
        guard turnAccepted, turnWasSpoken, spokenAcknowledgment, acceptedFallback,
              !progressStarted, !seenFinal, activeRun != nil, gate.state == .waiting,
              let text = pendingProgressText, let transport else { return }
        progressStarted = true
        let epoch = gate.epoch, generation = acknowledgmentGeneration, voice = turnVoice
        progressSpeechTask = Task { [weak self] in
            guard let self else { return }
            do {
                // Give quick answers time to arrive before spending speech work on an opening.
                try await Task.sleep(nanoseconds: 800_000_000)
                guard !Task.isCancelled, self.gate.epoch == epoch,
                      self.acknowledgmentGeneration == generation, !self.seenFinal,
                      self.gate.state == .waiting, self.spokenAcknowledgment,
                      self.acceptedFallback else { return }
                let clip = try await LocalVoice.synthesize(SpeechText.prepare(text), voice: voice, transport: transport)
                // Never overlap the receipt cue. The final reply always cancels this task.
                while self.acknowledgmentPlayer != nil {
                    try await Task.sleep(nanoseconds: 50_000_000)
                }
                guard !Task.isCancelled, self.gate.epoch == epoch, self.acknowledgmentGeneration == generation,
                      self.gate.state == .waiting, !self.seenFinal, self.activeRun != nil,
                      self.spokenAcknowledgment, self.acceptedFallback, self.turnVoice == voice else { return }
                let player = try AVAudioPlayer(data: clip.data)
                guard player.duration <= 12 else { return }
                let audio = AVAudioSession.sharedInstance()
                try audio.setCategory(.playback, mode:.spokenAudio); try audio.setActive(true)
                self.acknowledgmentPlayer = player; player.delegate = self
                if !player.play() { self.stopAcknowledgment() }
            } catch { /* Optional progress never fails or delays the final answer. */ }
        }
    }
    @Published var endpoint = "" { didSet { if oldValue != endpoint { bootstrap = ""; pairingApproval = nil }; refreshSavedCredential(); restoreSpeechAcceptance() } }
    @Published var hasSavedCredential = false
    @Published var bootstrap = ""
    @Published var invitationText = ""
    @Published var restrictedMode = false
    @Published var enrolledDeviceID = ""
    @Published var pairingApproval: GatewayPairingRequired?
    @Published var requestAdmin = false
    @Published private(set) var adminPermissionGranted = false
    @Published private(set) var supportsAccessManagement = false
    var canManageAccess: Bool { adminPermissionGranted && supportsAccessManagement }
    @Published var accessDevices: [[String: String]] = []
    @Published var accessInvites: [[String: String]] = []
    @Published var accessAgents: [String] = []
    @Published var inviteAgent = ""
    @Published var inviteLabel = ""
    @Published var shareInvitation = ""
    private var enrollmentTransport: RestrictedGateway?
    func importOwnerSetup() {
        guard !connected, !busy else { return }
        do {
            let setup = try VoxkinOwnerSetup(invitationText.trimmingCharacters(in: .whitespacesAndNewlines))
            let target = setup.gateway.absoluteString
            // Setup must not silently replace an already enrolled phone or device grant.
            for key in ["restricted|", "grant|", "bootstrap|"] {
                if try CredentialStore.load(key + target) != nil {
                    throw VoiceError.invalid("This gateway already has saved access. Use its existing connection or Forget gateway before replacing it.")
                }
            }
            endpoint = target; bootstrap = setup.token; requestAdmin = false
            invitationText = ""
            status = "Owner setup loaded. Review the gateway, then tap Connect. Gateway pairing approval may be required."
        } catch { status = safe(error) }
    }
    func joinInvitation() async {
        guard !connected, !busy else { return }
        busy = true
        let enrollmentGeneration = connectionID
        defer { if connectionID == enrollmentGeneration { busy = false; enrollmentTransport = nil } }
        do {
            let invite = try VoxkinInvitation(invitationText.trimmingCharacters(in: .whitespacesAndNewlines))
            var origin = URLComponents(url: invite.gateway, resolvingAgainstBaseURL: false)!
            origin.scheme = "wss"; origin.path = ""
            guard let url = origin.url else { throw VoiceError.invalid("Invalid invitation gateway.") }
            let client = try RestrictedGateway(url:url, token:"")
            enrollmentTransport = client
            defer { client.close() }
            let result = try await client.call(["op":"redeem","secret":invite.secret])
            guard connectionID == enrollmentGeneration else { return }
            guard let secret = result["deviceSecret"] as? String, let device = result["id"] as? String else { throw VoiceError.invalid("Invalid enrollment response.") }
            try CredentialStore.save(Data(device.utf8), "restricted-device|" + url.absoluteString)
            try CredentialStore.save(Data(secret.utf8), "restricted|" + url.absoluteString)
            try CredentialStore.save(Data(url.absoluteString.utf8), "last-endpoint")
            endpoint = url.absoluteString; restrictedMode = true; invitationText = ""; refreshSavedCredential()
            status = "Phone enrolled. Ask the owner to approve device " + String(device.suffix(8)) + ", then Connect."
        } catch { if connectionID == enrollmentGeneration { status = safe(error) } }
    }
    func refreshAccess() async {
        guard canManageAccess, let transport else { return }
        let current = connectionID
        do {
            let result = try await transport.request("voxkin.access.list", [:])
            guard current == connectionID, canManageAccess else { return }
            accessAgents = result["agents"] as? [String] ?? []
            if !accessAgents.contains(inviteAgent) { inviteAgent = accessAgents.first ?? "" }
            func rows(_ key: String) -> [[String:String]] { (result[key] as? [[String:Any]] ?? []).map { r in r.reduce(into: [String:String]()) { if let v = $1.value as? String { $0[$1.key] = v } } } }
            accessDevices = rows("devices"); accessInvites = rows("invites")
        } catch { if current == connectionID { status = safe(error) } }
    }
    func manageAccess(_ action: String, id: String = "") async {
        guard canManageAccess, let transport else { return }
        let current = connectionID, origin = endpoint
        do {
            var params: [String:Any] = [:]
            if action == "invite" { params = ["agentId":inviteAgent,"label":inviteLabel] }
            else { params[action == "cancel" ? "inviteId" : "deviceId"] = id }
            let result = try await transport.request("voxkin.access." + action, params)
            guard current == connectionID, canManageAccess else { return }
            if action == "invite", let secret = result["inviteSecret"] as? String {
                _ = try RestrictedGateway.route(try Endpoint.validate(origin))
                var fragment = URLComponents(); fragment.queryItems = [URLQueryItem(name:"gateway",value:origin),URLQueryItem(name:"secret",value:secret)]
                shareInvitation = "voxkin://join#" + (fragment.percentEncodedQuery ?? "")
            }
            await refreshAccess()
        } catch { if current == connectionID { status = safe(error) } }
    }
    @Published var acceptedFallback = false {
        didSet {
            if !acceptedFallback { clearAcknowledgment() }
            guard !restoringSpeechAcceptance, mockFactory == nil, let url = try? Endpoint.validate(endpoint) else { return }
            do {
                if acceptedFallback { try CredentialStore.save(Data([1]), "speech-consent|" + url.absoluteString) }
                else { try CredentialStore.remove("speech-consent|" + url.absoluteString) }
            } catch { status = "Could not save speech acceptance. Unlock this device and try again." }
        }
    }
    private var restoringSpeechAcceptance = false
    @Published var selectedAgent = ""
    @Published var agents: [String] = []
    @Published var draft = ""
    @Published private(set) var conversationMode = false
    private var conversationPauseTask: Task<Void, Never>?
    private var conversationResumeTask: Task<Void, Never>?
    private var conversationGeneration = UUID()
    private let conversationPauseNanoseconds: UInt64
    private let conversationResumeNanoseconds: UInt64
    @Published var finishingSpeech = false
    @Published private(set) var microphoneReady = false
    private var sendAfterFinishing = false
    @Published var reply = ""
    @Published var sentText = ""
    @Published var turnStartedAt: Date?
    @Published var timing = TurnTiming()
    private var transcriptionStartedAt: TimeInterval?
    private var transcriptionDuration: TimeInterval?
    private var subscribedSession: String?
    private var subscriptionTask: Task<Void, Never>?
    private var subscriptionTarget: String?
    private var subscriptionGeneration = UUID()
    private func feedback(_ style: UIImpactFeedbackGenerator.FeedbackStyle) {
        guard mockFactory == nil else { return }
        UIImpactFeedbackGenerator(style: style).impactOccurred()
    }
    @Published var interim = ""
    @Published var status = "Add your gateway in Settings to get started."
    @Published var provider = ""
    @Published var connected = false
    @Published var busy = false
    @Published var gate = TurnGate()
    @Published var activeRun: (session: String, run: String)?
    private var transport: GatewayTransport?
    private var player: AVAudioPlayer?
    private var playbackCompletion: CheckedContinuation<Void, Error>?
    private var speechPrefetch: Task<AudioClip, Error>?
    private let capture: SpeechCapturing
    private var turnTask: Task<Void, Never>?
    private var turnDeadline: Task<Void, Never>?
    private var connectionID = UUID()
    private var sessionSuffix = UUID().uuidString.lowercased()
    @Published var conversations: [ConversationSummary] = []
    @Published var historyMessages: [ConversationMessage] = []
    @Published var historyLoading = false
    @Published var historyError = ""
    @Published var historyHasMore = false
    @Published var messagesHaveMore = false
    @Published var historyAvailable = false
    @Published var historyActionsAvailable = false
    private var historyPreviewAvailable = false
    @Published var historySelection = ""
    @Published var historyReady = true
    private var historyOffset = 0
    private var messageOffset = 0
    private var historyCursor: String?
    private var messageCursor: String?
    private var historyGeneration = UUID()
    private var currentSession: String { VoxkinHistory.session(agent: selectedAgent, id: sessionSuffix) }
    var historyUIContext: String { "\(connectionID)|\(selectedAgent)" }
    private var historyPreferenceKey: String { "voxkin-last-conversation|\(restrictedMode)|\(restrictedMode ? enrolledDeviceID : "owner")|\(endpoint)|\(selectedAgent)" }

    private var seenFinal = false
    private var supportsMessages = false
    private var interimIDs = Set<String>()
    private var notices: [String] = []
    private var streamingText = ""
    private func refreshInterim() { interim = String((notices + (streamingText.isEmpty ? [] : [streamingText])).joined(separator: "\n\n").suffix(16_000)) }
    private var observers: [NSObjectProtocol] = []
    private let restoresSavedConnection: Bool
    private let mockFactory: (@MainActor () -> GatewayTransport)?
    init(restoreConnection: Bool = true, preferences: UserDefaults = .standard, capture: SpeechCapturing? = nil, mockMethods: [String] = [], conversationPauseNanoseconds: UInt64 = 2_000_000_000, conversationResumeNanoseconds: UInt64 = 350_000_000, mockFactory: (@MainActor () -> GatewayTransport)? = nil) {
        self.restoresSavedConnection = restoreConnection
        self.conversationPauseNanoseconds = conversationPauseNanoseconds
        self.conversationResumeNanoseconds = conversationResumeNanoseconds
        self.preferences = preferences
        self.mockMethods = mockMethods
        self.mockFactory = mockFactory
        self.capture = capture ?? SpeechCapture()
        super.init()
        spokenAcknowledgment = preferences.object(forKey: "spoken-acknowledgment") as? Bool ?? true
        if let raw = preferences.string(forKey: "reply-length"), let saved = ReplyLength(rawValue: raw) {
            replyLength = saved
        } else {
            replyLength = (preferences.object(forKey: "concise-replies") as? Bool ?? true) ? .brief : .full
        }
        if mockFactory != nil { acceptedFallback = true; supportsMessages = true }
        if restoreConnection { restoreSavedConnection() }
        observers.append(NotificationCenter.default.addObserver(forName: UIApplication.protectedDataDidBecomeAvailableNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in
                guard let self, self.mockFactory == nil else { return }
                self.restoreSavedConnection()
                self.refreshSavedCredential()
                self.restoreSpeechAcceptance()
            }
        })
        for name in [AVAudioSession.interruptionNotification, AVAudioSession.routeChangeNotification, AVAudioSession.mediaServicesWereResetNotification] {
            observers.append(NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { [weak self] notification in
                if name == AVAudioSession.routeChangeNotification {
                    let reason = (notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? NSNumber)?.uintValue
                    guard reason != AVAudioSession.RouteChangeReason.categoryChange.rawValue else { return }
                }
                if name == AVAudioSession.interruptionNotification {
                    let type = (notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.uintValue
                    guard type == AVAudioSession.InterruptionType.began.rawValue else { return }
                }
                Task { @MainActor in self?.interrupt() }
            })
        }
    }
    private func restoreSavedConnection() {
        guard restoresSavedConnection, mockFactory == nil, endpoint.isEmpty else { return }
        do {
            if let raw = try CredentialStore.load("last-endpoint"), let saved = String(data: raw, encoding: .utf8) {
                endpoint = try Endpoint.validate(saved).absoluteString
                refreshSavedCredential(); restoreSpeechAcceptance()
                status = "Disconnected — saved gateway ready."
            }
        } catch { status = "Saved connection unavailable. Open Voxkin on the unlocked iPhone when parked." }
    }
    func foreground() async {
        restoreSavedConnection()
        refreshSavedCredential()
        if mockFactory == nil { restoreSpeechAcceptance() }
        guard !connected, !busy, pairingApproval == nil, hasSavedCredential || mockFactory != nil else { return }
        await connect()
    }
    func connect() async {
        guard !connected, !busy else { return }
        let enteredToken = bootstrap
        disconnect(); bootstrap = enteredToken; busy = true; status = "Connecting to gateway…"
        let current = connectionID
        defer { if connectionID == current { busy = false } }
        do {
            let transport: GatewayTransport
            if let mockFactory { transport = mockFactory(); historyAvailable = mockMethods.contains("chat.history"); historyActionsAvailable = mockMethods.contains("sessions.patch") && mockMethods.contains("sessions.delete"); historyPreviewAvailable = mockMethods.contains("sessions.preview"); supportsLocalVoice = ["voxkin.catalog", "voxkin.speak"].allSatisfy(mockMethods.contains) }
            else if restrictedMode {
                let url = try Endpoint.validate(endpoint)
                guard let data = try CredentialStore.load("restricted|" + url.absoluteString), let secret = String(data:data, encoding:.utf8) else { throw VoiceError.invalid("Join with a Voxkin invitation first.") }
                let gateway = try RestrictedGateway(url:url,token:secret)
                self.transport = gateway
                try await gateway.connect()
                guard connectionID == current else { gateway.close(); return }
                supportsMessages = false; supportsLocalVoice = true; historyAvailable = gateway.supportsHistory; historyActionsAvailable = gateway.supportsHistoryActions
                transport = gateway
            } else {
                let url = try Endpoint.validate(endpoint)
                let account = "identity|" + url.absoluteString
                let identity = try DeviceIdentity(raw: CredentialStore.load(account))
                try CredentialStore.save(identity.raw, account)
                let grantAccount = "grant|" + url.absoluteString
                let saved = try CredentialStore.load(grantAccount).map { try JSONDecoder().decode(SavedGrant.self, from: $0) }
                guard let credential = try CredentialStore.connectionCredential(url, enteredToken: bootstrap), !credential.token.isEmpty else {
                    throw VoiceError.invalid("Enter a gateway pairing token. Provider keys never belong here.")
                }
                try CredentialStore.save(Data(url.absoluteString.utf8), "last-endpoint")
                refreshSavedCredential()
                let gateway = Gateway(); self.transport = gateway
                let hello = try await gateway.connect(url: url, identity: identity, token: credential.token, scopes: requestAdmin ? ["operator.read", "operator.write", "operator.admin"] : credential.scopes)
                guard connectionID == current else { gateway.close(); return }
                let auth = hello["auth"] as? [String: Any] ?? [:]
                let scopes = auth["scopes"] as? [String] ?? []
                adminPermissionGranted = scopes.contains("operator.admin")
                guard auth["role"] as? String == "operator", scopes.contains("operator.read"), scopes.contains("operator.write") else { throw VoiceError.invalid("Gateway did not grant the requested read/write permissions.") }
                if let issued = auth["deviceToken"] as? String, !issued.isEmpty {
                    let grant = SavedGrant(token: issued, scopes: issued == saved?.token ? (saved?.scopes ?? scopes) : scopes)
                    try CredentialStore.save(JSONEncoder().encode(grant), grantAccount)
                    try CredentialStore.remove("bootstrap|" + url.absoluteString)
                }
                bootstrap = ""; refreshSavedCredential()
                let features = hello["features"] as? [String: Any] ?? [:]
                let methods = features["methods"] as? [String] ?? []
                historyAvailable = ["sessions.list", "chat.history"].allSatisfy(methods.contains)
                historyActionsAvailable = ["sessions.patch", "sessions.delete"].allSatisfy(methods.contains)
                historyPreviewAvailable = methods.contains("sessions.preview")
                supportsMessages = methods.contains("sessions.messages.subscribe")
                supportsLocalVoice = ["voxkin.catalog", "voxkin.speak"].allSatisfy(methods.contains)
                guard ["agents.list", "chat.send", "chat.abort", "tts.speak"].allSatisfy(methods.contains) else { throw VoiceError.invalid("Gateway lacks a required prototype method. No alternate voice route will be used.") }
                supportsAccessManagement = methods.contains("voxkin.access.list")
                transport = gateway
            }
            self.transport = transport
            restoreVoiceSelection()
            transport.onClose = { [weak self] in
                guard let self, self.connectionID == current else { return }
                self.disconnectedUnexpectedly()
            }
            transport.onEvent = { [weak self] name, payload in
                guard let self, self.connectionID == current else { return }
                self.event(name, payload)
            }
            let result = try await transport.request("agents.list", [:])
            guard connectionID == current else { return }
            agents = (result["agents"] as? [[String: Any]] ?? []).compactMap { $0["id"] as? String }.filter { $0.range(of: "^[a-zA-Z0-9_-]+$", options: .regularExpression) != nil }
            guard !agents.isEmpty else { throw VoiceError.invalid("No selectable agents were returned.") }
            selectedAgent = agents[0]; connected = true; gate.connected()
            await restoreHistorySession()
            guard connectionID == current, connected else { return }
            status = "Ready when you are."
            discoverVoices(transport: transport, connection: current)
            prepareMessageSubscription()
        } catch {
            guard connectionID == current else { return }
            disconnect(); gate.invalidate(.failed); pairingApproval = error as? GatewayPairingRequired; status = safe(error)
        }
    }
    private func discoverVoices(transport: GatewayTransport, connection: UUID) {
        guard supportsLocalVoice else {
            voiceCatalogStatus = "Gateway default voice. Install the optional local-voice plugin on your gateway for language and voice choices."
            return
        }
        voiceCatalogStatus = "Discovering local voices…"
        catalogTask = Task {
            do {
                let catalog = try await transport.request("voxkin.catalog", [:])
                guard !Task.isCancelled, connectionID == connection else { return }
                localVoices = try LocalVoice.catalog(catalog)
                if restrictedMode && selectedVoice == nil, let first = localVoices.first { selectVoice(first.id) }
                voiceCatalogStatus = "Local voices are provided by your gateway’s optional plugin. No cloud fallback."
            } catch {
                guard !Task.isCancelled, connectionID == connection else { return }
                localVoices = []; voiceCatalogStatus = "Local voices unavailable. Check plugin setup on your gateway; reconnect to retry."
            }
        }
    }
    func disconnect() {
        pairingApproval = nil
        enrollmentTransport?.close(); enrollmentTransport = nil
        catalogTask?.cancel(); catalogTask = nil
        adminPermissionGranted = false; supportsAccessManagement = false; shareInvitation = ""; accessDevices = []; accessInvites = []
        localVoices = []; supportsLocalVoice = false; turnVoice = nil
        historyGeneration = UUID(); historyLoading = false; historyAvailable = false; historyActionsAvailable = false; historyPreviewAvailable = false; historyReady = true; conversations = []; historyMessages = []; historySelection = ""; historyError = ""; historyHasMore = false; messagesHaveMore = false
        connectionID = UUID(); busy = false; connected = false; subscriptionTask?.cancel(); subscriptionTask = nil; subscriptionTarget = nil; subscriptionGeneration = UUID(); subscribedSession = nil; timing = TurnTiming()
        transport?.onClose = nil; transport?.onEvent = nil; transport?.close(); transport = nil
        stopLocal(); gate.invalidate(.disconnected); agents = []; selectedAgent = ""; reply = ""; interim = ""; sentText = ""; turnStartedAt = nil; draft = ""; provider = ""; bootstrap = ""
        activeRun = nil; sessionSuffix = UUID().uuidString.lowercased(); status = "Disconnected. No requests will be replayed."
    }
    private func disconnectedUnexpectedly() {
        disconnect()
        status = "Connection lost; agent outcome may be unknown. Reconnect manually. Nothing is replayed."
    }
    func restoreSpeechAcceptance() {
        restoringSpeechAcceptance = true
        defer { restoringSpeechAcceptance = false }
        guard mockFactory == nil, let url = try? Endpoint.validate(endpoint) else { acceptedFallback = false; return }
        acceptedFallback = (try? CredentialStore.load("speech-consent|" + url.absoluteString)) == Data([1])
    }
    func refreshSavedCredential() {
        guard let url = try? Endpoint.validate(endpoint) else { hasSavedCredential = false; return }
        enrolledDeviceID = (try? CredentialStore.load("restricted-device|" + url.absoluteString)).flatMap { String(data:$0,encoding:.utf8) } ?? ""
        if ((try? CredentialStore.load("restricted|" + url.absoluteString)) != nil) { restrictedMode = true; hasSavedCredential = true; return }
        restrictedMode = false
        hasSavedCredential = ((try? CredentialStore.load("grant|" + url.absoluteString)) != nil)
            || ((try? CredentialStore.load("bootstrap|" + url.absoluteString)) != nil)
    }
    func forget() {
        let raw = endpoint; disconnect()
        do {
            let url = try Endpoint.validate(raw)
            try CredentialStore.remove("restricted-device|" + url.absoluteString)
            try CredentialStore.remove("restricted|" + url.absoluteString)
            try CredentialStore.remove("grant|" + url.absoluteString)
            try CredentialStore.remove("identity|" + url.absoluteString)
            try CredentialStore.remove("bootstrap|" + url.absoluteString)
            try CredentialStore.remove("speech-consent|" + url.absoluteString)
            try CredentialStore.remove("local-voice|" + url.absoluteString)
            selectedVoiceID = ""
            if let saved = try CredentialStore.load("last-endpoint"), String(data: saved, encoding: .utf8) == url.absoluteString {
                try CredentialStore.remove("last-endpoint")
            }
            endpoint = ""; refreshSavedCredential()
            status = "Local identity forgotten. This does not revoke access on the gateway; revoke the old device there."
        } catch { status = safe(error) }
    }
    func changeAgent() {
        activeRun = nil; stopLocal(); gate.invalidate(connected ? .ready : .disconnected); sentText = ""; interim = ""; notices = []; streamingText = ""; interimIDs = []; turnStartedAt = nil; reply = ""; draft = ""; provider = ""
        historyGeneration = UUID(); historyLoading = false; historyMessages = []; conversations = []; historySelection = ""
        sessionSuffix = UUID().uuidString.lowercased(); status = "Selected agent changed."
        Task { await restoreHistorySession() }
        prepareMessageSubscription()
    }
    /// Optional interim updates must never gate submission of the user's message.
    private func prepareMessageSubscription() {
        guard connected, supportsMessages, let transport else { return }
        let session = currentSession
        guard subscribedSession != session, subscriptionTarget != session else { return }
        subscriptionTask?.cancel(); subscriptionTarget = session
        let generation = UUID(); subscriptionGeneration = generation
        let agent = selectedAgent, connection = connectionID
        subscriptionTask = Task {
            defer { if self.subscriptionGeneration == generation { self.subscriptionTask = nil; self.subscriptionTarget = nil } }
            do {
                try Task.checkCancellation()
                _ = try await transport.request("sessions.messages.subscribe", ["key": session, "agentId": agent, "subscriptionId": "voxkin"])
                guard !Task.isCancelled, self.connectionID == connection, self.subscriptionGeneration == generation else { return }
                self.subscribedSession = session
            } catch { /* Interim text is optional; normal chat/final events remain available. */ }
        }
    }
    func send() {
        guard connected, !busy, !historyLoading, historyReady, activeRun == nil, let transport, agents.contains(selectedAgent), !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        guard draft.count <= 4000 else { status = "Keep messages under 4,000 characters."; return }
        guard mockFactory != nil || acceptedFallback else { status = "Accept the gateway speech-provider/fallback policy before a live turn."; return }
        guard voiceSelectionValid else { status = "Your saved local voice is unavailable. Choose a voice or Gateway default in Settings."; return }
        if restrictedMode && historySelection.isEmpty { status = "Create or select a conversation in History first."; return }
        preferences.set(sessionSuffix, forKey: historyPreferenceKey)
        let voice = selectedVoice
        let text = draft
        let isSpoken = conversationMode || (!spokenDraft.isEmpty && draft == spokenDraft)
        // Per-request preference, not a session/global configuration change.
        let progressGuidance = isSpoken && spokenAcknowledgment && supportsMessages ? " For quick questions, answer directly without acknowledgment or preamble. Only if work will take time, send at most one short natural contextual opening through the supported user-visible source-reply channel (under 180 characters), describing only work you are actually starting. Do not say Got it or narrate internal reasoning. Do not repeat that opening in the final answer. If clarification is needed, ask it directly as your answer rather than announcing that you will ask." : ""
        let style = replyLength.guidance + progressGuidance + (voice.map { " Respond in " + ($0.language == "es" ? "Spanish" : "English") + " unless the user explicitly requests another language. Speech will use the selected voice’s language." } ?? "")
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let isCommand = trimmed.hasPrefix("/") || trimmed.hasPrefix("!")
        let requestText = isCommand ? text : "[Voxkin reply preference for this turn only: \(style)]\n\n" + text
        let run = UUID().uuidString, session = currentSession
        do {
            let epoch = try gate.begin(session: session, run: run)
            activeRun = (session, run); turnVoice = voice
            turnWasSpoken = isSpoken; spokenDraft = ""; turnAccepted = false; pendingProgressText = nil; progressStarted = false
            prepareMessageSubscription()
            timing.sent(transcription: transcriptionDuration, endOfTurn: lastRecognitionAt.map { ProcessInfo.processInfo.systemUptime - $0 }); lastRecognitionAt = nil; transcriptionDuration = nil; feedback(.light)
            interimIDs = []; notices = []; streamingText = ""; seenFinal = false; sentText = text; draft = ""; interim = ""; turnStartedAt = Date(); reply = ""; provider = ""; status = "Waiting for the selected agent…"
            let agent = selectedAgent
            turnTask = Task {
                do {
                    guard !Task.isCancelled, self.gate.epoch == epoch else { return }
                    let response = try await transport.request("chat.send", ["sessionKey": session, "agentId": agent, "message": requestText, "idempotencyKey": run, "deliver": false])
                    guard self.gate.epoch == epoch else { return }
                    if let accepted = response["runId"] as? String, accepted != run { throw VoiceError.invalid("Unexpected run ID; outcome may be unknown. No replay.") }
                    if response["runId"] as? String == run { self.turnAccepted = true; self.acknowledgeAcceptedTurn(epoch: epoch); self.speakProgressIfReady() }
                } catch { self.fail(error, epoch: epoch) }
            }
            turnDeadline = Task {
                try? await Task.sleep(nanoseconds: 120_000_000_000)
                guard !Task.isCancelled, self.gate.epoch == epoch else { return }
                self.fail(VoiceError.invalid("Turn timed out. Agent outcome may be unknown; nothing was resent."), epoch: epoch)
            }
        } catch { status = safe(error) }
    }
    private func event(_ name: String, _ payload: [String: Any]) {
        if name == "session.message" {
            guard let session = payload["sessionKey"] as? String,
                  let message = payload["message"] as? [String: Any] else { return }
            let meta = message["__openclaw"] as? [String: Any]
            let outerRun = payload["runId"] as? String, nestedRun = meta?["runId"] as? String
            // Persisted commentary may carry the run only inside transcript metadata.
            // Conflicting IDs are rejected; never guess from session-level lastRunId.
            if let outerRun, let nestedRun, outerRun != nestedRun { return }
            guard let run = outerRun ?? nestedRun, gate.accepts(session: session, run: run), !seenFinal,
                  let messageID = payload["messageId"] as? String,
                  let text = ChatText.interim(message) else { return }
            let fallback = message["openclawStreamFallback"] as? [String: Any]
            let id = (fallback?["itemId"] as? String).map { "segment:" + $0 } ?? "message:" + messageID
            guard interimIDs.count < 100, !interimIDs.contains(id) else { return }
            timing.update(); interimIDs.insert(id); notices.append(String(text.prefix(4000)))
            while notices.joined().count > 12_000 { notices.removeFirst() }
            refreshInterim(); status = "Your agent is working…"
            if pendingProgressText == nil, text.count <= 180, !text.contains("```"), !text.contains("://") {
                pendingProgressText = text
                speakProgressIfReady()
            }
            return
        }
        guard name == "chat", let run = payload["runId"] as? String, let session = payload["sessionKey"] as? String else { return }
        if activeRun?.session == session, activeRun?.run == run, ["final", "error", "aborted"].contains(payload["state"] as? String ?? "") { activeRun = nil }
        guard gate.accepts(session: session, run: run) else { return }
        let epoch = gate.epoch
        if payload["state"] as? String == "delta", !seenFinal {
            timing.update()
            if let message = payload["message"] as? [String: Any], let text = ChatText.text(message) { streamingText = text }
            else if let delta = payload["deltaText"] as? String {
                streamingText = payload["replace"] as? Bool == true ? delta : streamingText + delta
            }
            streamingText = String(streamingText.prefix(16_000)); refreshInterim(); status = "Your agent is replying…"; return
        }
        if payload["state"] as? String == "status" {
            switch payload["phase"] as? String {
            case "waiting_for_state": status = "Waiting for the gateway…"
            case "starting_model": status = "Your agent is thinking…"
            case "preparing_context", "memory_flushing": status = "Getting ready…"
            default: status = "Your agent is working…"
            }
            return
        }
        if ["error", "aborted"].contains(payload["state"] as? String ?? "") { fail(VoiceError.invalid("Agent run ended without a complete reply."), epoch: epoch); return }
        guard payload["state"] as? String == "final", !seenFinal else { return }
        let text = ChatText.final(payload) ?? ""
        stopAcknowledgment(); cancelAcknowledgmentPreparation()
        timing.final(); seenFinal = true; streamingText = ""; refreshInterim(); reply = text
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            stopConversationScheduling(); turnDeadline?.cancel(); gate.invalidate(.ready)
            status = "Request finished without a text reply. Ready for your next message."
            return
        }
        guard let transport else { return }
        guard text.count <= 4000 else { fail(VoiceError.invalid("Reply exceeds the 4,000-character speech limit; read the text instead."), epoch: epoch); return }
        let voice = turnVoice
        status = "Preparing gateway speech…"
        turnTask = Task {
            do {
                guard !Task.isCancelled, self.gate.epoch == epoch else { return }
                let chunks = SpeechChunks.split(SpeechText.prepare(text)).filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
                guard !chunks.isEmpty else { throw VoiceError.invalid("The reply contains no spoken text.") }
                let firstClip = try await LocalVoice.synthesize(chunks[0], voice: voice, transport: transport, received: { if self.gate.epoch == epoch { self.timing.speechReceived() } })
                guard !Task.isCancelled, self.gate.epoch == epoch else { return }
                var clip = firstClip
                for index in chunks.indices {
                    guard !Task.isCancelled, self.gate.epoch == epoch else { return }
                    let next: Task<AudioClip, Error>? = index + 1 < chunks.count ? Task {
                        try Task.checkCancellation()
                        return try await LocalVoice.synthesize(chunks[index + 1], voice: voice, transport: transport)
                    } : nil
                    self.speechPrefetch = next
                    try await self.play(clip, epoch: epoch)
                    guard !Task.isCancelled, self.gate.epoch == epoch else { return }
                    if let next {
                        self.status = "Preparing the next sentence…"
                        clip = try await next.value
                    }
                    self.speechPrefetch = nil
                }
                guard self.gate.epoch == epoch else { return }
                self.gate.invalidate(.ready)
                try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
                self.status = "Ready when you are."
                self.resumeConversationAfterReply()
            } catch { self.fail(error, epoch: epoch) }
        }
    }
    func stopPlayback() {
        feedback(.soft); stopLocal(); gate.invalidate(connected ? .interrupted : .disconnected)
        status = activeRun == nil ? "Stopped. Tap the mic when you’re ready." : "Audio stopped. Your agent is still working—use Cancel request to ask it to stop."
    }
    func cancelAgent() async {
        guard let activeRun, let transport else { return }
        let run = activeRun.run, session = activeRun.session
        let connection = connectionID
        stopLocal(); gate.invalidate(.interrupted)
        do {
            _ = try await transport.request("chat.abort", ["sessionKey": session, "runId": run])
            if connectionID == connection, self.activeRun?.session == session, self.activeRun?.run == run { self.activeRun = nil; status = "Cancellation requested for this run only. Completed side effects cannot be undone." }
        } catch { if connectionID == connection, self.activeRun?.session == session, self.activeRun?.run == run { status = safe(error) } }
    }
    var canStartConversation: Bool {
        connected && !busy && activeRun == nil && acceptedFallback && voiceSelectionValid &&
        agents.contains(selectedAgent) && draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        (gate.state == .ready || gate.state == .interrupted)
    }
    var canInterruptConversationReply: Bool {
        conversationMode && connected && activeRun == nil && gate.state == .speaking
    }
    func startConversation() async {
        guard !conversationMode, canStartConversation else { return }
        conversationGeneration = UUID(); conversationMode = true
        feedback(.medium)
        await record()
    }
    private func stopConversationScheduling() {
        clearAcknowledgment()
        conversationMode = false; conversationGeneration = UUID()
        conversationPauseTask?.cancel(); conversationPauseTask = nil
        conversationResumeTask?.cancel(); conversationResumeTask = nil
    }
    func endConversation() {
        guard conversationMode else { return }
        stopPlayback()
        status = activeRun == nil ? "Conversation ended. Microphone off." : "Conversation ended. Agent may still be working; use Cancel request to stop it."
    }
    func interruptConversationReply() async {
        guard canInterruptConversationReply else { return }
        stopLocal(endConversation: false); gate.invalidate(.ready)
        let generation = conversationGeneration
        status = "Reply interrupted. Listening again shortly…"
        try? await Task.sleep(nanoseconds: conversationResumeNanoseconds)
        guard !Task.isCancelled, conversationMode, conversationGeneration == generation else { return }
        guard canStartConversation else {
            stopConversationScheduling()
            status = "Conversation paused. Review your draft or start again when ready."
            return
        }
        await record()
    }
    private func scheduleConversationPause(epoch: UUID) {
        guard conversationMode, !finishingSpeech else { return }
        conversationPauseTask?.cancel()
        let generation = conversationGeneration
        conversationPauseTask = Task { [weak self] in
            guard let self else { return }
            try? await Task.sleep(nanoseconds: self.conversationPauseNanoseconds)
            guard !Task.isCancelled, self.conversationMode, self.conversationGeneration == generation,
                  self.gate.epoch == epoch, self.gate.state == .recording else { return }
            self.finishAndSend()
        }
    }
    private func resumeConversationAfterReply() {
        guard conversationMode else { return }
        let generation = conversationGeneration
        status = "Conversation on. Listening again shortly…"
        conversationResumeTask = Task { [weak self] in
            guard let self else { return }
            try? await Task.sleep(nanoseconds: self.conversationResumeNanoseconds)
            guard !Task.isCancelled, self.conversationMode, self.conversationGeneration == generation else { return }
            guard self.canStartConversation else {
                self.stopConversationScheduling()
                self.status = "Conversation paused. Review your draft or start again when ready."
                return
            }
            await self.record()
        }
    }
    func record() async {
        guard connected, activeRun == nil, gate.state == .ready || gate.state == .interrupted else { return }
        guard voiceSelectionValid else { status = "Choose an available voice in Settings before recording."; return }
        stopLocal(endConversation: false); gate.invalidate(.ready); gate.recorded(true)
        let epoch = gate.epoch; draft = ""; spokenDraft = ""; lastRecognitionAt = nil; transcriptionDuration = nil; transcriptionStartedAt = nil; status = "Opening microphone…"
        do {
            try await capture.start(locale: selectedVoice.map { Locale(identifier: $0.locale) } ?? .current, onText: { [weak self] text in
                guard let self, self.gate.epoch == epoch else { return }
                // Recognition can finish with an empty result after useful partial words.
                // Keep the last nonempty hypothesis for this capture; new recordings clear it.
                guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                guard self.draft != text else { return }
                self.lastRecognitionAt = ProcessInfo.processInfo.systemUptime
                self.draft = text; self.spokenDraft = text
                self.scheduleConversationPause(epoch: epoch)
            }, onEnd: { [weak self] message in
                guard let self, self.gate.epoch == epoch else { return }
                let shouldSend = (self.sendAfterFinishing || self.conversationMode) && message == nil
                self.microphoneReady = false; self.sendAfterFinishing = false
                self.conversationPauseTask?.cancel(); self.conversationPauseTask = nil
                self.finishingSpeech = false
                if let start = self.transcriptionStartedAt { self.transcriptionDuration = ProcessInfo.processInfo.systemUptime - start }
                self.transcriptionStartedAt = nil
                self.gate.recorded(false)
                let hasWords = !self.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                self.status = message ?? (hasWords ? "Review your draft before sending." : "No words captured. Tap the mic to try again, or type a message.")
                if shouldSend && hasWords { self.send() }
                else if self.conversationMode { self.stopConversationScheduling() }
            })
            guard gate.epoch == epoch, gate.state == .recording else { return }
            microphoneReady = true
            prepareAcknowledgment()
            feedback(.soft); status = conversationMode ? "Listening… Pause about 2 seconds to send. Stop ends conversation." : "Listening… Tap Stop to review your words."
        } catch { fail(error, epoch: epoch) }
    }
    func clearDraft() {
        guard activeRun == nil, !busy,
              gate.state == .recording || gate.state == .ready || gate.state == .interrupted else { return }
        stopConversationScheduling(); capture.stop(); finishingSpeech = false; microphoneReady = false; sendAfterFinishing = false
        transcriptionStartedAt = nil; transcriptionDuration = nil
        gate.invalidate(connected ? .ready : .disconnected)
        draft = ""; status = "Draft cleared. Tap the mic or type to start again."
    }
    func finishRecording() {
        guard gate.state == .recording, microphoneReady, !finishingSpeech else { return }
        stopConversationScheduling()
        finishSpeech(send: false)
    }
    func finishAndSend() {
        finishSpeech(send: true)
    }
    private func finishSpeech(send: Bool) {
        guard gate.state == .recording, !finishingSpeech else { return }
        sendAfterFinishing = send; microphoneReady = false
        conversationPauseTask?.cancel(); conversationPauseTask = nil
        transcriptionStartedAt = ProcessInfo.processInfo.systemUptime
        finishingSpeech = true; status = "Finishing your words…"
        capture.finish()
    }
    func interrupt() {
        guard conversationMode || gate.state == .recording || gate.state == .speaking || gate.state == .waiting else { return }
        stopPlayback(); status = "Audio interrupted or route changed. Resume explicitly; nothing auto-plays."
    }
    func background() {
        let approval = pairingApproval
        disconnect()
        pairingApproval = approval
        if let approval { status = approval.errorDescription! }
    }
    private func stopLocal(endConversation: Bool = true) {
        stopAcknowledgment(); progressSpeechTask?.cancel(); progressSpeechTask = nil; spokenDraft = ""; turnWasSpoken = false; lastRecognitionAt = nil
        if endConversation { stopConversationScheduling() }
        conversationPauseTask?.cancel(); conversationPauseTask = nil
        previewing = false; finishingSpeech = false; microphoneReady = false; sendAfterFinishing = false; transcriptionStartedAt = nil; transcriptionDuration = nil
        speechPrefetch?.cancel(); speechPrefetch = nil
        let completion = playbackCompletion; playbackCompletion = nil
        completion?.resume(throwing: CancellationError())
        capture.stop(); player?.stop(); player = nil; turnTask?.cancel(); turnDeadline?.cancel()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
    private func fail(_ error: Error, epoch: UUID) {
        guard gate.epoch == epoch else { return }
        stopLocal(); gate.invalidate(connected ? .interrupted : .failed); status = safe(error)
    }
    private func safe(_ error: Error) -> String {
        if let pairing = error as? GatewayPairingRequired { return pairing.errorDescription! }
        let description = (error as? VoiceError)?.errorDescription ?? ""
        if description.contains("PAIRING_REQUIRED") { return "Approve this iPhone on the gateway, then tap Connect again. Your saved token is kept." }
        if description.contains("AUTH_TOKEN_MISMATCH") { return "The gateway rejected this token. Enter a replacement in Settings, then connect." }
        if let network = error as? URLError, [.notConnectedToInternet, .cannotConnectToHost, .cannotFindHost, .networkConnectionLost, .timedOut].contains(network.code) {
            return "Cannot reach your gateway. Check your connection and Tailscale, then tap Connect. Nothing was resent."
        }
        if !description.isEmpty { return description }
        return reply.isEmpty ? "That didn’t complete. Check your connection or type instead. Nothing was resent." : "Speech couldn’t play. Your reply is still above; nothing was resent."
    }
    private func play(_ clip: AudioClip, epoch: UUID) async throws {
        guard !Task.isCancelled, gate.epoch == epoch else { throw CancellationError() }
        let player = try AVAudioPlayer(data: clip.data)
        guard gate.state == .speaking || gate.speaking(epoch: epoch) else { throw CancellationError() }
        turnDeadline?.cancel(); provider = clip.provider
        let audio = AVAudioSession.sharedInstance()
        try audio.setCategory(.playback, mode: .spokenAudio); try audio.setActive(true)
        self.player = player; player.delegate = self
        try await withCheckedThrowingContinuation { (completion: CheckedContinuation<Void, Error>) in
            playbackCompletion = completion
            if player.play() { if !previewing { timing.audio() }; status = previewing ? "Playing voice preview…" : "Speaking…" }
            else { playbackCompletion = nil; completion.resume(throwing: VoiceError.invalid("Could not play gateway audio. Read the reply above.")) }
        }
    }
    nonisolated func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        Task { @MainActor in
            if self.acknowledgmentPlayer === player {
                self.stopAcknowledgment()
                return
            }
            guard self.player === player else { return }
            self.player = nil
            let completion = self.playbackCompletion; self.playbackCompletion = nil
            completion?.resume(throwing: VoiceError.invalid("Audio could not be decoded. Your reply is still above."))
        }
    }
    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in
            if self.acknowledgmentPlayer === player {
                self.stopAcknowledgment()
                return
            }
            guard self.player === player else { return }
            self.player = nil
            let completion = self.playbackCompletion; self.playbackCompletion = nil
            if flag { completion?.resume() }
            else { completion?.resume(throwing: VoiceError.invalid("Playback stopped unexpectedly. Your reply is still above.")) }
        }
    }
}

extension VoiceModel {
    func canEditConversation(_ item: ConversationSummary) -> Bool {
        connected && historyActionsAvailable && !historyLoading && activeRun == nil && !busy &&
        (restrictedMode ? UUID(uuidString: item.id) != nil : VoxkinHistory.owns(item.id, agent: selectedAgent) && item.sessionID != nil)
    }
    func renameConversation(_ item: ConversationSummary, to raw: String) async {
        guard canEditConversation(item), let transport else { return }
        let title = VoxkinHistory.cleanTitle(raw)
        guard !title.isEmpty, title.unicodeScalars.count <= 80 else { historyError = "Use a name between 1 and 80 characters."; return }
        let generation = historyGeneration, connection = connectionID, agent = selectedAgent
        historyLoading = true; historyError = ""
        defer { if historyGeneration == generation { historyLoading = false } }
        do {
            if restrictedMode {
                _ = try await transport.request("voxkin.history.rename", ["conversationId":item.id, "title":title])
            } else {
                _ = try await transport.request("sessions.patch", ["key":item.id, "agentId":agent, "label":title, "expectedSessionId":item.sessionID!])
            }
            guard historyContextIsCurrent(generation, connection, agent) else { return }
            if let index = conversations.firstIndex(where: { $0.id == item.id }) { conversations[index] = item.named(title, manual:true) }
        } catch {
            if historyContextIsCurrent(generation, connection, agent) { historyError = "Could not rename. Try a unique name, or refresh and retry. " + safe(error) }
        }
    }
    func deleteConversation(_ item: ConversationSummary) async {
        guard canEditConversation(item), let transport else { return }
        let generation = historyGeneration, connection = connectionID, agent = selectedAgent
        historyLoading = true; historyError = ""
        defer { if historyGeneration == generation { historyLoading = false } }
        do {
            if restrictedMode {
                _ = try await transport.request("voxkin.history.delete", ["conversationId":item.id])
            } else {
                let target: [String: Any] = ["key":item.id, "agentId":agent, "expectedSessionId":item.sessionID!]
                var archive = target; archive["archived"] = true
                _ = try await transport.request("sessions.patch", archive)
                do {
                    guard historyContextIsCurrent(generation, connection, agent) else { throw CancellationError() }
                    var deletion = target; deletion["archivedOnly"] = true; deletion["deleteTranscript"] = true
                    _ = try await transport.request("sessions.delete", deletion)
                } catch {
                    // A failed second step must not silently hide the conversation.
                    var restore = target; restore["archived"] = false
                    do { _ = try await transport.request("sessions.patch", restore) }
                    catch {
                        if historyContextIsCurrent(generation, connection, agent) {
                            historyError = "Could not confirm deletion. Refresh History; the conversation may have been removed or archived on the gateway."
                        }
                        return
                    }
                    throw error
                }
            }
            guard historyContextIsCurrent(generation, connection, agent) else { return }
            conversations.removeAll { $0.id == item.id }
            // Restart list pagination: deleting a row shifts offsets/cursors.
            historyOffset = 0; historyCursor = nil; historyHasMore = false
            if historySelection == item.id || (!restrictedMode && currentSession == item.id) {
                endConversation(); stopLocal(); gate.invalidate(.ready)
                preferences.removeObject(forKey:historyPreferenceKey)
                sessionSuffix = UUID().uuidString.lowercased(); historySelection = ""
                historyReady = !restrictedMode; historyMessages = []; messagesHaveMore = false
                sentText = ""; reply = ""; interim = ""; draft = ""
                prepareMessageSubscription()
            }
            status = "Conversation removed from History."
            historyLoading = false
            await loadConversations()
        } catch {
            if historyContextIsCurrent(generation, connection, agent) { historyError = "Could not delete the conversation. Refresh and retry. " + safe(error) }
        }
    }
    private func historyContextIsCurrent(_ generation: UUID, _ connection: UUID, _ agent: String) -> Bool {
        connected && historyGeneration == generation && connectionID == connection && selectedAgent == agent
    }
    private func restoreHistorySession() async {
        historyError = ""; historyMessages = []; conversations = []; historySelection = ""; historyReady = !restrictedMode
        guard historyAvailable else { return }
        if let saved = preferences.string(forKey: historyPreferenceKey), UUID(uuidString: saved) != nil {
            sessionSuffix = saved; historySelection = restrictedMode ? saved : currentSession; historyReady = false
            if historyAvailable { await loadHistoryMessages() }
        } else if restrictedMode && historyAvailable {
            await newConversation()
        }
    }
    func newConversation() async {
        guard connected, !historyLoading, activeRun == nil, let transport else { return }
        endConversation(); stopLocal()
        historyGeneration = UUID()
        let generation = historyGeneration, connection = connectionID, agent = selectedAgent
        historyLoading = true; historyError = ""
        defer { if historyGeneration == generation { historyLoading = false } }
        do {
            var id = UUID().uuidString.lowercased()
            if restrictedMode {
                let value = try await transport.request("voxkin.history.create", [:])
                guard let newID = value["id"] as? String, UUID(uuidString: newID) != nil else { throw VoiceError.invalid("Invalid conversation identity.") }
                id = newID
            }
            guard historyContextIsCurrent(generation, connection, agent) else { return }
            sessionSuffix = id; historySelection = restrictedMode ? id : currentSession; historyReady = true
            preferences.set(id, forKey: historyPreferenceKey)
            historyMessages = []; sentText = ""; reply = ""; interim = ""; draft = ""; messagesHaveMore = false
            gate.invalidate(.ready); prepareMessageSubscription(); status = "New conversation ready."
        } catch { if historyContextIsCurrent(generation, connection, agent) { historyError = safe(error) } }
    }
    func loadConversations(more: Bool = false) async {
        guard connected, historyAvailable, !historyLoading, let transport else { return }
        let generation = historyGeneration, connection = connectionID, agent = selectedAgent
        historyLoading = true; historyError = ""
        defer { if historyGeneration == generation { historyLoading = false } }
        do {
            let offset = more ? historyOffset : 0
            var params: [String: Any]
            let result: [String: Any]
            if restrictedMode {
                params = ["limit":20]
                if more, let historyCursor { params["before"] = historyCursor }
                result = try await transport.request("voxkin.history.list", params)
            } else {
                params = ["agentId":agent, "search":"agent:\(agent):voxkin:", "limit":20, "offset":offset,
                          "includeDerivedTitles":true, "includeLastMessage":false, "includeGlobal":false,
                          "includeUnknown":false, "archived":false, "excludeSubagents":true, "excludeCron":true, "excludeSystem":true]
                result = try await transport.request("sessions.list", params)
            }
            guard historyContextIsCurrent(generation, connection, agent) else { return }
            let rows = result[restrictedMode ? "conversations" : "sessions"] as? [[String: Any]] ?? []
            var items = rows.compactMap { row -> ConversationSummary? in
                guard let id = row[restrictedMode ? "id" : "key"] as? String,
                      restrictedMode ? UUID(uuidString: id) != nil : VoxkinHistory.owns(id, agent: agent) else { return nil }
                let manual = row["label"] as? String ?? (row["customTitle"] as? String)
                let title = manual ?? row["autoLabel"] as? String ?? row["title"] as? String ?? row["derivedTitle"] as? String ?? row["displayName"] as? String ?? "Conversation"
                let cleaned = VoxkinHistory.cleanTitle(title)
                return ConversationSummary(id: id, title: cleaned.isEmpty ? "Conversation" : String(cleaned.prefix(80)),
                    preview: String((row["preview"] as? String ?? "").prefix(160)),
                    updatedAt: Date(timeIntervalSince1970: (row["updatedAt"] as? Double ?? 0) / 1000),
                    sessionID: row["sessionId"] as? String, manuallyNamed: manual != nil)
            }
            // One bounded batch, never download every transcript to populate the list.
            // Preview is a recent window; explicit gateway names always win.
            let stableNames = Set(rows.compactMap { row -> String? in
                guard let title = row["autoLabel"] as? String, !VoxkinHistory.cleanTitle(title).isEmpty else { return nil }
                return row["key"] as? String
            })
            let unnamed = items.filter { !$0.manuallyNamed && !stableNames.contains($0.id) }
            if !restrictedMode, historyPreviewAvailable, !unnamed.isEmpty {
                if let preview = try? await transport.request("sessions.preview", ["keys": unnamed.map(\.id), "limit":8, "maxChars":800]),
                   let previews = preview["previews"] as? [[String: Any]] {
                    for row in previews where row["status"] as? String == "ok" {
                        guard let key = row["key"] as? String, let index = items.firstIndex(where: { $0.id == key && !$0.manuallyNamed }),
                              var messages = row["items"] as? [[String: Any]] else { continue }
                        // Gateway previews cap text at 800 characters. Spoken preferences
                        // can consume that entire budget before the user's question.
                        if messages.contains(where: { message in
                            let text = message["text"] as? String ?? ""
                            return message["role"] as? String == "user"
                                && text.hasPrefix("[Voxkin reply preference for this turn only: ")
                                && text.hasSuffix("...")
                        }) {
                            if let full = try? await transport.request("chat.history", ["sessionKey":key, "agentId":agent, "limit":8, "offset":0, "maxBytes":32_000]),
                               let rows = full["messages"] as? [[String: Any]] {
                                messages = rows
                            }
                            guard historyContextIsCurrent(generation, connection, agent) else { return }
                        }
                        guard let title = VoxkinHistory.automaticTitle(VoxkinHistory.messages(messages)) else { continue }
                        items[index] = items[index].named(title)
                        // After a real exchange, save the topic once. Later previews must
                        // not continually rename a long-running conversation.
                        if historyActionsAvailable, let sessionID = items[index].sessionID,
                           messages.filter({ $0["role"] as? String == "user" }).count >= 2,
                           !VoxkinHistory.isTrivialTitle(title) {
                            guard historyContextIsCurrent(generation, connection, agent) else { return }
                            _ = try? await transport.request("sessions.patch", ["key":key, "agentId":agent, "expectedSessionId":sessionID, "autoLabel":title])
                        }
                    }
                }
                guard historyContextIsCurrent(generation, connection, agent) else { return }
            }
            if more { let existing = Set(conversations.map(\.id)); conversations += items.filter { !existing.contains($0.id) } }
            else { conversations = items }
            historyOffset = offset + rows.count; historyCursor = result["nextCursor"] as? String
            historyHasMore = restrictedMode ? historyCursor != nil : rows.count == 20
        } catch { if historyContextIsCurrent(generation, connection, agent) { historyError = safe(error) } }
    }
    func openConversation(_ item: ConversationSummary) async {
        guard connected, !historyLoading, activeRun == nil else { return }
        let id: String
        if restrictedMode { guard UUID(uuidString: item.id) != nil else { return }; id = item.id }
        else { guard VoxkinHistory.owns(item.id, agent: selectedAgent) else { return }; id = String(item.id.split(separator: ":").last!) }
        endConversation(); stopLocal(); gate.invalidate(.ready)
        historyGeneration = UUID(); sessionSuffix = id; historySelection = item.id; historyReady = false
        let generation = historyGeneration, connection = connectionID, agent = selectedAgent
        historyMessages = []; sentText = ""; reply = ""; interim = ""; draft = ""
        await loadHistoryMessages()
        // Save only after an authorized successful history read.
        if historyContextIsCurrent(generation, connection, agent), historySelection == item.id, historyReady { preferences.set(id, forKey: historyPreferenceKey); prepareMessageSubscription() }
    }
    func loadHistoryMessages(more: Bool = false) async {
        guard connected, historyAvailable, !historyLoading, activeRun == nil, !historySelection.isEmpty, let transport else { return }
        let generation = historyGeneration, connection = connectionID, agent = selectedAgent
        historyLoading = true; historyError = ""
        defer { if historyGeneration == generation { historyLoading = false } }
        do {
            let offset = more ? messageOffset : 0
            var params: [String: Any]
            let result: [String: Any]
            if restrictedMode {
                params = ["conversationId":historySelection,"limit":50]
                if more, let messageCursor { params["before"] = messageCursor }
                result = try await transport.request("voxkin.history.messages", params)
            } else {
                guard VoxkinHistory.owns(historySelection, agent: agent) else { throw VoiceError.invalid("Not a Voxkin conversation.") }
                params = ["sessionKey":historySelection,"agentId":agent,"limit":50,"offset":offset,"maxBytes":128_000]
                result = try await transport.request("chat.history", params)
            }
            guard historyContextIsCurrent(generation, connection, agent) else { return }
            guard let rows = result["messages"] as? [[String: Any]] else { throw VoiceError.invalid("Invalid history response.") }
            historyReady = true
            let items = VoxkinHistory.messages(rows, offset: offset)
            if more { let existing = Set(historyMessages.map(\.id)); historyMessages = items.filter { !existing.contains($0.id) } + historyMessages }
            else { historyMessages = items }
            messageOffset = result["nextOffset"] as? Int ?? offset + rows.count
            messageCursor = result["nextCursor"] as? String
            messagesHaveMore = restrictedMode ? messageCursor != nil : result["hasMore"] as? Bool ?? false
            if !more { sentText = items.last(where: { $0.role == "user" })?.text ?? ""; reply = items.last(where: { $0.role == "assistant" })?.text ?? "" }
        } catch { if historyContextIsCurrent(generation, connection, agent) { historyError = safe(error) } }
    }
}
