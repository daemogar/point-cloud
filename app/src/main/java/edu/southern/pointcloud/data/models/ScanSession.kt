package edu.southern.pointcloud.data.models

import java.time.Instant

enum class ScanMode { DEPTH_AR, PHOTO_SURVEY }

data class ScanSession(
    val id: String,
    val createdAt: Instant = Instant.now(),
    val mode: ScanMode,
    val gpsOrigin: GeoPoint? = null,
    val pointCount: Long = 0L,
    val coverageAreaM2: Double = 0.0,
    val outputFiles: List<String> = emptyList()
) {
    /** True when the scan covers a useful amount of the target ~8 094 m² (2 acres). */
    val coveragePercent: Int
        get() = ((coverageAreaM2 / 8_094.0) * 100).toInt().coerceAtMost(100)
}
