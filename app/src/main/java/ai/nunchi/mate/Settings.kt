package ai.nunchi.mate

import ai.nunchi.engine.suggest.SuggestionKind
import ai.nunchi.mate.voice.VoiceStyle
import android.content.Context

enum class Talkativeness(val label: String, val maxPerHour: Int) {
    CHATTY("수다쟁이", 6), NORMAL("보통", 3), QUIET("조용히", 1), MUTE("주행 중 무음", 0),
}

enum class ReplyWait(val label: String, val ms: Long) { SHORT("3초", 3_000), NORMAL("5초", 5_000), LONG("8초", 8_000) }

data class Settings(
    val driverName: String = "운전자",
    val captions: Boolean = true,
    val animations: Boolean = true,
    val talkativeness: Talkativeness = Talkativeness.NORMAL,
    val suggestionsEnabled: Boolean = true,
    val disabledKinds: Set<SuggestionKind> = emptySet(),
    val quietFrom: Int = 0,
    val quietTo: Int = 0,
    val replyWait: ReplyWait = ReplyWait.NORMAL,
    val noReplyIsDecline: Boolean = false,
    val safetyVoice: Boolean = true,
    val minimalDriving: Boolean = false,
    val passengerMode: Boolean = false,
    val voiceStyle: VoiceStyle? = null,
    val voiceVolume: Int = 100,
    val keepPlaces: Boolean = true,
) {
    fun kindEnabled(k: SuggestionKind) = k !in disabledKinds

    fun isQuietHour(hour: Int): Boolean = when {
        quietFrom == quietTo -> false
        quietFrom < quietTo -> hour in quietFrom until quietTo
        else -> hour >= quietFrom || hour < quietTo
    }

    fun canSuggest(hour: Int): Boolean =
        suggestionsEnabled && !passengerMode && talkativeness != Talkativeness.MUTE && !isQuietHour(hour)
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load() = Settings(
        driverName = prefs.getString("driverName", "운전자")!!,
        captions = prefs.getBoolean("captions", true),
        animations = prefs.getBoolean("animations", true),
        talkativeness = enum(prefs.getString("talk", null), Talkativeness.NORMAL),
        suggestionsEnabled = prefs.getBoolean("suggestions", true),
        disabledKinds = prefs.getStringSet("disabledKinds", emptySet())!!.mapNotNull { runCatching { SuggestionKind.valueOf(it) }.getOrNull() }.toSet(),
        quietFrom = prefs.getInt("quietFrom", 0),
        quietTo = prefs.getInt("quietTo", 0),
        replyWait = enum(prefs.getString("replyWait", null), ReplyWait.NORMAL),
        noReplyIsDecline = prefs.getBoolean("noReplyIsDecline", false),
        safetyVoice = prefs.getBoolean("safetyVoice", true),
        minimalDriving = prefs.getBoolean("minimalDriving", false),
        passengerMode = prefs.getBoolean("passengerMode", false),
        voiceStyle = prefs.getString("voiceStyle", null)?.let { runCatching { VoiceStyle.valueOf(it) }.getOrNull() },
        voiceVolume = prefs.getInt("voiceVolume", 100).coerceIn(0, 100),
        keepPlaces = prefs.getBoolean("keepPlaces", true),
    )

    fun save(s: Settings) = prefs.edit()
        .putString("driverName", s.driverName)
        .putBoolean("captions", s.captions)
        .putBoolean("animations", s.animations)
        .putString("talk", s.talkativeness.name)
        .putBoolean("suggestions", s.suggestionsEnabled)
        .putStringSet("disabledKinds", s.disabledKinds.map { it.name }.toSet())
        .putInt("quietFrom", s.quietFrom)
        .putInt("quietTo", s.quietTo)
        .putString("replyWait", s.replyWait.name)
        .putBoolean("noReplyIsDecline", s.noReplyIsDecline)
        .putBoolean("safetyVoice", s.safetyVoice)
        .putBoolean("minimalDriving", s.minimalDriving)
        .putBoolean("passengerMode", s.passengerMode)
        .putString("voiceStyle", s.voiceStyle?.name)
        .putInt("voiceVolume", s.voiceVolume)
        .putBoolean("keepPlaces", s.keepPlaces)
        .apply()

    private inline fun <reified T : Enum<T>> enum(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    companion object {
        fun wipeAll(context: Context) {
            for (name in listOf("settings", "avatar", "companion")) context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }

        fun exportJson(context: Context): String {
            val sb = StringBuilder("{\n")
            listOf("settings", "avatar", "companion").forEachIndexed { i, name ->
                if (i > 0) sb.append(",\n")
                sb.append("  \"").append(name).append("\": {")
                val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
                sb.append(all.entries.joinToString(", ") { (k, v) -> "\"$k\": ${jsonValue(v)}" })
                sb.append("}")
            }
            return sb.append("\n}\n").toString()
        }

        private fun jsonValue(v: Any?): String = when (v) {
            null -> "null"
            is Number, is Boolean -> v.toString()
            is Set<*> -> v.joinToString(", ", "[", "]") { "\"$it\"" }
            else -> "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        }
    }
}
