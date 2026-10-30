package com.blueway.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blueway.app.AppViewModel
import com.blueway.app.R
import com.blueway.app.Screen
import com.blueway.app.Tab
import com.blueway.app.data.NoiseLevel
import com.blueway.app.data.ReportType
import com.blueway.app.data.RouteMode
import com.blueway.app.data.RouteResult
import com.blueway.app.data.SharedReports
import kotlinx.coroutines.launch

private class WelcomePage(val title: String, val body: String, val images: List<Int>)

private val PAGES = listOf(
    WelcomePage("Rutas más tranquilas\npara una ciudad más inclusiva", "Una ciudad para todas las personas", emptyList()),
    WelcomePage(
        "Anticipa el ruido\nantes de salir",
        "El mapa muestra el ruido estimado de cada calle de Málaga según su tipo y la hora del día.",
        NoiseLevel.entries.map { it.icon },
    ),
    WelcomePage(
        "Compara y elige\ntu camino",
        "Rápida, tranquila o con menos ruido. Tú decides, y siempre verás lo que cuesta cada opción.",
        listOf(R.drawable.ic_report_noise_low, R.drawable.ic_report_noise_normal),
    ),
    WelcomePage(
        "Avisa de lo que\nestá pasando",
        "Obras, eventos o tráfico, sin registrarte. Cada dato indica si es estimado, simulado o avisado. BlueWay informa, pero no garantiza una ruta tranquila.",
        listOf(R.drawable.ic_report_works, R.drawable.ic_report_event, R.drawable.ic_report_traffic),
    ),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val bw = LocalBw.current
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            HorizontalPager(pager, Modifier.weight(1f)) { index ->
                val page = PAGES[index]
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(24.dp))
                    Image(painterResource(R.drawable.logo_blueway), "BlueWay", Modifier.width(260.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(page.title, style = MaterialTheme.typography.titleLarge, color = bw.ink, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                    if (page.images.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(page.body, style = MaterialTheme.typography.bodyLarge, color = bw.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 28.dp))
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            page.images.forEach { Image(painterResource(it), null, Modifier.size(if (page.images.size > 3) 72.dp else 92.dp)) }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box {
                        Image(
                            painterResource(R.drawable.illustration_welcome),
                            "Ilustración: una persona camina tranquila por Málaga",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(
                            Modifier.matchParentSize().background(
                                Brush.verticalGradient(0f to Color.White, 0.16f to Color.Transparent, 0.9f to Color.Transparent, 1f to Color.White),
                            ),
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val last = pager.currentPage == PAGES.lastIndex
                GradientButton(
                    if (last) "Empezar" else if (pager.currentPage == 0) "Comenzar" else "Siguiente",
                    {
                        if (last) {
                            onFinish()
                        } else {
                            scope.launch {
                                if (reduce) pager.scrollToPage(pager.currentPage + 1) else pager.animateScrollToPage(pager.currentPage + 1)
                            }
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                // El lema ocupa siempre su sitio para que el botón no cambie de posición entre páginas.
                Text(
                    PAGES[0].body,
                    color = if (pager.currentPage == 0) bw.muted else Color.Transparent,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.semantics { contentDescription = "Página ${pager.currentPage + 1} de ${PAGES.size}" },
                ) {
                    repeat(PAGES.size) { i ->
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (i == pager.currentPage) bw.ink else bw.outline))
                    }
                }
            }
        }
    }
}

@Composable
private fun TabBackdrop(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        WaveBackground()
        content()
    }
}

/** Pestaña Rutas: ficha comparativa (RF4) y rutas guardadas. */
@Composable
fun RoutesTab(vm: AppViewModel) {
    val bw = LocalBw.current
    TabBackdrop {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f).statusBarsPadding(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("Rutas", style = MaterialTheme.typography.headlineLarge, color = bw.ink)
                    Text(
                        vm.destination?.let { "Comparación hasta ${it.name}" } ?: "Elige un destino para comparar opciones.",
                        color = bw.muted,
                    )
                }
                if (vm.routes.isEmpty()) {
                    item {
                        GradientButton("Buscar un destino", { vm.navigate(Screen.Search(false)) }, showChevron = false)
                    }
                } else {
                    items(vm.routes, key = { it.mode.name }) { route -> CompareCard(vm, route) }
                    item {
                        SoftCard(Modifier.fillMaxWidth(), onClick = vm::saveCurrentRoute) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Rounded.BookmarkBorder, null, tint = bw.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Guardar la ruta elegida", style = MaterialTheme.typography.titleSmall, color = bw.inkSoft)
                            }
                        }
                    }
                    item {
                        Text(
                            "Comparación estimada con el modelo de ruido de BlueWay y datos de OpenStreetMap. La ruta con menos ruido no tiene por qué ser la mejor para todas las personas.",
                            style = MaterialTheme.typography.bodySmall, color = bw.muted,
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    SectionTitle("Rutas guardadas")
                }
                if (vm.savedRoutes.isEmpty()) {
                    item { Text("Todavía no has guardado ninguna ruta.", color = bw.muted) }
                }
                items(vm.savedRoutes.toList(), key = { it.id }) { saved ->
                    ListRow(
                        saved.name, saved.mode.label,
                        { IconTile(Icons.Rounded.Bookmark) },
                        { vm.openSavedRoute(saved) },
                        trailing = {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { vm.deleteSavedRoute(saved) }
                                    .semantics { contentDescription = "Eliminar ruta guardada" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Close, null, tint = bw.muted) }
                        },
                    )
                }
            }
            BottomTabs(vm)
        }
    }
}

@Composable
private fun CompareCard(vm: AppViewModel, route: RouteResult) {
    val bw = LocalBw.current
    val selected = vm.selectedMode == route.mode
    Surface(
        onClick = {
            vm.selectMode(route.mode)
            vm.tab = Tab.HOME
        },
        shape = RoundedCornerShape(24.dp),
        color = bw.surface,
        shadowElevation = if (selected) 6.dp else 2.dp,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, bw.primary) else null,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(modeIcon(route.mode), tint = Color.White, bg = if (route.mode == RouteMode.FAST) bw.primary else bw.green, size = 46.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(route.mode.label, style = MaterialTheme.typography.titleMedium, color = bw.ink)
                    Text("${formatMinutes(route.seconds)} · ${formatMeters(route.meters)}", color = bw.muted, style = MaterialTheme.typography.bodyMedium)
                }
                if (selected) Pill("Elegida", Color.White, bw.primary)
            }
            Text(noiseSummary(route), style = MaterialTheme.typography.titleSmall, color = bw.inkSoft)
            NoiseBar(route.metersByLevel)
            val total = route.metersByLevel.sum().coerceAtLeast(1.0)
            Text(
                NoiseLevel.entries.filter { route.metersByLevel[it.ordinal] / total >= 0.01 }
                    .joinToString(" · ") { "${it.label} ${(route.metersByLevel[it.ordinal] * 100 / total).toInt()}%" },
                style = MaterialTheme.typography.bodySmall, color = bw.muted,
            )
            Text(
                "Media ${route.averageDb} dB · Máximo ${route.peakDb} dB · ${countOf(route.reportsOnRoute, "aviso", "avisos")}",
                style = MaterialTheme.typography.bodySmall, color = bw.muted,
            )
            Text(
                "Confianza: " + (if (route.nearSensorPct >= 50) "media" else "baja") + ". Origen: Estimado; ${route.nearSensorPct}% junto a sensores simulados.",
                style = MaterialTheme.typography.bodySmall, color = bw.muted,
            )
        }
    }
}

@Composable
fun FavoritesTab(vm: AppViewModel) {
    val bw = LocalBw.current
    TabBackdrop {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f).statusBarsPadding(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("Favoritos", style = MaterialTheme.typography.headlineLarge, color = bw.ink)
                    Text("Tus lugares se guardan solo en este teléfono.", color = bw.muted)
                }
                if (vm.favorites.isEmpty()) {
                    item {
                        Text(
                            "Aún no tienes favoritos. Abre una zona en el mapa y toca el corazón para guardarla.",
                            color = bw.muted, modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(vm.favorites.toList(), key = { it.name + it.point }) { place ->
                    ListRow(
                        place.name, place.detail,
                        { IconTile(Icons.Rounded.Favorite) },
                        {
                            vm.tab = Tab.HOME
                            vm.chooseDestination(place)
                        },
                        trailing = {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { vm.toggleFavorite(place) }
                                    .semantics { contentDescription = "Quitar de favoritos" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Close, null, tint = bw.muted) }
                        },
                    )
                }
            }
            BottomTabs(vm)
        }
    }
}

@Composable
fun ProfileTab(vm: AppViewModel) {
    val bw = LocalBw.current
    var editingName by remember { mutableStateOf(false) }
    val sensitivityLabel = listOf("baja", "media", "alta")[vm.settings.filters.sensitivity]
    TabBackdrop {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.avatar_default), "Avatar", Modifier.size(128.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { editingName = true }.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(vm.settings.name, style = MaterialTheme.typography.headlineMedium, color = bw.ink)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Edit, "Cambiar nombre", tint = bw.primary, modifier = Modifier.size(20.dp))
                    }
                    Text("Sensibilidad al ruido $sensitivityLabel", color = bw.muted)
                }
                ProfileRow(Icons.Rounded.Favorite, "Mis lugares", "Guarda y organiza tus destinos favoritos.") { vm.tab = Tab.FAVORITES }
                ProfileRow(Icons.Rounded.Bookmark, "Rutas guardadas", "Accede a tus rutas favoritas.") { vm.tab = Tab.ROUTES }
                ProfileRow(Icons.Rounded.Notifications, "Ajustes de notificaciones", "Elige qué avisos quieres recibir.") { vm.navigate(Screen.NotificationSettings) }
                ProfileRow(Icons.Rounded.Tune, "Preferencias de ruta", "Personaliza tu experiencia.") { vm.navigate(Screen.Filters) }
                ToggleRow(Icons.Rounded.Visibility, "Modo de alto contraste", "Activa una vista más clara y accesible.", vm.settings.highContrast) { v -> vm.updateSettings { copy(highContrast = v) } }
                ToggleRow(Icons.Rounded.Animation, "Reducir movimiento", "Quita animaciones del mapa y las pantallas.", vm.settings.reduceMotion) { v -> vm.updateSettings { copy(reduceMotion = v) } }
                ProfileRow(Icons.AutoMirrored.Rounded.HelpOutline, "Ayuda y soporte", "Resolvemos tus dudas.") { vm.navigate(Screen.Help) }
                ProfileRow(Icons.Rounded.Info, "Sobre BlueWay", "Conoce más sobre nuestro proyecto.") { vm.navigate(Screen.About) }
                Spacer(Modifier.height(8.dp))
            }
            BottomTabs(vm)
        }
    }
    if (editingName) {
        var name by remember { mutableStateOf(vm.settings.name) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Tu nombre") },
            text = {
                OutlinedTextField(name, { name = it.take(30) }, singleLine = true, label = { Text("Solo se guarda en este teléfono") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val clean = name.trim()
                    if (clean.isNotEmpty()) vm.updateSettings { copy(name = clean) }
                    editingName = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListRow(title, subtitle, { IconTile(icon) }, onClick)
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(title, subtitle, { IconTile(icon) }, { onChange(!checked) }, trailing = { Switch(checked, onChange) })
}

@Composable
fun NotificationSettingsScreen(vm: AppViewModel) {
    val bw = LocalBw.current
    TabBackdrop {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            ScreenHeader("Notificaciones", "Son avisos discretos dentro de la app durante la ruta, sin sonido ni vibración.", { vm.back() })
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterRow(null, Icons.Rounded.Notifications, bw.tint, "Mostrar avisos en ruta", "Te los enseñamos al acercarte a menos de 150 m.", vm.settings.notifyReports) { v ->
                    vm.updateSettings { copy(notifyReports = v) }
                }
                FilterRow(null, Icons.Rounded.Notifications, bw.tint, "Voz durante la ruta", "Lee las indicaciones en voz alta. Desactivada por defecto.", vm.settings.voiceGuidance) { v ->
                    if (v != vm.settings.voiceGuidance) vm.toggleVoice()
                }
                if (vm.settings.notifyReports) {
                    Spacer(Modifier.height(4.dp))
                    SectionTitle("Tipos de aviso")
                    ReportType.entries.forEach { type ->
                        FilterRow(type.icon, null, Color.White, type.label, type.shortDescription, type in vm.settings.notifyTypes) { v ->
                            vm.updateSettings { copy(notifyTypes = if (v) notifyTypes + type else notifyTypes - type) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextScreen(vm: AppViewModel, title: String, sections: List<Pair<String, String>>) {
    val bw = LocalBw.current
    TabBackdrop {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            ScreenHeader(title, null, { vm.back() })
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                sections.forEach { (heading, body) ->
                    SoftCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(heading, style = MaterialTheme.typography.titleMedium, color = bw.ink)
                            Spacer(Modifier.height(4.dp))
                            Text(body, style = MaterialTheme.typography.bodyMedium, color = bw.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HelpScreen(vm: AppViewModel) = TextScreen(
    vm, "Ayuda y soporte",
    listOf(
        "¿Cómo busco una ruta?" to "Escribe o dicta el destino en la pantalla de inicio, o mantén pulsado un punto del mapa. Después elige entre Ruta tranquila, Ruta rápida o Menos ruido, y toca Iniciar.",
        "¿Qué significan los colores?" to "Verde es ruido bajo, amarillo moderado, naranja alto y rojo muy alto. Cada ficha también lo indica con texto. Consulta la leyenda en la pestaña Mapas.",
        "¿Cómo envío un aviso?" to "Toca el botón del megáfono en el mapa, elige el tipo, confirma el lugar y envíalo. No necesitas cuenta. Si ya hay uno igual cerca, se refuerza en lugar de duplicarse.",
        "¿Por qué dice «trayecto simulado»?" to "Porque la salida no es tu ubicación real, por ejemplo si estás fuera de Málaga o has elegido otra salida. Sirve para ver la ruta sin caminarla.",
        "¿La app funciona sin internet?" to "Las rutas, el ruido estimado y la búsqueda de calles sí. Sin conexión no se descarga el mapa de fondo ni funciona la búsqueda ampliada de lugares.",
        "Privacidad" to if (SharedReports.enabled) {
            "Sin cuenta. No se graba audio y no se guardan tus recorridos GPS. Tus ajustes y favoritos se quedan en este teléfono. Los avisos que envías se comparten con una ubicación aproximada y un identificador aleatorio que nadie más ve, y se borran una semana después de caducar. Si buscas un lugar, el texto se envía a Photon (komoot), y el mapa se descarga de OpenFreeMap."
        } else {
            "Sin cuenta. No se graba audio y no se guardan tus recorridos GPS. Tus ajustes, favoritos y avisos se quedan en este teléfono. Si buscas un lugar, el texto se envía a Photon (komoot), y el mapa se descarga de OpenFreeMap."
        },
    ),
)

@Composable
fun AboutScreen(vm: AppViewModel) = TextScreen(
    vm, "Sobre BlueWay",
    listOf(
        "Dirección a la calma" to "BlueWay ayuda a personas autistas y con sensibilidad sensorial a planificar sus desplazamientos por Málaga anticipando el entorno sonoro.",
        "Qué es real y qué no" to "Las calles, parques, bancos y cafeterías son datos reales de OpenStreetMap. El ruido es una estimación según el tipo de vía, la cercanía al tráfico y la hora. Los sensores son simulados y los avisos marcados como «Ejemplo» son de prueba: todavía no hay sensores instalados.",
        "Lo que BlueWay no promete" to "La app informa, pero no garantiza que una ruta sea silenciosa, segura o adecuada para todas las personas. Tampoco ha sido validada todavía con personas autistas.",
        "Limitaciones de esta versión" to if (SharedReports.enabled) "Cubre Málaga capital. Los avisos los envían personas usuarias y pueden no estar comprobados." else "Cubre Málaga capital. Los avisos se guardan solo en tu teléfono y aún no se comparten con otras personas.",
        "Créditos" to "Mapa base: OpenFreeMap y OpenMapTiles. Datos: © colaboradores de OpenStreetMap (ODbL). Búsqueda: Photon (komoot). Tipografía: Nunito (OFL). Motor del mapa: MapLibre.",
    ),
)
