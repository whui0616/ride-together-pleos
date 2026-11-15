package ai.nunchi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreTest {

    @Test
    fun `하루 상한 500을 넘기면 남은 만큼만 준다`() {
        var s = ScoreState()
        var total = 0
        repeat(40) { val (n, g) = s.grant(1, XpReason.ATTENDANCE); s = n; total += g }
        assertEquals(500, total)
        assertEquals(0, s.remainingToday(1))
        assertEquals(500, s.remainingToday(2))
        assertEquals(20, s.grant(2, XpReason.ATTENDANCE).second)
    }

    @Test
    fun `장부는 최근 50건만`() {
        var s = ScoreState()
        repeat(60) { s = s.grant(1, XpReason.SUGGESTION_ANSWERED).first }
        assertEquals(50, s.ledger.size)
        assertTrue(s.ledger.all { it.reason == XpReason.SUGGESTION_ANSWERED && it.xp == 2 })
    }

    @Test
    fun `경험치 사유에 운전 채점이 없다`() {
        val banned = listOf("부드러운", "방향지시등", "깜빡이", "급제동", "급가속", "감속", "안전 점수", "km", "거리", "속도")
        XpReason.entries.forEach { r -> banned.forEach { w -> assertFalse("${r.name}: ${r.label}", r.label.contains(w)) } }
    }

    @Test
    fun `첫 주행은 첫 주행과 트립 보너스를 주고 두 번째부터는 트립만`() {
        val d = DriveSummary(durationMin = 12, rainy = false)
        val (s1, b1) = ScoreState().endDrive(1, d)
        assertEquals(listOf(XpReason.FIRST_DRIVE, XpReason.TRIP), b1)
        assertTrue(s1.firstDriveDone)
        assertEquals(1, s1.trips)
        val (s2, b2) = s1.endDrive(1, d)
        assertEquals(listOf(XpReason.TRIP), b2)
        assertEquals(2, s2.trips)
        assertEquals(2, s2.tripsOn(1))
        assertEquals(0, s2.tripsOn(2))
    }

    @Test
    fun `트립 경험치는 하루 다섯 번까지만, 횟수는 계속 센다`() {
        val d = DriveSummary(durationMin = 1, rainy = false)
        var s = ScoreState(firstDriveDone = true)
        val perTrip = (1..7).map { val (n, b) = s.endDrive(3, d); s = n; b.count { it == XpReason.TRIP } }
        assertEquals(listOf(1, 1, 1, 1, 1, 0, 0), perTrip)
        assertEquals(7, s.trips)
        assertEquals(listOf(XpReason.TRIP), s.endDrive(4, d).second)
    }

    @Test
    fun `처음 겪는 새벽과 빗길은 한 번씩만`() {
        val rain = DriveSummary(durationMin = 30, rainy = true)
        val (s1, b1) = ScoreState(firstDriveDone = true).endDrive(1, rain, dawn = true)
        assertEquals(listOf(XpReason.TRIP, XpReason.FIRST_DAWN, XpReason.FIRST_RAIN), b1)
        assertTrue(s1.firstDawnDone && s1.firstRainDone)
        assertEquals(listOf(XpReason.TRIP), s1.endDrive(1, rain, dawn = true).second)
    }

    @Test
    fun `오래 달렸다고 더 주지 않는다`() {
        val short = ScoreState(firstDriveDone = true).endDrive(1, DriveSummary(durationMin = 3, rainy = false)).second
        val long = ScoreState(firstDriveDone = true).endDrive(1, DriveSummary(durationMin = 300, rainy = false)).second
        assertEquals(short, long)
    }

    @Test
    fun `새 지역은 방문 지역 수로 센다`() {
        assertEquals(2, ScoreState().visitNewPlace().visitNewPlace().placesVisited)
    }

    @Test
    fun `레벨 칭호와 최대 레벨`() {
        assertEquals("새싹 친구", LevelTitles.of(1))
        assertEquals("동네 친구", LevelTitles.of(7))
        assertEquals("가족", LevelTitles.of(50))
        assertEquals(10 to "단짝", LevelTitles.next(5))
        assertNull(LevelTitles.next(50))
        assertEquals(50, CompanionProfile(xp = 99_999).level)
    }

    @Test
    fun `주행 구간은 P 해제 후 움직이면 시작하고 P로 끝난다`() {
        val t = DriveSessionTracker()
        assertNull(t.feed(DrivingSignals(gear = Gear.PARK), 0))
        assertNull(t.feed(DrivingSignals(gear = Gear.DRIVE, speedKmh = 0f), 1_000))
        assertFalse(t.driving)
        assertNull(t.feed(DrivingSignals(gear = Gear.DRIVE, speedKmh = 10f, wiper = WiperLevel.LOW), 2_000))
        assertTrue(t.driving)
        assertNull(t.feed(DrivingSignals(gear = Gear.DRIVE, speedKmh = 30f, wiper = WiperLevel.LOW), 3_000))
        val d = t.feed(DrivingSignals(gear = Gear.PARK), 2_000 + 125 * 60_000L)!!
        assertEquals(125L, d.durationMin)
        assertTrue(d.rainy)
        assertFalse(t.driving)
        assertNull(t.feed(DrivingSignals(gear = Gear.DRIVE, speedKmh = 20f), 9_000_000))
        assertFalse(t.feed(DrivingSignals(gear = Gear.PARK), 9_060_000)!!.rainy)
    }
}
