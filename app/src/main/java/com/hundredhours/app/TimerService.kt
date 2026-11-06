package com.hundredhours.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps an ongoing notification while the timer runs. The countdown itself is computed from
 * timestamps, so it stays correct even if this service is killed; the notification uses the
 * system chronometer, so it ticks without us waking up every second.
 */
class TimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { refresh() }
    private val scope = MainScope()
    private var inForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        StudyStore.init(this)
        // Pause / undo / adjust from the app changes the state: follow it.
        scope.launch { StudyStore.state.collect { if (inForeground) refresh() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        StudyStore.init(this)
        if (intent?.action == ACTION_PAUSE) {
            StudyStore.update { it.pause(System.currentTimeMillis()) }
        }
        val state = StudyStore.state.value
        val now = System.currentTimeMillis()
        if (!state.isRunning || state.isWeekOver(now)) {
            StudyStore.update { it.settle(now) }
            // startForeground must still be called once after startForegroundService().
            goForeground(buildNotification(state, now))
            stop()
            return START_NOT_STICKY
        }
        goForeground(buildNotification(state, now))
        scheduleNext(state, now)
        return START_STICKY
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        StudyStore.update { it.settle(now) }
        val state = StudyStore.state.value
        if (!state.isRunning) {
            stop(); return
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state, now))
        scheduleNext(state, now)
    }

    /** Wake up again when the goal is reached (switch to count-up) or the week ends (stop). */
    private fun scheduleNext(state: StudyState, now: Long) {
        handler.removeCallbacks(refresh)
        val remaining = state.remainingMs(now)
        val nextEvent = listOfNotNull(
            if (remaining > 0) now + remaining else null,
            state.weekEnd,
        ).minOrNull() ?: return
        handler.postDelayed(refresh, (nextEvent - now).coerceAtLeast(0) + 500)
    }

    private fun goForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        inForeground = true
    }

    private fun stop() {
        handler.removeCallbacks(refresh)
        inForeground = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(state: StudyState, now: Long): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pause = PendingIntent.getService(
            this, 1, Intent(this, TimerService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val remaining = state.remainingMs(now)
        val goalH = state.goalMs / HOUR_MS
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .addAction(0, getString(R.string.pause), pause)
        if (remaining > 0) {
            builder.setContentTitle(getString(R.string.notif_studying))
                .setContentText(getString(R.string.notif_left_of_goal, goalH))
                .setWhen(now + remaining)
                .setChronometerCountDown(true)
        } else {
            builder.setContentTitle(getString(R.string.notif_goal_reached))
                .setContentText(getString(R.string.notif_extra))
                .setWhen(now - state.extraMs(now))
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "timer"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_PAUSE = "com.hundredhours.app.PAUSE"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
        }
    }
}
