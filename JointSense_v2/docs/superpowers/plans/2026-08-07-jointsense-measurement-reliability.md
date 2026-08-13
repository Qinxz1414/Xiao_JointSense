# JointSense Measurement Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make quantification, image handling, measurement, calibration, reporting, and Back/error behavior deterministic, asynchronous, recoverable, and regression-tested on the Phase 1 architecture.

**Architecture:** Pure Kotlin analysis classes return typed values and range status; feature-scoped ViewModels expose lifecycle-aware StateFlows and perform single-flight work through injected dispatchers. URIs and SavedStateHandle retain drafts, while Room commits are idempotent by draft ID. Calibration applies explicit scientific validation before writing an active curve.

**Tech Stack:** Phase 1 stack plus Android Photo Picker, Activity Result APIs, PdfDocument, SavedStateHandle, kotlinx-coroutines-test 1.10.2, AndroidX Compose UI tests.

## Global Constraints

- Complete and verify `2026-08-07-jointsense-architecture-foundation.md` first.
- Preserve `tealness = B - R`, OA weights `TNF_ALPHA 0.40 / IL6 0.35 / IL1_BETA 0.25`, and grade thresholds `[0,.25,.50,.75,.90,1]`.
- Quantification below the first signal returns `0 + BELOW_RANGE`; above the last signal returns the last knot concentration + `ABOVE_RANGE`; never extrapolate or jump to OA caps.
- Calibration requires exactly nine finite readings, exactly one 0-concentration Blank, unique non-Blank concentrations, net dynamic range at least 8.0, and monotonic fitted signals.
- Maximum PAVA adjustment is `max(3.0, rawDynamicRange * 0.15)` tealness units.
- Do not convert invalid numeric input to zero.
- Result Back returns to the originating top-level route; Result never navigates back to factor selection.
- About and exported reports contain the fixed disclaimer; the on-screen Result does not.
- Shared Android image decoding belongs to a new `:core:image` Android library. `:feature:measurement` and `:feature:calibration` may depend on it; neither feature may depend on the other.
- Typed route declarations and `NavGraphBuilder` registration remain app-owned. Task 6 is authorized to modify the named app navigation files and tests while calibration state/UI stays feature-owned.
- When an accepted isotonic curve contains a fitted-signal plateau, inverse quantification is right-continuous: an exact plateau signal returns the highest concentration in the complete equal-signal plateau.
- Stage only files named by each task.

---

### Task 1: Correct standard-curve interpolation and isolate OA analysis

**Files:**
- Create: `core/analysis/src/main/kotlin/cloud/univ/jointsense/analysis/CurveKnot.kt`
- Create: `core/analysis/src/main/kotlin/cloud/univ/jointsense/analysis/QuantificationResult.kt`
- Create: `core/analysis/src/main/kotlin/cloud/univ/jointsense/analysis/StandardCurve.kt`
- Create: `core/analysis/src/main/kotlin/cloud/univ/jointsense/analysis/FactoryCurves.kt`
- Create: `core/analysis/src/main/kotlin/cloud/univ/jointsense/analysis/OaIndexCalculator.kt`
- Create: `core/analysis/src/test/kotlin/cloud/univ/jointsense/analysis/StandardCurveTest.kt`
- Create: `core/analysis/src/test/kotlin/cloud/univ/jointsense/analysis/OaIndexCalculatorTest.kt`
- Delete after replacement: `app/src/main/java/cloud/univ/jointsense/model/OaIndex.kt`

**Interfaces:**
- Consumes: `InflammationFactor`, `RangeStatus`, `TestResult` from `:core:domain`.
- Produces: `StandardCurve.quantify(signal): QuantificationResult`; immutable `FactoryCurves.forFactor(factor)` using the existing app's exact approved knots; `OaIndexCalculator.calculate(latest): Float?`; `OaIndexCalculator.grade(ai): Int`.

- [x] **Step 1: Write interpolation regression tests that fail on the current implementation**

```kotlin
private val curve = StandardCurve(listOf(CurveKnot(0f, -8f), CurveKnot(20f, -4f)))

@Test fun midpointUsesSignalAsInputAndConcentrationAsOutput() {
    assertEquals(10f, curve.quantify(-6f).concentration, 0.001f)
    assertEquals(RangeStatus.IN_RANGE, curve.quantify(-6f).rangeStatus)
}

@Test fun outOfRangeValuesClampWithoutUsingOaCaps() {
    assertEquals(QuantificationResult(0f, RangeStatus.BELOW_RANGE), curve.quantify(-9f))
    assertEquals(QuantificationResult(20f, RangeStatus.ABOVE_RANGE), curve.quantify(-3f))
}

@Test fun exactPlateauSignalUsesRightContinuousUpperConcentration() {
    val plateau = StandardCurve(listOf(
        CurveKnot(0f, -8f), CurveKnot(10f, -4f),
        CurveKnot(20f, -4f), CurveKnot(30f, 0f),
    ))
    assertEquals(20f, plateau.quantify(-4f).concentration, 0.001f)
}
```

- [x] **Step 2: Run tests and confirm RED**

Run: `.\gradlew.bat :core:analysis:test --tests "*StandardCurveTest"`

Expected: new analysis types are unresolved; when adapted temporarily to the old implementation, midpoint is not 10.

- [x] **Step 3: Implement correct interpolation with constructor validation**

```kotlin
class StandardCurve(knots: List<CurveKnot>) {
    private val knots = knots.sortedBy(CurveKnot::signal).also {
        require(it.size >= 2)
        require(it.zipWithNext().all { (a, b) -> b.signal >= a.signal && b.concentration > a.concentration })
    }

    fun quantify(signal: Float): QuantificationResult {
        require(signal.isFinite())
        if (signal < knots.first().signal) return QuantificationResult(0f, RangeStatus.BELOW_RANGE)
        if (signal > knots.last().signal) return QuantificationResult(knots.last().concentration, RangeStatus.ABOVE_RANGE)
        val upper = knots.indexOfFirst { signal <= it.signal }.coerceAtLeast(1)
        val lower = upper - 1
        val a = knots[lower]
        val b = knots[upper]
        val concentration = if (b.signal == a.signal) b.concentration else
            a.concentration + (b.concentration - a.concentration) *
                (signal - a.signal) / (b.signal - a.signal)
        return QuantificationResult(concentration, RangeStatus.IN_RANGE)
    }
}
```

Handle an exact first-knot signal as `IN_RANGE` with that knot's concentration; add a dedicated branch before computing `upper` to avoid coercing it into segment 0→1.

For an exact signal shared by two or more consecutive knots, scan the complete equal-signal plateau and return its highest concentration. This right-continuous rule governs PAVA plateaus and must be covered independently from the first-knot branch.

Move the current factory knot constants from the legacy `StandardCurve` into immutable `FactoryCurves`; add a round-trip test for every knot (`quantify(knot.signal) == knot.concentration`) so the migration changes no scientific constants.

The compile-only legacy `StandardCurve` cannot be deleted in this pure-analysis task because the isolated legacy `BuiltInData` and `CalibrationManager` sources still reference its mutable/inverse-only API. Do not add new callers or build a compatibility facade. Task 6 removes that complete unused cluster after the Room-backed calibration flow is rebuilt.

- [x] **Step 4: Add OA threshold and missing-factor tests**

Test exact grades at 0, 0.25, 0.50, 0.75, 0.90, 1.0 and verify weights renormalize over present factors. Return a grade number only; move all Compose color mapping to `:core:designsystem`.

- [x] **Step 5: Run analysis tests and confirm GREEN**

Run: `.\gradlew.bat :core:analysis:test`

Expected: all interpolation and OA tests pass.

- [x] **Step 6: Commit analysis corrections**

```powershell
git add core/analysis app/src/main/java/cloud/univ/jointsense/model/OaIndex.kt
git commit -m "fix: correct inflammation quantification"
```

---

### Task 2: Validate calibration and fit monotonic photo signals

**Files:**
- Create: `core/analysis/src/main/kotlin/cloud/univ/jointsense/analysis/calibration/CalibrationInput.kt`
- Create: `core/analysis/src/main/kotlin/cloud/univ/jointsense/analysis/calibration/CalibrationError.kt`
- Create: `core/analysis/src/main/kotlin/cloud/univ/jointsense/analysis/calibration/CalibrationValidator.kt`
- Create: `core/analysis/src/main/kotlin/cloud/univ/jointsense/analysis/calibration/IsotonicRegression.kt`
- Create: `core/analysis/src/test/kotlin/cloud/univ/jointsense/analysis/calibration/CalibrationValidatorTest.kt`

**Interfaces:**
- Consumes: nine `CalibrationInput(wellIndex, concentration, rawSignal)` records.
- Produces: `CalibrationValidation.Valid(knots)` or `.Invalid(errors)`; each valid knot includes raw, net, and fitted signals.

- [x] **Step 1: Write failing validation tests**

```kotlin
@Test fun rejectsMissingBlankInsteadOfUsingFirstWell() {
    val result = validator.validate(inputs(concentrations = (1..9).map(Int::toFloat)))
    assertTrue((result as CalibrationValidation.Invalid).errors.contains(CalibrationError.MissingBlank))
}

@Test fun rejectsTextParseFailureInsteadOfTurningItIntoBlank() {
    assertEquals(ConcentrationParseResult.Invalid, parseConcentration("abc"))
}

@Test fun rejectsInsufficientDynamicRange() {
    val result = validator.validate(inputs(signals = List(9) { it * 0.5f }))
    assertTrue((result as CalibrationValidation.Invalid).errors.contains(CalibrationError.DynamicRangeTooLow))
}
```

- [x] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :core:analysis:test --tests "*CalibrationValidatorTest"`

Expected: validator types do not exist.

- [x] **Step 3: Implement exact structural validation**

Reject any list not size 9; any non-finite/negative concentration; blank count not 1; duplicate non-Blank concentration; non-finite signal; or net range below 8.0.

- [x] **Step 4: Implement PAVA and correction tolerance**

```kotlin
val fitted = IsotonicRegression.fit(sortedByConcentration.map { it.netSignal })
val tolerance = maxOf(3f, rawDynamicRange * 0.15f)
if (fitted.indices.any { abs(fitted[it] - sortedByConcentration[it].netSignal) > tolerance }) {
    return CalibrationValidation.Invalid(listOf(CalibrationError.NonMonotonicBeyondTolerance))
}
```

PAVA pools adjacent blocks while the left mean exceeds the right mean and expands pooled means back to original positions.

The isolated legacy `Calibration` model remains compile-referenced by the legacy `CalibrationManager`/`CalibrationDetector` cluster. Do not migrate those callers or add a compatibility facade in this pure-analysis task; Task 6 removes the complete unused legacy calibration cluster after its Room-backed replacement is wired.

- [x] **Step 5: Run validator tests and confirm GREEN**

Run: `.\gradlew.bat :core:analysis:test --tests "*CalibrationValidatorTest"`

Expected: missing Blank, invalid text, range, PAVA acceptance, and excessive-adjustment cases pass.

- [x] **Step 6: Commit calibration science guards**

```powershell
git add core/analysis
git commit -m "fix: validate standard curve calibration"
```

---

### Task 3: Add sampled, orientation-aware image decoding

**Files:**
- Create: `core/image/build.gradle.kts`
- Create: `core/image/src/main/AndroidManifest.xml`
- Create: `core/image/src/main/kotlin/cloud/univ/jointsense/image/ImageDecodePolicy.kt`
- Create: `core/image/src/main/kotlin/cloud/univ/jointsense/image/SampledBitmapDecoder.kt`
- Create: `core/image/src/main/kotlin/cloud/univ/jointsense/image/DecodedImage.kt`
- Create: `core/image/src/main/kotlin/cloud/univ/jointsense/image/ImageDecodeError.kt`
- Create: `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/image/MeasurementTempFileStore.kt`
- Create: `core/image/src/test/kotlin/cloud/univ/jointsense/image/ImageDecodePolicyTest.kt`
- Create: `core/image/src/androidTest/kotlin/cloud/univ/jointsense/image/SampledBitmapDecoderTest.kt`
- Create: `feature/measurement/src/test/kotlin/cloud/univ/jointsense/measurement/image/MeasurementTempFileStoreTest.kt`
- Modify: `settings.gradle.kts`
- Modify: `feature/measurement/build.gradle.kts`

**Interfaces:**
- Consumes: `ContentResolver`, source URI, `Dispatchers.IO`.
- Produces: shared `:core:image` API `suspend fun decode(uri: Uri, maxEdge: Int = 2048): DecodedImage`; pure `calculateInSampleSize(width, height, maxEdge)`; a measurement-owned temp-file store that owns camera URIs until success or explicit cancellation.

- [x] **Step 1: Write failing sample-size tests**

```kotlin
@Test fun downsamplesLargeCameraImageToBoundedMemory() {
    assertEquals(2, calculateInSampleSize(4032, 3024, 2048))
    assertEquals(1, calculateInSampleSize(1280, 720, 2048))
}
```

- [x] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :core:image:testDebugUnitTest --tests "*ImageDecodePolicyTest"`

Expected: function is unresolved.

- [x] **Step 3: Implement two-pass decoding and EXIF rotation**

Register `:core:image`, apply `jointsense.android.library`, and add `implementation(libs.androidx.exifinterface)` there. `:feature:measurement` depends on `:core:image`; Task 6 adds the same dependency to calibration. Open the URI once for bounds and once for sampled decode. Use `BitmapFactory.Options.inJustDecodeBounds`, computed power-of-two `inSampleSize`, and `ExifInterface` to rotate 90/180/270 degrees. Throw typed `ImageDecodeError.Unsupported`, `.Unreadable`, or `.OutOfMemory` rather than returning null.

`MeasurementTempFileStore` creates files only under `context.cacheDir/measurement`, restores an existing pending URI from `SavedStateHandle`, deletes only files it owns, and clears a temp file after successful persistence or explicit flow cancellation. Its unit test verifies that retry/recreation retains the pending file while success/cancel removes it.

- [x] **Step 4: Verify policy and instrumentation decoding**

Run:

```powershell
.\gradlew.bat :core:image:testDebugUnitTest --tests "*ImageDecodePolicyTest" :feature:measurement:testDebugUnitTest --tests "*MeasurementTempFileStoreTest"
.\gradlew.bat :core:image:compileDebugAndroidTestSources :feature:measurement:compileDebugKotlin
```

Expected: policy tests pass and Android decoder tests compile; run connected test when a device is available.

- [x] **Step 5: Commit image decoding**

```powershell
git add settings.gradle.kts core/image feature/measurement/build.gradle.kts feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/image feature/measurement/src/test/kotlin/cloud/univ/jointsense/measurement/image
git commit -m "feat: decode measurement images safely"
```

---

### Task 4: Implement the measurement StateFlow and idempotent submission

**Files:**
- Create: `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/MeasurementUiState.kt`
- Create: `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/MeasurementAction.kt`
- Modify: `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/MeasurementViewModel.kt`
- Modify: `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/MeasurementViewModelFactory.kt`
- Create: `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/SessionNameGenerator.kt`
- Create: `feature/measurement/src/test/kotlin/cloud/univ/jointsense/measurement/MeasurementViewModelTest.kt`
- Create: `feature/measurement/src/test/kotlin/cloud/univ/jointsense/measurement/SessionNameGeneratorTest.kt`

**Interfaces:**
- Consumes: shared decoder from `:core:image` Task 3, analysis from Task 1, `TestSessionRepository`, SavedStateHandle, injected IO/Default dispatchers.
- Produces: `StateFlow<MeasurementUiState>`; `onAction(MeasurementAction)`; one-shot `MeasurementEffect.NavigateToResult(resultId)`.

- [x] **Step 1: Write failing state-order and double-submit tests**

```kotlin
@Test fun analyzeEmitsAnalyzingPersistingSuccess() = runTest {
    viewModel.onAction(MeasurementAction.Analyze)
    advanceUntilIdle()
    assertEquals(listOf(Stage.Analyzing, Stage.Persisting, Stage.Success), recordedTerminalStages)
}

@Test fun secondAnalyzeDuringFlightCommitsSameDraftOnlyOnce() = runTest {
    viewModel.onAction(MeasurementAction.Analyze)
    viewModel.onAction(MeasurementAction.Analyze)
    advanceUntilIdle()
    assertEquals(1, repository.commitCalls)
}

@Test fun continueMeasurementCreatesANewDraftId() = runTest {
    val committedDraft = viewModel.state.value.draftId
    val next = factory.create(SavedStateHandle(mapOf("origin" to "HOME")))
    assertNotEquals(committedDraft, next.state.value.draftId)
}
```

- [x] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :feature:measurement:testDebugUnitTest --tests "*MeasurementViewModelTest"`

Expected: state/action/ViewModel types do not exist.

- [x] **Step 3: Implement explicit state and recovery data**

```kotlin
enum class Stage {
    AwaitingImage, Decoding, ReadyToCrop, ReadyToAnalyze,
    Analyzing, Persisting, Success, RecoverableError,
}

sealed interface MeasurementError {
    data class PermissionDenied(val permanentlyDenied: Boolean) : MeasurementError
    data object ImageUnreadable : MeasurementError
    data object UnsupportedImage : MeasurementError
    data object ImageTooLarge : MeasurementError
    data object InvalidCrop : MeasurementError
    data object AnalysisFailed : MeasurementError
    data object PersistenceFailed : MeasurementError
}

data class MeasurementUiState(
    val stage: Stage = Stage.AwaitingImage,
    val draftId: String,
    val imageUri: String? = null,
    val cropRect: CropRect? = null,
    val factor: InflammationFactor = InflammationFactor.IL6,
    val error: MeasurementError? = null,
    val resumeStage: Stage? = null,
    val resultId: String? = null,
)
```

Persist URI, crop, factor, origin, and UUID draft ID in SavedStateHandle. Use a `Mutex.tryLock()` or active Job guard to make Analyze single-flight.

- [x] **Step 4: Implement dispatcher boundaries and error recovery**

Decode/file work uses injected IO; feature extraction/interpolation uses injected Default; repository calls are suspend. A failure sets `stage=RecoverableError`, records the prior valid stage in `resumeStage`, and preserves URI/crop/factor/draft ID. `Retry` resumes from `resumeStage`; a persistence retry reuses the same draft ID and cannot show Success before the repository returns.

Generate a display name from the highest numeric suffix for the active locale prefix, not `sessions.size + 1`:

```kotlin
fun nextSessionName(existingNames: List<String>, prefix: String): String {
    val pattern = Regex("^${Regex.escape(prefix)} #(\\d+)$")
    val next = existingNames.mapNotNull { pattern.matchEntire(it)?.groupValues?.get(1)?.toLongOrNull() }
        .maxOrNull()?.plus(1) ?: 1L
    return "$prefix #$next"
}
```

Test `Test #1, Test #3 → Test #4` so deleting a middle session cannot reuse an existing name.

- [x] **Step 5: Run ViewModel tests and confirm GREEN**

Run: `.\gradlew.bat :feature:measurement:testDebugUnitTest --tests "*MeasurementViewModelTest"`

Expected: state order, cancellation, retry, and idempotency tests pass.

- [x] **Step 6: Commit the measurement state machine**

```powershell
git add feature/measurement
git commit -m "feat: add recoverable measurement state machine"
```

---

### Task 5: Bind camera, Photo Picker, crop, and Back UI to the state machine

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/cloud/univ/jointsense/navigation/JointSenseNavHost.kt`
- Modify: `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/MeasurementScreens.kt`
- Modify: `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/MeasurementEntry.kt`
- Modify: `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/crop/ImageCropView.kt`
- Create: `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/MeasurementErrorContent.kt`
- Create: `feature/measurement/src/androidTest/kotlin/cloud/univ/jointsense/measurement/MeasurementFlowTest.kt`

**Interfaces:**
- Consumes: Task 4 StateFlow/actions; typed navigation from Plan 1.
- Produces: visible progress/errors; Photo Picker; permission recovery; correct per-step Back.

The app composition root must construct `SampledBitmapDecoder` from the application `ContentResolver` and use the decoder-aware `MeasurementViewModelFactory` overload. Add a direct app dependency on `:core:image`; the legacy two-argument factory intentionally has no decoder and is not valid for the formal URI flow.

- [x] **Step 1: Write failing Compose flow tests**

Test that Analyze shows a progress indicator and disables itself; Retry preserves factor/crop; Back from Crop returns ImageSelect; Result Back invokes `onReturnToOrigin`; “Continue measurement” returns to the origin and opens a fresh `MeasurementGraph` whose ViewModel creates a new draft ID.

- [x] **Step 2: Compile tests and confirm RED**

Run: `.\gradlew.bat :feature:measurement:compileDebugAndroidTestSources`

Expected: new semantics/test tags are missing.

- [x] **Step 3: Replace GetContent with Photo Picker and persist camera URI**

Use `rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia())` with `ImageOnly`. Camera creates a temp URI before requesting launch and sends it to `MeasurementAction.ImageSelected`. Permission denial emits a visible explanation; permanent denial exposes an “Open settings” action.

- [x] **Step 4: Render every state and error explicitly**

Add stable test tags `measurement_progress`, `measurement_error`, `retry_button`, and `analyze_button`. No catch block may contain only `printStackTrace()` or return silently.

- [x] **Step 5: Verify UI compilation and device tests**

Run:

```powershell
.\gradlew.bat :feature:measurement:compileDebugKotlin :feature:measurement:compileDebugAndroidTestSources :app:compileDebugKotlin
.\gradlew.bat :feature:measurement:connectedDebugAndroidTest
```

Expected: compilation exits 0; connected flow tests pass when a device exists.

- [x] **Step 6: Commit measurement UI integration**

```powershell
git add app/build.gradle.kts app/src/main/java/cloud/univ/jointsense/navigation/JointSenseNavHost.kt feature/measurement
git commit -m "fix: make measurement flow recoverable"
```

---

### Task 6: Rebuild calibration as a graph-scoped state machine

**Files:**
- Modify: `docs/superpowers/plans/2026-08-07-jointsense-measurement-reliability.md`
- Create: `feature/calibration/src/main/kotlin/cloud/univ/jointsense/calibration/CalibrationUiState.kt`
- Create: `feature/calibration/src/main/kotlin/cloud/univ/jointsense/calibration/CalibrationViewModel.kt`
- Create: `feature/calibration/src/main/kotlin/cloud/univ/jointsense/calibration/CalibrationViewModelFactory.kt`
- Create: `feature/calibration/src/main/kotlin/cloud/univ/jointsense/calibration/GridSignalDetector.kt`
- Create: `feature/calibration/src/main/kotlin/cloud/univ/jointsense/calibration/LegacyCalibrationRevalidator.kt`
- Modify: `feature/calibration/src/main/kotlin/cloud/univ/jointsense/calibration/CalibrationScreens.kt`
- Modify: `feature/calibration/src/main/kotlin/cloud/univ/jointsense/calibration/CalibrationEntry.kt`
- Modify: `feature/calibration/src/main/kotlin/cloud/univ/jointsense/calibration/CalibrationCropView.kt`
- Modify: `feature/calibration/build.gradle.kts`
- Modify: `feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/SettingsViewModel.kt`
- Modify: `feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/ProfileScreen.kt`
- Modify: `app/src/main/java/cloud/univ/jointsense/navigation/JointSenseNavHost.kt`
- Modify: `app/src/main/java/cloud/univ/jointsense/navigation/NavigationActions.kt`
- Modify: `app/src/main/java/cloud/univ/jointsense/di/AppContainer.kt`
- Modify: `app/src/androidTest/java/cloud/univ/jointsense/navigation/JointSenseNavigationTest.kt`
- Modify: `app/src/test/java/cloud/univ/jointsense/ModuleBoundarySmokeTest.kt`
- Create: `feature/calibration/src/test/kotlin/cloud/univ/jointsense/calibration/CalibrationViewModelTest.kt`
- Create: `feature/calibration/src/test/kotlin/cloud/univ/jointsense/calibration/GridSignalDetectorTest.kt`
- Create: `feature/calibration/src/test/kotlin/cloud/univ/jointsense/calibration/LegacyCalibrationRevalidatorTest.kt`
- Create: `feature/calibration/src/androidTest/kotlin/cloud/univ/jointsense/calibration/CalibrationFlowTest.kt`
- Modify: `feature/settings/src/test/kotlin/cloud/univ/jointsense/settings/SettingsViewModelTest.kt`
- Delete after replacement: `app/src/main/java/cloud/univ/jointsense/data/CalibrationManager.kt`
- Delete after replacement: `app/src/main/java/cloud/univ/jointsense/data/BuiltInData.kt`
- Delete after replacement: `app/src/main/java/cloud/univ/jointsense/model/Calibration.kt`
- Delete after replacement: `app/src/main/java/cloud/univ/jointsense/model/StandardCurve.kt`
- Delete after replacement: `app/src/main/java/cloud/univ/jointsense/model/CalibrationDetector.kt`

**Interfaces:**
- Consumes: sampled decoder from `:core:image`, moved grid-signal algorithm, Task 2 validator, CalibrationRepository, app-owned typed calibration routes.
- Produces: SavedStateHandle-backed calibration state; validated `CalibrationStatus.ACTIVE` writes; one-shot review of Phase 1 `NEEDS_REVIEW` records.

- [x] **Step 1: Write failing ViewModel tests**

Verify Factor change reloads the correct factory ladder; invalid text remains a field error; missing Blank blocks Review; excessive PAVA adjustment blocks Save; valid Save writes raw/fitted knots once.

- [x] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :feature:calibration:testDebugUnitTest --tests "*CalibrationViewModelTest"`

Expected: graph-scoped ViewModel types do not exist.

- [x] **Step 3: Implement state, parsing, validation, and persistence**

Store concentration field text separately from parsed values. Review calls `CalibrationValidator.validate`; Save is enabled only for `CalibrationValidation.Valid`. Saving writes one factor without deleting active curves for other factors.

Move the existing 3×3 detector math into `GridSignalDetector` and feed it only sampled/orientation-corrected images. `LegacyCalibrationRevalidator` processes each `NEEDS_REVIEW` record once: promote it to `ACTIVE` only when the same nine-reading validator passes; otherwise retain it as `NEEDS_REVIEW` and expose that state in Settings.

- [x] **Step 4: Implement stepwise Back and restore semantics**

Replace the current five destinations that all render the same internal flow with real route-specific feature entries. `JointSenseNavHost` owns mapping between `CalibrationSelectRoute`, `CalibrationCropRoute`, `CalibrationAssignRoute`, `CalibrationReviewRoute`, `CalibrationDoneRoute` and feature callbacks; the graph-scoped ViewModel/state survives those destinations without feature→app imports. Top app bar and system/predictive Back both pop exactly one calibration route. Done exits the calibration graph. “Restore factory curve” requires confirmation and clears all user calibration through `CalibrationRepository.clearAll()`.

- [x] **Step 5: Run calibration tests**

Run:

```powershell
.\gradlew.bat :feature:calibration:testDebugUnitTest
.\gradlew.bat :feature:calibration:compileDebugAndroidTestSources
.\gradlew.bat :feature:calibration:connectedDebugAndroidTest
```

Expected: unit tests pass; connected tests pass with a device.

- [x] **Step 6: Commit calibration rebuild**

```powershell
git add docs/superpowers/plans/2026-08-07-jointsense-measurement-reliability.md feature/calibration feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/SettingsViewModel.kt feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/ProfileScreen.kt feature/settings/src/test/kotlin/cloud/univ/jointsense/settings/SettingsViewModelTest.kt app/src/main/java/cloud/univ/jointsense/navigation/JointSenseNavHost.kt app/src/main/java/cloud/univ/jointsense/navigation/NavigationActions.kt app/src/main/java/cloud/univ/jointsense/di/AppContainer.kt app/src/androidTest/java/cloud/univ/jointsense/navigation/JointSenseNavigationTest.kt app/src/test/java/cloud/univ/jointsense/ModuleBoundarySmokeTest.kt app/src/main/java/cloud/univ/jointsense/data/CalibrationManager.kt app/src/main/java/cloud/univ/jointsense/data/BuiltInData.kt app/src/main/java/cloud/univ/jointsense/model/Calibration.kt app/src/main/java/cloud/univ/jointsense/model/StandardCurve.kt app/src/main/java/cloud/univ/jointsense/model/CalibrationDetector.kt
git commit -m "fix: validate and persist calibration safely"
```

---

### Task 7: Make report generation structured, localized, and wrap-safe

**Files:**
- Create: `core/domain/src/main/kotlin/cloud/univ/jointsense/domain/report/ReportModel.kt`
- Create: `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/report/LocalizedReportFormatter.kt`
- Create: `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/report/PdfReportExporter.kt`
- Create: `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/report/TextLayout.kt`
- Modify: `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/ReportScreen.kt`
- Create/modify: `feature/insights/src/main/res/values/strings.xml`
- Create: `feature/insights/src/main/res/values-zh-rCN/strings.xml`
- Create: `feature/insights/src/test/kotlin/cloud/univ/jointsense/insights/report/TextLayoutTest.kt`
- Create: `feature/insights/src/test/kotlin/cloud/univ/jointsense/insights/report/LocalizedReportFormatterTest.kt`
- Create: `feature/insights/src/androidTest/kotlin/cloud/univ/jointsense/insights/report/PdfReportExporterTest.kt`
- Delete after replacement: `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/BaselineReportExporter.kt`

**Interfaces:**
- Consumes: localized Resources/Locale and `ReportModel`.
- Produces: shared localized text/PDF content; wrap-safe `layoutLines(text, paint, maxWidth)`; fixed disclaimer.

- [x] **Step 1: Write failing wrap and disclaimer tests**

```kotlin
@Test fun wrapsLongChineseAndEnglishWithoutDroppingCharacters() {
    val lines = layoutLines(input, fakeMeasurer(maxChars = 12), maxWidth = 100f)
    assertEquals(input.replace("\n", ""), lines.joinToString(""))
    assertTrue(lines.all { it.length <= 12 })
}
```

Assert both locale formatters include the exact approved disclaimer and screen Result formatter does not.

Add formatter tests for `Locale.US` and `Locale.SIMPLIFIED_CHINESE`: dates use the locale's medium date/time format, concentrations and OA index use locale-aware decimal separators, percentages use `NumberFormat.getPercentInstance(locale)`, and the stable scientific unit remains `pg/mL` in both languages.

- [x] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :feature:insights:testDebugUnitTest --tests "*TextLayoutTest"`

Expected: report model/layout types do not exist.

- [x] **Step 3: Implement shared report model and line layout**

Use `Paint.breakText` for PDF line splitting, preserve explicit paragraphs, repeat title/header on new pages, and close `PdfDocument`/FileOutputStream with `use` or `try/finally`. Add the exact approved English and Chinese disclaimer resources now; Plan 3's resource-parity pass keeps these keys unchanged and adds the remaining screen translations.

- [x] **Step 4: Implement share failure feedback**

Catch `ActivityNotFoundException` and file errors in the real `ReportScreen` share actions, map them to localized user-visible report errors, and never leave a Success state without an existing non-empty file. The earlier architecture phase already removed `app/src/main/java/cloud/univ/jointsense/data/ReportExporter.kt`; replace the currently invoked feature-local `BaselineReportExporter` instead.

- [x] **Step 5: Run report verification**

Run:

```powershell
.\gradlew.bat :feature:insights:testDebugUnitTest
.\gradlew.bat :feature:insights:compileDebugAndroidTestSources
```

Expected: wrapping and formatter tests pass; PDF instrumentation compiles.

- [x] **Step 6: Commit report reliability**

```powershell
git add core/domain feature/insights
git commit -m "fix: export complete localized reports"
```

---

### Task 8: Verify Phase 2 regressions and update documentation

**Files:**
- Modify: `项目结构需求梳理.md`
- Modify: `docs/superpowers/plans/2026-08-07-jointsense-measurement-reliability.md`

**Interfaces:**
- Consumes: all Phase 2 tasks.
- Produces: checked plan, exact fixed-bug list, fresh command evidence.

- [x] **Step 1: Run full regression verification**

```powershell
.\gradlew.bat :core:analysis:test :core:image:testDebugUnitTest :feature:measurement:testDebugUnitTest :feature:calibration:testDebugUnitTest :feature:insights:testDebugUnitTest
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:lintDebug :app:assembleDebug
```

Run `connectedDebugAndroidTest` when a device is available.

- [x] **Step 2: Audit removed failure patterns**

```powershell
rg -n "printStackTrace\(\)|toFloatOrNull\(\) \?: 0|coerceAtLeast\(0\).*blank|FlowScreen|MainTab" app core feature
```

Expected: no silent error patterns or manual navigation enums remain.

- [x] **Step 3: Update the handoff document with actual results**

Record each fixed defect, curve behavior, calibration thresholds, image limit, report behavior, and exact verification commands/output. Mark device tests unavailable if they were not executed.

- [ ] **Step 4: Commit Phase 2 documentation**

```powershell
git add 项目结构需求梳理.md docs/superpowers/plans/2026-08-07-jointsense-measurement-reliability.md
git commit -m "docs: record measurement reliability results"
```

#### Task 8 evidence — updated 2026-08-13

- The exact failure-pattern audit command in Step 2 returned ripgrep exit `1` with zero output lines: no `printStackTrace()`, invalid-number-to-zero fallback, Blank coercion, `FlowScreen`, or `MainTab` match exists under `app`, `core`, or `feature`. A second source-only run excluding `**/build/**` produced the same zero-match result.
- Step 1 was rerun after execution access recovered. The focused Phase 2 command completed 85/85 actionable tasks; `testDebugUnitTest --rerun-tasks` completed 206/206 actionable tasks with 237/237 XML tests and zero failures/errors/skips; `:app:lintDebug :app:assembleDebug --rerun-tasks` completed 374/374 actionable tasks.
- Task 7 base commit `8f34c28` plus reviewed lifecycle/collision/timestamp fixes are complete. The final fixes passed `:feature:insights:testDebugUnitTest --rerun-tasks` (14/14; 38/38 actionable), compiled Android test sources, built the app in a 211/211 actionable command, and were committed as `3c8445c`.
- No Phase 2 connected-device suite has a passing result. Tasks 3–5 had no attached device; Task 6 reached a transient `M2012K11AC` but timed out/cancelled after partial instrumentation; Task 7 had no stable authorized device/emulator run.
- Step 4 remains unchecked until these final documentation edits are committed. Generated build directories remain untracked and out of scope.
