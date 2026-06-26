package edu.southern.pointcloud.export

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File

/**
 * Uploads an exported file directly to Google Drive or OneDrive.
 *
 * Both apps expose a standard Android share target, so no API key or
 * OAuth setup is required — the user's existing app login is used.
 *
 * If neither cloud app is installed, falls back to the full system
 * share sheet so the user can pick any available destination.
 */
object CloudExportManager {

    // Google Drive package / activity
    private const val DRIVE_PKG      = "com.google.android.apps.docs"
    private const val DRIVE_ACTIVITY = "com.google.android.apps.docs.app.ShareIntentDisambiguationActivity"

    // OneDrive package
    private const val ONEDRIVE_PKG   = "com.microsoft.skydrive"

    enum class CloudTarget { GOOGLE_DRIVE, ONEDRIVE, ANY }

    /**
     * Share [file] to [target] cloud storage. If the targeted app is not
     * installed the system share sheet is shown instead.
     */
    fun share(context: Context, file: File, target: CloudTarget) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mime = when {
            file.name.endsWith(".obj") || file.name.endsWith(".xyz") -> "text/plain"
            else -> "application/octet-stream"
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Point Cloud Scan — ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val intent = when (target) {
            CloudTarget.GOOGLE_DRIVE -> {
                if (isInstalled(context, DRIVE_PKG)) {
                    send.apply { setPackage(DRIVE_PKG) }
                } else {
                    fallbackChooser(context, send, "Upload to Google Drive")
                }
            }
            CloudTarget.ONEDRIVE -> {
                if (isInstalled(context, ONEDRIVE_PKG)) {
                    send.apply { setPackage(ONEDRIVE_PKG) }
                } else {
                    fallbackChooser(context, send, "Upload to OneDrive")
                }
            }
            CloudTarget.ANY -> fallbackChooser(context, send, "Share Point Cloud")
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun isDriveInstalled(context: Context)     = isInstalled(context, DRIVE_PKG)
    fun isOneDriveInstalled(context: Context)  = isInstalled(context, ONEDRIVE_PKG)

    private fun isInstalled(context: Context, pkg: String): Boolean =
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) { false }

    private fun fallbackChooser(context: Context, send: Intent, title: String): Intent =
        Intent.createChooser(send, title)
}
