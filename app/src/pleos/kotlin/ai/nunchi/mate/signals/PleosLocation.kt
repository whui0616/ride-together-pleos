package ai.nunchi.mate.signals

import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

object PleosLocation {
    fun create(context: Context): LocationReader = PleosLocationReader(context)
}

private class PleosLocationReader(private val context: Context) : LocationReader {
    private val main = Handler(Looper.getMainLooper())
    private var navi: NaviHelper? = null
    private var listener: NaviHelperEventListener? = null

    override fun start(onLocation: (address: String, lat: Double, lon: Double) -> Unit) {
        stop()
        val helper = NaviHelper(context.applicationContext)
        val l = object : NaviHelperEventListener {
            override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
                with(currentLocationInfo) { main.post { onLocation(address.orEmpty(), latitude, longitude) } }
            }
        }
        helper.initialize(
            onConnected = {
                helper.addListener(l)
                main.post(poll)
            },
            onFailed = { Log.w(TAG, "NaviHelper connection failed", it) },
        )
        navi = helper
        listener = l
    }

    private val poll = object : Runnable {
        override fun run() {
            navi?.getCurrentLocationInfo() ?: return
            main.postDelayed(this, POLL_MS)
        }
    }

    override fun stop() {
        main.removeCallbacks(poll)
        val helper = navi ?: return
        listener?.let { runCatching { helper.removeListener(it) } }
        runCatching { helper.release() }
        navi = null
        listener = null
    }

    private companion object {
        const val TAG = "PleosLocation"
        const val POLL_MS = 30_000L
    }
}
