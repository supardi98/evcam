package net.supardi.evcam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.supardi.evcam.logic.*


@Composable
fun DisplayOverlaysPanel(
    gridType: GridType,
    onGridTypeChange: (GridType) -> Unit,
    showVirtualHorizon: Boolean,
    onVirtualHorizonChange: (Boolean) -> Unit,
    showCinemaGuide: Boolean = false,
    onCinemaGuideChange: (Boolean) -> Unit = {},
    enableHistogram: Boolean,
    onHistogramChange: (Boolean) -> Unit,
    enableFocusPeaking: Boolean,
    onFocusPeakingChange: (Boolean) -> Unit,
    isFaceDetectionEnabled: Boolean,
    onFaceDetectionEnabledChange: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) {
                detectTapGestures { }
            }
            .padding(16.dp)

    ) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("DISPLAY OVERLAYS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(20.dp).clickable { onClose() }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Grid", color = Color.Gray, modifier = Modifier.width(100.dp), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                GridType.values().forEach { g ->
                    val isSelected = gridType == g
                    Text(
                        text = g.label,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.2f))
                            .clickable { onGridTypeChange(g) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Level", color = Color.Gray, modifier = Modifier.width(100.dp), fontSize = 12.sp)
            Switch(
                checked = showVirtualHorizon,
                onCheckedChange = onVirtualHorizonChange,
                modifier = Modifier.scale(0.8f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("21:9 Cinema", color = Color.Gray, modifier = Modifier.width(100.dp), fontSize = 12.sp)
            Switch(
                checked = showCinemaGuide,
                onCheckedChange = onCinemaGuideChange,
                modifier = Modifier.scale(0.8f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Histogram", color = Color.Gray, modifier = Modifier.width(100.dp), fontSize = 12.sp)
            Switch(
                checked = enableHistogram,
                onCheckedChange = onHistogramChange,
                modifier = Modifier.scale(0.8f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Focus Peaking", color = Color.Gray, modifier = Modifier.width(100.dp), fontSize = 12.sp)
            Switch(
                checked = enableFocusPeaking,
                onCheckedChange = onFocusPeakingChange,
                modifier = Modifier.scale(0.8f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Face Detection", color = Color.Gray, modifier = Modifier.width(100.dp), fontSize = 12.sp)
            Switch(
                checked = isFaceDetectionEnabled,
                onCheckedChange = onFaceDetectionEnabledChange,
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}
