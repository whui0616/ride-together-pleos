package ai.nunchi.mate.avatar

import ai.nunchi.mate.ui.CharacterLook
import android.content.Context

enum class Gender(val label: String, val defaultHair: String) {
    MALE("남", "0"), FEMALE("여", "0");
    val defaultLook: CharacterLook get() = CharacterLook(hair = defaultHair, hairColor = if (this == MALE) "brown" else "black")
}

enum class Personality(val title: String, val mbtiHint: String, val description: String, val greeting: String) {
    ENERGETIC("활발", "ENFP 느낌", "먼저 말 걸고 작은 일에도 신나 해요.", "오늘도 같이 가요! 어디로 가요?"),
    CALM("차분", "ISFJ 느낌", "말수가 적고 꼭 필요한 말만 조용히 해요.", "안전하게 다녀와요."),
    PLAYFUL("장난꾸러기", "ESTP 느낌", "농담을 좋아하고 가끔 놀려요.", "또 늦었죠? 그래도 같이 가 줄게요."),
    WARM("다정", "INFJ 느낌", "피곤한지 먼저 살피고 챙겨 줘요.", "오늘 많이 피곤하죠. 천천히 가요."),
    COOL("쿨", "INTJ 느낌", "감정 표현은 적지만 할 일은 확실히 해요.", "경로 확인했어요. 출발해요."),
}

data class Avatar(
    val gender: Gender = Gender.MALE,
    val look: CharacterLook = Gender.MALE.defaultLook,
    val personality: Personality = Personality.ENERGETIC,
    val name: String = "버디",
)

class AvatarStore(context: Context) {
    private val prefs = context.getSharedPreferences("avatar", Context.MODE_PRIVATE)

    var created: Boolean
        get() = prefs.getBoolean("created", false)
        set(v) = prefs.edit().putBoolean("created", v).apply()

    var tutorialDone: Boolean
        get() = prefs.getBoolean("tutorialDone", false)
        set(v) = prefs.edit().putBoolean("tutorialDone", v).apply()

    var createdAtMs: Long
        get() = prefs.getLong("createdAt", 0L)
        set(v) = prefs.edit().putLong("createdAt", v).apply()

    fun load(): Avatar {
        val gender = enum(prefs.getString("gender", null), Gender.MALE)
        return Avatar(
            gender = gender,
            look = prefs.getString("look", null)?.let { CharacterLook.decode(it) } ?: gender.defaultLook,
            personality = enum(prefs.getString("personality", null), Personality.ENERGETIC),
            name = prefs.getString("name", "버디") ?: "버디",
        )
    }

    fun save(a: Avatar) {
        prefs.edit()
            .putString("gender", a.gender.name)
            .putString("look", a.look.encode())
            .putString("personality", a.personality.name)
            .putString("name", a.name)
            .apply()
    }

    fun reset() = prefs.edit().clear().apply()

    private inline fun <reified T : Enum<T>> enum(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
