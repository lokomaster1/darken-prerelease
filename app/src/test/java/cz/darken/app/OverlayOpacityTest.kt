package cz.darken.app

import cz.darken.app.overlay.OverlayOpacity
import cz.darken.app.model.DimLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayOpacityTest {
    @Test fun lowDimmingKeepsItsOpacity() {
        assertEquals(0.1f, OverlayOpacity.alpha(10), 0.000001f)
    }

    @Test fun warningBoundaryIsSafeButHigherDimmingRemainsAvailable() {
        assertEquals(0.8f, OverlayOpacity.alpha(80), 0f)
        assertEquals(1f, OverlayOpacity.alpha(99), 0f)
        for (level in 1..99) {
            org.junit.Assert.assertTrue(OverlayOpacity.alpha(level) > OverlayOpacity.alpha(level - 1))
        }
    }

    @Test fun detentCanBeEnteredAndExitedInBothDirections() {
        assertEquals(77, DimLevel.snapSliderLevel(77))
        assertEquals(78, DimLevel.snapSliderLevel(78))
        assertEquals(79, DimLevel.snapSliderLevel(79))
        for (level in 80..82) assertEquals(80, DimLevel.snapSliderLevel(level))
        assertEquals(83, DimLevel.snapSliderLevel(83))
        assertEquals(99, DimLevel.snapSliderLevel(99))
    }

    @Test fun suspendedServiceContributesNoOpacityDuringPreview() {
        assertEquals(0f, OverlayOpacity.alpha(99, suspended = true), 0f)
        assertEquals(1f, OverlayOpacity.alpha(99), 0f)
    }
}
