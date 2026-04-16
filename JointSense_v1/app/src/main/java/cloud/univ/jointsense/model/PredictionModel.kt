package cloud.univ.jointsense.model

import cloud.univ.jointsense.data.InflammationFactor

/**
 * Linear regression prediction model for inflammation factor concentration.
 *
 * The model uses 6 features (R, G, B channel mean and standard deviation)
 * that were selected through Lasso regression. Features are standardized
 * using z-score normalization before prediction.
 *
 * Prediction formula:
 *   y = bias + Σ(coefficient_i × standardized_feature_i)
 *
 * where standardized_feature_i = (feature_i - mean_i) / std_i
 *
 * NOTE: The coefficients below are PLACEHOLDER values.
 * Replace them with actual trained model parameters from the .tflite
 * or Python sklearn export for production use.
 */
object PredictionModel {

    // ============================================================
    // Standardization parameters from training data
    // (mean and std for each of the 6 features)
    // Order: [rMean, gMean, bMean, rStd, gStd, bStd]
    // ============================================================
    private val featureMeans = floatArrayOf(
        145.2f,  // Red channel mean - mean
        115.8f,  // Green channel mean - mean
        98.5f,   // Blue channel mean - mean
        28.3f,   // Red channel std - mean
        24.1f,   // Green channel std - mean
        19.7f    // Blue channel std - mean
    )

    private val featureStds = floatArrayOf(
        38.5f,   // Red channel mean - std
        32.4f,   // Green channel mean - std
        27.8f,   // Blue channel mean - std
        9.2f,    // Red channel std - std
        7.8f,    // Green channel std - std
        6.5f     // Blue channel std - std
    )

    // ============================================================
    // Linear regression coefficients for each inflammation factor
    // Order: [rMean, gMean, bMean, rStd, gStd, bStd]
    // ============================================================

    // IL-6 model coefficients
    private val il6Coefficients = floatArrayOf(
        2.15f, -1.83f, 0.92f, 0.67f, -0.45f, 0.31f
    )
    private val il6Bias = 45.0f

    // TNF-α model coefficients
    private val tnfAlphaCoefficients = floatArrayOf(
        -1.42f, 2.38f, -0.76f, 0.89f, 0.52f, -0.28f
    )
    private val tnfAlphaBias = 52.0f

    // IL-1β model coefficients
    private val il1BetaCoefficients = floatArrayOf(
        1.05f, 0.78f, -2.14f, -0.53f, 0.91f, 0.42f
    )
    private val il1BetaBias = 38.0f

    /**
     * Predict the concentration of the specified inflammation factor.
     *
     * @param features Array of 6 features: [rMean, gMean, bMean, rStd, gStd, bStd]
     * @param factor The inflammation factor to predict
     * @return Predicted concentration in pg/mL
     */
    fun predict(features: FloatArray, factor: InflammationFactor): Float {
        require(features.size == 6) {
            "Expected 6 features [rMean, gMean, bMean, rStd, gStd, bStd], got ${features.size}"
        }

        // Step 1: Standardize features using z-score normalization
        val standardized = FloatArray(6) { i ->
            if (featureStds[i] != 0f) {
                (features[i] - featureMeans[i]) / featureStds[i]
            } else {
                0f
            }
        }

        // Step 2: Get model parameters for the selected factor
        val (coefficients, bias) = when (factor) {
            InflammationFactor.IL6 -> il6Coefficients to il6Bias
            InflammationFactor.TNF_ALPHA -> tnfAlphaCoefficients to tnfAlphaBias
            InflammationFactor.IL1_BETA -> il1BetaCoefficients to il1BetaBias
        }

        // Step 3: Linear regression prediction
        // y = bias + Σ(coefficient_i × standardized_feature_i)
        var prediction = bias
        for (i in standardized.indices) {
            prediction += coefficients[i] * standardized[i]
        }

        // Concentration cannot be negative
        return prediction.coerceAtLeast(0f)
    }
}
