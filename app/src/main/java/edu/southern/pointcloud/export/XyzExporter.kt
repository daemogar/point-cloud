package edu.southern.pointcloud.export

import edu.southern.pointcloud.data.models.Point3D
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * Writes an ASCII XYZ file (X Y Z R G B, space-separated).
 *
 * SketchUp imports this via Extensions → Point Cloud → Import XYZ.
 * Also readable by CloudCompare, MeshLab, and most GIS tools.
 */
object XyzExporter {

    fun export(points: List<Point3D>, outFile: File, onProgress: (Int) -> Unit = {}) {
        outFile.parentFile?.mkdirs()
        var reported = 0

        BufferedWriter(FileWriter(outFile), 256 * 1024).use { w ->
            for ((i, p) in points.withIndex()) {
                w.write(
                    "%.4f %.4f %.4f %d %d %d\n".format(
                        p.x, p.y, p.z,
                        p.r.toInt() and 0xFF,
                        p.g.toInt() and 0xFF,
                        p.b.toInt() and 0xFF
                    )
                )
                val progress = (i + 1) * 100 / points.size
                if (progress != reported) { reported = progress; onProgress(progress) }
            }
        }
    }
}
