package cz.darken.app

import cz.darken.app.overlay.OverlayOpacity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayOpacityTest {
    @Test fun displayedScaleCoversAvailableWindowOpacity() {
        assertEquals(0f, OverlayOpacity.alpha(0), 0f)
        assertEquals(0.4f, OverlayOpacity.alpha(49), 0.005f)
        assertEquals(0.8f, OverlayOpacity.alpha(99), 0f)
    }

    @Test fun everyDisplayedStepChangesOpacityWithoutCrossingTheLimit() {
        for (level in 1..99) {
            assertTrue(OverlayOpacity.alpha(level) > OverlayOpacity.alpha(level - 1))
            assertTrue(OverlayOpacity.alpha(level) <= 0.8f)
            assertEquals(0.8f / 99, OverlayOpacity.alpha(level) - OverlayOpacity.alpha(level - 1), 0.000001f)
        }
    }

    @Test fun outOfRangeRequestsStayWithinSupportedOpacity() {
        assertEquals(0f, OverlayOpacity.alpha(-10), 0f)
        assertEquals(0.8f, OverlayOpacity.alpha(120), 0f)
    }

    @Test fun suspendedOverlayContributesNoOpacityDuringPreview() {
        assertEquals(0f, OverlayOpacity.alpha(99, suspended = true), 0f)
        assertEquals(0.8f, OverlayOpacity.alpha(99), 0f)
    }
}
