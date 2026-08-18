package net.supardi.evcam.logic

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import java.io.File

enum class StopMotionInterval(val label: String, val ms: Long) {
    MANUAL("Manual", 0L),
    HALF_SEC("0.5s", 500L),
    ONE_SEC("1s", 1000L),
    TWO_SEC("2s", 2000L),
    FIVE_SEC("5s", 5000L),
    TEN_SEC("10s", 10_000L),
    CUSTOM("Custom", -1L)
}

enum class StopMotionFps(val label: String, val fps: Int) {
    FPS_8("8 fps", 8),
    FPS_12("12 fps", 12),
    FPS_24("24 fps", 24),
    FPS_30("30 fps", 30)
}

enum class StopMotionResolution(val label: String, val width: Int, val height: Int) {
    SD("SD 640p", 640, 480),
    HD("HD 720p", 1280, 720),
    FHD("FHD 1080p", 1920, 1080)
}

enum class StopMotionOnionSkin(val label: String, val alpha: Float) {
    OFF("Off", 0f),
    LOW("25%", 0.25f),
    MEDIUM("50%", 0.50f),
    HIGH("75%", 0.75f),
    FULL("100%", 1.00f)
}

class StopMotionViewModel(private val context: Context) {

    // Dir to store frames
    private val framesDir = File(context.cacheDir, "stopmotion_frames").also { it.mkdirs() }

    var frames by mutableStateOf<List<File>>(emptyList())
        private set

    init {
        frames = framesDir.listFiles()?.filter { it.isFile && it.name.startsWith("frame_") }?.sortedBy { it.name } ?: emptyList()
    }

    var isCapturing by mutableStateOf(false)
        private set
        
    var remainingMs by mutableLongStateOf(0L)
        private set

    var isExporting by mutableStateOf(false)
        private set

    var exportProgress by mutableStateOf(0f)
        private set

    var exportSuccess by mutableStateOf<Boolean?>(null)

    // Settings
    var interval by mutableStateOf(StopMotionInterval.MANUAL)
    var customIntervalMs by mutableStateOf(1000L) // Default 1s for custom

    var outputFps by mutableStateOf(StopMotionFps.FPS_12)
    var resolution by mutableStateOf(StopMotionResolution.HD)
    var onionSkin by mutableStateOf(StopMotionOnionSkin.MEDIUM)
    var showSettings by mutableStateOf(false)



    private val handler = Handler(Looper.getMainLooper())
    private var countDownTimer: android.os.CountDownTimer? = null
    var onCaptureRequest: (() -> Unit)? = null

    /** Called from camera - saves the JPEG bytes as a frame file */
    fun addFrame(jpegBytes: ByteArray) {
        val file = File(framesDir, "frame_${System.currentTimeMillis()}.jpg")
        file.writeBytes(jpegBytes)
        frames = frames + file
    }

    /** Delete the last captured frame */
    fun deleteLastFrame() {
        val last = frames.lastOrNull() ?: return
        last.delete()
        frames = frames.dropLast(1)
    }

    /** Delete a specific frame by index */
    fun deleteFrame(index: Int) {
        if (index < 0 || index >= frames.size) return
        frames[index].delete()
        frames = frames.toMutableList().also { it.removeAt(index) }
    }

    fun startAutoCapture() {
        if (interval == StopMotionInterval.MANUAL) return
        val ms = if (interval == StopMotionInterval.CUSTOM) customIntervalMs else interval.ms
        if (ms <= 0) return
        isCapturing = true
        remainingMs = ms
        scheduleNext(ms)
    }

    fun stopAutoCapture() {
        isCapturing = false
        countDownTimer?.cancel()
        countDownTimer = null
        remainingMs = 0L
    }

    private fun scheduleNext(intervalMs: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : android.os.CountDownTimer(intervalMs, 100) {
            override fun onTick(millisUntilFinished: Long) {
                if (isCapturing) {
                    remainingMs = millisUntilFinished
                } else {
                    cancel()
                }
            }
            override fun onFinish() {
                if (isCapturing) {
                    remainingMs = 0L
                    onCaptureRequest?.invoke()
                    scheduleNext(intervalMs)
                }
            }
        }.start()
    }

    fun exportToVideo(onDone: (String?) -> Unit) {
        if (frames.isEmpty()) { onDone(null); return }
        isExporting = true
        exportProgress = 0f
        exportSuccess = null

        val outputDir = File(context.cacheDir, "stopmotion_export").also { it.mkdirs() }
        val outputFile = File(outputDir, "stopmotion_${System.currentTimeMillis()}.mp4")

        // Read first frame to detect orientation
        var outWidth = resolution.width
        var outHeight = resolution.height
        val firstFrame = frames.firstOrNull()
        if (firstFrame != null) {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(firstFrame.absolutePath, opts)
            if (opts.outHeight > opts.outWidth) { // It's portrait
                outWidth = resolution.height
                outHeight = resolution.width
            }
        }

        StopMotionEncoder.encode(
            frames = frames,
            fps = outputFps.fps,
            originalWidth = outWidth,
            originalHeight = outHeight,
            outputPath = outputFile.absolutePath,
            onProgress = { p ->
                handler.post { exportProgress = p }
            },
            onDone = { success ->
                handler.post {
                    isExporting = false
                    exportSuccess = success
                    if (success) onDone(outputFile.absolutePath) else onDone(null)
                }
            }
        )
    }

    fun clearAllFrames() {
        frames.forEach { it.delete() }
        frames = emptyList()
    }

    fun cleanup() {
        stopAutoCapture()
    }
}
