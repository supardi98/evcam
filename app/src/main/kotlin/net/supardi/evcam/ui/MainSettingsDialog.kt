package net.supardi.evcam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainSettingsDialog(
    showSettings: Boolean,
    enableRawCapture: Boolean,
    onEnableRawCaptureChange: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    maxBrightness: Boolean,
    onMaxBrightnessChange: (Boolean) -> Unit,
    volumeShutterEnabled: Boolean,
    onVolumeShutterEnabledChange: (Boolean) -> Unit,
    isShutterSoundEnabled: Boolean,
    onIsShutterSoundEnabledChange: (Boolean) -> Unit,
    enableGeotagging: Boolean,
    onEnableGeotaggingChange: (Boolean) -> Unit,
    onOpenWatermarkSettings: () -> Unit,
    onOpenPluginManager: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!showSettings) return
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.DarkGray.copy(alpha = 0.9f))
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Settings", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("RAW (DNG) Capture", color = Color.White)
                    Switch(checked = enableRawCapture, onCheckedChange = onEnableRawCaptureChange, modifier = Modifier.scale(0.8f))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Keep Screen On", color = Color.White)
                    Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOnChange, modifier = Modifier.scale(0.8f))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Max Brightness", color = Color.White)
                    Switch(checked = maxBrightness, onCheckedChange = onMaxBrightnessChange, modifier = Modifier.scale(0.8f))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Volume Key Shutter", color = Color.White)
                    Switch(checked = volumeShutterEnabled, onCheckedChange = onVolumeShutterEnabledChange, modifier = Modifier.scale(0.8f))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Shutter Sound", color = Color.White)
                    Switch(checked = isShutterSoundEnabled, onCheckedChange = onIsShutterSoundEnabledChange, modifier = Modifier.scale(0.8f))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Watermark Settings", color = Color.White)
                    Button(
                        onClick = onOpenWatermarkSettings,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text("Edit", color = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Save Location (Geotag EXIF)", color = Color.White)
                    Switch(checked = enableGeotagging, onCheckedChange = onEnableGeotaggingChange, modifier = Modifier.scale(0.8f))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onOpenPluginManager,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Plugin Manager")
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Done")
                }
            }
        }
    }
}
