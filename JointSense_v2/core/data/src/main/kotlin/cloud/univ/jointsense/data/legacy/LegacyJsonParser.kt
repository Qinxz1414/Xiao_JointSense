package cloud.univ.jointsense.data.legacy

import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationKnot
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.ColorSignalMethod
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

class LegacyParseException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class LegacyJsonParser(
    private val json: Json = Json,
) {
    fun parseSessions(payload: String): List<TestSession> = parsePayload("sessions") {
        json.parseToJsonElement(payload).requireArray("sessions").mapIndexed { sessionIndex, element ->
            val session = element.requireObject("sessions[$sessionIndex]")
            val sessionId = session.requireId("id", "sessions[$sessionIndex]")
            val results = session.requireArray("results", "sessions[$sessionIndex]").mapIndexed { resultIndex, resultElement ->
                val path = "sessions[$sessionIndex].results[$resultIndex]"
                val result = resultElement.requireObject(path)
                TestResult(
                    id = result.requireId("id", path),
                    sessionId = sessionId,
                    draftId = null,
                    factor = result.requireFactor("factor", path),
                    concentration = result.requireFiniteFloat("concentration", path),
                    rangeStatus = RangeStatus.UNKNOWN,
                    features = RgbFeatures(
                        rMean = result.requireFiniteFloat("rMean", path),
                        gMean = result.requireFiniteFloat("gMean", path),
                        bMean = result.requireFiniteFloat("bMean", path),
                        rStd = result.requireFiniteFloat("rStd", path),
                        gStd = result.requireFiniteFloat("gStd", path),
                        bStd = result.requireFiniteFloat("bStd", path),
                    ),
                    timestamp = result.requireLong("timestamp", path),
                )
            }
            TestSession(
                id = sessionId,
                name = session.requireString("name", "sessions[$sessionIndex]"),
                createdAt = session.requireLong("createdAt", "sessions[$sessionIndex]"),
                source = DataSource.USER,
                results = results,
            )
        }
    }

    fun parseCalibrations(payload: String): List<Calibration> = parsePayload("calibration") {
        val root = json.parseToJsonElement(payload).requireObject("calibration")
        val createdAt = root.requireLong("createdAt", "calibration")
        val factors = root.requireObject("factors", "calibration")
        factors.map { (factorName, knotsElement) ->
            val factor = factorName.toFactor("calibration.factors")
            val knots = knotsElement.requireArray("calibration.factors.$factorName").mapIndexed { position, knotElement ->
                val path = "calibration.factors.$factorName[$position]"
                val knot = knotElement.requireObject(path)
                val concentration = knot.requireFiniteFloat("c", path)
                val signal = knot.requireFiniteFloat("s", path)
                CalibrationKnot(
                    position = position,
                    concentration = concentration,
                    rawSignal = signal,
                    netSignal = signal,
                    fittedSignal = signal,
                    isBlank = concentration == 0f,
                )
            }
            Calibration(
                factor = factor,
                createdAt = createdAt,
                version = 1,
                status = CalibrationStatus.NEEDS_REVIEW,
                kitName = null,
                kitLot = null,
                knots = knots,
                signalMethod = ColorSignalMethod.LEGACY_MEAN_BR,
            )
        }
    }

    private inline fun <T> parsePayload(name: String, block: () -> T): T = try {
        block()
    } catch (exception: LegacyParseException) {
        throw exception
    } catch (exception: Exception) {
        throw LegacyParseException("Invalid legacy $name payload", exception)
    }
}

private fun JsonElement.requireArray(path: String): JsonArray =
    this as? JsonArray ?: throw LegacyParseException("$path must be an array")

private fun JsonElement.requireObject(path: String): JsonObject =
    this as? JsonObject ?: throw LegacyParseException("$path must be an object")

private fun JsonObject.requireArray(key: String, path: String): JsonArray =
    get(key)?.requireArray("$path.$key") ?: throw LegacyParseException("Missing $path.$key")

private fun JsonObject.requireObject(key: String, path: String): JsonObject =
    get(key)?.requireObject("$path.$key") ?: throw LegacyParseException("Missing $path.$key")

private fun JsonObject.requireId(key: String, path: String): String =
    requireString(key, path).also {
        if (it.isBlank()) throw LegacyParseException("$path.$key must not be blank")
    }

private fun JsonObject.requireString(key: String, path: String): String {
    val primitive = get(key) as? JsonPrimitive
        ?: throw LegacyParseException("Missing or invalid $path.$key")
    if (!primitive.isString) throw LegacyParseException("$path.$key must be a string")
    return primitive.content
}

private fun JsonObject.requireFactor(key: String, path: String): InflammationFactor =
    requireString(key, path).toFactor("$path.$key")

private fun String.toFactor(path: String): InflammationFactor = try {
    InflammationFactor.valueOf(this)
} catch (exception: IllegalArgumentException) {
    throw LegacyParseException("$path contains unknown factor '$this'", exception)
}

private fun JsonObject.requireLong(key: String, path: String): Long {
    val primitive = get(key) as? JsonPrimitive
        ?: throw LegacyParseException("Missing or invalid $path.$key")
    if (primitive.isString) throw LegacyParseException("$path.$key must be a number")
    return primitive.longOrNull ?: throw LegacyParseException("$path.$key must be an integer")
}

private fun JsonObject.requireFiniteFloat(key: String, path: String): Float {
    val primitive = get(key) as? JsonPrimitive
        ?: throw LegacyParseException("Missing or invalid $path.$key")
    if (primitive.isString) throw LegacyParseException("$path.$key must be a number")
    val double = primitive.doubleOrNull
        ?: throw LegacyParseException("$path.$key must be a number")
    val value = double.toFloat()
    if (!double.isFinite() || !value.isFinite()) {
        throw LegacyParseException("$path.$key must be finite")
    }
    return value
}
