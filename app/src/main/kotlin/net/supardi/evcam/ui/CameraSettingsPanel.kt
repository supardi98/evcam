package net.supardi.evcam.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.supardi.evcam.CameraMode
import net.supardi.evcam.GridType

@Composable
fun ModeSwitchAndProControls(
    cameraMode: CameraMode,
    isRecording: Boolean,
    isIsoAuto: Boolean,
    isShutterAuto: Boolean,
    isFocusAuto: Boolean,
    whiteBalance: Int,
    exposureIndex: Int,
    showProPanel: Boolean,
    showLayerPanel: Boolean,
    showSettings: Boolean,
    gridType: GridType,
    showVirtualHorizon: Boolean,
    enableHistogram: Boolean,
    enableFocusPeaking: Boolean,
    onCameraModeChange: (CameraMode) -> Unit,
    onProPanelToggle: (Boolean) -> Unit,
    onLayerPanelToggle: (Boolean) -> Unit,
    onAutoAllProSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAnyOverlayActive = gridType != GridType.NONE || showVirtualHorizon || enableHistogram || enableFocusPeaking
    val photoSelected = cameraMode == CameraMode.PHOTO
    val targetBias = if (photoSelected) -1f else 1f
    
    val animatedBias by animateFloatAsState(
        targetValue = targetBias,
        animationSpec = spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy
        ),
        label = "TogglePillSpring"
    )
    val photoTextColor by animateColorAsState(
        targetValue = if (photoSelected) Color.Black else Color.White,
        label = "PhotoTextAnim"
    )
    val videoTextColor by animateColorAsState(
        targetValue = if (!photoSelected) Color.Black else Color.White,
        label = "VideoTextAnim"
    )

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        // Layer Panel Toggle Button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isAnyOverlayActive) Color.Yellow.copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.5f))
                .clickable { 
                    val nextVal = !showLayerPanel
                    onLayerPanelToggle(nextVal)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "Layer Settings",
                tint = if (isAnyOverlayActive) Color.Yellow else Color.LightGray,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Mode Switch Pill
        Box(
            modifier = Modifier
                .height(38.dp)
                .width(160.dp)
                .clip(CircleShape)
                .background(Color.DarkGray.copy(alpha = 0.5f))
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(BiasAlignment(horizontalBias = animatedBias, verticalBias = 0f))
                    .clip(CircleShape)
                    .background(Color.Yellow)
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { if (!isRecording) onCameraModeChange(CameraMode.PHOTO) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PHOTO",
                        color = photoTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { if (!isRecording) onCameraModeChange(CameraMode.VIDEO) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VIDEO",
                        color = videoTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // PRO Mode Button
        val hasManualPro = !isIsoAuto || !isShutterAuto || !isFocusAuto || whiteBalance != android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO || exposureIndex != 0

        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(CircleShape)
                .background(if (hasManualPro) Color.Yellow.copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.5f))
                .clickable { 
                    onProPanelToggle(!showProPanel)
                }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "PRO",
                color = if (hasManualPro) Color.Yellow else Color.LightGray,
                fontWeight = if (hasManualPro) FontWeight.Bold else FontWeight.Normal
            )
        }

        if (hasManualPro) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray.copy(alpha = 0.5f))
                    .clickable { onAutoAllProSettings() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close, 
                    contentDescription = "Auto all Pro settings", 
                    tint = Color.Yellow, 
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
