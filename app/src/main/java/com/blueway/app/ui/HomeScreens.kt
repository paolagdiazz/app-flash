package com.blueway.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.blueway.app.AppViewModel
import com.blueway.app.CameraCommand
import com.blueway.app.R
import com.blueway.app.Screen
import com.blueway.app.Tab
import com.blueway.app.data.NoiseLevel
import com.blueway.app.data.RouteMode
import com.blueway.app.data.RouteResult

@Composable
private fun SearchBar(vm: AppViewModel) {
    val bw = LocalBw.current
    Surface(
        onClick = { vm.navigate(Screen.Search(false)) },
        shape = RoundedCornerShape(50),
        color = bw.surface,
        shadowElevation = 6.dp,
        border = if (bw.highContrast) BorderStroke(2.dp, bw.outline) else null,
        modifier = Modifier.fillMaxWidth().height(58.dp),
    ) {
        Row(Modifier.padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = bw.inkSoft, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                vm.destination?.name ?: "¿A dónde quieres ir?",
                color = if (vm.destination != null) bw.inkSoft else bw.muted,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (vm.destination != null) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).clickable(role = Role.Button) { vm.clearRoute() }
                        .semantics { contentDescription = "Quitar destino" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, null, tint = bw.muted) }
            } else {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).clickable(role = Role.Button) { vm.navigate(Screen.Search(false, voice = true)) }
                        .semantics { contentDescription = "Buscar por voz" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Mic, null, tint = bw.primary) }
            }
        }
    }
}

@Composable
private fun ModeChip(text: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val bw = LocalBw.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (active) bw.primary else bw.surface,
        shadowElevation = 3.dp,
        border = if (bw.highContrast) BorderStroke(2.dp, bw.outline) else null,
        modifier = Modifier.height(46.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (active) Color.White else bw.inkSoft, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(7.dp))
            Text(text, color = if (active) Color.White else bw.inkSoft, style = MaterialTheme.typography.labelMedium)
        }
    }
}

fun modeIcon(mode: RouteMode): ImageVector = when (mode) {
    RouteMode.CALM -> Icons.Rounded.Eco
    RouteMode.FAST -> Icons.Rounded.Timer
    RouteMode.QUIETEST -> Icons.Rounded.BarChart
}

/** Frase con la comparación de ruido, siempre marcada como estimación. */
fun noiseSummary(route: RouteResult): String = when {
    route.mode == RouteMode.FAST -> "Referencia · ${route.averageDb} dB de media (estimado)"
    route.sameAsFastest -> "Coincide con la ruta rápida"
    (route.noiseReductionPct ?: 0) > 0 -> "-${route.noiseReductionPct}% de ruido (estimado)"
    else -> "Sin reducción de ruido estimada"
}

@Composable
private fun MapFabs(vm: AppViewModel, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
        RoundIconButton(Icons.Rounded.MyLocation, "Centrar en mi ubicación", {
            val here = vm.userLocation
            when {
                here == null -> vm.message = "Aún no tenemos tu ubicación. Comprueba el permiso y el GPS."
                !vm.userInCoverage() -> {
                    vm.message = "Estás fuera de la zona con datos. BlueWay cubre Málaga capital."
                    vm.cameraCommands.trySend(CameraCommand.FollowReset)
                }
                else -> vm.cameraCommands.trySend(CameraCommand.Center(here, 15.5))
            }
        })
        RoundIconButton(Icons.Rounded.Layers, "Capas del mapa", { vm.layersSheetOpen = true })
        RoundIconButton(Icons.Rounded.NearMe, "Ver toda la ruta", {
            val route = vm.selectedRoute()
            if (route != null) vm.cameraCommands.trySend(CameraCommand.Fit(route.points)) else vm.cameraCommands.trySend(CameraCommand.FollowReset)
        })
        RoundIconButton(Icons.Rounded.Campaign, "Enviar un aviso", {
            vm.openReportSheet()
        })
    }
}

@Composable
fun HomeOverlay(vm: AppViewModel) {
    val bw = LocalBw.current
    val route = vm.selectedRoute()
    Box(Modifier.fillMaxSize()) {
        DestinationCallout(vm, route)
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp).padding(top = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo_blueway), "BlueWay", modifier = Modifier.width(140.dp))
                Spacer(Modifier.weight(1f))
                val active = vm.activeReports().size
                BadgedBox(badge = { if (active > 0) Badge(containerColor = Color(0xFF0C46C3), contentColor = Color.White) { Text("$active") } }) {
                    RoundIconButton(Icons.Rounded.Notifications, "Avisos, $active activos", { vm.navigate(Screen.Alerts) })
                }
                Spacer(Modifier.width(10.dp))
                RoundIconButton(Icons.Rounded.Settings, "Ajustes y perfil", { vm.tab = Tab.PROFILE })
            }
            Spacer(Modifier.height(10.dp))
            SearchBar(vm)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RouteMode.entries.forEach { mode ->
                    ModeChip(mode.label, modeIcon(mode), vm.selectedMode == mode) {
                        vm.selectMode(mode)
                    }
                }
                ModeChip("Filtros", Icons.Rounded.Tune, false) { vm.navigate(Screen.Filters) }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter)) {
            MapFabs(vm, Modifier.align(Alignment.End).padding(end = 16.dp, bottom = 10.dp))
            RouteCard(vm, route)
            BottomTabs(vm)
        }
    }
}

@Composable
private fun DestinationCallout(vm: AppViewModel, route: RouteResult?) {
    val offset = destinationScreenOffset ?: return
    val bw = LocalBw.current
    if (route == null) return
    val density = LocalDensity.current
    val widthPx = with(density) { 170.dp.toPx() }
    val heightPx = with(density) { 118.dp.toPx() }
    Surface(
        onClick = { vm.destination?.let { vm.openZone(it.point, it.name) } },
        shape = RoundedCornerShape(16.dp),
        color = bw.surface,
        shadowElevation = 5.dp,
        modifier = Modifier
            .width(170.dp)
            .offset { IntOffset((offset.x - widthPx / 2).toInt().coerceAtLeast(8), (offset.y - heightPx).toInt().coerceAtLeast(8)) },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Destino", style = MaterialTheme.typography.labelMedium, color = bw.inkSoft)
                Text(
                    "${formatMinutes(route.seconds)} (${formatMeters(route.meters)})",
                    style = MaterialTheme.typography.bodySmall, color = bw.muted, maxLines = 1,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = bw.inkSoft)
        }
    }
}

@Composable
private fun RouteCard(vm: AppViewModel, route: RouteResult?) {
    val bw = LocalBw.current
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = bw.surface,
        shadowElevation = 7.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            when {
                vm.computing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = bw.primary, strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Calculando rutas…", color = bw.muted)
                }
                vm.routeError != null -> {
                    Text(vm.routeError!!, color = bw.ink, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { vm.navigate(Screen.Filters) }) { Text("Revisar filtros") }
                }
                route != null -> RouteCardContent(vm, route)
                else -> {
                    Text("¿A dónde vamos hoy?", style = MaterialTheme.typography.titleMedium, color = bw.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Busca un destino o mantén pulsado el mapa. Toca una calle para ver su ruido.",
                        style = MaterialTheme.typography.bodyMedium, color = bw.muted,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { vm.navigate(Screen.Legend) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Info, null, tint = bw.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "El ruido es estimado y los sensores son simulados. Ver leyenda",
                            style = MaterialTheme.typography.bodySmall, color = bw.inkSoft,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteCardContent(vm: AppViewModel, route: RouteResult) {
    val bw = LocalBw.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        val green = route.mode != RouteMode.FAST
        IconTile(modeIcon(route.mode), tint = Color.White, bg = if (green) bw.green else bw.primary, size = 50.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(route.mode.label, style = MaterialTheme.typography.titleLarge, color = bw.ink)
            Text(
                "${formatMinutes(route.seconds)} (${formatMeters(route.meters)})",
                style = MaterialTheme.typography.titleSmall, color = bw.green.takeIf { green } ?: bw.inkSoft,
            )
        }
        Surface(
            onClick = vm::startNavigation,
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            modifier = Modifier.height(50.dp),
        ) {
            Box(Modifier.background(bw.gradient).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                Row(Modifier.height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NearMe, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Iniciar", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(noiseSummary(route), style = MaterialTheme.typography.titleSmall, color = bw.green.takeIf { route.mode != RouteMode.FAST && (route.noiseReductionPct ?: 0) > 0 } ?: bw.muted)
    Spacer(Modifier.height(6.dp))
    NoiseBar(route.metersByLevel)
    Spacer(Modifier.height(4.dp))
    if (route.reportsOnRoute > 0) {
        Text(
            "${countOf(route.reportsOnRoute, "aviso", "avisos")} en esta ruta. Ver avisos",
            style = MaterialTheme.typography.bodySmall, color = bw.inkSoft, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(role = Role.Button) { vm.navigate(Screen.Alerts) }.padding(vertical = 3.dp),
        )
    }
    val origin = vm.effectiveOrigin
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Desde: ${origin?.name.orEmpty()}" + if (origin?.detail == "Salida de ejemplo") " (ejemplo)" else "",
            style = MaterialTheme.typography.bodySmall, color = bw.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { vm.navigate(Screen.Search(true)) }) { Text("Cambiar") }
    }
}

/** Pestaña Mapas: explorar el ruido por capas y por hora del día. */
@Composable
fun MapsOverlay(vm: AppViewModel) {
    val bw = LocalBw.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp).padding(top = 6.dp)) {
            Surface(shape = RoundedCornerShape(22.dp), color = bw.surface, shadowElevation = 5.dp) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("Mapa acústico de Málaga", style = MaterialTheme.typography.titleLarge, color = bw.ink)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NoiseLevel.entries.forEach { level ->
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(if (bw.highContrast) Color.White else level.soft).padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(level.color))
                                Spacer(Modifier.width(5.dp))
                                Text(level.label, style = MaterialTheme.typography.labelSmall, color = bw.inkSoft)
                            }
                        }
                    }
                    val shownHour = vm.previewHour ?: vm.currentHour
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (vm.previewHour == null) "Ahora (%02d:00) · Estimado".format(shownHour) else "A las %02d:00 · Estimado".format(shownHour),
                        style = MaterialTheme.typography.titleSmall, color = bw.inkSoft,
                    )
                    Slider(
                        value = shownHour.toFloat(),
                        onValueChange = { vm.previewHour = it.toInt() },
                        valueRange = 0f..23f,
                        steps = 22,
                        modifier = Modifier.semantics { contentDescription = "Hora del día para ver el ruido estimado" },
                    )
                    Text(
                        "El ruido se calcula por tipo de calle y hora. No es una medición.",
                        style = MaterialTheme.typography.bodySmall, color = bw.muted,
                    )
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                Surface(
                    onClick = { vm.navigate(Screen.Legend) },
                    shape = RoundedCornerShape(50),
                    color = bw.surface,
                    shadowElevation = 5.dp,
                    modifier = Modifier.height(52.dp),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, tint = bw.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Leyenda", style = MaterialTheme.typography.titleSmall, color = bw.inkSoft)
                    }
                }
                Spacer(Modifier.weight(1f))
                MapFabs(vm)
            }
            BottomTabs(vm)
        }
    }
}
