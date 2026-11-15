package ai.nunchi.engine

data class LobbyState(
    val streak: Int = 0,
    val lastVisitDay: Long = -1,
    val affinity: Int = 0,
    val petsToday: Int = 0,
    val petDay: Long = -1,
    val coins: Int = 0,
    val mood: Int = 70,
    val snacksToday: Int = 0,
    val snackDay: Long = -1,
    val talksToday: Int = 0,
    val talkDay: Long = -1,
    val missionClaimedDay: Long = -1,
    val rewardClaimedStreak: Int = 0,
    val visitDays: List<Long> = emptyList(),
) {
    val affinityLevel: Int get() = (1 + affinity / AFFINITY_PER_LEVEL).coerceAtMost(MAX_AFFINITY_LEVEL)

    fun addressing(driverName: String = "운전자"): String = when {
        affinityLevel >= 9 -> "가족"
        affinityLevel >= 6 -> "단짝"
        affinityLevel >= 3 -> "친구"
        else -> "${driverName}님"
    }

    fun visit(day: Long): LobbyState {
        if (lastVisitDay == day) return this
        val gap = if (lastVisitDay < 0) 0 else day - lastVisitDay
        val next = if (gap == 1L) streak + 1 else 1
        val missed = (gap - 1).coerceAtLeast(0).toInt()
        val days = (visitDays + day).distinct().sorted().takeLast(VISIT_DAYS_KEPT)
        return copy(
            streak = next, lastVisitDay = day,
            mood = (mood - missed * MOOD_DROP_PER_MISSED_DAY).coerceIn(0, 100),
            visitDays = days,
        )
    }

    fun canPet(day: Long): Boolean = petDay != day || petsToday < PETS_PER_DAY

    fun pet(day: Long): LobbyState {
        if (!canPet(day)) return this
        val used = if (petDay == day) petsToday else 0
        return copy(affinity = affinity + 1, petsToday = used + 1, petDay = day, mood = (mood + MOOD_PET).coerceAtMost(100))
    }

    fun talk(day: Long): LobbyState = copy(talksToday = (if (talkDay == day) talksToday else 0) + 1, talkDay = day)

    fun snacksUsed(day: Long) = if (snackDay == day) snacksToday else 0
    fun canSnack(day: Long): Boolean = coins >= SNACK_COST && snacksUsed(day) < SNACKS_PER_DAY

    fun snack(day: Long): LobbyState {
        if (!canSnack(day)) return this
        return copy(coins = coins - SNACK_COST, snacksToday = snacksUsed(day) + 1, snackDay = day, mood = (mood + MOOD_SNACK).coerceAtMost(100))
    }

    fun addCoins(n: Int): LobbyState = copy(coins = (coins + n).coerceAtLeast(0))

    fun pendingStreakReward(): StreakReward? =
        STREAK_REWARDS.lastOrNull { it.days <= streak && it.days > rewardClaimedStreak }

    fun claimStreakReward(): LobbyState {
        val r = pendingStreakReward() ?: return this
        return copy(coins = coins + r.coins, rewardClaimedStreak = r.days)
    }

    fun missions(day: Long, tripsToday: Int): List<Mission> = listOf(
        Mission("캐릭터와 한마디", if (talkDay == day) talksToday else 0, 1),
        Mission("쓰다듬기", if (petDay == day) petsToday else 0, PETS_PER_DAY),
        Mission("함께 다녀오기", tripsToday, 1),
    )

    fun missionsDone(day: Long, tripsToday: Int) = missions(day, tripsToday).all { it.done }

    fun canClaimMissions(day: Long, tripsToday: Int) = missionClaimedDay != day && missionsDone(day, tripsToday)

    fun claimMissions(day: Long, tripsToday: Int): LobbyState =
        if (canClaimMissions(day, tripsToday)) copy(coins = coins + MISSION_REWARD, missionClaimedDay = day) else this

    val moodBand: MoodBand
        get() = when {
            mood >= 80 -> MoodBand.GREAT
            mood >= 50 -> MoodBand.GOOD
            mood >= 25 -> MoodBand.LOW
            else -> MoodBand.BAD
        }

    companion object {
        const val PETS_PER_DAY = 3
        const val AFFINITY_PER_LEVEL = 5
        const val MAX_AFFINITY_LEVEL = 10
        const val SNACK_COST = 5
        const val SNACKS_PER_DAY = 3
        const val MOOD_PET = 5
        const val MOOD_SNACK = 20
        const val MOOD_DROP_PER_MISSED_DAY = 15
        const val MISSION_REWARD = 30
        const val VISIT_DAYS_KEPT = 90
        val STREAK_REWARDS = listOf(StreakReward(3, 20), StreakReward(7, 50), StreakReward(30, 300), StreakReward(100, 1000))
    }
}

data class StreakReward(val days: Int, val coins: Int)

data class Mission(val title: String, val progress: Int, val target: Int) {
    val done: Boolean get() = progress >= target
}

enum class MoodBand(val emoji: String, val label: String) {
    GREAT("😄", "최고"), GOOD("🙂", "좋음"), LOW("😐", "시무룩"), BAD("😢", "외로움"),
}
