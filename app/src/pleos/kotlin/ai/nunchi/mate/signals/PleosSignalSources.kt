package ai.nunchi.mate.signals

import ai.nunchi.mate.control.CarControl
import android.content.Context

object PleosSignalSources {
    const val ENABLED = true

    val PERMISSIONS = arrayOf(
        "pleos.car.permission.NAVI_ROUTE",
        "pleos.car.permission.NAVI_ROUTE_SEARCH",
        "pleos.car.permission.TTS_SERVICE",
        "pleos.car.permission.STT_SERVICE",
        "pleos.car.permission.CAR_LIGHTS",
        "pleos.car.permission.CAR_WINDOWS",
        "pleos.car.permission.CAR_DRIVE",
        "pleos.car.permission.CAR_CLIMATE",
        "pleos.car.permission.CAR_SEATS",
        "pleos.car.permission.CAR_DOORS",
        "pleos.car.permission.CONTROL_CAR_CLIMATE",
        "pleos.car.permission.CONTROL_CAR_SEATS",
        "pleos.car.permission.CONTROL_CAR_WINDOWS",
        "pleos.car.permission.CONTROL_CAR_LIGHTS",
        "pleos.car.permission.CONTROL_CAR_DOORS",
    )

    fun create(context: Context): List<SignalSource> =
        listOf(PleosNaviSignalSource(context), PleosVehicleSignalSource(context))

    fun carControl(context: Context): CarControl = PleosCarControl(context)
}
