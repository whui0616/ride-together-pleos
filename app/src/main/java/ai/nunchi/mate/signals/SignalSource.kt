package ai.nunchi.mate.signals

import ai.nunchi.engine.SignalUpdate

interface SignalSource {
    val name: String
    fun start(sink: (SignalUpdate) -> Unit)
    fun stop()
}
