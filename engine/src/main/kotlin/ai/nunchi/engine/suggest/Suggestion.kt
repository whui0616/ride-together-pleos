package ai.nunchi.engine.suggest

enum class SuggestionKind { MEAL, DRIVE_THRU, REST, CHARGE, STORY, ROUTE }

enum class SuggestionState {
    CANDIDATE, QUEUED, SPOKEN, AWAITING_REPLY,
    ACCEPTED, REJECTED, LATER, EXPIRED_NO_REPLY, DISCARDED
}

data class Suggestion(
    val id: String,
    val kind: SuggestionKind,
    val placeName: String,
    val lat: Double,
    val lng: Double,
    val distanceAheadM: Double,
    val requiresReply: Boolean,
    val script: String,
    val createdAtMs: Long,
    val isOutdoor: Boolean = false,
)
