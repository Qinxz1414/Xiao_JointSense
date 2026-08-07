package cloud.univ.jointsense.data

import cloud.univ.jointsense.model.StandardCurve

/**
 * Built-in quantified ELISA detection data — ALL sample plates.
 *
 * Every available sample is embedded so the dashboard, trend chart and AI
 * index open populated instead of empty:
 *
 *   • 测试用例.pptx — 9 measured test plates. Each slide carries the known
 *     TNF-α / IL-6 / IL-1β concentration (pg/mL) read from the plate; these
 *     are embedded verbatim as the ground-truth sample readings.
 *
 *   • 3 pasted clipboard plates (2026-08-06). Their wells were analyzed with
 *     the Rule/SKILL.md ELISA palette (projection well-detection + 90th-pct
 *     tealness), then their concentration was INTERPOLATED along the
 *     calibrated standard curve in [cloud.univ.jointsense.model.StandardCurve]
 *     — i.e. signal → pg/mL by interpolation, not a coarse level lookup.
 *
 * Concentration is the authoritative quantified field consumed by the
 * dashboard / AI index. RGB stats are representative values synthesised from
 * each well's tealness signal via [StandardCurve] (photo-derived, not
 * device-measured pixel stats) and are only there to keep the data model
 * complete.
 *
 * This legacy seed set is retained only for compatibility with old app data;
 * current startup seeding is owned by the Room data layer.
 */
object BuiltInData {

    // ---- Timestamps (epoch millis, GMT+8 capture → UTC stored) ----
    // 9 test-case plates spread over 2026-07-28 … 2026-08-05 (20:00 +08).
    private val TC_TS = longArrayOf(
        1_785_240_000_000L, 1_785_326_400_000L, 1_785_412_800_000L,
        1_785_499_200_000L, 1_785_585_600_000L, 1_785_672_000_000L,
        1_785_758_400_000L, 1_785_844_800_000L, 1_785_931_200_000L
    )
    // 3 pasted clipboard plates on 2026-08-06.
    private const val CLIP_1_TS = 1_786_015_500_000L // 19:25
    private const val CLIP_2_TS = 1_786_015_860_000L // 19:31
    private const val CLIP_3_TS = 1_786_029_600_000L // 23:20

    // 测试用例.pptx: per-slide concentrations (TNF-α, IL-6, IL-1β) in pg/mL.
    private val TC_CONC = arrayOf(
        floatArrayOf(0f, 0f, 0f),
        floatArrayOf(2f, 5f, 2f),
        floatArrayOf(5f, 10f, 5f),
        floatArrayOf(10f, 20f, 10f),
        floatArrayOf(20f, 50f, 20f),
        floatArrayOf(50f, 100f, 50f),
        floatArrayOf(100f, 200f, 100f),
        floatArrayOf(200f, 500f, 200f),
        floatArrayOf(500f, 1000f, 500f)
    )

    // 3 clipboard plates: measured 90th-pct tealness signal per factor
    // (TNF-α, IL-6, IL-1β). Concentration is interpolated from these via
    // StandardCurve so the embedded numbers are derived by the curve.
    private val CLIP_SIGNAL = arrayOf(
        floatArrayOf(-9f, -8f, -12f),   // Plate 1 — all below blank
        floatArrayOf(-5f, -17f, -1f),   // Plate 2 — IL-1β just above blank
        floatArrayOf(0f, -2f, 24f)      // Plate 3 — IL-1β strong, others mid
    )

    private fun rgbFor(factor: InflammationFactor, concentration: Float): FloatArray {
        val sig = StandardCurve.signalForConcentration(concentration, factor).coerceIn(-20f, 40f)
        val r = 150f
        val g = 146f
        val b = (r + sig).coerceIn(0f, 255f)
        return floatArrayOf(r, g, b, 8f, 6f, 6f) // rMean,gMean,bMean,rStd,gStd,bStd
    }

    private fun result(
        id: String,
        factor: InflammationFactor,
        concentration: Float,
        timestamp: Long
    ): TestResult {
        val rgb = rgbFor(factor, concentration)
        return TestResult(
            id = id,
            factor = factor,
            concentration = concentration,
            timestamp = timestamp,
            rMean = rgb[0], gMean = rgb[1], bMean = rgb[2],
            rStd = rgb[3], gStd = rgb[4], bStd = rgb[5]
        )
    }

    private fun factors() = listOf(
        InflammationFactor.TNF_ALPHA,
        InflammationFactor.IL6,
        InflammationFactor.IL1_BETA
    )

    private fun tcPlate(index: Int): TestSession {
        val ts = TC_TS[index]
        val conc = TC_CONC[index]
        val results = factors().mapIndexed { fi, f ->
            result("builtin-tc${index + 1}-${f.name.lowercase()}", f, conc[fi], ts)
        }
        return TestSession(
            id = "builtin-tc${index + 1}",
            name = "Test Plate ${index + 1} · 2026-${tcDate(index)}",
            createdAt = ts,
            results = results
        )
    }

    private fun tcDate(index: Int): String {
        // Maps index 0..8 → 07-28 … 08-05 for the display label only.
        val dates = listOf("07-28", "07-29", "07-30", "07-31", "08-01", "08-02", "08-03", "08-04", "08-05")
        return dates[index]
    }

    private fun clipPlate(id: String, name: String, ts: Long, signal: FloatArray): TestSession {
        val results = factors().mapIndexed { fi, f ->
            val conc = StandardCurve.concentrationFor(signal[fi], f)
            result("$id-${f.name.lowercase()}", f, conc, ts)
        }
        return TestSession(id = id, name = name, createdAt = ts, results = results)
    }

    val sessions: List<TestSession> = buildList {
        // 9 measured test-case plates (known concentrations).
        for (i in TC_CONC.indices) add(tcPlate(i))
        // 3 pasted clipboard plates (interpolated via standard curve).
        add(clipPlate("builtin-clip-1", "Clipboard Plate 1 · 2026-08-06 19:25", CLIP_1_TS, CLIP_SIGNAL[0]))
        add(clipPlate("builtin-clip-2", "Clipboard Plate 2 · 2026-08-06 19:31", CLIP_2_TS, CLIP_SIGNAL[1]))
        add(clipPlate("builtin-clip-3", "Clipboard Plate 3 · 2026-08-06 23:20", CLIP_3_TS, CLIP_SIGNAL[2]))
    }
}
