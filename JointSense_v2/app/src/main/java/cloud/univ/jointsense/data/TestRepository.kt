package cloud.univ.jointsense.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class TestRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("joint_sense_data", Context.MODE_PRIVATE)

    fun saveSessions(sessions: List<TestSession>) {
        val jsonArray = JSONArray()
        for (session in sessions) {
            val sessionJson = JSONObject().apply {
                put("id", session.id)
                put("name", session.name)
                put("createdAt", session.createdAt)
                val resultsArray = JSONArray()
                for (result in session.results) {
                    resultsArray.put(JSONObject().apply {
                        put("id", result.id)
                        put("factor", result.factor.name)
                        put("concentration", result.concentration.toDouble())
                        put("timestamp", result.timestamp)
                        put("rMean", result.rMean.toDouble())
                        put("gMean", result.gMean.toDouble())
                        put("bMean", result.bMean.toDouble())
                        put("rStd", result.rStd.toDouble())
                        put("gStd", result.gStd.toDouble())
                        put("bStd", result.bStd.toDouble())
                    })
                }
                put("results", resultsArray)
            }
            jsonArray.put(sessionJson)
        }
        prefs.edit().putString("sessions", jsonArray.toString()).apply()
    }

    fun loadSessions(): List<TestSession> {
        val json = prefs.getString("sessions", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            val sessions = mutableListOf<TestSession>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val resultsArray = obj.getJSONArray("results")
                val results = mutableListOf<TestResult>()
                for (j in 0 until resultsArray.length()) {
                    val r = resultsArray.getJSONObject(j)
                    results.add(
                        TestResult(
                            id = r.getString("id"),
                            factor = InflammationFactor.valueOf(r.getString("factor")),
                            concentration = r.getDouble("concentration").toFloat(),
                            timestamp = r.getLong("timestamp"),
                            rMean = r.getDouble("rMean").toFloat(),
                            gMean = r.getDouble("gMean").toFloat(),
                            bMean = r.getDouble("bMean").toFloat(),
                            rStd = r.getDouble("rStd").toFloat(),
                            gStd = r.getDouble("gStd").toFloat(),
                            bStd = r.getDouble("bStd").toFloat()
                        )
                    )
                }
                sessions.add(
                    TestSession(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        createdAt = obj.getLong("createdAt"),
                        results = results
                    )
                )
            }
            sessions
        } catch (e: Exception) {
            emptyList()
        }
    }
}
