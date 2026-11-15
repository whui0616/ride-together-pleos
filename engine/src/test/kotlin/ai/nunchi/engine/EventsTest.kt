package ai.nunchi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EventsTest {
    private val nov4 = LocalDate.of(2026, 11, 4)
    private val t = nov4.toEpochDay()
    private val signalWeek = EventCalendar.window(EventCalendar.ALL.first { it.id == "signal_week" }, 2026)

    @Test fun ids_unique_and_no_distance_goals() {
        assertEquals(EventCalendar.ALL.size, EventCalendar.ALL.map { it.id }.toSet().size)
        EventGoal.entries.forEach { assertFalse(it.label.contains("km") || it.label.contains("거리")) }
        EventGoal.entries.forEach { g -> listOf("방향지시등", "부드러운", "감속", "휴식").forEach { w -> assertFalse(g.label, g.label.contains(w)) } }
    }

    @Test fun calendar_on_nov4_has_active_upcoming_and_ended() {
        val ws = EventCalendar.windows(nov4)
        val active = ws.filter { it.status(t) == EventStatus.ACTIVE }.map { it.def.id }.toSet()
        assertEquals(setOf("autumn", "signal_week"), active)
        assertTrue(ws.any { it.def.id == "winter" && it.status(t) == EventStatus.UPCOMING })
        assertTrue(ws.any { it.def.id == "summer_rest" && it.status(t) == EventStatus.ENDED })
        assertFalse(ws.any { it.def.id == "school_zone" })
    }

    @Test fun winter_crosses_the_year() {
        val w = EventCalendar.window(EventCalendar.ALL.first { it.id == "winter" }, 2026)
        assertEquals(LocalDate.of(2026, 12, 1).toEpochDay(), w.start)
        assertEquals(LocalDate.of(2027, 2, 28).toEpochDay(), w.end)
        assertTrue(EventCalendar.windows(LocalDate.of(2027, 1, 15)).any { it.def.id == "winter" && it.status(LocalDate.of(2027, 1, 15).toEpochDay()) == EventStatus.ACTIVE })
    }

    @Test fun guide_runs_seven_days_from_creation() {
        val ws = EventCalendar.windows(nov4, createdDay = t - 6)
        val g = ws.first { it.def.id == "guide" }
        assertEquals(EventStatus.ACTIVE, g.status(t))
        assertEquals(EventStatus.ENDED, g.status(t + 1))
    }

    @Test fun progress_counts_only_after_joining() {
        val before = mapOf(EventGoal.TALK to 100)
        var s = EventState().update(listOf(signalWeek), t, before, emptyList())
        assertTrue(s.joined(signalWeek))
        val m = signalWeek.def.missions.first { it.goal == EventGoal.TALK }
        assertEquals(0, s.progress(signalWeek, m, t, before, emptyList()))
        s = s.update(listOf(signalWeek), t, mapOf(EventGoal.TALK to 112), emptyList())
        assertEquals(12, s.progress(signalWeek, m, t, mapOf(EventGoal.TALK to 112), emptyList()))
    }

    @Test fun visits_count_inside_the_window_including_join_day() {
        val s = EventState().update(listOf(signalWeek), t, emptyMap(), emptyList())
        val visit = signalWeek.def.missions.first { it.goal == EventGoal.VISIT }
        val days = listOf(signalWeek.start - 1, signalWeek.start, t)
        assertEquals(2, s.progress(signalWeek, visit, t, emptyMap(), days))
    }

    @Test fun complete_then_claim_once_gives_coins_and_tickets() {
        val days = (signalWeek.start..t).toList() + (t + 1)
        var s = EventState().update(listOf(signalWeek), t, mapOf(EventGoal.TALK to 0), days)
        assertFalse(s.canClaim(signalWeek))
        s = s.update(listOf(signalWeek), t, mapOf(EventGoal.TALK to 5), days)
        assertTrue(s.canClaim(signalWeek))
        val (after, coins) = s.claim(signalWeek)!!
        assertEquals(signalWeek.def.coins, coins)
        assertEquals(signalWeek.def.tickets, after.tickets)
        assertNull(after.claim(signalWeek))
    }

    @Test fun ended_event_does_not_keep_counting() {
        val s = EventState().update(listOf(signalWeek), t, mapOf(EventGoal.TALK to 0), emptyList())
        val later = signalWeek.end + 3
        val m = signalWeek.def.missions.first { it.goal == EventGoal.TALK }
        val s2 = s.update(listOf(signalWeek), later, mapOf(EventGoal.TALK to 999), emptyList())
        assertEquals(0, s2.progress(signalWeek, m, later, mapOf(EventGoal.TALK to 999), emptyList()))
        assertFalse(s2.canClaim(signalWeek))
    }

    @Test fun spend_tickets_needs_enough() {
        val s = EventState(tickets = 3)
        assertNull(s.spendTickets(4))
        assertEquals(0, s.spendTickets(3)!!.tickets)
    }

}
