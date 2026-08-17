package net.supardi.evcam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .androidx.compose.ui.input.pointer.pointerInput(Unit) {
                androidx.compose.foundation.gestures.detectTapGestures { }
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
                onClick = onOpenCameraInfo,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("Hardware Camera Info", color = Color.White, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

