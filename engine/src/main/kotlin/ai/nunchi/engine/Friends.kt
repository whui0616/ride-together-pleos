package ai.nunchi.engine

import kotlin.random.Random

data class FriendProfile(
    val code: String,
    val name: String,
    val personality: String,
    val look: Map<String, String>,
    val level: Int,
    val honor: String?,
    val streak: Int,
    val achPercent: Int,
    val lastSeenDays: Int,
) {
    val online: Boolean get() = lastSeenDays == 0
}

object FriendDirectory {
    private fun look(hair: String, color: String, outfit: String) = mapOf("hair" to hair, "hairColor" to color, "outfit" to outfit)

    val ALL: List<FriendProfile> = listOf(
        FriendProfile("MINJ2026", "민지네 루미", "WARM", look("4", "pink", "7"), 32, "전국 탐험가", 41, 48, 0),
        FriendProfile("JUNH7XQ4", "준호의 루온", "COOL", look("3", "black", "5"), 18, "패셔니스타", 12, 31, 0),
        FriendProfile("SEOA3KP9", "서아의 몽이", "PLAYFUL", look("3", "honey", "11"), 9, null, 5, 17, 1),
        FriendProfile("DOHY8MZ2", "도윤이 차 친구", "CALM", look("1", "brown", "10"), 50, "1년의 동행", 230, 72, 0),
        FriendProfile("HARU5TB6", "하루", "ENERGETIC", look("0", "silver", "0"), 3, null, 2, 6, 4),
    )

    private val byCode = ALL.associateBy { it.code }
    fun find(code: String): FriendProfile? = byCode[normalize(code)]
    fun normalize(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

    val STARTER_FRIENDS = listOf("MINJ2026", "JUNH7XQ4")
    val STARTER_REQUESTS = listOf("SEOA3KP9", "DOHY8MZ2")
}

enum class Cheer(val emoji: String, val label: String) { THUMBS("👍", "최고"), FIRE("🔥", "불타요"), CLAP("👏", "박수"), HEART("💙", "응원해"), CAR("🚗", "안전 운전") }

enum class AddResult(val message: String) {
    ADDED("친구가 됐어요"), NOT_FOUND("그 코드의 친구를 찾을 수 없어요"), ALREADY("이미 친구예요"),
    SELF("내 코드예요"), FULL("친구는 ${FriendState.MAX_FRIENDS}명까지예요"), BLOCKED("차단한 친구예요. 차단을 풀면 추가할 수 있어요"),
}

data class FriendState(
    val myCode: String = "",
    val friends: List<String> = emptyList(),
    val requests: List<String> = emptyList(),
    val blocked: Set<String> = emptySet(),
    val favorites: Set<String> = emptySet(),
    val points: Map<String, Int> = emptyMap(),
    val giftDay: Map<String, Long> = emptyMap(),
    val cheerDay: Map<String, Long> = emptyMap(),
    val petDay: Map<String, Long> = emptyMap(),
    val receivedDay: Long = -1,
) {
    val seeded: Boolean get() = myCode.isNotEmpty()

    fun seed(rng: Random = Random.Default): FriendState = if (seeded) this else copy(
        myCode = newCode(rng),
        friends = FriendDirectory.STARTER_FRIENDS,
        requests = FriendDirectory.STARTER_REQUESTS,
        points = FriendDirectory.STARTER_FRIENDS.associateWith { POINTS_PER_LEVEL },
    )

    fun isFriend(code: String) = code in friends

    fun add(raw: String): Pair<FriendState, AddResult> {
        val code = FriendDirectory.normalize(raw)
        return when {
            code == myCode -> this to AddResult.SELF
            code in blocked -> this to AddResult.BLOCKED
            code in friends -> this to AddResult.ALREADY
            FriendDirectory.find(code) == null -> this to AddResult.NOT_FOUND
            friends.size >= MAX_FRIENDS -> this to AddResult.FULL
            else -> copy(friends = friends + code, requests = requests - code) to AddResult.ADDED
        }
    }

    fun accept(code: String): FriendState = if (code !in requests) this else add(code).first.copy(requests = requests - code)
    fun decline(code: String): FriendState = copy(requests = requests - code)
    fun remove(code: String): FriendState = copy(friends = friends - code, favorites = favorites - code)
    fun block(code: String): FriendState = remove(code).copy(blocked = blocked + code, requests = requests - code)
    fun unblock(code: String): FriendState = copy(blocked = blocked - code)
    fun toggleFavorite(code: String): FriendState = copy(favorites = if (code in favorites) favorites - code else favorites + code)

    fun pointsOf(code: String): Int = points[code] ?: 0
    fun friendshipLevel(code: String): Int = (1 + pointsOf(code) / POINTS_PER_LEVEL).coerceAtMost(MAX_FRIENDSHIP)
    private fun bump(code: String, by: Int) = points + (code to pointsOf(code) + by)

    fun canGift(code: String, day: Long) = isFriend(code) && giftDay[code] != day
    fun canCheer(code: String, day: Long) = isFriend(code) && cheerDay[code] != day
    fun canPet(code: String, day: Long) = isFriend(code) && petDay[code] != day

    fun gift(code: String, day: Long): FriendState? = if (!canGift(code, day)) null else copy(giftDay = giftDay + (code to day), points = bump(code, 2))

    fun cheer(code: String, day: Long): FriendState? = if (!canCheer(code, day)) null else copy(cheerDay = cheerDay + (code to day), points = bump(code, 1))

    fun pet(code: String, day: Long): Pair<FriendState, Int>? = if (!canPet(code, day)) null else copy(petDay = petDay + (code to day), points = bump(code, 1)) to PET_COINS

    fun giftItem(code: String): FriendState = if (!isFriend(code)) this else copy(points = bump(code, 5))

    fun incomingGifts(day: Long): List<String> = if (receivedDay == day) emptyList() else
        friends.filter { c -> FriendDirectory.find(c)?.online == true && Math.floorMod(c.hashCode() + day, 2L) == 0L }

    fun claimGifts(day: Long): Pair<FriendState, Int>? {
        val from = incomingGifts(day)
        if (from.isEmpty()) return null
        return copy(receivedDay = day) to from.size * GIFT_COINS
    }

    companion object {
        const val MAX_FRIENDS = 100
        const val GIFT_COINS = 5
        const val PET_COINS = 2
        const val POINTS_PER_LEVEL = 10
        const val MAX_FRIENDSHIP = 10
        private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        fun newCode(rng: Random): String = (1..8).map { CODE_CHARS[rng.nextInt(CODE_CHARS.length)] }.joinToString("")
    }
}
