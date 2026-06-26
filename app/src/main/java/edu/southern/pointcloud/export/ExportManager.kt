package edu.southern.pointcloud.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import edu.southern.pointcloud.data.models.GeoPoint
import edu.southern.pointcloud.data.models.Point3D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class ExportFormat {
    PLY,           // binary PLY — MeshLab, CloudCompare, newer SketchUp
    LAS,           // LAS 1.4   — Revit, AutoCAD Civil 3D
    XYZ,           // ASCII XYZ — universal fallback
    OBJ_TERRAIN,   // triangulated terrain mesh — SketchUp Make 2017 File > Import (no plugin)
    XYZ_SKETCHUP   // downsampled ≤50 K pts CSV — TIG PointCloudMaker plugin for Make 2017
}

private fun ExportFormat.fileExtension() = when (this) {
    ExportFormat.PLY         -> "ply"
    ExportFormat.LAS         -> "las"
    ExportFormat.XYZ         -> "xyz"
    ExportFormat.OBJ_TERRAIN -> "obj"
    ExportFormat.XYZ_SKETCHUP -> "xyz"
}

private fun ExportFormat.mimeType() = when (this) {
    ExportFormat.XYZ, ExportFormat.XYZ_SKETCHUP -> "text/plain"
    ExportFormat.OBJ_TERRAIN                     -> "text/plain"
    else                                          -> "application/octet-stream"
}

data class ExportResult(
    val file: File,
    val format: ExportFormat,
    val pointCount: Int,
    val meshResolution: MeshResolution? = null
)

class ExportManager(private val context: Context) {

    private val exportDir: File
        get() = File(context.getExternalFilesDir(null), "point_clouds").also { it.mkdirs() }

    suspend fun export(
        points: List<Point3D>,
        format: ExportFormat,
        gpsOrigin: GeoPoint? = null,
        meshResolution: MeshResolution = MeshResolution.MEDIUM,
        onProgress: (Int) -> Unit = {}
    ): ExportResult = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val ext = format.fileExtension()
        val suffix = if (format == ExportFormat.OBJ_TERRAIN) "_${meshResolution.name.lowercase()}" else ""
        val outFile = File(exportDir, "scan_${timestamp}${suffix}.$ext")

        when (format) {
            ExportFormat.PLY -> PlyExporter.export(points, outFile, onProgress)

            ExportFormat.LAS -> LasExporter.export(points, outFile, gpsOrigin, onProgress)

            ExportFormat.XYZ -> XyzExporter.export(points, outFile, onProgress)

            ExportFormat.OBJ_TERRAIN -> {
                onProgress(5)
                val mesh = TerrainMeshGenerator.generate(points, meshResolution)
                ObjExporter.export(mesh, outFile) { p -> onProgress(5 + p * 95 / 100) }
                return@withContext ExportResult(outFile, format, mesh.vertexCount, meshResolution)
            }

            ExportFormat.XYZ_SKETCHUP -> {
                val written = SketchUpXyzExporter.export(points, outFile, onProgress)
                return@withContext ExportResult(outFile, format, written)
            }
        }

        ExportResult(outFile, format, points.size)
    }

    fun shareFile(result: ExportResult) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            result.file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = result.format.mimeType()
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Point Cloud – ${result.file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Point Cloud").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun listExports(): List<File> =
        exportDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
}
