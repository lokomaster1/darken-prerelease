package cz.darken.app.model

/** User-facing dimming range, independent of persistence and Android components. */
object DimLevel {
    const val MIN = 0
    const val MAX = 99
    const val DEFAULT = 50
    fun clamp(level: Int): Int = level.coerceIn(MIN, MAX)
}
