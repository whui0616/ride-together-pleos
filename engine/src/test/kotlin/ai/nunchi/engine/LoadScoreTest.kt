package ai.nunchi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadScoreTest {
    private val calc = LoadScoreCalculator()

    @Test
    fun `scenario 1 - rainy intersection scores 75`() {
        val s = DrivingSignals(
            speedKmh = 30f, gear = Gear.DRIVE, wiper = WiperLevel.HIGH,
            nextTurnDistanceM = 80, turnSignal = TurnSignal.LEFT,
        )
        assertEquals(75, calc.calculate(s).score)
    }

    @Test
    fun `parked is always zero`() {
        val s = DrivingSignals(gear = Gear.PARK, wiper = WiperLevel.HIGH, turnSignal = TurnSignal.HAZARD)
        assertEquals(0, calc.calculate(s).score)
    }

    @Test
    fun `congestion bonus clamps at zero and is skipped near a turn`() {
        assertEquals(0, calc.calculate(DrivingSignals(speedKmh = 8f, gear = Gear.DRIVE)).score)
        val nearTurn = DrivingSignals(speedKmh = 8f, gear = Gear.DRIVE, nextTurnDistanceM = 200)
        assertEquals(25, calc.calculate(nearTurn).score)
    }

    @Test
    fun `brake and hard deceleration add up`() {
        val s = DrivingSignals(speedKmh = 40f, gear = Gear.DRIVE, brakePressed = true, decelerationMps2 = 5f)
        assertEquals(35, calc.calculate(s).score)
    }

    @Test
    fun `steering adds 10 from 90 degrees and 20 from 180 degrees either way`() {
        val d = DrivingSignals(speedKmh = 40f, gear = Gear.DRIVE)
        assertEquals(0, calc.calculate(d.copy(steeringDeg = 89f)).score)
        assertEquals(listOf("조향" to 10), calc.calculate(d.copy(steeringDeg = 90f)).contributions)
        assertEquals(10, calc.calculate(d.copy(steeringDeg = -179f)).score)
        assertEquals(listOf("큰 조향" to 20), calc.calculate(d.copy(steeringDeg = -180f)).contributions)
        assertEquals(20, calc.calculate(d.copy(steeringDeg = 540f)).score)
    }

    @Test
    fun `left turn at an intersection with steering`() {
        val s = DrivingSignals(speedKmh = 20f, gear = Gear.DRIVE, turnSignal = TurnSignal.LEFT, steeringDeg = -270f)
        assertEquals(40, calc.calculate(s).score)
        assertEquals(75, calc.calculate(s.copy(nextTurnDistanceM = 80)).score)
        assertEquals(0, calc.calculate(s.copy(gear = Gear.PARK)).score)
    }

    @Test
    fun `signal store keeps the latest steering angle`() {
        val store = SignalStore()
        store.apply(SignalUpdate.Steering(-200f), 0)
        assertEquals(-200f, store.snapshot(0).steeringDeg, 0.01f)
        store.apply(SignalUpdate.Steering(0f), 100)
        assertEquals(0f, store.snapshot(100).steeringDeg, 0.01f)
    }

    @Test
    fun `score is capped at 100`() {
        val s = DrivingSignals(
            speedKmh = 110f, gear = Gear.DRIVE, nextTurnDistanceM = 50, inCautionZone = true, cameraAlert = true,
            turnSignal = TurnSignal.RIGHT, wiper = WiperLevel.HIGH, brakePressed = true, decelerationMps2 = 4f,
        )
        assertEquals(100, calc.calculate(s).score)
    }

    @Test
    fun `smoother rises instantly and halves after one half-life`() {
        val sm = LoadSmoother(releaseHalfLifeMs = 3_000)
        assertEquals(80f, sm.update(80, 0), 0.01f)
        assertEquals(40f, sm.update(0, 3_000), 0.01f)
        assertEquals(90f, sm.update(90, 3_100), 0.01f)
    }

    @Test
    fun `signal store computes deceleration and drops it when stale`() {
        val store = SignalStore(decelStaleMs = 1_000)
        store.apply(SignalUpdate.Speed(72f), 0)
        store.apply(SignalUpdate.Speed(36f), 1_000)
        assertEquals(10f, store.snapshot(1_000).decelerationMps2, 0.01f)
        assertEquals(0f, store.snapshot(2_500).decelerationMps2, 0.01f)
    }

    @Test
    fun `speech window uses distance minus margin over speed`() {
        val s = DrivingSignals(speedKmh = 60f, gear = Gear.DRIVE, nextTurnDistanceM = 2_400)
        assertEquals(135f, SpeechWindow.availableSec(s), 0.5f)
        assertTrue(SpeechWindow.availableSec(s.copy(speedKmh = 2f)).isInfinite())
        assertTrue(SpeechWindow.availableSec(s.copy(nextTurnDistanceM = null)).isInfinite())
    }
}
