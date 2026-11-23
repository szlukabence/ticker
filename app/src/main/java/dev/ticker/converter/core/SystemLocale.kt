package dev.ticker.converter.core

import android.content.res.Resources
import java.util.Locale

/**
 * The phone's own locale. With a per-app language, [Locale.getDefault] is the app's language, but number
 * format and the default currency follow the phone's region, not the language Ticker's text is in.
 */
/** Falls back to [Locale.getDefault] where there is no Android runtime (JVM unit tests). */
fun systemLocale(): Locale =
    runCatching { Resources.getSystem().configuration.locales[0] }.getOrNull() ?: Locale.getDefault()
