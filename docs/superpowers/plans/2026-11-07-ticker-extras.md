# Ticker Extras Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a card-fee toggle, a favourites home-screen widget, "Convert" in the text-selection menu, and a receipt mode that converts every line of a receipt photo.

**Architecture:** Pure Kotlin logic (`Fee`, `SelectionParser`, `ReceiptParser`, `WidgetModel`, adaptive threshold) lives in `core/` and `ocr/` and is unit-tested on the JVM. Android surfaces are thin layers over those:
- a translucent `SelectionConvertActivity` for `PROCESS_TEXT`;
- a Glance widget plus a WorkManager refresh worker;
- a receipt screen fed by a second Tesseract mode and CameraX `ImageCapture`.

A one-shot `HandoffQueue` replaces the camera-only SharedFlow so any entry point can open Convert with an amount, even from a cold start.

**Tech Stack:**
- Kotlin 2.4.21, Jetpack Compose (BOM 2026.09.00), Material3, Room, DataStore.
- CameraX 1.6.2, tesseract4android 4.9.0.
- New: `androidx.glance:glance-appwidget:1.2.0`, `androidx.work:work-runtime-ktx:2.12.0`.

**Spec:** `docs/superpowers/specs/2026-11-07-ticker-extras-design.md`

## Global Constraints

- minSdk 26, targetSdk 36, compileSdk 37 (unchanged).
- New UI uses the Neon helpers: `tile()`, `glow()`, `dotGrid()`, `Neon.glowText`, `TickerIcons`, `NeonIconButton`, `NeonPillButton`. Icons are drawn vectors, never emoji.
- The fee applies only to *converted* amounts: never to the typed amount, the camera reading, the selected amount, or a receipt line's original.
- The widget never touches the network while rendering; only `RatesRefreshWorker` and the app fetch.
- No new permissions (ImageCapture uses the existing CAMERA permission).
- Unit test command: `./gradlew :app:testDebugUnitTest`. Device: wireless adb, serial `adb-RFCWC16YRJT-52Vqjt._adb-tls-connect._tcp` (or the port the user gives).
- Commits are local only; never push.

## Review Focus

1. **Fee applied twice, or to the typed/read amount.** Pinned by `activeRowIgnoresFee` and `cameraReadingIgnoresFee`.
2. **"Open in Ticker" from a cold start loses the amount.** Pinned by `HandoffQueueTest.deliversValueSentBeforeCollection`.
3. **Selections with several numbers** ("2 for €5", "€12,99 incl. 27% VAT"): the number next to the currency mark must win. Pinned by `SelectionParserTest.prefersNumberNextToSymbol`.
4. **Receipt lines whose numbers aren't prices** (dates, times, tax ids, VAT %, phone numbers). Pinned by `ReceiptParserTest.ignoresDatesTimesIdsAndPercents`.
5. **Widget with no cached snapshot or fewer than 2 favourites**: an empty state, not a crash. Pinned by `WidgetModelTest`.

---

### Task 1: Fee core and settings

**Files:**
- Create: `app/src/main/java/dev/ticker/converter/core/math/Fee.kt`
- Modify: `app/src/main/java/dev/ticker/converter/core/model/Models.kt` (`Settings`)
- Test: `app/src/test/java/dev/ticker/converter/core/math/FeeTest.kt`, `app/src/test/java/dev/ticker/converter/data/local/CodecsTest.kt` (add a case)

**Interfaces:**
- Produces: `Settings.feeBps: Int = 200` (basis points; 175 = 1.75 %, range 0..1000) and `Settings.feeOn: Boolean = false`. Also `object Fee { fun apply(amount: BigDecimal, bps: Int): BigDecimal; fun label(bps: Int): String; const val MAX_BPS = 1000; const val STEP_BPS = 10; val PRESETS = listOf(50, 100, 175, 200, 250, 300) }`.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun appliesPercent() = assertEquals(0, BigDecimal("102").compareTo(Fee.apply(BigDecimal("100"), 200)))
@Test fun zeroIsIdentity() = assertEquals(0, BigDecimal("7.31").compareTo(Fee.apply(BigDecimal("7.31"), 0)))
@Test fun maxIsTenPercent() = assertEquals(0, BigDecimal("110").compareTo(Fee.apply(BigDecimal("100"), 1000)))
@Test fun labels() { assertEquals("2%", Fee.label(200)); assertEquals("1.75%", Fee.label(175)); assertEquals("0.5%", Fee.label(50)) }
// CodecsTest
@Test fun oldSettingsJsonGetsFeeDefaults() {
    val s = SettingsCodec.decode("""{"favorites":["EUR","USD"]}""")
    assertEquals(200, s.feeBps); assertFalse(s.feeOn)
}
```

- [ ] **Step 2: Run them.** `./gradlew :app:testDebugUnitTest --tests '*FeeTest' --tests '*CodecsTest'`. Expected: compile failure (no `Fee`, no `feeBps`).
- [ ] **Step 3: Implement `Fee` and the two `Settings` fields.** (Ruling vs spec §2 "`feePercent: BigDecimal`": it is stored as `Int` basis points, because `Settings` is kotlinx-serialized and BigDecimal has no serializer. 175 bps covers the 1.75 % preset that tenths could not. The behaviour is identical. Cost if wrong: a field rename.) `apply` = `amount × (10000 + bps) / 10000` using `MathContext.DECIMAL64`. `label` = `bps / 100` as a plain string with trailing zeros stripped, plus `%`.
- [ ] **Step 4: Run.** Same command. Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(fee): fee model and settings"`

### Task 2: Fee in Convert (chip, sheet, rows)

**Files:**
- Modify: `ui/convert/ConvertViewModel.kt`, `ui/convert/ConvertScreen.kt`
- Create: `ui/components/FeeControls.kt` (`FeeChip`, `FeeSheet`)
- Test: `app/src/test/java/dev/ticker/converter/ui/convert/ConvertViewModelTest.kt`

**Interfaces:**
- Consumes: `Fee`, `Settings.feeBps` and `Settings.feeOn` (Task 1).
- Produces: `ConvertUiState.feeOn: Boolean`, `ConvertUiState.feeBps: Int`; `ConvertViewModel.onToggleFee()`, `ConvertViewModel.onSetFee(bps: Int)` (clamped to 0..`Fee.MAX_BPS`, both persisted via `mutate`).

- [ ] **Step 1: Write failing tests** in `ConvertViewModelTest`, using the existing fakes:
  - `feeOnRaisesConvertedRows`: with 100 EUR active and `feeOn = true, feeBps = 200`, the USD row equals the plain conversion × 1.02, formatted.
  - `activeRowIgnoresFee`: the EUR row still reads "100".
  - `toggleFeePersists`: after `onToggleFee()`, `settings.first().feeOn == true`.
  - `setFeeClamps`: `onSetFee(5000)` stores 1000; `onSetFee(-5)` stores 0.
- [ ] **Step 2: Run.** `./gradlew :app:testDebugUnitTest --tests '*ConvertViewModelTest'`. Expected: FAIL (unresolved `onToggleFee`, `feeOn`).
- [ ] **Step 3: Implement.**
  - In `buildState`, the `else -> Converter.convert(...)` branch becomes `Converter.convert(...)?.let { if (s.feeOn) Fee.apply(it, s.feeBps) else it }`.
  - Expose `feeOn` and `feeBps` in the state.
  - `FeeChip(on, bps, onToggle, onLongPress)`:
    - 36 dp pill, using `combinedClickable`.
    - Off: outlined dim `FEE`. On: green `FEE +2%` with `glow()`.
    - Semantics: `Role.Switch`, `toggleableState`, `contentDescription = "Card fee ${Fee.label(bps)}"`, and `customActions = listOf(CustomAccessibilityAction("Set fee") { … })`.
  - `FeeSheet(bps, onSet, onDismiss)`:
    - A `ModalBottomSheet` with container `Color(0xFF0D0D0D)`.
    - The value large (`Fee.label`).
    - −/+ `NeonIconButton`s stepping by `Fee.STEP_BPS`.
    - A `FlowRow` of `Fee.PRESETS` as pills.
  - On Convert:
    - The chip sits in the header row before the edit button.
    - When on, `SyncBadge` is followed by a dim label `incl. ${Fee.label(bps)} fee`.
- [ ] **Step 4: Run.** Same command. Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(fee): fee chip and sheet on Convert"`

### Task 3: Fee in Camera

**Files:**
- Modify: `ui/camera/CameraViewModel.kt`, `ui/camera/CameraScreen.kt`
- Test: `app/src/test/java/dev/ticker/converter/ui/camera/CameraViewModelTest.kt`

**Interfaces:**
- Consumes: `Fee`, `Settings.feeBps`/`feeOn`.
- Produces: `CameraUiState.feeLabel: String?` (null when the fee is off). `CameraViewModel` keeps observing settings (`settings.settings`), so a fee change made on Convert shows up here.

- [ ] **Step 1: Failing tests.**
  - `cameraConvertsWithFee`: reading 100 HUF with the fee on at 200 → `converted` equals the plain conversion × 1.02.
  - `cameraReadingIgnoresFee`: `readingText` is unchanged.
  - `feeLabelShownOnlyWhenOn`.
- [ ] **Step 2: Run** `--tests '*CameraViewModelTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** In `conv(v)`, apply the fee after `Converter.convert`. The result card shows `feeLabel` as `incl. 2% fee` under the amount.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(fee): apply fee to camera result"`

### Task 4: HandoffQueue (cold-start safe "open in Convert")

**Files:**
- Modify: `AppContainer.kt`, `ui/convert/ConvertViewModel.kt`, `ui/camera/CameraViewModel.kt`, `ui/convert/ConvertScreen.kt`, `ui/camera/CameraScreen.kt`, and the two existing VM tests.
- Create: `app/src/main/java/dev/ticker/converter/HandoffQueue.kt`
- Test: `app/src/test/java/dev/ticker/converter/HandoffQueueTest.kt`

**Interfaces:**
- Produces:
  - `class HandoffQueue { val flow: Flow<CameraHandoff>; fun send(h: CameraHandoff): Boolean }`, backed by `Channel(Channel.CONFLATED)` + `receiveAsFlow()`, so it delivers once, even if sent before anyone collects.
  - `AppContainer.handoff: HandoffQueue` replaces `cameraHandoff`.
  - `ConvertViewModel(..., handoff: Flow<CameraHandoff>, ...)`.
  - `CameraViewModel(..., sendHandoff: (CameraHandoff) -> Boolean)`.
- Existing tests keep their `MutableSharedFlow` and pass `handoff` / `handoff::tryEmit`.

- [ ] **Step 1: Failing tests.**
  - `deliversValueSentBeforeCollection`: send, then `flow.first()` returns it.
  - `deliversOnlyOnce`: after one `first()`, a second collector with `withTimeoutOrNull(100)` gets null.
- [ ] **Step 2: Run** `--tests '*HandoffQueueTest'`. Expected: compile FAIL.
- [ ] **Step 3: Implement and rewire** the constructors and call sites; update the two VM tests' constructor arguments.
- [ ] **Step 4: Run the full unit suite.** Expected: all pass (212 + new).
- [ ] **Step 5: Commit.** `git commit -m "refactor: one-shot handoff queue for opening Convert"`

### Task 5: SelectionParser

**Files:**
- Create: `app/src/main/java/dev/ticker/converter/core/math/SelectionParser.kt`
- Test: `app/src/test/java/dev/ticker/converter/core/math/SelectionParserTest.kt`

**Interfaces:**
- Produces: `data class Selection(val amount: BigDecimal?, val currencyCode: String?)` and `object SelectionParser { fun parse(text: String, isKnownCode: (String) -> Boolean): Selection }`.

- [ ] **Step 1: Failing tests** (assert both fields):
  - **Symbol table:** `"€12.99"`→(12.99, EUR); `"12,99 €"`→(12.99, EUR); `"1 290 Ft"`→(1290, HUF); `"£5"`→GBP; `"¥300"`→JPY; `"₹99"`→INR; `"25 zł"`→PLN; `"120 Kč"`→CZK; `"45 lei"`→RON; `"CHF 9.50"`→CHF; `"₿0.01"`→BTC; `"$5"`→USD; `"US$5"`→USD; `"C$5"`→CAD; `"CA$5"`→CAD; `"A$5"`→AUD; `"AU$5"`→AUD.
  - `isoCodeBeforeAndAfter`: `"USD 5"`, `"5 usd"`, `"5.00 EUR"`.
  - `unknownCodeIgnored`: `"5 XYZ"`→(5, null) when `isKnownCode` is false.
  - `ambiguousKrIsNull`: `"50 kr"`→(50, null).
  - `noNumber`: `"hello €"`→(null, EUR).
  - `prefersNumberNextToSymbol`: `"2 for €5"`→(5, EUR); `"€12,99 incl. 27% VAT"`→(12.99, EUR).
  - `thousands`: `"€1,234.50"`→1234.50.
- [ ] **Step 2: Run** `--tests '*SelectionParserTest'`. Expected: compile FAIL.
- [ ] **Step 3: Implement.**
  - Find the currency marks and their positions: longest symbols first (`CA$`, `US$`, `AU$`, `C$`, `A$` before `$`), then three-letter words passing `isKnownCode`.
  - Tokenise the number runs with the regex `\d[\d\s.,']*\d|\d`, skipping runs directly followed by `%`.
  - The amount is the number run nearest to the chosen currency mark (by character distance), or the first run when there's no mark. It's parsed with `PriceParser.parse`.
  - (Ruling vs spec §4 "first number": the nearest number wins when a mark exists. Cost if wrong: one rule.)
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(selection): parse amount and currency from selected text"`

### Task 6: Convert from selection (activity, view model, card)

**Files:**
- Create: `ui/selection/SelectionViewModel.kt`, `ui/selection/SelectionConvertActivity.kt`, `ui/selection/SelectionCard.kt`
- Modify: `AndroidManifest.xml`, `res/values/themes.xml` (add `Theme.Ticker.Selection`)
- Test: `app/src/test/java/dev/ticker/converter/ui/selection/SelectionViewModelTest.kt`

**Interfaces:**
- Consumes: `SelectionParser` (Task 5), `HandoffQueue` (Task 4), `Fee` (Task 1), `RatesRepository`, `SettingsRepository`, `CurrencyCatalog`, `AmountFormatter`.
- Produces:
  - `data class SelectionRow(val currency: Currency, val amount: String)`
  - `data class SelectionUiState(val amount: BigDecimal?, val amountText: String, val from: Currency?, val rows: List<SelectionRow>, val feeLabel: String?, val status: SyncStatus)`
  - `SelectionViewModel(text: String, rates, settings, catalog, formatter, handoff: HandoffQueue)` with `onPickFrom(code)` and `openInTicker(): Boolean`.

- [ ] **Step 1: Failing VM tests** (same fakes as `ConvertViewModelTest`):
  - `usesDetectedCurrency`: `"€10"` → `from = EUR`.
  - `fallsBackToCameraFrom`: `"50 kr"` with `cameraFrom = "HUF"` → `from = HUF`.
  - `fallsBackToFirstFavourite` when `cameraFrom` is null.
  - `rowsAreFavouritesExceptFromMaxSix`.
  - `feeAppliesToRows`.
  - `noAmountGivesNoRows`.
  - `openInTickerSendsHandoff`: the queue receives `CameraHandoff("EUR", 10)`.
  - `onPickFromRecomputes`.
- [ ] **Step 2: Run** `--tests '*SelectionViewModelTest'`. Expected: FAIL.
- [ ] **Step 3: Implement the VM.**
  - On init, launch `rates.loadCached()`, then `rates.refreshAll(cryptos among favourites)`.
  - Combine `rates.snapshot`, `rates.status` and `settings.settings` into the state.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Implement the activity and card (UI, no unit test).**
  - **Manifest** (inside `<application>`):
    ```xml
    <activity android:name=".ui.selection.SelectionConvertActivity" android:exported="true"
        android:label="Convert" android:theme="@style/Theme.Ticker.Selection"
        android:excludeFromRecents="true" android:taskAffinity="">
        <intent-filter>
            <action android:name="android.intent.action.PROCESS_TEXT"/>
            <category android:name="android.intent.category.DEFAULT"/>
            <data android:mimeType="text/plain"/>
        </intent-filter>
    </activity>
    ```
  - **Theme:** parent = the app theme, with `windowIsTranslucent=true`, a transparent `windowBackground`, `backgroundDimEnabled=true` and `windowNoTitle`.
  - **Activity:** reads `getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)`.
  - **Card:** a full-screen `Box` clickable to finish, with `SelectionCard` centred in a 92 %-width `tile()` with `glow()`:
    - **Amount pill:** amount + currency code + chevron; tapping it opens `CurrencyPicker`.
    - **Rows:** up to 6, each with `CurrencyGlyph`, code and amount.
    - **Footer:** `SyncBadge`, the fee label, and an `Open in Ticker` `NeonPillButton`.
  - **Open in Ticker:** `openInTicker()`, then start `MainActivity` with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`, then `finish()`.
  - **No amount:** show `No amount in the selected text` and only the button.
- [ ] **Step 6: Build and install;** select "€12.99" in Chrome on the phone. Expected: "Convert" in the selection menu, the card shows EUR→favourites, and Open in Ticker lands on Convert with EUR 12.99 active.
- [ ] **Step 7: Commit.** `git commit -m "feat(selection): Convert entry in the text selection menu"`

### Task 7: Widget model

**Files:**
- Create: `app/src/main/java/dev/ticker/converter/widget/WidgetModel.kt`
- Test: `app/src/test/java/dev/ticker/converter/widget/WidgetModelTest.kt`

**Interfaces:**
- Produces:
  - `data class WidgetRow(val code: String, val glyph: String, val amount: String, val change: String?, val down: Boolean)`
  - `sealed interface WidgetUi { data object NoRates; data object NeedFavourites; data class Rates(val baseCode: String, val baseGlyph: String, val rows: List<WidgetRow>, val updated: String, val stale: Boolean, val feeLabel: String?) }`
  - `object WidgetModel { fun build(s: Settings, snap: RateSnapshot?, now: Instant, zone: ZoneId, catalog: CurrencyCatalog, formatter: AmountFormatter): WidgetUi }`
  - The row amount is `formatter.format(convert(1, base, code) [+fee], currency)`; the change is `changeArrowText(dailyChangePct(code, base))`; `stale` means older than 2 h; `updated` is `HH:mm`.

- [ ] **Step 1: Failing tests.**
  - `noSnapshotIsNoRates`.
  - `oneFavouriteNeedsFavourites`.
  - `rowsAreOneBaseInEachOtherFavourite`.
  - `feeAppliedWhenOn` (and `feeLabel` set).
  - `staleAfterTwoHours`: 2 h 1 min → true, 1 h 59 min → false.
  - `missingRateRowShowsDash`.
- [ ] **Step 2: Run** `--tests '*WidgetModelTest'`. Expected: compile FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(widget): widget content model"`

### Task 8: Glance widget, receiver and refresh worker

**Files:**
- Modify: `gradle/libs.versions.toml` (`glance = "1.2.0"`, `work = "2.12.0"`, libraries `glance-appwidget`, `work-runtime-ktx`), `app/build.gradle.kts`, `AndroidManifest.xml`, `TickerApp.kt`, `app/proguard-rules.pro` (only if the R8 build warns).
- Create: `widget/TickerWidget.kt`, `widget/TickerWidgetReceiver.kt`, `widget/RatesRefreshWorker.kt`, `res/xml/ticker_widget_info.xml`, `res/layout/widget_loading.xml` (Glance initial layout).

**Interfaces:**
- Consumes: `WidgetModel` (Task 7), `TickerApp.container`.
- Produces:
  - `TickerWidget : GlanceAppWidget`, with `sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 50.dp), DpSize(180.dp, 110.dp), DpSize(180.dp, 180.dp)))`.
  - `TickerWidgetReceiver`: `onEnabled` → `RatesRefreshWorker.schedule(context)`; `onDisabled` → `RatesRefreshWorker.cancel(context)`.
  - `RatesRefreshWorker : CoroutineWorker`: `loadCached()`, then `refreshAll(...)`, then `TickerWidget().updateAll(context)`. Its companion `schedule` enqueues `PeriodicWorkRequest(30, MINUTES)` with `NetworkType.CONNECTED`, `ExistingPeriodicWorkPolicy.KEEP`, unique name `rates-refresh`.

- [ ] **Step 1: Add dependencies; build** `./gradlew :app:assembleDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 2: Implement `TickerWidget.provideGlance`.**
  - **Data:** `container.rates.loadCached()`, then `WidgetModel.build(settings.first(), rates.snapshot.value, Instant.now(), …)`.
  - **Content:** `provideContent` reads `LocalSize.current.height` to decide the row count (2 / 4 / 6).
  - **Layout:**
    - `GlanceModifier.background(Color.Black)` with `cornerRadius(22.dp)` and `clickable(actionStartActivity<MainActivity>())`.
    - Header: base glyph + `1 EUR` + `updated 14:05` (amber when `stale`).
    - Rows: code (bold), amount, and change in green or red.
    - `FontFamily.Monospace` for numbers.
  - **States:** `NoRates` → `Open Ticker to load rates`; `NeedFavourites` → `Add favourites in Ticker`.
- [ ] **Step 3: Write `ticker_widget_info.xml`.** `minWidth=110dp`, `minHeight=50dp`, `targetCellWidth=2`, `targetCellHeight=2`, `resizeMode=horizontal|vertical`, `updatePeriodMillis=0`, `widgetCategory=home_screen`, `initialLayout=@layout/widget_loading`, `description=@string/widget_description` ("Your favourite rates").
- [ ] **Step 4: Register the receiver in the manifest** (exported, `APPWIDGET_UPDATE` filter, meta-data → `@xml/ticker_widget_info`).
- [ ] **Step 5: In `TickerApp`**, after `refreshAll` in `onStart`, call `TickerWidget().updateAll(this)` inside `runCatching`.
- [ ] **Step 6: Build, run the unit suite, install.** On the phone, add the widget in three sizes. Expected: rows render; tapping opens Ticker; after changing a favourite in the app and pulling to refresh, the widget updates.
- [ ] **Step 7: Commit.** `git commit -m "feat(widget): favourites widget with background refresh"`

### Task 9: Adaptive threshold and receipt preprocessing

**Files:**
- Modify: `ocr/ImagePreprocessor.kt`
- Test: `app/src/test/java/dev/ticker/converter/ocr/OcrCoreTest.kt` (`ImagePreprocessorTest`)

**Interfaces:**
- Produces:
  - `ImagePreprocessor.adaptiveBinarize(gray: IntArray, w: Int, h: Int, window: Int = 31, c: Int = 12): IntArray`: Bradley local-mean threshold over an integral image; a pixel is ink (0) if it is below the local mean − c, else 255.
  - `ImagePreprocessor.prepareReceipt(bitmap: Bitmap): Bitmap`: scale the long edge to ≤ 2000 → `grayOf` → `adaptiveBinarize` → `padWhite(..., 12)` → `toBitmap`.

- [ ] **Step 1: Failing tests.**
  - `adaptiveKeepsTextUnderUnevenLight`: a 200×40 image whose left half has background 230 with a text block at 150, and whose right half has background 90 with a text block at 20. Text pixels are 0 and background pixels are 255 on **both** halves. (Global Otsu would black out the right half, so the test also asserts `binarize` fails it, documenting why.)
  - `adaptiveFlatImageIsAllPaper`: uniform 200 → all 255.
- [ ] **Step 2: Run** `--tests '*ImagePreprocessorTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** both functions.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(ocr): adaptive threshold for receipts"`

### Task 10: ReceiptParser

**Files:**
- Create: `app/src/main/java/dev/ticker/converter/core/math/ReceiptParser.kt`
- Test: `app/src/test/java/dev/ticker/converter/core/math/ReceiptParserTest.kt`, plus fixtures `app/src/test/resources/receipts/hu_tesco.txt`, `de_rewe.txt`, `en_cafe.txt`, `no_total.txt`

**Interfaces:**
- Produces: `data class ReceiptItem(val name: String, val amount: BigDecimal)`, `data class Receipt(val items: List<ReceiptItem>, val printedTotal: BigDecimal?)`, and `object ReceiptParser { fun parse(lines: List<String>): Receipt }`.

- [ ] **Step 1: Write fixtures.** Plain text as OCR would return it, 12–25 lines each.
  - **Required content:** a shop header, a date and time, a tax id, item lines with prices, a quantity line, a VAT line with a %, the total line, and payment and change lines.
  - **Hungarian receipt:** uses whole forints (`1 290`); the total line is `ÖSSZESEN: 4 350`.
  - **German receipt:** uses `SUMME EUR 23,47` and `MwSt 19%`.
- [ ] **Step 2: Failing tests.**
  - `readsItemsAndTotal_hu`: exact item list and total 4350.
  - `readsItemsAndTotal_de`: total 23.47.
  - `readsItemsAndTotal_en`.
  - `ignoresDatesTimesIdsAndPercents`: no item has an amount from `2026.11.07`, `14:05`, `12345678-2-42` or `27%`.
  - `skipsTaxPaymentChangeSubtotal`.
  - `quantityLineTakesRightmost`: `2 x 1.50   3.00` → 3.00.
  - `accentAndCaseInsensitiveKeywords`: `osszesen` and `Összesen` both count as totals.
  - `noTotalGivesNull`.
  - `namesTrimmed`: `"KENYÉR ....... 499"` → name `KENYÉR`.
- [ ] **Step 3: Run** `--tests '*ReceiptParserTest'`. Expected: compile FAIL.
- [ ] **Step 4: Implement** the rules exactly as spec §5 "Parsing" lists them (keyword lists verbatim). Normalise keywords with `java.text.Normalizer` NFD, strip combining marks, then upper-case.
- [ ] **Step 5: Run.** Expected: PASS.
- [ ] **Step 6: Commit.** `git commit -m "feat(receipt): receipt line parser"`

### Task 11: OCR line mode (and choosing the page segmentation)

**Files:**
- Modify: `ocr/OcrEngine.kt`, `ocr/OcrToken.kt` (add `OcrLine`)
- Create: `app/src/androidTest/java/dev/ticker/converter/ReceiptOcrTest.kt`; fixture `app/src/androidTest/assets/receipt_hu.png` (rendered by the test itself if no photo is available: see Step 1)

**Interfaces:**
- Produces: `data class OcrLine(val text: String, val box: Box)` and `suspend fun OcrEngine.recognizeLines(bitmap: Bitmap): List<OcrLine>`.
  - It uses a second `TessBaseAPI` instance, created lazily on the same single-thread executor: no whitelist, `PSM_SINGLE_BLOCK` or `PSM_SINGLE_COLUMN` per Step 3, iterating at `RIL_TEXTLINE`.
  - `close()` recycles both instances.

- [ ] **Step 1: Write an instrumented test.**
  - It renders the `hu_tesco.txt` fixture in black monospace text on a slightly grey, unevenly lit 1080-wide bitmap: a horizontal gradient from 235 to 150, plus noise.
  - It runs `prepareReceipt` + `recognizeLines` + `ReceiptParser.parse`.
  - It asserts the total is 4350 and that at least 80 % of the item amounts match.
- [ ] **Step 2: Run on the device.** `ANDROID_SERIAL=… ./gradlew :app:connectedDebugAndroidTest`. Expected: FAIL (no `recognizeLines`).
- [ ] **Step 3: Implement `recognizeLines`.** Run the test once with `PSM_SINGLE_BLOCK` and once with `PSM_SINGLE_COLUMN`, keep the one that matches more items, and ledger it as `Ruling: PSM = … — matched a/b vs c/b`.
- [ ] **Step 4: Run the device test.** Expected: PASS. Then reinstall the app; `connectedDebugAndroidTest` uninstalls it.
- [ ] **Step 5: Commit.** `git commit -m "feat(ocr): line-level OCR mode for receipts"`

### Task 12: Receipt screen, Receipt button, gallery → receipt

**Files:**
- Create: `ui/receipt/ReceiptViewModel.kt`, `ui/receipt/ReceiptScreen.kt`
- Modify: `ui/camera/CameraScreen.kt` (bind `ImageCapture`, add the Receipt button, route the gallery to the receipt, remove the gallery chip sheet), `ui/camera/CameraViewModel.kt` (remove gallery state), `CameraViewModelTest.kt` (drop the gallery tests), `ui/theme/TickerIcons.kt` (add a `Receipt` icon)
- Test: `app/src/test/java/dev/ticker/converter/ui/receipt/ReceiptViewModelTest.kt`

**Interfaces:**
- Consumes: `ReceiptParser` (Task 10), `OcrEngine.recognizeLines` (Task 11), `ImagePreprocessor.prepareReceipt` (Task 9), `Fee`, `HandoffQueue`.
- Produces:
  - `data class ReceiptRow(val name: String, val original: String, val converted: String, val included: Boolean)`
  - `data class ReceiptUiState(val reading: Boolean, val failed: Boolean, val from: Currency, val to: Currency, val rows: List<ReceiptRow>, val sumOriginal: String, val sumConverted: String, val printedTotal: String?, val mismatch: Boolean, val feeLabel: String?)`
  - `ReceiptViewModel(rates, settings, catalog, formatter, handoff, from: String, to: String)`, with these methods:
    - `onLines(lines: List<String>)`
    - `onFailed()`
    - `onToggle(index: Int)`
    - `onPickFrom(code)` and `onPickTo(code)`
    - `openTotal(): Boolean`

- [ ] **Step 1: Failing VM tests.**
  - `convertsEveryLine`.
  - `toggleExcludesFromSum`.
  - `mismatchWhenPrintedTotalDiffers`: compares the sum of *all* items with the printed total; tolerance 0.01.
  - `noMismatchWithoutPrintedTotal`.
  - `feeAppliesToConvertedOnly`.
  - `noItemsIsFailed`.
  - `openTotalSendsSumOfIncluded`.
- [ ] **Step 2: Run** `--tests '*ReceiptViewModelTest'`. Expected: FAIL.
- [ ] **Step 3: Implement the VM.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: UI.**
  - **`ReceiptScreen`:** a full-screen `Dialog` with `dotGrid()`.
    - Header: `RECEIPT`, the from → to chips (same pill as Camera), a close `NeonIconButton`, and the fee label.
    - `LazyColumn` of `tile()` rows: name ellipsised; original dim; converted on the right. Excluded rows are struck through at alpha 0.4.
    - A glowing total `tile(active = true)`: the converted sum in large green `Neon.glowText`, the original sum dim, `Receipt says X` when present, and the amber mismatch note.
    - While `reading`: a spinner + `Reading receipt…`.
    - When `failed`: the spec's message + `Try another photo`.
  - **Camera:**
    - Bind `ImageCapture.Builder().setCaptureMode(CAPTURE_MODE_MAXIMIZE_QUALITY).build()` alongside Preview and Analysis.
    - Receipt button (new `TickerIcons.Receipt`) placed where the swap button was. Swap moves next to the from → to chips at the top.
    - Tapping it: `takePicture` (in-memory) → `ImageProxy.toBitmap()`, rotated by `imageInfo.rotationDegrees` → `prepareReceipt` → `recognizeLines` → `vm.onLines(lines.map { it.text })`.
    - The gallery picks a photo → `decodeScaled(…, 2000)` → same pipeline.
- [ ] **Step 6: Run the full unit suite; install.** Snap a real receipt and open one from the gallery. Expected: the item list and total appear; tapping a row toggles it; tapping the total opens Convert.
- [ ] **Step 7: Commit.** `git commit -m "feat(receipt): receipt screen from camera or gallery"`

### Task 13: Whole-feature check on the phone

- [ ] **Step 1:** Run `./gradlew :app:testDebugUnitTest` and the device tests. Expected: all pass. Then reinstall.
- [ ] **Step 2:** Run the spec §7 manual list with the user:
  - Selection in Chrome and Messages.
  - The widget in three sizes, plus a background refresh after 30 min (or force it with `adb shell cmd jobscheduler run -f dev.ticker.converter <id>`).
  - The fee toggle on every screen.
  - Two real receipts.
- [ ] **Step 3:** Fix anything found test-first, then commit.
