package io.github.j3ffx.nuance.core

import io.github.j3ffx.nuance.core.charts.SyntheticCharts
import io.github.j3ffx.nuance.core.charts.SyntheticImage
import io.github.j3ffx.nuance.core.correction.CorrectionStrategy
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.modes.ColorFunction
import io.github.j3ffx.nuance.core.modes.HuePreset
import io.github.j3ffx.nuance.core.modes.Settings
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Golden references: every mode applied to the synthetic patch chart, kept as text in
 * src/jvmTest/resources/golden. Any change to an algorithm shows up here; when it is intended,
 * regenerate with `npm run golden` and review the diff.
 */
class GoldenTest {
    private val root = File(System.getProperty("nuance.root") ?: ".")
    private val dir = File(root, "core/src/jvmTest/resources/golden")
    private val update = System.getProperty("nuance.updateGolden") == "true"

    private fun modes(): Map<String, (Rgb) -> Rgb> = buildMap {
        for (axis in Axis.entries) {
            val name = axis.name.lowercase()
            for (severity in listOf(50, 100)) {
                put(
                    "simulate-$name-$severity",
                    ColorFunction.rgb(
                        ColorFunction.simulation(
                            CvdProfile(
                                axis,
                                severity / 100.0
                            )
                        )
                    )
                )
            }
            for (strategy in CorrectionStrategy.all) {
                val s = Settings(CvdProfile(axis, 1.0), intensity = 0.7, strategyId = strategy.id)
                put("correct-${strategy.id}-$name-100", ColorFunction.rgb(ColorFunction.correction(s)))
            }
        }
        val base = Settings(CvdProfile(Axis.Deutan, 1.0))
        for (preset in HuePreset.entries) {
            put(
                "isolate-${preset.name.lowercase()}",
                ColorFunction.rgb(ColorFunction.isolation(base.copy(isolateHue = preset.hue)))
            )
        }
        put("enhance-30-30", ColorFunction.rgb(ColorFunction.enhancement(0.3, 0.3)))
    }

    @Test
    fun everyModeMatchesItsGolden() {
        val patches = SyntheticCharts.patches()
        val problems = mutableListOf<String>()
        for ((name, f) in modes()) {
            val lines = patches.map { "${hex(it)} ${hex(f(it))}" }
            val file = File(dir, "$name.txt")
            if (update) {
                dir.mkdirs()
                file.writeText(lines.joinToString("\n", postfix = "\n"))
                continue
            }
            if (!file.exists()) {
                problems += "$name: no golden file (run npm run golden)"
                continue
            }
            val expected = file.readLines().filter { it.isNotBlank() }
            if (expected.size != lines.size) {
                problems += "$name: ${expected.size} lines kept, ${lines.size} now"
                continue
            }
            // One step of 8-bit rounding is tolerated per channel.
            val diffs = lines.indices.filter { !close(expected[it], lines[it]) }
            if (diffs.isNotEmpty()) {
                problems +=
                    "$name: ${diffs.size} patches differ, first: ${expected[diffs[0]]} → ${lines[diffs[0]]}"
            }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    @Test
    fun goldensCoverEveryMode() {
        if (update) return
        val kept = dir.listFiles { f -> f.extension == "txt" }?.map { it.nameWithoutExtension }?.toSet().orEmpty()
        val stale = kept - modes().keys
        assertTrue(stale.isEmpty(), "golden files without a mode: $stale")
    }

    private fun hex(c: Rgb) = "%06X".format(SyntheticImage.pack(c) and 0xFFFFFF)

    private fun close(a: String, b: String): Boolean {
        val x = a.split(" ").map { it.toInt(16) }
        val y = b.split(" ").map { it.toInt(16) }
        if (x[0] != y[0]) return false
        return (0..2).all { shift -> abs(((x[1] shr (shift * 8)) and 0xFF) - ((y[1] shr (shift * 8)) and 0xFF)) <= 1 }
    }
}
