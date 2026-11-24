package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.TripRepository
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MapScreen
import com.example.ui.screens.SharedTripScreen
import com.example.ui.screens.TripWorkspaceScreen
import com.example.ui.theme.AmberBright
import com.example.ui.theme.AmberMuted
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyMid
import com.example.ui.theme.WayReelTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.TripsListUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object MapRoute

@Serializable
data object PlanRoute

@Serializable
data class TripWorkspaceRoute(val tripId: String, val initialTab: Int = 1)

@Serializable
data object SharedTripRoute

private data class TopLevelNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any,
    val tag: String
)

class MainActivity : ComponentActivity() {

    private var incomingSharedText by mutableStateOf<String?>(null)
    private var incomingSharedTripUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // CRITICAL: Configure App Check with launch intent before setContent, ViewModels, or Firebase ops
        configureAppCheck(this, intent)

        handleIncomingIntent(intent)
        enableEdgeToEdge()

        setContent {
            WayReelTheme {
                AppNavigation(
                    incomingSharedText = incomingSharedText,
                    incomingSharedTripUrl = incomingSharedTripUrl,
                    onConsumedSharedText = { incomingSharedText = null },
                    onConsumedSharedTripUrl = { incomingSharedTripUrl = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    if (text.contains("wayreel://trip") || text.contains("reelroute://trip")) {
                        incomingSharedTripUrl = text
                    } else {
                        incomingSharedText = text
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                val data = intent.dataString
                if (!data.isNullOrBlank() && (data.startsWith("wayreel://trip") || data.startsWith("reelroute://trip"))) {
                    incomingSharedTripUrl = data
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    incomingSharedText: String?,
    incomingSharedTripUrl: String?,
    onConsumedSharedText: () -> Unit,
    onConsumedSharedTripUrl: () -> Unit
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            val viewModel: MainViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        MainViewModel(
                            repository = TripRepository(db),
                            currentUserId = user.uid
                        )
                    }
                }
            )

            AuthenticatedNavHost(
                viewModel = viewModel,
                incomingSharedText = incomingSharedText,
                incomingSharedTripUrl = incomingSharedTripUrl,
                onConsumedSharedText = onConsumedSharedText,
                onConsumedSharedTripUrl = onConsumedSharedTripUrl
            )
        }
    }
}

@Composable
private fun AuthenticatedNavHost(
    viewModel: MainViewModel,
    incomingSharedText: String?,
    incomingSharedTripUrl: String?,
    onConsumedSharedText: () -> Unit,
    onConsumedSharedTripUrl: () -> Unit
) {
    val navController = rememberNavController()
    val sharedPreview by viewModel.sharedPreview.collectAsStateWithLifecycle()
    val activeTrip by viewModel.activeTrip.collectAsStateWithLifecycle()
    val tripsState by viewModel.tripsState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    LaunchedEffect(incomingSharedTripUrl) {
        if (!incomingSharedTripUrl.isNullOrBlank()) {
            if (viewModel.openSharedLink(incomingSharedTripUrl)) {
                navController.navigate(SharedTripRoute)
            }
            onConsumedSharedTripUrl()
        }
    }

    val topLevelItems = listOf(
        TopLevelNavItem(
            label = "Reels",
            selectedIcon = Icons.Filled.AutoAwesome,
            unselectedIcon = Icons.Outlined.AutoAwesome,
            route = HomeRoute,
            tag = "nav_reels"
        ),
        TopLevelNavItem(
            label = "Map",
            selectedIcon = Icons.Filled.Map,
            unselectedIcon = Icons.Outlined.Map,
            route = MapRoute,
            tag = "nav_map"
        ),
        TopLevelNavItem(
            label = "Itinerary",
            selectedIcon = Icons.Filled.Route,
            unselectedIcon = Icons.Outlined.Route,
            route = PlanRoute,
            tag = "nav_itinerary"
        )
    )

    val showTopLevelChrome = currentDest == null ||
        currentDest.hasRoute<HomeRoute>() ||
        currentDest.hasRoute<MapRoute>() ||
        currentDest.hasRoute<PlanRoute>()

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = NavyDeep,
            contentWindowInsets = WindowInsets.navigationBars,
            bottomBar = {
                if (showTopLevelChrome && !useRail) {
                    NavigationBar(
                        containerColor = NavyMid,
                        contentColor = TextPrimary,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        topLevelItems.forEach { item ->
                            val selected = when (item.route) {
                                HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                MapRoute -> currentDest?.hasRoute<MapRoute>() == true
                                PlanRoute -> currentDest?.hasRoute<PlanRoute>() == true
                                else -> false
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.tag),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AmberBright,
                                    selectedTextColor = AmberBright,
                                    indicatorColor = AmberMuted,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                if (showTopLevelChrome && useRail) {
                    NavigationRail(
                        containerColor = NavyMid,
                        contentColor = TextPrimary
                    ) {
                        topLevelItems.forEach { item ->
                            val selected = when (item.route) {
                                HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                MapRoute -> currentDest?.hasRoute<MapRoute>() == true
                                PlanRoute -> currentDest?.hasRoute<PlanRoute>() == true
                                else -> false
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.tag),
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = AmberBright,
                                    selectedTextColor = AmberBright,
                                    indicatorColor = AmberMuted,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute
                    ) {
                        composable<HomeRoute> {
                            HomeScreen(
                                viewModel = viewModel,
                                incomingSharedText = incomingSharedText,
                                onConsumedIncomingText = onConsumedSharedText,
                                onOpenTrip = { tripId ->
                                    viewModel.openTrip(tripId)
                                    navigateTopLevel(MapRoute)
                                },
                                onOpenSharedPreview = {
                                    navController.navigate(SharedTripRoute)
                                }
                            )
                        }

                        composable<MapRoute> {
                            MapScreen(
                                viewModel = viewModel,
                                onGoToExtract = { navigateTopLevel(HomeRoute) },
                                onOpenTripWorkspace = { tripId, initialTab ->
                                    viewModel.openTrip(tripId)
                                    navController.navigate(TripWorkspaceRoute(tripId, initialTab))
                                }
                            )
                        }

                        composable<PlanRoute> {
                            LaunchedEffect(activeTrip, tripsState) {
                                if (activeTrip == null) {
                                    val firstTrip = (tripsState as? TripsListUiState.Success)?.trips?.firstOrNull()
                                    if (firstTrip != null) {
                                        viewModel.openTrip(firstTrip.id)
                                    }
                                }
                            }
                            TripWorkspaceScreen(
                                viewModel = viewModel,
                                initialTab = 2,
                                showBackButton = false,
                                onBack = { navigateTopLevel(HomeRoute) }
                            )
                        }

                        composable<TripWorkspaceRoute> { backStack ->
                            val args = backStack.toRoute<TripWorkspaceRoute>()
                            DisposableEffect(args.tripId) {
                                if (args.tripId.isNotBlank()) {
                                    viewModel.openTrip(args.tripId)
                                }
                                onDispose {}
                            }
                            TripWorkspaceScreen(
                                viewModel = viewModel,
                                initialTab = args.initialTab,
                                showBackButton = true,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<SharedTripRoute> {
                            val snap = sharedPreview
                            if (snap != null) {
                                SharedTripScreen(
                                    snapshot = snap,
                                    onBack = {
                                        viewModel.closeSharedPreview()
                                        navController.popBackStack()
                                    },
                                    onSaveCopy = {
                                        viewModel.saveSharedSnapshotToMyTrips(snap) { newId ->
                                            viewModel.openTrip(newId)
                                            navController.navigate(MapRoute) {
                                                popUpTo<HomeRoute>()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
