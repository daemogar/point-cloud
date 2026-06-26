package edu.southern.pointcloud.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.*
import edu.southern.pointcloud.data.models.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

class LocationTracker(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    val locationFlow: Flow<GeoPoint> = callbackFlow {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            500L  // every 500 ms while scanning
        ).setMinUpdateDistanceMeters(0.1f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    trySend(GeoPoint(loc.latitude, loc.longitude, loc.altitude))
                }
            }
        }

        client.requestLocationUpdates(request, callback, context.mainLooper)
        awaitClose { client.removeLocationUpdates(callback) }
    }.conflate()
}
