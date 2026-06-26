package edu.southern.pointcloud.export

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * Writes a Wavefront OBJ terrain mesh for SketchUp Make 2017.
 *
 * Import steps in SketchUp Make 2017:
 *   File → Import → change type to "OBJ File" → select file → Open
 *   In the import options tick "Merge coplanar faces" (optional but tidier).
 *
 * The result is a triangulated terrain surface you can model on directly —
 * no plugin required.
 */
object ObjExporter {

    fun export(
        mesh: TerrainMesh,
        outFile: File,
        onProgress: (Int) -> Unit = {}
    ) {
        if (mesh.vertexCount == 0) return
        outFile.parentFile?.mkdirs()

        BufferedWriter(FileWriter(outFile), 512 * 1024).use { w ->
            w.write("# Point Cloud Scanner — terrain mesh for SketchUp Make 2017\n")
            w.write("# Import: File > Import > (Files of type: OBJ File)\n")
            w.write("# ${mesh.vertexCount} vertices  ${mesh.triangleCount} triangles\n")
            w.write("# Grid: ${mesh.cols} cols × ${mesh.rows} rows\n")
            w.write("o terrain\n\n")

            val vCount = mesh.vertexCount
            for (i in 0 until vCount) {
                val vi = i * 3
                w.write("v %.4f %.4f %.4f\n".format(
                    mesh.vertices[vi],
                    mesh.vertices[vi + 1],
                    mesh.vertices[vi + 2]
                ))
                if (i % 2000 == 0) onProgress(i * 50 / vCount)
            }

            w.write("\n")

            val fCount = mesh.triangleCount
            for (i in 0 until fCount) {
                val fi = i * 3
                // OBJ indices are 1-based
                w.write("f ${mesh.indices[fi] + 1} ${mesh.indices[fi + 1] + 1} ${mesh.indices[fi + 2] + 1}\n")
                if (i % 2000 == 0) onProgress(50 + i * 50 / fCount)
            }
        }

        onProgress(100)
    }
}
