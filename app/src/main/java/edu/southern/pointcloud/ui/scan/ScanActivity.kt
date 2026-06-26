package edu.southern.pointcloud.ui.scan

import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import edu.southern.pointcloud.R
import edu.southern.pointcloud.export.ExportFormat
import edu.southern.pointcloud.export.MeshResolution
import edu.southern.pointcloud.ui.theme.PointCloudScannerTheme
import kotlinx.coroutines.launch
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ScanActivity : AppCompatActivity() {

    private val viewModel: ScanViewModel by viewModels()
    private lateinit var glSurfaceView: GLSurfaceView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            initScanning()
        } else {
            Toast.makeText(this, "Camera and Location permissions are required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        setupGlSurface()

        val composeOverlay = findViewById<ComposeView>(R.id.composeOverlay)
        composeOverlay.setContent {
            PointCloudScannerTheme {
                ScanOverlay(viewModel = viewModel)
            }
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val needed = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) initScanning() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun initScanning() {
        if (!viewModel.initARCore()) {
            Toast.makeText(this, "ARCore initialization failed — see logs", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupGlSurface() {
        glSurfaceView.preserveEGLContextOnPause = true
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setRenderer(object : GLSurfaceView.Renderer {
            override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
                gl.glClearColor(0.1f, 0.1f, 0.1f, 1f)
            }
            override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
                gl.glViewport(0, 0, width, height)
                viewModel.scanner.setDisplaySurface(
                    glSurfaceView.holder.surface, width, height
                )
            }
            override fun onDrawFrame(gl: GL10) {
                gl.glClear(GL10.GL_COLOR_BUFFER_BIT or GL10.GL_DEPTH_BUFFER_BIT)
                viewModel.onFrame()
            }
        })
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
        viewModel.stopScanning()
    }
}

@Composable
fun ScanOverlay(viewModel: ScanViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExportDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Top status bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${"%,d".format(state.pointCount)} pts",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Coverage: ${state.coveragePercent}%  (${
                    "%.0f".format(state.coverageM2)} m²)",
                color = Color(0xFF90EE90),
                style = MaterialTheme.typography.bodyMedium
            )
            if (state.currentGps != null) {
                Text(
                    text = "GPS: %.5f, %.5f  dist %.1f m".format(
                        state.currentGps!!.latitude,
                        state.currentGps!!.longitude,
                        state.distanceFromOriginM
                    ),
                    color = Color(0xFFADD8E6),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!state.isDepthSupported) {
                Text("⚠ Hardware depth not detected — using ML depth",
                    color = Color.Yellow, style = MaterialTheme.typography.bodySmall)
            }
        }

        // Coverage progress bar
        LinearProgressIndicator(
            progress = { state.coveragePercent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 100.dp, start = 16.dp, end = 16.dp),
            color = MaterialTheme.colorScheme.primary
        )

        // Bottom controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clear button
            FilledTonalIconButton(
                onClick = { viewModel.clearScan() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0xFFB22222).copy(alpha = 0.85f)
                )
            ) {
                Icon(Icons.Default.Delete, "Clear", tint = Color.White)
            }

            // Start / Stop
            FloatingActionButton(
                onClick = {
                    if (state.isScanning) viewModel.stopScanning()
                    else viewModel.startScanning()
                },
                containerColor = if (state.isScanning)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (state.isScanning) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = if (state.isScanning) "Stop" else "Scan",
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }

            // Export button (only when we have points)
            FilledTonalIconButton(
                onClick = { showExportDialog = true },
                enabled = state.pointCount > 0,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0xFF2E8B57).copy(alpha = 0.85f)
                )
            ) {
                Icon(Icons.Default.FileUpload, "Export", tint = Color.White)
            }
        }

        // Export progress overlay
        if (state.exportProgress in 1..99) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(progress = { state.exportProgress / 100f })
                    Spacer(Modifier.height(16.dp))
                    Text("Exporting ${state.exportProgress}%", color = Color.White)
                }
            }
        }

        // Export success card
        state.exportResult?.takeIf { state.exportProgress == 100 }?.let { result ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E8B57))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(result.file.name, style = MaterialTheme.typography.bodyMedium)
                        Text("${"%,d".format(result.pointCount)} points exported",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { viewModel.shareExport() }) {
                        Icon(Icons.Default.Share, "Share")
                    }
                }
            }
        }
    }

    // Format picker dialog
    if (showExportDialog) {
        ExportFormatDialog(
            pointCount = state.pointCount,
            onFormat = { fmt, res ->
                showExportDialog = false
                viewModel.exportPointCloud(fmt, res)
            },
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
fun ExportFormatDialog(
    pointCount: Long,
    onFormat: (ExportFormat, MeshResolution) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedResolution by remember { mutableStateOf(MeshResolution.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export ${"%,d".format(pointCount)} points") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {

                // ── SketchUp Make 2017 section ──────────────────────────────
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "SketchUp Make 2017",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(6.dp))

                        // OBJ terrain mesh (recommended, no plugin)
                        Button(
                            onClick = { onFormat(ExportFormat.OBJ_TERRAIN, selectedResolution) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Terrain Mesh  (.obj)")
                                Text(
                                    "File › Import — no plugin needed",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        // Resolution picker for terrain mesh
                        Spacer(Modifier.height(4.dp))
                        Text("Grid resolution:", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        MeshResolution.entries.forEach { res ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = selectedResolution == res,
                                    onClick  = { selectedResolution = res }
                                )
                                Text(res.label, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))

                        // Downsampled XYZ for TIG plugin
                        OutlinedButton(
                            onClick = { onFormat(ExportFormat.XYZ_SKETCHUP, selectedResolution) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Point Cloud  (.xyz, ≤50 K pts)")
                                Text(
                                    "Requires TIG PointCloudMaker plugin (free)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Other software section ──────────────────────────────────
                Text("Other software", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = { onFormat(ExportFormat.LAS, selectedResolution) },
                    Modifier.fillMaxWidth()) {
                    Text("LAS 1.4  — Revit / AutoCAD Civil 3D")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = { onFormat(ExportFormat.PLY, selectedResolution) },
                    Modifier.fillMaxWidth()) {
                    Text("PLY  — MeshLab / CloudCompare")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = { onFormat(ExportFormat.XYZ, selectedResolution) },
                    Modifier.fillMaxWidth()) {
                    Text("XYZ  — ASCII universal")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
