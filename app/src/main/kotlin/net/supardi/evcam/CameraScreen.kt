package net.supardi.evcam

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
    
    var cameraMode by remember { mutableStateOf(CameraMode.PHOTO) }
    var isProMode by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isRecording by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var lastCapturedUri by remember {
        val saved = prefs.getString("lastCapturedUri", null)
        val parsedUri = if (!saved.isNullOrEmpty()) Uri.parse(saved) else null
        mutableStateOf<Uri?>(parsedUri ?: fetchLatestMediaUri(context))
    }
    var lastCapturedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var camera2Control by remember { mutableStateOf<Camera2CameraControl?>(null) }
    var iso by remember { mutableFloatStateOf(100f) }
    var minIso by remember { mutableFloatStateOf(50f) }
    var maxIso by remember { mutableFloatStateOf(3200f) }
    var shutterSpeed by remember { mutableFloatStateOf(10000000f) } // 10ms
    var focusDistance by remember { mutableFloatStateOf(0f) }
    var whiteBalance by remember { mutableIntStateOf(prefs.getInt("whiteBalance", android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO)) }
    
    var isIsoAuto by remember { mutableStateOf(true) }
    var isShutterAuto by remember { mutableStateOf(true) }
    var isFocusAuto by remember { mutableStateOf(true) }
    var showProPanel by remember { mutableStateOf(false) }
    var enableHistogram by remember { mutableStateOf(prefs.getBoolean("enableHistogram", false)) }
    var enableFocusPeaking by remember { mutableStateOf(prefs.getBoolean("enableFocusPeaking", false)) }
    var enableRawCapture by remember { mutableStateOf(prefs.getBoolean("enableRawCapture", false)) }
    var manualKelvin by remember { mutableFloatStateOf(prefs.getFloat("manualKelvin", 5000f)) }
    var timerBurstCount by remember { mutableIntStateOf(prefs.getInt("timerBurstCount", 1)) }
    var histogramData by remember { mutableStateOf<IntArray?>(null) }
    var histogramUpdateCount by remember { mutableLongStateOf(0L) }
    var peakingBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var peakingUpdateCount by remember { mutableLongStateOf(0L) }
    
    var isBursting by remember { mutableStateOf(false) }
    var burstCount by remember { mutableIntStateOf(0) }
    
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
    
    var showSettings by remember { mutableStateOf(false) }
    var showLayerPanel by remember { mutableStateOf(false) }
    var showMediaPreviewDialog by remember { mutableStateOf(false) }
    
    var keepScreenOn by remember { mutableStateOf(prefs.getBoolean("keepScreenOn", false)) }
    var maxBrightness by remember { mutableStateOf(prefs.getBoolean("maxBrightness", false)) }
    
    var minZoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(1f) }
    var currentZoom by remember { mutableFloatStateOf(1f) }
    
    var focusOffset by remember { mutableStateOf<Offset?>(null) }
    var showFocusBox by remember { mutableStateOf(false) }
    var focusState by remember { mutableStateOf(FocusState.SEARCHING) }
    
    var gridType by remember { mutableStateOf(GridType.valueOf(prefs.getString("gridType", GridType.NONE.name) ?: GridType.NONE.name)) }
    var flashMode by remember { mutableStateOf(FlashMode.valueOf(prefs.getString("flashMode", FlashMode.AUTO.name) ?: FlashMode.AUTO.name)) }
    var timerMode by remember { mutableStateOf(TimerMode.valueOf(prefs.getString("timerMode", TimerMode.OFF.name) ?: TimerMode.OFF.name)) }
    var showVirtualHorizon by remember { mutableStateOf(prefs.getBoolean("showVirtualHorizon", true)) }
    var showZoomSlider by remember { mutableStateOf(false) }
    var showBrightnessSlider by remember { mutableStateOf(false) }
    var minExposureIndex by remember { mutableIntStateOf(-6) }
    var maxExposureIndex by remember { mutableIntStateOf(6) }
    var exposureStep by remember { mutableFloatStateOf(0.3333f) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var isTorchOn by remember { mutableStateOf(false) }
    var volumeShutterEnabled by remember { mutableStateOf(prefs.getBoolean("volumeShutterEnabled", true)) }
    
    var showPluginManager by remember { mutableStateOf(false) }
    var isHandTrackingInstalled by remember { mutableStateOf(prefs.getBoolean("isHandTrackingInstalled", false)) }
    var isHandTrackingEnabled by remember { mutableStateOf(prefs.getBoolean("isHandTrackingEnabled", true)) }
    var isShutterSoundEnabled by remember { mutableStateOf(prefs.getBoolean("isShutterSoundEnabled", true)) }
    var showRemoveConfirmation by remember { mutableStateOf(false) }
    var showWatermark by remember { mutableStateOf(prefs.getBoolean("showWatermark", false)) }
    var watermarkElements by remember { mutableStateOf(deserializeWatermarkElements(prefs.getString("watermarkElements", null))) }
    var enableGeotagging by remember { mutableStateOf(prefs.getBoolean("enableGeotagging", false)) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var liveLocation by remember { mutableStateOf<android.location.Location?>(null) }
    var liveAddress by remember { mutableStateOf<android.location.Address?>(null) }
    
    var aspectRatio by remember { mutableStateOf(AspectRatioMode.valueOf(prefs.getString("aspectRatio", AspectRatioMode.RATIO_4_3.name) ?: AspectRatioMode.RATIO_4_3.name)) }
    var videoQuality by remember { mutableStateOf(VideoQualityMode.valueOf(prefs.getString("videoQuality", VideoQualityMode.HD.name) ?: VideoQualityMode.HD.name)) }
    var videoFps by remember { mutableStateOf(VideoFpsMode.valueOf(prefs.getString("videoFps", VideoFpsMode.FPS_30.name) ?: VideoFpsMode.FPS_30.name)) }
    var imageFormat by remember { mutableStateOf(ImageFormatMode.valueOf(prefs.getString("imageFormat", ImageFormatMode.JPEG.name) ?: ImageFormatMode.JPEG.name)) }
    
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCaptureUseCase by remember { mutableStateOf<androidx.camera.video.VideoCapture<Recorder>?>(null) }
    
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
    
    LaunchedEffect(lensFacing, cameraMode, aspectRatio, videoQuality) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().apply {
                if (cameraMode == CameraMode.PHOTO) setTargetAspectRatio(aspectRatio.value)
            }.build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            val imageCap = ImageCapture.Builder().apply {
                setTargetAspectRatio(aspectRatio.value)
            }.build()
            
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(videoQuality.quality))
                .build()
            val videoCap = androidx.camera.video.VideoCapture.withOutput(recorder)
            
            imageCaptureUseCase = imageCap
            videoCaptureUseCase = videoCap
            
            val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(androidx.core.content.ContextCompat.getMainExecutor(context), proAnalyzer)

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            
            try {
                cameraProvider.unbindAll()
                val camera = if (cameraMode == CameraMode.VIDEO) {
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, videoCap)
                } else {
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCap, imageAnalysis)
                }
                cameraControl = camera.cameraControl
                camera2Control = Camera2CameraControl.from(camera.cameraControl)
                
                val zoomState = camera.cameraInfo.zoomState.value
                if (zoomState != null) {
                    minZoomRatio = zoomState.minZoomRatio
                    maxZoomRatio = zoomState.maxZoomRatio
                    if (currentZoom < minZoomRatio || currentZoom > maxZoomRatio) {
                        currentZoom = 1f.coerceIn(minZoomRatio, maxZoomRatio)
                    }
                    cameraControl?.setZoomRatio(currentZoom)
                }
                
                val camera2Info = androidx.camera.camera2.interop.Camera2CameraInfo.from(camera.cameraInfo)
                val isoRange = camera2Info.getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                if (isoRange != null) {
                    minIso = isoRange.lower.toFloat()
                    maxIso = isoRange.upper.toFloat()
                }

                val expState = camera.cameraInfo.exposureState
                if (expState.isExposureCompensationSupported) {
                    minExposureIndex = expState.exposureCompensationRange.lower
                    maxExposureIndex = expState.exposureCompensationRange.upper
                    exposureStep = expState.exposureCompensationStep.toFloat()
                }
            } catch (e: Exception) {
                Log.e("Evcam", "Use case binding failed", e)
            }
        }, executor)
    }

    LaunchedEffect(currentZoom, cameraControl) {
        cameraControl?.setZoomRatio(currentZoom)
    }
    
    val executeCapture = {
        if (cameraMode == CameraMode.PHOTO) {
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
                takePhoto(cap, context, ContextCompat.getMainExecutor(context), showWatermark, watermarkElements, liveLocation, liveAddress, enableGeotagging, enableRawCapture, aspectRatio) { bitmap, uri ->
                    lastCapturedBitmap = bitmap
                    lastCapturedUri = uri 
                    prefs.edit().putString("lastCapturedUri", uri.toString()).apply()
                    if (!isShutterSoundEnabled) {
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_SYSTEM, originalVolume, 0)
                    }
                }
            }
        } else {
            if (isRecording) {
                activeRecording?.stop()
            } else {
                videoCaptureUseCase?.let { cap ->
                    activeRecording = startVideoRecord(cap, context) { event ->
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
            .aspectRatio(
                if (cameraMode == CameraMode.VIDEO) {
                    9f / 16f
                } else {
                    when (aspectRatio) {
                        AspectRatioMode.RATIO_16_9 -> 9f / 16f
                        AspectRatioMode.RATIO_4_3 -> 3f / 4f
                        AspectRatioMode.RATIO_1_1 -> 1f
                    }
                }
            )
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> 
                    previewView.apply {
                        val scaleGestureDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScale(detector: ScaleGestureDetector): Boolean {
                                showZoomSlider = true
                                currentZoom = (currentZoom * detector.scaleFactor).coerceIn(minZoomRatio, maxZoomRatio)
                                return true
                            }
                        })
                        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onSingleTapUp(e: MotionEvent): Boolean {
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
                        })
                        setOnTouchListener { _, event ->
                            scaleGestureDetector.onTouchEvent(event)
                            if (!scaleGestureDetector.isInProgress) {
                                gestureDetector.onTouchEvent(event)
                            }
                            true
                        }
                    }
                }
            )
            
            if (showWatermark && cameraMode == CameraMode.PHOTO) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val normalizedRotation = ((deviceRotation ?: 0f) % 360f + 360f) % 360f
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
                val count = peakingUpdateCount // trigger recomposition
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawImage(
                        image = peakingBitmap!!.asImageBitmap(),
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                    )
                }
            }
            
            if (enableHistogram && histogramData != null && cameraMode == CameraMode.PHOTO) {
                val count = histogramUpdateCount // trigger recomposition
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .padding(16.dp)
                        .size(100.dp, 60.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .align(Alignment.TopStart)
                ) {
                    val data = histogramData!!
                    val maxCount = data.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
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
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val paintColor = Color.White.copy(alpha = 0.5f)
                    val strokeWidth = 1.dp.toPx()
                    
                    when (gridType) {
                        GridType.THIRDS -> {
                            drawLine(paintColor, androidx.compose.ui.geometry.Offset(w / 3, 0f), androidx.compose.ui.geometry.Offset(w / 3, h), strokeWidth)
                            drawLine(paintColor, androidx.compose.ui.geometry.Offset(w * 2 / 3, 0f), androidx.compose.ui.geometry.Offset(w * 2 / 3, h), strokeWidth)
                            drawLine(paintColor, androidx.compose.ui.geometry.Offset(0f, h / 3), androidx.compose.ui.geometry.Offset(w, h / 3), strokeWidth)
                            drawLine(paintColor, androidx.compose.ui.geometry.Offset(0f, h * 2 / 3), androidx.compose.ui.geometry.Offset(w, h * 2 / 3), strokeWidth)
                        }
                        GridType.FOURTHS -> {
                            for (i in 1..3) {
                                drawLine(paintColor, androidx.compose.ui.geometry.Offset(w * i / 4, 0f), androidx.compose.ui.geometry.Offset(w * i / 4, h), strokeWidth)
                                drawLine(paintColor, androidx.compose.ui.geometry.Offset(0f, h * i / 4), androidx.compose.ui.geometry.Offset(w, h * i / 4), strokeWidth)
                            }
                        }
                        GridType.GOLDEN_RATIO -> {
                            drawLine(paintColor, androidx.compose.ui.geometry.Offset(w * 0.382f, 0f), androidx.compose.ui.geometry.Offset(w * 0.382f, h), strokeWidth)
                            drawLine(paintColor, androidx.compose.ui.geometry.Offset(w * 0.618f, 0f), androidx.compose.ui.geometry.Offset(w * 0.618f, h), strokeWidth)
                            drawLine(paintColor, androidx.compose.ui.geometry.Offset(0f, h * 0.382f), androidx.compose.ui.geometry.Offset(w, h * 0.382f), strokeWidth)
                            drawLine(paintColor, androidx.compose.ui.geometry.Offset(0f, h * 0.618f), androidx.compose.ui.geometry.Offset(w, h * 0.618f), strokeWidth)
                        }
                        GridType.CROSSHAIR -> {
                            val length = 20.dp.toPx()
                            drawLine(paintColor, androidx.compose.ui.geometry.Offset(w / 2, h / 2 - length), androidx.compose.ui.geometry.Offset(w / 2, h / 2 + length), strokeWidth)
                            drawLine(paintColor, androidx.compose.ui.geometry.Offset(w / 2 - length, h / 2), androidx.compose.ui.geometry.Offset(w / 2 + length, h / 2), strokeWidth)
                        }
                        GridType.NONE -> {}
                    }
                }
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
                    Slider(
                        value = currentZoom,
                        onValueChange = { currentZoom = it },
                        valueRange = minZoomRatio..maxZoomRatio,
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

        if (showBrightnessSlider && maxExposureIndex > minExposureIndex) {
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
        
        if (showVirtualHorizon) {
            val roll = deviceOrientation.roll
            val pitch = deviceOrientation.pitch
            val normalizedRoll = (roll % 90f + 90f) % 90f
            val isRollLevel = normalizedRoll < 2f || normalizedRoll > 88f
            val isPitchLevel = kotlin.math.abs(pitch) < 3f
            val isLevel = isRollLevel && isPitchLevel
            val levelColor = if (isLevel) Color(0xFF00FF00) else Color.White.copy(alpha = 0.6f)
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                
                // Fixed horizontal reference ticks
                val tickLength = 25.dp.toPx()
                val tickGap = 35.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.4f),
                    start = Offset(center.x - tickGap - tickLength, center.y),
                    end = Offset(center.x - tickGap, center.y),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.4f),
                    start = Offset(center.x + tickGap, center.y),
                    end = Offset(center.x + tickGap + tickLength, center.y),
                    strokeWidth = 2.dp.toPx()
                )
                
                // Rotating 4-spoke circle (Center Reticle)
                val circleRadius = 14.dp.toPx()
                val spokeInner = 14.dp.toPx()
                val spokeOuter = 40.dp.toPx()
                
                withTransform({
                    rotate(degrees = -roll, pivot = center)
                }) {
                    drawCircle(
                        color = levelColor,
                        radius = circleRadius,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                    drawLine(levelColor, Offset(center.x - spokeOuter, center.y), Offset(center.x - spokeInner, center.y), 2.dp.toPx())
                    drawLine(levelColor, Offset(center.x + spokeInner, center.y), Offset(center.x + spokeOuter, center.y), 2.dp.toPx())
                    drawLine(levelColor, Offset(center.x, center.y - spokeOuter), Offset(center.x, center.y - spokeInner), 2.dp.toPx())
                    drawLine(levelColor, Offset(center.x, center.y + spokeInner), Offset(center.x, center.y + spokeOuter), 2.dp.toPx())
                }
                
                // Pitch bubble (Floating secondary circle for forward/backward tilt)
                val pitchFactor = (pitch / 45f).coerceIn(-1f, 1f)
                val bubbleOffset = Offset(
                    x = center.x,
                    y = center.y + pitchFactor * 80.dp.toPx()
                )
                
                drawCircle(
                    color = if (isLevel) Color(0xFF00FF00) else Color.White.copy(alpha = 0.5f),
                    radius = 10.dp.toPx(),
                    center = bubbleOffset,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )
            }
            
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${kotlin.math.abs(roll).toInt()}°",
                    color = levelColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = (-30).dp)
                )
            }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (isTorchOn) {
                                        isTorchOn = false
                                    } else {
                                        flashMode = when (flashMode) {
                                            FlashMode.AUTO -> FlashMode.ON
                                            FlashMode.ON -> FlashMode.OFF
                                            FlashMode.OFF -> FlashMode.AUTO
                                        }
                                    }
                                },
                                onLongPress = {
                                    isTorchOn = !isTorchOn
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val flashIcon = if (isTorchOn) {
                        Icons.Default.FlashOn
                    } else {
                        when (flashMode) {
                            FlashMode.AUTO -> Icons.Default.FlashAuto
                            FlashMode.ON -> Icons.Default.FlashOn
                            FlashMode.OFF -> Icons.Default.FlashOff
                        }
                    }
                    val iconTint = if (isTorchOn) Color.Yellow else Color.White
                    Icon(imageVector = flashIcon, contentDescription = "Flash", tint = iconTint)
                }
                if (cameraMode == CameraMode.PHOTO) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = {
                        timerMode = when (timerMode) {
                            TimerMode.OFF -> TimerMode.SEC_3
                            TimerMode.SEC_3 -> TimerMode.SEC_10
                            TimerMode.SEC_10 -> TimerMode.SEC_15
                            TimerMode.SEC_15 -> TimerMode.SEC_20
                            TimerMode.SEC_20 -> if (isHandTrackingInstalled && isHandTrackingEnabled) TimerMode.PEACE else TimerMode.OFF
                            TimerMode.PEACE -> TimerMode.OFF
                        }
                    }) {
                        val timerTint = if (timerMode == TimerMode.OFF) Color.White else Color.Yellow
                        Box(contentAlignment = Alignment.Center) {
                            val icon = if (timerMode == TimerMode.PEACE) Icons.Default.PanTool else Icons.Default.Timer
                            Icon(imageVector = icon, contentDescription = "Timer", tint = timerTint)
                            if (timerMode != TimerMode.OFF && timerMode != TimerMode.PEACE) {
                                Text(
                                    text = "${timerMode.seconds}",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.offset(y = 2.dp)
                                )
                            }
                        }
                    }
                    
                    if (timerMode != TimerMode.OFF) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${timerBurstCount}x",
                            color = Color.Yellow,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    timerBurstCount = when (timerBurstCount) {
                                        1 -> 3
                                        3 -> 5
                                        5 -> 10
                                        else -> 1
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cameraMode == CameraMode.PHOTO) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable {
                                aspectRatio = when (aspectRatio) {
                                    AspectRatioMode.RATIO_4_3 -> AspectRatioMode.RATIO_16_9
                                    AspectRatioMode.RATIO_16_9 -> AspectRatioMode.RATIO_1_1
                                    AspectRatioMode.RATIO_1_1 -> AspectRatioMode.RATIO_4_3
                                }
                                prefs.edit().putString("aspectRatio", aspectRatio.name).apply()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = aspectRatio.label,
                            color = Color.Yellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable {
                                videoQuality = when (videoQuality) {
                                    VideoQualityMode.HD -> VideoQualityMode.FHD
                                    VideoQualityMode.FHD -> VideoQualityMode.UHD
                                    VideoQualityMode.UHD -> VideoQualityMode.HD
                                }
                                prefs.edit().putString("videoQuality", videoQuality.name).apply()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = videoQuality.label,
                            color = Color.Yellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable {
                                videoFps = when (videoFps) {
                                    VideoFpsMode.FPS_30 -> VideoFpsMode.FPS_60
                                    VideoFpsMode.FPS_60 -> VideoFpsMode.FPS_30
                                }
                                prefs.edit().putString("videoFps", videoFps.name).apply()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = videoFps.label,
                            color = Color.Yellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = { showSettings = true }) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showProPanel && !showSettings) {
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
            
            if (showLayerPanel && !showSettings) {
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
                                .background(if (currentZoom == zoomVal) Color.Yellow.copy(alpha = 0.3f) else Color.Transparent)
                                .clickable { currentZoom = zoomVal }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label, 
                                color = if (currentZoom == zoomVal) Color.Yellow else Color.White,
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
                            if (showLayerPanel) showProPanel = false
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
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PHOTO",
                        color = if (cameraMode == CameraMode.PHOTO) Color.Yellow else Color.White,
                        fontWeight = if (cameraMode == CameraMode.PHOTO) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { if (!isRecording) cameraMode = CameraMode.PHOTO }
                            .padding(horizontal = 12.dp)
                    )
                    Text(text = "|", color = Color.Gray)
                    Text(
                        text = "VIDEO",
                        color = if (cameraMode == CameraMode.VIDEO) Color.Yellow else Color.White,
                        fontWeight = if (cameraMode == CameraMode.VIDEO) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { if (!isRecording) cameraMode = CameraMode.VIDEO }
                            .padding(horizontal = 12.dp)
                    )
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
                            if (showProPanel) showLayerPanel = false
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
                isRecording = isRecording,
                cameraMode = cameraMode,
                lensFacing = lensFacing,
                context = context,
                onThumbnailClick = { showMediaPreviewDialog = true },
                onShutterTap = { initiateCapture() },
                onBurstStart = { isBursting = true },
                onBurstEnd = { isBursting = false },
                onSwitchCamera = {
                    if (!isRecording) {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    }
                }
            )
        }
        
        if (showSettings) {
            MainSettingsDialog(
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
                onDismiss = { showSettings = false }
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
}

data class DeviceOrientationData(
    val roll: Float = 0f,
    val pitch: Float = 0f,
    val isFlat: Boolean = false
)

@Composable
fun rememberDeviceOrientation(): DeviceOrientationData {
    val context = LocalContext.current
    var data by remember { mutableStateOf(DeviceOrientationData()) }
    
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        var filteredRoll = 0f
        var filteredPitch = 0f
        val alpha = 0.2f
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val rawRoll = Math.toDegrees(kotlin.math.atan2(x.toDouble(), y.toDouble())).toFloat()
                val isFlat = kotlin.math.abs(z) > 8.5f
                val norm = kotlin.math.sqrt((x * x + y * y).toDouble()).toFloat()
                val rawPitch = Math.toDegrees(kotlin.math.atan2(-z.toDouble(), norm.coerceAtLeast(0.001f).toDouble())).toFloat()
                
                filteredRoll += alpha * (rawRoll - filteredRoll)
                filteredPitch += alpha * (rawPitch - filteredPitch)
                
                var finalRoll = filteredRoll
                val rollMod = ((filteredRoll % 90f) + 90f) % 90f
                if (rollMod < 1.8f || rollMod > 88.2f) {
                    finalRoll = kotlin.math.round(filteredRoll / 90f) * 90f
                }
                
                var finalPitch = filteredPitch
                if (kotlin.math.abs(filteredPitch) < 1.8f) {
                    finalPitch = 0f
                }
                
                data = DeviceOrientationData(
                    roll = kotlin.math.floor(finalRoll),
                    pitch = kotlin.math.floor(finalPitch),
                    isFlat = isFlat
                )
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        sensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
    return data
}

private fun fetchLatestMediaUri(context: android.content.Context): Uri? {
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

private fun takePhoto(
    imageCapture: ImageCapture, 
    context: android.content.Context, 
    executor: java.util.concurrent.Executor,
    showWatermark: Boolean,
    watermarkElements: List<WatermarkElement>,
    liveLocation: android.location.Location?,
    liveAddress: android.location.Address?,
    enableGeotagging: Boolean,
    enableRawCapture: Boolean,
    aspectRatioMode: AspectRatioMode,
    onPhotoSaved: (android.graphics.Bitmap, android.net.Uri) -> Unit
) {
    if (enableRawCapture) {
        android.widget.Toast.makeText(context, "RAW Capture is enabled (Saving as DNG is experimental)", android.widget.Toast.LENGTH_SHORT).show()
    }
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                try {
                    var bitmap = image.toBitmap()
                    image.close()
                    
                    if (aspectRatioMode == AspectRatioMode.RATIO_1_1) {
                        val size = Math.min(bitmap.width, bitmap.height)
                        val cropX = (bitmap.width - size) / 2
                        val cropY = (bitmap.height - size) / 2
                        bitmap = android.graphics.Bitmap.createBitmap(bitmap, cropX, cropY, size, size)
                    }
                    
                    var location: android.location.Location? = null
                    if (enableGeotagging && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                        location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) 
                            ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    }
                    
                    val resultBitmap = if (showWatermark) {
                        val mutableBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                        val canvas = android.graphics.Canvas(mutableBitmap)
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            isAntiAlias = true
                            setShadowLayer(5f, 2f, 2f, android.graphics.Color.BLACK)
                        }
                        
                        val marginX = bitmap.width * 0.05f
                        val marginY = bitmap.height * 0.05f
                        val textSize = bitmap.height * 0.03f
                        val lineSpacing = textSize * 1.2f
                        paint.textSize = textSize
                        
                        WatermarkQuadrant.values().forEach { quadrant ->
                            val elements = watermarkElements.filter { it.quadrant == quadrant }
                            if (elements.isNotEmpty()) {
                                var currentY = when (quadrant) {
                                    WatermarkQuadrant.TOP_LEFT, WatermarkQuadrant.TOP_RIGHT -> marginY + textSize
                                    WatermarkQuadrant.BOTTOM_LEFT, WatermarkQuadrant.BOTTOM_RIGHT -> bitmap.height - marginY
                                }
                                
                                val quadrantElements = if (quadrant == WatermarkQuadrant.BOTTOM_LEFT || quadrant == WatermarkQuadrant.BOTTOM_RIGHT) elements.reversed() else elements
                                val maxTextWidth = (bitmap.width * 0.45f).toInt()
                                val textPaint = android.text.TextPaint(paint)
                                
                                quadrantElements.forEach { element ->
                                    val text = when (element.type) {
                                        WatermarkElementType.TEXT -> element.content
                                        WatermarkElementType.LOCATION -> formatLocationElement(element.content, location ?: liveLocation, liveAddress)
                                        WatermarkElementType.DATE -> formatDateElement(element.content)
                                    }
                                    
                                    textPaint.textSize = textSize * (element.size / 14f)
                                    
                                    val alignment = when (quadrant) {
                                        WatermarkQuadrant.TOP_LEFT, WatermarkQuadrant.BOTTOM_LEFT -> android.text.Layout.Alignment.ALIGN_NORMAL
                                        WatermarkQuadrant.TOP_RIGHT, WatermarkQuadrant.BOTTOM_RIGHT -> android.text.Layout.Alignment.ALIGN_OPPOSITE
                                    }
                                    
                                    val layout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                        android.text.StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxTextWidth).setAlignment(alignment).build()
                                    } else {
                                        @Suppress("DEPRECATION")
                                        android.text.StaticLayout(text, textPaint, maxTextWidth, alignment, 1.0f, 0.0f, false)
                                    }
                                    
                                    val currentX = when (quadrant) {
                                        WatermarkQuadrant.TOP_LEFT, WatermarkQuadrant.BOTTOM_LEFT -> marginX
                                        WatermarkQuadrant.TOP_RIGHT, WatermarkQuadrant.BOTTOM_RIGHT -> bitmap.width - marginX - maxTextWidth
                                    }
                                    
                                    if (quadrant == WatermarkQuadrant.BOTTOM_LEFT || quadrant == WatermarkQuadrant.BOTTOM_RIGHT) {
                                        currentY -= layout.height
                                    }
                                    
                                    canvas.save()
                                    canvas.translate(currentX, currentY)
                                    layout.draw(canvas)
                                    canvas.restore()
                                    
                                    if (quadrant == WatermarkQuadrant.TOP_LEFT || quadrant == WatermarkQuadrant.TOP_RIGHT) {
                                        currentY += layout.height + lineSpacing
                                    } else {
                                        currentY -= lineSpacing
                                    }
                                }
                            }
                        }
                        mutableBitmap
                    } else bitmap
                    
                    val name = java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", java.util.Locale.US).format(System.currentTimeMillis())
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
                            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/evcam")
                        }
                    }
                    val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            resultBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
                        }
                        if (enableGeotagging && location != null) {
                            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                                val exif = android.media.ExifInterface(pfd.fileDescriptor)
                                fun convertLocationToExifFormat(coord: Double): String {
                                    val absCoord = Math.abs(coord)
                                    val degree = absCoord.toInt()
                                    val minute = ((absCoord - degree) * 60).toInt()
                                    val second = (((absCoord - degree) * 60) - minute) * 60
                                    return "$degree/1,$minute/1,${(second * 1000).toInt()}/1000"
                                }
                                    exif.setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE, convertLocationToExifFormat(location.latitude))
                                    exif.setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE_REF, if (location.latitude > 0) "N" else "S")
                                    exif.setAttribute(android.media.ExifInterface.TAG_GPS_LONGITUDE, convertLocationToExifFormat(location.longitude))
                                    exif.setAttribute(android.media.ExifInterface.TAG_GPS_LONGITUDE_REF, if (location.longitude > 0) "E" else "W")
                                    exif.saveAttributes()
                                }
                            }
                        
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onPhotoSaved(resultBitmap, uri)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Evcam", "Failed to save photo", e)
                }
            }

            override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                android.util.Log.e("Evcam", "Photo capture failed", exception)
            }
        }
    )
}

@SuppressLint("MissingPermission")
private fun startVideoRecord(
    videoCapture: VideoCapture<Recorder>,
    context: android.content.Context,
    onEvent: (VideoRecordEvent) -> Unit
): Recording {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/evcam")
        }
    }

    val mediaStoreOutputOptions = MediaStoreOutputOptions
        .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        .setContentValues(contentValues)
        .build()

    val pendingRecording = videoCapture.output
        .prepareRecording(context, mediaStoreOutputOptions)
        
    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        pendingRecording.withAudioEnabled()
    }

    return pendingRecording.start(androidx.core.content.ContextCompat.getMainExecutor(context), onEvent)
}

@Composable
fun WatermarkSettingsDialog(
    showWatermark: Boolean,
    onShowWatermarkChange: (Boolean) -> Unit,
    watermarkElements: List<WatermarkElement>,
    onWatermarkElementsChange: (List<WatermarkElement>) -> Unit,
    liveLocation: android.location.Location?,
    liveAddress: android.location.Address?,
    onDismiss: () -> Unit
) {
    var showAddMenu by remember { mutableStateOf(false) }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(Color(0xFF1E1E1E), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text("Advanced Watermark", color = Color.White, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Watermark", color = Color.White)
                    Switch(checked = showWatermark, onCheckedChange = onShowWatermarkChange)
                }
                
                if (showWatermark) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Preview (Drag/Tap to configure)", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f/4f)
                            .background(Color.Black, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        WatermarkQuadrant.values().forEach { quadrant ->
                            val elementsInQuadrant = watermarkElements.filter { it.quadrant == quadrant }
                            val alignment = when(quadrant) {
                                WatermarkQuadrant.TOP_LEFT -> Alignment.TopStart
                                WatermarkQuadrant.TOP_RIGHT -> Alignment.TopEnd
                                WatermarkQuadrant.BOTTOM_LEFT -> Alignment.BottomStart
                                WatermarkQuadrant.BOTTOM_RIGHT -> Alignment.BottomEnd
                            }
                            Column(modifier = Modifier.align(alignment)) {
                                elementsInQuadrant.forEach { element ->
                                    val text = when (element.type) {
                                        WatermarkElementType.TEXT -> element.content
                                        WatermarkElementType.LOCATION -> formatLocationElement(element.content, liveLocation, liveAddress)
                                        WatermarkElementType.DATE -> formatDateElement(element.content)
                                    }
                                    
                                    var offsetX by remember { mutableFloatStateOf(0f) }
                                    var offsetY by remember { mutableFloatStateOf(0f) }
                                    
                                    Text(
                                        text = text, 
                                        color = Color.White, 
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .offset { androidx.compose.ui.unit.IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                                            .pointerInput(element) {
                                                detectDragGestures(
                                                    onDragEnd = {
                                                        var newQ = element.quadrant
                                                        when (element.quadrant) {
                                                            WatermarkQuadrant.TOP_LEFT -> {
                                                                if (offsetX > 200) newQ = WatermarkQuadrant.TOP_RIGHT
                                                                if (offsetY > 300) newQ = WatermarkQuadrant.BOTTOM_LEFT
                                                                if (offsetX > 200 && offsetY > 300) newQ = WatermarkQuadrant.BOTTOM_RIGHT
                                                            }
                                                            WatermarkQuadrant.TOP_RIGHT -> {
                                                                if (offsetX < -200) newQ = WatermarkQuadrant.TOP_LEFT
                                                                if (offsetY > 300) newQ = WatermarkQuadrant.BOTTOM_RIGHT
                                                                if (offsetX < -200 && offsetY > 300) newQ = WatermarkQuadrant.BOTTOM_LEFT
                                                            }
                                                            WatermarkQuadrant.BOTTOM_LEFT -> {
                                                                if (offsetX > 200) newQ = WatermarkQuadrant.BOTTOM_RIGHT
                                                                if (offsetY < -300) newQ = WatermarkQuadrant.TOP_LEFT
                                                                if (offsetX > 200 && offsetY < -300) newQ = WatermarkQuadrant.TOP_RIGHT
                                                            }
                                                            WatermarkQuadrant.BOTTOM_RIGHT -> {
                                                                if (offsetX < -200) newQ = WatermarkQuadrant.BOTTOM_LEFT
                                                                if (offsetY < -300) newQ = WatermarkQuadrant.TOP_RIGHT
                                                                if (offsetX < -200 && offsetY < -300) newQ = WatermarkQuadrant.TOP_LEFT
                                                            }
                                                        }
                                                        if (newQ != element.quadrant) {
                                                            val newList = watermarkElements.toMutableList()
                                                            val idx = newList.indexOfFirst { it.id == element.id }
                                                            if (idx != -1) {
                                                                newList[idx] = element.copy(quadrant = newQ)
                                                                onWatermarkElementsChange(newList)
                                                            }
                                                        }
                                                        offsetX = 0f
                                                        offsetY = 0f
                                                    }
                                                ) { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset ->
                                                    change.consume()
                                                    offsetX += dragAmount.x
                                                    offsetY += dragAmount.y
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Elements List
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                        items(watermarkElements.size) { index ->
                            val element = watermarkElements[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF2A2A2A), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                                    .padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(element.type.name, color = Color.Gray, fontSize = 10.sp)
                                    if (element.type == WatermarkElementType.TEXT) {
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = element.content,
                                            onValueChange = { newText ->
                                                val newList = watermarkElements.toMutableList()
                                                newList[index] = element.copy(content = newText)
                                                onWatermarkElementsChange(newList)
                                            },
                                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White)
                                        )
                                    } else {
                                        var formatExpanded by remember { mutableStateOf(false) }
                                        Box(modifier = Modifier.padding(vertical = 4.dp)) {
                                            Text(
                                                element.content.ifEmpty { "Tap to Select Format" }, 
                                                color = Color.White, 
                                                fontSize = 14.sp, 
                                                modifier = Modifier.clickable { formatExpanded = true }.padding(vertical = 4.dp)
                                            )
                                            androidx.compose.material3.DropdownMenu(expanded = formatExpanded, onDismissRequest = { formatExpanded = false }) {
                                                val options = if (element.type == WatermarkElementType.DATE) {
                                                    listOf("yyyy/MM/dd HH:mm", "dd MMM yyyy", "hh:mm a", "dd/MM/yyyy", "EEEE, dd MMMM yyyy", "MM/dd/yyyy HH:mm:ss")
                                                } else {
                                                    listOf("CITY", "CITY_COUNTRY", "FULL_ADDRESS", "DECIMAL_DEGREES", "DMS")
                                                }
                                                options.forEach { opt ->
                                                    androidx.compose.material3.DropdownMenuItem(
                                                        text = { Text(opt) },
                                                        onClick = {
                                                            val newList = watermarkElements.toMutableList()
                                                            newList[index] = element.copy(content = opt)
                                                            onWatermarkElementsChange(newList)
                                                            formatExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Size: ${element.size}", color = Color.Gray, fontSize = 10.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(modifier = Modifier.clickable { 
                                            val newList = watermarkElements.toMutableList()
                                            newList[index] = element.copy(size = (element.size - 1).coerceAtLeast(8))
                                            onWatermarkElementsChange(newList)
                                        }.background(Color.DarkGray, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) { Text("-", color = Color.White, fontSize = 12.sp) }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(modifier = Modifier.clickable { 
                                            val newList = watermarkElements.toMutableList()
                                            newList[index] = element.copy(size = (element.size + 1).coerceAtMost(48))
                                            onWatermarkElementsChange(newList)
                                        }.background(Color.DarkGray, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) { Text("+", color = Color.White, fontSize = 12.sp) }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                // Quadrant selector
                                var expanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.background(Color(0xFF333333), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp).clickable { expanded = true }) {
                                    Text(element.quadrant.name.replace("_", " "), color = Color.White, fontSize = 10.sp)
                                    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        WatermarkQuadrant.values().forEach { q ->
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text(q.name) },
                                                onClick = {
                                                    val newList = watermarkElements.toMutableList()
                                                    newList[index] = element.copy(quadrant = q)
                                                    onWatermarkElementsChange(newList)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.IconButton(
                                    onClick = {
                                        val newList = watermarkElements.toMutableList()
                                        newList.removeAt(index)
                                        onWatermarkElementsChange(newList)
                                    }
                                ) {
                                    Text("✕", color = Color.Gray, fontSize = 16.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        item {
                            Box {
                                Button(
                                    onClick = { showAddMenu = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                ) {
                                    Text("+ Add Element", color = Color.White)
                                }
                                androidx.compose.material3.DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                                    WatermarkElementType.values().forEach { type ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(type.name) },
                                            onClick = {
                                                val newList = watermarkElements.toMutableList()
                                                val content = if (type == WatermarkElementType.TEXT) "New Text" else type.name
                                                newList.add(WatermarkElement(java.util.UUID.randomUUID().toString(), type, content, WatermarkQuadrant.BOTTOM_LEFT))
                                                onWatermarkElementsChange(newList)
                                                showAddMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) {
                    Text("Done", color = Color.White)
                }
            }
        }
    }
}

fun formatLocationElement(format: String, location: android.location.Location?, address: android.location.Address?): String {
    if (location == null) return "[Location]"
    val fmt = if (format.isEmpty() || format == "LOCATION") "CITY" else format
    return when (fmt) {
        "CITY" -> address?.locality ?: address?.subAdminArea ?: "Unknown City"
        "CITY_COUNTRY" -> {
            val city = address?.locality ?: address?.subAdminArea ?: ""
            val country = address?.countryName ?: ""
            if (city.isNotEmpty() && country.isNotEmpty()) "$city, $country"
            else if (city.isNotEmpty()) city else if (country.isNotEmpty()) country else "Unknown Location"
        }
        "FULL_ADDRESS" -> address?.getAddressLine(0) ?: "Unknown Address"
        "DECIMAL_DEGREES" -> String.format(java.util.Locale.US, "%.5f°, %.5f°", location.latitude, location.longitude)
        "DMS" -> {
            fun toDms(deg: Double): String {
                val abs = Math.abs(deg)
                val d = abs.toInt()
                val m = ((abs - d) * 60).toInt()
                val s = (((abs - d) * 60.0) - m) * 60.0
                return "$d°$m'${String.format(java.util.Locale.US, "%.1f", s)}\""
            }
            val latDir = if (location.latitude >= 0) "N" else "S"
            val lngDir = if (location.longitude >= 0) "E" else "W"
            "${toDms(location.latitude)}$latDir, ${toDms(location.longitude)}$lngDir"
        }
        else -> "Lat: ${location.latitude}, Lng: ${location.longitude}"
    }
}

fun formatDateElement(format: String): String {
    val fmt = if (format.isEmpty() || format == "DATE") "yyyy/MM/dd HH:mm" else format
    return try {
        java.text.SimpleDateFormat(fmt, java.util.Locale.US).format(java.util.Date())
    } catch (e: Exception) {
        fmt
    }
}

@Composable
private fun MediaPreviewDialog(
    lastCapturedBitmap: android.graphics.Bitmap?,
    lastCapturedUri: Uri?,
    cameraMode: CameraMode,
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .fillMaxHeight(0.75f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.DarkGray.copy(alpha = 0.95f))
                    .clickable(enabled = false) {}
                    .padding(16.dp)
            ) {
                if (lastCapturedBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = lastCapturedBitmap.asImageBitmap(),
                        contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 56.dp)
                    )
                } else if (lastCapturedUri != null) {
                    AsyncImage(
                        model = lastCapturedUri,
                        contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 56.dp)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close, 
                        contentDescription = "Close", 
                        tint = Color.White, 
                        modifier = Modifier.size(20.dp)
                    )
                }

                Button(
                    onClick = {
                        val targetUri = lastCapturedUri ?: fetchLatestMediaUri(context)
                        if (targetUri != null) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(targetUri, if (cameraMode == CameraMode.VIDEO) "video/*" else "image/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No Gallery app found", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "No Gallery item found", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Yellow),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open in Gallery", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun MainSettingsDialog(
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

@Composable
private fun CameraBottomBar(
    lastCapturedBitmap: android.graphics.Bitmap?,
    lastCapturedUri: Uri?,
    isRecording: Boolean,
    cameraMode: CameraMode,
    lensFacing: Int,
    context: android.content.Context,
    onThumbnailClick: () -> Unit,
    onShutterTap: () -> Unit,
    onBurstStart: () -> Unit,
    onBurstEnd: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray.copy(alpha = 0.5f))
                .clickable { 
                    if (lastCapturedBitmap != null || lastCapturedUri != null) {
                        onThumbnailClick()
                    } else {
                        Toast.makeText(context, "Belum ada foto atau video yang diambil", Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (lastCapturedBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = lastCapturedBitmap.asImageBitmap(),
                    contentDescription = "Gallery Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (lastCapturedUri != null) {
                AsyncImage(
                    model = lastCapturedUri,
                    contentDescription = "Gallery Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (isRecording) Color.Red else Color.White)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onShutterTap() },
                        onLongPress = {
                            if (cameraMode == CameraMode.PHOTO) {
                                onBurstStart()
                            } else {
                                onShutterTap()
                            }
                        },
                        onPress = {
                            try {
                                tryAwaitRelease()
                            } finally {
                                onBurstEnd()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {}

        IconButton(
            onClick = onSwitchCamera,
            modifier = Modifier.size(64.dp).background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun ProControlPanel(
    iso: Float,
    minIso: Float,
    maxIso: Float,
    isIsoAuto: Boolean,
    onIsoChange: (Float) -> Unit,
    onIsoAutoToggle: () -> Unit,
    shutterSpeed: Float,
    isShutterAuto: Boolean,
    onShutterChange: (Float) -> Unit,
    onShutterAutoToggle: () -> Unit,
    focusDistance: Float,
    isFocusAuto: Boolean,
    onFocusChange: (Float) -> Unit,
    onFocusAutoToggle: () -> Unit,
    whiteBalance: Int,
    onWhiteBalanceChange: (Int) -> Unit,
    manualKelvin: Float,
    onManualKelvinChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MANUAL CONTROLS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Icon(
                imageVector = Icons.Default.Close, 
                contentDescription = "Close panel", 
                tint = Color.White,
                modifier = Modifier.size(20.dp).clickable { onClose() }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Text("ISO", color = Color.Gray, modifier = Modifier.width(40.dp), fontSize = 12.sp)
            Slider(
                value = iso.coerceIn(minIso, maxIso), 
                onValueChange = onIsoChange, 
                valueRange = minIso..maxIso, 
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text(
                text = if (isIsoAuto) "AUTO" else "${iso.toInt()}", 
                color = if (isIsoAuto) Color.Yellow else Color.White, 
                modifier = Modifier.width(40.dp).clickable { onIsoAutoToggle() }, 
                textAlign = TextAlign.End, 
                fontSize = 12.sp
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Text("SHT", color = Color.Gray, modifier = Modifier.width(40.dp), fontSize = 12.sp)
            Slider(value = shutterSpeed, onValueChange = onShutterChange, valueRange = 100000f..1000000000f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Text(
                text = if (isShutterAuto) "AUTO" else "1/${1_000_000_000L / shutterSpeed.toLong().coerceAtLeast(1)}", 
                color = if (isShutterAuto) Color.Yellow else Color.White, 
                modifier = Modifier.width(40.dp).clickable { onShutterAutoToggle() }, 
                textAlign = TextAlign.End, 
                fontSize = 12.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Text("FOC", color = Color.Gray, modifier = Modifier.width(40.dp), fontSize = 12.sp)
            Slider(value = focusDistance, onValueChange = onFocusChange, valueRange = 0f..10f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Text(
                text = if (isFocusAuto) "AUTO" else String.format(Locale.US, "%.1f", focusDistance), 
                color = if (isFocusAuto) Color.Yellow else Color.White, 
                modifier = Modifier.width(40.dp).clickable { onFocusAutoToggle() }, 
                textAlign = TextAlign.End, 
                fontSize = 12.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("AWB", color = Color.Gray, modifier = Modifier.width(40.dp), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = "AUTO",
                    color = if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_AUTO) Color.Black else Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_AUTO) Color.Yellow else Color.Transparent)
                        .clickable { onWhiteBalanceChange(CaptureRequest.CONTROL_AWB_MODE_AUTO) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp
                )
                Text(
                    text = "DAY",
                    color = if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT) Color.Black else Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT) Color.Yellow else Color.Transparent)
                        .clickable { onWhiteBalanceChange(CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp
                )
                Text(
                    text = "CLD",
                    color = if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT) Color.Black else Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT) Color.Yellow else Color.Transparent)
                        .clickable { onWhiteBalanceChange(CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp
                )
                Text(
                    text = "CUS",
                    color = if (whiteBalance == android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_OFF) Color.Black else Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (whiteBalance == android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_OFF) Color.Yellow else Color.Transparent)
                        .clickable { onWhiteBalanceChange(android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_OFF) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp
                )
            }
            if (whiteBalance == android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_OFF) {
                Slider(
                    value = manualKelvin,
                    onValueChange = onManualKelvinChange,
                    valueRange = 2000f..10000f,
                    modifier = Modifier.height(30.dp)
                )
                Text("${manualKelvin.toInt()}K", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DisplayOverlaysPanel(
    gridType: GridType,
    onGridTypeChange: (GridType) -> Unit,
    showVirtualHorizon: Boolean,
    onVirtualHorizonChange: (Boolean) -> Unit,
    enableHistogram: Boolean,
    onHistogramChange: (Boolean) -> Unit,
    enableFocusPeaking: Boolean,
    onFocusPeakingChange: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("DISPLAY OVERLAYS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(20.dp).clickable { onClose() }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Grid", color = Color.Gray, modifier = Modifier.width(100.dp), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                GridType.values().forEach { g ->
                    val isSelected = gridType == g
                    Text(
                        text = g.label,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.2f))
                            .clickable { onGridTypeChange(g) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Level", color = Color.Gray, modifier = Modifier.width(100.dp), fontSize = 12.sp)
            Switch(
                checked = showVirtualHorizon,
                onCheckedChange = onVirtualHorizonChange,
                modifier = Modifier.scale(0.8f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Histogram", color = Color.Gray, modifier = Modifier.width(100.dp), fontSize = 12.sp)
            Switch(
                checked = enableHistogram,
                onCheckedChange = onHistogramChange,
                modifier = Modifier.scale(0.8f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Focus Peaking", color = Color.Gray, modifier = Modifier.width(100.dp), fontSize = 12.sp)
            Switch(
                checked = enableFocusPeaking,
                onCheckedChange = onFocusPeakingChange,
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}
