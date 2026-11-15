package ai.nunchi.engine.suggest

import ai.nunchi.engine.Reply

import ai.nunchi.engine.Gear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionManagerTest {
    private val scorer = SuggestionScorer()
    private val prefs = UserPrefs()

    private fun meal(id: String = "m", createdAt: Long = 0L, distance: Double = 1000.0) = Suggestion(
        id, SuggestionKind.MEAL, "식당", 0.0, 0.0, distance,
        requiresReply = true, script = "앞에 식당 들를까요?", createdAtMs = createdAt,
    )

    private fun story(id: String = "st", createdAt: Long = 0L) = Suggestion(
        id, SuggestionKind.STORY, "유적", 0.0, 0.0, 1000.0,
        requiresReply = false, script = "여기는 옛 성터예요.", createdAtMs = createdAt,
    )

    private fun openCtx(now: Long) = DriveContext(
        gear = Gear.DRIVE, msSinceHardBrake = 60_000L, msSinceLastSpeech = Long.MAX_VALUE,
        hourOfDay = 12, nowMs = now,
    )

    @Test fun passed_place_discarded_after_two_increasing_samples_over_200m() {
        val m = SuggestionManager()
        m.offer(meal())
        m.onLocationSample(1_000L, mapOf("m" to 800.0))
        m.onLocationSample(2_000L, mapOf("m" to 500.0))
        m.onLocationSample(3_000L, mapOf("m" to 300.0))
        m.onLocationSample(4_000L, mapOf("m" to 350.0))
        assertEquals(SuggestionState.QUEUED, m.stateOf("m"))
        m.onLocationSample(5_000L, mapOf("m" to 420.0))
        assertEquals(SuggestionState.DISCARDED, m.stateOf("m"))
    }

    @Test fun increase_within_200m_margin_not_discarded() {
        val m = SuggestionManager()
        m.offer(meal())
        m.onLocationSample(1_000L, mapOf("m" to 150.0))
        m.onLocationSample(2_000L, mapOf("m" to 160.0))
        m.onLocationSample(3_000L, mapOf("m" to 180.0))
        assertEquals(SuggestionState.QUEUED, m.stateOf("m"))
    }

    @Test fun single_increase_then_decrease_resets_streak() {
        val m = SuggestionManager()
        m.offer(meal())
        m.onLocationSample(1_000L, mapOf("m" to 500.0))
        m.onLocationSample(2_000L, mapOf("m" to 550.0))
        m.onLocationSample(3_000L, mapOf("m" to 520.0))
        m.onLocationSample(4_000L, mapOf("m" to 560.0))
        assertEquals(SuggestionState.QUEUED, m.stateOf("m"))
        m.onLocationSample(5_000L, mapOf("m" to 600.0))
        assertEquals(SuggestionState.DISCARDED, m.stateOf("m"))
    }

    @Test fun route_change_discards_off_route() {
        val m = SuggestionManager()
        m.offer(meal("a")); m.offer(meal("b"))
        m.onRouteChanged(setOf("b"))
        assertEquals(SuggestionState.DISCARDED, m.stateOf("a"))
        assertEquals(SuggestionState.QUEUED, m.stateOf("b"))
    }

    @Test fun gear_P_clears_queue_and_trip_state() {
        val m = SuggestionManager()
        m.offer(meal("a")); m.offer(story("b"))
        m.tick(openCtx(0L), prefs, scorer)
        assertEquals(1, m.repliesSpoken)
        m.onGear(Gear.PARK)
        assertTrue(m.states().isEmpty())
        assertNull(m.awaitingId)
        assertEquals(0, m.repliesSpoken)
        assertFalse(m.quietMode)
    }

    @Test fun gear_D_or_R_does_not_clear() {
        val m = SuggestionManager()
        m.offer(meal())
        m.onGear(Gear.REVERSE); m.onGear(Gear.DRIVE); m.onGear(Gear.NEUTRAL)
        assertEquals(SuggestionState.QUEUED, m.stateOf("m"))
    }

    @Test fun expires_after_10_minutes() {
        val m = SuggestionManager()
        m.offer(meal(createdAt = 0L))
        m.onLocationSample(10 * 60_000L - 1, mapOf("m" to 1000.0))
        assertEquals(SuggestionState.QUEUED, m.stateOf("m"))
        m.onLocationSample(10 * 60_000L, mapOf("m" to 1000.0))
        assertEquals(SuggestionState.DISCARDED, m.stateOf("m"))
    }

    @Test fun tick_also_expires_old() {
        val m = SuggestionManager()
        m.offer(meal(createdAt = 0L))
        assertNull(m.tick(openCtx(10 * 60_000L), prefs, scorer))
        assertEquals(SuggestionState.DISCARDED, m.stateOf("m"))
    }

    @Test fun tick_returns_best_and_marks_awaiting() {
        val m = SuggestionManager()
        m.offer(meal("far", distance = 2500.0))
        m.offer(meal("near", distance = 1000.0))
        val s = m.tick(openCtx(0L), prefs, scorer)
        assertNotNull(s)
        assertEquals("near", s!!.id)
        assertEquals(SuggestionState.AWAITING_REPLY, m.stateOf("near"))
        assertEquals("near", m.awaitingId)
        assertEquals(SuggestionState.QUEUED, m.stateOf("far"))
        assertNull(m.tick(openCtx(1_000L), prefs, scorer))
    }

    @Test fun tick_returns_null_when_window_closed() {
        val m = SuggestionManager()
        m.offer(meal())
        assertNull(m.tick(openCtx(0L).copy(turnSignalOn = true), prefs, scorer))
        assertEquals(SuggestionState.QUEUED, m.stateOf("m"))
    }

    @Test fun tick_returns_null_when_below_threshold() {
        val m = SuggestionManager()
        m.offer(meal())
        assertNull(m.tick(openCtx(0L).copy(hourOfDay = 15), prefs, scorer))
        assertEquals(SuggestionState.QUEUED, m.stateOf("m"))
    }

    @Test fun story_marked_spoken_without_reply_window() {
        val m = SuggestionManager()
        m.offer(story())
        val s = m.tick(openCtx(0L), prefs, scorer)
        assertEquals("st", s!!.id)
        assertEquals(SuggestionState.SPOKEN, m.stateOf("st"))
        assertNull(m.awaitingId)
        assertEquals(1, m.storiesSpoken)
    }

    @Test fun manager_enforces_90s_gap_and_trip_limits() {
        val m = SuggestionManager()
        m.offer(meal("a")); m.offer(meal("b")); m.offer(meal("c"))
        assertEquals("a", m.tick(openCtx(0L), prefs, scorer)!!.id)
        m.onReply(Reply.NO)
        assertNull(m.tick(openCtx(89_999L), prefs, scorer))
        assertEquals("b", m.tick(openCtx(90_000L), prefs, scorer)!!.id)
        m.onReply(Reply.NO)
        assertNull(m.tick(openCtx(400_000L), prefs, scorer))
        assertEquals(SuggestionState.QUEUED, m.stateOf("c"))
    }

    @Test fun yes_and_no_update_state_and_prefs() {
        val m = SuggestionManager()
        val p = UserPrefs()
        m.offer(meal("a")); m.offer(meal("b"))
        m.tick(openCtx(0L), p, scorer)
        m.onReply(Reply.YES, p)
        assertEquals(SuggestionState.ACCEPTED, m.stateOf("a"))
        assertEquals(5, p.kindBias(SuggestionKind.MEAL))
        m.tick(openCtx(100_000L), p, scorer)
        m.onReply(Reply.NO, p)
        assertEquals(SuggestionState.REJECTED, m.stateOf("b"))
        assertEquals(-3, p.kindBias(SuggestionKind.MEAL))
    }

    @Test fun reply_timeout_5s_expires_no_reply() {
        val m = SuggestionManager()
        m.offer(meal())
        m.tick(openCtx(0L), prefs, scorer)
        m.onReplyTimeout(4_999L)
        assertEquals(SuggestionState.AWAITING_REPLY, m.stateOf("m"))
        m.onReplyTimeout(5_000L)
        assertEquals(SuggestionState.EXPIRED_NO_REPLY, m.stateOf("m"))
        assertNull(m.awaitingId)
    }

    @Test fun tts_finished_restarts_reply_window() {
        val m = SuggestionManager()
        m.offer(meal())
        m.tick(openCtx(0L), prefs, scorer)
        m.onTtsFinished(3_000L)
        m.onReplyTimeout(7_999L)
        assertEquals(SuggestionState.AWAITING_REPLY, m.stateOf("m"))
        m.onReplyTimeout(8_000L)
        assertEquals(SuggestionState.EXPIRED_NO_REPLY, m.stateOf("m"))
    }

    @Test fun unknown_reply_treated_as_no_reply() {
        val m = SuggestionManager()
        m.offer(meal())
        m.tick(openCtx(0L), prefs, scorer)
        m.onReply(Reply.UNKNOWN)
        assertEquals(SuggestionState.EXPIRED_NO_REPLY, m.stateOf("m"))
    }

    @Test fun later_requeues_once_then_discards() {
        val m = SuggestionManager()
        m.offer(meal())
        assertEquals("m", m.tick(openCtx(0L), prefs, scorer)!!.id)
        m.onReply(Reply.LATER)
        assertEquals(SuggestionState.LATER, m.stateOf("m"))
        assertEquals(1, m.laterCountOf("m"))
        assertEquals("m", m.tick(openCtx(100_000L), prefs, scorer)!!.id)
        assertEquals(SuggestionState.AWAITING_REPLY, m.stateOf("m"))
        m.onReply(Reply.LATER)
        assertEquals(SuggestionState.DISCARDED, m.stateOf("m"))
        assertNull(m.tick(openCtx(200_000L), prefs, scorer))
    }

    @Test fun later_suggestion_discarded_when_place_passed() {
        val m = SuggestionManager()
        m.offer(meal())
        m.tick(openCtx(0L), prefs, scorer)
        m.onReply(Reply.LATER)
        m.onLocationSample(1_000L, mapOf("m" to 300.0))
        m.onLocationSample(2_000L, mapOf("m" to 400.0))
        m.onLocationSample(3_000L, mapOf("m" to 500.0))
        assertEquals(SuggestionState.DISCARDED, m.stateOf("m"))
    }

    @Test fun three_consecutive_no_reply_enables_quiet_mode() {
        val m = SuggestionManager()
        m.offer(meal("a")); m.offer(meal("b"))
        m.tick(openCtx(0L), prefs, scorer); m.onReplyTimeout(5_000L)
        m.tick(openCtx(100_000L), prefs, scorer); m.onReplyTimeout(105_000L)
        assertEquals(2, m.noReplyStreak)
        assertFalse(m.quietMode)

        m.onGear(Gear.PARK)
        assertEquals(2, m.noReplyStreak)

        val t = 1_000_000L
        m.offer(meal("c", createdAt = t)); m.offer(meal("d", createdAt = t)); m.offer(story("s", createdAt = t))
        m.tick(openCtx(1_000_000L), prefs, scorer); m.onReplyTimeout(1_005_000L)
        assertTrue(m.quietMode)
        assertEquals(SuggestionState.EXPIRED_NO_REPLY, m.stateOf("c"))

        assertEquals("s", m.tick(openCtx(1_100_000L), prefs, scorer)!!.id)
        assertNull(m.tick(openCtx(1_200_000L), prefs, scorer))
        assertEquals(SuggestionState.QUEUED, m.stateOf("d"))

        m.onGear(Gear.PARK)
        assertFalse(m.quietMode)
    }

    @Test fun no_reply_streak_resets_on_any_reply() {
        val m = SuggestionManager()
        m.offer(meal("a")); m.offer(meal("b"))
        m.tick(openCtx(0L), prefs, scorer); m.onReplyTimeout(5_000L)
        assertEquals(1, m.noReplyStreak)
        m.tick(openCtx(100_000L), prefs, scorer); m.onReply(Reply.NO)
        assertEquals(0, m.noReplyStreak)
    }

    @Test fun later_reply_also_resets_streak() {
        val m = SuggestionManager()
        m.offer(meal("a")); m.offer(meal("b"))
        m.tick(openCtx(0L), prefs, scorer); m.onReplyTimeout(5_000L)
        m.tick(openCtx(100_000L), prefs, scorer); m.onReply(Reply.LATER)
        assertEquals(0, m.noReplyStreak)
    }

    @Test fun disabled_kind_is_never_picked() {
        val m = SuggestionManager()
        m.disabledKinds = setOf(SuggestionKind.MEAL)
        m.offer(meal())
        assertNull(m.tick(openCtx(1_000L), prefs, scorer))
        m.disabledKinds = emptySet()
        assertNotNull(m.tick(openCtx(2_000L), prefs, scorer))
    }

    @Test fun reply_timeout_follows_setting() {
        val m = SuggestionManager()
        m.replyTimeoutMs = 3_000L
        m.offer(meal())
        assertNotNull(m.tick(openCtx(1_000L), prefs, scorer))
        m.onReplyTimeout(3_500L)
        assertEquals(SuggestionState.AWAITING_REPLY, m.stateOf("m"))
        m.onReplyTimeout(4_100L)
        assertEquals(SuggestionState.EXPIRED_NO_REPLY, m.stateOf("m"))
    }

    @Test fun no_reply_as_decline_records_reject() {
        val m = SuggestionManager()
        val p = UserPrefs()
        m.noReplyIsDecline = true
        m.offer(meal())
        assertNotNull(m.tick(openCtx(1_000L), p, scorer))
        m.onReplyTimeout(10_000L)
        assertEquals(SuggestionState.REJECTED, m.stateOf("m"))
        assertEquals(1, p.historySize(SuggestionKind.MEAL))
    }
}
