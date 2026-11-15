package ai.nunchi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {
    private val trips = Achievements.byId["trips"]!!

    @Test fun definitions_are_unique_and_sorted() {
        assertEquals(Achievements.ALL.size, Achievements.ALL.map { it.id }.toSet().size)
        Achievements.ALL.forEach { assertEquals(it.thresholds, it.thresholds.sorted()) }
    }

    @Test fun bump_unlocks_tier_and_pays_coins() {
        val (s, u) = AchState().bump(AchKey.TRIPS, 1).evaluate(day = 5)
        assertEquals(1, s.tierOf(trips))
        assertEquals(1, u.size)
        assertEquals(10, u[0].coins)
        assertEquals(5L, s.unlockedDay["trips"])
        assertEquals(listOf("trips"), s.recent)
    }

    @Test fun jumping_two_tiers_yields_two_unlocks() {
        val (_, u) = AchState().set(AchKey.TRIPS, 15).evaluate(0)
        assertEquals(listOf(1, 2), u.filter { it.achievement == trips }.map { it.tier })
        assertEquals(10 + 30, u.filter { it.achievement == trips }.sumOf { it.coins })
    }

    @Test fun no_duplicate_unlock_on_reevaluate() {
        val (s1, _) = AchState().set(AchKey.TRIPS, 10).evaluate(0)
        val (s2, u2) = s1.evaluate(1)
        assertTrue(u2.isEmpty())
        assertEquals(s1.tiers, s2.tiers)
    }

    @Test fun external_snapshot_counts_and_progress() {
        val (s, _) = AchState().evaluate(0, mapOf(AchKey.VISITS to 37))
        val visits = Achievements.byId["visits"]!!
        assertEquals(1, s.tierOf(visits))
        assertEquals(37 to 50, s.progress(visits))
        assertFalse(s.nearlyDone(visits))
        val (s2, _) = s.evaluate(0, mapOf(AchKey.VISITS to 46))
        assertTrue(s2.nearlyDone(visits))
    }

    @Test fun honor_only_at_final_tier() {
        val (s, u) = AchState().set(AchKey.TRIPS, 3000).evaluate(0)
        assertEquals(listOf(null, null, null, null, "길동무"), u.filter { it.achievement == trips }.map { it.honor })
        assertTrue("길동무" in s.honors)
        assertEquals(1f, s.ratio(trips))
    }

    @Test fun no_safety_scoring_achievements() {
        val banned = listOf("부드러운", "방향지시등", "깜빡이", "급제동", "급가속", "감속", "안전 점수")
        Achievements.ALL.forEach { a -> banned.forEach { w -> assertFalse("${a.id}: ${a.title} / ${a.desc}", a.title.contains(w) || a.desc.contains(w)) } }
        assertTrue(AchCategory.entries.none { it.label.contains("안전") })
    }

    @Test fun stale_tiers_of_removed_achievements_do_not_inflate_completion() {
        val s = AchState(tiers = mapOf("smooth_stop" to 4, "signal" to 4, "week100" to 1))
        assertEquals(0, s.completionPercent)
        assertEquals(0, s.tiersDone)
    }

    @Test fun completion_percent_and_recent_cap() {
        var s = AchState()
        s = s.set(AchKey.FIRST_DRIVE, 1).set(AchKey.DAWN_DRIVE, 1).set(AchKey.OUTFITS, 1).set(AchKey.NEW_YEAR, 1)
        val (r, u) = s.evaluate(0)
        assertEquals(4, u.size)
        assertEquals(AchState.RECENT_KEPT, r.recent.size)
        assertEquals(4 * 100 / Achievements.totalTiers, r.completionPercent)
    }

    @Test fun every_achievement_has_a_memory_line_without_blanks() {
        Achievements.ALL.forEach { a ->
            (1..a.maxTier).forEach { t ->
                val line = a.memoryLine(t)
                assertTrue(a.id, line.isNotBlank())
                assertFalse("${a.id}: $line", line.contains("%d"))
            }
        }
        assertEquals("우리 함께한 지 365일이에요! 처음 만난 날 기억나요?", Achievements.byId["days"]!!.memoryLine(3))
    }

    @Test fun unlock_becomes_an_album_card_with_place_look_and_line() {
        val (s, u) = AchState().bump(AchKey.TRIPS, 1).evaluate(day = 100)
        val card = u.single().card(place = "강릉시", look = "hair=0;outfit=3")
        val kept = s.keep(listOf(card))
        assertEquals(listOf(MemoryCard("trips", 1, 100, "강릉시", "hair=0;outfit=3", "함께 다녀온 길이 1번이에요. 다음엔 어디 갈까요?")), kept.album)
        assertEquals("함께 다녀온 길", card.title)
    }

    @Test fun album_keeps_the_latest_cards_only() {
        var s = AchState()
        repeat(AchState.ALBUM_KEPT + 5) { i -> s = s.keep(listOf(MemoryCard("trips", 1, i.toLong(), "", "", "x"))) }
        assertEquals(AchState.ALBUM_KEPT, s.album.size)
        assertEquals(5L, s.album.first().day)
    }

    @Test fun recall_line_says_how_long_ago_and_where() {
        val day = java.time.LocalDate.of(2025, 11, 12).toEpochDay()
        val card = MemoryCard("places", 1, day, "강릉시", "", "…")
        assertEquals(null, card.recallLine(day))
        assertEquals("어제 강릉시에 갔던 날 기억나요? 「탐험가」 추억이 그날이에요.", card.recallLine(day + 1))
        assertEquals("3일 전 강릉시에 갔던 날 기억나요? 「탐험가」 추억이 그날이에요.", card.recallLine(day + 3))
        assertEquals("2주 전 강릉시에 갔던 날 기억나요? 「탐험가」 추억이 그날이에요.", card.recallLine(day + 15))
        assertEquals("작년 오늘 강릉시에 갔던 날 기억나요? 「탐험가」 추억이 그날이에요.", card.recallLine(java.time.LocalDate.of(2026, 11, 12).toEpochDay()))
        assertEquals("2달 전 기억나요? 「탐험가」 추억이 그날이에요.", card.copy(place = "").recallLine(day + 61))
    }

    @Test fun recall_prefers_this_day_in_an_earlier_year_then_rotates() {
        val today = java.time.LocalDate.of(2026, 11, 12).toEpochDay()
        val lastYear = MemoryCard("places", 1, java.time.LocalDate.of(2025, 11, 12).toEpochDay(), "강릉시", "", "…")
        val a = MemoryCard("trips", 1, today - 10, "", "", "…")
        val b = MemoryCard("rain", 1, today - 3, "", "", "…")
        val todayCard = MemoryCard("outfit", 1, today, "", "", "…")
        assertEquals(null, AchState(album = listOf(todayCard)).recall(today, 0))
        assertEquals(lastYear, AchState(album = listOf(lastYear, a, b, todayCard)).recall(today, 5))
        val s = AchState(album = listOf(a, b, todayCard))
        assertEquals(listOf(a, b, a), (0..2).map { s.recall(today, it) })
    }

    @Test fun backfill_builds_cards_from_earlier_unlocks_once() {
        val (s, _) = AchState().set(AchKey.TRIPS, 10).set(AchKey.FIRST_DRIVE, 1).evaluate(day = 50)
        val filled = s.backfillAlbum("hair=1")
        assertEquals(setOf("trips", "first_drive"), filled.album.map { it.achId }.toSet())
        assertTrue(filled.album.all { it.day == 50L && it.place.isEmpty() && it.look == "hair=1" })
        assertEquals("함께 다녀온 길이 10번이에요. 다음엔 어디 갈까요?", filled.album.first { it.achId == "trips" }.line)
        assertEquals(filled, filled.backfillAlbum("hair=2"))
    }

    @Test fun set_at_least_never_lowers() {
        val s = AchState().set(AchKey.STREAK_BEST, 9).setAtLeast(AchKey.STREAK_BEST, 4)
        assertEquals(9, s.count(AchKey.STREAK_BEST))
    }

    @Test fun commute_by_weekday_hour_and_first_place_card() {
        assertEquals(AchKey.COMMUTE_IN, AchKey.commute(java.time.DayOfWeek.MONDAY, 8))
        assertEquals(AchKey.COMMUTE_OUT, AchKey.commute(java.time.DayOfWeek.FRIDAY, 18))
        assertEquals(null, AchKey.commute(java.time.DayOfWeek.SATURDAY, 8))
        assertEquals(null, AchKey.commute(java.time.DayOfWeek.MONDAY, 13))
        val c = MemoryCard.firstPlace(10, "강릉시", "")
        assertEquals("처음 가 본 강릉시", c.title)
        assertEquals("처음 와 본 강릉시예요. 오늘을 기억해 둘게요.", c.line)
    }
}
