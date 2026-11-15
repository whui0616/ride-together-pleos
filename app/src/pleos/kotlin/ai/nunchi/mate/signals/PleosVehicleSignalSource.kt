package ai.nunchi.mate.signals

import ai.nunchi.engine.SignalUpdate
import ai.nunchi.engine.TurnSignal
import ai.nunchi.engine.WiperLevel
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs

class PleosVehicleSignalSource(private val context: Context) : SignalSource {
    override val name = "Pleos Vehicle (방향지시등·와이퍼·브레이크·조향)"

    private val main = Handler(Looper.getMainLooper())
    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private var vehicle: Vehicle? = null
    private var sink: ((SignalUpdate) -> Unit)? = null
    private var turnListener: TurnSignalStateListener? = null
    private var wiperListener: WiperStateListener? = null
    private var brakeListener: BrakePedalStatusListener? = null
    private var steeringListener: SteeringWheelAngleListener? = null
    private var wiperLevel: WiperLevel? = null
    private var loggedSteeringDeg: Float? = null

    override fun start(sink: (SignalUpdate) -> Unit) {
        stop()
        val v = Vehicle(context.applicationContext)
        v.initialize()
        vehicle = v
        this.sink = sink

        val turn = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = onTurn(state)
            override fun onFailed(e: Exception) = logFail("turn signal", e)
        }
        val wiper = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) {
                main.post { onWiperState(state) }
            }
            override fun onFailed(e: Exception) = logFail("wiper", e)
        }
        val brake = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(status: Boolean?) = onBrake(status)
            override fun onFailed(e: Exception) = logFail("brake", e)
        }
        val steering = object : SteeringWheelAngleListener {
            override fun onSteeringWheelAngleUpdated(angle: Float?) = onSteering(angle)
            override fun onFailed(e: Exception) = logFail("steering", e)
        }

        v.getTurnSignal().registerTurnSignalState(turn)
        v.getWiper().registerWiperState(windshield, wiper)
        v.getBrake().registerBrakePedalStatus(brake)
        v.getSteeringWheel().registerSteeringWheelAngle(steering)
        turnListener = turn
        wiperListener = wiper
        brakeListener = brake
        steeringListener = steering
    }

    override fun stop() {
        main.removeCallbacks(pollWiperSwitch)
        val v = vehicle ?: return
        turnListener?.let { v.getTurnSignal().unregisterTurnSignalState(it) }
        wiperListener?.let { v.getWiper().unregisterWiperState(windshield, it) }
        brakeListener?.let { v.getBrake().unregisterBrakePedalStatus(it) }
        steeringListener?.let { v.getSteeringWheel().unregisterSteeringWheelAngle(it) }
        v.release()
        vehicle = null
        sink = null
        wiperLevel = null
        loggedSteeringDeg = null
    }

    private fun onTurn(state: TurnSignalValue?) {
        Log.d(TAG, "turn signal = $state")
        sink?.invoke(SignalUpdate.Turn(turnOf(state)))
    }

    private fun onBrake(pressed: Boolean?) {
        Log.d(TAG, "brake pedal = $pressed")
        sink?.invoke(SignalUpdate.Brake(pressed == true))
    }

    private fun onSteering(angle: Float?) {
        val deg = angle ?: 0f
        val last = loggedSteeringDeg
        if (last == null || abs(deg - last) >= STEERING_LOG_STEP_DEG) {
            Log.d(TAG, "steering angle = $angle")
            loggedSteeringDeg = deg
        }
        sink?.invoke(SignalUpdate.Steering(deg))
    }

    private fun onWiperState(state: WiperState?) {
        Log.d(TAG, "wiper state = $state")
        main.removeCallbacks(pollWiperSwitch)
        if (state != WiperState.ON) {
            emitWiper(WiperLevel.OFF)
            return
        }
        pollWiperSwitch.run()
    }

    private val pollWiperSwitch = object : Runnable {
        override fun run() {
            val v = vehicle ?: return
            v.getWiper().getCurrentWiperSwitch(
                windshield,
                { sw -> main.post { emitWiper(levelOf(sw)) } },
                { e -> logFail("wiper switch", e); main.post { emitWiper(WiperLevel.LOW) } },
            )
            main.postDelayed(this, WIPER_POLL_MS)
        }
    }

    private fun emitWiper(level: WiperLevel) {
        if (level == wiperLevel) return
        Log.d(TAG, "wiper level = $level")
        wiperLevel = level
        sink?.invoke(SignalUpdate.Wiper(level))
    }

    private fun turnOf(v: TurnSignalValue?) = when (v) {
        TurnSignalValue.LEFT -> TurnSignal.LEFT
        TurnSignalValue.RIGHT -> TurnSignal.RIGHT
        else -> TurnSignal.OFF
    }

    private fun levelOf(sw: WiperSwitch?) = when (sw) {
        WiperSwitch.OFF -> WiperLevel.OFF
        WiperSwitch.CONTINUOUS_LEVEL_3, WiperSwitch.CONTINUOUS_LEVEL_4, WiperSwitch.CONTINUOUS_LEVEL_5 -> WiperLevel.HIGH
        WiperSwitch.CONTINUOUS_LEVEL_1, WiperSwitch.CONTINUOUS_LEVEL_2, null -> WiperLevel.LOW
        else -> WiperLevel.INTERMITTENT
    }

    private fun logFail(what: String, e: Exception) {
        Log.w(TAG, "$what failed", e)
    }

    private companion object {
        const val TAG = "PleosVehicle"
        const val WIPER_POLL_MS = 1_000L
        const val STEERING_LOG_STEP_DEG = 1f
    }
}
