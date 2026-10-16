import Foundation
import Security
import VoiceCore

enum CredentialStore {
    static func connectionCredential(_ url: URL, enteredToken: String) throws -> SavedGrant? {
        let key = url.absoluteString
        if !enteredToken.isEmpty { try save(Data(enteredToken.utf8), "bootstrap|" + key) }
        if let data = try load("bootstrap|" + key), let token = String(data: data, encoding: .utf8), !token.isEmpty {
            return SavedGrant(token: token, scopes: ["operator.read", "operator.write"])
        }
        return try load("grant|" + key).map { try JSONDecoder().decode(SavedGrant.self, from: $0) }
    }
    static func load(_ account: String) throws -> Data? {
        var query = base(account); query[kSecReturnData as String] = true; query[kSecReturnAttributes as String] = true; query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw VoiceError.invalid("Keychain is unavailable. Unlock this device.") }
        guard let result = item as? [String: Any], let data = result[kSecValueData as String] as? Data else {
            throw VoiceError.invalid("Could not read saved credential securely.")
        }
        // Migrate existing installs only after a successful read. Never delete/recreate
        // an inaccessible item: an older build may require one unlocked launch first.
        if result[kSecAttrAccessible as String] as? String != kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly as String {
            let status = SecItemUpdate(base(account) as CFDictionary,
                [kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly] as CFDictionary)
            guard status == errSecSuccess else { throw VoiceError.invalid("Open Voxkin on the unlocked iPhone to update saved credentials.") }
        }
        return data
    }
    static func save(_ data: Data, _ account: String) throws {
        let query = base(account)
        let update: [String: Any] = [kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        var status = SecItemUpdate(query as CFDictionary, update as CFDictionary)
        if status == errSecItemNotFound {
            var add = query; add[kSecValueData as String] = data
            add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            status = SecItemAdd(add as CFDictionary, nil)
        }
        guard status == errSecSuccess else { throw VoiceError.invalid("Could not save device credential securely.") }
    }
    static func remove(_ account: String) throws {
        let status = SecItemDelete(base(account) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw VoiceError.invalid("Could not remove local credential.") }
    }
    private static func base(_ account: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "studio.builderofthings.voxkin.credentials", kSecAttrAccount as String: account, kSecAttrSynchronizable as String: false]
    }
}
struct SavedGrant: Codable { let token: String; let scopes: [String] }
