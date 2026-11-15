package ai.nunchi.mate.brain

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File

class ModelFile private constructor(private val context: Context, private val dir: String, val name: String, private val chunks: List<String>) {
    private val target = File(File(context.filesDir, dir), name)

    @Synchronized
    fun file(): File {
        val total = chunks.sumOf { c -> context.assets.openFd("$dir/$c").use { it.length } }
        if (target.length() == total) return target
        val started = SystemClock.elapsedRealtime()
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        tmp.outputStream().buffered(1 shl 20).use { out ->
            chunks.forEach { c -> context.assets.open("$dir/$c").use { it.copyTo(out, 1 shl 20) } }
        }
        check(tmp.length() == total) { "model size ${tmp.length()} != $total" }
        target.delete()
        check(tmp.renameTo(target)) { "rename failed" }
        Log.i(TAG, "assembled $name ${total / 1_000_000}MB from ${chunks.size} chunks in ${SystemClock.elapsedRealtime() - started}ms")
        return target
    }

    companion object {
        private const val TAG = "ModelFile"
        private val CHUNK = Regex("(.+)\\.(\\d+)\\.bin")

        fun from(context: Context, dir: String): ModelFile? {
            val names = context.assets.list(dir).orEmpty().mapNotNull { CHUNK.matchEntire(it) }
            val model = names.firstOrNull()?.groupValues?.get(1) ?: return null
            val chunks = names.filter { it.groupValues[1] == model }.sortedBy { it.groupValues[2].toInt() }.map { it.value }
            return ModelFile(context.applicationContext, dir, model, chunks)
        }
    }
}
