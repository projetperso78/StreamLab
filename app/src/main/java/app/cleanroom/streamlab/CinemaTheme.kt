package app.cleanroom.streamlab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One look for the whole app: the "cinema à bord" palette of the home screen. */
object Cinema {
    val bg = Color(0xFF0B1118)
    val elevated = Color(0xFF15232C)
    val raised = Color(0xFF1B2D37)
    val mint = Color(0xFF73E0BD)
    val gold = Color(0xFFF5C985)
    val white = Color(0xFFF5F8F7)
    val muted = Color(0xFFB5C7C8)
    val line = Color(0xFF2C434D)
    val danger = Color(0xFFFF8A80)

    val colors: ColorScheme = darkColorScheme(
        primary = mint, onPrimary = bg, primaryContainer = Color(0xFF1E4A40), onPrimaryContainer = white,
        secondary = gold, onSecondary = bg, secondaryContainer = Color(0xFF4A3B22), onSecondaryContainer = white,
        background = bg, onBackground = white, surface = elevated, onSurface = white,
        surfaceVariant = raised, onSurfaceVariant = muted, outline = line, outlineVariant = line,
        surfaceContainer = elevated, surfaceContainerHigh = raised, surfaceContainerHighest = raised,
        surfaceContainerLow = elevated, surfaceContainerLowest = bg, error = danger,
    )

    private val monogramColors = listOf(
        Color(0xFF2E6B5E), Color(0xFF6B4E2E), Color(0xFF2E4F6B), Color(0xFF5E2E6B), Color(0xFF6B2E3C), Color(0xFF3E6B2E),
    )

    /** Stable colour per name, so a station without a logo always looks the same. */
    fun monogramColor(name: String): Color = monogramColors[Math.floorMod(name.hashCode(), monogramColors.size)]

    fun initials(name: String): String =
        name.split(' ', '-', '·').filter { word -> word.firstOrNull()?.isLetterOrDigit() == true }
            .take(2).joinToString("") { it.first().uppercase() }.ifBlank { "♪" }
}

/** Square tile with the initials of a station or channel that has no logo. */
@Composable
fun MonogramTile(name: String, size: Dp, modifier: Modifier = Modifier) {
    Surface(modifier.size(size), shape = RoundedCornerShape(size / 4), color = Cinema.monogramColor(name)) {
        Box(contentAlignment = Alignment.Center) {
            Text(Cinema.initials(name), color = Cinema.white, fontWeight = FontWeight.SemiBold, fontSize = (size.value * .34f).sp, maxLines = 1)
        }
    }
}

@Composable
fun SlIcon(res: Int, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Icon(painterResource(res), contentDescription, modifier, tint = tint)
}
