package cz.darken.app.timer

import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import cz.darken.app.data.PreferencesRepository
import cz.darken.app.overlay.OverlayLauncher
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class TimerAction { STOP, START }

/** One outstanding alarm per action. Decisions and overlay transitions run on the main thread. */
object OverlayTimerScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    const val EXTRA_TRIGGER = "trigger_at"

    fun canSchedule(context: Context): Boolean =
        context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true

    suspend fun update(
        context: Context,
        action: TimerAction = TimerAction.STOP,
        transform: (OverlayTimer) -> OverlayTimer,
    ) = scope.async {
        mutex.withLock {
            val repository = PreferencesRepository(context.applicationContext)
            val previous = when (action) {
                TimerAction.STOP -> repository.shutdownTimer.first()
                TimerAction.START -> repository.startupTimer.first()
            }
            val changed = transform(previous).copy(nextTriggerAt = 0, lastFiredDate = "")
            val timer = if (changed.enabled && canSchedule(context)) {
                changed.copy(nextTriggerAt = changed.nextOccurrence(Instant.now(), ZoneId.systemDefault()))
            } else changed
            when (action) {
                TimerAction.STOP -> repository.setShutdownTimer(timer)
                TimerAction.START -> repository.setStartupTimer(timer)
            }
            reconcileLocked(context, repository)
        }
    }.await()

    /** Late starts are skipped; an overdue shutdown can still remove an existing overlay. */
    suspend fun reconcile(
        context: Context,
        rebase: Boolean = false,
        expectedTrigger: Long? = null,
        firedAction: TimerAction? = null,
        skipMissedStartup: Boolean = false,
    ): Boolean = scope.async {
        mutex.withLock {
            reconcileLocked(context, PreferencesRepository(context.applicationContext), rebase,
                expectedTrigger, firedAction, skipMissedStartup)
        }
    }.await()

    private suspend fun reconcileLocked(
        context: Context,
        repository: PreferencesRepository,
        rebase: Boolean = false,
        expectedTrigger: Long? = null,
        firedAction: TimerAction? = null,
        skipMissedStartup: Boolean = false,
    ): Boolean {
        var stop = repository.shutdownTimer.first()
        var start = repository.startupTimer.first()
        if (expectedTrigger != null) {
            val expected = if (firedAction == TimerAction.START) start else stop
            if (expected.nextTriggerAt != expectedTrigger) return false
        }
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val access = canSchedule(context)
        val stopAt = stop.nextTriggerAt
        val stopDue = access && stop.isDue(now) && !rebase
        var started = false
        if (stopDue) {
            OverlayLauncher.stopForScheduledShutdown(context)
            stop = stop.afterFiring(now, zone)
        }
        if (!stop.enabled || !access) stop = stop.copy(nextTriggerAt = 0)
        else stop = stop.copy(nextTriggerAt = stop.nextOccurrence(now, zone))

        val startAllowed = access && OverlayLauncher.canStart(context) && !start.conflictsWith(stop)
        if (start.enabled && startAllowed && start.isDue(now) && !rebase) {
            val timely = start.acceptsStartupAt(now)
            if (firedAction == TimerAction.START && timely && (!stopDue || start.nextTriggerAt > stopAt)) {
                started = startOverlay(context)
            }
            // Leave a freshly due alarm for its receiver: a simultaneous activity resume must
            // not consume it first. Boot/update restoration explicitly skips missed starts.
            if (firedAction == TimerAction.START || skipMissedStartup || !timely) {
                start = start.afterFiring(now, zone)
            }
        }
        start = when {
            !start.enabled || !startAllowed -> start.copy(nextTriggerAt = 0)
            !rebase && start.isDue(now) -> start
            else -> start.copy(nextTriggerAt = start.nextOccurrence(now, zone))
        }
        repository.setShutdownTimer(stop)
        repository.setStartupTimer(start)
        schedule(context, stop, TimerAction.STOP)
        schedule(context, start, TimerAction.START)
        return stopDue && !started
    }

    private fun startOverlay(context: Context): Boolean {
        if (OverlayLauncher.isOverlayRunning()) return true
        try {
            OverlayLauncher.start(context)
            return true
        } catch (error: ForegroundServiceStartNotAllowedException) {
            Log.w("OverlayTimer", "Scheduled overlay startup was restricted", error)
        } catch (error: SecurityException) {
            Log.w("OverlayTimer", "Scheduled overlay startup permission was revoked", error)
        }
        return false
    }

    private fun schedule(context: Context, timer: OverlayTimer, action: TimerAction) {
        val manager = context.getSystemService<AlarmManager>() ?: return
        val receiver = if (action == TimerAction.STOP) ShutdownAlarmReceiver::class.java else StartupAlarmReceiver::class.java
        val intent = Intent(context, receiver).apply {
            // Keep the original shutdown PendingIntent identity across upgrades.
            this.action = if (action == TimerAction.STOP) "cz.darken.app.timer.SHUTDOWN" else "cz.darken.app.timer.STARTUP"
            putExtra(EXTRA_TRIGGER, timer.nextTriggerAt)
        }
        val operation = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!timer.enabled || timer.nextTriggerAt == 0L || !canSchedule(context)) {
            manager.cancel(operation)
            return
        }
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.nextTriggerAt, operation)
        } catch (_: SecurityException) {
            manager.cancel(operation)
        }
    }
}
