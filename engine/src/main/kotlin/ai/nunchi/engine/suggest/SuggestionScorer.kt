package ai.nunchi.engine.suggest

class UserPrefs {
    var suggestionsEnabled: Boolean = true

    private val history = HashMap<SuggestionKind, ArrayDeque<Boolean>>()
    private val bias = HashMap<SuggestionKind, Int>()

    fun recordAccept(kind: SuggestionKind) {
        push(kind, true)
        bias[kind] = (kindBias(kind) + ACCEPT_BIAS).coerceIn(BIAS_MIN, BIAS_MAX)
    }

    fun recordReject(kind: SuggestionKind) {
        push(kind, false)
        bias[kind] = (kindBias(kind) + REJECT_BIAS).coerceIn(BIAS_MIN, BIAS_MAX)
    }

    fun kindBias(kind: SuggestionKind): Int = bias[kind] ?: 0

    fun acceptanceRate(kind: SuggestionKind): Double? {
        val h = history[kind] ?: return null
        if (h.isEmpty()) return null
        return h.count { it }.toDouble() / h.size
    }

    fun historySize(kind: SuggestionKind): Int = history[kind]?.size ?: 0

    private fun push(kind: SuggestionKind, accepted: Boolean) {
        val h = history.getOrPut(kind) { ArrayDeque() }
        h.addLast(accepted)
        while (h.size > HISTORY_SIZE) h.removeFirst()
    }

    companion object {
        const val HISTORY_SIZE = 20
        const val ACCEPT_BIAS = 5
        const val REJECT_BIAS = -8
        const val BIAS_MIN = -30
        const val BIAS_MAX = 20
    }
}

class SuggestionScorer {

    fun score(s: Suggestion, ctx: DriveContext, prefs: UserPrefs): Double {
        if (!prefs.suggestionsEnabled) return 0.0
        val total = distanceScore(s.distanceAheadM) * W_DISTANCE +
            contextScore(s, ctx) * W_CONTEXT +
            preferenceScore(s.kind, prefs) * W_PREFERENCE +
            weatherScore(s, ctx) * W_WEATHER
        return total + prefs.kindBias(s.kind)
    }

    fun passes(s: Suggestion, ctx: DriveContext, prefs: UserPrefs): Boolean =
        score(s, ctx, prefs) >= THRESHOLD

    fun distanceScore(m: Double): Double = when {
        m < 500.0 -> 0.0
        m <= 3000.0 -> 100.0
        m <= 6000.0 -> 100.0 * (6000.0 - m) / 3000.0
        else -> 0.0
    }

    fun contextScore(s: Suggestion, ctx: DriveContext): Double = when (s.kind) {
        SuggestionKind.MEAL -> mealTimeScore(ctx.hourOfDay)
        SuggestionKind.DRIVE_THRU ->
            (mealTimeScore(ctx.hourOfDay) + if (ctx.wiperOn) 20.0 else 0.0).coerceAtMost(100.0)
        SuggestionKind.REST -> when {
            ctx.drivingMinutes >= 90 -> 100.0
            ctx.drivingMinutes >= 60 -> 50.0
            else -> 0.0
        }
        SuggestionKind.CHARGE -> when {
            ctx.batterySoc <= 20 -> 100.0
            ctx.batterySoc <= 35 -> 60.0
            else -> 0.0
        }
        SuggestionKind.STORY -> 50.0
        SuggestionKind.ROUTE -> 50.0
    }

    fun mealTimeScore(hour: Int): Double =
        if (hour in 11..13 || hour in 17..19) 100.0 else 20.0

    fun preferenceScore(kind: SuggestionKind, prefs: UserPrefs): Double =
        prefs.acceptanceRate(kind)?.let { it * 100.0 } ?: 50.0

    fun weatherScore(s: Suggestion, ctx: DriveContext): Double =
        if (ctx.wiperOn && s.isOutdoor) 0.0 else 100.0

    companion object {
        const val W_DISTANCE = 0.30
        const val W_CONTEXT = 0.35
        const val W_PREFERENCE = 0.25
        const val W_WEATHER = 0.10
        const val THRESHOLD = 60.0
    }
}
