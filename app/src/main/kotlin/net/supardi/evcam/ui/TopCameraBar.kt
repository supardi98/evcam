package net.supardi.evcam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 24.dp, end = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row {
            if (uiState.hasFlashSupport) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (uiState.isTorchOn) {
                                        uiState.isTorchOn = false
                                    } else {
                                        uiState.flashMode = when (uiState.flashMode) {
                                            FlashMode.AUTO -> FlashMode.ON
                                            FlashMode.ON -> FlashMode.OFF
                                            FlashMode.OFF -> FlashMode.AUTO
                                        }
                                    }
                                },
                                onLongPress = {
                                    uiState.isTorchOn = !uiState.isTorchOn
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val flashIcon = if (uiState.isTorchOn) {
                        Icons.Default.FlashOn
                    } else {
                        when (uiState.flashMode) {
                            FlashMode.AUTO -> Icons.Default.FlashAuto
                            FlashMode.ON -> Icons.Default.FlashOn
                            FlashMode.OFF -> Icons.Default.FlashOff
                        }
                    }
                    val iconTint = if (uiState.isTorchOn) Color.Yellow else Color.White
                    Icon(imageVector = flashIcon, contentDescription = "Flash", tint = iconTint)
                }
            }
            
            if (uiState.cameraMode == CameraMode.VIDEO) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
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
                if (uiState.hasNightExtension) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { 
                                uiState.isNightModeEnabled = !uiState.isNightModeEnabled 
                                if (uiState.isNightModeEnabled) uiState.isHdrEnabled = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val backgroundAlpha = if (uiState.isNightModeEnabled) 0.3f else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Yellow.copy(alpha = backgroundAlpha)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DarkMode,
                                contentDescription = "Night Mode",
                                tint = if (uiState.isNightModeEnabled) Color.Yellow else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                
                if (uiState.hasHdrExtension) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { 
                                uiState.isHdrEnabled = !uiState.isHdrEnabled
                                if (uiState.isHdrEnabled) uiState.isNightModeEnabled = false // Mutually exclusive
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val backgroundAlpha = if (uiState.isHdrEnabled) 0.3f else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Yellow.copy(alpha = backgroundAlpha)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.HdrOn,
                                contentDescription = "HDR Mode",
                                tint = if (uiState.isHdrEnabled) Color.Yellow else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { uiState.showFilterDialog = true },
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
                
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = {
                    uiState.timerMode = when (uiState.timerMode) {
                        TimerMode.OFF -> TimerMode.SEC_3
                        TimerMode.SEC_3 -> TimerMode.SEC_10
                        TimerMode.SEC_10 -> TimerMode.SEC_15
                        TimerMode.SEC_15 -> TimerMode.SEC_20
                        TimerMode.SEC_20 -> if (uiState.isHandTrackingInstalled && uiState.isHandTrackingEnabled) TimerMode.PEACE else TimerMode.OFF
                        TimerMode.PEACE -> TimerMode.OFF
                    }
                }) {
                    val timerTint = if (uiState.timerMode == TimerMode.OFF) Color.White else Color.Yellow
                    Box(contentAlignment = Alignment.Center) {
                        val icon = if (uiState.timerMode == TimerMode.PEACE) Icons.Default.PanTool else Icons.Default.Timer
                        Icon(imageVector = icon, contentDescription = "Timer", tint = timerTint)
                        if (uiState.timerMode != TimerMode.OFF && uiState.timerMode != TimerMode.PEACE) {
                            Text(
                                text = "${uiState.timerMode.seconds}",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.offset(y = 2.dp)
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
            } else {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable {
                            uiState.videoQuality = when (uiState.videoQuality) {
                                VideoQualityMode.HD -> VideoQualityMode.FHD
                                VideoQualityMode.FHD -> VideoQualityMode.UHD
                                VideoQualityMode.UHD -> VideoQualityMode.HD
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
                            uiState.videoFps = when (uiState.videoFps) {
                                VideoFpsMode.FPS_30 -> VideoFpsMode.FPS_60
                                VideoFpsMode.FPS_60 -> VideoFpsMode.FPS_30
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
            
            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = {
                uiState.showSettings = !uiState.showSettings
                if (uiState.showSettings) {
                    uiState.showProPanel = false
                    uiState.showLayerPanel = false
                }
            }) {
                Icon(
                    imageVector = Icons.Default.Settings, 
                    contentDescription = "Settings", 
                    tint = if (uiState.showSettings) Color.Yellow else Color.White
                )
            }
        }
    }
}
