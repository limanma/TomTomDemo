package com.example.tomtomdemo


import android.Manifest
import android.os.Bundle
import androidx.activity.viewModels

import androidx.appcompat.app.AppCompatActivity
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.location.OnLocationUpdateListener
import com.tomtom.sdk.location.android.AndroidLocationProvider
import com.tomtom.sdk.map.display.MapOptions
import com.tomtom.sdk.map.display.route.RouteClickListener
import com.tomtom.sdk.map.display.style.StandardStyles
import com.tomtom.sdk.map.display.ui.MapFragment
import com.tomtom.sdk.map.display.ui.currentlocation.CurrentLocationButton


class MainActivity : AppCompatActivity() {
    companion object {
        const val ZOOM_TO_ROUTE_PADDING = 100
    }

    private val simpleViewModel by viewModels<SimpleViewModel>()

    private lateinit var locationProvider: LocationProvider
    private lateinit var onLocationUpdateListener: OnLocationUpdateListener
    private lateinit var tomTomMapManager: TomTomMapManager

    private val mapFragment: MapFragment by lazy {
        MapFragment.newInstance(MapOptions(mapKey = BuildConfig.TOMTOM_API_KEY, mapStyle = StandardStyles.DRIVING)).apply {
            getMapAsync { map ->
                map.setLocationProvider(locationProvider)
                map.addRouteClickListener(routeClickListener)
                tomTomMapManager = TomTomMapManager(map, this@MainActivity) { isFollowRouteDirection: Boolean ->
                    navigationFragment.hideOrShowSpeedView(isFollowRouteDirection)
                }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        simpleViewModel.navigationStatus.observe(this) {
            if (it == SimpleViewModel.Status.STARTED) {
                tomTomMapManager.onNavigationStart(
                    matchedLocationProvider = navigationFragment.mapMatchedLocationProvider,
                    addListeners = { progressUpdatedListener, routeAddedListener, routeRemovedListener, activeRouteChangedListener ->
                        navigationFragment.configListeners(
                            progressUpdatedListener,
                            routeAddedListener,
                            routeRemovedListener,
                            activeRouteChangedListener,
                            true
                        )
                    }
                )
            } else if (it == SimpleViewModel.Status.STOPPED) {
                mapFragment.currentLocationButton.visibilityPolicy =
                    CurrentLocationButton.VisibilityPolicy.InvisibleWhenRecentered
                tomTomMapManager.onNavigationStopped { progressUpdatedListener, routeAddedListener, routeRemovedListener, activeRouteChangedListener ->
                    navigationFragment.configListeners(
                        progressUpdatedListener,
                        routeAddedListener,
                        routeRemovedListener,
                        activeRouteChangedListener,
                        false
                    )
                }
                initLocationProvider()
                locationProvider.enable()
                showUserLocation()
            }
        }
        simpleViewModel.planRouteData.observe(this) {
            if (it != null) {
                tomTomMapManager.clearCurrentMap()
                tomTomMapManager.planRoute(it) { route ->
                    simpleViewModel.route = route
                    searchFragment.clear()
                    this@MainActivity.hideKeyboard()
                }
            }
        }
        initLocationProvider()
        checkLocationPermissions {
            locationProvider.enable()
            showFragmentAsync(mapFragment, R.id.map_container)
            showFragmentAsync(searchFragment, R.id.search_fragment_container)
        }
    }
    private val routeClickListener = RouteClickListener {
        if (!navigationFragment.isNavigationRunning()) {
            mapFragment.currentLocationButton.visibilityPolicy = CurrentLocationButton.VisibilityPolicy.Invisible
            showFragmentSync(navigationFragment, R.id.navigation_fragment_container)
            navigationFragment.startSimpleNavigation(tomTomMapManager.route, tomTomMapManager.routePlanningOptions)
        }
    }

    /**
     * init AndroidLocationProvider instance
     */
    private fun initLocationProvider() {
        locationProvider = AndroidLocationProvider(context = this)
    }


    /**
     * check location permission
     */
    private fun checkLocationPermissions(onLocationPermissionsGranted: () -> Unit) {
        if (checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            && checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            onLocationPermissionsGranted()
        } else {
            requestLocationPermission(onLocationPermissionsGranted)
        }
    }

    /**
     * show user current location
     */
    private fun showUserLocation() {
        onLocationUpdateListener = OnLocationUpdateListener { location ->
            tomTomMapManager.showUserLocation(location)
            locationProvider.removeOnLocationUpdateListener(onLocationUpdateListener)
        }
        locationProvider.addOnLocationUpdateListener(onLocationUpdateListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        tomTomMapManager.clean()
        locationProvider.close()
    }
}