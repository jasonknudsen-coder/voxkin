import AVFoundation
import Speech
import VoiceCore

enum DictationLocales {
    /// Voice locale is a hint, not a requirement to have that exact regional recognizer.
    /// Never cross into another language or allow network recognition.
    static func candidates(requested: Locale, preferred: [String], supported: [Locale]) -> [Locale] {
        func key(_ locale: Locale) -> String { locale.identifier.replacingOccurrences(of: "_", with: "-").lowercased() }
        let language = requested.language.languageCode?.identifier
        guard let language else { return [] }
        let supportedKeys = Set(supported.map(key))
        var seen = Set<String>()
        return (preferred.map { Locale(identifier: $0) } + [requested] + supported.sorted { $0.identifier < $1.identifier }).filter {
            $0.language.languageCode?.identifier == language && supportedKeys.contains(key($0)) && seen.insert(key($0)).inserted
        }
    }
    static func unavailableMessage(_ locale: Locale) -> String {
        let language = Locale.current.localizedString(forLanguageCode: locale.language.languageCode?.identifier ?? "") ?? "selected language"
        return "On-device \(language) dictation is unavailable. In iPhone Settings → General → Keyboard, check Enable Dictation and add a \(language) keyboard, then retry. You can type instead; no cloud fallback."
    }
}

struct CaptureWindow {
    static let idleSeconds: UInt64 = 60
    static let speechSeconds: UInt64 = 30
    private var heardWords = false
    mutating func noteTranscript(_ text: String) -> UInt64? {
        guard !heardWords, !text.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty else { return nil }
        heardWords = true
        return Self.speechSeconds
    }
}

@MainActor protocol SpeechCapturing: AnyObject {
    func start(locale: Locale, onText: @escaping (String) -> Void, onEnd: @escaping (String?) -> Void) async throws
    func finish()
    func stop()
}

@MainActor final class SpeechCapture: SpeechCapturing {
    private var input: VoxkinAudioInput?
    private let authorizeSpeech: () async -> SFSpeechRecognizerAuthorizationStatus
    private let authorizeMicrophone: () async -> Bool
    init(authorizeSpeech: @escaping () async -> SFSpeechRecognizerAuthorizationStatus = {
        await withCheckedContinuation { continuation in SFSpeechRecognizer.requestAuthorization { continuation.resume(returning: $0) } }
    }, authorizeMicrophone: @escaping () async -> Bool = { await AVAudioApplication.requestRecordPermission() }) {
        self.authorizeSpeech = authorizeSpeech; self.authorizeMicrophone = authorizeMicrophone
    }
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var recognition: SFSpeechRecognitionTask?
    private var generation = UUID()
    private var limit: Task<Void, Never>?
    private var window = CaptureWindow()
    private var completion: ((String?) -> Void)?
    func start(locale: Locale, onText: @escaping (String) -> Void, onEnd: @escaping (String?) -> Void) async throws {
        stop() // A new attempt owns a fresh graph; never retain a stale CarPlay route.
        let current = UUID(); generation = current; completion = onEnd
        var started = false
        defer { if !started && generation == current { stop() } }
        try Task.checkCancellation()
        let speech = await authorizeSpeech()
        try Task.checkCancellation()
        guard generation == current else { throw CancellationError() }
        guard speech == .authorized else { throw VoiceError.invalid("Speech permission is required. You can type instead.") }
        let microphone = await authorizeMicrophone()
        try Task.checkCancellation()
        guard generation == current else { throw CancellationError() }
        guard microphone else { throw VoiceError.invalid("Microphone permission is required. You can type instead.") }
        let candidates = DictationLocales.candidates(requested: locale,
            preferred: [Locale.current.identifier] + Locale.preferredLanguages,
            supported: Array(SFSpeechRecognizer.supportedLocales()))
        var selected: SFSpeechRecognizer?
        for candidate in candidates {
            if let recognizer = SFSpeechRecognizer(locale: candidate), recognizer.supportsOnDeviceRecognition, recognizer.isAvailable {
                selected = recognizer; break
            }
        }
        guard let recognizer = selected else { throw VoiceError.invalid(DictationLocales.unavailableMessage(locale)) }
        let audio = AVAudioSession.sharedInstance()
        try audio.setCategory(.record, mode: .measurement, options: [.allowBluetoothHFP])
        try audio.setActive(true)
        let request = SFSpeechAudioBufferRecognitionRequest(); request.requiresOnDeviceRecognition = true; request.shouldReportPartialResults = true
        self.request = request
        guard audio.isInputAvailable, !audio.currentRoute.inputs.isEmpty else {
            throw VoiceError.invalid("No usable microphone route. Tap to try again.")
        }
        let input = VoxkinAudioInput()
        self.input = input
        do { try input.start(request: request) }
        catch { throw VoiceError.invalid("Microphone route unavailable or changed. Tap to try again.") }
        recognition = recognizer.recognitionTask(with: request) { [weak self] result, error in
            Task { @MainActor in
                guard let self, self.generation == current else { return }
                if let result {
                    let text = String(result.bestTranscription.formattedString.prefix(4000))
                    if self.input != nil, let seconds = self.window.noteTranscript(text) {
                        self.scheduleLimit(current:current, seconds:seconds, message:"30-second spoken-turn limit reached. Review your draft.")
                    }
                    onText(text)
                }
                if error != nil || result?.isFinal == true { self.complete(error == nil ? nil : "Recognition ended. Review your draft or type instead.") }
            }
        }
        started = true
        scheduleLimit(current:current, seconds:CaptureWindow.idleSeconds, message:"Microphone paused after 60 seconds without speech. Tap the mic to start again.")
    }
    private func scheduleLimit(current: UUID, seconds: UInt64, message: String) {
        limit?.cancel()
        limit = Task { [weak self] in
            try? await Task.sleep(nanoseconds: seconds * 1_000_000_000)
            guard !Task.isCancelled, self?.generation == current else { return }
            self?.complete(message)
        }
    }
    private func complete(_ message: String?) {
        let callback = completion
        stop()
        callback?(message)
    }
    func finish() {
        guard request != nil else { complete("Microphone was not ready. Tap to try again."); return }
        let current = generation
        limit?.cancel()
        input?.stop(); input = nil
        request?.endAudio()
        limit = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            guard !Task.isCancelled, self?.generation == current else { return }
            self?.complete("Check your words, then tap Send.")
        }
    }
    func stop() {
        completion = nil; window = CaptureWindow()
        generation = UUID(); limit?.cancel(); limit = nil
        input?.stop(); input = nil
        request?.endAudio(); recognition?.cancel(); recognition = nil; request = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}

#if targetEnvironment(simulator)
/// Explicit simulator-only gesture fixture. Never records microphone audio.
@MainActor final class ConversationTestCapture: SpeechCapturing {
    private var completion: ((String?) -> Void)?
    func start(locale: Locale, onText: @escaping (String) -> Void, onEnd: @escaping (String?) -> Void) async throws {
        completion = onEnd
    }
    func finish() { let callback = completion; completion = nil; callback?(nil) }
    func stop() { completion = nil }
}
#endif
