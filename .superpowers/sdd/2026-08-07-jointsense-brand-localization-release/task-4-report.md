# Phase 3 Task 4 — Home, Trends, Report, and Result information hierarchy

## Scope delivered

- Added pure Kotlin presentation boundaries in `InsightsUiModels.kt` and `ResultUiModels.kt`.
- Home now follows the approved order: latest OA inflammation index (AI), grade and observation time; 0–4 grade scale; all three absolute factor values; exactly the seven most recent OA observations; primary new-measurement action.
- Home empty state publishes no index, grade, factor, or trend values. It exposes separate start and restore-sample callbacks. The app boundary routes restore-sample requests to Profile; Task 5 owns its confirmation and repository mutation.
- Trends retains its selected range through recreation with `rememberSaveable`, uses controls with a 48 dp minimum height, and publishes direct series/date/concentration labels plus stable chart semantics tags.
- Report publishes OA index/grade, absolute values for all three factors, available weekly factor deltas, a rule-based weekly OA trend interpretation (`RISING`, `STABLE`, `FALLING`, `INSUFFICIENT_DATA`), suggestions, and export actions.
- Removed the unsupported 14-day progression forecast/risk from the screen, localized resources, report formatter, and tests. No replacement prediction, risk probability, or clinical threshold was introduced.
- Result now leads with the measured factor, concentration, explicit four-state quantitative range (including `UNKNOWN`), RGB and net-tealness features, and the complete three-factor session summary. Continue and Home are distinct actions.
- Result top/system Back still invokes `onReturnToOrigin`; the new Home action invokes `onGoHome` and clears navigation to the real Home root.
- Added paired English and Simplified Chinese resources for every new user-visible string; the Task 3 resource contract remains green. The fixed disclaimer remains export-only and absent from Home/Trends/Report preview/Result.

## TDD evidence

### RED

The new pure Kotlin and Compose contracts were added before production code:

```powershell
.\gradlew.bat :feature:insights:testDebugUnitTest --tests '*InsightsUiModelsTest' `
  :feature:measurement:testDebugUnitTest --tests '*ResultUiModelTest' `
  :feature:insights:compileDebugAndroidTestSources `
  :feature:measurement:compileDebugAndroidTestSources --rerun-tasks
```

Expected RED occurred after 68 tasks: `toHomePresentation`, `toReportPresentation`, trend interpretation, result presentation models, and the Home restore callback did not exist; the new Compose sources therefore did not compile.

### GREEN

The same focused gate then completed successfully:

```powershell
.\gradlew.bat :feature:insights:testDebugUnitTest --tests '*InsightsUiModelsTest' `
  :feature:measurement:testDebugUnitTest --tests '*ResultUiModelTest' `
  :feature:insights:compileDebugAndroidTestSources `
  :feature:measurement:compileDebugAndroidTestSources --rerun-tasks
```

Result: `BUILD SUCCESSFUL`; 77/77 actionable tasks executed. The tests cover exact seven-point selection, fabricated empty-state rejection, all three report factors, four weekly trend mappings, result concentration/features, all four `RangeStatus` values, and the `UNKNOWN != IN_RANGE` contract.

The affected regression/resource/app-source gate also passed:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*ResourceParityTest' `
  :feature:insights:testDebugUnitTest `
  :feature:measurement:testDebugUnitTest `
  :app:compileDebugAndroidTestSources --rerun-tasks
```

Result: `BUILD SUCCESSFUL`; 185/185 actionable tasks executed. ResourceParityTest passed 5/5, both feature JVM suites passed, and app instrumentation sources—including real Result Home navigation—compiled.

## Final verification

Fresh non-incremental JVM gate:

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
```

- `BUILD SUCCESSFUL`; 209/209 actionable tasks executed.
- 42 suites, 249 tests, 0 failures, 0 errors, 0 skipped.
- Module totals: app 24; core data 6; core database 1; core design system 26; core image 10; calibration 60; insights 24; measurement 92; settings 6.

Latest Lint/APK gate:

```powershell
.\gradlew.bat :app:lintDebug :app:assembleDebug
```

- `BUILD SUCCESSFUL`; 374 actionable tasks (15 executed, 359 up-to-date after the fresh non-incremental combined gate).
- Lint: 0 errors, 45 existing warnings.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 21,275,038 bytes, SHA-256 `D55AF351538467EB3EC6D4296E0BFD4E85384C0312E7498D087D1A5F1EEAC830`.
- `git diff --check`: clean apart from existing LF-to-CRLF conversion notices.
- Production audit finds no `14-day` / `14 天` forecast claim. Disclaimer access remains only in the export formatter.

## Device status

`adb devices -l` started the daemon but reported no authorized device or emulator. Compose instrumentation tests were compiled, but no connected-device test was executed or claimed.

## Fix round 1/5

The first specification and code review round closed all required Task 4 findings:

- Weekly OA comparison now requires a finite baseline at or before the exact seven-day boundary and a finite current-window point after that boundary. Stale-only, future-only, zero/non-finite baseline, invalid index, and recent-only evidence return no comparison and render `INSUFFICIENT_DATA`.
- Result navigation now has one shared `resultId` lookup. A stale `currentSession` cannot shadow the requested result, and a genuinely missing result renders an explicit localized not-found state with independent Back and Home actions instead of a synthetic `UNKNOWN` result.
- Home restore now requests a one-shot Profile confirmation before navigation. Only an explicit confirmation calls `DataManagementRepository.restoreBuiltInSamples()`. The copy states that calibration curves are not restored or changed; cancel, success, failure, duplicate request, sequential duplicate confirmation, and 128 concurrent confirmations are covered. The repository claim uses an atomic compare-and-set transition and cancellation is rethrown rather than reported as failure.
- One domain presentation-order contract is used for Task 4 display/export paths: `TNF-α`, `IL-6`, `IL-1β`.
- Home, Trends, Report preview/export, and Result presentation boundaries discard non-finite values; OA index is constrained to 0..1, concentration values cannot be negative, and grades outside 0..4 are unavailable. Malformed persisted Result data no longer reaches grade math or well-signal rendering.
- Trends accepts an injected clock; its instrumentation fixture uses fixed observations inside the default seven-day window, while future and invalid chart points are excluded.

### Fix-round TDD evidence

RED was observed before implementation. The affected gate failed to compile because `locateResultById`, restore-confirmation state/actions, and related presentation contracts did not yet exist. Weekly boundary and adversarial model tests were added in the same RED batch.

After implementation and concurrency review, the affected JVM suites and all four affected Android-test Kotlin compilations passed. The final fresh verification was:

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks --no-daemon
```

- `BUILD SUCCESSFUL`; 209/209 actionable tasks executed.
- 44 suites, 266 tests, 0 failures, 0 errors, 0 skipped.
- Module totals: app 25; core data 6; core database 1; core design system 26; core image 10; calibration 60; insights 32; measurement 96; settings 10.

```powershell
.\gradlew.bat :feature:insights:compileDebugAndroidTestKotlin `
  :feature:measurement:compileDebugAndroidTestKotlin `
  :feature:settings:compileDebugAndroidTestKotlin `
  :app:compileDebugAndroidTestKotlin --rerun-tasks --no-daemon
```

- `BUILD SUCCESSFUL`; 170/170 actionable tasks executed.

```powershell
.\gradlew.bat :app:lintDebug :app:assembleDebug --rerun-tasks --no-daemon
```

- `BUILD SUCCESSFUL`; 374/374 actionable tasks executed.
- Lint: 0 errors, 45 existing warnings.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 22,123,705 bytes.
- SHA-256: `8E670C98D2E6A93382CE7052440C984BFE5B546907F04A0FD7ED05B327F9DC5E`.
- `git diff --check`: clean apart from line-ending conversion notices.

The final terminology audit replaced unconditional “weekly change” headings with “available comparison” / “有可用基线时” wording. `ResourceParityTest`, the full insights JVM suite, and `:app:assembleDebug` passed after this wording-only refinement; the APK size and hash above are from that final artifact.

`adb devices -l` again reported no connected device, so the Android instrumentation suites were compiled but not executed or claimed in fix round 1.
