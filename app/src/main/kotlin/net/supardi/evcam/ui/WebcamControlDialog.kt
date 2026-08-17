package net.supardi.evcam.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.supardi.evcam.logic.WebcamStreamServer

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
    onStartProtocol: (String) -> Unit,
    onStopProtocol: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var httpMjpegLoading by remember { mutableStateOf(false) }
    var httpSnapshotLoading by remember { mutableStateOf(false) }
    var rtspLoading by remember { mutableStateOf(false) }
    var webRtcLoading by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.92f))
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                    Text("IP WEBCAM PROTOCOLS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp).clickable { onClose() }
                )
            }

            if (connectedClients > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF00E676).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFF00E676))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF00E676))
                            )
                            Text(
                                text = "$connectedClients Active Client${if (connectedClients > 1) "s" else ""} Connected",
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Error Dialog Alert Box
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Red.copy(alpha = 0.2f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(errorMessage!!, color = Color.Red, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp).clickable { errorMessage = null }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 1. HTTP Live MJPEG Protocol Card
            ProtocolCard(
                title = "HTTP Live MJPEG",
                subtitle = "Stream video langsung ke browser / OBS",
                url = "http://$serverIp:$port/live.mjpeg",
                dashboardUrl = "http://$serverIp:$port",
                isEnabled = enableHttpMjpeg,
                isLoading = httpMjpegLoading,
                context = context,
                onToggle = { isChecked ->
                    if (isChecked) {
                        httpMjpegLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            delay(400)
                            try {
                                onEnableHttpMjpegChange(true)
                                onStartProtocol("HTTP_MJPEG")
                            } catch (e: Exception) {
                                onEnableHttpMjpegChange(false)
                                errorMessage = "Gagal memulai HTTP MJPEG: ${e.localizedMessage}"
                            } finally {
                                httpMjpegLoading = false
                            }
                        }
                    } else {
                        onEnableHttpMjpegChange(false)
                        onStopProtocol("HTTP_MJPEG")
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. HTTP Snapshot Protocol Card
            ProtocolCard(
                title = "HTTP Snapshot (/shot.jpg)",
                subtitle = "Ambil foto single frame via URL",
                url = "http://$serverIp:$port/shot.jpg",
                dashboardUrl = null,
                isEnabled = enableHttpSnapshot,
                isLoading = httpSnapshotLoading,
                context = context,
                onToggle = { isChecked ->
                    if (isChecked) {
                        httpSnapshotLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            delay(300)
                            try {
                                onEnableHttpSnapshotChange(true)
                                onStartProtocol("HTTP_SNAPSHOT")
                            } catch (e: Exception) {
                                onEnableHttpSnapshotChange(false)
                                errorMessage = "Gagal memulai HTTP Snapshot: ${e.localizedMessage}"
                            } finally {
                                httpSnapshotLoading = false
                            }
                        }
                    } else {
                        onEnableHttpSnapshotChange(false)
                        onStopProtocol("HTTP_SNAPSHOT")
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. RTSP H.264 Protocol Card
            ProtocolCard(
                title = "RTSP H.264 Stream",
                subtitle = "Stream RTSP latensi rendah untuk NVR / VLC / OBS",
                url = "rtsp://$serverIp:8554/live",
                dashboardUrl = null,
                isEnabled = enableRtspStream,
                isLoading = rtspLoading,
                context = context,
                onToggle = { isChecked ->
                    if (isChecked) {
                        rtspLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            delay(500)
                            try {
                                onEnableRtspStreamChange(true)
                                onStartProtocol("RTSP")
                            } catch (e: Exception) {
                                onEnableRtspStreamChange(false)
                                errorMessage = "Gagal memulai RTSP: ${e.localizedMessage}"
                            } finally {
                                rtspLoading = false
                            }
                        }
                    } else {
                        onEnableRtspStreamChange(false)
                        onStopProtocol("RTSP")
                    }
                }
            )


            Spacer(modifier = Modifier.height(12.dp))

            // 4. WebRTC Protocol Card
            ProtocolCard(
                title = "WebRTC Sub-100ms Stream",
                subtitle = "Stream HTML5 real-time sub-100ms ultra low-latency (Buka di browser)",
                url = "http://$serverIp:9090/stream",
                dashboardUrl = "http://$serverIp:9090",
                isEnabled = enableWebRtc,
                isLoading = webRtcLoading,
                context = context,

                onToggle = { isChecked ->
                    if (isChecked) {
                        webRtcLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            delay(500)
                            try {
                                onEnableWebRtcChange(true)
                                onStartProtocol("WEBRTC")
                            } catch (e: Exception) {
                                onEnableWebRtcChange(false)
                                errorMessage = "Gagal memulai WebRTC: ${e.localizedMessage}"
                            } finally {
                                webRtcLoading = false
                            }
                        }
                    } else {
                        onEnableWebRtcChange(false)
                        onStopProtocol("WEBRTC")
                    }
                }
            )
        }
    }
}

@Composable
private fun ProtocolCard(
    title: String,
    subtitle: String,
    url: String,
    dashboardUrl: String?,
    isEnabled: Boolean,
    isLoading: Boolean,
    context: Context,
    onToggle: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isEnabled) Color.Yellow.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(subtitle, color = Color.Gray, fontSize = 10.sp)
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Yellow,
                        strokeWidth = 2.dp
                    )
                } else {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onToggle,
                        modifier = Modifier.scale(0.75f)
                    )
                }
            }

            if (isEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(url, color = Color.Yellow, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(1f))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Copy URL Icon Button
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy URL",
                            tint = Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Stream URL", url)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "URL disalin!", Toast.LENGTH_SHORT).show()
                                }
                        )

                        // Open in Browser Icon Button (Only for http URLs)
                        if (url.startsWith("http")) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open in Browser",
                                tint = Color.Yellow,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(dashboardUrl ?: url))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Gagal membuka browser", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
