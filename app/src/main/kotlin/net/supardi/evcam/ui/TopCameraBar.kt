package net.supardi.evcam.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.supardi.evcam.logic.*

@Composable
fun TopCameraBar(
    uiState: CameraUiState,
    modifier: Modifier = Modifier
) {
    var isSceneOptionsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
        // Baris Atas Utama
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Group Kiri Utama
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.hasFlashSupport) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .pointerInput(uiState.cameraMode) {
                                detectTapGestures(
                                    onTap = {
                                        if (uiState.cameraMode == CameraMode.VIDEO) {
                                            uiState.isTorchOn = !uiState.isTorchOn
                                        } else {
                                            if (uiState.isTorchOn) {
                                                uiState.isTorchOn = false
                                            } else {
                                                uiState.flashMode = when (uiState.flashMode) {
                                                    FlashMode.AUTO -> FlashMode.ON
                                                    FlashMode.ON -> FlashMode.OFF
                                                    FlashMode.OFF -> FlashMode.AUTO
                                                }
                                            }
                                        }
                                    },
                                    onLongPress = {
                                        if (uiState.cameraMode == CameraMode.PHOTO) {
                                            uiState.isTorchOn = !uiState.isTorchOn
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val flashIcon = if (uiState.cameraMode == CameraMode.VIDEO) {
                            if (uiState.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff
                        } else {
                            if (uiState.isTorchOn) {
                                Icons.Default.FlashOn
                            } else {
                                when (uiState.flashMode) {
                                    FlashMode.AUTO -> Icons.Default.FlashAuto
                                    FlashMode.ON -> Icons.Default.FlashOn
                                    FlashMode.OFF -> Icons.Default.FlashOff
                                }
                            }
                        }
                        val iconTint = if (uiState.isTorchOn || (uiState.cameraMode == CameraMode.PHOTO && uiState.flashMode == FlashMode.ON)) Color.Yellow else Color.White
                        Icon(imageVector = flashIcon, contentDescription = "Flash", tint = iconTint)
                    }
                }
                
                if (uiState.cameraMode == CameraMode.VIDEO && !uiState.isRecording) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { uiState.videoAudioEnabled = !uiState.videoAudioEnabled },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.videoAudioEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = if (uiState.videoAudioEnabled) "Audio On" else "Audio Off",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (uiState.cameraMode == CameraMode.PHOTO) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable {
                                uiState.showFilterDialog = !uiState.showFilterDialog
                                if (uiState.showFilterDialog) {
                                    uiState.showProPanel = false
                                    uiState.showLayerPanel = false
                                    uiState.showSettings = false
                                }
                            },

                        contentAlignment = Alignment.Center
                    ) {
                        val backgroundAlpha = if (uiState.selectedFilter != ColorFilterMode.NORMAL) 0.3f else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Yellow.copy(alpha = backgroundAlpha)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Palette,
                                contentDescription = "Color Filter",
                                tint = if (uiState.selectedFilter != ColorFilterMode.NORMAL) Color.Yellow else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                
                if (uiState.cameraMode == CameraMode.PHOTO) {
                    Spacer(modifier = Modifier.width(4.dp))
                    val isTimerActive = uiState.timerMode != TimerMode.OFF
                    val timerTint = if (isTimerActive) Color.Yellow else Color.White

                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (isTimerActive) Color.Yellow.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable {
                                uiState.timerMode = when (uiState.timerMode) {
                                    TimerMode.OFF -> TimerMode.SEC_3
                                    TimerMode.SEC_3 -> TimerMode.SEC_10
                                    TimerMode.SEC_10 -> TimerMode.SEC_15
                                    TimerMode.SEC_15 -> TimerMode.SEC_20
                                    TimerMode.SEC_20 -> if (uiState.isHandTrackingInstalled && uiState.isHandTrackingEnabled) TimerMode.PEACE else TimerMode.OFF
                                    TimerMode.PEACE -> TimerMode.OFF
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            val icon = if (uiState.timerMode == TimerMode.PEACE) Icons.Default.PanTool else Icons.Default.Timer
                            Icon(
                                imageVector = icon, 
                                contentDescription = "Timer", 
                                tint = timerTint, 
                                modifier = Modifier.size(20.dp)
                            )
                            if (isTimerActive && uiState.timerMode != TimerMode.PEACE) {
                                Text(
                                    text = "${uiState.timerMode.seconds}s",
                                    color = Color.Yellow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }



                    
                    if (uiState.timerMode != TimerMode.OFF) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${uiState.timerBurstCount}x",
                            color = Color.Yellow,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    uiState.timerBurstCount = when (uiState.timerBurstCount) {
                                        1 -> 3
                                        3 -> 5
                                        5 -> 10
                                        else -> 1
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                }

            }


            
            // Group Kanan Utama
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.cameraMode == CameraMode.PHOTO) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable {
                                uiState.aspectRatio = when (uiState.aspectRatio) {
                                    AspectRatioMode.RATIO_4_3 -> AspectRatioMode.RATIO_16_9
                                    AspectRatioMode.RATIO_16_9 -> AspectRatioMode.RATIO_1_1
                                    AspectRatioMode.RATIO_1_1 -> AspectRatioMode.RATIO_4_3
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.aspectRatio.label,
                            color = Color.Yellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable {
                                uiState.photoQuality = when (uiState.photoQuality) {
                                    PhotoQualityMode.ULTRA -> PhotoQualityMode.MAX
                                    PhotoQualityMode.MAX -> PhotoQualityMode.HIGH
                                    PhotoQualityMode.HIGH -> PhotoQualityMode.MEDIUM
                                    PhotoQualityMode.MEDIUM -> PhotoQualityMode.LOW
                                    PhotoQualityMode.LOW -> PhotoQualityMode.ULTRA
                                }
                                uiState.prefs.edit().putString("photoQuality", uiState.photoQuality.name).apply()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.photoQuality.label,
                            color = Color.Yellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable {
                                if (uiState.supportedVideoQualities.isNotEmpty()) {
                                    val currentIndex = uiState.supportedVideoQualities.indexOf(uiState.videoQuality)
                                    val nextIndex = if (currentIndex != -1) (currentIndex + 1) % uiState.supportedVideoQualities.size else 0
                                    uiState.videoQuality = uiState.supportedVideoQualities[nextIndex]
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.videoQuality.label,
                            color = Color.Yellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable {
                                if (uiState.supportedFpsModes.isNotEmpty()) {
                                    val currentIndex = uiState.supportedFpsModes.indexOf(uiState.videoFps)
                                    val nextIndex = if (currentIndex == -1 || currentIndex >= uiState.supportedFpsModes.size - 1) 0 else currentIndex + 1
                                    uiState.videoFps = uiState.supportedFpsModes[nextIndex]
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.videoFps.label,
                            color = Color.Yellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        uiState.showSettings = !uiState.showSettings
                        if (uiState.showSettings) {
                            uiState.showProPanel = false
                            uiState.showLayerPanel = false
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings, 
                        contentDescription = "Settings", 
                        tint = if (uiState.showSettings) Color.Yellow else Color.White
                    )
                }
            }
        }

        // ── Baris Kedua: Expandable Scene Selector Pill (Kiri) & Histogram (Kanan) ──
        // ── Baris Kedua: Expandable Scene Selector Pill (Kiri) & Histogram (Kanan) ──
        Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Kiri: SCENE Pill Button
                // Custom Scene Modes Bar (AUTO, NIGHT, SUNSET, ACTION, PORTRAIT, LANDSCAPE, DOCUMENT)
                val activeScene = uiState.selectedCustomScene
                val isActive = activeScene != CustomSceneMode.AUTO

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Main Trigger Button
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isActive) Color.Yellow.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f))
                            .clickable { isSceneOptionsExpanded = !isSceneOptionsExpanded }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "Scene Mode",
                                tint = if (isActive) Color.Yellow else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = activeScene.label,
                                color = if (isActive) Color.Yellow else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Expanded Options
                    AnimatedVisibility(
                        visible = isSceneOptionsExpanded,
                        enter = expandHorizontally(tween(200)) + fadeIn(tween(200)),
                        exit = shrinkHorizontally(tween(200)) + fadeOut(tween(200))
                    ) {
                        val scrollState = rememberScrollState()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .padding(start = 6.dp, end = 4.dp)
                                .horizontalScroll(scrollState)
                        ) {
                            CustomSceneMode.values().forEach { mode ->
                                val isSupported = if (uiState.cameraMode == CameraMode.PHOTO) mode.supportPhoto else mode.supportVideo
                                if (isSupported) {
                                    val isSelected = activeScene == mode
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.15f))
                                            .clickable {
                                                uiState.selectedCustomScene = mode
                                                isSceneOptionsExpanded = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = mode.label,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }


                // Kanan: Histogram Canvas (Strict Horizontal Row Alignment)
                if (uiState.enableHistogram && uiState.histogramData != null) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .size(90.dp, 34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        val data = uiState.histogramData!!
                        val maxCount = (data.maxOrNull()?.toFloat() ?: 1f).coerceAtLeast(1f)
                        val barWidth = size.width / data.size
                        val path = androidx.compose.ui.graphics.Path()
                        path.moveTo(0f, size.height)
                        
                        for (i in data.indices) {
                            val normalizedHeight = (data[i] / maxCount) * size.height
                            val x = i * barWidth
                            val y = size.height - normalizedHeight
                            path.lineTo(x, y)
                        }
                        path.lineTo(size.width, size.height)
                        path.close()
                        
                        drawPath(
                            path = path,
                            color = Color.White,
                            style = androidx.compose.ui.graphics.drawscope.Fill
                        )
                    }
                }
            }
    }
}

private fun getSceneModeName(mode: Int): String {
    return when (mode) {
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_ACTION -> "ACTION"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_PORTRAIT -> "PORTRAIT"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_LANDSCAPE -> "LANDSCAPE"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_NIGHT -> "NIGHT"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_NIGHT_PORTRAIT -> "NIGHT PORTRAIT"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_THEATRE -> "THEATRE"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_BEACH -> "BEACH"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_SNOW -> "SNOW"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_SUNSET -> "SUNSET"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_STEADYPHOTO -> "STEADY"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_FIREWORKS -> "FIREWORKS"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_SPORTS -> "SPORTS"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_PARTY -> "PARTY"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_CANDLELIGHT -> "CANDLE"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_BARCODE -> "BARCODE"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_HDR -> "HDR"
        android.hardware.camera2.CameraCharacteristics.CONTROL_SCENE_MODE_FACE_PRIORITY -> "FACE"
        else -> "SCENE ($mode)"
    }
}


