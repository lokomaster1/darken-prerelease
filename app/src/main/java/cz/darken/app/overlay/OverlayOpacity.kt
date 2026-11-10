package cz.darken.app.overlay

import cz.darken.app.model.OverlayTint

/** Window opacity, rather than View alpha, participates in Android's touch security checks. */
object OverlayOpacity {
    fun alpha(dimLevel: Int, suspended: Boolean = false): Float =
        if (suspended) 0f else OverlayTint.alphaForDimLevel(dimLevel)
}
