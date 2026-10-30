package com.blueway.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.TurnSlightLeft
import androidx.compose.material.icons.rounded.TurnSlightRight
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blueway.app.AppViewModel
import com.blueway.app.data.RouteMode
import com.blueway.app.data.StepKind

private fun stepIcon(kind: StepKind): ImageVector = when (kind) {
    StepKind.START, StepKind.STRAIGHT -> Icons.Rounded.ArrowUpward
    StepKind.LEFT -> Icons.Rounded.TurnLeft
    StepKind.RIGHT -> Icons.Rounded.TurnRight
    StepKind.SLIGHT_LEFT -> Icons.Rounded.TurnSlightLeft
    StepKind.SLIGHT_RIGHT -> Icons.Rounded.TurnSlightRight
    StepKind.ARRIVE -> Icons.Rounded.Place
}

@Composable
fun NavigationOverlay(vm: AppViewModel) {
    val bw = LocalBw.current
    val route = vm.navRoute ?: return
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val stepIndex = vm.currentStepIndex()
    val step = route.steps[vm.displayedStepIndex()]
    val remaining = (route.meters - vm.navProgress).coerceAtLeast(0.0)

    Box(Modifier.fillMaxSize()) {
        Surface(
            shape = RoundedCornerShape(26.dp), color = bw.surface, shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.Rounded.Close, "Terminar la ruta", { vm.back() }, size = 48.dp)
                Spacer(Modifier.width(8.dp))
                val calm = route.mode != RouteMode.FAST
                IconTile(modeIcon(route.mode), tint = Color.White, bg = if (calm) bw.green else bw.primary, size = 48.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(route.mode.label, style = MaterialTheme.typography.titleMedium, color = bw.ink)
                    Text("${formatMinutes(remaining / 1.25)} (${formatMeters(remaining)})", style = MaterialTheme.typography.bodyMedium, color = bw.muted)
                    Text(noiseSummary(route), style = MaterialTheme.typography.bodySmall, color = if (calm) bw.green else bw.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RoundIconButton(
                    if (vm.settings.voiceGuidance) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                    if (vm.settings.voiceGuidance) "Desactivar voz" else "Activar voz",
                    vm::toggleVoice, size = 48.dp,
                )
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            vm.navBanner?.let { report ->
                Surface(
                    onClick = { vm.selectedReport = report; vm.dismissBanner() },
                    shape = RoundedCornerShape(18.dp), color = bw.surface, shadowElevation = 5.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(report.type.icon), null, Modifier.size(44.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Aviso cercano: ${report.type.label}", style = MaterialTheme.typography.titleSmall, color = bw.inkSoft)
                            Text("Toca para ver el detalle", style = MaterialTheme.typography.bodySmall, color = bw.muted)
                        }
                    }
                }
            }
            if (vm.navSimulated) {
                Surface(
                    shape = RoundedCornerShape(50), color = Color(0xFF0C2A8F),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                ) {
                    Text(
                        "Trayecto simulado: la salida no es tu ubicación",
                        color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), color = bw.surface, shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)) {
                    if (vm.navArrived) {
                        ArrivedContent(vm)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(bw.gradient), contentAlignment = Alignment.Center) {
                                Icon(stepIcon(step.kind), null, tint = Color.White, modifier = Modifier.size(46.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(formatMeters(vm.metersToNextStep()), style = MaterialTheme.typography.headlineMedium, color = bw.ink)
                                Text(step.text, style = MaterialTheme.typography.titleMedium, color = bw.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Column(
                            Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            route.steps.forEachIndexed { i, s ->
                                if (i >= stepIndex) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (i == stepIndex) bw.tint else Color.Transparent)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(stepIcon(s.kind), null, tint = if (i == stepIndex) bw.primary else bw.muted, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(s.text, style = MaterialTheme.typography.bodyMedium, color = bw.muted, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (s.seconds > 0) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(formatMinutes(s.seconds), style = MaterialTheme.typography.labelMedium, color = bw.muted)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val reduction = route.noiseReductionPct
                            RouteStat(Icons.Rounded.Eco, "Menos ruido", if (reduction != null && reduction > 0) "-$reduction%" else "—", Modifier.weight(1f))
                            RouteStat(Icons.Rounded.Park, "Zonas verdes", "${route.greenAreas}", Modifier.weight(1f))
                            RouteStat(Icons.Rounded.Chair, "Bancos", "${route.benches}", Modifier.weight(1f))
                            RouteStat(Icons.Rounded.WbSunny, "Con sombra", "${route.shadeStretches}", Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteStat(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    val bw = LocalBw.current
    SoftCard(modifier, corner = 16.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = bw.primary, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = bw.muted, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleSmall, color = bw.inkSoft)
        }
    }
}

@Composable
private fun ArrivedContent(vm: AppViewModel) {
    val bw = LocalBw.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(bw.greenSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Flag, null, tint = bw.green, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("Has llegado", style = MaterialTheme.typography.headlineMedium, color = bw.ink)
        Text(vm.destination?.name.orEmpty(), color = bw.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        GradientButton("Terminar", {
            vm.back()
            vm.clearRoute()
        }, showChevron = false)
    }
}
