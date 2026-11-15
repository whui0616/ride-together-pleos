package ai.nunchi.engine.chat

import ai.nunchi.engine.Message
import ai.nunchi.engine.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatQuestionTest {
    private fun brain(intent: ChatIntent, line: String, short: String? = null) =
        ChatReply(line, short, intent).toMessage(ChatQuestion.messageId(intent, 7), 0)

    @Test
    fun `intent survives in the message id`() {
        for (intent in ChatIntent.entries) {
            assertEquals(intent, ChatQuestion.intentOf(brain(intent, "네, 좋아요.")))
        }
    }

    @Test
    fun `non brain messages have no intent`() {
        val food = Message("food-1", "들렀다 갈까요?", Priority.SUGGESTION, 0)
        assertNull(ChatQuestion.intentOf(food))
        assertFalse(ChatQuestion.asks(food))
        assertNull(ChatQuestion.intentOf(Message("brain-3", "들를까요?", Priority.NORMAL, 0)))
    }

    @Test
    fun `stop and reply questions are asked`() {
        assertTrue(ChatQuestion.asks(brain(ChatIntent.SUGGEST_STOP, "앞에 드라이브스루가 있어요. 들를까요?")))
        assertTrue(ChatQuestion.asks(brain(ChatIntent.REPLY_MESSAGE, "곧 도착한다고 답장할까요?")))
        assertTrue(ChatQuestion.asks(brain(ChatIntent.SUGGEST_STOP, "휴게소에서 쉬어 갈까요.")))
    }

    @Test
    fun `other intents are never asked`() {
        for (intent in listOf(ChatIntent.SMALL_TALK, ChatIntent.ROUTE_INFO, ChatIntent.DECLINE)) {
            assertFalse(intent.wire, ChatQuestion.asks(brain(intent, "음악 좀 틀어 드릴까요?")))
        }
    }

    @Test
    fun `statement with a confirm intent is not asked`() {
        assertFalse(ChatQuestion.asks(brain(ChatIntent.REPLY_MESSAGE, "새 메시지는 운전이 한가할 때 읽어 드릴게요.")))
    }

    @Test
    fun `decision follows the spoken text`() {
        val m = brain(ChatIntent.SUGGEST_STOP, "근처에 쉬어 갈 곳이 있어요. 들를까요?", "쉴 곳이 있어요.")
        assertTrue(ChatQuestion.asks(m))
        assertFalse(ChatQuestion.asks(m, spoken = "쉴 곳이 있어요."))
        assertTrue(ChatQuestion.asks(m, spoken = "아까 하던 얘기 마저 할게요. 근처에 쉬어 갈 곳이 있어요. 들를까요?"))
    }
}
