package app.maw629.homerelay.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

fun fileBadgeColor(extension: String): Color = when (extension.trim().uppercase()) {
    "PDF" -> Color(0xFFD32F2F)
    "DOC", "DOCX" -> Color(0xFF1976D2)
    "XLS", "XLSX" -> Color(0xFF388E3C)
    "PPT", "PPTX" -> Color(0xFFEF6C00)
    "ZIP", "RAR", "7Z" -> Color(0xFF7B1FA2)
    "JPG", "JPEG", "PNG", "GIF", "WEBP", "BMP", "HEIC" -> Color(0xFF00897B)
    else -> Color(0xFF616161)
}

@Composable
fun FileTypeBadge(monogram: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(44.dp).testTag("fileBadge"),
        shape = RoundedCornerShape(8.dp),
        color = color
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = monogram,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
