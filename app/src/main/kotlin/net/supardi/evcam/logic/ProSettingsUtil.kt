package net.supardi.evcam.logic


import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector

fun applyProCamera2Settings(
    builder: CaptureRequest.Builder?,
    isProMode: Boolean,
    isIsoAuto: Boolean,
    isShutterAuto: Boolean,
    isFocusAuto: Boolean,
    iso: Float,
    shutterSpeed: Float,
    focusDistance: Float,
    whiteBalance: Int,
    manualKelvin: Float
) {
    builder?.let { b ->
        if (isProMode) {
            if (isIsoAuto && isShutterAuto) {
                b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            } else {
                b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                b.set(CaptureRequest.SENSOR_SENSITIVITY, iso.toInt())
                b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterSpeed.toLong())
            }
            
            if (isFocusAuto) {
                b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            } else {
                b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                b.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
            }
            
            b.set(CaptureRequest.CONTROL_AWB_MODE, whiteBalance)
            if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_OFF) {
                b.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                val temp = manualKelvin / 100.0f
                var r: Float
                var g: Float
                var b_val: Float
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
                b.set(CaptureRequest.COLOR_CORRECTION_GAINS, RggbChannelVector(rGain, gGain, gGain, bGain))
            } else {
                b.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_FAST)
            }
        } else {
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            b.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        }
    }
}
