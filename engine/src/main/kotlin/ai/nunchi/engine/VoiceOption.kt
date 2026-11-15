package ai.nunchi.engine

enum class VoiceTier(val label: String) {
    BASIC("기본"),
    YOUNG("어린 캐릭터"),
    MATURE("성숙한 어른"),
}

enum class VoiceEngine {
    PLEOS,

    RECORDED,
}

enum class VoiceOption(
    val id: String,
    val label: String,
    val female: Boolean,
    val tier: VoiceTier,
    val unlockLevel: Int,
    val engine: VoiceEngine = VoiceEngine.RECORDED,
) {
    EUNHYE("eunhye", "은혜", true, VoiceTier.BASIC, 1),
    SANGHYEOK("sanghyeok", "상혁", false, VoiceTier.BASIC, 1),
    DOYUN("doyun", "도윤", false, VoiceTier.YOUNG, 2),
    DOHYUN("dohyun", "도현", false, VoiceTier.YOUNG, 4),
    HAEUN("haeun", "하은", true, VoiceTier.YOUNG, 5),
    DAEUN("daeun", "다은", true, VoiceTier.YOUNG, 6),
    YUNA("yuna", "유나", true, VoiceTier.YOUNG, 7),
    JIYU("jiyu", "지유", true, VoiceTier.YOUNG, 8),
    SEOA("seoa", "서아", true, VoiceTier.YOUNG, 9),
    SEOYOON("seoyoon", "서윤", true, VoiceTier.MATURE, 10),
    EUNWOO("eunwoo", "은우", false, VoiceTier.MATURE, 12);

    val fallbackSpeaker: String get() = if (female) EUNHYE.id else SANGHYEOK.id

    fun isUnlocked(level: Int): Boolean = level >= unlockLevel

    val sample: String get() = "안녕하세요, $label 목소리로 함께할게요."

    val unlockLine: String get() = "새 목소리가 열렸어요! 이제 $label 목소리도 쓸 수 있어요."

    companion object {
        val DEFAULT = EUNHYE

        fun of(id: String?): VoiceOption = entries.firstOrNull { it.id == id } ?: DEFAULT

        fun of(id: String?, level: Int): VoiceOption = of(id).takeIf { it.isUnlocked(level) } ?: DEFAULT

        fun unlockedBetween(from: Int, to: Int): List<VoiceOption> = entries.filter { it.unlockLevel in (from + 1)..to }
    }
}
