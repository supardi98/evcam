package net.supardi.evcam

import net.supardi.evcam.ui.*
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

enum class CameraMode { PHOTO, VIDEO }
enum class FocusState { SEARCHING, SUCCESS, FAILED }
enum class GridType(val label: String) { NONE("Off"), THIRDS("3x3"), FOURTHS("4x4"), GOLDEN_RATIO("Phi"), CROSSHAIR("Center") }
enum class FlashMode { AUTO, ON, OFF }
enum class TimerMode(val seconds: Int) { OFF(0), SEC_3(3), SEC_10(10), SEC_15(15), SEC_20(20), PEACE(3) }
enum class AspectRatioMode(val value: Int, val label: String) { RATIO_4_3(AspectRatio.RATIO_4_3, "4:3"), RATIO_16_9(AspectRatio.RATIO_16_9, "16:9"), RATIO_1_1(AspectRatio.RATIO_4_3, "1:1") }
enum class VideoQualityMode(val quality: Quality, val label: String) { HD(Quality.HD, "720p"), FHD(Quality.FHD, "1080p"), UHD(Quality.UHD, "4K") }
enum class VideoFpsMode(val fps: Int, val label: String) { FPS_30(30, "30 FPS"), FPS_60(60, "60 FPS") }
enum class ImageFormatMode(val label: String) { JPEG("JPEG"), RAW("RAW+JPEG") }

enum class ColorFilterMode(val label: String, val matrixValues: FloatArray) {
    NORMAL("Normal", floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    MONOCHROME("B&W", floatArrayOf(
        0.33f, 0.59f, 0.11f, 0f, 0f,
        0.33f, 0.59f, 0.11f, 0f, 0f,
        0.33f, 0.59f, 0.11f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    SEPIA("Sepia", floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    VINTAGE("Vintage", floatArrayOf(
        0.9f, 0f, 0f, 0f, 0f,
        0f, 0.8f, 0f, 0f, 0f,
        0f, 0f, 0.7f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    COOL("Cool", floatArrayOf(
        0.8f, 0f, 0f, 0f, 0f,
        0f, 0.9f, 0f, 0f, 0f,
        0f, 0f, 1.2f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    WARM("Warm", floatArrayOf(
        1.2f, 0f, 0f, 0f, 0f,
        0f, 1.0f, 0f, 0f, 0f,
        0f, 0f, 0.8f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))
}

enum class LocationFormat(val label: String) { CITY("City Only"), CITY_COUNTRY("City, Country"), FULL_ADDRESS("Full Address"), COORDINATES("Lat/Lng") }
enum class WatermarkElementType { TEXT, LOCATION, DATE }
enum class WatermarkQuadrant { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
data class WatermarkElement(val id: String, val type: WatermarkElementType, var content: String, var quadrant: WatermarkQuadrant, var size: Int = 14)

fun serializeWatermarkElements(elements: List<WatermarkElement>): String {
    val array = org.json.JSONArray()
    for (e in elements) {
        val obj = org.json.JSONObject()
        obj.put("id", e.id)
        obj.put("type", e.type.name)
        obj.put("content", e.content)
        obj.put("quadrant", e.quadrant.name)
        obj.put("size", e.size)
        array.put(obj)
    }
    return array.toString()
}

fun deserializeWatermarkElements(jsonStr: String?): List<WatermarkElement> {
    val list = mutableListOf<WatermarkElement>()
    if (jsonStr != null && jsonStr.isNotEmpty()) {
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(WatermarkElement(
                    id = obj.getString("id"),
                    type = WatermarkElementType.valueOf(obj.getString("type")),
                    content = obj.getString("content"),
                    quadrant = WatermarkQuadrant.valueOf(obj.getString("quadrant")),
                    size = obj.optInt("size", 14)
                ))
            }
        } catch(e: Exception) {}
    }
    if (list.isEmpty()) {
        list.add(WatermarkElement("1", WatermarkElementType.TEXT, "Shot on EV Cam Pro", WatermarkQuadrant.BOTTOM_LEFT))
    }
    return list
}

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
    var showProPanel by uiState::showProPanel
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
    
    var showSettings by uiState::showSettings
    var showLayerPanel by uiState::showLayerPanel
    var showMediaPreviewDialog by uiState::showMediaPreviewDialog
    
    var keepScreenOn by uiState::keepScreenOn
    var maxBrightness by uiState::maxBrightness
    var evScrollAnchorY by uiState::evScrollAnchorY
    
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
    var isHandTrackingInstalled by uiState::isHandTrackingInstalled
    var isHandTrackingEnabled by uiState::isHandTrackingEnabled
    var isShutterSoundEnabled by uiState::isShutterSoundEnabled
    var showRemoveConfirmation by uiState::showRemoveConfirmation
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
    var showFilterDialog by uiState::showFilterDialog
    var imageFormat by uiState::imageFormat
    
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
    
    LaunchedEffect(keepScreenOn) { prefs.edit().putBoolean("keepScreenOn", keepScreenOn).apply() }
    LaunchedEffect(maxBrightness) { prefs.edit().putBoolean("maxBrightness", maxBrightness).apply() }
    LaunchedEffect(gridType) { prefs.edit().putString("gridType", gridType.name).apply() }
    LaunchedEffect(flashMode) { prefs.edit().putString("flashMode", flashMode.name).apply() }
    LaunchedEffect(timerMode) { prefs.edit().putString("timerMode", timerMode.name).apply() }
    LaunchedEffect(showVirtualHorizon) { prefs.edit().putBoolean("showVirtualHorizon", showVirtualHorizon).apply() }
    LaunchedEffect(volumeShutterEnabled) { prefs.edit().putBoolean("volumeShutterEnabled", volumeShutterEnabled).apply() }
    LaunchedEffect(isShutterSoundEnabled) { prefs.edit().putBoolean("isShutterSoundEnabled", isShutterSoundEnabled).apply() }
    LaunchedEffect(isHandTrackingInstalled) { prefs.edit().putBoolean("isHandTrackingInstalled", isHandTrackingInstalled).apply() }
    LaunchedEffect(isHandTrackingEnabled) { prefs.edit().putBoolean("isHandTrackingEnabled", isHandTrackingEnabled).apply() }
    LaunchedEffect(showWatermark) { prefs.edit().putBoolean("showWatermark", showWatermark).apply() }
    
    LaunchedEffect(enableHistogram) { prefs.edit().putBoolean("enableHistogram", enableHistogram).apply() }
    LaunchedEffect(enableFocusPeaking) { prefs.edit().putBoolean("enableFocusPeaking", enableFocusPeaking).apply() }
    LaunchedEffect(enableRawCapture) { prefs.edit().putBoolean("enableRawCapture", enableRawCapture).apply() }
    LaunchedEffect(manualKelvin) { prefs.edit().putFloat("manualKelvin", manualKelvin).apply() }
    LaunchedEffect(whiteBalance) { prefs.edit().putInt("whiteBalance", whiteBalance).apply() }
    LaunchedEffect(timerBurstCount) { prefs.edit().putInt("timerBurstCount", timerBurstCount).apply() }
    LaunchedEffect(watermarkElements) {
        prefs.edit().putString("watermarkElements", serializeWatermarkElements(watermarkElements)).apply()
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
    LaunchedEffect(enableGeotagging) { prefs.edit().putBoolean("enableGeotagging", enableGeotagging).apply() }
    LaunchedEffect(aspectRatio) { prefs.edit().putString("aspectRatio", aspectRatio.name).apply() }
    LaunchedEffect(videoQuality) { prefs.edit().putString("videoQuality", videoQuality.name).apply() }
    LaunchedEffect(videoFps) { prefs.edit().putString("videoFps", videoFps.name).apply() }
    LaunchedEffect(videoAudioEnabled) { prefs.edit().putBoolean("videoAudioEnabled", videoAudioEnabled).apply() }
    LaunchedEffect(isNightModeEnabled) { prefs.edit().putBoolean("isNightModeEnabled", isNightModeEnabled).apply() }
    LaunchedEffect(selectedFilter) { prefs.edit().putString("selectedFilter", selectedFilter.name).apply() }
    LaunchedEffect(imageFormat) { prefs.edit().putString("imageFormat", imageFormat.name).apply() }




    
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
        camera2Control?.let { control ->
            val builder = androidx.camera.camera2.interop.CaptureRequestOptions.Builder()
            if (isProMode) {
                if (isIsoAuto && isShutterAuto) {
                    builder.clearCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE)
                } else {
                    builder.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE, android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE_OFF)
                    builder.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.SENSOR_SENSITIVITY, iso.toInt())
                    builder.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.SENSOR_EXPOSURE_TIME, shutterSpeed.toLong())
                }
                
                if (isFocusAuto) {
                    builder.clearCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE)
                } else {
                    builder.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE, android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE_OFF)
                    builder.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
                }
                
                builder.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE, whiteBalance)
                if (whiteBalance == android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_OFF) {
                    builder.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_MODE, android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                    val temp = manualKelvin / 100.0f
                    var r: Float
                    var g: Float
                    var b: Float
                    if (temp <= 66.0f) {
                        r = 255.0f
                        g = (99.4708025861f * Math.log(temp.toDouble()).toFloat() - 161.1195681661f).coerceIn(0f, 255f)
                        b = if (temp <= 19.0f) 0.0f else (138.5177312231f * Math.log(temp.toDouble() - 10.0).toFloat() - 305.0447927307f).coerceIn(0f, 255f)
                    } else {
                        r = (329.698727446f * Math.pow(temp.toDouble() - 60.0, -0.1332047592).toFloat()).coerceIn(0f, 255f)
                        g = (288.1221695283f * Math.pow(temp.toDouble() - 60.0, -0.0755148492).toFloat()).coerceIn(0f, 255f)
                        b = 255.0f
                    }
                    val rGain = (255f / r).coerceIn(1f, 3.5f)
                    val gGain = (255f / g).coerceIn(1f, 3.5f)
                    val bGain = (255f / b).coerceIn(1f, 3.5f)
                    builder.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_GAINS, android.hardware.camera2.params.RggbChannelVector(rGain, gGain, gGain, bGain))
                } else {
                    builder.clearCaptureRequestOption(android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_MODE)
                    builder.clearCaptureRequestOption(android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_GAINS)
                }
            } else {
                builder.clearCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE)
                builder.clearCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE)
                builder.clearCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE)
                builder.clearCaptureRequestOption(android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_MODE)
                builder.clearCaptureRequestOption(android.hardware.camera2.CaptureRequest.COLOR_CORRECTION_GAINS)
            }
            control.captureRequestOptions = builder.build()
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
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        if (selectedFilter != ColorFilterMode.NORMAL) {
                            val paint = androidx.compose.ui.graphics.Paint().apply {
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                    androidx.compose.ui.graphics.ColorMatrix(selectedFilter.matrixValues)
                                )
                            }
                            drawIntoCanvas { canvas ->
                                canvas.saveLayer(size.toRect(), paint)
                                drawContent()
                                canvas.restore()
                            }
                        } else {
                            drawContent()
                        }
                    },

                factory = { ctx -> 

                    previewView.apply {
                        val scaleGestureDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScale(detector: ScaleGestureDetector): Boolean {
                                showZoomSlider = true
                                // Pinch: snapTo = instant, cancels any running preset animation
                                val newZoom = (zoomAnim.value * detector.scaleFactor).coerceIn(minZoomRatio, maxZoomRatio)
                                coroutineScope.launch { zoomAnim.snapTo(newZoom) }
                                return true
                            }
                        })
                        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onSingleTapUp(e: MotionEvent): Boolean {
                                if (isAeAfLocked) {
                                    isAeAfLocked = false
                                    cameraControl?.cancelFocusAndMetering()
                                }
                                
                                if (isProMode && !isFocusAuto) {
                                    return true
                                }
                                
                                isFocusAuto = true
                                focusOffset = Offset(e.x, e.y)
                                showFocusBox = true
                                showZoomSlider = true
                                showBrightnessSlider = true
                                focusState = FocusState.SEARCHING
                                val factory = previewView.meteringPointFactory
                                val point = factory.createPoint(e.x, e.y)
                                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                    .setAutoCancelDuration(2, TimeUnit.SECONDS)
                                    .build()
                                val future = cameraControl?.startFocusAndMetering(action)
                                future?.addListener({
                                    try {
                                        val result = future.get()
                                        focusState = if (result != null && result.isFocusSuccessful) FocusState.SUCCESS else FocusState.FAILED
                                    } catch (exc: Exception) {
                                        focusState = FocusState.FAILED
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                return true
                            }

                            override fun onLongPress(e: MotionEvent) {
                                val factory = previewView.meteringPointFactory
                                val point = factory.createPoint(e.x, e.y)
                                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                    .disableAutoCancel()
                                    .build()
                                cameraControl?.startFocusAndMetering(action)
                                isAeAfLocked = true
                                focusOffset = Offset(e.x, e.y)
                                showFocusBox = true
                                focusState = FocusState.SUCCESS
                            }

                            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                                if (e1 == null) return false
                                val diffX = e2.x - e1.x
                                val diffY = e2.y - e1.y
                                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) && kotlin.math.abs(diffX) > 100 && kotlin.math.abs(velocityX) > 100) {
                                    if (diffX < 0 && cameraMode == CameraMode.PHOTO) {
                                        if (!isRecording) cameraMode = CameraMode.VIDEO
                                        return true
                                    } else if (diffX > 0 && cameraMode == CameraMode.VIDEO) {
                                        if (!isRecording) cameraMode = CameraMode.PHOTO
                                        return true
                                    }
                                }
                                return false
                            }
                        })
                        setOnTouchListener { _, event ->
                            scaleGestureDetector.onTouchEvent(event)
                            if (!scaleGestureDetector.isInProgress) {
                                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                                    evScrollAnchorY = event.y
                                } else if (event.actionMasked == MotionEvent.ACTION_MOVE && !isProMode) {
                                    val deltaY = evScrollAnchorY - event.y // positive = swipe up
                                    val scrollStepThreshold = 50f // pixels per EV step
                                    val steps = (deltaY / scrollStepThreshold).toInt()
                                    if (steps != 0) {
                                        val newIdx = (exposureIndex + steps).coerceIn(minExposureIndex, maxExposureIndex)
                                        if (newIdx != exposureIndex) {
                                            exposureIndex = newIdx
                                            cameraControl?.setExposureCompensationIndex(newIdx)
                                            showBrightnessSlider = true
                                            if (newIdx != 0) {
                                                isProMode = true
                                            }
                                        }
                                        // Reset anchor to current y to support continuous scrolling
                                        evScrollAnchorY = event.y
                                    }
                                }
                                gestureDetector.onTouchEvent(event)
                            }
                            true
                        }


                    }
                }
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = isTransitioningRatio,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(100)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (cameraMode == CameraMode.VIDEO) "16:9 VIDEO" else aspectRatio.label,
                        color = Color.Yellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
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
                val orientData = net.supardi.evcam.ui.DeviceOrientationData(deviceOrientation.roll, deviceOrientation.pitch, deviceOrientation.isFlat)
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

        TopCameraBar(
            cameraMode = cameraMode,
            flashMode = flashMode,
            isTorchOn = isTorchOn,
            onFlashModeChange = { flashMode = it },
            onTorchToggle = { isTorchOn = !isTorchOn },
            videoAudioEnabled = videoAudioEnabled,
            onVideoAudioToggle = { videoAudioEnabled = !videoAudioEnabled },
            isNightModeEnabled = isNightModeEnabled,
            onNightModeToggle = { isNightModeEnabled = !isNightModeEnabled },
            selectedFilter = selectedFilter,
            onFilterClick = { showFilterDialog = true },
            timerMode = timerMode,
            onTimerModeChange = { timerMode = it },
            isHandTrackingInstalled = isHandTrackingInstalled,
            isHandTrackingEnabled = isHandTrackingEnabled,
            timerBurstCount = timerBurstCount,
            onTimerBurstCountChange = { timerBurstCount = it },
            aspectRatio = aspectRatio,
            onAspectRatioChange = { aspectRatio = it },
            videoQuality = videoQuality,
            onVideoQualityChange = { videoQuality = it },
            videoFps = videoFps,
            onVideoFpsChange = { videoFps = it },
            showSettings = showSettings,
            onSettingsClick = {
                showSettings = !showSettings
                if (showSettings) {
                    showProPanel = false
                    showLayerPanel = false
                }
            }
        )

        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .pointerInput(Unit) { /* consume touches so panel clicks don't fall through to camera preview */ },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showProPanel,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = androidx.compose.animation.core.spring(
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy
                    )
                ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250)),
                exit = androidx.compose.animation.shrinkVertically(animationSpec = androidx.compose.animation.core.tween(200)) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))
            ) {
                Column {
                    ProControlPanel(
                        iso = iso,
                        minIso = minIso,
                        maxIso = maxIso,
                        isIsoAuto = isIsoAuto,
                        onIsoChange = { isIsoAuto = false; iso = it; isProMode = true },
                        onIsoAutoToggle = { isIsoAuto = !isIsoAuto },
                        shutterSpeed = shutterSpeed,
                        isShutterAuto = isShutterAuto,
                        onShutterChange = { isShutterAuto = false; shutterSpeed = it },
                        onShutterAutoToggle = { isShutterAuto = !isShutterAuto },
                        focusDistance = focusDistance,
                        isFocusAuto = isFocusAuto,
                        onFocusChange = { isFocusAuto = false; focusDistance = it },
                        onFocusAutoToggle = { isFocusAuto = !isFocusAuto },
                        whiteBalance = whiteBalance,
                        onWhiteBalanceChange = { whiteBalance = it },
                        manualKelvin = manualKelvin,
                        onManualKelvinChange = { manualKelvin = it },
                        onClose = { showProPanel = false }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            androidx.compose.animation.AnimatedVisibility(
                visible = showLayerPanel,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = androidx.compose.animation.core.spring(
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy
                    )
                ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250)),
                exit = androidx.compose.animation.shrinkVertically(animationSpec = androidx.compose.animation.core.tween(200)) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))
            ) {
                Column {
                    DisplayOverlaysPanel(
                        gridType = gridType,
                        onGridTypeChange = {
                            gridType = it
                            prefs.edit().putString("gridType", it.name).apply()
                        },
                        showVirtualHorizon = showVirtualHorizon,
                        onVirtualHorizonChange = {
                            showVirtualHorizon = it
                            prefs.edit().putBoolean("showVirtualHorizon", it).apply()
                        },
                        enableHistogram = enableHistogram,
                        onHistogramChange = {
                            enableHistogram = it
                            prefs.edit().putBoolean("enableHistogram", it).apply()
                        },
                        enableFocusPeaking = enableFocusPeaking,
                        onFocusPeakingChange = {
                            enableFocusPeaking = it
                            prefs.edit().putBoolean("enableFocusPeaking", it).apply()
                        },
                        onClose = { showLayerPanel = false }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showSettings,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = androidx.compose.animation.core.spring(
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy
                    )
                ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250)),
                exit = androidx.compose.animation.shrinkVertically(animationSpec = androidx.compose.animation.core.tween(200)) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))
            ) {
                Column {
                    SettingsPanel(
                        enableRawCapture = enableRawCapture,
                        onEnableRawCaptureChange = { enableRawCapture = it },
                        keepScreenOn = keepScreenOn,
                        onKeepScreenOnChange = { keepScreenOn = it },
                        maxBrightness = maxBrightness,
                        onMaxBrightnessChange = { maxBrightness = it },
                        volumeShutterEnabled = volumeShutterEnabled,
                        onVolumeShutterEnabledChange = { volumeShutterEnabled = it },
                        isShutterSoundEnabled = isShutterSoundEnabled,
                        onIsShutterSoundEnabledChange = { isShutterSoundEnabled = it },
                        enableGeotagging = enableGeotagging,
                        onEnableGeotaggingChange = { enableGeotagging = it },
                        onOpenWatermarkSettings = { showWatermarkDialog = true },
                        onOpenPluginManager = { showPluginManager = true },
                        onClose = { showSettings = false }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

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

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val isAnyOverlayActive = gridType != GridType.NONE || showVirtualHorizon || enableHistogram || enableFocusPeaking
                
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isAnyOverlayActive) Color.Yellow.copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.5f))
                        .clickable { 
                            showLayerPanel = !showLayerPanel 
                            if (showLayerPanel) {
                                showProPanel = false
                                showSettings = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Layer Settings",
                        tint = if (isAnyOverlayActive) Color.Yellow else Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))
                val photoSelected = cameraMode == CameraMode.PHOTO
                val targetBias = if (photoSelected) -1f else 1f
                val animatedBias by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = targetBias,
                    animationSpec = androidx.compose.animation.core.spring(
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy
                    ),
                    label = "TogglePillSpring"
                )
                val photoTextColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (photoSelected) Color.Black else Color.White,
                    label = "PhotoTextAnim"
                )
                val videoTextColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (!photoSelected) Color.Black else Color.White,
                    label = "VideoTextAnim"
                )

                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .width(160.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray.copy(alpha = 0.5f))
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.5f)
                            .align(BiasAlignment(horizontalBias = animatedBias, verticalBias = 0f))
                            .clip(CircleShape)
                            .background(Color.Yellow)
                    )

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { if (!isRecording) cameraMode = CameraMode.PHOTO },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "PHOTO",
                                color = photoTextColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { if (!isRecording) cameraMode = CameraMode.VIDEO },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "VIDEO",
                                color = videoTextColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                val hasManualPro = !isIsoAuto || !isShutterAuto || !isFocusAuto || whiteBalance != CaptureRequest.CONTROL_AWB_MODE_AUTO || exposureIndex != 0
                
                LaunchedEffect(hasManualPro) {
                    isProMode = hasManualPro
                }

                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(if (hasManualPro) Color.Yellow.copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.5f))
                        .clickable { 
                            showProPanel = !showProPanel 
                            if (showProPanel) {
                                showLayerPanel = false
                                showSettings = false
                            }
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PRO",
                        color = if (hasManualPro) Color.Yellow else Color.LightGray,
                        fontWeight = if (hasManualPro) FontWeight.Bold else FontWeight.Normal
                    )
                }

                if (hasManualPro) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray.copy(alpha = 0.5f))
                            .clickable { 
                                // Reset all pro settings to AUTO
                                isIsoAuto = true
                                isShutterAuto = true
                                isFocusAuto = true
                                whiteBalance = CaptureRequest.CONTROL_AWB_MODE_AUTO
                                exposureIndex = 0
                                cameraControl?.setExposureCompensationIndex(0)
                                isProMode = false
                                showProPanel = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close, 
                            contentDescription = "Auto all Pro settings", 
                            tint = Color.Yellow, 
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
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
        

        if (showMediaPreviewDialog && (lastCapturedBitmap != null || lastCapturedUri != null)) {
            MediaPreviewDialog(
                lastCapturedBitmap = lastCapturedBitmap,
                lastCapturedUri = lastCapturedUri,
                cameraMode = cameraMode,
                context = context,
                onDismiss = { showMediaPreviewDialog = false }
            )
        }
        if (showPluginManager) {
            var downloadProgress by remember { mutableStateOf(0f) }
            var isDownloading by remember { mutableStateOf(false) }
            
            LaunchedEffect(isDownloading) {
                if (isDownloading) {
                    while (downloadProgress < 1f) {
                        kotlinx.coroutines.delay(50)
                        downloadProgress += 0.02f
                    }
                    isHandTrackingInstalled = true
                    isDownloading = false
                }
            }
            
            if (showRemoveConfirmation) {
                AlertDialog(
                    onDismissRequest = { showRemoveConfirmation = false },
                    title = { Text("Uninstall Plugin") },
                    text = { Text("Are you sure you want to remove AI Hand Tracking?") },
                    confirmButton = {
                        TextButton(onClick = {
                            isHandTrackingInstalled = false
                            showRemoveConfirmation = false
                        }) { Text("Remove") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRemoveConfirmation = false }) { Text("Cancel") }
                    }
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable { if (!isDownloading) showPluginManager = false },
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
                        if (isHandTrackingInstalled) {
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("On", color = Color.White, fontSize = 12.sp)
                                    Switch(checked = isHandTrackingEnabled, onCheckedChange = { isHandTrackingEnabled = it }, modifier = Modifier.scale(0.6f))
                                }
                                Text("Remove", color = Color.Red, fontSize = 12.sp, modifier = Modifier.clickable { 
                                    showRemoveConfirmation = true
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
                    Button(onClick = { showPluginManager = false }, enabled = !isDownloading, modifier = Modifier.align(Alignment.End)) {
                        Text("Close")
                    }
                }
            }
        }
    }
    
    if (showWatermarkDialog) {
        WatermarkSettingsDialog(
            showWatermark = showWatermark,
            onShowWatermarkChange = { showWatermark = it },
            watermarkElements = watermarkElements,
            onWatermarkElementsChange = { watermarkElements = it },
            liveLocation = liveLocation,
            liveAddress = liveAddress,
            onDismiss = { showWatermarkDialog = false }
        )
    }
    
    if (showFilterDialog) {
        ColorFilterDialog(
            selectedFilter = selectedFilter,
            onFilterSelect = { selectedFilter = it },
            onDismissRequest = { showFilterDialog = false }
        )
    }
}




fun fetchLatestMediaUri(context: android.content.Context): Uri? {
    try {
        val projection = arrayOf(android.provider.MediaStore.Images.Media._ID)
        val sortOrder = "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"
        val queryUri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(queryUri, projection, null, null, sortOrder)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID))
                return android.content.ContentUris.withAppendedId(queryUri, id)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}






