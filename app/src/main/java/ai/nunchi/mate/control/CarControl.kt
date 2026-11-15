package ai.nunchi.mate.control

import ai.nunchi.engine.control.CarAction
import ai.nunchi.engine.control.CarCommand
import ai.nunchi.engine.control.CarResult

interface CarControl {
    val name: String

    fun execute(command: CarCommand, onResult: (CarResult) -> Unit)

    fun release() = Unit
}

class SimulatedCarControl : CarControl {
    override val name = "차량 조작(시뮬레이션)"
    private var celsius = 22

    override fun execute(command: CarCommand, onResult: (CarResult) -> Unit) {
        val step = command.degrees ?: CarCommand.STEP
        val changed = when (command.action) {
            CarAction.WARMER -> celsius + step
            CarAction.COOLER -> celsius - step
            CarAction.SET_TEMPERATURE -> command.degrees ?: celsius
            else -> null
        }?.coerceIn(CarCommand.MIN_CELSIUS, CarCommand.MAX_CELSIUS)
        changed?.let { celsius = it }
        onResult(CarResult(ok = true, celsius = changed, detail = "시뮬레이션"))
    }
}
