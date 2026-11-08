# Ticker: currency converter for Android (design spec)

> **Update 2026-11-08:** Binance was removed before publishing (its data terms do not clearly allow use in a public app). Crypto prices now come from the fawazahmed0 API, updated daily, and charts have no intraday range. Parts of this document that describe Binance are historical.

Date: 2026-11-07
Status: draft, awaiting user review

## 1. Purpose

A free, ad-free, subscription-free currency converter for Android that is better
than the paid ones. It is built publish-ready (Play Store and F-Droid) but not
published yet.

**Success criteria**

- Opening the app always triggers a fresh rate fetch; the user can see when data was last synced.
- Converts between any mix of fiat and crypto currencies.
- Home screen favourites can be added, removed and reordered.
- A charts page for any pair.
- Pointing the camera at a price tag shows the converted price.
- Works offline with the last saved rates, clearly labelled as such.
- No Google Play Services, no analytics, no account, no ads. Eligible for F-Droid.

## 2. Decisions made during brainstorming

| Topic | Decision |
|---|---|
| Platform | Native Android: Kotlin + Jetpack Compose |
| Distribution | Personal use for now, built publish-ready |
| OCR engine | Tesseract (tesseract4android), not ML Kit, so the app stays Google-free |
| Camera behaviour | Aim box with live reading (option 1) |
| Home layout | Calculator style: favourites list plus an always-visible keypad that does maths |
| Visual style | "Midnight Ticker": true black, monospaced numbers, terminal green |
| Charts and Camera screens | Approved as mocked up (see §6 and §7) |
| Working name / app ID | "Ticker" / `dev.ticker.converter`. The app ID is permanent after publishing; the user may change it during spec review. |

## 3. Non-goals (v1)

Home-screen widgets, price alerts, charts comparing several pairs at once,
candlestick charts, portfolio tracking, user accounts, cloud sync, tablet layouts,
and translations beyond English.

## 4. Architecture

Single Gradle module `:app`, single Activity, Navigation Compose with three bottom tabs.
Manual dependency injection through one `AppContainer` created in `Application`.

```
dev.ticker.converter
├── core/
│   ├── model/      Currency, CurrencyKind, RateSnapshot, PricePoint, SyncStatus
│   └── math/       ExpressionEvaluator, Converter, AmountFormatter, PriceParser   (pure Kotlin, no Android)
├── data/
│   ├── remote/     FrankfurterApi, FawazApi, BinanceApi + DTOs (Retrofit + kotlinx.serialization)
│   ├── local/      Room: RatesDao, HistoryDao. SettingsStore (DataStore)
│   ├── catalog/    CurrencyCatalog: bundled currencies.json (ISO 4217 names, flags, decimals) + crypto names
│   └── repo/       RatesRepository, HistoryRepository
├── ocr/            OcrEngine (Tesseract wrapper), PriceFrameAnalyzer (CameraX), ImagePreprocessor
└── ui/
    ├── theme/      Midnight Ticker colours, typography (JetBrains Mono + Space Grotesk, bundled)
    ├── components/ CurrencyRow, Keypad, SyncBadge, CurrencyPicker
    ├── convert/    ConvertScreen + ConvertViewModel
    ├── charts/     ChartsScreen + ChartsViewModel
    └── camera/     CameraScreen + CameraViewModel
```

Each unit has one job:

- **`core/math`** is pure Kotlin and fully unit-tested. It knows nothing about Android or the network.
- **Repositories** are the only code that talks to the network or the database.
- **ViewModels** turn repository flows into UI state.

**Key libraries:**

- Jetpack: Compose BOM, Navigation Compose, Lifecycle ViewModel, Room (KSP), DataStore.
- Networking: Retrofit with the kotlinx.serialization converter, OkHttp.
- Camera and OCR: CameraX (core, camera2, lifecycle, view), tesseract4android.
- Charts: Vico (compose).

Every library must be open source and free of Google Play Services. The implementation plan pins exact versions.

**Platform:** minSdk 26, targetSdk 36. Permissions: `INTERNET` and `CAMERA` only.
The gallery uses the system Photo Picker, which needs no permission.

## 5. Data and rates

### 5.1 Model

All rates are stored as **USD per 1 unit** of each currency (`usdPer[code]`).
Converting `amount` from A to B is `amount × usdPer[A] / usdPer[B]`.
All maths uses `BigDecimal` (`MathContext.DECIMAL64`).

`RateSnapshot` holds:
- `usdPer`
- `usdPerPrev[code]`: the value about one day earlier. Crypto uses Binance's 24h open price. Fiat uses the previous available ECB or fawazahmed0 date.
- `fetchedAt`
- `sourceFor[code]`

A row's daily change is measured against the active row's currency:
`(usdPer[c]/usdPer[active]) / (usdPerPrev[c]/usdPerPrev[active]) − 1`.

USDT is treated as exactly 1 USD.

### 5.2 Sources (no API keys)

| Purpose | Primary | Fallback |
|---|---|---|
| Fiat latest (ECB set, ~30) | Frankfurter `api.frankfurter.dev/v1/latest?base=USD` | fawazahmed0 |
| All other fiat + long-tail crypto | fawazahmed0 `cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json` | `latest.currency-api.pages.dev/v1/currencies/usd.json` |
| Crypto live prices (all coins) | Binance `data-api.binance.vision/api/v3/ticker/price`, one request, keeping `*USDT` symbols (~26 KB gzipped) | fawazahmed0 (daily) |
| Crypto 24h open (favourites only) | Binance `/api/v3/ticker/24hr?type=MINI&symbols=[…]` (the unfiltered call is ~1.9 MB, so it isn't used) | fawazahmed0 previous day |
| Fiat previous-day value (`usdPerPrev`) | Frankfurter time series over the last 7 days, using the second-latest date | fawazahmed0 dated snapshot from the day before its latest |

The merge order per currency is Binance (crypto) → Frankfurter (fiat) → fawazahmed0.
The first source that has the currency wins, and `sourceFor` records which one it was.

Endpoint paths and response shapes are verified against the live APIs before
implementation. Recorded JSON fixtures are kept for tests.

### 5.3 Freshness rules

- **On every app start and every return to foreground** (`ProcessLifecycleOwner` `ON_START`): fetch all sources in parallel with a 10 s timeout each.
- **While the Convert tab is visible:** re-fetch Binance every 30 s. Fiat is not re-polled.
- **Pull-down on the Convert list:** forces a full refresh.
- **On success:** save the snapshot to Room, replacing the previous one.
- **Sync badge states:**
  - `LIVE · SYNC hh:mm:ss` (green): every source succeeded.
  - `PARTIAL · SYNC …` (amber): some sources failed; their currencies use saved values.
  - `OFFLINE · last sync 2h ago` (amber): everything failed; saved snapshot shown.
  - `NO DATA`: first launch with no network. Shows the message "Connect to the internet to load rates" and a retry button.
- A currency missing from every source shows "—" and dimmed text in its row.

### 5.4 Currency catalog

- **Bundled `currencies.json`:** ISO 4217 code, name, flag emoji and minor-unit digits for every fiat currency.
- **Crypto list:** built from the Binance `*USDT` symbols, plus a bundled name map for about the top 200 coins.
- **Fallback:** coins without a bundled name show their ticker.
- **Icons:** crypto shows a styled symbol tile, so no icon downloads are needed.

## 6. Screens

### 6.1 Convert (home)

- **Layout:** a list of favourites, each row showing flag/tile, code, daily change % and amount. Below it, a 4×4 keypad that is always visible: `7 8 9 ÷ / 4 5 6 × / 1 2 3 − / . 0 ⌫ +`.
- **Typing:**
  - One row is **active**, with a green left border and green amount. The keypad edits that row's expression. Its small top line shows the expression ("25×4") and its big line shows the result.
  - Tapping another row makes it active, and its current displayed value becomes its new amount.
  - Long-pressing ⌫ clears the expression.
- **Expressions:**
  - Operators `+ − × ÷` follow normal precedence. No parentheses or percent.
  - An incomplete expression ("25×") shows the result of its last complete part.
  - Division by zero shows "—".
- **Updates:** every other row updates live as you type.
- **Edit mode (✎):** drag handles to reorder, a remove button on each row, and "+ Add currency". Adding opens the picker, a full-screen search over code and name with separate Fiat and Crypto sections.
- **Default favourites on first run:** the device-locale currency, then USD, EUR, GBP, BTC and ETH, with duplicates removed.
- **Saved across restarts:** favourites, their order, the active currency and its expression (in DataStore).
- **Formatting (`AmountFormatter`):**
  - Thousands are always grouped with a thin space ("39 540"), matching the approved mockups.
  - The decimal separator follows the device locale ("." or ",").
  - `|x| ≥ 1`: the currency's minor-unit decimals (fiat) or 2 decimals (crypto).
  - `|x| < 1`: 4 significant digits, at most 10 decimals.

### 6.2 Charts

- **Pair selector:** two currency chips, using the same picker, plus ⇄ to swap. It defaults to the first two favourites.
- **Header:** the current rate, then the change over the selected range as an absolute value and a percentage.
- **Chart:** a Vico line chart with a green fill, or red when the change is negative. Hold and drag shows a crosshair with the date and value.
- **Ranges:** 1D, 1W, 1M, 3M, 1Y, 5Y and MAX. 1D is disabled, with a tooltip, unless one side of the pair is crypto.
- **Stats:** HIGH, LOW and AVG for the range.
- **Saved pairs:** ☆ saves the pair to a list under the chart, showing each pair's change over the selected range. Tapping a saved pair loads it.

**History sources per range**

| Range | Crypto (Binance klines vs USDT) | Fiat (Frankfurter, USD base) |
|---|---|---|
| 1D | 5m interval | n/a (latest rate held flat) |
| 1W | 1h | daily |
| 1M | 4h | daily |
| 3M, 1Y | 1d | daily |
| 5Y, MAX | 1w | daily, thinned to ≤ 500 points |

**Building a pair's chart:**
- **Cross pairs** (e.g. BTC/HUF): both series are converted to USD and aligned by timestamp. Fiat values carry forward over weekends and holidays.
- **Fiat outside the ECB set:** history is sampled from fawazahmed0 dated snapshots, at most 30 requests per range. Ranges go up to 1Y, limited by how far back that archive reaches; longer ranges are disabled for those currencies.
- **Caching:** history is saved in Room per (currency, range) and reused if it's younger than:
  - 1D: 5 min
  - 1W/1M: 1 h
  - longer ranges: 24 h
- **Load failure:** the saved series is shown with a "couldn't refresh" note and a retry button. If nothing is saved, an empty state with a retry button appears.

### 6.3 Camera

- **Layout:** a full-screen CameraX preview with a green aim box (about 60% × 15% of the screen, centred slightly above the middle). The area outside the box is dimmed.
- **Top chips:**
  - `FROM → TO ▾`: picks the "from" currency, which is remembered. "To" defaults to the top favourite that isn't the "from" currency.
  - ⚡ turns the torch on and off.
- **Bottom controls:**
  - 🖼 opens the gallery (Photo Picker).
  - ❚❚ freezes or unfreezes the reading.
  - ⇄ swaps "from" and "to".
- **Result card** below the box: the converted amount (large, green) and "1 290 HUF · tap to open in Convert". Tapping it puts the amount into the Convert tab with "from" as the active row.
- **Permission:** asked the first time the tab is opened. If denied, the screen explains why the camera is needed and offers a button that opens app settings.
- **Gallery flow:** OCR runs on the whole image, finds all price candidates and lists them as tappable chips with their converted values.

## 7. OCR pipeline

1. **Frames:** CameraX `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST`, analysing at most one frame every 300 ms.
2. **Crop:** map the aim box to image coordinates, accounting for rotation, and crop.
3. **Preprocess (`ImagePreprocessor`):** greyscale, then scale so the text height is about 40 px, then Otsu binarisation. If the background is darker than the text, invert it.
4. **Tesseract:**
   - Model: `eng` from tessdata_fast, bundled in assets and copied to `filesDir` on first use.
   - Page segmentation mode `SPARSE_TEXT`.
   - Character whitelist: `0123456789.,' ` plus non-breaking space.
   - One engine instance on a dedicated single-thread executor.
5. **Pick the price:** from the word-level results, take the tokens with the tallest bounding box (the price is usually the biggest text). Merge neighbouring tokens on the same line when they form a grouped number ("1 290").
6. **Parse (`PriceParser`):**
   - Spaces, apostrophes and non-breaking spaces are always thousands separators.
   - If both `.` and `,` appear, the last one is the decimal separator.
   - A separator that appears more than once is a thousands separator.
   - A single separator followed by exactly 3 digits is a thousands separator ("1.290" → 1290). Followed by 1–2 digits, it is a decimal ("12.99", "12,5").
   - Discard anything that doesn't parse or is 0.
7. **Stabilise:** show a value only once the same value has been parsed in 2 consecutive frames. Keep the last shown value until a new stable value appears.
8. **Freeze:** stop analysis and keep the current value.

OCR never sends data anywhere. Images are processed in memory and dropped.

## 8. Error handling summary

| Situation | Behaviour |
|---|---|
| One source fails | Its currencies use saved values; badge shows `PARTIAL` |
| All sources fail | Saved snapshot shown; badge shows `OFFLINE · last sync …` |
| First launch offline | Empty state "Connect to the internet to load rates" with retry |
| Currency missing everywhere | Row shows "—", dimmed |
| History fetch fails | Saved series with a note, or empty state with retry |
| Camera permission denied | Explanation with a button to open app settings |
| OCR finds nothing | Hint "Point the box at a price"; no flicker |
| Malformed API data | Source treated as failed (logged with `Log.w`, never crashes) |

## 9. Testing

**Unit tests** (JVM, JUnit4 + kotlinx-coroutines-test):
- `ExpressionEvaluator`: precedence, incomplete input, division by zero, decimal edge cases.
- `PriceParser`: a table of real-world formats, at least 30 cases: "1 290", "1.290,00", "12.99", "1,299.50", "12,5", "1'299.00", noise.
- `Converter` and `AmountFormatter`: cross rates, tiny crypto amounts, minor units.
- `RatesRepository`: uses fake remote sources and an in-memory store to check merge order, fallbacks, PARTIAL/OFFLINE status and the first-launch empty state.
- History alignment: cross-pair alignment, forward-fill, thinning.
- DTO parsing: against recorded JSON fixtures, using MockWebServer.

**OCR regression:** about 10 bundled photos of price tags, run through preprocess, Tesseract and parse in an instrumented test, each asserting the expected value.

**Manual on device:** camera reading on real price tags, overall feel, offline mode (aeroplane mode), and a cold start sync.

## 10. Build and release groundwork

- **Toolchain:** JDK via mise (`temurin-21`, pinned in the project's `.mise.toml`). The Android SDK is at `~/Android/Sdk`, referenced from `local.properties`, which is not committed. Gradle wrapper with a version catalog (`libs.versions.toml`).
- **Builds:** debug builds install with `adb install`. The release build enables R8 minification and has rules for Tesseract's JNI and kotlinx.serialization.
- **Signing:** the release keystore lives outside the project, at `~/.keystores/ticker-release.jks`. Its passwords are read from `~/.gradle/gradle.properties`. The README documents how to back it up.
- **Docs:** `PRIVACY.md` (no data collected; the camera is processed on the device) and a short `README.md`.
- **Fonts:** JetBrains Mono and Space Grotesk, both under the SIL Open Font License, bundled with their licence files.

## 11. Verified on 2026-11-07

- **Endpoints:** Frankfurter `/v1/latest?base=USD` and `/v1/{start}..{end}?base=USD&symbols=…` both work. fawazahmed0 `@latest` and `@YYYY-MM-DD` work on jsDelivr, with `YYYY-MM-DD.currency-api.pages.dev` as a mirror. Its archive starts **2024-03-02**. Binance `data-api.binance.vision` `ticker/price`, `ticker/24hr?type=MINI&symbols=` and `klines` all work.
- **Libraries:**
  - tesseract4android `cz.adaptech.tesseract4android:tesseract4android:4.9.0` (JitPack)
  - Vico 3.3.1, CameraX 1.6.2
  - AGP 9.5.1, Kotlin 2.4.21, KSP 2.3.13
- **F-Droid note:** F-Droid builds dependencies from source, so JitPack binaries need an F-Droid srclib recipe at submission time. This doesn't block v1.
