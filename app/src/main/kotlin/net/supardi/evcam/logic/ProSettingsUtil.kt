package net.supardi.evcam.logic


import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector

fun applyProCamera2Settings(
    camera2Control: Camera2CameraControl?,
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
    camera2Control?.let { control ->
        val builder = CaptureRequestOptions.Builder()
        if (isProMode) {
            if (isIsoAuto && isShutterAuto) {
                builder.clearCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE)
            } else {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso.toInt())
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterSpeed.toLong())
            }
            
            if (isFocusAuto) {
                builder.clearCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE)
            } else {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
            }
            
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, whiteBalance)
            if (whiteBalance == CaptureRequest.CONTROL_AWB_MODE_OFF) {
                builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
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
                builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, RggbChannelVector(rGain, gGain, gGain, bGain))
            } else {
                builder.clearCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE)
                builder.clearCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS)
            }
        } else {
            builder.clearCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE)
            builder.clearCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE)
            builder.clearCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE)
            builder.clearCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE)
            builder.clearCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS)
        }
        control.captureRequestOptions = builder.build()
    }
}
