package com.example.tomtomdemo


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log

import androidx.appcompat.app.AppCompatActivity
import com.tomtom.sdk.datamanagement.navigationtile.NavigationTileStore
import com.tomtom.sdk.datamanagement.navigationtile.NavigationTileStoreConfiguration
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.GeoPoint


import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.OnLocationUpdateListener
import com.tomtom.sdk.location.android.AndroidLocationProvider
import com.tomtom.sdk.location.mapmatched.MapMatchedLocationProvider
import com.tomtom.sdk.location.simulation.SimulationLocationProvider
import com.tomtom.sdk.location.simulation.strategy.InterpolationStrategy

import com.tomtom.sdk.map.display.MapOptions
import com.tomtom.sdk.map.display.TomTomMap
import com.tomtom.sdk.map.display.camera.CameraChangeListener
import com.tomtom.sdk.map.display.camera.CameraOptions
import com.tomtom.sdk.map.display.camera.CameraTrackingMode
import com.tomtom.sdk.map.display.common.screen.Padding
import com.tomtom.sdk.map.display.location.LocationMarkerOptions
import com.tomtom.sdk.map.display.route.Instruction
import com.tomtom.sdk.map.display.route.RouteClickListener
import com.tomtom.sdk.map.display.route.RouteOptions
import com.tomtom.sdk.map.display.ui.MapFragment
import com.tomtom.sdk.map.display.ui.currentlocation.CurrentLocationButton
import com.tomtom.sdk.navigation.ActiveRouteChangedListener
import com.tomtom.sdk.navigation.ProgressUpdatedListener
import com.tomtom.sdk.navigation.RouteAddedListener
import com.tomtom.sdk.navigation.RouteAddedReason
import com.tomtom.sdk.navigation.RoutePlan
import com.tomtom.sdk.navigation.RouteRemovedListener
import com.tomtom.sdk.navigation.TomTomNavigation
import com.tomtom.sdk.navigation.UnitSystemType
import com.tomtom.sdk.navigation.online.Configuration
import com.tomtom.sdk.navigation.online.OnlineTomTomNavigationFactory
import com.tomtom.sdk.navigation.ui.NavigationFragment
import com.tomtom.sdk.navigation.ui.NavigationUiOptions
import com.tomtom.sdk.routing.RoutePlanner
import com.tomtom.sdk.routing.RoutePlanningCallback
import com.tomtom.sdk.routing.RoutePlanningResponse
import com.tomtom.sdk.routing.RoutingFailure
import com.tomtom.sdk.routing.online.OnlineRoutePlanner
import com.tomtom.sdk.routing.options.Itinerary
import com.tomtom.sdk.routing.options.RoutePlanningOptions

import com.tomtom.sdk.routing.options.guidance.GuidanceOptions
import com.tomtom.sdk.routing.options.guidance.InstructionPhoneticsType
import com.tomtom.sdk.routing.route.Route
import com.tomtom.sdk.search.Search
import com.tomtom.sdk.search.SearchCallback
import com.tomtom.sdk.search.SearchOptions
import com.tomtom.sdk.search.SearchResponse
import com.tomtom.sdk.search.common.error.SearchFailure
import com.tomtom.sdk.search.online.OnlineSearch
import com.tomtom.sdk.search.ui.SearchFragment
import com.tomtom.sdk.search.ui.SearchFragmentListener
import com.tomtom.sdk.search.ui.SearchResultsView
import com.tomtom.sdk.search.ui.SearchView
import com.tomtom.sdk.search.ui.SearchViewListener
import com.tomtom.sdk.search.ui.model.PlaceDetails
import com.tomtom.sdk.search.ui.model.SearchApiParameters
import com.tomtom.sdk.search.ui.model.SearchProperties
import com.tomtom.sdk.search.ui.model.toPlace
import com.tomtom.sdk.vehicle.Vehicle
import com.tomtom.sdk.vehicle.VehicleProviderFactory
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        const val ZOOM_TO_ROUTE_PADDING = 100
    }

    private val apiKey = BuildConfig.TOMTOM_API_KEY

    private lateinit var locationProvider: LocationProvider
    private lateinit var tomTomMap: TomTomMap
    private lateinit var route: Route
    private lateinit var routePlanningOptions: RoutePlanningOptions
    private lateinit var onLocationUpdateListener: OnLocationUpdateListener

    private val mapFragment: MapFragment by lazy {
        MapFragment.newInstance(MapOptions(mapKey = apiKey)).apply {
            getMapAsync { map ->
                map.setLocationProvider(locationProvider)
                map.addRouteClickListener(routeClickListener)
                tomTomMap = map
                showUserLocation()
            }
        }
    }

    private val searchFragment: SearchFragment by lazy {
        SearchFragment.newInstance(
            SearchProperties(
                searchApiKey = apiKey,
                searchApiParameters = SearchApiParameters(limit = 5),
            )
        )
    }

    private val routePlanner: RoutePlanner by lazy {
        OnlineRoutePlanner.create(this, apiKey)
    }

    private val navigationManager: NavigationManager by lazy {
        NavigationManager(this, locationProvider, routePlanner, route, {
            tomTomMap.addCameraChangeListener(cameraChangeListener)
            tomTomMap.cameraTrackingMode = CameraTrackingMode.FollowRouteDirection
            tomTomMap.enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Chevron))
            setMapMatchedLocationProvider()
            setMapNavigationPadding()
            navigationManager.configListeners(
                progressUpdatedListener,
                routeAddedListener,
                routeRemovedListener,
                activeRouteChangedListener,
                true
            )
        }) {
            mapFragment.currentLocationButton.visibilityPolicy =
                CurrentLocationButton.VisibilityPolicy.InvisibleWhenRecentered
            tomTomMap.removeCameraChangeListener(cameraChangeListener)
            tomTomMap.cameraTrackingMode = CameraTrackingMode.None
            tomTomMap.enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Pointer))
            tomTomMap.setPadding(Padding(0, 0, 0, 0))
            navigationManager.configListeners(
                progressUpdatedListener,
                routeAddedListener,
                routeRemovedListener,
                activeRouteChangedListener,
                false
            )
            tomTomMap.clear()
            initLocationProvider()
            locationProvider.enable()
            showUserLocation()
        }
    }

    private val cameraChangeListener by lazy {
        CameraChangeListener {
            val cameraTrackingMode = tomTomMap.cameraTrackingMode
            navigationManager.hideOrShowSpeedView(cameraTrackingMode == CameraTrackingMode.FollowRouteDirection)
        }
    }

    private val progressUpdatedListener = ProgressUpdatedListener {
        tomTomMap.routes.firstOrNull()?.progress = it.distanceAlongRoute
    }

    private val routeAddedListener by lazy {
        RouteAddedListener { route, _, routeAddedReason ->
            if (routeAddedReason !is RouteAddedReason.NavigationStarted) {
                drawRoute(
                    route = route,
                    color = Color.GRAY,
                    withDepartureMarker = false,
                    withZoom = false,
                )
            }
        }
    }

    private val routeRemovedListener by lazy {
        RouteRemovedListener { route, _ ->
            tomTomMap.routes.find { it.tag == route.id.toString() }?.remove()
        }
    }

    private val activeRouteChangedListener by lazy {
        ActiveRouteChangedListener { route ->
            tomTomMap.routes.forEach {
                if (it.tag == route.id.toString()) {
                    it.color = RouteOptions.DEFAULT_COLOR
                } else {
                    it.color = Color.GRAY
                }
            }
        }
    }

    private fun setMapNavigationPadding() {
        val paddingBottom = resources.getDimensionPixelOffset(R.dimen.map_padding_bottom)
        val padding = Padding(0, 0, 0, paddingBottom)
        tomTomMap.setPadding(padding)
    }

    private fun setMapMatchedLocationProvider() {
        val mapMatchedLocationProvider = MapMatchedLocationProvider(navigationManager.tomTomNavigation)
        tomTomMap.setLocationProvider(mapMatchedLocationProvider)
        mapMatchedLocationProvider.enable()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initLocationProvider()
        checkLocationPermissions {
            locationProvider.enable()
        }

        showFragmentAsync(mapFragment, R.id.map_container)
        configSearch()
    }

    private fun initLocationProvider() {
        locationProvider = AndroidLocationProvider(context = this)
    }

    private val routeClickListener = RouteClickListener {
        if (!navigationManager.isNavigationRunning()) {
            mapFragment.currentLocationButton.visibilityPolicy = CurrentLocationButton.VisibilityPolicy.Invisible
            navigationManager.showNavigation(R.id.navigation_fragment_container)
            navigationManager.startNavigation(route, routePlanningOptions)
        }
    }

    private fun configSearch() {
        searchFragment.setSearchApi(OnlineSearch.create(this@MainActivity, apiKey))
        showFragmentSync(searchFragment, R.id.search_fragment_container)

        searchFragment.enableSearchBackButton(false)
        searchFragment.setFragmentListener(object : SearchFragmentListener {
            override fun onSearchBackButtonClick() {
            }

            override fun onSearchResultClick(placeDetails: PlaceDetails) {
                planRoute(placeDetails)
            }

            override fun onSearchError(throwable: Throwable) {
            }

            override fun onSearchQueryChanged(input: String) {
            }

            override fun onCommandInsert(command: String) {
            }
        })
    }


    private fun planRoute(
        placeDetails: PlaceDetails
    ) {
        val userLocation = tomTomMap.currentLocation?.position ?: return
        val itinerary = Itinerary(
            origin = userLocation,
            destination = GeoPoint(placeDetails.position.latitude, placeDetails.position.longitude)
        )
        routePlanningOptions = RoutePlanningOptions(
            itinerary = itinerary,
            guidanceOptions = GuidanceOptions(),
            vehicle = Vehicle.Car(),
        )

        routePlanner.planRoute(
            routePlanningOptions,
            object : RoutePlanningCallback {
                override fun onSuccess(result: RoutePlanningResponse) {
                    route = result.routes.first()
                    drawRoute(route)
                    tomTomMap.zoomToRoutes(ZOOM_TO_ROUTE_PADDING)
                    searchFragment.clear()
                    this@MainActivity.hideKeyboard()
                }

                override fun onFailure(failure: RoutingFailure) {
                }

                override fun onRoutePlanned(route: Route) {
                }
            },
        )
    }

    private fun drawRoute(
        route: Route,
        color: Int = RouteOptions.DEFAULT_COLOR,
        withDepartureMarker: Boolean = true,
        withZoom: Boolean = true,
    ) {
        val instructions =
            route.legs
                .flatMap { routeLeg -> routeLeg.instructions }
                .map {
                    Instruction(
                        routeOffset = it.routeOffset,
                    )
                }
        val routeOptions =
            RouteOptions(
                geometry = route.geometry,
                destinationMarkerVisible = true,
                departureMarkerVisible = withDepartureMarker,
                instructions = instructions,
                routeOffset = route.routePoints.map { it.routeOffset },
                color = color,
                tag = route.id.toString(),
            )
        tomTomMap.addRoute(routeOptions)
        if (withZoom) {
            tomTomMap.zoomToRoutes(ZOOM_TO_ROUTE_PADDING)
        }
    }

    private fun checkLocationPermissions(onLocationPermissionsGranted: () -> Unit) {
        if (checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            && checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            onLocationPermissionsGranted()
        } else {
            requestLocationPermission(onLocationPermissionsGranted)
        }
    }

    private fun showUserLocation() {
        onLocationUpdateListener = OnLocationUpdateListener { location ->
            val locationMarker = LocationMarkerOptions(type = LocationMarkerOptions.Type.Pointer)
            tomTomMap.enableLocationMarker(locationMarker)
            tomTomMap.moveCamera(CameraOptions(location.position, zoom = 10.0))
            locationProvider.removeOnLocationUpdateListener(onLocationUpdateListener)
        }
        locationProvider.addOnLocationUpdateListener(onLocationUpdateListener)
    }


    override fun onDestroy() {
        super.onDestroy()
        tomTomMap.setLocationProvider(null)
        locationProvider.close()
        navigationManager.clean()
    }
}