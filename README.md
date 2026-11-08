# Ticker

A free, ad-free, tracker-free currency converter for Android: fiat and crypto, a calculator-style favourites screen, pair charts, and camera price conversion that runs fully on the phone.

- Fresh rates on every launch (ECB reference rates via Frankfurter; other currencies and crypto from the public-domain fawazahmed0 currency API), with offline fallback to the last saved rates.
- No Google Play Services: OCR is Tesseract (tesseract4android), camera is CameraX.

Design spec: `docs/superpowers/specs/2026-11-07-ticker-currency-converter-design.md`

## Build

Requirements: JDK 21 (pinned in `.mise.toml`), Android SDK with platform 37 at the path in `local.properties` (`sdk.dir=...`).

```bash
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleDebug          # debug APK in app/build/outputs/apk/debug/
./gradlew :app:installDebug           # install on a phone connected with USB debugging
./gradlew :app:connectedDebugAndroidTest   # on-device tests (database + OCR regression)
```

## Release signing

Create the key once, in your own terminal (it asks for passwords):

```bash
mkdir -p ~/.keystores && keytool -genkeypair -v -keystore ~/.keystores/ticker-release.jks -alias ticker -keyalg RSA -keysize 4096 -validity 36500
```

Then add to `~/.gradle/gradle.properties` (never commit this):

```properties
TICKER_STORE_FILE=/home/<you>/.keystores/ticker-release.jks
TICKER_STORE_PASSWORD=...
TICKER_KEY_ALIAS=ticker
TICKER_KEY_PASSWORD=...
```

`./gradlew :app:assembleRelease` then produces a signed APK. Without these properties the release APK is unsigned.

**Back up `ticker-release.jks` and both passwords somewhere off this laptop.** Every future update must be signed with the same key; if it is lost, people who installed your signed APK cannot update. (F-Droid builds are signed with F-Droid's own key.)

## Licence

Ticker is free software under the GNU General Public License, version 3 or (at your option) any later version. See [LICENSE](LICENSE). Copyright © 2026 Bence Szluka.

Ticker comes with no warranty. Exchange rates are for information only and are not financial advice.

Bundled third-party code, fonts and data keep their own licences; the texts ship in `app/src/main/assets/licenses/` and are shown in the app under About:

- Tesseract OCR, Tesseract4Android and the `tessdata_fast` English model: Apache-2.0. Tesseract4Android also bundles Leptonica (BSD-2-Clause style), libpng (libpng licence) and the Independent JPEG Group's libjpeg. *This software is based in part on the work of the Independent JPEG Group.*
- Android Jetpack, Kotlin, OkHttp, Retrofit, Reorderable and the other libraries: Apache-2.0; Checker Framework qualifiers: MIT.
- Fonts: JetBrains Mono and Space Grotesk, SIL Open Font License 1.1.

## Data sources

- **European Central Bank** euro reference rates, fetched through [Frankfurter](https://frankfurter.dev). Source: ECB. Cross rates, daily changes and charts are Ticker's own calculations from these rates.
- **[fawazahmed0 currency API](https://github.com/fawazahmed0/exchange-api)** (CC0, public domain) for other currencies and all crypto prices, updated daily.

## Publishing on F-Droid

1. Push this repository to `https://github.com/szlukabence/ticker` (public) and tag the release: `git tag v0.1.0 && git push origin main v0.1.0`.
2. Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata) on GitLab, copy `docs/fdroid/io.github.szlukabence.ticker.yml` to `metadata/` in the fork, and check it with `fdroid lint io.github.szlukabence.ticker` and `fdroid build -v -l io.github.szlukabence.ticker`.
3. Open a merge request. Store texts, icon and screenshots come from `fastlane/metadata/android/en-US/`.
4. For each later release: raise `versionCode` (by 1) and `versionName` in `app/build.gradle.kts`, add `fastlane/metadata/android/en-US/changelogs/<versionCode×10+3>.txt` (for versionCode 2 that is `23.txt`), commit, and push a `v<versionName>` tag. F-Droid picks new tags up by itself and builds one APK per processor type (`-Pabi=...`, version codes `versionCode×10+0…3`).
