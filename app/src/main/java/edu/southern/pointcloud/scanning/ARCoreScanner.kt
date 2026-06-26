package edu.southern.pointcloud.scanning

import android.content.Context
import android.view.Surface
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import edu.southern.pointcloud.data.models.GeoPoint
import edu.southern.pointcloud.data.models.Point3D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps an ARCore [Session] configured for Raw Depth.
 *
 * Usage:
 *  1. Call [create] once; it checks device support.
 *  2. Call [setDisplaySurface] after the GLSurfaceView is ready.
 *  3. In your render loop call [processFrame] and feed resulting points to
 *     [PointCloudAccumulator].
 *  4. Call [close] when scanning stops.
 */
class ARCoreScanner(private val context: Context) {

    var session: Session? = null
        private set

    var isDepthSupported: Boolean = false
        private set

    /** True after [create] succeeds. */
    var isReady: Boolean = false
        private set

    fun create(): Result<Unit> {
        return try {
            // Check ARCore availability
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            if (availability == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE) {
                return Result.failure(UnsupportedOperationException("ARCore not supported on this device."))
            }

            val sess = Session(context)

            // Check depth support (Samsung ToF sensor)
            isDepthSupported = sess.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)

            val config = Config(sess).apply {
                depthMode = if (isDepthSupported) {
                    Config.DepthMode.RAW_DEPTH_ONLY
                } else {
                    // Fall back to regular depth (ML-estimated) for non-ToF devices
                    Config.DepthMode.AUTOMATIC
                }
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                focusMode  = Config.FocusMode.AUTO
                lightEstimationMode = Config.LightEstimationMode.DISABLED
            }
            sess.configure(config)
            session = sess
            isReady = true
            Result.success(Unit)
        } catch (e: UnavailableUserDeclinedInstallationException) {
            Result.failure(e)
        } catch (e: UnavailableApkTooOldException) {
            Result.failure(e)
        } catch (e: UnavailableSdkTooOldException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setDisplaySurface(surface: Surface, width: Int, height: Int) {
        session?.let {
            it.setDisplayGeometry(0, width, height)
        }
    }

    fun resume() = session?.resume()
    fun pause()  = session?.pause()

    /**
     * Capture one depth frame and convert to world-space points.
     * Returns null if the session is not tracking or depth is unavailable.
     *
     * @param enuOffset [east, north, up] metres from GPS origin to current position
     */
    fun processFrame(enuOffset: FloatArray): List<Point3D>? {
        val sess = session ?: return null
        val frame = try { sess.update() } catch (e: CameraNotAvailableException) { return null }

        if (frame.camera.trackingState != TrackingState.TRACKING) return null

        var depthImage: Image? = null
        var confidenceImage: Image? = null

        return try {
            depthImage = if (isDepthSupported) {
                frame.acquireRawDepthImage16Bits()
            } else {
                frame.acquireDepthImage16Bits()
            }
            confidenceImage = if (isDepthSupported) {
                frame.acquireRawDepthConfidenceImage()
            } else {
                // When using automatic depth mode confidence is always available
                frame.acquireRawDepthConfidenceImage()
            }

            DepthFrameProcessor.process(
                depthImage   = depthImage,
                confidenceImage = confidenceImage,
                colorImage   = null, // colour capture is optional
                cameraIntrinsics = frame.camera.imageIntrinsics,
                cameraPose   = frame.camera.pose,
                enuOffsetMeters = enuOffset
            )
        } catch (e: NotYetAvailableException) {
            null
        } finally {
            depthImage?.close()
            confidenceImage?.close()
        }
    }

    fun close() {
        session?.close()
        session = null
        isReady = false
    }
}
