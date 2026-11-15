package ai.nunchi.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScorerTest {
    private val scorer = SuggestionScorer()
    private val eps = 1e-6

    private fun sug(
        kind: SuggestionKind = SuggestionKind.MEAL,
        distance: Double = 1000.0,
        outdoor: Boolean = false,
    ) = Suggestion(
        id = "s", kind = kind, placeName = "p", lat = 0.0, lng = 0.0,
        distanceAheadM = distance, requiresReply = kind != SuggestionKind.STORY,
        script = "", createdAtMs = 0L, isOutdoor = outdoor,
    )

    @Test fun distance_band() {
        assertEquals(0.0, scorer.distanceScore(499.0), eps)
        assertEquals(100.0, scorer.distanceScore(500.0), eps)
        assertEquals(100.0, scorer.distanceScore(3000.0), eps)
        assertEquals(50.0, scorer.distanceScore(4500.0), eps)
        assertEquals(0.0, scorer.distanceScore(6000.0), eps)
        assertEquals(0.0, scorer.distanceScore(6001.0), eps)
    }

    @Test fun meal_time_bands() {
        assertEquals(20.0, scorer.mealTimeScore(10), eps)
        assertEquals(100.0, scorer.mealTimeScore(11), eps)
        assertEquals(100.0, scorer.mealTimeScore(13), eps)
        assertEquals(20.0, scorer.mealTimeScore(14), eps)
        assertEquals(100.0, scorer.mealTimeScore(17), eps)
        assertEquals(100.0, scorer.mealTimeScore(19), eps)
        assertEquals(20.0, scorer.mealTimeScore(20), eps)
    }

    @Test fun meal_at_lunch_passes_threshold() {
        val score = scorer.score(sug(), DriveContext(hourOfDay = 12), UserPrefs())
        assertEquals(87.5, score, eps)
        assertTrue(score >= SuggestionScorer.THRESHOLD)
    }

    @Test fun meal_off_hours_fails_threshold() {
        val score = scorer.score(sug(), DriveContext(hourOfDay = 15), UserPrefs())
        assertEquals(59.5, score, eps)
        assertFalse(scorer.passes(sug(), DriveContext(hourOfDay = 15), UserPrefs()))
    }

    @Test fun meal_too_close_fails_threshold() {
        val score = scorer.score(sug(distance = 400.0), DriveContext(hourOfDay = 12), UserPrefs())
        assertEquals(57.5, score, eps)
    }

    @Test fun meal_mid_distance_linear() {
        val score = scorer.score(sug(distance = 4500.0), DriveContext(hourOfDay = 12), UserPrefs())
        assertEquals(72.5, score, eps)
    }

    @Test fun charge_soc_bands() {
        val s = sug(kind = SuggestionKind.CHARGE)
        assertEquals(100.0, scorer.contextScore(s, DriveContext(batterySoc = 20)), eps)
        assertEquals(60.0, scorer.contextScore(s, DriveContext(batterySoc = 21)), eps)
        assertEquals(60.0, scorer.contextScore(s, DriveContext(batterySoc = 35)), eps)
        assertEquals(0.0, scorer.contextScore(s, DriveContext(batterySoc = 36)), eps)
    }

    @Test fun charge_full_battery_fails_threshold() {
        assertFalse(scorer.passes(sug(kind = SuggestionKind.CHARGE), DriveContext(batterySoc = 80), UserPrefs()))
        assertTrue(scorer.passes(sug(kind = SuggestionKind.CHARGE), DriveContext(batterySoc = 15), UserPrefs()))
    }

    @Test fun rest_driving_time_bands() {
        val s = sug(kind = SuggestionKind.REST)
        assertEquals(0.0, scorer.contextScore(s, DriveContext(drivingMinutes = 59)), eps)
        assertEquals(50.0, scorer.contextScore(s, DriveContext(drivingMinutes = 60)), eps)
        assertEquals(50.0, scorer.contextScore(s, DriveContext(drivingMinutes = 89)), eps)
        assertEquals(100.0, scorer.contextScore(s, DriveContext(drivingMinutes = 90)), eps)
    }

    @Test fun rain_outdoor_zeroes_weather() {
        val ctx = DriveContext(hourOfDay = 12, wiperOn = true)
        assertEquals(0.0, scorer.weatherScore(sug(outdoor = true), ctx), eps)
        assertEquals(100.0, scorer.weatherScore(sug(outdoor = false), ctx), eps)
        assertEquals(100.0, scorer.weatherScore(sug(outdoor = true), ctx.copy(wiperOn = false)), eps)
        assertEquals(77.5, scorer.score(sug(outdoor = true), ctx, UserPrefs()), eps)
    }

    @Test fun drive_thru_rain_bonus_capped() {
        val s = sug(kind = SuggestionKind.DRIVE_THRU)
        assertEquals(40.0, scorer.contextScore(s, DriveContext(hourOfDay = 15, wiperOn = true)), eps)
        assertEquals(100.0, scorer.contextScore(s, DriveContext(hourOfDay = 12, wiperOn = true)), eps)
        assertEquals(66.5, scorer.score(s, DriveContext(hourOfDay = 15, wiperOn = true), UserPrefs()), eps)
    }

    @Test fun story_context_always_50() {
        assertEquals(50.0, scorer.contextScore(sug(kind = SuggestionKind.STORY), DriveContext(hourOfDay = 3)), eps)
    }

    @Test fun preference_default_50_when_no_history() {
        assertEquals(50.0, scorer.preferenceScore(SuggestionKind.MEAL, UserPrefs()), eps)
    }

    @Test fun preference_acceptance_rate() {
        val p = UserPrefs()
        p.recordAccept(SuggestionKind.MEAL); p.recordAccept(SuggestionKind.MEAL)
        p.recordReject(SuggestionKind.MEAL); p.recordReject(SuggestionKind.MEAL)
        assertEquals(50.0, scorer.preferenceScore(SuggestionKind.MEAL, p), eps)
        assertEquals(50.0, scorer.preferenceScore(SuggestionKind.REST, p), eps)
        p.recordAccept(SuggestionKind.MEAL)
        assertEquals(60.0, scorer.preferenceScore(SuggestionKind.MEAL, p), eps)
    }

    @Test fun preference_history_keeps_last_20() {
        val p = UserPrefs()
        repeat(20) { p.recordReject(SuggestionKind.MEAL) }
        assertEquals(0.0, scorer.preferenceScore(SuggestionKind.MEAL, p), eps)
        repeat(20) { p.recordAccept(SuggestionKind.MEAL) }
        assertEquals(20, p.historySize(SuggestionKind.MEAL))
        assertEquals(100.0, scorer.preferenceScore(SuggestionKind.MEAL, p), eps)
    }

    @Test fun bias_accept_plus5_reject_minus8() {
        val p = UserPrefs()
        p.recordAccept(SuggestionKind.MEAL)
        assertEquals(5, p.kindBias(SuggestionKind.MEAL))
        p.recordReject(SuggestionKind.MEAL)
        assertEquals(-3, p.kindBias(SuggestionKind.MEAL))
    }

    @Test fun bias_clamped_to_range() {
        val p = UserPrefs()
        repeat(10) { p.recordAccept(SuggestionKind.MEAL) }
        assertEquals(UserPrefs.BIAS_MAX, p.kindBias(SuggestionKind.MEAL))
        repeat(10) { p.recordReject(SuggestionKind.MEAL) }
        assertEquals(UserPrefs.BIAS_MIN, p.kindBias(SuggestionKind.MEAL))
    }

    @Test fun bias_added_to_final_score() {
        val p = UserPrefs()
        repeat(4) { p.recordAccept(SuggestionKind.MEAL) }
        assertEquals(120.0, scorer.score(sug(), DriveContext(hourOfDay = 12), p), eps)
    }

    @Test fun bias_can_push_below_threshold() {
        val p = UserPrefs()
        repeat(4) { p.recordReject(SuggestionKind.MEAL) }
        assertEquals(45.0, scorer.score(sug(), DriveContext(hourOfDay = 12), p), eps)
        assertFalse(scorer.passes(sug(), DriveContext(hourOfDay = 12), p))
    }

    @Test fun disabled_gives_zero() {
        val p = UserPrefs().apply { suggestionsEnabled = false }
        assertEquals(0.0, scorer.score(sug(), DriveContext(hourOfDay = 12), p), eps)
    }
}
