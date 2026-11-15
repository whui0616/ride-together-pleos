package ai.nunchi.mate.signals

data class Destination(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val poiId: String,
    val address: String,
)

interface NaviControl {
    var onEvent: ((String) -> Unit)?
    fun requestRoute(destination: Destination)
    fun cancelRoute()
}
