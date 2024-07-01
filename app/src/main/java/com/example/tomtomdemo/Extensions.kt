package com.example.tomtomdemo

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

/**
 * Created by Chen Wei on 2024/6/27.
 */

fun Activity.hideKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    var view = currentFocus
    if (view == null) {
        view = View(this)
    }
    imm.hideSoftInputFromWindow(view.windowToken, 0)
}

fun Context.checkPermission(permission: String) = ContextCompat.checkSelfPermission(
    this,
    permission
) == PackageManager.PERMISSION_GRANTED

fun ComponentActivity.requestLocationPermission(
    onLocationPermissionsGranted: () -> Unit
) =
    this.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            && permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            onLocationPermissionsGranted()
        } else {
            Toast.makeText(
                this, getString(R.string.location_permission_denied), Toast.LENGTH_SHORT
            ).show()
        }
    }.launch(
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

fun FragmentActivity.showFragmentAsync(fragment: Fragment, @IdRes layoutId: Int) {
    supportFragmentManager.beginTransaction()
        .replace(layoutId, fragment)
        .commitAllowingStateLoss()
}

fun FragmentActivity.showFragmentSync(fragment: Fragment, @IdRes layoutId: Int) {
    supportFragmentManager.beginTransaction()
        .replace(layoutId, fragment)
        .commitNowAllowingStateLoss()
}

inline fun <T1, T2, R> letBoth(a: T1?, b: T2?, block: (T1, T2) -> R): R? {
    return if (a != null && b != null) {
        block(a, b)
    } else {
        null
    }
}

inline fun <T1, T2, T3, R> letAll(a: T1?, b: T2?, c: T3?, block: (T1, T2, T3) -> R): R? {
    return if (a != null && b != null && c != null) {
        block(a, b, c)
    } else {
        null
    }
}