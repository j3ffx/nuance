package io.github.j3ffx.nuance.core.color

/** Three doubles: a colour in some space (linear RGB, XYZ, LMS, Lab…). Computations run in double. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)

    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)

    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)

    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z

    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

    operator fun get(i: Int) = when (i) {
        0 -> x
        1 -> y
        2 -> z
        else -> throw IndexOutOfBoundsException("Vec3 has 3 components, asked for $i")
    }

    companion object {
        val Zero = Vec3(0.0, 0.0, 0.0)
        val One = Vec3(1.0, 1.0, 1.0)

        fun lerp(a: Vec3, b: Vec3, t: Double) = a + (b - a) * t
    }
}

/** A 3×3 matrix, row-major. */
class Mat3(private val m: DoubleArray) {
    init {
        require(m.size == 9) { "a 3×3 matrix needs 9 values, got ${m.size}" }
    }

    operator fun get(row: Int, col: Int) = m[row * 3 + col]

    operator fun times(v: Vec3) = Vec3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z,
        m[3] * v.x + m[4] * v.y + m[5] * v.z,
        m[6] * v.x + m[7] * v.y + m[8] * v.z
    )

    operator fun times(o: Mat3): Mat3 {
        val r = DoubleArray(9)
        for (i in 0..2) for (j in 0..2) r[i * 3 + j] = (0..2).sumOf { k -> this[i, k] * o[k, j] }
        return Mat3(r)
    }

    fun row(i: Int) = Vec3(m[i * 3], m[i * 3 + 1], m[i * 3 + 2])

    fun inverse(): Mat3 {
        val det = m[0] * (m[4] * m[8] - m[5] * m[7]) -
            m[1] * (m[3] * m[8] - m[5] * m[6]) +
            m[2] * (m[3] * m[7] - m[4] * m[6])
        require(kotlin.math.abs(det) > 1e-12) { "singular matrix" }
        val c = doubleArrayOf(
            m[4] * m[8] - m[5] * m[7], m[2] * m[7] - m[1] * m[8], m[1] * m[5] - m[2] * m[4],
            m[5] * m[6] - m[3] * m[8], m[0] * m[8] - m[2] * m[6], m[2] * m[3] - m[0] * m[5],
            m[3] * m[7] - m[4] * m[6], m[1] * m[6] - m[0] * m[7], m[0] * m[4] - m[1] * m[3]
        )
        return Mat3(DoubleArray(9) { c[it] / det })
    }

    fun toArray(): DoubleArray = m.copyOf()

    override fun toString() = (0..2).joinToString("; ", "[", "]") { row(it).let { r -> "${r.x}, ${r.y}, ${r.z}" } }

    companion object {
        val Identity = Mat3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0))

        fun of(vararg values: Double) = Mat3(doubleArrayOf(*values))

        fun lerp(a: Mat3, b: Mat3, t: Double) = Mat3(DoubleArray(9) { a.m[it] + (b.m[it] - a.m[it]) * t })
    }
}
