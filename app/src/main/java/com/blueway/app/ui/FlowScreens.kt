package com.blueway.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Accessible
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blueway.app.AppViewModel
import com.blueway.app.R
import com.blueway.app.Screen
import com.blueway.app.data.DataSource
import com.blueway.app.data.NoiseLevel
import com.blueway.app.data.Place
import com.blueway.app.data.PlaceCategory
import com.blueway.app.data.RouteFilters
import com.blueway.app.data.Zone
import kotlinx.coroutines.delay
import java.util.Locale

fun categoryIcon(c: PlaceCategory): ImageVector = when (c) {
    PlaceCategory.EDUCATION -> Icons.Rounded.School
    PlaceCategory.HEALTH -> Icons.Rounded.LocalHospital
    PlaceCategory.PARKS -> Icons.Rounded.Park
    PlaceCategory.TRANSPORT -> Icons.Rounded.DirectionsBus
    PlaceCategory.CULTURE -> Icons.Rounded.AccountBalance
    PlaceCategory.CALM -> Icons.Rounded.SelfImprovement
    PlaceCategory.OTHER -> Icons.Rounded.Place
}

/** Lugares del diseño; se muestran como sugerencias hasta que haya recientes reales. */
private val SUGGESTED = listOf("Universidad de Málaga", "La Malagueta", "Hospital Regional", "Málaga-María Zambrano")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(vm: AppViewModel, forOrigin: Boolean, voice: Boolean) {
    val bw = LocalBw.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf<PlaceCategory?>(null) }
    val focus = remember { FocusRequester() }

    fun choose(place: Place) {
        vm.back()
        if (forOrigin) vm.setOrigin(place) else vm.chooseDestination(place)
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it }
        }
    }
    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "¿A dónde quieres ir?")
        try {
            speech.launch(intent)
        } catch (_: ActivityNotFoundException) {
            vm.message = "Este teléfono no tiene búsqueda por voz disponible."
        }
    }
    LaunchedEffect(Unit) {
        if (voice) listen() else runCatching { focus.requestFocus() }
    }
    LaunchedEffect(query) {
        val text = query.trim()
        if (text.length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        results = vm.searchLocal(text)
        searching = true
        delay(450)
        val online = vm.searchOnline(text)
        results = (results + online).distinctBy { it.name.lowercase(Locale.ROOT) }
        searching = false
    }

    Box(Modifier.fillMaxSize()) {
        WaveBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(8.dp))
            BackCircle({ if (category != null) category = null else vm.back() })
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(50), color = bw.surface, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                Row(Modifier.padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = bw.inkSoft, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it.take(80) },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Nunito, fontSize = 16.sp, color = bw.ink, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(bw.primary),
                        modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Buscar un lugar" },
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(if (forOrigin) "Buscar lugar de salida…" else "Buscar un lugar…", color = bw.muted)
                            }
                            inner()
                        },
                    )
                    if (query.isNotEmpty()) {
                        Box(Modifier.size(46.dp).clip(CircleShape).clickable(role = Role.Button) { query = "" }.semantics { contentDescription = "Borrar texto" }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, null, tint = bw.muted)
                        }
                    } else {
                        Box(Modifier.size(46.dp).clip(CircleShape).clickable(role = Role.Button, onClick = ::listen).semantics { contentDescription = "Buscar por voz" }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Mic, null, tint = bw.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                val selectedCategory = category
                when {
                    query.trim().length >= 2 -> {
                        items(results, key = { it.name + it.point }) { PlaceRow(it, ::choose) }
                        if (results.isEmpty()) {
                            item {
                                Text(
                                    if (searching) "Buscando…" else "No encontramos ese lugar en Málaga capital.",
                                    color = bw.muted, modifier = Modifier.padding(8.dp),
                                )
                            }
                        }
                    }
                    selectedCategory != null -> {
                        item { SectionTitle(selectedCategory.label) }
                        items(vm.placesIn(selectedCategory), key = { it.name + it.point }) { PlaceRow(it, ::choose) }
                    }
                    else -> {
                        if (forOrigin) {
                            item {
                                ListRow(
                                    "Mi ubicación", "Usar la posición de tu teléfono",
                                    { IconTile(Icons.Rounded.MyLocation) },
                                    { vm.back(); vm.setOrigin(null) }, color = bw.tint, elevated = false,
                                )
                            }
                        }
                        val recent = vm.recents.toList()
                        val suggested = if (recent.isNotEmpty()) recent else SUGGESTED.mapNotNull { name ->
                            vm.searchLocal(name).firstOrNull()
                        }.ifEmpty { vm.city?.places?.take(4).orEmpty() }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionTitle(if (recent.isNotEmpty()) "Destinos recientes" else "Sugerencias", Modifier.weight(1f))
                                if (recent.isNotEmpty()) TextButton(onClick = vm::clearRecents) { Text("Borrar") }
                            }
                        }
                        items(suggested, key = { "r" + it.name + it.point }) { PlaceRow(it, ::choose) }
                        item {
                            Spacer(Modifier.height(4.dp))
                            SectionTitle("Categorías")
                            Spacer(Modifier.height(10.dp))
                            CategoryGrid { category = it }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(place: Place, onClick: (Place) -> Unit) {
    ListRow(place.name, place.detail, { IconTile(categoryIcon(place.category)) }, { onClick(place) }, color = LocalBw.current.tint, elevated = false)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid(onSelect: (PlaceCategory) -> Unit) {
    val bw = LocalBw.current
    val list = PlaceCategory.entries.filter { it != PlaceCategory.OTHER }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        list.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { c ->
                    SoftCard(Modifier.weight(1f).aspectRatio(1.15f), onClick = { onSelect(c) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(6.dp)) {
                            Icon(categoryIcon(c), null, tint = bw.primary, modifier = Modifier.size(38.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(c.label, style = MaterialTheme.typography.bodyMedium, color = bw.inkSoft, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FiltersScreen(vm: AppViewModel) {
    val bw = LocalBw.current
    var f by remember { mutableStateOf(vm.settings.filters) }
    Box(Modifier.fillMaxSize()) {
        WaveBackground()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            ScreenHeader("Personaliza tu ruta", "Ajusta la ruta según tus necesidades.", { vm.back() })
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterRow(R.drawable.ic_report_noise_extreme, null, Color(0xFFFFE6EA), "Evitar zonas de alto ruido", "Reduce la exposición a zonas ruidosas.", f.avoidHighNoise) { f = f.copy(avoidHighNoise = it) }
                FilterRow(null, Icons.Rounded.Park, bw.greenSoft, "Priorizar zonas verdes", "Incluye parques y espacios naturales.", f.preferGreen) { f = f.copy(preferGreen = it) }
                FilterRow(null, Icons.Rounded.Groups, bw.tint, "Evitar aglomeraciones", "Rutas por zonas menos concurridas.", f.avoidCrowds) { f = f.copy(avoidCrowds = it) }
                FilterRow(null, Icons.Rounded.Timer, bw.tint, "Ruta más corta", "Prioriza la menor distancia posible.", f.shortest) { f = f.copy(shortest = it) }
                FilterRow(null, Icons.Rounded.Accessible, bw.tint, "Accesibilidad", "Evita escaleras y firmes difíciles, si consta en OpenStreetMap.", f.accessible) { f = f.copy(accessible = it) }
                Spacer(Modifier.height(10.dp))
                SectionTitle("Nivel de sensibilidad al ruido")
                Slider(
                    value = f.sensitivity.toFloat(),
                    onValueChange = { f = f.copy(sensitivity = it.toInt()) },
                    valueRange = 0f..2f,
                    steps = 1,
                    modifier = Modifier.semantics { contentDescription = "Sensibilidad al ruido" },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("Baja", "Media", "Alta").forEachIndexed { i, label ->
                        Text(
                            label,
                            color = if (f.sensitivity == i) bw.ink else bw.muted,
                            fontWeight = if (f.sensitivity == i) FontWeight.ExtraBold else FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                GradientButton("Aplicar filtros", {
                    vm.applyFilters(f)
                    vm.back()
                })
                TextButton(onClick = { f = RouteFilters() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Restablecer") }
            }
        }
    }
}

@Composable
fun FilterRow(image: Int?, icon: ImageVector?, bg: Color, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val bw = LocalBw.current
    SoftCard(Modifier.fillMaxWidth(), onClick = { onChange(!checked) }) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                if (image != null) Image(painterResource(image), null, Modifier.size(44.dp))
                if (icon != null) Icon(icon, null, tint = bw.primary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = bw.inkSoft)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = bw.muted)
            }
            Spacer(Modifier.width(6.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
fun LegendScreen(vm: AppViewModel) {
    val bw = LocalBw.current
    Box(Modifier.fillMaxSize()) {
        WaveBackground()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                BackCircle({ vm.back() })
                Image(painterResource(R.drawable.logo_blueway), "BlueWay", Modifier.width(130.dp).align(Alignment.Center))
            }
            Text("Niveles de ruido", style = MaterialTheme.typography.headlineMedium, color = bw.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(
                "El mapa muestra una estimación del ruido para ayudarte a elegir rutas más tranquilas.",
                color = bw.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 6.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NoiseLevel.entries.forEach { level ->
                    SoftCard(Modifier.fillMaxWidth(), color = level.soft) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(level.icon), null, Modifier.size(62.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(level.label, style = MaterialTheme.typography.titleLarge, color = bw.ink)
                                Text(level.range, color = bw.muted, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                Pill(level.tag, if (bw.highContrast) bw.ink else level.color, Color.White)
                                Spacer(Modifier.height(4.dp))
                                Text(level.description, color = bw.muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("De dónde sale cada dato", style = MaterialTheme.typography.titleMedium, color = bw.ink)
                        Spacer(Modifier.height(6.dp))
                        DataSource.entries.forEach {
                            Text("${it.label}: ${it.explanation}", style = MaterialTheme.typography.bodySmall, color = bw.muted, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Info, null, tint = bw.primary, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Ten en cuenta", style = MaterialTheme.typography.titleMedium, color = bw.ink)
                                Text(
                                    "Los niveles de ruido pueden variar según la hora del día, eventos y condiciones del entorno. BlueWay informa, pero no garantiza una ruta tranquila.",
                                    style = MaterialTheme.typography.bodySmall, color = bw.muted,
                                )
                            }
                            if (!bw.highContrast) Image(painterResource(R.drawable.illustration_city), null, Modifier.size(70.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FactorTile(Icons.Rounded.Schedule, "Hora del día", Modifier.weight(1f))
                            FactorTile(Icons.Rounded.Groups, "Aglomeraciones", Modifier.weight(1f))
                            FactorTile(Icons.Rounded.Event, "Eventos", Modifier.weight(1f))
                            FactorTile(Icons.Rounded.Cloud, "Meteorología", Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FactorTile(icon: ImageVector, label: String, modifier: Modifier) {
    val bw = LocalBw.current
    Surface(shape = RoundedCornerShape(14.dp), color = bw.surface, modifier = modifier) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = bw.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = bw.muted, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZoneScreen(vm: AppViewModel, zone: Zone) {
    val bw = LocalBw.current
    val level = NoiseLevel.of(zone.db)
    val place = Place(zone.title, zone.subtitle, zone.point)
    Box(Modifier.fillMaxSize().background(bw.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(250.dp)) {
                Image(painterResource(R.drawable.photo_zone), "Imagen ilustrativa de una calle de Málaga", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.7f to Color.Transparent, 1f to bw.background)))
                Text(
                    "Imagen ilustrativa",
                    style = MaterialTheme.typography.labelSmall, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(26.dp), color = bw.surface, shadowElevation = 5.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(level.icon), null, Modifier.size(70.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(zone.title, style = MaterialTheme.typography.titleLarge, color = bw.ink)
                                Text(zone.subtitle, style = MaterialTheme.typography.bodyMedium, color = bw.muted)
                            }
                            Spacer(Modifier.width(8.dp))
                            SoftCard {
                                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("≈ ${zone.db} dB", style = MaterialTheme.typography.titleMedium, color = bw.ink, fontWeight = FontWeight.ExtraBold)
                                    Text("Nivel actual", style = MaterialTheme.typography.labelSmall, color = bw.muted)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LevelPill(level)
                            SourceTag(zone.source)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(zone.source.explanation, style = MaterialTheme.typography.bodySmall, color = bw.muted)
                        zone.sensorNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = bw.muted) }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AmenityTile(Icons.Rounded.Park, if (zone.inGreen) "Zona verde" else countOf(zone.trees, "árbol", "árboles"), Modifier.weight(1f))
                            AmenityTile(Icons.Rounded.Chair, countOf(zone.benches, "banco", "bancos"), Modifier.weight(1f))
                            AmenityTile(Icons.Rounded.WbSunny, if (zone.trees >= 4) "Con sombra" else "Poca sombra", Modifier.weight(1f))
                            AmenityTile(Icons.Rounded.LocalCafe, countOf(zone.cafes, "café", "cafés"), Modifier.weight(1f))
                        }
                    }
                }
                GradientButton("Cómo llegar", {
                    vm.back()
                    vm.tab = com.blueway.app.Tab.HOME
                    vm.chooseDestination(place.copy(name = if (zone.title.startsWith("Zona") || zone.title.startsWith("Ruido")) zone.subtitle else zone.title))
                }, leading = Icons.Rounded.NearMe)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val fav = vm.isFavorite(place.name)
                    SoftCard(Modifier.weight(1f), onClick = { vm.toggleFavorite(place) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = bw.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(if (fav) "Guardado" else "Guardar", color = bw.inkSoft, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                SoftCard(Modifier.fillMaxWidth(), color = bw.surface) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Información de la zona", style = MaterialTheme.typography.titleMedium, color = bw.ink)
                        Spacer(Modifier.height(8.dp))
                        InfoLine(Icons.Rounded.Groups, "Afluencia estimada", when {
                            zone.crowdPct < 25 -> "Baja"
                            zone.crowdPct < 60 -> "Media"
                            else -> "Alta"
                        })
                        InfoLine(Icons.Rounded.Timer, "Horario más tranquilo", zone.quietHours)
                        InfoLine(Icons.Rounded.Eco, "Ideal para", idealFor(level, zone))
                    }
                }
                if (zone.reports.isNotEmpty()) {
                    SoftCard(Modifier.fillMaxWidth(), color = bw.surface) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Avisos cercanos", style = MaterialTheme.typography.titleMedium, color = bw.ink)
                            zone.reports.forEach {
                                Text(
                                    "${it.type.label} · ${it.status().label} · ${timeAgo(it.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall, color = bw.muted,
                                    modifier = Modifier.clickable(role = Role.Button) { vm.selectedReport = it }.padding(vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                SoftCard(Modifier.fillMaxWidth(), color = bw.surface) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Evolución estimada del ruido", style = MaterialTheme.typography.titleMedium, color = bw.ink)
                        Text("Un día típico (0 h – 24 h)", style = MaterialTheme.typography.bodySmall, color = bw.muted)
                        Spacer(Modifier.height(10.dp))
                        NoiseCurve(zone.curve, vm.currentHour)
                    }
                }
                Text(
                    "Datos de calles, bancos y cafeterías: © colaboradores de OpenStreetMap.",
                    style = MaterialTheme.typography.bodySmall, color = bw.muted,
                )
            }
        }
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.92f), Color.White.copy(alpha = 0.92f)))).statusBarsPadding())
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            BackCircle({ vm.back() })
            val fav = vm.isFavorite(place.name)
            RoundIconButton(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (fav) "Quitar de favoritos" else "Añadir a favoritos", { vm.toggleFavorite(place) })
        }
    }
}

private fun idealFor(level: NoiseLevel, zone: Zone): String = buildList {
    if (level == NoiseLevel.LOW) add("Descansar")
    if (level == NoiseLevel.LOW || level == NoiseLevel.MODERATE) add("Pasear")
    if (zone.benches > 0 && level != NoiseLevel.VERY_HIGH) add("Hacer una pausa")
}.ifEmpty { listOf("Pasar de largo") }.joinToString(" · ")

@Composable
private fun AmenityTile(icon: ImageVector, label: String, modifier: Modifier) {
    val bw = LocalBw.current
    SoftCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = bw.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = bw.muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, label: String, value: String) {
    val bw = LocalBw.current
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = bw.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = bw.muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = bw.inkSoft, textAlign = TextAlign.End)
    }
}

@Composable
private fun NoiseCurve(curve: List<Int>, nowHour: Int) {
    val bw = LocalBw.current
    val min = curve.min() - 4
    val max = curve.max().coerceAtLeast(min + 1)
    val description = "Mínimo ${curve.min()} decibelios y máximo ${curve.max()} decibelios a lo largo del día"
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(Modifier.fillMaxWidth().height(70.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            curve.forEachIndexed { hour, db ->
                val fraction = ((db - min).toFloat() / (max - min)).coerceIn(0.1f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .height(70.dp * fraction)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (hour == nowHour) bw.ink else NoiseLevel.of(db).color.copy(alpha = 0.75f)),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0h", "6h", "12h", "18h", "24h").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = bw.muted) }
        }
    }
}
