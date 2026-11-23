package dev.ticker.converter.ui.nav

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import dev.ticker.converter.ui.trips.TripRoute
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.ticker.converter.AppContainer
import dev.ticker.converter.ui.components.BottomBar
import dev.ticker.converter.ui.components.Tab
import dev.ticker.converter.ui.convert.ConvertRoute
import dev.ticker.converter.ui.charts.ChartsRoute
import dev.ticker.converter.ui.camera.CameraRoute
import androidx.compose.ui.graphics.Color
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.SystemBarsFor
import dev.ticker.converter.ui.theme.TickerTheme
import dev.ticker.converter.ui.theme.dotGrid

/** A request to show [tab]; [seq] changes on every intent so asking for the same tab twice still navigates. */
data class LaunchRequest(val tab: Tab, val seq: Int)

@Composable
fun TickerNavHost(container: AppContainer, appDark: Boolean, activity: ComponentActivity, launchTab: LaunchRequest? = null) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = Tab.entries.firstOrNull { it.route == entry?.destination?.route } ?: Tab.CONVERT
    val select: (Tab) -> Unit = { tab ->
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    LaunchedEffect(launchTab) { launchTab?.let { select(it.tab) } }
    // Camera controls sit on the live picture, so the Camera tab (dock included) is always dark.
    val dark = appDark || current == Tab.CAMERA
    SystemBarsFor(activity, dark)
    TickerTheme(dark) {
        // The dot grid sits behind every tab and the dock; screens draw on a transparent background.
        Box(Modifier.fillMaxSize().dotGrid()) {
            Scaffold(
                containerColor = Color.Transparent,
                // A transparent container has no matching content colour, so name the text colour explicitly.
                contentColor = TickerColors.Text,
                // Each screen applies its own status-bar inset (Camera draws under it); the bottom bar pads itself.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = { BottomBar(current, select) },
            ) { padding ->
                NavHost(nav, startDestination = Tab.CONVERT.route, modifier = Modifier.padding(padding)) {
                    composable(Tab.CONVERT.route) { ConvertRoute(container, onOpenTrip = { nav.navigate("trip/$it") }) }
                    // Placeholders until the trip screens arrive.
                    composable("trip/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { back ->
                        TripRoute(
                            container, back.arguments?.getString("id").orEmpty(),
                            onBack = { nav.popBackStack() }, onOpenTrips = { nav.navigate("trips") },
                        )
                    }
                    composable("trips") { Box(Modifier.fillMaxSize()) }
                    composable(Tab.CHARTS.route) { ChartsRoute(container) }
                    composable(Tab.CAMERA.route) { CameraRoute(container, onOpenConvert = { select(Tab.CONVERT) }) }
                }
            }
        }
    }
}
