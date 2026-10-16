import Foundation
import VoiceCore

struct LocalVoice: Identifiable, Equatable {
    let id: String
    let name: String
    let language: String
    let locale: String
    var languageName: String { language == "es" ? "Español" : "English" }
    var sample: String { language == "es" ? "Hola, soy tu asistente. ¿Qué te gustaría hacer hoy?" : "Hello, I’m your assistant. What would you like to do today?" }
    static func catalog(_ payload: [String: Any]) throws -> [LocalVoice] {
        guard payload["version"] as? Int == 1, payload["provider"] as? String == "voxkin-local",
              payload["local"] as? Bool == true, let ready = payload["ready"] as? Bool,
              let rows = payload["voices"] as? [[String: Any]], rows.count <= 50 else {
            throw VoiceError.invalid("Unsupported local voice catalog. Gateway default is still available.")
        }
        guard ready else { throw VoiceError.invalid("Local voice setup is incomplete. Run the plugin setup on your gateway host.") }
        var ids = Set<String>()
        return try rows.map { row in
            guard let id = row["id"] as? String, id.range(of: "^[a-zA-Z0-9_-]{1,64}$", options: .regularExpression) != nil,
                  ids.insert(id).inserted, let name = row["name"] as? String, !name.isEmpty, name.count <= 80,
                  let language = row["language"] as? String, ["en", "es"].contains(language),
                  let locale = row["locale"] as? String,
                  (language == "en" && ["en-US", "en-GB"].contains(locale)) || (language == "es" && locale == "es-ES") else {
                throw VoiceError.invalid("Invalid local voice catalog. No voice was selected automatically.")
            }
            return LocalVoice(id: id, name: name, language: language, locale: locale)
        }
    }
    @MainActor static func synthesize(_ text: String, voice: LocalVoice?, transport: GatewayTransport, received: (() -> Void)? = nil) async throws -> AudioClip {
        let payload: [String: Any]
        if let voice {
            payload = try await transport.request("voxkin.speak", ["text": text, "language": voice.language, "voice": voice.id])
            guard payload["provider"] as? String == "voxkin-local", payload["voice"] as? String == voice.id,
                  payload["language"] as? String == voice.language else {
                throw VoiceError.invalid("Local voice response did not match your selection. No fallback was used.")
            }
        } else { payload = try await transport.request("tts.speak", ["text": text]) }
        received?()
        return try AudioClip(payload: payload)
    }
}
