package ai.nunchi.mate.voice

enum class VoiceStyle(
    val label: String,
    val pitch: Float,
    val rate: Float,
) {
    SOFT("포근하게", 1.08f, 0.92f),
    BRIGHT("발랄하게", 1.18f, 1.05f),
    CALM("차분하게", 0.92f, 0.90f),
}
