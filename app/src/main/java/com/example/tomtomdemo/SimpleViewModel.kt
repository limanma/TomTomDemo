package com.example.tomtomdemo

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

import com.tomtom.sdk.routing.route.Route
import com.tomtom.sdk.search.ui.model.PlaceDetails

/**
 * Created by Chen Wei on 2024/6/28.
 */
class SimpleViewModel : ViewModel() {
    enum class Status {
        STARTED, STOPPED
    }

    lateinit var route: Route

    var navigationStatus = MutableLiveData<Status>()

    val planRouteData = MutableLiveData<PlaceDetails>()
}