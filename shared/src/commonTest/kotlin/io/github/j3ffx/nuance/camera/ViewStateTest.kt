package io.github.j3ffx.nuance.camera

import io.github.j3ffx.nuance.core.modes.Mode
import io.github.j3ffx.nuance.render.ViewLayout
import io.github.j3ffx.nuance.render.ViewRect
import kotlin.test.Test
import kotlin.test.assertEquals

class ViewStateTest {
    @Test
    fun eachModeShowsItsViews() {
        assertEquals(listOf(ViewKind.Corrected), ViewState(mode = Mode.Correct).views)
        assertEquals(listOf(ViewKind.Isolated), ViewState(mode = Mode.Isolate).views)
        assertEquals(listOf(ViewKind.RealSeen), ViewState(mode = Mode.Simulate).views)
        assertEquals(
            listOf(ViewKind.Real, ViewKind.RealSeen, ViewKind.Corrected, ViewKind.CorrectedSeen),
            ViewState(mode = Mode.Compare).views
        )
        val two =
            ViewState(
                mode = Mode.Compare,
                compareCount = 2,
                compareSelection = listOf(ViewKind.Real, ViewKind.CorrectedSeen)
            )
        assertEquals(listOf(ViewKind.Real, ViewKind.CorrectedSeen), two.views)
    }

    @Test
    fun devCrossAxisComparesTheProtanAndDeutanCorrections() {
        assertEquals(
            listOf(ViewKind.Real, ViewKind.RealSeen, ViewKind.ProtanCorrectedSeen, ViewKind.DeutanCorrectedSeen),
            ViewState(mode = Mode.Compare, crossAxis = true).views
        )
    }

    @Test
    fun holdingShowsTheOriginalAloneWhateverTheMode() {
        for (mode in Mode.entries) {
            assertEquals(
                listOf(ViewKind.Real),
                ViewState(mode = mode, showingOriginal = true).views
            )
        }
    }

    @Test
    fun layoutsTileTheScreenWithoutOverlap() {
        for (count in listOf(1, 2, 4)) {
            for (landscape in listOf(false, true)) {
                val rects = ViewLayout.rects(count, landscape)
                assertEquals(count, rects.size)
                assertEquals(1f, rects.sumOf { (it.width * it.height).toDouble() }.toFloat(), 1e-6f)
                for (r in rects) assertEquals(true, r.x >= 0 && r.y >= 0 && r.x + r.width <= 1 && r.y + r.height <= 1)
            }
        }
        // Two views: one above the other in portrait, side by side in landscape.
        assertEquals(ViewRect(0f, 0.5f, 1f, 0.5f), ViewLayout.rects(2, landscape = false)[1])
        assertEquals(ViewRect(0.5f, 0f, 0.5f, 1f), ViewLayout.rects(2, landscape = true)[1])
    }
}
