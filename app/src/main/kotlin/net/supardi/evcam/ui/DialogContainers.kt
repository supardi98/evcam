package net.supardi.evcam.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.supardi.evcam.logic.*


@Composable
fun DialogContainers(
    uiState: CameraUiState
) {
    val context = uiState.context

    // 1. Media Preview Dialog Wrapper
    if (uiState.showMediaPreviewDialog && (uiState.lastCapturedBitmap != null || uiState.lastCapturedUri != null)) {
        MediaPreviewDialog(
            lastCapturedBitmap = uiState.lastCapturedBitmap,
            lastCapturedUri = uiState.lastCapturedUri,
            cameraMode = uiState.cameraMode,
            context = context,
            onDismiss = { uiState.showMediaPreviewDialog = false }
        )
    }

    // 2. Plugin Manager Dialog Wrapper (AI Hand Tracking)
    if (uiState.showPluginManager) {
        var downloadProgress by remember { mutableStateOf(0f) }
        var isDownloading by remember { mutableStateOf(false) }
        
        LaunchedEffect(isDownloading) {
            if (isDownloading) {
                while (downloadProgress < 1f) {
                    delay(50)
                    downloadProgress += 0.02f
                }
                uiState.isHandTrackingInstalled = true
                isDownloading = false
            }
        }
        
        if (uiState.showRemoveConfirmation) {
            AlertDialog(
                onDismissRequest = { uiState.showRemoveConfirmation = false },
                title = { Text("Uninstall Plugin") },
                text = { Text("Are you sure you want to remove AI Hand Tracking?") },
                confirmButton = {
                    TextButton(onClick = {
                        uiState.isHandTrackingInstalled = false
                        uiState.showRemoveConfirmation = false
                    }) { Text("Remove") }
                },
                dismissButton = {
                    TextButton(onClick = { uiState.showRemoveConfirmation = false }) { Text("Cancel") }
                }
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable { if (!isDownloading) uiState.showPluginManager = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.DarkGray)
                    .clickable(enabled = false) {}
                    .padding(24.dp)
            ) {
                Text("Plugin Manager", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI Hand Tracking", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Mendeteksi gestur jari (Peace)", color = Color.Gray, fontSize = 12.sp)
                    }
                    if (uiState.isHandTrackingInstalled) {
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("On", color = Color.White, fontSize = 12.sp)
                                Switch(checked = uiState.isHandTrackingEnabled, onCheckedChange = { uiState.isHandTrackingEnabled = it }, modifier = Modifier.scale(0.6f))
                            }
                            Text("Remove", color = Color.Red, fontSize = 12.sp, modifier = Modifier.clickable { 
                                uiState.showRemoveConfirmation = true
                            }.padding(top = 4.dp))
                        }
                    } else if (isDownloading) {
                        Text("${(downloadProgress * 100).toInt()}%", color = Color.Yellow)
                    } else {
                        Button(onClick = { isDownloading = true }) {
                            Text("Download")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { uiState.showPluginManager = false }, enabled = !isDownloading, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }

    // 3. Watermark Settings Dialog Wrapper
    if (uiState.showWatermarkDialog) {
        WatermarkSettingsDialog(
            showWatermark = uiState.showWatermark,
            onShowWatermarkChange = { uiState.showWatermark = it },
            watermarkElements = uiState.watermarkElements,
            onWatermarkElementsChange = { uiState.watermarkElements = it },
            liveLocation = uiState.liveLocation,
            liveAddress = uiState.liveAddress,
            onDismiss = { uiState.showWatermarkDialog = false }
        )
    }

    // 4. Color Filter Dialog Wrapper
    if (uiState.showFilterDialog) {
        ColorFilterDialog(
            selectedFilter = uiState.selectedFilter,
            onFilterSelect = { uiState.selectedFilter = it },
            onDismissRequest = { uiState.showFilterDialog = false }
        )
    }
}
