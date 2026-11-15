package ai.nunchi.engine

enum class Persona(val title: String, val greeting: String) {
    EASYGOING("느긋한 여행가", "천천히 가도 괜찮아요, 풍경도 보면서요."),
    ADVENTURER("모험가", "오늘은 어디까지 달려 볼까요?"),
    BUDDY("든든한 동료", "오늘도 같이 가요!"),
}

data class CompanionProfile(
    val xp: Int = 0,
    val distanceKm: Float = 0f,
    val highwayKm: Float = 0f,
) {
    val level: Int get() = (1 + xp / XP_PER_LEVEL).coerceAtMost(LevelTitles.MAX_LEVEL)
    val title: String get() = LevelTitles.of(level)
    val xpInLevel: Int get() = xp % XP_PER_LEVEL

    val persona: Persona
        get() = when {
            distanceKm < PERSONA_MIN_KM -> Persona.BUDDY
            highwayKm / distanceKm >= 0.5f -> Persona.ADVENTURER
            highwayKm / distanceKm <= 0.1f -> Persona.EASYGOING
            else -> Persona.BUDDY
        }

    companion object {
        const val XP_PER_LEVEL = 100
        const val PERSONA_MIN_KM = 20f
    }
}

class HabitTracker(initial: CompanionProfile = CompanionProfile()) {
    var profile = initial
        private set

    fun addXp(n: Int) { if (n > 0) profile = profile.copy(xp = profile.xp + n) }

    private var prev: DrivingSignals? = null
    private var prevAtMs = 0L

    fun update(s: DrivingSignals, nowMs: Long) {
        val p = prev
        if (p != null) {
            val dtH = (nowMs - prevAtMs).coerceAtLeast(0) / 3_600_000f
            val km = p.speedKmh * dtH
            profile = profile.copy(
                distanceKm = profile.distanceKm + km,
                highwayKm = profile.highwayKm + if (p.speedKmh >= 80f) km else 0f,
            )
        }
        prev = s
        prevAtMs = nowMs
    }
}
