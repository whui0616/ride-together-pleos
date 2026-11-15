package ai.nunchi.mate.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

enum class Mood {
    CALM,
    TALKING,
    FOCUS,
    LISTENING,
    HAPPY,
    SLEEPY,
    SAD,
    SURPRISED,
}

enum class VehicleState(val accent: Int) {
    CRUISE(Color.rgb(38, 201, 220)),
    SUGGESTING(Color.rgb(45, 126, 247)),
    CHARGING(Color.rgb(91, 205, 109)),
    ATTENTION(Color.rgb(239, 163, 42)),
    REST(Color.rgb(130, 106, 222)),
    PARKED(Color.rgb(245, 190, 55)),
}

class CompanionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var mood = Mood.CALM
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    var look = CharacterLook.BASE
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    var vehicleState = VehicleState.CRUISE
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    var gender = "f"
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    var headZoom = false
        set(value) { if (field != value) { field = value; invalidate() } }

    var pose: String? = null
        set(value) { field = value; invalidate() }

    var jointOverrides: Map<String, Float> = emptyMap()
        set(value) { field = value; invalidate() }

    var idle = false
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    private val statik = StaticRig.get(context)
    private val body = RectF()
    private val bmpDst = RectF()
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var composed: StaticRig.Composed? = null
    private var composedKey: Any? = null
    private var bodyScale = 1f
    private var seconds = 0f
    private var running = false

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            seconds = (SystemClock.uptimeMillis() % 3_600_000L) / 1000f
            invalidate()
            postDelayed(this, if (!idle) 100L else if (Aura.isOn(look.aura)) 60L else 66L)
        }
    }

    private fun setRunning(on: Boolean) {
        if (running == on) return
        running = on
        removeCallbacks(frame)
        if (on) post(frame)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        setRunning(isVisible && isAttachedToWindow)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setRunning(isShown)
    }

    override fun onDetachedFromWindow() {
        setRunning(false)
        composed?.bitmap?.recycle(); composed = null; composedKey = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val scale = min(width, height) / 100f
        bodyScale = scale
        canvas.save()
        canvas.translate((width - 100 * scale) / 2, (height - 100 * scale) / 2)
        canvas.scale(scale, scale)

        drawCharacter(canvas)
        canvas.restore()
    }

    private fun drawCharacter(canvas: Canvas) {
        statik.fit(gender, CHARACTER_BOUNDS, body)
        val t = seconds
        val px = body.centerX(); val py = body.bottom
        val aura = idle && !headZoom && Aura.isOn(look.aura)
        val ms = SystemClock.uptimeMillis()
        canvas.save()
        if (headZoom) canvas.scale(HEAD_ZOOM, HEAD_ZOOM, px, body.top + body.height() * HEAD_PIVOT_Y)
        if (idle) {
            var rot = 0f; var dy = 0f; var sy = 1f
            when (pose ?: "welcome") {
                "wave" -> rot = 5f * wave(t, 0.9f)
                "welcome" -> Unit
                "cheer" -> dy = -abs(wave(t, 0.7f)) * body.height() * 0.05f
                "point" -> rot = -4f
                "think" -> rot = 6f
                else -> Unit
            }
            when (mood) {
                Mood.SURPRISED -> dy -= abs(wave(t, 0.5f)) * body.height() * 0.04f
                Mood.SLEEPY -> rot += 8f
                Mood.SAD -> { rot += 3f; sy *= 0.97f }
                Mood.TALKING -> sy *= 1f + 0.01f * wave(t, 0.35f)
                else -> Unit
            }
            canvas.translate(0f, dy)
            canvas.rotate(rot, px, py)
            canvas.scale(1f, sy, px, py)
        }
        if (aura) Aura.drawBack(context, canvas, look.aura, body, ms)
        drawComposed(canvas)
        if (aura) Aura.drawFront(context, canvas, look.aura, body, ms)
        canvas.restore()
    }

    private fun drawComposed(canvas: Canvas) {
        val s = bodyScale * (if (headZoom) HEAD_ZOOM else 1f)
        val pw = (body.width() * s).toInt().coerceAtLeast(1)
        val ph = (body.height() * s).toInt().coerceAtLeast(1)
        val key = listOf(gender, look, pw, ph)
        if (key != composedKey) {
            composed?.bitmap?.recycle()
            composed = statik.compose(gender, look, pw, ph); composedKey = key
        }
        val c = composed ?: return
        bmpDst.set(
            body.left + c.dx / s, body.top + c.dy / s,
            body.left + (c.dx + c.bitmap.width) / s, body.top + (c.dy + c.bitmap.height) / s,
        )
        canvas.drawBitmap(c.bitmap, null, bmpDst, bmpPaint)
    }

    private fun wave(t: Float, period: Float): Float = sin(t * 2f * PI.toFloat() / period)

    private companion object {
        val CHARACTER_BOUNDS = RectF(18f, 1f, 82f, 99f)
        const val HEAD_ZOOM = 2f
        const val HEAD_PIVOT_Y = -0.18f
    }
}
