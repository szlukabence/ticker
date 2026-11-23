# Ticker maintenance releases 0.2.1–0.2.3: design

Date: 2026-11-23. Status: approved in chat 2026-11-23.

**Idea check:** worth building. These are three small features that make the app quicker to use and easier to translate. They also add steady, visible maintenance before the F-Droid resubmission (about 2027-02-08). No new dependencies.

## Goal

Ship three small releases, in this order:

| Release | Feature |
|---|---|
| 0.2.1 | The home-screen widget follows Ticker's theme setting. |
| 0.2.2 | A Quick Settings tile, plus long-press app shortcuts for Scan price and Charts. |
| 0.2.3 | A Hungarian translation. All user-facing text moves into string resources. |

Translation comes last, so it also covers the tile and shortcut labels.

## Global constraints

- minSdk 26, targetSdk 36, compileSdk 37, AGP 9.5.1, Glance 1.2.0.
- No new library dependencies. No new permissions.
- Unit tests are plain JVM tests (JUnit and kotlin.test, no Robolectric). Logic that needs testing lives in plain functions or objects.
- Versions follow `versionCode × 10 + abi`. Each release adds a changelog file:

  | versionName | versionCode | ABI codes | Changelog file |
  |---|---|---|---|
  | 0.2.1 | 3 | 30–33 | `33.txt` |
  | 0.2.2 | 4 | 40–43 | `43.txt` |
  | 0.2.3 | 5 | 50–53 | `53.txt` |

  Changelogs go in `fastlane/metadata/android/en-US/changelogs/`. 0.2.3 also adds `hu-HU/changelogs/53.txt`.
- `docs/fdroid/io.github.szlukabence.ticker.yml` gets the new version in each release.
- No tag, push or commit without the user's explicit OK. Install on the phone only with `adb install -r`.
- The app name stays "Ticker" in every language.
- The Camera tab stays dark, as before.

## 1. Widget follows the theme (0.2.1)

Today the widget always uses `DarkPalette`.

- Add a pure mapping `WidgetTheme.colors(mode: ThemeMode)` that returns the widget colour roles: bg, accent, text, dim, negative, warning.
  - **SYSTEM:** each role is a Glance day/night `ColorProvider(day = Light…, night = Dark…)` (`androidx.glance.color.ColorProvider`). The widget then follows the phone's dark mode live, with no redraw needed.
  - **LIGHT:** fixed `LightPalette` colours. The widget background is `LightPalette.surface` (white card).
  - **DARK:** fixed `DarkPalette` colours. The background stays black, as today.
- So the mapping can be tested on the JVM, it returns a plain description: `WidgetColors(dayNight: Boolean, light: WidgetRoles, dark: WidgetRoles)` or similar. The widget turns that into ColorProviders.
- `WidgetModel.redrawKey` includes `settings.theme`. Choosing Light or Dark in the app then repaints the widget within the existing 1 s debounce.
- `widget_loading` (the initial layout) follows the phone's night mode through `values` / `values-night` colours. It is shown only briefly, so it doesn't need the in-app setting.

**Tests:**
- The mapping for each of the three modes.
- `redrawKey` differs when only the theme differs.

## 2. Quick Settings tile and shortcuts (0.2.2)

- **Tile:** `TickerTileService : TileService`, labelled "Ticker".
  - It uses a new monochrome vector `res/drawable/ic_tile.xml`, the two-arrow Swap glyph from `TickerIcons.Convert`.
  - Tapping it opens `MainActivity` and closes the panel:
    - API 34+: `startActivityAndCollapse(PendingIntent)`
    - older: the `Intent` overload, with `@Suppress("DEPRECATION")`
  - The tile state is always `STATE_INACTIVE`. It's a launcher, not a toggle.
  - Manifest: `android.permission.BIND_QUICK_SETTINGS_TILE` on the service, the `android.service.quicksettings.action.QS_TILE` intent filter, and `exported=true`.
- **Shortcuts:** static, in `res/xml/shortcuts.xml`, linked from the launcher activity's `<meta-data android:name="android.app.shortcuts">`.
  - "Scan price" (id `scan`) opens the Camera tab.
  - "Charts" (id `charts`) opens Charts.
  - Each sends `ACTION_VIEW` to `MainActivity` with the string extra `dev.ticker.converter.TAB` set to `camera` or `charts`.
  - Icons are monochrome vectors from the Camera and Charts glyphs, on a background that works with adaptive icons.
- **Routing:** a pure `Tab.fromLaunchExtra(value: String?): Tab` maps by route and falls back to CONVERT for null or unknown values.
  - `MainActivity` reads the extra in `onCreate` and in `onNewIntent`.
  - `TickerNavHost` takes a `launchTab` parameter. It navigates there when that value changes, using the existing `select` function so back behaviour stays the same.
- **Tests:**
  - `fromLaunchExtra` for `camera`, `charts`, `convert`, null, `"bogus"`, and upper-case `"CAMERA"` (not a route, so CONVERT).
  - A JVM test that parses `shortcuts.xml` and checks that every `TAB` extra maps to a real tab.

## 3. Hungarian (0.2.3)

### Moving the text into resources

- Every user-facing text in Compose code moves to `res/values/strings.xml`. That includes `Text`, `contentDescription`, labels, placeholders, the snackbar and toast texts, and the widget and tile texts. Compose reads them with `stringResource`. Glance and services use `context.getString`.
- Manifest labels move too: the selection activity "Convert", the widget receiver "Ticker rates", and the tile and shortcut labels.
- **Out of scope:**
  - Internal names, like `TickerIcons` icon names and log tags.
  - Exception messages that never reach the screen.
  - OCR and receipt keyword lists.
- **Key names:** `<screen>_<what>`, for example `convert_refresh`, `about_privacy_title`, `tab_camera`.
- **Placeholders:** positional (`%1$s`). Counts use `<plurals>`.
- `Tab.label` becomes `@StringRes val label: Int`.
- **Screen models give data, not sentences.** A view model or model that builds text for the user hands over data or an enum instead, and the composable picks the string. Cases include the widget's "incl. X fee" and any status or error text built in view models. Numbers and percentages are formatted as today.
- **About and legal text:**
  - `AboutContent` keeps its structure, but the `LEGAL`, `DISCLAIMER` and `PRIVACY` prose, plus the data-source descriptions, become string resource IDs.
  - These never change: the copyright line, component names, licence names, licence files, URLs, and the attribution text "source: ECB".
  - The Hungarian GPL notice ends with a sentence saying the English licence text is the one that counts.
  - The existing tests that check the legal notices are complete keep passing. They check IDs and the untranslated fields.

### Choosing the language

- Ticker follows the phone's language. There is no in-app language menu.
- `androidResources { generateLocaleConfig = true; localeFilters += listOf("en", "hu") }`, plus `app/src/main/res/resources.properties` with `unqualifiedResLocale=en-US`. This adds Ticker to the Android 13+ per-app language list and drops translations that come from libraries.

### Hungarian text

- New file `res/values-hu/strings.xml` covering every key. These terms are fixed and used everywhere:

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

- The user, a native speaker, reviews the whole file before the release.

### Currency names

- Fiat names use the system's translation, but only when the current language is one Ticker is translated into (today, `hu`). Otherwise they stay the bundled English names, exactly as they are now.
- `CurrencyCatalog` takes `localizedLanguages: Set<String>` and `locale: () -> Locale`, defaulting to `Locale::getDefault`.
  - It builds a localised copy of the fiat map once per locale and caches it.
  - Each name comes from `java.util.Currency.getInstance(code).getDisplayName(locale)`. If that throws, or returns the code itself, it falls back to the English name.
  - `get` and `all` return the localised copies.
  - `search` matches the code, the localised name, or the English name.
- The locale is read on each call, so an Android 13 per-app language change works without a process restart.
- Crypto names don't change.

### Dates

- Chart axis and tooltip dates use the locale's own date order through `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)`. Time is added for the 1D and 1W ranges.
- The "updated" time is `HH:mm` and stays as it is.

### Store listing

- New files in `fastlane/metadata/android/hu-HU/`: `title.txt` ("Ticker"), `short_description.txt`, `full_description.txt`, `changelogs/53.txt`.
- Screenshots stay English only. F-Droid falls back to en-US.

### Guard tests (JVM)

- **Parity:** parse `src/main/res/values/strings.xml` and `values-hu/strings.xml`.
  - Both have the same keys, for both strings and plurals.
  - Each pair of strings uses the same set of `%n$` placeholders.
  - No Hungarian value is empty.
  - Plurals have the `one` and `other` quantities.
- **No text in the code:** scan `src/main/java/**/*.kt` for `Text("` followed by a letter, and for `contentDescription = "`, `label = "` and `title = "` followed by a letter.
  - The allowlist is the brand word "TICKER" plus symbol-only strings.
  - The test fails and names each file and line it finds.
- **Currency names:**
  - Hungarian locale: USD's name is Hungarian (not equal to the English name), and searching "dollár" and "dollar" both find USD.
  - English locale: names equal the bundled English names.
  - German locale (not translated): English names.

## Review focus

The things most likely to go wrong that the unit tests don't cover:

1. Hungarian text overflowing narrow places: the dock tab labels, the keypad header buttons, and the widget's 110 dp column. Check on the phone (1080×2340) and shorten words where needed.
2. Tapping a shortcut while Ticker is already open (`onNewIntent`) must switch tabs without stacking a second activity.
3. The widget with SYSTEM theme must switch when the phone's dark mode changes while the app is closed.
4. The tile on Android 14+ must close the panel and open the app. On API 26–33 the deprecated path must also work.
5. Number formatting must not change. The decimal separator still comes from the phone's region, not from the app's language.

## Out of scope

- An in-app language menu.
- Dynamic favourite-pair shortcuts.
- Languages other than Hungarian.
- Translated screenshots.
