package edu.southern.pointcloud.scanning

import android.media.Image
import com.google.ar.core.CameraIntrinsics
import com.google.ar.core.Pose
import edu.southern.pointcloud.data.models.Point3D
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Converts a single ARCore Raw Depth frame into world-space Point3D values.
 *
 * ARCore provides depth in millimetres as uint16.  We back-project each pixel
 * into camera space, then transform to ARCore world space via [cameraPose],
 * then shift by the ENU offset from the session GPS origin to produce points
 * that are directly usable in LAS / PLY export.
 *
 * Step-down factor of 4 (every 4th pixel) keeps memory manageable while
 * walking 2 acres; reduce to 2 for higher density at the cost of ~4× RAM.
 */
object DepthFrameProcessor {

    private const val STEP = 4
    private const val MIN_DEPTH_MM = 200       // 20 cm — ignore nose-tip noise
    private const val MAX_DEPTH_MM = 8_000     // 8 m  — ToF sensor practical limit
    private const val MIN_CONFIDENCE = 50      // 0–255 ARCore confidence threshold

    fun process(
        depthImage: Image,
        confidenceImage: Image,
        colorImage: Image?,
        cameraIntrinsics: CameraIntrinsics,
        cameraPose: Pose,
        enuOffsetMeters: FloatArray  // [east, north, up] from GPS origin
    ): List<Point3D> {

        val width  = depthImage.width
        val height = depthImage.height

        val depthBuf = depthImage.planes[0].buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val confBuf  = confidenceImage.planes[0].buffer

        val fx = cameraIntrinsics.focalLength[0]
        val fy = cameraIntrinsics.focalLength[1]
        val cx = cameraIntrinsics.principalPoint[0]
        val cy = cameraIntrinsics.principalPoint[1]

        // Pre-compute camera→world rotation+translation from pose matrix
        val m = FloatArray(16)
        cameraPose.toMatrix(m, 0)

        val points = ArrayList<Point3D>((width / STEP) * (height / STEP) / 2)

        for (row in 0 until height step STEP) {
            for (col in 0 until width step STEP) {
                val idx = row * width + col

                val depthMm   = depthBuf.get(idx).toInt() and 0xFFFF
                val confidence = confBuf.get(idx).toInt() and 0xFF

                if (depthMm < MIN_DEPTH_MM || depthMm > MAX_DEPTH_MM) continue
                if (confidence < MIN_CONFIDENCE) continue

                val depthM = depthMm / 1000f

                // Back-project: camera-space coords (right-handed, Z into scene)
                val camX = (col - cx) * depthM / fx
                val camY = (row - cy) * depthM / fy
                val camZ = depthM

                // Transform camera → ARCore world space (column-major 4×4 matrix)
                val wx = m[0] * camX + m[4] * camY + m[8]  * camZ + m[12]
                val wy = m[1] * camX + m[5] * camY + m[9]  * camZ + m[13]
                val wz = m[2] * camX + m[6] * camY + m[10] * camZ + m[14]

                // Shift from ARCore origin to GPS-referenced ENU space
                val ex = wx + enuOffsetMeters[0]
                val ny = wy + enuOffsetMeters[1]
                val uz = wz + enuOffsetMeters[2]

                // Sample colour from JPEG-compressed color image plane if available
                val (r, g, b) = sampleColor(colorImage, col, row, width, height)

                // Confidence → 16-bit intensity (0–65535)
                val intensity = (confidence * 257).coerceIn(0, 65535)

                points.add(Point3D(ex, ny, uz, r, g, b, intensity))
            }
        }

        return points
    }

    private fun sampleColor(
        image: Image?,
        col: Int,
        row: Int,
        depthW: Int,
        depthH: Int
    ): Triple<Byte, Byte, Byte> {
        if (image == null) return Triple(127.toByte(), 127.toByte(), 127.toByte())

        // ARCore color image is typically YUV_420_888; scale coords to color image size
        val cW = image.width
        val cH = image.height
        val cx = (col.toFloat() / depthW * cW).roundToInt().coerceIn(0, cW - 1)
        val cy = (row.toFloat() / depthH * cH).roundToInt().coerceIn(0, cH - 1)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yPixel  = yPlane.buffer.get(cy * yPlane.rowStride + cx * yPlane.pixelStride).toInt() and 0xFF
        val uvRow   = cy / 2
        val uvCol   = cx / 2
        val uPixel  = uPlane.buffer.get(uvRow * uPlane.rowStride + uvCol * uPlane.pixelStride).toInt() and 0xFF
        val vPixel  = vPlane.buffer.get(uvRow * vPlane.rowStride + uvCol * vPlane.pixelStride).toInt() and 0xFF

        val r = (yPixel + 1.402   * (vPixel - 128)).toInt().coerceIn(0, 255).toByte()
        val g = (yPixel - 0.344136 * (uPixel - 128) - 0.714136 * (vPixel - 128)).toInt().coerceIn(0, 255).toByte()
        val b = (yPixel + 1.772   * (uPixel - 128)).toInt().coerceIn(0, 255).toByte()

        return Triple(r, g, b)
    }
}
