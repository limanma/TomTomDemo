package com.example.tomtomdemo

import android.content.Context
import android.graphics.Color
import com.example.tomtomdemo.MainActivity.Companion.ZOOM_TO_ROUTE_PADDING
import com.tomtom.sdk.location.GeoLocation
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.location.mapmatched.MapMatchedLocationProvider
import com.tomtom.sdk.map.display.TomTomMap
import com.tomtom.sdk.map.display.camera.CameraChangeListener
import com.tomtom.sdk.map.display.camera.CameraOptions
import com.tomtom.sdk.map.display.camera.CameraTrackingMode
import com.tomtom.sdk.map.display.common.screen.Padding
import com.tomtom.sdk.map.display.location.LocationMarkerOptions
import com.tomtom.sdk.map.display.route.Instruction
import com.tomtom.sdk.map.display.route.RouteOptions
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

/**
 * Created by Chen Wei on 2024/7/1.
 * manage logic of route related
 */
class TomTomMapManager(
    private val tomTomMap: TomTomMap,
    private val context: Context,
    private val onCameraChange: (Boolean) -> Unit
) {

    companion object {
        const val CAMERA_ZOOM_DEFAULT = 10.0
    }

    private lateinit var _routePlanningOptions: RoutePlanningOptions
    private lateinit var _route: Route

    private val routePlanner: RoutePlanner = OnlineRoutePlanner.create(context, BuildConfig.TOMTOM_API_KEY)

    val routePlanningOptions: RoutePlanningOptions
        get() = _routePlanningOptions

    val route: Route
        get() = _route

    private val cameraChangeListener by lazy {
        CameraChangeListener {
            onCameraChange(tomTomMap.cameraTrackingMode == CameraTrackingMode.FollowRouteDirection)
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

    /**
     * start to plan a route on the map
     * @param placeDetails destination place
     * @param onSuccess action when plan route successfully
     */
    fun planRoute(
        placeDetails: PlaceDetails,
        onSuccess: (Route) -> Unit
    ) {
        val userLocation = tomTomMap.currentLocation?.position ?: return
        val itinerary = Itinerary(
            origin = userLocation,
            destination = GeoPoint(placeDetails.position.latitude, placeDetails.position.longitude)
        )
        _routePlanningOptions = RoutePlanningOptions(
            itinerary = itinerary,
            guidanceOptions = GuidanceOptions(),
            vehicle = Vehicle.Car(),
        )

        routePlanner.planRoute(
            _routePlanningOptions,
            object : RoutePlanningCallback {
                override fun onSuccess(result: RoutePlanningResponse) {
                    _route = result.routes.first()
                    drawRoute(_route)
                    tomTomMap.zoomToRoutes(ZOOM_TO_ROUTE_PADDING)
                    onSuccess(_route)
                }

                override fun onFailure(failure: RoutingFailure) {
                }

                override fun onRoutePlanned(route: Route) {
                }
            },
        )
    }

    /**
     * start to draw a route on the map
     * @param route what to draw
     * @param color route color
     * @param withDepartureMarker the visibility of the departure marker
     * @param withZoom whether to zoom the map when draw the route
     */
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

    /**
     * action when navigation start
     */
    fun onNavigationStart(
        matchedLocationProvider: MapMatchedLocationProvider,
        addListeners: (ProgressUpdatedListener, RouteAddedListener, RouteRemovedListener, ActiveRouteChangedListener) -> Unit
    ) = with(
        tomTomMap
    ) {
        addCameraChangeListener(cameraChangeListener)
        cameraTrackingMode = CameraTrackingMode.FollowRouteDirection
        enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Chevron))
        setMapNavigationPadding(context.resources.getDimensionPixelOffset(R.dimen.map_padding_bottom))
        setMapMatchedLocationProvider(matchedLocationProvider)
        addListeners(
            progressUpdatedListener,
            routeAddedListener,
            routeRemovedListener,
            activeRouteChangedListener,
        )
    }

    /**
     * action when navigation stopped
     */
    fun onNavigationStopped(
        removeListeners: (ProgressUpdatedListener, RouteAddedListener, RouteRemovedListener, ActiveRouteChangedListener) -> Unit
    ) = with(tomTomMap)
    {
        removeCameraChangeListener(cameraChangeListener)
        cameraTrackingMode = CameraTrackingMode.None
        enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.Pointer))
        setMapNavigationPadding(0)
        removeListeners(
            progressUpdatedListener,
            routeAddedListener,
            routeRemovedListener,
            activeRouteChangedListener,
        )
        clearCurrentMap()
    }

    private fun setMapMatchedLocationProvider(mapMatchedLocationProvider: MapMatchedLocationProvider) {
        tomTomMap.setLocationProvider(mapMatchedLocationProvider)
        mapMatchedLocationProvider.enable()
    }

    private fun setMapNavigationPadding(paddingBottom: Int) {
        val padding = Padding(0, 0, 0, paddingBottom)
        tomTomMap.setPadding(padding)
    }

    fun showUserLocation(location: GeoLocation) {
        val locationMarker = LocationMarkerOptions(type = LocationMarkerOptions.Type.Pointer)
        tomTomMap.enableLocationMarker(locationMarker)
        tomTomMap.moveCamera(CameraOptions(location.position, zoom = CAMERA_ZOOM_DEFAULT))
    }

    fun clearCurrentMap() {
        tomTomMap.clear()
    }

    fun clean() {
        clearCurrentMap()
        tomTomMap.setLocationProvider(null)
    }
}