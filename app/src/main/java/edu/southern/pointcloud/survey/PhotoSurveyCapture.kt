package edu.southern.pointcloud.survey

import android.content.Context
import android.location.Exif
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import edu.southern.pointcloud.data.models.GeoPoint
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

/**
 * Photo Survey mode: captures a geotagged JPEG every [intervalMs] milliseconds
 * while the user walks the property.
 *
 * The resulting image set can be imported into:
 *  • OpenDroneMap (free/open-source, desktop or cloud)
 *  • Agisoft Metashape (professional photogrammetry)
 *  • RealityCapture (very fast, commercial)
 *  • Pix4D (cloud-based)
 * ...to generate a dense point cloud of the entire 2-acre terrain.
 *
 * This complements the ARCore depth scan which excels at detailed structures
 * but has limited range (~5–8 m).
 */
class PhotoSurveyCapture(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var captureJob: Job? = null

    val outputDir: File
        get() = File(context.getExternalFilesDir(null), "photo_survey").also { it.mkdirs() }

    private var _captureCount = 0
    val captureCount get() = _captureCount

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        preview: Preview
    ) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        imageCapture = capture
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture
        )
    }

    /**
     * Start auto-capture at [intervalMs]. [gpsProvider] is called before each
     * shot to embed GPS EXIF tags — photogrammetry software uses these to
     * anchor the reconstruction to real-world coordinates.
     */
    fun startAutoCapture(
        intervalMs: Long = 2_000,
        gpsProvider: () -> GeoPoint?,
        scope: CoroutineScope
    ) {
        captureJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val gps = gpsProvider()
                capturePhoto(gps)
                delay(intervalMs)
            }
        }
    }

    fun stopAutoCapture() {
        captureJob?.cancel()
    }

    private suspend fun capturePhoto(gps: GeoPoint?) = suspendCancellableCoroutine<Unit> { cont ->
        val cap = imageCapture ?: run { cont.resume(Unit) {} ; return@suspendCancellableCoroutine }
        val ts  = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(outputDir, "survey_${ts}.jpg")

        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        cap.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                // Embed GPS EXIF tags so photogrammetry software can georeference
                gps?.let { embedGps(file, it) }
                _captureCount++
                cont.resume(Unit) {}
            }
            override fun onError(exc: ImageCaptureException) {
                cont.resume(Unit) {}  // skip frame on error
            }
        })
    }

    private fun embedGps(file: File, gps: GeoPoint) {
        try {
            ExifInterface(file.absolutePath).apply {
                setGpsInfo(gps.latitude, gps.longitude, gps.altitudeMeters)
                saveAttributes()
            }
        } catch (_: Exception) { }
    }

    private fun ExifInterface.setGpsInfo(lat: Double, lon: Double, alt: Double) {
        val absLat = Math.abs(lat)
        val absLon = Math.abs(lon)
        val latDeg = absLat.toInt(); val latMin = ((absLat - latDeg) * 60).toInt()
        val latSec = ((absLat - latDeg - latMin / 60.0) * 3600)
        val lonDeg = absLon.toInt(); val lonMin = ((absLon - lonDeg) * 60).toInt()
        val lonSec = ((absLon - lonDeg - lonMin / 60.0) * 3600)

        setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (lat >= 0) "N" else "S")
        setAttribute(ExifInterface.TAG_GPS_LATITUDE, "$latDeg/1,$latMin/1,${(latSec*1000).toInt()}/1000")
        setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (lon >= 0) "E" else "W")
        setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "$lonDeg/1,$lonMin/1,${(lonSec*1000).toInt()}/1000")
        setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "${(alt * 100).toInt()}/100")
        setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (alt >= 0) "0" else "1")
    }

    fun release() {
        captureJob?.cancel()
        executor.shutdown()
    }
}
