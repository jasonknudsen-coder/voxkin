package studio.voxkin.android

/** Monotonic, in-memory durations only. No message content or identifiers. */
data class TurnTiming(
    private val sentAt: Long? = null,
    private val finalAt: Long? = null,
    private val receivedAt: Long? = null,
    val lastWordToSend: Long? = null,
    val finishingDictation: Long? = null,
    val receiptCue: Long? = null,
    val speechPreparation: Long? = null,
    val firstUpdate: Long? = null,
    val reply: Long? = null,
    val speechRequest: Long? = null,
    val playbackSetup: Long? = null,
    val firstAudio: Long? = null,
) {
    fun sent(now: Long, capture: CaptureTiming.Metrics? = null) = TurnTiming(sentAt = now,
        lastWordToSend = capture?.let { (now - it.lastWordAt).coerceAtLeast(0) }, finishingDictation = capture?.finishing)
    fun acknowledged(now: Long) = if (receiptCue == null && sentAt != null)
        copy(receiptCue = (now - sentAt).coerceAtLeast(0)) else this
    fun update(now: Long) = if (firstUpdate == null && sentAt != null)
        copy(firstUpdate = (now - sentAt).coerceAtLeast(0)) else this
    fun final(now: Long) = if (finalAt == null && sentAt != null)
        copy(finalAt = now, reply = (now - sentAt).coerceAtLeast(0)) else this
    fun received(now: Long) = if (receivedAt == null && finalAt != null)
        copy(receivedAt = now, speechRequest = (now - finalAt).coerceAtLeast(0)) else this
    fun audio(now: Long) = if (firstAudio == null && sentAt != null && receivedAt != null)
        copy(firstAudio = (now - sentAt).coerceAtLeast(0), playbackSetup = (now - receivedAt).coerceAtLeast(0),
            speechPreparation = finalAt?.let { (now - it).coerceAtLeast(0) }) else this
}
