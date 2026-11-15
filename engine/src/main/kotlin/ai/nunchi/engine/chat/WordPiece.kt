package ai.nunchi.engine.chat

class WordPiece(vocab: List<String>, private val maxTokens: Int = 128) {
    private val ids: Map<String, Int> = vocab.withIndex().associate { (i, t) -> t to i }
    private val cls = id("[CLS]")
    private val sep = id("[SEP]")
    private val unk = id("[UNK]")

    private fun id(token: String) = requireNotNull(ids[token]) { "vocab에 $token 없음" }

    fun encode(text: String): LongArray {
        val out = ArrayList<Long>()
        out += cls.toLong()
        for (word in words(text)) {
            for (piece in pieces(word)) {
                if (out.size >= maxTokens - 1) break
                out += piece.toLong()
            }
        }
        out += sep.toLong()
        return out.toLongArray()
    }

    private fun words(text: String): List<String> {
        val words = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() { if (cur.isNotEmpty()) { words += cur.toString(); cur.setLength(0) } }
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            when {
                cp == 0 || cp == 0xFFFD || isControl(cp) -> Unit
                isWhitespace(cp) -> flush()
                isPunctuation(cp) || isCjk(cp) -> { flush(); words += String(Character.toChars(cp)) }
                else -> cur.appendCodePoint(cp)
            }
        }
        flush()
        return words
    }

    private fun pieces(word: String): List<Int> {
        if (word.codePointCount(0, word.length) > MAX_WORD_CHARS) return listOf(unk)
        val result = ArrayList<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var found: Int? = null
            while (start < end) {
                val sub = (if (start > 0) "##" else "") + word.substring(start, end)
                found = ids[sub]
                if (found != null) break
                end = word.offsetByCodePoints(end, -1)
            }
            if (found == null) return listOf(unk)
            result += found
            start = end
        }
        return result
    }

    private companion object {
        const val MAX_WORD_CHARS = 100

        fun isWhitespace(cp: Int) = cp == ' '.code || cp == '\t'.code || cp == '\n'.code || cp == '\r'.code ||
            Character.getType(cp) == Character.SPACE_SEPARATOR.toInt()

        fun isControl(cp: Int): Boolean {
            if (cp == '\t'.code || cp == '\n'.code || cp == '\r'.code) return false
            val t = Character.getType(cp)
            return t == Character.CONTROL.toInt() || t == Character.FORMAT.toInt()
        }

        fun isPunctuation(cp: Int): Boolean {
            if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) return true
            return when (Character.getType(cp).toByte()) {
                Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
                Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                Character.OTHER_PUNCTUATION -> true
                else -> false
            }
        }

        fun isCjk(cp: Int) = cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0x20000..0x2A6DF || cp in 0x2A700..0x2B73F ||
            cp in 0x2B740..0x2B81F || cp in 0x2B820..0x2CEAF || cp in 0xF900..0xFAFF || cp in 0x2F800..0x2FA1F
    }
}

class TopicHead(private val topics: List<ChatTopic>, private val weights: List<FloatArray>, private val bias: FloatArray) {
    val dim: Int = weights.first().size

    fun classify(vector: FloatArray): Pair<ChatTopic, Float> {
        require(vector.size == dim) { "벡터 크기 ${vector.size} != $dim" }
        val logits = FloatArray(topics.size) { k -> bias[k] + weights[k].indices.sumOf { (weights[k][it] * vector[it]).toDouble() }.toFloat() }
        val max = logits.max()
        val exp = logits.map { Math.exp((it - max).toDouble()) }
        val best = logits.indices.maxBy { logits[it] }
        return topics[best] to (exp[best] / exp.sum()).toFloat()
    }

    companion object {
        fun parse(text: String): TopicHead {
            val rows = text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }.toList()
            val topics = rows.map { requireNotNull(ChatTopic.of(it[0])) { "모르는 분류 ${it[0]}" } }
            return TopicHead(topics, rows.map { r -> r[2].trim().split(' ').map(String::toFloat).toFloatArray() },
                rows.map { it[1].toFloat() }.toFloatArray())
        }
    }
}
