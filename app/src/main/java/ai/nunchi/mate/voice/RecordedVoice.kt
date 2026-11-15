package ai.nunchi.mate.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

class RecordedVoice(private val context: Context, private val onFinished: () -> Unit) {
    private val lines: Map<String, String> = runCatching {
        context.assets.open("voices/lines.tsv").bufferedReader(Charsets.UTF_8).useLines { rows ->
            rows.mapNotNull { row ->
                val tab = row.lastIndexOf('\t')
                if (tab <= 0) null else key(row.substring(0, tab)) to row.substring(tab + 1)
            }.toMap()
        }
    }.getOrElse {
        Log.w(TAG, "녹음 목록 없음: $it")
        emptyMap()
    }
    private val voices: Set<String> = context.assets.list("voices")?.toSet().orEmpty()
    private var player: MediaPlayer? = null
    var volume = 1f

    val lineCount: Int get() = lines.size

    fun has(voiceId: String, text: String): Boolean = voiceId in voices && key(text) in lines

    fun play(voiceId: String, text: String): Boolean {
        val n = lines[key(text)] ?: return false
        if (voiceId !in voices) return false
        stop()
        return runCatching {
            val afd = context.assets.openFd("voices/$voiceId/$n.ogg")
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            afd.use { p.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            p.setOnCompletionListener { done(it) }
            p.setOnErrorListener { mp, what, extra ->
                Log.w(TAG, "재생 오류 $voiceId/$n: $what/$extra")
                done(mp)
                true
            }
            p.setVolume(volume, volume)
            p.setOnPreparedListener { it.start() }
            player = p
            p.prepareAsync()
            true
        }.getOrElse {
            Log.w(TAG, "녹음 열기 실패 $voiceId/$n: $it")
            stop()
            false
        }
    }

    fun stop() {
        player?.let {
            it.setOnCompletionListener(null)
            it.setOnErrorListener(null)
            runCatching { it.release() }
        }
        player = null
    }

    fun release() = stop()

    private fun done(mp: MediaPlayer) {
        if (mp !== player) return
        stop()
        onFinished()
    }

    private companion object {
        const val TAG = "RecordedVoice"

        fun key(text: String) = text.trim().replace(Regex("\\s+"), " ")
    }
}
