package net.supardi.evcam.logic

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


class Camera2Engine(private val context: Context) {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    
    var cameraDevice: CameraDevice? = null
        private set
    var captureSession: CameraCaptureSession? = null
        private set
    
    private var backgroundThread: HandlerThread? = null
    var backgroundHandler: Handler? = null
        private set
    
    var imageReader: ImageReader? = null
    var analysisImageReader: ImageReader? = null
    var mediaRecorder: MediaRecorder? = null
    private var isRecordingVideo = false
    
    var previewRequestBuilder: CaptureRequest.Builder? = null
    var currentPreviewSurface: Surface? = null
    var persistentSurface: Surface? = null
        private set

    fun getOrCreatePersistentSurface(): Surface {
        if (persistentSurface == null) {
            persistentSurface = android.media.MediaCodec.createPersistentInputSurface()
        }
        return persistentSurface!!
    }
    
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Closed)
    val cameraState: StateFlow<CameraState> = _cameraState

    sealed class CameraState {
        object Closed : CameraState()
        object Opening : CameraState()
        data class Opened(val device: CameraDevice) : CameraState()
        data class Error(val error: Int) : CameraState()
    }

    fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(cameraId: String) {
        _cameraState.value = CameraState.Opening
        try {
            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        analysisImageReader?.close()
        analysisImageReader = null
        mediaRecorder?.release()
        mediaRecorder = null
        _cameraState.value = CameraState.Closed
    }

    data class VideoCapabilitiesInfo(
        val supportedQualities: List<VideoQualityMode>,
        val supportedFpsModes: List<VideoFpsMode>,
        val profileDescriptions: List<String>
    )

    fun getSupportedFpsForQuality(cameraId: String, quality: VideoQualityMode): List<VideoFpsMode> {
        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return listOf(VideoFpsMode.FPS_30)

            val targetWidth = when (quality) {
                VideoQualityMode.UHD -> 3840
                VideoQualityMode.QHD -> 2560
                VideoQualityMode.FHD -> 1920
                VideoQualityMode.HD -> 1280
                VideoQualityMode.SD -> 640
            }
            val targetHeight = when (quality) {
                VideoQualityMode.UHD -> 2160
                VideoQualityMode.QHD -> 1440
                VideoQualityMode.FHD -> 1080
                VideoQualityMode.HD -> 720
                VideoQualityMode.SD -> 480
            }

            val sizes = map.getOutputSizes(MediaRecorder::class.java) ?: emptyArray()
            val matchingSize = sizes.find { 
                (it.width == targetWidth && it.height == targetHeight) || (it.width == targetHeight && it.height == targetWidth) 
            }

            val fpsSet = mutableSetOf<Int>()

            if (matchingSize != null) {
                val durRecorder = map.getOutputMinFrameDuration(MediaRecorder::class.java, matchingSize)
                val baseFps = if (durRecorder > 0) (1_000_000_000.0 / durRecorder).toInt() else 30
                if (baseFps > 0) fpsSet.add(baseFps)

                try {
                    val highSpeedRanges = map.getHighSpeedVideoFpsRangesFor(matchingSize)
                    highSpeedRanges?.forEach { fpsSet.add(it.upper) }
                } catch (e: Exception) {}

                try {
                    val idInt = cameraId.toIntOrNull()
                    if (idInt != null) {
                        if (quality == VideoQualityMode.HD && android.media.CamcorderProfile.hasProfile(idInt, android.media.CamcorderProfile.QUALITY_HIGH_SPEED_720P)) {
                            fpsSet.add(120)
                        }
                        if (quality == VideoQualityMode.FHD && android.media.CamcorderProfile.hasProfile(idInt, android.media.CamcorderProfile.QUALITY_HIGH_SPEED_1080P)) {
                            fpsSet.add(120)
                        }
                    }
                } catch (e: Exception) {}
            } else {
                fpsSet.add(30)
            }

            val result = mutableListOf<VideoFpsMode>()
            val sortedFps = fpsSet.sorted()
            for (fpsVal in sortedFps) {
                when {
                    fpsVal in 25..35 -> if (!result.contains(VideoFpsMode.FPS_30)) result.add(VideoFpsMode.FPS_30)
                    fpsVal in 50..65 -> if (!result.contains(VideoFpsMode.FPS_60)) result.add(VideoFpsMode.FPS_60)
                    fpsVal in 110..130 -> if (!result.contains(VideoFpsMode.FPS_120)) result.add(VideoFpsMode.FPS_120)
                    fpsVal in 230..250 -> if (!result.contains(VideoFpsMode.FPS_240)) result.add(VideoFpsMode.FPS_240)
                }
            }
            if (result.isEmpty()) result.add(VideoFpsMode.FPS_30)
            return result
        } catch (e: Exception) {
            return listOf(VideoFpsMode.FPS_30)
        }
    }

    fun queryVideoCapabilities(cameraId: String): VideoCapabilitiesInfo {
        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            
            val qualities = mutableListOf<VideoQualityMode>()
            val descriptions = mutableListOf<String>()

            if (map != null) {
                val sizes = map.getOutputSizes(MediaRecorder::class.java) ?: emptyArray()

                // Standard resolution matching CameraInfoDialog (no 480p)
                if (sizes.any { (it.width == 3840 && it.height == 2160) || (it.width == 2160 && it.height == 3840) }) {
                    qualities.add(VideoQualityMode.UHD)
                }
                if (sizes.any { (it.width == 2560 && it.height == 1440) || (it.width == 1440 && it.height == 2560) }) {
                    qualities.add(VideoQualityMode.QHD)
                }
                if (sizes.any { (it.width == 1920 && it.height == 1080) || (it.width == 1080 && it.height == 1920) }) {
                    qualities.add(VideoQualityMode.FHD)
                }
                if (sizes.any { (it.width == 1280 && it.height == 720) || (it.width == 720 && it.height == 1280) }) {
                    qualities.add(VideoQualityMode.HD)
                }

                if (qualities.isEmpty()) {
                    qualities.addAll(listOf(VideoQualityMode.QHD, VideoQualityMode.FHD, VideoQualityMode.HD))
                }

                for (q in qualities) {
                    val resStr = when (q) {
                        VideoQualityMode.UHD -> "4K UHD"
                        VideoQualityMode.QHD -> "2560x1440"
                        VideoQualityMode.FHD -> "1080p"
                        VideoQualityMode.HD -> "720p"
                        VideoQualityMode.SD -> "480p"
                    }
                    val fpsModes = getSupportedFpsForQuality(cameraId, q)
                    val fpsListStr = fpsModes.joinToString(", ") { "${it.fps}" }
                    descriptions.add("$resStr @ $fpsListStr fps")
                }
            }

            val initialQuality = qualities.firstOrNull() ?: VideoQualityMode.HD
            val initialFpsModes = getSupportedFpsForQuality(cameraId, initialQuality)

            return VideoCapabilitiesInfo(qualities, initialFpsModes, descriptions)
        } catch (e: Exception) {
            return VideoCapabilitiesInfo(
                listOf(VideoQualityMode.QHD, VideoQualityMode.FHD, VideoQualityMode.HD),
                listOf(VideoFpsMode.FPS_30, VideoFpsMode.FPS_60, VideoFpsMode.FPS_120),
                listOf("2560x1440 @ 30 fps", "1080p @ 30 fps", "720p @ 30, 60, 120 fps")
            )
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            _cameraState.value = CameraState.Opened(camera)
        }

        override fun onDisconnected(camera: CameraDevice) {
            closeCamera()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            closeCamera()
            _cameraState.value = CameraState.Error(error)
        }
    }

    fun setupImageReader(width: Int = 1920, height: Int = 1080) {
        imageReader?.close()
        imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2)
        
        analysisImageReader?.close()
        analysisImageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2)
    }

    private var lastRecordedWidth = 1920
    private var lastRecordedHeight = 1080
    private var lastRecordedFps = 30
    private var lastAudioEnabled = true
    var lastOutputFile: String? = null
    private var currentDeviceOrientationDegrees: Int = 0

    fun setDeviceOrientation(degrees: Int) {
        currentDeviceOrientationDegrees = degrees
    }

    fun getOrientationHint(): Int {
        val deviceId = cameraDevice?.id ?: return 90
        val chars = try {
            cameraManager.getCameraCharacteristics(deviceId)
        } catch (e: Exception) {
            return 90
        }
        val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val isFront = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT

        return if (isFront) {
            (sensorOrientation + currentDeviceOrientationDegrees) % 360
        } else {
            (sensorOrientation - currentDeviceOrientationDegrees + 360) % 360
        }
    }

    fun setupMediaRecorder(width: Int = 1920, height: Int = 1080, fps: Int = 30, audioEnabled: Boolean = true, outputFile: String) {
        lastRecordedWidth = width
        lastRecordedHeight = height
        lastRecordedFps = fps
        lastAudioEnabled = audioEnabled
        lastOutputFile = outputFile

        try {
            val pSurface = getOrCreatePersistentSurface()
            mediaRecorder?.release()
            @Suppress("DEPRECATION")
            mediaRecorder = MediaRecorder().apply {
                if (audioEnabled) setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(outputFile)
                setVideoEncodingBitRate(if (fps >= 60) 25000000 else 10000000)
                setVideoFrameRate(fps)
                if (fps > 30) {
                    setCaptureRate(fps.toDouble())
                }
                setVideoSize(width, height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                if (audioEnabled) setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                val hint = getOrientationHint()
                setOrientationHint(hint)
                Log.d("EVCAM", "MediaRecorder orientationHint=$hint device=$currentDeviceOrientationDegrees size=${width}x${height}")
                setInputSurface(pSurface)
                prepare()
            }
            Log.d("EVCAM", "MediaRecorder with persistent surface successfully prepared: ${width}x${height} @ ${fps}fps")
        } catch (e: Exception) {
            Log.e("EVCAM", "Failed setupMediaRecorder (${width}x${height} @ ${fps}fps), attempting fallback 1080p 30fps", e)
            try {
                val pSurface = getOrCreatePersistentSurface()
                mediaRecorder?.release()
                @Suppress("DEPRECATION")
                mediaRecorder = MediaRecorder().apply {
                    if (audioEnabled) setAudioSource(MediaRecorder.AudioSource.MIC)
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setOutputFile(outputFile)
                    setVideoEncodingBitRate(8000000)
                    setVideoFrameRate(30)
                    setVideoSize(1920, 1080)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    if (audioEnabled) setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    val hint = getOrientationHint()
                    setOrientationHint(hint)
                    setInputSurface(pSurface)
                    prepare()
                }
                Log.d("EVCAM", "Fallback MediaRecorder 1080p 30fps successfully prepared")
            } catch (e2: Exception) {
                Log.e("EVCAM", "Fallback MediaRecorder setup failed completely", e2)
            }
        }
    }

    private fun rePrepareMediaRecorder() {
        val file = lastOutputFile ?: return
        val recorder = mediaRecorder ?: return
        try {
            if (lastAudioEnabled) recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setOutputFile(file)
            recorder.setVideoEncodingBitRate(10000000)
            recorder.setVideoFrameRate(lastRecordedFps)
            recorder.setVideoSize(lastRecordedWidth, lastRecordedHeight)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (lastAudioEnabled) recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.prepare()
            Log.d("EVCAM", "MediaRecorder successfully re-prepared for next recording")
        } catch (e: Exception) {
            Log.e("EVCAM", "Failed re-preparing MediaRecorder, attempting fallback setup", e)
            setupMediaRecorder(lastRecordedWidth, lastRecordedHeight, lastRecordedFps, lastAudioEnabled, file)
        }
    }

    fun enableStabilization(builder: CaptureRequest.Builder, eisEnabled: Boolean = false) {
        val deviceId = cameraDevice?.id ?: return
        try {
            val chars = cameraManager.getCameraCharacteristics(deviceId)
            
            // Hardware OIS is always automatically enabled if available on sensor
            val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            if (oisModes?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true) {
                builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
            }
            
            // EIS Video Stabilization is toggled by user preference
            val videoStabModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            if (videoStabModes != null) {
                val targetMode = if (eisEnabled && videoStabModes.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)) {
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                } else {
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                }
                builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, targetMode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createPreviewSession(targets: List<Surface>, onConfigured: (CameraCaptureSession) -> Unit) {
        val device = cameraDevice ?: return
        try {
            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder?.let { enableStabilization(it) }
            if (targets.isNotEmpty()) {
                currentPreviewSurface = targets[0]
            }
            currentPreviewSurface?.let { previewRequestBuilder?.addTarget(it) }
            analysisImageReader?.surface?.let { previewRequestBuilder?.addTarget(it) }
            
            @Suppress("DEPRECATION")
            device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session
                    updatePreview()
                    onConfigured(session)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("Camera2Engine", "Failed to configure capture session")
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }
    
    var isAfTriggered = false
    var onAfStateCallback: ((Int) -> Unit)? = null
    var onAwbGainsCallback: ((Float) -> Unit)? = null

    private val repeatingCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            if (afState != null) {
                onAfStateCallback?.invoke(afState)
            }
            // Read active AWB color correction gains from sensor metadata to calculate live Kelvin
            val gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS)
            if (gains != null && gains.red > 0f && gains.blue > 0f) {
                // Approximate color temperature from red/blue channel gains ratio
                val ratio = gains.red / gains.blue
                // Empirical Kelvin curve estimation: ratio ~ 0.5 -> 7500K, ratio ~ 1.0 -> 5500K, ratio ~ 2.0 -> 3000K
                val estimatedKelvin = (5500f / (ratio.toDouble().pow(0.8))).toFloat().coerceIn(2000f, 10000f)
                onAwbGainsCallback?.invoke(estimatedKelvin)
            }


        }
    }


    fun updatePreview() {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        try {
            session.setRepeatingRequest(builder.build(), repeatingCaptureCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }
    
    fun setZoomRatio(zoomRatio: Float) {
        val chars = cameraManager.getCameraCharacteristics(cameraDevice?.id ?: return)
        val sensorRect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        
        val centerX = sensorRect.centerX()
        val centerY = sensorRect.centerY()
        val deltaX = (0.5f * sensorRect.width() / zoomRatio).toInt()
        val deltaY = (0.5f * sensorRect.height() / zoomRatio).toInt()
        
        val cropRegion = Rect(
            centerX - deltaX,
            centerY - deltaY,
            centerX + deltaX,
            centerY + deltaY
        )
        previewRequestBuilder?.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
        updatePreview()
    }
    
    fun focusAt(x: Float, y: Float, width: Float, height: Float) {
        isAfTriggered = true
        val chars = cameraManager.getCameraCharacteristics(cameraDevice?.id ?: return)
        val sensorRect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        
        val cropRegion = previewRequestBuilder?.get(CaptureRequest.SCALER_CROP_REGION) ?: sensorRect
        
        val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val isFront = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT

        val normalizedX: Float
        val normalizedY: Float

        when (sensorOrientation) {
            90 -> {
                normalizedX = y / height
                normalizedY = if (isFront) x / width else 1f - (x / width)
            }
            270 -> {
                normalizedX = 1f - (y / height)
                normalizedY = if (isFront) 1f - (x / width) else x / width
            }
            180 -> {
                normalizedX = 1f - (x / width)
                normalizedY = 1f - (y / height)
            }
            else -> {
                normalizedX = if (isFront) 1f - (x / width) else x / width
                normalizedY = y / height
            }
        }

        val mappedX = cropRegion.left + (normalizedX.coerceIn(0f, 1f)) * cropRegion.width()
        val mappedY = cropRegion.top + (normalizedY.coerceIn(0f, 1f)) * cropRegion.height()

        Log.d("EVCAM_TOUCH", "Touch ($x, $y) on View ($width x $height) -> Sensor Mapped ($mappedX, $mappedY) Orient: $sensorOrientation")
        
        val halfTouchWidth = 150
        val halfTouchHeight = 150
        
        val focusRect = Rect(
            max(cropRegion.left, (mappedX - halfTouchWidth).toInt()),
            max(cropRegion.top, (mappedY - halfTouchHeight).toInt()),
            min(cropRegion.right, (mappedX + halfTouchWidth).toInt()),
            min(cropRegion.bottom, (mappedY + halfTouchHeight).toInt())
        )
        
        val meteringRectangle = MeteringRectangle(focusRect, MeteringRectangle.METERING_WEIGHT_MAX)
        
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRectangle))
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRectangle))
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
        
        updatePreview()
        
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
    }

    fun resetFocusToContinuous() {
        if (isRecordingVideo) {
            try {
                previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_REGIONS, null)
                previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_REGIONS, null)
                previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                updatePreview()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setExposureCompensation(index: Int) {
        val builder = previewRequestBuilder ?: return
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, index)
        updatePreview()
    }
    
    fun setProSettings(
        isProMode: Boolean,
        isIsoAuto: Boolean, iso: Int,
        isShutterAuto: Boolean, shutterSpeed: Long,
        isFocusAuto: Boolean, focusDistance: Float,
        whiteBalance: Int, manualKelvin: Int
    ) {
        val builder = previewRequestBuilder ?: return
        if (isProMode) {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)

            if (isIsoAuto && isShutterAuto) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            } else {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
                // Cap preview shutter speed to max 0.5s (500ms) for smooth live viewfinder
                val previewShutter = minOf(shutterSpeed, 500_000_000L)
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, previewShutter)
            }




            if (isFocusAuto) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
            }

            builder.set(CaptureRequest.CONTROL_AWB_MODE, whiteBalance)
            if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_OFF) {
                builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                val temp = manualKelvin / 100.0f
                val r: Float
                val g: Float
                val b_val: Float
                if (temp <= 66.0f) {
                    r = 255.0f
                    g = (99.4708025861f * Math.log(temp.toDouble()).toFloat() - 161.1195681661f).coerceIn(0f, 255f)
                    b_val = if (temp <= 19.0f) 0.0f else (138.5177312231f * Math.log(temp.toDouble() - 10.0).toFloat() - 305.0447927307f).coerceIn(0f, 255f)
                } else {
                    r = (329.698727446f * Math.pow(temp.toDouble() - 60.0, -0.1332047592).toFloat()).coerceIn(0f, 255f)
                    g = (288.1221695283f * Math.pow(temp.toDouble() - 60.0, -0.0755148492).toFloat()).coerceIn(0f, 255f)
                    b_val = 255.0f
                }
                val rGain = (255f / r).coerceIn(1f, 3.5f)
                val gGain = (255f / g).coerceIn(1f, 3.5f)
                val bGain = (255f / b_val).coerceIn(1f, 3.5f)
                builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, android.hardware.camera2.params.RggbChannelVector(rGain, gGain, gGain, bGain))
            } else {
                builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_FAST)
            }
        } else {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_FAST)
        }
        updatePreview()
    }

    
    fun setTorchState(enabled: Boolean) {
        val builder = previewRequestBuilder ?: return
        if (enabled) {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        } else {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }
        updatePreview()
    }
    
    fun setSceneMode(isNightMode: Boolean, isHdrMode: Boolean) {
        val builder = previewRequestBuilder ?: return
        if (isNightMode) {
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_NIGHT)
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
        } else if (isHdrMode) {
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HDR)
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
        } else {
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        }
        updatePreview()
    }

    fun takePhoto(flashMode: FlashMode, onImageCaptured: (Image) -> Unit) {
        Log.d("EVCAM", "takePhoto() called, cameraDevice=$cameraDevice, imageReader=$imageReader, captureSession=$captureSession")
        val device = cameraDevice ?: run { Log.e("EVCAM", "cameraDevice is null"); return }
        val reader = imageReader ?: run { Log.e("EVCAM", "imageReader is null"); return }
        val session = captureSession ?: run { Log.e("EVCAM", "captureSession is null"); return }

        reader.setOnImageAvailableListener({ ir ->
            Log.d("EVCAM", "imageReader onImageAvailable fired!")
            val image = ir.acquireLatestImage()
            if (image != null) {
                ir.setOnImageAvailableListener(null, null)
                onImageCaptured(image)
            } else {
                Log.e("EVCAM", "acquireLatestImage returned null!")
            }
        }, backgroundHandler)

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(reader.surface)
            currentPreviewSurface?.let { captureBuilder.addTarget(it) }
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, 95.toByte())
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientationHint())
            
            // Copy settings from previewRequestBuilder
            val cropRegion = previewRequestBuilder?.get(CaptureRequest.SCALER_CROP_REGION)
            if (cropRegion != null) {
                captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
            }
            
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, previewRequestBuilder?.get(CaptureRequest.CONTROL_AF_MODE) ?: CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            
            val controlMode = previewRequestBuilder?.get(CaptureRequest.CONTROL_MODE)
            if (controlMode != null) captureBuilder.set(CaptureRequest.CONTROL_MODE, controlMode)
            
            val sceneMode = previewRequestBuilder?.get(CaptureRequest.CONTROL_SCENE_MODE)
            if (sceneMode != null) captureBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, sceneMode)
            
            when (flashMode) {
                FlashMode.AUTO -> captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                FlashMode.ON -> captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                FlashMode.OFF -> {
                    captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
            }
            
            val isoVal = previewRequestBuilder?.get(CaptureRequest.SENSOR_SENSITIVITY)
            if (isoVal != null) captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoVal)
            
            val shutterVal = previewRequestBuilder?.get(CaptureRequest.SENSOR_EXPOSURE_TIME)
            if (shutterVal != null) captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterVal)
            
            val focusDist = previewRequestBuilder?.get(CaptureRequest.LENS_FOCUS_DISTANCE)
            if (focusDist != null) captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDist)
            
            enableStabilization(captureBuilder)
            Log.d("EVCAM", "Sending capture STILL_CAPTURE request to session")
            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    Log.d("EVCAM", "STILL_CAPTURE onCaptureCompleted!")
                }
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    Log.e("EVCAM", "STILL_CAPTURE onCaptureFailed: reason=${failure.reason}")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            reader.setOnImageAvailableListener(null, null)
            Log.e("EVCAM", "Exception during takePhoto", e)
        }
    }

    fun takeBurst(burstCount: Int, flashMode: FlashMode, onImageCaptured: (Image) -> Unit) {
        val device = cameraDevice ?: return
        val reader = imageReader ?: return
        val session = captureSession ?: return
        var receivedCount = 0

        reader.setOnImageAvailableListener({ ir ->
            val image = ir.acquireNextImage()
            if (image != null) {
                receivedCount++
                if (receivedCount >= burstCount) {
                    ir.setOnImageAvailableListener(null, null)
                }
                onImageCaptured(image)
            }
        }, backgroundHandler)

        try {
            val requests = mutableListOf<CaptureRequest>()
            for (i in 0 until burstCount) {
                val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                captureBuilder.addTarget(reader.surface)
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientationHint())
                
                val cropRegion = previewRequestBuilder?.get(CaptureRequest.SCALER_CROP_REGION)
                if (cropRegion != null) captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
                
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, previewRequestBuilder?.get(CaptureRequest.CONTROL_AF_MODE) ?: CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                enableStabilization(captureBuilder)
                requests.add(captureBuilder.build())
            }
            session.captureBurst(requests, null, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun startRecording(onStarted: () -> Unit) {
        if (isRecordingVideo) return
        val device = cameraDevice ?: run { Log.e("EVCAM", "startRecording failed: cameraDevice is null"); return }
        val session = captureSession ?: run { Log.e("EVCAM", "startRecording failed: captureSession is null"); return }
        val pSurface = persistentSurface ?: run { Log.e("EVCAM", "startRecording failed: persistentSurface is null"); return }

        try {
            val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            enableStabilization(requestBuilder)
            
            try {
                val activeId = device.id
                val chars = cameraManager.getCameraCharacteristics(activeId)
                val availableRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()

                var targetRange: android.util.Range<Int>? = null
                for (r in availableRanges) {
                    if (r.upper == lastRecordedFps) {
                        targetRange = r
                        break
                    }
                }
                if (targetRange == null) {
                    for (r in availableRanges) {
                        if (r.upper in (lastRecordedFps - 5)..(lastRecordedFps + 5)) {
                            targetRange = r
                            break
                        }
                    }
                }
                if (targetRange == null && availableRanges.isNotEmpty()) {
                    targetRange = availableRanges.maxByOrNull { it.upper }
                }

                if (targetRange != null) {
                    Log.d("EVCAM", "Setting CONTROL_AE_TARGET_FPS_RANGE to $targetRange for $lastRecordedFps fps recording")
                    requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetRange)
                }
            } catch (e: Exception) {
                Log.e("EVCAM", "Failed setting AE_TARGET_FPS_RANGE", e)
            }
            
            val previewSurface = currentPreviewSurface
            if (previewSurface != null) requestBuilder.addTarget(previewSurface)
            requestBuilder.addTarget(pSurface)
            
            previewRequestBuilder = requestBuilder
            
            session.setRepeatingRequest(requestBuilder.build(), repeatingCaptureCallback, backgroundHandler)
            mediaRecorder?.start()
            isRecordingVideo = true
            Log.d("EVCAM", "MediaRecorder started recording successfully")
            onStarted()
        } catch (e: Exception) {
            Log.e("EVCAM", "Exception during startRecording", e)
        }
    }

    fun stopRecording(onStopped: () -> Unit) {
        if (!isRecordingVideo) return
        try {
            Log.d("EVCAM", "Stopping MediaRecorder...")
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecordingVideo = false

            // Re-create preview request builder targeting only previewSurface
            val device = cameraDevice
            val pSurface = currentPreviewSurface
            if (device != null && pSurface != null) {
                previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(pSurface)
                }
            }

            // Execute callback to copy temp file and generate thumbnail
            onStopped()

            // Setup new temp file for subsequent recording
            val nextFile = File(context.cacheDir, "temp_vid_${System.currentTimeMillis()}.mp4").absolutePath
            Log.d("EVCAM", "Re-setup MediaRecorder for next recording to file $nextFile")
            setupMediaRecorder(lastRecordedWidth, lastRecordedHeight, lastRecordedFps, lastAudioEnabled, nextFile)

            updatePreview()
        } catch (e: Exception) {
            Log.e("EVCAM", "Exception during stopRecording", e)
            isRecordingVideo = false
            updatePreview()
        }
    }
}
