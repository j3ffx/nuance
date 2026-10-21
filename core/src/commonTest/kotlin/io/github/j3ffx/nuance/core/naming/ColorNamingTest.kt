package io.github.j3ffx.nuance.core.naming

import io.github.j3ffx.nuance.core.Rgb
import io.github.j3ffx.nuance.core.naming.Chroma.Greyish
import io.github.j3ffx.nuance.core.naming.Chroma.Vivid
import io.github.j3ffx.nuance.core.naming.ColorTerm.Beige
import io.github.j3ffx.nuance.core.naming.ColorTerm.Black
import io.github.j3ffx.nuance.core.naming.ColorTerm.Blue
import io.github.j3ffx.nuance.core.naming.ColorTerm.Brown
import io.github.j3ffx.nuance.core.naming.ColorTerm.Green
import io.github.j3ffx.nuance.core.naming.ColorTerm.Grey
import io.github.j3ffx.nuance.core.naming.ColorTerm.Olive
import io.github.j3ffx.nuance.core.naming.ColorTerm.Orange
import io.github.j3ffx.nuance.core.naming.ColorTerm.Pink
import io.github.j3ffx.nuance.core.naming.ColorTerm.Red
import io.github.j3ffx.nuance.core.naming.ColorTerm.Turquoise
import io.github.j3ffx.nuance.core.naming.ColorTerm.Violet
import io.github.j3ffx.nuance.core.naming.ColorTerm.White
import io.github.j3ffx.nuance.core.naming.ColorTerm.Yellow
import io.github.j3ffx.nuance.core.naming.Lightness.Dark
import io.github.j3ffx.nuance.core.naming.Lightness.Light
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorNamingTest {
    private fun rgb(hex: String) = Rgb(
        hex.substring(0, 2).toInt(16) / 255f,
        hex.substring(2, 4).toInt(16) / 255f,
        hex.substring(4, 6).toInt(16) / 255f
    )

    /** Reference swatches (CSS colour names, for the record) and the name a person would give. */
    private val swatches = listOf(
        "000000" to ColorName(Black),
        "111111" to ColorName(Black),
        "ffffff" to ColorName(White),
        "808080" to ColorName(Grey),
        "d3d3d3" to ColorName(Grey, Light),
        "404040" to ColorName(Grey, Dark),
        "ff0000" to ColorName(Red, chroma = Vivid),
        "b22222" to ColorName(Red),
        "800000" to ColorName(Red, Dark),
        "ff8000" to ColorName(Orange),
        "ff6600" to ColorName(Orange, chroma = Vivid),
        "8b4513" to ColorName(Brown),
        "a0522d" to ColorName(Brown),
        "d2b48c" to ColorName(Beige),
        "f5f5dc" to ColorName(Beige),
        "ffff00" to ColorName(Yellow, chroma = Vivid),
        "ffd700" to ColorName(Yellow),
        "808000" to ColorName(Olive),
        "00ff00" to ColorName(Green, chroma = Vivid),
        "228b22" to ColorName(Green),
        "006400" to ColorName(Green, Dark),
        "90ee90" to ColorName(Green, Light),
        "008080" to ColorName(Turquoise, Dark),
        "40e0d0" to ColorName(Turquoise, Light),
        "0000ff" to ColorName(Blue, chroma = Vivid),
        "4169e1" to ColorName(Blue),
        "000080" to ColorName(Blue, Dark),
        "87ceeb" to ColorName(Blue, Light),
        "708090" to ColorName(Blue, chroma = Greyish),
        "8000ff" to ColorName(Violet, chroma = Vivid),
        "800080" to ColorName(Violet),
        "b0a0e0" to ColorName(Violet, Light),
        "ff00ff" to ColorName(Pink, chroma = Vivid),
        "ffc0cb" to ColorName(Pink, Light),
        "ff69b4" to ColorName(Pink),
        "fa8072" to ColorName(Pink)
    )

    @Test
    fun namesReferenceSwatchesAsAPersonWould() {
        val wrong = swatches.mapNotNull { (hex, expected) ->
            val got = ColorNaming.name(rgb(hex))
            if (got == expected) null else "#$hex: expected $expected, got $got"
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun everyGreyIsGreyBlackOrWhite() {
        for (i in 0..255) {
            val v = i / 255f
            val term = ColorNaming.name(Rgb(v, v, v)).term
            assertTrue(term == Black || term == Grey || term == White, "grey $i named $term")
        }
    }

    @Test
    fun averagesInLinearLight() {
        // Half black, half white: the mean light is 50 %, encoded as 188, not 128.
        val mean = ColorNaming.average(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
        assertEquals("#BCBCBC", ColorNaming.hex(mean))
    }

    @Test
    fun hexIsUppercaseWithLeadingZeros() {
        assertEquals("#0A00FF", ColorNaming.hex(Rgb(10 / 255f, 0f, 1f)))
    }
}
