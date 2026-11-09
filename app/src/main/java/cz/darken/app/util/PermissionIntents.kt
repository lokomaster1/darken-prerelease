package cz.darken.app.util

import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import androidx.core.content.getSystemService
import androidx.core.app.NotificationManagerCompat
import cz.darken.app.overlay.OverlayService

object PermissionIntents {

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService<PowerManager>() ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Try the system screen directly; package visibility must not decide availability. */
    fun openOverlaySettings(context: Context): Boolean = openFirstAvailable(
        context,
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()),
        appDetailsSettings(context),
    )

    fun appDetailsSettings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }

    private fun notificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun openNotificationSettings(context: Context): Boolean = openFirstAvailable(
        context, notificationSettings(context), appDetailsSettings(context),
    )

    fun areInteractiveNotificationsEnabled(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val manager = context.getSystemService<NotificationManager>() ?: return false
        val channel = manager.getNotificationChannel(OverlayService.CHANNEL_INTERACTIVE)
        // A channel that has not been created yet is not blocked by the user.
        return channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun openInteractiveNotificationSettings(context: Context): Boolean {
        val manager = context.getSystemService<NotificationManager>()
        val channel = manager?.getNotificationChannel(OverlayService.CHANNEL_INTERACTIVE)
        if (NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            channel?.importance == NotificationManager.IMPORTANCE_NONE
        ) {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, OverlayService.CHANNEL_INTERACTIVE)
            return openFirstAvailable(
                context, intent, notificationSettings(context), appDetailsSettings(context),
            )
        }
        return openNotificationSettings(context)
    }

    fun requestIgnoreBatteryOptimizations(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
        }

    fun openBatteryOptimizationSettings(context: Context): Boolean = openFirstAvailable(
        context,
        requestIgnoreBatteryOptimizations(context),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appDetailsSettings(context),
    )

    fun openAppDetailsSettings(context: Context): Boolean =
        openFirstAvailable(context, appDetailsSettings(context))

    private fun openFirstAvailable(context: Context, vararg intents: Intent): Boolean {
        for (intent in intents) {
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Manufacturer-specific settings screens may not be available.
            } catch (_: SecurityException) {
                // Try a more general screen if the manufacturer restricts access.
            }
        }
        return false
    }
}
