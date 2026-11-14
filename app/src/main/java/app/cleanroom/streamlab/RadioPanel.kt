package app.cleanroom.streamlab

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cleanroom.streamlab.core.StationTidy
import app.cleanroom.streamlab.core.TextMatch
import app.cleanroom.streamlab.player.ZapList
import app.cleanroom.streamlab.services.RadioCountry
import app.cleanroom.streamlab.services.RadioStation
import app.cleanroom.streamlab.services.RegionDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun RadioPanel(
    app: StreamLabApplication,
    onAdded: (Long, String) -> Unit,
    onStatus: (String) -> Unit,
    openNearOnStart: Boolean = false,
    onNearOpened: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val catalogUnavailableText = stringResource(R.string.pass_radio_catalog_unavailable)
    val offlineText = stringResource(R.string.offline_message)
    val stationsErrorText = stringResource(R.string.pass_radio_stations_error)
    val unreadableStreamText = stringResource(R.string.pass_radio_unreadable)
    val nowPlayingText = stringResource(R.string.pass_radio_verified)
    val favoriteAddedText = stringResource(R.string.radio_favorite_added)
    val nearCode = remember(context) { RegionDetector.detect(context) }
    val saved by app.repository.channels.collectAsState(initial = emptyList())
    // Stations already kept in the library, by directory id: lets the star show its state.
    val savedByUuid = remember(saved) { saved.filter { !it.tvgId.isNullOrBlank() }.associateBy { it.tvgId!! } }
    var countries by remember { mutableStateOf<List<RadioCountry>>(emptyList()) }
    var selectedCountry by remember { mutableStateOf<RadioCountry?>(null) }
    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var countryQuery by remember { mutableStateOf("") }
    var stationQuery by remember { mutableStateOf("") }
    var showTrending by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var requestGeneration by remember { mutableIntStateOf(0) }
    var reloadCountries by remember { mutableIntStateOf(0) }
    var startingUuid by remember { mutableStateOf<String?>(null) }

    fun backToCountries() { requestGeneration++; selectedCountry = null; stations = emptyList(); startingUuid = null; busy = false; problem = null }
    // System Back inside a country returns to the country list, like the on-screen button.
    androidx.activity.compose.BackHandler(enabled = selectedCountry != null) { backToCountries() }

    LaunchedEffect(reloadCountries) {
        busy = true
        problem = null
        runCatching { app.radioDirectory.countries() }
            .onSuccess { countries = it }
            .onFailure { problem = if (isOffline(context)) offlineText else catalogUnavailableText }
        busy = false
    }

    fun loadCountry(country: RadioCountry, trendingNow: Boolean = false) {
        val generation = ++requestGeneration
        selectedCountry = country
        showTrending = trendingNow
        stationQuery = ""
        stations = emptyList()
        busy = true
        problem = null
        scope.launch {
            runCatching {
                if (trendingNow) app.radioDirectory.trending(country.code).ifEmpty { app.radioDirectory.stations(country.code, limit = 300) }
                else app.radioDirectory.stations(country.code, limit = 300)
            }
                .onSuccess { list -> if (requestGeneration == generation) stations = list }
                .onFailure { if (requestGeneration == generation) problem = if (isOffline(context)) offlineText else stationsErrorText }
            if (requestGeneration == generation) busy = false
        }
    }

    LaunchedEffect(countries, openNearOnStart) {
        if (!openNearOnStart || countries.isEmpty()) return@LaunchedEffect
        countries.firstOrNull { it.code == nearCode }?.let { loadCountry(it) }
        onNearOpened()
    }

    suspend fun keep(station: RadioStation, country: RadioCountry): Long = app.repository.addRadioStation(
        countryCode = station.countryCode.ifBlank { country.code },
        name = station.name,
        url = station.streamUrl,
        favicon = station.favicon,
        stationUuid = station.uuid,
        backups = station.backups,
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FirstUseTip(FirstUseTips.RADIO, R.string.tip_radio_title,
            listOf(R.string.tip_radio_1, R.string.tip_radio_2, R.string.tip_radio_3, R.string.tip_radio_4))
        if (selectedCountry == null) {
            SearchField(countryQuery, { countryQuery = it }, stringResource(R.string.pass_radio_search_country))
            if (busy && countries.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            problem?.let { ProblemLine(it) }
            if (!busy && countries.isEmpty()) Button(onClick = { reloadCountries++ }) { Text(stringResource(R.string.pass_radio_retry)) }
            val filtered = ZapList.nearFirst(countries.filter {
                TextMatch.matches(countryName(it.code), countryQuery) || it.code.contains(countryQuery, true)
            }.sortedWith(compareBy(java.text.Collator.getInstance()) { countryName(it.code) })) { it.code == nearCode }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(filtered, key = { it.code }) { country ->
                    val near = country.code == nearCode
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { loadCountry(country) }.testTag("radio-country-${country.code}"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(if (near) 1.5.dp else 1.dp, if (near) Cinema.mint else MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(country.flag, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                if (near) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("radio-near-you")) {
                                    SlIcon(R.drawable.ic_location, null, Modifier.size(14.dp), tint = Cinema.mint)
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.pass_radio_near_you), style = MaterialTheme.typography.labelMedium, color = Cinema.mint)
                                }
                                Text(countryName(country.code), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.pass_radio_station_count, country.stationCount), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SlIcon(R.drawable.ic_chevron_right, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            val country = requireNotNull(selectedCountry)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { backToCountries() }, modifier = Modifier.testTag("radio-back-countries")) {
                    SlIcon(R.drawable.ic_arrow_back, stringResource(R.string.pass_radio_back_countries))
                }
                Text("${country.flag}  ${countryName(country.code)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            // Most loved of all time vs. gaining listeners right now.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showTrending, onClick = { if (showTrending) loadCountry(country, trendingNow = false) },
                    label = { Text(stringResource(R.string.radio_sort_popular)) },
                    leadingIcon = { SlIcon(R.drawable.ic_nav_favorite, null, Modifier.size(18.dp), tint = Cinema.gold) },
                    modifier = Modifier.testTag("radio-sort-popular"),
                )
                FilterChip(
                    selected = showTrending, onClick = { if (!showTrending) loadCountry(country, trendingNow = true) },
                    label = { Text(stringResource(R.string.radio_sort_trending)) },
                    leadingIcon = { SlIcon(R.drawable.ic_trending, null, Modifier.size(18.dp), tint = Cinema.mint) },
                    modifier = Modifier.testTag("radio-sort-trending"),
                )
            }
            SearchField(stationQuery, { stationQuery = it }, stringResource(R.string.pass_radio_search_station))
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            problem?.let { ProblemLine(it) }
            val filtered = stations.filter { TextMatch.matches(it.name, stationQuery) || TextMatch.matches(it.tags, stationQuery) }
            val local = remember(country.code) { StationTidy.localLanguages(country.code) }
            val firstForeign = filtered.indexOfFirst { !StationTidy.isLocal(it.language, local) }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                filtered.forEachIndexed { index, station ->
                    if (index == firstForeign && index > 0) item(key = "other-languages") {
                        Text(stringResource(R.string.radio_other_languages), style = MaterialTheme.typography.labelLarge,
                            color = Cinema.gold, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                    }
                    item(key = station.uuid) {
                        val savedRow = savedByUuid[station.uuid]
                        RadioStationRow(
                            app = app,
                            station = station,
                            favorite = savedRow?.favorite == true,
                            starting = startingUuid == station.uuid,
                            onListen = {
                                if (startingUuid != null) return@RadioStationRow
                                val generation = requestGeneration
                                startingUuid = station.uuid
                                scope.launch {
                                    runCatching { keep(station, country) }
                                        .onSuccess { id ->
                                            if (generation == requestGeneration) onAdded(id, String.format(Locale.getDefault(), nowPlayingText, station.name, ""))
                                        }
                                        .onFailure { if (generation == requestGeneration) problem = unreadableStreamText }
                                    if (generation == requestGeneration) startingUuid = null
                                }
                            },
                            onFavorite = {
                                scope.launch {
                                    runCatching {
                                        val id = savedRow?.id ?: keep(station, country)
                                        app.repository.toggleFavorite(id)
                                        if (savedRow?.favorite != true) onStatus(String.format(Locale.getDefault(), favoriteAddedText, station.name))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value, onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(label) },
        leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.Search, null) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun ProblemLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SlIcon(R.drawable.ic_warning, null, Modifier.size(18.dp), tint = Cinema.gold)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Cinema.gold)
    }
}

@Composable
private fun RadioStationRow(
    app: StreamLabApplication,
    station: RadioStation,
    favorite: Boolean,
    starting: Boolean,
    onListen: () -> Unit,
    onFavorite: () -> Unit,
) {
    val playLabel = stringResource(R.string.radio_play, station.name)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onListen).testTag("radio-station-${station.uuid}"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioStationIcon(app, station)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(station.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val genres = StationTidy.genres(station.tags)
                if (genres.isNotBlank()) Text(genres, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val favoriteLabel = stringResource(if (favorite) R.string.radio_favorite_remove else R.string.radio_favorite_add, station.name)
            IconButton(onClick = onFavorite, modifier = Modifier.testTag("radio-favorite-${station.uuid}").semantics { contentDescription = favoriteLabel }) {
                SlIcon(if (favorite) R.drawable.ic_nav_favorite else R.drawable.ic_star_outline, null,
                    tint = if (favorite) Cinema.gold else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledIconButton(
                onClick = onListen, enabled = !starting,
                modifier = Modifier.size(46.dp).semantics { contentDescription = playLabel },
                shape = CircleShape,
            ) {
                if (starting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else SlIcon(R.drawable.ic_ctl_play, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun RadioStationIcon(app: StreamLabApplication, station: RadioStation) {
    var bitmap by remember(station.favicon) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(station.favicon) {
        val url = station.favicon ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = app.network.fetchMedia(url, 512_000)
                // Tiny favicons (16 px) look broken once enlarged: the initials tile is cleaner.
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.takeIf { it.width >= 48 && it.height >= 48 }?.asImageBitmap()
            }.getOrNull()
        }
    }
    val image = bitmap
    if (image != null) {
        Surface(Modifier.size(52.dp), shape = RoundedCornerShape(13.dp), color = Cinema.white) {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.pass_radio_logo, station.name),
                modifier = Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Fit,
            )
        }
    } else {
        MonogramTile(station.name, 52.dp)
    }
}

private fun countryName(code: String): String = Locale.Builder().setRegion(code).build().displayCountry.ifBlank { code }

/** No usable network at all (airplane mode, no Wi-Fi and no mobile data). */
internal fun isOffline(context: android.content.Context): Boolean {
    val manager = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return true
    return !caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
