package com.example.tomtomdemo


import android.Manifest
import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels

import androidx.appcompat.app.AppCompatActivity
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.OnLocationUpdateListener
import com.tomtom.sdk.location.android.AndroidLocationProvider
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
import com.tomtom.sdk.map.display.style.StandardStyles
import com.tomtom.sdk.map.display.ui.MapFragment
import com.tomtom.sdk.map.display.ui.currentlocation.CurrentLocationButton
import com.tomtom.sdk.navigation.ActiveRouteChangedListener
import com.tomtom.sdk.navigation.ProgressUpdatedListener
import com.tomtom.sdk.navigation.RouteAddedListener
import com.tomtom.sdk.navigation.RouteAddedReason
import com.tomtom.sdk.navigation.RouteRemovedListener
import com.tomtom.sdk.routing.RoutePlanner
import com.tomtom.sdk.routing.RoutePlanningCallback
import com.tomtom.sdk.routing.RoutePlanningResponse
import com.tomtom.sdk.routing.RoutingFailure
import com.tomtom.sdk.routing.online.OnlineRoutePlanner
import com.tomtom.sdk.routing.options.Itinerary
import com.tomtom.sdk.routing.options.RoutePlanningOptions
import com.tomtom.sdk.routing.options.guidance.GuidanceOptions
import com.tomtom.sdk.routing.route.Route
import com.tomtom.sdk.search.ui.model.PlaceDetails
import com.tomtom.sdk.vehicle.Vehicle

class MainActivity : AppCompatActivity() {
    companion object {
        const val ZOOM_TO_ROUTE_PADDING = 100
    }

    private val apiKey = BuildConfig.TOMTOM_API_KEY

    private val simpleViewModel by viewModels<SimpleViewModel>()

    private lateinit var locationProvider: LocationProvider
    private lateinit var tomTomMap: TomTomMap
    private lateinit var route: Route
    private lateinit var routePlanningOptions: RoutePlanningOptions
    private lateinit var onLocationUpdateListener: OnLocationUpdateListener

    private val mapFragment: MapFragment by lazy {
        MapFragment.newInstance(MapOptions(mapKey = apiKey, mapStyle = StandardStyles.DRIVING)).apply {
            getMapAsync { map ->
                map.setLocationProvider(locationProvider)
                map.addRouteClickListener(routeClickListener)
                tomTomMap = map
                showUserLocation()
            }
        }
    }

    private val searchFragment: SimpleSearchFragment by lazy {
        SimpleSearchFragment.newInstance()
    }

    private val navigationFragment: SimpleNavigationFragment by lazy {
        SimpleNavigationFragment.newInstance(locationProvider)
    }

    private val routePlanner: RoutePlanner by lazy {
        OnlineRoutePlanner.create(this, apiKey)
    }

    private val cameraChangeListener by lazy {
        CameraChangeListener {
            val cameraTrackingMode = tomTomMap.cameraTrackingMode
            navigationFragment.hideOrShowSpeedView(cameraTrackingMode == CameraTrackingMode.FollowRouteDirection)
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
        val mapMatchedLocationProvider = navigationFragment.mapMatchedLocationProvider
        tomTomMap.setLocationProvider(mapMatchedLocationProvider)
        mapMatchedLocationProvider.enable()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        simpleViewModel.navigationStatus.observe(this) {
            if (it == SimpleViewModel.Status.STARTED) {
                tomTomMap.addCameraChangeListener(cameraChangeListener)
                tomTomMap.cameraTrackingMode = CameraTrackingMode.FollowRouteDirection
                tomTomMap.enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Chevron))
                setMapMatchedLocationProvider()
                setMapNavigationPadding()
                navigationFragment.configListeners(
                    progressUpdatedListener,
                    routeAddedListener,
                    routeRemovedListener,
                    activeRouteChangedListener,
                    true
                )
            } else if (it == SimpleViewModel.Status.STOPPED) {
                mapFragment.currentLocationButton.visibilityPolicy =
                    CurrentLocationButton.VisibilityPolicy.InvisibleWhenRecentered
                tomTomMap.removeCameraChangeListener(cameraChangeListener)
                tomTomMap.cameraTrackingMode = CameraTrackingMode.None
                tomTomMap.enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Pointer))
                tomTomMap.setPadding(Padding(0, 0, 0, 0))
                navigationFragment.configListeners(
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
        simpleViewModel.planRouteData.observe(this) {
            if (it != null) {
                planRoute(it)
            }
        }
        initLocationProvider()
        checkLocationPermissions {
            locationProvider.enable()
        }

        showFragmentAsync(mapFragment, R.id.map_container)
        showFragmentAsync(searchFragment, R.id.search_fragment_container)
    }

    private fun initLocationProvider() {
        locationProvider = AndroidLocationProvider(context = this)
    }

    private val routeClickListener = RouteClickListener {
        if (!navigationFragment.isNavigationRunning()) {
            mapFragment.currentLocationButton.visibilityPolicy = CurrentLocationButton.VisibilityPolicy.Invisible
            showFragmentSync(navigationFragment, R.id.navigation_fragment_container)
            navigationFragment.startSimpleNavigation(route, routePlanningOptions)
        }
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
                    simpleViewModel.route = route
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
    }
}