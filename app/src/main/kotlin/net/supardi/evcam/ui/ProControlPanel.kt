package net.supardi.evcam.ui

import android.hardware.camera2.CaptureRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun ProControlPanel(
    iso: Float,
    minIso: Float,
    maxIso: Float,
    isIsoAuto: Boolean,
    onIsoChange: (Float) -> Unit,
    onIsoAutoToggle: () -> Unit,
    shutterSpeed: Float,
    isShutterAuto: Boolean,
    onShutterChange: (Float) -> Unit,
    onShutterAutoToggle: () -> Unit,
    focusDistance: Float,
    isFocusAuto: Boolean,
    onFocusChange: (Float) -> Unit,
    onFocusAutoToggle: () -> Unit,
    whiteBalance: Int,
    onWhiteBalanceChange: (Int) -> Unit,
    manualKelvin: Float,
    onManualKelvinChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MANUAL CONTROLS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Icon(
                imageVector = Icons.Default.Close, 
                contentDescription = "Close panel", 
                tint = Color.White,
                modifier = Modifier.size(20.dp).clickable { onClose() }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Text("ISO", color = Color.Gray, modifier = Modifier.width(40.dp), fontSize = 12.sp)
            Slider(
                value = iso.coerceIn(minIso, maxIso), 
                onValueChange = onIsoChange, 
                valueRange = minIso..maxIso, 
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text(
                text = if (isIsoAuto) "AUTO" else "${iso.toInt()}", 
                color = if (isIsoAuto) Color.Yellow else Color.White, 
                modifier = Modifier.width(40.dp).clickable { onIsoAutoToggle() }, 
                textAlign = TextAlign.End, 
                fontSize = 12.sp
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Text("SHT", color = Color.Gray, modifier = Modifier.width(40.dp), fontSize = 12.sp)
            Slider(value = shutterSpeed, onValueChange = onShutterChange, valueRange = 100000f..1000000000f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Text(
                text = if (isShutterAuto) "AUTO" else "1/${1_000_000_000L / shutterSpeed.toLong().coerceAtLeast(1)}", 
                color = if (isShutterAuto) Color.Yellow else Color.White, 
                modifier = Modifier.width(40.dp).clickable { onShutterAutoToggle() }, 
                textAlign = TextAlign.End, 
                fontSize = 12.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Text("FOC", color = Color.Gray, modifier = Modifier.width(40.dp), fontSize = 12.sp)
            Slider(value = focusDistance, onValueChange = onFocusChange, valueRange = 0f..10f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Text(
                text = if (isFocusAuto) "AUTO" else String.format(Locale.US, "%.1f", focusDistance), 
                color = if (isFocusAuto) Color.Yellow else Color.White, 
                modifier = Modifier.width(40.dp).clickable { onFocusAutoToggle() }, 
                textAlign = TextAlign.End, 
                fontSize = 12.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("AWB", color = Color.Gray, modifier = Modifier.width(40.dp), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = "AUTO",
                    color = if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_AUTO) Color.Black else Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_AUTO) Color.Yellow else Color.White.copy(alpha = 0.2f))
                        .clickable { onWhiteBalanceChange(CaptureRequest.CONTROL_AWB_MODE_AUTO) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_AUTO) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "DAY",
                    color = if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT) Color.Black else Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT) Color.Yellow else Color.White.copy(alpha = 0.2f))
                        .clickable { onWhiteBalanceChange(CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "CLD",
                    color = if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT) Color.Black else Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT) Color.Yellow else Color.White.copy(alpha = 0.2f))
                        .clickable { onWhiteBalanceChange(CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "CUS",
                    color = if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_OFF) Color.Black else Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_OFF) Color.Yellow else Color.White.copy(alpha = 0.2f))
                        .clickable { onWhiteBalanceChange(CaptureRequest.CONTROL_AWB_MODE_OFF) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_OFF) FontWeight.Bold else FontWeight.Normal
                )
            }
            if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_OFF) {
                Slider(
                    value = manualKelvin,
                    onValueChange = onManualKelvinChange,
                    valueRange = 2000f..10000f,
                    modifier = Modifier.height(30.dp)
                )
                Text("${manualKelvin.toInt()}K", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}
