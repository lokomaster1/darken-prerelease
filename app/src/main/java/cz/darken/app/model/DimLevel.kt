package cz.darken.app.model

/** User-facing dimming range, independent of persistence and Android components. */
object DimLevel {
    const val MIN = 0
    const val MAX = 99
    const val DEFAULT = 50
    const val WARNING_THRESHOLD = 80

    /** A small plateau makes 80 easy to select without preventing continued dragging. */
    fun snapSliderLevel(level: Int): Int =
        if (level in WARNING_THRESHOLD..82) WARNING_THRESHOLD else clamp(level)
    fun clamp(level: Int): Int = level.coerceIn(MIN, MAX)
}
