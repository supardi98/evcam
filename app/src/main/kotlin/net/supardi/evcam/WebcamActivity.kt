package net.supardi.evcam

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.PowerSettingsNew
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
    val prefs = remember { context.getSharedPreferences("evcam_prefs", android.content.Context.MODE_PRIVATE) }
    val uiState = rememberCameraUiState(context, prefs)

    val serverIp = remember { WebcamStreamServer.getLocalIpAddress(context) }

    var isHttpMjpeg by remember { mutableStateOf(false) }
    var isHttpSnapshot by remember { mutableStateOf(false) }
    var isRtsp by remember { mutableStateOf(false) }
    var isWebRtc by remember { mutableStateOf(false) }
    var isBlackoutPowerSaving by remember { mutableStateOf(false) }
    var connectedClients by remember { mutableIntStateOf(0) }

    var width by remember { mutableIntStateOf(1280) }
    var height by remember { mutableIntStateOf(720) }
    var orientation by remember { mutableStateOf("LANDSCAPE") }

    // Dedicated Camera Engine start/stop
    DisposableEffect(Unit) {
        camera2Engine.startBackgroundThread()
        camera2Engine.openCamera("0")
        camera2Engine.setupImageReader(1920, 1080, width, height)

        onDispose {
            camera2Engine.closeCamera()
            camera2Engine.stopBackgroundThread()
        }
    }

    // Attach frame listener for active streaming
    LaunchedEffect(isHttpMjpeg, isHttpSnapshot, isRtsp, isWebRtc) {
        val isAnyActive = isHttpMjpeg || isHttpSnapshot || isRtsp || isWebRtc
        if (isAnyActive) {
            webcamServer.enableHttpMjpeg = isHttpMjpeg
            webcamServer.enableHttpSnapshot = isHttpSnapshot
            webcamServer.enableRtspStream = isRtsp
            webcamServer.enableWebRtc = isWebRtc
            webcamServer.onClientCountChanged = { count -> connectedClients = count }

            if (isHttpMjpeg || isHttpSnapshot) webcamServer.start() else webcamServer.stop()
            if (isRtsp) rtspServer.start() else rtspServer.stop()
            if (isWebRtc) webRtcServer.start() else webRtcServer.stop()

            camera2Engine.analysisImageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        if (isHttpMjpeg || isHttpSnapshot) webcamServer.pushYuvFrame(image)
                        if (isRtsp) rtspServer.pushYuvFrame(image)
                        if (isWebRtc) webRtcServer.pushYuvFrame(image)
                    } finally {
                        image.close()
                    }
                }
            }, camera2Engine.backgroundHandler)
        } else {
            webcamServer.stop()
            rtspServer.stop()
            webRtcServer.stop()
        }
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
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(56.dp))
                    Text("DEDICATED IP WEBCAM MODE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Main Camera UI Destroyed — Maximum Battery & Power Saving", color = Color.Gray, fontSize = 12.sp)
                    Text("Tap screen to unlock UI", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
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
                        Text("DEDICATED IP WEBCAM", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    if (connectedClients > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF00E676).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFF00E676))
                        ) {
                            Text(
                                text = "$connectedClients Active Client${if (connectedClients > 1) "s" else ""}",
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Resolution & Orientation Config
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("⚙️ Stream Resolution & Format", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(Triple("1080p", 1920, 1080), Triple("720p", 1280, 720), Triple("480p", 640, 480)).forEach { (lbl, w, h) ->
                                val sel = width == w && height == h
                                Button(
                                    onClick = {
                                        width = w
                                        height = h
                                        camera2Engine.setupImageReader(1920, 1080, w, h)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (sel) Color.Yellow else Color.DarkGray),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(lbl, color = if (sel) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Protocol Controls
                Text("Streaming Protocols", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                WebcamProtocolCard("HTTP Live MJPEG", "http://$serverIp:8080/live.mjpeg", isHttpMjpeg) { isHttpMjpeg = it }
                Spacer(modifier = Modifier.height(8.dp))
                WebcamProtocolCard("HTTP Snapshot", "http://$serverIp:8080/shot.jpg", isHttpSnapshot) { isHttpSnapshot = it }
                Spacer(modifier = Modifier.height(8.dp))
                WebcamProtocolCard("RTSP H.264 Stream", "rtsp://$serverIp:8554/live", isRtsp) { isRtsp = it }
                Spacer(modifier = Modifier.height(8.dp))
                WebcamProtocolCard("WebRTC Sub-100ms Stream", "http://$serverIp:9090/stream", isWebRtc) { isWebRtc = it }

                Spacer(modifier = Modifier.height(16.dp))

                // Blackout Mode Toggle
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isBlackoutPowerSaving) Color(0xFF00E676).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, if (isBlackoutPowerSaving) Color(0xFF00E676) else Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🔋 Black Screen Power Saving Mode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Turn off screen preview to reduce heat and save maximum battery during live streaming.", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(checked = isBlackoutPowerSaving, onCheckedChange = { isBlackoutPowerSaving = it })
                    }
                }
            }
        }
    }
}

@Composable
private fun WebcamProtocolCard(
    title: String,
    url: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isEnabled) Color.Yellow.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, if (isEnabled) Color.Yellow.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(url, color = Color.Yellow, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Switch(checked = isEnabled, onCheckedChange = onToggle, modifier = Modifier.scale(0.85f))
        }
    }
}
