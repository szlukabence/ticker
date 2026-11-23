# Ticker Trips (0.3.0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A trip spending log. You start a trip, save conversions and receipt totals with one tap, and see the total, the daily average and the budget. You can correct an entry with what the bank actually charged, export a trip as CSV, and back up or restore all trips.

**Architecture:**
- Pure logic lives in `dev.ticker.converter.trips`: models, `EntryFactory`, `TripMath`, `TripCsv`, `TripBackup`. All of it is JVM-tested.
- A separate Room database, `trips.db`, sits behind `TripsStore`. `TripsRepository` holds the rules: one active trip, undo, and merge-on-restore. It is tested against `FakeTripsStore`.
- The screens are Convert's chip and Add button, the receipt's "Add to trip", the trip screen, the trip and entry editors, and the all-trips screen. They are thin composables over view models that the tests cover.

**Tech Stack:** Kotlin, Compose, Room 2.8.5 with KSP 2.3.13, kotlinx.serialization 1.12.0, and the Activity Result API (CreateDocument / OpenDocument). Tests use JUnit4, kotlin.test and kotlinx-coroutines-test.

**Spec:** `/home/szlukabence/Work/currency-converter/docs/superpowers/specs/2026-11-23-ticker-trips-design.md`

## Global Constraints

- **Dependencies:** no new libraries, Gradle plugins or permissions. `allowBackup="false"` stays.
- **Money:** always `BigDecimal`, stored as `toPlainString()`. Never use `Double`.
- **Tests:** run with `./gradlew :app:testDebugUnitTest`, from the working directory `app/`. Use the existing `MainDispatcherRule` for view models.
- **Strings:** every new user-facing string goes in **both** `values/strings.xml` and `values-hu/strings.xml` in the same task. `StringParityTest` and `NoHardcodedTextTest` must stay green after every task. Model text uses `UiText`.
  - Keys are prefixed `trip_`, `trips_` and `entry_`.
  - The Hungarian terms are fixed:

    | English | Hungarian |
    |---|---|
    | Trip / Trips | Utazás / Utazások |
    | Add | Hozzáadás |
    | Undo | Visszavonás |
    | budget | keret |
    | daily average | napi átlag |
    | Bank charged | A bank terhelése |
    | card / cash | kártya / készpénz |
    | Back up | Mentés |
    | Restore | Visszaállítás |
    | End trip | Utazás lezárása |

- **Git:** a local commit per task is fine once the user OKs this plan. No tags or pushes without an explicit OK. Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` (use the session's actual model name).
- **Phone:** install with `~/Android/Sdk/platform-tools/adb install -r` only. Ask before each phone session. Never uninstall, never run connected tests, never change phone settings. Set the in-app theme back to System afterwards.
- **Trip test data:** create trips on the phone only with the user's OK. Delete them afterwards, through the app, only if the user asks.
- **Release:** 0.3.0, versionCode 6, ABI codes 60–63.
  - Changelogs go in `en-US/changelogs/63.txt` and `hu-HU/changelogs/63.txt`. The text is in spec §4.
  - Update the fdroid yml: add Builds blocks for 60–63 with `commit: COMMIT_HASH`, and set `CurrentVersion: 0.3.0` and `CurrentVersionCode: 63`.

## Review Focus

1. **Undo after several quick Adds:** Undo removes only the entry saved last, by its ID. Task 4's test pins this; the reviewer checks the job and state handling.
2. **Restoring the same file twice:** the second restore adds 0 and says "Nothing new to restore". Task 3 pins this, and Task 7 checks the message.
3. **Rounding:** the CSV `counted` sum, `TripMath.total` and the backup's round-trip all agree. Task 2 pins this.
4. **Time zone:** `byDay` groups by the *current* zone. A test with `Europe/Budapest` and `America/New_York` pins this documented choice (Task 1).
5. **Hungarian overflow:** the sync-badge row with the chip and "+ Hozzáadás" must fit at 1080 px. Long names are cut with an ellipsis (`maxLines = 1`, `TextOverflow.Ellipsis`). Check on the phone in Task 8.

---

### Task 1: Trip models, EntryFactory, TripMath

**Files:**
- Create: `app/src/main/java/dev/ticker/converter/trips/TripModels.kt`, `trips/EntryFactory.kt`, `trips/TripMath.kt`
- Modify: `core/model/Models.kt` (add `@Serializable enum class Payment { CARD, CASH }`, and `Settings.lastPayment: Payment = Payment.CARD`)
- Test: `app/src/test/java/dev/ticker/converter/trips/EntryFactoryTest.kt`, `trips/TripMathTest.kt`

**Interfaces:**
- **Produces, in `trips/TripModels.kt`:**
  - `data class Trip(val id: String, val name: String, val homeCode: String, val startedAt: Instant, val endedAt: Instant?, val budgetHome: BigDecimal?)`, with `val active get() = endedAt == null`
  - `data class Entry(val id: String, val tripId: String, val at: Instant, val amount: BigDecimal, val code: String, val rateToHome: BigDecimal, val rateAt: Instant, val rateSource: RateOrigin, val feeBps: Int, val homeAmount: BigDecimal, val actualHome: BigDecimal?, val payment: Payment, val note: String?, val source: EntrySource)`
  - `enum class RateOrigin { ECB, FAWAZ, MIXED }`
  - `enum class EntrySource { CONVERT, RECEIPT, MANUAL }`
  - All of them are `@Serializable`. Use a `BigDecimalAsString` and an `InstantAsEpochMs` serializer, both in the same file.
- **Produces `EntryFactory`:**
  - `object EntryFactory { fun fromConversion(amount: BigDecimal, code: String, trip: Trip, homeDecimals: Int, snap: RateSnapshot?, feeOn: Boolean, feeBps: Int, payment: Payment, source: EntrySource, now: Instant, note: String? = null, newId: () -> String = { UUID.randomUUID().toString() }): EntryResult }`
  - `sealed interface EntryResult { data class Ok(val entry: Entry) : EntryResult; data class Unavailable(val reason: Reason) : EntryResult }`
  - `enum class Reason { NO_RATES, NO_RATE_FOR_PAIR, ZERO_AMOUNT }`
- **Produces `TripMath`:**
  - `object TripMath { fun counted(e: Entry): BigDecimal; fun total(es: List<Entry>): BigDecimal; fun byDay(es: List<Entry>, zone: ZoneId): List<Pair<LocalDate, List<Entry>>>; fun days(trip: Trip, today: LocalDate, zone: ZoneId): Int; fun dailyAverage(trip: Trip, es: List<Entry>, today: LocalDate, zone: ZoneId, homeDecimals: Int): BigDecimal; fun budgetLeft(trip: Trip, total: BigDecimal): BigDecimal?; fun budgetFraction(trip: Trip, total: BigDecimal): Float? }`
- **Rules:**
  - `rateSource` comes from `snap.sourceFor[code]` and `[home]`. Both `FRANKFURTER` gives ECB, both `FAWAZ` gives FAWAZ, anything else gives MIXED. When `code == home`, the rate is `BigDecimal.ONE` and the source is ECB.
  - The fee applies only when `payment == CARD && feeOn`, through `Fee.apply`. Rounding is `setScale(homeDecimals, HALF_UP)`.
  - `dailyAverage` uses the same scale and HALF_UP.

- [ ] **Step 1: Write the failing tests.**
  - **`EntryFactoryTest`.** The snapshot has `usdPer` EUR = 1.10 and HUF = 1.10/396.02, both FRANKFURTER.
    - `workedExample`: 42.50 EUR, CARD, fee on, 150 bps, HUF with 2 decimals. `homeAmount == BigDecimal("17083.00")` and `rateToHome` rounds to 396.02 at 2 places.
    - `cashHasNoFee`: CASH with fee on gives `homeAmount == 16830.85` and `feeBps == 0`.
    - `feeOffHasNoFee`.
    - `sameCurrencyRateIsOne`: 1000 HUF into a HUF trip gives `rateToHome == 1`, and with CASH, `homeAmount == 1000.00`.
    - `noSnapshot`: `Unavailable(NO_RATES)`.
    - `missingPair`: `Unavailable(NO_RATE_FOR_PAIR)`.
    - `zeroAndNegative`: `Unavailable(ZERO_AMOUNT)`.
    - `mixedSource`: EUR is FRANKFURTER and BTC is FAWAZ, so the result is MIXED.
    - `rateAtIsSnapshotTime`.
    - `idFromGenerator`.
  - **`TripMathTest`.**
    - `countedPrefersActual`.
    - `totalSumsCounted`: three entries, one with `actualHome`.
    - `byDayNewestFirst`.
    - `byDayUsesCurrentZone`: an entry at 2026-11-18T22:30Z falls on Nov 18 in New York and Nov 18 in Budapest (23:30). An entry at 23:30Z falls on Nov 19 in Budapest and Nov 18 in New York.
    - `daysInclusiveMinOne`: started today gives 1, and a 4-day trip gives 4.
    - `endedTripStopsAtEnd`.
    - `averageRounds`: 148 920 over 4 days gives 37 230.00.
    - `budgetLeftNegativeWhenOver`.
    - `fractionClamped`.
    - `noBudgetNull`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*EntryFactoryTest' --tests '*TripMathTest'`. Expected: compile failure, because the types are undefined.
- [ ] **Step 3: Implement** the models, `EntryFactory`, `TripMath`, `Payment` and `Settings.lastPayment`. Check that old settings JSON still decodes: `SettingsCodecTest` keeps passing, since there is a default.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest`. Expected: all pass.
- [ ] **Step 5: Commit** `Trips: models, entry factory and trip maths`.

### Task 2: CSV and backup format

**Files:**
- Create: `trips/TripCsv.kt`, `trips/TripBackup.kt`
- Test: `trips/TripCsvTest.kt`, `trips/TripBackupTest.kt`

**Interfaces:**
- Consumes: Task 1's models and `TripMath`.
- **Produces `TripCsv`:**
  - `object TripCsv { const val HEADER = "date,time,note,amount,currency,rate_to_home,rate_time,rate_source,fee_percent,home_amount,bank_charged,counted,home_currency,payment,source"; fun write(trip: Trip, entries: List<Entry>, zone: ZoneId): String }`
  - The output starts with `﻿` and uses CRLF line endings. Rows run oldest first.
  - Formats: `date` is `yyyy-MM-dd`, `time` is `HH:mm`, and `rate_time` is ISO-8601 in that zone. `fee_percent` is `Fee.label` without the `%`. `bank_charged` is empty when unset.
  - Quote a field when it contains `,`, `"`, CR or LF, and double the quotes inside it.
- **Produces `TripBackup`:**
  - `object TripBackup { fun encode(trips: List<Trip>, entries: List<Entry>, now: Instant): String; fun decode(text: String): Decoded; fun merge(existing: Snapshot, incoming: Snapshot, activeTripId: String?): Merge }`
  - `data class Snapshot(val trips: List<Trip>, val entries: List<Entry>)`
  - `sealed interface Decoded { data class Ok(val snapshot: Snapshot) : Decoded; data class Error(val kind: Kind) : Decoded }`
  - `enum class Kind { NOT_A_BACKUP, NEWER_VERSION, CORRUPT }`
  - `data class Merge(val trips: List<Trip>, val entries: List<Entry>)` holds only what is new. An incoming active trip comes in as ended (`endedAt` = its latest entry `at`, or `startedAt`) when `activeTripId != null` and it's a different trip. An entry whose trip is in neither set is dropped.
  - JSON: `{"format":"ticker-trips","version":1,"exportedAt":<ms>,"trips":[{...trip,"entries":[...]}]}`.

- [ ] **Step 1: Write the failing tests.**
  - **`TripCsvTest`:**
    - `startsWithBomAndHeader`.
    - `crlfLines`.
    - `oldestFirst`.
    - `quotesNoteWithCommaAndQuote`: the note `Lunch, "Naschmarkt"` becomes `"Lunch, ""Naschmarkt"""`.
    - `bankChargedEmptyWhenUnset`.
    - `countedColumnSumsToTotal`: parse the output with a small test CSV splitter and check that the sum of `counted` equals `TripMath.total`, for five entries, two of them with `actualHome`.
    - `decimalPointNotLocale`: run under `Locale.setDefault(hu-HU)` and check that the output uses `.`. Restore the default in `@After`.
  - **`TripBackupTest`:**
    - `roundTrip`: encode, then decode, gives equal lists, with BigDecimal scale kept (`17083.00` stays `17083.00`).
    - `rejectsOtherJson`: gives NOT_A_BACKUP.
    - `rejectsNewer`: version 2 gives NEWER_VERSION.
    - `garbage`: gives CORRUPT.
    - `mergeSkipsExistingIds`.
    - `mergeTwiceAddsNothing`: merge, apply, then merge the same input again gives empty lists.
    - `incomingActiveBecomesEndedWhenLocalActive`.
    - `incomingActiveStaysActiveWhenNoneLocal`.
    - `orphanEntryDropped`.
- [ ] **Step 2: Run** `--tests '*TripCsvTest' --tests '*TripBackupTest'`. Expected: compile failure.
- [ ] **Step 3: Implement** `TripCsv` and `TripBackup`. Use `Json { ignoreUnknownKeys = true }` and wrapper data classes that are private to the file.
- [ ] **Step 4: Run** the full suite. Expected: all pass.
- [ ] **Step 5: Commit** `Trips: CSV export and backup format`.

### Task 3: Trips database and repository

**Files:**
- Create:
  - `trips/data/TripsDatabase.kt`: `TripEntity`, `EntryEntity` (FK cascade, index on `tripId`), `TripDao`, `TripsConverters`, the `TripsDatabase` class (version 1, `exportSchema = true`, built with **no** destructive fallback)
  - `trips/data/TripsStore.kt`: the `TripsStore` interface and `RoomTripsStore`
  - `trips/TripsRepository.kt`
- Modify:
  - `app/build.gradle.kts`: add `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`
  - `AppContainer.kt`: add `val trips = TripsRepository(RoomTripsStore(TripsDatabase.create(app).dao()), clock)`, and make `clock` public (`val clock`) so the routes can pass it to the view models
- Create (test): `app/src/test/java/dev/ticker/converter/fakes/FakeTripsStore.kt`, `trips/TripsRepositoryTest.kt`

**Interfaces:**
- **Produces `TripsStore`:**
  - `interface TripsStore { fun trips(): Flow<List<Trip>>; fun entries(tripId: String): Flow<List<Entry>>; suspend fun allTrips(): List<Trip>; suspend fun allEntries(): List<Entry>; suspend fun upsertTrip(t: Trip); suspend fun upsertEntry(e: Entry); suspend fun deleteEntry(id: String); suspend fun deleteTrip(id: String); suspend fun insertAll(trips: List<Trip>, entries: List<Entry>) }`
  - `insertAll` runs in one Room `@Transaction`. `deleteTrip` cascades to the trip's entries.
- **Produces `TripsRepository`:**
  - `class TripsRepository(private val store: TripsStore, private val clock: Clock, private val newId: () -> String = { UUID.randomUUID().toString() })`
  - `val trips: Flow<List<Trip>>` is sorted with the active trip first, then `startedAt` descending.
  - `val active: Flow<Trip?>`
  - `fun entries(tripId: String): Flow<List<Entry>>`
  - `suspend fun start(name: String, homeCode: String, budget: BigDecimal?): StartResult`, where `sealed interface StartResult { data class Started(val trip: Trip); data class OtherActive(val trip: Trip) }`
  - `suspend fun endAndStart(name, homeCode, budget): Trip`
  - `suspend fun end(id: String)`
  - `suspend fun update(trip: Trip)`
  - `suspend fun add(e: Entry)`, `suspend fun updateEntry(e: Entry)`, `suspend fun deleteEntry(id: String)`, `suspend fun deleteTrip(id: String)`
  - `suspend fun exportAll(): TripBackup.Snapshot`
  - `suspend fun restore(s: TripBackup.Snapshot): RestoreCount`, where `data class RestoreCount(val trips: Int, val entries: Int)`
  - `start` trims the name. A blank name throws `IllegalArgumentException`; the UI blocks it first.
  - `update` throws when the home currency changes on a trip that has entries.

- [ ] **Step 1: Write the failing tests** (`TripsRepositoryTest`, with `FakeTripsStore` and a fixed clock):
  - `startCreatesActive`.
  - `secondStartReportsOtherActive`.
  - `endAndStartEndsOld`.
  - `endSetsEndedAt`.
  - `activeFirstThenNewest`.
  - `deleteTripRemovesEntries`.
  - `homeCurrencyLockedWithEntries`.
  - `homeCurrencyChangeableWhenEmpty`.
  - `restoreCountsOnlyNew`.
  - `restoreTwiceIsZero`.
  - `restoreKeepsSingleActive`.
  - `blankNameRejected`.
- [ ] **Step 2: Run** `--tests '*TripsRepositoryTest'`. Expected: compile failure.
- [ ] **Step 3: Implement** the Room layer, the store, the repository and the container wiring. `FakeTripsStore` keeps `MutableStateFlow` maps.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected:
  - The tests pass.
  - The build succeeds.
  - `app/schemas/dev.ticker.converter.trips.data.TripsDatabase/1.json` exists.
- [ ] **Step 5: Commit** `Trips: separate database and repository`. Include `app/schemas/`.

### Task 4: Convert chip, one-tap Add and Undo

**Files:**
- Create: `ui/trips/TripChip.kt`, `ui/trips/TripEditorDialog.kt`
- Modify:
  - `ui/convert/ConvertViewModel.kt`, `ui/convert/ConvertScreen.kt:91-96` (the sync-badge row)
  - `ui/nav/TickerNavHost.kt`: add routes `trip/{id}` and `trips` (placeholder screens until Tasks 6–7), and pass `onOpenTrip` / `onOpenTrips` into `ConvertRoute`
  - `ConvertViewModelTest.kt`: constructor call sites at lines 47 and 266
- Test: `ui/convert/ConvertViewModelTest.kt` (new cases), `ui/trips/TripEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `TripsRepository`, `EntryFactory`.
- **Produces, in `ConvertViewModel`:**
  - The constructor gains `trips: TripsRepository` and `clock: Clock`.
  - `ConvertUiState` adds:
    - `trip: TripChipUi?`, where `data class TripChipUi(val id: String, val name: String)`
    - `addBlocked: EntryFactory.Reason?` (null means Add is enabled)
    - `saved: SavedNote?`, where `data class SavedNote(val entryId: String, val tripName: String)`
  - `fun onAddToTrip()`: builds the entry from the active row's code and evaluated amount, with `Settings.lastPayment` and `source = CONVERT`. It saves the entry, sets `saved`, and clears `saved` after 4 000 ms. A new Add cancels the previous timer.
  - `fun onUndoAdd()`: deletes `saved.entryId` and clears `saved`.
- **Produces `TripEditorViewModel(trips, catalog, defaultHome: String, editing: Trip?)`:**
  - `state: name, homeCode, budgetText, homeLocked, error: UiText?, conflict: Trip?`
  - `fun save(): Trip?` returns the trip, or sets `conflict` when another trip is active.
  - `fun endAndStart(): Trip`
  - The budget is parsed with the keypad rules: either `.` or `,` works as the decimal mark, blank means none, and ≤ 0 is an error.
- **UI:**
  - **The row:** `SyncBadge`, the fee text, `Spacer(weight)`, then `TripChip`.
    - The chip is a NeonPill button showing `+ Trip` or `● NAME`, with `maxLines = 1` and an ellipsis at max 140 dp.
    - Next to it, `+ Add` (`trip_add`) shows when there's a trip and the amount is above 0. It's disabled when `addBlocked != null`, and its description becomes `trip_add_blocked_<reason>`.
  - **The saved line:** a row under the chip row with `trip_saved` ("Saved to %1$s") and an Undo text button (`trip_undo`). It slides in and out, with no motion when reduced motion is on.

- [ ] **Step 1: Write the failing tests.**
  - **`ConvertViewModelTest`:**
    - `chipShowsActiveTrip`.
    - `addSavesEntryWithLastPayment`.
    - `addBlockedWithoutRates` (`NO_RATES`).
    - `addHiddenWhenNoTrip` (`trip == null`).
    - `undoDeletesSavedEntry`.
    - `undoAfterTwoAddsDeletesOnlySecond`.
    - `savedClearsAfter4s` (using `advanceTimeBy(4_001)`).
    - `secondAddRestartsTimer`.
  - **`TripEditorViewModelTest`:**
    - `defaultsToRegionHome`.
    - `blankNameError`.
    - `budgetCommaOrPoint`.
    - `negativeBudgetError`.
    - `conflictWhenActive`.
    - `endAndStartWorks`.
    - `homeLockedWhenEditingTripWithEntries`.
- [ ] **Step 2: Run** `--tests '*ConvertViewModelTest' --tests '*TripEditorViewModelTest'`. Expected: compile failure, then FAIL.
- [ ] **Step 3: Implement.**
  - `defaultHome` comes from `catalog.defaultFavorites(systemLocale()).first()`.
  - `homeDecimals` comes from `catalog.get(home)?.decimals ?: 2`.
  - Add the en and hu strings, including `trip_add_blocked_no_rates`, `trip_add_blocked_no_pair` and `trip_add_blocked_zero`.
- [ ] **Step 4: Run** the full suite. Expected: all pass, and the parity and no-hardcoded tests stay green.
- [ ] **Step 5: Commit** `Trips: Convert chip, one-tap add and undo`.

### Task 5: Add to trip from a receipt

**Files:**
- Modify: `ui/receipt/ReceiptViewModel.kt` (the constructor gains `trips: TripsRepository` and `clock: Clock`), `ui/receipt/ReceiptScreen.kt:173` area, `ReceiptViewModelTest.kt`

**Interfaces:**
- **Produces:**
  - `ReceiptUiState.trip: TripChipUi?`
  - `ReceiptUiState.saved: SavedNote?`
  - `fun onAddToTrip()`: saves `includedSum` in `from`, with `source = RECEIPT`, `note = null` and the last payment.
  - `fun onUndoAdd()`
  - It uses the same 4 s timer as Task 4. Extract a small helper class `SavedNoteTimer(scope)` in `ui/trips/SavedNoteTimer.kt` and use it in both view models.

- [ ] **Step 1: Write the failing tests:**
  - `receiptAddSavesIncludedSum`: two items with one excluded, so the entry amount equals the remaining item.
  - `receiptAddSourceIsReceipt`.
  - `receiptNoTripNoButton`.
  - `receiptUndo`.
- [ ] **Step 2: Run** `--tests '*ReceiptViewModelTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - Add a `NeonPillButton(stringResource(R.string.trip_add_to_trip), vm::onAddToTrip, icon = TickerIcons.Plus)` next to the open-total control, shown only when `state.trip != null`.
  - Add the same saved line as in Task 4.
  - Add the en and hu strings.
- [ ] **Step 4: Run** the full suite. Expected: all pass.
- [ ] **Step 5: Commit** `Trips: add a receipt total to the trip`.

### Task 6: Trip screen and entry editor

**Files:**
- Create: `ui/trips/TripScreen.kt`, `ui/trips/TripViewModel.kt`, `ui/trips/EntryEditorDialog.kt`, `ui/trips/EntryEditorViewModel.kt`
- Modify: `ui/nav/TickerNavHost.kt`: route `trip/{id}` becomes `TripRoute`
- Test: `ui/trips/TripViewModelTest.kt`, `ui/trips/EntryEditorViewModelTest.kt`

**Interfaces:**
- **Produces `TripViewModel(trips, rates, settings, catalog, formatter, tripId, clock, zone)`:**
  - `state: TripUiState(trip, totalText, totalAltText /* ≈ in the first other favourite, null if none */, averageText, count, budgetFraction, budgetLeftText, overBudget, days: List<DayUi>)`
  - `data class DayUi(val date: LocalDate, val rows: List<EntryRowUi>)`
  - `data class EntryRowUi(val id: String, val label: UiText /* note or trip_entry_default_<source> */, val original: String, val payment: Payment, val counted: String, val bankTag: String?)`
  - `fun onEnd()`
  - `fun csv(): Pair<String /* file name */, String /* content */>`
  - The file name is `"${slug(name)}-${yyyy-MM-dd}.csv"`, where `slug` lowercases the name, keeps letters and digits including accented ones, and replaces runs of anything else with `-`.
- **Produces `EntryEditorViewModel(trips, rates, settings, catalog, formatter, tripId, editing: Entry?, clock)`:**
  - `state: amountText, code, payment, note, at, bankText, estimateText, rateText /* "1 EUR = 396.02 HUF" */, rateFromText /* "ECB · 18 Nov 09:41" */, diffText /* "+37 HUF" */, error: UiText?, confirmDelete: Boolean`
  - **New entry (MANUAL):** `save()` converts with the current snapshot via `EntryFactory`, and shows the reason when it can't.
  - **Edit:** `save()` keeps `amount`, `code`, the rate fields and `homeAmount`. It updates `note`, `payment`, `at` and `actualHome` (blank means null; parsed with `.` or `,`; must be > 0). It also writes `Settings.lastPayment`.
  - `fun requestDelete()` and `fun delete()`.
- **UI:**
  - Lay the screen out as spec §3 and the research page's mockup 2. The header menu holds Edit trip (opens `TripEditorDialog`), End trip (confirmed in the screen), Save CSV and All trips.
  - **Save CSV** uses `rememberLauncherForActivityResult(CreateDocument("text/csv"))`. It writes `vm.csv().second` through `contentResolver.openOutputStream(uri)` in UTF-8, then shows `trip_csv_saved` or `trip_file_error`.
  - Tapping a row opens `EntryEditorDialog`. The editor shows the note `entry_fee_note`: "Card or cash doesn't change the saved amount. Enter what the bank charged instead."

- [ ] **Step 1: Write the failing tests.**
  - **`TripViewModelTest`:**
    - `summaryNumbers`: 4 days with a total of 148 920.00 gives an average of 37 230 and the matching fraction.
    - `overBudgetFlag`.
    - `groupsByDay`.
    - `bankTagShown`.
    - `defaultLabelBySource`.
    - `csvFileName`: "Bécs tél" gives `bécs-tél-2026-11-23.csv`.
    - `csvContentMatchesTripCsv`.
  - **`EntryEditorViewModelTest`:**
    - `manualSaveConverts`.
    - `manualNoRatesShowsReason`.
    - `editKeepsHomeAmount`.
    - `editSetsActualAndDiff`: 17 120 against an estimate of 17 083 gives "+37".
    - `blankBankClearsActual`.
    - `badBankError`.
    - `paymentChangeDoesNotRecalculate`.
    - `paymentSavedAsLast`.
    - `deleteNeedsConfirm`.
- [ ] **Step 2: Run** them. Expected: compile failure.
- [ ] **Step 3: Implement** the view models and screens. Add the en and hu strings.
- [ ] **Step 4: Run** the full suite. Expected: all pass.
- [ ] **Step 5: Commit** `Trips: trip screen and entry editor`.

### Task 7: All trips, backup and restore

**Files:**
- Create: `ui/trips/TripsScreen.kt`, `ui/trips/TripsViewModel.kt`
- Modify: `ui/nav/TickerNavHost.kt` (route `trips`)
- Test: `ui/trips/TripsViewModelTest.kt`

**Interfaces:**
- **Produces `TripsViewModel(trips, catalog, formatter, clock, zone)`:**
  - `state: rows: List<TripRowUi(id, name, datesText, totalText, active)>, message: UiText?, confirmDeleteId: String?`
  - `fun backup(): Pair<String /* "ticker-trips-YYYY-MM-DD.json" */, String>`
  - `fun restore(text: String)` sets `message`:
    - `trips_restored` with a plural for trips and entries ("Restored 2 trips, 31 entries")
    - `trips_restore_nothing`
    - `trips_restore_error_<kind>`
  - `fun requestDelete(id)`, `fun confirmDelete()`, `fun cancelDelete()`
- **UI:** the list, plus a menu with Back up all trips (`CreateDocument("application/json")`) and Restore from backup (`OpenDocument(arrayOf("application/json", "text/plain", "*/*"))`). Read the file as UTF-8 text and pass it to `restore`. IO errors become `trip_file_error`.

- [ ] **Step 1: Write the failing tests:**
  - `rowsActiveFirst`.
  - `backupNameAndRoundTrip`: `backup().second` decodes to the store's contents.
  - `restoreMessageCounts`.
  - `restoreTwiceNothing`.
  - `restoreErrorKinds`: covers all three kinds.
  - `deleteNeedsConfirm`.
- [ ] **Step 2: Run** them. Expected: compile failure.
- [ ] **Step 3: Implement.** Add the en and hu strings, with `<plurals>` for `trips_count` and `entries_count`.
- [ ] **Step 4: Run** the full suite. Expected: all pass.
- [ ] **Step 5: Commit** `Trips: all trips, backup and restore`.

### Task 8: Release 0.3.0 and phone check

**Files:**
- Modify: `app/build.gradle.kts`, `fastlane/metadata/android/{en-US,hu-HU}/changelogs/63.txt`, both `full_description.txt` files, `docs/fdroid/io.github.szlukabence.ticker.yml`

- [ ] **Step 1: Write the release files** as described in Global Constraints. Run `./gradlew :app:testDebugUnitTest :app:lintDebug`. Expected: the tests pass and lint reports no new errors.
- [ ] **Step 2: Phone check** (ask the user first; the trips themselves are created by the user or with their OK).
  - Install with `-r`.
  - **Convert:** check that the `+ Trip` chip appears. Create the trip "Teszt" in HUF with a budget of 200 000.
  - **Adding entries:** type 42.50 EUR, tap `+ Add`, and check the saved line and Undo. Add 2 more.
  - **Receipt:** add a receipt total. If no receipt is at hand, use the gallery with any receipt photo the user picks.
  - **Trip screen:** check the total, the average and the budget bar. Edit one entry's bank charge.
  - **Files:** Save the CSV to Downloads, then `adb pull` it and check that the sum of `counted` matches the screen. Back up, then restore: the result should be "nothing new".
  - **Themes and language:** repeat the Convert row and the trip screen in Hungarian (`cmd locale set-app-locales … hu-HU`, then reset), and in Light and Dark.
  - **Wrap up:** screenshot each step for the user. Set the theme back to System and the locale back to default. Ask the user whether to delete "Teszt".
- [ ] **Step 3: Native review:** send the user the changed hu strings (the `trip_*`, `trips_*` and `entry_*` keys) and the hu changelog and description for review. Apply their fixes and run the suite again.
- [ ] **Step 4: Commit** `Trips (0.3.0)`.

### Final

- **Review:** a whole-branch review by `buildflow:buildflow-reviewer` (Opus, high) of the diff since `6abce94`, together with the spec and this plan's Review Focus.
- **Fixes:** one fix pass, done with TDD.
- **Finish:** use finishing-a-development-branch. Tagging `v0.3.0` and pushing need the user's explicit OK.
