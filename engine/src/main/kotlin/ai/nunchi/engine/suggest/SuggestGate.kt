package ai.nunchi.engine.suggest

import ai.nunchi.engine.Gear

object SuggestGate {
    const val HARD_BRAKE_COOLDOWN_MS = 8_000L
    const val MIN_GAP_MS = 90_000L
    const val MAX_REPLY_SUGGESTIONS_PER_TRIP = 2
    const val MAX_STORIES_PER_TRIP = 1
    const val MIN_METERS_TO_TBT = 300.0
    const val MAX_BRAKE_PERCENT = 30
    const val MAX_SPEED_DELTA = 15.0

    fun isOpen(ctx: DriveContext, s: Suggestion): Boolean = reason(ctx, s) == null

    fun reason(ctx: DriveContext, s: Suggestion): String? = when {
        ctx.gear != Gear.DRIVE -> "GEAR_NOT_D"
        ctx.msSinceHardBrake < HARD_BRAKE_COOLDOWN_MS -> "HARD_BRAKE_COOLDOWN"
        ctx.turnSignalOn -> "TURN_SIGNAL_ON"
        ctx.brakePedalPercent >= MAX_BRAKE_PERCENT -> "BRAKING"
        ctx.speedDeltaKmh10s >= MAX_SPEED_DELTA -> "SPEED_UNSTABLE"
        ctx.msSinceLastSpeech < MIN_GAP_MS -> "SPEECH_GAP"
        s.requiresReply && ctx.repliesSpokenThisTrip >= MAX_REPLY_SUGGESTIONS_PER_TRIP -> "TRIP_REPLY_LIMIT"
        s.kind == SuggestionKind.STORY && ctx.storiesSpokenThisTrip >= MAX_STORIES_PER_TRIP -> "TRIP_STORY_LIMIT"
        ctx.safetyAlertActive -> "SAFETY_ALERT"
        ctx.metersToNextTbt < MIN_METERS_TO_TBT -> "TBT_IMMINENT"
        else -> null
    }
}
