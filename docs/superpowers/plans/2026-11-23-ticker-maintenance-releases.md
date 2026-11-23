# Ticker maintenance releases 0.2.1–0.2.3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three small releases:
- 0.2.1: the widget follows the theme.
- 0.2.2: a Quick Settings tile and app shortcuts.
- 0.2.3: all text moves into string resources, plus a Hungarian translation.

**Architecture:** Logic lives in pure, JVM-testable functions:
- `WidgetTheme.colors`
- `Tab.fromLaunchExtra`
- `UiText` for text built by models
- the `CurrencyCatalog` localisation

Android glue stays thin: Glance, `TileService`, `MainActivity` intents and `stringResource`. Guard tests parse the `strings.xml` files and scan the Kotlin sources.

**Tech Stack:** Kotlin, Jetpack Compose, Glance 1.2.0, AGP 9.5.1, JUnit4 and kotlin.test (no Robolectric).

**Spec:** `/home/szlukabence/Work/currency-converter/docs/superpowers/specs/2026-11-23-ticker-maintenance-releases-design.md`

## Global Constraints

- minSdk 26, targetSdk 36, compileSdk 37. No new dependencies. No new permissions.
- Unit test command: `./gradlew :app:testDebugUnitTest`. Unit tests run with the working directory set to `app/`, so files are read as `src/main/res/...`.
- **Versions:**

  | versionName | versionCode | ABI codes | Changelog |
  |---|---|---|---|
  | 0.2.1 | 3 | 30–33 | `33.txt` |
  | 0.2.2 | 4 | 40–43 | `43.txt` |
  | 0.2.3 | 5 | 50–53 | `53.txt` |

  Each release updates `app/build.gradle.kts`, `fastlane/metadata/android/en-US/changelogs/<n>.txt` and `docs/fdroid/io.github.szlukabence.ticker.yml`. In the yml, add a Builds block per ABI, copying the 0.2.0 blocks with the new commit and codes, and set `CurrentVersion` / `CurrentVersionCode`.
- **Git:** local commits per task are allowed once the user OKs this plan. No tags, pushes or amends without an explicit OK. End commit messages with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Replace the model name with the session's actual model.
- **Phone:**
  - Install only with `~/Android/Sdk/platform-tools/adb install -r`. Never uninstall. Never run connectedAndroidTest. Never change phone settings.
  - Test themes through the app's own Theme menu, then set it back to **System**.
  - Ask the user before each phone session, since they may need to re-pair or unlock.
- The app name stays "Ticker". The Camera tab stays dark.
- **String key names:** `<area>_<what>` in snake_case, for example `tab_camera`, `convert_refresh`, `about_privacy_title`. Placeholders are positional (`%1$s`), and counts use `<plurals>`.
- **Fixed Hungarian terms:**

  | English | Hungarian |
  |---|---|
  | Convert | Átváltás |
  | Charts | Grafikonok |
  | Camera | Kamera |
  | favourites | kedvencek |
  | rate | árfolyam |
  | card fee | kártyadíj |
  | receipt | blokk |
  | Scan price | Ár beolvasása |
  | System / Light / Dark | Rendszer / Világos / Sötét |
  | Refresh | Frissítés |

## Review Focus

1. **Hungarian overflow:** dock labels, header icon-button descriptions shown as text, the keypad header and the 110 dp widget. Check on the phone (1080×2340) in Task 8 and shorten the Hungarian where it clips.
2. **Shortcut while Ticker is open:** `onNewIntent` must switch tabs in the existing activity, without a second copy. The test pins `fromLaunchExtra`. Task 2 covers the activity manually.
3. **Widget on System theme:** it must follow the phone's dark mode while the app is closed. Task 1 uses day/night ColorProviders, which need no redraw. Check manually only if the user agrees to toggle dark mode themselves, since we never change phone settings.
4. **Tile on API 34+ and on older versions:** both code paths are compiled. On the phone (API 34+) the tap must close the panel and open the app.
5. **Number format:** it must keep following the region, not the app language. `AmountFormatter` is untouched. Task 7 adds a test that the catalog localisation doesn't change `AmountFormatter.forLocale`.

---

### Task 1: Widget follows the theme (release 0.2.1)

**Files:**
- Create: `app/src/main/java/dev/ticker/converter/widget/WidgetTheme.kt`
- Modify: `widget/TickerWidget.kt` (companion colours become a parameter), `widget/WidgetModel.kt:37` (`redrawKey`)
- Modify: `res/layout/widget_loading.xml`, plus `res/values/colors.xml` and `res/values-night/colors.xml` (add `widget_bg` and `widget_text`)
- Modify: `app/build.gradle.kts`, the 0.2.1 release files (Global Constraints)
- Test: `app/src/test/java/dev/ticker/converter/widget/WidgetThemeTest.kt`, `WidgetModelTest.kt`

**Interfaces:**
- Produces:
  - `data class WidgetRoles(val bg: Color, val accent: Color, val text: Color, val dim: Color, val negative: Color, val warning: Color)`
  - `data class WidgetColors(val dayNight: Boolean, val light: WidgetRoles, val dark: WidgetRoles)`
  - `object WidgetTheme { val Light: WidgetRoles; val Dark: WidgetRoles; fun colors(mode: ThemeMode): WidgetColors }`
- Roles: `Light.bg = LightPalette.surface`, `Dark.bg = Color.Black`. The other roles copy the same-named palette fields.

- [ ] **Step 1: Write failing tests**
  - `WidgetThemeTest.systemIsDayNight`: `colors(SYSTEM).dayNight == true`, `light == Light`, `dark == Dark`.
  - `lightIsFixedLight`: `dayNight == false`, `light == Light`, `dark == Light`.
  - `darkIsFixedDark`: `dark == Dark`, `light == Dark`.
  - `lightBgIsWhiteCard`: `Light.bg == LightPalette.surface`.
  - `WidgetModelTest.redrawKeyChangesWithTheme`: `redrawKey(s, snap) != redrawKey(s.copy(theme = ThemeMode.LIGHT), snap)`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*WidgetThemeTest' --tests '*WidgetModelTest'`. Expected: compile failure, because `WidgetTheme` is undefined.
- [ ] **Step 3: Implement** `WidgetTheme`, and add `s.theme` to `redrawKey`.
- [ ] **Step 4: Wire the widget.**
  - `provideGlance` reads `settings?.theme ?: ThemeMode.SYSTEM` and passes `WidgetTheme.colors(mode)` into `Content`.
  - A helper `fun WidgetColors.provider(pick: (WidgetRoles) -> Color): ColorProvider` returns `androidx.glance.color.ColorProvider(day = pick(light), night = pick(dark))` when `dayNight`, otherwise `ColorProvider(pick(light))`.
  - Replace `Color.Black` and the companion `ACCENT`, `TEXT` and the other colour constants. Remove the "stays dark" comment.
  - `widget_loading.xml` uses `@color/widget_bg` and `@color/widget_text`:
    - `values`: `#FFFFFFFF` and `#FF151917`
    - `values-night`: `#FF000000` and `#FFE6E6E6`
- [ ] **Step 5: Run** `./gradlew :app:testDebugUnitTest`. Expected: all pass.
- [ ] **Step 6: Release files for 0.2.1.**
  - Set versionCode 3 and versionName "0.2.1".
  - `changelogs/33.txt`: `The home-screen widget now follows Ticker's light or dark theme.`
  - Update the fdroid yml.
- [ ] **Step 7: Phone check** (ask the user first).
  - Run `./gradlew :app:assembleDebug`, then `adb install -r` the arm64 debug APK.
  - Set the in-app theme to Light: the widget turns white within about 2 s. Set it to Dark: black. Set it back to System.
  - Take a screenshot of each state for the user.
- [ ] **Step 8: Commit** `Widget follows the app theme (0.2.1)`.

### Task 2: Shortcut routing

**Files:**
- Modify: `ui/components/BottomBar.kt:30` (add the companion to `Tab`), `MainActivity.kt`, `ui/nav/TickerNavHost.kt`
- Create: `res/xml/shortcuts.xml`, `res/drawable/ic_shortcut_scan.xml`, `res/drawable/ic_shortcut_charts.xml`
- Modify: `AndroidManifest.xml` (add the meta-data to `MainActivity`, set `android:launchMode="singleTop"`)
- Test: `app/src/test/java/dev/ticker/converter/ui/nav/LaunchTabTest.kt`

**Interfaces:**
- Produces:
  - `Tab.Companion.fromLaunchExtra(value: String?): Tab`. It matches `Tab.route` exactly and falls back to `Tab.CONVERT`.
  - `const val EXTRA_TAB = "dev.ticker.converter.TAB"`, in `MainActivity.kt`'s companion.
  - `TickerNavHost(container, appDark, activity, launchTab: LaunchRequest?)`, with `data class LaunchRequest(val tab: Tab, val seq: Int)`. Each intent bumps `seq`, so the same tab twice still navigates.

- [ ] **Step 1: Write failing tests** in `LaunchTabTest`:
  - `"camera"` gives CAMERA, `"charts"` gives CHARTS, `"convert"` gives CONVERT.
  - `null`, `"bogus"` and `"CAMERA"` give CONVERT.
  - `shortcutsXmlTabsAreReal`: parse `src/main/res/xml/shortcuts.xml` with `javax.xml.parsers.DocumentBuilderFactory`. For each `<extra android:name="dev.ticker.converter.TAB" android:value=…>`, `Tab.entries.any { it.route == value }` is true and the value isn't `convert`. Also assert there are exactly 2 shortcuts with ids `scan` and `charts`.
- [ ] **Step 2: Run** `--tests '*LaunchTabTest'`. Expected: FAIL. It won't compile (no `fromLaunchExtra`), and `shortcuts.xml` is missing.
- [ ] **Step 3: Implement.**
  - **`shortcuts.xml`:** two `<shortcut>` entries, `scan` and `charts`. Each has `android:icon`, plus `shortcutShortLabel` and `shortcutLongLabel` as `@string/shortcut_scan_short` / `_long` and `@string/shortcut_charts_short` / `_long`. Add these strings to `values/strings.xml` now:
    - "Scan price" / "Scan a price with the camera"
    - "Charts" / "Open rate charts"
  - Each `<intent android:action="android.intent.action.VIEW" android:targetPackage="io.github.szlukabence.ticker" android:targetClass="dev.ticker.converter.MainActivity">` carries the TAB `<extra>`.
    - `targetPackage` is the `applicationId`, and `targetClass` uses the namespace. There is no debug `applicationIdSuffix`, so one file serves both build types.
  - **Icons:** 24 dp vectors built from the `TickerIcons.Camera` and `Charts` path data. Use stroke `#FF007A42`, width 2, round caps, on a 48 dp viewport with the glyph scaled into the middle 24 dp.
  - **`MainActivity`:**
    - Keep `var launch by mutableStateOf<LaunchRequest?>(null)`.
    - In `onCreate`, if `savedInstanceState == null`, set it from the intent.
    - `onNewIntent(intent)` calls `setIntent(intent)` and sets `LaunchRequest(Tab.fromLaunchExtra(intent.getStringExtra(EXTRA_TAB)), seq + 1)`.
    - Ignore intents that have no extra, so a plain launcher tap doesn't reset the tab.
  - **`TickerNavHost`:** add `LaunchedEffect(launchTab) { launchTab?.let { select(it.tab) } }`.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: all pass.
- [ ] **Step 5: Commit** `App shortcuts for Scan price and Charts`.

### Task 3: Quick Settings tile (release 0.2.2)

**Files:**
- Create: `app/src/main/java/dev/ticker/converter/tile/TickerTileService.kt`, `res/drawable/ic_tile.xml`
- Modify: `AndroidManifest.xml`, `res/values/strings.xml` (add `tile_label` = "Ticker"), the 0.2.2 release files

**Interfaces:**
- Consumes: `MainActivity`.
- Produces: none. The Android glue has no JVM test, and the device check in Step 3 is its verification. Record that as a ruling in the ledger.

- [ ] **Step 1: Implement `TickerTileService`.**
  - `onStartListening`: `qsTile?.apply { state = Tile.STATE_INACTIVE; updateTile() }`.
  - `onClick`: build `Intent(this, MainActivity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK)`.
    - `Build.VERSION.SDK_INT >= 34`: `startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT))`.
    - Otherwise: `@Suppress("DEPRECATION") startActivityAndCollapse(intent)`.
  - `ic_tile.xml`: a white (`#FFFFFFFF`) 24 dp vector of the two `TickerIcons.Convert` paths, stroke 2, round caps.
  - Manifest: `<service android:name=".tile.TickerTileService" android:exported="true" android:label="@string/tile_label" android:icon="@drawable/ic_tile" android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">` with the `android.service.quicksettings.action.QS_TILE` intent filter. Also add `<meta-data android:name="android.service.quicksettings.TOGGLEABLE_TILE" android:value="false"/>`.
- [ ] **Step 2: Release files for 0.2.2.**
  - Set versionCode 4 and versionName "0.2.2".
  - `changelogs/43.txt`: `New Quick Settings tile to open Ticker, and long-press shortcuts for Scan price and Charts.`
  - Update the fdroid yml.
  - Add one sentence to `full_description.txt`.
  - Run `./gradlew :app:testDebugUnitTest :app:lintDebug`. Expected: tests pass and lint shows no new errors.
- [ ] **Step 3: Phone check** (ask the user first).
  - Install with `-r`. Ask the user to add the Ticker tile; Claude doesn't edit Quick Settings.
  - Tap the tile: the panel closes and Ticker opens.
  - Long-press the launcher icon: Scan price opens Camera and Charts opens Charts. Repeat both while Ticker is already in the foreground.
  - Check with `adb shell dumpsys activity activities | grep -c dev.ticker.converter.MainActivity` that there is still one activity record.
- [ ] **Step 4: Commit** `Quick Settings tile (0.2.2)`.

### Task 4: String guard tests and the UiText helper

**Files:**
- Create: `app/src/test/java/dev/ticker/converter/testutil/StringsXml.kt`
- Create: `app/src/test/java/dev/ticker/converter/i18n/StringParityTest.kt`, `i18n/NoHardcodedTextTest.kt`
- Create: `app/src/main/java/dev/ticker/converter/ui/text/UiText.kt`
- Test: `app/src/test/java/dev/ticker/converter/ui/text/UiTextTest.kt`

**Interfaces:**
- Produces:
  - `object StringsXml { fun load(path: String): Res }`
  - `data class Res(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)`. Values are unescaped: `\'`, `\"` and `\n` resolved.
  - `fun placeholders(s: String): Set<String>` returns the `%n$x` tokens.
  - `sealed interface UiText { data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText; data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText; data class Raw(val text: String) : UiText }`
  - `@Composable fun UiText.resolve(): String`
  - `fun UiText.resolve(context: Context): String`

- [ ] **Step 1: Write the tests.**
  - **`StringParityTest`:**
    - `huHasEveryKey`: the key sets of `values/strings.xml` and `values-hu/strings.xml` are equal for both strings and plurals. A missing hu file counts as empty.
    - `placeholdersMatch`: for each key, `placeholders(en) == placeholders(hu)`.
    - `noEmptyHu`.
    - `pluralsHaveOneAndOther`: both files.
    - Failure messages list the offending keys.
  - **`NoHardcodedTextTest`:** walk `src/main/java`. For `*.kt` lines, flag these regexes (after stripping `//` comments):
    - `Text\(\s*"[\p{L}]`
    - `(contentDescription|label|title|placeholder)\s*=\s*"[\p{L}]`
    - also `NeonIconButton\([^,]+,\s*"[\p{L}]`, the description argument at `ChartsScreen.kt:89`
    - Allowlist: the literal `"TICKER"`, and `label = "` inside `animate*AsState(`. Those are animation labels, so skip a line matching `AsState\(`.
    - The failure message lists `file:line`.
  - **`UiTextTest`:** `UiText.Res(R.string.tab_convert, listOf("x"))` keeps its id and args. This checks that `R` resolves in unit tests.
- [ ] **Step 2: Run** `--tests '*StringParityTest' --tests '*NoHardcodedTextTest' --tests '*UiTextTest'`. Expected:
  - `UiTextTest` fails to compile until `UiText` exists, and the `tab_convert` key exists. Add it in Step 3.
  - Parity fails: there are no hu keys yet.
  - NoHardcoded fails with about 150 hits. **Leave both failing.** Tasks 5 and 6 turn them green. Write the hit count into the ledger.
- [ ] **Step 3: Implement** `UiText` and `StringsXml`, and add `tab_convert`, `tab_charts` and `tab_camera` to `values/strings.xml`. Run `UiTextTest`. Expected: PASS.
- [ ] **Step 4: Commit** `i18n guard tests and UiText` (parity and no-hardcoded are known red, as ledgered).

### Task 5: Move all text into resources

**Files:**
- Modify: every file `NoHardcodedTextTest` reports.
- Modify: `ui/components/SyncBadge.kt`, `widget/WidgetModel.kt`, `widget/TickerWidget.kt`, `ui/about/AboutContent.kt`, `AboutScreen.kt`, `ui/charts/ChartsScreen.kt:77`, `AndroidManifest.xml`
- Test: `SyncBadgeTextTest.kt`, `WidgetModelTest.kt`, `AboutContentTest.kt` (updated)

**Interfaces:**
- Consumes: `UiText` and `StringsXml` from Task 4.
- Produces:
  - `Tab(val route: String, @StringRes val label: Int, val icon: ImageVector)`
  - `fun syncBadgeText(status, now, zone): UiText`
  - `WidgetUi.Rates.feeLabel` stays the percent string ("1.5%"), and the widget wraps it with `widget_incl_fee` (`incl. %1$s fee`).
  - `AboutContent.LEGAL`, `DISCLAIMER` and `PRIVACY` become `@StringRes Int` (`about_legal`, `about_disclaimer`, `about_privacy`).
  - `DataSource.detail` becomes `@StringRes Int` (`about_source_ecb_detail`, `about_source_fawaz_detail`). `name`, `url`, `COPYRIGHT` and the `components` list stay literal.

- [ ] **Step 1: Update the tests first.**
  - **`SyncBadgeTextTest`:** each case asserts a `UiText`, for example:
    - `live`: `UiText.Res(R.string.sync_live, listOf("09:41:07"))`
    - `offlineRelative`: `UiText.Res(R.string.sync_offline, listOf(UiText.Plural(R.plurals.sync_hours_ago, 2)))`
    - `offlineJustNow`: `UiText.Res(R.string.sync_offline, listOf(UiText.Res(R.string.sync_just_now)))`
    - Nested `UiText` args are resolved recursively by `resolve`.
  - **Also:** a new `syncStringsKeepEnglishWording` test that loads `values/strings.xml` through `StringsXml` and asserts `sync_live == "SYNCED %1$s"` and `sync_offline == "OFFLINE · last sync %1$s"`.
  - **`AboutContentTest`:**
    - `noWarrantyIsStated` and the ECB attribution test read the English values via `StringsXml.load("src/main/res/values/strings.xml").strings[...]`.
    - A new `ecbAttributionUntranslated` asserts `"source: ECB"` appears in both the en and hu `about_source_ecb_detail`. Until Task 6 adds the hu file, this fails only on hu.
- [ ] **Step 2: Run** `--tests '*SyncBadgeTextTest' --tests '*AboutContentTest'`. Expected: compile failure, because the resource IDs are missing.
- [ ] **Step 3: Extract**, file by file, in the order the test reports them. Use the English text exactly as it is now, so the English UI doesn't change at all.
  - **Uppercase headings** (`"CHARTS"`): store them as they are shown, `charts_title` = "CHARTS".
  - **Manifest:** selection activity `android:label="@string/selection_label"` ("Convert"), widget receiver `@string/widget_label` ("Ticker rates").
  - **Charts dates** (`ChartsScreen.kt:77`): `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` for the long ranges. For D1 and W1, use `ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)`. Keep `.withZone(ZoneId.systemDefault())`. Also key `remember` on `LocalConfiguration.current`'s locale.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: everything passes except `StringParityTest` and the hu half of `ecbAttributionUntranslated`. `NoHardcodedTextTest` must pass now.
- [ ] **Step 5: Phone check of the English UI** (ask the user first). Install with `-r` and screenshot Convert, Charts, Camera, About and the widget. The text must be identical to 0.2.2.
- [ ] **Step 6: Commit** `Move all UI text into string resources`.

### Task 6: Hungarian strings and locale config

**Files:**
- Create: `res/values-hu/strings.xml`, `app/src/main/res/resources.properties` (`unqualifiedResLocale=en-US`)
- Modify: `app/build.gradle.kts`: inside `android {}`, add `androidResources { generateLocaleConfig = true; localeFilters += listOf("en", "hu") }`. If AGP 9.5 names `localeFilters` differently, check with context7 and ledger it.

- [ ] **Step 1: Write `values-hu/strings.xml`.**
  - Natural Hungarian, using the Global Constraints terms.
  - Uppercase headings stay uppercase ("GRAFIKONOK").
  - `about_legal` translates the GPL notice and ends with: `A licenc angol nyelvű szövege az irányadó.`
  - `about_source_ecb_detail` keeps `(source: ECB)` word for word.
  - Use Hungarian plural forms: `one` and `other` with the same text, since Hungarian nouns after numbers stay singular.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest`. Expected: all pass, `StringParityTest` included.
- [ ] **Step 3: Run** `./gradlew :app:assembleDebug`. Expected: builds, and `app/build/generated/**/locale_config.xml` lists `en-US` and `hu`.
- [ ] **Step 4: Commit** `Hungarian translation`.

### Task 7: Localised currency names

**Files:**
- Modify: `data/catalog/CurrencyCatalog.kt`, `AppContainer.kt:35`
- Test: `data/catalog/CurrencyCatalogTest.kt`

**Interfaces:**
- Produces: `CurrencyCatalog(fiat: List<Currency>, cryptoNames: Map<String, String>, localizedLanguages: Set<String> = emptySet(), locale: () -> Locale = Locale::getDefault)`.
  - `fromAssets(context)` passes `setOf("hu")`.
  - `get`, `all` and `search` use the localised fiat map for `locale()`. It is cached in a `ConcurrentHashMap<Locale, Map<String, Currency>>`.
  - The existing constructor calls in tests and in `Fakes.kt:83` stay valid through the defaults.

- [ ] **Step 1: Write failing tests**, with a catalog built from USD ("US Dollar"), EUR and HUF:
  - `huNamesAreLocalised`: locale `hu-HU`, `get("USD")!!.name != "US Dollar"` and `"dollár" in name.lowercase()`.
  - `huSearchFindsBothNames`: `search("dollár")` and `search("dollar")` both contain USD.
  - `englishKeepsBundledNames`: locale `en-US`, the name is `"US Dollar"`.
  - `untranslatedLanguageKeepsEnglish`: locale `de-DE`, the name is `"US Dollar"`.
  - `localeIsReadPerCall`: a mutable locale var switches from en to hu between calls, and the name changes.
  - `unknownCodeFallsBack`: a fiat entry `XTS` or a made-up `"ZZZ"` keeps its English name in hu.
  - `formatterStillFollowsRegion`: `AmountFormatter.forLocale(Locale.forLanguageTag("hu-HU"))` formats `1234.5 HUF` with `,`. This pins that the formatter stays tied to the region.
- [ ] **Step 2: Run** `--tests '*CurrencyCatalogTest'`. Expected: the hu tests FAIL, because the names stay English.
- [ ] **Step 3: Implement.**
  - The name comes from `runCatching { java.util.Currency.getInstance(code).getDisplayName(loc) }`. Use it only if it isn't blank and isn't equal to the code, ignoring case.
  - Only use it when `loc.language in localizedLanguages`.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: all pass.
- [ ] **Step 5: Commit** `Hungarian currency names`.

### Task 8: Store listing, release 0.2.3 and the phone check

**Files:**
- Create: `fastlane/metadata/android/hu-HU/title.txt` ("Ticker"), `short_description.txt` (80 characters or fewer), `full_description.txt` (translates the en-US file, same list structure), `changelogs/53.txt`
- Modify: `app/build.gradle.kts`, `en-US/changelogs/53.txt`, the fdroid yml

- [ ] **Step 1: Write the files.**
  - en `53.txt`: `Ticker now speaks Hungarian. It follows your phone's language; on Android 13 and newer you can also pick it per app in system settings. Currency names and chart dates follow the language too.`
  - Hungarian `53.txt`: the same in Hungarian.
  - Set versionCode 5 and versionName "0.2.3".
  - Add a line to en `full_description.txt`: `Available in English and Hungarian.`
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest :app:lintDebug`. Expected: tests pass. Lint shows no `MissingTranslation` or `ExtraTranslation` errors and no new errors.
- [ ] **Step 3: Phone check** (ask the user first). We don't change the phone language.
  - Use the per-app locale: `adb shell cmd locale set-app-locales io.github.szlukabence.ticker --locales hu-HU`. This changes only Ticker's own setting, not the phone.
  - Screenshot Convert, Charts (both a short and a long range), Camera, Receipt, About, the currency picker with a "dollár" search, the theme menu and the widget.
  - Check each screen for clipped or overflowing text (Review Focus 1). Fix it with shorter Hungarian.
  - Reset with `--locales ""` afterwards, and confirm the app is English again.
- [ ] **Step 4: Hand over for review.** Send the user `values-hu/strings.xml` and the hu-HU fastlane files (SendUserFile) for their native-speaker review. Apply their corrections and run the tests again.
- [ ] **Step 5: Commit** `Hungarian (0.2.3)`.

### Final

- Whole-branch review by `buildflow:buildflow-reviewer` (Opus, high), covering the diff since `66c99b6`, plus the spec and this plan's Review Focus.
- One fix pass, using TDD.
- Then use finishing-a-development-branch. Tagging `v0.2.1`, `v0.2.2` and `v0.2.3` on their release commits, and pushing, each need the user's explicit OK.
