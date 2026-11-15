package ai.nunchi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingEngineTest {
    private val busy = DrivingSignals(
        speedKmh = 30f, gear = Gear.DRIVE, wiper = WiperLevel.HIGH,
        nextTurnDistanceM = 80, turnSignal = TurnSignal.LEFT,
    )
    private val cruising = DrivingSignals(speedKmh = 60f, gear = Gear.DRIVE, nextTurnDistanceM = 2_400)

    private fun msg(id: String, p: Priority, text: String = "민지 님이 몇 시 도착하냐고 물었어요.", short: String? = null, at: Long = 0) =
        Message(id, text, p, createdAtMs = at, shortText = short)

    private fun TimingEngine.run(s: DrivingSignals, from: Long, to: Long): List<Pair<Long, Decision>> =
        (from..to step 250).map { it to tick(s, it) }.filter { it.second != Decision.None }

    @Test
    fun `normal message is held while busy and spoken after calm hold`() {
        val e = TimingEngine()
        e.submit(msg("m1", Priority.NORMAL))
        assertTrue(e.run(busy, 0, 5_000).isEmpty())
        assertEquals(LoadZone.RED, e.state.zone)

        val out = e.run(cruising, 5_250, 20_000)
        val (at, d) = out.first()
        assertTrue(d is Decision.Speak)
        assertTrue("spoke at $at", at in 11_000..13_500)
    }

    @Test
    fun `urgent message goes out in yellow but not in red`() {
        val e = TimingEngine()
        e.submit(msg("u1", Priority.URGENT, text = "전방 어린이보호구역이에요. 속도를 줄여 주세요.", short = "어린이보호구역이에요."))
        assertTrue(e.run(busy, 0, 1_000).isEmpty())

        val yellow = cruising.copy(inCautionZone = true, cameraAlert = true)
        val d = e.run(yellow, 1_250, 30_000).first().second as Decision.Speak
        assertEquals(LoadZone.YELLOW, e.state.zone)
        assertEquals("어린이보호구역이에요.", d.text)
    }

    @Test
    fun `speaking is interrupted on load spike and resumed with prefix`() {
        val e = TimingEngine()
        e.submit(msg("m1", Priority.NORMAL))
        val spoken = e.run(cruising, 0, 5_000).first().second as Decision.Speak

        val spike = cruising.copy(brakePressed = true, decelerationMps2 = 6f, turnSignal = TurnSignal.RIGHT)
        val interrupt = e.tick(spike, 5_250)
        assertEquals(Decision.Interrupt(spoken.message), interrupt)
        assertEquals(1, e.state.pending)

        val resumed = e.run(cruising, 5_500, 30_000).first().second as Decision.Speak
        assertTrue(resumed.text.startsWith("아까 하던 얘기 마저 할게요."))
    }

    @Test
    fun `long message waits for a long enough speech window, short text used when it fits`() {
        val e = TimingEngine()
        val long = "1.2km 앞 오른쪽에 햄버거 드라이브스루가 있어요. 지금 주문하면 도착할 때 바로 받을 수 있는데, 들렀다 갈까요?"
        e.submit(msg("s1", Priority.SUGGESTION, text = long, short = "앞에 드라이브스루 들를까요?"))

        val tight = DrivingSignals(speedKmh = 90f, gear = Gear.DRIVE, nextTurnDistanceM = 350)
        val d = e.run(tight, 0, 10_000).first().second as Decision.Speak
        assertEquals("앞에 드라이브스루 들를까요?", d.text)
    }

    @Test
    fun `nothing is spoken when even the short text does not fit`() {
        val e = TimingEngine()
        e.submit(msg("s1", Priority.SUGGESTION, text = "긴 이야기 ".repeat(20), short = "짧은 이야기를 하나 들려드릴까요?"))
        val tight = DrivingSignals(speedKmh = 90f, gear = Gear.DRIVE, nextTurnDistanceM = 200)
        assertTrue(e.run(tight, 0, 10_000).isEmpty())
        assertEquals(LoadZone.GREEN, e.state.zone)
        assertEquals(1, e.state.pending)
    }

    @Test
    fun `no new speech while listening, queued message waits`() {
        val e = TimingEngine()
        e.submit(msg("n1", Priority.NORMAL))
        val parked = DrivingSignals(gear = Gear.PARK)
        assertEquals(Decision.None, e.tick(parked, 0, allowNewSpeech = false))
        assertEquals(1, e.state.pending)
        assertTrue(e.tick(parked, 250) is Decision.Speak)
    }

    @Test
    fun `expired messages are dropped`() {
        val e = TimingEngine()
        e.submit(Message("u1", "곧 사라질 안내", Priority.URGENT, createdAtMs = 0, ttlMs = 1_000))
        e.run(busy, 0, 2_000)
        assertEquals(0, e.state.pending)
    }

    @Test
    fun `parked speaks immediately and orders by priority`() {
        val e = TimingEngine()
        e.submit(msg("s1", Priority.SUGGESTION, at = 0))
        e.submit(msg("n1", Priority.NORMAL, at = 10))
        val d = e.tick(DrivingSignals(gear = Gear.PARK), 100) as Decision.Speak
        assertEquals("n1", d.message.id)
        e.onSpeechDone("n1")
        assertEquals("s1", (e.tick(DrivingSignals(gear = Gear.PARK), 200) as Decision.Speak).message.id)
    }
}
