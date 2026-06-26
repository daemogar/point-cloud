package edu.southern.pointcloud.export

import edu.southern.pointcloud.data.models.Point3D

enum class MeshResolution(val cellSize: Float, val label: String) {
    HIGH(0.25f, "High — 25 cm grid"),
    MEDIUM(0.5f, "Medium — 50 cm grid (recommended)"),
    LOW(1.0f, "Low — 1 m grid (fastest)")
}

data class TerrainMesh(
    val vertices: FloatArray,   // interleaved x, y, z
    val indices: IntArray,      // triangle vertex indices (0-based)
    val cols: Int,
    val rows: Int,
    val vertexCount: Int,
    val triangleCount: Int
)

/**
 * Converts a point cloud to a triangulated terrain mesh (DEM).
 *
 * Algorithm:
 *  1. Bin all points into a regular horizontal grid.
 *  2. Each cell height = average Z of all points that fall in it.
 *  3. Fill empty cells with a multi-pass 3×3 neighbourhood average.
 *  4. Triangulate the grid with two triangles per quad.
 *
 * At 0.5 m resolution, 2 acres (≈90×90 m) produces ~32 K vertices and
 * ~64 K triangles — well within SketchUp Make 2017's limits.
 */
object TerrainMeshGenerator {

    fun generate(
        points: List<Point3D>,
        resolution: MeshResolution = MeshResolution.MEDIUM
    ): TerrainMesh {
        val cell = resolution.cellSize

        // --- 1. Bounding box ---
        var minX = Float.MAX_VALUE;  var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE;  var maxY = -Float.MAX_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x;  if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y;  if (p.y > maxY) maxY = p.y
        }
        if (minX >= maxX || minY >= maxY) return emptyMesh()

        val cols = ((maxX - minX) / cell).toInt() + 1
        val rows = ((maxY - minY) / cell).toInt() + 1
        val size = cols * rows

        // --- 2. Accumulate heights into grid ---
        val heightSum   = FloatArray(size)
        val heightCount = IntArray(size)

        for (p in points) {
            val c = ((p.x - minX) / cell).toInt().coerceIn(0, cols - 1)
            val r = ((p.y - minY) / cell).toInt().coerceIn(0, rows - 1)
            val i = r * cols + c
            heightSum[i]   += p.z
            heightCount[i] += 1
        }

        // Average
        val heights = FloatArray(size) { Float.NaN }
        for (i in 0 until size) {
            if (heightCount[i] > 0) heights[i] = heightSum[i] / heightCount[i]
        }

        // --- 3. Fill gaps (3 passes of 3×3 neighbourhood average) ---
        repeat(3) { fillGaps(heights, cols, rows) }

        // Any remaining NaN → global average
        val validHeights = heights.filter { !it.isNaN() }
        val globalAvg = if (validHeights.isNotEmpty()) validHeights.average().toFloat() else 0f
        for (i in heights.indices) if (heights[i].isNaN()) heights[i] = globalAvg

        // --- 4. Build vertex buffer ---
        val vertices = FloatArray(size * 3)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val vi = (r * cols + c) * 3
                vertices[vi]     = minX + c * cell
                vertices[vi + 1] = minY + r * cell
                vertices[vi + 2] = heights[r * cols + c]
            }
        }

        // --- 5. Triangulate grid (two CW triangles per quad) ---
        val triCount = (cols - 1) * (rows - 1) * 2
        val indices  = IntArray(triCount * 3)
        var ti = 0
        for (r in 0 until rows - 1) {
            for (c in 0 until cols - 1) {
                val tl = r * cols + c
                val tr = tl + 1
                val bl = tl + cols
                val br = bl + 1
                indices[ti++] = tl;  indices[ti++] = bl;  indices[ti++] = tr
                indices[ti++] = tr;  indices[ti++] = bl;  indices[ti++] = br
            }
        }

        return TerrainMesh(vertices, indices, cols, rows, size, triCount)
    }

    private fun fillGaps(h: FloatArray, cols: Int, rows: Int) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val i = r * cols + c
                if (!h[i].isNaN()) continue
                var sum = 0f;  var n = 0
                for (dr in -1..1) for (dc in -1..1) {
                    val nr = r + dr;  val nc = c + dc
                    if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue
                    val ni = nr * cols + nc
                    if (!h[ni].isNaN()) { sum += h[ni]; n++ }
                }
                if (n > 0) h[i] = sum / n
            }
        }
    }

    private fun emptyMesh() = TerrainMesh(FloatArray(0), IntArray(0), 0, 0, 0, 0)
}
