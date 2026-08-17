package net.supardi.evcam.ui

import android.hardware.camera2.CaptureRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
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
    minShutterSpeed: Float = 100000f,
    maxShutterSpeed: Float = 1000000000f,
    isShutterAuto: Boolean,
    onShutterChange: (Float) -> Unit,
    onShutterAutoToggle: () -> Unit,
    focusDistance: Float,
    maxFocusDistance: Float = 10f,
    isFocusAuto: Boolean,
    onFocusChange: (Float) -> Unit,
    onFocusAutoToggle: () -> Unit,
    whiteBalance: Int,
    onWhiteBalanceChange: (Int) -> Unit,
    manualKelvin: Float,
    onManualKelvinChange: (Float) -> Unit,
    onClose: () -> Unit,
    isHdrEnabled: Boolean = false,
    isNightModeEnabled: Boolean = false
) {

    val isSceneLocked = isHdrEnabled || isNightModeEnabled
    val sceneName = when {
        isHdrEnabled -> "HDR"
        isNightModeEnabled -> "Night"
        else -> ""
    }

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

        // ── ISO row: hidden when HDR or Night is active ───────────────────────
        AnimatedVisibility(
            visible = !isSceneLocked,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
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
                    modifier = Modifier.width(55.dp).clickable { onIsoAutoToggle() },
                    textAlign = TextAlign.End,
                    fontSize = 12.sp
                )

            }
        }

        // ── Shutter row: hidden when HDR or Night is active ───────────────────
        AnimatedVisibility(
            visible = !isSceneLocked,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            val minLog = kotlin.math.ln(minShutterSpeed.toDouble())
            val maxLog = kotlin.math.ln(maxShutterSpeed.toDouble())
            val currentLog = kotlin.math.ln(shutterSpeed.coerceIn(minShutterSpeed, maxShutterSpeed).toDouble())
            val sliderPos = ((currentLog - minLog) / (maxLog - minLog)).toFloat().coerceIn(0f, 1f)

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                Text("SHT", color = Color.Gray, modifier = Modifier.width(40.dp), fontSize = 12.sp)
                Slider(
                    value = sliderPos,
                    onValueChange = { pos ->
                        val logVal = minLog + pos * (maxLog - minLog)
                        val valNs = kotlin.math.exp(logVal).toFloat()
                        onShutterChange(valNs)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(
                    text = if (isShutterAuto) "AUTO" else {
                        val seconds = shutterSpeed / 1_000_000_000f
                        if (seconds >= 0.95f) {
                            String.format(Locale.US, "%.0fs", seconds)
                        } else {
                            val denominator = (1_000_000_000L / shutterSpeed.toLong().coerceAtLeast(1)).coerceAtLeast(1)
                            "1/$denominator"
                        }
                    },
                    color = if (isShutterAuto) Color.Yellow else Color.White,
                    modifier = Modifier.width(55.dp).clickable { onShutterAutoToggle() },
                    textAlign = TextAlign.End,
                    fontSize = 12.sp
                )
            }
        }


        // ── Info banner when ISO/Shutter locked by scene mode ────────────────
        AnimatedVisibility(
            visible = isSceneLocked,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF9800).copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "ISO & Shutter are automatically controlled by $sceneName mode",
                    color = Color(0xFFFF9800),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Text("FOC", color = Color.Gray, modifier = Modifier.width(40.dp), fontSize = 12.sp)
            Slider(
                value = focusDistance.coerceIn(0f, maxFocusDistance),
                onValueChange = onFocusChange,
                valueRange = 0f..maxFocusDistance,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text(
                text = if (isFocusAuto) "AUTO" else String.format(Locale.US, "%.1f", focusDistance), 
                color = if (isFocusAuto) Color.Yellow else Color.White, 
                modifier = Modifier.width(55.dp).clickable { onFocusAutoToggle() }, 
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
