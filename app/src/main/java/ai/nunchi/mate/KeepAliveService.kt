package ai.nunchi.mate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT) {
            Log.d(TAG, "exit from notification")
            onExit?.invoke() ?: stopSelf()
            return START_NOT_STICKY
        }
        val n = notification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
        Log.d(TAG, "foreground")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "stopped")
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "운전 중 동행", NotificationManager.IMPORTANCE_LOW))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, packageManager.getLaunchIntentForPackage(packageName), flags)
        val exit = PendingIntent.getService(this, 1, Intent(this, KeepAliveService::class.java).setAction(ACTION_EXIT), flags)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("운전 부하를 보며 곁에 있어요")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "끄기", exit).build())
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "KeepAlive"
        private const val CHANNEL_ID = "drive"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_EXIT = "ai.nunchi.mate.EXIT"

        var onExit: (() -> Unit)? = null

        fun start(context: Context, onExit: () -> Unit) {
            this.onExit = onExit
            context.startForegroundService(Intent(context, KeepAliveService::class.java))
        }

        fun stop(context: Context) {
            onExit = null
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
