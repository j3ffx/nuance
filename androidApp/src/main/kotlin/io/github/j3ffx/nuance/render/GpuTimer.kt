package io.github.j3ffx.nuance.render

import android.opengl.GLES30

/**
 * GPU time of the draw calls between [begin] and [end], through EXT_disjoint_timer_query when the
 * driver has it. Results come back a few frames later (queries are never waited on), so a ring of
 * queries is used; [lastMs] is the latest finished one.
 */
class GpuTimer {
    val supported: Boolean =
        GLES30.glGetString(GLES30.GL_EXTENSIONS)?.contains("GL_EXT_disjoint_timer_query") == true
    private val queries = IntArray(RING)
    private val pending = BooleanArray(RING)
    private var next = 0
    private var timing = false

    var lastMs: Float? = null
        private set

    init {
        if (supported) GLES30.glGenQueries(RING, queries, 0)
    }

    fun begin() {
        if (!supported) return
        collect()
        timing = !pending[next] // the slot's result isn't back yet: skip timing this frame
        if (timing) GLES30.glBeginQuery(GL_TIME_ELAPSED_EXT, queries[next])
    }

    fun end() {
        if (!timing) return
        GLES30.glEndQuery(GL_TIME_ELAPSED_EXT)
        pending[next] = true
        next = (next + 1) % RING
        timing = false
    }

    private fun collect() {
        val value = IntArray(1)
        for (i in 0 until RING) {
            if (!pending[i]) continue
            GLES30.glGetQueryObjectuiv(queries[i], GLES30.GL_QUERY_RESULT_AVAILABLE, value, 0)
            if (value[0] == 0) continue
            GLES30.glGetQueryObjectuiv(queries[i], GLES30.GL_QUERY_RESULT, value, 0)
            pending[i] = false
            // A disjoint event (frequency change, preemption) makes the result meaningless.
            val disjoint = IntArray(1)
            GLES30.glGetIntegerv(GL_GPU_DISJOINT_EXT, disjoint, 0)
            if (disjoint[0] == 0) lastMs = (value[0].toLong() and 0xFFFFFFFFL) / 1_000_000f
        }
    }

    fun release() {
        if (supported) GLES30.glDeleteQueries(RING, queries, 0)
    }

    private companion object {
        const val RING = 4
        const val GL_TIME_ELAPSED_EXT = 0x88BF
        const val GL_GPU_DISJOINT_EXT = 0x8FBB
    }
}
