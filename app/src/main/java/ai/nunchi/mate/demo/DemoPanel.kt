package ai.nunchi.mate.demo

import kotlin.math.roundToInt
import ai.nunchi.engine.DrivingSignals
import ai.nunchi.engine.EngineState
import ai.nunchi.engine.Gear
import ai.nunchi.engine.Message
import ai.nunchi.engine.Reply
import ai.nunchi.engine.SignalUpdate
import ai.nunchi.engine.suggest.SuggestionKind
import ai.nunchi.engine.TurnSignal
import ai.nunchi.engine.VoiceOption
import ai.nunchi.engine.VoiceTier
import ai.nunchi.engine.WiperLevel
import ai.nunchi.mate.signals.Destination
import ai.nunchi.mate.ui.LoadGraphView
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class DemoPanel(
    private val context: Context,
    private val root: LinearLayout,
    private val actions: Actions,
) {
    interface Actions {
        fun signal(update: SignalUpdate)
        fun play(scenario: Scenario)
        fun inject(factory: (Long) -> Message)
        fun acceptSuggestion()
        fun useRealSignals(enabled: Boolean)
        fun requestRoute(destination: Destination)
        fun cancelRoute()

        fun offerSuggestion(kind: SuggestionKind)
        fun reply(reply: Reply)
        fun placeDistance(meters: Double)
        fun batterySoc(percent: Int)
        fun askBrain(utterance: String)
        fun selectVoice(option: VoiceOption)
        fun addXp(amount: Int)
        fun visitRegion(address: String)
    }

    val graph = LoadGraphView(context)
    private val status = text("", 16f, "#E6EDF3")
    private val captionView = text("", 17f, "#F5B82E")
    private var realCheck: CheckBox? = null
    private var voiceButtons: Map<VoiceOption, TextView> = emptyMap()

    fun build(realSignalsAvailable: Boolean, naviAvailable: Boolean, sourceNames: List<String>) {
        root.addView(header("운전 부하 타임라인"))
        root.addView(graph, LinearLayout.LayoutParams(-1, dp(220)))
        root.addView(status.apply { setPadding(0, dp(8), 0, 0) })
        root.addView(captionView)

        root.addView(header("시나리오 (기획서 4장)"))
        DemoScenarios.all.forEach { s -> root.addView(button(s.title, primary = true) { actions.play(s) }) }

        if (naviAvailable) {
            root.addView(header("실제 내비 경로 (Pleos NaviHelper)"))
            root.addView(row(*DemoContent.destinations.map { d -> button("🧭 ${d.name}") { actions.requestRoute(d) } }.toTypedArray()))
            root.addView(button("경로 취소") { actions.cancelRoute() })
        }

        root.addView(header("가상 이벤트"))
        root.addView(row(
            button("💬 메시지") { actions.inject(DemoContent::incomingChat) },
            button("🍔 맛집") { actions.inject(DemoContent::driveThruSuggestion) },
        ))
        root.addView(row(
            button("📖 이야기") { actions.inject(DemoContent::localStory) },
            button("⚠ 긴급") { actions.inject(DemoContent::safetyNotice) },
        ))
        root.addView(button("👍 '응' 대답 (음성 대신)") { actions.acceptSuggestion() })

        root.addView(header("제안 엔진 (점수 · 발화 창 · 장소 통과 폐기)"))
        root.addView(row(
            button("🍜 맛집 후보") { actions.offerSuggestion(SuggestionKind.MEAL) },
            button("☕ 드라이브스루") { actions.offerSuggestion(SuggestionKind.DRIVE_THRU) },
        ))
        root.addView(row(
            button("🛏 휴게소") { actions.offerSuggestion(SuggestionKind.REST) },
            button("🔋 충전소") { actions.offerSuggestion(SuggestionKind.CHARGE) },
            button("📖 지역 이야기") { actions.offerSuggestion(SuggestionKind.STORY) },
        ))
        root.addView(row(
            button("🎤 응") { actions.reply(Reply.YES) },
            button("🎤 아니") { actions.reply(Reply.NO) },
            button("🎤 이따") { actions.reply(Reply.LATER) },
        ))
        val placeLabel = text("장소까지 거리 2000 m (지나치면 → 폐기)", 16f, "#C9D1D9")
        root.addView(placeLabel)
        root.addView(seek(6_000) { v ->
            placeLabel.text = "장소까지 거리 $v m"
            actions.placeDistance(v.toDouble())
        }.apply { progress = 2_000 })
        val socLabel = text("배터리 100 %", 16f, "#C9D1D9")
        root.addView(socLabel)
        root.addView(seek(100) { v ->
            socLabel.text = "배터리 $v %"
            actions.batterySoc(v)
        }.apply { progress = 100 })

        root.addView(header("신호 직접 조작"))
        val speedLabel = text("속도 0 km/h", 16f, "#C9D1D9")
        root.addView(speedLabel)
        root.addView(seek(140) { v ->
            speedLabel.text = "속도 $v km/h"
            actions.signal(SignalUpdate.Speed(v.toFloat()))
        })
        root.addView(row(
            button("P 정차") { actions.signal(SignalUpdate.GearChanged(Gear.PARK)) },
            button("D 주행") { actions.signal(SignalUpdate.GearChanged(Gear.DRIVE)) },
        ))
        root.addView(label("방향지시등"))
        root.addView(row(
            button("◀ 좌") { actions.signal(SignalUpdate.Turn(TurnSignal.LEFT)) },
            button("끔") { actions.signal(SignalUpdate.Turn(TurnSignal.OFF)) },
            button("우 ▶") { actions.signal(SignalUpdate.Turn(TurnSignal.RIGHT)) },
        ))
        root.addView(label("와이퍼"))
        root.addView(row(
            button("끔") { actions.signal(SignalUpdate.Wiper(WiperLevel.OFF)) },
            button("저속") { actions.signal(SignalUpdate.Wiper(WiperLevel.LOW)) },
            button("고속") { actions.signal(SignalUpdate.Wiper(WiperLevel.HIGH)) },
        ))
        root.addView(row(
            check("브레이크") { actions.signal(SignalUpdate.Brake(it)) },
            check("주의구간") { actions.signal(SignalUpdate.CautionZone(it)) },
            check("카메라") { actions.signal(SignalUpdate.CameraAlert(it)) },
        ))

        val turnLabel = text("다음 회전: 경로 없음", 16f, "#C9D1D9")
        root.addView(turnLabel)
        root.addView(seek(3_000) { v ->
            val d = if (v >= 3_000) null else v
            turnLabel.text = "다음 회전: ${d?.let { "$it m" } ?: "경로 없음"}"
            actions.signal(SignalUpdate.NextTurn(d))
        }.apply { progress = 3_000 })

        root.addView(header("지역 방문 (에뮬레이터 내비 위치는 고정이라 시연용)"))
        root.addView(row(*DEMO_REGIONS.map { (label, address) -> button("📍 $label") { actions.visitRegion(address) } }.toTypedArray()))

        root.addView(header("대화 AI (음성 대신 고정 문장)"))
        BRAIN_UTTERANCES.forEach { u -> root.addView(button("🧠 \"$u\"") { actions.askBrain(u) }) }

        root.addView(header("캐릭터 목소리 (레벨이 오르면 해금)"))
        voiceButtons = VoiceOption.entries.associateWith { v -> button(v.label) { actions.selectVoice(v) } }
        VoiceTier.entries.forEach { tier ->
            root.addView(label(tier.label))
            voiceButtons.filterKeys { it.tier == tier }.values.chunked(4).forEach { root.addView(row(*it.toTypedArray())) }
        }
        root.addView(button("⭐ 경험치 +${DEMO_XP} (해금 시연)") { actions.addXp(DEMO_XP) })

        root.addView(header("신호·음성 출처"))
        root.addView(text(sourceNames.joinToString("\n") { "· $it" }, 15f, "#8B96A3"))
        if (realSignalsAvailable) {
            realCheck = check("실차/에뮬레이터 신호 사용", initial = true) { actions.useRealSignals(it) }
            root.addView(realCheck)
        }
    }

    fun setRealSignals(enabled: Boolean) {
        realCheck?.isChecked = enabled
    }

    fun caption(text: String) {
        captionView.text = text
    }

    fun setVoice(selected: VoiceOption, level: Int) {
        voiceButtons.forEach { (v, b) ->
            val unlocked = v.isUnlocked(level)
            b.text = if (unlocked) "${if (v.female) "♀" else "♂"} ${v.label}" else "🔒 ${v.label} Lv.${v.unlockLevel}"
            b.alpha = if (unlocked) 1f else 0.5f
            (b.background as GradientDrawable).setColor(Color.parseColor(if (v == selected) "#3D7BFF" else "#2C343F"))
        }
    }

    fun render(s: DrivingSignals, e: EngineState, voiceName: String) {
        val window = if (e.availableSec.isInfinite()) "∞" else "%.0f초".format(e.availableSec)
        status.text = buildString {
            append("부하 %.0f · %s · 보류 %d · 발화창 %s\n".format(e.score, e.zone, e.pending, window))
            append(e.breakdown.contributions.joinToString("  ") { (k, v) -> "$k ${if (v >= 0) "+" else ""}$v" }.ifEmpty { "가중 요인 없음" })
            append("\n${s.gear} · %.0fkm/h · 회전 %s · 감속 %.1fm/s² · 조향 %d°".format(s.speedKmh, s.nextTurnDistanceM?.let { "${it}m" } ?: "-", s.decelerationMps2, s.steeringDeg.roundToInt()))
            append("\n음성: $voiceName")
        }
    }

    private fun header(t: String) = text(t, 19f, "#FFFFFF").apply {
        setPadding(0, dp(18), 0, dp(6))
        paint.isFakeBoldText = true
    }

    private fun label(t: String) = text(t, 16f, "#8B96A3").apply { setPadding(0, dp(6), 0, dp(2)) }

    private fun text(t: String, sizeSp: Float, color: String) = TextView(context).apply {
        text = t
        textSize = sizeSp
        setTextColor(Color.parseColor(color))
    }

    private fun button(t: String, primary: Boolean = false, onClick: () -> Unit) = TextView(context).apply {
        text = t
        textSize = 18f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setPadding(dp(10), dp(11), dp(10), dp(11))
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(Color.parseColor(if (primary) "#3D7BFF" else "#2C343F"))
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }
        setOnClickListener { onClick() }
    }

    private fun check(t: String, initial: Boolean = false, onChange: (Boolean) -> Unit) = CheckBox(context).apply {
        text = t
        isChecked = initial
        setTextColor(Color.WHITE)
        setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun seek(max: Int, onChange: (Int) -> Unit) = SeekBar(context).apply {
        this.max = max
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, v: Int, fromUser: Boolean) {
                if (fromUser) onChange(v)
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
    }

    private fun row(vararg views: View) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply {
                topMargin = dp(6)
                if (i > 0) marginStart = dp(6)
            })
        }
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        val BRAIN_UTTERANCES = listOf(
            "배고픈데 근처에 먹을 데 있어?", "아까부터 꼬르륵 소리가 나네",
            "히터 틀어 줘", "조수석 열선 켜 줘", "온도 24도로 맞춰 줘", "추워", "트렁크 열어 줘",
        )

        const val DEMO_XP = 100

        val DEMO_REGIONS = listOf(
            "전주" to "전북특별자치도 전주시 완산구",
            "강릉" to "강원특별자치도 강릉시",
            "부산 해운대" to "부산광역시 해운대구",
        )
    }
}
