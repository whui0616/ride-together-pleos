package ai.nunchi.mate.demo

import ai.nunchi.engine.CompanionProfile
import ai.nunchi.engine.Gear
import ai.nunchi.engine.Message
import ai.nunchi.engine.SignalUpdate
import ai.nunchi.engine.TurnSignal
import ai.nunchi.engine.WiperLevel
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

interface DemoHost {
    fun signal(update: SignalUpdate)
    fun submit(message: Message)
    fun caption(text: String)
    fun profile(): CompanionProfile
}

class Step(val atMs: Long, val run: DemoHost.(nowMs: Long) -> Unit)

class Scenario(val title: String, val steps: List<Step>)

object DemoScenarios {

    private fun DemoHost.resetTo(speedKmh: Float, nextTurnM: Int?, wiper: WiperLevel = WiperLevel.OFF) {
        signal(SignalUpdate.GearChanged(Gear.DRIVE))
        signal(SignalUpdate.Speed(speedKmh))
        signal(SignalUpdate.NextTurn(nextTurnM))
        signal(SignalUpdate.Wiper(wiper))
        signal(SignalUpdate.Turn(TurnSignal.OFF))
        signal(SignalUpdate.Brake(false))
        signal(SignalUpdate.CautionZone(false))
        signal(SignalUpdate.CameraAlert(false))
    }

    val all = listOf(
        Scenario(
            "①② 비 오는 교차로 → 직진",
            listOf(
                Step(0) { resetTo(35f, 280, WiperLevel.HIGH); caption("비가 오고, 280m 앞에서 좌회전") },
                Step(1_500) {
                    signal(SignalUpdate.NextTurn(150))
                    signal(SignalUpdate.Turn(TurnSignal.LEFT))
                    submit(DemoContent.incomingChat(it))
                    caption("메시지 수신 → 부하가 높아 보류")
                },
                Step(3_000) { signal(SignalUpdate.NextTurn(60)); signal(SignalUpdate.Speed(20f)) },
                Step(5_000) {
                    signal(SignalUpdate.Turn(TurnSignal.OFF))
                    signal(SignalUpdate.NextTurn(2_400))
                    signal(SignalUpdate.Speed(45f))
                    signal(SignalUpdate.Wiper(WiperLevel.LOW))
                    caption("교차로 통과, 2.4km 직진 → 여유가 생기면 전달")
                },
                Step(6_000) { signal(SignalUpdate.Speed(60f)) },
            ),
        ),
        Scenario(
            "③ 퇴근길 정체 + 드라이브스루",
            listOf(
                Step(0) { resetTo(12f, null); caption("퇴근길 정체 구간") },
                Step(1_000) { signal(SignalUpdate.Speed(8f)) },
                Step(1_500) { submit(DemoContent.driveThruSuggestion(it)); caption("정체 중 → 경로 앞 드라이브스루 제안") },
            ),
        ),
        Scenario(
            "④ 말하는 중 급정거",
            listOf(
                Step(0) { resetTo(60f, 3_000); caption("한적한 직진 구간") },
                Step(500) { submit(DemoContent.localStory(it)) },
                Step(6_000) { signal(SignalUpdate.Speed(60f)) },
                Step(6_500) {
                    signal(SignalUpdate.Brake(true))
                    signal(SignalUpdate.Speed(25f))
                    caption("앞차 급정거! → 즉시 말 끊기")
                },
                Step(8_500) { signal(SignalUpdate.Brake(false)); signal(SignalUpdate.Speed(30f)) },
                Step(9_500) { signal(SignalUpdate.Speed(45f)); caption("상황 안정 → 이어서 말하기") },
            ),
        ),
        Scenario(
            "⑤ 도착, P단",
            listOf(
                Step(0) { resetTo(30f, null); caption("목적지 도착 직전, 천천히 감속") },
                Step(1_000) { signal(SignalUpdate.Speed(22f)) },
                Step(2_000) { signal(SignalUpdate.Speed(14f)) },
                Step(3_000) { signal(SignalUpdate.Speed(7f)) },
                Step(4_000) { signal(SignalUpdate.Speed(0f)) },
                Step(5_000) { signal(SignalUpdate.GearChanged(Gear.PARK)); caption("P단 → 정차 모드") },
                Step(5_500) { submit(DemoContent.arrivalSummary(it, profile())) },
            ),
        ),
    )
}

class ScenarioPlayer(private val host: DemoHost) {
    private val handler = Handler(Looper.getMainLooper())
    private val token = Any()

    var playing = false
        private set

    fun play(scenario: Scenario) {
        cancel()
        playing = true
        val start = SystemClock.uptimeMillis()
        for (step in scenario.steps) {
            handler.postAtTime({ step.run(host, SystemClock.elapsedRealtime()) }, token, start + step.atMs)
        }
        handler.postAtTime({ playing = false }, token, start + (scenario.steps.maxOfOrNull { it.atMs } ?: 0) + 1)
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(token)
        playing = false
    }
}
