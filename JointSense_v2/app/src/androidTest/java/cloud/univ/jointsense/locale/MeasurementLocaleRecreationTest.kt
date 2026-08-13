package cloud.univ.jointsense.locale

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cloud.univ.jointsense.R
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import cloud.univ.jointsense.measurement.BaselineAnalysisResult
import cloud.univ.jointsense.measurement.BaselinePhotoAnalysisAdapter
import cloud.univ.jointsense.measurement.CropBounds
import cloud.univ.jointsense.measurement.FactorSelectRouteScreen
import cloud.univ.jointsense.measurement.ImageSelectRouteScreen
import cloud.univ.jointsense.measurement.MeasurementAction
import cloud.univ.jointsense.measurement.MeasurementImage
import cloud.univ.jointsense.measurement.MeasurementImageDecoder
import cloud.univ.jointsense.measurement.MeasurementViewModel
import cloud.univ.jointsense.measurement.MeasurementViewModelFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementLocaleRecreationTest {
    @Before
    fun resetBefore() {
        LocaleMeasurementHarness.reset()
        applyLocales("")
    }

    @After
    fun resetAfter() = applyLocales("")

    @Test
    fun localeRecreationKeepsMeasurementRouteAndFormalStateAndUsesEventLocalePrefix() {
        ActivityScenario.launch(LocaleMeasurementHostActivity::class.java).use { scenario ->
            val initial = currentActivity(scenario)
            val viewModel = initial.measurementViewModel
            viewModel.createNewSession("REPORT", "Test")
            waitUntil { viewModel.state.value.sessionCreationRequest?.completedSessionId != null }
            val request = requireNotNull(viewModel.state.value.sessionCreationRequest)
            val sessionId = requireNotNull(viewModel.acceptSessionCreation(request.requestId))
            viewModel.onAction(MeasurementAction.ImageSelected("content://measurement/locale-photo"))
            waitUntil { viewModel.state.value.image != null }
            viewModel.onAction(MeasurementAction.CropChanged(CropBounds(11, 12, 111, 112)))
            viewModel.onAction(MeasurementAction.CropConfirmed)
            viewModel.onAction(MeasurementAction.FactorSelected(InflammationFactor.IL1_BETA))
            InstrumentationRegistry.getInstrumentation().runOnMainSync(initial::showFactorRoute)
            assertEquals("factor", initial.routeIdentity)
            val draftId = viewModel.state.value.draftId

            applyLocales("zh-CN")
            val recreated = waitForRecreated(scenario, initial)
            waitUntil {
                recreated.routeIdentity == "factor" &&
                    recreated.measurementViewModel.state.value.currentSession?.id == sessionId
            }
            assertNotSame(initial, recreated)
            assertEquals("factor", recreated.routeIdentity)
            with(recreated.measurementViewModel.state.value) {
                assertEquals("content://measurement/locale-photo", imageUri)
                assertEquals(CropBounds(11, 12, 111, 112), cropRect)
                assertEquals(InflammationFactor.IL1_BETA, factor)
                assertEquals(draftId, this.draftId)
                assertEquals(sessionId, currentSession?.id)
                assertEquals("REPORT", originDestination)
            }

            val prefix = recreated.getString(R.string.session_name_prefix)
            assertEquals("检测", prefix)
            recreated.measurementViewModel.createNewSession("REPORT", prefix)
            waitUntil { recreated.measurementViewModel.state.value.sessionCreationRequest?.completedSessionId != null }
            assertTrue(LocaleMeasurementHarness.repository.names.last().startsWith("检测 #"))
        }
    }

    private fun waitForRecreated(
        scenario: ActivityScenario<LocaleMeasurementHostActivity>,
        old: LocaleMeasurementHostActivity,
    ): LocaleMeasurementHostActivity {
        var current = old
        waitUntil {
            runCatching { currentActivity(scenario) }.getOrNull()?.let { current = it }
            current !== old
        }
        return current
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
    var routeIdentity by androidx.compose.runtime.mutableStateOf("image")
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
        routeIdentity = savedInstanceState?.getString(KEY_ROUTE) ?: "image"
        enableEdgeToEdge()
        setContent {
            JointSenseTheme {
                if (routeIdentity == "factor") {
                    FactorSelectRouteScreen(
                        viewModel = measurementViewModel,
                        onResultReady = {},
                        onBack = {},
                    )
                } else {
                    ImageSelectRouteScreen(
                        viewModel = measurementViewModel,
                        onImageReady = {},
                        onBack = {},
                    )
                }
            }
        }
    }

    fun showFactorRoute() {
        routeIdentity = "factor"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_ROUTE, routeIdentity)
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val KEY_ROUTE = "locale.test.measurement.route"
    }
}

private object LocaleMeasurementHarness {
    var repository = LocaleMeasurementRepository()
    val drafts = AtomicInteger()
    val analyzer = object : BaselinePhotoAnalysisAdapter {
        override suspend fun analyze(
            image: MeasurementImage,
            cropBounds: CropBounds,
            factor: InflammationFactor,
        ) = BaselineAnalysisResult(
                concentration = 1f,
                rangeStatus = RangeStatus.IN_RANGE,
                features = RgbFeatures(1f, 2f, 3f, 0f, 0f, 0f),
            )
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

    override suspend fun deleteSession(id: String) {
        sessions.value = sessions.value.filterNot { it.id == id }
    }
}
