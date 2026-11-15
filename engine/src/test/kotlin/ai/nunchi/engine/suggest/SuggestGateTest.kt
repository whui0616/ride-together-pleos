package ai.nunchi.engine.suggest

import ai.nunchi.engine.Gear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestGateTest {
    private val meal = Suggestion(
        "m", SuggestionKind.MEAL, "p", 0.0, 0.0, 1000.0,
        requiresReply = true, script = "", createdAtMs = 0L,
    )
    private val story = meal.copy(id = "st", kind = SuggestionKind.STORY, requiresReply = false)

    private val open = DriveContext(
        gear = Gear.DRIVE,
        msSinceHardBrake = 8_000L,
        turnSignalOn = false,
        brakePedalPercent = 0,
        speedDeltaKmh10s = 0.0,
        msSinceLastSpeech = 90_000L,
        repliesSpokenThisTrip = 0,
        storiesSpokenThisTrip = 0,
        safetyAlertActive = false,
        metersToNextTbt = 300.0,
    )

    @Test fun all_conditions_met_is_open() {
        assertTrue(SuggestGate.isOpen(open, meal))
        assertNull(SuggestGate.reason(open, meal))
    }

    @Test fun gear_must_be_D() {
        for (g in listOf(Gear.PARK, Gear.REVERSE, Gear.NEUTRAL)) {
            assertEquals("GEAR_NOT_D", SuggestGate.reason(open.copy(gear = g), meal))
        }
    }

    @Test fun hard_brake_cooldown_boundary_8s() {
        assertEquals("HARD_BRAKE_COOLDOWN", SuggestGate.reason(open.copy(msSinceHardBrake = 7_999L), meal))
        assertNull(SuggestGate.reason(open.copy(msSinceHardBrake = 8_000L), meal))
    }

    @Test fun turn_signal_blocks() {
        assertEquals("TURN_SIGNAL_ON", SuggestGate.reason(open.copy(turnSignalOn = true), meal))
    }

    @Test fun brake_pedal_boundary_30pct() {
        assertNull(SuggestGate.reason(open.copy(brakePedalPercent = 29), meal))
        assertEquals("BRAKING", SuggestGate.reason(open.copy(brakePedalPercent = 30), meal))
    }

    @Test fun speed_delta_boundary_15() {
        assertNull(SuggestGate.reason(open.copy(speedDeltaKmh10s = 14.9), meal))
        assertEquals("SPEED_UNSTABLE", SuggestGate.reason(open.copy(speedDeltaKmh10s = 15.0), meal))
    }

    @Test fun speech_gap_boundary_90s() {
        assertEquals("SPEECH_GAP", SuggestGate.reason(open.copy(msSinceLastSpeech = 89_999L), meal))
        assertNull(SuggestGate.reason(open.copy(msSinceLastSpeech = 90_000L), meal))
    }

    @Test fun reply_suggestions_max_2_per_trip() {
        assertNull(SuggestGate.reason(open.copy(repliesSpokenThisTrip = 1), meal))
        assertEquals("TRIP_REPLY_LIMIT", SuggestGate.reason(open.copy(repliesSpokenThisTrip = 2), meal))
        assertNull(SuggestGate.reason(open.copy(repliesSpokenThisTrip = 2), story))
    }

    @Test fun story_max_1_per_trip() {
        assertNull(SuggestGate.reason(open.copy(storiesSpokenThisTrip = 0), story))
        assertEquals("TRIP_STORY_LIMIT", SuggestGate.reason(open.copy(storiesSpokenThisTrip = 1), story))
        assertNull(SuggestGate.reason(open.copy(storiesSpokenThisTrip = 1), meal))
    }

    @Test fun safety_alert_blocks() {
        assertEquals("SAFETY_ALERT", SuggestGate.reason(open.copy(safetyAlertActive = true), meal))
    }

    @Test fun tbt_boundary_300m() {
        assertEquals("TBT_IMMINENT", SuggestGate.reason(open.copy(metersToNextTbt = 299.9), meal))
        assertNull(SuggestGate.reason(open.copy(metersToNextTbt = 300.0), meal))
    }

    @Test fun first_failed_reason_wins() {
        val ctx = open.copy(gear = Gear.REVERSE, turnSignalOn = true)
        assertEquals("GEAR_NOT_D", SuggestGate.reason(ctx, meal))
        assertFalse(SuggestGate.isOpen(ctx, meal))
    }
}
