package ai.nunchi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyStateTest {

    @Test
    fun `첫 방문은 연속 1일`() {
        assertEquals(1, LobbyState().visit(100).streak)
    }

    @Test
    fun `같은 날 여러 번 와도 그대로`() {
        val s = LobbyState().visit(100)
        assertEquals(s, s.visit(100))
    }

    @Test
    fun `다음 날 오면 이어지고 하루 빠지면 1부터`() {
        val s = LobbyState().visit(100).visit(101).visit(102)
        assertEquals(3, s.streak)
        assertEquals(1, s.visit(104).streak)
    }

    @Test
    fun `시계가 뒤로 가면 1부터`() {
        assertEquals(1, LobbyState().visit(100).visit(101).visit(99).streak)
    }

    @Test
    fun `쓰다듬기는 하루 3번까지`() {
        var s = LobbyState()
        repeat(5) { s = s.pet(100) }
        assertEquals(3, s.affinity)
        assertFalse(s.canPet(100))
        assertTrue(s.canPet(101))
        assertEquals(4, s.pet(101).affinity)
    }

    @Test
    fun `친밀도 레벨은 5마다 오르고 10에서 멈춤`() {
        assertEquals(1, LobbyState(affinity = 4).affinityLevel)
        assertEquals(2, LobbyState(affinity = 5).affinityLevel)
        assertEquals(10, LobbyState(affinity = 999).affinityLevel)
    }

    @Test
    fun `호칭은 친밀도 레벨 따라 바뀜`() {
        assertEquals("민수님", LobbyState().addressing("민수"))
        assertEquals("친구", LobbyState(affinity = 10).addressing())
        assertEquals("단짝", LobbyState(affinity = 25).addressing())
        assertEquals("가족", LobbyState(affinity = 40).addressing())
    }

    @Test
    fun `출석 달력은 방문한 날만 쌓이고 최근 90일만`() {
        var s = LobbyState()
        for (d in 1L..95L) s = s.visit(d)
        assertEquals(90, s.visitDays.size)
        assertEquals(95L, s.visitDays.last())
        assertEquals(6L, s.visitDays.first())
    }

    @Test
    fun `며칠 안 오면 기분이 하루 15씩 내려가고 0 아래로는 안 감`() {
        val s = LobbyState(mood = 70).visit(100)
        assertEquals(70, s.mood)
        assertEquals(40, s.visit(103).mood)
        assertEquals(0, s.visit(120).mood)
    }

    @Test
    fun `쓰다듬기와 간식은 기분을 올리고 100에서 멈춤`() {
        val s = LobbyState(mood = 90, coins = 10).pet(1)
        assertEquals(95, s.mood)
        assertEquals(100, s.snack(1).mood)
    }

    @Test
    fun `간식은 코인 5개, 하루 3번`() {
        var s = LobbyState(coins = 12)
        assertTrue(s.canSnack(1))
        s = s.snack(1); s = s.snack(1)
        assertEquals(2, s.coins)
        assertFalse(s.canSnack(1))
        s = s.addCoins(100)
        s = s.snack(1)
        assertFalse(s.canSnack(1))
        assertTrue(s.canSnack(2))
    }

    @Test
    fun `연속 출석 보상은 3·7·30·100일에 한 번씩`() {
        var s = LobbyState()
        for (d in 1L..2L) s = s.visit(d)
        assertNull(s.pendingStreakReward())
        s = s.visit(3)
        assertEquals(StreakReward(3, 20), s.pendingStreakReward())
        s = s.claimStreakReward()
        assertEquals(20, s.coins)
        assertNull(s.pendingStreakReward())
        for (d in 4L..7L) s = s.visit(d)
        assertEquals(StreakReward(7, 50), s.pendingStreakReward())
        assertEquals(70, s.claimStreakReward().claimStreakReward().coins)
    }

    @Test
    fun `오늘의 미션은 교감 둘과 함께 다녀오기, 다 하면 코인 30`() {
        var s = LobbyState().visit(10)
        var m = s.missions(10, tripsToday = 0)
        assertEquals(listOf("캐릭터와 한마디", "쓰다듬기", "함께 다녀오기"), m.map { it.title })
        assertTrue(m.none { it.done })
        s = s.talk(10)
        repeat(3) { s = s.pet(10) }
        assertFalse(s.canClaimMissions(10, tripsToday = 0))
        assertTrue(s.missions(10, tripsToday = 1).all { it.done })
        assertTrue(s.canClaimMissions(10, 1))
        s = s.claimMissions(10, 1)
        assertEquals(30, s.coins)
        assertFalse(s.canClaimMissions(10, 1))
        s = s.visit(11)
        assertTrue(s.missions(11, tripsToday = 0).none { it.done })
    }

    @Test
    fun `기분 구간`() {
        assertEquals(MoodBand.GREAT, LobbyState(mood = 80).moodBand)
        assertEquals(MoodBand.GOOD, LobbyState(mood = 50).moodBand)
        assertEquals(MoodBand.LOW, LobbyState(mood = 30).moodBand)
        assertEquals(MoodBand.BAD, LobbyState(mood = 10).moodBand)
    }
}
