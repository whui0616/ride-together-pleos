package ai.nunchi.engine

enum class XpReason(val label: String, val xp: Int) {
    ATTENDANCE("오늘도 함께 출발", 20),
    STREAK_7("7일 연속 출석", 50),
    STREAK_30("30일 연속 출석", 300),
    FIRST_DRIVE("첫 주행", 100),
    TRIP("함께 다녀온 길", 15),
    NEW_PLACE("새 지역 첫 방문", 30),
    REVISIT("오랜만에 다시 온 지역", 10),
    REGULAR("단골 지역", 10),
    FIRST_DAWN("첫 새벽 드라이브", 50),
    FIRST_RAIN("첫 빗길", 50),
    SUGGESTION_ACCEPTED("제안 수락", 5),
    SUGGESTION_ANSWERED("캐릭터 말에 대답", 2),
    DAILY_MISSIONS("일일 미션 완료", 30),
}

data class XpEntry(val day: Long, val reason: XpReason, val xp: Int)

data class DriveSummary(
    val durationMin: Long,
    val rainy: Boolean,
)

object LevelTitles {
    private val titles = listOf(1 to "새싹 친구", 5 to "동네 친구", 10 to "단짝", 20 to "길동무", 30 to "오랜 친구", 50 to "가족")
    fun of(level: Int): String = titles.last { it.first <= level.coerceAtLeast(1) }.second
    fun next(level: Int): Pair<Int, String>? = titles.firstOrNull { it.first > level }
    const val MAX_LEVEL = 50
}

data class ScoreState(
    val earnedToday: Int = 0,
    val earnedDay: Long = -1,
    val ledger: List<XpEntry> = emptyList(),
    val firstDriveDone: Boolean = false,
    val trips: Int = 0,
    val tripsToday: Int = 0,
    val tripDay: Long = -1,
    val firstDawnDone: Boolean = false,
    val firstRainDone: Boolean = false,
    val placesVisited: Int = 0,
) {
    fun grant(day: Long, reason: XpReason, requested: Int = reason.xp): Pair<ScoreState, Int> {
        val used = if (earnedDay == day) earnedToday else 0
        val give = requested.coerceAtMost((DAILY_CAP - used).coerceAtLeast(0))
        if (give <= 0) return this to 0
        val entry = XpEntry(day, reason, give)
        return copy(earnedToday = used + give, earnedDay = day, ledger = (ledger + entry).takeLast(LEDGER_KEPT)) to give
    }

    fun remainingToday(day: Long) = DAILY_CAP - (if (earnedDay == day) earnedToday else 0)

    fun tripsOn(day: Long): Int = if (tripDay == day) tripsToday else 0

    fun endDrive(day: Long, d: DriveSummary, dawn: Boolean = false): Pair<ScoreState, List<XpReason>> {
        val today = tripsOn(day)
        var s = copy(trips = trips + 1, tripsToday = today + 1, tripDay = day)
        val bonuses = mutableListOf<XpReason>()
        if (!s.firstDriveDone) { bonuses += XpReason.FIRST_DRIVE; s = s.copy(firstDriveDone = true) }
        if (today < TRIP_XP_PER_DAY) bonuses += XpReason.TRIP
        if (dawn && !s.firstDawnDone) { bonuses += XpReason.FIRST_DAWN; s = s.copy(firstDawnDone = true) }
        if (d.rainy && !s.firstRainDone) { bonuses += XpReason.FIRST_RAIN; s = s.copy(firstRainDone = true) }
        return s to bonuses
    }

    fun visitNewPlace(): ScoreState = copy(placesVisited = placesVisited + 1)

    companion object {
        const val DAILY_CAP = 500
        const val LEDGER_KEPT = 50
        const val TRIP_XP_PER_DAY = 5
    }
}

class DriveSessionTracker {
    private var startMs = -1L
    private var rainTicks = 0
    private var ticks = 0

    val driving: Boolean get() = startMs >= 0

    fun feed(s: DrivingSignals, nowMs: Long): DriveSummary? {
        if (startMs < 0) {
            if (!s.isParked && s.speedKmh > 0f) { startMs = nowMs; rainTicks = 0; ticks = 0 }
            return null
        }
        ticks++
        if (s.wiper != WiperLevel.OFF) rainTicks++
        if (!s.isParked) return null
        val began = startMs
        startMs = -1
        return DriveSummary(
            durationMin = (nowMs - began) / 60_000,
            rainy = ticks > 0 && rainTicks * 2 >= ticks,
        )
    }
}
