package ai.nunchi.mate.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

class StaticRig private constructor(private val context: Context) {

    class Composed(val bitmap: Bitmap, val dx: Int, val dy: Int)

    val hairColors: List<LumiRig.HairColor> = HAIR_COLORS.map { (id, label, hex) -> Color.parseColor(hex).let { LumiRig.HairColor(id, label, it, it) } }

    fun options(gender: String, slot: String): List<LumiRig.Option> = when (slot) {
        "hair" -> (if (gender == "m") HAIR_M else HAIR_F).mapIndexed { i, l -> LumiRig.Option("$i", l) }
        "outfit" -> (if (gender == "m") OUTFIT_M else OUTFIT_F).mapIndexed { i, l -> LumiRig.Option("$i", l) }
        "eyes" -> (if (gender == "m") EYES_M else EYES_F).mapIndexed { i, l -> LumiRig.Option("$i", l) }
        "eyeColor" -> EYE_COLORS.mapIndexed { i, (l, _) -> LumiRig.Option("$i", l) }
        "brows" -> BROWS.mapIndexed { i, l -> LumiRig.Option("$i", l) }
        "mouth" -> MOUTHS.mapIndexed { i, l -> LumiRig.Option("$i", l) }
        "skin" -> SKIN_ORDER.map { LumiRig.Option("$it", SKINS[it]) }
        "aura" -> Aura.AURAS.map { LumiRig.Option(it.id, it.label) }
        "hairColor" -> hairColors.map { LumiRig.Option(it.id, it.label) }
        else -> emptyList()
    }

    fun label(gender: String, slot: String, option: String): String? = options(gender, slot).firstOrNull { it.id == option }?.label

    fun has(slot: String, option: String): Boolean = options("m", slot).any { it.id == option }

    private val bitmaps = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > LAYER_CACHE
    }
    private fun bitmap(file: String): Bitmap? = synchronized(bitmaps) {
        bitmaps[file] ?: runCatching { context.assets.open("character/pixel/$file").use { BitmapFactory.decodeStream(it) } }.getOrNull()?.also { bitmaps[file] = it }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun layers(gender: String, look: CharacterLook): List<String> {
        val g = if (gender == "m") "m" else "f"
        val h = look.hair.toIntOrNull()?.takeIf { it in HAIR_M.indices } ?: 0
        val hc = HAIR_COLORS.indexOfFirst { it.first == look.hairColor }.coerceAtLeast(0)
        val o = look.outfit.toIntOrNull()?.takeIf { it in OUTFIT_M.indices } ?: 0
        val e = look.eyes.toIntOrNull()?.takeIf { it in EYES_M.indices } ?: 0
        val ec = look.eyeColor.toIntOrNull()?.takeIf { it in EYE_COLORS.indices } ?: 0
        val br = look.brows.toIntOrNull()?.takeIf { it in BROWS.indices } ?: 0
        val mo = look.mouth.toIntOrNull()?.takeIf { it in MOUTHS.indices } ?: 0
        val s = look.skin.toIntOrNull()?.takeIf { it in SKINS.indices } ?: 1
        return listOfNotNull(
            "$g/hair/hair_${h}_color_${hc}_back.webp".takeIf { hasBack(g, h) },
            "$g/underhair/underhair_skin_$s.webp",
            "$g/outfits/outfit_%02d_skin_$s.webp".format(o),
            "$g/faces/face_0_skin_$s.webp",
            "common/blush.webp",
            "$g/eyes/eyes_${e}_color_$ec.webp",
            "common/brows/brows_${br}_color_$hc.webp",
            "common/mouth/mouth_$mo.webp",
            "$g/hair/hair_${h}_color_${hc}_front.webp",
            "$g/outfit_overlays/outfit_${o}_hair_$h.webp".takeIf { o in 23..25 },
        )
    }

    private val backings = object : LinkedHashMap<String, Bitmap?>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap?>?): Boolean = size > BACKING_CACHE
    }

    private fun backing(files: List<String>): Bitmap? = synchronized(backings) {
        val key = files.filter { f -> SILHOUETTE_DIRS.any { f.contains(it) } }.joinToString("|").replace(SKIN_SUFFIX, "")
        if (backings.containsKey(key)) backings[key] else buildBacking(files).also { backings[key] = it }
    }

    private fun buildBacking(files: List<String>): Bitmap? {
        val bmps = files.map { f -> (bitmap(f) ?: return null) to f.contains("/hair/") }
        val w = bmps[0].first.width; val h = bmps[0].first.height; val n = w * h
        if (bmps.any { it.first.width != w || it.first.height != h }) return null
        val kind = ByteArray(n)
        val px = IntArray(n)
        val hist = IntArray(256); val sumR = LongArray(256); val sumG = LongArray(256); val sumB = LongArray(256)
        for ((b, isHair) in bmps) {
            b.getPixels(px, 0, w, 0, 0, w, h)
            for (i in 0 until n) {
                val c = px[i]
                if ((c ushr 24) < SOLID_ALPHA) continue
                if (isHair) {
                    kind[i] = T_HAIR
                    val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255
                    val l = (r * 299 + g * 587 + bl * 114) / 1000
                    hist[l]++; sumR[l] += r; sumG[l] += g; sumB[l] += bl
                } else if (kind[i] == T_CLEAR) kind[i] = T_SOLID
            }
        }
        val total = hist.sum()
        if (total == 0) return null
        var acc = 0; var p = 0
        while (p < 255 && acc + hist[p] < total / 4) { acc += hist[p]; p++ }
        var cnt = 0L; var r = 0L; var g = 0L; var bl = 0L
        for (l in max(0, p - 6)..min(255, p + 6)) { cnt += hist[l]; r += sumR[l]; g += sumG[l]; bl += sumB[l] }
        if (cnt == 0L) return null
        val fill = Color.rgb((r / cnt).toInt(), (g / cnt).toInt(), (bl / cnt).toInt())

        val stack = IntArray(n); var sp = 0
        fun seed(i: Int) { if (kind[i] == T_CLEAR) { kind[i] = T_OUT; stack[sp++] = i } }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
        while (sp > 0) {
            val i = stack[--sp]; val x = i % w; val y = i / w
            if (x > 0) seed(i - 1); if (x < w - 1) seed(i + 1); if (y > 0) seed(i - w); if (y < h - 1) seed(i + w)
        }
        java.util.Arrays.fill(px, 0)
        var filled = 0
        for (start in 0 until n) {
            if (kind[start] != T_CLEAR) continue
            var touchesHair = false; var cs = 0; var head = 0
            kind[start] = T_HOLE; stack[cs++] = start
            while (head < cs) {
                val i = stack[head++]; val x = i % w; val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val xx = x + dx; val yy = y + dy
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                    val j = yy * w + xx
                    if (kind[j] == T_HAIR) touchesHair = true
                    else if (kind[j] == T_CLEAR && (dx == 0 || dy == 0)) { kind[j] = T_HOLE; stack[cs++] = j }
                }
            }
            if (touchesHair) { for (k in 0 until cs) px[stack[k]] = fill; filled += cs }
        }
        return if (filled == 0) null else Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun drawLayers(canvas: Canvas, gender: String, look: CharacterLook, src: Rect, dst: RectF) {
        val files = layers(gender, look)
        backing(files)?.let { canvas.drawBitmap(it, src, dst, paint) }
        drawFiles(canvas, files, src, dst)
    }

    private fun drawFiles(canvas: Canvas, files: List<String>, src: Rect, dst: RectF) {
        for (f in files) bitmap(f)?.let { b ->
            val wide = if (f.contains("/eyes/")) EYE_WIDTH else if (f.contains("/brows/")) BROW_WIDTH else 1f
            if (wide == 1f) canvas.drawBitmap(b, src, dst, paint) else drawHalves(canvas, b, src, dst, wide)
        }
    }

    private fun drawHalves(canvas: Canvas, b: Bitmap, src: Rect, dst: RectF, wide: Float) {
        val k = dst.width() / src.width()
        val cx = dst.left + (FACE_CX - src.left) * k
        val inset = EYE_INSET * k
        for (half in 0..1) {
            canvas.save()
            canvas.translate(if (half == 0) inset else -inset, 0f)
            canvas.scale(wide, 1f, cx, 0f)
            if (half == 0) canvas.clipRect(dst.left, dst.top, cx, dst.bottom) else canvas.clipRect(cx, dst.top, dst.right, dst.bottom)
            canvas.drawBitmap(b, src, dst, paint)
            canvas.restore()
        }
    }

    fun fit(gender: String, bounds: RectF, out: RectF): RectF {
        val s = minOf(bounds.width() / CROP.width(), bounds.height() / CROP.height())
        val w = CROP.width() * s; val h = CROP.height() * s
        out.set(bounds.centerX() - w / 2, bounds.bottom - h, bounds.centerX() + w / 2, bounds.bottom)
        return out
    }

    fun compose(gender: String, look: CharacterLook, bodyW: Int, bodyH: Int): Composed {
        val bmp = Bitmap.createBitmap(max(1, bodyW), max(1, bodyH), Bitmap.Config.ARGB_8888)
        drawLayers(Canvas(bmp), gender, look, CROP, RectF(0f, 0f, bodyW.toFloat(), bodyH.toFloat()))
        return Composed(bmp, 0, 0)
    }

    private val thumbs = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > THUMB_CACHE
    }

    fun part(gender: String, slot: String, option: String, px: Int): Bitmap? = synchronized(thumbs) {
        if (slot == "aura") return@synchronized thumbs.getOrPut("aura:$option:$px") { Aura.thumb(context, option, px) ?: return@synchronized null }
        val g = if (gender == "m") "m" else "f"
        swatchColor(slot, option)?.let { c -> return@synchronized thumbs.getOrPut("swatch:$c:$px") { swatch(c, px) } }
        val n = option.toIntOrNull() ?: return@synchronized null
        val (files, r) = when (slot) {
            "hair" -> if (n !in HAIR_M.indices) return@synchronized null else listOfNotNull(
                "$g/hair/hair_${n}_color_${TILE_HAIR_COLOR}_back.webp".takeIf { hasBack(g, n) }, "$g/hair/hair_${n}_color_${TILE_HAIR_COLOR}_front.webp") to HEAD
            "outfit" -> if (n !in OUTFIT_M.indices) return@synchronized null else listOf("$g/outfits/outfit_%02d_skin_$TILE_SKIN.webp".format(n)) to BODY
            "eyes" -> if (n !in EYES_M.indices) return@synchronized null else listOf("$g/eyes/eyes_${n}_color_0.webp") to EYE_ZOOM
            "brows" -> if (n !in BROWS.indices) return@synchronized null else listOf("common/brows/brows_${n}_color_0.webp") to BROW_ZOOM
            "mouth" -> if (n !in MOUTHS.indices) return@synchronized null else listOf("common/mouth/mouth_$n.webp") to MOUTH_ZOOM
            else -> return@synchronized null
        }
        thumbs.getOrPut("$g:$slot:$n:$px") {
            Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { b ->
                val m = px * 0.06f
                val k = min((px - 2 * m) / r.width(), (px - 2 * m) / r.height())
                val w = r.width() * k; val h = r.height() * k
                drawFiles(Canvas(b), files, r, RectF((px - w) / 2, (px - h) / 2, (px + w) / 2, (px + h) / 2))
            }
        }
    }

    private fun swatchColor(slot: String, option: String): Int? = when (slot) {
        "hairColor" -> HAIR_COLORS.firstOrNull { it.first == option }?.third
        "eyeColor" -> option.toIntOrNull()?.let { EYE_COLORS.getOrNull(it)?.second }
        "skin" -> option.toIntOrNull()?.let { SKIN_HEX.getOrNull(it) }
        else -> null
    }?.let { Color.parseColor(it) }

    private fun swatch(color: Int, px: Int): Bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b); val r = px * 0.30f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL; p.color = color
        c.drawCircle(px / 2f, px / 2f, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = max(2f, px * 0.02f)
        p.color = Color.rgb((Color.red(color) * 0.78f).toInt(), (Color.green(color) * 0.78f).toInt(), (Color.blue(color) * 0.78f).toInt())
        c.drawCircle(px / 2f, px / 2f, r, p)
    }

    companion object {
        private const val LAYER_CACHE = 12
        private const val THUMB_CACHE = 96
        private const val BACKING_CACHE = 6
        private const val T_CLEAR: Byte = 0
        private const val T_SOLID: Byte = 1
        private const val T_HAIR: Byte = 2
        private const val T_OUT: Byte = 3
        private const val T_HOLE: Byte = 4
        private const val SOLID_ALPHA = 128
        private val SKIN_SUFFIX = Regex("_skin_\\d+")
        private val SILHOUETTE_DIRS = listOf("/hair/", "/outfits/")

        val CROP = Rect(18, 14, 382, 586)
        private val HEAD = Rect(20, 14, 380, 374)
        private val BODY = Rect(60, 266, 340, 586)
        private val EYE_ZOOM = Rect(104, 136, 296, 328)
        private val BROW_ZOOM = Rect(104, 106, 296, 298)
        private val MOUTH_ZOOM = Rect(170, 243, 230, 303)
        private const val TILE_HAIR_COLOR = 1
        private const val TILE_SKIN = 1
        private const val EYE_WIDTH = 1.23f
        private const val BROW_WIDTH = 1.3f
        private const val FACE_CX = 200f
        private const val EYE_INSET = 3f
        private val HAIR_WITH_BACK = 3..5
        private val F_HAIR_WITH_BACK = setOf(6)
        private fun hasBack(g: String, h: Int) = h in HAIR_WITH_BACK || (g == "f" && h in F_HAIR_WITH_BACK)

        val HAIR_COLORS = listOf(
            Triple("black", "블랙", "#2D2928"), Triple("brown", "딥브라운", "#6A4631"), Triple("chestnut", "체스트넛", "#995A3C"),
            Triple("ash", "애쉬브라운", "#80716B"), Triple("honey", "허니블론드", "#C5A060"), Triple("silver", "실버", "#B5B7C0"),
            Triple("pink", "더스티핑크", "#B5788C"), Triple("navy", "블루블랙", "#43546B"),
        )
        val HAIR_M = listOf("스파이키", "사이드 파트", "웨이브 가르마", "소프트 프린지", "텍스처 크롭", "슬릭 백", "텍스처 투블럭", "쉐도우 펌", "숏 울프컷", "6:4 슬릭 파트")
        val HAIR_F = listOf("하이 포니테일", "웨이브 단발", "하프업 웨이브", "트윈테일", "롱 스트레이트", "픽시 단발", "히메컷", "하이 번", "비대칭 숏컷", "사이드 브레이드")
        val OUTFIT_M = listOf("스트리트", "미니멀 니트", "캠퍼스", "블랙 집업", "내추럴 가디건", "라운지 후디", "데님 캐주얼", "프레피 블레이저", "클린 셔츠", "아웃도어 파카", "시티 코트", "마린 쇼츠", "레더 봄버", "니트 폴로", "데님 트러커", "테크 윈드브레이커", "모카 가디건", "럭비 스웨터", "캠프 셔츠", "레이어드 바시티", "왕실 기사", "별빛 마법사", "중세 모험가", "세이지 토끼 잠옷", "초코 곰 잠옷", "차콜 고양이 잠옷")
        val OUTFIT_F = listOf("걸 스트리트", "내추럴 가디건", "시티 캐주얼", "데님 스커트", "프레피 니트", "라운지 후디", "캠퍼스 데님", "트렌치 룩", "모던 수트", "소프트 원피스", "브라운 데님", "마린 쇼츠", "슬림 V니트", "와인 랩 톱", "트위드 재킷", "데님 재킷·카고 스커트", "오프숄더 그래픽 티", "파우더 블라우스", "그레이 가디건", "크롭 가디건", "장미 공주", "숲의 요정", "은빛 여기사", "핑크 토끼 잠옷", "카라멜 곰 잠옷", "라일락 고양이 잠옷")
        val EYES_M = listOf("차분한 아몬드", "동그란 눈", "올라간 눈꼬리", "순한 처진 눈", "날렵한 시크", "다정한 라운드", "깔끔한 무쌍", "밝은 눈웃음", "강인한 늑대상", "여유로운 반쯤 감김", "맑은 강아지상", "깊은 후드 눈매", "집중한 눈빛", "장난스런 미소")
        val EYES_F = listOf("차분한 아몬드", "동그란 눈", "올라간 눈꼬리", "순한 처진 눈", "매혹적인 캣아이", "로맨틱 글래머", "신비로운 반달", "반짝이는 사슴눈", "도도한 여우눈", "나른한 사이렌", "샤프 윙 아이라인", "소프트 로맨틱", "시크 카리스마", "몽환 스파클")
        val BROWS = listOf("기본 아치", "차분한 직선", "올라간 눈썹", "순한 내림", "소프트 아치", "내추럴 직선", "시크한 각도", "로맨틱 곡선", "부드러운 내림", "하이 아치")
        val MOUTHS = listOf("살짝 미소", "차분한 입", "밝은 미소", "시크한 스머크", "큐피드 미소", "작은 도톰 입술", "장난스런 미소", "은은한 웃음", "편안한 미소", "혀 살짝", "혀 왼쪽", "혀 내민 웃음", "도톰한 큐피드", "도톰한 미소")
        val EYE_COLORS = listOf(
            "브라운" to "#71503A", "앰버" to "#B67C35", "헤이즐" to "#73704A", "그린" to "#477E5F", "블루" to "#4A7EA5", "그레이" to "#727993",
            "바이올렛" to "#6D4E9E", "핑크" to "#C06A8C", "레드" to "#A33F3F", "터콰이즈" to "#3B9C9C", "골드" to "#C9A02E", "블랙" to "#2A2A2E",
            "스카이블루" to "#78B2E6", "에메랄드" to "#2E9A62", "라벤더" to "#9A8AD0", "실버" to "#A4A8B2",
        )
        val SKINS = listOf("포슬린", "라이트", "웜 베이지", "탠", "브라운", "딥 브라운", "스노우", "에보니", "딥 에보니")
        val SKIN_HEX = listOf("#FFE8D8", "#F4CDAE", "#E3B28E", "#C18B63", "#955F42", "#633D2E", "#FFF3EA", "#4C2E22", "#3A231B")
        val SKIN_ORDER = listOf(6, 0, 1, 2, 3, 4, 5, 7, 8)

        @Volatile private var cached: StaticRig? = null

        fun get(context: Context): StaticRig = cached ?: synchronized(this) {
            cached ?: StaticRig(context.applicationContext).also { cached = it }
        }
    }
}
