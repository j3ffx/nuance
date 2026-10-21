package io.github.j3ffx.nuance.render

import io.github.j3ffx.nuance.core.Lut3d
import io.github.j3ffx.nuance.core.Rgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A renderer that shows nothing: iOS until its camera side is written (J7), and previews. */
class NoCameraRenderer : CameraRenderer {
    override val stats: StateFlow<RenderStats> = MutableStateFlow(RenderStats(source = "none"))
    override val sources: List<String> = emptyList()

    override fun selectSource(name: String) = Unit

    override fun setViews(luts: List<Lut3d>, bakeMs: Float?) = Unit

    override suspend fun sampleColor(x: Float, y: Float): Rgb? = null
}
