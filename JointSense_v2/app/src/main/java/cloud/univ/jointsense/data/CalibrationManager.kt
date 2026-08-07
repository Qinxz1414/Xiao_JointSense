package cloud.univ.jointsense.data

import android.content.Context
import android.content.SharedPreferences
import cloud.univ.jointsense.model.Calibration
import cloud.univ.jointsense.model.CalibrationKnot
import cloud.univ.jointsense.model.StandardCurve
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists user-calibrated standard curves and wires them into [StandardCurve].
 *
 * On [init] it loads any saved calibration and applies it so the legacy
 * analysis path uses the user's curve instead of the factory knots.
 * Calibrating one factor updates
 * only that factor; the others keep their factory values until calibrated.
 */
object CalibrationManager {

    private const val PREFS_NAME = "joint_sense_calibration"
    private const val KEY_CALIBRATION = "calibration"

    private var appContext: Context? = null
    private var current: Calibration? = null

    /** Load persisted calibration and activate it. Call once at startup. */
    fun init(context: Context) {
        appContext = context.applicationContext
        current = load()
        applyToCurve()
    }

    fun hasUserCalibration(): Boolean = current != null

    fun currentCalibration(): Calibration? = current

    fun factorCount(): Int = current?.factors?.size ?: 0

    /** Merge a freshly measured factor curve into the stored calibration. */
    fun saveFactor(factor: InflammationFactor, knots: List<Pair<Float, Float>>) {
        val merged = current?.factors?.toMutableMap() ?: mutableMapOf()
        merged[factor] = knots.map { (c, s) -> CalibrationKnot(c, s) }
        current = Calibration(merged, System.currentTimeMillis())
        persist(current)
        applyToCurve()
    }

    /** Drop the user calibration and revert to factory knots. */
    fun clear() {
        current = null
        appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove(KEY_CALIBRATION)
            ?.apply()
        StandardCurve.resetKnots()
    }

    private fun applyToCurve() {
        val cal = current
        if (cal != null) StandardCurve.setKnots(cal) else StandardCurve.resetKnots()
    }

    private fun load(): Calibration? {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return null
        val json = prefs.getString(KEY_CALIBRATION, null) ?: return null
        return try {
            val obj = JSONObject(json)
            val createdAt = obj.optLong("createdAt", 0L)
            val factorsObj = obj.getJSONObject("factors")
            val factors = mutableMapOf<InflammationFactor, List<CalibrationKnot>>()
            factorsObj.keys().forEach { key ->
                val factor = InflammationFactor.valueOf(key)
                val arr = factorsObj.getJSONArray(key)
                val knots = mutableListOf<CalibrationKnot>()
                for (i in 0 until arr.length()) {
                    val k = arr.getJSONObject(i)
                    knots.add(CalibrationKnot(k.getDouble("c").toFloat(), k.getDouble("s").toFloat()))
                }
                factors[factor] = knots
            }
            Calibration(factors, createdAt)
        } catch (e: Exception) {
            null
        }
    }

    private fun persist(cal: Calibration?) {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        if (cal == null) {
            prefs.edit().remove(KEY_CALIBRATION).apply()
            return
        }
        val obj = JSONObject().apply {
            put("createdAt", cal.createdAt)
            val factorsObj = JSONObject()
            cal.factors.forEach { (factor, knots) ->
                val arr = JSONArray()
                knots.forEach { k ->
                    arr.put(
                        JSONObject().apply {
                            put("c", k.conc.toDouble())
                            put("s", k.signal.toDouble())
                        }
                    )
                }
                factorsObj.put(factor.name, arr)
            }
            put("factors", factorsObj)
        }
        prefs.edit().putString(KEY_CALIBRATION, obj.toString()).apply()
    }
}
