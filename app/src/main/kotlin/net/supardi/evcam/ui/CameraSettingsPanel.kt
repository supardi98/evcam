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
import net.supardi.evcam.logic.*


@Composable
fun SettingsPopupPanels(
    uiState: CameraUiState,
    isEisSupported: Boolean = true,
    onOpenWatermarkSettings: () -> Unit,
    onOpenPluginManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. PRO Controls Panel
        AnimatedVisibility(
            visible = uiState.showProPanel,
            enter = expandVertically(animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow, dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy)) + fadeIn(tween(250)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(200))
        ) {
            Column {
                ProControlPanel(
                    iso = uiState.iso,
                    minIso = uiState.minIso,
                    maxIso = uiState.maxIso,
                    isIsoAuto = uiState.isIsoAuto,
                    onIsoChange = { uiState.isIsoAuto = false; uiState.iso = it; uiState.isProMode = true },
                    onIsoAutoToggle = { uiState.isIsoAuto = !uiState.isIsoAuto },
                    shutterSpeed = uiState.shutterSpeed,
                    minShutterSpeed = uiState.minShutterSpeed,
                    maxShutterSpeed = uiState.maxShutterSpeed,
                    isShutterAuto = uiState.isShutterAuto,
                    onShutterChange = { uiState.isShutterAuto = false; uiState.shutterSpeed = it },
                    onShutterAutoToggle = { uiState.isShutterAuto = !uiState.isShutterAuto },
                    focusDistance = uiState.focusDistance,
                    maxFocusDistance = uiState.maxFocusDistance,
                    isFocusAuto = uiState.isFocusAuto,
                    onFocusChange = { uiState.isFocusAuto = false; uiState.focusDistance = it },

                    onFocusAutoToggle = { uiState.isFocusAuto = !uiState.isFocusAuto },
                    whiteBalance = uiState.whiteBalance,
                    onWhiteBalanceChange = { uiState.whiteBalance = it },
                    manualKelvin = uiState.manualKelvin,
                    onManualKelvinChange = { uiState.manualKelvin = it },
                    onClose = { uiState.showProPanel = false },
                    isHdrEnabled = uiState.isHdrEnabled,
                    isNightModeEnabled = uiState.isNightModeEnabled
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        // 2. Overlays Layer Panel
        AnimatedVisibility(
            visible = uiState.showLayerPanel,
            enter = expandVertically(animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow, dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy)) + fadeIn(tween(250)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(200))
        ) {
            Column {
                DisplayOverlaysPanel(
                    gridType = uiState.gridType,
                    onGridTypeChange = {
                        uiState.gridType = it
                        uiState.prefs.edit().putString("gridType", it.name).apply()
                    },
                    showVirtualHorizon = uiState.showVirtualHorizon,
                    onVirtualHorizonChange = {
                        uiState.showVirtualHorizon = it
                        uiState.prefs.edit().putBoolean("showVirtualHorizon", it).apply()
                    },
                    enableHistogram = uiState.enableHistogram,
                    onHistogramChange = {
                        uiState.enableHistogram = it
                        uiState.prefs.edit().putBoolean("enableHistogram", it).apply()
                    },
                    enableFocusPeaking = uiState.enableFocusPeaking,
                    onFocusPeakingChange = {
                        uiState.enableFocusPeaking = it
                        uiState.prefs.edit().putBoolean("enableFocusPeaking", it).apply()
                    },
                    onClose = { uiState.showLayerPanel = false }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        // 3. Main Settings Panel
        AnimatedVisibility(
            visible = uiState.showSettings,
            enter = expandVertically(animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow, dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy)) + fadeIn(tween(250)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(200))
        ) {
            Column {
                SettingsPanel(
                    enableRawCapture = uiState.enableRawCapture,
                    onEnableRawCaptureChange = { uiState.enableRawCapture = it },
                    isEisSupported = isEisSupported,
                    enableEis = uiState.enableEis,
                    onEnableEisChange = { uiState.enableEis = it },
                    keepScreenOn = uiState.keepScreenOn,
                    onKeepScreenOnChange = { uiState.keepScreenOn = it },
                    maxBrightness = uiState.maxBrightness,
                    onMaxBrightnessChange = { uiState.maxBrightness = it },
                    volumeShutterEnabled = uiState.volumeShutterEnabled,
                    onVolumeShutterEnabledChange = { uiState.volumeShutterEnabled = it },
                    isShutterSoundEnabled = uiState.isShutterSoundEnabled,
                    onIsShutterSoundEnabledChange = { uiState.isShutterSoundEnabled = it },
                    enableGeotagging = uiState.enableGeotagging,
                    onEnableGeotaggingChange = { uiState.enableGeotagging = it },
                    onOpenWatermarkSettings = onOpenWatermarkSettings,
                    onOpenPluginManager = onOpenPluginManager,
                    onOpenCameraInfo = { uiState.showCameraInfoDialog = true },
                    onClose = { uiState.showSettings = false }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun ModeSwitchAndProControls(
    uiState: CameraUiState,
    modifier: Modifier = Modifier
) {
    val isAnyOverlayActive = uiState.gridType != GridType.NONE || uiState.showVirtualHorizon || uiState.enableHistogram || uiState.enableFocusPeaking
    val photoSelected = uiState.cameraMode == CameraMode.PHOTO
    val targetBias = if (photoSelected) -1f else 1f
    
    val animatedBias by animateFloatAsState(
        targetValue = targetBias,
        animationSpec = androidx.compose.animation.core.spring(
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
                    uiState.showLayerPanel = !uiState.showLayerPanel
                    if (uiState.showLayerPanel) {
                        uiState.showProPanel = false
                        uiState.showSettings = false
                    }
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
                        ) { if (!uiState.isRecording) uiState.cameraMode = CameraMode.PHOTO },
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
                        ) { if (!uiState.isRecording) uiState.cameraMode = CameraMode.VIDEO },
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
        if (uiState.hasManualSensorSupport) {
            val hasManualPro = !uiState.isIsoAuto || !uiState.isShutterAuto || !uiState.isFocusAuto || uiState.whiteBalance != android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO

            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (hasManualPro) Color.Yellow.copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.5f))
                    .clickable { 
                        uiState.showProPanel = !uiState.showProPanel
                        if (uiState.showProPanel) {
                            uiState.showLayerPanel = false
                            uiState.showSettings = false
                        }
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
                        .clickable { 
                            uiState.isIsoAuto = true
                            uiState.isShutterAuto = true
                            uiState.isFocusAuto = true
                            uiState.whiteBalance = android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO
                            uiState.exposureIndex = 0
                            uiState.isProMode = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Reset PRO",
                        tint = Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
