package io.github.j3ffx.nuance.render

import io.github.j3ffx.nuance.core.Lut3d
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A renderer that shows nothing: iOS until its camera side is written (J7), and previews. */
class NoCameraRenderer : CameraRenderer {
    override val stats: StateFlow<RenderStats> = MutableStateFlow(RenderStats(pipeline = "none"))
    override val pipelines: List<String> = emptyList()

    override fun selectPipeline(name: String) = Unit

    override fun setLut(lut: Lut3d, bakeMs: Float?) = Unit
}
