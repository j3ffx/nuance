package io.github.j3ffx.nuance.core.screening

import io.github.j3ffx.nuance.core.cvd.Axis
import kotlin.random.Random

/** How much to trust a result. Always shown, with the reminder that this is not a diagnosis. */
enum class Confidence { High, Medium, Low }

/**
 * What "Find my profile" concludes: an axis (null if nothing was found) and a severity in percent,
 * with the evidence behind it. Kept on the phone only.
 */
data class ScreeningResult(
    val axis: Axis?,
    val severity: Int,
    val confidence: Confidence,
    /** Missed plates per axis, a strong plate counting 2 and a subtle one 1 (out of 6). */
    val plateScores: Map<Axis, Int>,
    val demoSeen: Boolean,
    val arrangement: D15.Score,
    /** Severity measured on each axis tried (two when red-green plates could not tell protan from deutan). */
    val severities: Map<Axis, Int>
)

/**
 * The built-in test, as a session the UI drives step by step: plates, hue arrangement, then
 * severity on the axis the first two point to. Pure logic, no UI: a test runs it whole with
 * simulated observers (ScreeningTest).
 */
class Screening(seed: Long) {
    enum class Step { Plates, Arrangement, Severity, Done }

    private val random = Random(seed)

    val plates: List<Plate> = Plates.set(random)
    private val plateAnswers = ArrayList<Int?>()

    /** The caps as offered on screen, shuffled; the pilot (0) is never among them. */
    val offeredCaps: List<Int> = (1..15).shuffled(random)
    private val placed = ArrayList<Int>()

    private var staircases = ArrayDeque<Staircase>()
    private val finished = ArrayList<Staircase>()
    private var plan: Pair<Axis?, Confidence>? = null

    val step: Step
        get() = when {
            plateAnswers.size < plates.size -> Step.Plates
            placed.size < 15 -> Step.Arrangement
            staircases.isNotEmpty() -> Step.Severity
            else -> Step.Done
        }

    // Plates

    val plateIndex: Int get() = plateAnswers.size
    val plate: Plate? get() = plates.getOrNull(plateAnswers.size)

    /** [digit]: what the person read, or null for "I see nothing". */
    fun answerPlate(digit: Int?) {
        check(step == Step.Plates)
        plateAnswers += digit
    }

    // Arrangement

    /** The caps placed so far, after the pilot. */
    val arrangement: List<Int> get() = placed

    fun place(cap: Int) {
        check(step == Step.Arrangement && cap in 1..15 && cap !in placed)
        placed += cap
        if (placed.size == 15) planSeverity()
    }

    /** Takes back the last cap placed. */
    fun undo() {
        if (step == Step.Arrangement && placed.isNotEmpty()) placed.removeAt(placed.lastIndex)
    }

    // Severity

    val staircase: Staircase? get() = staircases.firstOrNull()

    /** [choice]: the patch touched (0 to 3), or null for "I see no difference". */
    fun answerTrial(choice: Int?) {
        val current = checkNotNull(staircase)
        current.answer(choice)
        if (current.done) finished += staircases.removeFirst()
    }

    private fun planSeverity() {
        val decision = decide(plateScores(), D15.score(placed))
        plan = decision.first to decision.second
        staircases = ArrayDeque(decision.third.map { Staircase(it, random) })
    }

    // Result

    fun plateScores(): Map<Axis, Int> = Axis.entries.associateWith { axis ->
        plates.indices.filter { plates[it].axis == axis && plateAnswers.getOrNull(it) != plates[it].digit }
            .sumOf { if (plates[it].subtle) 1 else 2 }.toInt()
    }

    val demoSeen: Boolean get() = plateAnswers.firstOrNull() == plates.first().digit

    fun result(): ScreeningResult {
        check(step == Step.Done)
        val (planned, base) = checkNotNull(plan)
        val severities = finished.associate { it.axis to it.severity() }
        // Red-green but ambiguous: the axis whose staircase measured the stronger deficiency.
        val axis = planned ?: severities.maxByOrNull { it.value }?.key
        var confidence = base
        val severity = axis?.let { severities[it] } ?: 0
        val measured = finished.firstOrNull { it.axis == axis }
        if (axis != null && severity < 10) confidence = lower(confidence) // the plates and the patches disagree
        if (measured != null && measured.threshold != null && measured.spread > 3) confidence = lower(confidence)
        if (!demoSeen) confidence = Confidence.Low
        return ScreeningResult(
            axis = axis,
            severity = severity,
            confidence = confidence,
            plateScores = plateScores(),
            demoSeen = demoSeen,
            arrangement = D15.score(placed),
            severities = severities
        )
    }

    companion object {
        /** A plate score at which an axis counts as failed: two subtle plates, or one strong. */
        const val FAIL = 2

        /**
         * From the plates and the arrangement: the axis (null: none found, or red-green yet to
         * be told apart), a first confidence, and the axes whose severity to measure.
         * - one axis failed, the arrangement agrees: high; the arrangement is normal (a milder
         *   form, which the D-15 lets through): medium; it points elsewhere: low.
         * - protan and deutan both failed (common: their confusion lines are close): the
         *   arrangement decides if it can, otherwise both severities are measured and the larger
         *   one wins.
         * - nothing failed and a normal arrangement: no deficiency found.
         */
        fun decide(scores: Map<Axis, Int>, arrangement: D15.Score): Triple<Axis?, Confidence, List<Axis>> {
            val failed = scores.filterValues { it >= FAIL }.keys
            val d15 = arrangement.axis
            return when {
                failed.isEmpty() && d15 == null -> Triple(null, Confidence.High, emptyList())

                failed.isEmpty() -> Triple(d15, Confidence.Low, listOf(d15!!))

                d15 != null && d15 in failed -> Triple(d15, Confidence.High, listOf(d15))

                failed.size == 1 -> {
                    val axis = failed.single()
                    Triple(axis, if (d15 == null) Confidence.Medium else Confidence.Low, listOf(axis))
                }

                Axis.Protan in failed && Axis.Deutan in failed &&
                    scores.getValue(Axis.Protan) == scores.getValue(Axis.Deutan) ->
                    Triple(null, Confidence.Medium, listOf(Axis.Protan, Axis.Deutan))

                else -> {
                    val axis = failed.maxBy { scores.getValue(it) }
                    Triple(axis, Confidence.Medium, listOf(axis))
                }
            }
        }

        private fun lower(c: Confidence) = when (c) {
            Confidence.High -> Confidence.Medium
            else -> Confidence.Low
        }
    }
}
