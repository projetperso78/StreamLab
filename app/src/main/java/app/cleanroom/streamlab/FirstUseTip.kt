package app.cleanroom.streamlab

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** "How it works" cards shown once per screen, then never again (can be shown again from Settings). */
object FirstUseTips {
    private const val STORE = "streamlab_tips"
    const val RADIO = "radio"
    const val TV = "tv"
    const val PLAYER = "player"
    val all = listOf(RADIO, TV, PLAYER)

    fun seen(context: Context, id: String): Boolean = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getBoolean(id, false)
    fun markSeen(context: Context, id: String) { context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putBoolean(id, true).apply() }
    fun showAgain(context: Context) { context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().clear().apply() }
}

@Composable
fun FirstUseTip(id: String, title: Int, steps: List<Int>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var visible by remember(id) { mutableStateOf(!FirstUseTips.seen(context, id)) }
    if (!visible) return
    Surface(
        modifier = modifier.fillMaxWidth().testTag("tip-$id"),
        shape = RoundedCornerShape(18.dp),
        color = Cinema.raised,
        border = BorderStroke(1.dp, Cinema.gold.copy(alpha = .7f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SlIcon(R.drawable.ic_help, null, Modifier.size(22.dp), tint = Cinema.gold)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(title), color = Cinema.gold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            steps.forEachIndexed { index, step ->
                Row {
                    Text("${index + 1}.", color = Cinema.mint, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(24.dp))
                    Text(stringResource(step), color = Cinema.white, style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(
                onClick = { FirstUseTips.markSeen(context, id); visible = false },
                modifier = Modifier.align(Alignment.End).testTag("tip-$id-ok"),
            ) { Text(stringResource(R.string.tip_ok), color = Cinema.mint, fontWeight = FontWeight.SemiBold) }
        }
    }
}
