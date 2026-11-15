package ai.nunchi.mate.ui

import ai.nunchi.engine.LoadZone
import ai.nunchi.engine.Persona
import ai.nunchi.mate.R
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView

val Persona.color: Int
    get() = when (this) {
        Persona.BUDDY -> Color.parseColor("#3D7BFF")
        Persona.EASYGOING -> Color.parseColor("#22B07D")
        Persona.ADVENTURER -> Color.parseColor("#FF8A3D")
    }

private fun pill(color: Int) = GradientDrawable().apply {
    cornerRadius = 999f
    setColor(color)
}

class DrivingScreen(activity: Activity) {
    val root: View = activity.findViewById(R.id.drivingView)
    val companion: CompanionView = activity.findViewById(R.id.drivingCompanion)
    private val line: TextView = activity.findViewById<TextView>(R.id.line).apply { breakByPhrase() }
    private val zoneChip: TextView = activity.findViewById(R.id.zoneChip)
    private val pendingChip: TextView = activity.findViewById(R.id.pendingChip)

    var captions = true
    var minimal = false

    fun setLine(text: String) {
        line.text = if (captions && !minimal) text else ""
    }

    fun render(zone: LoadZone, pending: Int, listening: Boolean) {
        val (label, color) = when {
            listening -> "🎤 듣고 있어요" to Color.parseColor("#3D7BFF")
            zone == LoadZone.RED -> "집중 모드 · 조용히" to Color.parseColor("#E5484D")
            zone == LoadZone.YELLOW -> "주변 살피는 중" to Color.parseColor("#E39A0B")
            else -> "여유 구간" to Color.parseColor("#22B07D")
        }
        val show = if (minimal) View.GONE else View.VISIBLE
        zoneChip.visibility = show
        zoneChip.text = label
        zoneChip.background = pill(color)
        pendingChip.visibility = if (pending > 0 && !minimal) View.VISIBLE else View.GONE
        pendingChip.text = "보관 중인 이야기 $pending"
        pendingChip.background = pill(Color.parseColor("#E3E6EB"))
    }
}
