package cz.darken.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** A temporary overlay owned by the editor's activity, independent of saved dimming state. */
class ColorPreviewOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show(argb: Int, intensity: Int): Boolean {
        close()
        val manager = windowManager ?: return false
        val preview = View(context).apply {
            setBackgroundColor(argb)
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
            alpha = OverlayOpacity.alpha(intensity)
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        OverlayService.setColorPreviewVisible(true)
        try {
            manager.addView(preview, params)
            view = preview
        } catch (_: SecurityException) {
            OverlayService.setColorPreviewVisible(false)
            return false
        } catch (_: WindowManager.BadTokenException) {
            OverlayService.setColorPreviewVisible(false)
            return false
        }
        return true
    }

    fun update(argb: Int, intensity: Int) {
        val preview = view ?: return
        preview.setBackgroundColor(argb)
        val params = preview.layoutParams as WindowManager.LayoutParams
        val alpha = OverlayOpacity.alpha(intensity)
        if (params.alpha != alpha) {
            params.alpha = alpha
            windowManager?.updateViewLayout(preview, params)
        }
    }

    fun close() {
        val preview = view ?: return
        try {
            windowManager?.removeViewImmediate(preview)
        } finally {
            view = null
            OverlayService.setColorPreviewVisible(false)
        }
    }
}
