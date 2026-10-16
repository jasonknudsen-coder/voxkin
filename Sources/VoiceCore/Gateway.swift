import Foundation

public struct GatewayPairingRequired: Error, LocalizedError {
    public let requestID: String?
    public init(requestID: String?) { self.requestID = requestID.flatMap { UUID(uuidString: $0)?.uuidString.lowercased() } }
    public var errorDescription: String? { "Waiting for gateway approval. Approve this phone on the gateway, then tap Retry connection." }
    public var approvalCommand: String? { requestID.map { "openclaw devices approve " + $0 } }
}

@MainActor public protocol GatewayTransport: AnyObject {
    var onEvent: ((String, [String: Any]) -> Void)? { get set }
    var onClose: (() -> Void)? { get set }
    func request(_ method: String, _ params: [String: Any]) async throws -> [String: Any]
    func close()
}

@MainActor protocol WireSocket: AnyObject {
    var currentURL: URL? { get }
    func start()
    func read() async throws -> Data
    func write(_ data: Data) async throws
    func stop()
}
private final class NoRedirectDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}

// URLSession exposes a WSS upgrade as HTTPS, and supplies a root slash.
// Accept only that representation change; never relax host, port, path or query.
func sameWebSocketEndpoint(_ current: URL?, _ requested: URL) -> Bool {
    func normalized(_ url: URL) -> URLComponents? {
        guard var value = URLComponents(url: url, resolvingAgainstBaseURL: false),
              ["wss", "https"].contains(value.scheme?.lowercased() ?? ""),
              value.user == nil, value.password == nil, value.fragment == nil else { return nil }
        value.scheme = "https"
        value.host = value.host?.lowercased()
        if value.port == 443 { value.port = nil }
        if value.percentEncodedPath.isEmpty { value.percentEncodedPath = "/" }
        return value
    }
    guard let current, requested.scheme?.lowercased() == "wss",
          let actual = normalized(current), let expected = normalized(requested) else { return false }
    return actual == expected
}

@MainActor private final class SystemSocket: WireSocket {
    private let session: URLSession
    private let task: URLSessionWebSocketTask
    var currentURL: URL? { task.currentRequest?.url }
    init(url: URL) {
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieStorage = nil; config.urlCredentialStorage = nil; config.urlCache = nil
        session = URLSession(configuration: config, delegate: NoRedirectDelegate(), delegateQueue: nil)
        task = session.webSocketTask(with: url); task.maximumMessageSize = 9_000_000
    }
    func start() { task.resume() }
    func read() async throws -> Data {
        switch try await task.receive() {
        case .string(let s): return Data(s.utf8)
        case .data(let d): return d
        @unknown default: throw VoiceError.invalid("Unsupported frame.")
        }
    }
    func write(_ data: Data) async throws { try await task.send(.string(String(decoding: data, as: UTF8.self))) }
    func stop() { task.cancel(with: .goingAway, reason: nil); session.invalidateAndCancel() }
}

private final class RequestCancellation: @unchecked Sendable {
    private let lock = NSLock()
    private var value = false
    var cancelled: Bool { lock.lock(); defer { lock.unlock() }; return value }
    func cancel() { lock.lock(); value = true; lock.unlock() }
}

/// Single socket, bounded requests, no retry/replay. Every closure is fenced to its socket generation.
@MainActor public final class Gateway: GatewayTransport {
    public var onEvent: ((String, [String: Any]) -> Void)?
    public var onClose: (() -> Void)?
    private var socket: WireSocket?
    private let makeSocket: (URL) -> WireSocket
    private let requestTimeout: UInt64
    private var receiver: Task<Void, Never>?
    private var pending: [String: CheckedContinuation<[String: Any], Error>] = [:]
    private var deadlines: [String: Task<Void, Never>] = [:]
    private var generation = UUID()
    private var challenge: CheckedContinuation<[String: Any], Error>?
    private var challengeDeadline: Task<Void, Never>?
    public init() { makeSocket = { SystemSocket(url: $0) }; requestTimeout = 60_000_000_000 }
    init(timeout: UInt64 = 60_000_000_000, makeSocket: @escaping (URL) -> WireSocket) { self.makeSocket = makeSocket; requestTimeout = timeout }
    public func connect(url: URL, identity: DeviceIdentity, token: String, scopes: [String]) async throws -> [String: Any] {
        close()
        _ = try Endpoint.validate(url.absoluteString)
        let ws = makeSocket(url); socket = ws
        let current = generation
        ws.start()
        let challenge = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<[String: Any], Error>) in
            self.challenge = continuation
            challengeDeadline = Task { [weak self] in
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                guard !Task.isCancelled, self?.generation == current else { return }
                self?.close()
            }
            receiver = Task { [weak self] in
                do {
                    while !Task.isCancelled {
                        let data = try await ws.read()
                        guard let self, self.generation == current else { return }
                        guard sameWebSocketEndpoint(ws.currentURL, url) else { throw VoiceError.invalid("Gateway redirect rejected.") }
                        try self.receive(data)
                    }
                } catch { if self?.generation == current { self?.close() } }
            }
        }
        guard generation == current, let nonce = challenge["nonce"] as? String,
              let number = challenge["ts"] as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID(),
              number.doubleValue >= 0, number.doubleValue.rounded() == number.doubleValue,
              number.doubleValue < Double(Int64.max) else { close(); throw VoiceError.invalid("Invalid challenge; reconnect manually.") }
        do {
            let hello = try await request("connect", [
                "minProtocol": 4, "maxProtocol": 4,
                "client": ["id": "openclaw-ios", "displayName": "Voxkin (private prototype)", "version": "0.1.0", "platform": "ios", "deviceFamily": "iphone", "mode": "ui"],
                "role": "operator", "scopes": scopes, "caps": [], "auth": ["token": token],
                "device": try identity.device(nonce: nonce, timestamp: number.int64Value, token: token, scopes: scopes)
            ])
            guard hello["type"] as? String == "hello-ok", hello["protocol"] as? Int == 4 else { throw VoiceError.invalid("Unsupported gateway protocol.") }
            return hello
        } catch { close(); throw error }
    }
    public func request(_ method: String, _ params: [String: Any]) async throws -> [String: Any] {
        try Task.checkCancellation()
        guard let socket, pending.count < 8 else { throw VoiceError.invalid("Disconnected or request queue full.") }
        let id = UUID().uuidString
        let data = try JSONSerialization.data(withJSONObject: ["type": "req", "id": id, "method": method, "params": params])
        guard data.count < 64_000 else { throw VoiceError.invalid("Request too large.") }
        let cancellation = RequestCancellation()
        return try await withTaskCancellationHandler(operation: {
          try await withCheckedThrowingContinuation { continuation in
            guard !cancellation.cancelled else { continuation.resume(throwing: CancellationError()); return }
            pending[id] = continuation
            deadlines[id] = Task { [weak self] in
                try? await Task.sleep(nanoseconds: self?.requestTimeout ?? 60_000_000_000)
                guard !Task.isCancelled else { return }
                self?.finish(id, .failure(VoiceError.invalid("Request timed out; outcome may be unknown. Nothing was replayed.")))
            }
            Task { [weak self] in
                do {
                    guard !cancellation.cancelled, self?.pending[id] != nil else {
                        self?.finish(id, .failure(CancellationError())); return
                    }
                    try await socket.write(data)
                }
                catch { self?.finish(id, .failure(VoiceError.invalid("Connection lost; outcome may be unknown."))) }
            }
          }
        }, onCancel: {
            cancellation.cancel()
            Task { @MainActor [weak self] in self?.finish(id, .failure(CancellationError())) }
        })
    }
    private func receive(_ data: Data) throws {
        guard data.count <= 9_000_000, let frame = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw VoiceError.invalid("Invalid frame.") }
        if frame["type"] as? String == "event", let name = frame["event"] as? String {
            let payload = frame["payload"] as? [String: Any] ?? [:]
            if name == "connect.challenge", let continuation = challenge {
                challenge = nil; challengeDeadline?.cancel(); continuation.resume(returning: payload)
            } else { onEvent?(name, payload) }
        } else if frame["type"] as? String == "res", let id = frame["id"] as? String {
            if frame["ok"] as? Bool == true { finish(id, .success(frame["payload"] as? [String: Any] ?? [:])) }
            else {
                // Do not render arbitrary server error strings: they may echo secrets or private content.
                let error = frame["error"] as? [String: Any] ?? [:]
                let details = error["details"] as? [String: Any] ?? [:]
                let code = details["code"] as? String ?? error["code"] as? String ?? "REJECTED"
                if ["PAIRING_REQUIRED", "NOT_PAIRED"].contains(code) {
                    finish(id, .failure(GatewayPairingRequired(requestID: details["requestId"] as? String)))
                    return
                }
                let safeCodes = ["PAIRING_REQUIRED", "AUTH_TOKEN_MISMATCH", "AUTH_SCOPE_MISMATCH", "FORBIDDEN", "UNAVAILABLE", "INVALID_REQUEST", "NOT_PAIRED"]
                finish(id, .failure(VoiceError.invalid("Gateway: \(safeCodes.contains(code) ? code : "request rejected"). Check pairing, scopes, and method support on the gateway.")))
            }
        }
    }
    private func finish(_ id: String, _ result: Result<[String: Any], Error>) {
        deadlines.removeValue(forKey: id)?.cancel()
        pending.removeValue(forKey: id)?.resume(with: result)
    }
    public func close() {
        let wasOpen = socket != nil
        generation = UUID(); receiver?.cancel(); receiver = nil
        socket?.stop(); socket = nil
        challengeDeadline?.cancel(); challengeDeadline = nil
        challenge?.resume(throwing: VoiceError.invalid("Connection ended before handshake.")); challenge = nil
        for id in Array(pending.keys) { finish(id, .failure(VoiceError.invalid("Disconnected; outcome may be unknown. Reconnect manually; no replay."))) }
        if wasOpen { onClose?() }
    }
}
import CoreFoundation

@MainActor public final class MockGateway: GatewayTransport {
    public var onEvent: ((String, [String: Any]) -> Void)?
    public var onClose: (() -> Void)?
    public private(set) var calls: [String] = []
    private var connected = true
    private var historyTitle = "A quiet weekend"
    private var historyDeleted = false
    private var generation = UUID()
    public init() {}
    public func request(_ method: String, _ params: [String: Any]) async throws -> [String: Any] {
        guard connected else { throw VoiceError.invalid("Mock disconnected.") }
        calls.append(method)
        switch method {
        case "sessions.list": return ["sessions": historyDeleted ? [] : [["key":"agent:synthetic:voxkin:11111111-2222-4333-8444-555555555555", "sessionId":"fixture-session", "label":historyTitle, "updatedAt":1_792_000_000_000.0]]]
        case "sessions.preview": return ["previews":[]]
        case "sessions.patch": if let title = params["label"] as? String { historyTitle = title }; return ["ok":true]
        case "sessions.delete": historyDeleted = true; return ["ok":true, "deleted":true]
        case "chat.history": return ["messages":[["id":"fixture-user","role":"user","content":"Suggest a quiet weekend activity."],["id":"fixture-answer","role":"assistant","content":"Take a short walk, then settle in with a good book."]],"hasMore":false]
        case "voxkin.catalog": return ["version":1,"provider":"voxkin-local","local":true,"ready":true,"voices":[["id":"af_heart","name":"Heart","language":"en","locale":"en-US"],["id":"ef_dora","name":"Dora","language":"es","locale":"es-ES"]]]
        case "voxkin.speak": return ["audioBase64":Self.tone().base64EncodedString(),"provider":"voxkin-local","mimeType":"audio/wav","voice":params["voice"] ?? "", "language":params["language"] ?? ""]
        case "agents.list": return ["agents": [["id": "synthetic", "name": "Synthetic agent"]]]
        case "chat.send":
            let run = params["idempotencyKey"] as? String ?? "", session = params["sessionKey"] as? String ?? ""
            let current = generation
            Task { [weak self] in
                try? await Task.sleep(nanoseconds: 300_000_000)
                guard let self, self.connected, self.generation == current else { return }
                self.onEvent?("chat", ["sessionKey": session, "runId": run, "state": "final", "message": ["role": "assistant", "content": [["type": "text", "text": "Synthetic reply. The test tone is not the gateway voice."]]]])
            }
            return ["runId": run]
        case "chat.abort": generation = UUID(); return [:]
        case "tts.speak": return ["audioBase64": Self.tone().base64EncodedString(), "provider": "MOCK test tone — no provider", "mimeType": "audio/wav"]
        default: throw VoiceError.invalid("Unsupported mock method.")
        }
    }
    public func close() { connected = false; generation = UUID(); onClose?() }
    public static func tone() -> Data {
        let count = 8000
        var data = Data()
        func text(_ s: String) { data.append(Data(s.utf8)) }
        func u16(_ n: UInt16) { var v = n.littleEndian; withUnsafeBytes(of: &v) { data.append(contentsOf: $0) } }
        func u32(_ n: UInt32) { var v = n.littleEndian; withUnsafeBytes(of: &v) { data.append(contentsOf: $0) } }
        text("RIFF"); u32(UInt32(36 + count * 2)); text("WAVEfmt "); u32(16); u16(1); u16(1); u32(16000); u32(32000); u16(2); u16(16); text("data"); u32(UInt32(count * 2))
        for i in 0..<count { u16(UInt16(bitPattern: Int16(sin(Double(i) * 2 * .pi * 440 / 16000) * 1800))) }
        return data
    }
}
