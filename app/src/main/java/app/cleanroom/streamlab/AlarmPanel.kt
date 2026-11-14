package app.cleanroom.streamlab

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.cleanroom.streamlab.core.AlarmSchedule
import app.cleanroom.streamlab.core.OpaqueMediaId
import app.cleanroom.streamlab.data.CatalogRepository
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Radio alarm settings: time, repeat, station, and the exact-alarm permission when Android asks for it. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AlarmPanel(repo: CatalogRepository) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var settings by remember { mutableStateOf(RadioAlarm.load(context)) }
    var allowed by remember { mutableStateOf(RadioAlarm.canSchedule(context)) }
    var next by remember { mutableStateOf<ZonedDateTime?>(null) }
    var pickTime by remember { mutableStateOf(false) }
    val channels by repo.channels.collectAsState(initial = emptyList())
    val favourites = channels.filter { it.favorite }

    LifecycleResumeEffect(Unit) {
        allowed = RadioAlarm.canSchedule(context)
        next = RadioAlarm.reschedule(context)
        onPauseOrDispose { }
    }
    fun apply(updated: AlarmSchedule.Settings) {
        settings = updated
        next = RadioAlarm.save(context, updated)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("alarm-panel"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.alarm_intro), style = MaterialTheme.typography.bodyMedium)
        if (!allowed) {
            Card(Modifier.fillMaxWidth().testTag("alarm-permission")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.alarm_permission_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.alarm_permission_body), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 31) {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                        }
                    }) { Text(stringResource(R.string.alarm_permission_button)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.alarm_enabled), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(settings.enabled, { apply(settings.copy(enabled = it)) }, modifier = Modifier.testTag("alarm-enabled"))
        }
        val nextRing = next
        Text(
            when {
                !settings.enabled -> stringResource(R.string.alarm_off)
                nextRing == null -> stringResource(R.string.alarm_needs_permission)
                else -> stringResource(R.string.alarm_next, nextRing.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT).withLocale(locale)))
            },
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("alarm-next"),
        )
        // A button rather than an inline field: no keyboard pops up when the screen opens.
        Button(onClick = { pickTime = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("alarm-time")) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                SlIcon(R.drawable.ic_alarm, null, Modifier.size(30.dp), tint = Cinema.gold)
                androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                Text("%02d:%02d".format(settings.hour, settings.minute), style = MaterialTheme.typography.headlineMedium)
            }
        }
        if (pickTime) {
            val state = rememberTimePickerState(settings.hour, settings.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { pickTime = false },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton(onClick = { apply(settings.copy(hour = state.hour, minute = state.minute)); pickTime = false },
                        modifier = Modifier.testTag("alarm-time-ok")) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.kids_cancel)) } },
            )
        }
        Text(stringResource(R.string.alarm_repeat), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                emptySet<java.time.DayOfWeek>() to R.string.alarm_once,
                AlarmSchedule.WEEKDAYS to R.string.alarm_weekdays,
                AlarmSchedule.EVERY_DAY to R.string.alarm_every_day,
            ).forEachIndexed { index, (days, label) ->
                FilterChip(settings.days == days, { apply(settings.copy(days = days)) }, { Text(stringResource(label)) },
                    modifier = Modifier.testTag("alarm-repeat-$index"))
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.alarm_station), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(settings.mediaId == null, { apply(settings.copy(mediaId = null)) }, { Text(stringResource(R.string.alarm_last_played)) },
                modifier = Modifier.testTag("alarm-station-last"))
            favourites.forEach { channel ->
                val id = OpaqueMediaId.channel(channel.id)
                FilterChip(settings.mediaId == id, { apply(settings.copy(mediaId = id)) }, { Text(channel.name) },
                    modifier = Modifier.testTag("alarm-station-${channel.id}"))
            }
        }
        if (favourites.isEmpty()) Text(stringResource(R.string.alarm_station_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { RadioAlarm.ring(context) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("alarm-try")) {
            Text(stringResource(R.string.alarm_try))
        }
        Text(stringResource(R.string.alarm_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
