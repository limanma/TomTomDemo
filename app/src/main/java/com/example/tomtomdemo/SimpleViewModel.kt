package com.example.tomtomdemo

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import com.example.tomtomdemo.MainActivity.Companion.ZOOM_TO_ROUTE_PADDING
import com.tomtom.sdk.location.LocationProvider
import com.tomtom.sdk.map.display.TomTomMap
import com.tomtom.sdk.map.display.route.Instruction
import com.tomtom.sdk.map.display.route.RouteOptions
import com.tomtom.sdk.routing.RoutePlanner
import com.tomtom.sdk.routing.options.RoutePlanningOptions
import com.tomtom.sdk.routing.route.Route

/**
 * Created by Chen Wei on 2024/6/28.
 */
class SimpleViewModel : ViewModel() {
    val apiKey = BuildConfig.TOMTOM_API_KEY

}