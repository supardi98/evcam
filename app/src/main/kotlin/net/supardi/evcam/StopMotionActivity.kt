package net.supardi.evcam

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import net.supardi.evcam.logic.*
import net.supardi.evcam.ui.theme.EvcamTheme
import java.io.File

class StopMotionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).let {
            it.show(WindowInsetsCompat.Type.statusBars())
            it.isAppearanceLightStatusBars = false
        }

        setContent {
            EvcamTheme {
                StopMotionScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopMotionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = remember { StopMotionViewModel(context) }
    val camera2Engine = remember { Camera2Engine(context) }

    var textureReady by remember { mutableStateOf(false) }
    val textureViewRef = remember { mutableStateOf<TextureView?>(null) }

    // Camera state
    val cameraState by camera2Engine.cameraState.collectAsState()
    var activeCamId by remember { mutableStateOf("0") }

    // Last frame bitmap for onion skin
    var onionBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Frame list state
    var selectedFrameIndex by remember { mutableStateOf<Int?>(null) }
    val frameListState = rememberLazyListState()

    // Auto scroll to latest frame
    LaunchedEffect(viewModel.frames.size) {
        if (viewModel.frames.isNotEmpty()) {
            frameListState.animateScrollToItem(viewModel.frames.size - 1)
            // Update onion skin
            onionBitmap = try {
                android.graphics.BitmapFactory.decodeFile(viewModel.frames.last().absolutePath)
            } catch (e: Exception) { null }
        }
    }

    // Camera lifecycle
    DisposableEffect(Unit) {
        camera2Engine.startBackgroundThread()
        camera2Engine.openCamera(activeCamId)
        onDispose {
            camera2Engine.closeCamera()
            camera2Engine.stopBackgroundThread()
            viewModel.cleanup()
        }
    }

    // Open camera when state is ready
    LaunchedEffect(cameraState, textureReady, viewModel.aspectRatio, viewModel.resolution) {
        if (cameraState is Camera2Engine.CameraState.Opened && textureReady) {
            val tv = textureViewRef.value ?: return@LaunchedEffect
            val baseWidth = (viewModel.resolution.baseHeight * viewModel.aspectRatio.ratio).toInt()
            val baseHeight = viewModel.resolution.baseHeight
            tv.surfaceTexture?.setDefaultBufferSize(baseWidth, baseHeight)
            val surface = android.view.Surface(tv.surfaceTexture)
            camera2Engine.setupImageReader(baseWidth, baseHeight)
            camera2Engine.createPreviewSession(listOf(surface, camera2Engine.imageReader!!.surface)) {}
        }
    }

    // Capture function - uses capturePhoto engine call to trigger JPEG capture
    fun captureFrame() {
        camera2Engine.capturePhoto(
            isUltraMode = false,
            activeCustomScene = net.supardi.evcam.logic.CustomSceneMode.AUTO,
            flashMode = net.supardi.evcam.logic.FlashMode.OFF,
            isIsoAuto = true,
            isShutterAuto = true,
            onImageCaptured = { image ->
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    viewModel.addFrame(bytes)
                } finally {
                    image.close()
                }
            }
        )
    }

    viewModel.onCaptureRequest = { captureFrame() }

    // Export result handler
    if (viewModel.exportSuccess == true) {
        LaunchedEffect(Unit) {
            viewModel.exportSuccess = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ── Camera Preview ──────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).also { tv ->
                    textureViewRef.value = tv
                    tv.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                            textureReady = true
                        }
                        override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture) = true
                        override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
                    }
                }
            }
        )

        // ── Onion Skin Overlay ──────────────────────────────────────
        if (onionBitmap != null && viewModel.onionSkin.alpha > 0f) {
            androidx.compose.foundation.Image(
                bitmap = onionBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = viewModel.onionSkin.alpha * 0.8f),
                contentScale = ContentScale.Crop
            )
        }

        // ── Top Bar ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "STOP MOTION",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp
                )
                Text(
                    "${viewModel.frames.size} frames",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }

            IconButton(onClick = { viewModel.showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }

        // ── Auto Interval Badge ──────────────────────────────────────
        if (viewModel.interval != StopMotionInterval.MANUAL) {
            Box(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)) {
                val pulse = rememberInfiniteTransition(label = "pulse")
                val scale by pulse.animateFloat(
                    initialValue = 1f, targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "scale"
                )
                Surface(
                    color = if (viewModel.isCapturing) Color(0xFFFF3B30) else Color(0xFF1C1C1E).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.graphicsLayer(scaleX = if (viewModel.isCapturing) scale else 1f, scaleY = if (viewModel.isCapturing) scale else 1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (viewModel.isCapturing) {
                                "${kotlin.math.ceil(viewModel.remainingMs / 1000f).toInt()}s"
                            } else {
                                if (viewModel.interval == StopMotionInterval.CUSTOM) "Custom (${viewModel.customIntervalMs / 1000f}s)" else viewModel.interval.label
                            },
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ── Bottom Panel ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .navigationBarsPadding()
        ) {

            // Frame Strip
            if (viewModel.frames.isNotEmpty()) {
                LazyRow(
                    state = frameListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    itemsIndexed(viewModel.frames) { index, file ->
                        val bmp = remember(file.lastModified()) {
                            runCatching {
                                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                                android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
                            }.getOrNull()
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp, 64.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2C2C2E))
                                .clickable { selectedFrameIndex = index }
                                .border(
                                    if (index == viewModel.frames.size - 1) 2.dp else 0.dp,
                                    if (index == viewModel.frames.size - 1) Color(0xFFFFD60A) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                        ) {
                            if (bmp != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            // Frame number badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(2.dp)
                                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            ) {
                                Text("${index + 1}", color = Color.White, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Delete last frame button
                IconButton(
                    onClick = { viewModel.deleteLastFrame() },
                    enabled = viewModel.frames.isNotEmpty() && !viewModel.isCapturing
                ) {
                    Icon(
                        Icons.Default.Undo, contentDescription = "Delete last",
                        tint = if (viewModel.frames.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Main Capture / Auto toggle button
                val captureScale by animateFloatAsState(if (viewModel.isCapturing) 0.92f else 1f, label = "cap")
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer(scaleX = captureScale, scaleY = captureScale)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            when {
                                viewModel.interval == StopMotionInterval.MANUAL -> captureFrame()
                                viewModel.isCapturing -> viewModel.stopAutoCapture()
                                else -> viewModel.startAutoCapture()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        viewModel.interval != StopMotionInterval.MANUAL && viewModel.isCapturing -> {
                            Icon(Icons.Default.Stop, contentDescription = "Stop auto", tint = Color(0xFFFF3B30), modifier = Modifier.size(32.dp))
                        }
                        viewModel.interval != StopMotionInterval.MANUAL -> {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start auto", tint = Color.Black, modifier = Modifier.size(32.dp))
                        }
                        else -> {
                            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.Black))
                        }
                    }
                }

                // Export button
                IconButton(
                    onClick = {
                        viewModel.exportToVideo { outputPath ->
                            if (outputPath != null) {
                                // Save to MediaStore
                                val file = File(outputPath)
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, "StopMotion_${System.currentTimeMillis()}.mp4")
                                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/EVCam")
                                    }
                                }
                                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                                if (uri != null && file.exists()) {
                                    context.contentResolver.openOutputStream(uri)?.use { out ->
                                        file.inputStream().use { it.copyTo(out) }
                                    }
                                }
                                file.delete()
                            }
                        }
                    },
                    enabled = viewModel.frames.size >= 2 && !viewModel.isExporting
                ) {
                    Icon(
                        Icons.Default.Movie, contentDescription = "Export",
                        tint = if (viewModel.frames.size >= 2) Color(0xFFFFD60A) else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Info bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Skin: ${viewModel.onionSkin.label}  •  FPS: ${viewModel.outputFps.label}  •  Timer: ${viewModel.interval.label}  •  Res: ${viewModel.resolution.label} (${viewModel.aspectRatio.label})",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── Export Progress Overlay ──────────────────────────────────
        if (viewModel.isExporting) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(
                        progress = viewModel.exportProgress,
                        modifier = Modifier.size(72.dp),
                        color = Color(0xFFFFD60A),
                        trackColor = Color.White.copy(alpha = 0.2f),
                        strokeWidth = 6.dp
                    )
                    Text(
                        "Compiling ${(viewModel.exportProgress * 100).toInt()}%",
                        color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                    )
                    Text(
                        "${viewModel.frames.size} frames → ${viewModel.outputFps.label}",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp
                    )
                }
            }
        }

        // ── Settings Bottom Sheet ────────────────────────────────────
        if (viewModel.showSettings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { viewModel.showSettings = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) {}
                        .navigationBarsPadding(),
                    color = Color(0xFF1C1C1E),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)

                        // Interval
                        var showCustomDialog by remember { mutableStateOf(false) }
                        
                        SettingRow(
                            label = "Capture Mode",
                            options = StopMotionInterval.values().map { 
                                if (it == StopMotionInterval.CUSTOM) "Custom (${viewModel.customIntervalMs / 1000f}s)" else it.label 
                            },
                            selected = viewModel.interval.ordinal,
                            onSelect = { 
                                val selected = StopMotionInterval.values()[it]
                                viewModel.interval = selected
                                if (selected == StopMotionInterval.CUSTOM) {
                                    showCustomDialog = true
                                }
                            }
                        )

                        if (showCustomDialog) {
                            var customInput by remember { mutableStateOf((viewModel.customIntervalMs / 1000f).toString()) }
                            AlertDialog(
                                onDismissRequest = { showCustomDialog = false },
                                title = { Text("Custom Interval (seconds)") },
                                text = {
                                    OutlinedTextField(
                                        value = customInput,
                                        onValueChange = { customInput = it },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        customInput.toFloatOrNull()?.let { sec ->
                                            if (sec > 0) {
                                                viewModel.customIntervalMs = (sec * 1000).toLong()
                                            }
                                        }
                                        showCustomDialog = false
                                    }) {
                                        Text("Save")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCustomDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        // Output FPS
                        SettingRow(
                            label = "Output FPS",
                            options = StopMotionFps.values().map { it.label },
                            selected = viewModel.outputFps.ordinal,
                            onSelect = { viewModel.outputFps = StopMotionFps.values()[it] }
                        )

                        // Onion skin
                        SettingRow(
                            label = "Onion Skin",
                            options = StopMotionOnionSkin.values().map { it.label },
                            selected = viewModel.onionSkin.ordinal,
                            onSelect = { viewModel.onionSkin = StopMotionOnionSkin.values()[it] }
                        )

                        // Resolution
                        SettingRow(
                            label = "Resolution",
                            options = net.supardi.evcam.logic.StopMotionResolution.values().map { it.label },
                            selected = viewModel.resolution.ordinal,
                            onSelect = { viewModel.resolution = net.supardi.evcam.logic.StopMotionResolution.values()[it] }
                        )

                        // Aspect Ratio
                        SettingRow(
                            label = "Aspect Ratio",
                            options = net.supardi.evcam.logic.StopMotionAspectRatio.values().map { it.label },
                            selected = viewModel.aspectRatio.ordinal,
                            onSelect = { viewModel.aspectRatio = net.supardi.evcam.logic.StopMotionAspectRatio.values()[it] }
                        )

                        // Clear all frames
                        if (viewModel.frames.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    viewModel.clearAllFrames()
                                    onionBitmap = null
                                    viewModel.showSettings = false
                                }
                            ) {
                                Text("Clear all ${viewModel.frames.size} frames", color = Color(0xFFFF3B30))
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // ── Selected Frame Full Screen ───────────────────────────────
        if (selectedFrameIndex != null && selectedFrameIndex!! < viewModel.frames.size) {
            val idx = selectedFrameIndex!!
            val file = viewModel.frames[idx]
            val fullBmp = remember(file.lastModified()) {
                runCatching {
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                }.getOrNull()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { selectedFrameIndex = null } // Click anywhere to close
            ) {
                if (fullBmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = fullBmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                
                // Top bar for preview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { selectedFrameIndex = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        "Frame ${idx + 1}/${viewModel.frames.size}",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                    IconButton(onClick = {
                        viewModel.deleteFrame(idx)
                        selectedFrameIndex = null
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF3B30))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options.size) { idx ->
                val opt = options[idx]
                val isSelected = idx == selected
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color(0xFFFFD60A) else Color(0xFF2C2C2E),
                    modifier = Modifier.clickable { onSelect(idx) }
                ) {
                    Text(
                        opt,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
