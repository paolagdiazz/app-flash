package com.blueway.app.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import com.blueway.app.AppViewModel
import com.blueway.app.CameraCommand
import com.blueway.app.R
import com.blueway.app.Screen
import com.blueway.app.Tab
import com.blueway.app.data.ReportRules
import com.blueway.app.engine.metersBetween
import com.blueway.app.data.LatLon
import kotlinx.coroutines.delay

@Composable
fun BlueWayRoot(vm: AppViewModel) {
    BlueWayTheme(vm.settings.highContrast, vm.settings.reduceMotion) {
        val screen = vm.backStack.last()
        BackHandler(enabled = vm.backStack.size > 1 || (screen == Screen.Main && vm.tab != Tab.HOME)) {
            if (vm.backStack.size > 1) vm.back() else vm.tab = Tab.HOME
        }
        val bw = LocalBw.current
        Box(Modifier.fillMaxSize().background(bw.background)) {
            if (vm.city == null && screen != Screen.Onboarding) {
                LoadingCity(vm.loadError)
            } else {
                if (screen != Screen.Onboarding) MapHost(vm)
                when (screen) {
                    Screen.Onboarding -> OnboardingScreen(vm::finishOnboarding)
                    Screen.Main -> MainOverlay(vm)
                    is Screen.Search -> SearchScreen(vm, screen.forOrigin, screen.voice)
                    Screen.Filters -> FiltersScreen(vm)
                    Screen.Alerts -> AlertsScreen(vm)
                    Screen.Legend -> LegendScreen(vm)
                    is Screen.ZoneDetail -> ZoneScreen(vm, screen.zone)
                    Screen.Navigation -> NavigationOverlay(vm)
                    Screen.NotificationSettings -> NotificationSettingsScreen(vm)
                    Screen.Help -> HelpScreen(vm)
                    Screen.About -> AboutScreen(vm)
                }
            }
            if (vm.pickingReportLocation) PickLocationOverlay(vm)
            GlobalSheets(vm)
            MessageSnack(vm)
        }
    }
}

@Composable
private fun LoadingCity(error: String?) {
    val bw = LocalBw.current
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.logo_blueway), "BlueWay", modifier = Modifier.width(220.dp))
        Spacer(Modifier.height(24.dp))
        if (error == null) {
            CircularProgressIndicator(color = bw.primary)
            Spacer(Modifier.height(12.dp))
            Text("Preparando el mapa de Málaga…", color = bw.muted)
        } else {
            Text(error, color = bw.ink, textAlign = TextAlign.Center)
        }
    }
}

/** Mensajes breves y discretos, sin sonido ni vibración. */
@Composable
private fun MessageSnack(vm: AppViewModel) {
    val text = vm.message ?: return
    LaunchedEffect(text) {
        delay(4500)
        vm.message = null
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.TopCenter) {
        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF0C2A8F), shadowElevation = 6.dp, modifier = Modifier.padding(top = 40.dp)) {
            Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}

var destinationScreenOffset by mutableStateOf<Offset?>(null)

/** Un único mapa detrás de todas las pantallas, para no recargarlo al cambiar de pestaña. */
@Composable
private fun MapHost(vm: AppViewModel) {
    val city = vm.city ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) vm.startLocation()
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            vm.startLocation()
        } else {
            permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    val navigating = vm.navRoute != null
    val selected = vm.navRoute ?: vm.selectedRoute()
    val origin = vm.effectiveOrigin?.point
    val hour = vm.previewHour ?: vm.currentHour
    val quietZones = remember(city) {
        city.parks.filter { metersBetween(it.point, LatLon(36.7203, -4.4195)) < 3500 }.take(20)
    }
    val content = MapContent(
        routes = if (navigating) emptyList() else vm.routes,
        selected = selected,
        destination = vm.destination?.point,
        user = vm.navPosition ?: (if (vm.routes.isNotEmpty()) origin else vm.userLocation) ?: vm.userLocation,
        reports = if (vm.layers.reports) vm.activeReports() else emptyList(),
        sensors = if (vm.layers.sensors) ReportRules.SENSORS else emptyList(),
        quietZones = if (vm.layers.green) quietZones else emptyList(),
        showNoise = vm.layers.noise,
        noiseOffset = city.noise.hourOffset(hour),
        navigating = navigating,
    )
    LaunchedEffect(vm.navPosition) {
        vm.navPosition?.let { vm.cameraCommands.trySend(CameraCommand.Center(it, 16.2)) }
    }
    BlueWayMap(
        content = content,
        camera = vm.cameraFlow,
        reduceMotion = vm.settings.reduceMotion,
        contentPaddingBottomPx = with(density) { 330.dp.roundToPx() },
        contentPaddingTopPx = with(density) { 270.dp.roundToPx() },
        contentPaddingSidePx = with(density) { 96.dp.roundToPx() },
        onMarker = { tap ->
            when (tap) {
                is MapMarkerTap.ReportTap -> vm.selectedReport = vm.reports.firstOrNull { it.id == tap.id }
                is MapMarkerTap.SensorTap -> ReportRules.SENSORS.firstOrNull { it.id == tap.id }?.let { vm.openZone(it.point, it.name) }
                is MapMarkerTap.ZoneTap -> quietZones.getOrNull(tap.index)?.let { vm.openZone(it.point, it.name) }
            }
        },
        onStreetTap = { if (!navigating && !vm.pickingReportLocation) vm.openZone(it) },
        onLongPress = { if (!navigating && !vm.pickingReportLocation) vm.pressedPoint = it },
        onCameraIdle = { vm.mapCenter = it },
        onDestinationMoved = { destinationScreenOffset = it },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
fun BottomTabs(vm: AppViewModel, modifier: Modifier = Modifier) {
    val bw = LocalBw.current
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = bw.surface,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TabItem(vm, Tab.HOME, Icons.Rounded.Home, Modifier.weight(1f))
            TabItem(vm, Tab.ROUTES, Icons.Rounded.Route, Modifier.weight(1f))
            TabItem(vm, Tab.MAPS, Icons.Rounded.Map, Modifier.weight(1f))
            TabItem(vm, Tab.FAVORITES, Icons.Rounded.Favorite, Modifier.weight(1f))
            TabItem(vm, Tab.PROFILE, Icons.Rounded.Person, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TabItem(vm: AppViewModel, tab: Tab, icon: ImageVector, modifier: Modifier) {
    val bw = LocalBw.current
    val active = vm.tab == tab
    val color = if (active) bw.primary else bw.muted
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (active) bw.tint else Color.Transparent)
            .clickable(role = Role.Tab) { vm.tab = tab }
            .semantics { selected = active }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
        Text(tab.label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MainOverlay(vm: AppViewModel) {
    when (vm.tab) {
        Tab.HOME -> HomeOverlay(vm)
        Tab.MAPS -> MapsOverlay(vm)
        Tab.ROUTES -> RoutesTab(vm)
        Tab.FAVORITES -> FavoritesTab(vm)
        Tab.PROFILE -> ProfileTab(vm)
    }
}
