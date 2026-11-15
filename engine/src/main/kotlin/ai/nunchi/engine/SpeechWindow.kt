package ai.nunchi.engine

object SpeechWindow {
    const val SEC_PER_CHAR = 0.13f
    const val SAFETY_MARGIN_M = 150
    const val BUFFER_SEC = 2f
    private const val CRAWL_MPS = 1f

    fun estimateSec(text: String): Float = text.count { !it.isWhitespace() } * SEC_PER_CHAR

    fun availableSec(s: DrivingSignals): Float {
        val turn = s.nextTurnDistanceM ?: return Float.POSITIVE_INFINITY
        if (s.isParked || s.speedMps < CRAWL_MPS) return Float.POSITIVE_INFINITY
        return ((turn - SAFETY_MARGIN_M) / s.speedMps).coerceAtLeast(0f)
    }

    fun fits(text: String, s: DrivingSignals): Boolean = estimateSec(text) + BUFFER_SEC <= availableSec(s)
}
