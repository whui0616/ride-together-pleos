package ai.nunchi.engine

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

enum class TurnSignal { OFF, LEFT, RIGHT, HAZARD }

enum class WiperLevel { OFF, INTERMITTENT, LOW, HIGH }

data class DrivingSignals(
    val speedKmh: Float = 0f,
    val gear: Gear = Gear.UNKNOWN,
    val turnSignal: TurnSignal = TurnSignal.OFF,
    val wiper: WiperLevel = WiperLevel.OFF,
    val brakePressed: Boolean = false,
    val decelerationMps2: Float = 0f,
    val accelerationMps2: Float = 0f,
    val nextTurnDistanceM: Int? = null,
    val inCautionZone: Boolean = false,
    val cameraAlert: Boolean = false,
    val steeringDeg: Float = 0f,
) {
    val speedMps: Float get() = speedKmh / 3.6f
    val isParked: Boolean get() = gear == Gear.PARK
}

sealed interface SignalUpdate {
    data class Speed(val kmh: Float) : SignalUpdate
    data class GearChanged(val gear: Gear) : SignalUpdate
    data class Turn(val state: TurnSignal) : SignalUpdate
    data class Wiper(val level: WiperLevel) : SignalUpdate
    data class Brake(val pressed: Boolean) : SignalUpdate
    data class NextTurn(val distanceM: Int?) : SignalUpdate
    data class CautionZone(val active: Boolean) : SignalUpdate
    data class CameraAlert(val active: Boolean) : SignalUpdate
    data class Steering(val deg: Float) : SignalUpdate
}

class SignalStore(private val decelStaleMs: Long = 1_000) {
    private var signals = DrivingSignals()
    private var lastSpeedAtMs: Long? = null

    fun apply(update: SignalUpdate, nowMs: Long) {
        signals = when (update) {
            is SignalUpdate.Speed -> withSpeed(update.kmh, nowMs)
            is SignalUpdate.GearChanged -> signals.copy(gear = update.gear)
            is SignalUpdate.Turn -> signals.copy(turnSignal = update.state)
            is SignalUpdate.Wiper -> signals.copy(wiper = update.level)
            is SignalUpdate.Brake -> signals.copy(brakePressed = update.pressed)
            is SignalUpdate.NextTurn -> signals.copy(nextTurnDistanceM = update.distanceM)
            is SignalUpdate.CautionZone -> signals.copy(inCautionZone = update.active)
            is SignalUpdate.CameraAlert -> signals.copy(cameraAlert = update.active)
            is SignalUpdate.Steering -> signals.copy(steeringDeg = update.deg)
        }
    }

    fun snapshot(nowMs: Long): DrivingSignals {
        val at = lastSpeedAtMs
        return if (at != null && nowMs - at > decelStaleMs) signals.copy(decelerationMps2 = 0f, accelerationMps2 = 0f) else signals
    }

    private fun withSpeed(kmh: Float, nowMs: Long): DrivingSignals {
        val prevAt = lastSpeedAtMs
        val decel = if (prevAt != null && nowMs > prevAt) {
            ((signals.speedKmh - kmh) / 3.6f) / ((nowMs - prevAt) / 1000f)
        } else {
            0f
        }
        lastSpeedAtMs = nowMs
        return signals.copy(speedKmh = kmh, decelerationMps2 = decel.coerceAtLeast(0f), accelerationMps2 = (-decel).coerceAtLeast(0f))
    }
}
