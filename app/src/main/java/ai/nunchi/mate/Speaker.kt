package ai.nunchi.mate

import ai.nunchi.engine.SpeechWindow
import ai.nunchi.engine.VoiceEngine
import ai.nunchi.engine.VoiceOption
import ai.nunchi.mate.signals.PleosSpeech
import ai.nunchi.mate.voice.PlatformVoice
import ai.nunchi.mate.voice.RecordedVoice
import ai.nunchi.mate.voice.Voice
import ai.nunchi.mate.voice.VoiceStyle
import android.content.Context
import android.os.Handler
import android.os.Looper

class Speaker(context: Context, private val onDone: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var currentId: String? = null

    private val pleos: Voice? = PleosSpeech.createVoice(context) { finished() }
    private val platform: Voice = PlatformVoice(context) { finished() }
    private val recorded = RecordedVoice(context) { finished() }

    private val active: Voice?
        get() = pleos?.takeIf { it.ready } ?: platform.takeIf { it.ready }

    var voiceStyle: VoiceStyle = VoiceStyle.SOFT
        set(value) {
            field = value
            pleos?.configure(value)
            platform.configure(value)
        }

    var volume: Float = 1f
        set(value) {
            field = value
            recorded.volume = value
            platform.setVolume(value)
            pleos?.setVolume(value)
        }

    val engineName: String
        get() = if (voice.engine == VoiceEngine.RECORDED && recorded.lineCount > 0) {
            "미리 녹음 ${voice.label} (그 밖 문장: ${active?.name ?: "텍스트"})"
        } else {
            active?.name ?: "음성 없음 (텍스트로 표시)"
        }

    var voice: VoiceOption = VoiceOption.DEFAULT
        set(value) {
            field = value
            when (value.engine) {
                VoiceEngine.RECORDED -> pleos?.setSpeaker(value.fallbackSpeaker)
                VoiceEngine.PLEOS -> pleos?.setSpeaker(value.id)
            }
        }

    fun speak(id: String, text: String) {
        currentId = id
        if (voice.engine == VoiceEngine.RECORDED && recorded.play(voice.id, text)) return
        val voice = active
        if (voice != null) {
            voice.configure(voiceStyle)
            voice.speak(text)
        } else {
            val ms = (SpeechWindow.estimateSec(text) * 1000).toLong()
            main.postDelayed({ finished() }, FALLBACK_TOKEN, ms)
        }
    }

    fun stop() {
        currentId = null
        pleos?.takeIf { it.ready }?.stop()
        platform.takeIf { it.ready }?.stop()
        recorded.stop()
        main.removeCallbacksAndMessages(FALLBACK_TOKEN)
    }

    fun shutdown() {
        stop()
        pleos?.release()
        platform.release()
        recorded.release()
    }

    private fun finished() {
        main.post {
            val id = currentId ?: return@post
            currentId = null
            onDone(id)
        }
    }

    private companion object {
        val FALLBACK_TOKEN = Any()
    }
}
