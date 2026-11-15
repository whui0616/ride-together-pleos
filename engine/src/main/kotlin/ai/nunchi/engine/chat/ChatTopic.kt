package ai.nunchi.engine.chat

import ai.nunchi.engine.Persona
import ai.nunchi.engine.control.CarLines

enum class ChatTopic(val wire: String, val intent: ChatIntent, val hint: String) {
    FOOD("food", ChatIntent.SUGGEST_STOP, "배고픔, 식사, 커피, 간식, 맛집"),
    REST("rest", ChatIntent.SUGGEST_STOP, "졸림, 피곤함, 휴식, 화장실, 휴게소"),
    ROUTE("route", ChatIntent.ROUTE_INFO, "도착 시간, 남은 거리, 경로"),
    MESSAGE("message", ChatIntent.REPLY_MESSAGE, "문자, 카톡, 메시지, 답장, 연락"),
    BORED("bored", ChatIntent.SMALL_TALK, "심심함, 이야기, 노래, 놀이"),
    CONTROL("control", ChatIntent.DECLINE, "창문, 에어컨, 히터, 열선, 문, 시동 같은 차량 조작 부탁"),
    MOOD("mood", ChatIntent.SMALL_TALK, "기분, 스트레스, 슬픔, 짜증, 힘듦"),
    GREETING("greeting", ChatIntent.SMALL_TALK, "인사, 고마움, 칭찬"),
    OTHER("other", ChatIntent.SMALL_TALK, "그 밖의 말"),
    ;

    companion object {
        fun of(wire: String?): ChatTopic? = entries.firstOrNull { it.wire == wire?.trim()?.lowercase() }
    }
}

object TopicClassifier {
    fun byKeyword(utterance: String): ChatTopic? {
        val t = utterance.replace(" ", "")
        return KEYWORDS.firstOrNull { (_, words) -> words.any { it in t } }?.first
    }

    private val KEYWORDS: List<Pair<ChatTopic, List<String>>> = listOf(
        ChatTopic.CONTROL to listOf("창문", "에어컨", "히터", "문열", "문닫", "문좀", "트렁크", "시동", "선루프", "와이퍼켜", "와이퍼꺼", "열선"),
        ChatTopic.ROUTE to listOf("도착", "언제가", "얼마나걸", "얼마나남", "몇시", "남았", "몇분", "몇킬로"),
        ChatTopic.MESSAGE to listOf("메시지", "문자", "카톡", "답장", "연락"),
        ChatTopic.REST to listOf("졸려", "졸리", "졸음", "피곤", "눈꺼풀", "하품", "눈이감", "눈감겨", "쉬고", "쉬자", "쉬었다", "휴게소", "화장실", "잠와", "잠이와"),
        ChatTopic.FOOD to listOf("배고", "배가고", "출출", "밥", "먹을", "먹고", "맛집", "커피", "간식", "햄버거"),
        ChatTopic.BORED to listOf("심심", "이야기", "얘기", "재밌", "재미있", "노래", "퀴즈"),
        ChatTopic.MOOD to listOf("우울", "슬퍼", "슬프", "짜증", "화나", "스트레스", "힘들", "속상", "기분"),
        ChatTopic.GREETING to listOf("안녕", "고마", "감사", "잘했", "최고", "반가"),
    )
}

object TopicLines {
    fun reply(topic: ChatTopic, persona: Persona, driving: Boolean, etaText: String? = null): ChatReply {
        if (topic == ChatTopic.ROUTE) {
            return if (etaText != null) ChatReply("지금 경로로 ${etaText}쯤 도착할 것 같아요.", "${etaText}쯤 도착해요.", topic.intent)
            else ChatReply("경로 안내를 시작하면 도착 시간을 알려 드릴게요.", null, topic.intent)
        }
        val (d, p) = LINES.getValue(topic).getValue(persona)
        return ChatReply(if (driving) d else p, SHORT[topic], topic.intent)
    }

    private val SHORT = mapOf(
        ChatTopic.FOOD to "뭐 좀 드시고 갈까요?",
        ChatTopic.REST to "잠깐 쉬었다 갈까요?",
        ChatTopic.CONTROL to CarLines.UNCLEAR,
    )

    private val LINES: Map<ChatTopic, Map<Persona, Pair<String, String>>> = mapOf(
        ChatTopic.FOOD to mapOf(
            Persona.BUDDY to ("배고프시죠? 가까운 식당이나 휴게소 찾아볼까요?" to "배고플 땐 든든하게 먹어야죠! 근처 식당이나 휴게소를 찾아볼까요?"),
            Persona.EASYGOING to ("천천히 쉬면서 뭐 좀 드시고 갈까요?" to "서두를 것 없어요. 근처에서 따뜻한 한 끼 드시고 갈까요?"),
            Persona.ADVENTURER to ("출출하죠? 이 동네 맛집 찾아볼까요?" to "새로운 동네엔 새로운 맛이 있죠! 근처 맛집을 찾아볼까요?"),
        ),
        ChatTopic.REST to mapOf(
            Persona.BUDDY to ("졸리면 위험해요. 다음 휴게소에서 쉬어 갈까요?" to "많이 피곤하시죠? 잠깐 눈 붙이고 기지개 켜면 훨씬 나아요. 여기서 쉬었다 갈까요?"),
            Persona.EASYGOING to ("조금 쉬었다 가요. 다음 쉼터에 들를까요?" to "차 세운 김에 푹 쉬어요. 바람 좀 쐬고 천천히 출발할까요?"),
            Persona.ADVENTURER to ("충전이 필요해요! 다음 휴게소에서 쉴까요?" to "모험가도 쉬어야 멀리 가요. 잠깐 쉬었다 다시 출발할까요?"),
        ),
        ChatTopic.MESSAGE to mapOf(
            Persona.BUDDY to ("메시지는 운전이 한가할 때 읽어 드릴게요." to "새 메시지가 오면 운전이 한가할 때 제가 먼저 알려 드릴게요."),
            Persona.EASYGOING to ("메시지는 여유 있을 때 천천히 알려 드릴게요." to "메시지가 오면 서두르지 않게, 여유 있을 때 알려 드릴게요."),
            Persona.ADVENTURER to ("메시지는 직진 구간에서 알려 드릴게요!" to "메시지가 오면 길이 편할 때 바로 알려 드릴게요!"),
        ),
        ChatTopic.BORED to mapOf(
            Persona.BUDDY to ("심심하시죠? 차 세우면 재밌는 얘기 해 드릴게요." to "심심할 땐 이야기가 최고죠! 이 동네 재밌는 이야기 하나 해 드릴까요?"),
            Persona.EASYGOING to ("창밖 풍경 보면서 가요. 쉴 때 이야기해 드릴게요." to "잠깐 쉬는 동안 이 동네 이야기 하나 천천히 들려 드릴까요?"),
            Persona.ADVENTURER to ("다음에 쉴 때 신기한 이야기 들려 드릴게요!" to "이 동네에 숨은 이야기가 있대요! 하나 들려 드릴까요?"),
        ),
        ChatTopic.CONTROL to Persona.entries.associateWith {
            CarLines.UNCLEAR to "히터, 에어컨, 창문, 열선, 선루프는 말씀만 하시면 바꿔 드려요. 문이나 시동은 직접 해 주세요."
        },
        ChatTopic.MOOD to mapOf(
            Persona.BUDDY to ("제가 옆에 있잖아요. 천천히 같이 가요." to "힘든 날이었나 봐요. 오늘은 무리하지 말고, 좋아하는 노래 들으면서 천천히 가요."),
            Persona.EASYGOING to ("괜찮아요. 숨 한번 크게 쉬고 천천히 가요." to "괜찮아요, 그런 날도 있죠. 잠깐 숨 고르고 천천히 가요. 저는 늘 여기 있어요."),
            Persona.ADVENTURER to ("기분 전환엔 드라이브가 최고죠! 힘내요." to "기분이 가라앉을 땐 새로운 길이 약이에요. 다음엔 가 본 적 없는 곳으로 가 봐요!"),
        ),
        ChatTopic.GREETING to mapOf(
            Persona.BUDDY to ("저도 함께라서 좋아요. 오늘도 같이 가요!" to "저도 함께라서 좋아요. 오늘도 든든하게 옆에 있을게요!"),
            Persona.EASYGOING to ("고마워요. 오늘도 천천히, 즐겁게 가요." to "고마워요. 오늘도 풍경 보면서 천천히, 즐겁게 가요."),
            Persona.ADVENTURER to ("좋아요! 오늘도 신나게 달려 봐요!" to "좋아요! 오늘은 또 어떤 길이 기다릴지 기대돼요!"),
        ),
        ChatTopic.OTHER to Persona.entries.associateWith {
            "네, 듣고 있어요. 천천히 가요." to "네, 듣고 있어요. 저는 쉬어 갈 곳, 먹을 곳, 도착 시간 같은 걸 도와드릴 수 있어요."
        },
    )
}
