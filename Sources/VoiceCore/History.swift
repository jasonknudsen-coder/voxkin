import Foundation

public struct ConversationSummary: Identifiable, Equatable {
    public let id: String
    public let title: String
    public let preview: String
    public let updatedAt: Date
    public let sessionID: String?
    public let manuallyNamed: Bool
    public init(id: String, title: String, preview: String = "", updatedAt: Date = Date(), sessionID: String? = nil, manuallyNamed: Bool = false) {
        self.id = id; self.title = title; self.preview = preview; self.updatedAt = updatedAt
        self.sessionID = sessionID; self.manuallyNamed = manuallyNamed
    }
    public func named(_ title: String, manual: Bool? = nil) -> Self {
        Self(id: id, title: title, preview: preview, updatedAt: updatedAt, sessionID: sessionID, manuallyNamed: manual ?? manuallyNamed)
    }
}
public struct ConversationMessage: Identifiable, Equatable {
    public let id: String
    public let role: String
    public let text: String
    public init(id: String, role: String, text: String) { self.id = id; self.role = role; self.text = text }
}
public enum VoxkinHistory {
    public static func cleanTitle(_ raw: String) -> String {
        let text = displayText(raw)
        guard !text.hasPrefix("[Voxkin reply preference") else { return "" }
        return text.split(whereSeparator: { $0.isWhitespace || $0.isNewline }).joined(separator: " ")
    }
    public static func isTrivialTitle(_ text: String) -> Bool {
        text.range(of: "^(?:(?:hi|hello|hey)(?: there)?|hola|test(?:ing)?[ 0-9]*|[0-9 ]+|can you hear me|are you there)[.!?]*$", options:[.regularExpression,.caseInsensitive]) != nil
    }
    public static func automaticTitle(_ messages: [ConversationMessage]) -> String? {
        let texts = messages.filter { $0.role == "user" }.prefix(3).map { cleanTitle($0.text) }.filter { !$0.isEmpty }
        guard let first = texts.first else { return nil }
        let text = texts.first { !isTrivialTitle($0) } ?? first
        return text.count > 80 ? String(text.prefix(79)) + "…" : text
    }
    public static func session(agent: String, id: String) -> String { "agent:\(agent):voxkin:\(id)" }
    public static func owns(_ key: String, agent: String) -> Bool {
        let prefix = "agent:\(agent):voxkin:"
        guard key.hasPrefix(prefix) else { return false }
        return UUID(uuidString: String(key.dropFirst(prefix.count))) != nil
    }
    public static func displayText(_ text: String) -> String {
        // Remove only our exact per-turn wrapper, not arbitrary bracketed user text.
        if text.hasPrefix("[Voxkin reply preference for this turn only: "), let end = text.range(of: "]\n\n") {
            return String(text[end.upperBound...])
        }
        return text
    }
    public static func messages(_ rows: [[String: Any]], offset: Int = 0) -> [ConversationMessage] {
        rows.enumerated().compactMap { index, row in
            guard let role = row["role"] as? String, ["user", "assistant"].contains(role),
                  row["channel"] as? String != "analysis" else { return nil }
            let blocks = row["content"] as? [[String: Any]] ?? []
            // Never expose tool payloads/reasoning or mixed assistant tool messages.
            if role == "assistant" && blocks.contains(where: { !["text", "output_text"].contains($0["type"] as? String ?? "") }) { return nil }
            let raw = row["text"] as? String ?? row["content"] as? String ?? blocks.compactMap { block -> String? in
                guard ["text", "output_text"].contains(block["type"] as? String ?? "") else { return nil }
                return block["text"] as? String
            }.joined(separator: "\n")
            let text = displayText(raw)
            guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
            return ConversationMessage(id: row["id"] as? String ?? row["messageId"] as? String ?? "page-\(offset)-\(index)", role: role, text: String(text.prefix(32_000)))
        }
    }
}
