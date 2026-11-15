package ai.nunchi.mate.ui

import ai.nunchi.engine.AchCategory
import ai.nunchi.engine.AchKey
import ai.nunchi.engine.AchState
import ai.nunchi.engine.Achievement
import ai.nunchi.engine.Achievements
import ai.nunchi.engine.AddResult
import ai.nunchi.engine.Background
import ai.nunchi.engine.Cheer
import ai.nunchi.engine.FriendDirectory
import ai.nunchi.engine.FriendProfile
import ai.nunchi.engine.FriendState
import ai.nunchi.engine.RankAccount
import ai.nunchi.engine.RankBoard
import ai.nunchi.engine.RankEntry
import ai.nunchi.engine.Ranking
import ai.nunchi.engine.Stylist
import ai.nunchi.engine.StylingState
import ai.nunchi.engine.BuyResult
import ai.nunchi.engine.Catalog
import ai.nunchi.engine.ItemSource
import ai.nunchi.engine.Season
import ai.nunchi.engine.ShopItem
import ai.nunchi.engine.Tier
import ai.nunchi.engine.CompanionProfile
import ai.nunchi.engine.LobbyState
import ai.nunchi.engine.MemoryCard
import ai.nunchi.engine.LevelTitles
import ai.nunchi.engine.MoodBand
import ai.nunchi.engine.ScoreState
import ai.nunchi.engine.VoiceOption
import ai.nunchi.engine.VoiceTier
import ai.nunchi.engine.XpReason
import ai.nunchi.engine.suggest.SuggestionKind
import ai.nunchi.mate.BuildConfig
import ai.nunchi.mate.ProfileStore
import ai.nunchi.mate.R
import ai.nunchi.mate.ReplyWait
import ai.nunchi.mate.Settings
import ai.nunchi.mate.SettingsStore
import ai.nunchi.mate.Talkativeness
import ai.nunchi.mate.avatar.Personality
import ai.nunchi.mate.voice.VoiceStyle
import android.widget.Switch
import ai.nunchi.mate.avatar.Avatar
import ai.nunchi.mate.avatar.Gender
import ai.nunchi.mate.avatar.AvatarStore
import ai.nunchi.mate.demo.DemoContent
import ai.nunchi.mate.demo.Lines
import ai.nunchi.mate.signals.Destination
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

enum class HomeMenu(val icon: String, val label: String, val left: Boolean, val iconRes: Int) {
    RANKING("🏆", "순위", true, R.drawable.ic_menu_ranking),
    STATUS("📊", "캐릭터 상태", true, R.drawable.ic_menu_status),
    SHOP("🛍️", "상점", true, R.drawable.ic_menu_shop),
    SETTINGS("⚙️", "설정", false, R.drawable.ic_menu_settings),
    WARDROBE("👕", "옷장", false, R.drawable.ic_menu_wardrobe),
    ACHIEVEMENTS("🎖️", "업적", false, R.drawable.ic_menu_achievements),
    FRIENDS("👥", "친구", false, R.drawable.ic_friend_add),
}

class HomeScreen(
    private val activity: Activity,
    private val avatarStore: AvatarStore,
    private val profiles: ProfileStore,
    private val onLook: (CharacterLook) -> Unit,
    private val onRecreate: () -> Unit,
    private val onReplayTutorial: () -> Unit,
    private val onDemoPanel: () -> Unit,
    private val onRoute: (Destination) -> Unit = {},
    private val onXp: (XpReason) -> Unit = {},
    private val onSettings: (Settings) -> Unit = {},
    private val onAvatar: (Avatar) -> Unit = {},
    private val onDiagnostics: () -> List<Pair<String, String>> = { emptyList() },
    private val onWipe: () -> Unit = {},
    private val onStyling: (StylingState) -> Unit = {},
    private val currentVoice: () -> VoiceOption = { VoiceOption.DEFAULT },
    private val onVoice: (VoiceOption) -> Unit = {},
    private val onSpeak: (String) -> Unit = {},
) {
    var settings = Settings()
    val root: View = activity.findViewById(R.id.parkedView)
    val companion: CompanionView = activity.findViewById(R.id.parkedCompanion)
    private val statik = StaticRig.get(activity)
    private val name: TextView = activity.findViewById(R.id.parkedName)
    private val greeting: TextView = activity.findViewById<TextView>(R.id.parkedGreeting).apply { breakByPhrase() }
    private val menuLeft: LinearLayout = activity.findViewById(R.id.menuLeft)
    private val menuRight: LinearLayout = activity.findViewById(R.id.menuRight)
    private val overlay: FrameLayout = activity.findViewById(R.id.menuOverlay)
    private val overlayCard: View = activity.findViewById(R.id.overlayCard)
    private val overlayScrim: View = activity.findViewById(R.id.overlayScrim)
    private val overlayTitle: TextView = activity.findViewById(R.id.overlayTitle)
    private val overlayBody: LinearLayout = activity.findViewById(R.id.overlayBody)
    val menuButtons = mutableMapOf<HomeMenu, View>()
    private val menuDots = mutableMapOf<HomeMenu, View>()
    private val main = Handler(Looper.getMainLooper())

    private val barSeason = text("", 24f, "#15181D")
    private val barLevel = text("", 24f, "#15181D").apply { paint.isFakeBoldText = true }
    private val barXp = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = CompanionProfile.XP_PER_LEVEL
        progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#3D7BFF"))
    }
    private val barCoins = text("", 24f, "#15181D")
    private val bar = LinearLayout(activity)

    private val chips = LinearLayout(activity)
    private val chipMission = chip("📋", "오늘의 미션", { openSheet("📋 오늘의 미션") { fillMissions() } }, R.drawable.ic_chip_mission)
    private val chipSnack = chip("🍪", "간식 🪙${LobbyState.SNACK_COST}", { snack() }, R.drawable.ic_chip_snack)
    private val chipCalendar = chip("📅", "출석", { claimStreak(); openSheet("📅 출석 달력") { fillCalendar() } }, R.drawable.ic_chip_calendar)
    private val chipRoute = chip("🚗", "출발 준비", { openSheet("🚗 출발 준비") { fillRoute() } }, R.drawable.ic_chip_route)
    private val chipPhoto = chip("📷", "", { snapshot() }, R.drawable.ic_chip_photo)
    private val chipHide = chip("👁", "", { toggleHide() }, R.drawable.ic_chip_hide)
    private val chipDot = TextView(activity)

    private var lobby = profiles.lobby
    private var ach = profiles.ach
    private var inventory = profiles.inventory
    private var styling = profiles.styling
    private var friends = profiles.friends.let { f -> if (f.seeded) f else f.seed().also { profiles.friends = it } }
    private var friendOpen: String? = null
    private var rankBoard = RankBoard.LEVEL
    private var rankByCar = false
    private var rankOpen: String? = null
    private val rankHearted = mutableSetOf<String>()
    private var lastShown: List<Any?> = emptyList()
    private var previewMood: Mood? = null
    var liveMood: Mood? = null
        set(v) { if (field != v) { field = v; companion.mood = currentMood() } }
    private fun currentMood() = previewMood ?: liveMood ?: lobbyMood()
    private var wardrobeTab = "hair"
    private val stage: View get() = companion.parent as View
    private lateinit var bubble: LinearLayout
    private val undoStack = ArrayDeque<CharacterLook>()
    private var wardrobeOwnedOnly = false
    private var lastSetEffect: Season? = null
    private var previewLook: CharacterLook? = null
    private var shopFilter: String? = null
    private var shopSort: String? = null
    private var achFilter: String? = null
    private var lineUntilMs = 0L
    private var reactionUntilMs = 0L
    private var wokeUntilMs = 0L
    private var hidden = false
    private var lastLevel = -1
    private var profile = CompanionProfile()
    private var score = ScoreState()
    var place: String = ""
    private var recallDay = -1L
    private var albumAll = false
    private var avatar = Avatar()
    private var diary: List<String> = emptyList()
    private var open: HomeMenu? = null
    private var sheet: (() -> Unit)? = null

    private val boredRunnable = Runnable {
        if (root.visibility == View.VISIBLE && !isMenuOpen && !isSleepTime()) say(Lines.bored(avatar.personality))
    }

    init {
        HomeMenu.entries.forEach { m ->
            val b = menuButton(m)
            (if (m.left) menuLeft else menuRight).addView(b, LinearLayout.LayoutParams(dp(290), -2).apply { bottomMargin = dp(14) })
            menuButtons[m] = b
        }
        activity.findViewById<View>(R.id.overlayClose).setOnClickListener { close() }
        activity.findViewById<View>(R.id.overlayScrim).setOnClickListener { close() }
        overlayCard.isClickable = true
        overlay.elevation = dp(8).toFloat()
        activity.findViewById<View>(R.id.tutorialOverlay).elevation = dp(8).toFloat()

        bar.apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = 999f; setColor(Color.WHITE) }
            setPadding(dp(28), dp(12), dp(28), dp(12)); elevation = dp(2).toFloat()
            addView(barSeason, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) })
            addView(barLevel)
            addView(barXp, LinearLayout.LayoutParams(dp(200), dp(14)).apply { marginStart = dp(12); marginEnd = dp(24) })
            addView(barCoins)
            setOnClickListener { openMenu(HomeMenu.STATUS) }
        }
        val frame = root as FrameLayout
        frame.addView(bar, 1, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(20) })

        chips.apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            listOf(chipMission, chipSnack, chipCalendar, chipRoute, chipPhoto, chipHide).forEach {
                addView(it, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
            }
        }
        frame.addView(chips, 2, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(16) })
        chipDot.apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#FF4D4D")) }
            visibility = View.GONE
        }
        (chipMission as FrameLayout).addView(chipDot, FrameLayout.LayoutParams(dp(12), dp(12), Gravity.TOP or Gravity.END).apply { topMargin = dp(2); marginEnd = dp(2) })

        name.apply {
            background = GradientDrawable().apply { cornerRadius = 999f; setColor(Color.parseColor("#CC15181D")) }
            setTextColor(Color.WHITE); textSize = 26f; setPadding(dp(24), dp(6), dp(24), dp(8))
        }
        greeting.apply {
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(Color.WHITE) }
            setTextColor(Color.parseColor("#15181D")); textSize = 28f; maxWidth = dp(GREETING_MAX_DP); gravity = Gravity.CENTER
            setPadding(dp(30), dp(14), dp(30), dp(16)); elevation = dp(2).toFloat()
        }
        (greeting.parent as ViewGroup).removeView(greeting)
        greeting.translationZ = 1f
        bubble = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(greeting, LinearLayout.LayoutParams(-2, -2))
            val tail = android.graphics.Path().apply { moveTo(0f, 0f); lineTo(1f, 0.5f); lineTo(0f, 1f); close() }
            addView(View(activity).apply {
                background = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.PathShape(tail, 1f, 1f)).apply { paint.color = Color.WHITE }
            }, LinearLayout.LayoutParams(dp(34), dp(22)).apply { marginStart = -dp(2) })
        }
        (root as FrameLayout).addView(bubble, 1, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(20); topMargin = dp(GREETING_TOP_DP) })
        bubble.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placeBubble() }

        val detector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { talk(); return true }
            override fun onDoubleTap(e: MotionEvent): Boolean { doubleTap(); return true }
            override fun onLongPress(e: MotionEvent) { pet() }
            override fun onDown(e: MotionEvent) = true
        })
        companion.setOnTouchListener { v, e -> detector.onTouchEvent(e); if (e.action == MotionEvent.ACTION_UP) v.performClick(); true }
    }

    fun render(p: CompanionProfile, a: Avatar, diaryLines: List<String>, sc: ScoreState = score) {
        profile = p; avatar = a; diary = diaryLines; score = sc
        companion.look = previewLook ?: a.look
        companion.gender = shownRank()?.gender ?: if (a.gender == Gender.MALE) "m" else "f"
        applyNameTag()
        val today = LocalDate.now().toEpochDay()
        val newDay = lobby.lastVisitDay != today
        val visited = lobby.visit(today)
        if (visited != lobby) { lobby = visited; profiles.lobby = visited }
        if (newDay) {
            onXp(XpReason.ATTENDANCE)
            when (lobby.streak) { 7 -> onXp(XpReason.STREAK_7); 30 -> onXp(XpReason.STREAK_30) }
            val d = LocalDate.now()
            ach = ach.bump(AchKey.VISITS)
            if (LocalTime.now().hour < 6) ach = ach.bump(AchKey.EARLY_VISIT)
            if (d.monthValue == 1 && d.dayOfMonth == 1) ach = ach.bump(AchKey.NEW_YEAR)
            if (d.monthValue == 12 && d.dayOfMonth == 25) ach = ach.bump(AchKey.CHRISTMAS)
            if (profiles.birthday == String.format("%02d-%02d", d.monthValue, d.dayOfMonth)) ach = ach.bump(AchKey.BIRTHDAY)
        }
        if (lastLevel in 1 until p.level) {
            lobby = lobby.addCoins(LEVEL_UP_COINS); profiles.lobby = lobby
            say("🎉 Lv.${p.level}! 코인 +$LEVEL_UP_COINS")
        }
        lastLevel = p.level

        val friendShown = shownFriend()
        if (friendShown != null) greeting.text = friendGreeting(friendShown)
        else if (System.currentTimeMillis() > lineUntilMs) greeting.text = defaultGreeting()
        companion.mood = currentMood()
        applyBackground()
        barSeason.text = seasonEmoji()
        barLevel.text = "Lv.${p.level}"
        barXp.progress = p.xpInLevel
        barCoins.text = "🪙 ${coinLabel(lobby.coins)}"
        chipDot.visibility = if (lobby.canClaimMissions(today, score.tripsOn(today))) View.VISIBLE else View.GONE
        menuDots[HomeMenu.STATUS]?.visibility = chipDot.visibility
        profiles.lastDrive?.let { profiles.lastDrive = null; showLastDrive(it) }
        menuDots[HomeMenu.FRIENDS]?.visibility = if (friends.requests.isNotEmpty() || friends.incomingGifts(today).isNotEmpty()) View.VISIBLE else View.GONE
        checkAchievements()
        scheduleBored()
        val shown = listOf(p, a, sc, diaryLines, lobby, ach, inventory, styling, friends, today)
        if (shown != lastShown) {
            lastShown = shown
            open?.let { fill(it) }
            sheet?.invoke()
        }
    }

    fun showSpoken(line: String) = say(line, maxOf(SPOKEN_MS, line.length * 180L))

    fun showThinking() = say("🤔 음… 생각 중이에요", THINK_MS)

    private fun say(line: String, ms: Long = LINE_MS) {
        greeting.text = line
        lineUntilMs = System.currentTimeMillis() + ms
        main.postDelayed({ if (System.currentTimeMillis() >= lineUntilMs) { greeting.text = defaultGreeting(); companion.mood = currentMood() } }, ms + 50)
        scheduleBored()
    }

    private fun speakLine(line: String, ms: Long = LINE_MS) {
        say(line, ms)
        onSpeak(line.replace(Regex("^[^가-힣A-Za-z0-9]+"), ""))
    }

    private fun talk() {
        val today = LocalDate.now().toEpochDay()
        lobby = lobby.talk(today); profiles.lobby = lobby
        bumpAch(AchKey.TALKS)
        if (isSleepTime() && System.currentTimeMillis() > wokeUntilMs) {
            wokeUntilMs = System.currentTimeMillis() + WAKE_MS
            companion.mood = Mood.HAPPY
            speakLine(Lines.wake(avatar.personality))
        } else {
            val recall = if (recallDay != today) ach.recall(today, Math.floorMod(today, 1_000_000L).toInt())?.recallLine(today) else null
            if (recall != null) { recallDay = today; speakLine("💭 $recall", LAST_DRIVE_MS) }
            else if (profiles.fortuneDay != today) {
                profiles.fortuneDay = today
                profiles.fortuneIndex = (profiles.fortuneIndex + 1 + (today % 3).toInt()) % Lines.fortunes.size
                speakLine("🔮 ${Lines.fortunes[profiles.fortuneIndex]}", LAST_DRIVE_MS)
            } else speakLine(Lines.tap(avatar.personality, LocalTime.now().hour))
        }
        chipDot.visibility = if (lobby.canClaimMissions(today, score.tripsOn(today))) View.VISIBLE else View.GONE
    }

    private fun doubleTap() {
        reactionUntilMs = System.currentTimeMillis() + REACTION_MS
        companion.mood = Mood.SURPRISED
        companion.animate().scaleX(1.08f).scaleY(0.94f).setDuration(120).withEndAction {
            companion.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
        }.start()
        speakLine(Lines.doubleTap(avatar.personality))
    }

    private fun pet() {
        val today = LocalDate.now().toEpochDay()
        val ok = lobby.canPet(today)
        if (ok) { lobby = lobby.pet(today); profiles.lobby = lobby }
        if (ok) bumpAch(AchKey.PETS)
        speakLine((if (ok) "♥ " else "") + Lines.pet(avatar.personality, ok))
    }

    private fun snack() {
        val today = LocalDate.now().toEpochDay()
        val ok = lobby.canSnack(today)
        if (ok) { lobby = lobby.snack(today); profiles.lobby = lobby }
        if (ok) bumpAch(AchKey.SNACKS)
        if (ok) showSnackArt()
        say((if (ok) "🍪 " else "") + Lines.snack(avatar.personality, ok))
        barCoins.text = "🪙 ${coinLabel(lobby.coins)}"
    }

    private fun showSnackArt() {
        val pick = listOf("candy", "cookie", "fruit").random()
        val art = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(runCatching { AssetImages.bitmap(activity, "illust/snack_$pick.webp") }.getOrNull() ?: return)
            alpha = 0f; translationY = dp(24).toFloat()
        }
        (root as FrameLayout).addView(art, FrameLayout.LayoutParams(dp(110), dp(110), android.view.Gravity.CENTER).apply { leftMargin = dp(400); topMargin = -dp(80) })
        art.animate().alpha(1f).translationY(0f).setDuration(260).withEndAction {
            art.animate().alpha(0f).translationY(-dp(30).toFloat()).setStartDelay(1100).setDuration(400).withEndAction { (root as FrameLayout).removeView(art) }.start()
        }.start()
    }

    private fun claimStreak() {
        val r = lobby.pendingStreakReward() ?: return
        lobby = lobby.claimStreakReward(); profiles.lobby = lobby
        say("🎁 ${r.days}일 연속 출석! 코인 +${r.coins}")
        barCoins.text = "🪙 ${coinLabel(lobby.coins)}"
    }

    private fun lobbyMood(): Mood {
        val now = System.currentTimeMillis()
        return when {
            now < reactionUntilMs -> Mood.SURPRISED
            isSleepTime() && now > wokeUntilMs -> Mood.SLEEPY
            lobby.moodBand == MoodBand.LOW || lobby.moodBand == MoodBand.BAD -> Mood.SAD
            else -> Mood.HAPPY
        }
    }

    private fun defaultGreeting(): String {
        shownRank()?.let { r -> return Personality.entries.firstOrNull { it.name == r.personality }?.greeting ?: "안녕하세요!" }
        val p = avatar.personality
        val today = LocalDate.now()
        val bd = profiles.birthday
        return when {
            bd != null && bd == "%02d-%02d".format(today.monthValue, today.dayOfMonth) -> "🎂 " + Lines.birthday(p, avatar.name)
            isSleepTime() && System.currentTimeMillis() > wokeUntilMs -> "zZ... (자는 중)"
            lobby.moodBand != MoodBand.GOOD && lobby.moodBand != MoodBand.GREAT -> Lines.moodLine(p, lobby.moodBand)
            else -> p.greeting
        }
    }

    private fun isSleepTime() = LocalTime.now().hour in SLEEP_FROM until SLEEP_TO

    private fun scheduleBored() {
        main.removeCallbacks(boredRunnable)
        if (root.visibility == View.VISIBLE) main.postDelayed(boredRunnable, BORED_MS)
    }

    private var bgLoaded: String? = null
    private fun backgroundFile(): String = bgFile(styling.background)
    private fun bgFile(b: Background): String = "bg/bg_lobby_" + when (b) {
        Background.AUTO -> when (LocalTime.now().hour) { in 6..9 -> "morning"; in 10..16 -> "day"; in 17..19 -> "evening"; else -> "night" }
        else -> b.name.lowercase()
    } + ".webp"
    private fun applyBackground() {
        val b = shownRank()?.let { runCatching { Background.valueOf(it.background) }.getOrNull() } ?: styling.background
        val f = bgFile(b)
        if (bgLoaded == f) return
        bgLoaded = f
        val cover = runCatching { CoverDrawable(AssetImages.bitmap(activity, f)) }.getOrNull()
        val overlay = when (b) {
            Background.SPRING -> "spring"; Background.SUMMER -> "summer"; Background.AUTUMN -> "autumn"; Background.WINTER -> "winter"
            else -> null
        }?.let { runCatching { CoverDrawable(AssetImages.bitmap(activity, "bg/bg_overlay_$it.webp")) }.getOrNull() }
        root.background = when {
            cover == null -> GradientDrawable().apply { setColor(backgroundColor()) }
            overlay == null -> cover
            else -> android.graphics.drawable.LayerDrawable(arrayOf(cover, overlay))
        }
    }

    private fun backgroundColor(): Int = Color.parseColor(
        when (styling.background) {
            Background.AUTO -> when (LocalTime.now().hour) {
                in 6..9 -> "#FFF6E5"
                in 10..16 -> "#F2F3F5"
                in 17..19 -> "#FFE9D9"
                else -> "#E4E8F3"
            }
            Background.MORNING -> "#FFF6E5"; Background.DAY -> "#F2F3F5"; Background.EVENING -> "#FFE9D9"; Background.NIGHT -> "#E4E8F3"
            Background.SPRING -> "#FFE8F0"; Background.SUMMER -> "#E0F4FF"; Background.AUTUMN -> "#FFE9CC"; Background.WINTER -> "#EEF3FA"
            Background.GARAGE -> "#E6E6E6"; Background.ROOFTOP -> "#D9DCEB"
        },
    )

    private fun seasonEmoji(): String = when (LocalDate.now().monthValue) {
        in 3..5 -> "🌸"; in 6..8 -> "🌊"; in 9..11 -> "🍁"; else -> "❄️"
    }

    private fun toggleHide() {
        hidden = !hidden
        val v = if (hidden) View.GONE else View.VISIBLE
        menuLeft.visibility = v; menuRight.visibility = v; bar.visibility = v
        for (i in 0 until chips.childCount) chips.getChildAt(i).let { if (it !== chipHide) it.visibility = if (hidden) View.GONE else View.VISIBLE }
    }

    private fun snapshot() {
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val wasHidden = hidden
        if (!wasHidden) toggleHide()
        chips.visibility = View.GONE
        val ov = overlay.visibility; overlay.visibility = View.GONE
        val tx = stage.translationX; stage.translationX = 0f
        root.draw(c)
        stage.translationX = tx; overlay.visibility = ov
        chips.visibility = View.VISIBLE
        if (!wasHidden) toggleHide()
        val dir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: activity.filesDir
        val f = File(dir, "${avatar.name}_${System.currentTimeMillis()}.png")
        runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            .onSuccess { Toast.makeText(activity, "저장했어요: ${f.name}", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(activity, "저장 실패", Toast.LENGTH_SHORT).show() }
    }

    private fun showLastDrive(text: String) {
        say("🚗 함께 다녀왔어요 · $text", LAST_DRIVE_MS)
    }

    fun openMenu(m: HomeMenu) {
        open = m; sheet = null
        if (m != HomeMenu.FRIENDS) friendOpen = null
        if (m != HomeMenu.RANKING) rankOpen = null
        if (previewLook != null) { previewLook = null; companion.look = avatar.look }
        placeCard(side = m == HomeMenu.WARDROBE || m == HomeMenu.SHOP || m == HomeMenu.FRIENDS || m == HomeMenu.RANKING)
        overlayTitle.text = "${m.icon} ${m.label}"
        (overlayBody.parent as? ScrollView)?.scrollTo(0, 0)
        fill(m)
        overlay.visibility = View.VISIBLE
        scheduleBored()
    }

    private fun openSheet(title: String, body: () -> Unit) {
        open = null; sheet = body
        placeCard(side = false)
        overlayTitle.text = title
        (overlayBody.parent as? ScrollView)?.scrollTo(0, 0)
        overlayBody.removeAllViews()
        body()
        overlay.visibility = View.VISIBLE
        scheduleBored()
    }

    private fun placeBubble() {
        val target = stage.left + stage.width / 2f + stage.translationX - dp(GREETING_GAP_DP) - bubble.width
        bubble.translationX = maxOf(dp(20).toFloat(), target) - bubble.left
    }

    private fun placeCard(side: Boolean) {
        (overlayCard.layoutParams as FrameLayout.LayoutParams).gravity = if (side) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.CENTER
        overlayCard.requestLayout()
        overlayScrim.setBackgroundColor(Color.parseColor(if (side) "#14000000" else "#66000000"))
        shiftCompanion(side)
        dimChips(true)
    }

    private fun shiftCompanion(side: Boolean) {
        val lp = overlayCard.layoutParams as FrameLayout.LayoutParams
        val cardSpan = lp.width + lp.rightMargin
        stage.animate().translationX(if (side) -cardSpan / 2f else 0f).setDuration(220).setUpdateListener { placeBubble() }.start()
        val a = if (side) 0f else 1f
        menuLeft.animate().alpha(a).setDuration(220).start()
        menuRight.animate().alpha(a).setDuration(220).start()
        bar.animate().alpha(a).setDuration(220).start()
    }

    private fun dimChips(open: Boolean) {
        chips.animate().alpha(if (open) 0f else 1f).setDuration(150).start()
    }

    fun close() {
        open = null; sheet = null; friendOpen = null; rankOpen = null
        shiftCompanion(false)
        if (previewLook != null) { previewLook = null; companion.look = avatar.look }
        if (previewMood != null) { previewMood = null; companion.mood = currentMood() }
        companion.pose = null
        overlay.visibility = View.GONE
        dimChips(false)
        syncFriendShown()
    }

    val isMenuOpen get() = overlay.visibility == View.VISIBLE

    private fun fill(m: HomeMenu) {
        val sv = overlayBody.parent as? ScrollView
        val keepY = sv?.scrollY ?: 0
        overlayBody.removeAllViews()
        if (keepY > 0) sv?.post { sv.scrollTo(0, keepY) }
        syncFriendShown()
        when (m) {
            HomeMenu.STATUS -> fillStatus()
            HomeMenu.RANKING -> fillRanking()
            HomeMenu.ACHIEVEMENTS -> fillAchievements()
            HomeMenu.SHOP -> fillShop()
            HomeMenu.WARDROBE -> fillWardrobe()
            HomeMenu.SETTINGS -> fillSettings()
            HomeMenu.FRIENDS -> fillFriends()
        }
    }

    private fun fillMissions() {
        overlayBody.removeAllViews()
        val today = LocalDate.now().toEpochDay()
        val list = lobby.missions(today, score.tripsOn(today))
        val done = list.count { it.done }
        overlayBody.addView(text("$done / ${list.size} 완료", 22f, "#15181D").apply { paint.isFakeBoldText = true })
        overlayBody.addView(text("매일 자정에 새로 나와요. 운전을 채점하거나 주행 거리를 재는 미션은 없어요.", 15f, "#6B7380").apply { setPadding(0, dp(4), 0, dp(14)) })
        list.forEach { m ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor(if (m.done) "#EAF1FF" else "#F6F7F9")) }
                setPadding(dp(22), dp(16), dp(22), dp(16))
            }
            val head = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            head.addView(text((if (m.done) "✅ " else "⬜ ") + m.title, 19f, "#15181D").apply { paint.isFakeBoldText = true }, LinearLayout.LayoutParams(0, -2, 1f))
            head.addView(text("${m.progress.coerceAtMost(m.target)} / ${m.target}", 17f, "#6B7380"))
            row.addView(head)
            row.addView(ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = m.target; progress = m.progress.coerceAtMost(m.target)
                progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#3D7BFF"))
            }, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(8) })
            overlayBody.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        val claimable = lobby.canClaimMissions(today, score.tripsOn(today))
        val claimed = lobby.missionClaimedDay == today
        overlayBody.addView(button(if (claimed) "오늘 보상 받았어요" else "보상 받기  🪙 +${LobbyState.MISSION_REWARD}", enabled = claimable) {
            lobby = lobby.claimMissions(today, score.tripsOn(today)); profiles.lobby = lobby
            barCoins.text = "🪙 ${coinLabel(lobby.coins)}"; chipDot.visibility = View.GONE
            onXp(XpReason.DAILY_MISSIONS)
            say("🪙 미션 완료! 코인 +${LobbyState.MISSION_REWARD} · XP +${XpReason.DAILY_MISSIONS.xp}")
            fillMissions()
        })
    }

    private fun fillCalendar() {
        overlayBody.removeAllViews()
        val today = LocalDate.now()
        val first = today.withDayOfMonth(1)
        val visited = lobby.visitDays.toSet()
        val inMonth = (1..today.lengthOfMonth()).count { first.withDayOfMonth(it).toEpochDay() in visited }
        overlayBody.addView(text("${today.monthValue}월 · 출석 ${inMonth}일 · 연속 ${lobby.streak}일", 22f, "#15181D").apply { paint.isFakeBoldText = true; setPadding(0, 0, 0, dp(12)) })
        val week = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("일", "월", "화", "수", "목", "금", "토").forEach { week.addView(text(it, 14f, "#6B7380").apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f)) }
        overlayBody.addView(week)
        var row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val lead = first.dayOfWeek.value % 7
        repeat(lead) { row.addView(View(activity), LinearLayout.LayoutParams(0, dp(44), 1f)) }
        for (d in 1..today.lengthOfMonth()) {
            val date = first.withDayOfMonth(d)
            val on = date.toEpochDay() in visited
            val isToday = date == today
            val cell = text(if (on) "✔" else "$d", 16f, if (on) "#FFFFFF" else if (date.isAfter(today)) "#C3C7CF" else "#15181D").apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(Color.parseColor(if (on) "#3D7BFF" else "#00000000"))
                    if (isToday) setStroke(dp(2), Color.parseColor("#3D7BFF"))
                }
            }
            val wrap = FrameLayout(activity).apply { addView(cell, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER)) }
            row.addView(wrap, LinearLayout.LayoutParams(0, dp(44), 1f))
            if (row.childCount == 7) { overlayBody.addView(row); row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL } }
        }
        if (row.childCount > 0) {
            while (row.childCount < 7) row.addView(View(activity), LinearLayout.LayoutParams(0, dp(44), 1f))
            overlayBody.addView(row)
        }
        overlayBody.addView(section("연속 출석 보상"))
        LobbyState.STREAK_REWARDS.forEach { r ->
            val got = lobby.rewardClaimedStreak >= r.days
            statRow("${r.days}일 연속", (if (got) "받음 · " else "") + "🪙 ${r.coins}")
        }
        overlayBody.addView(text("하루 한 번 시동만 걸어도 출석이에요. 하루 빠지면 1일부터 다시.", 14f, "#6B7380").apply { setPadding(0, dp(14), 0, 0) })
    }

    private fun fillRoute() {
        overlayBody.removeAllViews()
        overlayBody.addView(text("어디로 갈까요?", 22f, "#15181D").apply { paint.isFakeBoldText = true })
        overlayBody.addView(text("고르면 내비에 경로를 보내요. 지금은 시연용 목적지 2곳.", 15f, "#6B7380").apply { setPadding(0, dp(4), 0, dp(14)) })
        DemoContent.destinations.forEach { d ->
            settingRow(d.name, d.address) { close(); onRoute(d); say("🚗 ${d.name} 경로 보냈어요. 출발해요!") }
        }
        overlayBody.addView(text("자주 가는 곳·최근 목적지는 내비 연동 후.", 14f, "#6B7380").apply { setPadding(0, dp(14), 0, 0) })
    }

    private fun fillStatus() {
        val p = profile
        val days = daysTogether()
        overlayBody.addView(text("${avatar.name} · ${avatar.personality.title} (${avatar.personality.mbtiHint})", 22f, "#15181D").apply { paint.isFakeBoldText = true })
        overlayBody.addView(text("함께한 지 ${days}일째 · 나를 \"${lobby.addressing(settings.driverName)}\"라고 불러요", 18f, "#6B7380").apply { setPadding(0, dp(4), 0, dp(14)) })

        overlayBody.addView(text("Lv.${p.level} · ${p.title}", 30f, "#15181D").apply { paint.isFakeBoldText = true })
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = CompanionProfile.XP_PER_LEVEL; progress = p.xpInLevel
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#3D7BFF"))
        }
        overlayBody.addView(bar, LinearLayout.LayoutParams(-1, dp(14)).apply { topMargin = dp(8) })
        overlayBody.addView(android.view.View(activity), LinearLayout.LayoutParams(-1, dp(14)))

        overlayBody.addView(section("함께한 기록"))
        statRow("함께한 날", "${days}일째")
        statRow("출석", "총 ${ach.count(AchKey.VISITS)}일 · 🔥 ${lobby.streak}일 연속")
        statRow("함께 다녀온 길", "${score.trips}번")
        statRow("가 본 지역", "${score.placesVisited}곳")
        statRow("함께 달린 거리", "${p.distanceKm.toInt()} km")
        statRow("모은 업적", "${ach.tiersDone} / ${Achievements.totalTiers} 단계")
        statRow("친밀도", "${lobby.affinityLevel} / ${LobbyState.MAX_AFFINITY_LEVEL}")
        statRow("기분", "${lobby.moodBand.emoji} ${lobby.moodBand.label}")

    }

    private fun fillRanking() {
        rankOpen?.let { code -> Ranking.find(code)?.let { return rankLobby(it) } }
        if (previewLook != null) { previewLook = null; companion.look = avatar.look }
        overlayBody.addView(text(if (rankByCar) "차종별 순위" else "전국 순위", 22f, "#15181D").apply { paint.isFakeBoldText = true })
        overlayBody.addView(text("이름을 누르면 그 사람 캐릭터 로비를 구경해요 (서버 연동 전 시연용 계정)", 15f, "#6B7380").apply { setPadding(0, dp(4), 0, dp(10)) })
        choiceRow(listOf("전국" to !rankByCar, "차종별" to rankByCar)) { i -> rankByCar = i == 1; fill(HomeMenu.RANKING) }
        choiceRow(RankBoard.entries.map { it.label.removePrefix("전국 ") to (it == rankBoard) }) { i -> rankBoard = RankBoard.entries[i]; fill(HomeMenu.RANKING) }
        fun rows(list: List<RankEntry>) = list.forEachIndexed { i, e ->
            val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}" }
            val row = rankRow(medal, if (e.isMe) "나 · ${avatar.name}" else e.name, rankBoard.format(e), e.isMe)
            e.code?.let { c -> row.setOnClickListener { rankOpen = c; fill(HomeMenu.RANKING) } }
        }
        if (rankByCar) Ranking.byCar(rankBoard, myRankEntry(), rankHearted).forEach { (car, list) ->
            overlayBody.addView(section("🚗 $car · ${list.size}명" + if (car == Ranking.MY_CAR) " · 내 차" else ""))
            rows(list)
        } else rows(Ranking.board(rankBoard, myRankEntry(), rankHearted))
        overlayBody.addView(text("순위는 정차 중에만 보여요. 주행 중에는 표시하지 않습니다(심사 기준).", 14f, "#6B7380").apply { setPadding(0, dp(14), 0, 0) })
    }

    private fun myRankEntry() = RankEntry(
        null, avatar.name, profile.level, profile.distanceKm.toInt(), lobby.visitDays.size * 3,
        Achievements.ALL.sumOf { ach.tierOf(it) },
    )

    private fun rankLobby(a: RankAccount) {
        val look = CharacterLook.fromMap(a.look)
        if (previewLook != look) { previewLook = look; companion.look = look }
        val p = Personality.entries.firstOrNull { it.name == a.personality }
        val e = Ranking.entry(a, a.code in rankHearted)
        overlayBody.addView(chipText("← 순위", false, true) { rankOpen = null; fill(HomeMenu.RANKING) }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(8) })
        overlayBody.addView(text("${p?.let { PERSONALITY_ICON[it] } ?: ""} ${a.name}의 로비", 24f, "#15181D").apply { paint.isFakeBoldText = true })
        overlayBody.addView(text("${p?.title ?: ""} · 가운데 캐릭터가 ${a.name}예요", 15f, "#6B7380").apply { setPadding(0, dp(2), 0, dp(10)) })
        RankBoard.entries.forEach { b ->
            val place = Ranking.board(b, myRankEntry(), rankHearted).indexOfFirst { it.code == a.code } + 1
            statRow(b.label, "${b.format(e)} · ${place}위")
        }
        a.honor?.let { statRow("칭호", "「$it」") }
        statRow("차", a.car)
        val hearted = a.code in rankHearted
        overlayBody.addView(button(if (hearted) "♥ 오늘 하트 보냈어요" else "♥ 하트 누르기", !hearted) {
            rankHearted += a.code
            say("♥ ${a.name}에게 하트를 보냈어요"); fill(HomeMenu.RANKING)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    private fun putFriends(n: FriendState) { friends = n; profiles.friends = n }

    private fun fillFriends() {
        friendOpen?.let { code -> FriendDirectory.find(code)?.takeIf { friends.isFriend(code) }?.let { return friendProfile(it) } }
        if (previewLook != null) { previewLook = null; companion.look = avatar.look }
        val today = LocalDate.now().toEpochDay()

        FriendDirectory.ALL.filter { !friends.isFriend(it.code) }.takeIf { it.isNotEmpty() }?.let { missing ->
            putFriends(missing.fold(friends.copy(blocked = emptySet())) { st, f -> st.add(f.code).first })
        }

        val incoming = friends.incomingGifts(today)
        if (incoming.isNotEmpty()) overlayBody.addView(button("🎁 받은 선물 ${incoming.size}개 모두 받기 · 🪙${incoming.size * FriendState.GIFT_COINS}", true) {
            val (n, coins) = friends.claimGifts(today) ?: return@button
            putFriends(n); lobby = lobby.addCoins(coins); profiles.lobby = lobby; barCoins.text = "🪙 ${coinLabel(lobby.coins)}"
            say("🎁 친구 선물 🪙$coins 받았어요"); fill(HomeMenu.FRIENDS)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(6) })

        val list = friends.friends.mapNotNull { FriendDirectory.find(it) }
            .sortedWith(compareByDescending<FriendProfile> { it.code in friends.favorites }.thenByDescending { friends.pointsOf(it.code) })
        overlayBody.addView(section("친구 ${list.size}"))
        if (list.isEmpty()) overlayBody.addView(text("${avatar.name}: \"친구 코드 공유해 봐요. 같이 안전 운전해요!\"", 16f, "#15181D"))
        list.forEach { f ->
            val online = if (f.online) "🟢 오늘 출석" else "${f.lastSeenDays}일 전"
            val row = friendRowBase(f, "Lv.${f.level} · 우정 ${friends.friendshipLevel(f.code)} · $online")
            if (friends.canGift(f.code, today)) row.addView(chipText("🎁", false, true) { sendGift(f, today) }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
            row.addView(chipText("보기 →", false, true) { friendOpen = f.code; fill(HomeMenu.FRIENDS) })
            row.setOnClickListener { friendOpen = f.code; fill(HomeMenu.FRIENDS) }
            overlayBody.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }

    }

    private fun friendRowBase(f: FriendProfile, sub: String): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#F6F7F9")) }
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        val p = Personality.entries.firstOrNull { it.name == f.personality }
        row.addView(text(p?.let { PERSONALITY_ICON[it] } ?: "🙂", 22f, "#15181D"))
        val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        col.addView(text((if (f.code in friends.favorites) "★ " else "") + f.name + (f.honor?.let { " 「$it」" } ?: ""), 18f, "#15181D").apply { paint.isFakeBoldText = true; maxLines = 1 })
        col.addView(text(sub, 14f, "#6B7380"))
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun sendGift(f: FriendProfile, today: Long) {
        putFriends(friends.gift(f.code, today) ?: return)
        say("🎁 ${f.name}에게 코인 ${FriendState.GIFT_COINS}개를 보냈어요 (내 코인은 그대로)")
        fill(HomeMenu.FRIENDS)
    }

    private fun friendProfile(f: FriendProfile) {
        val today = LocalDate.now().toEpochDay()
        val fLook = CharacterLook.fromMap(f.look)
        if (previewLook != fLook) { previewLook = fLook; companion.look = fLook }
        val p = Personality.entries.firstOrNull { it.name == f.personality }

        overlayBody.addView(chipText("← 친구 목록", false, true) { friendOpen = null; fill(HomeMenu.FRIENDS) }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(8) })
        overlayBody.addView(text("${p?.let { PERSONALITY_ICON[it] } ?: ""} ${f.name}", 24f, "#15181D").apply { paint.isFakeBoldText = true })
        overlayBody.addView(text("${p?.title ?: ""} · 가운데 캐릭터가 ${f.name}예요 · " + if (f.online) "🟢 오늘 출석" else "${f.lastSeenDays}일 전 출석", 15f, "#6B7380").apply { setPadding(0, dp(2), 0, dp(10)) })
        statRow("레벨 · 칭호", "Lv.${f.level}" + (f.honor?.let { " 「$it」" } ?: ""))
        statRow("연속 출석", "🔥 ${f.streak}일")
        statRow("업적 달성률", "${f.achPercent}%")

        statRow("우정 레벨", "${friends.friendshipLevel(f.code)} / ${FriendState.MAX_FRIENDSHIP} (${friends.pointsOf(f.code)}점)")
        statRow("코디", describeLook(fLook))

        overlayBody.addView(section("하루 한 번씩"))
        val acts = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        acts.addView(chipText(if (friends.canGift(f.code, today)) "🎁 코인 선물" else "🎁 보냄", false, friends.canGift(f.code, today)) { sendGift(f, today) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        acts.addView(chipText(if (friends.canPet(f.code, today)) "🤚 쓰다듬기" else "🤚 했음", false, friends.canPet(f.code, today)) {
            val (n, coins) = friends.pet(f.code, today) ?: return@chipText
            putFriends(n); lobby = lobby.addCoins(coins); profiles.lobby = lobby; barCoins.text = "🪙 ${coinLabel(lobby.coins)}"
            companion.pose = "cheer"; main.postDelayed({ companion.pose = null }, 1500)
            say("♥ ${f.name}를 쓰다듬었어요. 양쪽 다 코인 +$coins"); fill(HomeMenu.FRIENDS)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        overlayBody.addView(acts, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val canCheer = friends.canCheer(f.code, today)
        overlayBody.addView(text(if (canCheer) "이모지 응원 (하나 고르기)" else "오늘 응원 보냈어요", 14f, "#6B7380").apply { setPadding(0, 0, 0, dp(4)) })
        choiceRow(Cheer.entries.map { "${it.emoji} ${it.label}" to false }) { i ->
            val n = friends.cheer(f.code, today) ?: return@choiceRow say("응원은 친구마다 하루 한 번이에요")
            putFriends(n); say("${Cheer.entries[i].emoji} ${f.name}에게 「${Cheer.entries[i].label}」 응원을 보냈어요"); fill(HomeMenu.FRIENDS)
        }

        overlayBody.addView(section("꾸미기 · 상점"))
        val shop = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        shop.addView(chipText("👕 코디 따라 하기", false, true) { copyOutfit(f, fLook) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        shop.addView(chipText("🛍️ 아이템 선물", false, true) { giftItem(f) }, LinearLayout.LayoutParams(0, -2, 1f))
        overlayBody.addView(shop, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

    }

    private fun copyOutfit(f: FriendProfile, fLook: CharacterLook) {
        val missing = fLook.toMap().mapNotNull { (slot, opt) -> Catalog.of(slot, opt)?.takeIf { !inventory.owns(it, profile.level) } }
        var mine = avatar.look
        fLook.toMap().forEach { (slot, opt) -> if (ownsOption(slot, opt)) mine = mine.with(slot, opt) }
        previewLook = null; friendOpen = null
        wear(mine)
        companion.look = avatar.look
        say(if (missing.isEmpty()) "👕 ${f.name} 코디 그대로 입었어요!" else "👕 가진 것만 따라 입었어요 · 없는 ${missing.size}개는 상점에")
        fill(HomeMenu.FRIENDS)
    }

    private fun giftItem(f: FriendProfile) {
        val items = Catalog.sellable
        val labels = items.map { "${it.rarity.badge} ${nm(it)} · ${SLOT_LABELS[it.slot]} · 🪙${it.price}" }
        val adapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply { setTextColor(Color.parseColor("#15181D")) }
        }
        AlertDialog.Builder(activity).setTitle("${f.name}에게 선물할 아이템")
            .setAdapter(adapter) { _, i ->
                val item = items[i]
                if (lobby.coins < item.price) return@setAdapter say("🪙${item.price - lobby.coins} 더 모아야 해요")
                confirmBuy("${f.name}에게 ${nm(item)} 선물", item.price, "선물은 환불되지 않아요. 내 옷장에는 들어오지 않아요.") {
                    lobby = lobby.addCoins(-item.price); profiles.lobby = lobby; barCoins.text = "🪙 ${coinLabel(lobby.coins)}"
                    putFriends(friends.giftItem(f.code))
                    say("🛍️ ${f.name}에게 ${nm(item)}을(를) 선물했어요. 우정 +5"); fill(HomeMenu.FRIENDS)
                }
            }.setNegativeButton("닫기", null).show()
    }

    fun setAch(key: String, value: Int) { ach = ach.set(key, value); profiles.ach = ach }

    fun rememberFirstPlace() {
        if (place.isEmpty()) return
        ach = ach.keep(listOf(MemoryCard.firstPlace(LocalDate.now().toEpochDay(), place, avatar.look.encode()))); profiles.ach = ach
    }

    fun bumpAch(key: String, by: Int = 1) {
        ach = ach.bump(key, by)
        checkAchievements()
    }

    fun notePersonality(p: Personality) {
        val mask = (ach.counters[PERSONALITY_MASK] ?: 0) or (1 shl p.ordinal)
        ach = ach.set(PERSONALITY_MASK, mask).set(AchKey.PERSONALITIES, Integer.bitCount(mask))
        checkAchievements()
    }

    private fun checkAchievements() {
        val today = LocalDate.now().toEpochDay()
        val before = ach
        if ((ach.counters[PERSONALITY_MASK] ?: 0) == 0) {
            val mask = 1 shl avatar.personality.ordinal
            ach = ach.set(PERSONALITY_MASK, mask).set(AchKey.PERSONALITIES, 1)
        }
        ach = ach.setAtLeast(AchKey.STREAK_BEST, lobby.streak)
        ach = ach.backfillAlbum(avatar.look.encode())
        val external = mapOf(
            AchKey.FIRST_DRIVE to if (score.firstDriveDone) 1 else 0,
            AchKey.TRIPS to score.trips,
            AchKey.DAYS to daysTogether().toInt(),
            AchKey.AFFINITY to lobby.affinityLevel, AchKey.PLACES to score.placesVisited,
            AchKey.LEVEL to profile.level,
            AchKey.OWNED to inventory.ownedCount(),
            AchKey.HAIR_COLORS to Catalog.ALL.count { it.slot == "hairColor" && inventory.owns(it, profile.level) },
        )
        val (next, unlocks) = ach.evaluate(today, external)
        ach = next.keep(unlocks.map { it.card(place, avatar.look.encode()) })
        if (ach != before) profiles.ach = ach
        if (unlocks.isEmpty()) return
        val coins = unlocks.sumOf { it.coins }
        lobby = lobby.addCoins(coins); profiles.lobby = lobby
        barCoins.text = "🪙 ${coinLabel(lobby.coins)}"
        val u = unlocks.last()
        val honor = unlocks.mapNotNull { it.honor }.firstOrNull()
        say("🎖️ ${u.achievement.title} · \"${u.achievement.memoryLine(u.tier)}\" 코인 +$coins" + (honor?.let { " · 칭호 「$it」" } ?: ""), LAST_DRIVE_MS)
        menuDots[HomeMenu.ACHIEVEMENTS]?.visibility = View.VISIBLE
        if (open == HomeMenu.ACHIEVEMENTS) fill(HomeMenu.ACHIEVEMENTS)
    }

    private fun fillAchievements() {
        menuDots[HomeMenu.ACHIEVEMENTS]?.visibility = View.GONE
        val all = Achievements.ALL
        val done = all.sumOf { ach.tierOf(it) }
        overlayBody.addView(text("달성률 ${ach.completionPercent}% · $done / ${Achievements.totalTiers}단계", 22f, "#15181D").apply { paint.isFakeBoldText = true })
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = ach.completionPercent
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#3D7BFF"))
        }
        overlayBody.addView(bar, LinearLayout.LayoutParams(-1, dp(10)).apply { topMargin = dp(6); bottomMargin = dp(8) })
        val honors = ach.honors
        overlayBody.addView(text(if (honors.isEmpty()) "칭호 없음 · 업적을 끝까지 올리면 칭호를 받아요" else "칭호: " + honors.joinToString(" · ") { "「$it」" }, 15f, "#6B7380").apply { setPadding(0, 0, 0, dp(10)) })

        if (ach.album.isNotEmpty()) {
            val head = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(section("📸 추억 앨범 · ${ach.album.size}장"), LinearLayout.LayoutParams(0, -2, 1f))
            if (ach.album.size > ALBUM_SHOWN) head.addView(chipText(if (albumAll) "접기" else "전부 보기", albumAll, true) { albumAll = !albumAll; fill(HomeMenu.ACHIEVEMENTS) })
            overlayBody.addView(head)
            ach.album.asReversed().let { if (albumAll) it else it.take(ALBUM_SHOWN) }.forEach { albumRow(it) }
        }

        val recent = ach.recent.mapNotNull { Achievements.byId[it] }
        if (recent.isNotEmpty()) {
            overlayBody.addView(section("최근 달성"))
            recent.forEach { achRow(it) }
        }

        val filters = listOf(null to "전체", "near" to "거의 다 됨") + AchCategory.entries.map { it.name to it.label }
        val row1 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val row2 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        filters.forEachIndexed { i, (key, label) ->
            val c = chipText(label, achFilter == key, true) { achFilter = key; fill(HomeMenu.ACHIEVEMENTS) }
            (if (i < 5) row1 else row2).addView(c, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        }
        overlayBody.addView(section("전체 업적"))
        overlayBody.addView(row1, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        overlayBody.addView(row2, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val f = achFilter
        val shown = when (f) {
            null -> all
            "near" -> all.filter { ach.nearlyDone(it) }
            else -> all.filter { it.category.name == f }
        }
        if (shown.isEmpty()) overlayBody.addView(text("해당하는 업적이 없어요", 16f, "#6B7380"))
        if (f == null) {
            AchCategory.entries.forEach { c ->
                val list = shown.filter { it.category == c }
                if (list.isEmpty()) return@forEach
                overlayBody.addView(section("${c.label} · ${list.sumOf { ach.tierOf(it) }}/${list.sumOf { it.maxTier }}"))
                list.forEach { achRow(it) }
            }
        } else shown.forEach { achRow(it) }
        overlayBody.addView(text("업적은 함께한 시간·가 본 곳·캐릭터와 한 일의 기록이에요. 운전을 채점하지 않고, 거리나 주행 시간도 세지 않습니다.", 14f, "#6B7380").apply { setPadding(0, dp(14), 0, 0) })
    }

    private fun albumRow(c: MemoryCard) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#FFF8EC")) }
            setPadding(dp(16), dp(12), dp(22), dp(12))
        }
        val pic = runCatching { statik.compose(genderKey(), CharacterLook.decode(c.look), dp(ALBUM_PIC_W), dp(ALBUM_PIC_H)).bitmap }.getOrNull()
        if (pic != null) row.addView(ImageView(activity).apply { setImageBitmap(pic) }, LinearLayout.LayoutParams(dp(ALBUM_PIC_W), dp(ALBUM_PIC_H)).apply { marginEnd = dp(14) })
        val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val d = LocalDate.ofEpochDay(c.day)
        col.addView(text("${d.year}.${d.monthValue}.${d.dayOfMonth}" + (if (c.place.isNotEmpty()) " · ${c.place}" else ""), 14f, "#6B7380"))
        val tierLabel = c.achievement?.takeIf { it.maxTier > 1 }?.let { " ${Tier.entries[(c.tier - 1).coerceIn(0, Tier.entries.size - 1)].label}" } ?: ""
        col.addView(text("「${c.title}」$tierLabel", 18f, "#15181D").apply { paint.isFakeBoldText = true })
        col.addView(text("\"${c.line}\"", 16f, "#15181D"))
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        overlayBody.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun achRow(a: Achievement) {
        val tier = ach.tierOf(a)
        val maxed = tier >= a.maxTier
        val secret = a.hidden && tier == 0
        val (cur, goal) = ach.progress(a)
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor(if (tier > 0) "#EAF1FF" else "#F6F7F9")) }
            setPadding(dp(22), dp(16), dp(22), dp(16)); alpha = if (tier > 0 || ach.nearlyDone(a)) 1f else 0.7f
        }
        val medal = when { secret -> "❔"; tier == 0 -> "🔒"; else -> Tier.entries[tier - 1].medal }
        row.addView(text(medal, 26f, "#15181D"))
        val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        val titleLine = if (secret) "???" else a.title + (if (a.maxTier > 1) " · ${if (maxed) Tier.entries[tier - 1].label else Tier.entries[tier].label}" else "") + (if (maxed) " ✓" else "")
        col.addView(text(titleLine, 19f, "#15181D").apply { paint.isFakeBoldText = true })
        val desc = when {
            secret -> "숨김 업적 · 달성하면 보여요"
            maxed -> a.describe(a.maxTier - 1) + " 완료" + (a.honor?.let { " · 칭호 「$it」" } ?: "") + (ach.unlockedDay[a.id]?.let { " · ${LocalDate.ofEpochDay(it)}" } ?: "")
            else -> a.describe(tier) + " · 보상 🪙${a.coinsFor(tier + 1)}"
        }
        col.addView(text(desc, 15f, "#6B7380"))
        if (!secret && !maxed) {
            val pr = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            pr.addView(ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = goal; progress = cur.coerceAtMost(goal)
                progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(if (ach.nearlyDone(a)) "#FF8A3D" else "#3D7BFF"))
            }, LinearLayout.LayoutParams(0, dp(8), 1f))
            pr.addView(text("$cur / $goal", 14f, "#6B7380").apply { setPadding(dp(10), 0, 0, 0) })
            col.addView(pr, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        overlayBody.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun setLook(look: CharacterLook) {
        val old = avatar.look
        avatar = avatar.copy(look = look)
        previewLook = previewLook?.let { p -> CharacterLook.KEYS.fold(p) { acc, s -> if (old[s] != look[s]) acc.with(s, look[s]) else acc } }?.takeUnless { it == look }
        companion.look = previewLook ?: look
        onLook(look)
    }

    private fun wear(look: CharacterLook) {
        if (look == avatar.look) return
        undoStack.addLast(avatar.look); while (undoStack.size > UNDO_KEPT) undoStack.removeFirst()
        setLook(look); bumpAch(AchKey.OUTFITS)
        styling = styling.noteWorn(look.toMap()); profiles.styling = styling
        Stylist.setEffect(look.toMap())?.let { s -> if (s != lastSetEffect) { lastSetEffect = s; say(Lines.setEffect(avatar.personality, s.label)) } }
    }

    private fun fillWardrobe() {
        val look = avatar.look
        val lvl = profile.level
        fun refresh() { fill(HomeMenu.WARDROBE) }
        prefetchBgThumbs()

        val tabRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        WARDROBE_TABS.forEach { (id, icon, label) ->
            val on = wardrobeTab == id
            val tab = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
                background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.parseColor(if (on) "#3D7BFF" else "#F6F7F9")) }
                addView(text(icon, 24f, "#15181D").apply { gravity = Gravity.CENTER })
                addView(text(label, 13f, if (on) "#FFFFFF" else "#15181D").apply { gravity = Gravity.CENTER; maxLines = 1; if (on) paint.isFakeBoldText = true })
                setOnClickListener { wardrobeTab = id; (overlayBody.parent as? ScrollView)?.scrollTo(0, 0); refresh() }
            }
            tabRow.addView(tab, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        }
        overlayBody.addView(tabRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        when (wardrobeTab) {
            "hair" -> { wardrobeSlot("hair", look, lvl, ::refresh, hint = false); wardrobeHairColors(look, lvl, ::refresh) }
            "eyes" -> { wardrobeSlot("eyes", look, lvl, ::refresh, hint = false); wardrobeFreeSlot("eyeColor", look, ::refresh) }
            "skin" -> { wardrobeSlot("skin", look, lvl, ::refresh, hint = false); wardrobeFreeSlot("brows", look, ::refresh, hint = false); wardrobeFreeSlot("mouth", look, ::refresh) }
            "bg" -> wardrobeBackgrounds(lvl, ::refresh)
            "voice" -> voicePicker(::refresh)
            "personality" -> personalityPicker(::refresh)
            "more" -> wardrobeMore(::refresh)
            else -> wardrobeSlot(wardrobeTab, look, lvl, ::refresh)
        }
    }

    private fun wardrobeSlot(slot: String, look: CharacterLook, lvl: Int, refresh: () -> Unit, hint: Boolean = true) {
        val comp = Stylist.completion(inventory, lvl)[slot]
        val head = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(section("${SLOT_LABELS[slot] ?: slot}" + (comp?.let { " · 보유 ${it.first}/${it.second}" } ?: "")), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(chipText("↩ 되돌리기", false, undoStack.isNotEmpty()) { undoStack.removeLastOrNull()?.let { setLook(it); refresh() } }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        head.addView(chipText(if (wardrobeOwnedOnly) "가진 것만" else "전부 보기", wardrobeOwnedOnly, true) { wardrobeOwnedOnly = !wardrobeOwnedOnly; refresh() })
        overlayBody.addView(head)

        val g = genderKey()
        val options = statik.options(g, slot).filter { !wardrobeOwnedOnly || ownsOption(slot, it.id) }
        grid(options.sortedByDescending { "$slot:${it.id}" in styling.favorites }.map { o ->
            val item = Catalog.of(slot, o.id)
            val unlocked = ownsOption(slot, o.id)
            val fav = "$slot:${o.id}" in styling.favorites
            val img = statik.part(g, slot, o.id, dp(TILE_IMG))
            tile(img, "✕", "", look[slot] == o.id, unlocked, badge(item, unlocked), fav, onClick = {
                when {
                    unlocked -> { wear(look.with(slot, o.id)); refresh() }
                    item?.source == ItemSource.LEVEL -> say("Lv.${item.requiredLevel}이 되면 ${o.label}을 쓸 수 있어요")
                    else -> { say("${o.label}은 상점에서 🪙${item?.price}"); openMenu(HomeMenu.SHOP) }
                }
            }, onLong = { styling = styling.toggleFavorite("$slot:${o.id}"); profiles.styling = styling; refresh() })
        })
        if (hint) wardrobeHint()
    }

    private fun wardrobeHairColors(look: CharacterLook, lvl: Int, refresh: () -> Unit) {
        val comp = Stylist.completion(inventory, lvl)["hairColor"]
        overlayBody.addView(section("머리색 · 보유 ${comp?.first ?: 0}/${comp?.second ?: 0}"))
        val g = genderKey()
        val colors = statik.hairColors.filter { !wardrobeOwnedOnly || ownsOption("hairColor", it.id) }
        grid(colors.sortedByDescending { "hairColor:${it.id}" in styling.favorites }.map { c ->
            val has = ownsOption("hairColor", c.id)
            val item = Catalog.of("hairColor", c.id)
            val fav = "hairColor:${c.id}" in styling.favorites
            val img = statik.part(g, "hairColor", c.id, dp(TILE_IMG))
            tile(img, null, "", look.hairColor == c.id, has, badge(item, has), fav, onClick = {
                when {
                    has -> { wear(look.copy(hairColor = c.id)); refresh() }
                    else -> { say("${c.label}은 상점에서 🪙${item?.price}"); openMenu(HomeMenu.SHOP) }
                }
            }, onLong = { styling = styling.toggleFavorite("hairColor:${c.id}"); profiles.styling = styling; refresh() })
        })
        wardrobeHint()
    }

    private fun wardrobeFreeSlot(slot: String, look: CharacterLook, refresh: () -> Unit, hint: Boolean = true) {
        overlayBody.addView(section(SLOT_LABELS[slot] ?: slot))
        val g = genderKey()
        grid(statik.options(g, slot).sortedByDescending { "$slot:${it.id}" in styling.favorites }.map { o ->
            val fav = "$slot:${o.id}" in styling.favorites
            val img = statik.part(g, slot, o.id, dp(TILE_IMG))
            tile(img, null, "", look[slot] == o.id, true, null, fav,
                onClick = { wear(look.with(slot, o.id)); refresh() },
                onLong = { styling = styling.toggleFavorite("$slot:${o.id}"); profiles.styling = styling; refresh() })
        })
        if (hint) wardrobeHint()
    }

    private fun wardrobeHint() {
        overlayBody.addView(text("타일을 길게 누르면 즐겨찾기 ♥ · 🔒는 상점 코인이나 레벨로 열려요", 14f, "#6B7380").apply { setPadding(0, dp(6), 0, 0) })
    }

    private fun wardrobeBackgrounds(lvl: Int, refresh: () -> Unit) {
        overlayBody.addView(section("로비 배경"))
        grid(Background.entries.map { b ->
            val ok = lvl >= b.requiredLevel || inventory.admin
            tile(bgThumb(b), "🏞️", b.label, styling.background == b, ok, if (ok) null else "🔒 Lv.${b.requiredLevel}", false, imgH = BG_THUMB_H, onClick = {
                if (ok) { styling = styling.copy(background = b); profiles.styling = styling; applyBackground(); refresh() } else say("Lv.${b.requiredLevel}부터 쓸 수 있어요")
            })
        }, cols = 3)
    }

    private val bgThumbs = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    private val bgMissing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var bgPrefetched = false
    private fun decodeBg(file: String): Bitmap? {
        if (file in bgMissing) return null
        val b = runCatching { activity.assets.open(file).use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }) } }.getOrNull()
        if (b != null) bgThumbs[file] = b else bgMissing += file
        return b
    }
    private fun bgThumb(b: Background): Bitmap? = bgFile(b).let { bgThumbs[it] ?: decodeBg(it) }
    private fun prefetchBgThumbs() {
        if (bgPrefetched) return
        bgPrefetched = true
        Thread { Background.entries.forEach { b -> bgFile(b).let { if (!bgThumbs.containsKey(it)) decodeBg(it) } } }.start()
    }

    private fun wardrobeMore(refresh: () -> Unit) {
        overlayBody.addView(section("미리보기 · 표정"))
        val moods = listOf(Mood.HAPPY to "기쁨", Mood.CALM to "평온", Mood.TALKING to "말하기", Mood.SURPRISED to "놀람", Mood.SLEEPY to "졸림", Mood.SAD to "시무룩")
        choiceRow(moods.map { it.second to (previewMood == it.first) }) { i -> previewMood = moods[i].first.takeUnless { it == previewMood }; companion.mood = currentMood(); refresh() }
        val tools = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        tools.addView(chipText("📷 사진", false, true) { snapshot() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        tools.addView(chipText("🪪 캐릭터 카드", false, true) { characterCard() }, LinearLayout.LayoutParams(0, -2, 1f))
        overlayBody.addView(tools, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(6) })

        overlayBody.addView(section("표시"))
        toggleRow("이름표", "캐릭터 아래 이름", styling.nameTag) { styling = styling.copy(nameTag = it); profiles.styling = styling; onStyling(styling); applyNameTag(); refresh() }
        toggleRow("성격 아이콘", "이름 옆에 ${PERSONALITY_ICON[avatar.personality]}", styling.personalityIcon) { styling = styling.copy(personalityIcon = it); profiles.styling = styling; applyNameTag(); refresh() }
        toggleRow("주행 중 작게 보기", "주행 화면 캐릭터를 70% 크기로. 코디는 그대로, 움직임만 멈춰요", styling.drivingSmall) { styling = styling.copy(drivingSmall = it); profiles.styling = styling; onStyling(styling); refresh() }

        val comp = Stylist.completion(inventory, profile.level)
        val total = comp.values.sumOf { it.first } to comp.values.sumOf { it.second }
        overlayBody.addView(section("컬렉션 · ${total.first}/${total.second} (${total.first * 100 / maxOf(1, total.second)}%)"))
        overlayBody.addView(text(SHOP_SLOTS.mapNotNull { s -> comp[s]?.let { "${SLOT_LABELS[s] ?: s} ${it.first}/${it.second}" } }.joinToString(" · "), 15f, "#6B7380"))
        overlayBody.addView(text("🔒는 상점에서 코인으로, 장식 일부는 레벨로 해금돼요. 주행 중에는 코디 그대로 보이고 움직임만 멈춥니다.", 14f, "#6B7380").apply { setPadding(0, dp(10), 0, 0) })
    }

    private fun genderKey() = if (avatar.gender == Gender.MALE) "m" else "f"

    private fun nm(item: ShopItem): String = statik.label(genderKey(), item.slot, item.option) ?: item.name

    private fun badge(item: ShopItem?, owned: Boolean): String? = when {
        item == null || owned -> null
        item.source == ItemSource.LEVEL -> "🔒 Lv.${item.requiredLevel}"
        else -> "🪙 ${item.price}"
    }

    private fun tile(img: Bitmap?, glyph: String?, label: String, on: Boolean, enabled: Boolean, badge: String?, fav: Boolean, imgH: Int = TILE_IMG, pill: String? = if (on) "착용 중" else null, bg: String? = null, action: String? = null, actionOn: Boolean = true, onAction: (() -> Unit)? = null, onClick: () -> Unit, onLong: (() -> Unit)? = null): View {
        val box = FrameLayout(activity).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat(); setColor(Color.parseColor(bg ?: if (on) "#EAF1FF" else "#FFFFFF"))
                setStroke(dp(if (on) 4 else 2), Color.parseColor(if (on) "#3D7BFF" else "#E3E6EB"))
            }
            setOnClickListener { onClick() }
            if (onLong != null) setOnLongClickListener { onLong(); true }
        }
        val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(8), dp(12), dp(8), dp(10)) }
        val pic: View = if (img != null) ImageView(activity).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageBitmap(img) }
        else text(glyph ?: "✕", 40f, "#C3C7CF").apply { gravity = Gravity.CENTER }
        pic.alpha = if (enabled) 1f else 0.45f
        val picBox = FrameLayout(activity).apply { addView(pic, FrameLayout.LayoutParams(-1, -1)) }
        if (fav) picBox.addView(text("♥", 18f, "#FF4D6D"), FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { marginStart = dp(4) })
        col.addView(picBox, LinearLayout.LayoutParams(-1, dp(imgH)))
        if (label.isNotEmpty()) col.addView(text(label, 15f, if (enabled) "#15181D" else "#9AA0AA").apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER; if (on) paint.isFakeBoldText = true
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        if (action != null) col.addView(text(action, 15f, if (actionOn) "#FFFFFF" else "#6B7380").apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER; paint.isFakeBoldText = true
            background = GradientDrawable().apply { cornerRadius = 999f; setColor(Color.parseColor(if (actionOn) "#3D7BFF" else "#E3E6EB")) }
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setOnClickListener { onAction?.invoke() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        box.addView(col, FrameLayout.LayoutParams(-1, -2))
        if (badge != null || pill != null) {
            val band = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val pillSlot = FrameLayout(activity)
            if (pill != null) pillSlot.addView(text(pill, 12f, "#FFFFFF").apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                background = GradientDrawable().apply { cornerRadius = 999f; setColor(Color.parseColor("#3D7BFF")) }
                setPadding(dp(10), dp(3), dp(10), dp(3))
            }, FrameLayout.LayoutParams(-2, -2))
            band.addView(pillSlot, LinearLayout.LayoutParams(0, -2, 1f))
            if (badge != null) band.addView(text(badge, 13f, "#FFFFFF").apply {
                background = GradientDrawable().apply { cornerRadius = 999f; setColor(Color.parseColor("#B3222633")) }
                setPadding(dp(10), dp(4), dp(10), dp(4))
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
            box.addView(band, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { topMargin = dp(8); marginStart = dp(8); marginEnd = dp(8) })
        }
        return box
    }

    private fun grid(tiles: List<View>, cols: Int = GRID_COLS) {
        tiles.chunked(cols).forEach { chunk ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until cols) {
                val lp = LinearLayout.LayoutParams(0, -1, 1f).apply { if (i < cols - 1) marginEnd = dp(10) }
                row.addView(chunk.getOrNull(i) ?: View(activity), lp)
            }
            overlayBody.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }

    private fun describeLook(l: CharacterLook): String {
        val names = CharacterLook.KEYS.mapNotNull { k ->
            val o = l[k]
            statik.label(genderKey(), k, o)
        }
        return names.joinToString(" · ").ifEmpty { "기본" }
    }

    private fun applyNameTag() {
        name.visibility = if (styling.nameTag) View.VISIBLE else View.INVISIBLE
        val f = shownFriend()
        val r = shownRank()
        name.text = if (r != null) r.name else if (f != null) {
            val fp = Personality.entries.firstOrNull { it.name == f.personality }
            f.name + if (styling.personalityIcon && fp != null) " ${PERSONALITY_ICON[fp]}" else ""
        } else avatar.name + if (styling.personalityIcon) " ${PERSONALITY_ICON[avatar.personality]}" else ""
    }

    private fun shownFriend(): FriendProfile? =
        if (open == HomeMenu.FRIENDS) friendOpen?.let { code -> FriendDirectory.find(code)?.takeIf { friends.isFriend(code) } } else null

    private fun shownRank(): RankAccount? = if (open == HomeMenu.RANKING) rankOpen?.let { Ranking.find(it) } else null

    private fun friendGreeting(f: FriendProfile): String =
        Personality.entries.firstOrNull { it.name == f.personality }?.greeting ?: "안녕하세요!"

    private fun syncFriendShown() {
        applyNameTag()
        val r = shownRank()
        companion.gender = r?.gender ?: genderKey()
        applyBackground()
        val f = shownFriend()
        if (r != null) greeting.text = Personality.entries.firstOrNull { it.name == r.personality }?.greeting ?: "안녕하세요!"
        else if (f != null) greeting.text = friendGreeting(f)
        else if (System.currentTimeMillis() > lineUntilMs) greeting.text = defaultGreeting()
    }

    private fun characterCard() {
        val w = dp(420); val h = dp(640)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = android.graphics.Paint().apply { color = backgroundColor() }
        c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), dp(28).toFloat(), dp(28).toFloat(), bg)
        val stroke = android.graphics.Paint().apply { color = Color.parseColor("#F2C94C"); style = android.graphics.Paint.Style.STROKE; strokeWidth = dp(6).toFloat() }
        c.drawRoundRect(dp(3).toFloat(), dp(3).toFloat(), (w - dp(3)).toFloat(), (h - dp(3)).toFloat(), dp(26).toFloat(), dp(26).toFloat(), stroke)
        val cw = companion.width.coerceAtLeast(1); val ch = companion.height.coerceAtLeast(1)
        val scale = minOf(w * 0.8f / cw, h * 0.6f / ch)
        c.save(); c.translate((w - cw * scale) / 2f, dp(40).toFloat()); c.scale(scale, scale); companion.draw(c); c.restore()
        val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#15181D"); textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true; textSize = dp(28).toFloat() }
        val sp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6B7380"); textAlign = android.graphics.Paint.Align.CENTER; textSize = dp(16).toFloat() }
        var y = dp(40) + ch * scale + dp(44)
        c.drawText("${avatar.name} ${PERSONALITY_ICON[avatar.personality]}", w / 2f, y, tp); y += dp(30)
        c.drawText("Lv.${profile.level} ${profile.title} · ${avatar.personality.title}", w / 2f, y, sp); y += dp(24)
        ach.honors.takeIf { it.isNotEmpty() }?.let { c.drawText(it.joinToString(" · ") { h -> "「$h」" }, w / 2f, y, sp); y += dp(24) }
        c.drawText("함께한 지 ${daysTogether()}일 · 업적 ${ach.completionPercent}% · 🔥${lobby.streak}일 연속", w / 2f, y, sp); y += dp(24)
        c.drawText("car-pet", w / 2f, (h - dp(20)).toFloat(), sp)
        val dir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: activity.filesDir
        val f = File(dir, "card_${avatar.name}_${System.currentTimeMillis()}.png")
        runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            .onSuccess { Toast.makeText(activity, "카드 저장: ${f.name}", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(activity, "저장 실패", Toast.LENGTH_SHORT).show() }
    }

    private fun chipText(label: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) =
        text(label, 20f, if (on) "#FFFFFF" else "#15181D").apply {
            maxLines = 1
            background = GradientDrawable().apply { cornerRadius = 999f; setColor(Color.parseColor(if (on) "#3D7BFF" else "#E3E6EB")) }
            setPadding(dp(18), dp(14), dp(18), dp(14)); gravity = Gravity.CENTER; alpha = if (enabled) 1f else 0.5f
            setOnClickListener { onClick() }
        }

    private fun fillShop() {
        val today = LocalDate.now().toEpochDay()
        val lvl = profile.level
        val look = avatar.look
        fun refresh() { fill(HomeMenu.SHOP) }
        fun tiles(items: List<ShopItem>, highlight: Boolean = false) = grid(items.map { shopTile(it, today, lvl, look, ::refresh, highlight) })

        val head = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text("🪙 ${coinLabel(lobby.coins)}", 26f, "#15181D").apply { paint.isFakeBoldText = true }, LinearLayout.LayoutParams(0, -2, 1f))
        overlayBody.addView(head)

        val deal = inventory.dailyDeal(today)
        overlayBody.addView(section("⏳ 오늘의 한정 · ${Catalog.DAILY_DEAL_OFF_PERCENT}% 할인 · 자정에 바뀜"))
        tiles(listOf(deal), highlight = true)

        overlayBody.addView(section("🎲 랜덤 박스"))
        val pool = Catalog.sellable
        val unowned = pool.count { !inventory.owns(it, lvl) }
        val boxRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#F6F7F9")) }
            setPadding(dp(22), dp(16), dp(22), dp(16))
        }
        val boxCol = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        boxCol.addView(text("판매 상품 ${pool.size}개 중 하나 · 각 ${String.format("%.1f", 100f / pool.size)}%", 19f, "#15181D").apply { paint.isFakeBoldText = true })
        boxCol.addView(text("없는 것이 나올 확률 ${unowned * 100 / pool.size}% · 이미 있으면 🪙${Catalog.BOX_PRICE * Catalog.BOX_DUP_REFUND_PERCENT / 100} 돌려드려요", 14f, "#6B7380"))
        boxRow.addView(boxCol, LinearLayout.LayoutParams(0, -2, 1f))
        boxRow.addView(chipText("🪙${Catalog.BOX_PRICE} 열기", false, lobby.coins >= Catalog.BOX_PRICE) {
            confirmBuy("랜덤 박스", Catalog.BOX_PRICE) {
                val r = inventory.openBox(lobby.coins, lvl, today) ?: return@confirmBuy say("코인이 모자라요")
                inventory = r.inventory; profiles.inventory = inventory
                lobby = lobby.addCoins(-Catalog.BOX_PRICE + r.refund); profiles.lobby = lobby
                barCoins.text = "🪙 ${coinLabel(lobby.coins)}"
                bumpAch(AchKey.SHOP_BUYS)
                say(if (r.duplicate) "🎲 ${nm(r.item)}… 이미 있네요. 🪙${r.refund} 돌려드려요" else "🎲 ${r.item.rarity.badge} ${nm(r.item)} 당첨!")
                if (!r.duplicate) preview((previewLook ?: look).with(r.item.slot, r.item.option))
                refresh()
            }
        })
        overlayBody.addView(boxRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        overlayBody.addView(section("전체 상품"))
        val cats = listOf(null to "전체") + SHOP_SLOTS.map { it to (SLOT_LABELS[it] ?: it) }
        val r1 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val r2 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        cats.forEachIndexed { i, (k, l) -> (if (i < 4) r1 else r2).addView(chipText(l, shopFilter == k, true) { shopFilter = k; refresh() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) }) }
        overlayBody.addView(r1, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        overlayBody.addView(r2, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        val sorts = listOf("price_asc" to "가격↑", "price_desc" to "가격↓", "rarity" to "희귀도", "unowned" to "안 가진 것만")
        val r3 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        sorts.forEach { (k, l) -> r3.addView(chipText(l, shopSort == k, true) { shopSort = if (shopSort == k) null else k; refresh() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) }) }
        overlayBody.addView(r3, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        var list = Catalog.ALL.filter { it.source != ItemSource.DEFAULT }.filter { shopFilter == null || it.slot == shopFilter }
        list = when (shopSort) {
            "price_asc" -> list.sortedBy { inventory.priceOf(it, today) }
            "price_desc" -> list.sortedByDescending { inventory.priceOf(it, today) }
            "rarity" -> list.sortedByDescending { it.rarity.ordinal }
            "unowned" -> list.filter { !inventory.owns(it, lvl) }
            else -> list
        }
        if (list.isEmpty()) overlayBody.addView(text("해당하는 상품이 없어요", 16f, "#6B7380"))
        else tiles(list)

        overlayBody.addView(text("전부 코인 상품이고 확률·가격은 여기 적힌 대로예요.", 14f, "#6B7380").apply { setPadding(0, dp(14), 0, 0) })
    }

    private fun shopTile(item: ShopItem, today: Long, lvl: Int, look: CharacterLook, refresh: () -> Unit, highlight: Boolean = false): View {
        val owned = inventory.owns(item, lvl)
        val worn = look[item.slot] == item.option
        val price = inventory.priceOf(item, today)
        val previewed = previewLook?.let { it[item.slot] == item.option } == true
        var action: String? = null; var actionOn = true; var onAction: (() -> Unit)? = null
        when {
            item.source == ItemSource.LEVEL && !owned -> { action = "🔒 Lv.${item.requiredLevel}"; actionOn = false; onAction = { say("Lv.${item.requiredLevel}이 되면 ${nm(item)}을 쓸 수 있어요") } }
            owned && !worn -> { action = "입기"; onAction = { wear(look.with(item.slot, item.option)); refresh() } }
            !owned && item.sellable -> { action = if (price != item.price) "🪙$price (원래 ${item.price})" else "🪙$price 구매"; actionOn = lobby.coins >= price; onAction = { buyItem(item, price, look, lvl, today, refresh) } }
        }
        return tile(itemThumb(item), SLOT_GLYPH[item.slot] ?: "✕", "", previewed || worn, owned || item.source != ItemSource.LEVEL, "${item.rarity.badge} ${item.rarity.label}", false,
            pill = when { worn -> "착용 중"; previewed -> "미리보기"; else -> null }, bg = if (highlight) "#FFF4E5" else null,
            action = action, actionOn = actionOn, onAction = onAction,
            onClick = { if (owned) { if (!worn) { wear(look.with(item.slot, item.option)); refresh() } } else previewToggle(item.slot, item.option) })
    }

    private fun buyItem(item: ShopItem, price: Int, look: CharacterLook, lvl: Int, today: Long, refresh: () -> Unit) {
        if (lobby.coins < price) return say("🪙${price - lobby.coins} 더 모아야 해요. 출석하고 미션을 하면 금방 모여요")
        confirmBuy(nm(item), price) {
            val r = inventory.buy(item, lobby.coins, lvl, today) ?: return@confirmBuy say("살 수 없어요")
            applyBuy(r, nm(item)); setLook(look.with(item.slot, item.option)); bumpAch(AchKey.OUTFITS); refresh()
        }
    }

    private fun itemThumb(item: ShopItem): Bitmap? = statik.part(genderKey(), item.slot, item.option, dp(TILE_IMG))

    private fun confirmBuy(what: String, price: Int, note: String = "", onOk: () -> Unit) {
        AlertDialog.Builder(activity).setTitle("$what 구매")
            .setMessage("🪙$price 를 써요. 남는 코인: ${lobby.coins - price}" + if (note.isEmpty()) "" else "\n$note")
            .setPositiveButton("구매") { _, _ -> onOk() }.setNegativeButton("취소", null).show()
    }

    private fun applyBuy(r: BuyResult, what: String) {
        inventory = r.inventory; profiles.inventory = inventory
        lobby = lobby.addCoins(-r.paid + r.bonus); profiles.lobby = lobby
        barCoins.text = "🪙 ${coinLabel(lobby.coins)}"
        bumpAch(AchKey.SHOP_BUYS)
        say("🛍️ $what 구매! " + (if (r.bonus > 0) "첫 구매 보너스 🪙${r.bonus} · " else "") + Lines.shopBought(avatar.personality))
        previewLook = null
    }

    private fun preview(look: CharacterLook) {
        previewLook = look.takeUnless { it == avatar.look }
        companion.look = previewLook ?: avatar.look
        open?.let { fill(it) }
    }

    private fun previewToggle(slot: String, option: String) {
        val base = previewLook ?: avatar.look
        preview(if (base[slot] == option) base.with(slot, avatar.look[slot]) else base.with(slot, option))
    }

    private fun ownsOption(slot: String, option: String): Boolean {
        val item = Catalog.of(slot, option) ?: return true
        return inventory.owns(item, profile.level)
    }

    private fun fillSettings() {
        val s = settings
        fun put(n: Settings) { settings = n; onSettings(n); fill(HomeMenu.SETTINGS) }

        overlayBody.addView(section("캐릭터"))
        settingRow("이름 바꾸기", "지금: ${avatar.name}") { askText("캐릭터 이름", avatar.name) { onAvatar(avatar.copy(name = it.ifBlank { avatar.name })) } }
        settingRow("캐릭터 다시 만들기", "성별을 다시 골라요. 외형은 기본으로, 성격·레벨·기록은 유지(성격은 옷장에서)") { onRecreate() }
        toggleRow("캐릭터 말 자막", "주행 화면 아래 한 줄", s.captions) { put(s.copy(captions = it)) }
        toggleRow("정차 중 애니메이션", "숨쉬기·환영 동작. 주행 중엔 원래 없음", s.animations) { put(s.copy(animations = it)) }

        overlayBody.addView(section("주행 중 (심사 기준: 운전자가 끌 수 있어야 해요)"))
        toggleRow("주행 중 제안", "식사·카페·휴식·충전·이야기·경로 제안 전체", s.suggestionsEnabled) { put(s.copy(suggestionsEnabled = it)) }
        settingRow("말 양", "지금: ${s.talkativeness.label} · 시간당 최대 ${s.talkativeness.maxPerHour}회") { }
        choiceRow(Talkativeness.entries.map { it.label to (it == s.talkativeness) }) { i -> put(s.copy(talkativeness = Talkativeness.entries[i])) }
        settingRow("제안 종류", "끈 종류는 제안하지 않아요") { }
        choiceRow(SuggestionKind.entries.map { KIND_LABELS[it]!! to s.kindEnabled(it) }, multi = true) { i ->
            val k = SuggestionKind.entries[i]
            put(s.copy(disabledKinds = if (s.kindEnabled(k)) s.disabledKinds + k else s.disabledKinds - k))
        }
        settingRow("제안 금지 시간대", if (s.quietFrom == s.quietTo) "없음" else "${s.quietFrom}시 ~ ${s.quietTo}시 (예: 출근길 7~9시)") {
            askText("시작시~끝시 (예: 7~9, 없애려면 0~0)", "${s.quietFrom}~${s.quietTo}") { v ->
                val m = Regex("(\\d{1,2})\\D+(\\d{1,2})").find(v)
                if (m != null) put(s.copy(quietFrom = m.groupValues[1].toInt().coerceIn(0, 23), quietTo = m.groupValues[2].toInt().coerceIn(0, 23)))
            }
        }
        settingRow("대답 기다리는 시간", "지금: ${s.replyWait.label}") { }
        choiceRow(ReplyWait.entries.map { it.label to (it == s.replyWait) }) { i -> put(s.copy(replyWait = ReplyWait.entries[i])) }
        toggleRow("대답 없으면 거절로", "끄면 '보류'로 두고 잠시 뒤 다시 물어요", s.noReplyIsDecline) { put(s.copy(noReplyIsDecline = it)) }
        toggleRow("안전 안내 음성", "어린이보호구역 등. 끄면 글자로만", s.safetyVoice) { put(s.copy(safetyVoice = it)) }
        toggleRow("주행 중 최소 화면", "캐릭터만, 글자·칩 없음", s.minimalDriving) { put(s.copy(minimalDriving = it)) }
        toggleRow("동승자 모드", "제안을 쉬고 조용히", s.passengerMode) { put(s.copy(passengerMode = it)) }

        overlayBody.addView(section("음성"))
        val volTitle = text("목소리 크기 · ${s.voiceVolume}%", 19f, "#15181D").apply { paint.isFakeBoldText = true }
        val volCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#F6F7F9")) }
            setPadding(dp(22), dp(16), dp(22), dp(16))
        }
        volCol.addView(volTitle)
        volCol.addView(text("캐릭터 목소리만. 차 전체 음량은 그대로", 15f, "#6B7380"))
        volCol.addView(android.widget.SeekBar(activity).apply {
            max = 100; progress = s.voiceVolume
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#3D7BFF"))
            thumbTintList = progressTintList
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: android.widget.SeekBar, v: Int, fromUser: Boolean) { volTitle.text = "목소리 크기 · $v%" }
                override fun onStartTrackingTouch(b: android.widget.SeekBar) = Unit
                override fun onStopTrackingTouch(b: android.widget.SeekBar) { if (b.progress != s.voiceVolume) put(s.copy(voiceVolume = b.progress)) }
            })
        }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(6) })
        overlayBody.addView(volCol, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        settingRow("읽기 톤", (s.voiceStyle?.label ?: "성격 따라 자동") + " · 녹음에 없는 문장(목적지·시각)을 읽을 때만") { }
        choiceRow(listOf("자동" to (s.voiceStyle == null)) + VoiceStyle.entries.map { it.label to (it == s.voiceStyle) }) { i ->
            put(s.copy(voiceStyle = if (i == 0) null else VoiceStyle.entries[i - 1]))
        }
        onDiagnostics().forEach { (k, v) -> statRow(k, v) }

        overlayBody.addView(section("개인정보 · 데이터"))
        settingRow("수집하는 것 / 안 하는 것", "속도·기어·지시등·와이퍼·감속도(차 안에서만 계산). 위치는 제안 거리 계산에만. 서버 전송 없음, 녹음 저장 없음.") { }
        toggleRow("방문 장소 기록", "끄면 탐험 도장·새 지역 보너스가 안 쌓여요", s.keepPlaces) { put(s.copy(keepPlaces = it)) }
        settingRow("데이터 내보내기", "설정·캐릭터·성장 기록을 JSON 파일로") { exportData() }
        settingRow("데이터 초기화", "캐릭터·레벨·업적·설정 전부 삭제. 되돌릴 수 없어요") { confirmReset() }

        overlayBody.addView(section("정보"))
        settingRow("튜토리얼 다시 보기", "메뉴 설명을 다시 봅니다") { close(); onReplayTutorial() }
        settingRow("앱 정보", "car-pet ${BuildConfig.VERSION_NAME} · Pleos 앱 공모전 출품작 · 개발 중 빌드") { }
        if (BuildConfig.DEBUG) settingRow("데모 패널 열기/닫기", "개발용. 심사 빌드에는 없음") { onDemoPanel() }
        if (BuildConfig.DEBUG) settingRow("코인 +100 (개발용)", "간식·보상 테스트") { lobby = lobby.addCoins(100); profiles.lobby = lobby; barCoins.text = "🪙 ${coinLabel(lobby.coins)}" }
        toggleRow("관리자 모드", "옷·머리·머리색·배경 전부 해금 + 코인 무제한(시연용). 끄면 원래대로 돌아가요", inventory.admin) {
            profiles.adminMode = it; inventory = profiles.inventory
            lobby = profiles.lobby; barCoins.text = "🪙 ${coinLabel(lobby.coins)}"
            fill(HomeMenu.SETTINGS)
        }
    }

    private fun personalityPicker(refresh: () -> Unit) {
        overlayBody.addView(section("성격 · 지금 ${avatar.personality.title}"))
        overlayBody.addView(text("말투와 인사말이 달라져요", 14f, "#6B7380").apply { setPadding(dp(4), 0, 0, dp(8)) })
        Personality.entries.forEach { p ->
            val on = p == avatar.personality
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(14), dp(18), dp(14))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat(); setColor(Color.parseColor(if (on) "#EAF1FF" else "#F6F7F9"))
                    setStroke(dp(if (on) 3 else 1), Color.parseColor(if (on) "#3D7BFF" else "#E3E6EB"))
                }
                addView(text("${PERSONALITY_ICON[p]} ${p.title}  ${p.mbtiHint}", 20f, if (on) "#3D7BFF" else "#15181D").apply { paint.isFakeBoldText = true })
                addView(text(p.description, 15f, "#15181D"))
                addView(text("“${p.greeting}”", 14f, "#6B7380"))
                setOnClickListener { if (!on) { onAvatar(avatar.copy(personality = p)); say(p.greeting); refresh() } }
            }
            overlayBody.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun voicePicker(refresh: () -> Unit) {
        val now = currentVoice()
        val lvl = if (inventory.admin) Int.MAX_VALUE else profile.level
        overlayBody.addView(section("캐릭터 목소리 · 지금 ${now.label}"))
        overlayBody.addView(text("고르면 바로 들려줘요. 레벨이 오르면 새 목소리가 열려요", 14f, "#6B7380").apply { setPadding(dp(4), 0, 0, dp(8)) })
        VoiceTier.entries.forEach { tier ->
            overlayBody.addView(text(tier.label, 15f, "#6B7380").apply { setPadding(dp(4), dp(4), 0, dp(6)) })
            VoiceOption.entries.filter { it.tier == tier }.chunked(4).forEach { chunk ->
                val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                for (k in 0 until 4) {
                    val v = chunk.getOrNull(k)
                    val lp = LinearLayout.LayoutParams(0, -2, 1f).apply { if (k < 3) marginEnd = dp(6) }
                    if (v == null) { row.addView(View(activity), lp); continue }
                    val open = v.isUnlocked(lvl)
                    row.addView(voiceChip(v, v == now, open) {
                        if (!open) say("🔒 ${v.label} 목소리는 Lv.${v.unlockLevel}에 열려요")
                        else if (v != now) { onVoice(v); refresh() }
                    }, lp)
                }
                overlayBody.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
    }

    private fun voiceChip(v: VoiceOption, on: Boolean, open: Boolean, onClick: () -> Unit) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        background = GradientDrawable().apply { cornerRadius = 999f; setColor(Color.parseColor(if (on) "#3D7BFF" else "#E3E6EB")) }
        setPadding(dp(12), dp(8), dp(14), dp(8)); alpha = if (open) 1f else 0.5f
        addView(ImageView(activity).apply { setImageBitmap(voiceFace(v)) },
            LinearLayout.LayoutParams(dp(VOICE_FACE_DP), dp(VOICE_FACE_DP)).apply { marginEnd = dp(8) })
        addView(text(if (open) v.label else "🔒 ${v.label} Lv.${v.unlockLevel}", 20f, if (on) "#FFFFFF" else "#15181D").apply { maxLines = 1 })
        setOnClickListener { onClick() }
    }

    private fun voiceFace(v: VoiceOption): Bitmap = voiceFaces.getOrPut(v.tier to v.female) {
        val res = when (v.tier) {
            VoiceTier.BASIC -> if (v.female) R.drawable.ic_voice_woman else R.drawable.ic_voice_man
            VoiceTier.YOUNG -> if (v.female) R.drawable.ic_voice_girl else R.drawable.ic_voice_boy
            VoiceTier.MATURE -> if (v.female) R.drawable.ic_voice_lady else R.drawable.ic_voice_gentleman
        }
        val px = dp(VOICE_FACE_DP)
        val k = px / 60f
        Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { b ->
            val c = android.graphics.Canvas(b)
            c.translate(-18 * k, -8 * k)
            activity.getDrawable(res)!!.apply { setBounds(0, 0, (96 * k).toInt(), (96 * k).toInt()); draw(c) }
        }
    }
    private val voiceFaces = mutableMapOf<Pair<VoiceTier, Boolean>, Bitmap>()

    private fun toggleRow(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#F6F7F9")) }
            setPadding(dp(22), dp(16), dp(22), dp(16))
            setOnClickListener { onChange(!on) }
        }
        val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(title, 19f, "#15181D").apply { paint.isFakeBoldText = true })
        col.addView(text(desc, 15f, "#6B7380"))
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Switch(activity).apply { isChecked = on; setOnCheckedChangeListener { _, v -> if (v != on) onChange(v) } })
        overlayBody.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun choiceRow(items: List<Pair<String, Boolean>>, multi: Boolean = false, onPick: (Int) -> Unit) {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEachIndexed { i, (label, on) ->
            row.addView(chipText(label, on, true) { onPick(i) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        }
        overlayBody.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun askText(title: String, current: String, onDone: (String) -> Unit) {
        val input = EditText(activity).apply { setText(current); setTextColor(Color.parseColor("#15181D")) }
        AlertDialog.Builder(activity).setTitle(title).setView(input)
            .setPositiveButton("저장") { _, _ -> onDone(input.text.toString().trim()) }
            .setNegativeButton("취소", null).show()
    }

    private fun exportData() {
        val dir = activity.getExternalFilesDir(null) ?: activity.filesDir
        val f = File(dir, "carpet_export_${LocalDate.now()}.json")
        runCatching { f.writeText(SettingsStore.exportJson(activity)) }
            .onSuccess { Toast.makeText(activity, "저장했어요: ${f.absolutePath}", Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(activity, "저장 실패", Toast.LENGTH_SHORT).show() }
    }

    private fun confirmReset() {
        AlertDialog.Builder(activity).setTitle("정말 초기화할까요?").setMessage("캐릭터, 레벨, 업적, 코인, 설정이 전부 지워져요. 되돌릴 수 없어요.")
            .setPositiveButton("한 번 더 확인") { _, _ ->
                AlertDialog.Builder(activity).setTitle("마지막 확인").setMessage("${avatar.name}와(과)의 기록이 사라져요.")
                    .setPositiveButton("초기화") { _, _ -> onWipe() }.setNegativeButton("취소", null).show()
            }
            .setNegativeButton("취소", null).show()
    }

    private fun daysTogether(): Long {
        val t0 = avatarStore.createdAtMs
        return if (t0 == 0L) 1 else 1 + TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - t0)
    }

    private fun menuButton(m: HomeMenu): View {
        val b = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(Color.WHITE) }
            setPadding(dp(24), dp(20), dp(24), dp(20))
            elevation = dp(2).toFloat()
            addView(ImageView(activity).apply { setImageResource(m.iconRes) }, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(text(m.label, 30f, "#15181D").apply { setPadding(dp(14), 0, 0, 0); paint.isFakeBoldText = true })
            setOnClickListener { openMenu(m) }
        }
        val dot = View(activity).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#FF4D4D")) }
            visibility = View.GONE
        }
        menuDots[m] = dot
        return FrameLayout(activity).apply {
            addView(b, FrameLayout.LayoutParams(-1, -2))
            addView(dot, FrameLayout.LayoutParams(dp(12), dp(12), Gravity.TOP or Gravity.END).apply { topMargin = dp(2); marginEnd = dp(2) })
        }
    }

    private fun chip(icon: String, label: String, onClick: () -> Unit, iconRes: Int = 0): View {
        val t = text(if (iconRes != 0) label else if (label.isEmpty()) icon else "$icon $label", 22f, "#15181D").apply {
            if (iconRes != 0) {
                val d = activity.getDrawable(iconRes)?.apply { setBounds(0, 0, dp(36), dp(36)) }
                setCompoundDrawablesRelative(d, null, null, null); compoundDrawablePadding = if (label.isEmpty()) 0 else dp(10); gravity = Gravity.CENTER_VERTICAL
            }
            background = GradientDrawable().apply { cornerRadius = 999f; setColor(Color.WHITE) }
            setPadding(dp(20), dp(14), dp(20), dp(14)); elevation = dp(2).toFloat()
            setOnClickListener { onClick() }
        }
        return FrameLayout(activity).apply { addView(t, FrameLayout.LayoutParams(-2, -2)) }
    }

    private fun button(label: String, enabled: Boolean, onClick: () -> Unit) = text(label, 19f, "#FFFFFF").apply {
        gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(18)); paint.isFakeBoldText = true
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor(if (enabled) "#3D7BFF" else "#C3C7CF")) }
        isEnabled = enabled
        setOnClickListener { onClick() }
    }

    private fun statRow(label: String, value: String) {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, dp(10)) }
        row.addView(text(label, 17f, "#6B7380"), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text(value, 19f, "#15181D").apply { paint.isFakeBoldText = true })
        overlayBody.addView(row)
    }

    private fun rankRow(rank: String, who: String, score: String, me: Boolean): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor(if (me) "#EAF1FF" else "#F6F7F9")) }
            setPadding(dp(22), dp(16), dp(22), dp(16))
        }
        row.addView(text(rank, 22f, "#15181D").apply { paint.isFakeBoldText = true }, LinearLayout.LayoutParams(dp(40), -2))
        row.addView(text(who, 18f, "#15181D"), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text(score, 17f, "#6B7380"))
        overlayBody.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        return row
    }

    private fun settingRow(title: String, desc: String, onClick: () -> Unit) {
        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#F6F7F9")) }
            setPadding(dp(22), dp(16), dp(22), dp(16))
            setOnClickListener { onClick() }
        }
        col.addView(text(title, 19f, "#15181D").apply { paint.isFakeBoldText = true })
        col.addView(text(desc, 15f, "#6B7380"))
        overlayBody.addView(col, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun coinLabel(c: Int) = if (c >= 100_000) "∞" else "$c"

    private fun section(t: String) = text(t, 20f, "#6B7380").apply { setPadding(0, dp(14), 0, dp(8)) }

    private fun text(t: String, sizeSp: Float, color: String) = TextView(activity).apply {
        text = t; textSize = scaled(sizeSp); setTextColor(Color.parseColor(color)); breakByPhrase()
    }

    private fun scaled(sizeSp: Float) = minOf(maxOf(sizeSp, 30f), sizeSp * 1.25f)

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    private companion object {
        const val LINE_MS = 4_000L
        const val SPOKEN_MS = 8_000L
        const val THINK_MS = 12_000L
        const val LAST_DRIVE_MS = 20_000L
        const val REACTION_MS = 1_500L
        const val WAKE_MS = 60_000L
        const val BORED_MS = 3 * 60_000L
        const val LEVEL_UP_COINS = 50
        const val PERSONALITY_MASK = "personality_mask"
        const val ALBUM_SHOWN = 4
        const val ALBUM_PIC_W = 56
        const val ALBUM_PIC_H = 88
        const val UNDO_KEPT = 10

        val PERSONALITY_ICON = mapOf(Personality.ENERGETIC to "⚡", Personality.CALM to "🍃", Personality.PLAYFUL to "😜", Personality.WARM to "🌸", Personality.COOL to "🧊")
        const val SLEEP_FROM = 0
        const val SLEEP_TO = 5
        val SHOP_SLOTS = listOf("hair", "hairColor", "outfit")
        val SLOT_LABELS = mapOf("hair" to "머리 모양", "hairColor" to "머리색", "outfit" to "옷", "eyes" to "눈", "eyeColor" to "눈동자색", "brows" to "눈썹", "mouth" to "입", "skin" to "피부색", "aura" to "아우라")
        val WARDROBE_TABS = listOf(
            Triple("hair", "💇", "머리"), Triple("outfit", "👕", "옷"), Triple("eyes", "👀", "눈"), Triple("skin", "🙂", "얼굴"),
            Triple("aura", "✨", "아우라"), Triple("bg", "🏞️", "배경"), Triple("voice", "🎙️", "목소리"), Triple("personality", "🎭", "성격"), Triple("more", "⋯", "더보기"),
        )

        val SLOT_GLYPH = WARDROBE_TABS.associate { it.first to it.second }
        const val GRID_COLS = 4
        const val VOICE_FACE_DP = 40
        const val TILE_IMG = 150
        const val BG_THUMB_H = 140
        const val GREETING_MAX_DP = 400
        const val GREETING_TOP_DP = 40
        const val GREETING_GAP_DP = 260
        val ACC_UNLOCK_LEVEL = mapOf("cap" to 5, "glasses" to 10, "fedora" to 20, "ribbon" to 30, "crown" to 50)
        val KIND_LABELS = mapOf(
            SuggestionKind.MEAL to "식사", SuggestionKind.DRIVE_THRU to "카페", SuggestionKind.REST to "휴식",
            SuggestionKind.CHARGE to "충전", SuggestionKind.STORY to "이야기", SuggestionKind.ROUTE to "경로",
        )
    }
}

class TutorialOverlay(private val activity: Activity, private val home: HomeScreen, private val onDone: () -> Unit) {
    private val root: FrameLayout = activity.findViewById(R.id.tutorialOverlay)
    private val title: TextView = activity.findViewById(R.id.tutorialTitle)
    private val body: TextView = activity.findViewById(R.id.tutorialBody)
    private val next: TextView = activity.findViewById(R.id.tutorialNext)
    private val progress: TextView = activity.findViewById(R.id.tutorialProgress)
    private var step = 0

    private data class Step(val menu: HomeMenu?, val title: String, val body: String)

    private val steps = listOf(
        Step(null, "반가워요!", "여기가 홈이에요. 가운데가 내 캐릭터. 톡 누르면 한마디, 두 번 누르면 깜짝, 길게 누르면 쓰다듬기(하루 3번, 친밀도 +1). 차를 세우면(P) 이 화면, 달리면 캐릭터가 조용히 옆에만 있어요."),
        Step(null, "위 바와 아래 칩", "위 바: 레벨·연속 출석·친밀도·기분·코인. 아래 칩: 오늘의 미션, 간식(코인 5), 오늘의 한마디, 출석 달력, 출발 준비, 사진, 숨기기."),
        Step(HomeMenu.STATUS, "캐릭터 상태", "레벨, 경험치, 함께한 기록, 기분. 경험치는 출석·함께 다녀온 길·새 지역·처음 겪는 일로만 올라요. 운전을 채점하지 않고, 많이 달린다고 오르지도 않아요."),
        Step(HomeMenu.RANKING, "순위", "친구와 레벨·업적을 비교해요. 정차 중에만 볼 수 있어요."),
        Step(HomeMenu.WARDROBE, "옷장", "머리 색·옷·소품. 처음엔 머리 색 5가지와 기본 옷만. 나머지는 해금 예정."),
        Step(HomeMenu.ACHIEVEMENTS, "업적", "함께한 날, 다녀온 길, 방문 지역 도장. 하나씩 모아요."),
        Step(HomeMenu.FRIENDS, "친구", "친구 코드로 추가하고, 하루 한 번 선물·응원·쓰다듬기. 메시지·채팅은 없어요."),
        Step(HomeMenu.SETTINGS, "설정", "캐릭터 다시 만들기, 튜토리얼 다시 보기, 생일."),
        Step(null, "준비 끝", "달리는 동안 캐릭터가 가끔 한마디 해요. 대답은 '응' 또는 '아니'로만. 그럼 출발해요!"),
    )

    init {
        next.setOnClickListener { advance() }
    }

    fun start() {
        step = 0
        home.close()
        root.visibility = View.VISIBLE
        render()
    }

    val isShowing get() = root.visibility == View.VISIBLE

    private fun advance() {
        step++
        if (step >= steps.size) { finish(); return }
        render()
    }

    private fun finish() {
        highlight(null)
        root.visibility = View.GONE
        onDone()
    }

    private fun render() {
        val s = steps[step]
        title.text = s.title
        body.text = s.body
        progress.text = "${step + 1} / ${steps.size}"
        next.text = if (step == steps.size - 1) "시작하기" else "다음"
        highlight(s.menu)
    }

    private fun highlight(menu: HomeMenu?) {
        home.menuButtons.forEach { (m, v) ->
            val on = m == menu
            v.scaleX = if (on) 1.12f else 1f
            v.scaleY = if (on) 1.12f else 1f
            v.alpha = if (menu == null || on) 1f else 0.35f
            ((v as? FrameLayout)?.getChildAt(0)?.background as? GradientDrawable)?.setStroke(if (on) 4 else 0, Color.parseColor("#3D7BFF"))
        }
    }
}
