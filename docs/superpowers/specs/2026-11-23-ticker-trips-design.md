# Ticker Trips (0.3.0): design

Date: 2026-11-23. Status: approved in chat on 2026-11-23.

Research page (private): https://claude.ai/artifact/PvpHMWe6WteUSKuxLMoYVa

**Idea check: build it.**
- The paid trip-expense apps put export, custom rates and a second active trip behind a subscription. TravelSpend Premium starts at $4.99, or $59.99 lifetime.
- The free and open-source options can't fetch rates (XPENC), haven't been updated since 2020 (Tricky Tripper), or have no camera and no fee handling (Where It Went).
- Ticker already has live and cached rates, receipt OCR and the card-fee estimate.

## Goal

A trip spending log built on the converter:
- Start a trip, then save conversions and receipt totals to it with one tap.
- See the total in your home currency, the daily average and the budget left.
- Correct entries with the amount the bank actually charged.
- Export the trip as CSV, and back up and restore all trips.

Everything stays on the phone.

## Global constraints

- minSdk 26, targetSdk 36. Room 2.8.5, KSP 2.3.13, kotlinx.serialization 1.12.0.
- No new library dependencies, no new Gradle plugins, no new permissions.
- Unit tests are plain JVM tests (no Robolectric).
- The release is 0.3.0: versionCode 6, ABI codes 60–63, changelogs `en-US/changelogs/63.txt` and `hu-HU/changelogs/63.txt`, plus the fdroid recipe yml.
- Every new user-facing string goes in `values/strings.xml` and `values-hu/strings.xml`. The existing parity and no-hardcoded-text tests enforce this.
- No commit, tag or push without the user's OK. On the phone, use `adb install -r` only. Never uninstall, never run connected tests, never change phone settings.
- Money is always `BigDecimal`, stored as plain strings. It is never a `Double`.
- The app keeps `allowBackup="false"`. Trip data leaves the phone only through files the user saves.

## 1. Storage

- **A new database** `trips.db` (`TripsDatabase`, Room version 1, `exportSchema = true`).
  - Schemas go to `app/schemas/` through the KSP argument `room.schemaLocation`, and are committed.
  - **No** `fallbackToDestructiveMigration`.
  - The existing `ticker.db` (rate cache) is unchanged.
- **`trip` table:**

  | Column | Notes |
  |---|---|
  | `id` | TEXT primary key, UUID |
  | `name` | |
  | `homeCode` | |
  | `startedAt` | epoch ms |
  | `endedAt` | nullable |
  | `budgetHome` | nullable plain-decimal string |

- **`entry` table:** each row saves the numbers it was made with.

  | Column | Notes |
  |---|---|
  | `id` | TEXT primary key, UUID |
  | `tripId` | foreign key to `trip.id`, cascade delete, indexed |
  | `at` | epoch ms |
  | `amount` | string |
  | `code` | |
  | `rateToHome` | string: one unit of `code` in `homeCode`, before the fee |
  | `rateAt` | epoch ms of the snapshot used |
  | `rateSource` | `ECB`, `FAWAZ` or `MIXED`, worked out from `sourceFor` of both codes; `ECB` when both codes are ECB or the code is the home currency |
  | `feeBps` | 0 for cash |
  | `homeAmount` | string, rounded to the home currency's decimals with HALF_UP |
  | `actualHome` | nullable string |
  | `payment` | `CARD` or `CASH` |
  | `note` | nullable |
  | `source` | `CONVERT`, `RECEIPT` or `MANUAL` |

- **The active trip** is the trip with `endedAt IS NULL`. There is at most one.
  - `TripsRepository.start(...)` refuses to start a trip while another is active. The UI asks the user to end the current one first.
- **Access** goes through `interface TripsStore`, which has Flow reads and suspend writes. `RoomTripsStore` implements it. Tests use `FakeTripsStore`.

## 2. Logic (pure, JVM-tested)

- **`EntryFactory.fromConversion(amount, code, trip, snap: RateSnapshot?, feeOn, feeBps, payment, source, now, note = null)`** returns either `Entry` or `Unavailable(reason)`.
  - The reasons are `NO_RATES` (no snapshot), `NO_RATE_FOR_PAIR`, and `ZERO_AMOUNT` (for amounts ≤ 0).
  - `rateToHome = Converter.convert(1, code, home, snap)`.
  - `homeAmount = round(amount × rateToHome × (1 + fee))`. The fee is applied only for CARD with `feeOn`, through the existing `Fee.apply`.
  - When `code == home`, the rate is 1 and the fee rules still apply.
- **`TripMath`:**
  - `counted(e) = e.actualHome ?: e.homeAmount`
  - `total(entries)`
  - `byDay(entries, zone)`: days newest first, entries newest first within a day
  - `dailyAverage(trip, entries, today, zone)`: the total divided by the days from the start to today (or to the end date for an ended trip), inclusive, at least 1
  - `budgetLeft(trip, total)`: null when there's no budget; can be negative
  - `budgetFraction`: clamped to 0..1 for the bar; the text shows the real value
- **Worked example (a test):** EUR 42.50 at 396.02 HUF with a 1.5% card fee (150 bps) gives HUF 17 083.
  - 42.50 × 396.02 = 16 830.85, then × 1.015 = 17 083.31, which rounds to 17 083.00 (HUF has 2 decimals in ISO).
  - The display rounds as the formatter does today.
- **`TripCsv.write(trip, entries, zone): String`:**
  - UTF-8 with a BOM, comma-separated, RFC 4180 quoting, `.` as the decimal mark, CRLF line endings.
  - Header row: `date,time,note,amount,currency,rate_to_home,rate_time,rate_source,fee_percent,home_amount,bank_charged,counted,home_currency,payment,source`.
  - One row per entry, oldest first. No total row.
  - **Test:** parsing it back, the `counted` column sums to `TripMath.total`.
- **`TripBackup`:**
  - Encodes `{"format":"ticker-trips","version":1,"exportedAt":…,"trips":[{…,"entries":[…]}]}`.
  - Decoding rejects files with the wrong `format` or a `version > 1`, with a clear error.
  - `merge(existing, incoming)` returns only the trips and entries whose IDs aren't present yet. An entry whose trip is missing is skipped.
  - A restore never overwrites anything. If the incoming file has an active trip while one is already active here, the incoming one is imported as ended, with `endedAt` set to its last entry or its start.

## 3. Screens

- **Convert**, on the sync-badge row, aligned right (`TripChip`):
  - With no active trip, it shows `+ Trip`, which opens the new-trip dialog.
  - With an active trip, it shows `● NAME`, which opens the trip screen.
  - When a trip is active and the typed amount is above 0, a `+ Add` button sits next to the chip.
    - **Tapping it saves right away:** the active row's code and amount, `source = CONVERT`, and the last-used payment, which is a setting that defaults to CARD.
    - **The confirmation line:** "Saved to NAME · Undo" for 4 s. Undo deletes exactly that entry ID.
    - **When it can't save:** the button is disabled, and its description states the reason.
- **Receipt screen:** a new `Add to trip` button next to "open total", shown when a trip is active. It saves `includedSum` in the receipt's from-currency with `source = RECEIPT`, then shows the same confirmation.
- **Trip screen:** the route `trip/{id}`, outside the dock. The dock keeps highlighting Convert, and Back returns to Convert.
  - **Header:** the name, plus a menu with Edit trip, End trip, Save CSV, All trips.
  - **Summary card:** the total in the home currency, the daily average, the entry count, the budget bar and what's left. Over budget shows in the warning colour.
  - **Entries** grouped by day. Each row shows the note or a default label, the original amount and code, card or cash, the counted home amount, and a "bank: X" tag when the actual charge is set.
  - **An `Add` button** opens the entry editor with `source = MANUAL`. The editor has the amount, a currency picker (reusing `CurrencyPicker`), card or cash, a note, and a date and time defaulting to now. It converts with the current snapshot through `EntryFactory`.
- **Entry editor** (`FullScreenDialog`):
  - **Shown, read-only:** the paid amount, the saved rate with its time and source, and the estimate.
  - **Editable:** the note, card or cash, the date, and "Bank actually charged" (blank means none). It shows the difference from the estimate.
  - **Delete** asks for confirmation in the dialog itself.
  - Changing card or cash doesn't recalculate `homeAmount`. The rate is saved at entry time, so the user corrects the real figure with "bank charged". The editor says so.
- **Trip editor** (`FullScreenDialog`, for both new and edit):
  - The name (required) and the home currency (defaults to the region currency via `CurrencyCatalog.defaultFavorites(systemLocale())`; it can't be changed once there are entries).
  - The budget is optional.
  - "Start trip" is refused while another trip is active, and the dialog offers "End NAME and start".
- **All trips** (route `trips`):
  - The list shows the name, dates, total and an "active" badge. Tapping a trip opens it.
  - Each trip has Delete, confirmed in the screen, which cascades to its entries.
  - The menu has **Back up all trips** (`CreateDocument("application/json")`, default name `ticker-trips-YYYY-MM-DD.json`) and **Restore from backup** (`OpenDocument`). The result line reads "Restored 2 trips, 31 entries", or names the error.
- **Save CSV:** `CreateDocument("text/csv")`, default name `<trip-name>-YYYY-MM-DD.csv`.
- Trips screens follow the app theme. The Camera stays dark.

## 4. Release 0.3.0

- **Changelog (en):** `New: Trips. Start a trip, save conversions and receipt totals with one tap, and see the total in your home currency with a daily average and an optional budget. Correct an entry with what your bank charged. Export a trip as CSV, and back up or restore all trips as a file. Everything stays on your phone.` The hu changelog says the same.
- Add a Trips item to `full_description.txt` in en and hu.

## Review focus

1. **Undo racing a second Add:** Undo must delete only its own entry ID, even after several quick Adds.
2. **Restoring the same backup twice:** the second restore must add nothing and say so.
3. **Rounding:** the CSV sum, the screen total and the backup all have to agree to the minor unit. The total is a sum of rounded amounts, never a rounded sum of raw values.
4. **Time zones:** an entry saved at 23:30 in Budapest has to show under that day after the phone moves to another zone. `byDay` uses the current zone; record and test that choice.
5. **Hungarian overflow:** the Convert row holding the sync badge, the trip chip and `+ Add` has to fit at 1080 px wide in Hungarian. The chip truncates long trip names with an ellipsis.

## Out of scope

Categories, cash pockets, spreading a cost over several nights, a widget line, per-day budgets, splitting between people, Android auto-backup, sync, and the user's own exchange rates (the "bank charged" field covers the need).
