package com.example.tomtomdemo

import android.os.Bundle

import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import com.tomtom.sdk.datamanagement.navigationtile.NavigationTileStore
import com.tomtom.sdk.datamanagement.navigationtile.NavigationTileStoreConfiguration
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.mapmatched.MapMatchedLocationProvider
import com.tomtom.sdk.location.simulation.SimulationLocationProvider
import com.tomtom.sdk.location.simulation.strategy.InterpolationStrategy
import com.tomtom.sdk.navigation.ActiveRouteChangedListener
import com.tomtom.sdk.navigation.ProgressUpdatedListener
import com.tomtom.sdk.navigation.RouteAddedListener
import com.tomtom.sdk.navigation.RoutePlan
import com.tomtom.sdk.navigation.RouteRemovedListener
import com.tomtom.sdk.navigation.TomTomNavigation
import com.tomtom.sdk.navigation.online.Configuration
import com.tomtom.sdk.navigation.online.OnlineTomTomNavigationFactory
import com.tomtom.sdk.navigation.ui.NavigationFragment
import com.tomtom.sdk.navigation.ui.NavigationUiOptions
import com.tomtom.sdk.routing.RoutePlanner
import com.tomtom.sdk.routing.online.OnlineRoutePlanner
import com.tomtom.sdk.routing.options.RoutePlanningOptions
import com.tomtom.sdk.routing.route.Route
import java.util.Locale

/**
 * Created by Chen Wei on 2024/6/28.
 */
class SimpleNavigationFragment : NavigationFragment() {

    companion object {
        /**
         * The key used to pass [NavigationUiOptions] to [NavigationFragment] using [Bundle].
         */
        const val KEY_NAVIGATION_OPTIONS = "NAVIGATION_UI_OPTIONS"

        /**
         * Creates an instance of [SimpleNavigationFragment] with provided [NavigationUiOptions].
         */
        @JvmStatic
        fun newInstance(
            navigationUiOptions: NavigationUiOptions,
            locationProvider: LocationProvider,
            activity: FragmentActivity
        ): SimpleNavigationFragment {
            val fragment = SimpleNavigationFragment()
            fragment.arguments = bundleOf(Pair(KEY_NAVIGATION_OPTIONS, navigationUiOptions))
            fragment.navigationTileStore = NavigationTileStore.create(
                context = activity,
                navigationTileStoreConfig = NavigationTileStoreConfiguration(
                    apiKey = BuildConfig.TOMTOM_API_KEY
                )
            )
            fragment.routePlanner = OnlineRoutePlanner.create(activity, BuildConfig.TOMTOM_API_KEY)
            fragment.tomTomNavigation = OnlineTomTomNavigationFactory.create(
                Configuration(
                    context = activity,
                    navigationTileStore = fragment.navigationTileStore,
                    locationProvider = locationProvider,
                    routePlanner = fragment.routePlanner
                )
            )
            return fragment
        }
    }

    private val simpleViewModel by activityViewModels<SimpleViewModel>()

    private lateinit var navigationTileStore: NavigationTileStore
    private lateinit var tomTomNavigation: TomTomNavigation
    private lateinit var routePlanner: RoutePlanner

    val mapMatchedLocationProvider: MapMatchedLocationProvider
        get() = MapMatchedLocationProvider(tomTomNavigation)

    private val navigationListener =
        object : NavigationListener {
            override fun onStarted() {
                simpleViewModel.navigationStatus.value = SimpleViewModel.Status.STARTED
                setSimulationLocationProviderToNavigation(simpleViewModel.route)
            }

            override fun onStopped() {
                simpleViewModel.navigationStatus.value = SimpleViewModel.Status.STOPPED
                stopSimpleNavigation()
            }
        }


    fun startSimpleNavigation(
        route: Route, routePlanningOptions: RoutePlanningOptions,
    ) {
        setTomTomNavigation(tomTomNavigation)
        startNavigation(RoutePlan(route, routePlanningOptions))
        addNavigationListener(navigationListener)
    }


    fun stopSimpleNavigation() {
        stopNavigation()
        removeNavigationListener(navigationListener)
    }

    fun configListeners(
        progressUpdatedListener: ProgressUpdatedListener,
        routeAddedListener: RouteAddedListener,
        routeRemovedListener: RouteRemovedListener,
        activeRouteChangedListener: ActiveRouteChangedListener,
        add: Boolean
    ) {
        with(tomTomNavigation) {
            if (add) {
                addProgressUpdatedListener(progressUpdatedListener)
                addRouteAddedListener(routeAddedListener)
                addRouteRemovedListener(routeRemovedListener)
                addActiveRouteChangedListener(activeRouteChangedListener)
            } else {
                removeProgressUpdatedListener(progressUpdatedListener)
                removeActiveRouteChangedListener(activeRouteChangedListener)
                removeRouteAddedListener(routeAddedListener)
                removeRouteRemovedListener(routeRemovedListener)
            }
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
            navigationView.showSpeedView()
        } else {
            navigationView.hideSpeedView()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tomTomNavigation.close()
        navigationTileStore.close()
    }
}