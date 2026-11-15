package ai.nunchi.mate

import ai.nunchi.engine.situation.SituationMemory
import android.content.Context

class SituationStore(context: Context) : SituationMemory {
    private val prefs = context.getSharedPreferences("situations", Context.MODE_PRIVATE)

    override fun get(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null

    override fun put(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }
}
