package cloud.univ.jointsense.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import cloud.univ.jointsense.data.BuiltInData
import cloud.univ.jointsense.data.CalibrationManager
import cloud.univ.jointsense.data.InflammationFactor
import cloud.univ.jointsense.data.TestRepository
import cloud.univ.jointsense.data.TestResult
import cloud.univ.jointsense.data.TestSession
import cloud.univ.jointsense.model.FeatureExtractor
import cloud.univ.jointsense.model.OaIndex
import cloud.univ.jointsense.model.StandardCurve
import cloud.univ.jointsense.ui.components.TimePoint
import kotlin.math.abs

/**
 * Main bottom-navigation tabs (design: Home / Trends / Test / Report /
 * Profile). The center Test tab is an action, not a page.
 */
enum class MainTab {
    HOME,
    TRENDS,
    REPORT,
    PROFILE
}

/**
 * Full-screen flow pages shown above the tab scaffold while a test is
 * in progress (or when browsing history).
 */
enum class FlowScreen {
    IMAGE_SELECT,
    IMAGE_CROP,
    FACTOR_SELECT,
    RESULT,
    HISTORY,
    CALIBRATION
}

enum class EventKind { TEST, UP, DOWN }

data class KeyEventItem(
    val time: Long,
    val kind: EventKind,
    val text: String
)

/**
 * Main ViewModel for the JointSense application.
 * Manages tab + flow navigation, test sessions, image processing,
 * predictions and the derived statistics that feed the dashboard,
 * trends and AI report screens.
 */
class JointSenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TestRepository(application)

    // Navigation state
    var activeTab by mutableStateOf(MainTab.HOME)
        private set

    var flowScreen by mutableStateOf<FlowScreen?>(null)
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
        // Load and activate any user-calibrated standard curve so the live
        // analysis path uses it instead of the factory knots.
        CalibrationManager.init(application)

        val loaded = repository.loadSessions()
        sessions = if (loaded.isEmpty()) {
            // First launch (or after "clear all"): populate the app with
            // the built-in quantified ELISA detection data so the dashboard
            // is not empty. User-created data always takes precedence.
            repository.saveSessions(BuiltInData.sessions)
            BuiltInData.sessions
        } else {
            loaded
        }
    }

    // ========================
    // Tab navigation
    // ========================

    fun selectTab(tab: MainTab) {
        activeTab = tab
        flowScreen = null
    }

    /** Leave the flow and return to whichever tab was active. */
    fun exitFlow() {
        // Drop result-less sessions created by an abandoned test run
        currentSession?.let { session ->
            if (session.results.isEmpty()) {
                sessions = sessions.filter { it.id != session.id }
                repository.saveSessions(sessions)
            }
        }
        selectedBitmap = null
        lastResult = null
        lastFeatures = null
        currentSession = null
        flowScreen = null
    }

    fun goHome() {
        exitFlow()
        activeTab = MainTab.HOME
    }

    fun navigateToFlow(screen: FlowScreen) {
        flowScreen = screen
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
        flowScreen = FlowScreen.IMAGE_SELECT
    }

    fun selectSession(session: TestSession) {
        currentSession = session
        flowScreen = FlowScreen.RESULT
    }

    fun deleteSession(session: TestSession) {
        sessions = sessions.filter { it.id != session.id }
        repository.saveSessions(sessions)
        if (currentSession?.id == session.id) {
            currentSession = null
        }
    }

    fun clearAllData() {
        sessions = emptyList()
        currentSession = null
        lastResult = null
        lastFeatures = null
        selectedBitmap = null
        repository.saveSessions(sessions)
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
        flowScreen = FlowScreen.IMAGE_CROP
    }

    fun updateCropRect(rect: Rect) {
        cropRect = rect
    }

    fun confirmCrop() {
        flowScreen = FlowScreen.FACTOR_SELECT
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

            // Step 2: Quantify concentration by interpolating the well's
            // tealness signal (B − R) along the calibrated standard curve —
            // proper ELISA "插补" instead of the placeholder linear model.
            val tealness = features.bMean - features.rMean
            val concentration = StandardCurve.concentrationFor(tealness, selectedFactor)

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

            flowScreen = FlowScreen.RESULT
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
        flowScreen = FlowScreen.IMAGE_SELECT
    }

    /**
     * Check if current session can accept more tests (max 5).
     */
    fun canAddMoreTests(): Boolean {
        return (currentSession?.results?.size ?: 0) < 5
    }

    // ========================
    // Derived statistics
    // ========================

    /** Every stored result, chronologically. */
    val allResults: List<TestResult>
        get() = sessions.flatMap { it.results }.sortedBy { it.timestamp }

    /** Whether a user-calibrated curve (vs the factory knots) is active. */
    val hasUserCalibration: Boolean
        get() = CalibrationManager.hasUserCalibration()

    /** How many factors have a user calibration. */
    val calibrationFactorCount: Int
        get() = CalibrationManager.factorCount()

    /** Chronological concentration series for one factor. */
    fun factorSeries(factor: InflammationFactor): List<TimePoint> =
        allResults
            .filter { it.factor == factor }
            .map { TimePoint(it.timestamp, it.concentration) }

    /** Latest value per factor across the whole history. */
    val latestValues: Map<InflammationFactor, Float>
        get() = OaIndex.latestPerFactor(allResults)

    /** Current composite AI (latest value per factor), null if no data. */
    val currentAi: Float?
        get() = OaIndex.aiFromResults(allResults)

    /** Current grade 0..4, null if no data. */
    val currentGrade: Int?
        get() = currentAi?.let { OaIndex.grade(it) }

    /** Chronological per-session AI series. */
    val aiSeriesAll: List<Pair<Long, Float>>
        get() = OaIndex.aiSeries(sessions)

    fun aiSeriesSince(sinceMillis: Long): List<Pair<Long, Float>> =
        aiSeriesAll.filter { it.first >= sinceMillis }

    /**
     * Percent change of a factor's mean concentration in the last 7
     * days vs the previous 7 days; null when either window is empty.
     */
    fun factorDeltaPct7d(factor: InflammationFactor): Float? {
        val now = System.currentTimeMillis()
        val recent = allResults.filter {
            it.factor == factor && it.timestamp > now - DAY_MILLIS * 7
        }
        val previous = allResults.filter {
            it.factor == factor &&
                it.timestamp <= now - DAY_MILLIS * 7 &&
                it.timestamp > now - DAY_MILLIS * 14
        }
        if (recent.isEmpty() || previous.isEmpty()) return null
        val a = recent.map { it.concentration.toDouble() }.average()
        val b = previous.map { it.concentration.toDouble() }.average()
        if (b <= 0.0) return null
        return ((a - b) / b * 100.0).toFloat()
    }

    /** Percent change of the AI vs one week ago (or the first point). */
    val aiWeekDeltaPct: Float?
        get() {
            val series = aiSeriesAll
            if (series.size < 2) return null
            val now = System.currentTimeMillis()
            val latest = series.last().second
            val baseline = series.lastOrNull { it.first <= now - DAY_MILLIS * 7 }?.second
                ?: series.first().second
            if (baseline <= 0f) return null
            return ((latest - baseline) / baseline * 100f)
        }

    /** Recent key events for the Trends screen (newest first). */
    val keyEvents: List<KeyEventItem>
        get() {
            val events = mutableListOf<KeyEventItem>()
            val series = aiSeriesAll
            val aiByLastTimestamp = series.associate { it.first to it.second }

            for (session in sessions) {
                if (session.results.isEmpty()) continue
                val t = session.results.maxOf { it.timestamp }
                val ai = aiByLastTimestamp[t]
                events.add(
                    KeyEventItem(
                        time = t,
                        kind = EventKind.TEST,
                        text = "Test completed - ${session.results.size} measurement(s)" +
                            (ai?.let { ", AI %.2f".format(it) } ?: "")
                    )
                )
            }

            for (i in 1 until series.size) {
                val (t, ai) = series[i]
                val prev = series[i - 1].second
                val delta = ai - prev
                if (abs(delta) >= 0.03f) {
                    events.add(
                        KeyEventItem(
                            time = t,
                            kind = if (delta > 0) EventKind.UP else EventKind.DOWN,
                            text = "AI index ${if (delta > 0) "rose" else "dropped"} " +
                                "from %.2f to %.2f".format(prev, ai)
                        )
                    )
                }
            }

            return events.sortedByDescending { it.time }.take(10)
        }

    companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
