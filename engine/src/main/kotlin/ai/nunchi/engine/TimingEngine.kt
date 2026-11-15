package ai.nunchi.engine

enum class Priority { URGENT, NORMAL, SUGGESTION }

data class Message(
    val id: String,
    val text: String,
    val priority: Priority,
    val createdAtMs: Long,
    val shortText: String? = null,
    val ttlMs: Long = defaultTtlMs(priority),
    val resumed: Boolean = false,
) {
    fun isExpired(nowMs: Long) = nowMs - createdAtMs > ttlMs

    companion object {
        fun defaultTtlMs(p: Priority): Long = when (p) {
            Priority.URGENT -> 30_000
            Priority.NORMAL -> 30 * 60_000
            Priority.SUGGESTION -> 5 * 60_000
        }
    }
}

sealed interface Decision {
    data object None : Decision
    data class Speak(val message: Message, val text: String) : Decision
    data class Interrupt(val message: Message) : Decision
}

data class EngineState(
    val breakdown: LoadBreakdown = LoadBreakdown(0, emptyList()),
    val score: Float = 0f,
    val zone: LoadZone = LoadZone.GREEN,
    val calmForMs: Long = 0,
    val pending: Int = 0,
    val speaking: Message? = null,
    val availableSec: Float = Float.POSITIVE_INFINITY,
)

class TimingEngine(
    private val calculator: LoadScoreCalculator = LoadScoreCalculator(),
    private val smoother: LoadSmoother = LoadSmoother(),
    private val calmHoldMs: Long = 3_000,
    private val resumePrefix: String = "아까 하던 얘기 마저 할게요. ",
) {
    private val queue = mutableListOf<Message>()
    private var speaking: Message? = null
    private var calmSinceMs: Long? = null

    var state = EngineState()
        private set

    fun submit(message: Message) {
        queue += message
        state = state.copy(pending = queue.size)
    }

    fun reset() {
        queue.clear()
        speaking = null
        calmSinceMs = null
        smoother.reset()
        state = EngineState()
    }

    fun onSpeechDone(messageId: String) {
        if (speaking?.id == messageId) speaking = null
        state = state.copy(speaking = speaking)
    }

    fun tick(signals: DrivingSignals, nowMs: Long, allowNewSpeech: Boolean = true): Decision {
        val breakdown = calculator.calculate(signals)
        val score = smoother.update(breakdown.score, nowMs)
        val zone = LoadZone.of(score, signals.isParked)
        if (zone == LoadZone.GREEN || zone == LoadZone.PARKED) {
            if (calmSinceMs == null) calmSinceMs = nowMs
        } else {
            calmSinceMs = null
        }
        val calmForMs = calmSinceMs?.let { nowMs - it } ?: 0
        queue.removeAll { it.isExpired(nowMs) }

        val decision = decide(signals, zone, calmForMs, allowNewSpeech)
        state = EngineState(
            breakdown = breakdown,
            score = score,
            zone = zone,
            calmForMs = calmForMs,
            pending = queue.size,
            speaking = speaking,
            availableSec = SpeechWindow.availableSec(signals),
        )
        return decision
    }

    private fun decide(signals: DrivingSignals, zone: LoadZone, calmForMs: Long, allowNewSpeech: Boolean): Decision {
        val current = speaking
        if (current != null) {
            if (mayContinue(current, zone)) return Decision.None
            speaking = null
            queue += current.copy(resumed = true)
            return Decision.Interrupt(current)
        }
        if (!allowNewSpeech) return Decision.None

        val calm = zone == LoadZone.PARKED || (zone == LoadZone.GREEN && calmForMs >= calmHoldMs)
        val candidates = queue.sortedWith(compareBy<Message> { it.priority }.thenBy { it.createdAtMs })
        for (m in candidates) {
            val text = pickText(m, zone, calm, signals) ?: continue
            queue.remove(m)
            speaking = m
            return Decision.Speak(m, text)
        }
        return Decision.None
    }

    private fun mayContinue(m: Message, zone: LoadZone): Boolean = when (m.priority) {
        Priority.URGENT -> zone != LoadZone.RED
        else -> zone == LoadZone.GREEN || zone == LoadZone.PARKED
    }

    private fun pickText(m: Message, zone: LoadZone, calm: Boolean, s: DrivingSignals): String? {
        val prefix = if (m.resumed) resumePrefix else ""
        val full = prefix + m.text
        val short = m.shortText?.let { prefix + it }
        return when (m.priority) {
            Priority.URGENT -> if (zone == LoadZone.RED) null else short ?: full
            else -> when {
                !calm -> null
                SpeechWindow.fits(full, s) -> full
                short != null && SpeechWindow.fits(short, s) -> short
                else -> null
            }
        }
    }
}
