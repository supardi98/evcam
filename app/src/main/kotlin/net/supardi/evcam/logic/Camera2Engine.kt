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
    var isTouchFocusActive = false

    fun getCameraIdForFacing(lensFacing: Int): String {
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == lensFacing) {
                    return id
                }
            }
        } catch (e: Exception) {}
        return "0"
    }
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
    
    var isUltraMode = false
    
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

    fun setupImageReader(width: Int = 1920, height: Int = 1080, streamWidth: Int = 1280, streamHeight: Int = 720) {
        imageReader?.close()
        imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 4)
        
        analysisImageReader?.close()
        analysisImageReader = ImageReader.newInstance(streamWidth, streamHeight, ImageFormat.YUV_420_888, 4)
    }

    fun getSupportedStreamResolutions(cameraId: String = "0", aspectRatio: String = "ALL", useMaximumResolution: Boolean = false): List<Triple<String, Int, Int>> {
        try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val map = if (useMaximumResolution && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION) ?: chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            } else {
                chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            } ?: return defaultResolutions()
            
            val sizes = map.getOutputSizes(android.graphics.ImageFormat.JPEG) ?: map.getOutputSizes(android.graphics.ImageFormat.YUV_420_888) ?: return defaultResolutions()

            val list = mutableListOf<Triple<String, Int, Int>>()
            val sortedSizes = sizes.sortedByDescending { it.width * it.height }

            sortedSizes.forEach { s ->
                val w = maxOf(s.width, s.height)
                val h = minOf(s.width, s.height)
                val ratio = w.toFloat() / h.toFloat()

                val matchesRatio = when (aspectRatio) {
                    "RATIO_16_9" -> kotlin.math.abs(ratio - (16f / 9f)) < 0.05f
                    "RATIO_4_3" -> kotlin.math.abs(ratio - (4f / 3f)) < 0.05f
                    "RATIO_1_1" -> kotlin.math.abs(ratio - 1.0f) < 0.05f
                    else -> true
                }

                if (matchesRatio) {
                    val label = when {
                        w == 3840 && h == 2160 -> "4K (16:9)"
                        w == 2560 && h == 1440 -> "2.5K (16:9)"
                        w == 1920 && h == 1080 -> "1080p (16:9)"
                        w == 1280 && h == 720 -> "720p (16:9)"
                        w == 1440 && h == 1080 -> "1080p (4:3)"
                        w == 1080 && h == 1080 -> "1080p (1:1)"
                        w == 640 && h == 480 -> "480p (4:3)"
                        else -> "${w}x${h}"
                    }
                    if (list.none { it.second == w && it.third == h }) {
                        list.add(Triple(label, w, h))
                    }
                }
            }

            return if (list.isNotEmpty()) list else defaultResolutions()
        } catch (e: Exception) {
            return defaultResolutions()
        }
    }

    private fun defaultResolutions(): List<Triple<String, Int, Int>> {
        return listOf(
            Triple("4K (2160p)", 3840, 2160),
            Triple("2.5K (1440p)", 2560, 1440),
            Triple("1080p (FHD)", 1920, 1080),
            Triple("720p (HD)", 1280, 720),
            Triple("480p (SD)", 640, 480)
        )
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
    var isHorizonLockEnabled: Boolean = false

    fun getOrientationHint(): Int {
        if (isHorizonLockEnabled) return 0
        
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

    fun setupMediaRecorder(width: Int = 1920, height: Int = 1080, fps: Int = 30, audioEnabled: Boolean = true, outputFile: String): Pair<Int, Int> {
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
                setVideoEncodingBitRate(if (fps >= 120) 40000000 else if (fps >= 60) 25000000 else 10000000)
                if (fps >= 120) {
                    // Slow motion: capture at high fps but tell player to play at 30fps
                    setVideoFrameRate(30)
                    setCaptureRate(fps.toDouble())
                } else {
                    setVideoFrameRate(fps)
                    if (fps > 30) setCaptureRate(fps.toDouble())
                }
                setVideoSize(width, height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                if (audioEnabled && fps < 120) setAudioEncoder(MediaRecorder.AudioEncoder.AAC) // no audio in slow motion
                val hint = getOrientationHint()
                setOrientationHint(hint)
                Log.d("EVCAM", "MediaRecorder orientationHint=$hint device=$currentDeviceOrientationDegrees size=${width}x${height}")
                setInputSurface(pSurface)
                prepare()
            }
            Log.d("EVCAM", "MediaRecorder with persistent surface successfully prepared: ${width}x${height} @ ${fps}fps")
            return Pair(width, height)
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
                lastRecordedWidth = 1920
                lastRecordedHeight = 1080
                return Pair(1920, 1080)
            } catch (e2: Exception) {
                Log.e("EVCAM", "Fallback MediaRecorder setup failed completely", e2)
                return Pair(width, height)
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
            val hint = getOrientationHint()
            recorder.setOrientationHint(hint)
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

    fun createPreviewSession(targets: List<Surface>, targetFps: Int = 30, onConfigured: (CameraCaptureSession) -> Unit) {
        val device = cameraDevice ?: return
        try {
            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder?.let { enableStabilization(it) }

            // Apply target FPS range (e.g. 60 FPS, 30 FPS, 24 FPS)
            if (targetFps > 0) {
                previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(targetFps, targetFps))
            }

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
                    isHighSpeedSession = false
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

    var isHighSpeedSession = false
        private set

    fun createHighSpeedSession(
        previewSurface: Surface,
        recordSurface: Surface,
        fps: Int,
        shutterSpeedNs: Long = 0L, // 0 = auto; >0 = manual (will be capped to 1/fps)
        onConfigured: (CameraCaptureSession) -> Unit,
        onFailed: () -> Unit
    ) {
        val device = cameraDevice ?: return
        // Max allowed exposure at this fps (1 frame period in nanoseconds)
        val maxExposureNs = 1_000_000_000L / fps
        // 180-degree rule: recommended exposure = 1/(fps*2)
        val recommendedExposureNs = maxExposureNs / 2
        // Actual exposure to use: cap manual shutter; use 180-rule for auto
        val effectiveShutterNs = when {
            shutterSpeedNs > 0 -> shutterSpeedNs.coerceAtMost(maxExposureNs)
            else -> recommendedExposureNs
        }
        Log.d("EVCAM", "SlowMo shutter: fps=$fps maxExposure=${maxExposureNs}ns effective=${effectiveShutterNs}ns")
        try {
            currentPreviewSurface = previewSurface
            val targets = listOf(previewSurface, recordSurface)

            @Suppress("DEPRECATION")
            device.createConstrainedHighSpeedCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session
                    isHighSpeedSession = true

                    // Build high speed preview request
                    try {
                        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                        builder.addTarget(previewSurface)
                        builder.addTarget(recordSurface)
                        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(fps, fps))
                        // Enforce shutter speed: disable AE and set manual exposure + auto ISO
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, effectiveShutterNs)
                        // Use a reasonable ISO; ideally read from last AE estimate but default to 400
                        val isoToUse = try {
                            val chars = cameraManager.getCameraCharacteristics(device.id)
                            val range = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                            range?.clamp(400) ?: 400
                        } catch (e: Exception) { 400 }
                        builder.set(CaptureRequest.SENSOR_SENSITIVITY, isoToUse)
                        previewRequestBuilder = builder

                        val highSpeedSession = session as android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
                        val burstRequests = highSpeedSession.createHighSpeedRequestList(builder.build())
                        session.setRepeatingBurst(burstRequests, null, backgroundHandler)
                        Log.d("EVCAM", "High speed session configured at ${fps}fps with ${burstRequests.size} burst requests")
                        onConfigured(session)
                    } catch (e: Exception) {
                        Log.e("EVCAM", "Failed to set up high speed repeating burst", e)
                        onFailed()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("EVCAM", "Failed to configure high speed capture session")
                    onFailed()
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("EVCAM", "createConstrainedHighSpeedCaptureSession failed", e)
            onFailed()
        }
    }

    fun startHighSpeedRecording(recordSurface: Surface, fps: Int, onStarted: () -> Unit) {
        if (isRecordingVideo) return
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            val previewSurface = currentPreviewSurface
            if (previewSurface != null) builder.addTarget(previewSurface)
            builder.addTarget(recordSurface)
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(fps, fps))
            previewRequestBuilder = builder

            val highSpeedSession = session as android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
            val burstRequests = highSpeedSession.createHighSpeedRequestList(builder.build())
            session.setRepeatingBurst(burstRequests, null, backgroundHandler)
            mediaRecorder?.start()
            isRecordingVideo = true
            Log.d("EVCAM", "High speed MediaRecorder started at ${fps}fps")
            onStarted()
        } catch (e: Exception) {
            Log.e("EVCAM", "Exception during startHighSpeedRecording", e)
        }
    }

    fun getSupportedHighSpeedSizes(cameraId: String): List<Pair<Int, Int>> {
        return try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return emptyList()
            map.highSpeedVideoSizes?.map { Pair(it.width, it.height) } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }
    
    var isAfTriggered = false
    var onAfStateCallback: ((Int) -> Unit)? = null
    var onAwbGainsCallback: ((Float) -> Unit)? = null
    var lastLiveGains: android.hardware.camera2.params.RggbChannelVector? = null

    var lastTotalCaptureResult: TotalCaptureResult? = null

    private val repeatingCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            lastTotalCaptureResult = result
            super.onCaptureCompleted(session, request, result)
            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            if (afState != null) {
                onAfStateCallback?.invoke(afState)
            }
            // Read active AWB color correction gains from sensor metadata for exact Custom AWB matching
            val gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS)
            if (gains != null && gains.red > 0f && gains.blue > 0f) {
                lastLiveGains = gains
                // Invert formula: rGain = 2.5 - 1.5*norm, bGain = 1.0 + 2.0*norm
                val normR = ((2.5f - gains.red) / 1.5f).coerceIn(0f, 1f)
                val normB = ((gains.blue - 1.0f) / 2.0f).coerceIn(0f, 1f)
                val avgNorm = (normR + normB) / 2.0f
                val estimatedKelvin = (2000f + avgNorm * 8000f).coerceIn(2000f, 10000f)
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
        } catch (e: IllegalStateException) {
            // Session is closed, ignore
            android.util.Log.e("EVCAM", "Session closed during updatePreview", e)
        } catch (e: Exception) {
            android.util.Log.e("EVCAM", "Error during updatePreview", e)
        }
    }

    fun pausePreview() {
        try {
            captureSession?.stopRepeating()
        } catch (e: Exception) {
            android.util.Log.e("EVCAM", "Error pausing preview", e)
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
        isTouchFocusActive = true
        isAfTriggered = true
        val chars = cameraManager.getCameraCharacteristics(cameraDevice?.id ?: return)
        val sensorRect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        
        val cropRegion = previewRequestBuilder?.get(CaptureRequest.SCALER_CROP_REGION) ?: sensorRect
        
        val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val isFront = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT

        val uiPt = floatArrayOf(x / width, y / height)
        val matrix = android.graphics.Matrix()
        // Rotate the normalized UI coordinates back to the sensor coordinate space
        matrix.postRotate(-sensorOrientation.toFloat(), 0.5f, 0.5f)
        if (isFront) {
            // Front camera preview is mirrored horizontally
            matrix.postScale(-1f, 1f, 0.5f, 0.5f)
        }
        matrix.mapPoints(uiPt)
        
        val normalizedX = uiPt[0].coerceIn(0f, 1f)
        val normalizedY = uiPt[1].coerceIn(0f, 1f)

        val mappedX = cropRegion.left + (normalizedX.coerceIn(0f, 1f)) * cropRegion.width()
        val mappedY = cropRegion.top + (normalizedY.coerceIn(0f, 1f)) * cropRegion.height()

        Log.d("EVCAM_TOUCH", "Touch ($x, $y) on View ($width x $height) -> Sensor Mapped ($mappedX, $mappedY) Orient: $sensorOrientation")
        
        val halfTouchWidth = (cropRegion.width() * 0.08f).toInt().coerceAtLeast(150)
        val halfTouchHeight = (cropRegion.height() * 0.08f).toInt().coerceAtLeast(150)
        
        val focusRect = Rect(
            max(cropRegion.left, (mappedX - halfTouchWidth).toInt()),
            max(cropRegion.top, (mappedY - halfTouchHeight).toInt()),
            min(cropRegion.right, (mappedX + halfTouchWidth).toInt()),
            min(cropRegion.bottom, (mappedY + halfTouchHeight).toInt())
        )
        
        val meteringRectangle = MeteringRectangle(focusRect, MeteringRectangle.METERING_WEIGHT_MAX)
        val builder = previewRequestBuilder ?: return
        
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRectangle))
        builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meteringRectangle))

        try {
            captureSession?.stopRepeating()
        } catch (e: Exception) { }

        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
        try {
            captureSession?.capture(builder.build(), repeatingCaptureCallback, backgroundHandler)
        } catch (e: Exception) { }

        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
        try {
            captureSession?.capture(builder.build(), repeatingCaptureCallback, backgroundHandler)
        } catch (e: Exception) {
            Log.e("EVCAM", "Failed to trigger AF", e)
        }
        
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        updatePreview()
    }

    fun resetFocusToContinuous() {
        isTouchFocusActive = false
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
        whiteBalance: Int, manualKelvin: Int,
        activeCustomScene: CustomSceneMode
    ) {
        val builder = previewRequestBuilder ?: return

        // 1. First, apply the Custom Scene Mode base template
        if (activeCustomScene != CustomSceneMode.AUTO) {
            applyCustomSceneModeInternal(activeCustomScene, update = false)
        } else {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            if (!isTouchFocusActive) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_FAST)
        }

        // 2. Then, override with manual Pro Mode settings if explicitly set and not locked by scene
        if (isProMode) {
            // Override Exposure
            if (!isIsoAuto || !isShutterAuto) {
                if (!activeCustomScene.lockIso && !activeCustomScene.lockShutter) {
                    builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    if (!isIsoAuto) builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
                    if (!isShutterAuto) {
                        val previewShutter = minOf(shutterSpeed, 500_000_000L)
                        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, previewShutter)
                    }
                }
            }

            // Override Focus
            if (!isFocusAuto) {
                if (!activeCustomScene.lockFocus) {
                    builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                    builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
                }
            }

            // Override White Balance
            if (whiteBalance != CaptureRequest.CONTROL_AWB_MODE_AUTO) {
                if (!activeCustomScene.lockWhiteBalance) {
                    builder.set(CaptureRequest.CONTROL_AWB_MODE, whiteBalance)
                    if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_OFF) {
                        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                        val baseGains = lastLiveGains
                        val baseR = baseGains?.red ?: 1.8f
                        val baseB = baseGains?.blue ?: 1.8f
                        val baseG1 = baseGains?.greenEven ?: 1.0f
                        val baseG2 = baseGains?.greenOdd ?: 1.0f

                        val factor = (manualKelvin / 5500f).coerceIn(0.4f, 2.2f)
                        val rGain = (baseR / factor).coerceIn(1.0f, 4.0f)
                        val bGain = (baseB * factor).coerceIn(1.0f, 4.0f)
                        builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, android.hardware.camera2.params.RggbChannelVector(rGain, baseG1, baseG2, bGain))
                    } else {
                        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_FAST)
                    }
                }
            }
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

    fun applyCustomSceneMode(sceneMode: CustomSceneMode) {
        applyCustomSceneModeInternal(sceneMode, update = true)
    }

    private fun applyCustomSceneModeInternal(sceneMode: CustomSceneMode, update: Boolean) {
        val builder = previewRequestBuilder ?: return
        
        // Reset effects and modes by default to prevent leakage between scenes
        builder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_OFF)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0)
        
        when (sceneMode) {
            CustomSceneMode.AUTO -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            }
            CustomSceneMode.NIGHT -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_NIGHT)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 2) // +0.7 EV gain
            }
            CustomSceneMode.SUNSET -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 2) // +0.7 EV gain for golden glow
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT)
            }
            CustomSceneMode.ACTION -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_SPORTS)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            CustomSceneMode.PORTRAIT -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_PORTRAIT)
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            }
            CustomSceneMode.LANDSCAPE -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_LANDSCAPE)
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -1) // -0.3 EV for deep colors
            }
            CustomSceneMode.DOCUMENT -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 1)
                builder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_MONO)
            }
            CustomSceneMode.MACRO -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                val deviceId = cameraDevice?.id
                if (deviceId != null) {
                    try {
                        val chars = cameraManager.getCameraCharacteristics(deviceId)
                        val minDist = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
                        if (minDist > 0f) {
                            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, minDist) // Lock to minimum focus distance
                        }
                    } catch (e: Exception) {}
                }
            }
            CustomSceneMode.FIREWORKS -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, 100) // Lock ISO 100
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 500_000_000L) // 0.5s for smooth preview
            }
            CustomSceneMode.BACKLIGHT -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HDR)
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 3) // +1.0 EV
            }
            CustomSceneMode.CANDLELIGHT -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT)
            }
            CustomSceneMode.SNOW_BEACH -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 3) // +1.0 EV compensation
            }
            CustomSceneMode.ASTRO_LONG_EXP -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, 800) // Optimal Astro ISO
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 500_000_000L) // 0.5s for live preview, 10s-30s on capture
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f) // Lock to infinity focus for night sky
            }
            CustomSceneMode.FOOD -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 1) // +0.3 EV compensation
            }
            CustomSceneMode.CONCERT -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_SPORTS) // Sports mode for fast shutter bias
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -5) // -1.5 EV compensation
            }
            CustomSceneMode.WATERFALL -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, 50) // Absolute minimum ISO
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 250_000_000L) // 1/4 second shutter
            }
            CustomSceneMode.COMPUTATIONAL_HDR -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            CustomSceneMode.HORIZON_LOCK -> {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            CustomSceneMode.SLOW_MOTION -> {
                // High speed session handles its own capture; no special params needed here
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
        }
        if (update) updatePreview()
    }


    fun capturePhoto(
        isUltraMode: Boolean,
        activeCustomScene: CustomSceneMode,
        flashMode: FlashMode,
        isIsoAuto: Boolean = true,
        iso: Int = 100,
        isShutterAuto: Boolean = true,
        shutterSpeed: Long = 10000000L,
        onImageCaptured: (Image) -> Unit
    ) {
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

            // Explicitly request high quality post-processing for still captures
            // to ensure noise reduction and sharpness are better than the real-time preview
            captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
            captureBuilder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
            captureBuilder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                captureBuilder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY)
                captureBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
            }
            
            // Copy settings from previewRequestBuilder
            val cropRegion = previewRequestBuilder?.get(CaptureRequest.SCALER_CROP_REGION)
            if (cropRegion != null) {
                captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
            }
            
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, previewRequestBuilder?.get(CaptureRequest.CONTROL_AF_MODE) ?: CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            
            if (isUltraMode && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                captureBuilder.set(CaptureRequest.SENSOR_PIXEL_MODE, CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION)
            }
            
            val controlMode = previewRequestBuilder?.get(CaptureRequest.CONTROL_MODE)
            if (controlMode != null) captureBuilder.set(CaptureRequest.CONTROL_MODE, controlMode)
            
            val sceneMode = previewRequestBuilder?.get(CaptureRequest.CONTROL_SCENE_MODE)
            if (sceneMode != null) captureBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, sceneMode)
            
            val focusDist = previewRequestBuilder?.get(CaptureRequest.LENS_FOCUS_DISTANCE)
            if (focusDist != null) captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDist)
            
            val awbMode = previewRequestBuilder?.get(CaptureRequest.CONTROL_AWB_MODE)
            if (awbMode != null) captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, awbMode)
            
            val colorMode = previewRequestBuilder?.get(CaptureRequest.COLOR_CORRECTION_MODE)
            if (colorMode != null) captureBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, colorMode)
            
            val colorGains = previewRequestBuilder?.get(CaptureRequest.COLOR_CORRECTION_GAINS)
            if (colorGains != null) captureBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, colorGains)
            
            val colorTransform = previewRequestBuilder?.get(CaptureRequest.COLOR_CORRECTION_TRANSFORM)
            if (colorTransform != null) captureBuilder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, colorTransform)

            val aeMode = previewRequestBuilder?.get(CaptureRequest.CONTROL_AE_MODE)
            if (aeMode != null) captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, aeMode)

            if (aeMode == CaptureRequest.CONTROL_AE_MODE_OFF) {
                if (flashMode == FlashMode.ON) {
                    captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                } else {
                    captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
            } else {
                when (flashMode) {
                    FlashMode.AUTO -> captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    FlashMode.ON -> captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                    FlashMode.OFF -> {
                        captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    }
                }
            }
            
            val isoVal = previewRequestBuilder?.get(CaptureRequest.SENSOR_SENSITIVITY)
            
            // Apply Manual Exposure overrides explicitly (so true shutter is used, not the clamped preview one)
            if (!isIsoAuto || !isShutterAuto) {
                if (!activeCustomScene.lockIso && !activeCustomScene.lockShutter) {
                    captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    if (!isIsoAuto) captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
                    if (!isShutterAuto) captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterSpeed)
                }
            } else {
                if (isoVal != null) captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoVal)
                val shutterVal = previewRequestBuilder?.get(CaptureRequest.SENSOR_EXPOSURE_TIME)
                if (shutterVal != null) captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterVal)
            }
            
            // For long exposure scenes, apply true capture shutter speed instead of the limited preview shutter speed
            when (activeCustomScene) {
                CustomSceneMode.ASTRO_LONG_EXP -> {
                    captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 10_000_000_000L) // 10 seconds true exposure
                    captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                }
                CustomSceneMode.FIREWORKS -> {
                    captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 2_000_000_000L) // 2 seconds true exposure
                    captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                }
                else -> {
                    // Handled above in the Manual Exposure override
                }
            }
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
        } catch (e: IllegalStateException) {
            Log.e("EVCAM", "Session closed during takeBurst", e)
        } catch (e: Exception) {
            Log.e("EVCAM", "Error during takeBurst", e)
        }
    }

    fun takeComputationalHdrBurst(onImagesCaptured: (List<ByteArray>) -> Unit) {
        val device = cameraDevice ?: return
        val reader = imageReader ?: return
        val session = captureSession ?: return
        var receivedCount = 0
        val imagesList = mutableListOf<ByteArray>()

        reader.setOnImageAvailableListener({ ir ->
            val image = ir.acquireNextImage()
            if (image != null) {
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    imagesList.add(bytes)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    image.close()
                }
                
                receivedCount++
                if (receivedCount >= 5) {
                    ir.setOnImageAvailableListener(null, null)
                    onImagesCaptured(imagesList)
                }
            }
        }, backgroundHandler)

        try {
            val requests = mutableListOf<CaptureRequest>()
            
            val baseIso = lastTotalCaptureResult?.get(CaptureResult.SENSOR_SENSITIVITY) ?: 400
            val baseShutter = lastTotalCaptureResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 30_000_000L // default 30ms

            // EV multipliers: -4 EV, -2 EV, 0 EV, +2 EV, +4 EV (approximate stops)
            val multipliers = listOf(1.0/16.0, 1.0/4.0, 1.0, 4.0, 16.0)

            for (multiplier in multipliers) {
                val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                captureBuilder.addTarget(reader.surface)
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientationHint())
                captureBuilder.set(CaptureRequest.JPEG_QUALITY, 100.toByte())

                val cropRegion = previewRequestBuilder?.get(CaptureRequest.SCALER_CROP_REGION)
                if (cropRegion != null) captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)

                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, previewRequestBuilder?.get(CaptureRequest.CONTROL_AF_MODE) ?: CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                
                // FORCE MANUAL EXPOSURE
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                
                var targetShutter = (baseShutter * multiplier).toLong()
                var targetIso = baseIso
                
                // If shutter exceeds 1/5th second (200,000,000 ns), clamp it and increase ISO instead
                if (targetShutter > 200_000_000L) {
                    val excess = targetShutter.toDouble() / 200_000_000L
                    targetShutter = 200_000_000L
                    targetIso = (targetIso * excess).toInt().coerceAtMost(3200) // clamp max ISO to 3200
                }
                
                // If shutter goes below 1/10000th second (100,000 ns), clamp it
                if (targetShutter < 100_000L) {
                    targetShutter = 100_000L
                }

                captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, targetShutter)
                captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, targetIso)

                enableStabilization(captureBuilder)
                requests.add(captureBuilder.build())
            }
            session.captureBurst(requests, null, backgroundHandler)
        } catch (e: Exception) {
            e.printStackTrace()
            reader.setOnImageAvailableListener(null, null)
        }
    }

    fun startRecording(recordSurface: Surface, onStarted: () -> Unit) {
        if (isRecordingVideo) return
        val device = cameraDevice ?: run { Log.e("EVCAM", "startRecording failed: cameraDevice is null"); return }
        val session = captureSession ?: run { Log.e("EVCAM", "startRecording failed: captureSession is null"); return }

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
            requestBuilder.addTarget(recordSurface)
            
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
