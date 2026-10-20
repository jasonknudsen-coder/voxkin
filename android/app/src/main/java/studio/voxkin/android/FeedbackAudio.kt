package studio.voxkin.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

internal interface FeedbackPlayback {
    fun stop()
    suspend fun play(bytes: ByteArray, valid: () -> Boolean)
}

/** Independent resource owner: never touches answer audio, status, timing or microphone. */
internal class FeedbackAudio(private val context: Context, private val changed: (Boolean) -> Unit, private val lostFocus: () -> Unit) : FeedbackPlayback {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var active: Clip? = null
    private inner class Clip(val player: MediaPlayer, val file: File, val focus: AudioFocusRequest) {
        var closed = false
        fun close() {
            if (closed) return
            closed = true
            player.release()
            file.delete()
            manager.abandonAudioFocusRequest(focus)
            if (active === this) { active = null; changed(false) }
        }
    }
    override fun stop() { active?.close() }
    override suspend fun play(bytes: ByteArray, valid: () -> Boolean) {
        if (!valid()) return
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        lateinit var focus: AudioFocusRequest
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change -> if (change < 0 && active?.let { !it.closed && it.focus === focus } == true && valid()) lostFocus() }.build()
        if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        val clip = try {
            val file = File.createTempFile("feedback-", ".audio", context.cacheDir)
            try { Clip(MediaPlayer(), file, focus) }
            catch (e: Exception) { file.delete(); throw e }
        }
        catch (e: Exception) { manager.abandonAudioFocusRequest(focus); throw e }
        active = clip
        try {
            clip.file.writeBytes(bytes)
            withTimeout(12000) {
                suspendCancellableCoroutine<Unit> { cont ->
                    clip.player.setAudioAttributes(attributes)
                    clip.player.setDataSource(clip.file.path)
                    clip.player.setOnPreparedListener { p ->
                        if (cont.isActive && !clip.closed && active === clip && valid() && p.duration in 1..12000) {
                            try { p.start(); changed(true) }
                            catch (e: Exception) { if (cont.isActive) cont.resumeWith(Result.failure(e)) }
                        } else if (cont.isActive) cont.cancel()
                    }
                    clip.player.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) {} }
                    clip.player.setOnErrorListener { _, _, _ -> if (cont.isActive) cont.cancel(); true }
                    clip.player.prepareAsync()
                }
            }
        } finally { clip.close() }
    }
}
