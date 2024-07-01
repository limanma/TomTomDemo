package com.example.tomtomdemo

import android.os.Bundle
import android.view.View
import android.widget.Toast

import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import com.tomtom.quantity.Distance
import com.tomtom.sdk.datamanagement.navigationtile.NavigationTileStore
import com.tomtom.sdk.datamanagement.navigationtile.NavigationTileStoreConfiguration
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.mapmatched.MapMatchedLocationProvider
import com.tomtom.sdk.location.simulation.SimulationLocationProvider
import com.tomtom.sdk.location.simulation.strategy.InterpolationStrategy
import com.tomtom.sdk.navigation.ActiveRouteChangedListener
import com.tomtom.sdk.navigation.GuidanceUpdatedListener
import com.tomtom.sdk.navigation.ProgressUpdatedListener
import com.tomtom.sdk.navigation.RouteAddedListener
import com.tomtom.sdk.navigation.RoutePlan
import com.tomtom.sdk.navigation.RouteRemovedListener
import com.tomtom.sdk.navigation.TomTomNavigation
import com.tomtom.sdk.navigation.guidance.GuidanceAnnouncement
import com.tomtom.sdk.navigation.guidance.InstructionPhase
import com.tomtom.sdk.navigation.guidance.instruction.GuidanceInstruction
import com.tomtom.sdk.navigation.online.Configuration
import com.tomtom.sdk.navigation.online.OnlineTomTomNavigationFactory
import com.tomtom.sdk.navigation.ui.NavigationFragment
import com.tomtom.sdk.navigation.ui.NavigationUiOptions
import com.tomtom.sdk.routing.RoutePlanner
import com.tomtom.sdk.routing.online.OnlineRoutePlanner
import com.tomtom.sdk.routing.options.RoutePlanningOptions
import com.tomtom.sdk.routing.route.Route

/**
 * Created by Chen Wei on 2024/6/28.
 */
class SimpleNavigationFragment : NavigationFragment() {

    companion object {
        /**
         * Creates an instance of [SimpleNavigationFragment] with provided [LocationProvider] and [FragmentActivity].
         */
        @JvmStatic
        fun newInstance(
            provider: LocationProvider,
        ): SimpleNavigationFragment {
            return SimpleNavigationFragment().apply {
                arguments = bundleOf(
                    Pair(
                        KEY_NAVIGATION_OPTIONS, NavigationUiOptions(
                            keepInBackground = true,
                            isSoundEnabled = false,
                        )
                    )
                )
                locationProvider = provider
            }
        }
    }

    private val simpleViewModel by activityViewModels<SimpleViewModel>()

    private lateinit var navigationTileStore: NavigationTileStore
    private lateinit var tomTomNavigation: TomTomNavigation
    private lateinit var routePlanner: RoutePlanner
    private lateinit var locationProvider: LocationProvider

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

    private val guidanceUpdatedListener: GuidanceUpdatedListener = object : GuidanceUpdatedListener {
        override fun onAnnouncementGenerated(announcement: GuidanceAnnouncement, shouldPlay: Boolean) {
            Toast.makeText(context, announcement.plainTextMessage, Toast.LENGTH_SHORT).show()
        }

        override fun onDistanceToNextInstructionChanged(
            distance: Distance,
            instructions: List<GuidanceInstruction>,
            currentPhase: InstructionPhase
        ) {
        }

        override fun onInstructionsChanged(instructions: List<GuidanceInstruction>) {
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.let {
            navigationTileStore = NavigationTileStore.create(
                context = it,
                navigationTileStoreConfig = NavigationTileStoreConfiguration(
                    apiKey = BuildConfig.TOMTOM_API_KEY
                )
            )
            routePlanner = OnlineRoutePlanner.create(it, BuildConfig.TOMTOM_API_KEY)
            tomTomNavigation = OnlineTomTomNavigationFactory.create(
                Configuration(
                    context = it,
                    navigationTileStore = navigationTileStore,
                    locationProvider = locationProvider,
                    routePlanner = routePlanner
                )
            )
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
                addGuidanceUpdatedListener(guidanceUpdatedListener)
            } else {
                removeProgressUpdatedListener(progressUpdatedListener)
                removeActiveRouteChangedListener(activeRouteChangedListener)
                removeRouteAddedListener(routeAddedListener)
                removeRouteRemovedListener(routeRemovedListener)
                removeGuidanceUpdatedListener(guidanceUpdatedListener)
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

    fun isNavigationRunning(): Boolean = ::tomTomNavigation.isInitialized && tomTomNavigation.navigationSnapshot != null

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