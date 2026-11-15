package ai.nunchi.engine.chat

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class TopicModelTest {
    private data class Row(val text: String, val ids: LongArray, val python: ChatTopic, val gold: ChatTopic?)

    private val rows = resource("topic_expected.tsv").lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map {
        val c = it.split('\t')
        Row(c[0], c[1].split(' ').map(String::toLong).toLongArray(), ChatTopic.of(c[2])!!, ChatTopic.of(c[3]))
    }.toList()

    @Test
    fun tokenizerMatchesHuggingFace() {
        for (r in rows) assertArrayEquals(r.text, r.ids, tokenizer.encode(r.text))
        assertArrayEquals(longArrayOf(0, 2), tokenizer.encode("   "))
    }

    @Test
    fun modelMatchesPythonAndHoldout() {
        var same = 0
        for (r in rows) if (classify(r.text).first == r.python) same++
        assertTrue("파이썬과 같은 분류 $same/${rows.size}", same >= rows.size - 2)

        val hold = resource("topic_holdout.tsv").lineSequence().filter { it.isNotBlank() }.map { it.split('\t') }.toList()
        val right = hold.count { (gold, text) -> classify(text).first.wire == gold }
        assertTrue("처음 보는 문장 정확도 $right/${hold.size}", right >= hold.size * 0.9)
    }

    @Test
    fun demoSentenceIsFood() {
        assertEquals(null, TopicClassifier.byKeyword("아까부터 꼬르륵 소리가 나네"))
        assertEquals(ChatTopic.FOOD, classify("아까부터 꼬르륵 소리가 나네").first)
    }

    companion object {
        private val dir = File("../app/src/main/assets/topic")
        private lateinit var tokenizer: WordPiece
        private lateinit var head: TopicHead
        private lateinit var env: OrtEnvironment
        private lateinit var session: OrtSession

        private fun resource(name: String) = TopicModelTest::class.java.classLoader.getResource(name)!!.readText()

        @BeforeClass @JvmStatic
        fun load() {
            tokenizer = WordPiece(File(dir, "vocab.txt").readLines())
            head = TopicHead.parse(File(dir, "head.tsv").readText())
            val model = File.createTempFile("topic", ".onnx").apply { deleteOnExit() }
            model.outputStream().use { out ->
                dir.listFiles()!!.filter { it.name.endsWith(".bin") }.sortedBy { it.name }.forEach { f -> f.inputStream().use { it.copyTo(out) } }
            }
            env = OrtEnvironment.getEnvironment()
            session = env.createSession(model.path, OrtSession.SessionOptions())
        }

        @AfterClass @JvmStatic
        fun close() = session.close()

        fun classify(text: String): Pair<ChatTopic, Float> {
            val ids = tokenizer.encode(text)
            OnnxTensor.createTensor(env, arrayOf(ids)).use { t ->
                OnnxTensor.createTensor(env, arrayOf(LongArray(ids.size) { 1 })).use { m ->
                    session.run(mapOf("ids" to t, "mask" to m)).use { out ->
                        @Suppress("UNCHECKED_CAST")
                        return head.classify((out[0].value as Array<FloatArray>)[0])
                    }
                }
            }
        }
    }
}
