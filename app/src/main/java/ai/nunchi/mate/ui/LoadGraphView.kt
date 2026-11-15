package ai.nunchi.mate.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class LoadGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Mark(val color: Int, val label: String) {
        HELD(Color.parseColor("#F5A524"), "보류"),
        SPOKE(Color.parseColor("#2DBE8C"), "발화"),
        INTERRUPTED(Color.parseColor("#F2555A"), "중단"),
    }

    private val windowMs = 60_000L
    private val samples = ArrayDeque<Pair<Long, Float>>()
    private val marks = ArrayDeque<Pair<Long, Mark>>()
    private var nowMs = 0L

    private val density = resources.displayMetrics.density
    private val band = Paint()
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255)
        strokeWidth = density
        pathEffect = DashPathEffect(floatArrayOf(4 * density, 4 * density), 0f)
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        strokeJoin = Paint.Join.ROUND
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f * density }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        textSize = 13 * density
    }
    private val path = Path()

    fun add(now: Long, score: Float) {
        nowMs = now
        samples.addLast(now to score)
        while (samples.isNotEmpty() && now - samples.first().first > windowMs) samples.removeFirst()
        while (marks.isNotEmpty() && now - marks.first().first > windowMs) marks.removeFirst()
        invalidate()
    }

    fun mark(now: Long, kind: Mark) {
        marks.addLast(now to kind)
        invalidate()
    }

    fun clear() {
        samples.clear()
        marks.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val legendH = 20 * density
        val w = width.toFloat()
        val h = height - legendH
        fun y(score: Float) = h - score / 100f * h
        fun x(t: Long) = w - (nowMs - t).toFloat() / windowMs * w

        canvas.drawColor(Color.parseColor("#11151A"))
        band.color = Color.argb(46, 242, 85, 90); canvas.drawRect(0f, y(100f), w, y(60f), band)
        band.color = Color.argb(40, 245, 165, 36); canvas.drawRect(0f, y(60f), w, y(30f), band)
        band.color = Color.argb(40, 45, 190, 140); canvas.drawRect(0f, y(30f), w, y(0f), band)
        for (v in floatArrayOf(30f, 60f)) {
            canvas.drawLine(0f, y(v), w, y(v), grid)
            canvas.drawText(v.toInt().toString(), 4 * density, y(v) - 3 * density, text)
        }

        for ((t, kind) in marks) {
            markPaint.color = kind.color
            val mx = x(t)
            canvas.drawLine(mx, 0f, mx, h, markPaint)
            canvas.drawCircle(mx, 6 * density, 4 * density, markPaint)
        }

        path.reset()
        samples.forEachIndexed { i, (t, s) ->
            if (i == 0) path.moveTo(x(t), y(s)) else path.lineTo(x(t), y(s))
        }
        canvas.drawPath(path, line)

        var lx = 4 * density
        val ly = height - 4 * density
        canvas.drawText("최근 60초", lx, ly, text)
        lx += text.measureText("최근 60초") + 12 * density
        for (m in Mark.entries) {
            markPaint.color = m.color
            canvas.drawCircle(lx + 4 * density, ly - 3.5f * density, 4 * density, markPaint)
            lx += 11 * density
            canvas.drawText(m.label, lx, ly, text)
            lx += text.measureText(m.label) + 12 * density
        }
    }
}
