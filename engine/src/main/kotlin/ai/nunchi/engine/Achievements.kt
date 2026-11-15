package ai.nunchi.engine

enum class AchCategory(val label: String) {
    TOGETHER("함께한 시간"), EXPLORE("탐험"), FIRST("처음"), ANNIVERSARY("기념일"), BOND("교감"), CHARACTER("캐릭터"), SECRET("특별"),
}

enum class Tier(val label: String, val medal: String) {
    BRONZE("동", "🥉"), SILVER("은", "🥈"), GOLD("금", "🥇"), PLATINUM("백금", "💎"), DIAMOND("전설", "👑"),
}

data class Achievement(
    val id: String,
    val category: AchCategory,
    val title: String,
    val desc: String,
    val key: String,
    val thresholds: List<Int>,
    val honor: String? = null,
    val memory: String = "오늘을 기억해 둘게요.",
) {
    init { require(thresholds.isNotEmpty() && thresholds.size <= Tier.entries.size && thresholds == thresholds.sorted()) }
    val maxTier: Int get() = thresholds.size
    val hidden: Boolean get() = category == AchCategory.SECRET
    fun tierFor(value: Int): Int = thresholds.count { value >= it }
    fun coinsFor(tier: Int): Int = TIER_COINS[tier - 1]
    fun describe(tier: Int): String = desc.replace("%d", thresholds[tier.coerceIn(0, maxTier - 1)].toString())
    fun memoryLine(tier: Int): String = memory.replace("%d", thresholds[(tier - 1).coerceIn(0, maxTier - 1)].toString())

    companion object { val TIER_COINS = listOf(10, 30, 100, 300, 1000) }
}

object AchKey {
    const val TRIPS = "trips"; const val RAIN_DRIVE = "rain_drive"; const val NIGHT_DRIVE = "night_drive"
    const val FIRST_DRIVE = "first_drive"; const val STREAK_BEST = "streak_best"; const val VISITS = "visits"
    const val TALKS = "talks"; const val ANSWERED = "answered"; const val ACCEPTED = "accepted"; const val REJECTED = "rejected"
    const val PETS = "pets"; const val SNACKS = "snacks"; const val AFFINITY = "affinity"
    const val DAWN_DRIVE = "dawn_drive"; const val EARLY_VISIT = "early_visit"; const val BIRTHDAY = "birthday"
    const val PLACES = "places"; const val REST_AREAS = "rest_areas"; const val DRIVE_THRU = "drive_thru"; const val STORIES = "stories"
    const val OUTFITS = "outfits"; const val PERSONALITIES = "personalities"
    const val SHOP_BUYS = "shop_buys"; const val OWNED = "owned"; const val HAIR_COLORS = "hair_colors"
    const val RAIN_DRIVE_THRU = "rain_drive_thru"; const val NEW_YEAR = "new_year"; const val CHRISTMAS = "christmas"
    const val LEVEL = "level"; const val REJECT_STREAK = "reject_streak"
    const val DAYS = "days"
    const val COMMUTE_IN = "commute_in"; const val COMMUTE_OUT = "commute_out"

    fun commute(dayOfWeek: java.time.DayOfWeek, hour: Int): String? = when {
        dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY -> null
        hour in 6..9 -> COMMUTE_IN
        hour in 17..20 -> COMMUTE_OUT
        else -> null
    }
}

object Achievements {
    private fun a(id: String, c: AchCategory, t: String, d: String, k: String, vararg th: Int, honor: String? = null, say: String = "오늘을 기억해 둘게요.") =
        Achievement(id, c, t, d, k, th.toList(), honor, say)

    val ALL: List<Achievement> = listOf(
        a("days", AchCategory.TOGETHER, "함께한 날", "함께한 지 %d일", AchKey.DAYS, 30, 100, 365, 1000, 1825, honor = "오랜 친구", say = "우리 함께한 지 %d일이에요! 처음 만난 날 기억나요?"),
        a("streak", AchCategory.TOGETHER, "매일 보는 사이", "연속 출석 %d일", AchKey.STREAK_BEST, 7, 30, 100, 365, 1000, honor = "1년의 동행", say = "%d일째 매일 만나네요. 내일도 봐요!"),
        a("visits", AchCategory.TOGETHER, "자주 오는 사이", "총 출석 %d일", AchKey.VISITS, 10, 50, 365, 1000, 2000, say = "같이 출발한 날이 %d일이 됐어요."),
        a("trips", AchCategory.TOGETHER, "함께 다녀온 길", "함께 다녀온 길 %d번", AchKey.TRIPS, 1, 10, 100, 1000, 3000, honor = "길동무", say = "함께 다녀온 길이 %d번이에요. 다음엔 어디 갈까요?"),
        a("earlybird", AchCategory.TOGETHER, "얼리버드", "아침 6시 전 출석 %d일", AchKey.EARLY_VISIT, 7, 30, 100, 300, 1000, say = "이른 아침을 %d번이나 같이 열었어요."),
        a("commute_in", AchCategory.TOGETHER, "같이 출근", "함께 출근 %d번", AchKey.COMMUTE_IN, 10, 100, 500, 1000, honor = "출근 메이트", say = "오늘도 같이 출근했어요. 벌써 %d번째예요."),
        a("commute_out", AchCategory.TOGETHER, "같이 퇴근", "함께 퇴근 %d번", AchKey.COMMUTE_OUT, 10, 100, 500, 1000, honor = "퇴근 메이트", say = "오늘도 수고했어요. 같이 퇴근한 게 %d번째예요."),
        a("first_drive", AchCategory.FIRST, "처음 만난 날", "첫 주행 %d회", AchKey.FIRST_DRIVE, 1, say = "우리 처음 같이 달린 날이에요. 잊지 않을게요."),
        a("owl", AchCategory.FIRST, "첫 새벽 드라이브", "새벽(03~05시) 주행 %d회", AchKey.DAWN_DRIVE, 1, 10, 30, 100, 300, say = "새벽 공기 속 첫 드라이브였어요. 조용해서 좋았어요."),
        a("rain", AchCategory.FIRST, "빗길 동행", "비 오는 날 함께 달리기 %d회", AchKey.RAIN_DRIVE, 1, 30, 100, 300, 500, say = "비 오는 날 같이 달렸어요. 빗소리 좋았죠?"),
        a("night", AchCategory.FIRST, "밤길 동행", "밤(22~05시)에 함께 달리기 %d회", AchKey.NIGHT_DRIVE, 1, 30, 100, 500, 1000, say = "밤길도 같이라 든든했어요."),
        a("birthday", AchCategory.ANNIVERSARY, "생일 축하", "내 생일에 캐릭터 만나기 %d회", AchKey.BIRTHDAY, 1, 2, 3, 5, 10, say = "생일 축하해요! 오늘은 제가 더 열심히 할게요."),
        a("new_year", AchCategory.ANNIVERSARY, "새해 첫 인사", "1월 1일 출석 %d회", AchKey.NEW_YEAR, 1, 2, 3, 5, 10, say = "새해 첫날을 같이 시작했어요. 올해도 잘 부탁해요!"),
        a("christmas", AchCategory.ANNIVERSARY, "메리 크리스마스", "12월 25일 출석 %d회", AchKey.CHRISTMAS, 1, 2, 3, 5, 10, say = "메리 크리스마스! 오늘을 오래 기억할게요."),
        a("talks", AchCategory.BOND, "수다 친구", "캐릭터와 %d번 대화", AchKey.TALKS, 100, 1000, 3000, 10000, 30000, say = "우리 벌써 %d번이나 이야기했어요."),
        a("answered", AchCategory.BOND, "무시하지 않는 사람", "제안에 %d번 응답", AchKey.ANSWERED, 100, 500, 1000, 3000, 10000, honor = "무시하지 않는 사람", say = "제 말에 %d번이나 대답해 줬어요. 고마워요."),
        a("accepted", AchCategory.BOND, "같이 가요", "제안 수락 %d회", AchKey.ACCEPTED, 10, 100, 500, 1000, 3000, say = "제 제안을 %d번 들어줬어요. 같이 가서 좋았어요."),
        a("rejected", AchCategory.BOND, "취향이 확실한", "제안 거절 %d회", AchKey.REJECTED, 10, 100, 500, 1000, 3000, say = "취향이 확실하시네요. 그것도 좋아요."),
        a("pets", AchCategory.BOND, "쓰담쓰담", "쓰다듬기 %d회", AchKey.PETS, 100, 500, 1000, 3000, 5000, say = "쓰담쓰담 %d번째예요. 기분 좋아요."),
        a("snacks", AchCategory.BOND, "간식 요정", "간식 %d번", AchKey.SNACKS, 50, 300, 1000, 3000, 5000, say = "간식 %d번! 잘 먹었어요."),
        a("affinity", AchCategory.BOND, "단짝", "친밀도 %d 도달", AchKey.AFFINITY, 3, 5, 7, 10, honor = "가족", say = "우리 꽤 친해졌죠? 저는 그렇게 생각해요."),
        a("places", AchCategory.EXPLORE, "탐험가", "새로운 지역 %d곳", AchKey.PLACES, 1, 10, 50, 100, 200, honor = "전국 탐험가", say = "처음 가 본 곳이 %d곳이 됐어요. 또 어디 가 볼까요?"),
        a("rest_areas", AchCategory.EXPLORE, "휴게소 투어", "휴식 제안으로 휴게소 %d곳", AchKey.REST_AREAS, 5, 20, 50, 100, 200, say = "같이 들른 휴게소가 %d곳이에요."),
        a("drive_thru", AchCategory.EXPLORE, "드라이브스루 단골", "드라이브스루 %d회", AchKey.DRIVE_THRU, 10, 50, 100, 300, 1000, say = "드라이브스루 %d번째예요. 단골이네요!"),
        a("stories", AchCategory.EXPLORE, "길 위의 이야기", "캐릭터 이야기 %d개 듣기", AchKey.STORIES, 10, 50, 100, 200, 300, say = "제 이야기를 %d개나 들어줬어요."),
        a("grown", AchCategory.CHARACTER, "같이 자랐어요", "레벨 %d", AchKey.LEVEL, 5, 10, 20, 30, 40, say = "레벨 %d이에요! 우리 같이 크고 있어요."),
        a("outfit", AchCategory.CHARACTER, "첫 꾸미기", "옷장에서 코디 바꾸기 %d회", AchKey.OUTFITS, 1, 20, 100, 300, 1000, say = "새 모습 어때요? 저는 마음에 들어요."),
        a("personalities", AchCategory.CHARACTER, "다 겪어 봤어", "성격 %d종 모두 써 보기", AchKey.PERSONALITIES, 5, say = "제 모습을 전부 만나 봤네요."),
        a("shop_first", AchCategory.CHARACTER, "첫 쇼핑", "상점에서 %d번 구매", AchKey.SHOP_BUYS, 1, 10, 30, 50, 100, say = "선물 고마워요. 아껴 입을게요."),
        a("closet", AchCategory.CHARACTER, "옷장 부자", "의상 %d개 보유", AchKey.OWNED, 5, 10, 20, 30, honor = "패셔니스타", say = "옷이 %d벌이나 생겼어요. 뭘 입을지 고민돼요."),
        a("hair_colors", AchCategory.CHARACTER, "무지개 머리", "머리색 %d가지 전부 해금", AchKey.HAIR_COLORS, 3, 5, 8, say = "머리색을 %d가지나 해 봤어요."),
        a("rain_drive_thru", AchCategory.SECRET, "비 오는 날 커피", "비 오는 날 드라이브스루 수락 %d회", AchKey.RAIN_DRIVE_THRU, 1, say = "비 오는 날 커피 한 잔, 좋았어요."),
        a("stubborn", AchCategory.SECRET, "고집불통", "제안 %d번 연속 거절", AchKey.REJECT_STREAK, 7, say = "…그래도 계속 말 걸 거예요."),
        a("level50", AchCategory.SECRET, "전설", "레벨 %d", AchKey.LEVEL, 50, honor = "전설", say = "여기까지 같이 왔어요. 고마워요, 정말."),
    )

    val byId: Map<String, Achievement> = ALL.associateBy { it.id }
    val totalTiers: Int = ALL.sumOf { it.maxTier }
}

data class Unlock(val achievement: Achievement, val tier: Int, val day: Long) {
    val coins: Int get() = achievement.coinsFor(tier)
    val honor: String? get() = achievement.honor.takeIf { tier == achievement.maxTier }
    fun card(place: String, look: String) = MemoryCard(achievement.id, tier, day, place, look, achievement.memoryLine(tier))
}

data class MemoryCard(val achId: String, val tier: Int, val day: Long, val place: String, val look: String, val line: String) {
    val achievement: Achievement? get() = Achievements.byId[achId]
    val title: String get() = achievement?.title ?: if (achId == FIRST_PLACE) "처음 가 본 $place" else achId

    companion object {
        const val FIRST_PLACE = "first_place"
        fun firstPlace(day: Long, place: String, look: String) = MemoryCard(FIRST_PLACE, 1, day, place, look, "처음 와 본 ${place}예요. 오늘을 기억해 둘게요.")
    }

    fun recallLine(today: Long): String? {
        val ago = today - day
        if (ago <= 0) return null
        val then = java.time.LocalDate.ofEpochDay(day); val now = java.time.LocalDate.ofEpochDay(today)
        val years = now.year - then.year
        val whenText = when {
            years >= 1 && then.monthValue == now.monthValue && then.dayOfMonth == now.dayOfMonth -> if (years == 1) "작년 오늘" else "${years}년 전 오늘"
            ago == 1L -> "어제"
            ago < 7 -> "${ago}일 전"
            ago < 30 -> "${ago / 7}주 전"
            ago < 365 -> "${ago / 30}달 전"
            else -> "${ago / 365}년 전"
        }
        return if (place.isNotEmpty()) "$whenText ${place}에 갔던 날 기억나요? 「$title」 추억이 그날이에요."
        else "$whenText 기억나요? 「$title」 추억이 그날이에요."
    }
}

data class AchState(
    val counters: Map<String, Int> = emptyMap(),
    val tiers: Map<String, Int> = emptyMap(),
    val unlockedDay: Map<String, Long> = emptyMap(),
    val recent: List<String> = emptyList(),
    val lastExternal: Map<String, Int> = emptyMap(),
    val album: List<MemoryCard> = emptyList(),
) {
    fun bump(key: String, by: Int = 1): AchState = copy(counters = counters + (key to (counters[key] ?: 0) + by))
    fun set(key: String, value: Int): AchState = copy(counters = counters + (key to value))
    fun setAtLeast(key: String, value: Int): AchState = if ((counters[key] ?: 0) >= value) this else set(key, value)
    fun count(key: String): Int = maxOf(counters[key] ?: 0, lastExternal[key] ?: 0)

    fun tierOf(a: Achievement): Int = tiers[a.id] ?: 0

    fun progress(a: Achievement): Pair<Int, Int> {
        val v = count(a.key)
        val t = tierOf(a)
        return v to a.thresholds[t.coerceAtMost(a.maxTier - 1)]
    }

    fun ratio(a: Achievement): Float {
        val (v, goal) = progress(a)
        return if (tierOf(a) >= a.maxTier) 1f else (v.toFloat() / goal).coerceIn(0f, 1f)
    }

    fun nearlyDone(a: Achievement): Boolean = tierOf(a) < a.maxTier && ratio(a) >= 0.9f

    val completionPercent: Int get() = if (Achievements.totalTiers == 0) 0 else Achievements.ALL.sumOf { tierOf(it) } * 100 / Achievements.totalTiers

    val tiersDone: Int get() = Achievements.ALL.sumOf { tierOf(it) }

    val honors: List<String> get() = Achievements.ALL.filter { it.honor != null && tierOf(it) >= it.maxTier }.map { it.honor!! }

    fun evaluate(day: Long, external: Map<String, Int> = emptyMap()): Pair<AchState, List<Unlock>> {
        var s = copy(lastExternal = external)
        val unlocks = mutableListOf<Unlock>()
        for (a in Achievements.ALL) {
            val have = s.tierOf(a)
            val now = a.tierFor(s.count(a.key))
            if (now <= have) continue
            for (t in have + 1..now) unlocks += Unlock(a, t, day)
            s = s.copy(
                tiers = s.tiers + (a.id to now),
                unlockedDay = s.unlockedDay + (a.id to day),
                recent = (listOf(a.id) + s.recent.filter { it != a.id }).take(RECENT_KEPT),
            )
        }
        return s to unlocks
    }

    fun keep(cards: List<MemoryCard>): AchState = if (cards.isEmpty()) this else copy(album = (album + cards).takeLast(ALBUM_KEPT))

    fun backfillAlbum(look: String): AchState {
        if (album.isNotEmpty()) return this
        val cards = Achievements.ALL.filter { tierOf(it) > 0 }.mapNotNull { a ->
            val day = unlockedDay[a.id] ?: return@mapNotNull null
            MemoryCard(a.id, tierOf(a), day, "", look, a.memoryLine(tierOf(a)))
        }.sortedBy { it.day }
        return keep(cards)
    }

    fun recall(today: Long, pick: Int): MemoryCard? {
        val past = album.filter { it.day < today }
        if (past.isEmpty()) return null
        val now = java.time.LocalDate.ofEpochDay(today)
        past.lastOrNull { c -> java.time.LocalDate.ofEpochDay(c.day).let { it.year < now.year && it.monthValue == now.monthValue && it.dayOfMonth == now.dayOfMonth } }?.let { return it }
        return past[Math.floorMod(pick, past.size)]
    }

    companion object {
        const val RECENT_KEPT = 3
        const val ALBUM_KEPT = 200
    }
}
