package app.cleanroom.streamlab

import android.content.Context
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import app.cleanroom.streamlab.core.KidsPin
import kotlinx.coroutines.delay

/**
 * Kids mode: the app shows only the favourites a parent picked, with no settings, sources
 * or services. Leaving it needs the parent PIN (stored as a salted hash only).
 */
object KidsMode {
    private const val STORE = "streamlab_kids"
    private const val ACTIVE = "active"
    private const val SALT = "salt"
    private const val HASH = "hash"
    private const val FAILURES = "failures"
    private const val LOCKED_UNTIL = "locked_until"

    /** Observed by MainActivity so turning the mode on from Settings switches screens at once. */
    val active = mutableStateOf(false)

    private fun prefs(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun load(context: Context) { active.value = prefs(context).getBoolean(ACTIVE, false) }

    fun enable(context: Context, pin: String) {
        val salt = KidsPin.newSalt()
        val hash = KidsPin.hash(pin, salt)
        prefs(context).edit()
            .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(FAILURES, 0).putLong(LOCKED_UNTIL, 0L)
            .putBoolean(ACTIVE, true).commit()
        active.value = true
    }

    fun guard(context: Context): KidsPin.Guard =
        prefs(context).let { KidsPin.Guard(it.getInt(FAILURES, 0), it.getLong(LOCKED_UNTIL, 0L)) }

    /** Returns true and leaves kids mode when the PIN is right; counts failures otherwise. */
    fun tryUnlock(context: Context, pin: String, now: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(context)
        val guard = guard(context)
        if (guard.isLocked(now)) return false
        val salt = p.getString(SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val hash = p.getString(HASH, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val ok = salt != null && hash != null && KidsPin.matches(pin, salt, hash)
        if (ok) {
            p.edit().putBoolean(ACTIVE, false).putInt(FAILURES, 0).putLong(LOCKED_UNTIL, 0L).commit()
            active.value = false
        } else {
            val next = guard.afterFailure(now)
            p.edit().putInt(FAILURES, next.failures).putLong(LOCKED_UNTIL, next.lockedUntil).commit()
        }
        return ok
    }
}

@Composable
fun KidsModeScreen(app: StreamLabApplication, controller: MediaController?) {
    val channels by app.repository.channels.collectAsState(initial = emptyList())
    val favourites = channels.filter { it.favorite }
    var askPin by remember { mutableStateOf(false) }
    // Back never leaves kids mode; the parent button does.
    BackHandler { askPin = true }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFC857))) {
        Surface(Modifier.fillMaxSize().testTag("kids-mode"), color = Color(0xFF14102B), contentColor = Color.White) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.kids_title), style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { askPin = true }, modifier = Modifier.testTag("kids-exit")) {
                        Text(stringResource(R.string.kids_parent_button))
                    }
                }
                if (favourites.isEmpty()) {
                    Text(stringResource(R.string.kids_empty), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(24.dp).testTag("kids-empty"))
                } else {
                    TvPreviewPanel(favourites, controller, onSources = { askPin = true })
                }
            }
        }
        if (askPin) KidsUnlockDialog(onDismiss = { askPin = false })
    }
}

@Composable
private fun KidsUnlockDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val guard = remember(now, wrong) { KidsMode.guard(context) }
    LaunchedEffect(guard.lockedUntil) {
        while (guard.isLocked(System.currentTimeMillis())) { delay(1_000); now = System.currentTimeMillis() }
    }
    val locked = guard.isLocked(now)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kids_unlock_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(pin, { pin = it; wrong = false }, "kids-pin", enabled = !locked)
                when {
                    locked -> Text(stringResource(R.string.kids_locked, guard.secondsLeft(now).toInt()), color = MaterialTheme.colorScheme.error)
                    wrong -> Text(stringResource(R.string.kids_wrong_pin), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !locked && KidsPin.isValidFormat(pin),
                modifier = Modifier.testTag("kids-unlock"),
                onClick = {
                    if (KidsMode.tryUnlock(context, pin)) onDismiss()
                    else { wrong = true; pin = ""; now = System.currentTimeMillis() }
                },
            ) { Text(stringResource(R.string.kids_unlock)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.kids_cancel)) } },
    )
}

/** Parent sets a PIN twice, then kids mode starts. */
@Composable
fun KidsSetupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = KidsPin.isValidFormat(confirm) && confirm != pin
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kids_setup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.kids_setup_body), style = MaterialTheme.typography.bodySmall)
                PinField(pin, { pin = it }, "kids-new-pin", label = stringResource(R.string.kids_pin_label))
                PinField(confirm, { confirm = it }, "kids-confirm-pin", label = stringResource(R.string.kids_pin_confirm))
                if (mismatch) Text(stringResource(R.string.kids_pin_mismatch), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = KidsPin.isValidFormat(pin) && pin == confirm,
                modifier = Modifier.testTag("kids-start"),
                onClick = { KidsMode.enable(context, pin); onDismiss() },
            ) { Text(stringResource(R.string.kids_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.kids_cancel)) } },
    )
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, tag: String, enabled: Boolean = true, label: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it in '0'..'9' }.take(KidsPin.LENGTH)) },
        enabled = enabled,
        singleLine = true,
        label = label?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag),
    )
}
