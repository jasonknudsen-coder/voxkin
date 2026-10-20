package studio.voxkin.android

import java.util.UUID
import org.json.JSONObject

/** Only a canonical request ID is retained; server error text is never displayed. */
class GatewayPairingRequired(rawRequestId: String?) : GatewayFailure(
    "Waiting for gateway approval. Approve this phone on the gateway, then tap Retry connection."
) {
    val requestId: String? = rawRequestId?.takeIf {
        it.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
    }?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
    val approvalCommand: String? get() = requestId?.let { "openclaw devices approve $it" }
}

object GatewayErrors {
    fun owner(error: JSONObject?): GatewayFailure {
        val details = error?.optJSONObject("details")
        val code = (details?.opt("code") as? String)?.takeIf { it.isNotBlank() }
            ?: (error?.opt("code") as? String)
        return if (code in listOf("PAIRING_REQUIRED", "NOT_PAIRED"))
            GatewayPairingRequired(details?.opt("requestId") as? String)
        else GatewayFailure("Gateway rejected request. Check access and method support. Nothing was replayed.")
    }
}
