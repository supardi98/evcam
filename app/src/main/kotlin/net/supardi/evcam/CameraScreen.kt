package net.supardi.evcam

import net.supardi.evcam.ui.*
import net.supardi.evcam.logic.*
import androidx.compose.foundation.Image

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState

import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.toRect

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.hardware.camera2.CaptureRequest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowManager
import android.widget.Toast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timer10
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit




@SuppressLint("MissingPermission")
@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? android.app.Activity
    val prefs = context.getSharedPreferences("evcam_prefs", android.content.Context.MODE_PRIVATE)
    
    val uiState = rememberCameraUiState(context, prefs)
    
    var cameraMode by uiState::cameraMode
    var isProMode by uiState::isProMode
    var lensFacing by uiState::lensFacing
    var isRecording by uiState::isRecording
    var activeRecording by uiState::activeRecording
    
    var lastCapturedUri by uiState::lastCapturedUri
    var lastCapturedBitmap by uiState::lastCapturedBitmap
    
    // var cameraControl by uiState::cameraControl
    // var camera2Control by uiState::camera2Control
    var iso by uiState::iso
    var minIso by uiState::minIso
    var maxIso by uiState::maxIso
    var shutterSpeed by uiState::shutterSpeed
    var focusDistance by uiState::focusDistance
    var whiteBalance by uiState::whiteBalance
    
    var isIsoAuto by uiState::isIsoAuto
    var isShutterAuto by uiState::isShutterAuto
    var isFocusAuto by uiState::isFocusAuto
    var enableHistogram by uiState::enableHistogram

    var enableFocusPeaking by uiState::enableFocusPeaking
    var enableRawCapture by uiState::enableRawCapture
    var manualKelvin by uiState::manualKelvin
    var timerBurstCount by uiState::timerBurstCount
    
    var histogramData by uiState::histogramData
    var histogramUpdateCount by uiState::histogramUpdateCount
    var peakingBitmap by uiState::peakingBitmap
    var peakingUpdateCount by uiState::peakingUpdateCount
    
    var isBursting by uiState::isBursting
    var burstCount by uiState::burstCount
    
    var showMediaPreviewDialog by uiState::showMediaPreviewDialog

    
    var keepScreenOn by uiState::keepScreenOn
    var maxBrightness by uiState::maxBrightness

    
    var minZoomRatio by uiState::minZoomRatio
    var maxZoomRatio by uiState::maxZoomRatio
    var currentZoom by uiState::currentZoom
    val zoomAnim = uiState.zoomAnim
    
    var focusOffset by uiState::focusOffset
    var showFocusBox by uiState::showFocusBox
    var focusState by uiState::focusState
    var isAeAfLocked by uiState::isAeAfLocked
    var recordingSeconds by uiState::recordingSeconds
    var isTransitioningRatio by uiState::isTransitioningRatio
    
    var gridType by uiState::gridType
    var flashMode by uiState::flashMode
    var timerMode by uiState::timerMode
    var showVirtualHorizon by uiState::showVirtualHorizon
    var showZoomSlider by uiState::showZoomSlider
    var showBrightnessSlider by uiState::showBrightnessSlider
    var minExposureIndex by uiState::minExposureIndex
    var maxExposureIndex by uiState::maxExposureIndex
    var exposureStep by uiState::exposureStep
    var exposureIndex by uiState::exposureIndex
    var isTorchOn by uiState::isTorchOn
    var volumeShutterEnabled by uiState::volumeShutterEnabled
    
    var showPluginManager by uiState::showPluginManager
    var isShutterSoundEnabled by uiState::isShutterSoundEnabled
    var showWatermark by uiState::showWatermark
    var watermarkElements by uiState::watermarkElements
    var enableGeotagging by uiState::enableGeotagging
    var showWatermarkDialog by uiState::showWatermarkDialog
    var liveLocation by uiState::liveLocation
    var liveAddress by uiState::liveAddress
    
    var aspectRatio by uiState::aspectRatio
    var videoQuality by uiState::videoQuality
    var videoFps by uiState::videoFps
    var videoAudioEnabled by uiState::videoAudioEnabled
    var isNightModeEnabled by uiState::isNightModeEnabled
    var isHdrEnabled by uiState::isHdrEnabled
    var selectedFilter by uiState::selectedFilter


    
    var imageCaptureUseCase by uiState::imageCaptureUseCase
    var videoCaptureUseCase by uiState::videoCaptureUseCase

    val triggerVibe = {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(55, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(55)
                }
            }
        } catch (e: Exception) {}
    }

    val proAnalyzer = remember {
        ProAnalyzer(
            enableHistogram = enableHistogram,
            enableFocusPeaking = enableFocusPeaking,
            onHistogramUpdate = { 
                histogramData = it 
                histogramUpdateCount++
            },
            onPeakingUpdate = { 
                peakingBitmap = it 
                peakingUpdateCount++
            }
        )
    }
    
    LaunchedEffect(enableHistogram, enableFocusPeaking) {
        proAnalyzer.enableHistogram = enableHistogram
        proAnalyzer.enableFocusPeaking = enableFocusPeaking
    }

    val targetRatio = if (cameraMode == CameraMode.VIDEO) {
        9f / 16f
    } else {
        when (aspectRatio) {
            AspectRatioMode.RATIO_16_9 -> 9f / 16f
            AspectRatioMode.RATIO_4_3 -> 3f / 4f
            AspectRatioMode.RATIO_1_1 -> 1f
        }
    }

    val animatedAspectRatio by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetRatio,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 350, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "AspectRatioAnimation",
        finishedListener = {
            isTransitioningRatio = false
        }
    )


    
    DisposableEffect(context, imageCaptureUseCase, videoCaptureUseCase) {
        val orientationEventListener = object : android.view.OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45..134 -> android.view.Surface.ROTATION_270
                    in 135..224 -> android.view.Surface.ROTATION_180
                    in 225..314 -> android.view.Surface.ROTATION_90
                    else -> android.view.Surface.ROTATION_0
                }
            }
        }
        orientationEventListener.enable()
        onDispose { orientationEventListener.disable() }
    }
    
    // Fetch live location if any element uses location
    LaunchedEffect(watermarkElements) { 
        val hasLocation = watermarkElements.any { it.type == WatermarkElementType.LOCATION }
        if (hasLocation && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
            val location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) 
                ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                liveLocation = location
                try {
                    val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (addresses != null && addresses.isNotEmpty()) {
                        liveAddress = addresses[0]
                    }
                } catch(e: Exception) {}
            }
        }
    }





    
    val deviceOrientation = rememberDeviceOrientation()
    val deviceRotation = deviceOrientation.roll
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    var countdownValue by remember { mutableStateOf<Int?>(null) }
    var timerJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showShutterFlash by remember { mutableStateOf(false) }
    
    val mediaActionSound = remember {
        android.media.MediaActionSound().apply {
            load(android.media.MediaActionSound.SHUTTER_CLICK)
        }
    }
    
    DisposableEffect(Unit) {
        onDispose { mediaActionSound.release() }
    }
    
    
    LaunchedEffect(zoomAnim.value, showZoomSlider) {
        if (showZoomSlider) {
            delay(3000)
            showZoomSlider = false
        }
    }
    
    LaunchedEffect(Unit) {
        uiState.isBursting = false
        focusRequester.requestFocus()
    }

    LaunchedEffect(keepScreenOn) {
        activity?.window?.let { window ->
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    LaunchedEffect(maxBrightness) {
        activity?.window?.let { window ->
            val layoutParams = window.attributes
            layoutParams.screenBrightness = if (maxBrightness) 1.0f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = layoutParams
        }
    }
    
    val camera2Engine = remember { Camera2Engine(context) }
    
    DisposableEffect(lifecycleOwner) {
        camera2Engine.startBackgroundThread()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE, androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    camera2Engine.closeCamera()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    val manager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                    val camId = manager.cameraIdList.firstOrNull { id ->
                        manager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == lensFacing
                    }
                    if (camId != null) {
                        camera2Engine.openCamera(camId)
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            camera2Engine.closeCamera()
            camera2Engine.stopBackgroundThread()
        }
    }
    
    LaunchedEffect(isTorchOn) {
        camera2Engine.setTorchState(isTorchOn)
    }
    
    val textureView = remember { net.supardi.evcam.ui.AutoFitTextureView(context) }
    
    LaunchedEffect(lensFacing) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        
        val camId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == lensFacing
        }
        
        if (camId != null) {
            val chars = manager.getCameraCharacteristics(camId)
            val maxDigital = chars.get(android.hardware.camera2.CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
            minZoomRatio = 1f
            maxZoomRatio = maxDigital.coerceAtLeast(1f)
            
            val aeRange = chars.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            if (aeRange != null) {
                minExposureIndex = aeRange.lower
                maxExposureIndex = aeRange.upper
            }
            
            camera2Engine.closeCamera()
            camera2Engine.openCamera(camId)
        }
    }
    
    LaunchedEffect(exposureIndex) {
        camera2Engine.setExposureCompensation(exposureIndex)
    }

    val activeCamId = if (lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) "1" else "0"

    val cameraState by camera2Engine.cameraState.collectAsState()

    LaunchedEffect(uiState.videoQuality, lensFacing, cameraState) {
        if (cameraState is Camera2Engine.CameraState.Opened) {
            val fpsList = camera2Engine.getSupportedFpsForQuality(activeCamId, uiState.videoQuality)
            uiState.supportedFpsModes = fpsList
            if (!fpsList.contains(uiState.videoFps)) {
                uiState.videoFps = fpsList.firstOrNull() ?: VideoFpsMode.FPS_30
            }
        }
    }
    
    LaunchedEffect(cameraState) {
        if (cameraState is Camera2Engine.CameraState.Opened) {
            val videoCaps = camera2Engine.queryVideoCapabilities(activeCamId)
            uiState.supportedVideoQualities = videoCaps.supportedQualities
            val fpsList = camera2Engine.getSupportedFpsForQuality(activeCamId, uiState.videoQuality)
            uiState.supportedFpsModes = fpsList
            uiState.supportedVideoProfiles = videoCaps.profileDescriptions

            if (!videoCaps.supportedQualities.contains(uiState.videoQuality)) {
                uiState.videoQuality = videoCaps.supportedQualities.firstOrNull() ?: VideoQualityMode.FHD
            }
            if (!fpsList.contains(uiState.videoFps)) {
                uiState.videoFps = fpsList.firstOrNull() ?: VideoFpsMode.FPS_30
            }

            val device = (cameraState as Camera2Engine.CameraState.Opened).device
            val startPreview = {
                textureView.surfaceTexture?.setDefaultBufferSize(1920, 1080)
                val surface = android.view.Surface(textureView.surfaceTexture)
                camera2Engine.setupImageReader(1920, 1080)
                val tempVideoFile = java.io.File(context.cacheDir, "temp_video.mp4").absolutePath
                camera2Engine.setupMediaRecorder(width = 1920, height = 1080, fps = 30, audioEnabled = true, outputFile = tempVideoFile)
                
                val targets = mutableListOf<android.view.Surface>(surface)
                val persistentSurface = camera2Engine.getOrCreatePersistentSurface()
                targets.add(persistentSurface)
                camera2Engine.imageReader?.surface?.let { targets.add(it) }
                camera2Engine.analysisImageReader?.surface?.let { targets.add(it) }
                
                camera2Engine.analysisImageReader?.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        if (enableHistogram || enableFocusPeaking) {
                            proAnalyzer.analyze(image, 0)
                        } else {
                            image.close()
                        }
                    }
                }, camera2Engine.backgroundHandler)
                
                camera2Engine.createPreviewSession(targets) { _ ->
                    isTransitioningRatio = false
                }
            }
            if (textureView.isAvailable) {
                startPreview()
            } else {
                textureView.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                        startPreview()
                    }
                    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture) = true
                    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
                }
            }
        }
    }

    // Single Animatable for zoom:
    // - gesture (pinch/drag/slider): zoomAnim.snapTo() — instant, cancels any running animation
    // - preset button (1x/2x/5x):   zoomAnim.animateTo() — smooth 280ms easing

    // Apply zoom to camera whenever zoomAnim.value changes (from animation OR snap)
    LaunchedEffect(zoomAnim) {
        androidx.compose.runtime.snapshotFlow { zoomAnim.value }
            .collect { value ->
                currentZoom = value
                camera2Engine.setZoomRatio(value)
            }
    }
    // Also re-apply when cameraControl changes (new camera bound)
    // LaunchedEffect(cameraControl) {
    //     cameraControl?.setZoomRatio(zoomAnim.value)
    // }

    
    val executeCapture = {
        if (cameraMode == CameraMode.PHOTO) {
            triggerVibe()
            coroutineScope.launch {
                showShutterFlash = true
                delay(150)
                showShutterFlash = false
            }
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val originalVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_SYSTEM)
            if (isShutterSoundEnabled) {
                mediaActionSound.play(android.media.MediaActionSound.SHUTTER_CLICK)
            } else {
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_SYSTEM, 0, 0)
            }
            takePhoto(context, camera2Engine, flashMode, selectedFilter, showWatermark, watermarkElements, liveLocation, liveAddress, enableGeotagging, enableRawCapture, aspectRatio, deviceRotation.toInt()) { bitmap, uri ->
                lastCapturedBitmap = bitmap
                lastCapturedUri = uri
                prefs.edit().putString("lastCapturedUri", uri.toString()).apply()
                if (!isShutterSoundEnabled) {
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_SYSTEM, originalVolume, 0)
                }
            }

        } else {
            triggerVibe() // Vibrate both at the start and end of video recording
            if (isRecording) {
                val stopMethod = activeRecording?.javaClass?.getMethod("stop")
                stopMethod?.invoke(activeRecording)
                isRecording = false
            } else {
                val (vWidth, vHeight) = when (uiState.videoQuality) {
                    VideoQualityMode.UHD -> Pair(3840, 2160)
                    VideoQualityMode.QHD -> Pair(2560, 1440)
                    VideoQualityMode.FHD -> Pair(1920, 1080)
                    VideoQualityMode.HD -> Pair(1280, 720)
                    VideoQualityMode.SD -> Pair(640, 480)
                }
                val vFps = uiState.videoFps.fps

                activeRecording = startVideoRecord(
                    context = context,
                    camera2Engine = camera2Engine,
                    width = vWidth,
                    height = vHeight,
                    fps = vFps,
                    audioEnabled = videoAudioEnabled,
                    onMediaSaved = { bitmap, uri ->
                        lastCapturedUri = uri
                        prefs.edit().putString("lastCapturedUri", uri.toString()).apply()
                    },
                    onEvent = { event ->
                        if (event == "Start") isRecording = true
                        else if (event == "Finalize") {
                            isRecording = false
                        }
                    }
                )
            }
        }
    }

    
    LaunchedEffect(isBursting) {
        if (isBursting) {
            burstCount = 0
            while(isBursting) {
                executeCapture()
                burstCount++
                kotlinx.coroutines.delay(200)
            }
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        }
    }
    
    val initiateCapture = {
        if (countdownValue != null) {
            // Cancel running timer and capture immediately
            timerJob?.cancel()
            timerJob = null
            countdownValue = null
            executeCapture()
        } else {
            if (cameraMode == CameraMode.PHOTO && timerMode != TimerMode.OFF) {
                timerJob = coroutineScope.launch {
                    for (i in timerMode.seconds downTo 1) {
                        countdownValue = i
                        delay(1000)
                    }
                    countdownValue = null
                    repeat(timerBurstCount) {
                        executeCapture()
                        if (timerBurstCount > 1) delay(300)
                    }
                }
            } else {
                executeCapture()
            }
        }
    }

    LaunchedEffect(isProMode, iso, shutterSpeed, focusDistance, whiteBalance, manualKelvin, isIsoAuto, isShutterAuto, isFocusAuto) {
        camera2Engine.setProSettings(
            isProMode = isProMode,
            isIsoAuto = isIsoAuto,
            iso = iso.toInt(),
            isShutterAuto = isShutterAuto,
            shutterSpeed = shutterSpeed.toLong(),
            isFocusAuto = isFocusAuto,
            focusDistance = focusDistance,
            whiteBalance = whiteBalance.toInt(),
            manualKelvin = manualKelvin.toInt()
        )
    }

    LaunchedEffect(isNightModeEnabled, isHdrEnabled) {
        camera2Engine.setSceneMode(isNightModeEnabled, isHdrEnabled)
    }

    LaunchedEffect(cameraState) {
        camera2Engine.onAfStateCallback = { afState ->
            when (afState) {
                android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN,
                android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN -> {
                    if (showFocusBox) {
                        uiState.focusState = FocusState.SEARCHING
                    }
                }
                android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED -> {
                    if (showFocusBox) {
                        uiState.focusState = FocusState.SUCCESS
                    }
                }
                android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED,
                android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED -> {
                    if (showFocusBox) {
                        uiState.focusState = FocusState.FAILED
                    }
                }
            }
        }
    }


    Box(modifier = modifier
        .fillMaxSize()
        .background(Color.Black)
        .focusRequester(focusRequester)
        .focusable()
        .onKeyEvent { event ->
            if (volumeShutterEnabled && event.type == KeyEventType.KeyDown && 
                (event.key.nativeKeyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || 
                 event.key.nativeKeyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) {
                initiateCapture()
                true
            } else {
                false
            }
        }) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.Center)
            .clipToBounds()
            .aspectRatio(animatedAspectRatio)
        ) {
            CameraViewfinder(
                previewView = textureView,
                uiState = uiState,
                coroutineScope = coroutineScope,
                camera2Engine = camera2Engine,
                modifier = Modifier.fillMaxSize()
            )


            
            if (showWatermark && cameraMode == CameraMode.PHOTO) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val normalizedRotation = (deviceRotation % 360f + 360f) % 360f

                    val snapAngle = when {
                        normalizedRotation < 45f || normalizedRotation >= 315f -> 0f
                        normalizedRotation < 135f -> 90f
                        normalizedRotation < 225f -> 180f
                        else -> 270f
                    }
                    val isLandscape = snapAngle == 90f || snapAngle == 270f
                    val boxW = if (isLandscape) maxHeight else maxWidth
                    val boxH = if (isLandscape) maxWidth else maxHeight

                    Box(
                        modifier = Modifier
                            .size(boxW, boxH)
                            .align(Alignment.Center)
                            .graphicsLayer { rotationZ = snapAngle }
                            .padding(16.dp)
                    ) {
                        WatermarkQuadrant.values().forEach { quadrant ->
                            val elementsInQuadrant = watermarkElements.filter { it.quadrant == quadrant }
                            if (elementsInQuadrant.isNotEmpty()) {
                                val alignment = when (quadrant) {
                                    WatermarkQuadrant.TOP_LEFT -> Alignment.TopStart
                                    WatermarkQuadrant.TOP_RIGHT -> Alignment.TopEnd
                                    WatermarkQuadrant.BOTTOM_LEFT -> Alignment.BottomStart
                                    WatermarkQuadrant.BOTTOM_RIGHT -> Alignment.BottomEnd
                                }
                                Column(modifier = Modifier.align(alignment).fillMaxWidth()) {
                                    elementsInQuadrant.forEach { element ->
                                        val text = when (element.type) {
                                            WatermarkElementType.TEXT -> element.content
                                            WatermarkElementType.LOCATION -> formatLocationElement(element.content, liveLocation, liveAddress)
                                            WatermarkElementType.DATE -> formatDateElement(element.content)
                                        }
                                        Text(
                                            text = text,
                                            color = Color.White,
                                            fontSize = element.size.sp,
                                            textAlign = if (quadrant == WatermarkQuadrant.TOP_RIGHT || quadrant == WatermarkQuadrant.BOTTOM_RIGHT) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 4f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (enableFocusPeaking && peakingBitmap != null && cameraMode == CameraMode.PHOTO) {
                @Suppress("UNUSED_VARIABLE")
                val count = peakingUpdateCount // trigger recomposition
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawImage(
                        image = peakingBitmap!!.asImageBitmap(),
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                    )
                }
            }
            
            if (enableHistogram && histogramData != null && cameraMode == CameraMode.PHOTO) {
                @Suppress("UNUSED_VARIABLE")
                val count = histogramUpdateCount // trigger recomposition
                androidx.compose.foundation.Canvas(

                    modifier = Modifier
                        .padding(16.dp)
                        .size(100.dp, 60.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .align(Alignment.TopStart)
                ) {
                    val data = histogramData!!
                    val maxCount = (data.maxOrNull()?.toFloat() ?: 1f).coerceAtLeast(1f)

                    val barWidth = size.width / data.size
                    
                    val path = androidx.compose.ui.graphics.Path()
                    path.moveTo(0f, size.height)
                    
                    for (i in data.indices) {
                        val normalizedHeight = (data[i] / maxCount) * size.height
                        val x = i * barWidth
                        val y = size.height - normalizedHeight
                        path.lineTo(x, y)
                    }
                    path.lineTo(size.width, size.height)
                    path.close()
                    
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = androidx.compose.ui.graphics.drawscope.Fill
                    )
                }
            }

            if (showFocusBox && focusOffset != null) {
                val focusColor = when (focusState) {
                    FocusState.SEARCHING -> Color.Yellow
                    FocusState.SUCCESS -> Color.Green
                    FocusState.FAILED -> Color.Red
                }
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(androidx.compose.ui.platform.LocalDensity.current) { focusOffset!!.x.toDp() - 30.dp },
                            y = with(androidx.compose.ui.platform.LocalDensity.current) { focusOffset!!.y.toDp() - 30.dp }
                        )
                        .size(60.dp)
                        .border(2.dp, focusColor, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                )
                LaunchedEffect(focusState, focusOffset) {
                    if (focusState == FocusState.SUCCESS || focusState == FocusState.FAILED) {
                        delay(1200)
                        showFocusBox = false
                        focusState = FocusState.SEARCHING
                    }
                }
            }
            
            if (gridType != GridType.NONE) {
                GridOverlay(gridType = gridType, modifier = Modifier.fillMaxSize())
            }

            if (showVirtualHorizon) {
                val orientData = DeviceOrientationData(deviceOrientation.roll, deviceOrientation.pitch, deviceOrientation.isFlat)
                VirtualHorizonOverlay(deviceOrientation = orientData, modifier = Modifier.fillMaxSize())
            }


        }
        
        if (maxZoomRatio > minZoomRatio && showZoomSlider) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val zoomInteractionSource = remember { MutableInteractionSource() }
                    val isZoomDragged by zoomInteractionSource.collectIsDraggedAsState()
                    

                    Slider(
                        value = zoomAnim.value,
                        onValueChange = { targetVal ->
                            coroutineScope.launch {
                                if (isZoomDragged) {
                                    // User is dragging knob natively: update instantly and lightweight
                                    zoomAnim.snapTo(targetVal)
                                } else {
                                    // User tapped/clicked on slider track: perform smooth transition
                                    zoomAnim.animateTo(
                                        targetValue = targetVal,
                                        animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                    )
                                }
                            }
                        },
                        valueRange = minZoomRatio..maxZoomRatio,
                        interactionSource = zoomInteractionSource,
                        modifier = Modifier
                            .requiredWidth(250.dp)
                            .graphicsLayer { rotationZ = 270f }
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.1fx", currentZoom),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        if (showBrightnessSlider && maxExposureIndex > minExposureIndex && !isProMode) {

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Slider(
                        value = exposureIndex.toFloat(),
                        onValueChange = {
                            val newIdx = it.toInt()
                            exposureIndex = newIdx
                        },
                        valueRange = minExposureIndex.toFloat()..maxExposureIndex.toFloat(),
                        modifier = Modifier
                            .requiredWidth(250.dp)
                            .graphicsLayer { rotationZ = 270f }
                    )
                }
                val evVal = exposureIndex * exposureStep
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable {
                            exposureIndex = 0
                            // cameraControl?.setExposureCompensationIndex(0)
                            showBrightnessSlider = false
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%+.1f EV", evVal),
                        color = if (exposureIndex == 0) Color.White else Color.Yellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    if (exposureIndex != 0) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Reset EV to 0.0",
                            tint = Color.Yellow,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
        

        if (showShutterFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(12.dp, Color.White)
            )
        }
        

        
        if (countdownValue != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = countdownValue.toString(),
                    color = Color.White,
                    fontSize = 150.sp,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black,
                            offset = Offset(4f, 4f),
                            blurRadius = 16f
                        )
                    )
                )
            }
        }
        

        
        // Top Center Overlays: Video Duration & AE/AF Lock Badge (Positioned below the top action bar)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 104.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            if (isRecording) {
                val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.2f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(500),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = alpha))
                    )
                    Text(
                        text = String.format(Locale.US, "%02d:%02d", recordingSeconds / 60, recordingSeconds % 60),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (isAeAfLocked) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Yellow)
                        .clickable { 
                            isAeAfLocked = false
                            // cameraControl?.cancelFocusAndMetering()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "AE/AF LOCK",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        TopCameraBar(uiState = uiState)


        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .pointerInput(Unit) { /* consume touches so panel clicks don't fall through to camera preview */ },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SettingsPopupPanels(
                uiState = uiState,
                onOpenWatermarkSettings = { showWatermarkDialog = true },
                onOpenPluginManager = { showPluginManager = true }
            )


            val zoomOptions = mutableListOf<Float>()
            if (minZoomRatio < 1f) zoomOptions.add(minZoomRatio)
            zoomOptions.add(1f)
            if (maxZoomRatio >= 2f) zoomOptions.add(2f)
            if (maxZoomRatio >= 5f) zoomOptions.add(5f)

            if (zoomOptions.size > 1) {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    zoomOptions.forEach { zoomVal ->
                        val label = if (zoomVal < 1f) String.format(Locale.US, "%.1fx", zoomVal) else "${zoomVal.toInt()}x"
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (kotlin.math.abs(zoomAnim.value - zoomVal) < 0.05f) Color.Yellow.copy(alpha = 0.3f) else Color.Transparent)
                                .clickable {
                                    showZoomSlider = true
                                    coroutineScope.launch {
                                        zoomAnim.snapTo(zoomAnim.value) // start from current
                                        zoomAnim.animateTo(
                                            targetValue = zoomVal,
                                            animationSpec = androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                        )
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label, 
                                color = if (kotlin.math.abs(zoomAnim.value - zoomVal) < 0.05f) Color.Yellow else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            ModeSwitchAndProControls(uiState = uiState)


            
            if (isBursting && burstCount > 0) {
                Text(
                    text = "$burstCount",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(24.dp))
            }
            CameraBottomBar(
                lastCapturedBitmap = lastCapturedBitmap,
                lastCapturedUri = lastCapturedUri,
                context = context,
                cameraMode = cameraMode,
                isRecording = isRecording,
                isFrontCamera = lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT,
                onThumbnailClick = { showMediaPreviewDialog = true },
                // Photo mode
                onShutterTap = { initiateCapture() },
                onBurstStart = { isBursting = true },
                onBurstEnd = { isBursting = false },
                // Video mode
                onVideoTap = { initiateCapture() },
                onVideoTapStop = { initiateCapture() },
                onQuickRecordStart = {
                    if (!isRecording) initiateCapture()
                },
                onQuickRecordStop = {
                    if (isRecording) initiateCapture()
                },
                onDragZoom = { deltaY ->
                    showZoomSlider = true
                    val zoomStep = (deltaY / 300f) * (maxZoomRatio - minZoomRatio)
                    val newZoom = (zoomAnim.value + zoomStep).coerceIn(minZoomRatio, maxZoomRatio)
                    coroutineScope.launch { zoomAnim.snapTo(newZoom) }
                },
                onSwitchCamera = {
                    if (!isRecording) {
                        lensFacing = if (lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT else android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
                    }
                }
            )
        }
        

        DialogContainers(uiState = uiState)
    }
}








