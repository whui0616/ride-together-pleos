package ai.nunchi.mate.signals

import ai.nunchi.mate.control.CarControl
import ai.nunchi.mate.control.SimulatedCarControl
import android.content.Context

object PleosSignalSources {
    const val ENABLED = false

    val PERMISSIONS = emptyArray<String>()

    fun create(context: Context): List<SignalSource> = emptyList()

    fun carControl(context: Context): CarControl = SimulatedCarControl()
}
