package app.cleanroom.streamlab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cleanroom.streamlab.services.ExternalService
import app.cleanroom.streamlab.services.ServiceRegistry
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val bg = Color(0xFF0B1118)
private val elevated = Color(0xFF15232C)
private val mint = Color(0xFF73E0BD)
private val gold = Color(0xFFF5C985)
private val white = Color(0xFFF5F8F7)
private val muted = Color(0xFFB5C7C8)

private data class CinemaAction(val id: String, val title: Int, val details: Int, val index: String, val section: String)

@Composable
internal fun CinemaPassengerHome(
    onOpenSection: (String) -> Unit,
    onOpenService: (ExternalService) -> Unit,
    /** Inside the main app: no own navigation bar or safety card (the app shows those itself). */
    embedded: Boolean = false,
    continueItems: List<ContinueItem> = emptyList(),
    onResume: (app.cleanroom.streamlab.data.CatalogChannelRow) -> Unit = {},
) {
    val fontScale = LocalDensity.current.fontScale
    val feedState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val primary = remember {
        listOf(
            CinemaAction("live", R.string.pass_tv, R.string.pass_tv_desc, "01", "Live"),
            CinemaAction("radio", R.string.pass_radio, R.string.pass_radio_desc, "02", "Radio"),
        )
    }
    val library = remember(embedded) {
        listOfNotNull(
            CinemaAction("drive", R.string.drive_title, R.string.drive_card_desc, "A", "Drive").takeIf { embedded },
            CinemaAction("favorites", R.string.pass_favorites, R.string.pass_favorites_desc, if (embedded) "B" else "A", "Favorites"),
            CinemaAction("videos", R.string.pass_videos, R.string.pass_videos_desc, if (embedded) "C" else "B", "Videos"),
        )
    }
    val services = remember {
        ServiceRegistry.all.sortedWith(compareBy(
            { when (it.id) {
                "youtube" -> 0
                "netflix" -> 1
                "prime_video" -> 2
                "disney_plus" -> 3
                else -> 10
            } },
            { it.name },
        ))
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = mint, onPrimary = bg, secondary = gold, background = bg,
            surface = elevated, onSurface = white, onBackground = white,
        ),
    ) {
        Column((if (embedded) Modifier.fillMaxSize().background(bg) else Modifier.fillMaxSize().background(bg).safeDrawingPadding())
            .testTag(if (embedded) "cinema-home" else "cinema-passenger-home")) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val layout = CinemaLayout.forViewport(maxWidth.value.toInt(), fontScale, maxHeight.value.toInt())
                val feedVerticalGap = if (layout.splitHero) 8.dp else 14.dp
                val feed: @Composable (Modifier) -> Unit = { modifier ->
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(layout.gridColumns),
                        state = feedState,
                        modifier = modifier.testTag(if (embedded) "apps-grid" else "passenger-grid"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(feedVerticalGap),
                    ) {
                        if (!layout.splitHero) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                CinemaHero(
                                    Modifier.fillMaxWidth(),
                                    compact = layout.compactHero,
                                )
                            }
                        }
                        if (!embedded) item(span = { GridItemSpan(maxLineSpan) }) {
                            PassengerSafetyNotice(Modifier.fillMaxWidth())
                        }
                        if (continueItems.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            ContinueWatchingRow(continueItems, onResume)
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            CinemaSection(stringResource(R.string.pass_primary), "01")
                        }
                        items(primary, key = { "primary_" + it.id }, span = { GridItemSpan(layout.featureSpan) }) { action ->
                            CinemaActionCard(action, fontScale, featured = true,
                                compactMedia = layout.splitHero) { onOpenSection(action.section) }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            CinemaSection(stringResource(R.string.pass_services), "02")
                        }
                        if (layout.splitHero) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                CinemaServiceCarousel(services, layout.serviceColumns, onOpenService)
                            }
                        } else {
                            items(services, key = { "service_" + it.id }, span = { GridItemSpan(layout.serviceSpan) }) { service ->
                                CinemaServiceCard(service, compact = false) { onOpenService(service) }
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            CinemaSection(stringResource(R.string.pass_library), "03")
                        }
                        items(library, key = { "library_" + it.id }, span = { GridItemSpan(layout.featureSpan) }) { action ->
                            CinemaActionCard(action, fontScale, featured = false,
                                compactMedia = false) { onOpenSection(action.section) }
                        }
                    }
                }
                if (layout.splitHero) {
                    Row(Modifier.fillMaxSize()) {
                        CinemaHero(
                            Modifier.width(layout.heroWidthDp.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                            isRail = true,
                        )
                    feed(Modifier.weight(1f).fillMaxHeight().padding(bottom = 8.dp))
                    }
                } else {
                    feed(Modifier.fillMaxSize().padding(bottom = 8.dp))
                }
            }
            if (!embedded) CinemaNavigation(
                onHome = { scope.launch { feedState.animateScrollToItem(0) } },
                onOpen = onOpenSection,
            )
        }
    }
}

@Composable
internal fun PassengerSafetyNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag("passenger-safety-notice"),
        color = Color(0xFF1D2B31),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, gold.copy(alpha = .72f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.pass_safety_title),
                color = gold,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(stringResource(R.string.pass_safety_body), color = white,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("passenger-safety-body"))
        }
    }
}

@Composable
private fun CinemaHero(
    modifier: Modifier,
    isRail: Boolean = false,
    compact: Boolean = false,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier.heightIn(min = if (isRail) 0.dp else if (compact) 104.dp else 290.dp)
        .background(bg)
        .testTag("cinema-hero")) {
        Image(
            painterResource(R.drawable.cinema_lounge_hero), null,
            Modifier.matchParentSize().graphicsLayer(scaleX = if (rtl) -1f else 1f),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            if (rtl) listOf(bg.copy(alpha = .24f), bg.copy(alpha = .78f), bg.copy(alpha = .94f))
            else listOf(bg.copy(alpha = .94f), bg.copy(alpha = .78f), bg.copy(alpha = .24f)),
        )))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            listOf(bg.copy(alpha = .18f), Color.Transparent, bg.copy(alpha = .74f)),
        )))
        Column(
            Modifier.fillMaxWidth().padding(if (isRail) 24.dp else if (compact) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (isRail) 18.dp else if (compact) 8.dp else 12.dp),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CinemaIcon("play", mint, Modifier.size(29.dp))
                    Text("StreamLab", color = white, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                }
            }
            if (!compact) {
                Text(
                    stringResource(R.string.pass_kicker), color = gold,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                stringResource(R.string.pass_title), modifier = Modifier.testTag("cinema-title"),
                color = white,
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (!compact) {
                Text(stringResource(R.string.pass_intro), color = muted, style = MaterialTheme.typography.bodyLarge)
            }
            if (isRail) {
                Spacer(Modifier.height(20.dp))
                Text("CINEMA LOUNGE", color = gold, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun CinemaServiceCarousel(
    services: List<ExternalService>,
    visibleCardCount: Int,
    onOpenService: (ExternalService) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val count = visibleCardCount.coerceIn(2, 4)
        val spacing = 14.dp
        val cardWidth = ((maxWidth - spacing * (count - 1)) / count).coerceAtLeast(148.dp)
        LazyRow(
            modifier = Modifier.fillMaxWidth().testTag("passenger-service-carousel"),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            lazyItems(services, key = { "service_" + it.id }) { service ->
                Box(Modifier.width(cardWidth)) {
                    CinemaServiceCard(service, compact = true) { onOpenService(service) }
                }
            }
        }
    }
}

@Composable
private fun CinemaSeatArtwork(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF2B5665), Color(0xFF0B192A))),
            topLeft = Offset(w * .09f, h * .03f), size = Size(w * .82f, h * .67f),
            cornerRadius = CornerRadius(18.dp.toPx()),
        )
        drawRoundRect(
            color = gold.copy(alpha = .72f),
            topLeft = Offset(w * .09f, h * .03f), size = Size(w * .82f, h * .67f),
            cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(2.dp.toPx()),
        )
        drawCircle(mint.copy(alpha = .32f), w * .13f, Offset(w * .51f, h * .38f))
        val triangle = Path().apply {
            moveTo(w * .46f, h * .27f); lineTo(w * .46f, h * .50f)
            lineTo(w * .60f, h * .38f); close()
        }
        drawPath(triangle, mint)
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF604733), Color(0xFF20262D))),
            topLeft = Offset(w * .16f, h * .77f), size = Size(w * .68f, h * .20f),
            cornerRadius = CornerRadius(14.dp.toPx()),
        )
    }
}

@Composable
private fun CinemaSection(title: String, number: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 1.dp).testTag("cinema-section-$number"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = white, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).height(1.dp).background(white.copy(alpha = .08f)))
    }
}

@Composable
private fun CinemaActionCard(action: CinemaAction, fontScale: Float, featured: Boolean,
    compactMedia: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val openLabel = stringResource(R.string.pass_accessibility_title, stringResource(action.title))
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = if (featured) (if (compactMedia) 165.dp else 225.dp) else 121.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .testTag("passenger-card-" + action.id)
            .semantics { contentDescription = openLabel },
        shape = RoundedCornerShape(22.dp),
        color = elevated,
        border = BorderStroke(
            if (focused) 3.dp else 1.5.dp,
            if (focused) mint else if (featured) gold.copy(alpha = .86f) else gold.copy(alpha = .44f),
        ),
    ) {
        Column(Modifier.fillMaxWidth().background(elevated)) {
            if (featured) {
                Box(Modifier.fillMaxWidth().height(
                    if (compactMedia) 96.dp else if (fontScale >= 1.65f) 118.dp else 142.dp,
                )) {
                    Image(
                        painterResource(if (action.id == "live") R.drawable.cinema_live_tv else R.drawable.cinema_world_radio),
                        null, Modifier.matchParentSize(), contentScale = ContentScale.Crop,
                    )
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                        listOf(Color.Transparent, elevated.copy(alpha = .17f)),
                    )))
                    Row(
                        Modifier.align(Alignment.TopStart).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CinemaIcon(action.id, gold, Modifier.size(27.dp))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(if (featured) 17.dp else 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!featured) {
                    if (action.id == "drive") SlIcon(R.drawable.ic_car, null, Modifier.size(32.dp), tint = gold)
                    else CinemaIcon(action.id, gold, Modifier.size(32.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(action.title), color = white, fontWeight = FontWeight.SemiBold,
                        style = if (featured) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge)
                    Text(stringResource(action.details), color = muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun CinemaArtwork(kind: String, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (kind == "live") {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFF3990A4), Color(0xFF143147))),
                topLeft = Offset(w * .05f, h * .14f), size = Size(w * .9f, h * .61f),
                cornerRadius = CornerRadius(12.dp.toPx()),
            )
            drawRoundRect(
                color = gold, topLeft = Offset(w * .05f, h * .14f),
                size = Size(w * .9f, h * .61f), cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(2.dp.toPx()),
            )
            drawCircle(mint.copy(alpha = .75f), w * .12f, Offset(w * .49f, h * .44f))
            drawLine(gold, Offset(w * .5f, h * .75f), Offset(w * .5f, h * .88f), 3.dp.toPx())
            drawLine(gold, Offset(w * .32f, h * .88f), Offset(w * .68f, h * .88f), 3.dp.toPx())
        } else {
            drawRoundRect(
                color = Color(0xFF1B3D48), topLeft = Offset(w * .06f, h * .24f),
                size = Size(w * .88f, h * .55f), cornerRadius = CornerRadius(13.dp.toPx()),
            )
            drawRoundRect(
                color = gold, topLeft = Offset(w * .06f, h * .24f),
                size = Size(w * .88f, h * .55f), cornerRadius = CornerRadius(13.dp.toPx()),
                style = Stroke(2.dp.toPx()),
            )
            drawCircle(gold, w * .13f, Offset(w * .72f, h * .50f), style = Stroke(2.dp.toPx()))
            for (i in 0..5) {
                val x = w * (.17f + .07f * i)
                val height = h * (.04f + .026f * (i % 3))
                drawLine(mint, Offset(x, h * .51f - height), Offset(x, h * .51f + height), 2.dp.toPx())
            }
        }
    }
}

@Composable
private fun CinemaServiceCard(service: ExternalService, compact: Boolean, onClick: () -> Unit) {
    // One neutral tone for every service: the real app icon, when installed, brings the only colour.
    val tint = muted
    val officialDescription = stringResource(R.string.pass_service_desc)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 118.dp else 132.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .semantics { contentDescription = "${service.name}. $officialDescription" }
            .testTag("passenger-service-" + service.id),
        shape = RoundedCornerShape(19.dp),
        color = elevated,
        border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) mint else white.copy(alpha = .08f)),
    ) {
        Column(Modifier.fillMaxWidth().background(elevated)) {
            if (compact) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ServiceBrandBadge(service.id, tint, service.name)
                        Spacer(Modifier.weight(1f))
                        SlIcon(R.drawable.ic_open_external, null, Modifier.size(18.dp), tint = tint)
                    }
                    Text(
                        service.name,
                        modifier = Modifier.fillMaxWidth().testTag("service-name-${service.id}"),
                        color = white,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr),
                        maxLines = 2,
                    )
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top) {
                    ServiceBrandBadge(service.id, tint, service.name)
                    SlIcon(R.drawable.ic_open_external, null, Modifier.size(20.dp), tint = tint)
                }
                Text(service.name, color = white, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
                Text(stringResource(R.string.pass_service_desc), color = muted,
                    style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun CinemaNavigation(onHome: () -> Unit, onOpen: (String) -> Unit) {
    val items = listOf(
        Triple("home", R.string.nav_home, "Apps"),
        Triple("live", R.string.nav_tv, "Live"),
        Triple("radio", R.string.nav_radio, "Radio"),
        Triple("favorites", R.string.nav_favorites, "Favorites"),
        Triple("more", R.string.title_settings, "Settings"),
    )
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("cinema-navigation")) {
        val viewport = CinemaLayout.forViewport(
            widthDp = maxWidth.value.toInt(),
            fontScale = LocalDensity.current.fontScale,
        )
        val columns = viewport.navigationColumns
        val cellWidth = CinemaLayout.navigationCellWidthDp(maxWidth.value.toInt(), columns).dp
        FlowRow(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF1A2D33), bg)))
                .padding(horizontal = 9.dp, vertical = 7.dp),
            maxItemsInEachRow = columns,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { (id, label, section) ->
                val isHome = id == "home"
                val labelText = stringResource(label)
                Surface(
                    onClick = { if (isHome) onHome() else onOpen(section) },
                    modifier = Modifier.width(cellWidth).heightIn(min = if (viewport.iconOnlyNavigation) 64.dp else 59.dp)
                        .focusable()
                        .semantics { contentDescription = labelText }
                        .testTag("passenger-nav-" + id),
                    color = if (isHome) mint.copy(alpha = .17f) else Color.Transparent,
                    border = BorderStroke(1.dp, if (isHome) mint.copy(alpha = .63f) else Color.Transparent),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Column(
                        Modifier.padding(horizontal = 2.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CinemaIcon(id, if (isHome) mint else muted,
                            Modifier.size(if (viewport.iconOnlyNavigation) 29.dp else 25.dp))
                        if (!viewport.iconOnlyNavigation) {
                            Text(labelText, color = if (isHome) mint else muted,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaIcon(kind: String, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w.coerceAtMost(h) * .085f).coerceAtLeast(1f)
        when (kind) {
            "live", "tv" -> {
                drawRoundRect(
                    color, Offset(w * .12f, h * .15f), Size(w * .76f, h * .60f),
                    CornerRadius(w * .10f), style = Stroke(stroke),
                )
                drawLine(color, Offset(w*.5f,h*.74f), Offset(w*.5f,h*.87f), stroke)
                drawLine(color, Offset(w*.29f,h*.87f), Offset(w*.71f,h*.87f), stroke)
            }
            "radio" -> {
                drawRoundRect(
                    color, Offset(w*.12f,h*.34f), Size(w*.76f,h*.52f),
                    CornerRadius(w*.11f), style = Stroke(stroke),
                )
                drawLine(color, Offset(w*.25f,h*.33f), Offset(w*.69f,h*.09f), stroke)
                drawCircle(color, w*.10f, Offset(w*.69f,h*.58f), style = Stroke(stroke))
                drawLine(color, Offset(w*.25f,h*.56f), Offset(w*.45f,h*.56f), stroke)
            }
            "favorites" -> {
                val path = Path().apply {
                    moveTo(w*.50f,h*.86f)
                    cubicTo(w*.12f,h*.57f,w*.05f,h*.31f,w*.26f,h*.22f)
                    cubicTo(w*.39f,h*.15f,w*.48f,h*.26f,w*.50f,h*.35f)
                    cubicTo(w*.62f,h*.14f,w*.82f,h*.17f,w*.88f,h*.35f)
                    cubicTo(w*.95f,h*.56f,w*.67f,h*.75f,w*.50f,h*.86f)
                }
                drawPath(path, color, style = Stroke(stroke))
            }
            "home" -> {
                val path = Path().apply {
                    moveTo(w*.12f,h*.48f);lineTo(w*.50f,h*.12f);lineTo(w*.88f,h*.48f)
                }
                drawPath(path, color, style = Stroke(stroke))
                drawRoundRect(
                    color, Offset(w*.23f,h*.48f), Size(w*.54f,h*.42f),
                    CornerRadius(w*.05f), style = Stroke(stroke),
                )
            }
            "more" -> {
                drawCircle(color, w*.22f, Offset(w*.5f,h*.5f), style = Stroke(stroke))
                drawCircle(color, w*.05f, Offset(w*.5f,h*.5f))
                for (i in 0..7) {
                    val angle = Math.PI * i / 4.0
                    val dx = cos(angle).toFloat()
                    val dy = sin(angle).toFloat()
                    drawLine(color,
                        Offset(w*(.5f + .29f*dx), h*(.5f + .29f*dy)),
                        Offset(w*(.5f + .40f*dx), h*(.5f + .40f*dy)), stroke)
                }
            }
            else -> {
                val p = Path().apply {
                    moveTo(w*.24f,h*.13f);lineTo(w*.24f,h*.87f)
                    lineTo(w*.84f,h*.50f);close()
                }
                drawPath(p, color)
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(items: List<ContinueItem>, onResume: (app.cleanroom.streamlab.data.CatalogChannelRow) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CinemaSection(stringResource(R.string.continue_title), "00")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("continue-row")) {
            lazyItems(items, key = { "continue_" + it.channel.id }) { entry ->
                Surface(
                    onClick = { onResume(entry.channel) },
                    modifier = Modifier.width(230.dp).testTag("continue-${entry.channel.id}"),
                    color = elevated,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, gold.copy(alpha = .5f)),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = mint, modifier = Modifier.size(34.dp)) {
                                Box(contentAlignment = Alignment.Center) { SlIcon(R.drawable.ic_ctl_play, null, Modifier.size(20.dp), tint = bg) }
                            }
                            Text(entry.channel.name, color = white, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { app.cleanroom.streamlab.core.ContinueWatching.fraction(entry.positionMs, entry.durationMs) },
                            modifier = Modifier.fillMaxWidth(), color = gold, trackColor = Color(0xFF2C434D),
                        )
                        Text(stringResource(R.string.continue_left, app.cleanroom.streamlab.core.ContinueWatching.minutesLeft(entry.positionMs, entry.durationMs).toInt()),
                            color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
