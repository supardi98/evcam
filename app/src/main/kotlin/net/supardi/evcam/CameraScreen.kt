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
    var shutterSpeed by uiState::shutterSpeed
    var focusDistance by uiState::focusDistance
    var whiteBalance by uiState::whiteBalance

    var isIsoAuto by uiState::isIsoAuto
    var isShutterAuto by uiState::isShutterAuto
    var isFocusAuto by uiState::isFocusAuto
    var enableHistogram by uiState::enableHistogram

    var enableFocusPeaking by uiState::enableFocusPeaking
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
    var photoQuality by uiState::photoQuality
    var selectedFilter by uiState::selectedFilter

    val flipRotationAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    var isFlippingCamera by remember { mutableStateOf(false) }

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


    
    val camera2Engine = remember { Camera2Engine(context) }



    DisposableEffect(context) {
        val orientationEventListener = object : android.view.OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return
                val degrees = when (orientation) {
                    in 45..134 -> 270
                    in 135..224 -> 180
                    in 225..314 -> 90
                    else -> 0
                }
                camera2Engine.setDeviceOrientation(degrees)
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
    
    var isProcessingHdr by remember { mutableStateOf(false) }
    var hdrProgress by remember { mutableStateOf(0) }

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
    
    var isAppInForeground by remember { mutableStateOf(false) }
    var previewSessionTrigger by remember { mutableStateOf(0) }
    var cameraRenderThread by remember { mutableStateOf<net.supardi.evcam.gl.CameraRenderThread?>(null) }
    val rotationSensorHelper = remember { net.supardi.evcam.logic.RotationSensorHelper(context) }

    LaunchedEffect(isAppInForeground) {
        Log.d("EVCAM", "isAppInForeground: $isAppInForeground, lastCapturedUri: ${uiState.lastCapturedUri}")
        if (isAppInForeground && uiState.lastCapturedUri != null) {
            if (!net.supardi.evcam.logic.isUriValid(context, uiState.lastCapturedUri!!)) {
                Log.d("EVCAM", "URI is no longer valid, fetching latest available media")
                val latestUri = net.supardi.evcam.logic.fetchLatestMediaUri(context)
                uiState.lastCapturedUri = latestUri
                uiState.lastCapturedBitmap = null
                if (latestUri != null) {
                    uiState.prefs.edit().putString("lastCapturedUri", latestUri.toString()).apply()
                } else {
                    uiState.prefs.edit().remove("lastCapturedUri").apply()
                }
            } else {
                Log.d("EVCAM", "URI is still valid")
            }
        }
    }
    
    DisposableEffect(uiState.selectedCustomScene) {
        val isHorizonLock = uiState.selectedCustomScene == CustomSceneMode.HORIZON_LOCK
        if (isHorizonLock) {
            rotationSensorHelper.start()
        } else {
            rotationSensorHelper.stop()
        }
        onDispose {
            rotationSensorHelper.stop()
        }
    }
    
    DisposableEffect(lifecycleOwner) {
        camera2Engine.startBackgroundThread()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE, androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    isAppInForeground = false
                    camera2Engine.closeCamera()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    isAppInForeground = true
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

    LaunchedEffect(uiState.selectedCustomScene) {
        val isHorizonLock = uiState.selectedCustomScene == CustomSceneMode.HORIZON_LOCK
        camera2Engine.isHorizonLockEnabled = isHorizonLock
        camera2Engine.applyCustomSceneMode(uiState.selectedCustomScene)
    }

    
    val textureView = remember { net.supardi.evcam.ui.AutoFitTextureView(context) }
    
    LaunchedEffect(lensFacing, isAppInForeground) {
        if (isAppInForeground) {
            // Small delay to ensure HAL has finished releasing the camera from ON_PAUSE
            kotlinx.coroutines.delay(200)
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

            // Dynamically check if active camera (Front or Back) has hardware LED Flash unit & Manual Focus support
            uiState.hasFlashSupport = net.supardi.evcam.logic.Camera2Helper.hasFlashSupport(chars)
            if (!uiState.hasFlashSupport) {
                uiState.isTorchOn = false
            }
            
            val minFocusDistance = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            uiState.hasManualFocusSupport = minFocusDistance > 0f
            if (!uiState.hasManualFocusSupport) {
                uiState.isFocusAuto = true
            }

            camera2Engine.closeCamera()
            camera2Engine.openCamera(camId)
        }
    }
}
    
    LaunchedEffect(cameraMode) {
        // Automatically turn off continuous Torch & close Pro panel when switching between Photo & Video modes
        uiState.isTorchOn = false
        uiState.showProPanel = false
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
    
    LaunchedEffect(cameraState, aspectRatio, photoQuality, videoQuality, previewSessionTrigger) {
        if (cameraState is Camera2Engine.CameraState.Opened) {
            val videoCaps = camera2Engine.queryVideoCapabilities(activeCamId)
            uiState.supportedVideoQualities = videoCaps.supportedQualities.sortedBy { it.quality }
            val fpsList = camera2Engine.getSupportedFpsForQuality(activeCamId, uiState.videoQuality)
            uiState.supportedFpsModes = fpsList
            uiState.supportedVideoProfiles = videoCaps.profileDescriptions

            if (!videoCaps.supportedQualities.contains(uiState.videoQuality)) {
                uiState.videoQuality = videoCaps.supportedQualities.firstOrNull() ?: VideoQualityMode.FHD
            }
            if (!fpsList.contains(uiState.videoFps)) {
                uiState.videoFps = fpsList.firstOrNull() ?: VideoFpsMode.FPS_30
            }

            // Query hardware characteristics for ISO, Shutter, Focus, and AWB
            try {
                val activeId = (cameraState as Camera2Engine.CameraState.Opened).device.id
                val chars = camera2Engine.cameraManager.getCameraCharacteristics(activeId)

                Camera2Helper.getIsoRange(chars)?.let { (min, max) ->
                    uiState.minIso = min
                    uiState.maxIso = max
                    android.util.Log.d("EVCAM_HW", "Hardware ISO Range: $min .. $max")
                }

                Camera2Helper.getShutterRange(chars)?.let { (min, max) ->
                    uiState.minShutterSpeed = min
                    // Full 30s hardware exposure capability
                    uiState.maxShutterSpeed = maxOf(max, 30_000_000_000f)
                    android.util.Log.d("EVCAM_HW", "Hardware Exposure Time Range (ns): $min .. $max (${max / 1_000_000_000f}s)")
                }




                val minFocusDist = Camera2Helper.getMinimumFocusDistance(chars)
                if (minFocusDist > 0f) {
                    uiState.maxFocusDistance = minFocusDist
                    android.util.Log.d("EVCAM_HW", "Hardware Min Focus Dist (diopters): $minFocusDist")
                }

                val sceneModes = Camera2Helper.getSupportedSceneModes(chars)
                uiState.supportedSceneModes = sceneModes
                android.util.Log.d("EVCAM_HW", "Hardware Scene Modes: $sceneModes")

                val awbModes = Camera2Helper.getSupportedAwbModes(chars)

                if (awbModes.isNotEmpty()) {
                    uiState.supportedAwbModes = awbModes
                    android.util.Log.d("EVCAM_HW", "Hardware AWB Modes: $awbModes")
                }
            } catch (e: Exception) {
                android.util.Log.e("EVCAM", "Failed to query hardware characteristics", e)
            }

            // Encapsulate preview session creation so it can be re-called with different
            // video dimensions when the user starts recording at a different quality.
            fun startPreviewSession(recW: Int = 1440, recH: Int = 1080, recFps: Int = 30) {
                textureView.surfaceTexture?.setDefaultBufferSize(1440, 1080)
                val surface = android.view.Surface(textureView.surfaceTexture)
                
                // Always request ALL sizes (uncropped 4:3 from sensor) so we can software-crop it 
                // perfectly to maximize Megapixels instead of relying on HAL's limited 16:9 sizes.
                val isUltra = uiState.photoQuality == PhotoQualityMode.ULTRA
                camera2Engine.isUltraMode = isUltra
                val photoSizes = camera2Engine.getSupportedStreamResolutions(activeCamId, "ALL", isUltra)
                
                val targetPhotoSize = if (photoSizes.isNotEmpty()) {
                    when (uiState.photoQuality) {
                        PhotoQualityMode.ULTRA -> photoSizes.first()
                        PhotoQualityMode.MAX -> photoSizes.first()
                        PhotoQualityMode.HIGH -> if (photoSizes.size > 1) photoSizes[1] else photoSizes.first()
                        PhotoQualityMode.MEDIUM -> photoSizes.firstOrNull { maxOf(it.second, it.third) <= 2560 } ?: photoSizes[photoSizes.size / 2]
                        PhotoQualityMode.LOW -> photoSizes.last()
                    }
                } else Triple("1080p", 1920, 1080)
                
                camera2Engine.setupImageReader(targetPhotoSize.second, targetPhotoSize.third)
                val tempVideoFile = java.io.File(context.cacheDir, "temp_video.mp4").absolutePath
                camera2Engine.setupMediaRecorder(width = recW, height = recH, fps = recFps, audioEnabled = true, outputFile = tempVideoFile)

                val targets = mutableListOf<android.view.Surface>(surface)
                val persistentSurface = camera2Engine.getOrCreatePersistentSurface()
                targets.add(persistentSurface)
                camera2Engine.imageReader?.surface?.let { targets.add(it) }
                camera2Engine.analysisImageReader?.surface?.let { targets.add(it) }

                camera2Engine.analysisImageReader?.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        try {
                            if (enableHistogram || enableFocusPeaking) {
                                proAnalyzer.analyze(image, 0)
                            }
                        } catch (e: Exception) {

                            android.util.Log.e("EVCAM", "Error processing analysis image", e)
                        } finally {
                            image.close()
                        }
                    }
                }, camera2Engine.backgroundHandler)



                camera2Engine.createPreviewSession(targets) { _ ->
                    isTransitioningRatio = false
                    if (isFlippingCamera) {
                        coroutineScope.launch {
                            flipRotationAnim.snapTo(270f)
                            flipRotationAnim.animateTo(
                                targetValue = 360f,
                                animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            )
                            flipRotationAnim.snapTo(0f)
                            isFlippingCamera = false
                        }
                    }
                }

            }

            textureView.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                    startPreviewSession()
                }
                override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
            }

            if (textureView.isAvailable) {
                startPreviewSession()
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

    // Save power by closing the camera completely if the media preview dialog is open for more than 10 seconds
    LaunchedEffect(showMediaPreviewDialog) {
        if (showMediaPreviewDialog) {
            delay(10000)
            if (showMediaPreviewDialog) {
                camera2Engine.closeCamera()
            }
        } else {
            if (camera2Engine.cameraState.value is Camera2Engine.CameraState.Closed) {
                if (activeCamId.isNotEmpty()) {
                    camera2Engine.openCamera(activeCamId)
                }
            } else {
                camera2Engine.updatePreview()
            }
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
            val isFront = lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
            
            if (uiState.selectedCustomScene == CustomSceneMode.COMPUTATIONAL_HDR) {
                isProcessingHdr = true
                hdrProgress = 0
                takeComputationalHdrPhoto(
                    context, camera2Engine,
                    liveLocation, enableGeotagging, aspectRatio, deviceRotation.toInt(),
                    isFrontCamera = isFront, mirrorSelfie = uiState.mirrorSelfie,
                    onProgress = { progress -> 
                        if (progress == -1) {
                            isProcessingHdr = false // Error
                        } else {
                            hdrProgress = progress
                        }
                    },
                    onPhotoSaved = { bitmap, uri ->
                        lastCapturedBitmap = bitmap
                        lastCapturedUri = uri
                        prefs.edit().putString("lastCapturedUri", uri.toString()).apply()
                        if (!isShutterSoundEnabled) {
                            audioManager.setStreamVolume(android.media.AudioManager.STREAM_SYSTEM, originalVolume, 0)
                        }
                        isProcessingHdr = false
                    }
                )
            } else {
                takePhoto(
                    context, camera2Engine, flashMode, uiState.selectedCustomScene.lockColorFilter ?: selectedFilter, showWatermark, watermarkElements,
                    liveLocation, liveAddress, enableGeotagging, aspectRatio,
                    isFrontCamera = isFront, mirrorSelfie = uiState.mirrorSelfie,
                    customSceneMode = uiState.selectedCustomScene,
                    isUltraMode = camera2Engine.isUltraMode,
                    isIsoAuto = isIsoAuto,
                    iso = iso.toInt(),
                    isShutterAuto = isShutterAuto,
                    shutterSpeed = shutterSpeed.toLong()
                ) { bitmap, uri ->
                    lastCapturedBitmap = bitmap
                    lastCapturedUri = uri
                    prefs.edit().putString("lastCapturedUri", uri.toString()).apply()
                    if (!isShutterSoundEnabled) {
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_SYSTEM, originalVolume, 0)
                    }
                }
            }
        } else {
            triggerVibe() // Vibrate both at the start and end of video recording
                if (isRecording) {
                    activeRecording?.stop()
                    isRecording = false
                    cameraRenderThread?.stopRecording()
                    cameraRenderThread = null
                    textureView.setSensorAspectRatio(3f / 4f)
                    previewSessionTrigger++
                } else {
                    val (vWidth, vHeight) = when (uiState.videoQuality) {
                        VideoQualityMode.UHD -> Pair(3840, 2160)
                        VideoQualityMode.QHD -> Pair(2560, 1440)
                        VideoQualityMode.FHD -> Pair(1920, 1080)
                        VideoQualityMode.HD -> Pair(1280, 720)
                        VideoQualityMode.SD -> Pair(640, 480)
                    }
                    val vFps = uiState.videoFps.fps
                    val tempVideoFile = java.io.File(context.cacheDir, "temp_video.mp4").absolutePath

                    val isHorizonLock = uiState.selectedCustomScene == CustomSceneMode.HORIZON_LOCK
                    val isSlowMotion = uiState.selectedCustomScene == CustomSceneMode.SLOW_MOTION

                    if (isSlowMotion && vFps < 120) {
                        android.widget.Toast.makeText(
                            context,
                            "Slow Motion butuh FPS 120 atau 240. Ubah FPS dulu di pengaturan video.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else if (isSlowMotion) {
                        // Slow motion: pakai regular session, MediaRecorder sudah set
                        // setCaptureRate(120) + setVideoFrameRate(30) → 4x slow saat diputar.
                        // Jauh lebih kompatibel daripada ConstrainedHighSpeedCaptureSession.

                        // Shutter speed enforcement: cap ke 1/fps jika manual terlalu lambat
                        val maxShutterNs = 1_000_000_000L / vFps
                        if (!isShutterAuto && shutterSpeed.toLong() > maxShutterNs) {
                            android.widget.Toast.makeText(
                                context,
                                "Shutter speed dikap ke 1/${vFps}s untuk slow motion",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }

                        val (actualW, actualH) = camera2Engine.setupMediaRecorder(
                            width = vWidth, height = vHeight, fps = vFps,
                            audioEnabled = false, outputFile = tempVideoFile
                        )
                        textureView.surfaceTexture?.setDefaultBufferSize(actualW, actualH)
                        textureView.setSensorAspectRatio(actualH.toFloat() / actualW.toFloat())
                        val texSurface = android.view.Surface(textureView.surfaceTexture)
                        val slowTargets = mutableListOf<android.view.Surface>(texSurface)
                        slowTargets.add(camera2Engine.getOrCreatePersistentSurface())
                        camera2Engine.imageReader?.surface?.let { slowTargets.add(it) }
                        camera2Engine.analysisImageReader?.surface?.let { slowTargets.add(it) }

                        camera2Engine.createPreviewSession(slowTargets, targetFps = vFps) { _ ->
                            activeRecording = startVideoRecord(
                                context = context,
                                camera2Engine = camera2Engine,
                                width = actualW, height = actualH, fps = vFps,
                                recordSurface = camera2Engine.getOrCreatePersistentSurface(),
                                onMediaSaved = { _, uri ->
                                    lastCapturedBitmap = null
                                    lastCapturedUri = uri
                                    prefs.edit().putString("lastCapturedUri", uri.toString()).apply()
                                },
                                onEvent = { event ->
                                    if (event == "Start") isRecording = true
                                    else if (event == "Finalize") isRecording = false
                                }
                            )
                        }
                    } else {
                        val (actualW, actualH) = camera2Engine.setupMediaRecorder(
                            width = vWidth, height = vHeight, fps = vFps,
                             outputFile = tempVideoFile
                        )
                        textureView.surfaceTexture?.setDefaultBufferSize(actualW, actualH)
                        textureView.setSensorAspectRatio(actualH.toFloat() / actualW.toFloat())
                        val texSurface = android.view.Surface(textureView.surfaceTexture)
                        val newTargets = mutableListOf<android.view.Surface>(texSurface)

                        if (isHorizonLock) {
                            val crt = net.supardi.evcam.gl.CameraRenderThread(actualW, actualH, rotationSensorHelper)
                            cameraRenderThread = crt
                            crt.start()
                            crt.awaitSurfaceReady()
                            val pSurface = camera2Engine.getOrCreatePersistentSurface()
                            crt.setOutputSurface(pSurface, actualW, actualH)
                            newTargets.add(crt.cameraSurface!!)
                        } else {
                            camera2Engine.getOrCreatePersistentSurface().let { newTargets.add(it) }
                        }

                        camera2Engine.imageReader?.surface?.let { newTargets.add(it) }
                        camera2Engine.analysisImageReader?.surface?.let { newTargets.add(it) }
                        camera2Engine.createPreviewSession(newTargets) { _ ->
                            activeRecording = startVideoRecord(
                                context = context,
                                camera2Engine = camera2Engine,
                                width = actualW, height = actualH, fps = vFps,
                                recordSurface = if (isHorizonLock) cameraRenderThread!!.cameraSurface!! else camera2Engine.getOrCreatePersistentSurface(),
                                onMediaSaved = { _, uri ->
                                    lastCapturedBitmap = null
                                    lastCapturedUri = uri
                                    prefs.edit().putString("lastCapturedUri", uri.toString()).apply()
                                },
                                onEvent = { event ->
                                    if (event == "Start") isRecording = true
                                    else if (event == "Finalize") isRecording = false
                                }
                            )
                        }
                    }
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

    LaunchedEffect(cameraState, isProMode, iso, shutterSpeed, focusDistance, whiteBalance, manualKelvin, isIsoAuto, isShutterAuto, isFocusAuto, uiState.selectedCustomScene) {
        camera2Engine.setProSettings(
            isProMode = isProMode,
            isIsoAuto = isIsoAuto,
            iso = iso.toInt(),
            isShutterAuto = isShutterAuto,
            shutterSpeed = shutterSpeed.toLong(),
            isFocusAuto = isFocusAuto,
            focusDistance = focusDistance,
            whiteBalance = whiteBalance.toInt(),
            manualKelvin = manualKelvin.toInt(),
            activeCustomScene = uiState.selectedCustomScene
        )
    }

    LaunchedEffect(selectedFilter, uiState.selectedCustomScene, cameraState) {
        val effectiveFilter = uiState.selectedCustomScene.lockColorFilter ?: selectedFilter
        camera2Engine.setColorFilter(effectiveFilter)
    }

    LaunchedEffect(cameraState) {
        camera2Engine.onAwbGainsCallback = { liveKelvin ->
            // Update manualKelvin continuously while in AUTO/preset modes so switching to CUS inherits live Kelvin
            if (uiState.whiteBalance != android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_OFF) {
                uiState.manualKelvin = liveKelvin
            }
        }
        camera2Engine.onFacesDetectedCallback = { faces, cropRegion ->
            uiState.detectedFaces = faces.toList()
            if (uiState.sensorActiveArraySize == null) {
                uiState.sensorActiveArraySize = camera2Engine.sensorActiveArraySize
            }
            uiState.sensorCropRegion = cropRegion
        }
        camera2Engine.onAfStateCallback = { afState ->
            android.util.Log.d("EVCAM_AF", "afState changed: $afState focusState: ${uiState.focusState}")
            if (showFocusBox) {
                when (afState) {
                    android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN,
                    android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN -> {
                        if (uiState.focusState == FocusState.TAP_INITIAL || uiState.focusState == FocusState.SEARCHING) {
                            uiState.focusState = FocusState.SEARCHING
                        }
                    }
                    android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                    android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED,
                    android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_INACTIVE -> {
                        if (uiState.focusState == FocusState.SEARCHING) {
                            uiState.focusState = FocusState.SUCCESS
                            if (uiState.isPendingAfLock) {
                                uiState.isAeAfLocked = true
                                uiState.isPendingAfLock = false
                            }
                        }
                    }
                    android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED,
                    android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED -> {
                        if (uiState.focusState == FocusState.SEARCHING) {
                            uiState.focusState = FocusState.FAILED
                            uiState.isAeAfLocked = false
                            uiState.isPendingAfLock = false
                        }
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
            .graphicsLayer {
                rotationY = flipRotationAnim.value
                cameraDistance = 12f * density
            }
        ) {
            CameraViewfinder(
                previewView = textureView,
                uiState = uiState,
                coroutineScope = coroutineScope,
                camera2Engine = camera2Engine,
                modifier = Modifier.fillMaxSize()
            )
            
            val isHorizonLock = uiState.selectedCustomScene == CustomSceneMode.HORIZON_LOCK
            if (isHorizonLock) {
                val horizonState by rotationSensorHelper.horizonState.collectAsState()
                
                @Suppress("DEPRECATION")
                val displayRotation = (androidx.compose.ui.platform.LocalContext.current.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
                val uiRollOffset = when (displayRotation) {
                    android.view.Surface.ROTATION_90 -> 90f
                    android.view.Surface.ROTATION_180 -> 180f
                    android.view.Surface.ROTATION_270 -> -90f // or 270f
                    else -> 0f
                }
                
                // Adjust effective roll relative to current UI orientation
                var effectiveRoll = horizonState.rollAngle + uiRollOffset
                if (effectiveRoll > 180f) effectiveRoll -= 360f
                if (effectiveRoll < -180f) effectiveRoll += 360f

                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    withTransform({
                        rotate(-effectiveRoll)
                    }) {
                        // The recorded video is 16:9 Landscape.
                        // We use the Canvas width as the base width for the 16:9 box.
                        val baseW = size.width
                        val baseH = size.width * (9f / 16f)
                        
                        val boxW = baseW / horizonState.zoomScale
                        val boxH = baseH / horizonState.zoomScale
                        
                        // Draw bounding box border
                        drawRect(
                            color = Color.Green.copy(alpha = 0.8f),
                            topLeft = androidx.compose.ui.geometry.Offset((size.width - boxW) / 2f, (size.height - boxH) / 2f),
                            size = androidx.compose.ui.geometry.Size(boxW, boxH),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                        )
                    }
                }
            }

            if (enableFocusPeaking && peakingBitmap != null && cameraMode == CameraMode.PHOTO && lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK && !isFlippingCamera && flipRotationAnim.value == 0f) {


                @Suppress("UNUSED_VARIABLE")
                val count = peakingUpdateCount // trigger recomposition
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val isFront = lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
                    val sensorRotation = if (isFront) 270f else 90f
                    val nativeSensorH = size.width * (4f / 3f)
                    
                    withTransform({
                        rotate(sensorRotation, pivot = center)
                        if (isFront) {
                            scale(scaleX = 1f, scaleY = -1f, pivot = center)
                        }
                    }) {

                        val drawW = size.width.toInt()
                        val drawH = nativeSensorH.toInt()
                        drawImage(
                            image = peakingBitmap!!.asImageBitmap(),
                            dstOffset = androidx.compose.ui.unit.IntOffset(
                                x = (center.x - drawH / 2f).toInt(),
                                y = (center.y - drawW / 2f).toInt()
                            ),
                            dstSize = androidx.compose.ui.unit.IntSize(drawH, drawW)
                        )
                    }
                }
            }

            if (uiState.isFaceDetectionEnabled && uiState.detectedFaces.isNotEmpty() && uiState.sensorCropRegion != null) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val isFront = lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
                    val sensorRotation = if (isFront) 270f else 90f
                    val nativeSensorH = size.width * (4f / 3f)
                    
                    withTransform({
                        rotate(sensorRotation, pivot = center)
                        if (isFront) {
                            scale(scaleX = 1f, scaleY = -1f, pivot = center)
                        }
                    }) {
                        val cropRegion = uiState.sensorCropRegion!!
                        val drawW = size.width
                        val drawH = nativeSensorH

                        for (face in uiState.detectedFaces) {
                            val bounds = face.bounds
                            val normLeft = (bounds.left - cropRegion.left).toFloat() / cropRegion.width()
                            val normTop = (bounds.top - cropRegion.top).toFloat() / cropRegion.height()
                            val normRight = (bounds.right - cropRegion.left).toFloat() / cropRegion.width()
                            val normBottom = (bounds.bottom - cropRegion.top).toFloat() / cropRegion.height()
                            
                            val x1 = (center.x - drawH / 2f) + normLeft * drawH
                            val y1 = (center.y - drawW / 2f) + normTop * drawW
                            val x2 = (center.x - drawH / 2f) + normRight * drawH
                            val y2 = (center.y - drawW / 2f) + normBottom * drawW
                            
                            drawRect(
                                color = Color.Yellow,
                                topLeft = androidx.compose.ui.geometry.Offset(x1, y1),
                                size = androidx.compose.ui.geometry.Size(x2 - x1, y2 - y1),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f)
                            )
                        }
                    }
                }
            }


            
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
                            .graphicsLayer { rotationZ = -snapAngle }
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
                                        if (text.isNotEmpty()) {
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
            }


            
            if (showFocusBox && focusOffset != null) {
                val focusColor = when (focusState) {
                    FocusState.TAP_INITIAL -> Color.White
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
                LaunchedEffect(uiState.focusTapCount) {
                    if (showFocusBox) {
                        focusState = FocusState.TAP_INITIAL
                        delay(220) // Show white box first on touch
                        if (focusState == FocusState.TAP_INITIAL) {
                            focusState = FocusState.SEARCHING // Turn yellow while focusing
                        }
                        
                        // Safety timeout (2.5s) for both Photo and Video modes
                        delay(2500)
                        if (showFocusBox && (focusState == FocusState.SEARCHING || focusState == FocusState.TAP_INITIAL)) {
                            if (uiState.isPendingAfLock) {
                                uiState.isAeAfLocked = false
                                uiState.isPendingAfLock = false
                            }
                            focusState = FocusState.FAILED
                            delay(1000)
                            showFocusBox = false
                            focusState = FocusState.TAP_INITIAL
                        }
                    }
                }
                
                LaunchedEffect(focusState) {
                    if (focusState == FocusState.SUCCESS || focusState == FocusState.FAILED) {
                        delay(1200)
                        showFocusBox = false
                        focusState = FocusState.TAP_INITIAL
                        camera2Engine.resetFocusToContinuous()
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

        if (showBrightnessSlider && maxExposureIndex > minExposureIndex && !isProMode && !uiState.selectedCustomScene.lockEv) {

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
            } else if (isAeAfLocked && (focusState == FocusState.SUCCESS || !showFocusBox)) {
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

        TopCameraBar(
            uiState = uiState,
            onOpenStopMotion = {
                context.startActivity(android.content.Intent(context, StopMotionActivity::class.java))
            }
        )


        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
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
                isProcessingHdr = isProcessingHdr,
                hdrProgress = hdrProgress,
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
                    if (!isRecording && !isFlippingCamera) {
                        isFlippingCamera = true
                        peakingBitmap = null // Turn off focus peaking before animation starts
                        coroutineScope.launch {
                            // Rotate 0 to 90 degrees (facing edge-on)
                            flipRotationAnim.animateTo(
                                targetValue = 90f,
                                animationSpec = androidx.compose.animation.core.tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            )
                            // Trigger camera hardware switch while view is turned 90 degrees away
                            lensFacing = if (lensFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT else android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
                        }
                    }
                }


            )
        }
        

        DialogContainers(uiState = uiState)
        
    }
}



