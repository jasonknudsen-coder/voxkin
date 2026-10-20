package studio.voxkin.android

/** Authenticated advertised features, never inferred from a failed operation. */
data class HistoryCapabilities(
    val list: Boolean = false, val read: Boolean = false, val create: Boolean = false,
    val rename: Boolean = false, val delete: Boolean = false,
) {
    companion object {
        fun owner(methods: Collection<String>) = HistoryCapabilities(
            list = "sessions.list" in methods, read = "chat.history" in methods, create = true,
            rename = "sessions.patch" in methods,
            delete = "sessions.patch" in methods && "sessions.delete" in methods,
        )
        fun restricted(history: Boolean, actions: Boolean) = HistoryCapabilities(
            history, history, history, history && actions, history && actions,
        )
    }
}
