package ai.nunchi.mate.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class PlatformVoice(context: Context, private val onFinished: () -> Unit) : Voice {
    override val name = "Android TTS"
    override var ready = false
        private set

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS && hasKorean()
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = onFinished()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = onFinished()
        })
    }

    override fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }, UTTERANCE_ID)
    }

    private var volume = 1f
    override fun setVolume(v: Float) { volume = v }

    override fun configure(style: VoiceStyle) {
        tts.setPitch(style.pitch)
        tts.setSpeechRate(style.rate)
    }

    override fun stop() {
        tts.stop()
    }

    override fun release() {
        tts.shutdown()
    }

    private fun hasKorean(): Boolean = tts.setLanguage(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE

    private companion object {
        const val UTTERANCE_ID = "nunchi"
    }
}
