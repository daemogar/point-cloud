package edu.southern.pointcloud.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary   = Color(0xFF4FC3F7),
    secondary = Color(0xFF81C784),
    tertiary  = Color(0xFFFFB74D),
    background = Color(0xFF121212),
    surface    = Color(0xFF1E1E1E),
    onPrimary  = Color.Black,
    onBackground = Color.White,
    onSurface  = Color.White
)

@Composable
fun PointCloudScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography(),
        content     = content
    )
}
