package com.example.tomtomdemo

import androidx.annotation.IdRes
import androidx.fragment.app.FragmentActivity
import com.tomtom.sdk.datamanagement.navigationtile.NavigationTileStore
import com.tomtom.sdk.datamanagement.navigationtile.NavigationTileStoreConfiguration
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.simulation.SimulationLocationProvider
import com.tomtom.sdk.location.simulation.strategy.InterpolationStrategy
import com.tomtom.sdk.navigation.ActiveRouteChangedListener
import com.tomtom.sdk.navigation.ProgressUpdatedListener
import com.tomtom.sdk.navigation.RouteAddedListener
import com.tomtom.sdk.navigation.RoutePlan
import com.tomtom.sdk.navigation.RouteRemovedListener
import com.tomtom.sdk.navigation.TomTomNavigation
import com.tomtom.sdk.navigation.UnitSystemType
import com.tomtom.sdk.navigation.online.Configuration
import com.tomtom.sdk.navigation.online.OnlineTomTomNavigationFactory
import com.tomtom.sdk.navigation.ui.NavigationFragment
import com.tomtom.sdk.navigation.ui.NavigationUiOptions
import com.tomtom.sdk.routing.RoutePlanner
import com.tomtom.sdk.routing.options.RoutePlanningOptions
import com.tomtom.sdk.routing.route.Route
import java.util.Locale

/**
 * Created by Chen Wei on 2024/6/28.
 */
class NavigationManager(
    private val context: FragmentActivity,
    private val locationProvider: LocationProvider,
    private val routePlanner: RoutePlanner,
    val route: Route,
    val navigationStarted: () -> Unit,
    private val navigationStopped: () -> Unit
) {

    private val navigationTileStore: NavigationTileStore by lazy {
        NavigationTileStore.create(
            context = context,
            navigationTileStoreConfig = NavigationTileStoreConfiguration(
                apiKey = BuildConfig.TOMTOM_API_KEY
            )
        )
    }
    val tomTomNavigation: TomTomNavigation by lazy {
        OnlineTomTomNavigationFactory.create(
            Configuration(
                context = context,
                navigationTileStore = navigationTileStore,
                locationProvider = locationProvider,
                routePlanner = routePlanner
            )
        )
    }

    private val navigationFragment: NavigationFragment by lazy {
        NavigationFragment.newInstance(
            NavigationUiOptions(
                keepInBackground = true,
                isSoundEnabled = true,
                voiceLanguage = Locale.getDefault(),
                unitSystemType = UnitSystemType.default
            )
        )
    }

    private val navigationListener =
        object : NavigationFragment.NavigationListener {
            override fun onStarted() {
                navigationStarted.invoke()
                setSimulationLocationProviderToNavigation(route)
            }

            override fun onStopped() {
                stopNavigation()
            }
        }

    fun startNavigation(
        route: Route, routePlanningOptions: RoutePlanningOptions,
    ) {
        navigationFragment.setTomTomNavigation(tomTomNavigation)
        navigationFragment.startNavigation(RoutePlan(route, routePlanningOptions))
        navigationFragment.addNavigationListener(navigationListener)
    }

    private fun stopNavigation() {
        navigationFragment.stopNavigation()
        navigationFragment.removeNavigationListener(navigationListener)
        navigationStopped.invoke()
    }

    fun configListeners(
        progressUpdatedListener: ProgressUpdatedListener,
        routeAddedListener: RouteAddedListener,
        routeRemovedListener: RouteRemovedListener,
        activeRouteChangedListener: ActiveRouteChangedListener,
        add: Boolean
    ) {
        if (add) {
            tomTomNavigation.addProgressUpdatedListener(progressUpdatedListener)
            tomTomNavigation.addRouteAddedListener(routeAddedListener)
            tomTomNavigation.addRouteRemovedListener(routeRemovedListener)
            tomTomNavigation.addActiveRouteChangedListener(activeRouteChangedListener)
        } else {
            tomTomNavigation.removeProgressUpdatedListener(progressUpdatedListener)
            tomTomNavigation.removeActiveRouteChangedListener(activeRouteChangedListener)
            tomTomNavigation.removeRouteAddedListener(routeAddedListener)
            tomTomNavigation.removeRouteRemovedListener(routeRemovedListener)
        }
    }

    private fun setSimulationLocationProviderToNavigation(route: Route) {
        val routeGeoLocations = route.geometry.map { GeoLocation(it) }
        val simulationStrategy = InterpolationStrategy(routeGeoLocations)
        val oldLocationProvider = tomTomNavigation.locationProvider
        val simulationLocationProvider = SimulationLocationProvider.create(strategy = simulationStrategy)
        tomTomNavigation.locationProvider = simulationLocationProvider
        oldLocationProvider.close()
        simulationLocationProvider.enable()
    }

    fun isNavigationRunning(): Boolean = tomTomNavigation.navigationSnapshot != null

    fun hideOrShowSpeedView(show: Boolean) {
        if (show) {
            navigationFragment.navigationView.showSpeedView()
        } else {
            navigationFragment.navigationView.hideSpeedView()
        }
    }

    fun showNavigation(@IdRes layoutId: Int) {
        context.showFragmentSync(navigationFragment, layoutId)
    }

    fun clean() {
        tomTomNavigation.close()
        navigationTileStore.close()
    }
}