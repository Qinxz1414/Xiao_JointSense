# JointSense Architecture Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate JointSense from a single-module, manual-state application to a buildable multi-module foundation with Room, type-safe Navigation Compose, coroutine/Flow repositories, lossless legacy migration, and Chinese/English application locales.

**Architecture:** `:app` is the composition root; four feature modules consume repository and analysis contracts from pure Kotlin core modules. Room is the only runtime data source, while a one-shot coordinator imports the existing SharedPreferences JSON. A single typed NavHost replaces `FlowScreen`/`MainTab`, and locale selection uses AppCompat application locales.

**Tech Stack:** Kotlin 2.2.10, AGP 9.3.1, Java 11, Jetpack Compose BOM 2026.02.01, Navigation Compose 2.9.8, Room 2.8.4, AppCompat 1.7.1, Activity 1.12.3, Lifecycle 2.9.2, Core 1.18.0, kotlinx-coroutines 1.10.2, KSP 2.3.8, JUnit 4.13.2.

## Global Constraints

- Keep `minSdk = 24`, `targetSdk = 36`, and `compileSdk = 36.1`.
- Keep package/application ID `cloud.univ.jointsense` and Java source/target compatibility 11.
- Preserve all existing user worktree changes; stage only files named by each task.
- Do not introduce Hilt, cloud services, Navigation 3, Room 3, or preview AndroidX dependencies.
- Keep the photo-derived `tealness = B - R` approach and existing OA factor weights; this plan changes architecture, not scientific constants.
- Default UI locale follows the system; supported explicit locales are `zh-CN` and `en-US`.
- Result Back returns to the originating top-level destination; only Home root delegates Back to Activity exit.
- Legacy SharedPreferences remain read-only after migration and are never deleted by automatic code.
- Commands run from `JointSense_v2/` unless a step explicitly says otherwise.
- Read `docs/superpowers/specs/2026-08-07-jointsense-v2-overhaul-design.md` before starting each task.

---

### Task 1: Establish convention plugins and the Gradle module graph

**Files:**
- Create: `build-logic/settings.gradle.kts`
- Create: `build-logic/build.gradle.kts`
- Create: `build-logic/src/main/kotlin/jointsense.kotlin.library.gradle.kts`
- Create: `build-logic/src/main/kotlin/jointsense.android.library.gradle.kts`
- Create: `build-logic/src/main/kotlin/jointsense.android.compose.gradle.kts`
- Create: `build-logic/src/main/kotlin/jointsense.android.room.gradle.kts`
- Create: `core/domain/build.gradle.kts`
- Create: `core/analysis/build.gradle.kts`
- Create: `core/designsystem/build.gradle.kts`
- Create: `core/designsystem/src/main/AndroidManifest.xml`
- Create: `core/database/build.gradle.kts`
- Create: `core/database/src/main/AndroidManifest.xml`
- Create: `core/data/build.gradle.kts`
- Create: `core/data/src/main/AndroidManifest.xml`
- Create: `feature/insights/build.gradle.kts`
- Create: `feature/insights/src/main/AndroidManifest.xml`
- Create: `feature/measurement/build.gradle.kts`
- Create: `feature/measurement/src/main/AndroidManifest.xml`
- Create: `feature/calibration/build.gradle.kts`
- Create: `feature/calibration/src/main/AndroidManifest.xml`
- Create: `feature/settings/build.gradle.kts`
- Create: `feature/settings/src/main/AndroidManifest.xml`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: current AGP/Kotlin settings and `libs.versions.toml` aliases.
- Produces: plugin IDs `jointsense.kotlin.library`, `jointsense.android.library`, `jointsense.android.compose`, `jointsense.android.room`; Gradle projects `:core:*` and `:feature:*`.

- [ ] **Step 1: Record the pre-migration project graph**

Run:

```powershell
.\gradlew.bat projects
```

Expected: output contains only root project and `:app`; if Java is unavailable, record the exact failure and locate Android Studio's bundled JBR before continuing.

- [ ] **Step 2: Add pinned stable dependencies to the version catalog**

Add these exact versions and aliases:

```toml
[versions]
activityCompose = "1.12.3"
appcompat = "1.7.1"
coreKtx = "1.18.0"
coroutines = "1.10.2"
ksp = "2.3.8"
lifecycle = "2.9.2"
navigationCompose = "2.9.8"
room = "2.8.4"
serializationJson = "1.9.0"

[libraries]
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serializationJson" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 3: Implement convention plugins**

Use precompiled script plugins. The Android library convention must apply API 36.1/minSdk 24/Java 11; the Compose convention must enable Compose; the Room convention must add Room runtime/ktx/compiler and schema export.

```kotlin
// build-logic/src/main/kotlin/jointsense.android.room.gradle.kts
import com.google.devtools.ksp.gradle.KspExtension

plugins { id("com.google.devtools.ksp") }

dependencies {
    "implementation"("androidx.room:room-runtime:2.8.4")
    "implementation"("androidx.room:room-ktx:2.8.4")
    "ksp"("androidx.room:room-compiler:2.8.4")
}

extensions.configure<KspExtension> {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

- [ ] **Step 4: Register and configure all modules**

Add `pluginManagement { includeBuild("build-logic") }` and these includes to `settings.gradle.kts`:

```kotlin
include(
    ":app",
    ":core:domain",
    ":core:analysis",
    ":core:designsystem",
    ":core:database",
    ":core:data",
    ":feature:insights",
    ":feature:measurement",
    ":feature:calibration",
    ":feature:settings",
)
```

Each feature module applies `jointsense.android.library` and `jointsense.android.compose`; `:core:domain` and `:core:analysis` apply `jointsense.kotlin.library`; `:core:database` additionally applies `jointsense.android.room`.

- [ ] **Step 5: Verify the graph and empty-module compilation**

Run:

```powershell
.\gradlew.bat projects
.\gradlew.bat :core:domain:test :core:analysis:test :app:assembleDebug
```

Expected: all ten modules appear; commands exit 0.

- [ ] **Step 6: Commit the build foundation**

```powershell
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml app/build.gradle.kts build-logic core feature
git commit -m "build: establish JointSense module graph"
```

---

### Task 2: Define pure domain models and repository contracts

**Files:**
- Create: `core/domain/src/main/kotlin/cloud/univ/jointsense/domain/model/InflammationFactor.kt`
- Create: `core/domain/src/main/kotlin/cloud/univ/jointsense/domain/model/TestModels.kt`
- Create: `core/domain/src/main/kotlin/cloud/univ/jointsense/domain/model/CalibrationModels.kt`
- Create: `core/domain/src/main/kotlin/cloud/univ/jointsense/domain/repository/TestSessionRepository.kt`
- Create: `core/domain/src/main/kotlin/cloud/univ/jointsense/domain/repository/CalibrationRepository.kt`
- Create: `core/domain/src/test/kotlin/cloud/univ/jointsense/domain/model/TestModelsTest.kt`

**Interfaces:**
- Consumes: Kotlin stdlib and `kotlinx.coroutines.flow.Flow`.
- Produces: `InflammationFactor`, `DataSource`, `RangeStatus`, `TestSession`, `TestResult`, `NewTestResult`, `Calibration`, `CalibrationKnot`, `CalibrationStatus`, `TestSessionRepository`, `CalibrationRepository`.

- [ ] **Step 1: Write the failing domain invariant test**

```kotlin
class TestModelsTest {
    @Test fun newResultKeepsStableScientificCodes() {
        val value = NewTestResult(
            factor = InflammationFactor.TNF_ALPHA,
            concentration = 42f,
            rangeStatus = RangeStatus.IN_RANGE,
            features = RgbFeatures(100f, 101f, 109f, 1f, 2f, 3f),
        )
        assertEquals("TNF_ALPHA", value.factor.name)
        assertEquals(9f, value.features.tealness)
    }
}
```

- [ ] **Step 2: Run the domain test and confirm RED**

Run: `.\gradlew.bat :core:domain:test --tests "*TestModelsTest"`

Expected: compilation fails because domain types do not exist.

- [ ] **Step 3: Implement immutable models and contracts**

Use these exact core types:

```kotlin
enum class InflammationFactor { IL6, TNF_ALPHA, IL1_BETA }
enum class DataSource { USER, BUILT_IN }
enum class RangeStatus { UNKNOWN, BELOW_RANGE, IN_RANGE, ABOVE_RANGE }
enum class CalibrationStatus { ACTIVE, NEEDS_REVIEW }

data class RgbFeatures(
    val rMean: Float, val gMean: Float, val bMean: Float,
    val rStd: Float, val gStd: Float, val bStd: Float,
) { val tealness: Float get() = bMean - rMean }

data class NewTestResult(
    val factor: InflammationFactor,
    val concentration: Float,
    val rangeStatus: RangeStatus,
    val features: RgbFeatures,
    val timestamp: Long = System.currentTimeMillis(),
)
```

Repository signatures:

```kotlin
interface TestSessionRepository {
    fun observeSessions(): Flow<List<TestSession>>
    fun observeSession(id: String): Flow<TestSession?>
    suspend fun createSession(name: String, source: DataSource = DataSource.USER): String
    suspend fun commitResult(sessionId: String, draftId: String, result: NewTestResult): String
    suspend fun deleteSession(id: String)
    suspend fun clearAll()
    suspend fun restoreBuiltInSamples()
}

interface CalibrationRepository {
    fun observeCalibrations(): Flow<List<Calibration>>
    suspend fun save(calibration: Calibration)
    suspend fun clearAll()
}
```

- [ ] **Step 4: Run domain tests and confirm GREEN**

Run: `.\gradlew.bat :core:domain:test`

Expected: all domain tests pass.

- [ ] **Step 5: Commit domain contracts**

```powershell
git add core/domain
git commit -m "feat: define JointSense domain contracts"
```

---

### Task 3: Implement Room entities, DAOs, mappings, and transactions

**Files:**
- Create: `core/database/src/main/kotlin/cloud/univ/jointsense/database/entity/TestSessionEntity.kt`
- Create: `core/database/src/main/kotlin/cloud/univ/jointsense/database/entity/TestResultEntity.kt`
- Create: `core/database/src/main/kotlin/cloud/univ/jointsense/database/entity/CalibrationEntity.kt`
- Create: `core/database/src/main/kotlin/cloud/univ/jointsense/database/entity/CalibrationKnotEntity.kt`
- Create: `core/database/src/main/kotlin/cloud/univ/jointsense/database/entity/AppMetadataEntity.kt`
- Create: `core/database/src/main/kotlin/cloud/univ/jointsense/database/dao/TestSessionDao.kt`
- Create: `core/database/src/main/kotlin/cloud/univ/jointsense/database/dao/CalibrationDao.kt`
- Create: `core/database/src/main/kotlin/cloud/univ/jointsense/database/dao/AppMetadataDao.kt`
- Create: `core/database/src/main/kotlin/cloud/univ/jointsense/database/JointSenseDatabase.kt`
- Create: `core/database/src/main/kotlin/cloud/univ/jointsense/database/DatabaseTransactions.kt`
- Create: `core/database/src/androidTest/kotlin/cloud/univ/jointsense/database/JointSenseDatabaseTest.kt`
- Create: `core/database/schemas/.gitkeep`

**Interfaces:**
- Consumes: domain enums/models from Task 2.
- Produces: `JointSenseDatabase`, DAO interfaces, `DatabaseTransactions.clearAllData()`, `DatabaseTransactions.commitResult()`.

- [ ] **Step 1: Write failing Room relationship tests**

```kotlin
@RunWith(AndroidJUnit4::class)
class JointSenseDatabaseTest {
    @Test fun deletingSessionCascadesResults() = runTest {
        val session = TestSessionEntity("s1", "Test #1", 1L, DataSource.USER)
        db.testSessionDao().insertSession(session)
        db.testSessionDao().insertResult(TestResultEntity.fixture("r1", "s1", "d1"))
        db.testSessionDao().deleteSession("s1")
        assertTrue(db.testSessionDao().resultsForSession("s1").first().isEmpty())
    }

    @Test fun duplicateDraftIdDoesNotCreateSecondResult() = runTest {
        // insert the same non-null draftId twice; the second insert must conflict
    }
}
```

- [ ] **Step 2: Compile the tests and confirm RED**

Run: `.\gradlew.bat :core:database:compileDebugAndroidTestSources`

Expected: compilation fails because database types do not exist.

- [ ] **Step 3: Implement the v1 schema**

The result entity must declare the exact foreign key and unique draft index:

```kotlin
@Entity(
    tableName = "test_result",
    foreignKeys = [ForeignKey(
        entity = TestSessionEntity::class,
        parentColumns = ["id"], childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId"), Index(value = ["draftId"], unique = true)],
)
data class TestResultEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val draftId: String?,
    val factor: InflammationFactor,
    val concentration: Float,
    val rangeStatus: RangeStatus,
    val timestamp: Long,
    val rMean: Float, val gMean: Float, val bMean: Float,
    val rStd: Float, val gStd: Float, val bStd: Float,
)
```

Add converters for enums as stable `.name` strings. Export schema version 1.

- [ ] **Step 4: Implement DAO queries and database transactions**

Continuous reads return Flow; cross-table writes are transactional:

```kotlin
@Transaction
suspend fun clearAllData() {
    testSessionDao.deleteAllSessions()
    calibrationDao.deleteAllCalibrations()
    metadataDao.put(AppMetadataEntity("samplesInitialized", "true"))
}
```

- [ ] **Step 5: Run database verification**

Run:

```powershell
.\gradlew.bat :core:database:compileDebugKotlin :core:database:compileDebugAndroidTestSources
.\gradlew.bat :core:database:connectedDebugAndroidTest
```

Expected: compilation exits 0; when a device is available, cascade and unique-index tests pass. If no device exists, record only the instrumentation run as unavailable, not passed.

- [ ] **Step 6: Commit the Room schema**

```powershell
git add core/database
git commit -m "feat: add Room persistence schema"
```

---

### Task 4: Implement repositories, built-in seeding, and legacy migration

**Files:**
- Create: `core/data/src/main/kotlin/cloud/univ/jointsense/data/RoomTestSessionRepository.kt`
- Create: `core/data/src/main/kotlin/cloud/univ/jointsense/data/RoomCalibrationRepository.kt`
- Create: `core/data/src/main/kotlin/cloud/univ/jointsense/data/BuiltInSampleProvider.kt`
- Create: `core/data/src/main/kotlin/cloud/univ/jointsense/data/legacy/LegacyJsonParser.kt`
- Create: `core/data/src/main/kotlin/cloud/univ/jointsense/data/legacy/LegacyMigrationCoordinator.kt`
- Create: `core/data/src/main/kotlin/cloud/univ/jointsense/data/legacy/MigrationOutcome.kt`
- Create: `core/data/src/test/kotlin/cloud/univ/jointsense/data/legacy/LegacyJsonParserTest.kt`
- Create: `core/data/src/androidTest/kotlin/cloud/univ/jointsense/data/LegacyMigrationCoordinatorTest.kt`
- Move: `app/src/main/java/cloud/univ/jointsense/data/BuiltInData.kt` → `core/data/src/main/kotlin/cloud/univ/jointsense/data/BuiltInSampleProvider.kt`
- Read-only source: `app/src/main/java/cloud/univ/jointsense/data/TestRepository.kt`
- Read-only source: `app/src/main/java/cloud/univ/jointsense/data/CalibrationManager.kt`

**Interfaces:**
- Consumes: Task 2 repositories; Task 3 database/transactions; legacy prefs names `joint_sense_data` and `joint_sense_calibration`.
- Produces: repository implementations; `LegacyMigrationCoordinator.migrate(): MigrationOutcome`; deterministic sample restoration.

- [ ] **Step 1: Write failing legacy parser tests with real field names**

```kotlin
@Test fun parsesLegacySessionWithoutInventingRangeOrDraft() {
    val parsed = parser.parseSessions(legacySessionJson)
    val result = parsed.single().results.single()
    assertEquals(RangeStatus.UNKNOWN, result.rangeStatus)
    assertNull(result.draftId)
}

@Test fun malformedSecondResultRejectsWholePayload() {
    assertFailsWith<LegacyParseException> { parser.parseSessions(twoResultsSecondMalformed) }
}
```

- [ ] **Step 2: Run parser tests and confirm RED**

Run: `.\gradlew.bat :core:data:testDebugUnitTest --tests "*LegacyJsonParserTest"`

Expected: compilation fails because parser types do not exist.

- [ ] **Step 3: Implement strict kotlinx.serialization JSON parsing**

Use `Json.parseToJsonElement`; require every ID, enum and numeric field. Do not catch an exception and return an empty list. Map legacy results to `rangeStatus=UNKNOWN` and `draftId=null`.

- [ ] **Step 4: Write failing migration transaction tests**

Cover these exact outcomes:

```kotlin
sealed interface MigrationOutcome {
    data class Completed(val sessions: Int, val results: Int, val calibrations: Int) : MigrationOutcome
    data object AlreadyCompleted : MigrationOutcome
    data class Failed(val reason: String) : MigrationOutcome
}
```

Tests must verify: valid payload imports all rows; malformed payload imports zero rows and writes no completion flag; no legacy rows seeds 12 samples once; `SKIPPED_BY_USER` prevents retries.

- [ ] **Step 5: Implement atomic migration and repository idempotency**

`commitResult()` performs one transaction and returns the existing row ID when `draftId` already exists. `restoreBuiltInSamples()` upserts stable built-in IDs, so repeated calls never duplicate rows.

- [ ] **Step 6: Run repository and migration tests**

Run:

```powershell
.\gradlew.bat :core:data:testDebugUnitTest
.\gradlew.bat :core:data:compileDebugAndroidTestSources
.\gradlew.bat :core:data:connectedDebugAndroidTest
```

Expected: JVM tests pass; instrumentation tests pass when a device is available.

- [ ] **Step 7: Commit data migration**

```powershell
git add core/data app/src/main/java/cloud/univ/jointsense/data/BuiltInData.kt
git commit -m "feat: migrate legacy data into Room"
```

---

### Task 5: Replace manual navigation with typed Navigation Compose

**Files:**
- Create: `app/src/main/java/cloud/univ/jointsense/navigation/Routes.kt`
- Create: `app/src/main/java/cloud/univ/jointsense/navigation/JointSenseNavHost.kt`
- Create: `app/src/main/java/cloud/univ/jointsense/navigation/TopLevelDestination.kt`
- Create: `app/src/main/java/cloud/univ/jointsense/navigation/NavigationActions.kt`
- Create: `app/src/androidTest/java/cloud/univ/jointsense/navigation/JointSenseNavigationTest.kt`
- Modify: `app/src/main/java/cloud/univ/jointsense/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: feature destination-registration functions introduced in Task 7; AppContainer from Task 7.
- Produces: serializable routes; `JointSenseNavHost()`; unified Back behavior; `MainActivity : AppCompatActivity`.

- [ ] **Step 1: Write failing route and Back policy tests**

Test navigation sequence Home → Trends → Report → Back → Trends → Back → Home, measurement Crop → Back → ImageSelect, and Result → Back → origin.

- [ ] **Step 2: Compile navigation tests and confirm RED**

Run: `.\gradlew.bat :app:compileDebugAndroidTestSources`

Expected: typed routes and NavHost do not exist.

- [ ] **Step 3: Define exact typed routes**

```kotlin
@Serializable data object HomeRoute
@Serializable data object TrendsRoute
@Serializable data object ReportRoute
@Serializable data object ProfileRoute
@Serializable data object HistoryRoute
@Serializable data class MeasurementGraph(val origin: TopLevelDestination)
@Serializable data object ImageSelectRoute
@Serializable data object CropRoute
@Serializable data object FactorSelectRoute
@Serializable data class ResultRoute(val resultId: String)
@Serializable data object CalibrationGraph
@Serializable data object CalibrationSelectRoute
@Serializable data object CalibrationCropRoute
@Serializable data object CalibrationAssignRoute
@Serializable data object CalibrationReviewRoute
@Serializable data object CalibrationDoneRoute
```

- [ ] **Step 4: Implement one NavHost and remove duplicate manual branches**

Bottom destinations use `launchSingleTop=true`; nested flow routes use the natural NavController stack. Result Back pops the whole `MeasurementGraph` and restores its `origin`. Remove `MainTab`, `FlowScreen`, `AnimatedContent` route switching, and both duplicate `CALIBRATION` branches.

- [ ] **Step 5: Verify Back behavior**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestSources
.\gradlew.bat :app:connectedDebugAndroidTest
```

Expected: compilation exits 0; navigation tests pass with a device.

- [ ] **Step 6: Commit typed navigation**

```powershell
git add app/src/main/java/cloud/univ/jointsense/navigation app/src/main/java/cloud/univ/jointsense/MainActivity.kt app/src/main/AndroidManifest.xml app/src/androidTest
git commit -m "feat: adopt typed Navigation Compose"
```

---

### Task 6: Add application locale infrastructure

**Files:**
- Create: `app/src/main/res/resources.properties`
- Create: `app/src/main/java/cloud/univ/jointsense/locale/LanguageOption.kt`
- Create: `app/src/main/java/cloud/univ/jointsense/locale/AppLanguageController.kt`
- Create: `app/src/test/java/cloud/univ/jointsense/locale/LanguageOptionTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-zh-rCN/strings.xml`

**Interfaces:**
- Consumes: AppCompat 1.7.1 and Android resource locale generation.
- Produces: `LanguageOption.SYSTEM`, `.SIMPLIFIED_CHINESE`, `.ENGLISH`; `AppLanguageController.current()` and `.apply(option)`.

- [ ] **Step 1: Write failing language-tag tests**

```kotlin
@Test fun languageTagsAreStable() {
    assertEquals("", LanguageOption.SYSTEM.languageTag)
    assertEquals("zh-CN", LanguageOption.SIMPLIFIED_CHINESE.languageTag)
    assertEquals("en-US", LanguageOption.ENGLISH.languageTag)
}
```

- [ ] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*LanguageOptionTest"`

Expected: `LanguageOption` is unresolved.

- [ ] **Step 3: Implement AppCompat locale selection**

```kotlin
fun apply(option: LanguageOption) {
    val locales = if (option == LanguageOption.SYSTEM) LocaleListCompat.getEmptyLocaleList()
    else LocaleListCompat.forLanguageTags(option.languageTag)
    AppCompatDelegate.setApplicationLocales(locales)
}
```

Enable `androidResources { generateLocaleConfig = true }`, set `unqualifiedResLocale=en-US`, and add AppCompat's disabled metadata holder service with `autoStoreLocales=true` for API 24–32.

- [ ] **Step 4: Add matched base and Chinese bootstrap resources**

Both resource files must contain identical keys for app name, top-level navigation, language labels, Back, Cancel, Retry, and migration states. Base values are English; `values-zh-rCN` values use confirmed clinical Chinese.

- [ ] **Step 5: Run resource and locale verification**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*LanguageOptionTest"
.\gradlew.bat :app:processDebugResources :app:lintDebug
```

Expected: tests and Android resource merge pass; no missing default resource.

- [ ] **Step 6: Commit locale infrastructure**

```powershell
git add app/src/main/java/cloud/univ/jointsense/locale app/src/main/res app/src/main/AndroidManifest.xml app/build.gradle.kts app/src/test
git commit -m "feat: add Chinese and English app locales"
```

---

### Task 7: Move current screens and services behind module boundaries

**Files:**
- Move: `app/src/main/java/cloud/univ/jointsense/ui/screens/HomeScreen.kt` → `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/HomeScreen.kt`
- Move: `app/src/main/java/cloud/univ/jointsense/ui/screens/TrendsScreen.kt` → `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/TrendsScreen.kt`
- Move: `app/src/main/java/cloud/univ/jointsense/ui/screens/ReportScreen.kt` → `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/ReportScreen.kt`
- Move: `app/src/main/java/cloud/univ/jointsense/ui/screens/TestScreen.kt` → `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/MeasurementScreens.kt`
- Move: `app/src/main/java/cloud/univ/jointsense/ui/screens/ResultScreen.kt` → `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/ResultScreen.kt`
- Move: `app/src/main/java/cloud/univ/jointsense/ui/screens/HistoryScreen.kt` → `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/HistoryScreen.kt`
- Move: `app/src/main/java/cloud/univ/jointsense/ui/screens/CalibrationFlowScreen.kt` → `feature/calibration/src/main/kotlin/cloud/univ/jointsense/calibration/CalibrationScreens.kt`
- Move: `app/src/main/java/cloud/univ/jointsense/ui/screens/ProfileScreen.kt` → `feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/ProfileScreen.kt`
- Move: `app/src/main/java/cloud/univ/jointsense/ui/components/*` → `core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/`
- Move: `app/src/main/java/cloud/univ/jointsense/ui/theme/*` → `core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/theme/`
- Create: `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/InsightsEntry.kt`
- Create: `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/MeasurementEntry.kt`
- Create: `feature/calibration/src/main/kotlin/cloud/univ/jointsense/calibration/CalibrationEntry.kt`
- Create: `feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/SettingsEntry.kt`
- Create: `app/src/main/java/cloud/univ/jointsense/di/AppContainer.kt`
- Create: `app/src/test/java/cloud/univ/jointsense/ModuleBoundarySmokeTest.kt`
- Delete after migration: `app/src/main/java/cloud/univ/jointsense/viewmodel/JointSenseViewModel.kt`

**Interfaces:**
- Consumes: Tasks 2–6 contracts, repositories, routes, locale controller.
- Produces: `fun NavGraphBuilder.insightsDestinations(...)`, `measurementGraph(...)`, `calibrationGraph(...)`, `settingsDestinations(...)`; `AppContainer` repository instances.

- [ ] **Step 1: Write a failing module-boundary smoke test**

The test imports only public entry functions from each feature and asserts that `AppContainer` exposes `testSessions`, `calibrations`, and `migrationCoordinator`.

- [ ] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ModuleBoundarySmokeTest"`

Expected: entry functions and container are unresolved.

- [ ] **Step 3: Move code without changing user-visible behavior**

Preserve the current screen content for this task. Replace direct `JointSenseViewModel` access with narrow state/callback parameters or feature ViewModels that depend on domain repositories. Do not fix the image pipeline or redesign screens here; those are Plan 2 and Plan 3.

Public feature entry example:

```kotlin
fun NavGraphBuilder.insightsDestinations(
    repository: TestSessionRepository,
    onStartMeasurement: (TopLevelDestination) -> Unit,
) { /* register HomeRoute, TrendsRoute, ReportRoute */ }
```

- [ ] **Step 4: Wire explicit dependencies**

`AppContainer(application)` builds one Room database, repositories, migration coordinator, and analysis engine. `MainActivity` obtains it from a custom `JointSenseApplication` and passes factories to navigation entries.

- [ ] **Step 5: Remove old cross-layer classes only after compilation**

Delete the monolithic ViewModel and old moved files after `rg` confirms no imports remain. Preserve the user's calibration/built-in work by moving its behavior into the new modules before deleting originals.

- [ ] **Step 6: Run the architecture gate**

Run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

Expected: all JVM tests pass, Debug APK builds, Lint has no blocking errors.

- [ ] **Step 7: Commit module migration**

```powershell
git add app core feature
git commit -m "refactor: move JointSense into feature modules"
```

---

### Task 8: Verify Phase 1 and update the project handoff document

**Files:**
- Modify: `项目结构需求梳理.md`
- Modify: `docs/superpowers/plans/2026-08-07-jointsense-architecture-foundation.md`

**Interfaces:**
- Consumes: all Phase 1 deliverables.
- Produces: checked plan steps and an evidence-backed Phase 1 status in the handoff document.

- [ ] **Step 1: Run the complete Phase 1 verification**

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :core:database:compileDebugAndroidTestSources :core:data:compileDebugAndroidTestSources :app:compileDebugAndroidTestSources
.\gradlew.bat :app:lintDebug :app:assembleDebug
```

If an emulator/device is available:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected: all available commands exit 0; report exact skipped device tests.

- [ ] **Step 2: Audit architecture and localization boundaries**

Run:

```powershell
rg -n "cloud\.univ\.jointsense\.database" feature
rg -n "Text\(\s*\"|contentDescription\s*=\s*\"" feature app core/designsystem
```

Expected: no feature imports database packages; remaining hardcoded UI strings are listed for Plan 3 and are not claimed localized.

- [ ] **Step 3: Update documentation with actual evidence**

Replace the target-only Phase 1 language in `项目结构需求梳理.md` with the actual module tree, migration state, command outputs, and any unavailable instrumentation tests. Check completed boxes in this plan.

- [ ] **Step 4: Commit Phase 1 documentation**

```powershell
git add 项目结构需求梳理.md docs/superpowers/plans/2026-08-07-jointsense-architecture-foundation.md
git commit -m "docs: record architecture migration results"
```

