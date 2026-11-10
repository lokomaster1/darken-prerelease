package cz.darken.app.timer

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Explicit private alarm receiver; it never starts an overlay or opens an activity. */
class ShutdownAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trigger = intent.getLongExtra(OverlayTimerScheduler.EXTRA_TRIGGER, 0)
        if (trigger <= 0) return
        reconcileInBackground(context, expectedTrigger = trigger, firedAction = TimerAction.STOP)
    }
}

class StartupAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trigger = intent.getLongExtra(OverlayTimerScheduler.EXTRA_TRIGGER, 0)
        if (trigger <= 0) return
        reconcileInBackground(context, expectedTrigger = trigger, firedAction = TimerAction.START)
    }
}

class ShutdownRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> reconcileInBackground(context, skipMissedStartup = true)
            Intent.ACTION_TIME_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> reconcileInBackground(context)
            Intent.ACTION_TIMEZONE_CHANGED -> reconcileInBackground(context, rebase = true)
        }
    }
}

private fun BroadcastReceiver.reconcileInBackground(
    context: Context,
    rebase: Boolean = false,
    expectedTrigger: Long? = null,
    firedAction: TimerAction? = null,
    skipMissedStartup: Boolean = false,
) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
        try {
            OverlayTimerScheduler.reconcile(context.applicationContext, rebase, expectedTrigger, firedAction, skipMissedStartup)
        } catch (error: IOException) {
            Log.e("OverlayTimer", "Unable to restore overlay schedules", error)
        } finally {
            pending.finish()
        }
    }
}
