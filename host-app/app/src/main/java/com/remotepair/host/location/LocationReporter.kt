package com.remotepair.host.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.remotepair.host.service.HostSession

/**
 * Fetches the device location and reports it to the server (admin-visible).
 * No-op if location permission isn't granted (the calls just fail silently).
 */
object LocationReporter {
    @SuppressLint("MissingPermission")
    fun reportOnce(ctx: Context) {
        val client = LocationServices.getFusedLocationProviderClient(ctx.applicationContext)
        runCatching {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) HostSession.reportLocation(loc.latitude, loc.longitude)
                }
        }
        runCatching {
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) HostSession.reportLocation(loc.latitude, loc.longitude)
            }
        }
    }
}
