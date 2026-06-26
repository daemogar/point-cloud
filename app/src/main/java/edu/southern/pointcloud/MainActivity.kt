package edu.southern.pointcloud

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.southern.pointcloud.ui.scan.ScanActivity
import edu.southern.pointcloud.ui.theme.PointCloudScannerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PointCloudScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen()
                }
            }
        }
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ViewInAr,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Point Cloud Scanner",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            "Samsung S26 Ultra  •  ARCore Depth",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))

        // Scanning tip card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("2-Acre Scanning Guide", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                TipRow("Walk 1.5–3 m above ground, phone at chest height")
                TipRow("Cover rows spaced ~3 m apart (like mowing)")
                TipRow("Move slowly — 0.5 m/s keeps depth frames sharp")
                TipRow("~45 min walk covers 2 acres at full coverage")
                TipRow("GPS geolocates every scan — no markers needed")
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { context.startActivity(Intent(context, ScanActivity::class.java)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Default.FiberManualRecord, null)
            Spacer(Modifier.width(8.dp))
            Text("Start Depth Scan", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Exports: LAS 1.4 (Revit) • PLY (SketchUp) • XYZ (universal)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TipRow(text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
        Text("• ", style = MaterialTheme.typography.bodySmall)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
