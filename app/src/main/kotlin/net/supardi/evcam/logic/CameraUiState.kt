package net.supardi.evcam.logic


import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCapture
import androidx.camera.video.VideoCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.camera.core.CameraSelector
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.ui.geometry.Offset
import net.supardi.evcam.*


@Stable
class CameraUiState(
    val context: Context,
    val prefs: SharedPreferences
) {
    var cameraMode by mutableStateOf(CameraMode.PHOTO)
    var isProMode by mutableStateOf(false)
    var lensFacing by mutableStateOf(CameraSelector.LENS_FACING_BACK)
    var isRecording by mutableStateOf(false)
    var activeRecording by mutableStateOf<Recording?>(null)
    
    var lastCapturedUri by mutableStateOf<Uri?>(null)
    var lastCapturedBitmap by mutableStateOf<android.graphics.Bitmap?>(null)
    
    var cameraControl by mutableStateOf<CameraControl?>(null)
    var camera2Control by mutableStateOf<Camera2CameraControl?>(null)
    var iso by mutableFloatStateOf(100f)
    var minIso by mutableFloatStateOf(50f)
    var maxIso by mutableFloatStateOf(3200f)
    var shutterSpeed by mutableFloatStateOf(10000000f) // 10ms
    var focusDistance by mutableFloatStateOf(0f)
    var whiteBalance by mutableIntStateOf(prefs.getInt("whiteBalance", android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE_AUTO))
    
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
    var focusState by mutableStateOf(FocusState.SEARCHING)
    var isAeAfLocked by mutableStateOf(false)
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
    
    var aspectRatio by mutableStateOf(AspectRatioMode.valueOf(prefs.getString("aspectRatio", AspectRatioMode.RATIO_4_3.name) ?: AspectRatioMode.RATIO_4_3.name))
    var videoQuality by mutableStateOf(VideoQualityMode.valueOf(prefs.getString("videoQuality", VideoQualityMode.HD.name) ?: VideoQualityMode.HD.name))
    var videoFps by mutableStateOf(VideoFpsMode.valueOf(prefs.getString("videoFps", VideoFpsMode.FPS_30.name) ?: VideoFpsMode.FPS_30.name))
    var videoAudioEnabled by mutableStateOf(prefs.getBoolean("videoAudioEnabled", true))
    var isNightModeEnabled by mutableStateOf(prefs.getBoolean("isNightModeEnabled", false))
    var isHdrEnabled by mutableStateOf(prefs.getBoolean("isHdrEnabled", false))
    var selectedFilter by mutableStateOf(ColorFilterMode.valueOf(prefs.getString("selectedFilter", ColorFilterMode.NORMAL.name) ?: ColorFilterMode.NORMAL.name))

    // Hardware capabilities for the currently active camera
    var hasFlashSupport by mutableStateOf(true)
    var hasHdrExtension by mutableStateOf(true)
    var hasNightExtension by mutableStateOf(true)
    var hasManualSensorSupport by mutableStateOf(true)
    var supportedVideoQualities by mutableStateOf<List<VideoQualityMode>>(VideoQualityMode.values().toList())
    var supportedVideoProfiles by mutableStateOf<List<String>>(emptyList())
    var showFilterDialog by mutableStateOf(false)
    var imageFormat by mutableStateOf(ImageFormatMode.valueOf(prefs.getString("imageFormat", ImageFormatMode.JPEG.name) ?: ImageFormatMode.JPEG.name))
    
    var imageCaptureUseCase by mutableStateOf<ImageCapture?>(null)
    var videoCaptureUseCase by mutableStateOf<VideoCapture<Recorder>?>(null)
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
    LaunchedEffect(state.videoAudioEnabled) { prefs.edit().putBoolean("videoAudioEnabled", state.videoAudioEnabled).apply() }
    LaunchedEffect(state.isNightModeEnabled) { prefs.edit().putBoolean("isNightModeEnabled", state.isNightModeEnabled).apply() }
    LaunchedEffect(state.selectedFilter) { prefs.edit().putString("selectedFilter", state.selectedFilter.name).apply() }
    LaunchedEffect(state.imageFormat) { prefs.edit().putString("imageFormat", state.imageFormat.name).apply() }
    
    LaunchedEffect(state.enableHistogram) { prefs.edit().putBoolean("enableHistogram", state.enableHistogram).apply() }
    LaunchedEffect(state.enableFocusPeaking) { prefs.edit().putBoolean("enableFocusPeaking", state.enableFocusPeaking).apply() }
    LaunchedEffect(state.enableRawCapture) { prefs.edit().putBoolean("enableRawCapture", state.enableRawCapture).apply() }
    LaunchedEffect(state.manualKelvin) { prefs.edit().putFloat("manualKelvin", state.manualKelvin).apply() }
    LaunchedEffect(state.whiteBalance) { prefs.edit().putInt("whiteBalance", state.whiteBalance).apply() }
    LaunchedEffect(state.timerBurstCount) { prefs.edit().putInt("timerBurstCount", state.timerBurstCount).apply() }
    
    LaunchedEffect(state.watermarkElements) {
        prefs.edit().putString("watermarkElements", serializeWatermarkElements(state.watermarkElements)).apply()
    }
    
    return state

}
