package cz.darken.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.content.getSystemService
import cz.darken.app.MainActivityHolder
import cz.darken.app.R
import cz.darken.app.data.PreferencesRepository
import cz.darken.app.model.OverlayTint
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private lateinit var preferences: PreferencesRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settingsReady = CompletableDeferred<Unit>()
    private var settings = PreferencesRepository.OverlaySettings()
    private data class Command(val intent: Intent?, val startId: Int)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var currentDimLevel: Int = PreferencesRepository.FALLBACK_DEFAULT
    private var pressedCellId: Int? = null
    private var highlightPhase = OverlayNotificationFactory.HighlightPhase.NONE
    private val highlightHandler = Handler(Looper.getMainLooper())
    private var highlightFadeRunnable: Runnable? = null
    private var highlightClearRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createChannelsIfNeeded()
        preferences = PreferencesRepository(applicationContext)
        activeInstance = this
        serviceScope.launch {
            try {
                preferences.overlaySettings.collect { updated ->
                    settings = updated
                    settingsReady.complete(Unit)
                    if (overlayView != null) {
                        applyOverlayAppearance()
                        refreshNotification()
                    }
                }
            } catch (_: IOException) {
                tearDown()
            }
        }
        serviceScope.launch {
            settingsReady.await()
            for (command in commands) handleCommand(command.intent, command.startId)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP || action == ACTION_EXIT_APP) {
            // Shutdown must not wait for disk reads or allow queued startup to revive the overlay.
            while (commands.tryReceive().isSuccess) { /* Discard earlier pending commands. */ }
            handleCommand(intent, startId)
            return START_NOT_STICKY
        }
        if (action == null) {
            // Enter foreground before waiting for asynchronous preferences initialization.
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        commands.trySend(Command(intent, startId))
        return START_STICKY
    }

    private fun handleCommand(intent: Intent?, startId: Int) {
        when (intent?.action) {
            ACTION_STOP -> {
                tearDown(startId)
                return
            }
            ACTION_EXIT_APP -> {
                tearDown(startId)
                MainActivityHolder.requestExitApp()
                return
            }
            ACTION_UPDATE -> {
                val level = intent.getIntExtra(EXTRA_DIM_LEVEL, settings.defaultDimLevel)
                if (overlayView == null) {
                    // A delayed slider or notification command must not restart dimming.
                    stopSelf(startId)
                } else {
                    setDimLevel(level)
                }
                return
            }
            ACTION_ADJUST -> {
                if (overlayView == null) {
                    stopSelf(startId)
                    return
                }
                val delta = intent.getIntExtra(EXTRA_DELTA, 0)
                flashPressedCell(intent.getIntExtra(EXTRA_PRESSED_CELL, View.NO_ID))
                setDimLevel(currentDimLevel + delta)
                return
            }
            else -> {
                val level = intent?.getIntExtra(EXTRA_DIM_LEVEL, settings.defaultDimLevel)
                    ?: settings.defaultDimLevel
                ensureOverlay(level)
                if (overlayView != null) startForeground(NOTIFICATION_ID, buildNotification())
                return
            }
        }
    }

    override fun onDestroy() {
        commands.close()
        serviceScope.cancel()
        cancelHighlightAnimation()
        if (overlayView != null || OverlayStateStore.state.value.isRunning) {
            removeOverlay()
            notifyOverlayStopped()
        }
        activeInstance = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        tearDown()
        super.onTaskRemoved(rootIntent)
    }

    private fun tearDown(startId: Int? = null) {
        cancelHighlightAnimation()
        pressedCellId = null
        highlightPhase = OverlayNotificationFactory.HighlightPhase.NONE
        if (startId == null) serviceScope.cancel()
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (startId == null) stopSelf() else stopSelf(startId)
        notifyOverlayStopped()
    }

    private fun notifyOverlayStopped() {
        OverlayStateStore.stopped()
        OverlayUiSync.notifyOverlayStateChanged(this)
    }

    private fun ensureOverlay(dimLevel: Int) {
        if (overlayView == null) {
            showOverlay(dimLevel)
        } else {
            setDimLevel(dimLevel)
        }
    }

    private fun setDimLevel(dimLevel: Int) {
        currentDimLevel = dimLevel.coerceIn(PreferencesRepository.MIN_DIM, PreferencesRepository.MAX_DIM)
        OverlayStateStore.active(currentDimLevel)
        applyOverlayAppearance()
        refreshNotification()
    }

    private fun showOverlay(dimLevel: Int) {
        currentDimLevel = dimLevel.coerceIn(PreferencesRepository.MIN_DIM, PreferencesRepository.MAX_DIM)

        val wm = getSystemService<WindowManager>() ?: run {
            tearDown()
            return
        }
        windowManager = wm

        val view = View(this).apply {
            setBackgroundColor(settings.tintArgb)
            alpha = if (previewSuspended) 0f else OverlayTint.alphaForDimLevel(currentDimLevel)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        try {
            wm.addView(view, params)
        } catch (_: SecurityException) {
            tearDown()
            return
        } catch (_: WindowManager.BadTokenException) {
            tearDown()
            return
        }
        overlayView = view
        OverlayStateStore.active(currentDimLevel)
        OverlayUiSync.notifyOverlayStateChanged(this)
    }

    private fun applyOverlayAppearance() {
        val argb = settings.tintArgb
        overlayView?.setBackgroundColor(argb)
        overlayView?.alpha = if (previewSuspended) 0f else OverlayTint.alphaForDimLevel(currentDimLevel)
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            windowManager?.removeView(view)
        }
        overlayView = null
        windowManager = null
    }

    private fun refreshNotification() {
        if (overlayView == null) return
        val manager = getSystemService<NotificationManager>() ?: return
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun flashPressedCell(cellId: Int) {
        if (cellId == View.NO_ID) return
        cancelHighlightAnimation()
        pressedCellId = cellId
        highlightPhase = OverlayNotificationFactory.HighlightPhase.PRESSED

        val fade = Runnable {
            highlightPhase = OverlayNotificationFactory.HighlightPhase.FADING
            refreshNotification()
            highlightClearRunnable = Runnable { clearHighlight() }
            highlightHandler.postDelayed(highlightClearRunnable!!, HIGHLIGHT_FADE_MS)
        }
        highlightFadeRunnable = fade
        highlightHandler.postDelayed(fade, HIGHLIGHT_PRESS_MS)
    }

    private fun clearHighlight() {
        pressedCellId = null
        highlightPhase = OverlayNotificationFactory.HighlightPhase.NONE
        highlightFadeRunnable = null
        highlightClearRunnable = null
        refreshNotification()
    }

    private fun cancelHighlightAnimation() {
        highlightFadeRunnable?.let { highlightHandler.removeCallbacks(it) }
        highlightClearRunnable?.let { highlightHandler.removeCallbacks(it) }
        highlightFadeRunnable = null
        highlightClearRunnable = null
    }

    private fun buildNotification(): Notification {
        val mode = settings.notificationMode
        val defaultLevel = settings.defaultDimLevel
        return OverlayNotificationFactory
            .build(
                this,
                mode,
                currentDimLevel,
                defaultLevel,
                pressedCellId,
                highlightPhase,
            )
            .build()
    }

    private fun createChannelsIfNeeded() {
        val manager = getSystemService<NotificationManager>() ?: return

        val minimal = NotificationChannel(
            CHANNEL_MINIMAL,
            getString(R.string.notification_channel_minimal_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.notification_channel_minimal_description)
            setShowBadge(false)
        }

        val interactive = NotificationChannel(
            CHANNEL_INTERACTIVE,
            getString(R.string.notification_channel_interactive_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_interactive_description)
            setShowBadge(false)
        }

        manager.createNotificationChannel(minimal)
        manager.createNotificationChannel(interactive)
    }

    companion object {
        private var activeInstance: OverlayService? = null
        private var previewSuspended = false

        /** Called on the main thread by the activity-owned color preview. */
        fun setColorPreviewVisible(visible: Boolean) {
            previewSuspended = visible
            activeInstance?.applyOverlayAppearance()
        }

        const val CHANNEL_MINIMAL = "darken_overlay_minimal"
        const val CHANNEL_INTERACTIVE = "darken_overlay_interactive"
        private const val NOTIFICATION_ID = 1

        const val ACTION_STOP = "cz.darken.app.overlay.STOP"
        const val ACTION_UPDATE = "cz.darken.app.overlay.UPDATE"
        const val ACTION_ADJUST = "cz.darken.app.overlay.ADJUST"
        const val ACTION_EXIT_APP = "cz.darken.app.overlay.EXIT_APP"
        const val EXTRA_DIM_LEVEL = "dim_level"
        const val EXTRA_DELTA = "delta"
        const val EXTRA_PRESSED_CELL = "pressed_cell"

        private const val HIGHLIGHT_PRESS_MS = 90L
        private const val HIGHLIGHT_FADE_MS = 120L

        fun start(context: Context, dimLevel: Int?) {
            val intent = Intent(context, OverlayService::class.java).apply {
                if (dimLevel != null) putExtra(EXTRA_DIM_LEVEL, dimLevel)
            }
            context.startForegroundService(intent)
        }

        fun update(context: Context, dimLevel: Int) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_DIM_LEVEL, dimLevel)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
