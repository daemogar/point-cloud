package edu.southern.pointcloud.export

import edu.southern.pointcloud.data.models.Point3D
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * Exports a spatially downsampled XYZ file compatible with TIG's free
 * "PointCloudMaker" Ruby plugin for SketchUp Make 2017.
 *
 * Plugin install:
 *   1. Download PointCloudMaker from the SketchUp Extension Warehouse
 *      (search "PointCloudMaker" by TIG — it is free).
 *   2. In SketchUp Make 2017: Window → Extension Manager → Install Extension
 *   3. After install: Extensions → TIG → Point Cloud Maker → Import CSV/XYZ
 *
 * Format: one point per line, comma-separated:  x,y,z,r,g,b
 * Each point becomes a SketchUp construction guide point (small cross).
 * Keep under 50 000 points or SketchUp becomes unresponsive.
 *
 * Downsampling: spatial grid — one representative point per [gridSize] metre
 * cell, so the spread across the property is preserved rather than just
 * taking the first N points.
 */
object SketchUpXyzExporter {

    private const val MAX_POINTS  = 50_000
    private const val GRID_SIZE   = 0.5f  // start with 50 cm grid; auto-widens if needed

    fun export(
        points: List<Point3D>,
        outFile: File,
        onProgress: (Int) -> Unit = {}
    ): Int {  // returns actual point count written
        outFile.parentFile?.mkdirs()

        val sampled = spatialDownsample(points)

        BufferedWriter(FileWriter(outFile), 256 * 1024).use { w ->
            for ((i, p) in sampled.withIndex()) {
                w.write(
                    "%.4f,%.4f,%.4f,%d,%d,%d\n".format(
                        p.x, p.y, p.z,
                        p.r.toInt() and 0xFF,
                        p.g.toInt() and 0xFF,
                        p.b.toInt() and 0xFF
                    )
                )
                if (i % 1000 == 0) onProgress(i * 100 / sampled.size)
            }
        }

        onProgress(100)
        return sampled.size
    }

    private fun spatialDownsample(points: List<Point3D>): List<Point3D> {
        if (points.size <= MAX_POINTS) return points

        // Widen the cell until we get ≤ MAX_POINTS unique cells
        var cellSize = GRID_SIZE
        while (true) {
            val seen = HashSet<Long>((MAX_POINTS * 2))
            for (p in points) {
                val cx = (p.x / cellSize).toLong()
                val cy = (p.y / cellSize).toLong()
                val cz = (p.z / cellSize).toLong()
                seen.add(cx * 1_000_003L + cy * 1_009L + cz)
            }
            if (seen.size <= MAX_POINTS) break
            cellSize *= 1.5f
        }

        // Second pass: keep first point that lands in each cell
        val taken = HashMap<Long, Point3D>((MAX_POINTS * 2))
        for (p in points) {
            val cx = (p.x / cellSize).toLong()
            val cy = (p.y / cellSize).toLong()
            val cz = (p.z / cellSize).toLong()
            val key = cx * 1_000_003L + cy * 1_009L + cz
            taken.putIfAbsent(key, p)
            if (taken.size >= MAX_POINTS) break
        }
        return taken.values.toList()
    }
}
