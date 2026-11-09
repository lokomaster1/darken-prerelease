package cz.darken.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.darken.app.model.OverlayTint

/** Local editor selection. Saving a preset is an explicit callback owned by the screen. */
internal class CustomColorEditorState(initialArgb: Int, initialIntensity: Int) {
    var hexDigits by mutableStateOf("")
    var acceptHexInput by mutableStateOf(true)
    var selectedArgb by mutableIntStateOf(initialArgb)
    var previewIntensity by mutableIntStateOf(initialIntensity)
    var previewEnabled by mutableStateOf(false)
    var showHueSlider by mutableStateOf(false)
    var hueDegrees by mutableFloatStateOf(argbToHue(initialArgb))
    var pendingSliderSelection by mutableStateOf<Int?>(null)
    var colorEdited by mutableStateOf(false)
    var selectionSaved by mutableStateOf(false)
    fun parseSelection(): Int? =
        if (hexDigits.length == 6) OverlayTint.parseHexColor("#$hexDigits") else null

    fun updateLocalColor(argb: Int, sliderHue: Float? = null) {
        previewEnabled = true
        selectionSaved = false
        selectedArgb = argb
        hexDigits = argbToHexDigits(argb)
        hueDegrees = sliderHue ?: argbToHue(argb).coerceIn(0f, SLIDER_HUE_MAX)
    }

    fun editColor(argb: Int, sliderHue: Float? = null) {
        updateLocalColor(argb, sliderHue)
        colorEdited = true
    }

    fun previewHue(hue: Float) {
        val clamped = hue.coerceIn(0f, SLIDER_HUE_MAX)
        updateLocalColor(overlayTintArgbFromHue(clamped), sliderHue = clamped)
    }

    fun scheduleSliderSelection() {
        if (showHueSlider && !selectionSaved) {
            pendingSliderSelection = selectedArgb
        }
    }

}

internal fun argbToHexDigits(argb: Int): String {
    val rgb = argb and 0xFFFFFF
    return String.format("%06X", rgb)
}

internal fun argbToHue(argb: Int): Float = argbToHsv(argb)[0]

internal fun argbToHsv(argb: Int): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return hsv
}

/** Hex digits only (no #), uppercase, max 6 characters; extra input is ignored. */
internal fun normalizeHexDigits(raw: String): String {
    return raw
        .replace("#", "")
        .filter { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
        .uppercase()
        .take(6)
}
