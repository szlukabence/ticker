package dev.ticker.converter.ui.about

/** One bundled piece of third-party (or our own) software and the licence text shipped with it. */
data class Component(val name: String, val licence: String, val licenceAsset: String, val note: String = "")

data class DataSource(val name: String, val detail: String, val url: String)

/** Everything the About screen says. Kept as data so tests can check the legal notices are complete. */
object AboutContent {
    const val SOURCE_URL = "https://github.com/szlukabence/ticker"
    const val PRIVACY_URL = "$SOURCE_URL/blob/main/PRIVACY.md"

    const val COPYRIGHT = "Copyright © 2026 Bence Szluka"

    const val LEGAL =
        "Ticker is free software: you can redistribute it and/or modify it under the terms of the GNU " +
            "General Public License as published by the Free Software Foundation, either version 3 of the " +
            "License, or (at your option) any later version. Ticker comes with ABSOLUTELY NO WARRANTY; " +
            "see the licence below for details."

    const val DISCLAIMER =
        "Rates are reference rates, shown for information only. They are not the rates a bank, card or " +
            "exchange will give you, and they can be out of date or wrong. The card fee is an estimate. " +
            "Nothing in Ticker is financial advice; check the real rate before you pay."

    const val PRIVACY =
        "No account, ads, analytics or tracking. Camera images are read on the phone and never stored or " +
            "sent. The app only downloads public exchange rates; those servers see your IP address."

    val dataSources = listOf(
        DataSource(
            "European Central Bank",
            "Euro foreign exchange reference rates (source: ECB), fetched through Frankfurter. Cross rates, " +
                "daily changes and charts are Ticker's own calculations from these rates, not ECB figures.",
            "https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/",
        ),
        DataSource(
            "fawazahmed0 currency API",
            "Other currencies and all crypto prices, updated once a day. Public domain (CC0).",
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
