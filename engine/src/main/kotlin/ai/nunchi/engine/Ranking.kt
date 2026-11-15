package ai.nunchi.engine

data class RankAccount(
    val code: String,
    val name: String,
    val personality: String,
    val gender: String,
    val look: Map<String, String>,
    val background: String,
    val level: Int,
    val distanceKm: Int,
    val hearts: Int,
    val achTiers: Int,
    val honor: String? = null,
    val car: String = "",
)

data class RankEntry(val code: String?, val name: String, val level: Int, val distanceKm: Int, val hearts: Int, val achTiers: Int) {
    val isMe: Boolean get() = code == null
}

enum class RankBoard(val label: String) {
    LEVEL("전국 레벨"), DISTANCE("주행 거리"), HEARTS("받은 하트"), ACHIEVEMENTS("업적 해금");

    fun value(e: RankEntry): Int = when (this) {
        LEVEL -> e.level
        DISTANCE -> e.distanceKm
        HEARTS -> e.hearts
        ACHIEVEMENTS -> e.achTiers
    }

    fun format(e: RankEntry): String = when (this) {
        LEVEL -> "Lv.${e.level}"
        DISTANCE -> "%,d km".format(e.distanceKm)
        HEARTS -> "♥ %,d".format(e.hearts)
        ACHIEVEMENTS -> "업적 ${e.achTiers}개"
    }
}

object Ranking {
    private fun look(hair: Int, color: String, outfit: Int, skin: Int, eyes: Int, aura: String) =
        mapOf("hair" to "$hair", "hairColor" to color, "outfit" to "$outfit", "skin" to "$skin", "eyes" to "$eyes", "aura" to aura)

    val ACCOUNTS: List<RankAccount> = listOf(
        RankAccount("R01", "도로 위의 별", "COOL", "m", look(5, "black", 10, 2, 4, "cosmic"), "ROOFTOP", 58, 48_210, 1_204, 96, "전설", car = "아이오닉 6"),
        RankAccount("R02", "제주 한바퀴", "WARM", "f", look(4, "honey", 9, 1, 5, "water"), "SUMMER", 51, 61_530, 2_310, 88, "전국 탐험가", car = "아이오닉 5"),
        RankAccount("R03", "새벽 출근러", "CALM", "m", look(1, "brown", 8, 3, 0, "light"), "MORNING", 47, 39_870, 640, 71, car = "아이오닉 5"),
        RankAccount("R04", "벚꽃 드라이브", "PLAYFUL", "f", look(3, "pink", 4, 0, 7, "rainbow"), "SPRING", 44, 22_140, 3_051, 64, "패셔니스타", car = "캐스퍼 일렉트릭"),
        RankAccount("R05", "민지네 루미", "WARM", "f", look(4, "pink", 7, 1, 1, "wind"), "AUTUMN", 32, 18_920, 870, 52, "전국 탐험가", car = "아이오닉 5"),
        RankAccount("R06", "강릉 커피", "CALM", "m", look(2, "ash", 1, 2, 3, "ice"), "WINTER", 39, 27_400, 412, 60, car = "EV6"),
        RankAccount("R07", "주말엔 캠핑", "ENERGETIC", "m", look(4, "chestnut", 9, 4, 8, "nature"), "DAY", 36, 34_760, 958, 49, car = "EV9"),
        RankAccount("R08", "퇴근길 노을", "WARM", "f", look(1, "brown", 2, 2, 11, "fire"), "EVENING", 29, 15_330, 1_822, 45, car = "아이오닉 5"),
        RankAccount("R09", "준호의 루온", "COOL", "m", look(3, "black", 5, 1, 2, "shadow"), "NIGHT", 18, 9_870, 530, 33, "패셔니스타", car = "아이오닉 5 N"),
        RankAccount("R10", "초보운전 3개월", "PLAYFUL", "f", look(0, "honey", 0, 6, 6, "light"), "DAY", 9, 2_140, 2_684, 14, car = "캐스퍼 일렉트릭"),
        RankAccount("R11", "부산 갈매기", "ENERGETIC", "m", look(0, "navy", 11, 5, 12, "water"), "SUMMER", 41, 44_050, 377, 58, car = "EV6"),
        RankAccount("R12", "도윤이 차 친구", "CALM", "m", look(1, "brown", 10, 3, 9, "electric"), "GARAGE", 50, 52_880, 1_115, 90, "1년의 동행", car = "아이오닉 5"),
        RankAccount("R13", "서아의 몽이", "PLAYFUL", "f", look(3, "honey", 11, 0, 13, "rainbow"), "SPRING", 12, 4_310, 1_450, 21, car = "코나 일렉트릭"),
        RankAccount("R14", "한강 야경", "COOL", "f", look(5, "silver", 8, 7, 10, "shadow"), "ROOFTOP", 26, 12_760, 744, 40, car = "GV60"),
        RankAccount("R15", "하루", "ENERGETIC", "f", look(0, "black", 6, 8, 4, "electric"), "MORNING", 3, 610, 95, 6, car = "아이오닉 5"),
        RankAccount("R16", "전국 휴게소 도장", "WARM", "m", look(2, "chestnut", 12, 4, 5, "fire"), "AUTUMN", 45, 57_300, 1_966, 82, "전국 탐험가", car = "아이오닉 6"),
    )

    private val byCode = ACCOUNTS.associateBy { it.code }
    fun find(code: String): RankAccount? = byCode[code]

    fun entry(a: RankAccount, heartedByMe: Boolean = false) =
        RankEntry(a.code, a.name, a.level, a.distanceKm, a.hearts + if (heartedByMe) 1 else 0, a.achTiers)

    const val MY_CAR = "아이오닉 5"

    fun byCar(board: RankBoard, me: RankEntry, hearted: Set<String> = emptySet()): List<Pair<String, List<RankEntry>>> =
        ACCOUNTS.map { it.car }.distinct()
            .map { car -> car to board(board, me, hearted, car).filter { car == MY_CAR || !it.isMe } }
            .sortedWith(compareBy<Pair<String, List<RankEntry>>> { it.first != MY_CAR }.thenByDescending { it.second.size }.thenBy { it.first })

    fun board(board: RankBoard, me: RankEntry, hearted: Set<String> = emptySet(), car: String? = null): List<RankEntry> =
        (ACCOUNTS.filter { car == null || it.car == car }.map { entry(it, it.code in hearted) } + me)
            .sortedWith(compareByDescending<RankEntry> { board.value(it) }.thenByDescending { it.level }.thenBy { !it.isMe })
}
