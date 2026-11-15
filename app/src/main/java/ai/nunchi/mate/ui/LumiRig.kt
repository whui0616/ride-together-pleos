package ai.nunchi.mate.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

data class CharacterLook(
    val hair: String = "0",
    val hairColor: String = "black",
    val outfit: String = "0",
    val eyes: String = "0",
    val eyeColor: String = "0",
    val brows: String = "0",
    val mouth: String = "0",
    val skin: String = "1",
    val aura: String = Aura.NONE,
) {
    operator fun get(slot: String): String = when (slot) {
        "hair" -> hair
        "hairColor" -> hairColor
        "outfit" -> outfit
        "eyes" -> eyes
        "eyeColor" -> eyeColor
        "brows" -> brows
        "mouth" -> mouth
        "skin" -> skin
        "aura" -> aura
        else -> "none"
    }

    fun with(slot: String, option: String): CharacterLook {
        if (slot == LEGACY_TOP) return with("outfit", option)
        if (option !in VALID[slot].orEmpty()) return this
        return when (slot) {
            "hair" -> copy(hair = option)
            "hairColor" -> copy(hairColor = option)
            "outfit" -> copy(outfit = option)
            "eyes" -> copy(eyes = option)
            "eyeColor" -> copy(eyeColor = option)
            "brows" -> copy(brows = option)
            "mouth" -> copy(mouth = option)
            "skin" -> copy(skin = option)
            "aura" -> copy(aura = option)
            else -> this
        }
    }

    fun toMap(): Map<String, String> = KEYS.associateWith { get(it) }

    fun encode(): String = KEYS.joinToString(";") { "$it=${get(it)}" }

    companion object {
        val KEYS = listOf("hair", "hairColor", "outfit", "eyes", "eyeColor", "brows", "mouth", "skin", "aura")
        const val LEGACY_TOP = "top"
        val BASE = CharacterLook()
        private val VALID = mapOf(
            "hair" to StaticRig.HAIR_M.indices.map { "$it" },
            "hairColor" to StaticRig.HAIR_COLORS.map { it.first },
            "outfit" to StaticRig.OUTFIT_M.indices.map { "$it" },
            "eyes" to StaticRig.EYES_M.indices.map { "$it" },
            "eyeColor" to StaticRig.EYE_COLORS.indices.map { "$it" },
            "brows" to StaticRig.BROWS.indices.map { "$it" },
            "mouth" to StaticRig.MOUTHS.indices.map { "$it" },
            "skin" to StaticRig.SKINS.indices.map { "$it" },
            "aura" to Aura.AURAS.map { it.id },
        )

        fun fromMap(m: Map<String, String>): CharacterLook = (listOf(LEGACY_TOP) + KEYS).fold(BASE) { l, k -> m[k]?.let { l.with(k, it) } ?: l }

        fun decode(s: String?): CharacterLook {
            if (s.isNullOrBlank()) return BASE
            return fromMap(s.split(';').mapNotNull { kv -> val i = kv.indexOf('='); if (i > 0) kv.substring(0, i) to kv.substring(i + 1) else null }.toMap())
        }

        fun fromJson(o: JSONObject): CharacterLook {
            var look = BASE
            for (k in listOf(LEGACY_TOP) + KEYS) if (o.has(k)) look = look.with(k, o.getString(k))
            return look
        }
    }
}

class LumiRig private constructor(json: JSONObject) {

    class Bone(val name: String, val parent: Int, val x: Float, val y: Float, val rot: Float, val mirror: Boolean)

    class Part(
        val bone: Int,
        val path: Path,
        val fill: String?,
        val stroke: String?,
        val strokeWidth: Float,
        val opacity: Float,
        val slot: String?,
        val option: String?,
        val eyes: String?,
        val mouth: String?,
    )

    class Option(val id: String, val label: String)
    class HairColor(val id: String, val label: String, val main: Int, val tip: Int)
    class Preset(val id: String, val label: String, val look: CharacterLook)

    class Wave(val amp: Float, val period: Float, val phase: Float) {
        fun at(t: Float): Float = amp * sin(((t / period + phase) * 2 * PI).toFloat())
    }

    val width: Float
    val height: Float
    val bones: List<Bone>
    val parts: List<Part>
    val slots: Map<String, List<Option>>
    val hairColors: List<HairColor>
    val presets: List<Preset>
    val poses: Map<String, Map<String, Float>>
    val anims: Map<String, Map<String, Wave>>
    private val palette: Map<String, Int>

    init {
        val canvas = json.getJSONArray("canvas")
        width = canvas.getDouble(0).toFloat()
        height = canvas.getDouble(1).toFloat()

        val boneArr = json.getJSONArray("bones")
        val index = HashMap<String, Int>()
        bones = (0 until boneArr.length()).map { i ->
            val b = boneArr.getJSONObject(i)
            index[b.getString("name")] = i
            Bone(
                name = b.getString("name"),
                parent = if (b.isNull("parent")) -1 else index.getValue(b.getString("parent")),
                x = b.getDouble("x").toFloat(),
                y = b.getDouble("y").toFloat(),
                rot = b.getDouble("rot").toFloat(),
                mirror = b.optBoolean("mirror", false),
            )
        }

        val partArr = json.getJSONArray("parts")
        parts = (0 until partArr.length()).map { i ->
            val p = partArr.getJSONObject(i)
            val path = if (p.has("d")) {
                SvgPath.parse(p.getString("d"))
            } else {
                val e = p.getJSONArray("ellipse")
                val cx = e.getDouble(0).toFloat(); val cy = e.getDouble(1).toFloat()
                val rx = e.getDouble(2).toFloat(); val ry = e.getDouble(3).toFloat()
                Path().apply { addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW) }
            }
            Part(
                bone = index.getValue(p.getString("bone")),
                path = path,
                fill = p.optString("fill").ifEmpty { null },
                stroke = p.optString("stroke").ifEmpty { null },
                strokeWidth = p.optDouble("sw", 1.6).toFloat(),
                opacity = p.optDouble("opacity", 1.0).toFloat(),
                slot = p.optString("slot").ifEmpty { null },
                option = p.optString("option").ifEmpty { null },
                eyes = p.optString("eyes").ifEmpty { null },
                mouth = p.optString("mouth").ifEmpty { null },
            )
        }

        val slotObj = json.getJSONObject("slots")
        slots = slotObj.keys().asSequence().associateWith { key ->
            val arr = slotObj.getJSONArray(key)
            (0 until arr.length()).map { Option(arr.getJSONArray(it).getString(0), arr.getJSONArray(it).getString(1)) }
        }

        val hc = json.getJSONArray("hairColors")
        hairColors = (0 until hc.length()).map {
            val a = hc.getJSONArray(it)
            HairColor(a.getString(0), a.getString(1), Color.parseColor(a.getString(2)), Color.parseColor(a.getString(3)))
        }

        val pal = json.getJSONObject("palette")
        palette = pal.keys().asSequence().associateWith { Color.parseColor(pal.getString(it)) }

        val pr = json.getJSONArray("presets")
        presets = (0 until pr.length()).map {
            val o = pr.getJSONObject(it)
            Preset(o.getString("id"), o.getString("label"), CharacterLook.fromJson(o.getJSONObject("look")))
        }

        val po = json.getJSONObject("poses")
        poses = po.keys().asSequence().associateWith { name ->
            val o = po.getJSONObject(name)
            o.keys().asSequence().associateWith { o.getDouble(it).toFloat() }
        }

        val an = json.getJSONObject("anims")
        anims = an.keys().asSequence().associateWith { name ->
            val o = an.getJSONObject(name)
            o.keys().asSequence().associateWith { k ->
                val w: JSONArray = o.getJSONArray(k)
                Wave(w.getDouble(0).toFloat(), w.getDouble(1).toFloat(), w.getDouble(2).toFloat())
            }
        }
    }

    fun color(ref: String, look: CharacterLook): Int {
        if (!ref.startsWith("$")) return Color.parseColor(ref)
        val key = ref.substring(1)
        if (key == "hair" || key == "hairTip") {
            hairColors.firstOrNull { it.id == look.hairColor }?.let { return if (key == "hair") it.main else it.tip }
        }
        return palette[key] ?: Color.MAGENTA
    }

    companion object {
        @Volatile private var cached: LumiRig? = null

        fun get(context: Context): LumiRig = cached ?: synchronized(this) {
            cached ?: context.applicationContext.assets.open("character/rig.json").use { input ->
                LumiRig(JSONObject(input.readBytes().toString(Charsets.UTF_8)))
            }.also { cached = it }
        }
    }
}

class LumiRigRenderer(private val rig: LumiRig) {
    private val world = Array(rig.bones.size) { Matrix() }
    private val worldAngle = FloatArray(rig.bones.size)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun draw(canvas: Canvas, look: CharacterLook, pose: Map<String, Float>, anim: String?, mood: Mood, t: Float) {
        solve(pose, anim?.let { rig.anims[it] }, t)
        val eyes: String
        val mouth: String
        val blink = (t % 3.6f) > 3.45f
        when (mood) {
            Mood.HAPPY -> { eyes = "closed"; mouth = "open" }
            Mood.TALKING -> { eyes = if (blink) "closed" else "open"; mouth = if (floor(t * 6).toInt() % 2 == 1) "open" else "smile" }
            Mood.LISTENING -> { eyes = if (blink) "closed" else "open"; mouth = "o" }
            Mood.FOCUS -> { eyes = "open"; mouth = "flat" }
            Mood.CALM -> { eyes = if (blink) "closed" else "open"; mouth = "smile" }
            Mood.SLEEPY -> { eyes = "closed"; mouth = "flat" }
            Mood.SAD -> { eyes = "open"; mouth = "flat" }
            Mood.SURPRISED -> { eyes = "open"; mouth = "o" }
        }

        for (p in rig.parts) {
            if (p.slot != null && look[p.slot] != p.option) continue
            if (p.eyes != null && p.eyes != eyes) continue
            if (p.mouth != null && p.mouth != mouth) continue
            canvas.save()
            canvas.concat(world[p.bone])
            val alpha = (p.opacity * 255).toInt()
            p.fill?.let {
                fill.color = rig.color(it, look)
                fill.alpha = alpha
                canvas.drawPath(p.path, fill)
            }
            p.stroke?.let {
                stroke.color = rig.color(it, look)
                stroke.alpha = alpha
                stroke.strokeWidth = p.strokeWidth
                canvas.drawPath(p.path, stroke)
            }
            canvas.restore()
        }
    }

    private fun solve(pose: Map<String, Float>, anim: Map<String, LumiRig.Wave>?, t: Float) {
        val bob = anim?.get("_bobY")?.at(t) ?: 0f
        rig.bones.forEachIndexed { i, b ->
            var a = pose[b.name] ?: b.rot
            anim?.get(b.name)?.let { a += it.at(t) }
            val local = if (b.mirror) -a else a
            val m = world[i]
            if (b.parent < 0) {
                m.reset()
                worldAngle[i] = local
            } else {
                m.set(world[b.parent])
                worldAngle[i] = worldAngle[b.parent] + local
            }
            m.preTranslate(b.x, b.y + if (b.parent < 0) bob else 0f)
            m.preRotate(local)
        }
    }
}

internal object SvgPath {
    private val TOKEN = Regex("[MLQCZ]|-?\\d+(?:\\.\\d+)?")

    fun parse(d: String): Path {
        val path = Path()
        val toks = TOKEN.findAll(d).map { it.value }.toList()
        var i = 0
        var cmd = 'M'
        fun num() = toks[i++].toFloat()
        while (i < toks.size) {
            val tk = toks[i]
            if (tk.length == 1 && tk[0].isLetter()) {
                cmd = tk[0]; i++
                if (cmd == 'Z') { path.close(); continue }
            }
            when (cmd) {
                'M' -> { path.moveTo(num(), num()); cmd = 'L' }
                'L' -> path.lineTo(num(), num())
                'Q' -> path.quadTo(num(), num(), num(), num())
                'C' -> path.cubicTo(num(), num(), num(), num(), num(), num())
                else -> i++
            }
        }
        return path
    }
}
