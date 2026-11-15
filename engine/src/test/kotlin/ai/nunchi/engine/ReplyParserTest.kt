package ai.nunchi.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyParserTest {
    @Test
    fun `short affirmatives`() {
        listOf("응", "응 가자", "네", "좋아", "그래 들르자", "OK", "답장 보내 줘").forEach {
            assertEquals(it, Reply.YES, ReplyParser.parse(it))
        }
    }

    @Test
    fun `negatives win over affirmatives`() {
        listOf("아니", "아니 괜찮아", "됐어", "다음에", "괜찮아요", "괜찮", "아니?").forEach {
            assertEquals(it, Reply.NO, ReplyParser.parse(it))
        }
    }

    @Test
    fun `unrelated speech is unknown`() {
        listOf("", "  ", "오늘 날씨 어때", "음악 틀어").forEach {
            assertEquals(it, Reply.UNKNOWN, ReplyParser.parse(it))
        }
    }
}
