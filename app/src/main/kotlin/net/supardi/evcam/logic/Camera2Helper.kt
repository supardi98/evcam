package net.supardi.evcam.logic

import android.hardware.camera2.CameraCharacteristics
import android.util.Range
import android.util.Size

object Camera2Helper {

    fun getZoomRatios(chars: CameraCharacteristics): Pair<Float, Float> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val range = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            if (range != null) {
                Pair(range.lower, range.upper)
            } else {
                Pair(1f, chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)
            }
        } else {
            Pair(1f, chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)
        }
    }

    fun getIsoRange(chars: CameraCharacteristics): Pair<Float, Float>? {
        val range = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: return null
        return Pair(range.lower.toFloat(), range.upper.toFloat())
    }

    fun hasFlashSupport(chars: CameraCharacteristics): Boolean {
        return chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
    }

    fun hasManualSensorSupport(chars: CameraCharacteristics): Boolean {
        val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        return caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
    }

    fun getExposureInfo(chars: CameraCharacteristics): Triple<Int, Int, Float>? {
        val range = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: return null
        val step = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP) ?: return null
        return Triple(range.lower, range.upper, step.toFloat())
    }

    fun getSupportedFps(chars: CameraCharacteristics): List<Int> {
        val ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
        val list = mutableListOf<Int>()
        ranges.forEach { range ->
            if (range.lower == range.upper) {
                list.add(range.upper)
            }
        }
        if (list.isEmpty()) {
            list.addAll(listOf(24, 30, 60))
        }
        return list.distinct().sorted()
    }

    fun getSupportedVideoResolutions(chars: CameraCharacteristics): List<Size> {
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return emptyList()
        val sizes = map.getOutputSizes(android.media.MediaRecorder::class.java) ?: emptyArray()
        return sizes.toList().sortedByDescending { it.width * it.height }
    }
}
