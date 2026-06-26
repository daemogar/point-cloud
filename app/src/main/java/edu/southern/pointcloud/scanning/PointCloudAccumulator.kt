package edu.southern.pointcloud.scanning

import edu.southern.pointcloud.data.models.GeoPoint
import edu.southern.pointcloud.data.models.Point3D
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Accumulates point clouds from successive depth frames into a single buffer.
 *
 * Voxel de-duplication with 5 cm cells keeps the buffer from growing without
 * bound while walking 2 acres.  At 5 cm voxel size, 2 acres at 0.5 m scan
 * height can hold at most ~3.2 million unique voxels — well within the 12 GB
 * RAM of the S26 Ultra.
 */
class PointCloudAccumulator {

    private val VOXEL_SIZE = 0.05f  // 5 cm

    // HashMap key encodes voxel grid coords as a packed Long
    private val voxelSet = HashMap<Long, Point3D>(500_000)

    private val _pointCount = MutableStateFlow(0L)
    val pointCount: StateFlow<Long> = _pointCount

    private val _coverageM2 = MutableStateFlow(0.0)
    val coverageM2: StateFlow<Double> = _coverageM2

    // Bounding box for coverage estimate
    private var minX = Float.MAX_VALUE; private var maxX = Float.MIN_VALUE
    private var minY = Float.MAX_VALUE; private var maxY = Float.MIN_VALUE

    fun addPoints(points: List<Point3D>) {
        for (p in points) {
            val ix = (p.x / VOXEL_SIZE).toLong()
            val iy = (p.y / VOXEL_SIZE).toLong()
            val iz = (p.z / VOXEL_SIZE).toLong()
            // Pack three 21-bit signed ints into one Long (fits ±1 048 575 voxels each axis)
            val key = (ix and 0x1FFFFF) or ((iy and 0x1FFFFF) shl 21) or ((iz and 0x1FFFFF) shl 42)

            if (!voxelSet.containsKey(key)) {
                voxelSet[key] = p
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
        }
        _pointCount.value = voxelSet.size.toLong()
        if (minX < maxX && minY < maxY) {
            _coverageM2.value = (maxX - minX).toDouble() * (maxY - minY).toDouble()
        }
    }

    fun snapshot(): List<Point3D> = voxelSet.values.toList()

    fun clear() {
        voxelSet.clear()
        minX = Float.MAX_VALUE; maxX = Float.MIN_VALUE
        minY = Float.MAX_VALUE; maxY = Float.MIN_VALUE
        _pointCount.value = 0L
        _coverageM2.value = 0.0
    }
}
