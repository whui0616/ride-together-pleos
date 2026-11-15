package ai.nunchi.mate

import ai.nunchi.engine.CompanionProfile
import ai.nunchi.engine.Decision
import ai.nunchi.engine.DriveSessionTracker
import ai.nunchi.engine.DriveSummary
import ai.nunchi.engine.DrivingSignals
import ai.nunchi.engine.ScoreState
import ai.nunchi.engine.XpReason
import ai.nunchi.engine.AchKey
import ai.nunchi.engine.Gear
import ai.nunchi.engine.HabitTracker
import ai.nunchi.engine.LoadZone
import ai.nunchi.engine.Message
import ai.nunchi.engine.Priority
import ai.nunchi.engine.Reply
import ai.nunchi.engine.ReplyParser
import ai.nunchi.engine.SignalStore
import ai.nunchi.engine.SignalUpdate
import ai.nunchi.engine.TimingEngine
import ai.nunchi.engine.TurnSignal
import ai.nunchi.engine.VoiceOption
import ai.nunchi.engine.WiperLevel
import ai.nunchi.engine.chat.CarState
import ai.nunchi.engine.chat.ChatIntent
import ai.nunchi.engine.chat.ChatQuestion
import ai.nunchi.engine.chat.ChatReply
import ai.nunchi.engine.chat.ChatRequest
import ai.nunchi.engine.control.CarCommand
import ai.nunchi.engine.control.CarLines
import ai.nunchi.engine.situation.Region
import ai.nunchi.engine.situation.RegionVisit
import ai.nunchi.engine.situation.SituationDetector
import ai.nunchi.engine.suggest.DriveContext
import ai.nunchi.engine.suggest.Suggestion
import ai.nunchi.engine.suggest.SuggestionKind
import ai.nunchi.engine.suggest.SuggestionManager
import ai.nunchi.engine.suggest.SuggestionScorer
import ai.nunchi.engine.suggest.UserPrefs
import ai.nunchi.mate.brain.BrainAnswer
import ai.nunchi.mate.brain.Brains
import ai.nunchi.mate.brain.ChatBrain
import ai.nunchi.mate.control.CarControl
import ai.nunchi.mate.demo.DemoContent
import ai.nunchi.mate.demo.Lines
import ai.nunchi.mate.demo.DemoHost
import ai.nunchi.mate.demo.DemoPanel
import ai.nunchi.mate.demo.Scenario
import ai.nunchi.mate.demo.ScenarioPlayer
import ai.nunchi.mate.signals.CarPropertySignalSource
import ai.nunchi.mate.signals.Destination
import ai.nunchi.mate.signals.LocationReader
import ai.nunchi.mate.signals.NaviControl
import ai.nunchi.mate.signals.PleosLocation
import ai.nunchi.mate.signals.PleosSignalSources
import ai.nunchi.mate.signals.PleosSpeech
import ai.nunchi.mate.signals.SignalSource
import ai.nunchi.mate.avatar.Avatar
import ai.nunchi.mate.avatar.AvatarStore
import ai.nunchi.mate.ui.VehicleState
import ai.nunchi.mate.voice.VoiceStyle
import ai.nunchi.mate.ui.DrivingScreen
import ai.nunchi.mate.ui.HomeScreen
import ai.nunchi.mate.ui.LoadGraphView
import ai.nunchi.mate.ui.Mood
import ai.nunchi.mate.ui.TutorialOverlay
import android.content.Intent
import ai.nunchi.mate.voice.VoiceInput
import android.Manifest
import android.app.Activity
import android.car.Car
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import java.time.LocalDate
import android.widget.TextView
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

class MainActivity : Activity(), DemoHost, DemoPanel.Actions {

    private val main = Handler(Looper.getMainLooper())
    private val store = SignalStore()
    private val engine = TimingEngine()
    private lateinit var profiles: ProfileStore
    private lateinit var habits: HabitTracker
    private lateinit var situations: SituationDetector
    private var location: LocationReader? = null
    private var lastRegion: Region? = null
    private lateinit var speaker: Speaker
    private var voiceInput: VoiceInput? = null
    private lateinit var brain: ChatBrain
    private var brainSeq = 0
    private lateinit var car: CarControl
    private val pendingCar = mutableMapOf<String, CarCommand>()
    private lateinit var panel: DemoPanel
    private lateinit var player: ScenarioPlayer
    private lateinit var driving: DrivingScreen
    private lateinit var parked: HomeScreen
    private lateinit var tutorial: TutorialOverlay
    private lateinit var avatarStore: AvatarStore
    private var avatar = Avatar()

    private val sources = mutableListOf<SignalSource>()
    private var navi: NaviControl? = null
    private var useRealSignals = true
    private var lastLineAtMs = 0L
    private var lastSaveAtMs = 0L
    private var loggedScore = -100f
    private var loggedZone: LoadZone? = null
    private lateinit var score: ScoreState
    private val driveSession = DriveSessionTracker()
    private var voiceLevel = 1
    private val voiceOpenLevel get() = if (profiles.adminMode) Int.MAX_VALUE else voiceLevel
    private val diary = ArrayDeque<String>()

    private var question: Message? = null
    private var lastSpokeAtMs = 0L
    private var listening = false

    private val suggestions = SuggestionManager()
    private val prefs = UserPrefs()
    private lateinit var settingsStore: SettingsStore
    private var settings = Settings()
    private val offerTimes = ArrayDeque<Long>()
    private val scorer = SuggestionScorer()
    private val offered = HashMap<String, Suggestion>()
    private var lastHardBrakeMs = Long.MIN_VALUE / 2
    private var driveStartedMs: Long? = null
    private var lastParkedRenderMs = 0L
    private var placeDistanceM = 2_000.0
    private var batterySoc = 100
    private val speedHistory = ArrayDeque<Pair<Long, Float>>()
    private var alwaysListen = false
    private var openListening = false
    private var openSeq = 0
    private var openListenAfterMs = 0L
    private lateinit var listenButton: TextView

    private val tick = object : Runnable {
        override fun run() {
            onTick(SystemClock.elapsedRealtime())
            main.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        profiles = ProfileStore(this)
        score = profiles.score
        habits = HabitTracker(profiles.load())
        situations = SituationDetector(SituationStore(this))

        avatarStore = AvatarStore(this)
        avatar = avatarStore.load()
        DemoContent.personality = avatar.personality
        settingsStore = SettingsStore(this)
        settings = settingsStore.load()
        val unowned = ai.nunchi.mate.ui.CharacterLook.KEYS.mapNotNull { ai.nunchi.engine.Catalog.of(it, avatar.look[it]) }
            .filter { !profiles.inventory.owns(it, habits.profile.level) }.map { it.id }
        if (unowned.isNotEmpty()) profiles.inventory = profiles.inventory.let { it.copy(owned = it.owned + unowned) }
        if (!profiles.starterGranted) profiles.starterGranted = true

        driving = DrivingScreen(this)
        parked = HomeScreen(
            this, avatarStore, profiles,
            onLook = { l -> avatar = avatar.copy(look = l); avatarStore.save(avatar) },
            onRecreate = {
                startActivity(Intent(this, OnboardingActivity::class.java).putExtra(OnboardingActivity.EXTRA_FORCE, true))
                finish()
            },
            onReplayTutorial = { tutorial.start() },
            onDemoPanel = { toggleDemoPanel() },
            onRoute = { d -> requestRoute(d) },
            onXp = { reason -> val got = grantXp(reason); habits.addXp(got); if (got > 0) addDiary("⭐ ${reason.label} +$got") },
            onSettings = { s ->
                val louder = s.voiceVolume != settings.voiceVolume
                settings = s; settingsStore.save(s); applySettings()
                if (louder) SystemClock.elapsedRealtime().let { now -> submit(Message("volume-$now", speaker.voice.sample, Priority.NORMAL, now, ttlMs = 60_000)) }
            },
            onAvatar = { a -> avatar = a; avatarStore.save(a); DemoContent.personality = a.personality; applySettings(); parked.notePersonality(a.personality); parked.render(habits.profile, avatar, diary.toList(), score) },
            onDiagnostics = { diagnostics() },
            onStyling = { st -> val k = if (st.drivingSmall) 0.7f else 1f; driving.companion.scaleX = k; driving.companion.scaleY = k },
            currentVoice = { speaker.voice },
            onVoice = { v -> selectVoice(v) },
            onSpeak = { line -> speakNow("tap", line) },
            onWipe = {
                SettingsStore.wipeAll(this)
                startActivity(Intent(this, OnboardingActivity::class.java).putExtra(OnboardingActivity.EXTRA_FORCE, true))
                finish()
            },
        )
        tutorial = TutorialOverlay(this, parked) { avatarStore.tutorialDone = true }
        voiceLevel = habits.profile.level
        speaker = Speaker(this, ::onSpeechDone).apply { voice = VoiceOption.of(profiles.voice.id, voiceOpenLevel) }
        profiles.styling.let { st -> val k = if (st.drivingSmall) 0.7f else 1f; driving.companion.scaleX = k; driving.companion.scaleY = k }
        applySettings()
        voiceInput = PleosSpeech.createInput(this)
        brain = Brains.create(this)
        car = PleosSignalSources.carControl(this)
        player = ScenarioPlayer(this)

        val automotive = packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        if (automotive) sources += CarPropertySignalSource(this)
        sources += PleosSignalSources.create(this)
        navi = sources.filterIsInstance<NaviControl>().firstOrNull()?.also { n ->
            n.onEvent = { text -> panel.caption("🧭 $text"); addDiary("🧭 $text"); onNaviEvent(text) }
        }
        location = PleosLocation.create(this)

        panel = DemoPanel(this, findViewById<LinearLayout>(R.id.panel), this)
        panel.build(
            realSignalsAvailable = sources.isNotEmpty(),
            naviAvailable = navi != null,
            sourceNames = listOf("데모 패널·시나리오") + sources.map { it.name } +
                if (PleosSignalSources.ENABLED) listOf("Pleos TTS·STT (온디바이스)") else listOf("Pleos SDK 미포함 (gradle.properties pleos.sdk)"),
        )
        panel.setVoice(speaker.voice, voiceOpenLevel)
        val panelScroll = findViewById<View>(R.id.panelScroll)
        val toggle = findViewById<View>(R.id.togglePanel)
        if (BuildConfig.DEBUG) {
            toggle.setOnClickListener { toggleDemoPanel() }
            panelScroll.visibility = View.GONE
        } else {
            toggle.visibility = View.GONE
            panelScroll.visibility = View.GONE
        }
        listenButton = findViewById(R.id.alwaysListen)
        alwaysListen = profiles.alwaysListen && voiceInput != null
        listenButton.setOnClickListener { setAlwaysListen(!alwaysListen) }
        renderListenButton()

        val wanted = (if (automotive) arrayOf(Car.PERMISSION_SPEED) else emptyArray()) + PleosSignalSources.PERMISSIONS +
            (if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)

        startSources()
        location?.start(::onLocation)
        main.post(tick)
        KeepAliveService.start(this) { finishAndRemoveTask() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (parked.isMenuOpen) parked.close() else moveTaskToBack(true)
    }

    private fun toggleDemoPanel() {
        val panelScroll = findViewById<View>(R.id.panelScroll)
        panelScroll.visibility = if (panelScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    override fun onStop() {
        profiles.save(habits.profile)
        profiles.score = score
        super.onStop()
    }

    override fun onDestroy() {
        KeepAliveService.stop(this)
        main.removeCallbacks(tick)
        sources.forEach { it.stop() }
        location?.stop()
        profiles.save(habits.profile)

        player.cancel()
        speaker.shutdown()
        voiceInput?.release()
        brain.release()
        car.release()
        parked.companion.idle = false
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) {
            sources.forEach { it.stop() }
            startSources()
        }
    }

    private fun startSources() {
        sources.forEach { src ->
            src.start { update -> main.post { if (useRealSignals) store.apply(update, SystemClock.elapsedRealtime()) } }
        }
    }

    private fun onTick(now: Long) {
        val s = store.snapshot(now)
        habits.update(s, now)
        driveSession.feed(s, now)?.let { d -> endDrive(d) }
        tickSuggestions(s, now)
        checkUnlocks()
        if (!player.playing) {
            situations.tick(s, engine.state.zone, habits.profile, LocalDateTime.now(), now, lastSpokeAtMs).forEach(::submitSituation)
        }

        when (val d = engine.tick(s, now, allowNewSpeech = !listening || openListening)) {
            is Decision.Speak -> {
                if (openListening) stopListening()
                if (d.message.priority == Priority.URGENT && !settings.safetyVoice) main.post { onSpeechDone(d.message.id) }
                else speaker.speak(d.message.id, d.text)
                lastSpokeAtMs = now
                showLine(d.text, now)
                panel.graph.mark(now, LoadGraphView.Mark.SPOKE)
                question = d.message.takeIf { DemoContent.asksQuestion(it, d.text) || suggestionOf(it)?.requiresReply == true }
                if (suggestionOf(d.message)?.kind == SuggestionKind.STORY) parked.bumpAch(AchKey.STORIES)
                addDiary("🗣 ${d.text.take(28)}…")
            }
            is Decision.Interrupt -> {
                speaker.stop()
                if (suggestionOf(d.message) != null) suggestions.cancelAwaiting()
                question = null
                showLine(Lines.interrupted(avatar.personality), now)
                panel.graph.mark(now, LoadGraphView.Mark.INTERRUPTED)
            }
            Decision.None -> Unit
        }
        engine.state.speaking?.let { if (suggestionOf(it) != null) suggestions.onTtsFinished(now) }
        if (listening && engine.state.zone == LoadZone.RED) stopListening()
        maybeOpenListen(now)

        if (now - lastSaveAtMs > SAVE_EVERY_MS) {
            profiles.save(habits.profile)
            profiles.score = score
            lastSaveAtMs = now
        }
        render(s, now)
    }

    private fun today() = LocalDate.now().toEpochDay()

    private fun applySettings() {
        val s = settings
        parked.settings = s
        speaker.volume = s.voiceVolume / 100f
        speaker.voiceStyle = s.voiceStyle ?: when (avatar.personality) {
            ai.nunchi.mate.avatar.Personality.ENERGETIC, ai.nunchi.mate.avatar.Personality.PLAYFUL -> VoiceStyle.BRIGHT
            ai.nunchi.mate.avatar.Personality.CALM, ai.nunchi.mate.avatar.Personality.COOL -> VoiceStyle.CALM
            ai.nunchi.mate.avatar.Personality.WARM -> VoiceStyle.SOFT
        }
        suggestions.replyTimeoutMs = s.replyWait.ms
        suggestions.disabledKinds = s.disabledKinds
        suggestions.noReplyIsDecline = s.noReplyIsDecline
        prefs.suggestionsEnabled = s.suggestionsEnabled
        driving.captions = s.captions
        driving.minimal = s.minimalDriving
    }

    private fun diagnostics(): List<Pair<String, String>> = listOf(
        "음성 엔진" to speaker.engineName,
        "음성 인식" to (if (voiceInput != null) "연결" else "없음 (화면 버튼으로 대답)"),
        "내비 연동" to (if (navi != null) "연결" else "없음"),
        "차량 신호" to sources.joinToString(", ") { it::class.simpleName ?: "?" }.ifEmpty { "없음" },
    )

    private fun grantXp(reason: XpReason, requested: Int = reason.xp): Int {
        val (s, got) = score.grant(today(), reason, requested)
        score = s
        return got
    }

    private fun endDrive(d: DriveSummary) {
        val hour = LocalTime.now().hour
        val (s, bonuses) = score.endDrive(today(), d, dawn = hour in 3..4)
        score = s
        if (d.rainy) parked.bumpAch(AchKey.RAIN_DRIVE)
        if (hour >= 22 || hour < 5) parked.bumpAch(AchKey.NIGHT_DRIVE)
        if (hour in 3..4) parked.bumpAch(AchKey.DAWN_DRIVE)
        AchKey.commute(java.time.LocalDate.now().dayOfWeek, hour)?.let { parked.bumpAch(it) }
        val gained = bonuses.mapNotNull { r ->
            val got = grantXp(r); habits.addXp(got)
            if (got > 0) { addDiary("⭐ ${r.label} +$got"); "${r.label} +$got" } else null
        }
        profiles.lastDrive = gained.joinToString(" · ").ifEmpty { "오늘도 함께해서 즐거웠어요" }
        profiles.save(habits.profile)
        profiles.score = score
    }

    private fun noteRegionVisit(v: RegionVisit) {
        val reason = when (v) {
            RegionVisit.FIRST -> { score = score.visitNewPlace(); parked.rememberFirstPlace(); XpReason.NEW_PLACE }
            RegionVisit.REVISIT -> XpReason.REVISIT
            RegionVisit.REGULAR -> XpReason.REGULAR
            RegionVisit.NONE -> return
        }
        val got = grantXp(reason); habits.addXp(got)
        if (got > 0) addDiary("⭐ ${reason.label} +$got")
        profiles.save(habits.profile)
        profiles.score = score
    }

    private fun tickSuggestions(s: DrivingSignals, now: Long) {
        if (s.decelerationMps2 >= HARD_BRAKE_MPS2) lastHardBrakeMs = now
        if (s.gear == Gear.DRIVE) {
            if (driveStartedMs == null) driveStartedMs = now
        } else {
            driveStartedMs = null
        }
        speedHistory.addLast(now to s.speedKmh)
        while (speedHistory.isNotEmpty() && now - speedHistory.first().first > SPEED_WINDOW_MS) speedHistory.removeFirst()
        val speedDelta = (speedHistory.maxOf { it.second } - speedHistory.minOf { it.second }).toDouble()

        suggestions.onGear(s.gear)
        if (s.gear == Gear.PARK) offered.clear()
        suggestions.onLocationSample(now) { placeDistanceM }

        val e = engine.state
        val ctx = DriveContext(
            gear = s.gear,
            msSinceHardBrake = now - lastHardBrakeMs,
            turnSignalOn = s.turnSignal != TurnSignal.OFF,
            brakePedalPercent = if (s.brakePressed) 100 else 0,
            speedDeltaKmh10s = speedDelta,
            msSinceLastSpeech = Long.MAX_VALUE,
            safetyAlertActive = e.speaking?.priority == Priority.URGENT,
            metersToNextTbt = s.nextTurnDistanceM?.toDouble() ?: Double.MAX_VALUE,
            hourOfDay = LocalTime.now().hour,
            drivingMinutes = driveStartedMs?.let { ((now - it) / 60_000L).toInt() } ?: 0,
            batterySoc = batterySoc,
            wiperOn = s.wiper != WiperLevel.OFF,
            nowMs = now,
        )
        val calm = e.zone == LoadZone.GREEN && e.calmForMs >= CALM_HOLD_MS
        if (!calm || e.speaking != null || listening || e.pending > 0) return
        if (!settings.canSuggest(LocalTime.now().hour)) return
        while (offerTimes.isNotEmpty() && now - offerTimes.first() > 3_600_000L) offerTimes.removeFirst()
        if (offerTimes.size >= settings.talkativeness.maxPerHour) return
        val picked = suggestions.tick(ctx, prefs, scorer) ?: return
        offerTimes.addLast(now)

        submit(Message(id = "sug-${picked.id}", text = picked.script, priority = Priority.SUGGESTION, createdAtMs = now))
    }

    private fun suggestionOf(m: Message): Suggestion? =
        if (m.id.startsWith("sug-")) offered[m.id.removePrefix("sug-")] else null

    private fun render(s: DrivingSignals, now: Long) {
        val e = engine.state
        val p = habits.profile
        val isParked = e.zone == LoadZone.PARKED
        driving.root.visibility = if (isParked) View.GONE else View.VISIBLE
        parked.root.visibility = if (isParked) View.VISIBLE else View.GONE
        parked.companion.idle = isParked && settings.animations

        val waitingAnswer = listening && !openListening
        val mood = when {
            waitingAnswer -> Mood.LISTENING
            e.speaking != null -> Mood.TALKING
            e.zone == LoadZone.RED -> Mood.FOCUS
            isParked -> Mood.HAPPY
            else -> Mood.CALM
        }
        val vs = when {
            isParked -> VehicleState.PARKED
            e.zone == LoadZone.RED -> VehicleState.ATTENTION
            listening || e.speaking != null -> VehicleState.SUGGESTING
            else -> VehicleState.CRUISE
        }
        val dc = driving.companion
        dc.mood = mood
        dc.look = avatar.look
        dc.gender = if (avatar.gender == ai.nunchi.mate.avatar.Gender.MALE) "m" else "f"
        dc.vehicleState = vs
        parked.companion.vehicleState = vs
        parked.liveMood = when { listening -> Mood.LISTENING; e.speaking != null -> Mood.TALKING; else -> null }

        if (e.speaking == null && !waitingAnswer && now - lastLineAtMs > LINE_HOLD_MS) {
            driving.setLine(
                when (e.zone) {
                    LoadZone.RED -> ""
                    LoadZone.YELLOW -> Lines.yellow(avatar.personality)
                    LoadZone.GREEN, LoadZone.PARKED -> avatar.personality.greeting
                },
            )
        }
        driving.render(e.zone, e.pending, waitingAnswer)
        renderListenButton()
        if (isParked) {
            if (now - lastParkedRenderMs >= PARKED_RENDER_MS) { lastParkedRenderMs = now; parked.render(p, avatar, diary.toList(), score) }
            if (!avatarStore.tutorialDone && !tutorial.isShowing) tutorial.start()
        }
        panel.graph.add(now, e.score)
        if (e.zone != loggedZone || abs(e.score - loggedScore) >= 5f) {
            Log.d("Engine", "load ${e.score.toInt()} ${e.zone}")
            loggedScore = e.score
            loggedZone = e.zone
        }
        panel.render(s, e, speaker.engineName)
    }

    private fun onSpeechDone(id: String) {
        engine.onSpeechDone(id)
        val now = SystemClock.elapsedRealtime()
        if (id.startsWith("sug-")) suggestions.onTtsFinished(now)
        openListenAfterMs = SystemClock.elapsedRealtime() + OPEN_LISTEN_AFTER_SPEECH_MS
        val q = question ?: return
        if (q.id == id && engine.state.zone != LoadZone.RED) startListening(q)
    }

    private fun startListening(q: Message) {
        val input = voiceInput ?: return
        val timeout = Runnable {
            if (!listening) return@Runnable
            stopListening()
            onListenEnd(q)
        }
        val started = input.listen(
            onResult = { text -> main.post { onReply(q, text) } },
            onEnd = { main.postDelayed({ onListenEnd(q) }, LISTEN_END_GRACE_MS) },
            onReady = {
                main.post {
                    if (!listening) return@post
                    main.removeCallbacks(timeout)
                    main.postDelayed(timeout, LISTEN_TIMEOUT_MS)
                }
            },
        )
        if (!started) return
        listening = true
        showLine("🎤 '응' 또는 '아니'로 말해 주세요", SystemClock.elapsedRealtime())
        main.postDelayed(timeout, LISTEN_READY_LIMIT_MS + LISTEN_TIMEOUT_MS)
    }

    private fun onListenEnd(q: Message) {
        listening = false
        if (question?.id != q.id || suggestionOf(q) != null) return
        if (q.priority == Priority.SUGGESTION) { question = null; return }
        question = null
        pendingCar.remove(q.id)
        submit(situations.noAnswer(SystemClock.elapsedRealtime()))
    }

    private fun stopListening() {
        if (!listening) return
        voiceInput?.stop()
        listening = false
        openListening = false
    }

    private fun setAlwaysListen(on: Boolean) {
        if (on && voiceInput == null) {
            panel.caption("🎙 음성 인식이 없는 빌드예요(Pleos SDK 미포함)")
            return
        }
        alwaysListen = on
        profiles.alwaysListen = on
        if (!on && openListening) stopListening()
        openListenAfterMs = 0
        addDiary(if (on) "🎙 항상 듣기 켬" else "🎙 항상 듣기 끔")
        renderListenButton()
    }

    private fun maybeOpenListen(now: Long) {
        if (!alwaysListen || listening || player.playing || now < openListenAfterMs) return
        val e = engine.state
        if (e.speaking != null || question != null || e.zone == LoadZone.RED) return
        if (e.pending > 0 && e.zone != LoadZone.YELLOW) return
        val input = voiceInput ?: return
        val seq = ++openSeq
        val started = input.listen(
            onResult = { text -> main.post { if (seq == openSeq) onOpenUtterance(text) } },
            onEnd = { main.postDelayed({ if (seq == openSeq) endOpenListen(OPEN_LISTEN_GAP_MS) }, LISTEN_END_GRACE_MS) },
        )
        if (!started) {
            openListenAfterMs = now + OPEN_LISTEN_RETRY_MS
            return
        }
        listening = true
        openListening = true
        main.postDelayed({ if (seq == openSeq && openListening) { stopListening(); endOpenListen(OPEN_LISTEN_GAP_MS) } }, OPEN_LISTEN_LIMIT_MS)
    }

    private fun endOpenListen(gapMs: Long) {
        if (openListening) {
            listening = false
            openListening = false
        }
        openListenAfterMs = maxOf(openListenAfterMs, SystemClock.elapsedRealtime() + gapMs)
    }

    private fun onOpenUtterance(text: String) {
        if (text.isBlank()) return
        stopListening()
        endOpenListen(OPEN_LISTEN_AFTER_UTTERANCE_MS)
        val t = text.trim()
        if (t.length <= SHORT_REPLY_MAX && ReplyParser.parse(t) != Reply.UNKNOWN) return
        addDiary("🎙 \"$text\"")
        converse(text)
    }

    private fun renderListenButton() {
        val (label, bg, fg) = when {
            !alwaysListen -> Triple("🎙 항상 듣기 꺼짐", "#E3E6EB", "#15181D")
            openListening -> Triple("🎙 듣는 중…", "#3D7BFF", "#FFFFFF")
            else -> Triple("🎙 항상 듣기 켜짐", "#22B07D", "#FFFFFF")
        }
        if (listenButton.text != label) listenButton.text = label
        listenButton.setTextColor(Color.parseColor(fg))
        listenButton.background = GradientDrawable().apply { cornerRadius = 999f; setColor(Color.parseColor(bg)) }
    }

    private fun onReply(q: Message, text: String) {
        if (text.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        addDiary("🎤 \"$text\"")
        val reply = ReplyParser.parse(text)
        if (suggestionOf(q) == null && reply == Reply.UNKNOWN) {
            question = null
            converse(text)
        } else {
            applyReply(q, reply)
        }
        stopListening()
    }

    private fun applyReply(q: Message, reply: Reply) {
        val now = SystemClock.elapsedRealtime()
        val sug = suggestionOf(q)
        if (sug != null) {
            question = null
            suggestions.onReply(reply, prefs)
            if (reply != Reply.UNKNOWN) {

                parked.bumpAch(AchKey.ANSWERED)
                val got = grantXp(XpReason.SUGGESTION_ANSWERED); habits.addXp(got)
                if (got > 0) addDiary("⭐ ${XpReason.SUGGESTION_ANSWERED.label} +$got")
            }
            when (reply) {
                Reply.YES -> {
                    parked.bumpAch(AchKey.ACCEPTED); parked.setAch(AchKey.REJECT_STREAK, 0)
                    when (sug.kind) {
                        SuggestionKind.REST -> parked.bumpAch(AchKey.REST_AREAS)
                        SuggestionKind.DRIVE_THRU -> { parked.bumpAch(AchKey.DRIVE_THRU); if (store.snapshot(now).wiper != WiperLevel.OFF) parked.bumpAch(AchKey.RAIN_DRIVE_THRU) }
                        else -> Unit
                    }
                    val got = grantXp(XpReason.SUGGESTION_ACCEPTED); habits.addXp(got)
                    if (got > 0) addDiary("⭐ ${XpReason.SUGGESTION_ACCEPTED.label} +$got")
                    submit(DemoContent.suggestionAccepted(sug, now))
                    if (sug.kind != SuggestionKind.STORY) {
                        navi?.requestRoute(Destination(sug.placeName, sug.lat, sug.lng, poiId = "", address = ""))
                    }
                }
                Reply.NO -> { parked.bumpAch(AchKey.REJECTED); parked.bumpAch(AchKey.REJECT_STREAK); submit(DemoContent.suggestionDeclined(now)) }
                Reply.LATER -> submit(DemoContent.suggestionLater(now))
                Reply.UNKNOWN -> Unit
            }
            return
        }
        when (reply) {
            Reply.YES -> answer(q, accepted = true, now)
            Reply.NO -> answer(q, accepted = false, now)
            Reply.LATER, Reply.UNKNOWN -> Unit
        }
    }

    private fun answer(q: Message, accepted: Boolean, now: Long) {
        question = null
        val command = pendingCar.remove(q.id)
        when {
            command != null && accepted -> runCar(command)
            accepted -> submit(DemoContent.accepted(q, now))
            else -> submit(DemoContent.declined(now))
        }
    }

    private fun onNaviEvent(text: String) {
        val now = SystemClock.elapsedRealtime()
        val messages = when (text) {
            "목적지 도착" -> situations.onArrived(now)
            "경로 안내 취소" -> emptyList<Message>().also { situations.onRouteCancelled() }
            else -> SituationDetector.parseRouteStarted(text)
                ?.let { (name, km, minutes) -> situations.onRouteStarted(name, km, minutes, LocalDateTime.now(), now) }
                .orEmpty()
        }
        if (!player.playing) messages.forEach(::submitSituation)
    }

    private fun onLocation(address: String, lat: Double, lon: Double) {
        if (player.playing) return
        val region = Region.locate(address, lat, lon)
        if (region != null && region != lastRegion) {
            lastRegion = region
            parked.place = region.spoken
            Log.d("Situation", "region ${region.spoken}")
        }
        noteRegionVisit(situations.onRegion(region, LocalDateTime.now()))
    }

    private fun submitSituation(m: Message) {
        Log.d("Situation", "${m.id} ${m.priority} \"${m.text}\"")
        submit(m)
    }

    private fun converse(utterance: String) {
        val now = SystemClock.elapsedRealtime()
        val car = CarState.of(engine.state, store.snapshot(now))
        if (!car.aiAllowed) {
            panel.caption("🧠 운전 부하 RED: 대화 AI를 부르지 않아요")
            return
        }
        panel.caption("🧠 ${brain.name} 생각 중…")
        if (engine.state.zone == LoadZone.PARKED) parked.showThinking()
        brain.reply(ChatRequest(utterance, car, habits.profile.persona)) { a -> main.post { onBrainAnswer(a) } }
    }

    private fun onBrainAnswer(a: BrainAnswer) {
        Log.i("Brain", "${a.by} ${a.latencyMs}ms topic=${a.topic.wire} command=${a.command?.action} confirm=${a.confirm}")
        val command = a.command
        if (command != null && !a.confirm) {
            runCar(command)
            return
        }
        val id = ChatQuestion.messageId(a.reply.intent, ++brainSeq)
        if (command != null) pendingCar[id] = command
        submit(a.reply.toMessage(id, SystemClock.elapsedRealtime()))
        panel.caption("🧠 ${a.by} · ${a.latencyMs}ms · 분류 ${a.topic.wire}${command?.let { " · 물어봄 ${it.action}" }.orEmpty()}")
    }

    private fun runCar(command: CarCommand) {
        val started = SystemClock.elapsedRealtime()
        panel.caption("🚗 ${car.name}: ${command.action} 실행 중…")
        car.execute(command) { r ->
            main.post {
                val now = SystemClock.elapsedRealtime()
                val line = if (r.ok) CarLines.done(command, r.celsius) else CarLines.failed(command)
                submit(ChatReply(line, null, ChatIntent.CAR_CONTROL).toMessage("car-${++brainSeq}", now))
                addDiary("🚗 $line")
                panel.caption("🚗 ${car.name} · ${now - started}ms · ${if (r.ok) "성공" else "실패"} ${r.detail}".trimEnd())
            }
        }
    }

    private fun showLine(text: String, now: Long) {
        driving.setLine(text)
        if (engine.state.zone == LoadZone.PARKED && text.isNotEmpty()) parked.showSpoken(text)
        lastLineAtMs = now
    }

    private fun checkUnlocks() {
        val level = habits.profile.level
        if (level == voiceLevel) return
        val t = SystemClock.elapsedRealtime()
        VoiceOption.unlockedBetween(voiceLevel, level).forEach { v ->
            addDiary("🔓 새 목소리: ${v.label}")
            submit(Message("unlock-${v.id}-$t", v.unlockLine, Priority.NORMAL, t, ttlMs = 10 * 60_000))
        }
        voiceLevel = level
        panel.setVoice(speaker.voice, voiceOpenLevel)
    }

    private fun addDiary(entry: String) {
        val t = LocalTime.now()
        diary.addFirst("%02d:%02d  %s".format(t.hour, t.minute, entry))
        while (diary.size > 10) diary.removeLast()
    }

    override fun signal(update: SignalUpdate) = store.apply(update, SystemClock.elapsedRealtime())

    override fun submit(message: Message) {
        if (message.text.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        engine.submit(message)
        val zone = engine.state.zone
        if (zone == LoadZone.RED || zone == LoadZone.YELLOW) panel.graph.mark(now, LoadGraphView.Mark.HELD)
    }

    override fun caption(text: String) = panel.caption(text)
    override fun profile(): CompanionProfile = habits.profile

    override fun play(scenario: Scenario) {
        speaker.stop()
        stopListening()
        engine.reset()
        question = null
        lastLineAtMs = 0
        useRealSignals = false
        panel.setRealSignals(false)
        panel.graph.clear()
        player.play(scenario)
    }

    override fun inject(factory: (Long) -> Message) = submit(factory(SystemClock.elapsedRealtime()))

    override fun acceptSuggestion() {
        val q = question ?: return
        stopListening()
        applyReply(q, Reply.YES)
    }

    override fun useRealSignals(enabled: Boolean) {
        useRealSignals = enabled
    }

    override fun requestRoute(destination: Destination) {
        useRealSignals = true
        panel.setRealSignals(true)
        navi?.requestRoute(destination)
    }

    override fun cancelRoute() {
        navi?.cancelRoute()
    }

    override fun offerSuggestion(kind: SuggestionKind) {
        val s = DemoContent.candidate(kind, SystemClock.elapsedRealtime())
        offered[s.id] = s
        suggestions.offer(s)
        panel.caption("제안 후보 ${s.placeName} (${kind.name}) 대기 중 · 점수 %.1f (문턱 %.0f)".format(scoreNow(s), SuggestionScorer.THRESHOLD))
    }

    override fun reply(reply: Reply) {
        val q = question ?: return
        stopListening()
        applyReply(q, reply)
    }

    override fun placeDistance(meters: Double) {
        placeDistanceM = meters
    }

    override fun batterySoc(percent: Int) {
        batterySoc = percent
    }

    private fun scoreNow(s: Suggestion): Double {
        val now = SystemClock.elapsedRealtime()
        val sig = store.snapshot(now)
        return scorer.score(
            s,
            DriveContext(hourOfDay = LocalTime.now().hour, batterySoc = batterySoc, wiperOn = sig.wiper != WiperLevel.OFF, nowMs = now),
            prefs,
        )
    }

    override fun askBrain(utterance: String) {
        addDiary("🎤 \"$utterance\"")
        if (ReplyParser.parse(utterance) == Reply.UNKNOWN) converse(utterance)
    }

    override fun selectVoice(option: VoiceOption) {
        if (!option.isUnlocked(voiceOpenLevel)) {
            panel.caption("🔒 ${option.label} 목소리는 Lv.${option.unlockLevel}에 열려요")
            return
        }
        speaker.voice = option
        profiles.voice = option
        panel.setVoice(option, voiceOpenLevel)
        speakNow("voice-${option.id}", option.sample)
    }

    private fun speakNow(kind: String, text: String) {
        engine.state.speaking?.let { speaker.stop(); engine.onSpeechDone(it.id) }
        val now = SystemClock.elapsedRealtime()
        submit(Message("$kind-$now", text, Priority.NORMAL, now, ttlMs = 60_000))
    }

    override fun visitRegion(address: String) {
        val region = Region.parse(address) ?: return
        lastRegion = region
        parked.place = region.spoken
        panel.caption("📍 ${region.spoken}에 들어섰어요(시연)")
        noteRegionVisit(situations.onRegion(region, LocalDateTime.now()))
    }

    override fun addXp(amount: Int) {
        habits.addXp(amount)
        profiles.save(habits.profile)
        checkUnlocks()
    }

    private companion object {
        const val TICK_MS = 250L
        const val PARKED_RENDER_MS = 500L
        const val LINE_HOLD_MS = 8_000L
        const val LISTEN_TIMEOUT_MS = 8_000L

        const val LISTEN_END_GRACE_MS = 800L

        const val LISTEN_READY_LIMIT_MS = 8_000L

        const val OPEN_LISTEN_LIMIT_MS = 20_000L
        const val OPEN_LISTEN_GAP_MS = 300L
        const val OPEN_LISTEN_AFTER_SPEECH_MS = 600L
        const val OPEN_LISTEN_AFTER_UTTERANCE_MS = 2_000L
        const val OPEN_LISTEN_RETRY_MS = 2_000L
        const val SHORT_REPLY_MAX = 4
        const val SAVE_EVERY_MS = 30_000L
        const val REQ_PERMISSIONS = 1
        const val HARD_BRAKE_MPS2 = 3f
        const val SPEED_WINDOW_MS = 10_000L
        const val CALM_HOLD_MS = 3_000L
    }
}
