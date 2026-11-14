package app.cleanroom.streamlab

import app.cleanroom.streamlab.core.ContinueWatching
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cleanroom.streamlab.services.ExternalService
import app.cleanroom.streamlab.services.ServiceRegistry

private val cinemaGold = Color(0xFFECC88C)
private val cinemaInk = Color(0xFF0D151A)


@Composable
fun AppsServicesPanel(
    onOpen: (ExternalService) -> Unit,
    onOpenSection: (String) -> Unit,
    continueItems: List<ContinueItem> = emptyList(),
    onResume: (app.cleanroom.streamlab.data.CatalogChannelRow) -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 158.dp),
        modifier = Modifier.fillMaxSize().background(cinemaInk).testTag("apps-grid"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 0. Films started and not finished.
        if (continueItems.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.continue_title), color = cinemaGold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.testTag("continue-row"),
                ) {
                    items(continueItems.size) { index ->
                        val entry = continueItems[index]
                        Surface(
                            onClick = { onResume(entry.channel) },
                            modifier = Modifier.width(220.dp).testTag("continue-${entry.channel.id}"),
                            color = Color(0xFF1B2A30),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF425258)),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("▶ " + entry.channel.name, color = Color.White, style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold, maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { ContinueWatching.fraction(entry.positionMs, entry.durationMs) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = cinemaGold,
                                )
                                Text(stringResource(R.string.continue_left, ContinueWatching.minutesLeft(entry.positionMs, entry.durationMs).toInt()),
                                    color = Color(0xFFB9CBC9), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // 1. What people open StreamLab for, one tap away.
        item(span = { GridItemSpan(maxLineSpan) }) {
            AppCard("🚗", stringResource(R.string.drive_title), stringResource(R.string.drive_card_desc),
                stringResource(R.string.pass_accessibility_title, stringResource(R.string.drive_title)),
                onClick = { onOpenSection("Drive") }, tag = "home-drive", highlight = true)
        }
        item { AppCard("📻", stringResource(R.string.pass_radio), stringResource(R.string.pass_radio_desc),
            stringResource(R.string.pass_accessibility_title, stringResource(R.string.pass_radio)),
            onClick = { onOpenSection("Radio") }, tag = "home-radio") }
        item { AppCard("📺", stringResource(R.string.pass_tv), stringResource(R.string.pass_tv_desc),
            stringResource(R.string.pass_accessibility_title, stringResource(R.string.pass_tv)),
            onClick = { onOpenSection("Live") }, tag = "home-tv") }
        item { AppCard("🎬", stringResource(R.string.pass_videos), stringResource(R.string.pass_videos_desc),
            stringResource(R.string.pass_accessibility_title, stringResource(R.string.pass_videos)),
            onClick = { onOpenSection("Videos") }, tag = "home-videos") }
        item { AppCard("⭐", stringResource(R.string.pass_favorites), stringResource(R.string.pass_favorites_desc),
            stringResource(R.string.pass_accessibility_title, stringResource(R.string.pass_favorites)),
            onClick = { onOpenSection("Favorites") }, tag = "home-favorites") }

        // 2. Official apps of the user's services.
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 16.dp, bottom = 2.dp)) {
                Text(stringResource(R.string.apps_collection), color = cinemaGold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.apps_intro), color = Color(0xFFB9CBC9), style = MaterialTheme.typography.bodySmall)
            }
        }
        itemsIndexed(ServiceRegistry.all, key = { _, service -> service.id }) { _, service ->
            AppCard(
                icon = null,
                name = service.name,
                subtitle = stringResource(R.string.pass_service_desc),
                openLabel = stringResource(R.string.apps_open, service.name),
                serviceId = service.id,
                onClick = { onOpen(service) },
            )
        }

        // 3. Legal notice last: important, but not what the screen is for.
        item(span = { GridItemSpan(maxLineSpan) }) {
            PassengerSafetyNotice(Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun AppCard(icon: String?, name: String, subtitle: String, openLabel: String,
    serviceId: String? = null, onClick: () -> Unit, tag: String? = null, highlight: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = if (highlight) 112.dp else 132.dp).semantics { contentDescription = openLabel }
            .let { if (tag != null) it.testTag(tag) else it },
        color = if (highlight) Color(0xFF1F3B36) else Color(0xFF1B2A30),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF425258)),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (serviceId == null) {
                Text(icon.orEmpty(), style = MaterialTheme.typography.headlineSmall)
            } else {
                ServiceBrandBadge(serviceId, cinemaGold, name)
            }
            Spacer(Modifier.weight(1f))
            Text(name, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFFB9CBC9), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** One film or video started but not finished, for the Home "Continue watching" row. */
data class ContinueItem(val channel: app.cleanroom.streamlab.data.CatalogChannelRow, val positionMs: Long, val durationMs: Long)
