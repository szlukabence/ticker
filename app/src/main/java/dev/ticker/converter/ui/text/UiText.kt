package dev.ticker.converter.ui.text

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Text a model builds without a Context; the UI turns it into a string in the current language. */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText
}

@Composable
fun UiText.resolve(): String = resolve(LocalContext.current)

/** Nested [UiText] arguments are resolved first. */
fun UiText.resolve(context: Context): String {
    fun List<Any>.resolved() = map { if (it is UiText) it.resolve(context) else it }.toTypedArray()
    return when (this) {
        is UiText.Res -> context.getString(id, *args.resolved())
        is UiText.Plural -> context.resources.getQuantityString(id, count, *(listOf<Any>(count) + args).resolved())
        is UiText.Raw -> text
    }
}
