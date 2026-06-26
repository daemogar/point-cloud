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

enum class ExportFormat { PLY, LAS, XYZ }

data class ExportResult(val file: File, val format: ExportFormat, val pointCount: Int)

class ExportManager(private val context: Context) {

    private val exportDir: File
        get() = File(context.getExternalFilesDir(null), "point_clouds").also { it.mkdirs() }

    suspend fun export(
        points: List<Point3D>,
        format: ExportFormat,
        gpsOrigin: GeoPoint? = null,
        onProgress: (Int) -> Unit = {}
    ): ExportResult = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val ext = format.name.lowercase()
        val outFile = File(exportDir, "scan_${timestamp}.$ext")

        when (format) {
            ExportFormat.PLY -> PlyExporter.export(points, outFile, onProgress)
            ExportFormat.LAS -> LasExporter.export(points, outFile, gpsOrigin, onProgress)
            ExportFormat.XYZ -> XyzExporter.export(points, outFile, onProgress)
        }

        ExportResult(outFile, format, points.size)
    }

    fun shareFile(result: ExportResult) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            result.file
        )
        val mime = when (result.format) {
            ExportFormat.PLY -> "application/octet-stream"
            ExportFormat.LAS -> "application/octet-stream"
            ExportFormat.XYZ -> "text/plain"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
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
