package ai.nunchi.engine.chat

import ai.nunchi.engine.LoadZone
import ai.nunchi.engine.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReplyParserTest {
    private fun reply(line: String, short: String? = null) = ChatReply(line, short, ChatIntent.SMALL_TALK)

    @Test
    fun `blank line is rejected`() {
        assertNull(ChatReplyParser.sanitize(reply("   "), driving = true))
        assertNull(ChatReplyParser.sanitize(reply(""), driving = true))
    }

    @Test
    fun `driving line is cut at a sentence end within 40 chars`() {
        val long = "오늘 길이 꽤 막히네요. 그래도 금방 풀릴 거예요. 조금만 더 힘내요, 거의 다 왔어요!"
        val r = ChatReplyParser.sanitize(reply(long), driving = true)!!
        assertTrue(r.line.length <= ChatReplyParser.DRIVING_MAX)
        assertEquals("오늘 길이 꽤 막히네요. 그래도 금방 풀릴 거예요.", r.line)
    }

    @Test
    fun `without sentence end the cut falls on a space`() {
        val long = "지금 가는 길 옆으로 강이 보이는데 해가 질 무렵이면 물빛이 정말 예쁘게 바뀌는 곳이라고 해요"
        val cut = ChatReplyParser.truncate(long, ChatReplyParser.DRIVING_MAX)
        assertTrue(cut.length <= ChatReplyParser.DRIVING_MAX)
        assertTrue(long.startsWith(cut))
        assertFalse(cut.endsWith(" "))
        assertEquals(' ', long[cut.length])
    }

    @Test
    fun `parked allows longer lines`() {
        val long = "지금 지나는 성수동은 예전에 구두 공장과 창고가 모여 있던 곳이에요. 요즘은 카페가 많아졌대요."
        assertEquals(long, ChatReplyParser.sanitize(reply(long), driving = false)!!.line)
        assertTrue(ChatReplyParser.sanitize(reply(long), driving = true)!!.line.length <= ChatReplyParser.DRIVING_MAX)
    }

    @Test
    fun `short line is trimmed and dropped when not shorter`() {
        assertNull(ChatReplyParser.sanitize(reply("좋아요.", "좋아요. 정말 좋아요. 아주 좋아요. 진짜 좋아요."), true)!!.shortLine)
        val r2 = ChatReplyParser.sanitize(
            reply("앞에 드라이브스루가 있는데 잠깐 들러서 쉬었다 갈까요?", "앞에 드라이브스루가 있어요. 정말 맛있는 곳이래요. 들를까요?"),
            driving = true,
        )!!
        assertTrue(r2.shortLine!!.length <= ChatReplyParser.SHORT_MAX)
    }

    @Test
    fun `newlines, markdown and emoji are removed`() {
        assertEquals("좋아요! 같이 가요", ChatReplyParser.sanitize(reply("**좋아요!** 😀\n같이 가요 🚗"), driving = true)!!.line)
    }

    @Test
    fun `non korean replies are rejected`() {
        listOf("Sure, there is a drive-thru ahead.", "ドライブスルーがありますよ。", "OK, drive-thru 있어요")
            .forEach { assertNull(it, ChatReplyParser.sanitize(reply(it), driving = true)) }
        assertEquals("OK, 앞에 휴게소가 있어요.", ChatReplyParser.sanitize(reply("OK, 앞에 휴게소가 있어요."), true)!!.line)
        assertNull(ChatReplyParser.sanitize(reply("앞에 드라이브스루가 있는데 들를까요?", "Drive-thru ahead?"), true)!!.shortLine)
    }

    @Test
    fun `reply becomes a normal message with short ttl`() {
        val m = ChatReply("네, 좋아요.", "좋아요.", ChatIntent.SMALL_TALK).toMessage("brain-1", 1_000)
        assertEquals(Priority.NORMAL, m.priority)
        assertEquals("좋아요.", m.shortText)
        assertFalse(m.isExpired(1_000 + ChatReply.TTL_MS))
        assertTrue(m.isExpired(1_001 + ChatReply.TTL_MS))
    }

    @Test
    fun `ai is not allowed in red`() {
        assertFalse(CarState(LoadZone.RED, 60, 80).aiAllowed)
        listOf(LoadZone.YELLOW, LoadZone.GREEN, LoadZone.PARKED).forEach { assertTrue(CarState(it, 0).aiAllowed) }
    }

    @Test
    fun `car summary`() {
        assertEquals("주행 중 42km/h, 운전 부하 낮음, 다음 회전까지 820m", CarState(LoadZone.GREEN, 42, 820).summary())
        assertEquals("정차 중(P), 운전 부하 낮음", CarState(LoadZone.PARKED, 0).summary())
    }
}
