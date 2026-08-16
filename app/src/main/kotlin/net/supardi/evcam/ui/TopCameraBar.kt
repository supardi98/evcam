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
import net.supardi.evcam.*

@Composable
fun TopCameraBar(
    cameraMode: CameraMode,
    flashMode: FlashMode,
    isTorchOn: Boolean,
    onFlashModeChange: (FlashMode) -> Unit,
    onTorchToggle: () -> Unit,
    videoAudioEnabled: Boolean,
    onVideoAudioToggle: () -> Unit,
    isNightModeEnabled: Boolean,
    onNightModeToggle: () -> Unit,
    selectedFilter: ColorFilterMode,
    onFilterClick: () -> Unit,
    timerMode: TimerMode,
    onTimerModeChange: (TimerMode) -> Unit,
    isHandTrackingInstalled: Boolean,
    isHandTrackingEnabled: Boolean,
    timerBurstCount: Int,
    onTimerBurstCountChange: (Int) -> Unit,
    aspectRatio: AspectRatioMode,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    videoQuality: VideoQualityMode,
    onVideoQualityChange: (VideoQualityMode) -> Unit,
    videoFps: VideoFpsMode,
    onVideoFpsChange: (VideoFpsMode) -> Unit,
    showSettings: Boolean,
    onSettingsClick: () -> Unit,
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
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {

                                if (isTorchOn) {
                                    onTorchToggle()
                                } else {
                                    val nextFlash = when (flashMode) {
                                        FlashMode.AUTO -> FlashMode.ON
                                        FlashMode.ON -> FlashMode.OFF
                                        FlashMode.OFF -> FlashMode.AUTO
                                    }
                                    onFlashModeChange(nextFlash)
                                }
                            },
                            onLongPress = {
                                onTorchToggle()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val flashIcon = if (isTorchOn) {
                    Icons.Default.FlashOn
                } else {
                    when (flashMode) {
                        FlashMode.AUTO -> Icons.Default.FlashAuto
                        FlashMode.ON -> Icons.Default.FlashOn
                        FlashMode.OFF -> Icons.Default.FlashOff
                    }
                }
                val iconTint = if (isTorchOn) Color.Yellow else Color.White
                Icon(imageVector = flashIcon, contentDescription = "Flash", tint = iconTint)
            }
            
            if (cameraMode == CameraMode.VIDEO) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onVideoAudioToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    val emoji = if (videoAudioEnabled) "🔊" else "🔇"
                    Text(text = emoji, fontSize = 20.sp)
                }
            }

            if (cameraMode == CameraMode.PHOTO) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onNightModeToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    val emoji = "🌙"
                    val backgroundAlpha = if (isNightModeEnabled) 0.3f else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Yellow.copy(alpha = backgroundAlpha)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 20.sp)
                    }
                }
                
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onFilterClick() },
                    contentAlignment = Alignment.Center
                ) {
                    val emoji = "🎨"
                    val backgroundAlpha = if (selectedFilter != ColorFilterMode.NORMAL) 0.3f else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Yellow.copy(alpha = backgroundAlpha)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 20.sp)
                    }
                }
                
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = {
                    val nextTimer = when (timerMode) {
                        TimerMode.OFF -> TimerMode.SEC_3
                        TimerMode.SEC_3 -> TimerMode.SEC_10
                        TimerMode.SEC_10 -> TimerMode.SEC_15
                        TimerMode.SEC_15 -> TimerMode.SEC_20
                        TimerMode.SEC_20 -> if (isHandTrackingInstalled && isHandTrackingEnabled) TimerMode.PEACE else TimerMode.OFF
                        TimerMode.PEACE -> TimerMode.OFF
                    }
                    onTimerModeChange(nextTimer)
                }) {
                    val timerTint = if (timerMode == TimerMode.OFF) Color.White else Color.Yellow
                    Box(contentAlignment = Alignment.Center) {
                        val icon = if (timerMode == TimerMode.PEACE) Icons.Default.PanTool else Icons.Default.Timer
                        Icon(imageVector = icon, contentDescription = "Timer", tint = timerTint)
                        if (timerMode != TimerMode.OFF && timerMode != TimerMode.PEACE) {
                            Text(
                                text = "${timerMode.seconds}",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.offset(y = 2.dp)
                            )
                        }
                    }
                }
                
                if (timerMode != TimerMode.OFF) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${timerBurstCount}x",
                        color = Color.Yellow,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                val nextBurst = when (timerBurstCount) {
                                    1 -> 3
                                    3 -> 5
                                    5 -> 10
                                    else -> 1
                                }
                                onTimerBurstCountChange(nextBurst)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (cameraMode == CameraMode.PHOTO) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable {
                            val nextRatio = when (aspectRatio) {
                                AspectRatioMode.RATIO_4_3 -> AspectRatioMode.RATIO_16_9
                                AspectRatioMode.RATIO_16_9 -> AspectRatioMode.RATIO_1_1
                                AspectRatioMode.RATIO_1_1 -> AspectRatioMode.RATIO_4_3
                            }
                            onAspectRatioChange(nextRatio)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = aspectRatio.label,
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
                            val nextQuality = when (videoQuality) {
                                VideoQualityMode.HD -> VideoQualityMode.FHD
                                VideoQualityMode.FHD -> VideoQualityMode.UHD
                                VideoQualityMode.UHD -> VideoQualityMode.HD
                            }
                            onVideoQualityChange(nextQuality)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = videoQuality.label,
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
                            val nextFps = when (videoFps) {
                                VideoFpsMode.FPS_30 -> VideoFpsMode.FPS_60
                                VideoFpsMode.FPS_60 -> VideoFpsMode.FPS_30
                            }
                            onVideoFpsChange(nextFps)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = videoFps.label,
                        color = Color.Yellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings, 
                    contentDescription = "Settings", 
                    tint = if (showSettings) Color.Yellow else Color.White
                )
            }
        }
    }
}
