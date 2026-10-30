package com.blueway.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blueway.app.AppViewModel
import com.blueway.app.R
import com.blueway.app.Screen
import com.blueway.app.Tab
import com.blueway.app.data.DataSource
import com.blueway.app.data.Report
import com.blueway.app.data.ReportRules
import com.blueway.app.data.ReportStatus
import com.blueway.app.data.ReportType
import com.blueway.app.data.SharedReports
import com.blueway.app.engine.metersBetween

/** Pantalla "Avisos en tu ruta". */
@Composable
fun AlertsScreen(vm: AppViewModel) {
    val bw = LocalBw.current
    val route = vm.selectedRoute()
    val items = vm.alertsWithDistance()
    val subtitle = if (route != null) {
        "Te informamos de posibles incidencias para que elijas la mejor opción."
    } else {
        "Elige un destino para ver cuáles afectan a tu ruta."
    }
    Box(Modifier.fillMaxSize()) {
        WaveBackground()
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(if (route != null) "Avisos en tu ruta" else "Avisos cerca", subtitle, { vm.back() })
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val alternative = vm.routes.firstOrNull { it.mode == vm.selectedMode }?.takeIf { it.reportsOnRoute > 0 }
                    ?.let { current -> vm.routes.filter { it.reportsOnRoute < current.reportsOnRoute }.minByOrNull { it.noiseLoad } }
                if (alternative != null) {
                    item {
                        AlertCard(
                            image = null, bg = bw.greenSoft,
                            title = "Ruta alternativa disponible",
                            body = "${alternative.mode.label} tiene menos avisos en el camino.",
                            footer = "Toca para elegirla",
                            onClick = {
                                vm.selectMode(alternative.mode)
                                vm.back()
                            },
                        )
                    }
                }
                val active = items.filter { it.first.isActive() }
                val onRoute = active.filter { vm.isReportOnRoute(it.first) }
                val others = active.filter { !vm.isReportOnRoute(it.first) }
                val past = items.filter { !it.first.isActive() }
                if (route != null && onRoute.isEmpty()) {
                    item {
                        Text("No hay avisos activos en esta ruta. Eso no garantiza que no haya ruido.", color = bw.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(onRoute, key = { "r" + it.first.id }) { ReportCard(vm, it.first, it.second) }
                if (others.isNotEmpty()) {
                    item { SectionTitle(if (route != null) "Fuera de tu ruta" else "Activos") }
                    items(others, key = { "o" + it.first.id }) { ReportCard(vm, it.first, it.second) }
                }
                if (past.isNotEmpty()) {
                    item { SectionTitle("Caducados o descartados") }
                    items(past, key = { "p" + it.first.id }) { ReportCard(vm, it.first, it.second) }
                }
                if (SharedReports.enabled) {
                    item {
                        Text(
                            when {
                                vm.syncFailed -> "Sin conexión. Mostramos los últimos avisos conocidos."
                                vm.lastSync != null -> "Avisos compartidos entre personas usuarias. Actualizado: ${timeAgo(vm.lastSync!!).lowercase()}."
                                else -> "Actualizando avisos…"
                            },
                            style = MaterialTheme.typography.bodySmall, color = bw.muted,
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(6.dp))
                    GradientButton("Enviar un aviso", { vm.openReportSheet() }, showChevron = false)
                }
            }
        }
    }
}

private fun cardColor(type: ReportType): Color = when (type) {
    ReportType.WORKS, ReportType.NOISE_EXTREME -> Color(0xFFFFEDEF)
    ReportType.EVENT, ReportType.TRAFFIC, ReportType.NOISE_HIGH, ReportType.NOISE_MODERATE -> Color(0xFFFFF6E5)
    ReportType.NOISE_LOW -> Color(0xFFE9F9F1)
    else -> Color(0xFFEDF5FE)
}

@Composable
private fun ReportCard(vm: AppViewModel, report: Report, distance: Int?) {
    val where = distance?.let { if (it >= 1000) " a ${formatMeters(it.toDouble())}" else " a ${(it / 10) * 10} m" }.orEmpty()
    AlertCard(
        image = report.type.icon,
        bg = cardColor(report.type),
        title = report.type.label + where,
        body = report.description.ifEmpty { report.type.shortDescription },
        footer = buildString {
            append(timeAgo(report.createdAt))
            append(" · ")
            append(report.status().label)
            if (report.isSample) append(" · Ejemplo")
        },
        onClick = { vm.selectedReport = report },
    )
}

@Composable
private fun AlertCard(image: Int?, bg: Color, title: String, body: String, footer: String, onClick: () -> Unit) {
    val bw = LocalBw.current
    SoftCard(Modifier.fillMaxWidth(), color = bg, onClick = onClick, corner = 24.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (image != null) {
                Image(painterResource(image), null, Modifier.size(72.dp))
            } else {
                IconTile(Icons.Rounded.Eco, tint = Color.White, bg = bw.green, size = 72.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = bw.inkSoft)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = bw.muted)
                Spacer(Modifier.height(2.dp))
                Text(footer, style = MaterialTheme.typography.bodySmall, color = bw.muted)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = bw.muted)
        }
    }
}

/** Hojas inferiores que pueden abrirse desde cualquier pantalla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSheets(vm: AppViewModel) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bw = LocalBw.current
    if (vm.reportSheetOpen) {
        ModalBottomSheet(onDismissRequest = { vm.closeReportSheet() }, sheetState = state, containerColor = bw.surface) {
            val type = vm.reportDraftType
            if (type == null) ReportTypeGrid(vm) else ReportForm(vm, type)
        }
    }
    vm.selectedReport?.let { report ->
        ModalBottomSheet(onDismissRequest = { vm.selectedReport = null }, sheetState = state, containerColor = bw.surface) {
            ReportDetail(vm, report)
        }
    }
    vm.pressedPoint?.let { point ->
        ModalBottomSheet(onDismissRequest = { vm.pressedPoint = null }, containerColor = bw.surface) {
            val place = vm.placeFromMap(point)
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(place.name, style = MaterialTheme.typography.titleLarge, color = bw.ink)
                GradientButton("Ir aquí", {
                    vm.pressedPoint = null
                    vm.tab = Tab.HOME
                    vm.chooseDestination(place)
                }, showChevron = false)
                OutlinedButton(onClick = {
                    vm.pressedPoint = null
                    vm.openZone(point)
                }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Ver el ruido de esta zona") }
                OutlinedButton(onClick = {
                    vm.pressedPoint = null
                    vm.openReportSheet(point)
                }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Enviar un aviso aquí") }
            }
        }
    }
    if (vm.layersSheetOpen) {
        ModalBottomSheet(onDismissRequest = { vm.layersSheetOpen = false }, containerColor = bw.surface) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Capas del mapa", style = MaterialTheme.typography.titleLarge, color = bw.ink)
                LayerRow("Ruido estimado por calle", vm.layers.noise) { vm.layers = vm.layers.copy(noise = it) }
                LayerRow("Sensores simulados", vm.layers.sensors) { vm.layers = vm.layers.copy(sensors = it) }
                LayerRow("Avisos de personas usuarias", vm.layers.reports) { vm.layers = vm.layers.copy(reports = it) }
                LayerRow("Zonas verdes", vm.layers.green) { vm.layers = vm.layers.copy(green = it) }
                TextButton(onClick = {
                    vm.layersSheetOpen = false
                    vm.navigate(Screen.Legend)
                }) { Text("Ver la leyenda de ruido") }
            }
        }
    }
}

@Composable
private fun LayerRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LocalBw.current.inkSoft, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ReportTypeGrid(vm: AppViewModel) {
    val bw = LocalBw.current
    Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
        Text("¿Qué quieres avisar?", style = MaterialTheme.typography.titleLarge, color = bw.ink)
        Text("No necesitas cuenta. Solo guardamos una ubicación aproximada.", style = MaterialTheme.typography.bodyMedium, color = bw.muted)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportType.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { type ->
                        Surface(
                            onClick = { vm.reportDraftType = type },
                            shape = RoundedCornerShape(18.dp),
                            color = bw.tint,
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(painterResource(type.icon), null, Modifier.size(62.dp))
                                Spacer(Modifier.height(4.dp))
                                Text(type.label, style = MaterialTheme.typography.labelMedium, color = bw.inkSoft, textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportForm(vm: AppViewModel, type: ReportType) {
    val bw = LocalBw.current
    var text by remember { mutableStateOf("") }
    val point = vm.reportDraftPoint
    Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(type.icon), null, Modifier.size(64.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(type.label, style = MaterialTheme.typography.titleLarge, color = bw.ink)
                Text(type.shortDescription, style = MaterialTheme.typography.bodyMedium, color = bw.muted)
            }
            TextButton(onClick = { vm.reportDraftType = null }) { Text("Cambiar") }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Place, null, tint = bw.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ubicación", style = MaterialTheme.typography.titleSmall, color = bw.inkSoft)
                    Text(
                        point?.let { vm.placeFromMap(it).name } ?: "Elige un punto en el mapa",
                        style = MaterialTheme.typography.bodySmall, color = bw.muted,
                    )
                }
                TextButton(onClick = vm::startPickingLocation) { Text(if (point == null) "Elegir" else "Mover") }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(ReportRules.MAX_DESCRIPTION) },
            label = { Text("Descripción breve (opcional)") },
            supportingText = { Text("${text.length}/${ReportRules.MAX_DESCRIPTION}. No incluyas datos personales.") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
        Text(
            "Caduca en ${if (type.expiresHours >= 24) "${type.expiresHours / 24} días" else "${type.expiresHours} h"}. " +
                if (SharedReports.enabled) "Lo verán otras personas, sin saber quién lo envió." else "Por ahora se guarda solo en este teléfono.",
            style = MaterialTheme.typography.bodySmall, color = bw.muted,
        )
        GradientButton("Enviar aviso", {
            if (point == null) vm.startPickingLocation() else vm.submitReport(type, point, text)
        }, showChevron = false)
    }
}

/** Selector de ubicación: el mapa queda libre con un pin fijo en el centro. */
@Composable
fun PickLocationOverlay(vm: AppViewModel) {
    val bw = LocalBw.current
    BackHandler(onBack = vm::cancelPickingLocation)
    Box(Modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.ic_marker_pin), null,
            Modifier.align(Alignment.Center).padding(bottom = 60.dp).size(48.dp, 60.dp),
        )
        Surface(
            shape = RoundedCornerShape(20.dp), color = bw.surface, shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp).fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Mueve el mapa hasta el lugar del aviso", style = MaterialTheme.typography.titleSmall, color = bw.inkSoft, modifier = Modifier.weight(1f))
                RoundIconButton(Icons.Rounded.Close, "Cancelar", vm::cancelPickingLocation, size = 46.dp)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp)) {
            GradientButton("Confirmar ubicación", vm::confirmPickedLocation, showChevron = false)
        }
    }
}

@Composable
private fun ReportDetail(vm: AppViewModel, report: Report) {
    val bw = LocalBw.current
    val status = report.status()
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 22.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(report.type.icon), null, Modifier.size(70.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(report.type.label, style = MaterialTheme.typography.titleLarge, color = bw.ink)
                Text(timeAgo(report.createdAt), style = MaterialTheme.typography.bodyMedium, color = bw.muted)
            }
        }
        Text(report.description.ifEmpty { report.type.shortDescription }, color = bw.inkSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceTag(DataSource.REPORTED)
            Pill(
                status.label, bw.ink,
                when (status) {
                    ReportStatus.CONFIRMED -> bw.greenSoft
                    else -> bw.tint
                },
            )
        }
        if (report.isSample) {
            Text("Aviso de ejemplo para probar la app.", style = MaterialTheme.typography.bodySmall, color = bw.muted)
        }
        Text("${countOf(report.confirmations, "confirmación", "confirmaciones")} · ${countOf(report.rejections, "rechazo", "rechazos")}", style = MaterialTheme.typography.bodySmall, color = bw.muted)
        val distance = vm.userLocation?.takeIf { vm.userInCoverage() }?.let { metersBetween(it, report.point) }
        distance?.let { Text("A ${formatMeters(it)} de ti", style = MaterialTheme.typography.bodySmall, color = bw.muted) }
        if (report.isActive()) {
            if (vm.canVote(report)) {
                Spacer(Modifier.height(4.dp))
                Text("¿Sigue ahí?", style = MaterialTheme.typography.titleMedium, color = bw.inkSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { vm.voteReport(report, true) }, Modifier.weight(1f).height(50.dp)) { Text("Sí, sigue") }
                    OutlinedButton(onClick = { vm.voteReport(report, false) }, Modifier.weight(1f).height(50.dp)) { Text("Ya no está") }
                }
            } else if (!report.mine) {
                Text("Ya has valorado este aviso. Gracias.", style = MaterialTheme.typography.bodySmall, color = bw.muted)
            }
        }
        OutlinedButton(onClick = { vm.showOnMap(report.point) }, Modifier.fillMaxWidth().height(50.dp)) { Text("Ver en el mapa") }
        if (report.mine) {
            OutlinedButton(onClick = { vm.removeReport(report) }, Modifier.fillMaxWidth().height(50.dp)) { Text("Eliminar mi aviso") }
        }
    }
}
