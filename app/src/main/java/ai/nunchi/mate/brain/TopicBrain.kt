package ai.nunchi.mate.brain

import ai.nunchi.engine.chat.ChatRequest
import ai.nunchi.engine.chat.ChatTopic
import ai.nunchi.engine.chat.TopicHead
import ai.nunchi.engine.chat.WordPiece
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TopicBrain(
    context: Context,
    private val model: ModelFile,
    private val fallback: ChatBrain,
    private val timeoutMs: Long = 2_000,
) : ChatBrain {
    override val name = "분류 모델"

    private val assets = context.applicationContext.assets
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "topic-model").apply { isDaemon = true } }
    private val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "topic-timer").apply { isDaemon = true } }

    @Volatile private var loaded: Loaded? = null

    private class Loaded(val tokenizer: WordPiece, val head: TopicHead, val env: OrtEnvironment, val session: OrtSession)

    init {
        worker.execute { runCatching { load() }.onFailure { Log.w(TAG, "load failed", it) } }
    }

    private fun load() {
        val started = SystemClock.elapsedRealtime()
        val file = model.file()
        val tokenizer = WordPiece(assets.open("$DIR/vocab.txt").bufferedReader().readLines())
        val head = TopicHead.parse(assets.open("$DIR/head.tsv").bufferedReader().readText())
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(THREADS) }
        val session = env.createSession(file.path, options)
        loaded = Loaded(tokenizer, head, env, session)
        Log.i(TAG, "loaded ${model.name} in ${SystemClock.elapsedRealtime() - started}ms")
        classify(loaded!!, "안녕").also { Log.i(TAG, "warm-up ${it.third}ms") }
    }

    override fun reply(request: ChatRequest, onResult: (BrainAnswer) -> Unit) {
        val started = SystemClock.elapsedRealtime()
        fun elapsed() = SystemClock.elapsedRealtime() - started
        val settled = AtomicBoolean(false)
        fun fallBack(reason: String) {
            if (!settled.compareAndSet(false, true)) return
            Log.w(TAG, "fallback: $reason (${elapsed()}ms)")
            fallback.reply(request) { a -> onResult(a.copy(by = "${a.by} ← $reason", latencyMs = elapsed())) }
        }

        val ready = loaded ?: return fallBack("모델 준비 중")
        val timeout = timer.schedule({ fallBack("시간 초과") }, timeoutMs, TimeUnit.MILLISECONDS)
        worker.execute {
            val result = runCatching { classify(ready, request.utterance) }
            timeout.cancel(false)
            result.onSuccess { (topic, p, ms) ->
                if (settled.compareAndSet(false, true)) {
                    Log.i(TAG, "classified ${ms}ms topic=${topic.wire} p=${"%.2f".format(p)} \"${request.utterance}\"")
                    onResult(BrainAnswer(TopicReplies.compose(topic, request), name, elapsed(), topic))
                } else {
                    Log.w(TAG, "late ${elapsed()}ms topic=${topic.wire}")
                }
            }.onFailure { fallBack(it.javaClass.simpleName) }
        }
    }

    private fun classify(m: Loaded, text: String): Triple<ChatTopic, Float, Long> {
        val started = SystemClock.elapsedRealtime()
        val ids = m.tokenizer.encode(text.trim())
        val vector = OnnxTensor.createTensor(m.env, arrayOf(ids)).use { t ->
            OnnxTensor.createTensor(m.env, arrayOf(LongArray(ids.size) { 1 })).use { mask ->
                m.session.run(mapOf("ids" to t, "mask" to mask)).use { out ->
                    @Suppress("UNCHECKED_CAST")
                    (out[0].value as Array<FloatArray>)[0]
                }
            }
        }
        val (topic, p) = m.head.classify(vector)
        return Triple(topic, p, SystemClock.elapsedRealtime() - started)
    }

    override fun release() {
        worker.execute { loaded?.session?.close(); loaded = null }
        worker.shutdown()
        timer.shutdownNow()
        fallback.release()
    }

    companion object {
        private const val TAG = "TopicBrain"
        const val DIR = "topic"

        private const val THREADS = 2
    }
}
