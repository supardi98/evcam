package net.supardi.evcam.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import net.supardi.evcam.logic.SystemStatsMonitor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun SettingsPanel(
    enableRawCapture: Boolean,
    onEnableRawCaptureChange: (Boolean) -> Unit,
    isEisSupported: Boolean = true,
    enableEis: Boolean,
    onEnableEisChange: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    maxBrightness: Boolean,
    onMaxBrightnessChange: (Boolean) -> Unit,
    volumeShutterEnabled: Boolean,
    onVolumeShutterEnabledChange: (Boolean) -> Unit,
    mirrorSelfie: Boolean = true,
    onMirrorSelfieChange: (Boolean) -> Unit = {},
    isShutterSoundEnabled: Boolean,

    onIsShutterSoundEnabledChange: (Boolean) -> Unit,
    enableGeotagging: Boolean,
    onEnableGeotaggingChange: (Boolean) -> Unit,
    onOpenWatermarkSettings: () -> Unit,
    onOpenPluginManager: () -> Unit,
    onOpenCameraInfo: () -> Unit,
    onOpenHdrTuning: () -> Unit = {},
    onOpenWebcamSettings: () -> Unit = {},
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
            Text("SETTINGS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(20.dp).clickable { onClose() }
            )
        }

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .heightIn(max = 280.dp)
                .verticalScroll(scrollState)
                .drawWithContent {
                    drawContent()

                    val scrollFraction = if (scrollState.maxValue > 0)
                        scrollState.value.toFloat() / scrollState.maxValue else 0f
                    val canScrollMore = scrollState.canScrollForward

                    // Bottom fade gradient — hints more content below
                    if (canScrollMore) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                                startY = size.height * 0.72f,
                                endY = size.height
                            )
                        )
                    }

                    // Scrollbar track + thumb (only when scrollable)
                    if (scrollState.maxValue > 0) {
                        val trackWidth = 3.dp.toPx()
                        val trackX = size.width - trackWidth
                        val thumbHeightPx = size.height * 0.35f
                        val maxThumbOffset = size.height - thumbHeightPx
                        val thumbOffsetY = scrollFraction * maxThumbOffset

                        // Track background
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.08f),
                            topLeft = Offset(trackX, 0f),
                            size = Size(trackWidth, size.height),
                            cornerRadius = CornerRadius(trackWidth / 2)
                        )
                        // Thumb
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.5f),
                            topLeft = Offset(trackX, thumbOffsetY),
                            size = Size(trackWidth, thumbHeightPx),
                            cornerRadius = CornerRadius(trackWidth / 2)
                        )
                    }
                }
                .padding(end = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RAW (DNG) Capture", color = Color.White, fontSize = 13.sp)
                Switch(checked = enableRawCapture, onCheckedChange = onEnableRawCaptureChange, modifier = Modifier.scale(0.8f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Keep Screen On", color = Color.White, fontSize = 13.sp)
                Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOnChange, modifier = Modifier.scale(0.8f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Max Brightness", color = Color.White, fontSize = 13.sp)
                Switch(checked = maxBrightness, onCheckedChange = onMaxBrightnessChange, modifier = Modifier.scale(0.8f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Volume Key Shutter", color = Color.White, fontSize = 13.sp)
                Switch(checked = volumeShutterEnabled, onCheckedChange = onVolumeShutterEnabledChange, modifier = Modifier.scale(0.8f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mirror Front Selfie Photo", color = Color.White, fontSize = 13.sp)
                Switch(checked = mirrorSelfie, onCheckedChange = {
                    onMirrorSelfieChange(it)
                }, modifier = Modifier.scale(0.8f))
            }


            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Shutter Sound", color = Color.White, fontSize = 13.sp)
                Switch(checked = isShutterSoundEnabled, onCheckedChange = onIsShutterSoundEnabledChange, modifier = Modifier.scale(0.8f))
            }

            if (isEisSupported) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Video Stabilization (EIS)", color = Color.White, fontSize = 13.sp)
                    Switch(checked = enableEis, onCheckedChange = onEnableEisChange, modifier = Modifier.scale(0.8f))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Save Location (Geotag EXIF)", color = Color.White, fontSize = 13.sp)
                Switch(checked = enableGeotagging, onCheckedChange = onEnableGeotaggingChange, modifier = Modifier.scale(0.8f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Watermark", color = Color.White, fontSize = 13.sp)
                Button(
                    onClick = onOpenWatermarkSettings,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Edit", color = Color.White, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onOpenPluginManager,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("Open Plugin Manager", color = Color.White, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onOpenHdrTuning,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Yellow)
            ) {
                Text("HDR+ Tuning Studio", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onOpenWebcamSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Yellow)
            ) {
                Text("IP Webcam & Live Stream", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onOpenCameraInfo,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("Hardware Camera Info", color = Color.White, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            // System Hardware Resource Performance Card
            SystemHardwareCard()

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SystemHardwareCard() {
    var stats by remember { mutableStateOf(SystemStatsMonitor.getResourceStats()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            stats = SystemStatsMonitor.getResourceStats()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0x33000000),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Performance Monitor",
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "LIVE",
                    color = Color.Green,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // CPU Usage Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CPU Usage", color = Color.LightGray, fontSize = 11.sp)
                Text("${String.format("%.1f", stats.cpuUsagePercent)}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = (stats.cpuUsagePercent / 100f).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = if (stats.cpuUsagePercent > 75) Color.Red else Color(0xFF00E676),
                trackColor = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(8.dp))

            // RAM & Heap Memory Usage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("RAM PSS Used", color = Color.LightGray, fontSize = 10.sp)
                    Text("${stats.ramUsedMb} MB", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("JVM Heap Alloc", color = Color.LightGray, fontSize = 10.sp)
                    Text("${stats.heapUsedMb} MB / ${stats.ramTotalMb} MB", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // GPU Hardware Decoder Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("GPU Codec Acceleration", color = Color.LightGray, fontSize = 10.sp)
                Text("Active (MediaCodec HW)", color = Color(0xFF00E676), fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
            }
        }
    }
}


