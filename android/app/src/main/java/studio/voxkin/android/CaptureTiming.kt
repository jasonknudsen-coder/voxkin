package studio.voxkin.android

/** Capture-local timing survives edits to the same reviewed draft, but is consumed once. */
class CaptureTiming {
    data class Metrics(val lastWordAt: Long, val finishing: Long?)
    private var context: String? = null
    private var lastText = ""
    private var lastWordAt: Long? = null
    private var finishRequestedAt: Long? = null
    private var finishing: Long? = null
    private var completed = false
    fun clear() { context = null; lastText = ""; lastWordAt = null; finishRequestedAt = null; finishing = null; completed = false }
    fun begin(scope: String) { clear(); context = scope }
    fun changed(text: String?, now: Long) {
        if (context == null || completed || text.isNullOrBlank() || text == lastText) return
        lastText = text; lastWordAt = now
    }
    fun finishing(now: Long) { if (context != null && !completed && finishRequestedAt == null) finishRequestedAt = now }
    fun complete(finalText: String?, now: Long) {
        if (context == null || completed) return
        changed(finalText, now)
        finishing = finishRequestedAt?.let { (now - it).coerceAtLeast(0) }
        completed = true
    }
    fun draftChanged(text: String, scope: String) { if (text.isBlank() || (context != null && context != scope)) clear() }
    fun consume(scope: String): Metrics? {
        val metrics = lastWordAt?.takeIf { completed && context == scope }?.let { Metrics(it, finishing) }
        clear()
        return metrics
    }
}
