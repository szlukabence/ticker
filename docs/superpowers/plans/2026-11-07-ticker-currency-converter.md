# Ticker Currency Converter Implementation Plan

> **Update 2026-11-08:** Binance was removed before publishing (its data terms do not clearly allow use in a public app). Crypto prices now come from the fawazahmed0 API, updated daily, and charts have no intraday range. Parts of this document that describe Binance are historical.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build "Ticker", a free, Google-free Android currency converter. It has a calculator-style favourites screen, fiat and crypto rates fetched fresh on every launch, pair charts, and camera price conversion with Tesseract.

**Architecture:**
- **Structure:** one Gradle module `:app`, one Activity, three Compose tabs. Manual dependency injection through `AppContainer`.
- **Pure Kotlin core (`core/`):** maths, parsing and formatting. It has no Android dependencies and is fully unit-tested on the JVM.
- **Repositories (`data/repo`):** the only code that touches the network (Retrofit) or storage (Room, DataStore). They expose `StateFlow`s to ViewModels.
- **OCR (`ocr/`):** split into pure, testable steps (binarise, pick, parse, stabilise) wrapped around one Tesseract call.

**Tech Stack:**
- Build: Kotlin 2.4.21, AGP 9.5.1 (built-in Kotlin), KSP 2.3.13.
- AndroidX: Compose BOM 2026.09.00, Navigation Compose 2.10.2, Lifecycle 2.11.0, Room 2.8.5, DataStore 1.3.0, CameraX 1.6.2.
- Networking: Retrofit 3.0.0, OkHttp 5.5.1, kotlinx.serialization 1.12.0.
- UI libraries: Vico 3.3.1 (charts), sh.calvin.reorderable 3.1.0 (drag to reorder).
- OCR: tesseract4android 4.9.0 (JitPack).
- Tests: JUnit 4.13.2, kotlinx-coroutines-test 1.11.0, MockWebServer 5.5.1.

**Spec:** `docs/superpowers/specs/2026-11-07-ticker-currency-converter-design.md`. Read it alongside this plan. Section numbers below (§) refer to it.

## Global Constraints

- **App and package:** applicationId and root package `dev.ticker.converter`. App label "Ticker".
- **SDK:** minSdk 26, targetSdk 36, compileSdk 36.
- **Permissions:** `INTERNET` and `CAMERA` only. No Google Play Services, Firebase, analytics or ads. Every dependency must be open source.
- **Money maths:** `BigDecimal` with `MathContext.DECIMAL64`. Never use `Double` for amounts. `Double` is allowed only for percentages shown in the UI.
- **Rate model:** rates are stored as USD per 1 unit (`usdPer`). USDT = 1 USD exactly. Currency codes are always UPPERCASE inside the app.
- **Source precedence per currency:** Binance (crypto) → Frankfurter (fiat) → fawazahmed0.
- **Network:** 10 s timeout per source. The fawazahmed0 archive starts on 2024-03-02.
- **Copy (exact strings):**
  - `LIVE · SYNC hh:mm:ss`
  - `PARTIAL · SYNC hh:mm:ss`
  - `OFFLINE · last sync <relative>`
  - `NO DATA`
  - `Connect to the internet to load rates`
  - `Point the box at a price`
  - `<amount> <CODE> · tap to open in Convert`
- **Theme (Midnight Ticker):**
  - Colours: background `#000000`, text `#E6E6E6`, accent `#2BFF88`, negative `#FF4D6A`, warning (amber) `#FFB020`, dividers `#161616`.
  - Fonts: numbers in JetBrains Mono, headings in Space Grotesk (both bundled, SIL Open Font License).
- **Number formatting:**
  - Thousands grouped with a thin space U+2009.
  - The decimal separator follows the locale.
  - `|x| ≥ 1`: fiat uses its minor units, crypto uses 2 decimals.
  - `|x| < 1`: 4 significant digits, at most 10 decimals.
- **Toolchain:**
  - Java comes from mise. The project `.mise.toml` pins `java = "temurin-21"`. If `java` isn't found, prefix commands with `mise exec --`.
  - The SDK is at `~/Android/Sdk`, referenced in `local.properties`, which is gitignored.
  - The machine has 7.5 GB RAM, so set `org.gradle.jvmargs=-Xmx2g` and `kotlin.daemon.jvmargs=-Xmx1g`.
- **Git:** the repo is local only. Never push. Never commit `local.properties`, keystores or `.superpowers/`.

## Review Focus

1. **Comma-decimal locales (hu_HU, de_DE).**
   - Expected: the keypad's decimal key is labelled "," and appends "." internally. The expression line displays ",", and results format with ",".
   - Pinned by: `ConvertViewModelTest.decimalKeyUsesLocaleSeparatorForDisplay` (Task 10) and `AmountFormatterTest.commaLocale` (Task 3).
2. **Very long or huge input.**
   - Expected: typing past 15 significant digits in one number is ignored, and huge results never crash or show scientific notation.
   - Pinned by: `ExpressionEvaluatorTest.rejectsSixteenthDigit` (Task 2) and `AmountFormatterTest.hugeValuePlain` (Task 3).
3. **Weekend and holiday fiat data.**
   - Expected: on a Monday, the previous value is Friday's (the previous *available* date), not a missing Sunday, so no row shows 0% or "—".
   - Pinned by: `RatesRepositoryTest.fiatPrevUsesPreviousAvailableDate` (Task 7).
4. **Crypto ticker that matches a fiat ISO code.**
   - Expected: the fiat currency wins, and the crypto with that ticker is left out of the catalog. Rates never cross-contaminate.
   - Pinned by: `CurrencyCatalogTest.cryptoCollidingWithFiatIsDropped` (Task 1) and `RatesRepositoryTest.binanceNeverOverridesFiat` (Task 7).
5. **App backgrounded while on Convert.**
   - Expected: the 30 s crypto polling stops, so there is no network use in the background, and resumes with an immediate refresh on return.
   - Pinned by: `CryptoPollerTest.stopsWhenNotActive` (Task 7).

---

## File Map

```
.mise.toml  .gitignore  settings.gradle.kts  build.gradle.kts  gradle.properties  gradle/libs.versions.toml
README.md  PRIVACY.md
app/build.gradle.kts  app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/assets/currencies.json            fiat catalog (ISO code, name, flag, decimals)
app/src/main/assets/crypto_names.json          ~200 ticker→name
app/src/main/assets/tessdata/eng.traineddata   tessdata_fast
app/src/main/res/font/                          jetbrains_mono_*.ttf, space_grotesk_*.ttf (+ OFL.txt in assets/licenses)
app/src/main/java/dev/ticker/converter/
  TickerApp.kt                 Application: builds AppContainer, ProcessLifecycle refresh hook
  AppContainer.kt              manual DI
  MainActivity.kt              edge-to-edge, theme, TickerNavHost
  core/model/Models.kt         Currency, CurrencyKind, RateSnapshot, RateSource, SyncStatus, PricePoint, ChartRange, Settings, SavedPair
  core/math/ExpressionEvaluator.kt
  core/math/Converter.kt
  core/math/AmountFormatter.kt
  core/math/PriceParser.kt
  core/math/SeriesMath.kt
  data/catalog/CurrencyCatalog.kt
  data/remote/Dtos.kt  FrankfurterApi.kt  BinanceApi.kt  FawazClient.kt  RemoteSources.kt  Network.kt
  data/local/TickerDatabase.kt  Entities.kt  Daos.kt  RoomStores.kt  SettingsRepository.kt
  data/repo/RatesRepository.kt  CryptoPoller.kt  HistoryRepository.kt
  ocr/ImagePreprocessor.kt  PriceCandidatePicker.kt  ReadingStabilizer.kt  OcrEngine.kt  PriceFrameAnalyzer.kt
  ui/theme/Theme.kt  Type.kt
  ui/components/CurrencyRow.kt  Keypad.kt  SyncBadge.kt  CurrencyPicker.kt  BottomBar.kt
  ui/nav/TickerNavHost.kt
  ui/convert/ConvertViewModel.kt  ConvertScreen.kt
  ui/charts/ChartsViewModel.kt  ChartsScreen.kt  PriceChart.kt
  ui/camera/CameraViewModel.kt  CameraScreen.kt
app/src/test/java/dev/ticker/converter/...      mirrors main; fixtures in app/src/test/resources/fixtures/
app/src/androidTest/java/dev/ticker/converter/  DaoTest.kt, OcrRegressionTest.kt
```

---

### Task 0: Project scaffold that builds

**Files:**
- Create: `.mise.toml`, `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `local.properties`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `TickerApp.kt`, `MainActivity.kt`, `ui/nav/TickerNavHost.kt`, `ui/components/BottomBar.kt`, `ui/theme/Theme.kt`, `ui/theme/Type.kt`, `res/font/*`, `assets/licenses/OFL-*.txt`
- Test: `app/src/test/java/dev/ticker/converter/SmokeTest.kt`

**Interfaces:**
- Produces:
  - `@Composable fun TickerTheme(content: @Composable () -> Unit)`
  - `object TickerColors { val Bg; val Text; val Accent; val Negative; val Warning; val Divider; val Dim = Color(0xFF777777) }`
  - `val MonoFamily: FontFamily` and `val HeadFamily: FontFamily`
  - `enum class Tab(val route: String, val label: String) { CONVERT("convert","CONVERT"), CHARTS("charts","CHARTS"), CAMERA("camera","CAMERA") }`
  - `@Composable fun TickerNavHost(container: AppContainer)`. In this task it shows placeholder `Text(tab.label)` screens.

- [ ] **Step 1:** Run `git init` in the project root.
  - Write `.gitignore` covering `.gradle/`, `build/`, `local.properties`, `.superpowers/`, `*.jks`, `.idea/` and `captures/`.
  - Write `.mise.toml` with `[tools] java = "temurin-21"`.
  - Write `local.properties` with `sdk.dir=/home/<you>/Android/Sdk`.
- [ ] **Step 2:** Generate the Gradle wrapper:
  - Command: `gradle wrapper --gradle-version 9.9.0`. The mise-installed Gradle 9.6 is enough to run it.
  - If AGP 9.5.1 reports a different minimum Gradle version, use that version instead.
- [ ] **Step 3:** Write `gradle/libs.versions.toml` with every version from the Tech Stack line, plus:
  - Plugins: `com.android.application`, `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`, `com.google.devtools.ksp`.
  - Do **not** apply `org.jetbrains.kotlin.android`, because AGP 9 has built-in Kotlin.
  - In `settings.gradle.kts`, add `maven("https://jitpack.io")` to `dependencyResolutionManagement`, after `google()` and `mavenCentral()`.
- [ ] **Step 4:** Write `app/build.gradle.kts`:
  - SDK levels from Global Constraints. `buildFeatures.compose = true`.
  - Java and Kotlin target 17.
  - `testOptions.unitTests.isReturnDefaultValues = true`.
  - All dependencies declared now, so later tasks don't touch the build file except Task 14.
- [ ] **Step 5:** Fonts and theme:
  - Download JetBrains Mono (Regular, SemiBold) and Space Grotesk (Regular, SemiBold, Bold) TTFs from `github.com/google/fonts/tree/main/ofl/`, plus their `OFL.txt` files. Put the fonts in `res/font/` with lowercase snake-case names and the licences in `assets/licenses/`.
  - Implement `TickerTheme`: a dark `ColorScheme` built from `TickerColors`, and `Typography` using `MonoFamily` for body and display and `HeadFamily` for titles.
- [ ] **Step 6:** App shell:
  - `MainActivity` calls `enableEdgeToEdge()` and `setContent { TickerTheme { TickerNavHost(container) } }`.
  - `TickerApp` holds `lateinit var container: AppContainer`. Use an empty `class AppContainer(app: Application)` for now.
  - `BottomBar` follows mockup tab styling: an active pill `Accent` at 20% alpha and a label in `Accent`.
- [ ] **Step 7:** Write `SmokeTest.kotlinWorks()` asserting `1 + 1 == 2`.
- [ ] **Step 8:** Run `./gradlew :app:assembleDebug :app:testDebugUnitTest`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 9:** Commit: `git add -A && git commit -m "chore: scaffold Ticker Android project"`

---

### Task 1: Models and currency catalog

**Files:**
- Create: `core/model/Models.kt`, `data/catalog/CurrencyCatalog.kt`, `assets/currencies.json`, `assets/crypto_names.json`
- Test: `data/catalog/CurrencyCatalogTest.kt`

**Interfaces:**
- Produces (in `Models.kt`):
  ```kotlin
  enum class CurrencyKind { FIAT, CRYPTO }
  data class Currency(val code: String, val name: String, val kind: CurrencyKind, val glyph: String, val decimals: Int)
  enum class RateSource { BINANCE, FRANKFURTER, FAWAZ }
  data class RateSnapshot(
      val usdPer: Map<String, BigDecimal>, val usdPerPrev: Map<String, BigDecimal>,
      val sourceFor: Map<String, RateSource>, val fetchedAt: Instant)
  sealed interface SyncStatus {
      data object Syncing : SyncStatus; data object NoData : SyncStatus
      data class Live(val at: Instant) : SyncStatus
      data class Partial(val at: Instant, val failed: Set<RateSource>) : SyncStatus
      data class Offline(val lastSync: Instant) : SyncStatus }
  data class PricePoint(val time: Instant, val value: BigDecimal)
  enum class ChartRange { D1, W1, M1, M3, Y1, Y5, MAX }
  data class SavedPair(val base: String, val quote: String)
  @Serializable data class Settings(
      val favorites: List<String> = emptyList(), val activeCode: String? = null, val expression: String = "100",
      val cameraFrom: String? = null, val chartBase: String? = null, val chartQuote: String? = null,
      val savedPairs: List<SavedPair> = emptyList(), val initialized: Boolean = false)
  ```
  (`SavedPair` is `@Serializable` too.)
- Produces (in `CurrencyCatalog.kt`):
  ```kotlin
  class CurrencyCatalog(fiat: List<Currency>, cryptoNames: Map<String, String>) {
      fun get(code: String): Currency?
      fun all(): List<Currency>                       // fiat sorted by code, then crypto sorted by code
      fun search(query: String): List<Currency>       // case-insensitive prefix match on code, contains match on name; code matches first
      fun registerCryptos(codes: Collection<String>)  // adds Binance-discovered cryptos; skips codes that are fiat
      fun isFiat(code: String): Boolean
      fun defaultFavorites(locale: Locale): List<String>
      companion object { fun fromAssets(context: Context): CurrencyCatalog }
  }
  ```
  - A crypto's `glyph` is its ticker's first character, or "₿" for BTC and "Ξ" for ETH. Its `decimals` is 8, and its name is `cryptoNames[code] ?: code`.

- [ ] **Step 1:** Generate `currencies.json`:
  - Run a one-off script in the scratchpad (not committed) over ISO 4217 data (the JDK's `java.util.Currency.getAvailableCurrencies()` gives code, display name and fraction digits).
  - Flag emoji: derived from the first two letters of the code as regional indicators. Use 🇪🇺 for EUR and 🏳 for codes with no country (XAU, XDR and so on).
  - Format: `[{"code":"HUF","name":"Hungarian Forint","flag":"🇭🇺","decimals":2},…]`.
  - Exclude fund/test codes (`XTS`, `XXX`, and any code whose display name contains "Fund").
- [ ] **Step 2:** Write `crypto_names.json` with roughly the top 200 coins, taking names from the fawazahmed0 `currencies.json` endpoint for codes that aren't fiat.
- [ ] **Step 3:** Write the failing tests in `CurrencyCatalogTest`. Build the catalog directly with a small list; no assets needed.
  - `getIsCaseInsensitive`: `get("huf")?.code == "HUF"`.
  - `searchCodePrefixRanksFirst`: `search("eu").first().code == "EUR"`.
  - `searchMatchesName`: `search("forint").map{it.code} == listOf("HUF")`.
  - `cryptoCollidingWithFiatIsDropped`: `registerCryptos(listOf("HUF","BTC"))` gives `get("HUF")!!.kind == FIAT` and `get("BTC")!!.kind == CRYPTO`.
  - `unknownCryptoUsesTickerAsName`: `registerCryptos(listOf("ZZZ"))` gives `get("ZZZ")!!.name == "ZZZ"`.
  - `defaultFavoritesHungary`: `defaultFavorites(Locale("hu","HU")) == listOf("HUF","USD","EUR","GBP","BTC","ETH")`.
  - `defaultFavoritesUsDeduped`: `defaultFavorites(Locale.US) == listOf("USD","EUR","GBP","BTC","ETH")`.
- [ ] **Step 4:** Run `./gradlew :app:testDebugUnitTest --tests '*CurrencyCatalogTest'`. Expected: FAIL (unresolved).
- [ ] **Step 5:** Implement `Models.kt` and `CurrencyCatalog`. `fromAssets` parses both JSON files with kotlinx.serialization.
- [ ] **Step 6:** Run the same command. Expected: PASS.
- [ ] **Step 7:** Commit: `feat: currency models and catalog`.

---

### Task 2: ExpressionEvaluator

**Files:**
- Create: `core/math/ExpressionEvaluator.kt`
- Test: `core/math/ExpressionEvaluatorTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object ExpressionEvaluator {
      const val OPS = "+−×÷"                             // U+2212 minus, U+00D7, U+00F7
      fun evaluate(expr: String): BigDecimal?            // null = empty or division by zero
      fun append(expr: String, key: Char): String        // key ∈ '0'..'9', '.', or an OPS char; enforces input rules
      fun backspace(expr: String): String
  }
  ```

**Input rules enforced by `append`:**
- **Leading zero:** a leading "0" followed by a digit replaces the zero ("0"+"5" → "5").
- **Decimal point:** at most one "." per number. "." at the start of a number becomes "0.".
- **Operators:**
  - An operator typed after another operator replaces it ("5+" + "×" → "5×").
  - An operator on an empty expression is ignored, except "−", which starts a negative number.
- **Number length:** at most 15 significant digits per number; any further digit is ignored.

- [ ] **Step 1:** Write the failing tests:
  - `precedence`: `evaluate("2+3×4") == BigDecimal("14")`.
  - `leftToRight`: `evaluate("20÷4÷5")` compares equal to `1`.
  - `trailingOperatorIgnored`: `evaluate("25×") == BigDecimal("25")`.
  - `divisionByZeroIsNull`: `evaluate("5÷0") == null`.
  - `emptyIsNull`: `evaluate("") == null`.
  - `decimalExact`: `evaluate("0.1+0.2")` compares equal to `BigDecimal("0.3")`.
  - `negativeStart`: `evaluate("−5+2")` compares equal to `-3`.
  - `appendReplacesOperator`: `append("5+", '×') == "5×"`.
  - `appendLeadingZero`: `append("0", '5') == "5"`.
  - `appendSecondDotIgnored`: `append("1.5", '.') == "1.5"`.
  - `appendDotStartsZero`: `append("5+", '.') == "5+0."`.
  - `rejectsSixteenthDigit`: append '9' sixteen times to "" and the result's length is 15.
  - `backspace`: `backspace("12+") == "12"` and `backspace("") == ""`.
- [ ] **Step 2:** Run `--tests '*ExpressionEvaluatorTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement it. Tokenise into numbers and ops, then do one pass for ×/÷ and a second for +/−. Use `divide(x, MathContext.DECIMAL64)`.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5:** Commit: `feat: keypad expression evaluator`.

---

### Task 3: Converter and AmountFormatter

**Files:**
- Create: `core/math/Converter.kt`, `core/math/AmountFormatter.kt`
- Test: `core/math/ConverterTest.kt`, `core/math/AmountFormatterTest.kt`

**Interfaces:**
- Consumes: `RateSnapshot`, `Currency` (Task 1).
- Produces:
  ```kotlin
  object Converter {
      fun convert(amount: BigDecimal, from: String, to: String, s: RateSnapshot): BigDecimal?   // null if a rate is missing
      fun dailyChangePct(code: String, against: String, s: RateSnapshot): Double?          // spec §5.1 formula ×100; null if any value missing
  }
  class AmountFormatter(private val decimalSeparator: Char) {
      fun format(value: BigDecimal, c: Currency): String      // display, grouped with U+2009
      fun plain(value: BigDecimal, c: Currency): String       // same rounding, "." separator, no grouping (fed back into the keypad expression)
      companion object { fun forLocale(l: Locale): AmountFormatter }
  }
  ```

- [ ] **Step 1:** Write the failing tests. Use a fixture snapshot with: USD=1, EUR=1.1165, HUF=0.0030681, BTC=75933.63. For prev: EUR=1.1133, HUF=0.0030350, BTC=76124.99.
  - `ConverterTest.eurToHuf`: convert 100 EUR→HUF and round to 0 decimals → `36391` (100×1.1165/0.0030681).
  - `ConverterTest.missingRateNull`: convert to "XYZ" gives null.
  - `ConverterTest.dailyChangeAgainstActive`: `dailyChangePct("HUF","EUR",s)` ≈ `(0.0030681/1.1165)/(0.0030350/1.1133)×100−100`, within 1e-6.
  - `AmountFormatterTest.groupsWithThinSpace`: 39540 HUF (decimals 2) gives `"39 540.00"` (thin space).
  - `AmountFormatterTest.commaLocale`: with the `','` formatter, 115.62 USD gives `"115,62"`.
  - `AmountFormatterTest.smallValueSigDigits`: 0.0010943217 BTC gives `"0.001094"`.
  - `AmountFormatterTest.cryptoAboveOneTwoDecimals`: 3.21456 ETH gives `"3.21"`.
  - `AmountFormatterTest.zeroDecimalsCurrency`: 1290 JPY (decimals 0) gives `"1 290"`.
  - `AmountFormatterTest.hugeValuePlain`: 1e18 USD gives `"1 000 000 000 000 000 000.00"`, with no "E".
  - `AmountFormatterTest.plainForKeypad`: `plain(BigDecimal("39540.004"), HUF) == "39540"`. It strips trailing zeros after rounding.
- [ ] **Step 2:** Run `--tests '*ConverterTest' --tests '*AmountFormatterTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement both. Use `toPlainString()`, never `toString()`. Round with `RoundingMode.HALF_UP`.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5:** Commit: `feat: conversion and amount formatting`.

---

### Task 4: PriceParser

**Files:**
- Create: `core/math/PriceParser.kt`
- Test: `core/math/PriceParserTest.kt`

**Interfaces:**
- Produces: `object PriceParser { fun parse(raw: String): BigDecimal? }`. The input is OCR text that may contain spaces, U+00A0, U+2009, `'`, `’`, `.` and `,`. The rules are in spec §7 step 6. It returns null for unparseable input or 0.

- [ ] **Step 1:** Write a failing parameterised test `PriceParserTest.table` (JUnit4 `Parameterized`) with these rows (input → expected; null means null):
  - `"1 290"`→1290, `"1 290"` (NBSP)→1290, `"1.290"`→1290, `"1,290"`→1290
  - `"12.99"`→12.99, `"12,99"`→12.99, `"12,5"`→12.5, `"1.290,00"`→1290.00, `"1,290.00"`→1290.00
  - `"1'299.00"`→1299.00, `"1’299"`→1299, `"1.234.567"`→1234567, `"1,234,567.89"`→1234567.89
  - `"0,99"`→0.99, `".99"`→0.99, `"990"`→990, `" 45 "`→45, `"45."`→45, `"45,-"`→45
  - `"0"`→null, `"0,00"`→null, `""`→null, `"."`→null, `",,"`→null, `"abc"`→null
  - `"12.3.4,5"`→123.45 (both separators present, so the last one is the decimal)
  - `"1 2"`→12, `"100 000"`→100000, `"9.999"`→9999, `"9.99"`→9.99
- [ ] **Step 2:** Run `--tests '*PriceParserTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement it:
  - Strip every character except digits and `.,'’ ` (plus U+00A0 and U+2009), then trim. Strip trailing separators and a trailing "-".
  - Apply the spec §7 rules in order.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5:** Commit: `feat: price text parser`.

---

### Task 5: Remote sources

**Files:**
- Create: `data/remote/Dtos.kt`, `FrankfurterApi.kt`, `BinanceApi.kt`, `FawazClient.kt`, `RemoteSources.kt`, `Network.kt`
- Test: `data/remote/RemoteSourcesTest.kt`. Fixtures in `app/src/test/resources/fixtures/`:
  - `frankfurter_latest.json`, `frankfurter_series.json`
  - `fawaz_usd.json`, trimmed to about 10 currencies including `btc` and `huf`
  - `binance_prices.json`, `binance_mini.json`, `binance_klines.json`

**Interfaces:**
- Produces (in `RemoteSources.kt`, used by the repositories):
  ```kotlin
  data class DatedRates(val date: LocalDate, val unitsPerUsd: Map<String, BigDecimal>)   // UPPERCASE codes
  interface EcbSource {
      suspend fun latest(): DatedRates
      suspend fun series(start: LocalDate, end: LocalDate, codes: Collection<String>): List<DatedRates>  // ascending by date
  }
  interface FawazSource {
      suspend fun latest(): DatedRates
      suspend fun on(date: LocalDate): DatedRates
  }
  interface CryptoSource {
      suspend fun allUsdtPrices(): Map<String, BigDecimal>                 // "BTC" -> 75933.63 (strips USDT suffix; skips USDT itself and leveraged UP/DOWN/BULL/BEAR tokens)
      suspend fun prices(codes: Collection<String>): Map<String, BigDecimal>
      suspend fun open24h(codes: Collection<String>): Map<String, BigDecimal>
      suspend fun klines(code: String, interval: String, start: Instant): List<PricePoint>   // close prices
  }
  class RetrofitEcbSource(api: FrankfurterApi) : EcbSource
  class HttpFawazSource(client: OkHttpClient, json: Json, baseUrls: List<String> = FAWAZ_URLS) : FawazSource
  class RetrofitCryptoSource(api: BinanceApi) : CryptoSource
  object Network { fun okHttp(): OkHttpClient; fun json(): Json; fun frankfurter(base: String = "https://api.frankfurter.dev/"): FrankfurterApi; fun binance(base: String = "https://data-api.binance.vision/"): BinanceApi }
  ```
- **fawazahmed0 URL templates** (`{v}` is `latest` or `YYYY-MM-DD`), tried in order:
  - `https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@{v}/v1/currencies/usd.json`
  - `https://{v}.currency-api.pages.dev/v1/currencies/usd.json`
- **fawazahmed0 response:** its JSON is `{"date":…, "usd":{code:unitsPerUsd}}`. Parse it as a `JsonObject`, because the codes are dynamic keys.
- **Binance `symbols` parameter:** a JSON array string such as `["BTCUSDT","ETHUSDT"]`.
- **Errors:** every source throws `IOException` (or a subclass) on failure, including malformed JSON.
- **Timeouts:** OkHttp connect, read and call timeouts are all 10 s.

- [ ] **Step 1:** Save the fixtures by `curl`ing each endpoint once (they're listed in spec §11), trimming arrays to 3–10 entries. Keep one leveraged token (`BTCUPUSDT`) and `USDTUSDT`-like noise in `binance_prices.json` so the filtering gets tested.
- [ ] **Step 2:** Write the failing MockWebServer tests:
  - `ecbLatestParses`: `latest().unitsPerUsd["HUF"]` equals the fixture value, and `date` equals the fixture date.
  - `ecbSeriesAscending`: the dates are strictly ascending.
  - `fawazUppercasesAndFallsBack`: the first server returns 500 and the second returns the fixture, so `latest().unitsPerUsd["BTC"]` is non-null.
  - `cryptoStripsSuffixAndFilters`: the keys contain "BTC" and none end with "UP" or equal "USDT".
  - `cryptoOpen24h`: `open24h(listOf("BTC"))["BTC"] == BigDecimal(fixture openPrice)`.
  - `klinesMapsClose`: the first point's value equals kline index 4 and its time equals index 0 in ms.
  - `malformedJsonThrowsIOException`: a body of `"{"` throws `IOException`.
- [ ] **Step 3:** Run `--tests '*RemoteSourcesTest'`. Expected: FAIL.
- [ ] **Step 4:** Implement the DTOs, Retrofit interfaces and the three sources. Wrap `SerializationException` in `IOException`.
- [ ] **Step 5:** Run the tests. Expected: PASS.
- [ ] **Step 6:** Commit: `feat: remote rate sources`.

---

### Task 6: Local storage

**Files:**
- Create: `data/local/Entities.kt`, `Daos.kt`, `TickerDatabase.kt`, `RoomStores.kt`, `SettingsRepository.kt`
- Test: `data/local/SnapshotCodecTest.kt` (JVM). `androidTest/.../DaoTest.kt` (device; runs in Task 14).

**Interfaces:**
- Produces:
  ```kotlin
  interface SnapshotStore { suspend fun load(): RateSnapshot?; suspend fun save(s: RateSnapshot) }
  data class CachedSeries(val points: List<PricePoint>, val fetchedAt: Instant)
  interface HistoryStore { suspend fun load(key: String): CachedSeries?; suspend fun save(key: String, s: CachedSeries) }   // key = "BTC|M1"
  interface SettingsRepository { val settings: Flow<Settings>; suspend fun update(transform: (Settings) -> Settings) }
  class RoomSnapshotStore(dao: SnapshotDao) : SnapshotStore
  class RoomHistoryStore(dao: HistoryDao) : HistoryStore
  class DataStoreSettingsRepository(context: Context) : SettingsRepository   // one string preference key "settings_json"
  object SnapshotCodec { fun encode(s: RateSnapshot): String; fun decode(json: String): RateSnapshot }
  object SeriesCodec { fun encode(p: List<PricePoint>): String; fun decode(json: String): List<PricePoint> }
  ```
- Entities:
  - `SnapshotEntity(@PrimaryKey id: Int = 0, json: String)`
  - `HistoryEntity(@PrimaryKey key: String, json: String, fetchedAt: Long)`
- Encoding: BigDecimals as strings, Instants as epoch milliseconds.

- [ ] **Step 1:** Write the failing tests:
  - `SnapshotCodecTest.roundTrip`: `decode(encode(s)) == s`, including a `BigDecimal("0.0030681")` that keeps its scale.
  - `SeriesCodecTest.roundTrip`.
- [ ] **Step 2:** Run `--tests '*CodecTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement the codecs, the entities, the DAOs (`upsert`, `get`) and the database (version 1, `exportSchema = false`). Then the stores and `DataStoreSettingsRepository`. A missing or corrupt settings JSON yields `Settings()`.
- [ ] **Step 4:** Write `DaoTest` (instrumented, in-memory Room): saving and loading a snapshot works, and saving the same history key twice keeps the latest. It isn't run until a device is attached (Task 14).
- [ ] **Step 5:** Run `./gradlew :app:testDebugUnitTest --tests '*CodecTest' :app:compileDebugAndroidTestKotlin`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 6:** Commit: `feat: snapshot, history and settings storage`.

---

### Task 7: RatesRepository and CryptoPoller

**Files:**
- Create: `data/repo/RatesRepository.kt`, `data/repo/CryptoPoller.kt`
- Test: `data/repo/RatesRepositoryTest.kt`, `data/repo/CryptoPollerTest.kt`, `test/.../fakes/Fakes.kt` (FakeEcb, FakeFawaz, FakeCrypto, InMemorySnapshotStore, MutableClock)

**Interfaces:**
- Consumes: the sources (Task 5), `SnapshotStore` (Task 6), `CurrencyCatalog` (Task 1).
- Produces:
  ```kotlin
  class RatesRepository(
      private val ecb: EcbSource, private val fawaz: FawazSource, private val crypto: CryptoSource,
      private val store: SnapshotStore, private val catalog: CurrencyCatalog,
      private val clock: Clock, private val io: CoroutineDispatcher = Dispatchers.IO) {
      val snapshot: StateFlow<RateSnapshot?>
      val status: StateFlow<SyncStatus>
      suspend fun loadCached()                                  // fills snapshot from store; status = NoData if none, else Offline(fetchedAt) until a refresh finishes
      suspend fun refreshAll(favoriteCryptos: Collection<String>)
      suspend fun refreshCrypto(codes: Collection<String>)      // prices only; merges into current snapshot, keeps fetchedAt of fiat but updates status time
  }
  class CryptoPoller(private val repo: RatesRepository, private val periodMs: Long = 30_000) {
      suspend fun run(active: StateFlow<Boolean>, codes: () -> Collection<String>)   // while active: refreshCrypto immediately, then every periodMs
  }
  ```

**`refreshAll` algorithm:**
1. Set status to `Syncing`.
2. In parallel (`supervisorScope` + `async`), each call wrapped in `runCatching`:
   - `ecb.latest()`
   - `ecb.series(today−7d, today, ecbCodes)`, where today is the UTC date from `clock`
   - `fawaz.latest()`
   - `crypto.allUsdtPrices()`
   - `crypto.open24h(favoriteCryptos)`
3. After `fawaz.latest()` succeeds, call `fawaz.on(latest.date − 1 day)`. Its result provides `usdPerPrev` for currencies that only fawazahmed0 has.
4. `catalog.registerCryptos(binance keys)`.
5. Merge per code using the Global Constraints precedence:
   - Binance only for codes where `!catalog.isFiat(code)`. Frankfurter and fawazahmed0 rates convert as `usdPer = 1 / unitsPerUsd`. Always set `usdPer["USD"] = 1` and `usdPerPrev["USD"] = 1`.
   - Fiat prev: from the ECB series, the **last entry whose date is before `ecb.latest().date`**.
6. Codes missing from every fresh source keep their value from the previous snapshot (and its `sourceFor`).
7. Status:
   - all five calls succeeded → `Live(now)`
   - some failed → `Partial(now, failedSources)`
   - all failed → `Offline(prev.fetchedAt)`, or `NoData` if there's no previous snapshot
8. Save to the store only if at least one source succeeded.

- [ ] **Step 1:** Write the failing tests in `RatesRepositoryTest`, using `runTest` and fakes:
  - `allSourcesOkIsLive`: status is `Live`, `snapshot.usdPer["HUF"]` = 1/325.93 (DECIMAL64), `usdPer["BTC"]` = the Binance price, and the store's saved value equals the snapshot.
  - `binanceNeverOverridesFiat`: FakeCrypto returns `"HUF" to 999`, so `usdPer["HUF"]` still comes from ECB and `sourceFor["HUF"] == FRANKFURTER`.
  - `ecbDownUsesFawazForFiat`: ECB throws, so `sourceFor["HUF"] == FAWAZ` and the status is `Partial` with `FRANKFURTER` in `failed`.
  - `allDownWithCacheIsOffline`: the store has a snapshot with `fetchedAt = T0` and every source throws. Status is `Offline(T0)` and the snapshot is unchanged.
  - `allDownNoCacheIsNoData`.
  - `missingCodeKeepsPreviousValue`: the previous snapshot has "VND" and this time no source returns it, so it keeps its old value.
  - `fiatPrevUsesPreviousAvailableDate`: the series has Thu, Fri and Mon (latest), so `usdPerPrev["HUF"]` comes from Friday's value.
  - `refreshCryptoMergesPricesOnly`: after `refreshAll`, `refreshCrypto(["BTC"])` with a new price updates BTC and leaves HUF's object unchanged.
- [ ] **Step 2:** Write the failing tests in `CryptoPollerTest`, using `StandardTestDispatcher` and `advanceTimeBy`:
  - `pollsEvery30s`: active=true and advance 61 s, so `refreshCrypto` is called 3 times (t=0, 30, 60).
  - `stopsWhenNotActive`: set active to false at t=31 s and advance 120 s more, so the call count stays at 2.
  - `resumesImmediately`: flip active false→true and the count rises by 1 without advancing time.
- [ ] **Step 3:** Run `--tests '*RatesRepositoryTest' --tests '*CryptoPollerTest'`. Expected: FAIL.
- [ ] **Step 4:** Implement both. `CryptoPoller.run` uses `active.collectLatest { if (it) while (true) { runCatching { repo.refreshCrypto(codes()) }; delay(periodMs) } }`.
- [ ] **Step 5:** Run the tests. Expected: PASS.
- [ ] **Step 6:** Commit: `feat: rates repository with fallback and crypto polling`.

---

### Task 8: SeriesMath and HistoryRepository

**Files:**
- Create: `core/math/SeriesMath.kt`, `data/repo/HistoryRepository.kt`
- Test: `core/math/SeriesMathTest.kt`, `data/repo/HistoryRepositoryTest.kt`

**Interfaces:**
- Consumes: `EcbSource`, `FawazSource`, `CryptoSource`, `HistoryStore`, `CurrencyCatalog`, `RateSnapshot`.
- Produces:
  ```kotlin
  object SeriesMath {
      fun divide(base: List<PricePoint>, quote: List<PricePoint>): List<PricePoint>   // quote-per-base at each base timestamp; quote forward-filled; points before quote's first value dropped
      fun thin(points: List<PricePoint>, max: Int = 500): List<PricePoint>            // keeps first and last, evenly sampled
      data class Stats(val high: BigDecimal, val low: BigDecimal, val avg: BigDecimal, val change: BigDecimal, val changePct: Double)
      fun stats(points: List<PricePoint>): Stats?                                     // null if < 2 points
  }
  data class PairSeries(val points: List<PricePoint>, val stale: Boolean)
  interface PairHistory {   // extracted so ChartsViewModel can be tested with a fake (Task 11)
      fun availableRanges(base: String, quote: String, s: RateSnapshot): Set<ChartRange>
      suspend fun pairSeries(base: String, quote: String, range: ChartRange, s: RateSnapshot): PairSeries }
  class HistoryRepository(ecb: EcbSource, fawaz: FawazSource, crypto: CryptoSource, store: HistoryStore,
                          catalog: CurrencyCatalog, clock: Clock) : PairHistory {
      fun availableRanges(base: String, quote: String, s: RateSnapshot): Set<ChartRange>
      suspend fun pairSeries(base: String, quote: String, range: ChartRange, s: RateSnapshot): PairSeries   // throws IOException only if nothing cached
  }
  ```

**Range table** (start = now − span; crypto interval per spec §6.2):

| Range | Span | Crypto interval |
|---|---|---|
| D1 | 1 day | `5m` |
| W1 | 7 days | `1h` |
| M1 | 30 days | `4h` |
| M3 | 90 days | `1d` |
| Y1 | 365 days | `1d` |
| Y5 | 1826 days | `1w` |
| MAX | start = 2010-01-01 for crypto, 1999-01-04 for ECB | `1w` |

**Per-currency USD series (`usdSeries(code, range)`):**
- **USD:** two points with the value 1, at the start and now.
- **Crypto:** klines.
- **ECB fiat** (`s.sourceFor[code] == FRANKFURTER`): `ecb.series` daily, inverted.
- **Other fiat:** fawazahmed0 `on(date)` at ≤ 30 evenly spaced dates no earlier than 2024-03-02, inverted. Dates that fail are skipped.
- **Caching:** results are cached per `"$code|$range"`. TTLs: D1 5 min, W1 and M1 1 h, everything else 24 h.

**`availableRanges`:**
- D1 only if either side is crypto.
- Y5 and MAX are removed if either side is fiat with a non-FRANKFURTER source.

- [ ] **Step 1:** Write the failing tests in `SeriesMathTest`:
  - `divideForwardFillsWeekend`: base points Fri, Sat and Sun with quote only on Fri give 3 points, all using Friday's quote.
  - `divideDropsBeforeQuoteStart`.
  - `thinKeepsEnds`: 2000 points → 500 points, with the first and last kept.
  - `thinNoopWhenSmall`.
  - `statsValues`: points [10, 12, 8, 11] give high 12, low 8, avg 10.25, change 1, changePct 10.0.
- [ ] **Step 2:** Write the failing tests in `HistoryRepositoryTest`:
  - `cryptoFiatCrossPair`: BTC/HUF M1 calls `klines("BTC","4h",…)` and `ecb.series`, and each result point = btcUsd ÷ hufUsd.
  - `d1OnlyWithCrypto`: `availableRanges("EUR","HUF",s)` doesn't contain D1, and `availableRanges("BTC","HUF",s)` does.
  - `nonEcbFiatLimitedToY1`: VND sourced from FAWAZ, so Y5 and MAX are not available.
  - `fawazSampledAtMost30Requests`: VND Y1 makes ≤ 30 calls to `fawaz.on`, and every date is ≥ 2024-03-02.
  - `cacheHitWithinTtl`: a second call within 1 h for M1 makes no new source calls.
  - `failureFallsBackToStaleCache`: a cache older than the TTL plus a throwing source gives `stale = true` and the cached points.
  - `failureWithoutCacheThrows`.
- [ ] **Step 3:** Run `--tests '*SeriesMathTest' --tests '*HistoryRepositoryTest'`. Expected: FAIL.
- [ ] **Step 4:** Implement both.
- [ ] **Step 5:** Run the tests. Expected: PASS.
- [ ] **Step 6:** Commit: `feat: pair history with caching and cross-pair alignment`.

---

### Task 9: AppContainer wiring and shared UI components

**Files:**
- Create: `AppContainer.kt` (replaces the stub), `ui/components/CurrencyRow.kt`, `Keypad.kt`, `SyncBadge.kt`, `CurrencyPicker.kt`
- Modify: `TickerApp.kt`
- Test: `ui/components/SyncBadgeTextTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class AppContainer(app: Application) {
      val catalog: CurrencyCatalog; val rates: RatesRepository; val poller: CryptoPoller; val history: HistoryRepository
      val settings: SettingsRepository; val formatter: AmountFormatter  // AmountFormatter.forLocale(Locale.getDefault())
      val cameraHandoff: MutableSharedFlow<CameraHandoff>               // extraBufferCapacity = 1
      val appScope: CoroutineScope                                      // SupervisorJob + Dispatchers.Default
  }
  data class CameraHandoff(val code: String, val amount: BigDecimal)
  fun syncBadgeText(status: SyncStatus, now: Instant, zone: ZoneId): String    // pure; exact copy from Global Constraints
  @Composable fun SyncBadge(status: SyncStatus)          // green for Live, amber for Partial/Offline, dim for NoData/Syncing ("SYNCING…")
  @Composable fun CurrencyRow(currency: Currency, amount: String, expression: String?, changePct: Double?, active: Boolean, missing: Boolean, editing: Boolean, onClick: () -> Unit, onRemove: () -> Unit, dragHandle: Modifier)
  sealed interface Key { data class Digit(val d: Char) : Key; data object Dot : Key; data class Op(val op: Char) : Key; data object Backspace : Key; data object Clear : Key }
  @Composable fun Keypad(decimalLabel: Char, onKey: (Key) -> Unit)   // long-press ⌫ emits Clear
  @Composable fun CurrencyPicker(catalog: CurrencyCatalog, exclude: Set<String>, onPick: (String) -> Unit, onDismiss: () -> Unit)   // full-screen dialog, search field, sections "FIAT" / "CRYPTO"
  ```
- Behaviour in `TickerApp.onCreate`:
  - Build the container.
  - In `appScope`, call `rates.loadCached()`.
  - Register a `ProcessLifecycleOwner` observer that, on `ON_START`, launches `rates.refreshAll(favorite cryptos from current settings)`.

- [ ] **Step 1:** Write the failing tests in `SyncBadgeTextTest`:
  - `live`: `Live(09:41:07 local)` gives `"LIVE · SYNC 09:41:07"`.
  - `offlineRelative`: `Offline(now − 2h)` gives `"OFFLINE · last sync 2h ago"`. Use "m ago" under 1 h and "d ago" from 24 h up.
  - `partial`, `noData`.
- [ ] **Step 2:** Run `--tests '*SyncBadgeTextTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement `syncBadgeText` and the composables:
  - `CurrencyRow` matches the approved mockup:
    - active: 2 dp left border in `Accent`, background `#06150D`, amount in `Accent`
    - change shown as `+0.11%` (green) or `−1.85%` (red)
    - missing: amount "—" with alpha 0.4
  - Keypad layout per spec §6.1. Key background `#0D0D0D`, 1 dp border `#1D1D1D`, 6 dp corners. Operators in `Accent`; the "+" key is filled `Accent` with black text.
  - Add a `@Preview` for each component.
- [ ] **Step 4:** Run `./gradlew :app:testDebugUnitTest --tests '*SyncBadgeTextTest' :app:assembleDebug`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit: `feat: app container and shared components`.

---

### Task 10: Convert screen

**Files:**
- Create: `ui/convert/ConvertViewModel.kt`, `ui/convert/ConvertScreen.kt`
- Modify: `ui/nav/TickerNavHost.kt` (wire the Convert route)
- Test: `ui/convert/ConvertViewModelTest.kt`, `test/.../MainDispatcherRule.kt`, `test/.../fakes/FakeSettingsRepository.kt`

**Interfaces:**
- Consumes:
  - `RatesRepository`, `CryptoPoller`, `SettingsRepository`, `CurrencyCatalog`, `AmountFormatter`
  - `AppContainer.cameraHandoff`, `Key`, `ExpressionEvaluator`, `Converter`
- Produces:
  ```kotlin
  data class ConvertRow(val currency: Currency, val amount: String, val changePct: Double?, val missing: Boolean)
  data class ConvertUiState(val rows: List<ConvertRow>, val activeCode: String, val expressionDisplay: String,
                            val status: SyncStatus, val editing: Boolean, val decimalLabel: Char)
  class ConvertViewModel(rates: RatesRepository, poller: CryptoPoller, settings: SettingsRepository, catalog: CurrencyCatalog,
                         formatter: AmountFormatter, handoff: SharedFlow<CameraHandoff>, decimalSeparator: Char) : ViewModel() {
      val state: StateFlow<ConvertUiState>
      fun onKey(k: Key); fun onRowTapped(code: String); fun onToggleEdit()
      fun onMove(from: Int, to: Int); fun onRemove(code: String); fun onAdd(code: String)
      fun onRefresh(); fun setVisible(visible: Boolean)   // drives CryptoPoller's active flag
  }
  ```

**Behaviour rules:**
- **First run:** if `settings.initialized` is false, write `favorites = catalog.defaultFavorites(Locale.getDefault())`, `activeCode = favorites.first()` and `initialized = true`.
- **Active row:** shows `formatter.format(evaluate(expression))`.
- **Expression line:** shown only when the expression contains an operator. "." is replaced with `decimalSeparator` for display.
- **Other rows:** `Converter.convert(value, active, row, snapshot)`, formatted. A missing rate gives `missing = true` and amount "—". Their change is `Converter.dailyChangePct(row, active, s)`. The active row's change is null.
- **Tapping another row:** that row's code becomes active, and the expression becomes `formatter.plain(currentConvertedValue)`. If the value is missing, the expression becomes "".
- **Removing:** removing the active row makes the first remaining row active. The last favourite can't be removed (the button is disabled).
- **Persistence:** each change to favourites, the active code or the expression is written through `settings.update`.
- **Camera handoff:** a `CameraHandoff` adds the code to favourites if it's missing (at the top), makes it active, and sets the expression to `plain(amount)`.

- [ ] **Step 1:** Write the failing tests (fakes from Tasks 7 and 10, and a fixture snapshot from Task 3):
  - `firstRunSeedsDefaults`: `rows.map{it.currency.code}` equals `defaultFavorites(Locale.getDefault())`.
  - `typingUpdatesOtherRows`: active EUR, keys 1,0,0 gives the HUF row's amount = `formatter.format(convert(100,EUR,HUF))`.
  - `expressionShownOnlyWithOperator`: "25×4" gives `expressionDisplay == "25×4"`, and "100" gives `""`.
  - `decimalKeyUsesLocaleSeparatorForDisplay`: with decimalSeparator ',', keys 1, Dot, 5, Op('+') give `expressionDisplay == "1,5+"`, and the stored settings expression is `"1.5+"`.
  - `tapRowMakesItActiveWithItsValue`.
  - `missingRateShowsDash`.
  - `cannotRemoveLastFavorite`.
  - `moveReordersAndPersists`.
  - `cameraHandoffActivates`: emitting `CameraHandoff("HUF", 1290)` gives `activeCode == "HUF"` and the expression `"1290"`.
  - `restoresStateFromSettings`: settings pre-seeded with `activeCode = "USD"` and `expression = "7×6"` give a USD row amount for 42.
- [ ] **Step 2:** Run `--tests '*ConvertViewModelTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement the ViewModel. Combine `settings.settings`, `rates.snapshot` and `rates.status` with `combine`, then `stateIn(viewModelScope, WhileSubscribed(5000), …)`.
- [ ] **Step 4:** Implement `ConvertScreen`:
  - Top bar: "CONVERT" in `HeadFamily`, the `SyncBadge`, ✎ and ⟳.
  - `PullToRefreshBox` around a `LazyColumn` of `CurrencyRow`s. In edit mode, use `ReorderableItem` (sh.calvin.reorderable) and an "+ Add currency" row that opens `CurrencyPicker`.
  - `Keypad` pinned at the bottom.
  - A `NO DATA` empty state with the retry button.
  - `LifecycleResumeEffect` calls `setVisible(true)`, and `onPauseOrDispose` calls `setVisible(false)`.
  - The ViewModel is built with `viewModel { ConvertViewModel(...) }`, pulling everything from `AppContainer`.
- [ ] **Step 5:** Run `./gradlew :app:testDebugUnitTest --tests '*ConvertViewModelTest' :app:assembleDebug`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 6:** Commit: `feat: calculator-style convert screen`.

---

### Task 11: Charts screen

**Files:**
- Create: `ui/charts/ChartsViewModel.kt`, `ui/charts/ChartsScreen.kt`, `ui/charts/PriceChart.kt`
- Modify: `ui/nav/TickerNavHost.kt`
- Test: `ui/charts/ChartsViewModelTest.kt`

**Interfaces:**
- Consumes: `HistoryRepository`, `SeriesMath`, `RatesRepository.snapshot`, `SettingsRepository`, `CurrencyCatalog`, `AmountFormatter`, `CurrencyPicker`.
- Produces:
  ```kotlin
  data class SavedPairRow(val pair: SavedPair, val label: String, val changePct: Double?)
  data class ChartsUiState(val base: Currency, val quote: Currency, val range: ChartRange, val available: Set<ChartRange>,
      val points: List<PricePoint>, val current: String?, val changeText: String?, val positive: Boolean,
      val high: String?, val low: String?, val avg: String?, val loading: Boolean, val stale: Boolean, val error: Boolean,
      val isSaved: Boolean, val saved: List<SavedPairRow>)
  class ChartsViewModel(history: PairHistory, rates: RatesRepository, settings: SettingsRepository,
                        catalog: CurrencyCatalog, formatter: AmountFormatter) : ViewModel() {
      val state: StateFlow<ChartsUiState>
      fun onSwap(); fun onPickBase(code: String); fun onPickQuote(code: String); fun onRange(r: ChartRange)
      fun onToggleSave(); fun onSelectSaved(p: SavedPair); fun onRetry()
  }
  @Composable fun PriceChart(points: List<PricePoint>, positive: Boolean, formatValue: (BigDecimal) -> String, formatTime: (Instant) -> String)
  ```

**Behaviour rules:**
- **Default pair:** `chartBase`/`chartQuote` from settings, otherwise the first two favourites.
- **Default range:** M1.
- **Range not available:** if the selected range isn't in `available` after the pair changes, fall back to M1.
- **`changeText`:** `"▲ +6.21 (+1.60%) · 1M"` or `"▼ −0.12 (−0.31%) · 1W"`. Range labels are 1D, 1W, 1M, 3M, 1Y, 5Y and MAX.
- **Saved-pair rows:** their change uses the same range, loaded through `pairSeries`. Failures give `changePct = null`.

- [ ] **Step 1:** Write the failing tests, using a `FakeHistory : PairHistory` (the interface is defined in Task 8):
  - `defaultsToFirstTwoFavorites`.
  - `swapFlipsPair`.
  - `unavailableRangeFallsBackToM1`: on D1 with BTC/HUF, picking base EUR sets the range to M1.
  - `statsAndChangeText`: points [10, 12, 8, 11] give `high == format(12)` and a `changeText` that starts with "▲ +1".
  - `errorWhenNoData`: pairSeries throws, so `error = true` and `points` is empty.
  - `staleFlagPropagates`.
  - `toggleSavePersists`: `savedPairs` contains the pair and `isSaved` is true.
- [ ] **Step 2:** Run `--tests '*ChartsViewModelTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement the ViewModel.
- [ ] **Step 4:** Implement `PriceChart` with Vico 3.3.1 (`CartesianChartHost`, `rememberCartesianChart`, `rememberLineCartesianLayer`, `rememberDefaultCartesianMarker`):
  - Line colour `Accent` or `Negative`, with an area fill from 35% alpha down to 0.
  - The marker label shows `formatTime · formatValue`.
  - No axes except a faint horizontal grid in `#141414`.
  - Check the exact Vico 3 API names with context7 `/patrykandpatrick/vico` before writing them.
- [ ] **Step 5:** Implement `ChartsScreen` to the approved mockup:
  - Pair chips with ⇄, the price, `changeText`, then the chart.
  - Range chips; unavailable ones are disabled, and a tooltip "Daily data only for this pair" appears on press.
  - HIGH/LOW/AVG tiles, then the saved list.
  - A "couldn't refresh" line when stale, and an empty state with Retry on error.
- [ ] **Step 6:** Run `./gradlew :app:testDebugUnitTest --tests '*ChartsViewModelTest' :app:assembleDebug`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 7:** Commit: `feat: charts screen`.

---

### Task 12: OCR core

**Files:**
- Create: `ocr/ImagePreprocessor.kt`, `ocr/PriceCandidatePicker.kt`, `ocr/ReadingStabilizer.kt`, `ocr/OcrEngine.kt`, `assets/tessdata/eng.traineddata`
- Test: `ocr/ImagePreprocessorTest.kt`, `ocr/PriceCandidatePickerTest.kt`, `ocr/ReadingStabilizerTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) { val height get() = bottom - top }
  data class OcrToken(val text: String, val box: Box)
  object ImagePreprocessor {
      fun otsuThreshold(gray: IntArray): Int                                  // values 0..255
      fun binarize(gray: IntArray): IntArray                                  // 0 = ink, 255 = paper; inverts when the majority of pixels are dark
      fun toBitmap(bin: IntArray, w: Int, h: Int): Bitmap                     // Android-only helper, not unit-tested
      fun scaleFactorForTextHeight(cropHeightPx: Int): Float                  // target ≈ 40 px text ≈ crop height / 1.6
  }
  object PriceCandidatePicker {
      fun candidates(tokens: List<OcrToken>): List<BigDecimal>                // all parseable, merged groups, tallest first
      fun pick(tokens: List<OcrToken>): BigDecimal?                           // = candidates().firstOrNull()
  }
  class ReadingStabilizer(private val required: Int = 2) { fun offer(v: BigDecimal?): BigDecimal?; fun reset() }
  class OcrEngine(context: Context) : Closeable {
      suspend fun recognize(bitmap: Bitmap): List<OcrToken>                   // single-thread executor; PSM SPARSE_TEXT; whitelist "0123456789.,'  "
  }
  ```

**Merging rule for `candidates`:** tokens on the same line merge into one group when:
- their vertical overlap is ≥ 50% of the smaller height,
- the horizontal gap is ≤ 0.6 × the token height,
- and their heights differ by ≤ 25%.

The group's text is joined with " " and parsed with `PriceParser`. Groups are ordered by max token height, descending.

**`ReadingStabilizer`:** returns the new value only when the same value (`compareTo == 0`) was offered `required` times in a row. Otherwise it returns the last stable value. A `null` offer breaks the streak but keeps the last stable value.

- [ ] **Step 1:** Download `eng.traineddata` from `https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata` into `assets/tessdata/`. Expected size ≈ 4 MB.
- [ ] **Step 2:** Write the failing tests:
  - `ImagePreprocessorTest.otsuBimodal`: an array of 100×30 and 100×220 gives a threshold between 30 and 220.
  - `ImagePreprocessorTest.invertsDarkBackground`: 80% of pixels at 20 and 20% at 230 gives output where the former 230 pixels are 0 (ink).
  - `PriceCandidatePickerTest.mergesGroupedThousands`: tokens "1" and "290" (same line, height 40, gap 12) plus "200" (height 12) give `pick == 1290`.
  - `PriceCandidatePickerTest.tallestWins`: "12.99" at height 50 and "3.49" at height 20 give 12.99.
  - `PriceCandidatePickerTest.noMergeAcrossLines`.
  - `PriceCandidatePickerTest.emptyIsNull`.
  - `ReadingStabilizerTest.needsTwoInARow`: offering 5, 6, 6 returns null, null, 6.
  - `ReadingStabilizerTest.nullKeepsLast`: offering 6, 6, null returns …, 6, 6.
  - `ReadingStabilizerTest.changeNeedsConfirmation`: after a stable 6, offering 7 keeps 6, then a second 7 gives 7.
- [ ] **Step 3:** Run `--tests '*ImagePreprocessorTest' --tests '*PriceCandidatePickerTest' --tests '*ReadingStabilizerTest'`. Expected: FAIL.
- [ ] **Step 4:** Implement the pure parts. Then implement `OcrEngine`:
  - On first use, copy `tessdata/eng.traineddata` from assets to `filesDir/tessdata/`.
  - `TessBaseAPI().init(filesDir.path, "eng")`, then `setPageSegMode(PSM_SPARSE_TEXT)` and the whitelist variable.
  - Iterate words with `getResultIterator()` at `RIL_WORD`, reading `getUTF8Text` and `getBoundingRect`.
  - `close()` calls `recycle()`.
- [ ] **Step 5:** Run the tests. Expected: PASS. Then run `:app:assembleDebug` and expect BUILD SUCCESSFUL.
- [ ] **Step 6:** Commit: `feat: OCR preprocessing, candidate picking and Tesseract engine`.

---

### Task 13: Camera screen

**Files:**
- Create: `ocr/PriceFrameAnalyzer.kt`, `ui/camera/CameraViewModel.kt`, `ui/camera/CameraScreen.kt`
- Modify: `ui/nav/TickerNavHost.kt` (Camera route; navigate to Convert on handoff), `AndroidManifest.xml` (`CAMERA` permission, `<uses-feature android:name="android.hardware.camera" android:required="false"/>`)
- Test: `ui/camera/CameraViewModelTest.kt`

**Interfaces:**
- Consumes: `OcrEngine`, `ImagePreprocessor`, `PriceCandidatePicker`, `ReadingStabilizer`, `Converter`, `AmountFormatter`, `SettingsRepository`, `RatesRepository`, `AppContainer.cameraHandoff`.
- Produces:
  ```kotlin
  class PriceFrameAnalyzer(engine: OcrEngine, aimBox: () -> RectF /* normalised 0..1 in preview coords */,
                           minIntervalMs: Long = 300, onTokens: (List<OcrToken>) -> Unit) : ImageAnalysis.Analyzer
  data class GalleryCandidate(val amount: BigDecimal, val converted: String)
  data class CameraUiState(val from: Currency, val to: Currency, val reading: BigDecimal?, val readingText: String?,
      val converted: String?, val hint: Boolean, val frozen: Boolean, val torch: Boolean, val gallery: List<GalleryCandidate>?)
  class CameraViewModel(rates: RatesRepository, settings: SettingsRepository, catalog: CurrencyCatalog,
                        formatter: AmountFormatter, handoff: MutableSharedFlow<CameraHandoff>) : ViewModel() {
      val state: StateFlow<CameraUiState>
      fun onTokens(tokens: List<OcrToken>)     // pick → stabilizer → reading; ignored while frozen
      fun onGalleryTokens(tokens: List<OcrToken>)
      fun onToggleFreeze(); fun onToggleTorch(); fun onSwap(); fun onPickFrom(code: String); fun onPickTo(code: String)
      fun onResultTapped(): Boolean            // emits CameraHandoff(from, reading); returns true if emitted (screen then navigates to Convert)
      fun onDismissGallery()
  }
  ```

**Defaults:**
- `from` = `settings.cameraFrom`, otherwise the locale currency, otherwise "USD".
- `to` = the first favourite ≠ `from`.
- `hint` is true while no stable reading exists, and shows "Point the box at a price".
- `readingText` = `"1 290 HUF · tap to open in Convert"`.

- [ ] **Step 1:** Write the failing tests:
  - `stableReadingConverts`: two identical token lists ("1 290" tall) with from HUF and to EUR give `converted == format(convert(1290,HUF,EUR))` and `hint == false`.
  - `frozenIgnoresFrames`.
  - `swapExchangesFromTo`.
  - `pickFromPersists`: `settings.cameraFrom == "HUF"`.
  - `resultTapEmitsHandoff`.
  - `galleryListsAllCandidates`: tokens for 12.99 and 3.49 give 2 candidates, tallest first.
- [ ] **Step 2:** Run `--tests '*CameraViewModelTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement `CameraViewModel`.
- [ ] **Step 4:** Implement `PriceFrameAnalyzer`:
  - Skip frames that arrive within `minIntervalMs` of the last analysed one.
  - Map `aimBox` to the image rect, accounting for `imageInfo.rotationDegrees`.
  - Crop the Y plane to a gray `IntArray` (YUV_420_888 plane 0, respecting `rowStride`), scale with `scaleFactorForTextHeight`, binarise, call `toBitmap`, then `engine.recognize`.
  - Always call `image.close()` in `finally`.
- [ ] **Step 5:** Implement `CameraScreen` to the approved mockup:
  - Permission request via `rememberLauncherForActivityResult(RequestPermission())`. If denied, show an explanation with "Open settings" (`ACTION_APPLICATION_DETAILS_SETTINGS`).
  - `PreviewView` in `AndroidView`, bound with `ProcessCameraProvider` to `Preview` + `ImageAnalysis(STRATEGY_KEEP_ONLY_LATEST, OUTPUT_IMAGE_FORMAT_YUV_420_888)`.
  - Aim box: 60% × 15%, centre at 44% height, 2 dp `Accent` border, scrim `#000000B3` outside.
  - Result card, the `FROM → TO ▾` chip (opens `CurrencyPicker`), ⚡ calling `cameraControl.enableTorch`, and the 🖼 / ❚❚ / ⇄ controls.
  - Gallery: `PickVisualMedia(ImageOnly)` → decode the bitmap (downscale its long edge to 2000 px) → grayscale + binarise → `engine.recognize` → `onGalleryTokens` → a bottom sheet of chips.
  - The engine is created in `remember` and closed in `DisposableEffect`. Tesseract runs on its own executor, never on the main thread.
- [ ] **Step 6:** Run `./gradlew :app:testDebugUnitTest --tests '*CameraViewModelTest' :app:assembleDebug`. Expected: PASS and BUILD SUCCESSFUL.
- [ ] **Step 7:** Commit: `feat: camera price conversion`.

---

### Task 14: Release groundwork and on-device verification

**Files:**
- Create: `README.md`, `PRIVACY.md`, `androidTest/.../OcrRegressionTest.kt`, `androidTest/assets/tags/` (synthetic images generated by the test itself and, later, real photos)
- Modify: `app/build.gradle.kts` (release `signingConfig` from the `TICKER_STORE_FILE`, `TICKER_STORE_PASSWORD`, `TICKER_KEY_ALIAS` and `TICKER_KEY_PASSWORD` Gradle properties; skipped when absent), `app/proguard-rules.pro`

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1:** Write `proguard-rules.pro`:
  - `-keep class com.googlecode.tesseract.android.** { *; }`
  - the kotlinx.serialization keep rules for `@Serializable` classes in `dev.ticker.converter.**`
  - `-keepattributes *Annotation*, Signature`
  - Retrofit 3 ships its own rules.
- [ ] **Step 2:** Write `OcrRegressionTest`:
  - Render these price strings on a `Bitmap` with `Canvas`, in a bold sans font with 3° rotation and Gaussian-ish noise: "1 290 Ft", "12.99", "1.290,00 €", "€ 3,49", "1'299.-", "CHEDDAR 200g / 1 290".
  - Run them through `binarize` → `OcrEngine.recognize` → `PriceCandidatePicker.pick`.
  - Expected values in order: 1290, 12.99, 1290.00, 3.49, 1299, 1290.
- [ ] **Step 3:** Write `PRIVACY.md` (spec §10). Write `README.md` covering build, install and the keystore. For the keystore, the user runs this in their own terminal (it prompts for passwords):
  ```bash
  keytool -genkeypair -v -keystore ~/.keystores/ticker-release.jks -alias ticker -keyalg RSA -keysize 4096 -validity 36500
  ```
  The README also says to back up the `.jks` file and both passwords somewhere off this laptop.
- [ ] **Step 4:** Run `./gradlew :app:testDebugUnitTest :app:assembleRelease`. Expected: all tests pass, and the release build succeeds (unsigned if the properties are absent).
- [ ] **Step 5 (needs the phone with USB debugging):** Run `./gradlew :app:connectedDebugAndroidTest`. Expected: `DaoTest` and `OcrRegressionTest` pass. If an OCR case fails, adjust the preprocessing constants (Task 12), not the expected values.
- [ ] **Step 6 (manual, on the phone):** Install with `./gradlew :app:installDebug`. Each check below must hold; otherwise file a fix task.
  - **Launch:** a cold start shows `LIVE · SYNC`.
  - **Aeroplane mode:** relaunching shows `OFFLINE · last sync …` and still converts.
  - **Favourites:** typing `25×4` updates all rows. Reordering and removing a favourite survive an app restart.
  - **Charts:** BTC/HUF shows 1D. EUR/HUF has 1D disabled. Hold and drag shows the date and value.
  - **Camera:** it reads a real price tag within about 1 s, holds the value steady, freezes, works with the torch, and tapping the result opens Convert with the amount.
  - **Gallery:** a photo of a menu lists its prices.
- [ ] **Step 7:** Commit: `chore: release config, privacy policy, OCR regression test`.
