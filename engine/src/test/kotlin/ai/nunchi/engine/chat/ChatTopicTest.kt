package ai.nunchi.engine.chat

import ai.nunchi.engine.Persona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTopicTest {
    @Test
    fun keywordsPickTopic() {
        assertEquals(ChatTopic.FOOD, TopicClassifier.byKeyword("배고픈데 근처에 먹을 데 있어?"))
        assertEquals(ChatTopic.FOOD, TopicClassifier.byKeyword("배 고파"))
        assertEquals(ChatTopic.REST, TopicClassifier.byKeyword("아 너무 졸려"))
        assertEquals(ChatTopic.REST, TopicClassifier.byKeyword("눈꺼풀이 자꾸 무거워지네"))
        assertEquals(ChatTopic.ROUTE, TopicClassifier.byKeyword("언제 도착해?"))
        assertEquals(ChatTopic.MESSAGE, TopicClassifier.byKeyword("카톡 온 거 있어?"))
        assertEquals(ChatTopic.BORED, TopicClassifier.byKeyword("심심해"))
        assertEquals(ChatTopic.MOOD, TopicClassifier.byKeyword("오늘 너무 힘들었어"))
        assertEquals(ChatTopic.GREETING, TopicClassifier.byKeyword("고마워"))
        assertNull(TopicClassifier.byKeyword("오늘 날씨 어때"))
    }

    @Test
    fun controlWinsOverOtherWords() {
        assertEquals(ChatTopic.CONTROL, TopicClassifier.byKeyword("졸려서 그러는데 창문 좀 열어 줘"))
    }

    @Test
    fun everyLinePassesReplyRules() {
        for (topic in ChatTopic.entries) for (persona in Persona.entries) for (driving in listOf(true, false)) {
            val r = TopicLines.reply(topic, persona, driving)
            val clean = ChatReplyParser.sanitize(r, driving)
            assertEquals("$topic $persona driving=$driving", r.line, clean?.line)
            assertEquals(topic.intent, r.intent)
            if (ChatQuestion.needsConfirmation(topic.intent) && topic != ChatTopic.MESSAGE) {
                assertTrue("$topic $persona 제안은 질문으로 끝나야 한다", ChatQuestion.isQuestion(r.line))
            }
        }
    }

    @Test
    fun routeUsesEtaOnlyWhenKnown() {
        assertTrue("3시 25분" in TopicLines.reply(ChatTopic.ROUTE, Persona.BUDDY, true, "3시 25분").line)
        assertTrue("경로 안내" in TopicLines.reply(ChatTopic.ROUTE, Persona.BUDDY, true).line)
    }
}
