package dev.ticker.converter.ui.about

import androidx.annotation.StringRes
import dev.ticker.converter.R

/** One bundled piece of third-party (or our own) software and the licence text shipped with it. */
data class Component(val name: String, val licence: String, val licenceAsset: String, val note: String = "")

data class DataSource(val name: String, @StringRes val detail: Int, val url: String)

/** Everything the About screen says. Kept as data so tests can check the legal notices are complete. */
object AboutContent {
    const val SOURCE_URL = "https://github.com/szlukabence/ticker"
    const val PRIVACY_URL = "$SOURCE_URL/blob/main/PRIVACY.md"

    const val COPYRIGHT = "Copyright © 2026 Bence Szluka"

    val dataSources = listOf(
        DataSource(
            "European Central Bank",
            R.string.about_source_ecb_detail,
            "https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/",
        ),
        DataSource(
            "fawazahmed0 currency API",
            R.string.about_source_fawaz_detail,
            "https://github.com/fawazahmed0/exchange-api",
        ),
    )

    private const val APACHE = "licenses/Apache-2.0.txt"

    val components = listOf(
        Component("Ticker", "GPL-3.0-or-later", "licenses/GPL-3.0.txt", COPYRIGHT),
        Component("Tesseract OCR and its English model", "Apache-2.0", APACHE, "Copyright the Tesseract OCR authors (Hewlett-Packard, Google and contributors)"),
        Component("Tesseract4Android", "Apache-2.0", APACHE, "Copyright Adaptech s.r.o., Robert Pösel"),
        Component("Leptonica", "BSD-2-Clause style", "licenses/Leptonica.txt", "Copyright Leptonica"),
        Component(
            "libjpeg (Independent JPEG Group)", "IJG", "licenses/IJG-libjpeg.txt",
            "This software is based in part on the work of the Independent JPEG Group.",
        ),
        Component("libpng", "libpng License", "licenses/libpng.txt", "Copyright the PNG Reference Library authors"),
        Component("Android Jetpack (AndroidX, Compose, CameraX, Room, WorkManager, Glance, Media3)", "Apache-2.0", APACHE, "Copyright The Android Open Source Project"),
        Component("Protocol Buffers (inside DataStore and Glance)", "BSD-3-Clause", "licenses/BSD-3-Protobuf.txt", "Copyright Google Inc."),
        Component("Kotlin, kotlinx.coroutines, kotlinx.serialization, Compose Multiplatform", "Apache-2.0", APACHE, "Copyright JetBrains s.r.o. and contributors"),
        Component("OkHttp, Okio, Retrofit", "Apache-2.0", APACHE, "Copyright Square, Inc."),
        Component("Guava, AutoValue, Dagger, Error Prone, J2ObjC annotations, JSR-305, JSpecify", "Apache-2.0", APACHE, "Copyright Google LLC and contributors"),
        Component("javax.inject, jakarta.inject", "Apache-2.0", APACHE, "Copyright the JSR-330 Expert Group and Eclipse Foundation"),
        Component("Checker Framework qualifiers", "MIT", "licenses/MIT-CheckerFramework.txt", "Copyright the Checker Framework developers"),
        Component("Reorderable", "Apache-2.0", APACHE, "By Calvin Liang"),
        Component("JetBrains Mono font", "SIL Open Font License 1.1", "licenses/OFL-JetBrainsMono.txt", "Copyright The JetBrains Mono Project Authors"),
        Component("Space Grotesk font", "SIL Open Font License 1.1", "licenses/OFL-SpaceGrotesk.txt", "Copyright The Space Grotesk Project Authors"),
    )
}
