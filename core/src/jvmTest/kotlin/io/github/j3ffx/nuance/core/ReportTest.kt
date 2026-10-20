package io.github.j3ffx.nuance.core

import io.github.j3ffx.nuance.core.charts.SyntheticCharts
import io.github.j3ffx.nuance.core.charts.SyntheticImage
import io.github.j3ffx.nuance.core.color.deltaE2000
import io.github.j3ffx.nuance.core.correction.CorrectionMetrics
import io.github.j3ffx.nuance.core.correction.CorrectionStrategy
import io.github.j3ffx.nuance.core.correction.ErrorRedistribution
import io.github.j3ffx.nuance.core.correction.LabRemap
import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.cvd.CvdProfile
import io.github.j3ffx.nuance.core.modes.ColorFunction
import io.github.j3ffx.nuance.core.modes.LinearFunction
import io.github.j3ffx.nuance.core.modes.Settings
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.measureTime

/**
 * Writes the measurement report behind docs/ALGORITHMS.md: `npm run report` (the test does nothing
 * without -Pnuance.report). Tables go to measurements.md, comparison images to images/.
 */
class ReportTest {
    @Test
    fun writeReport() {
        val target = System.getProperty("nuance.report").orEmpty()
        if (target.isEmpty()) return
        val dir = File(File(System.getProperty("nuance.root")), target).apply { mkdirs() }
        val md = StringBuilder()
        md.appendLine("# Mesures des algorithmes")
        md.appendLine()
        md.appendLine(
            "Fichier généré par `npm run report` (`core/src/jvmTest/.../ReportTest.kt`) : ne pas le modifier à la main."
        )
        md.appendLine("Toutes les images sont synthétiques. ΔE = CIEDE2000.")
        md.appendLine()
        corrections(md)
        isoCost(md)
        lutAccuracy(md)
        bakeTimes(md)
        images(File(dir, "images"), md)
        File(dir, "measurements.md").writeText(md.toString())
    }

    private val strategies: List<Pair<String, CorrectionStrategy>> = listOf(
        "redistribution d’erreur" to ErrorRedistribution,
        "remappage CIELAB (défaut)" to LabRemap.Default
    )

    private fun corrections(md: StringBuilder) {
        val distinct = CorrectionMetrics.DISTINCT.toInt()
        md.appendLine("## Corrections à intensité 100 %")
        md.appendLine()
        md.appendLine(
            "Paires de couleurs que le profil confond (ΔE < $distinct à travers sa simulation, " +
                "ΔE ≥ 8 pour une vision typique), prises sur ses lignes de confusion. " +
                "« Avant → après » : ΔE moyen entre les deux couleurs d’une paire, vues à travers la simulation. " +
                "« Distinctes » : part des paires à ΔE ≥ $distinct après correction. " +
                "« Coût » : ΔE moyen entre une couleur et sa correction (vision typique). " +
                "« Peau » : ΔE moyen des tons chair, et leur plus grand décalage de teinte. " +
                "« Gris » : plus grand ΔE d’un gris."
        )
        md.appendLine()
        md.appendLine(
            "| Axe | Sévérité | Stratégie | Paires | Avant → après | Distinctes | Coût | Peau | Teinte peau | Gris |"
        )
        md.appendLine("|---|---:|---|---:|---:|---:|---:|---:|---:|---:|")
        for (axis in Axis.entries) {
            for (sev in listOf(0.2, 0.4, 0.6, 0.8, 1.0)) {
                for ((name, s) in strategies) {
                    val r = CorrectionMetrics.evaluate(s, CvdProfile(axis, sev), 1.0)
                    md.appendLine(
                        "| $axis | ${(sev * 100).toInt()} % | $name | ${r.pairs} | ${f1(
                            r.meanBefore
                        )} → ${f1(r.meanAfter)} | " +
                            "${pct(
                                r.distinctAfter
                            )} | ${f1(
                                r.meanChange
                            )} | ${f1(r.skinChange)} | ${f0(r.skinHueShift)}° | ${f2(r.worstGrayChange)} |"
                    )
                }
            }
        }
        md.appendLine()
    }

    private fun isoCost(md: StringBuilder) {
        md.appendLine("## Comparaison à coût égal")
        md.appendLine()
        md.appendLine(
            "Chaque stratégie est réglée pour modifier l’image d’un ΔE moyen de 8, puis on compare ce qu’elle " +
                "sépare. Les variantes du remappage (gains chroma / clarté) montrent d’où vient le choix par défaut."
        )
        md.appendLine()
        md.appendLine("| Axe | Sévérité | Stratégie | Distinctes | Avant → après | Teinte peau |")
        md.appendLine("|---|---:|---|---:|---:|---:|")
        val variants = strategies + listOf(
            "CIELAB 1 / 0" to LabRemap(1.0, 0.0),
            "CIELAB 0,6 / 0,6" to LabRemap(0.6, 0.6),
            "CIELAB 0 / 1" to LabRemap(0.0, 1.0)
        )
        for (axis in Axis.entries) {
            for (sev in listOf(0.6, 1.0)) {
                for ((name, s) in variants) {
                    val p = CvdProfile(axis, sev)
                    val r = CorrectionMetrics.evaluate(s, p, intensityFor(s, p, 8.0))
                    md.appendLine(
                        "| $axis | ${(sev * 100).toInt()} % | $name | ${pct(r.distinctAfter)} | " +
                            "${f1(r.meanBefore)} → ${f1(r.meanAfter)} | ${f0(r.skinHueShift)}° |"
                    )
                }
            }
        }
        md.appendLine()
    }

    private fun intensityFor(s: CorrectionStrategy, p: CvdProfile, cost: Double): Double {
        var lo = 0.0
        var hi = 4.0
        repeat(14) {
            val mid = (lo + hi) / 2
            if (CorrectionMetrics.evaluate(s, p, mid).meanChange < cost) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    private fun lutModes(): Map<String, LinearFunction> {
        val s = Settings(CvdProfile(Axis.Deutan, 1.0), intensity = 1.0, contrast = 0.3, saturation = 0.3)
        return mapOf(
            "simulation deutan 100 %" to ColorFunction.simulation(s.profile),
            "simulation tritan 100 %" to ColorFunction.simulation(CvdProfile(Axis.Tritan, 1.0)),
            "correction deutan 100 % + contraste" to ColorFunction.correction(s),
            "correction tritan 100 %" to ColorFunction.correction(
                s.copy(profile = CvdProfile(Axis.Tritan, 1.0), contrast = 0.0, saturation = 0.0)
            ),
            "isoler le rouge" to ColorFunction.isolation(s)
        )
    }

    private fun lutAccuracy(md: StringBuilder) {
        md.appendLine("## Taille de la LUT")
        md.appendLine()
        md.appendLine(
            "ΔE entre la fonction exacte et la LUT après passage en texture RGBA8 et interpolation trilinéaire " +
                "(ce que fait le GPU), sur 5000 couleurs tirées au hasard : moyenne / 99e centile / maximum."
        )
        md.appendLine()
        val sizes = listOf(17, 25, 33, 49, 65)
        md.appendLine("| Mode | " + sizes.joinToString(" | ") { "$it³" } + " |")
        md.appendLine("|---|" + sizes.joinToString("") { "---:|" })
        val random = Random(1)
        val samples = List(5000) { Rgb(random.nextFloat(), random.nextFloat(), random.nextFloat()) }
        for ((name, f) in lutModes()) {
            val exact = ColorFunction.rgb(f)
            val cells = sizes.map { size ->
                val lut = Lut3d.fromRgba8(size, Lut3d.bakeLinear(size, f).toRgba8())
                val e = samples.map { deltaE2000(exact(it), lut.sample(it)) }.sorted()
                "${f2(e.average())} / ${f2(e[e.size * 99 / 100])} / ${f2(e.last())}"
            }
            md.appendLine("| $name | " + cells.joinToString(" | ") + " |")
        }
        md.appendLine()
    }

    private fun bakeTimes(md: StringBuilder) {
        val f = lutModes().getValue("correction deutan 100 % + contraste")
        repeat(5) { Lut3d.bakeLinear(33, f) }
        md.appendLine("## Temps de cuisson")
        md.appendLine()
        md.appendLine("JVM du PC de développement, à titre indicatif (correction + contraste, médiane de 5) :")
        md.appendLine()
        for (size in listOf(17, 33, 49)) {
            val t = (1..5).map { measureTime { Lut3d.bakeLinear(size, f) }.inWholeMicroseconds / 1000.0 }.sorted()[2]
            md.appendLine("- $size³ : ${f1(t)} ms")
        }
        md.appendLine()
    }

    private fun images(dir: File, md: StringBuilder) {
        dir.mkdirs()
        md.appendLine("## Images de comparaison")
        md.appendLine()
        md.appendLine(
            "De haut en bas : réel, réel vu avec le profil, corrigé (stratégie par défaut, intensité 70 %), " +
                "corrigé vu avec le profil. Sévérité 100 %."
        )
        md.appendLine()
        for (axis in Axis.entries) {
            val name = axis.name.lowercase()
            val views = ColorFunction.comparer(Settings(CvdProfile(axis, 1.0), intensity = 0.7)).map(ColorFunction::rgb)
            val labels = listOf("Réel", "Réel, vu $name", "Corrigé", "Corrigé, vu $name")
            val charts = listOf(
                "rayures" to SyntheticCharts.confusionStripes(axis),
                "teintes" to SyntheticCharts.hueSweep()
            )
            for ((chartName, chart) in charts) {
                val file = "$name-$chartName.png"
                write(File(dir, file), views.map { chart.map(it) }, labels)
                md.appendLine("![${axis.name}, $chartName](images/$file)")
                md.appendLine()
            }
        }
    }

    private fun write(file: File, rows: List<SyntheticImage>, labels: List<String>) {
        val label = 150
        val w = rows[0].width + label
        val h = rows.sumOf { it.height }
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, w, h)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
        var y = 0
        for ((i, row) in rows.withIndex()) {
            out.setRGB(label, y, row.width, row.height, row.pixels, 0, row.width)
            g.color = Color.BLACK
            g.drawString(labels[i], 8, y + row.height / 2 + 5)
            y += row.height
        }
        g.dispose()
        ImageIO.write(out, "png", file)
    }

    // A dash where nothing was measured (no confused pair at low severity). Dot decimals, whatever
    // the machine's locale, so the file doesn't change from one PC to another.
    private fun fmt(pattern: String, v: Double) =
        if (v.isNaN()) "—" else java.lang.String.format(java.util.Locale.ROOT, pattern, v)

    private fun f0(v: Double) = fmt("%.0f", v)

    private fun f1(v: Double) = fmt("%.1f", v)

    private fun f2(v: Double) = fmt("%.2f", v)

    private fun pct(v: Double) = if (v.isNaN()) "—" else fmt("%.0f", v * 100) + " %"
}
