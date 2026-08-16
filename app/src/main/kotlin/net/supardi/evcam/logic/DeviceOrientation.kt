package net.supardi.evcam.logic


import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

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
        val alpha = 0.15f // Low pass filter coefficient
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                
                // Calculate roll (rotation around Y-axis) & pitch (rotation around X-axis) in degrees
                val rollVal = Math.toDegrees(Math.atan2(-ax.toDouble(), ay.toDouble())).toFloat()
                val pitchVal = Math.toDegrees(Math.atan2(az.toDouble(), Math.sqrt(ax * ax + ay * ay.toDouble()))).toFloat()
                
                // Smooth the values
                filteredRoll = filteredRoll + alpha * (rollVal - filteredRoll)
                filteredPitch = filteredPitch + alpha * (pitchVal - filteredPitch)
                
                // Flat: Device is placed flat on a surface (vertical gravity z is near 9.8 m/s^2)
                val isDeviceFlat = Math.abs(az) > 8.5f
                
                data = DeviceOrientationData(
                    roll = filteredRoll,
                    pitch = filteredPitch,
                    isFlat = isDeviceFlat
                )
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        if (sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
    return data
}
