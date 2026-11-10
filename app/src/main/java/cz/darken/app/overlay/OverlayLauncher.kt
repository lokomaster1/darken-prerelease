package cz.darken.app.overlay

import android.content.Context
import android.content.Intent
import cz.darken.app.MainActivity
import cz.darken.app.util.PermissionIntents

object OverlayLauncher {

    enum class ToggleResult {
        Started,
        Stopped,
        NeedPermissions,
    }

    fun isOverlayRunning(): Boolean = OverlayStateStore.state.value.isRunning

    fun isOverlayActive(): Boolean = OverlayStateStore.state.value.dimLevel != null

    fun canStart(context: Context): Boolean =
        PermissionIntents.canDrawOverlays(context)

    fun start(context: Context, dimLevel: Int? = null) {
        OverlayStateStore.starting()
        try {
            OverlayService.start(context, dimLevel)
        } catch (error: RuntimeException) {
            OverlayStateStore.stopped()
            throw error
        }
    }

    fun stop(context: Context) {
        OverlayService.stop(context)
    }

    fun stopForScheduledShutdown(context: Context) {
        OverlayService.stopForScheduledShutdown(context)
    }

    fun toggle(context: Context): ToggleResult {
        if (isOverlayRunning()) {
            stop(context)
            return ToggleResult.Stopped
        }
        if (!canStart(context)) {
            openMainActivityForPermissions(context)
            return ToggleResult.NeedPermissions
        }
        start(context)
        return ToggleResult.Started
    }

    fun openMainActivityForPermissions(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_PERMISSIONS, true)
        }
        context.startActivity(intent)
    }
}
