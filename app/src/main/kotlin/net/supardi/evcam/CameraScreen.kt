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
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
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
    
    var cameraControl by uiState::cameraControl
    var camera2Control by uiState::camera2Control
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
                imageCaptureUseCase?.targetRotation = rotation
                videoCaptureUseCase?.targetRotation = rotation
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
    
    LaunchedEffect(isTorchOn, cameraControl) {
        cameraControl?.enableTorch(isTorchOn)
    }
    
    LaunchedEffect(currentZoom) {
        if (kotlin.math.abs(currentZoom - 1f) < 0.01f) {
            delay(3000)
            showZoomSlider = false
        }
    }
    
    LaunchedEffect(Unit) {
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
    
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }

    LaunchedEffect(previewView) {
        androidx.compose.runtime.snapshotFlow { previewView.previewStreamState.value }
            .collect { state ->
                android.util.Log.d("EvcamTiming", "[StreamState] PreviewView streamState changed to -> $state at ${System.currentTimeMillis()}ms")
            }
    }
    
    LaunchedEffect(lensFacing, cameraMode, aspectRatio, videoQuality, videoFps, isNightModeEnabled) {
        val t0 = System.currentTimeMillis()
        android.util.Log.d("EvcamTiming", "[t0 = 0ms] State change trigger -> mode=$cameraMode, ratio=$aspectRatio, fps=${videoFps.fps}")
        isTransitioningRatio = true
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        
        cameraProviderFuture.addListener({
            val t1 = System.currentTimeMillis() - t0
            android.util.Log.d("EvcamTiming", "[t1 = ${t1}ms] ProcessCameraProvider listener callback fired")
            val cameraProvider = cameraProviderFuture.get()
            @Suppress("DEPRECATION")
            val preview = Preview.Builder().apply {
                if (cameraMode == CameraMode.VIDEO) {
                    setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    // Set target FPS range to hardware preview via Camera2Interop
                    val interop = androidx.camera.camera2.interop.Camera2Interop.Extender(this)
                    interop.setCaptureRequestOption(
                        android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        android.util.Range(videoFps.fps, videoFps.fps)
                    )
                    // Enable Video Stabilization (EIS/OIS) for steady video recording
                    interop.setCaptureRequestOption(
                        android.hardware.camera2.CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        android.hardware.camera2.CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                    )
                } else {
                    setTargetAspectRatio(aspectRatio.value)
                }
            }.build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            @Suppress("DEPRECATION")
            val imageCapBuilder = ImageCapture.Builder().apply {
                setTargetAspectRatio(aspectRatio.value)
            }
            // Force Optical Image Stabilization (OIS) for photos if supported by device
            val imageInterop = androidx.camera.camera2.interop.Camera2Interop.Extender(imageCapBuilder)
            imageInterop.setCaptureRequestOption(
                android.hardware.camera2.CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                android.hardware.camera2.CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
            )
            val imageCap = imageCapBuilder.build()

            
            val qualitySelector = QualitySelector.from(
                videoQuality.quality,
                FallbackStrategy.lowerQualityOrHigherThan(videoQuality.quality)
            )
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            
            val videoCap = androidx.camera.video.VideoCapture.withOutput(recorder)

            
            imageCaptureUseCase = imageCap
            videoCaptureUseCase = videoCap
            
            val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()

                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(androidx.core.content.ContextCompat.getMainExecutor(context), proAnalyzer)

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            
            var finalCameraSelector = cameraSelector
            try {
                val extensionsManager = androidx.camera.extensions.ExtensionsManager.getInstanceAsync(context, cameraProvider).get()
                if (isNightModeEnabled && cameraMode == CameraMode.PHOTO && 
                    extensionsManager.isExtensionAvailable(cameraSelector, androidx.camera.extensions.ExtensionMode.NIGHT)) {
                    finalCameraSelector = extensionsManager.getExtensionEnabledCameraSelector(cameraSelector, androidx.camera.extensions.ExtensionMode.NIGHT)
                }
            } catch (e: Exception) {
                android.util.Log.e("Evcam", "Failed to initialize ExtensionsManager", e)
            }
            
            var boundCamera: androidx.camera.core.Camera? = null
            try {
                val tUnbindStart = System.currentTimeMillis() - t0
                cameraProvider.unbindAll()
                val tUnbindEnd = System.currentTimeMillis() - t0
                android.util.Log.d("EvcamTiming", "[t2 = ${tUnbindEnd}ms] cameraProvider.unbindAll() took ${tUnbindEnd - tUnbindStart}ms")

                boundCamera = if (cameraMode == CameraMode.VIDEO) {
                    cameraProvider.bindToLifecycle(lifecycleOwner, finalCameraSelector, preview, videoCap)
                } else {
                    cameraProvider.bindToLifecycle(lifecycleOwner, finalCameraSelector, preview, imageCap, imageAnalysis)
                }

                val tBindEnd = System.currentTimeMillis() - t0
                android.util.Log.d("EvcamTiming", "[t3 = ${tBindEnd}ms] cameraProvider.bindToLifecycle() took ${tBindEnd - tUnbindEnd}ms")
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("Evcam", "Use case binding failed, falling back to preview", e)
                try {
                    cameraProvider.unbindAll()
                    boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
            
            boundCamera?.let { camera ->
                cameraControl = camera.cameraControl
                camera2Control = Camera2CameraControl.from(camera.cameraControl)
                
                val zoomState = camera.cameraInfo.zoomState.value
                if (zoomState != null) {
                    minZoomRatio = zoomState.minZoomRatio
                    maxZoomRatio = zoomState.maxZoomRatio
                    if (currentZoom < minZoomRatio || currentZoom > maxZoomRatio) {
                        coroutineScope.launch { zoomAnim.snapTo(1f.coerceIn(minZoomRatio, maxZoomRatio)) }
                    }
                    cameraControl?.setZoomRatio(zoomAnim.value)
                }
                
                val camera2Info = androidx.camera.camera2.interop.Camera2CameraInfo.from(camera.cameraInfo)
                val isoRange = camera2Info.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                if (isoRange != null) {
                    minIso = isoRange.lower.toFloat()
                    maxIso = isoRange.upper.toFloat()
                    if (iso < minIso || iso > maxIso) {
                        iso = minIso
                    }
                }

                val expState = camera.cameraInfo.exposureState
                if (expState.isExposureCompensationSupported) {
                    minExposureIndex = expState.exposureCompensationRange.lower
                    maxExposureIndex = expState.exposureCompensationRange.upper
                    exposureStep = expState.exposureCompensationStep.toFloat()
                }
            }
        }, executor)

        val startMs = System.currentTimeMillis()
        while (System.currentTimeMillis() - startMs < 550) {
            if (previewView.previewStreamState.value == androidx.camera.view.PreviewView.StreamState.STREAMING) {
                kotlinx.coroutines.delay(60)
                break
            }
            kotlinx.coroutines.delay(20)
        }
        val tFinal = System.currentTimeMillis() - t0
        android.util.Log.d("EvcamTiming", "[t_final = ${tFinal}ms] Hardware STREAMING confirmed, releasing transition mask")
        isTransitioningRatio = false
    }

    // Single Animatable for zoom:
    // - gesture (pinch/drag/slider): zoomAnim.snapTo() — instant, cancels any running animation
    // - preset button (1x/2x/5x):   zoomAnim.animateTo() — smooth 280ms easing

    // Apply zoom to camera whenever zoomAnim.value changes (from animation OR snap)
    LaunchedEffect(zoomAnim) {
        androidx.compose.runtime.snapshotFlow { zoomAnim.value }
            .collect { value ->
                currentZoom = value
                cameraControl?.setZoomRatio(value)
            }
    }
    // Also re-apply when cameraControl changes (new camera bound)
    LaunchedEffect(cameraControl) {
        cameraControl?.setZoomRatio(zoomAnim.value)
    }

    
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
            imageCaptureUseCase?.let { cap ->
                takePhoto(cap, context, ContextCompat.getMainExecutor(context), flashMode, selectedFilter, showWatermark, watermarkElements, liveLocation, liveAddress, enableGeotagging, enableRawCapture, aspectRatio) { bitmap, uri ->
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
            } else {
                videoCaptureUseCase?.let { cap ->
                    activeRecording = startVideoRecord(cap, context, videoAudioEnabled) { event ->

                        if (event is VideoRecordEvent.Start) isRecording = true
                        else if (event is VideoRecordEvent.Finalize) {
                            isRecording = false
                            val uri = event.outputResults.outputUri
                            lastCapturedUri = uri
                            prefs.edit().putString("lastCapturedUri", uri.toString()).apply()
                        }
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

    LaunchedEffect(isProMode, iso, shutterSpeed, focusDistance, whiteBalance, manualKelvin, isIsoAuto, isShutterAuto, isFocusAuto, camera2Control) {
        applyProCamera2Settings(
            camera2Control = camera2Control,
            isProMode = isProMode,
            isIsoAuto = isIsoAuto,
            isShutterAuto = isShutterAuto,
            isFocusAuto = isFocusAuto,
            iso = iso,
            shutterSpeed = shutterSpeed,
            focusDistance = focusDistance,
            whiteBalance = whiteBalance,
            manualKelvin = manualKelvin
        )
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
                previewView = previewView,
                uiState = uiState,
                coroutineScope = coroutineScope,
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
                    FocusState.SEARCHING -> Color.White
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
                    if (focusState != FocusState.SEARCHING) {
                        kotlinx.coroutines.delay(1000)
                        showFocusBox = false
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
                            cameraControl?.setExposureCompensationIndex(newIdx)
                            if (newIdx != 0) {
                                isProMode = true
                            }
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
                            cameraControl?.setExposureCompensationIndex(0)
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
                            cameraControl?.cancelFocusAndMetering()
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
                isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT,
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
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    }
                }
            )
        }
        

        DialogContainers(uiState = uiState)
    }
}








