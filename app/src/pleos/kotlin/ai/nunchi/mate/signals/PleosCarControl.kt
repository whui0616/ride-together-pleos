package ai.nunchi.mate.signals

import ai.nunchi.engine.control.CarAction
import ai.nunchi.engine.control.CarAmount
import ai.nunchi.engine.control.CarCommand
import ai.nunchi.engine.control.CarResult
import ai.nunchi.engine.control.CarZone
import ai.nunchi.mate.control.CarControl
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.roundToInt

class PleosCarControl(private val context: Context) : CarControl {
    override val name = "Pleos 차량 조작"

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "car-control").apply { isDaemon = true } }
    private var vehicle: Vehicle? = null
    private var lastCelsius: Int? = null
    private val caps = HashMap<String, Any>()

    init {
        worker.execute {
            val started = SystemClock.elapsedRealtime()
            for (attempt in 1..WARM_ATTEMPTS) try {
                val hvac = vehicle().getHVAC()
                cap<List<SeatArea>>("power") { ok, fail -> hvac.checkHvacPowerCapabilities({ l: List<SeatArea> -> ok(l) }, fail) }
                cap<List<SeatArea>>("ac") { ok, fail -> hvac.checkHvacAcCapabilities({ l: List<SeatArea> -> ok(l) }, fail) }
                cap<Map<SeatArea, AreaBounds<Float>>>("temperature") { ok, fail ->
                    hvac.checkHvacTemperatureCapabilities({ m: Map<SeatArea, AreaBounds<Float>> -> ok(m) }, fail)
                }
                warmOthers(vehicle!!)
                Log.i(TAG, "준비 ${SystemClock.elapsedRealtime() - started}ms (${attempt}번째)")
                break
            } catch (e: Exception) {
                Log.w(TAG, "준비 실패 ${attempt}번째: ${e.message}")
                Thread.sleep(WARM_RETRY_MS)
            }
        }
    }

    private fun warmOthers(v: Vehicle) {
        val hvac = v.getHVAC()
        val seat = v.getSeat()
        fun quiet(block: () -> Unit) = try { block() } catch (e: Exception) { Log.d(TAG, "미리 묻기 건너뜀: ${e.message}") }
        quiet { cap<Map<SeatArea, AreaBounds<Int>>>("seatHeat") { ok, fail -> seat.checkSeatTemperatureCapabilities({ m: Map<SeatArea, AreaBounds<Int>> -> ok(m) }, fail) } }
        quiet { cap<Map<SeatArea, AreaBounds<Int>>>("seatVent") { ok, fail -> seat.checkSeatVentilationCapabilities({ m: Map<SeatArea, AreaBounds<Int>> -> ok(m) }, fail) } }
        quiet { cap<Map<SeatArea, AreaBounds<Int>>>("fan") { ok, fail -> hvac.checkHvacFanSpeedCapabilities({ m: Map<SeatArea, AreaBounds<Int>> -> ok(m) }, fail) } }
        quiet { cap<List<WindowArea>>("defrost") { ok, fail -> hvac.checkHvacDefrosterCapabilities({ l: List<WindowArea> -> ok(l) }, fail) } }
        quiet { cap<List<SeatArea>>("recirc") { ok, fail -> hvac.checkHvacRecirculationCapabilities({ l: List<SeatArea> -> ok(l) }, fail) } }
        quiet { cap<AreaBounds<Int>>("wheel") { ok, fail -> v.getSteeringWheel().checkHvacSteeringWheelHeatCapabilities({ x: AreaBounds<Int> -> ok(x) }, fail) } }
        quiet { cap<Map<WindowArea, AreaBounds<Int>>>("window") { ok, fail -> v.getWindow().checkWindowPositionCapabilities({ m: Map<WindowArea, AreaBounds<Int>> -> ok(m) }, fail) } }
        quiet { cap<List<DoorArea>>("door") { ok, fail -> v.getDoor().checkDoorLockCapabilities({ l: List<DoorArea> -> ok(l) }, fail) } }
    }

    private fun vehicle(): Vehicle = vehicle ?: Vehicle(context.applicationContext).also { it.initialize(); vehicle = it }

    private fun <T : Any> cap(key: String, call: (ok: (T) -> Unit, fail: (Exception) -> Unit) -> Unit): T {
        @Suppress("UNCHECKED_CAST")
        caps[key]?.let { return it as T }
        return await(call).also { caps[key] = it }
    }

    override fun execute(command: CarCommand, onResult: (CarResult) -> Unit) {
        worker.execute {
            val started = SystemClock.elapsedRealtime()
            val result = try {
                run(command, vehicle())
            } catch (e: Exception) {
                Log.w(TAG, "${command.action} failed", e)
                CarResult(ok = false, detail = "${e.javaClass.simpleName}: ${e.message}")
            }
            Log.i(TAG, "${command.action} ${command.zone} ${command.amount} → ok=${result.ok} ${result.celsius ?: ""} " +
                "${SystemClock.elapsedRealtime() - started}ms ${result.detail}")
            onResult(result)
        }
    }

    override fun release() {
        worker.execute { vehicle?.release(); vehicle = null }
        worker.shutdown()
    }

    private fun run(c: CarCommand, v: Vehicle): CarResult {
        val hvac = v.getHVAC()
        fun power(on: Boolean) {
            val areas = cap<List<SeatArea>>("power") { ok, fail -> hvac.checkHvacPowerCapabilities({ l: List<SeatArea> -> ok(l) }, fail) }
            await<Unit> { ok, fail -> hvac.setHvacPower(pick(areas), on, { _, _ -> ok(Unit) }, fail) }
        }
        fun ac(on: Boolean) {
            val area = pick(cap("ac") { ok, fail -> hvac.checkHvacAcCapabilities({ l: List<SeatArea> -> ok(l) }, fail) })
            await<Unit> { ok, fail -> hvac.setHvacAc(area, on, { _, _ -> ok(Unit) }, fail) }
        }
        fun temperature(target: Int? = null, delta: Int = 0, atLeast: Int? = null): Int {
            val bounds = cap<Map<SeatArea, AreaBounds<Float>>>("temperature") { ok, fail ->
                hvac.checkHvacTemperatureCapabilities({ m: Map<SeatArea, AreaBounds<Float>> -> ok(m) }, fail)
            }
            val order = bounds.keys.sortedByDescending { SeatValue.ROW_1_LEFT in it.values }
            var last: Exception = IllegalStateException("지원하지 않는 기능")
            for (area in order) {
                try {
                    val now = try {
                        await<Float> { ok, fail -> hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, { t -> if (t != null) ok(t) else fail(IllegalStateException("온도 값 없음")) }, fail) }.roundToInt()
                    } catch (e: Exception) {
                        val base = lastCelsius ?: BASE_CELSIUS
                        Log.d(TAG, "온도 읽기 실패(${area.values.contentToString()}), ${base}도 기준: ${e.message}")
                        base
                    }
                    var want = target ?: (now + delta)
                    atLeast?.let { want = maxOf(want, it) }
                    val b = bounds.getValue(area)
                    val set = want.toFloat().coerceIn(maxOf(b.minValue, CarCommand.MIN_CELSIUS.toFloat()), minOf(b.maxValue, CarCommand.MAX_CELSIUS.toFloat()))
                    await<Unit> { ok, fail -> hvac.setHvacTemperature(area, set, TemperatureUnit.CELSIUS, { _, _ -> ok(Unit) }, fail) }
                    return set.roundToInt().also { lastCelsius = it }
                } catch (e: Exception) {
                    Log.d(TAG, "온도 영역 ${area.values.contentToString()} 실패: ${e.message}")
                    last = e
                }
            }
            throw last
        }
        val step = c.degrees ?: if (c.amount == CarAmount.LITTLE) 1 else CarCommand.STEP

        return when (c.action) {
            CarAction.HEATER_ON -> { power(true); ac(false); ok(temperature(atLeast = HEATER_MIN_CELSIUS)) }
            CarAction.AC_ON -> { power(true); ac(true); ok() }
            CarAction.AC_OFF -> { ac(false); ok() }
            CarAction.CLIMATE_OFF -> { power(false); ok() }
            CarAction.WARMER -> { power(true); ok(temperature(delta = step)) }
            CarAction.COOLER -> { power(true); ac(true); ok(temperature(delta = -step)) }
            CarAction.SET_TEMPERATURE -> { power(true); ok(temperature(target = c.degrees)) }
            CarAction.FAN_UP, CarAction.FAN_DOWN -> {
                power(true)
                val bounds = cap<Map<SeatArea, AreaBounds<Int>>>("fan") { ok, fail ->
                    hvac.checkHvacFanSpeedCapabilities({ m: Map<SeatArea, AreaBounds<Int>> -> ok(m) }, fail)
                }
                val area = pick(bounds.keys.toList())
                val b = bounds.getValue(area)
                val now = await<Int> { ok, fail -> hvac.getHvacFanSpeed(area, { s -> ok(s) }, fail) }
                val up = c.action == CarAction.FAN_UP
                val want = when {
                    c.amount == CarAmount.FULL -> if (up) b.maxValue else b.minValue
                    up -> now + 1
                    else -> now - 1
                }.coerceIn(b.minValue, b.maxValue)
                await<Unit> { ok, fail -> hvac.setHvacFanSpeed(area, want, { _, _ -> ok(Unit) }, fail) }
                ok()
            }
            CarAction.DEFROST_ON, CarAction.DEFROST_OFF -> {
                val areas = cap<List<WindowArea>>("defrost") { ok, fail -> hvac.checkHvacDefrosterCapabilities({ l: List<WindowArea> -> ok(l) }, fail) }
                val area = areas.firstOrNull { WindowValue.FRONT_WINDSHIELD in it.values } ?: areas.first()
                await<Unit> { ok, fail -> hvac.setHvacDefroster(area, c.action == CarAction.DEFROST_ON, { _, _ -> ok(Unit) }, fail) }
                ok()
            }
            CarAction.FRESH_AIR, CarAction.RECIRCULATE -> {
                val area = pick(cap("recirc") { ok, fail -> hvac.checkHvacRecirculationCapabilities({ l: List<SeatArea> -> ok(l) }, fail) })
                await<Unit> { ok, fail -> hvac.setHvacRecirculation(area, c.action == CarAction.RECIRCULATE, { _, _ -> ok(Unit) }, fail) }
                ok()
            }
            CarAction.SEAT_HEAT_ON, CarAction.SEAT_HEAT_OFF, CarAction.SEAT_VENT_ON, CarAction.SEAT_VENT_OFF -> {
                val seat = v.getSeat()
                val heat = c.action == CarAction.SEAT_HEAT_ON || c.action == CarAction.SEAT_HEAT_OFF
                val on = c.action == CarAction.SEAT_HEAT_ON || c.action == CarAction.SEAT_VENT_ON
                val bounds = cap<Map<SeatArea, AreaBounds<Int>>>(if (heat) "seatHeat" else "seatVent") { ok, fail ->
                    val got = { m: Map<SeatArea, AreaBounds<Int>> -> ok(m) }
                    if (heat) seat.checkSeatTemperatureCapabilities(got, fail) else seat.checkSeatVentilationCapabilities(got, fail)
                }
                val targets = bounds.filterKeys { a -> a.values.any { it in seatsOf(c.zone) } }
                if (targets.isEmpty()) return CarResult(false, detail = "자리 ${c.zone} 없음: ${bounds.keys.map { it.values.toList() }}")
                for ((area, b) in targets) {
                    val level = if (on) level(b.maxValue, c.amount) else 0
                    await<Unit> { ok, fail ->
                        val done = { _: SeatArea, _: Int? -> ok(Unit) }
                        if (heat) seat.setHvacSeatTemperature(area, level, done, fail) else seat.setHvacSeatVentilation(area, level, done, fail)
                    }
                }
                ok()
            }
            CarAction.WHEEL_HEAT_ON, CarAction.WHEEL_HEAT_OFF -> {
                val wheel = v.getSteeringWheel()
                val b = cap<AreaBounds<Int>>("wheel") { ok, fail -> wheel.checkHvacSteeringWheelHeatCapabilities({ x: AreaBounds<Int> -> ok(x) }, fail) }
                val level = if (c.action == CarAction.WHEEL_HEAT_ON) level(b.maxValue, c.amount) else 0
                await<Unit> { ok, fail -> wheel.setHvacSteeringWheelHeat(level, { _ -> ok(Unit) }, fail) }
                ok()
            }
            CarAction.WINDOW_OPEN, CarAction.WINDOW_CLOSE, CarAction.SUNROOF_OPEN, CarAction.SUNROOF_CLOSE -> {
                val window = v.getWindow()
                val roof = c.action == CarAction.SUNROOF_OPEN || c.action == CarAction.SUNROOF_CLOSE
                val open = c.action == CarAction.WINDOW_OPEN || c.action == CarAction.SUNROOF_OPEN
                val bounds = cap<Map<WindowArea, AreaBounds<Int>>>("window") { ok, fail ->
                    window.checkWindowPositionCapabilities({ m: Map<WindowArea, AreaBounds<Int>> -> ok(m) }, fail)
                }
                val wanted = if (roof) ROOF else windowsOf(c.zone)
                val targets = bounds.filterKeys { a -> a.values.any { it in wanted } }
                if (targets.isEmpty()) return CarResult(false, detail = "창 ${if (roof) "선루프" else c.zone} 없음")
                for ((area, b) in targets) {
                    val pos = when {
                        !open -> b.minValue
                        c.amount == CarAmount.LITTLE -> b.minValue + (b.maxValue - b.minValue) / 3
                        else -> b.maxValue
                    }
                    await<Unit> { ok, fail -> window.setWindowPosition(area, pos, { _, _ -> ok(Unit) }, fail) }
                }
                ok()
            }
            CarAction.CABIN_LIGHT_ON, CarAction.CABIN_LIGHT_OFF -> {
                val sw = if (c.action == CarAction.CABIN_LIGHT_ON) LightSwitch.ON else LightSwitch.OFF
                await<Unit> { ok, fail -> v.getLight().setCabinLightSwitch(sw, { _ -> ok(Unit) }, fail) }
                ok()
            }
            CarAction.DOOR_LOCK -> {
                val door = v.getDoor()
                val areas = cap<List<DoorArea>>("door") { ok, fail -> door.checkDoorLockCapabilities({ l: List<DoorArea> -> ok(l) }, fail) }
                for (area in areas) await<Unit> { ok, fail -> door.setDoorLock(area, true, { _, _ -> ok(Unit) }, fail) }
                ok()
            }
        }
    }

    private fun ok(celsius: Int? = null) = CarResult(ok = true, celsius = celsius)

    private fun level(max: Int, amount: CarAmount) = when (amount) {
        CarAmount.FULL -> max
        CarAmount.LITTLE -> minOf(1, max)
        CarAmount.NORMAL -> (max + 1) / 2
    }.coerceAtLeast(minOf(1, max))

    private fun pick(areas: List<SeatArea>): SeatArea =
        areas.firstOrNull { SeatValue.ROW_1_LEFT in it.values } ?: areas.firstOrNull() ?: throw IllegalStateException("지원하지 않는 기능")

    private fun seatsOf(zone: CarZone): Set<SeatValue> = when (zone) {
        CarZone.DRIVER -> setOf(SeatValue.ROW_1_LEFT)
        CarZone.PASSENGER -> setOf(SeatValue.ROW_1_RIGHT)
        CarZone.REAR -> setOf(SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT, SeatValue.ROW_3_LEFT, SeatValue.ROW_3_CENTER, SeatValue.ROW_3_RIGHT)
        CarZone.ALL -> SeatValue.entries.toSet()
    }

    private fun windowsOf(zone: CarZone): Set<WindowValue> = when (zone) {
        CarZone.DRIVER -> setOf(WindowValue.ROW_1_LEFT)
        CarZone.PASSENGER -> setOf(WindowValue.ROW_1_RIGHT)
        CarZone.REAR -> setOf(WindowValue.ROW_2_LEFT, WindowValue.ROW_2_RIGHT)
        CarZone.ALL -> setOf(WindowValue.ROW_1_LEFT, WindowValue.ROW_1_RIGHT, WindowValue.ROW_2_LEFT, WindowValue.ROW_2_RIGHT)
    }

    private fun <T> await(call: (ok: (T) -> Unit, fail: (Exception) -> Unit) -> Unit): T {
        val latch = CountDownLatch(1)
        var value: T? = null
        var error: Exception? = null
        call({ value = it; latch.countDown() }, { error = it; latch.countDown() })
        if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) throw TimeoutException("차 응답 없음(${TIMEOUT_MS}ms)")
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private companion object {
        const val TAG = "PleosCarControl"
        const val TIMEOUT_MS = 2_000L
        const val WARM_ATTEMPTS = 5
        const val WARM_RETRY_MS = 1_500L
        const val HEATER_MIN_CELSIUS = 25
        const val BASE_CELSIUS = 22
        val ROOF = setOf(WindowValue.ROOF_TOP_1, WindowValue.ROOF_TOP_2)
    }
}
