package cz.darken.app.model


object OverlayTint {

    const val PRESET_GRAY = "gray"
    const val PRESET_GRAPHITE = "graphite"
    const val PRESET_BURGUNDY = "burgundy"
    const val PRESET_CUSTOM = "custom"

    val presetSlots = listOf(PRESET_GRAY, PRESET_GRAPHITE, PRESET_BURGUNDY)

    val GrayArgb: Int = 0xFF000000.toInt()
    val GraphiteArgb: Int = 0xFF1A1A1A.toInt()
    val BurgundyArgb: Int = 0xFF6B2020.toInt()
    /** Warm light brown (matches 4th swatch in custom palette). */
    val AmberArgb: Int = 0xFF4A3728.toInt()
    val RedArgb: Int = 0xFF8B3030.toInt()

    fun resolveArgb(preset: String, customArgb: Int): Int = when (preset) {
        PRESET_GRAPHITE -> GraphiteArgb
        PRESET_BURGUNDY -> BurgundyArgb
        PRESET_CUSTOM -> customArgb
        else -> GrayArgb
    }

    fun parseHexColor(input: String): Int? {
        val cleaned = input.trim().removePrefix("#")
        if (cleaned.length != 6 && cleaned.length != 8) return null
        return try {
            val value = cleaned.toLong(16)
            if (cleaned.length == 6) {
                (0xFF000000 or value).toInt()
            } else {
                value.toInt()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun alphaForDimLevel(dimLevel: Int): Float {
        val level = DimLevel.clamp(dimLevel)
        // Align the warning boundary with 0.8 window opacity while preserving full dimming at 99.
        return if (level <= DimLevel.WARNING_THRESHOLD) level / 100f
        else 0.8f + (level - DimLevel.WARNING_THRESHOLD) * 0.2f /
            (DimLevel.MAX - DimLevel.WARNING_THRESHOLD)
    }
}
