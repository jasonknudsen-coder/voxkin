import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

private final class RestrictedNoRedirect: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}

/// Separate credential and transport: never connects to the operator WebSocket.
@MainActor public final class RestrictedGateway: GatewayTransport {
    public var onEvent: ((String, [String: Any]) -> Void)?
    public var onClose: (() -> Void)?
    private let endpoint: URL
    private let token: String
    private let session: URLSession
    private var polling: Task<Void, Never>?
    private var serverRuns: [String: String] = [:]
    private var generation = UUID()
    private var agent = ""
    public private(set) var supportsHistory = false
    public private(set) var supportsHistoryActions = false
    public convenience init(url: URL, token: String) throws {
        try self.init(url:url,token:token,configuration:.ephemeral)
    }
    init(url: URL, token: String, configuration: URLSessionConfiguration) throws {
        self.endpoint = try Self.route(url); self.token = token
        let config = configuration
        config.httpCookieStorage = nil; config.urlCredentialStorage = nil; config.urlCache = nil
        config.timeoutIntervalForRequest = 60
        self.session = URLSession(configuration: config, delegate: RestrictedNoRedirect(), delegateQueue: nil)
    }
    nonisolated public static func route(_ url: URL) throws -> URL {
        guard var c = URLComponents(url: url, resolvingAgainstBaseURL: false),
              ["wss", "https"].contains(c.scheme?.lowercased() ?? ""), c.host != nil,
              c.user == nil, c.password == nil, c.query == nil, c.fragment == nil,
              c.path.isEmpty || c.path == "/" else { throw VoiceError.invalid("Restricted access requires an HTTPS gateway origin, without a path or credentials.") }
        c.scheme = "https"; c.path = "/voxkin/v1"
        guard let result = c.url else { throw VoiceError.invalid("Invalid gateway.") }
        return result
    }
    public func call(_ body: [String: Any]) async throws -> [String: Any] {
        let current = generation
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"; request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if !token.isEmpty { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (bytes, response) = try await session.bytes(for: request)
        guard generation == current else { throw CancellationError() }
        guard let http = response as? HTTPURLResponse, http.statusCode == 200, http.url == endpoint,
              http.expectedContentLength < 9_000_000 else {
            throw VoiceError.invalid("Restricted request rejected. Check owner approval, access, and plugin availability. Nothing was replayed.")
        }
        var data = Data()
        for try await byte in bytes {
            guard generation == current else { throw CancellationError() }
            guard data.count < 9_000_000 else { close(); throw VoiceError.invalid("Restricted response exceeded the size limit.") }
            data.append(byte)
        }
        guard let result = try JSONSerialization.jsonObject(with:data) as? [String:Any] else { throw VoiceError.invalid("Invalid restricted response.") }
        return result
    }
    public func connect() async throws {
        let hello = try await call(["op":"hello"])
        guard hello["version"] as? Int == 1, let id = hello["agentId"] as? String, !id.isEmpty else { throw VoiceError.invalid("Unsupported restricted gateway.") }
        agent = id; supportsHistory = hello["history"] as? Bool == true
        supportsHistoryActions = hello["historyActions"] as? Bool == true
    }
    public func request(_ method: String, _ params: [String: Any]) async throws -> [String: Any] {
        switch method {
        case "voxkin.history.rename", "voxkin.history.delete":
            guard supportsHistoryActions else { throw VoiceError.invalid("Update the gateway plugin to rename or delete history.") }
            var body = params; body["op"] = String(method.dropFirst("voxkin.".count)); return try await call(body)
        case "voxkin.history.list", "voxkin.history.create", "voxkin.history.messages":
            guard supportsHistory else { throw VoiceError.invalid("Update the gateway plugin to use history.") }
            var body = params; body["op"] = String(method.dropFirst("voxkin.".count)); return try await call(body)
        case "agents.list": return ["agents":[["id":agent]]]
        case "voxkin.catalog": return try await call(["op":"catalog"])
        case "voxkin.speak": var p = params; p["op"] = "speak"; return try await call(p)
        case "chat.send":
            guard params["agentId"] as? String == agent, let text = params["message"] as? String,
                  let run = params["idempotencyKey"] as? String, let localSession = params["sessionKey"] as? String else { throw VoiceError.invalid("Invalid restricted request.") }
            guard VoxkinHistory.owns(localSession, agent: agent), let conversationID = localSession.split(separator: ":").last else { throw VoiceError.invalid("Invalid conversation identity.") }
            let accepted = try await call(["op":"send","text":text,"requestId":run,"conversationId":String(conversationID)])
            guard let serverRun = accepted["runId"] as? String else { throw VoiceError.invalid("Missing run identity.") }
            serverRuns[run] = serverRun
            polling?.cancel()
            let current = generation
            polling = Task { [weak self] in
                guard let self else { return }
                do {
                    while !Task.isCancelled && current == self.generation {
                        let result = try await self.call(["op":"poll","runId":serverRun])
                        guard !Task.isCancelled && current == self.generation else { return }
                        let state = result["state"] as? String ?? "error"
                        let final = ["final","error","aborted"].contains(state)
                        let payload: [String: Any] = ["runId":run,"sessionKey":localSession,"state":state == "working" ? "delta" : state,"message":["role":"assistant","content":[["type":"text","text":result["text"] as? String ?? ""]]]]
                        self.onEvent?("chat", payload)
                        if final { self.serverRuns.removeValue(forKey:run); return }
                        try await Task.sleep(nanoseconds:750_000_000)
                    }
                } catch {
                    if !Task.isCancelled && current == self.generation { self.onEvent?("chat",["runId":run,"sessionKey":localSession,"state":"error"]) }
                }
            }
            return ["runId":run]
        case "chat.abort":
            guard let run = params["runId"] as? String, let serverRun = serverRuns[run] else { throw VoiceError.invalid("No owned request to cancel.") }
            return try await call(["op":"abort","runId":serverRun])
        default: throw VoiceError.invalid("This operation is not available to restricted phones.")
        }
    }
    public func close() { generation = UUID(); polling?.cancel(); polling = nil; serverRuns.removeAll(); session.invalidateAndCancel() }
}

public struct VoxkinInvitation {
    public let gateway: URL
    public let secret: String
    public init(_ text: String) throws {
        guard let c = URLComponents(string: text), c.scheme == "voxkin", c.host == "join", c.path.isEmpty,
              c.user == nil, c.password == nil, c.port == nil, c.query == nil, let fragment = c.fragment,
              let parts = URLComponents(string: "https://invite.invalid/?" + fragment)?.queryItems,
              parts.count == 2, parts.filter({$0.name == "gateway"}).count == 1, parts.filter({$0.name == "secret"}).count == 1,
              let origin = parts.first(where: {$0.name == "gateway"})?.value, let url = URL(string: origin),
              let secret = parts.first(where: {$0.name == "secret"})?.value,
              secret.range(of: "^[A-Za-z0-9_-]{32,256}$", options:.regularExpression) != nil else { throw VoiceError.invalid("Invalid Voxkin invitation.") }
        _ = try RestrictedGateway.route(url)
        self.gateway = url; self.secret = secret
    }
}

/// Owner bootstrap material is only staged by scanning; connecting remains explicit.
public struct VoxkinOwnerSetup {
    public let gateway: URL
    public let token: String
    public init(_ text: String) throws {
        guard text.utf8.count <= 8192,
              let c = URLComponents(string: text), c.scheme == "voxkin", c.host == "setup", c.path.isEmpty,
              c.user == nil, c.password == nil, c.port == nil, c.query == nil,
              let fragment = c.percentEncodedFragment,
              let parts = URLComponents(string: "https://setup.invalid/?" + fragment)?.queryItems,
              parts.count == 2, parts.filter({ $0.name == "gateway" }).count == 1,
              parts.filter({ $0.name == "token" }).count == 1,
              let origin = parts.first(where: { $0.name == "gateway" })?.value,
              let url = URL(string: origin), url.scheme == "wss",
              let token = parts.first(where: { $0.name == "token" })?.value,
              !token.isEmpty, token.utf8.count <= 4096,
              !token.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) })
        else { throw VoiceError.invalid("Invalid Voxkin owner setup code.") }
        _ = try RestrictedGateway.route(url)
        self.gateway = url; self.token = token
    }
}

public enum VoxkinSetupCode {
    /// Shared camera acceptance policy: parsing never redeems or connects.
    public static func accepts(_ value: String) -> Bool {
        (try? VoxkinInvitation(value)) != nil || (try? VoxkinOwnerSetup(value)) != nil
    }
}
