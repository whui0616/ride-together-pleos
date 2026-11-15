package ai.nunchi.mate.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

object Aura {
    data class Def(val id: String, val label: String)

    const val NONE = "none"
    val AURAS = listOf(
        Def(NONE, "없음"), Def("water", "물"), Def("fire", "불"), Def("electric", "전기"), Def("rainbow", "무지개"),
        Def("ice", "얼음"), Def("wind", "바람"), Def("nature", "자연"), Def("shadow", "어둠"), Def("light", "빛"), Def("cosmic", "별빛"),
    )

    private const val FRAMES = 24
    private const val FRAME_MS = 60L
    private const val COLS = 6
    private const val BACK_W = 240; private const val BACK_H = 360
    private const val FRONT_W = 240; private const val FRONT_H = 24
    private const val THUMB_FRAME = 6

    private var loadedId: String? = null
    private var back: Bitmap? = null
    private var front: Bitmap? = null

    private fun open(context: Context, id: String, file: String): Bitmap? =
        runCatching { context.assets.open("character/effects/$id/$file").use { BitmapFactory.decodeStream(it) } }.getOrNull()

    private fun sheets(context: Context, id: String): Boolean = synchronized(this) {
        if (loadedId != id) {
            back = open(context, id, "back.png"); front = open(context, id, "front.png"); loadedId = id
        }
        back != null
    }

    private val paint = Paint()
    private val src = Rect()
    private val dst = RectF()

    fun isOn(id: String) = id != NONE && AURAS.any { it.id == id }

    private fun place(body: RectF, x: Float, y: Float, w: Float, h: Float) {
        val kx = body.width() / 364f; val ky = body.height() / 572f
        val ox = body.left - (40 + 18) * kx; val oy = body.top - (55 + 14) * ky
        dst.set(ox + x * kx, oy + y * ky, ox + (x + w) * kx, oy + (y + h) * ky)
    }

    private fun frame(ms: Long) = ((ms / FRAME_MS) % FRAMES).toInt()

    fun drawBack(context: Context, canvas: Canvas, id: String, body: RectF, ms: Long) {
        if (!isOn(id) || !sheets(context, id)) return
        val b = back ?: return
        val f = frame(ms)
        src.set(f % COLS * BACK_W, f / COLS * BACK_H, f % COLS * BACK_W + BACK_W, f / COLS * BACK_H + BACK_H)
        place(body, 0f, 0f, 480f, 720f)
        canvas.drawBitmap(b, src, dst, paint)
    }

    fun drawFront(context: Context, canvas: Canvas, id: String, body: RectF, ms: Long) {
        if (!isOn(id) || !sheets(context, id)) return
        val b = front ?: return
        val f = frame(ms)
        src.set(f % COLS * FRONT_W, f / COLS * FRONT_H, f % COLS * FRONT_W + FRONT_W, f / COLS * FRONT_H + FRONT_H)
        place(body, 0f, 640f, 480f, 48f)
        canvas.drawBitmap(b, src, dst, paint)
    }

    fun thumb(context: Context, id: String, px: Int): Bitmap? {
        if (AURAS.none { it.id == id }) return null
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (id == NONE) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC3C7CF.toInt(); style = Paint.Style.STROKE; strokeWidth = px * 0.04f }
            c.drawCircle(px / 2f, px / 2f, px * 0.3f, p)
            c.drawLine(px * 0.29f, px * 0.71f, px * 0.71f, px * 0.29f, p)
            return out
        }
        val cell = runCatching {
            context.assets.open("character/effects/$id/back.png").use { s ->
                @Suppress("DEPRECATION") val dec = BitmapRegionDecoder.newInstance(s, false)!!
                val col = THUMB_FRAME % COLS; val row = THUMB_FRAME / COLS
                dec.decodeRegion(Rect(col * BACK_W, row * BACK_H, col * BACK_W + BACK_W, row * BACK_H + BACK_H), null).also { dec.recycle() }
            }
        }.getOrNull() ?: return out
        val h = px.toFloat(); val w = h * BACK_W / BACK_H
        c.drawBitmap(cell, null, RectF((px - w) / 2, 0f, (px + w) / 2, h), paint)
        cell.recycle()
        return out
    }
}
