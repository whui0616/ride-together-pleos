package ai.nunchi.mate.demo

import ai.nunchi.engine.CompanionProfile
import ai.nunchi.engine.Message
import ai.nunchi.engine.Priority
import ai.nunchi.engine.suggest.Suggestion
import ai.nunchi.engine.suggest.SuggestionKind
import ai.nunchi.mate.avatar.Personality
import ai.nunchi.engine.chat.ChatIntent
import ai.nunchi.engine.chat.ChatQuestion
import ai.nunchi.engine.situation.Situation
import ai.nunchi.mate.signals.Destination
import java.time.LocalTime

object DemoContent {
    @Volatile var personality: Personality = Personality.ENERGETIC
    private var seq = 0
    private fun nextId(kind: String) = "$kind-${++seq}"

    private val chats = listOf(
        "민지" to "몇 시쯤 도착해?",
        "엄마" to "올 때 우유 하나만 사 와",
        "팀장님" to "내일 회의 10시로 당겨졌어요",
    )
    private var chatIndex = 0

    fun incomingChat(nowMs: Long, etaMinutes: Long = 25): Message {
        val (who, text) = chats[chatIndex++ % chats.size]
        val eta = LocalTime.now().plusMinutes(etaMinutes)
        val reply = if (text.contains("도착")) " 지금 경로로 ${eta.hour}시 ${eta.minute}분 도착 예정이에요. 답장 보낼까요?" else ""
        return Message(
            id = nextId("chat"),
            text = "아까 $who 님이 \"$text\" 라고 보냈어요.$reply",
            shortText = "$who 님 메시지가 와 있어요.",
            priority = Priority.NORMAL,
            createdAtMs = nowMs,
        )
    }

    fun driveThruSuggestion(nowMs: Long) = Message(
        id = nextId("food"),
        text = "꽤 막히네요. 1.2킬로미터 앞 오른쪽에 햄버거 드라이브스루가 있는데, 들렀다 갈까요?",
        shortText = "앞에 드라이브스루가 있어요. 들를까요?",
        priority = Priority.SUGGESTION,
        createdAtMs = nowMs,
    )

    fun asksQuestion(m: Message, spoken: String = m.text) =
        m.id.startsWith("food") || (m.id.startsWith("chat") && m.text.endsWith("?")) || ChatQuestion.asks(m, spoken) ||
            Situation.asks(m)

    fun accepted(question: Message, nowMs: Long): Message = Situation.of(question)?.accept?.let { ack(it, nowMs) }
        ?: when (ChatQuestion.intentOf(question)) {
        ChatIntent.SUGGEST_STOP ->
            if (question.text.contains("드라이브스루")) orderConfirmed(nowMs) else ack("좋아요. 경유지 추가는 음성 비서에게 부탁해 주세요.", nowMs)
        ChatIntent.REPLY_MESSAGE -> ack("알겠어요. 답장 보냈어요.", nowMs)
        else -> if (question.id.startsWith("food")) orderConfirmed(nowMs) else replySent(nowMs)
    }

    private fun ack(text: String, nowMs: Long) = Message(
        id = nextId("ack"),
        text = text,
        priority = Priority.NORMAL,
        createdAtMs = nowMs,
    )

    fun declined(nowMs: Long) = Message(
        id = nextId("ack"),
        text = "알겠어요. 필요하면 언제든 불러 주세요.",
        priority = Priority.NORMAL,
        createdAtMs = nowMs,
    )

    fun orderConfirmed(nowMs: Long) = Message(
        id = nextId("order"),
        text = "좋아요! 지난번처럼 불고기버거 세트로 미리 주문해 둘게요. 4분 뒤 도착이고 픽업 번호는 27번이에요.",
        shortText = "불고기버거 세트 주문했어요. 픽업 27번이에요.",
        priority = Priority.NORMAL,
        createdAtMs = nowMs,
    )

    private fun replySent(nowMs: Long): Message {
        val eta = LocalTime.now().plusMinutes(25)
        return Message(
            id = nextId("reply"),
            text = "\"${eta.hour}시 ${eta.minute}분쯤 도착해!\" 라고 답장 보냈어요.",
            priority = Priority.NORMAL,
            createdAtMs = nowMs,
        )
    }

    val destinations = listOf(
        Destination("42dot", 37.48544722, 127.03636666, "36khp37hh8gqcm5nqhe5", "서울시 강남구 남부순환로 2621"),
        Destination("판교 현대백화점", 37.41275833, 127.09481944, "36khp2hdj2cfcm5nqhe3", "경기도 성남시 수정구 창업로0번길 6"),
    )

    fun candidate(kind: SuggestionKind, nowMs: Long): Suggestion = when (kind) {
        SuggestionKind.MEAL -> Suggestion(
            id = nextId("meal"), kind = kind, placeName = "교동반점",
            lat = 37.7556, lng = 128.8961, distanceAheadM = 2_000.0, requiresReply = true,
            script = Lines.suggest(kind, personality, "교동반점", "2km"), createdAtMs = nowMs,
        )
        SuggestionKind.DRIVE_THRU -> Suggestion(
            id = nextId("thru"), kind = kind, placeName = "드라이브스루 카페",
            lat = 37.4127, lng = 127.0948, distanceAheadM = 1_500.0, requiresReply = true,
            script = Lines.suggest(kind, personality, "드라이브스루 카페", "1.5km"), createdAtMs = nowMs,
        )
        SuggestionKind.REST -> Suggestion(
            id = nextId("rest"), kind = kind, placeName = "휴게소",
            lat = 37.6, lng = 128.5, distanceAheadM = 3_000.0, requiresReply = true,
            script = Lines.suggest(kind, personality, "휴게소", "3km"), createdAtMs = nowMs,
        )
        SuggestionKind.CHARGE -> Suggestion(
            id = nextId("charge"), kind = kind, placeName = "급속 충전소",
            lat = 37.5, lng = 127.1, distanceAheadM = 4_000.0, requiresReply = true,
            script = Lines.suggest(kind, personality, "급속 충전소", "4km"), createdAtMs = nowMs,
        )
        SuggestionKind.STORY -> Suggestion(
            id = nextId("story"), kind = kind, placeName = "강릉",
            lat = 37.75, lng = 128.87, distanceAheadM = 1_000.0, requiresReply = false,
            script = Lines.suggest(kind, personality, "강릉", ""), createdAtMs = nowMs,
        )
        SuggestionKind.ROUTE -> Suggestion(
            id = nextId("route"), kind = kind, placeName = "판교 현대백화점",
            lat = 37.41275833, lng = 127.09481944, distanceAheadM = 2_500.0, requiresReply = true,
            script = Lines.suggest(kind, personality, "판교 현대백화점", ""), createdAtMs = nowMs,
        )
    }

    fun suggestionAccepted(s: Suggestion, nowMs: Long) = Message(
        id = nextId("ack"),
        text = if (s.kind == SuggestionKind.STORY) "" else Lines.accepted(personality, s.placeName),
        priority = Priority.NORMAL,
        createdAtMs = nowMs,
    )

    fun suggestionDeclined(nowMs: Long) = Message(
        id = nextId("ack"), text = Lines.declined(personality), priority = Priority.NORMAL, createdAtMs = nowMs,
    )

    fun suggestionLater(nowMs: Long) = Message(
        id = nextId("ack"), text = Lines.later(personality), priority = Priority.NORMAL, createdAtMs = nowMs,
    )

    private val stories = listOf(
        "지금 지나는 성수동은 예전에 구두 공장과 창고가 모여 있던 곳이에요. 요즘은 오래된 공장 건물을 고친 카페와 팝업 매장이 많아져서 주말마다 사람들로 붐빈대요.",
        "저 멀리 보이는 남산서울타워는 원래 방송 전파를 보내는 송신탑으로 세워졌어요. 1980년부터 전망대가 일반에 공개되면서 서울의 대표 명소가 됐죠.",
    )
    private var storyIndex = 0

    fun localStory(nowMs: Long) = Message(
        id = nextId("story"),
        text = stories[storyIndex++ % stories.size],
        priority = Priority.SUGGESTION,
        createdAtMs = nowMs,
    )

    fun safetyNotice(nowMs: Long) = Message(
        id = nextId("urgent"),
        text = "500미터 앞 어린이보호구역이에요. 속도를 조금 줄여 주세요.",
        shortText = "앞에 어린이보호구역이에요.",
        priority = Priority.URGENT,
        createdAtMs = nowMs,
    )

    fun arrivalSummary(nowMs: Long, p: CompanionProfile) = Message(
        id = nextId("arrive"),
        text = Lines.arrival(personality, p.xp),
        priority = Priority.NORMAL,
        createdAtMs = nowMs,
    )
}
