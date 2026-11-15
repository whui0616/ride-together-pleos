package ai.nunchi.engine.chat

object ChatReplyParser {
    const val DRIVING_MAX = 40
    const val PARKED_MAX = 160
    const val SHORT_MAX = 24

    fun sanitize(reply: ChatReply, driving: Boolean): ChatReply? {
        val line = truncate(clean(reply.line), if (driving) DRIVING_MAX else PARKED_MAX)
        if (!isKorean(line)) return null
        val short = reply.shortLine?.let { truncate(clean(it), SHORT_MAX) }
            ?.takeIf { isKorean(it) && it.length < line.length }
        return ChatReply(line, short, reply.intent)
    }

    internal fun isKorean(text: String): Boolean {
        val hangul = text.count { it in '가'..'힣' }
        val latin = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        return hangul > 0 && latin <= hangul
    }

    private fun clean(text: String): String =
        text.filterNot { it.isSurrogate() || it in "*#`_~>" }
            .replace(Regex("\\s+"), " ")
            .trim()

    internal fun truncate(text: String, max: Int): String {
        if (text.length <= max) return text
        val head = text.substring(0, max)
        val sentenceEnd = head.indexOfLast { it in ".!?。" }
        if (sentenceEnd >= max / 3) return head.substring(0, sentenceEnd + 1)
        val space = head.lastIndexOf(' ')
        if (space >= max / 2) return head.substring(0, space).trimEnd(',', ' ')
        return head
    }
}
