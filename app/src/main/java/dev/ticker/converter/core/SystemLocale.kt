package dev.ticker.converter.core

import android.content.Context
import android.os.Build
import java.util.Locale

/**
 * The phone's own locale. With a per-app language, [Locale.getDefault] and even the app's own system
 * Resources report the app's language, but number format and the default currency follow the phone's
 * region, not the language Ticker's text is in. Before Android 13 there is no per-app language, so the
 * default locale is the phone's.
 */
object SystemLocale {
    @Volatile private var source: () -> Locale = Locale::getDefault

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= 33) {
            val lm = context.getSystemService(android.app.LocaleManager::class.java)
            source = { lm.systemLocales.takeIf { !it.isEmpty }?.get(0) ?: Locale.getDefault() }
        }
    }

    fun get(): Locale = source()
}

fun systemLocale(): Locale = SystemLocale.get()
