package ai.nunchi.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class HabitTrackerTest {

    private fun drive(tracker: HabitTracker, store: SignalStore, speeds: List<Float>, startMs: Long = 0): Long {
        var t = startMs
        for (v in speeds) {
            store.apply(SignalUpdate.Speed(v), t)
            tracker.update(store.snapshot(t), t)
            t += 1_000
        }
        return t
    }

    @Test
    fun `운전을 어떻게 하든 경험치는 오르지 않는다`() {
        val tracker = HabitTracker()
        val store = SignalStore()
        store.apply(SignalUpdate.GearChanged(Gear.DRIVE), 0)
        var t = drive(tracker, store, listOf(36f, 28.8f, 21.6f, 14.4f, 7.2f, 0f))
        t = drive(tracker, store, listOf(10f, 20f, 36f, 0f), t)
        store.apply(SignalUpdate.Turn(TurnSignal.LEFT), t)
        drive(tracker, store, listOf(30f, 30f), t)
        assertEquals(0, tracker.profile.xp)
    }

    @Test
    fun `함께 달린 거리를 쌓고 고속도로 구간을 따로 센다`() {
        val tracker = HabitTracker()
        val s = DrivingSignals(gear = Gear.DRIVE)
        tracker.update(s.copy(speedKmh = 60f), 0)
        tracker.update(s.copy(speedKmh = 100f), 3_600_000)
        tracker.update(s.copy(speedKmh = 0f), 7_200_000)
        assertEquals(160f, tracker.profile.distanceKm, 0.01f)
        assertEquals(100f, tracker.profile.highwayKm, 0.01f)
        assertEquals(0, tracker.profile.xp)
    }

    @Test
    fun `밖에서 준 경험치만 더한다`() {
        val tracker = HabitTracker(CompanionProfile(xp = 90))
        tracker.addXp(20)
        tracker.addXp(0)
        tracker.addXp(-5)
        assertEquals(110, tracker.profile.xp)
        assertEquals(2, tracker.profile.level)
    }

    @Test
    fun `성격은 주로 다니는 길로 정해진다`() {
        assertEquals(Persona.BUDDY, CompanionProfile().persona)
        assertEquals(Persona.BUDDY, CompanionProfile(distanceKm = 10f).persona)
        assertEquals(Persona.EASYGOING, CompanionProfile(distanceKm = 50f, highwayKm = 2f).persona)
        assertEquals(Persona.ADVENTURER, CompanionProfile(distanceKm = 50f, highwayKm = 40f).persona)
        assertEquals(Persona.BUDDY, CompanionProfile(distanceKm = 50f, highwayKm = 15f).persona)
    }
}
