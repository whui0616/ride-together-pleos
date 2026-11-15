package ai.nunchi.engine.situation

import ai.nunchi.engine.CompanionProfile
import ai.nunchi.engine.DrivingSignals
import ai.nunchi.engine.Gear
import ai.nunchi.engine.LoadZone
import ai.nunchi.engine.Message
import ai.nunchi.engine.Priority
import ai.nunchi.engine.WiperLevel
import ai.nunchi.engine.chat.ChatReplyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SituationDetectorTest {
    private class Drive(start: LocalDateTime, gapMs: Long = 90_000) {
        val memory = SituationMemory.InMemory()
        var detector = SituationDetector(memory, gapMs)
        var time: LocalDateTime = start
        var nowMs = 1_000_000L
        var signals = DrivingSignals(gear = Gear.PARK)
        var zone = LoadZone.PARKED
        var profile = CompanionProfile()
        var lastSpokeAtMs = 0L
        val said = mutableListOf<Message>()

        fun run(ms: Long) {
            var left = ms
            do {
                said += detector.tick(signals, zone, profile, time, nowMs, lastSpokeAtMs)
                nowMs += TICK
                time = time.plusNanos(TICK * 1_000_000)
                left -= TICK
            } while (left > 0)
        }

        fun drive(kmh: Float) {
            signals = signals.copy(gear = Gear.DRIVE, speedKmh = kmh)
            zone = LoadZone.GREEN
        }

        fun park() {
            signals = signals.copy(gear = Gear.PARK, speedKmh = 0f)
            zone = LoadZone.PARKED
        }

        fun situations() = said.mapNotNull { Situation.of(it) }
        fun clear() = said.clear()

        companion object {
            const val TICK = 250L
        }
    }

    private val wednesdayMorning = LocalDateTime.of(2026, 12, 2, 7, 30)
    private val wednesdayAfternoon = LocalDateTime.of(2026, 11, 4, 15, 0)

    @Test
    fun `every line fits a driving utterance and maps back from its message`() {
        Situation.entries.forEach {
            val m = it.message(
                1L,
                mapOf(
                    "km" to "1200", "N" to "12", "목적지" to "판교 현대백화점 본관", "분" to "125", "끼니" to "점심",
                    "지역" to "서울 영등포구", "지역은" to "서울 영등포구는", "명소가" to "동피랑 벽화마을이",
                    "음식" to "초당순두부", "날짜" to "12월 25일", "도예요" to "충청남도예요",
                ),
            )
            assertTrue(m.text, m.text.length <= ChatReplyParser.DRIVING_MAX)
            assertFalse(m.text, m.text.contains("{"))
            assertEquals(it, Situation.of(m))
            assertEquals(it.question, Situation.asks(m))
            if (it.question) assertTrue(it.name, it.accept != null)
        }
        assertNull(Situation.of(Message("brain-chat-1", "x", Priority.NORMAL, 0)))
    }

    @Test
    fun `morning greeting once a day when the trip starts`() {
        val d = Drive(wednesdayMorning)
        d.run(1_000)
        d.drive(30f)
        d.run(5_000)
        assertEquals(listOf(Situation.MORNING), d.situations())

        d.clear()
        d.park()
        d.run(11 * 60_000)
        d.drive(30f)
        d.run(5_000)
        assertEquals(listOf(Situation.FIRST_DRIVE), d.situations())

        d.clear()
        d.park()
        d.run(11 * 60_000)
        d.drive(30f)
        d.run(5_000)
        assertTrue(d.situations().isEmpty())
    }

    @Test
    fun `daily greeting survives an app restart through memory`() {
        val d = Drive(wednesdayMorning)
        d.run(1_000)
        d.drive(30f)
        d.run(2_000)
        d.detector = SituationDetector(d.memory)
        d.clear()
        d.park()
        d.run(1_000)
        d.drive(30f)
        d.run(2_000)
        assertEquals(listOf(Situation.FIRST_DRIVE), d.situations())
    }

    @Test
    fun `lunch question only while driving calmly in the lunch window`() {
        val d = Drive(LocalDateTime.of(2026, 11, 4, 11, 0))
        d.run(1_000)
        d.drive(40f)
        d.run(2 * 60_000)
        assertFalse(Situation.LUNCH in d.situations())

        d.time = LocalDateTime.of(2026, 11, 4, 11, 40)
        d.zone = LoadZone.RED
        d.run(2 * 60_000)
        assertFalse(Situation.LUNCH in d.situations())

        d.zone = LoadZone.GREEN
        d.run(2 * 60_000)
        assertEquals(1, d.situations().count { it == Situation.LUNCH })
        val lunch = d.said.first { Situation.of(it) == Situation.LUNCH }
        assertTrue(Situation.asks(lunch))
    }

    @Test
    fun `wiper on and off after a while`() {
        val d = Drive(wednesdayAfternoon, gapMs = 0)
        d.run(1_000)
        d.drive(40f)
        d.run(1_000)
        d.clear()
        d.signals = d.signals.copy(wiper = WiperLevel.LOW)
        d.run(1_000)
        assertEquals(listOf(Situation.RAIN), d.situations())

        d.clear()
        d.signals = d.signals.copy(wiper = WiperLevel.HIGH)
        d.run(1_000)
        assertEquals(listOf(Situation.HEAVY_RAIN), d.situations())
        assertEquals(Priority.URGENT, d.said.single().priority)

        d.clear()
        d.run(6 * 60_000)
        d.signals = d.signals.copy(wiper = WiperLevel.OFF)
        d.run(1_000)
        assertEquals(listOf(Situation.RAIN_STOPPED), d.situations())
    }

    @Test
    fun `highway in after a minute above 80 and out after a minute below 60`() {
        val d = Drive(wednesdayAfternoon, gapMs = 0)
        d.run(1_000)
        d.drive(90f)
        d.run(30_000)
        assertFalse(Situation.HIGHWAY_IN in d.situations())
        d.run(40_000)
        assertTrue(Situation.HIGHWAY_IN in d.situations())

        d.clear()
        d.drive(50f)
        d.run(70_000)
        assertEquals(listOf(Situation.HIGHWAY_OUT), d.situations())
    }

    @Test
    fun `traffic jam after five slow minutes and cleared after a fast minute`() {
        val d = Drive(wednesdayAfternoon, gapMs = 0)
        d.run(1_000)
        d.drive(50f)
        d.run(10_000)
        d.clear()
        d.drive(8f)
        d.run(5 * 60_000 + 1_000)
        assertTrue(Situation.JAM in d.situations())
        d.clear()
        d.drive(50f)
        d.run(61_000)
        assertEquals(listOf(Situation.JAM_CLEARED), d.situations())
    }

    @Test
    fun `long red light and hard brake remark come while stopped`() {
        val d = Drive(wednesdayAfternoon, gapMs = 0)
        d.run(1_000)
        d.drive(40f)
        d.run(1_000)
        d.clear()
        d.signals = d.signals.copy(decelerationMps2 = 6f)
        d.run(1_000)
        assertTrue(d.situations().isEmpty())
        d.signals = d.signals.copy(decelerationMps2 = 0f)
        d.drive(0f)
        d.run(61_000)
        assertEquals(listOf(Situation.AFTER_HARD_BRAKE, Situation.LONG_RED), d.situations())
    }

    @Test
    fun `growth events are said once and wait for the gap`() {
        val d = Drive(wednesdayAfternoon, gapMs = 90_000)
        d.run(1_000)
        d.drive(0f)
        d.run(1_000)
        d.clear()
        d.profile = d.profile.copy(xp = 100)
        d.run(1_000)
        assertEquals(listOf(Situation.LEVEL_UP), d.situations())
        assertEquals("레벨 2 달성! 우리 같이 크고 있어요.", d.said.last().text)
        d.profile = d.profile.copy(xp = 200)
        d.run(1_000)
        assertEquals(listOf(Situation.LEVEL_UP), d.situations())
        d.run(90_000)
        assertEquals(listOf(Situation.LEVEL_UP, Situation.LEVEL_UP), d.situations())
    }

    @Test
    fun `driving well or badly is never graded out loud`() {
        val texts = Situation.entries.map { it.text }
        listOf("부드러웠", "깜빡이", "급제동이 한 번도").forEach { w -> assertTrue(w, texts.none { it.contains(w) }) }
    }

    @Test
    fun `distance milestone counts from what was there at install`() {
        val d = Drive(wednesdayAfternoon, gapMs = 0)
        d.profile = d.profile.copy(distanceKm = 250f)
        d.run(1_000)
        d.drive(0f)
        d.run(1_000)
        assertTrue(d.situations().isEmpty())
        d.profile = d.profile.copy(distanceKm = 301f)
        d.run(1_000)
        assertEquals("우리 함께 300km 달렸어요. 고마워요!", d.said.single().text)
    }

    @Test
    fun `back to calm after a busy stretch`() {
        val d = Drive(wednesdayAfternoon, gapMs = 0)
        d.run(1_000)
        d.drive(40f)
        d.run(1_000)
        d.clear()
        d.zone = LoadZone.RED
        d.run(10_000)
        d.zone = LoadZone.GREEN
        d.run(6_000)
        assertEquals(listOf(Situation.AFTER_BUSY), d.situations())
    }

    @Test
    fun `route start, ten minutes left, arrival without hard brakes, then parked`() {
        val d = Drive(LocalDateTime.of(2026, 11, 4, 11, 0), gapMs = 0)
        d.run(1_000)
        d.drive(40f)
        d.run(1_000)
        d.clear()
        val start = d.detector.onRouteStarted("판교 현대백화점", 120.5f, 40, d.time, d.nowMs)
        assertEquals("판교 현대백화점까지 약 40분이에요. 출발해요!", start[0].text)
        assertEquals("도착하면 딱 점심때예요. 맛집 찾아볼까요?", start[1].text)

        d.run(31 * 60_000)
        assertTrue(Situation.TEN_MINUTES_LEFT in d.situations())

        val arrived = d.detector.onArrived(d.nowMs).mapNotNull { Situation.of(it) }
        assertEquals(listOf(Situation.ARRIVED, Situation.FIRST_LONG_TRIP), arrived)
        d.clear()
        d.park()
        d.run(1_000)
        assertEquals(listOf(Situation.PARKED_AFTER_ARRIVAL), d.situations())

        d.detector.onRouteStarted("집", 150f, 90, d.time, d.nowMs)
        assertEquals(listOf(Situation.ARRIVED), d.detector.onArrived(d.nowMs + 60_000).mapNotNull { Situation.of(it) })
    }

    @Test
    fun `region visits tell the app what kind of visit it was`() {
        val d = Drive(wednesdayAfternoon, gapMs = 0)
        d.run(1_000)
        val home = Region(Province.GYEONGGI, "성남시")
        val trip = Region(Province.GANGWON, "강릉시")
        assertEquals(RegionVisit.NONE, d.detector.onRegion(home, d.time))
        assertEquals(RegionVisit.NONE, d.detector.onRegion(home, d.time))
        assertEquals(RegionVisit.FIRST, d.detector.onRegion(trip, d.time))
        assertEquals(RegionVisit.NONE, d.detector.onRegion(home, d.time))
        assertEquals(RegionVisit.NONE, d.detector.onRegion(trip, d.time))
        var t = d.time
        val kinds = (2..5).map { t = t.plusDays(1); d.detector.onRegion(home, t); d.detector.onRegion(trip, t) }
        assertEquals(listOf(RegionVisit.NONE, RegionVisit.NONE, RegionVisit.NONE, RegionVisit.REGULAR), kinds)
        t = t.plusDays(40)
        d.detector.onRegion(home, t)
        assertEquals(RegionVisit.REVISIT, d.detector.onRegion(trip, t))
    }

    @Test
    fun `parses the navi route start sentence`() {
        assertEquals(Triple("42dot", 12.3f, 25), SituationDetector.parseRouteStarted("경로 안내 시작: 42dot (12.3km, 약 25분)"))
        assertNull(SituationDetector.parseRouteStarted("목적지 도착"))
    }
}
