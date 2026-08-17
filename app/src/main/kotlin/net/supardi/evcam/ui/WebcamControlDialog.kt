package net.supardi.evcam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.window.Dialog

import androidx.compose.material3.Switch
import androidx.compose.ui.draw.scale

@Composable
fun WebcamControlDialog(
    isStreaming: Boolean,
    serverIp: String,
    port: Int,
    connectedClients: Int,
    enableHttpMjpeg: Boolean,
    onEnableHttpMjpegChange: (Boolean) -> Unit,
    enableHttpSnapshot: Boolean,
    onEnableHttpSnapshotChange: (Boolean) -> Unit,
    enableRtspStream: Boolean,
    onEnableRtspStreamChange: (Boolean) -> Unit,
    enableWebRtc: Boolean,
    onEnableWebRtcChange: (Boolean) -> Unit,
    onToggleStreaming: () -> Unit,
    onClose: () -> Unit
) {

    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.9f))
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color.Yellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Text("IP WEBCAM STREAM", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp).clickable { onClose() }
                )
            }

            // Status Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isStreaming) Color.Yellow.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f))
                    .padding(14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isStreaming) Color.Green else Color.Red)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isStreaming) "STREAMING ACTIVE" else "STREAMING OFF",
                            color = if (isStreaming) Color.Green else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    if (isStreaming) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("HTTP MJPEG URL:", color = Color.Gray, fontSize = 11.sp)
                        Text("http://$serverIp:$port/live.mjpeg", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Web Dashboard:", color = Color.Gray, fontSize = 11.sp)
                        Text("http://$serverIp:$port", color = Color.White, fontSize = 12.sp)

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Connected Clients: $connectedClients", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))


            // Protocol Toggle Section
            Text("ENABLED PROTOCOLS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("HTTP Live MJPEG (/live.mjpeg)", color = Color.White, fontSize = 12.sp)
                Switch(checked = enableHttpMjpeg, onCheckedChange = onEnableHttpMjpegChange, modifier = Modifier.scale(0.75f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("HTTP Snapshot (/shot.jpg)", color = Color.White, fontSize = 12.sp)
                Switch(checked = enableHttpSnapshot, onCheckedChange = onEnableHttpSnapshotChange, modifier = Modifier.scale(0.75f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RTSP H.264 Stream (Port 8554)", color = Color.White, fontSize = 12.sp)
                Switch(checked = enableRtspStream, onCheckedChange = onEnableRtspStreamChange, modifier = Modifier.scale(0.75f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("WebRTC Peer Connection (Port 9090)", color = Color.White, fontSize = 12.sp)
                Switch(checked = enableWebRtc, onCheckedChange = onEnableWebRtcChange, modifier = Modifier.scale(0.75f))
            }


            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleStreaming,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStreaming) Color.Red else Color.Yellow,
                    contentColor = if (isStreaming) Color.White else Color.Black
                ),
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isStreaming) "STOP WEBCAM STREAM" else "START WEBCAM STREAM",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
