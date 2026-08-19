package net.supardi.evcam.logic


import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.hardware.camera2.CameraCharacteristics

import androidx.compose.ui.geometry.Offset
import net.supardi.evcam.*


@Stable
class CameraUiState(
    val context: Context,
    val prefs: SharedPreferences
) {
    var cameraMode by mutableStateOf(CameraMode.PHOTO)
    var isProMode by mutableStateOf(false)
    var lensFacing by mutableStateOf(CameraCharacteristics.LENS_FACING_BACK)
    var isRecording by mutableStateOf(false)
    var activeRecording by mutableStateOf<VideoRecordController?>(null)
    
    var lastCapturedUri by mutableStateOf<Uri?>(null)
    var lastCapturedBitmap by mutableStateOf<android.graphics.Bitmap?>(null)
    
    // Engine specific properties for manual control
    var iso by mutableFloatStateOf(100f)
    var minIso by mutableFloatStateOf(50f)
    var maxIso by mutableFloatStateOf(3200f)
    var shutterSpeed by mutableFloatStateOf(10000000f) // 10ms
    var minShutterSpeed by mutableFloatStateOf(100000f) // 1/10000s = 100µs
    var maxShutterSpeed by mutableFloatStateOf(1000000000f) // 1s
    var focusDistance by mutableFloatStateOf(0f)
    var maxFocusDistance by mutableFloatStateOf(10f)
    var whiteBalance by mutableIntStateOf(prefs.getInt("whiteBalance", android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO))
    var supportedAwbModes by mutableStateOf<List<Int>>(listOf(
        android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO,
        android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
        android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
        android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
        android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
    ))

    
    var isIsoAuto by mutableStateOf(true)
    var isShutterAuto by mutableStateOf(true)
    var isFocusAuto by mutableStateOf(true)
    var showProPanel by mutableStateOf(false)
    var enableHistogram by mutableStateOf(prefs.getBoolean("enableHistogram", false))
    var enableFocusPeaking by mutableStateOf(prefs.getBoolean("enableFocusPeaking", false))
    var enableRawCapture by mutableStateOf(prefs.getBoolean("enableRawCapture", false))
    var manualKelvin by mutableFloatStateOf(prefs.getFloat("manualKelvin", 5000f))
    var timerBurstCount by mutableIntStateOf(prefs.getInt("timerBurstCount", 1))
    
    var histogramData by mutableStateOf<IntArray?>(null)
    var histogramUpdateCount by mutableLongStateOf(0L)
    var peakingBitmap by mutableStateOf<android.graphics.Bitmap?>(null)
    var peakingUpdateCount by mutableLongStateOf(0L)
    
    var isBursting by mutableStateOf(false)
    var burstCount by mutableIntStateOf(0)
    
    var showSettings by mutableStateOf(false)
    var showLayerPanel by mutableStateOf(false)
    var showMediaPreviewDialog by mutableStateOf(false)
    
    var keepScreenOn by mutableStateOf(prefs.getBoolean("keepScreenOn", false))
    var maxBrightness by mutableStateOf(prefs.getBoolean("maxBrightness", false))

    
    var minZoomRatio by mutableFloatStateOf(1f)
    var maxZoomRatio by mutableFloatStateOf(1f)
    var currentZoom by mutableFloatStateOf(1f)
    val zoomAnim = Animatable(1f)
    
    var focusOffset by mutableStateOf<Offset?>(null)
    var showFocusBox by mutableStateOf(false)
    var focusState by mutableStateOf(FocusState.TAP_INITIAL)
    var focusTapCount by mutableIntStateOf(0)
    var isAeAfLocked by mutableStateOf(false)
    var isPendingAfLock by mutableStateOf(false)
    var recordingSeconds by mutableIntStateOf(0)
    var isTransitioningRatio by mutableStateOf(false)
    
    var gridType by mutableStateOf(GridType.valueOf(prefs.getString("gridType", GridType.NONE.name) ?: GridType.NONE.name))
    var flashMode by mutableStateOf(FlashMode.valueOf(prefs.getString("flashMode", FlashMode.AUTO.name) ?: FlashMode.AUTO.name))
    var timerMode by mutableStateOf(TimerMode.valueOf(prefs.getString("timerMode", TimerMode.OFF.name) ?: TimerMode.OFF.name))
    var showVirtualHorizon by mutableStateOf(prefs.getBoolean("showVirtualHorizon", true))
    var showZoomSlider by mutableStateOf(false)
    var showBrightnessSlider by mutableStateOf(false)
    var minExposureIndex by mutableIntStateOf(-6)
    var maxExposureIndex by mutableIntStateOf(6)
    var exposureStep by mutableFloatStateOf(0.3333f)
    var exposureIndex by mutableIntStateOf(0)
    var isTorchOn by mutableStateOf(false)
    var volumeShutterEnabled by mutableStateOf(prefs.getBoolean("volumeShutterEnabled", true))
    var mirrorSelfie by mutableStateOf(prefs.getBoolean("mirrorSelfie", true))

    
    var showPluginManager by mutableStateOf(false)
    var showCameraInfoDialog by mutableStateOf(false)
    var isHandTrackingInstalled by mutableStateOf(prefs.getBoolean("isHandTrackingInstalled", false))
    var isHandTrackingEnabled by mutableStateOf(prefs.getBoolean("isHandTrackingEnabled", true))
    var isShutterSoundEnabled by mutableStateOf(prefs.getBoolean("isShutterSoundEnabled", true))
    var showRemoveConfirmation by mutableStateOf(false)
    var showWatermark by mutableStateOf(prefs.getBoolean("showWatermark", false))
    var watermarkElements by mutableStateOf(deserializeWatermarkElements(prefs.getString("watermarkElements", null)))
    var enableGeotagging by mutableStateOf(prefs.getBoolean("enableGeotagging", false))
    var showWatermarkDialog by mutableStateOf(false)
    var liveLocation by mutableStateOf<android.location.Location?>(null)
    var liveAddress by mutableStateOf<android.location.Address?>(null)
    
    var enableEis by mutableStateOf(prefs.getBoolean("enableEis", false))
    var aspectRatio by mutableStateOf(AspectRatioMode.valueOf(prefs.getString("aspectRatio", AspectRatioMode.RATIO_16_9.name) ?: AspectRatioMode.RATIO_16_9.name))
    var videoQuality by mutableStateOf(VideoQualityMode.valueOf(prefs.getString("videoQuality", VideoQualityMode.HD.name) ?: VideoQualityMode.HD.name))
    var photoQuality by mutableStateOf(PhotoQualityMode.valueOf(prefs.getString("photoQuality", PhotoQualityMode.MAX.name) ?: PhotoQualityMode.MAX.name))
    var videoFps by mutableStateOf(VideoFpsMode.valueOf(prefs.getString("videoFps", VideoFpsMode.FPS_30.name) ?: VideoFpsMode.FPS_30.name))
    var videoAudioEnabled by mutableStateOf(true)
    var isNightModeEnabled by mutableStateOf(prefs.getBoolean("isNightModeEnabled", false))
    var isHdrEnabled by mutableStateOf(prefs.getBoolean("isHdrEnabled", false))
    var selectedFilter by mutableStateOf(ColorFilterMode.valueOf(prefs.getString("selectedFilter", ColorFilterMode.NORMAL.name) ?: ColorFilterMode.NORMAL.name))

    // Hardware capabilities for the currently active camera
    var hasFlashSupport by mutableStateOf(true)
    var hasHdrExtension by mutableStateOf(true)
    var hasNightExtension by mutableStateOf(true)
    var hasManualSensorSupport by mutableStateOf(true)
    var hasManualFocusSupport by mutableStateOf(true)
    var supportedSceneModes by mutableStateOf<List<Int>>(emptyList())
    var selectedSceneMode by mutableIntStateOf(android.hardware.camera2.CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
    
    var photoCustomScene by mutableStateOf(CustomSceneMode.valueOf(prefs.getString("photoCustomScene", CustomSceneMode.AUTO.name) ?: CustomSceneMode.AUTO.name))
    var videoCustomScene by mutableStateOf(CustomSceneMode.valueOf(prefs.getString("videoCustomScene", CustomSceneMode.AUTO.name) ?: CustomSceneMode.AUTO.name))

    var selectedCustomScene: CustomSceneMode
        get() = if (cameraMode == CameraMode.PHOTO) photoCustomScene else videoCustomScene
        set(value) {
            if (cameraMode == CameraMode.PHOTO) {
                photoCustomScene = value
            } else {
                videoCustomScene = value
            }
        }














    var supportedVideoQualities by mutableStateOf<List<VideoQualityMode>>(VideoQualityMode.values().toList())
    var supportedFpsModes by mutableStateOf<List<VideoFpsMode>>(VideoFpsMode.values().toList())
    var supportedVideoProfiles by mutableStateOf<List<String>>(emptyList())
    var showFilterDialog by mutableStateOf(false)
    var imageFormat by mutableStateOf(ImageFormatMode.valueOf(prefs.getString("imageFormat", ImageFormatMode.JPEG.name) ?: ImageFormatMode.JPEG.name))
    
    var imageCaptureUseCase by mutableStateOf<Any?>(null)
    var videoCaptureUseCase by mutableStateOf<Any?>(null)

    // Face Detection Data
    var isFaceDetectionEnabled by mutableStateOf(prefs.getBoolean("isFaceDetectionEnabled", true))
    var detectedFaces by mutableStateOf<List<android.hardware.camera2.params.Face>>(emptyList())
    var sensorActiveArraySize by mutableStateOf<android.graphics.Rect?>(null)
    var sensorCropRegion by mutableStateOf<android.graphics.Rect?>(null)
}

@Composable
fun rememberCameraUiState(
    context: Context,
    prefs: SharedPreferences
): CameraUiState {
    val state = remember(context, prefs) {
        CameraUiState(context, prefs)
    }
    
    // Auto initialization of Uri fallback logic inside wrapper
    LaunchedEffect(state) {
        if (state.lastCapturedUri == null) {
            val saved = state.prefs.getString("lastCapturedUri", null)
            val parsedUri = if (!saved.isNullOrEmpty()) Uri.parse(saved) else null
            state.lastCapturedUri = parsedUri ?: fetchLatestMediaUri(state.context)
        }
    }
    
    // Auto-Saver preferences effects
    LaunchedEffect(state.keepScreenOn) { prefs.edit().putBoolean("keepScreenOn", state.keepScreenOn).apply() }
    LaunchedEffect(state.maxBrightness) { prefs.edit().putBoolean("maxBrightness", state.maxBrightness).apply() }
    LaunchedEffect(state.gridType) { prefs.edit().putString("gridType", state.gridType.name).apply() }
    LaunchedEffect(state.flashMode) { prefs.edit().putString("flashMode", state.flashMode.name).apply() }
    LaunchedEffect(state.timerMode) { prefs.edit().putString("timerMode", state.timerMode.name).apply() }
    LaunchedEffect(state.showVirtualHorizon) { prefs.edit().putBoolean("showVirtualHorizon", state.showVirtualHorizon).apply() }
    LaunchedEffect(state.volumeShutterEnabled) { prefs.edit().putBoolean("volumeShutterEnabled", state.volumeShutterEnabled).apply() }
    LaunchedEffect(state.isShutterSoundEnabled) { prefs.edit().putBoolean("isShutterSoundEnabled", state.isShutterSoundEnabled).apply() }
    LaunchedEffect(state.isHandTrackingInstalled) { prefs.edit().putBoolean("isHandTrackingInstalled", state.isHandTrackingInstalled).apply() }
    LaunchedEffect(state.isHandTrackingEnabled) { prefs.edit().putBoolean("isHandTrackingEnabled", state.isHandTrackingEnabled).apply() }
    LaunchedEffect(state.showWatermark) { prefs.edit().putBoolean("showWatermark", state.showWatermark).apply() }
    LaunchedEffect(state.enableGeotagging) { prefs.edit().putBoolean("enableGeotagging", state.enableGeotagging).apply() }
    LaunchedEffect(state.aspectRatio) { prefs.edit().putString("aspectRatio", state.aspectRatio.name).apply() }
    LaunchedEffect(state.videoQuality) { prefs.edit().putString("videoQuality", state.videoQuality.name).apply() }
    LaunchedEffect(state.videoFps) { prefs.edit().putString("videoFps", state.videoFps.name).apply() }
    LaunchedEffect(state.isNightModeEnabled) { prefs.edit().putBoolean("isNightModeEnabled", state.isNightModeEnabled).apply() }
    LaunchedEffect(state.selectedFilter) { prefs.edit().putString("selectedFilter", state.selectedFilter.name).apply() }
    LaunchedEffect(state.imageFormat) { prefs.edit().putString("imageFormat", state.imageFormat.name).apply() }
    LaunchedEffect(state.photoCustomScene) { prefs.edit().putString("photoCustomScene", state.photoCustomScene.name).apply() }
    LaunchedEffect(state.videoCustomScene) { prefs.edit().putString("videoCustomScene", state.videoCustomScene.name).apply() }
    
    LaunchedEffect(state.enableHistogram) { prefs.edit().putBoolean("enableHistogram", state.enableHistogram).apply() }
    LaunchedEffect(state.enableFocusPeaking) { prefs.edit().putBoolean("enableFocusPeaking", state.enableFocusPeaking).apply() }
    LaunchedEffect(state.enableRawCapture) { prefs.edit().putBoolean("enableRawCapture", state.enableRawCapture).apply() }
    LaunchedEffect(state.enableEis) { prefs.edit().putBoolean("enableEis", state.enableEis).apply() }
    LaunchedEffect(state.manualKelvin) { prefs.edit().putFloat("manualKelvin", state.manualKelvin).apply() }
    LaunchedEffect(state.whiteBalance) { prefs.edit().putInt("whiteBalance", state.whiteBalance).apply() }
    LaunchedEffect(state.timerBurstCount) { prefs.edit().putInt("timerBurstCount", state.timerBurstCount).apply() }
    
    LaunchedEffect(state.watermarkElements) {
        prefs.edit().putString("watermarkElements", serializeWatermarkElements(state.watermarkElements)).apply()
    }
    
    return state

}
