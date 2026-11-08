# Ticker extras: card fee, widget, convert from selection, receipt mode

Date: 2026-11-07 · Status: approved design, awaiting spec review
Builds on: `2026-11-07-ticker-currency-converter-design.md` (the app) and the Neon UI (commit 64c0a40).

## 1. Goal

Four additions that make Ticker more useful day to day, without a server, account or subscription:

1. **Card fee** — show what you actually pay after your bank's markup.
2. **Home-screen widget** — your favourites' rates at a glance.
3. **Convert from selection** — select a price in any app, tap "Convert".
4. **Receipt mode** — read a whole receipt from a photo and convert every line and the total.

All new UI uses the Neon look (`ui/theme/Neon.kt`: `tile()`, `glow()`, `dotGrid()`, `TickerIcons`, `NeonIconButton`, `NeonPillButton`).

## 2. Card fee

**Settings** (DataStore, `Settings`): `feePercent: BigDecimal` (default `2`, range 0–10, step 0.1) and `feeOn: Boolean` (default `false`).

**Rule:** when `feeOn`, every *converted* amount = plain conversion × (1 + feePercent / 100), then rounded to the target currency's decimals as today. The amount the user typed (Convert's active row, the price read by Camera, the selected text, receipt lines' originals) is never changed. When `feeOn` is false, nothing differs from today.

**Where it applies:** Convert (all non-active rows), Camera result, convert-from-selection card, receipt (converted lines and total), widget. Each of these shows a small "incl. 2% fee" label (the current %) while the fee is on.

**Controls:** a chip in the Convert header next to the edit/refresh buttons.
- Off: `FEE` in dim text, outlined. On: `FEE +2%` in green with the glow.
- Tap toggles `feeOn`. Long-press opens a small Neon sheet: −/+ stepper (0.1 steps), preset pills 0.5 · 1 · 1.75 · 2 · 2.5 · 3, and the current value large; changes save immediately.
- Accessibility: the chip is a toggle (`Role.Switch`) labelled "Card fee 2%, on/off"; the long-press action is exposed as a custom accessibility action "Set fee".

## 3. Home-screen widget

**Library:** Jetpack Glance (`glance-appwidget`, Material 3 variant) — Compose-style API that renders RemoteViews.

**Content:** header "1 EUR" (the first favourite, with its glyph) and "updated 14:05"; below, one row per other favourite: code, converted value of 1 unit, ▲/▼ 24 h change (same `changeArrowText`). Fee applies when on, with an "incl. 2% fee" line in the header.

**Sizes:** `SizeMode.Responsive` — small (2×1 cells): header + 2 rows; medium (2×2 / 4×2): header + 4 rows; tall (2×3 and up): header + 6 rows. Rows beyond the favourites count are not drawn.

**Look:** black rounded background, `#2BFF88` accents, JetBrains Mono for numbers where Glance allows (falls back to the system monospace). No blur (RemoteViews cannot), so no glow.

**States:**
- No rates cached yet → "Open Ticker to load rates".
- Fewer than 2 favourites → "Add favourites in Ticker".
- Rates older than 2 hours → the "updated" time turns amber (`TickerColors.Warning`).

**Data and refresh:**
- The widget only reads the cached `RateSnapshot` and settings (Room/DataStore); it never fetches during render.
- A WorkManager periodic job (`RatesRefreshWorker`, every 30 min, requires network) refreshes rates through `RatesRepository`, then updates all widgets. Enqueued as unique work (KEEP) when the first widget is added (`onEnabled`), cancelled when the last is removed (`onDisabled`).
- Whenever the app itself refreshes rates successfully, it also updates the widgets.
- Tap anywhere → opens `MainActivity` on Convert.

## 4. Convert from selection

**Hook:** a new activity `SelectionConvertActivity` with an intent filter for `android.intent.action.PROCESS_TEXT`, `text/plain`, label **"Convert"**. Android lists it in the text-selection toolbar of most apps (sometimes under ⋮). It reads `Intent.EXTRA_PROCESS_TEXT`; it never returns text (no replace-selection).

**Window:** translucent, dialog-like; a Neon card centred over the calling app, dismissed by tapping outside or Back.

**Parsing** — new pure `SelectionParser.parse(text): Selection(amount: BigDecimal?, currencyCode: String?)`:
- Amount: the first number `PriceParser` accepts in the text.
- Currency, first match wins: an ISO code next to or inside the text (`USD 5`, `5 usd`, case-insensitive, must be in the catalog) → a symbol from a fixed table: `€`→EUR, `£`→GBP, `¥`→JPY, `₹`→INR, `₩`→KRW, `₺`→TRY, `₴`→UAH, `₽`→RUB, `₿`→BTC, `Ft`→HUF, `zł`→PLN, `Kč`→CZK, `lei`→RON, `CHF`→CHF, `$`→USD (also `US$`; `C$`/`CA$`→CAD, `A$`/`AU$`→AUD).
- Ambiguous or unknown (`kr`, no sign) → `null`; the card then uses the Camera "from" currency (`Settings.cameraFrom`, else the first favourite).

**Card content:**
- A pill showing the amount and its currency; tapping the currency opens the existing `CurrencyPicker` to change it.
- Up to 6 favourites (the "from" currency excluded) as tiles with converted amounts; fee applies.
- The sync line (`SyncBadge`).
- An **Open in Ticker** pill: hands the amount and currency to Convert through the existing `CameraHandoff`, starts `MainActivity`, and finishes this activity.

**Rates:** shows the cached snapshot immediately, triggers a refresh in the background (consistent with "fresh data on every launch"), and updates in place when it arrives.

**No amount found:** the card says "No amount in the selected text" and offers only **Open in Ticker**.

## 5. Receipt mode

**Entry points** (Camera screen):
- **Gallery button** — now opens the chosen photo as a receipt. This replaces today's "PRICES IN PHOTO" chips sheet.
- **New Receipt button** — added to the bottom controls row. It takes one full-resolution photo of the current camera view and opens it as a receipt. The camera binds an `ImageCapture` use case alongside the existing Preview and ImageAnalysis.

**Reading:**
- `OcrEngine` gets a second mode, `recognizeLines(bitmap): List<OcrLine(text, box)>`, which iterates at text-line level.
- Tesseract settings for this mode: automatic page segmentation (PSM chosen in the plan by testing 4 vs 6 on sample receipts), no character whitelist.
- Preprocessing:
  - Scale so the long edge is at most 2000 px.
  - Grayscale.
  - **Local (adaptive) threshold** instead of global Otsu, so shadows and uneven light on paper don't wipe out lines.
  - The 12 px white border, as for the aim box.
- Runs on a background dispatcher. The screen shows "Reading receipt…" and a spinner; expect 1–3 s.

**Parsing** — new pure `ReceiptParser.parse(lines: List<String>): Receipt(items: List<ReceiptItem(name, amount)>, printedTotal: BigDecimal?)`:

*Which number is a line's price:*
- A line's price is its **rightmost** number that `PriceParser` accepts.
- Not a price: anything that is part of a date or time (`2026.11.07`, `07/11`, `14:05`); 9 or more digits (phone, tax and receipt numbers); a number followed by `%`.
- In quantity lines (`2 x 1.50   3.00`), the rightmost number is still the price.

*What the text before it is:* the item name, trimmed of trailing dots, spaces and currency marks.

*Lines that aren't items* (keywords matched case- and accent-insensitively):
- **Total:** TOTAL, SUM, SUMA, SUMME, GESAMT, ÖSSZESEN, FIZETENDŐ, TOTALE, TOTAAL, CELKEM, RAZEM, AMOUNT DUE. The printed total is the last such line's price.
- **Skipped entirely:** tax lines (VAT, ÁFA, MWST, TVA, IVA, PTU, DPH), payment and change lines (CASH, CARD, KÉSZPÉNZ, KÁRTYA, BANKKÁRTYA, VISSZAJÁRÓ, CHANGE, WECHSELGELD), and subtotal lines (SUBTOTAL, RÉSZÖSSZEG).
- Lines with no price are dropped.

**Receipt screen** (full screen, Neon):
- Header: `RECEIPT`, plus the from → to chips (same as Camera; changing them re-converts), and the fee label when the fee is on.
- One tile per item: name (ellipsised), original amount, converted amount.
  - Tapping a tile excludes or includes it; excluded items are struck through and dim.
- Total tile (glowing):
  - The **sum of included items** in both currencies.
  - When a printed total was found, also "Receipt says X". If it differs from the sum of all items by more than 0.01, add an amber note: "Some lines may be misread — tap to leave them out".
- Tapping the total opens Convert with the total, via `CameraHandoff`.

**Failure:** no items found → "Couldn't find any prices. Try a flatter photo with more light." and a Retake/Choose another button.

## 6. Non-goals (this round)

- Editing misread receipt values.
- Saving receipts or history.
- Quick Settings tile.
- Replacing the selected text.
- A widget for a single pair.
- Per-currency or named-card fees.

## 7. Testing

**Unit tests (JVM):**
- Fee: apply/no-apply, rounding to target decimals, 0% and 10% bounds.
- `SelectionParser`: each symbol in the table, ISO codes before and after the number, lower case, unknown/ambiguous → null, no number → null amount, thousands separators ("1 290 Ft", "€1,234.50").
- `ReceiptParser`, from text fixtures of 3–4 realistic receipts (Hungarian, German, English):
  - Items and the printed total.
  - Dates, times and long numbers ignored.
  - Tax and payment lines skipped.
  - Quantity lines.
  - A missing total.
- Adaptive threshold: a gradient-lit synthetic image keeps the dark text on both sides.
- Widget row model: building rows from settings and snapshot (fee on/off, fewer than 2 favourites, stale flag).

**On the phone (instrumented):** `OcrRegressionTest` gains a receipt fixture image and checks that at least the total and most item prices are read.

**Manual, on the user's phone:**
- Select a price in Chrome and in Messages; the Convert entry appears and the card converts it.
- Add the widget in all three sizes, wait for a background refresh, then tap it.
- Toggle the fee and check the amounts on every screen.
- Two real receipts: one snapped with the Receipt button, one from the gallery.

## 8. Build order

Each step is committed and tested on its own before the next:

1. Fee (core + Convert chip, then applied to Camera).
2. Convert from selection.
3. Widget + background refresh.
4. Receipt mode.
