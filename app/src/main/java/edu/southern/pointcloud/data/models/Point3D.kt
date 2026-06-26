package edu.southern.pointcloud.data.models

/**
 * A single point in 3D space.
 * Coordinates are in meters, in ENU (East-North-Up) space relative to
 * the scan session's GPS origin.  [r], [g], [b] are 0-255 from the camera
 * color image; [intensity] is reflectance (0-65535, from depth confidence).
 */
data class Point3D(
    val x: Float,   // East  (meters)
    val y: Float,   // North (meters)
    val z: Float,   // Up    (meters)
    val r: Byte = 127,
    val g: Byte = 127,
    val b: Byte = 127,
    val intensity: Int = 32768
)
