package studio.voxkin.android

import java.net.URLEncoder
import org.json.JSONObject

internal object AccessPolicy {
    val methods = setOf("voxkin.access.list", "voxkin.access.invite", "voxkin.access.approve", "voxkin.access.revoke", "voxkin.access.cancel")
    fun scopes(admin: Boolean) = listOf("operator.read", "operator.write") + if (admin) listOf("operator.admin") else emptyList()
    fun id(value: String) = value.matches(Regex("[a-f0-9]{32}"))
    fun agent(value: String) = value.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) && value != "main"
    fun label(value: String) = value.isNotBlank() && value == value.trim() && value.length <= 80 && value.none { it.isISOControl() }
    fun invitation(origin: String, secret: String): String {
        val text = "voxkin://join#gateway=${URLEncoder.encode(origin, "UTF-8")}&secret=${URLEncoder.encode(secret, "UTF-8")}"
        Protocol.setup(text)
        return text
    }
}

data class AccessRow(val id: String, val label: String, val agent: String, val status: String, val expiresAt: Long) {
    companion object {
        fun parse(row: JSONObject, device: Boolean): AccessRow {
            val id = row.getString("id"); val label = row.getString("label"); val agent = row.getString("agentId")
            val status = if (device) row.getString("status") else "invitation"
            require(AccessPolicy.id(id) && AccessPolicy.label(label) && AccessPolicy.agent(agent))
            require(!device || status in listOf("pending", "active", "revoked"))
            val expires = row.getLong("expiresAt")
            require(expires > 0)
            return AccessRow(id, label, agent, status, expires)
        }
    }
}

data class AccessConfirmation(val action: String, val id: String, val label: String, val agent: String, internal val generation: Int, internal val revision: Int)
