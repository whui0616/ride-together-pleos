package ai.nunchi.engine

import kotlin.math.abs
import kotlin.math.pow

data class LoadWeights(
    val turnUnder100m: Int = 35,
    val turnUnder300m: Int = 25,
    val turnUnder700m: Int = 10,
    val cautionZone: Int = 20,
    val cameraAlert: Int = 15,
    val turnSignal: Int = 20,
    val wiperLow: Int = 10,
    val wiperHigh: Int = 20,
    val brake: Int = 10,
    val hardBrake: Int = 25,
    val hardBrakeMps2: Float = 3f,
    val highSpeed: Int = 10,
    val highSpeedKmh: Float = 100f,
    val congestionBonus: Int = -10,
    val congestionKmh: Float = 15f,
    val steering: Int = 10,
    val steeringDeg: Float = 90f,
    val steeringLarge: Int = 20,
    val steeringLargeDeg: Float = 180f,
)

data class LoadBreakdown(val score: Int, val contributions: List<Pair<String, Int>>)

class LoadScoreCalculator(private val w: LoadWeights = LoadWeights()) {

    fun calculate(s: DrivingSignals): LoadBreakdown {
        if (s.isParked) return LoadBreakdown(0, listOf("정차(P)" to 0))

        val parts = mutableListOf<Pair<String, Int>>()
        val turn = s.nextTurnDistanceM
        when {
            turn == null -> Unit
            turn < 100 -> parts += "회전 100m 이내" to w.turnUnder100m
            turn < 300 -> parts += "회전 300m 이내" to w.turnUnder300m
            turn < 700 -> parts += "회전 700m 이내" to w.turnUnder700m
        }
        if (s.inCautionZone) parts += "주의구간" to w.cautionZone
        if (s.cameraAlert) parts += "단속카메라" to w.cameraAlert
        if (s.turnSignal != TurnSignal.OFF) parts += "방향지시등" to w.turnSignal
        when (s.wiper) {
            WiperLevel.OFF -> Unit
            WiperLevel.INTERMITTENT, WiperLevel.LOW -> parts += "와이퍼" to w.wiperLow
            WiperLevel.HIGH -> parts += "와이퍼 고속" to w.wiperHigh
        }
        if (s.brakePressed) parts += "브레이크" to w.brake
        val steer = abs(s.steeringDeg)
        when {
            steer >= w.steeringLargeDeg -> parts += "큰 조향" to w.steeringLarge
            steer >= w.steeringDeg -> parts += "조향" to w.steering
        }
        if (s.decelerationMps2 >= w.hardBrakeMps2) parts += "급감속" to w.hardBrake
        if (s.speedKmh > w.highSpeedKmh) parts += "고속" to w.highSpeed
        val turnImminent = turn != null && turn < 300
        if (s.speedKmh < w.congestionKmh && !turnImminent) parts += "정체" to w.congestionBonus

        return LoadBreakdown(parts.sumOf { it.second }.coerceIn(0, 100), parts)
    }
}

class LoadSmoother(private val releaseHalfLifeMs: Long = 3_000) {
    private var value: Float? = null
    private var lastAtMs = 0L

    fun update(raw: Int, nowMs: Long): Float {
        val prev = value
        val next = if (prev == null || raw >= prev) {
            raw.toFloat()
        } else {
            val dt = (nowMs - lastAtMs).coerceAtLeast(0)
            val keep = 0.5.pow(dt.toDouble() / releaseHalfLifeMs).toFloat()
            raw + (prev - raw) * keep
        }
        value = next
        lastAtMs = nowMs
        return next
    }

    fun reset() {
        value = null
    }
}

enum class LoadZone {
    RED,
    YELLOW,
    GREEN,
    PARKED;

    companion object {
        const val RED_FROM = 60f
        const val YELLOW_FROM = 30f

        fun of(score: Float, parked: Boolean): LoadZone = when {
            parked -> PARKED
            score >= RED_FROM -> RED
            score >= YELLOW_FROM -> YELLOW
            else -> GREEN
        }
    }
}
