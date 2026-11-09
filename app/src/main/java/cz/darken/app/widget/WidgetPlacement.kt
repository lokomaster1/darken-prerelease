package cz.darken.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetPlacement {
    enum class Result {
        Requested,
        Unsupported,
        Error,
    }

    fun isSupported(context: Context): Boolean = try {
        context.getSystemService(AppWidgetManager::class.java)?.isRequestPinAppWidgetSupported == true
    } catch (_: RuntimeException) {
        false
    }

    /** The launcher controls confirmation; an accepted request does not mean the widget was added. */
    fun requestAddWidget(context: Context): Result {
        return try {
            val manager = context.getSystemService(AppWidgetManager::class.java)
                ?: return Result.Unsupported
            if (!manager.isRequestPinAppWidgetSupported) return Result.Unsupported
            val provider = ComponentName(context, DarkenAppWidgetProvider::class.java)
            if (manager.requestPinAppWidget(provider, null, null)) Result.Requested else Result.Unsupported
        } catch (_: RuntimeException) {
            Result.Error
        }
    }
}
