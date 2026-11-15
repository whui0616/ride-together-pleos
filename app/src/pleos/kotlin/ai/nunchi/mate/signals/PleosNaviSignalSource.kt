package ai.nunchi.mate.signals

import ai.nunchi.engine.SignalUpdate
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

class PleosNaviSignalSource(private val context: Context) : SignalSource, NaviControl {
    override val name = "Pleos NaviHelper (TBT·카메라·주의구간·경로)"
    override var onEvent: ((String) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var navi: NaviHelper? = null
    private var listener: NaviHelperEventListener? = null
    private var lastTbt: String? = null

    @Volatile private var guiding: Boolean? = null

    override fun start(sink: (SignalUpdate) -> Unit) {
        stop()
        val helper = NaviHelper(context.applicationContext)
        val l = object : NaviHelperEventListener {
            override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
                val first = tbtInfoList.firstOrNull()?.takeIf { guiding != false }
                val desc = first?.let { "${it.type} ${it.distance.toInt()}m" }
                if (desc != lastTbt) Log.d(TAG, "TBT ${tbtInfoList.size}건(안내 중=$guiding), 첫 항목 $desc")
                lastTbt = desc
                sink(SignalUpdate.NextTurn(first?.distance?.toInt()))
            }

            override fun onNavigationStatus(navigationStatus: NavigationStatus) {
                Log.d(TAG, "navigation status = $navigationStatus")
                when (navigationStatus) {
                    NavigationStatus.ROUTE_DRIVE -> setGuiding(true)
                    NavigationStatus.FREE_DRIVE, NavigationStatus.INACTIVE -> setGuiding(false)
                    else -> Unit
                }
            }

            override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                Log.d(TAG, "destination: ${destinationInfo.name} 남은 ${destinationInfo.remainDistance}m")
                setGuiding(destinationInfo.remainDistance > 0)
            }

            override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
                Log.d(TAG, "route state: isRouting=${routeStateInfo.isRouting}")
            }

            override fun onCameraAlert(cameraAlertList: List<CameraAlert>) {
                Log.d(TAG, "camera " + cameraAlertList.joinToString { "${it.type} status=${it.status} ${it.remainDistance}m limit=${it.speedLimit}" })
                sink(SignalUpdate.CameraAlert(cameraAlertList.any { it.status && it.remainDistance in 0..CAMERA_NEAR_M }))
            }

            override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) {
                Log.d(TAG, "caution " + cautionPointList.joinToString { "${it.type} ${it.remainDistance}m" })
                sink(SignalUpdate.CautionZone(cautionPointList.any { it.remainDistance in 0..CAUTION_NEAR_M }))
            }

            override fun onRouteEnded() {
                Log.d(TAG, "route ended (안내 종료가 아니라 탐색 종료로 보임)")
            }

            override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
                setGuiding(true)
                val km = routeStartInfo.distance / 1000f
                emit("경로 안내 시작: ${routeStartInfo.name} (%.1fkm, 약 %d분)".format(km, routeStartInfo.duration / 60))
            }

            override fun onRoutingCompleted() = emit("경로 탐색 완료")

            override fun onRouteCancelled() {
                setGuiding(false)
                emit("경로 안내 취소")
            }

            override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
                setGuiding(false)
                emit("목적지 도착")
            }

            override fun onError(errorCode: NaviErrorCode) = emit("내비 오류: $errorCode")

            private fun setGuiding(active: Boolean) {
                guiding = active
                if (!active) sink(SignalUpdate.NextTurn(null))
            }
        }
        navi = helper
        listener = l
        helper.initialize(
            onConnected = {
                if (navi === helper) {
                    helper.addListener(l)
                    helper.getDestinationInfo()
                    main.post(pollTbt)
                    emit("내비 연결됨")
                }
            },
            onFailed = { Log.w(TAG, "NaviHelper connection failed", it) },
        )
    }

    override fun requestRoute(destination: Destination) {
        val helper = navi ?: return emit("내비가 아직 연결되지 않았어요")
        helper.requestRoute(
            RouteInfo(
                latitude = destination.latitude,
                longitude = destination.longitude,
                displayLatitude = null,
                displayLongitude = null,
                poiName = destination.name,
                poiId = destination.poiId,
                poiSubId = "0",
                address = destination.address,
            ),
        )
        emit("${destination.name}(으)로 경로 요청")
    }

    override fun cancelRoute() {
        navi?.cancelRoute()
    }

    private val pollTbt = object : Runnable {
        override fun run() {
            navi?.getTBTInfo() ?: return
            main.postDelayed(this, TBT_POLL_MS)
        }
    }

    private fun emit(text: String) {
        Log.d(TAG, text)
        main.post { onEvent?.invoke(text) }
    }

    override fun stop() {
        main.removeCallbacks(pollTbt)
        val helper = navi ?: return
        listener?.let { helper.removeListener(it) }
        helper.release()
        navi = null
        listener = null
        guiding = null
        lastTbt = null
    }

    private companion object {
        const val TAG = "PleosNavi"
        const val TBT_POLL_MS = 1_000L
        const val CAMERA_NEAR_M = 500
        const val CAUTION_NEAR_M = 300
    }
}
