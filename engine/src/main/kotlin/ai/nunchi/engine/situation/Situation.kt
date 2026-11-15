package ai.nunchi.engine.situation

import ai.nunchi.engine.Message
import ai.nunchi.engine.Priority

enum class Situation(
    val text: String,
    val priority: Priority = Priority.SUGGESTION,
    val question: Boolean = false,
    val accept: String? = null,
    val ttlMs: Long = 3 * 60_000,
) {
    NEW_YEAR("새해 복 많이 받으세요! 올해도 안전하게!"),
    CHRISTMAS("메리 크리스마스! 오늘은 어디 가세요?", question = true, accept = FUN_DAY),
    MONDAY("월요일 아침이에요. 커피 한 잔 사 갈까요?", question = true, accept = ASK_ASSISTANT_CAFE),
    FRIDAY("금요일 저녁이에요! 이번 주도 고생했어요."),
    WEEKEND("주말이에요! 오늘은 어디 가세요?", question = true, accept = FUN_DAY),
    AUTUMN("단풍 구경 가기 딱 좋은 날씨예요."),
    MORNING("좋은 아침이에요! 오늘도 힘내요."),
    FIRST_DRIVE("오늘 첫 운행이에요. 안전벨트 확인!"),

    LUNCH("곧 점심시간이에요. 근처 맛집 찾아볼까요?", question = true, accept = ASK_ASSISTANT_FOOD),
    DINNER("슬슬 저녁 시간이에요. 저녁은 어떻게 하세요?", question = true, accept = "좋아요, 맛있게 드세요!"),
    AFTERNOON("오후엔 졸리기 쉬워요. 커피 한 잔 할까요?", question = true, accept = ASK_ASSISTANT_CAFE),
    LATE_NIGHT("밤이 늦었어요. 졸리면 꼭 말해 주세요."),
    DAWN("새벽 운전이네요. 쉬엄쉬엄 가요."),

    RAIN("비가 오네요. 앞차와 거리 넉넉히요."),
    HEAVY_RAIN("비가 많이 와요. 천천히 가요.", priority = Priority.URGENT, ttlMs = 30_000),
    RAIN_STOPPED("비가 그쳤어요. 고생하셨어요!"),
    HIGHWAY_IN("고속도로예요. 집중하시게 조용히 갈게요."),
    HIGHWAY_OUT("고속도로 끝! 이제 시내 길이에요."),
    JAM("길이 막히네요. 음악 들으며 천천히 가요."),
    JAM_CLEARED("이제 좀 뚫렸어요. 다행이에요!"),
    LONG_RED("신호가 기네요. 어깨 한번 쭉 펴요."),

    AFTER_HARD_BRAKE("아까 놀라셨죠? 이제 괜찮아요."),
    TOO_FAST("아까 조금 빨랐어요. 천천히 가도 돼요."),
    DISTANCE("우리 함께 {km}km 달렸어요. 고마워요!", ttlMs = 30 * 60_000),
    EASYGOING("요즘 운전이 한결 느긋해졌어요.", ttlMs = 30 * 60_000),
    ADVENTURER("고속도로 좋아하시죠? 모험가 체질이에요!", ttlMs = 30 * 60_000),
    LEVEL_UP("레벨 {N} 달성! 우리 같이 크고 있어요.", ttlMs = 30 * 60_000),
    AFTER_BUSY("아까 복잡했죠? 잘 빠져나왔어요."),
    SILENCE("조용하네요. 노래 한 곡 틀어 볼까요?", question = true, accept = "좋아요. 음악은 미디어 앱에서 틀어 주세요."),

    ROUTE_STARTED("{목적지}까지 약 {분}분이에요. 출발해요!", priority = Priority.NORMAL),
    MEAL_AT_ARRIVAL("도착하면 딱 {끼니}때예요. 맛집 찾아볼까요?", question = true, accept = ASK_ASSISTANT_FOOD, ttlMs = 10 * 60_000),
    TEN_MINUTES_LEFT("{목적지}까지 10분쯤 남았어요.", priority = Priority.NORMAL, ttlMs = 2 * 60_000),
    ARRIVED("도착했어요! 오늘도 수고했어요.", priority = Priority.NORMAL),

    FIRST_LONG_TRIP("첫 장거리 완주! 정말 대단해요.", priority = Priority.NORMAL),
    PARKED_AFTER_ARRIVAL("주차 완료! 두고 내리는 물건 없죠?"),

    FIRST_VISIT("{지역은} 처음 와 보네요! 어떤 곳일까요?"),
    REVISIT("{지역}, {날짜}에 왔었죠? 오랜만이에요."),
    FREQUENT_VISIT("여기 벌써 {N}번째예요. 단골이네요!"),
    FAMOUS_SIGHT("{지역은} {명소가} 유명하대요. 들러 볼까요?", question = true, accept = ASK_ASSISTANT_STOP),
    LOCAL_FOOD("{지역}에 왔으니 {음식} 어때요? 찾아볼까요?", question = true, accept = ASK_ASSISTANT_FOOD),
    NEW_PROVINCE("이제 {도예요}. 꽤 멀리 왔네요!"),

    NO_ANSWER("바쁘시군요. 나중에 다시 여쭐게요.", ttlMs = 60_000);

    fun message(nowMs: Long, values: Map<String, String> = emptyMap()) = Message(
        id = "$PREFIX${name.lowercase()}-$nowMs",
        text = values.entries.fold(text) { t, (k, v) -> t.replace("{$k}", v) },
        priority = priority,
        createdAtMs = nowMs,
        ttlMs = ttlMs,
    )

    companion object {
        private const val PREFIX = "sit-"

        fun of(m: Message): Situation? {
            if (!m.id.startsWith(PREFIX)) return null
            val key = m.id.removePrefix(PREFIX).substringBeforeLast('-').uppercase()
            return entries.firstOrNull { it.name == key }
        }

        fun asks(m: Message): Boolean = of(m)?.question == true
    }
}

private const val ASK_ASSISTANT_FOOD = "좋아요. 맛집 찾기는 음성 비서에게 부탁해 주세요."
private const val ASK_ASSISTANT_CAFE = "좋아요. 카페 찾기는 음성 비서에게 부탁해 주세요."
private const val FUN_DAY = "좋아요, 즐거운 하루 보내요!"
private const val ASK_ASSISTANT_STOP = "좋아요. 경유지 추가는 음성 비서에게 부탁해 주세요."
