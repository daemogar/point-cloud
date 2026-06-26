package edu.southern.pointcloud.data.models

import kotlin.math.*

/** WGS-84 geodetic position with altitude in meters above ellipsoid. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0
) {
    companion object {
        private const val EARTH_RADIUS_M = 6_378_137.0

        /**
         * Convert a second GPS fix to an ENU offset (east, north, up) in meters
         * relative to [origin].
         */
        fun toEnuOffset(origin: GeoPoint, point: GeoPoint): Triple<Double, Double, Double> {
            val lat1 = Math.toRadians(origin.latitude)
            val lat2 = Math.toRadians(point.latitude)
            val dLat = lat2 - lat1
            val dLon = Math.toRadians(point.longitude - origin.longitude)

            val north = dLat * EARTH_RADIUS_M
            val east  = dLon * EARTH_RADIUS_M * cos((lat1 + lat2) / 2.0)
            val up    = point.altitudeMeters - origin.altitudeMeters

            return Triple(east, north, up)
        }

        /** Approximate distance in meters between two GPS points. */
        fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
            val (e, n, u) = toEnuOffset(a, b)
            return sqrt(e * e + n * n + u * u)
        }
    }
}
