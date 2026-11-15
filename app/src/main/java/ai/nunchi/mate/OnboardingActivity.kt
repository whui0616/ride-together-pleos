package ai.nunchi.mate

import ai.nunchi.mate.avatar.Avatar
import ai.nunchi.mate.avatar.AvatarStore
import ai.nunchi.mate.avatar.Gender
import ai.nunchi.mate.ui.CompanionView
import ai.nunchi.mate.ui.Mood
import ai.nunchi.mate.ui.VehicleState
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View

import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class OnboardingActivity : Activity() {

    private lateinit var store: AvatarStore
    private var avatar = Avatar()
    private var step = 0

    private lateinit var preview: CompanionView

    private lateinit var stepTitle: TextView
    private lateinit var stepHint: TextView
    private lateinit var content: LinearLayout
    private lateinit var dots: LinearLayout
    private lateinit var prevBtn: TextView
    private lateinit var nextBtn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AvatarStore(this)
        val force = intent.getBooleanExtra(EXTRA_FORCE, false)
        if (store.created && !force) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        if (force) avatar = store.load().let { it.copy(look = it.gender.defaultLook) }
        setContentView(buildLayout())
        render()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (step > 0) { step--; render() } else moveTaskToBack(true)
    }

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#F2F3F5"))
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = card()
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        preview = CompanionView(this).apply { idle = true; mood = Mood.HAPPY; vehicleState = VehicleState.PARKED }
        left.addView(preview, LinearLayout.LayoutParams(dp(560), dp(650)))
        left.addView(text("미리보기", 18f, "#6B7380").apply { gravity = Gravity.CENTER })
        root.addView(left, LinearLayout.LayoutParams(0, -1, 1f))

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = card()
            setPadding(dp(28), dp(24), dp(28), dp(24))
        }
        dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        right.addView(dots)

        stepTitle = text("", 32f, "#15181D").apply { paint.isFakeBoldText = true; setPadding(0, dp(12), 0, 0) }
        stepHint = text("", 18f, "#6B7380").apply { setPadding(0, dp(4), 0, dp(16)) }
        right.addView(stepTitle)
        right.addView(stepHint)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(content) }
        right.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0) }
        prevBtn = button("이전", primary = false) { if (step > 0) { step--; render() } }
        nextBtn = button("다음", primary = true) { next() }
        nav.addView(prevBtn, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        nav.addView(nextBtn, LinearLayout.LayoutParams(0, -2, 2f))
        right.addView(nav)
        root.addView(right, LinearLayout.LayoutParams(0, -1, 1.3f).apply { marginStart = dp(20) })
        return root
    }

    private fun render() {
        preview.look = avatar.look
        preview.gender = if (avatar.gender == Gender.MALE) "m" else "f"
        content.removeAllViews()
        dots.removeAllViews()
        repeat(STEPS) { i ->
            dots.addView(View(this).apply {
                background = GradientDrawable().apply {
                    cornerRadius = 99f
                    setColor(Color.parseColor(if (i <= step) "#3D7BFF" else "#D9DDE3"))
                }
            }, LinearLayout.LayoutParams(dp(if (i == step) 36 else 12), dp(12)).apply { marginEnd = dp(8) })
        }
        prevBtn.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        nextBtn.text = if (step == STEPS - 1) "이 캐릭터로 시작" else "다음"
        renderGender()
    }

    private fun next() {
        if (step < STEPS - 1) { step++; render(); return }
        store.save(avatar)
        val profiles = ProfileStore(this)
        val inv = profiles.inventory
        val starter = listOfNotNull(ai.nunchi.engine.Catalog.of("hair", avatar.look.hair), ai.nunchi.engine.Catalog.of("hairColor", avatar.look.hairColor)).map { it.id }
        if (starter.any { it !in inv.owned || it in inv.wishlist }) profiles.inventory = inv.copy(owned = inv.owned + starter, wishlist = inv.wishlist - starter.toSet())
        if (!store.created) {
            store.created = true
            store.createdAtMs = System.currentTimeMillis()
            store.tutorialDone = false
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun renderGender() {
        stepTitle.text = "함께 탈 캐릭터"
        stepHint.text = "성별만 골라요. 머리·옷은 홈의 옷장에서 바꿀 수 있어요."
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Gender.entries.forEach { g ->
            val on = avatar.gender == g
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = choice(on)
                setPadding(dp(16), dp(20), dp(16), dp(20))
                setOnClickListener {
                    avatar = avatar.copy(gender = g, look = g.defaultLook)
                    render()
                }
            }
            tile.addView(mini(g.defaultLook, g), LinearLayout.LayoutParams(dp(200), dp(230)))
            tile.addView(text(g.label, 26f, if (on) "#3D7BFF" else "#15181D").apply { paint.isFakeBoldText = true })
            row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(12) })
        }
        content.addView(row)
    }

    private fun mini(look: ai.nunchi.mate.ui.CharacterLook, g: Gender = avatar.gender) = CompanionView(this).apply {
        this.look = look; gender = if (g == Gender.MALE) "m" else "f"; mood = Mood.CALM; vehicleState = VehicleState.PARKED; pose = "idle"
    }

    private fun text(t: String, sizeSp: Float, color: String) = TextView(this).apply {
        text = t; textSize = minOf(maxOf(sizeSp, 30f), sizeSp * 1.25f); setTextColor(Color.parseColor(color))
    }

    private fun label(t: String) = text(t, 18f, "#6B7380").apply { setPadding(0, dp(14), 0, dp(8)) }

    private fun button(t: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = t; textSize = 26f; gravity = Gravity.CENTER
        setTextColor(if (primary) Color.WHITE else Color.parseColor("#15181D"))
        background = pill(if (primary) "#3D7BFF" else "#E3E6EB")
        setPadding(dp(24), dp(18), dp(24), dp(18))
        setOnClickListener { onClick() }
    }

    private fun card() = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(Color.WHITE) }
    private fun pill(hex: String) = GradientDrawable().apply { cornerRadius = 999f; setColor(Color.parseColor(hex)) }
    private fun choice(on: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(18).toFloat()
        setColor(Color.parseColor(if (on) "#EAF1FF" else "#F6F7F9"))
        setStroke(dp(if (on) 3 else 1), Color.parseColor(if (on) "#3D7BFF" else "#E3E6EB"))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_FORCE = "force"
        private const val STEPS = 1
    }
}
