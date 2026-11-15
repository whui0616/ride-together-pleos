package ai.nunchi.mate.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

object AssetImages {
    private const val BUDGET_BYTES = 28L * 1024 * 1024

    private val cache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {}
    private var used = 0L

    fun bitmap(context: Context, path: String, maxW: Int = 0): Bitmap = synchronized(cache) {
        cache[path]?.let { return it }
        val assets = context.applicationContext.assets
        val opts = BitmapFactory.Options()
        if (maxW > 0) {
            opts.inJustDecodeBounds = true
            assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outWidth / (sample * 2) >= maxW) sample *= 2
            opts.inSampleSize = sample
            opts.inJustDecodeBounds = false
        }
        val b = assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) } ?: error("decode $path")
        cache[path] = b
        used += b.allocationByteCount
        val it = cache.entries.iterator()
        while (used > BUDGET_BYTES && cache.size > 1 && it.hasNext()) {
            val e = it.next()
            if (e.key == path) continue
            used -= e.value.allocationByteCount
            it.remove()
        }
        b
    }

}

class CoverDrawable(private val bitmap: Bitmap) : Drawable() {
    private val paint = Paint()
    private val dst = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val s = maxOf(b.width() / bitmap.width.toFloat(), b.height() / bitmap.height.toFloat())
        val w = bitmap.width * s; val h = bitmap.height * s
        dst.set(b.exactCenterX() - w / 2, b.exactCenterY() - h / 2, b.exactCenterX() + w / 2, b.exactCenterY() + h / 2)
        canvas.drawBitmap(bitmap, null, dst, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
