package ai.nunchi.mate.signals

interface LocationReader {
    fun start(onLocation: (address: String, lat: Double, lon: Double) -> Unit)
    fun stop()
}
