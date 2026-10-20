package studio.voxkin.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject

class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("encrypted_gateway", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("voxkin-v1", null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            "voxkin-v1",
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    fun put(name: String, value: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        c.updateAAD(name.toByteArray())
        prefs
            .edit()
            .putString(
                digest(name.toByteArray()),
                Base64.getEncoder().encodeToString(c.iv + c.doFinal(value.toByteArray())),
            )
            .apply()
    }

    fun get(name: String): String? {
        val value = prefs.getString(digest(name.toByteArray()), null) ?: return null
        return runCatching {
                val bytes = Base64.getDecoder().decode(value)
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                c.updateAAD(name.toByteArray())
                String(c.doFinal(bytes.copyOfRange(12, bytes.size)))
            }
            .getOrNull()
    }

    fun remove(name: String) {
        prefs.edit().remove(digest(name.toByteArray())).apply()
    }

    fun identity(origin: String): Identity {
        val name = "identity:$origin"
        val seed =
            get(name)?.let { Base64.getDecoder().decode(it) }
                ?: ByteArray(32).also {
                    SecureRandom().nextBytes(it)
                    put(name, Base64.getEncoder().encodeToString(it))
                }
        return Identity(seed)
    }
}

class Identity(seed: ByteArray) {
    private val key = Ed25519PrivateKeyParameters(seed, 0)
    private val pub = key.generatePublicKey().encoded
    val id = digest(pub)

    fun device(nonce: String, ts: Long, token: String, scopes: List<String>): JSONObject {
        require(nonce.isNotEmpty() && ts >= 0)
        val payload =
            listOf(
                    "v3",
                    id,
                    "openclaw-android",
                    "ui",
                    "operator",
                    scopes.joinToString(","),
                    ts.toString(),
                    token,
                    nonce,
                    "android",
                    "phone",
                )
                .joinToString("|")
                .toByteArray()
        val signer = Ed25519Signer()
        signer.init(true, key)
        signer.update(payload, 0, payload.size)
        return obj(
            "id" to id,
            "publicKey" to b64(pub),
            "signature" to b64(signer.generateSignature()),
            "signedAt" to ts,
            "nonce" to nonce,
        )
    }
}
