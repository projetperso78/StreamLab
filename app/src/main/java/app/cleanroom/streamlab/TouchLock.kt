package app.cleanroom.streamlab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Phone-only "lock the touch screen" overlay for the video player.
 * While locked, every touch on the player is swallowed; a long press on the padlock unlocks.
 * Home/Recents stay available (system navigation), and screen readers get an explicit
 * "unlock" accessibility action so the lock never traps a TalkBack user.
 */
@Composable
fun TouchLockOverlay(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    var showHint by remember { mutableStateOf(true) }
    val unlockLabel = stringResource(R.string.player_unlock)
    LaunchedEffect(showHint) {
        if (showHint) { delay(2_500); showHint = false }
    }
    BackHandler { showHint = true }
    Box(
        modifier
            .fillMaxSize()
            .testTag("player-touch-lock")
            .semantics {
                contentDescription = unlockLabel
                customActions = listOf(CustomAccessibilityAction(unlockLabel) { onUnlock(); true })
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { showHint = true }, onLongPress = { showHint = true }) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier
                    .size(76.dp)
                    .testTag("player-unlock")
                    .pointerInput(Unit) { detectTapGestures(onTap = { showHint = true }, onLongPress = { onUnlock() }) },
                shape = CircleShape,
                color = Color.Black.copy(alpha = .62f),
            ) {
                Box(contentAlignment = Alignment.Center) { SlIcon(R.drawable.ic_lock, null, Modifier.size(34.dp), tint = Color.White) }
            }
            if (showHint) {
                Text(
                    stringResource(R.string.player_unlock_hint),
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = .62f), MaterialTheme.shapes.medium)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
