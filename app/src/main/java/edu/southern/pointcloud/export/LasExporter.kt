package edu.southern.pointcloud.export

import edu.southern.pointcloud.data.models.GeoPoint
import edu.southern.pointcloud.data.models.Point3D
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate

/**
 * Writes a LAS 1.4 file (Point Data Record Format 2 — X Y Z Intensity R G B).
 *
 * Revit, AutoCAD, and Civil 3D open LAS natively.  SketchUp requires the
 * "Point Cloud Library" plugin.
 *
 * Coordinate encoding: geographic ENU metres are stored as int32 with
 * scale = 0.001 and offset = the session GPS origin converted to projected
 * metres.  This keeps integer values in a 32-bit range for the entire
 * 2-acre property.
 */
object LasExporter {

    private const val SCALE = 0.001  // 1 mm resolution

    // LAS 1.4 header is exactly 375 bytes; PDRF 2 record is 26 bytes
    private const val HEADER_SIZE = 375
    private const val POINT_RECORD_LENGTH = 26

    fun export(
        points: List<Point3D>,
        outFile: File,
        gpsOrigin: GeoPoint? = null,
        onProgress: (Int) -> Unit = {}
    ) {
        outFile.parentFile?.mkdirs()
        val n = points.size.toLong()

        // Compute bounds (used in header)
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        var minZ = Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE

        for (p in points) {
            if (p.x < minX) minX = p.x.toDouble(); if (p.x > maxX) maxX = p.x.toDouble()
            if (p.y < minY) minY = p.y.toDouble(); if (p.y > maxY) maxY = p.y.toDouble()
            if (p.z < minZ) minZ = p.z.toDouble(); if (p.z > maxZ) maxZ = p.z.toDouble()
        }

        // Offsets anchor the int32 range around the actual data
        val offX = minX
        val offY = minY
        val offZ = if (minZ.isFinite()) minZ else 0.0

        RandomAccessFile(outFile, "rw").use { raf ->
            raf.setLength(HEADER_SIZE.toLong() + n * POINT_RECORD_LENGTH)

            val header = buildHeader(n, offX, offY, offZ, minX, maxX, minY, maxY, minZ, maxZ)
            raf.seek(0)
            raf.write(header.array())

            // Write point records
            val chunkSize = 8192
            val buf = ByteBuffer.allocate(POINT_RECORD_LENGTH * chunkSize).order(ByteOrder.LITTLE_ENDIAN)
            var reported = 0

            for ((i, p) in points.withIndex()) {
                val ix = ((p.x - offX) / SCALE).toLong().toInt()
                val iy = ((p.y - offY) / SCALE).toLong().toInt()
                val iz = ((p.z - offZ) / SCALE).toLong().toInt()

                buf.putInt(ix)
                buf.putInt(iy)
                buf.putInt(iz)
                buf.putShort(p.intensity.toShort())           // Intensity
                buf.put(0x01.toByte())                         // Return number flags
                buf.put(0x00.toByte())                         // Classification (unclassified)
                buf.put(0x00.toByte())                         // User data
                buf.putShort(0)                                // Point source ID
                // PDRF2 colour channels (uint16, scale 0–65535)
                buf.putShort(((p.r.toInt() and 0xFF) * 257).toShort())
                buf.putShort(((p.g.toInt() and 0xFF) * 257).toShort())
                buf.putShort(((p.b.toInt() and 0xFF) * 257).toShort())

                if (!buf.hasRemaining()) {
                    raf.write(buf.array(), 0, buf.position())
                    buf.clear()
                }

                val progress = (i + 1) * 100 / points.size
                if (progress != reported) { reported = progress; onProgress(progress) }
            }

            if (buf.position() > 0) raf.write(buf.array(), 0, buf.position())
        }
    }

    private fun buildHeader(
        n: Long,
        offX: Double, offY: Double, offZ: Double,
        minX: Double, maxX: Double,
        minY: Double, maxY: Double,
        minZ: Double, maxZ: Double
    ): ByteBuffer {
        val h = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)

        fun putStringPadded(s: String, len: Int) {
            val bytes = s.toByteArray(Charsets.US_ASCII).copyOf(len)
            h.put(bytes)
        }

        val today = LocalDate.now()

        h.put("LASF".toByteArray())              // File signature (4)
        h.putShort(0)                             // File source ID (2)
        h.putShort(0x0011)                        // Global encoding (2)
        // Project ID — GUID: uint32 + uint16 + uint16 + uint8[8] = 16 bytes
        h.putInt(0); h.putShort(0); h.putShort(0); h.put(ByteArray(8))
        h.put(1); h.put(4)                        // Version 1.4 (2)
        putStringPadded("PointCloudScanner", 32)  // System identifier
        putStringPadded("Samsung S26 Ultra App", 32) // Generating software
        h.putShort(today.dayOfYear.toShort())     // File creation DOY
        h.putShort(today.year.toShort())          // File creation year
        h.putShort(HEADER_SIZE.toShort())         // Header size
        h.putInt(HEADER_SIZE)                     // Offset to point data
        h.putInt(0)                               // Number of VLRs
        h.put(2)                                  // Point Data Format ID 2 (XYZ + intensity + RGB)
        h.putShort(POINT_RECORD_LENGTH.toShort()) // Point data record length
        h.putInt(if (n <= 0xFFFFFFFFL) n.toInt() else 0) // Legacy point count
        repeat(5) { h.putInt(if (it == 0) (if (n <= 0xFFFFFFFFL) n.toInt() else 0) else 0) } // Legacy returns

        h.putDouble(SCALE); h.putDouble(SCALE); h.putDouble(SCALE) // XYZ scale
        h.putDouble(offX);  h.putDouble(offY);  h.putDouble(offZ)  // XYZ offset

        h.putDouble(maxX); h.putDouble(minX)
        h.putDouble(maxY); h.putDouble(minY)
        h.putDouble(maxZ); h.putDouble(minZ)

        // LAS 1.4 extended fields (bytes 227–374)
        h.putLong(0)   // Start of first Extended VLR (8)
        h.putLong(0)   // Number of Extended VLRs (8)
        h.putLong(n)   // Number of point records 64-bit (8)
        repeat(15) { h.putLong(if (it == 0) n else 0L) } // Returns 1-15 (120)
        h.putInt(0)    // Reserved / user data (4)  → total = 375

        return h
    }
}
