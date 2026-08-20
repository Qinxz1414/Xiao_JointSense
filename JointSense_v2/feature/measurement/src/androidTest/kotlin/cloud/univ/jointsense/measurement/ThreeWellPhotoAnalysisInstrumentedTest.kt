package cloud.univ.jointsense.measurement

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreeWellPhotoAnalysisInstrumentedTest {
    private val analyzer = AndroidBaselinePhotoAnalysisAdapter(EmptyCalibrationRepository)

    @Test
    fun horizontalThreeWellRowKeepsPhysicalFactorOrderAndSamplesEachWellIndependently() =
        runBlocking {
            val image = syntheticThreeWellImage(background = Color.rgb(245, 225, 15))
            try {
                val results = analyzer.analyze(image, CROP)

                assertEquals(
                    listOf(
                        InflammationFactor.TNF_ALPHA,
                        InflammationFactor.IL6,
                        InflammationFactor.IL1_BETA,
                    ),
                    results.map { it.factor },
                )
                assertPureColor(results[0], LEFT_COLOR)
                assertPureColor(results[1], CENTER_COLOR)
                assertPureColor(results[2], RIGHT_COLOR)
            } finally {
                image.release()
            }
        }

    @Test
    fun changingPixelsOutsideTheThreeSamplingEllipsesDoesNotChangeFeatures() = runBlocking {
        val darkBackground = syntheticThreeWellImage(background = Color.rgb(1, 2, 3))
        val brightBackground = syntheticThreeWellImage(background = Color.rgb(250, 240, 230))
        try {
            val darkResults = analyzer.analyze(darkBackground, CROP)
            val brightResults = analyzer.analyze(brightBackground, CROP)

            assertEquals(3, darkResults.size)
            assertEquals(3, brightResults.size)
            darkResults.zip(brightResults).forEach { (dark, bright) ->
                assertEquals(dark.factor, bright.factor)
                assertEquals(dark.features.rMean, bright.features.rMean, FLOAT_TOLERANCE)
                assertEquals(dark.features.gMean, bright.features.gMean, FLOAT_TOLERANCE)
                assertEquals(dark.features.bMean, bright.features.bMean, FLOAT_TOLERANCE)
                assertEquals(dark.features.tealness, bright.features.tealness, FLOAT_TOLERANCE)
                assertEquals(dark.rawSignal, bright.rawSignal, FLOAT_TOLERANCE)
            }
        } finally {
            darkBackground.release()
            brightBackground.release()
        }
    }

    private fun syntheticThreeWellImage(background: Int): BitmapMeasurementImage {
        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val paint = Paint().apply { isAntiAlias = false }
        listOf(
            Triple(80f, 80f, LEFT_COLOR),
            Triple(200f, 80f, CENTER_COLOR),
            Triple(320f, 80f, RIGHT_COLOR),
        ).forEach { (centerX, centerY, color) ->
            paint.color = color
            canvas.drawCircle(centerX, centerY, WELL_RADIUS, paint)
        }
        return BitmapMeasurementImage(bitmap)
    }

    private fun assertPureColor(result: BaselineAnalysisResult, expectedColor: Int) {
        val features = result.features
        val expectedRed = Color.red(expectedColor).toFloat()
        val expectedGreen = Color.green(expectedColor).toFloat()
        val expectedBlue = Color.blue(expectedColor).toFloat()
        assertEquals(expectedRed, features.rMean, FLOAT_TOLERANCE)
        assertEquals(expectedGreen, features.gMean, FLOAT_TOLERANCE)
        assertEquals(expectedBlue, features.bMean, FLOAT_TOLERANCE)
        assertEquals(expectedBlue - expectedRed, features.tealness, FLOAT_TOLERANCE)
        assertEquals(expectedBlue - expectedRed, result.rawSignal, FLOAT_TOLERANCE)
        assertEquals(0f, features.rStd, FLOAT_TOLERANCE)
        assertEquals(0f, features.gStd, FLOAT_TOLERANCE)
        assertEquals(0f, features.bStd, FLOAT_TOLERANCE)
    }

    private object EmptyCalibrationRepository : CalibrationRepository {
        override fun observeCalibrations(): Flow<List<Calibration>> = flowOf(emptyList())

        override fun observeCalibration(factor: InflammationFactor): Flow<Calibration?> =
            flowOf(null)

        override suspend fun save(calibration: Calibration) = Unit

        override suspend fun clearAll() = Unit
    }

    private companion object {
        const val IMAGE_WIDTH = 400
        const val IMAGE_HEIGHT = 160
        val CROP = CropBounds(left = 20, top = 20, right = 380, bottom = 140)
        const val WELL_RADIUS = 45f
        val LEFT_COLOR = Color.rgb(10, 20, 70)
        val CENTER_COLOR = Color.rgb(30, 80, 130)
        val RIGHT_COLOR = Color.rgb(50, 110, 200)
        const val FLOAT_TOLERANCE = 0.001f
    }
}
