package cloud.univ.jointsense.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import cloud.univ.jointsense.data.InflammationFactor
import cloud.univ.jointsense.data.TestRepository
import cloud.univ.jointsense.data.TestResult
import cloud.univ.jointsense.data.TestSession
import cloud.univ.jointsense.model.FeatureExtractor
import cloud.univ.jointsense.model.PredictionModel

/**
 * Screen navigation states for the app.
 */
enum class Screen {
    HOME,
    IMAGE_SELECT,
    IMAGE_CROP,
    FACTOR_SELECT,
    RESULT,
    HISTORY
}

/**
 * Main ViewModel for the JointSense application.
 * Manages navigation state, test sessions, image processing, and predictions.
 */
class JointSenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TestRepository(application)

    // Navigation state
    var currentScreen by mutableStateOf(Screen.HOME)
        private set

    // All test sessions
    var sessions by mutableStateOf(listOf<TestSession>())
        private set

    // Current active test session
    var currentSession by mutableStateOf<TestSession?>(null)
        private set

    // Selected image for analysis
    var selectedBitmap by mutableStateOf<Bitmap?>(null)
        private set

    // Crop rectangle (in image coordinates)
    var cropRect by mutableStateOf(Rect(0, 0, 200, 200))
        private set

    // Selected inflammation factor for prediction
    var selectedFactor by mutableStateOf(InflammationFactor.IL6)
        private set

    // Last prediction result
    var lastResult by mutableStateOf<TestResult?>(null)
        private set

    // Analysis state
    var isAnalyzing by mutableStateOf(false)
        private set

    // Extracted features (for display)
    var lastFeatures by mutableStateOf<FeatureExtractor.Features?>(null)
        private set

    init {
        sessions = repository.loadSessions()
    }

    // ========================
    // Navigation
    // ========================

    fun navigateTo(screen: Screen) {
        currentScreen = screen
    }

    fun goHome() {
        selectedBitmap = null
        lastResult = null
        lastFeatures = null
        currentSession = null
        currentScreen = Screen.HOME
    }

    // ========================
    // Session Management
    // ========================

    fun createNewSession() {
        val sessionNumber = sessions.size + 1
        val session = TestSession(name = "Test #$sessionNumber")
        currentSession = session
        sessions = sessions + session
        repository.saveSessions(sessions)
        currentScreen = Screen.IMAGE_SELECT
    }

    fun selectSession(session: TestSession) {
        currentSession = session
        currentScreen = Screen.RESULT
    }

    fun deleteSession(session: TestSession) {
        sessions = sessions.filter { it.id != session.id }
        repository.saveSessions(sessions)
        if (currentSession?.id == session.id) {
            currentSession = null
        }
    }

    // ========================
    // Image Handling
    // ========================

    fun setImage(bitmap: Bitmap) {
        selectedBitmap = bitmap
        // Set initial crop rect to center 50% of image
        val w = bitmap.width
        val h = bitmap.height
        cropRect = Rect(w / 4, h / 4, 3 * w / 4, 3 * h / 4)
        currentScreen = Screen.IMAGE_CROP
    }

    fun updateCropRect(rect: Rect) {
        cropRect = rect
    }

    fun confirmCrop() {
        currentScreen = Screen.FACTOR_SELECT
    }

    // ========================
    // Factor Selection
    // ========================

    fun selectFactor(factor: InflammationFactor) {
        selectedFactor = factor
    }

    // ========================
    // Analysis
    // ========================

    fun analyze() {
        val bitmap = selectedBitmap ?: return
        val rect = cropRect
        isAnalyzing = true

        try {
            // Step 1: Extract features from the selected region
            val features = FeatureExtractor.extract(
                bitmap,
                rect.left,
                rect.top,
                rect.width(),
                rect.height()
            )
            lastFeatures = features

            // Step 2: Predict concentration using linear regression model
            val concentration = PredictionModel.predict(
                features.toFloatArray(),
                selectedFactor
            )

            // Step 3: Create test result
            val result = TestResult(
                factor = selectedFactor,
                concentration = concentration,
                rMean = features.rMean,
                gMean = features.gMean,
                bMean = features.bMean,
                rStd = features.rStd,
                gStd = features.gStd,
                bStd = features.bStd
            )
            lastResult = result

            // Step 4: Add result to current session (max 5 results per session)
            currentSession?.let { session ->
                if (session.results.size < 5) {
                    val updatedSession = session.copy(
                        results = session.results + result
                    )
                    currentSession = updatedSession
                    sessions = sessions.map {
                        if (it.id == session.id) updatedSession else it
                    }
                    repository.saveSessions(sessions)
                }
            }

            currentScreen = Screen.RESULT
        } finally {
            isAnalyzing = false
        }
    }

    // ========================
    // Continue Testing
    // ========================

    fun startNewTestInSession() {
        selectedBitmap = null
        lastResult = null
        lastFeatures = null
        currentScreen = Screen.IMAGE_SELECT
    }

    /**
     * Check if current session can accept more tests (max 5).
     */
    fun canAddMoreTests(): Boolean {
        return (currentSession?.results?.size ?: 0) < 5
    }
}
