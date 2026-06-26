package edu.southern.pointcloud.ui.scan

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.southern.pointcloud.data.models.GeoPoint
import edu.southern.pointcloud.data.models.Point3D
import edu.southern.pointcloud.data.models.ScanMode
import edu.southern.pointcloud.export.ExportFormat
import edu.southern.pointcloud.export.ExportManager
import edu.southern.pointcloud.export.ExportResult
import edu.southern.pointcloud.location.LocationTracker
import edu.southern.pointcloud.scanning.ARCoreScanner
import edu.southern.pointcloud.scanning.PointCloudAccumulator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ScanUiState(
    val isScanning: Boolean = false,
    val pointCount: Long = 0L,
    val coverageM2: Double = 0.0,
    val gpsOrigin: GeoPoint? = null,
    val currentGps: GeoPoint? = null,
    val isDepthSupported: Boolean = false,
    val arcoreError: String? = null,
    val exportProgress: Int = 0,
    val exportResult: ExportResult? = null
) {
    val coveragePercent: Int get() = ((coverageM2 / 8_094.0) * 100).toInt().coerceAtMost(100)
    val distanceFromOriginM: Double
        get() = if (gpsOrigin != null && currentGps != null)
            GeoPoint.distanceMeters(gpsOrigin, currentGps) else 0.0
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    val accumulator = PointCloudAccumulator()
    val scanner = ARCoreScanner(app)
    private val locationTracker = LocationTracker(app)
    private val exportManager = ExportManager(app)

    private var gpsJob: Job? = null
    private var currentEnu = FloatArray(3)

    init {
        viewModelScope.launch {
            accumulator.pointCount.collect { n ->
                _uiState.update { it.copy(pointCount = n) }
            }
        }
        viewModelScope.launch {
            accumulator.coverageM2.collect { m2 ->
                _uiState.update { it.copy(coverageM2 = m2) }
            }
        }
    }

    fun initARCore(): Boolean {
        val result = scanner.create()
        return if (result.isSuccess) {
            _uiState.update { it.copy(isDepthSupported = scanner.isDepthSupported) }
            true
        } else {
            _uiState.update { it.copy(arcoreError = result.exceptionOrNull()?.message) }
            false
        }
    }

    fun startScanning() {
        scanner.resume()
        _uiState.update { it.copy(isScanning = true) }

        gpsJob = viewModelScope.launch {
            locationTracker.locationFlow.collect { gps ->
                val origin = _uiState.value.gpsOrigin ?: gps.also {
                    _uiState.update { s -> s.copy(gpsOrigin = gps) }
                }
                val (e, n, u) = GeoPoint.toEnuOffset(origin, gps)
                currentEnu[0] = e.toFloat()
                currentEnu[1] = n.toFloat()
                currentEnu[2] = u.toFloat()
                _uiState.update { it.copy(currentGps = gps) }
            }
        }
    }

    fun stopScanning() {
        scanner.pause()
        gpsJob?.cancel()
        _uiState.update { it.copy(isScanning = false) }
    }

    /** Called from the GL render thread — must be fast. */
    fun onFrame() {
        if (!_uiState.value.isScanning) return
        val pts = scanner.processFrame(currentEnu.copyOf()) ?: return
        if (pts.isNotEmpty()) accumulator.addPoints(pts)
    }

    fun exportPointCloud(format: ExportFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            val points = accumulator.snapshot()
            if (points.isEmpty()) return@launch
            val result = exportManager.export(
                points   = points,
                format   = format,
                gpsOrigin = _uiState.value.gpsOrigin,
                onProgress = { p -> _uiState.update { it.copy(exportProgress = p) } }
            )
            _uiState.update { it.copy(exportResult = result, exportProgress = 100) }
        }
    }

    fun shareExport() {
        _uiState.value.exportResult?.let { exportManager.shareFile(it) }
    }

    fun clearScan() {
        accumulator.clear()
        currentEnu = FloatArray(3)
        _uiState.update {
            ScanUiState(isDepthSupported = it.isDepthSupported)
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanner.close()
    }
}
