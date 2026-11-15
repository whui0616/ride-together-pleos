package ai.nunchi.mate.signals

import ai.nunchi.mate.voice.Voice
import ai.nunchi.mate.voice.VoiceInput
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.util.Log
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

object PleosSpeech {
    fun createVoice(context: Context, onFinished: () -> Unit): Voice = PleosVoice(context, onFinished)

    fun createInput(context: Context): VoiceInput = PleosVoiceInput(context)
}

private class PleosVoice(context: Context, private val onFinished: () -> Unit) : Voice {
    override val name = "Pleos TTS (온디바이스)"
    override var ready = false
        private set

    private val tts = TextToSpeech(context.applicationContext, TtsMode.ON_DEVICE)
    private val listener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = onFinished()
        override fun onStop() = Unit
        override fun onError(errorType: TtsError) {
            Log.w(TAG, "TTS error $errorType")
            onFinished()
        }
    }

    @Volatile private var speaker: String? = null

    init {
        tts.initialize(object : TtsInit {
            override fun onSuccess() {
                tts.addEventListener(listener)
                tts.setLocale(Locale.KOREA)
                speaker?.let(::applySpeaker)
                ready = true
            }

            override fun onFailure(errorType: TtsError) {
                Log.w(TAG, "TTS init failed $errorType")
            }
        })
    }

    override fun setSpeaker(id: String) {
        speaker = id
        if (ready) applySpeaker(id)
    }

    private fun applySpeaker(id: String) {
        tts.setSpeakerType(id)
        Log.d(TAG, "TTS speaker $id")
    }

    override fun speak(text: String) = tts.speak(text, true)

    override fun stop() = tts.stop()

    override fun release() {
        tts.removeEventListener(listener)
        tts.release()
    }
}

private class PleosVoiceInput(context: Context) : VoiceInput {
    private var ready = false
    private var onResult: ((String) -> Unit)? = null
    private var onEnd: (() -> Unit)? = null
    private var onReady: (() -> Unit)? = null

    private var peakRms = 0.0
    private var lastPartial = ""

    private val stt = SpeechToText(context.applicationContext, SttMode.ON_DEVICE)
    private val listener = object : ResultListener {
        override fun onReady() {
            peakRms = 0.0
            lastPartial = ""
            Log.d(TAG, "STT ready")
            onReady?.invoke()
        }

        override fun onStartedRecognition() {
            Log.d(TAG, "STT started")
        }

        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) {
            if (rms > peakRms) peakRms = rms
        }

        override fun onUpdated(stt: String, completed: Boolean) {
            if (completed) Log.d(TAG, "STT result \"$stt\" peakRms=${"%.1f".format(peakRms)}")
            else if (stt != lastPartial) Log.d(TAG, "STT partial \"$stt\"")
            lastPartial = stt
            if (completed) onResult?.invoke(stt)
        }

        override fun onEndedRecognition() {
            Log.d(TAG, "STT ended peakRms=${"%.1f".format(peakRms)}")
            onEnd?.invoke()
        }

        override fun onError(errorType: SttError) {
            Log.w(TAG, "STT error $errorType")
            onEnd?.invoke()
        }
    }

    init {
        stt.initialize(object : SttInit {
            override fun onSuccess() {
                stt.addListener(listener)
                stt.setLocale(Locale.KOREA)
                ready = true
            }

            override fun onFailure(errorType: SttError) {
                Log.w(TAG, "STT init failed $errorType")
            }
        })
    }

    override fun listen(onResult: (String) -> Unit, onEnd: () -> Unit, onReady: () -> Unit): Boolean {
        if (!ready) return false
        this.onResult = onResult
        this.onEnd = onEnd
        this.onReady = onReady
        Log.d(TAG, "STT request")
        stt.request()
        return true
    }

    override fun stop() {
        Log.d(TAG, "STT stop peakRms=${"%.1f".format(peakRms)}")
        stt.stop()
    }

    override fun release() {
        stt.removeListener(listener)
        stt.release()
    }
}

private const val TAG = "PleosSpeech"
