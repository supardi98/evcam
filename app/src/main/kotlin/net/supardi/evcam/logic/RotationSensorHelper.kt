package net.supardi.evcam.logic

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RotationSensorHelper(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    data class HorizonState(
        val rollAngle: Float = 0f,
        val zoomScale: Float = 1f // 1.0 = base unrotated crop
    )

    private val _horizonState = MutableStateFlow(HorizonState())
    val horizonState: StateFlow<HorizonState> = _horizonState

    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var smoothingJob: Job? = null

    // Target values
    private var targetRoll = 0f
    private var targetZoom = 1f

    // Current smoothed values
    private var currentRoll = 0f
    private var currentZoom = 1f

    // Constants for output aspect (16:9) and sensor aspect (9:16 since texture is upright Portrait from a 16:9 buffer)
    private val outputAspect = 16f / 9f
    private val sensorAspect = 9f / 16f

    // Baseline zoom at 0 degrees
    private val baselineZoom: Float
    
    // Timer for zoom out delay
    private var timeAtNormalRoll: Long = 0

    init {
        val cosA = 1f
        val sinA = 0f
        val rotW = outputAspect * cosA + 1f * sinA
        val rotH = outputAspect * sinA + 1f * cosA
        val scaleW = sensorAspect / rotW
        val scaleH = 1f / rotH
        val maxScale = Math.min(scaleW, scaleH)
        baselineZoom = if (maxScale > 0) 1f / maxScale else 1f
    }

    fun start() {
        if (isListening || rotationSensor == null) return
        isListening = true
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        startSmoothingLoop()
    }

    fun stop() {
        if (!isListening) return
        isListening = false
        sensorManager.unregisterListener(this)
        smoothingJob?.cancel()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_GRAVITY) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            // If z is very large, the phone is pointing up or down (flat).
            // When flat, x and y are noisy, so we ignore updates to avoid wild spinning.
            if (Math.abs(z) < 8.5f) {
                // Math.atan2(y, x) is standard, but passing (x, y) gives angle relative to Y-axis.
                // Upright Portrait: x=0, y=-9.8 -> atan2(0, -9.8) = 0
                // Landscape Left: x=9.8, y=0 -> atan2(9.8, 0) = 90
                val rollDegrees = Math.toDegrees(Math.atan2(x.toDouble(), y.toDouble())).toFloat()
                targetRoll = -rollDegrees
            }
        }
        
        targetZoom = calculateRequiredZoom(targetRoll)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calculateRequiredZoom(rollDegrees: Float): Float {
        val rad = Math.toRadians(rollDegrees.toDouble())
        val cosA = Math.abs(Math.cos(rad)).toFloat()
        val sinA = Math.abs(Math.sin(rad)).toFloat()
        
        val rotW = outputAspect * cosA + 1f * sinA
        val rotH = outputAspect * sinA + 1f * cosA
        
        val scaleW = sensorAspect / rotW
        val scaleH = 1f / rotH
        
        val maxScale = Math.min(scaleW, scaleH)
        val rawZoom = if (maxScale > 0) 1f / maxScale else 1f
        
        return rawZoom / baselineZoom
    }

    private fun startSmoothingLoop() {
        smoothingJob?.cancel()
        smoothingJob = scope.launch {
            while (isActive) {
                var diff = targetRoll - currentRoll
                while (diff > 180f) diff -= 360f
                while (diff < -180f) diff += 360f
                
                // If the phone is shaking or rotating significantly, reset the zoom-out timer
                if (Math.abs(diff) > 1.0f) {
                    timeAtNormalRoll = System.currentTimeMillis()
                }
                
                currentRoll += diff * 0.4f
                
                // Keep currentRoll normalized
                while (currentRoll > 180f) currentRoll -= 360f
                while (currentRoll < -180f) currentRoll += 360f
                
                
                if (targetZoom > currentZoom) {
                    currentZoom += (targetZoom - currentZoom) * 0.8f
                    timeAtNormalRoll = System.currentTimeMillis() // Reset timer since we are zooming in
                } else {
                    // Only zoom out if we've been completely stable for 2.5 seconds
                    if (System.currentTimeMillis() - timeAtNormalRoll > 2500) {
                        currentZoom += (targetZoom - currentZoom) * 0.01f // Very slow cinematic zoom out
                    }
                }
                
                _horizonState.value = HorizonState(currentRoll, currentZoom)
                
                delay(16) // ~60fps
            }
        }
    }
}
