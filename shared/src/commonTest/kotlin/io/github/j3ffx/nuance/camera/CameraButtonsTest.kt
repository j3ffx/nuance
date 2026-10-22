package io.github.j3ffx.nuance.camera

import io.github.j3ffx.nuance.render.CameraStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class CameraButtonsTest {
    @Test
    fun zoomStepsGoThroughTheLensesThenBack() {
        val wide = CameraStatus(minZoom = 0.6f, maxZoom = 8f, zoom = 1f)
        assertEquals(2f, nextZoomStep(wide))
        assertEquals(3f, nextZoomStep(wide.copy(zoom = 2f)))
        assertEquals(5f, nextZoomStep(wide.copy(zoom = 3f)))
        assertEquals(0.6f, nextZoomStep(wide.copy(zoom = 5f)))
        assertEquals(1f, nextZoomStep(wide.copy(zoom = 0.6f)))
        // Without an ultra wide lens, 1× is the first step.
        assertEquals(1f, nextZoomStep(CameraStatus(minZoom = 1f, maxZoom = 4f, zoom = 3.5f)))
    }

    @Test
    fun zoomReadsLikeACameraApp() {
        assertEquals("0.6×", zoomText(0.6f))
        assertEquals("1×", zoomText(1f))
        assertEquals("2.5×", zoomText(2.49f))
    }

    @Test
    fun aZoomedViewStaysInsideTheImage() {
        assertEquals(ImageZoomState(2f, 0.25f, 0.75f), ImageZoomState(2f, 0f, 1f).clamped())
        assertEquals(ImageZoomState(1f, 0.5f, 0.5f), ImageZoomState(1f, 0.1f, 0.9f).clamped())
    }
}
