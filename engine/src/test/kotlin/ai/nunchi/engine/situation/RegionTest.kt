package ai.nunchi.engine.situation

import ai.nunchi.engine.CompanionProfile
import ai.nunchi.engine.DrivingSignals
import ai.nunchi.engine.Gear
import ai.nunchi.engine.LoadZone
import ai.nunchi.engine.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RegionTest {
    @Test
    fun `parses Korean addresses into province and district`() {
        assertEquals("성남시", Region.parse("경기도 성남시 수정구 창업로 42")!!.spoken)
        assertEquals("서울 강남구", Region.parse("서울특별시 강남구 남부순환로 2621")!!.spoken)
        assertEquals("세종", Region.parse("세종특별자치시 한누리대로 2130")!!.spoken)
        assertEquals("강릉시", Region.parse("강원특별자치도 강릉시 창해로 514")!!.spoken)
        assertEquals(Province.JEONBUK, Region.parse("전라북도 전주시 완산구")!!.province)
        assertNull(Region.parse("1600 Amphitheatre Pkwy, Mountain View"))
        assertNull(Region.parse(""))
        assertNull(Region.parse(null))
    }

    @Test
    fun `facts come from the district, or the province for big cities`() {
        assertEquals("한옥마을", RegionFacts.of(Region.parse("전북특별자치도 전주시")!!)!!.sight)
        assertEquals("해운대", RegionFacts.of(Region.parse("부산광역시 수영구")!!)!!.sight)
        assertNull(RegionFacts.of(Region.parse("경기도 성남시")!!))
    }

    @Test
    fun `particles follow the final consonant`() {
        assertEquals("는", "성남시".particle("은", "는"))
        assertEquals("은", "서울 강남구 역삼동".particle("은", "는"))
        assertEquals("이에요", "부산".particle("이에요", "예요"))
        assertEquals("예요", "경기도".particle("이에요", "예요"))
    }

    private class Run {
        val memory = SituationMemory.InMemory()
        var detector = SituationDetector(memory, gapMs = 0)
        var time: LocalDateTime = LocalDateTime.of(2026, 11, 5, 15, 0)
        var nowMs = 1_000_000L
        val said = mutableListOf<Message>()
        private val signals = DrivingSignals(gear = Gear.DRIVE, speedKmh = 40f)

        fun tick(times: Int = 4) = repeat(times) {
            said += detector.tick(signals, LoadZone.GREEN, CompanionProfile(), time, nowMs, nowMs)
            nowMs += 250
        }

        fun arrive(address: String) {
            detector.onRegion(Region.parse(address), time)
            tick(8)
        }

        fun lines() = said.map { it.text }
        fun situations() = said.mapNotNull { Situation.of(it) }.filter { it in REGION }
    }

    @Test
    fun `first place after install is only recorded, later new places are first visits`() {
        val r = Run()
        r.tick()
        r.arrive("경기도 성남시 수정구")
        assertTrue(r.situations().isEmpty())
        r.arrive("전북특별자치도 전주시 완산구")
        assertEquals(
            listOf(Situation.NEW_PROVINCE, Situation.FIRST_VISIT, Situation.FAMOUS_SIGHT),
            r.situations(),
        )
        assertTrue(r.lines().contains("이제 전라북도예요. 꽤 멀리 왔네요!"))
        assertTrue(r.lines().contains("전주시는 처음 와 보네요! 어떤 곳일까요?"))
        assertTrue(r.lines().contains("전주시는 한옥마을이 유명하대요. 들러 볼까요?"))
    }

    @Test
    fun `revisit after a month mentions the date, a quick return says nothing`() {
        val r = Run()
        r.tick()
        r.arrive("경기도 성남시")
        r.arrive("경기도 용인시 기흥구")
        r.said.clear()
        r.arrive("경기도 성남시")
        assertTrue(r.situations().none { it == Situation.REVISIT || it == Situation.FIRST_VISIT })

        r.detector = SituationDetector(r.memory, gapMs = 0)
        r.time = r.time.plusDays(40)
        r.tick()
        r.said.clear()
        r.arrive("경기도 용인시")
        assertTrue(r.lines().toString(), r.lines().contains("용인시, 11월 5일에 왔었죠? 오랜만이에요."))
    }

    @Test
    fun `fifth visit is celebrated`() {
        val r = Run()
        r.tick()
        r.arrive("경기도 성남시")
        repeat(5) {
            r.time = r.time.plusDays(1)
            r.arrive("경기도 수원시")
            r.arrive("경기도 성남시")
        }
        assertTrue(r.lines().toString(), r.lines().any { it == "여기 벌써 5번째예요. 단골이네요!" })
    }

    @Test
    fun `local food is suggested on a first visit at meal time`() {
        val r = Run()
        r.time = LocalDateTime.of(2026, 11, 5, 12, 0)
        r.tick()
        r.arrive("경기도 성남시")
        r.arrive("강원특별자치도 춘천시")
        assertTrue(r.lines().toString(), r.lines().contains("춘천시에 왔으니 닭갈비 어때요? 찾아볼까요?"))
    }
}

private val REGION = setOf(
    Situation.FIRST_VISIT, Situation.REVISIT, Situation.FREQUENT_VISIT,
    Situation.FAMOUS_SIGHT, Situation.LOCAL_FOOD, Situation.NEW_PROVINCE,
)

class RegionLocateTest {
    @Test
    fun `short navi address falls back to the nearest city`() {
        assertEquals("성남시", Region.locate("수정구 시흥동", 37.4127, 127.0947)!!.spoken)
        assertEquals("서울 강남구", Region.locate("강남구 역삼동", 37.5006, 127.0366)!!.spoken)
        assertEquals("부산 해운대구", Region.locate("해운대구 우동", 35.1631, 129.1636)!!.spoken)
        assertEquals("전주시", Region.locate("완산구 효자동", 35.8150, 127.1100)!!.spoken)
        assertEquals("강릉시", Region.locate("강원특별자치도 강릉시 창해로", 0.0, 0.0)!!.spoken)
        assertNull(Region.locate("Mountain View", 37.42, -122.08))
    }
}
