import Foundation
import CryptoKit

public enum VoiceError: Error, LocalizedError {
    case invalid(String)
    public var errorDescription: String? { if case .invalid(let value) = self { return value }; return nil }
}
public enum Endpoint {
    public static func validate(_ raw: String) throws -> URL {
        guard let url = URL(string: raw), url.scheme == "wss", let host = url.host, !host.isEmpty,
              url.user == nil, url.password == nil, url.query == nil, url.fragment == nil else {
            throw VoiceError.invalid("Use a trusted wss:// gateway URL without credentials, query, or fragment.")
        }
        return url
    }
}
public extension Data {
    var base64URL: String { base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "") }
}
public struct DeviceIdentity {
    private let key: Curve25519.Signing.PrivateKey
    public init(raw: Data? = nil) throws { key = try raw.map { try Curve25519.Signing.PrivateKey(rawRepresentation: $0) } ?? Curve25519.Signing.PrivateKey() }
    public var raw: Data { key.rawRepresentation }
    public var publicKey: String { key.publicKey.rawRepresentation.base64URL }
    public var id: String { SHA256.hash(data: key.publicKey.rawRepresentation).map { String(format: "%02x", $0) }.joined() }
    public func device(nonce: String, timestamp: Int64, token: String, scopes: [String]) throws -> [String: Any] {
        guard !nonce.isEmpty, timestamp >= 0 else { throw VoiceError.invalid("Invalid gateway challenge.") }
        let payload = ["v3", id, "openclaw-ios", "ui", "operator", scopes.joined(separator: ","), String(timestamp), token, nonce, "ios", "iphone"].joined(separator: "|")
        return ["id": id, "publicKey": publicKey, "signature": try key.signature(for: Data(payload.utf8)).base64URL, "signedAt": timestamp, "nonce": nonce]
    }
}
public struct TurnGate {
    public enum State: String { case disconnected, ready, recording, transcribing, waiting, speaking, interrupted, failed }
    public private(set) var state: State = .disconnected
    public private(set) var epoch = UUID()
    public private(set) var runID: String?
    public private(set) var session: String?
    public init() {}
    public mutating func connected() { invalidate(.ready) }
    public mutating func invalidate(_ next: State) { epoch = UUID(); runID = nil; session = nil; state = next }
    public mutating func begin(session: String, run: String) throws -> UUID {
        guard state == .ready || state == .interrupted else { throw VoiceError.invalid("Finish or stop the current turn first.") }
        epoch = UUID(); self.session = session; runID = run; state = .waiting; return epoch
    }
    public func accepts(session: String, run: String) -> Bool { state == .waiting && self.session == session && runID == run }
    public mutating func speaking(epoch: UUID) -> Bool { guard self.epoch == epoch, state == .waiting else { return false }; state = .speaking; return true }
    public mutating func recorded(_ recording: Bool) { state = recording ? .recording : .ready }
}
public struct AudioClip {
    public let data: Data
    public let provider: String
    public init(payload: [String: Any]) throws {
        guard let encoded = payload["audioBase64"] as? String, encoded.utf8.count <= 8_000_000,
              let data = Data(base64Encoded: encoded), !data.isEmpty, data.count <= 6_000_000,
              let provider = payload["provider"] as? String, !provider.isEmpty,
              let mime = payload["mimeType"] as? String,
              ["audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/mp4", "audio/aac"].contains(mime) else {
            throw VoiceError.invalid("Gateway returned missing, oversized, or unsupported audio. Text remains available.")
        }
        self.data = data; self.provider = provider
    }
}
public enum ChatText {
    /// Only explicitly classified, user-visible interim text is eligible for speech.
    /// Never infer visibility from prose, provenance IDs, or partial chat deltas.
    public static func interim(_ message: [String: Any]) -> String? {
        guard message["role"] as? String == "assistant", message["errorMessage"] == nil else { return nil }
        let meta = message["__openclaw"] as? [String: Any]
        guard meta?["runTerminal"] as? Bool != true else { return nil }
        if let reason = message["stopReason"] as? String, reason != "stop" { return nil }
        var commentary = false
        if let phase = message["phase"] {
            guard phase as? String == "commentary" else { return nil }
            commentary = true
        }
        let text: String
        if let value = message["content"] as? String { text = value }
        else if let blocks = message["content"] as? [[String: Any]], !blocks.isEmpty {
            var parts: [String] = []
            var signedCommentaryBlocks = 0
            for block in blocks {
                guard ["text", "input_text", "output_text"].contains(block["type"] as? String ?? ""),
                      let value = block["text"] as? String else { return nil }
                if let signature = block["textSignature"] as? String, signature.hasPrefix("{") {
                    guard let data = signature.data(using: .utf8),
                          let parsed = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                          parsed["v"] as? Int == 1 else { return nil }
                    if let phase = parsed["phase"] {
                        guard phase as? String == "commentary" else { return nil }
                        commentary = true; signedCommentaryBlocks += 1
                    }
                }
                parts.append(value)
            }
            guard signedCommentaryBlocks == 0 || signedCommentaryBlocks == blocks.count else { return nil }
            text = parts.joined(separator: "\n")
        } else { return nil }
        if let mirror = message["openclawDeliveryMirror"] as? [String: Any] {
            guard mirror["kind"] as? String == "message-tool-source-reply",
                  mirror["final"] as? Bool == false else { return nil }
            commentary = true
        }
        if let fallback = message["openclawStreamFallback"] as? [String: Any] {
            guard fallback["source"] as? String == "segment",
                  let item = fallback["itemId"] as? String,
                  !item.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
            commentary = true
        }
        guard commentary, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return String(text.prefix(32_000))
    }
    public static func final(_ payload: [String: Any]) -> String? {
        guard payload["state"] as? String == "final", let message = payload["message"] as? [String: Any], message["role"] as? String == "assistant" else { return nil }
        return self.text(message)
    }
    public static func text(_ message: [String: Any]) -> String? {
        guard message["role"] as? String == "assistant" else { return nil }
        if let text = message["content"] as? String { return String(text.prefix(32_000)) }
        let blocks = message["content"] as? [[String: Any]] ?? []
        return String(blocks.filter { $0["type"] as? String == "text" }.compactMap { $0["text"] as? String }.joined(separator: "\n").prefix(32_000))
    }
}

/// Speech-only punctuation cleanup. Never change the displayed reply.
public enum SpeechText {
    public static func prepare(_ text: String) -> String {
        // Match the inline Markdown rendered by SwiftUI: speak the visible words,
        // not emphasis delimiters, backticks, or link destination syntax.
        let rendered = try? AttributedString(markdown: text, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))
        var spoken = rendered.map { String($0.characters) } ?? text
        // Markdown horizontal rules and bullet markers are layout, not spoken words.
        spoken = spoken.replacingOccurrences(of: #"(?m)^[ \t]*(?:-[ \t]*){3,}$"#, with: "", options: .regularExpression)
        spoken = spoken.replacingOccurrences(of: #"(?m)^[ \t]*[-–—•][ \t]+"#, with: "", options: .regularExpression)
        // Em dashes are prose breaks. Leave en dashes and single hyphens alone:
        // they may express ranges or subtraction, even between words.
        spoken = spoken.replacingOccurrences(of: #"(?<!\d)—|—(?!\d)"#, with: ", ", options: .regularExpression)
        spoken = spoken.replacingOccurrences(of: #"(?<=\p{L})[ \t]+-{2}[ \t]+(?=\p{L})"#, with: ", ", options: .regularExpression)
        spoken = spoken.replacingOccurrences(of: #"[ \t]+,"#, with: ",", options: .regularExpression)
        spoken = spoken.replacingOccurrences(of: #"([.!?,:;])[ \t]*,[ \t]*"#, with: "$1 ", options: .regularExpression)
        spoken = spoken.replacingOccurrences(of: #"[ \t]{2,}"#, with: " ", options: .regularExpression)
        return spoken
    }
}

/// Split only completed replies. Preserve every character; bound synthesis and prefetch work.
public enum SpeechChunks {
    public static func split(_ text: String) -> [String] {
        guard text.count > 180 else { return [text] }
        var chunks: [String] = [], buffer = ""
        for character in text {
            buffer.append(character)
            let minimum = chunks.isEmpty ? 40 : 320
            let maximum = chunks.isEmpty ? 180 : 600
            let sentence = ".!?\n".contains(character)
            if buffer.count >= minimum && sentence || buffer.count >= maximum && character.isWhitespace || buffer.count >= maximum + 100 {
                chunks.append(buffer); buffer = ""
            }
        }
        if !buffer.isEmpty { chunks.append(buffer) }
        return chunks
    }
}
