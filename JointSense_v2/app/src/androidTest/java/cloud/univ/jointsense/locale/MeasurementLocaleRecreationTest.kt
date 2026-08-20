package cloud.univ.jointsense.locale

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cloud.univ.jointsense.R
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.ColorSignalMethod
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.model.inflammationFactorPresentationOrder
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import cloud.univ.jointsense.measurement.BaselineAnalysisResult
import cloud.univ.jointsense.measurement.BaselinePhotoAnalysisAdapter
import cloud.univ.jointsense.measurement.CropBounds
import cloud.univ.jointsense.measurement.MeasurementAction
import cloud.univ.jointsense.measurement.MeasurementImage
import cloud.univ.jointsense.measurement.MeasurementImageDecoder
import cloud.univ.jointsense.measurement.MeasurementViewModel
import cloud.univ.jointsense.measurement.MeasurementViewModelFactory
import cloud.univ.jointsense.measurement.Stage
import cloud.univ.jointsense.navigation.AnalysisRoute
import cloud.univ.jointsense.navigation.CropRoute
import cloud.univ.jointsense.navigation.HomeRoute
import cloud.univ.jointsense.navigation.ImageSelectRoute
import cloud.univ.jointsense.navigation.JointSenseNavHost
import cloud.univ.jointsense.navigation.JointSenseRoute
import cloud.univ.jointsense.navigation.MAIN_NEW_TEST_TAG
import cloud.univ.jointsense.navigation.NavigationActions
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementLocaleRecreationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun resetBefore() {
        LocaleMeasurementHarness.reset()
        applyLocales("en")
    }

    @After
    fun resetAfter() = applyLocales("")

    @Test
    fun productionNavigationAndSessionDriverKeepRouteStateAndUseEventLocalePrefix() {
        ActivityScenario.launch(LocaleMeasurementHostActivity::class.java).use { scenario ->
            val initial = waitForStableActivity(scenario, language = "en")
            assertLocalizedConfiguration(initial, "en", "Test")
            composeRule.onNodeWithTag(LOCALE_ROUTE_HOME_TAG).assertIsDisplayed()

            composeRule.onNodeWithTag(MAIN_NEW_TEST_TAG).performClick()
            composeRule.onNodeWithTag(LOCALE_ROUTE_IMAGE_TAG).assertIsDisplayed()
            waitUntil { LocaleMeasurementHarness.repository.names.singleOrNull()?.startsWith("Test #") == true }

            val viewModel = initial.measurementViewModel
            waitUntil { viewModel.state.value.currentSession != null }
            val sessionId = requireNotNull(viewModel.state.value.currentSession?.id)
            viewModel.onAction(MeasurementAction.ImageSelected("content://measurement/locale-photo"))
            waitUntil { viewModel.state.value.image != null }
            viewModel.onAction(MeasurementAction.CropChanged(CropBounds(11, 12, 111, 112)))
            viewModel.onAction(MeasurementAction.CropConfirmed)
            composeRule.onNodeWithTag(LOCALE_OPEN_CROP_TAG).performClick()
            composeRule.onNodeWithTag(LOCALE_OPEN_ANALYSIS_TAG).performClick()
            composeRule.onNodeWithTag(LOCALE_ROUTE_ANALYSIS_TAG).assertIsDisplayed()
            val draftId = viewModel.state.value.draftId

            applyLocales("zh-CN")
            val chinese = waitForRecreated(scenario, initial, "zh")
            assertNotSame(initial, chinese)
            composeRule.onNodeWithTag(LOCALE_ROUTE_ANALYSIS_TAG).assertIsDisplayed()
            assertLocalizedConfiguration(chinese, "zh", "检测")
            with(chinese.measurementViewModel.state.value) {
                assertEquals("content://measurement/locale-photo", imageUri)
                assertEquals(CropBounds(11, 12, 111, 112), cropRect)
                assertEquals(Stage.ReadyToAnalyze, stage)
                assertEquals(draftId, this.draftId)
                assertEquals(sessionId, currentSession?.id)
                assertEquals("HOME", originDestination)
            }

            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                NavigationActions(chinese.navController).exitMeasurement()
            }
            composeRule.onNodeWithTag(LOCALE_ROUTE_HOME_TAG).assertIsDisplayed()
            composeRule.onNodeWithTag(MAIN_NEW_TEST_TAG).performClick()
            composeRule.onNodeWithTag(LOCALE_ROUTE_IMAGE_TAG).assertIsDisplayed()
            waitUntil { LocaleMeasurementHarness.repository.names.size == 2 }
            assertTrue(LocaleMeasurementHarness.repository.names.last().startsWith("检测 #"))

            applyLocales("en")
            val english = waitForRecreated(scenario, chinese, "en")
            composeRule.onNodeWithTag(LOCALE_ROUTE_IMAGE_TAG).assertIsDisplayed()
            assertLocalizedConfiguration(english, "en", "Test")
        }
    }

    private fun assertLocalizedConfiguration(
        activity: LocaleMeasurementHostActivity,
        language: String,
        expectedPrefix: String,
    ) {
        assertEquals(language, activity.resources.configuration.locales[0].language)
        assertEquals(expectedPrefix, activity.getString(R.string.session_name_prefix))
        composeRule.onNodeWithTag(LOCALE_PREFIX_TAG).assertTextEquals(expectedPrefix)
    }

    private fun waitForStableActivity(
        scenario: ActivityScenario<LocaleMeasurementHostActivity>,
        language: String,
    ): LocaleMeasurementHostActivity {
        var current = currentActivity(scenario)
        var consecutive = 0
        waitUntil {
            val observed = runCatching { currentActivity(scenario) }.getOrNull()
            if (observed === current && observed?.resources?.configuration?.locales?.get(0)?.language == language) {
                consecutive += 1
            } else {
                if (observed != null) current = observed
                consecutive = 0
            }
            consecutive >= 3
        }
        return current
    }

    private fun waitForRecreated(
        scenario: ActivityScenario<LocaleMeasurementHostActivity>,
        old: LocaleMeasurementHostActivity,
        language: String,
    ): LocaleMeasurementHostActivity {
        var current = old
        waitUntil {
            runCatching { currentActivity(scenario) }.getOrNull()?.let { current = it }
            current !== old && current.resources.configuration.locales[0].language == language
        }
        return waitForStableActivity(scenario, language)
    }

    private fun currentActivity(
        scenario: ActivityScenario<LocaleMeasurementHostActivity>,
    ): LocaleMeasurementHostActivity {
        lateinit var current: LocaleMeasurementHostActivity
        scenario.onActivity { current = it }
        return current
    }

    private fun waitUntil(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Timed out waiting for test state" }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            SystemClock.sleep(20)
        }
    }

    private fun applyLocales(tags: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}

class LocaleMeasurementHostActivity : AppCompatActivity() {
    lateinit var navController: NavHostController
        private set
    val measurementViewModel: MeasurementViewModel by viewModels {
        MeasurementViewModelFactory(
            repository = LocaleMeasurementHarness.repository,
            analyzer = LocaleMeasurementHarness.analyzer,
            decoder = object : MeasurementImageDecoder {
                override suspend fun decode(uri: String): MeasurementImage = LocaleMeasurementImage
            },
            draftIdFactory = { "locale-draft-${LocaleMeasurementHarness.drafts.incrementAndGet()}" },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val controller = rememberNavController()
            SideEffect { navController = controller }
            JointSenseTheme {
                JointSenseNavHost(
                    navController = controller,
                    measurementViewModel = measurementViewModel,
                    screenSlot = { route, actions ->
                        Column(Modifier.testTag(localeRouteTag(route))) {
                            Text(
                                text = stringResource(R.string.session_name_prefix),
                                modifier = Modifier.testTag(LOCALE_PREFIX_TAG),
                            )
                            when (route) {
                                ImageSelectRoute -> Button(
                                    onClick = actions::openCrop,
                                    modifier = Modifier.testTag(LOCALE_OPEN_CROP_TAG),
                                ) { Text("Crop") }
                                CropRoute -> Button(
                                    onClick = actions::openAnalysis,
                                    modifier = Modifier.testTag(LOCALE_OPEN_ANALYSIS_TAG),
                                ) { Text("Analyze") }
                                else -> Unit
                            }
                        }
                    },
                )
            }
        }
    }
}

private fun localeRouteTag(route: JointSenseRoute): String = when (route) {
    HomeRoute -> LOCALE_ROUTE_HOME_TAG
    ImageSelectRoute -> LOCALE_ROUTE_IMAGE_TAG
    CropRoute -> LOCALE_ROUTE_CROP_TAG
    AnalysisRoute -> LOCALE_ROUTE_ANALYSIS_TAG
    else -> "locale-route:${route::class.simpleName}"
}

private const val LOCALE_ROUTE_HOME_TAG = "locale-route:home"
private const val LOCALE_ROUTE_IMAGE_TAG = "locale-route:image"
private const val LOCALE_ROUTE_CROP_TAG = "locale-route:crop"
private const val LOCALE_ROUTE_ANALYSIS_TAG = "locale-route:analysis"
private const val LOCALE_OPEN_CROP_TAG = "locale-action:crop"
private const val LOCALE_OPEN_ANALYSIS_TAG = "locale-action:analysis"
private const val LOCALE_PREFIX_TAG = "locale-prefix"

private object LocaleMeasurementHarness {
    var repository = LocaleMeasurementRepository()
    val drafts = AtomicInteger()
    val analyzer = object : BaselinePhotoAnalysisAdapter {
        override suspend fun analyze(
            image: MeasurementImage,
            cropBounds: CropBounds,
        ): List<BaselineAnalysisResult> = inflammationFactorPresentationOrder.map { factor ->
            BaselineAnalysisResult(
                factor = factor,
                concentration = 1f,
                rangeStatus = RangeStatus.IN_RANGE,
                features = RgbFeatures(1f, 2f, 3f, 0f, 0f, 0f),
                rawSignal = 2f,
                signalMethod = ColorSignalMethod.PIXEL_BR_P90_V1,
            )
        }
    }

    fun reset() {
        repository = LocaleMeasurementRepository()
        drafts.set(0)
    }
}

private object LocaleMeasurementImage : MeasurementImage {
    override val width = 400
    override val height = 300
}

private class LocaleMeasurementRepository : TestSessionRepository {
    private val sessions = MutableStateFlow<List<TestSession>>(emptyList())
    val names = mutableListOf<String>()

    override fun observeSessions(): Flow<List<TestSession>> = sessions
    override fun observeSession(id: String): Flow<TestSession?> =
        sessions.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun createSession(name: String, source: DataSource): String {
        names += name
        val id = "locale-session-${names.size}"
        sessions.value += TestSession(id, name, names.size.toLong(), source, emptyList())
        return id
    }

    override suspend fun commitResult(
        sessionId: String,
        draftId: String,
        result: NewTestResult,
    ): String = "unused-result"

    override suspend fun commitMeasurement(
        sessionId: String,
        draftId: String,
        measurement: cloud.univ.jointsense.domain.model.NewMeasurementBatch,
    ): String = "unused-result"

    override suspend fun deleteSession(id: String) {
        sessions.value = sessions.value.filterNot { it.id == id }
    }
}
