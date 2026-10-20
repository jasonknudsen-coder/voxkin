package studio.voxkin.android

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class SpokenDraft {
    private var text: String? = null
    private var context: String? = null
    fun clear() { text = null; context = null }
    fun markFinal(value: String, scope: String) { text = value.takeIf { it.isNotBlank() }; context = scope }
    fun matches(value: String, scope: String) = text != null && text == value && context == scope
    fun consume(value: String, scope: String): Boolean = matches(value, scope).also { clear() }
}

data class FeedbackToken(val epoch: Long)

/** No audio/transport side effects; every optional action is one-shot and revocable. */
class TurnFeedback {
    private var epoch = 0L
    private var enabled = false
    private var run = ""
    private var accepted = false
    private var receipt = false
    private var settled = false
    private var progress = false
    private var notice: String? = null
    fun begin(run: String, eligible: Boolean): FeedbackToken {
        invalidate()
        this.run = run; enabled = eligible
        accepted = false; receipt = false; settled = false; progress = false; notice = null
        return FeedbackToken(epoch)
    }
    fun valid(token: FeedbackToken) = enabled && token.epoch == epoch
    fun invalidate() { epoch++; enabled = false }
    fun accept(token: FeedbackToken, returnedRun: String?): Boolean {
        if (!valid(token) || returnedRun != run) return false
        accepted = true
        return true
    }
    fun offer(token: FeedbackToken, text: String): Boolean {
        if (!valid(token) || notice != null || progress || text.isBlank() ||
            text.codePointCount(0, text.length) > 180 || "```" in text || "://" in text) return false
        notice = text
        return true
    }
    fun claimReceipt(token: FeedbackToken): Boolean {
        if (!valid(token) || !accepted || receipt) return false
        receipt = true
        return true
    }
    fun receiptSettled(token: FeedbackToken) { if (valid(token) && receipt) settled = true }
    fun claimProgress(token: FeedbackToken): String? {
        if (!valid(token) || !accepted || !settled || progress) return null
        val result = notice ?: return null
        progress = true
        return result
    }
}

object ReceiptTone {
    fun wav(): ByteArray {
        val samples = 2880
        val buffer = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        buffer.putInt(16).putShort(1).putShort(1).putInt(24000).putInt(48000)
        buffer.putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { i ->
            val fade = sin(PI * i / (samples - 1))
            buffer.putShort((1800 * fade * fade * sin(2 * PI * 660 * i / 24000)).toInt().toShort())
        }
        return buffer.array()
    }
}
