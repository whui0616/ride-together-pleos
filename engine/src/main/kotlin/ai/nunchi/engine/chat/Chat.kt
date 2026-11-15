package ai.nunchi.engine.chat

import ai.nunchi.engine.DrivingSignals
import ai.nunchi.engine.EngineState
import ai.nunchi.engine.LoadZone
import ai.nunchi.engine.Message
import ai.nunchi.engine.Persona
import ai.nunchi.engine.Priority

enum class ChatIntent(val wire: String) {
    SMALL_TALK("small_talk"),
    SUGGEST_STOP("suggest_stop"),
    REPLY_MESSAGE("reply_message"),
    ROUTE_INFO("route_info"),
    CAR_CONTROL("car_control"),
    DECLINE("decline");

    companion object {
        fun of(wire: String?): ChatIntent =
            entries.firstOrNull { it.wire == wire?.trim()?.lowercase() } ?: SMALL_TALK
    }
}

data class CarState(
    val zone: LoadZone,
    val speedKmh: Int,
    val nextTurnDistanceM: Int? = null,
) {
    val parked: Boolean get() = zone == LoadZone.PARKED

    val aiAllowed: Boolean get() = zone != LoadZone.RED

    fun summary(): String = buildString {
        append(if (parked) "정차 중(P)" else "주행 중 ${speedKmh}km/h")
        append(", 운전 부하 ")
        append(
            when (zone) {
                LoadZone.RED -> "높음"
                LoadZone.YELLOW -> "보통"
                LoadZone.GREEN, LoadZone.PARKED -> "낮음"
            },
        )
        nextTurnDistanceM?.let { append(", 다음 회전까지 ${it}m") }
    }

    companion object {
        fun of(state: EngineState, signals: DrivingSignals) =
            CarState(state.zone, signals.speedKmh.toInt(), signals.nextTurnDistanceM)
    }
}

data class ChatRequest(
    val utterance: String,
    val car: CarState,
    val persona: Persona = Persona.BUDDY,
)

data class ChatReply(
    val line: String,
    val shortLine: String?,
    val intent: ChatIntent,
) {
    fun toMessage(id: String, nowMs: Long) = Message(
        id = id,
        text = line,
        shortText = shortLine,
        priority = Priority.NORMAL,
        createdAtMs = nowMs,
        ttlMs = TTL_MS,
    )

    companion object {
        const val TTL_MS = 60_000L
    }
}
