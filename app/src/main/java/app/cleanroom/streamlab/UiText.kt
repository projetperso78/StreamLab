package app.cleanroom.streamlab

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Keeps resource-backed UI messages responsive to app-locale changes. */
internal sealed interface UiText {
    data object Empty : UiText
    data class Literal(val value: String) : UiText
    data class Resource(@param:StringRes val id: Int, val arguments: List<Any> = emptyList()) : UiText
}

@Composable
internal fun resolveUiText(value: UiText): String = when (value) {
    UiText.Empty -> ""
    is UiText.Literal -> value.value
    is UiText.Resource -> stringResource(value.id, *value.arguments.toTypedArray())
}

internal fun localizedUiText(@StringRes id: Int, vararg arguments: Any): UiText =
    UiText.Resource(id, arguments.toList())
