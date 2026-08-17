package net.supardi.evcam

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.supardi.evcam.logic.*
import net.supardi.evcam.ui.theme.EvcamTheme

class WebcamActivity : ComponentActivity() {

    private var webcamServer: WebcamStreamServer? = null
    private var rtspServer: RtspServer? = null
    private var webRtcServer: WebRtcServer? = null
    private var camera2Engine: Camera2Engine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val context = applicationContext
        webcamServer = WebcamStreamServer(context)
        rtspServer = RtspServer(8554)
        webRtcServer = WebRtcServer(9090)
        camera2Engine = Camera2Engine(context)

        setContent {
            EvcamTheme {
                WebcamDedicatedScreen(
                    camera2Engine = camera2Engine!!,
                    webcamServer = webcamServer!!,
                    rtspServer = rtspServer!!,
                    webRtcServer = webRtcServer!!,
                    onBackToCamera = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webcamServer?.stop()
        rtspServer?.stop()
        webRtcServer?.stop()
        camera2Engine?.stopBackgroundThread()
        camera2Engine?.closeCamera()
    }
}

@Composable
fun WebcamDedicatedScreen(
    camera2Engine: Camera2Engine,
    webcamServer: WebcamStreamServer,
    rtspServer: RtspServer,
    webRtcServer: WebRtcServer,
    onBackToCamera: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val serverIp = remember { WebcamStreamServer.getLocalIpAddress(context) }

    var isHttpMjpeg by remember { mutableStateOf(false) }
    var isHttpSnapshot by remember { mutableStateOf(false) }
    var isRtsp by remember { mutableStateOf(false) }
    var isWebRtc by remember { mutableStateOf(false) }
    var isBlackoutPowerSaving by remember { mutableStateOf(false) }
    var connectedClients by remember { mutableIntStateOf(0) }

    var httpMjpegLoading by remember { mutableStateOf(false) }
    var httpSnapshotLoading by remember { mutableStateOf(false) }
    var rtspLoading by remember { mutableStateOf(false) }
    var webRtcLoading by remember { mutableStateOf(false) }

    var width by remember { mutableIntStateOf(1280) }
    var height by remember { mutableIntStateOf(720) }
    var orientation by remember { mutableStateOf("LANDSCAPE") }

    val isAnyActive = isHttpMjpeg || isHttpSnapshot || isRtsp || isWebRtc

    // Lazy Camera Engine start/stop: Open camera ONLY when at least one streaming protocol is ON
    LaunchedEffect(isAnyActive) {
        if (isAnyActive) {
            camera2Engine.startBackgroundThread()
            camera2Engine.openCamera("0")
            camera2Engine.setupImageReader(1920, 1080, width, height)
        } else {
            camera2Engine.closeCamera()
            camera2Engine.stopBackgroundThread()
        }
    }

    LaunchedEffect(cameraState, width, height, isAnyActive) {
        if (isAnyActive && cameraState is Camera2Engine.CameraState.Opened) {
            camera2Engine.setupImageReader(1920, 1080, width, height)

            camera2Engine.analysisImageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        webcamServer.pushYuvFrame(image)
                        rtspServer.pushYuvFrame(image)
                        webRtcServer.pushYuvFrame(image)
                    } finally {
                        image.close()
                    }
                } catch (e: Exception) {
                    // Buffer closed or camera reconfiguring safely ignored
                }
            }, camera2Engine.backgroundHandler)

            val surfaces = mutableListOf<android.view.Surface>()
            camera2Engine.analysisImageReader?.surface?.let { surfaces.add(it) }
            camera2Engine.createPreviewSession(surfaces) { _ -> }
        }
    }

    // Attach frame listener & server lifecycles
    LaunchedEffect(isHttpMjpeg, isHttpSnapshot, isRtsp, isWebRtc) {
        webcamServer.enableHttpMjpeg = isHttpMjpeg
        webcamServer.enableHttpSnapshot = isHttpSnapshot
        webcamServer.enableRtspStream = isRtsp
        webcamServer.enableWebRtc = isWebRtc
        webcamServer.onClientCountChanged = { count -> connectedClients = count }

        if (isHttpMjpeg || isHttpSnapshot) webcamServer.start() else webcamServer.stop()
        if (isRtsp) rtspServer.start() else rtspServer.stop()
        if (isWebRtc) webRtcServer.start() else webRtcServer.stop()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0D1117)
    ) {
        if (isBlackoutPowerSaving) {
            // Fullscreen Blackout Screen for maximum battery saving
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { isBlackoutPowerSaving = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(56.dp))
                    Text("POWER SAVING BLACKOUT MODE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Turn off screen preview to reduce heat and save maximum battery during live streaming.", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                    Text("Tap anywhere on screen to unlock display", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onBackToCamera) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text("IP WEBCAM PROTOCOLS", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                if (connectedClients > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
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
                                Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF00E676)))
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
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Resolution & Format Selector Config
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("⚙️ Stream Video Configuration", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // 1. Resolution Selection
                        Text("Resolution Quality", color = Color.LightGray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val resolutions = listOf(
                                Triple("1080p (FHD)", 1920, 1080),
                                Triple("720p (HD)", 1280, 720),
                                Triple("480p (SD)", 640, 480)
                            )
                            resolutions.forEach { (label, w, h) ->
                                val isSelected = width == w && height == h
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color.Yellow else Color.DarkGray,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            width = w
                                            height = h
                                            camera2Engine.setupImageReader(1920, 1080, w, h)
                                            val surfaces = mutableListOf<android.view.Surface>()
                                            camera2Engine.analysisImageReader?.surface?.let { surfaces.add(it) }
                                            camera2Engine.createPreviewSession(surfaces) { _ -> }
                                        }
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2. Orientation Selection
                        Text("Stream Orientation", color = Color.LightGray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val orientations = listOf("LANDSCAPE" to "🖼️ Landscape (16:9)", "PORTRAIT" to "📱 Portrait (9:16)")
                            orientations.forEach { (mode, label) ->
                                val isSelected = orientation == mode
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF00E676) else Color.DarkGray,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { orientation = mode }
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. HTTP Live MJPEG Protocol Card
                FullProtocolCard(
                    title = "HTTP Live MJPEG",
                    subtitle = "Stream video langsung ke browser / OBS",
                    url = "http://$serverIp:8080/live.mjpeg",
                    dashboardUrl = "http://$serverIp:8080",
                    isEnabled = isHttpMjpeg,
                    isLoading = httpMjpegLoading,
                    context = context,
                    onToggle = { isChecked ->
                        if (isChecked) {
                            httpMjpegLoading = true
                            coroutineScope.launch {
                                delay(300)
                                isHttpMjpeg = true
                                httpMjpegLoading = false
                            }
                        } else {
                            isHttpMjpeg = false
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. HTTP Snapshot Protocol Card
                FullProtocolCard(
                    title = "HTTP Snapshot (/shot.jpg)",
                    subtitle = "Ambil foto single frame via URL",
                    url = "http://$serverIp:8080/shot.jpg",
                    dashboardUrl = null,
                    isEnabled = isHttpSnapshot,
                    isLoading = httpSnapshotLoading,
                    context = context,
                    onToggle = { isChecked ->
                        if (isChecked) {
                            httpSnapshotLoading = true
                            coroutineScope.launch {
                                delay(300)
                                isHttpSnapshot = true
                                httpSnapshotLoading = false
                            }
                        } else {
                            isHttpSnapshot = false
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. RTSP H.264 Stream Card
                FullProtocolCard(
                    title = "RTSP H.264 Stream",
                    subtitle = "Stream RTSP latensi rendah untuk NVR / VLC / OBS",
                    url = "rtsp://$serverIp:8554/live",
                    dashboardUrl = null,
                    isEnabled = isRtsp,
                    isLoading = rtspLoading,
                    context = context,
                    onToggle = { isChecked ->
                        if (isChecked) {
                            rtspLoading = true
                            coroutineScope.launch {
                                delay(400)
                                isRtsp = true
                                rtspLoading = false
                            }
                        } else {
                            isRtsp = false
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. WebRTC Sub-100ms Stream Card
                FullProtocolCard(
                    title = "WebRTC Sub-100ms Stream",
                    subtitle = "Stream HTML5 real-time sub-100ms ultra low-latency (Buka di browser)",
                    url = "http://$serverIp:9090/stream",
                    dashboardUrl = "http://$serverIp:9090",
                    isEnabled = isWebRtc,
                    isLoading = webRtcLoading,
                    context = context,
                    onToggle = { isChecked ->
                        if (isChecked) {
                            webRtcLoading = true
                            coroutineScope.launch {
                                delay(400)
                                isWebRtc = true
                                webRtcLoading = false
                            }
                        } else {
                            isWebRtc = false
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Battery Power Saving (Blackout Screen) Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isBlackoutPowerSaving) Color(0xFF00E676).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, if (isBlackoutPowerSaving) Color(0xFF00E676).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🔋 Black Screen Power Saving", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "Turn off screen preview to reduce heat and save maximum battery during live streaming.",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isBlackoutPowerSaving,
                            onCheckedChange = { isBlackoutPowerSaving = it },
                            modifier = Modifier.scale(0.85f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullProtocolCard(
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
