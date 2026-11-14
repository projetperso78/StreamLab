package app.cleanroom.streamlab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val background = Color(0xFF0B1118)
private val surface = Color(0xFF15232C)
private val mint = Color(0xFF73E0BD)
private val gold = Color(0xFFF5C985)
private val white = Color(0xFFF5F8F7)
private val muted = Color(0xFFB5C7C8)

@Composable
internal fun CinemaPassengerSettings(
    language: String,
    onSelectLanguage: (String) -> Unit,
    onBack: () -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = mint, onPrimary = background, background = background,
        surface = surface, onSurface = white,
    )) {
        Box(Modifier.fillMaxSize().background(background).safeDrawingPadding()
            .testTag("passenger-settings"), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 760.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                var backFocused by remember { mutableStateOf(false) }
                Surface(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)
                    .onFocusChanged { backFocused = it.isFocused }
                    .focusable()
                    .testTag("passenger-settings-back"), color = surface,
                    border = BorderStroke(if (backFocused) 3.dp else 1.dp, mint.copy(alpha = .7f)),
                    shape = RoundedCornerShape(15.dp)) {
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.nav_home), color = mint,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.title_settings), color = white,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(R.string.pass_settings_intro), color = muted,
                        style = MaterialTheme.typography.bodyLarge)
                }
                PassengerSafetyNotice(Modifier.fillMaxWidth())
                // Car screen: the picture stays lit while TV plays (parked), instead of dimming.
                val context = androidx.compose.ui.platform.LocalContext.current
                var keepOn by remember { mutableStateOf(CarScreen.keepOn(context)) }
                Surface(color = surface, shape = RoundedCornerShape(23.dp), border = BorderStroke(1.dp, gold.copy(alpha = .5f))) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.car_keep_screen_on), color = white, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.car_keep_screen_on_detail), color = muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        androidx.compose.material3.Switch(checked = keepOn, onCheckedChange = { keepOn = it; CarScreen.setKeepOn(context, it) },
                            modifier = Modifier.testTag("passenger-keep-screen-on"))
                    }
                }
                Surface(color = surface, shape = RoundedCornerShape(23.dp),
                    border = BorderStroke(1.dp, gold.copy(alpha = .5f))) {
                    Column(Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Text(stringResource(R.string.settings_language), color = gold,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.settings_language_detail), color = muted,
                            style = MaterialTheme.typography.bodyMedium)
                        listOf(StreamLabLocale.SYSTEM to stringResource(R.string.language_system), "fr" to "Français", "en" to "English", "ar" to "العربية")
                            .forEach { (code, label) ->
                                val selected = language == code
                                var focused by remember(code) { mutableStateOf(false) }
                                Surface(
                                    selected = selected,
                                    onClick = { onSelectLanguage(code) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                        .onFocusChanged { focused = it.isFocused }
                                        .focusable()
                                        .testTag("passenger-language-" + code),
                                    color = if (selected) mint.copy(alpha = .16f) else background,
                                    border = BorderStroke(
                                        if (focused) 3.dp else if (selected) 2.dp else 1.dp,
                                        if (focused || selected) mint else muted.copy(alpha = .45f),
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, color = white,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                        RadioButton(selected = selected,
                                            onClick = null,
                                            colors = RadioButtonDefaults.colors(selectedColor = mint))
                                    }
                                }
                            }
                    }
                }
            }
        }
    }
}
