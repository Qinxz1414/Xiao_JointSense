# JointSense Brand, Localization, and Release Quality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved A “Joint Signal” vector identity, a consistent clinical Compose workspace, complete Chinese/English resources, accessible interactions, and release-quality build/test/documentation evidence.

**Architecture:** `:core:designsystem` owns visual tokens, shared components, vector identity, and UI mappings for domain values. Feature modules own their localized screen resources and present narrow state models. Settings exposes locale, calibration, history, sample restore, unified clear, and About without reaching into Room directly.

**Tech Stack:** Phase 1/2 stack, Android VectorDrawable and Adaptive Icons, Material 3 Compose, Android resource localization, Compose UI testing, Android Lint.

## Global Constraints

- Complete and verify the architecture-foundation and measurement-reliability plans first.
- Implement the approved A “Joint Signal” geometry: two joint endpoints, two analysis arcs, one central synovial test well.
- Use vector-native Android assets; do not use raster image generation for the logo or leave project references under a generated-image cache.
- Brand colors: Ink `#0E2841`, Primary `#156082`, Cyan `#0F9ED5`, Bio Green `#196B24`; do not use purple.
- WellPalette is only for experimental signal intensity, not generic navigation or cards.
- Keep stable factor identity: TNF-α red, IL-6 orange, IL-1β green; pair every color with text/value/shape.
- All touch targets are at least 48dp and all actionable icons have localized content descriptions.
- Base resources are English; `values-zh-rCN` is Simplified Chinese using scientific/clinical wording.
- The fixed non-diagnostic disclaimer appears in About and exported reports only.
- Do not claim clinical-grade OD450 accuracy.
- Stage only files named by each task.

---

### Task 1: Replace the old purple logo with the Joint Signal vector identity

**Files:**
- Create: `core/designsystem/src/main/res/drawable/jointsense_logo.xml`
- Create: `core/designsystem/src/main/res/drawable/jointsense_logo_monochrome.xml`
- Create: `app/src/main/res/drawable/ic_launcher_joint_signal_foreground.xml`
- Modify: `app/src/main/res/drawable/ic_launcher_background.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create: `app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml`
- Create: `app/src/main/res/mipmap-anydpi/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Delete: `app/src/main/res/drawable/logo.png`
- Delete/replace: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `core/designsystem/src/test/kotlin/cloud/univ/jointsense/designsystem/BrandResourceTest.kt`

**Interfaces:**
- Consumes: approved A logo geometry and brand tokens.
- Produces: `@drawable/jointsense_logo`, `@drawable/jointsense_logo_monochrome`, legacy/adaptive launcher icons.

- [ ] **Step 1: Write a failing brand-resource test**

```kotlin
@Test fun logoResourcesContainBrandColorsAndNoPurple() {
    val xml = File("src/main/res/drawable/jointsense_logo.xml").readText()
    assertTrue(xml.contains("#156082"))
    assertTrue(xml.contains("#0F9ED5"))
    assertTrue(xml.contains("#196B24"))
    assertFalse(Regex("#(?:8A2BE2|7B2CBF|9C27B0|6200EE|7D21DC|BB86FC|3700B3)", RegexOption.IGNORE_CASE).containsMatchIn(xml))
}
```

- [ ] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :core:designsystem:testDebugUnitTest --tests "*BrandResourceTest"`

Expected: new vector resource file is absent.

- [ ] **Step 3: Draw the approved geometry as VectorDrawable paths**

Use a 100×100 viewport. Keep endpoints/arcs inside x/y 18–82 and central well inside 36–64 so launcher masks do not crop semantic geometry. Use round line caps and separate paths for Primary/Cyan arcs, Ink joint outlines, and Bio Green center.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="100dp" android:height="100dp"
    android:viewportWidth="100" android:viewportHeight="100">
    <path android:pathData="M23,29 C31,18 44,15 56,21 C61,24 65,28 68,33"
        android:strokeColor="#156082" android:strokeWidth="9"
        android:strokeLineCap="round" android:fillColor="@android:color/transparent" />
    <path android:pathData="M77,71 C69,82 56,85 44,79 C39,76 35,72 32,67"
        android:strokeColor="#0F9ED5" android:strokeWidth="9"
        android:strokeLineCap="round" android:fillColor="@android:color/transparent" />
    <path android:pathData="M50,43 A7,7 0,1 0,50,57 A7,7 0,1 0,50,43"
        android:fillColor="#196B24" />
</vector>
```

Add Ink endpoint rings as two additional closed paths. The monochrome variant uses one `?android:attr/colorControlNormal` fill/stroke mapping.

- [ ] **Step 4: Wire launcher resources and remove raster usage**

Adaptive foreground uses the same paths on an Ink background; legacy icons use an inset layer-list referencing the vector. API 33 launcher resources add `<monochrome android:drawable="@drawable/jointsense_logo_monochrome" />`. Replace all `painterResource(R.drawable.logo)` calls with `R.drawable.jointsense_logo`, remove unused purple color resources, and verify no old `logo.png` references remain.

- [ ] **Step 5: Verify resource compilation and brand test**

Run:

```powershell
.\gradlew.bat :core:designsystem:testDebugUnitTest --tests "*BrandResourceTest"
.\gradlew.bat :app:processDebugResources :app:assembleDebug
```

Expected: resource test passes and launcher resources link on API 24 and v26 variants.

- [ ] **Step 6: Commit the new identity**

```powershell
git add core/designsystem/src/main/res core/designsystem/src/test app/src/main/res
git commit -m "feat: introduce Joint Signal identity"
```

---

### Task 2: Consolidate the clinical design system and dark-theme behavior

**Files:**
- Create: `core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/theme/JointSenseColors.kt`
- Create: `core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/theme/JointSenseTheme.kt`
- Create: `core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/theme/JointSenseTypography.kt`
- Create: `core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/component/JointSenseTopBar.kt`
- Create: `core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/component/ClinicalCard.kt`
- Create: `core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/component/FactorValue.kt`
- Create: `core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/component/GradeScale.kt`
- Create: `core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/component/LoadingErrorState.kt`
- Create: `core/designsystem/src/test/kotlin/cloud/univ/jointsense/designsystem/BrandPaletteTest.kt`
- Delete after replacement: legacy theme/component files moved from `app/src/main/java/cloud/univ/jointsense/ui/`

**Interfaces:**
- Consumes: domain factor/grade values without Android UI types in domain.
- Produces: `JointSenseTheme`, `factorColor(factor)`, `gradeColor(grade)`, shared screen components.

- [ ] **Step 1: Write failing stable-mapping tests**

```kotlin
@Test fun factorAndGradeMappingsAreStable() {
    assertEquals(0xFFD64545, factorArgb(InflammationFactor.TNF_ALPHA))
    assertEquals(0xFFE97132, factorArgb(InflammationFactor.IL6))
    assertEquals(0xFF196B24, factorArgb(InflammationFactor.IL1_BETA))
    assertEquals(5, (0..4).map(::gradeArgb).distinct().size)
}
```

- [ ] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :core:designsystem:testDebugUnitTest --tests "*BrandPaletteTest"`

Expected: design-system mappings do not exist.

- [ ] **Step 3: Implement fixed semantic tokens and mappings**

Keep WellPalette separate from `MaterialTheme.colorScheme`. Light/dark schemes share semantic hue identity while using theme-appropriate surfaces and text contrast. Status bar follows Ink in light mode and dark surface in dark mode.

- [ ] **Step 4: Replace duplicated page chrome with shared components**

All top bars use `JointSenseTopBar`; cards use `ClinicalCard`; loading/error states use one component with localized action labels. Do not place page-specific text inside core components.

- [ ] **Step 5: Run palette tests and compile all feature modules**

Run:

```powershell
.\gradlew.bat :core:designsystem:testDebugUnitTest
.\gradlew.bat :feature:insights:compileDebugKotlin :feature:measurement:compileDebugKotlin :feature:calibration:compileDebugKotlin :feature:settings:compileDebugKotlin
```

Expected: tests and compilation pass.

- [ ] **Step 6: Commit the design system**

```powershell
git add core/designsystem app/src/main/java/cloud/univ/jointsense/ui
git commit -m "refactor: unify clinical design system"
```

---

### Task 3: Complete English and Simplified Chinese resources

**Files:**
- Create/modify: `feature/insights/src/main/res/values/strings.xml`
- Create/modify: `feature/insights/src/main/res/values-zh-rCN/strings.xml`
- Create/modify: `feature/measurement/src/main/res/values/strings.xml`
- Create: `feature/measurement/src/main/res/values-zh-rCN/strings.xml`
- Create/modify: `feature/calibration/src/main/res/values/strings.xml`
- Create: `feature/calibration/src/main/res/values-zh-rCN/strings.xml`
- Create/modify: `feature/settings/src/main/res/values/strings.xml`
- Create: `feature/settings/src/main/res/values-zh-rCN/strings.xml`
- Create/modify: `core/designsystem/src/main/res/values/strings.xml`
- Create: `core/designsystem/src/main/res/values-zh-rCN/strings.xml`
- Create: `app/src/test/java/cloud/univ/jointsense/locale/ResourceParityTest.kt`

**Interfaces:**
- Consumes: all visible strings discovered by `rg` in app/core/feature code.
- Produces: matched resource keys in English and zh-rCN; no user-visible hardcoded strings.

- [ ] **Step 1: Write a failing resource-parity test**

The test parses every module's base and `values-zh-rCN/strings.xml`, ignores `translatable="false"`, and asserts key-set equality. It also rejects empty translations.

- [ ] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ResourceParityTest"`

Expected: Chinese files or keys are missing.

- [ ] **Step 3: Extract every visible literal**

Run before editing:

```powershell
rg -n 'Text\(\s*"|text\s*=\s*"|contentDescription\s*=\s*"|title\s*=\s*"|subtitle\s*=\s*"' app core feature
```

Replace each user-visible literal with `stringResource`, plural resources, or a localized formatter. Stable scientific tokens such as `pg/mL` can be `translatable="false"`; session IDs, SQL, route names, and test tags are not UI strings.

- [ ] **Step 4: Add professional Chinese translations**

Use consistent terms: OA 炎症综合指数、标准曲线校准、空白孔、原始信号、净信号、拟合信号、检测下限、检测上限、肿瘤坏死因子 α、白细胞介素-6、白细胞介素-1β. Do not translate gene/protein abbreviations or `pg/mL`.

Use these exact disclaimer resources in About and the export formatter only:

```xml
<!-- values/strings.xml -->
<string name="research_disclaimer">Results in this report are estimates derived from smartphone-photo colorimetry for research and longitudinal trend observation only. They are not intended for clinical diagnosis, treatment decisions, or as a substitute for validated laboratory testing.</string>

<!-- values-zh-rCN/strings.xml -->
<string name="research_disclaimer">本报告结果基于手机照片色度代理估算，仅供科研与纵向趋势观察，不作为临床诊断、治疗决策或替代经验证实验室检测的依据。</string>
```

- [ ] **Step 5: Run parity, resource, and hardcoded-string audits**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*ResourceParityTest"
.\gradlew.bat :app:processDebugResources :app:lintDebug
rg -n 'Text\(\s*"|contentDescription\s*=\s*"' app core feature
```

Expected: parity/resource/Lint pass; final `rg` contains only test fixtures or explicitly documented non-UI literals.

- [ ] **Step 6: Commit complete localization**

```powershell
git add app core feature
git commit -m "feat: localize JointSense in Chinese and English"
```

---

### Task 4: Polish Home, Trends, Report, and Result information hierarchy

**Files:**
- Modify: `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/HomeScreen.kt`
- Modify: `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/TrendsScreen.kt`
- Modify: `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/ReportScreen.kt`
- Modify: `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/ResultScreen.kt`
- Create: `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/InsightsUiModels.kt`
- Create: `feature/insights/src/androidTest/kotlin/cloud/univ/jointsense/insights/InsightsScreenTest.kt`

**Interfaces:**
- Consumes: Room-backed feature ViewModels, design-system components, localized resources.
- Produces: approved clinical dashboard hierarchy and stable Compose semantics/test tags.

- [ ] **Step 1: Write failing semantics tests**

Home test asserts `oa_index_value`, `oa_grade`, all three `factor_value_*` nodes, `recent_trend`, and `start_measurement` exist. Result test asserts concentration, range status, features summary, and explicit Home/continue actions; it asserts the disclaimer text is absent.

- [ ] **Step 2: Compile tests and confirm RED**

Run: `.\gradlew.bat :feature:insights:compileDebugAndroidTestSources :feature:measurement:compileDebugAndroidTestSources`

Expected: semantics tags/hierarchy are missing.

- [ ] **Step 3: Implement the approved Home hierarchy**

Order: OA index/grade/time → 0–4 scale → three factor values → seven-point trend → primary “Start new measurement”. Empty state explains no data and offers measurement plus sample restore without fabricating metrics.

- [ ] **Step 4: Simplify Trends and Report**

Keep date-range selection inside Trends; label every chart axis/unit and series directly. Report screen contains index, grade, factor summary, trend interpretation, suggestions, and export actions; the fixed disclaimer remains export-only.

- [ ] **Step 5: Verify screen compilation and UI tests**

Run:

```powershell
.\gradlew.bat :feature:insights:compileDebugKotlin :feature:measurement:compileDebugKotlin
.\gradlew.bat :feature:insights:connectedDebugAndroidTest :feature:measurement:connectedDebugAndroidTest
```

Expected: compilation passes; device tests pass when available.

- [ ] **Step 6: Commit insights polish**

```powershell
git add feature/insights feature/measurement
git commit -m "feat: refine clinical insights workspace"
```

---

### Task 5: Complete Profile, language, About, sample restore, and unified clear flows

**Files:**
- Modify: `feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/ProfileScreen.kt`
- Create: `feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/LanguageDialog.kt`
- Create: `feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/AboutScreen.kt`
- Create: `feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/DataManagementDialogs.kt`
- Modify: `feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/SettingsViewModel.kt`
- Modify: `feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/SettingsViewModelFactory.kt`
- Create: `feature/settings/src/test/kotlin/cloud/univ/jointsense/settings/SettingsViewModelTest.kt`
- Create: `feature/settings/src/androidTest/kotlin/cloud/univ/jointsense/settings/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `LanguageController`, `DataManagementRepository`, TestSessionRepository, CalibrationRepository, migration/sample status.
- Produces: settings UI state; locale selection; confirmed restore/clear; fixed About disclaimer.

- [ ] **Step 1: Write failing settings behavior tests**

```kotlin
@Test fun clearAllDeletesSessionsSamplesAndCalibrationInOneAction() = runTest {
    viewModel.confirmClearAll()
    assertEquals(1, dataManager.clearAllCalls)
    assertTrue(viewModel.state.value.clearCompleted)
}

@Test fun restoreSamplesDoesNotRestoreCalibration() = runTest {
    viewModel.confirmRestoreSamples()
    assertEquals(1, dataManager.restoreSamplesCalls)
    assertEquals(0, calibrations.saveCalls)
}
```

- [ ] **Step 2: Run and confirm RED**

Run: `.\gradlew.bat :feature:settings:testDebugUnitTest --tests "*SettingsViewModelTest"`

Expected: SettingsViewModel and data manager contract do not exist.

- [ ] **Step 3: Implement grouped Profile content**

Sections: application settings (language, calibration); data/model (history, restore samples, clear all); support (model explanation, About). Show active calibration count/status and stored session count from Flow.

- [ ] **Step 4: Implement locale and destructive confirmations**

Language dialog has System/简体中文/English radio items and applies via `LanguageController`. Clear dialog explicitly lists user tests, built-in samples, and user calibration; requires one confirmation and invokes `DataManagementRepository.clearAllData()` exactly once. Restore sample dialog states it does not restore calibration and invokes only `DataManagementRepository.restoreBuiltInSamples()`.

- [ ] **Step 5: Add About copy and disclaimer**

Display app/version, photo colorimetry method, tealness definition, standard-curve behavior, OA factor weights, and exact fixed disclaimer in the active locale. Remove the inaccurate “RGB + LASSO prediction works” description because live analysis uses StandardCurve.

- [ ] **Step 6: Run settings tests**

Run:

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest
.\gradlew.bat :feature:settings:compileDebugAndroidTestSources
.\gradlew.bat :feature:settings:connectedDebugAndroidTest
```

Expected: unit tests pass; UI tests pass on a device.

- [ ] **Step 7: Commit settings completion**

```powershell
git add feature/settings
git commit -m "feat: complete app settings and data controls"
```

---

### Task 6: Enforce accessibility, dynamic type, and locale recreation behavior

**Files:**
- Create: `app/src/androidTest/java/cloud/univ/jointsense/accessibility/AccessibilitySmokeTest.kt`
- Create: `app/src/androidTest/java/cloud/univ/jointsense/locale/LocaleRecreationTest.kt`
- Modify: `core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/component/JointSenseTopBar.kt`
- Modify: `core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/component/ClinicalCard.kt`
- Modify: `core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/component/FactorValue.kt`
- Modify: `core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/component/GradeScale.kt`
- Modify: `core/designsystem/src/main/kotlin/cloud/univ/jointsense/designsystem/component/LoadingErrorState.kt`
- Modify: `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/HomeScreen.kt`
- Modify: `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/TrendsScreen.kt`
- Modify: `feature/insights/src/main/kotlin/cloud/univ/jointsense/insights/ReportScreen.kt`
- Modify: `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/MeasurementScreens.kt`
- Modify: `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/ResultScreen.kt`
- Modify: `feature/measurement/src/main/kotlin/cloud/univ/jointsense/measurement/HistoryScreen.kt`
- Modify: `feature/calibration/src/main/kotlin/cloud/univ/jointsense/calibration/CalibrationScreens.kt`
- Modify: `feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/ProfileScreen.kt`
- Modify: `feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/AboutScreen.kt`
- Modify: `feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/LanguageDialog.kt`
- Modify: `feature/settings/src/main/kotlin/cloud/univ/jointsense/settings/DataManagementDialogs.kt`

**Interfaces:**
- Consumes: all final screens, NavHost, `LanguageController`/`AppCompatLanguageController`.
- Produces: minimum touch semantics, localized labels, state restoration across Activity recreation.

- [ ] **Step 1: Write failing UI tests**

Tests assert every icon-only button has a content description, primary actions meet `isTouchMode` click semantics, language switch recreates Activity into Chinese/English, and a saved measurement URI/crop/factor remains after recreation.

- [ ] **Step 2: Compile tests and confirm RED**

Run: `.\gradlew.bat :app:compileDebugAndroidTestSources`

Expected: at least one current control lacks required semantics or state restoration.

- [ ] **Step 3: Fix semantics and responsive layout**

Use `Modifier.minimumInteractiveComponentSize()` for small actions; localize descriptions; add text/value labels alongside colors; replace fixed-height text containers with wrap-content/scrolling where 200% font scale would clip.

- [ ] **Step 4: Verify recreation and accessibility**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestSources
.\gradlew.bat :app:connectedDebugAndroidTest
```

Expected: tests pass on an available device; otherwise record them as compiled but not executed.

- [ ] **Step 5: Commit accessibility fixes**

```powershell
git add app/src/androidTest core/designsystem feature
git commit -m "fix: improve accessibility and state restoration"
```

---

### Task 7: Run final release verification and synchronize all documentation

**Files:**
- Modify: `项目结构需求梳理.md`
- Modify: `docs/superpowers/plans/2026-08-07-jointsense-brand-localization-release.md`
- Modify if visual output differs: `docs/superpowers/specs/2026-08-07-jointsense-v2-overhaul-design.md`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: all three implementation plans.
- Produces: verified final application, current project handoff document, ignored `.superpowers/` brainstorming artifacts.

- [ ] **Step 1: Run the full unit, resource, Lint, and build gate**

```powershell
.\gradlew.bat clean testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

Expected: every command exits 0 with zero test failures and no blocking Lint errors.

- [ ] **Step 2: Run the full device gate when available**

```powershell
adb devices
.\gradlew.bat connectedDebugAndroidTest
```

Expected: at least one authorized device followed by zero instrumentation failures. If no device is present, state that explicitly in the final report.

- [ ] **Step 3: Run structural and localization audits**

```powershell
rg -n "FlowScreen|MainTab|JointSenseViewModel|SharedPreferences" app core feature
rg -n 'Text\(\s*"|contentDescription\s*=\s*"' app core feature
rg -n "#(?:8A2BE2|7B2CBF|9C27B0|6200EE)|ChartPurple|Purple" app core feature
```

Expected: SharedPreferences appears only in the legacy migration adapter/AppCompat internals; old navigation/monolithic ViewModel/purple/hardcoded UI hits are absent or explicitly non-UI test fixtures.

- [ ] **Step 4: Verify report files and both locales manually or by instrumentation**

Generate one English and one Chinese report; confirm non-empty PDFs, wrapped lines, page margins, all factor values/units, OA index/grade, timestamp, and exact disclaimer. Verify About uses the same disclaimer and Result omits it.

- [ ] **Step 5: Update `项目结构需求梳理.md` completely**

Replace target-state wording with the actual module tree, current dependencies, Room schema, migration behavior, navigation, image pipeline, calibration rules, logo resources, language resources, fixed defects, and exact verification output. Do not retain “尚待实施” for completed items. List any genuinely unavailable device validation.

- [ ] **Step 6: Ignore local brainstorming artifacts and review the diff**

Add `/.superpowers/` to `.gitignore`; do not delete the local visual drafts. Run `git diff --check` and `git status --short`, confirming no unrelated user files are staged.

- [ ] **Step 7: Commit final documentation and quality evidence**

```powershell
git add .gitignore 项目结构需求梳理.md docs/superpowers/plans docs/superpowers/specs
git commit -m "docs: finalize JointSense v2 handoff"
```
