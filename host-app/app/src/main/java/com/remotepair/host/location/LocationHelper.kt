package com.remotepair.host.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

data class LocationFix(val lat: Double, val lon: Double, val accuracyM: Float, val whenMs: Long)

object LocationHelper {
    fun hasPermission(ctx: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun currentFix(ctx: Context): LocationFix? {
        if (!hasPermission(ctx)) return null
        return try {
            val client = LocationServices.getFusedLocationProviderClient(ctx)
            val loc = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            loc?.let { LocationFix(it.latitude, it.longitude, it.accuracy, it.time) }
        } catch (e: Exception) {
            null
        }
    }
}
