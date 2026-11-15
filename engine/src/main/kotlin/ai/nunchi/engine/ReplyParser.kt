package ai.nunchi.engine

enum class Reply { YES, NO, LATER, UNKNOWN }

object ReplyParser {
    val YES_KEYWORDS = listOf(
        "응", "어", "그래", "좋아", "좋지", "가자", "갈래", "콜", "오케이", "ok",
        "해줘", "넣어", "들를래", "들르자", "네", "예", "부탁", "보내",
    )
    val NO_KEYWORDS = listOf(
        "아니", "아냐", "싫어", "됐어", "괜찮", "다음에", "패스", "노", "안가", "안해", "말아", "말고", "나중에",
    )
    val LATER_KEYWORDS = listOf("잠깐", "잠시", "이따", "조금있다", "기다려")

    fun parse(text: String?): Reply {
        val t = normalize(text)
        if (t.isEmpty()) return Reply.UNKNOWN
        return when {
            NO_KEYWORDS.any { hit(t, it) } -> Reply.NO
            YES_KEYWORDS.any { hit(t, it) } -> Reply.YES
            LATER_KEYWORDS.any { hit(t, it) } -> Reply.LATER
            else -> Reply.UNKNOWN
        }
    }

    private fun hit(t: String, keyword: String): Boolean =
        if (keyword.length == 1) t.startsWith(keyword) else t.contains(keyword)

    fun normalize(text: String?): String {
        if (text == null) return ""
        val sb = StringBuilder()
        for (c in text) {
            if (c.isLetterOrDigit()) sb.append(c.lowercaseChar())
        }
        return sb.toString()
    }
}
