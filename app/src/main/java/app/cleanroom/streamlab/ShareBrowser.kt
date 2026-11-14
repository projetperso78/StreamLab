package app.cleanroom.streamlab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cleanroom.streamlab.services.ShareEntry
import app.cleanroom.streamlab.services.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Browse the home server's folders; picking a film adds it to "Vidéos" and plays it. */
@Composable
fun ShareBrowser(app: StreamLabApplication, profileId: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val path = remember { mutableStateListOf<String>() }
    var entries by remember { mutableStateOf<List<ShareEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var problem by remember { mutableStateOf<String?>(null) }
    val folderError = stringResource(R.string.nas_unreachable)
    val client = remember { WebDavClient(app.network.playerClient()) }

    LaunchedEffect(path.toList()) {
        loading = true
        problem = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val access = app.repository.shareAccess(profileId)
                client.list(path.lastOrNull() ?: access.url, access.username, access.password)
            }
        }
        result.onSuccess { entries = it }.onFailure { entries = emptyList(); problem = it.message?.takeIf { m -> m.length < 80 } ?: folderError }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            Row {
                if (path.isNotEmpty()) TextButton(onClick = { path.removeAt(path.lastIndex) }, modifier = Modifier.testTag("share-up")) { Text(stringResource(R.string.add_back)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.field_cancel)) }
            }
        },
        title = { Text(stringResource(R.string.nas_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (!loading && problem == null && entries.isEmpty()) Text(stringResource(R.string.nas_empty), style = MaterialTheme.typography.bodyMedium)
                LazyColumn(Modifier.heightIn(max = 420.dp).testTag("share-list")) {
                    items(entries, key = { it.url }) { entry ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (entry.folder) path.add(entry.url) else scope.launch {
                                    runCatching {
                                        val access = app.repository.shareAccess(profileId)
                                        app.repository.addShareVideo(profileId, entry.name, entry.url, client.authHeaders(access.username, access.password))
                                    }.onSuccess(onPick).onFailure { problem = folderError }
                                }
                            }.padding(vertical = 12.dp).testTag("share-entry-${entry.name}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SlIcon(if (entry.folder) R.drawable.ic_folder else R.drawable.ic_ctl_play, null, Modifier.size(22.dp),
                                tint = if (entry.folder) Cinema.gold else Cinema.mint)
                            Spacer(Modifier.width(12.dp))
                            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (!entry.folder && entry.size > 0) Text("%.1f Go".format(entry.size / 1_000_000_000.0),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    )
}
