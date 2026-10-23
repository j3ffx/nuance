package io.github.j3ffx.nuance.core.profile

import io.github.j3ffx.nuance.core.cvd.Axis
import io.github.j3ffx.nuance.core.modes.Mode

/**
 * Profiles as short text, to share one with someone (a message, a note, a file) and to keep the
 * list on the phone. One line per profile, e.g.
 * `nuance1;n=Pour Léa;a=d;s=60;p=0;d=60;t=0;m=c;i=100;st=lab-remap;c=0;sa=0;h=29;w=30`.
 *
 * `p`, `d` and `t` are each axis's severity (mixed profiles, 0.1.2); `a` and `s` repeat the main
 * axis, which is all 0.1.0 and 0.1.1 read, and all they wrote.
 *
 * Unknown keys are ignored and missing ones take their default, so an older app reads a newer
 * profile. In the name, `%`, `;`, `=` and line breaks are escaped as %XX.
 */
object ProfileCodec {
    private const val MAGIC = "nuance1"

    fun encode(p: Profile): String = buildList {
        add(MAGIC)
        add("id=${escape(p.id)}")
        if (p.name.isNotEmpty()) add("n=${escape(p.name)}")
        add("a=${axisCode(p.axis)}")
        add("s=${p.severity}")
        add("p=${p.protan}")
        add("d=${p.deutan}")
        add("t=${p.tritan}")
        add("m=${modeCode(p.mode)}")
        add("i=${p.intensity}")
        add("st=${escape(p.strategyId)}")
        add("c=${p.contrast}")
        add("sa=${p.saturation}")
        add("h=${p.isolateHue}")
        add("w=${p.isolateWidth}")
    }.joinToString(";")

    /** The profile in [text], or null if it isn't one (or a value is out of range). */
    fun decode(text: String): Profile? {
        val parts = text.trim().split(";")
        if (parts.firstOrNull() != MAGIC) return null
        val fields = parts.drop(1).mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq) to unescape(part.substring(eq + 1))
        }.toMap()
        return runCatching {
            // Each axis if given; else the main one alone, as 0.1.x wrote it.
            val mixed = listOf("p", "d", "t").any { it in fields }
            val axis = if (mixed) null else fields["a"]?.let(::axisOf) ?: return null
            val severity = if (mixed) 0 else fields["s"]?.toInt() ?: return null
            fun of(key: String, a: Axis) = if (mixed) {
                fields[key]?.toInt() ?: 0
            } else if (axis == a) {
                severity
            } else {
                0
            }
            Profile(
                id = fields["id"] ?: "imported",
                name = fields["n"].orEmpty(),
                protan = of("p", Axis.Protan),
                deutan = of("d", Axis.Deutan),
                tritan = of("t", Axis.Tritan),
                mode = fields["m"]?.let(::modeOf) ?: Mode.Correct,
                intensity = fields["i"]?.toInt() ?: 100,
                strategyId = fields["st"] ?: Profile.Default.strategyId,
                contrast = fields["c"]?.toInt() ?: 0,
                saturation = fields["sa"]?.toInt() ?: 0,
                isolateHue = fields["h"]?.toInt() ?: Profile.Default.isolateHue,
                isolateWidth = fields["w"]?.toInt() ?: 30
            )
        }.getOrNull()
    }

    /** A list of profiles, one per line. */
    fun encodeAll(profiles: List<Profile>) = profiles.joinToString("\n") { encode(it) }

    /** Every valid profile in [text]; invalid lines are skipped. */
    fun decodeAll(text: String) = text.lines().filter { it.isNotBlank() }.mapNotNull { decode(it) }

    private fun axisCode(a: Axis) = when (a) {
        Axis.Protan -> "p"
        Axis.Deutan -> "d"
        Axis.Tritan -> "t"
    }

    private fun axisOf(code: String) = when (code) {
        "p" -> Axis.Protan
        "d" -> Axis.Deutan
        "t" -> Axis.Tritan
        else -> null
    }

    private fun modeCode(m: Mode) = when (m) {
        Mode.Correct -> "c"
        Mode.Isolate -> "i"
        Mode.Simulate -> "s"
        Mode.Compare -> "k"
    }

    private fun modeOf(code: String) = when (code) {
        "c" -> Mode.Correct
        "i" -> Mode.Isolate
        "s" -> Mode.Simulate
        "k" -> Mode.Compare
        else -> null
    }

    private val ESCAPED = setOf('%', ';', '=', '\n', '\r')

    private fun escape(s: String) = buildString {
        for (ch in s) {
            if (ch in ESCAPED) append('%').append(ch.code.toString(16).uppercase().padStart(2, '0')) else append(ch)
        }
    }

    private fun unescape(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '%' && i + 2 < s.length) {
                val code = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (code != null) {
                    append(code.toChar())
                    i += 3
                    continue
                }
            }
            append(ch)
            i++
        }
    }
}
