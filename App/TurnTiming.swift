import Foundation

/// In-memory monotonic durations only: no text, audio, identifiers, or telemetry.
struct TurnTiming {
    private var sentAt: TimeInterval?
    private var speechAt: TimeInterval?
    var acknowledgment: TimeInterval?
    var endOfTurn: TimeInterval?
    var transcription: TimeInterval?
    var firstUpdate: TimeInterval?
    var reply: TimeInterval?
    var speech: TimeInterval?
    var speechRequest: TimeInterval?
    var playbackSetup: TimeInterval?
    private var speechReceivedAt: TimeInterval?
    var firstAudio: TimeInterval?
    mutating func sent(at now: TimeInterval = ProcessInfo.processInfo.systemUptime, transcription: TimeInterval? = nil, endOfTurn: TimeInterval? = nil) {
        self = TurnTiming(); self.transcription = transcription; self.endOfTurn = endOfTurn; sentAt = now
    }
    mutating func acknowledged(at now: TimeInterval = ProcessInfo.processInfo.systemUptime) {
        if acknowledgment == nil, let sentAt { acknowledgment = max(0, now - sentAt) }
    }
    mutating func update(at now: TimeInterval = ProcessInfo.processInfo.systemUptime) {
        if firstUpdate == nil, let sentAt { firstUpdate = max(0, now - sentAt) }
    }
    mutating func final(at now: TimeInterval = ProcessInfo.processInfo.systemUptime) {
        if let sentAt { reply = max(0, now - sentAt) }
        speechAt = now
    }
    mutating func speechReceived(at now: TimeInterval = ProcessInfo.processInfo.systemUptime) {
        guard speechReceivedAt == nil, let speechAt else { return }
        speechReceivedAt = now; speechRequest = max(0, now - speechAt)
    }
    mutating func audio(at now: TimeInterval = ProcessInfo.processInfo.systemUptime) {
        guard firstAudio == nil else { return }
        if let sentAt { firstAudio = max(0, now - sentAt) }
        if let speechAt { speech = max(0, now - speechAt) }
        if let speechReceivedAt { playbackSetup = max(0, now - speechReceivedAt) }
    }
}
