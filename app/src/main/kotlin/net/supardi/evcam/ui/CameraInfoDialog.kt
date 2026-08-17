package net.supardi.evcam.ui

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CameraManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class CameraHardwareInfo(
    val id: String,
    val facing: String,
    val megapixels: String,
    val effectiveMegapixels: String,
    val aperture: String,
    val focalLength: String,
    val equivalentFocalLength: String,
    val resolution: String,
    val sensorSize: String,
    val pixelSize: String,
    val shutterSpeedRange: String,
    val isoRange: String,
    val flashSupport: Boolean,
    val oisSupport: Boolean,
    val eisSupport: Boolean,
    val oisNote: String? = null,
    val eisNote: String? = null,
    val aeLock: Boolean,
    val wbLock: Boolean,
    val filterColorArrangement: String,
    val cropFactor: String,
    val fieldOfView: String,
    val exposureModes: String,
    val capabilities: String,
    val afModes: String,
    val awbModes: String,
    val sceneModes: String,
    val colorEffects: String,
    val maxFaceCount: String,
    val faceDetectMode: String,
    val camera2ApiLevel: String,
    val videoProfiles: String,
    val maxFrameRate: String,
    val highSpeedVideo: Boolean,
    val hdrVideoSupport: Boolean
)

private fun fetchCameraHardwareInfo(context: Context): List<CameraHardwareInfo> {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val infoList = mutableListOf<CameraHardwareInfo>()
    
    try {
        for (cameraId in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            
            val facingInt = chars.get(CameraCharacteristics.LENS_FACING)
            val facingStr = when (facingInt) {
                CameraCharacteristics.LENS_FACING_FRONT -> "Front camera"
                CameraCharacteristics.LENS_FACING_BACK -> "Rear camera"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "External camera"
                else -> "Unknown camera"
            }
            
            val maxPixelArray = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION) else null
            val maxActiveRect = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION) else null
            
            val activeArrayRect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            val pixelArraySize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            
            var resStr = "N/A"
            var mpStr = "N/A"
            var effectiveMpStr = "N/A"
            var pixelSizeStr = "N/A"
            val physicalSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            
            var w = maxPixelArray?.width ?: maxActiveRect?.width() ?: pixelArraySize?.width ?: activeArrayRect?.width() ?: 0
            var h = maxPixelArray?.height ?: maxActiveRect?.height() ?: pixelArraySize?.height ?: activeArrayRect?.height() ?: 0
            
            // DevCheck fallback for hidden Quad-Bayer sensors (if the device hides 50MP behind 12.5MP binning)
            if (maxPixelArray == null && maxActiveRect == null && (w * h) in 12_000_000..13_000_000) {
                w *= 2
                h *= 2
            }
            
            if (w > 0 && h > 0) {
                val mp = (w * h) / 1000000f
                mpStr = "%.0f MP".format(mp)
                if (physicalSize != null) {
                    val pixelSize = (physicalSize.width / w) * 1000f
                    pixelSizeStr = "%.0f µm".format(pixelSize).replace(",0", "").replace(".0", "")
                }
            }
            
            if (pixelArraySize != null) {
                resStr = "${pixelArraySize.width}x${pixelArraySize.height}"
                val effMp = (pixelArraySize.width * pixelArraySize.height) / 1000000f
                effectiveMpStr = "%.1f MP".format(effMp).replace(".0", "")
            }
            
            var sensorStr = "N/A"
            var eqFocalLenStr = "N/A"
            var cropFactorStr = "N/A"
            var fovStr = "N/A"
            val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val focalStr = if (focalLengths != null && focalLengths.isNotEmpty()) "ƒ/${chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.get(0)} • ${focalLengths[0]} mm" else "N/A"
            val focalLenOnlyStr = if (focalLengths != null && focalLengths.isNotEmpty()) "${focalLengths[0]} mm" else "N/A"

            if (physicalSize != null) {
                // Calculate diagonal in mm
                val diagonal = kotlin.math.sqrt((physicalSize.width * physicalSize.width + physicalSize.height * physicalSize.height).toDouble())
                // Optical format fraction (rule of thumb: 16mm diagonal = 1 inch type)
                val opticalFormat = 16.0 / diagonal
                sensorStr = "1/%.1f\"\n%.2f x %.2f mm".format(opticalFormat, physicalSize.width, physicalSize.height)
                
                // Crop factor
                val cropFactorVal = 43.27 / diagonal
                cropFactorStr = "%.1fx".format(cropFactorVal).replace(",0", "").replace(".0", "")
                
                // 35mm equivalent focal length
                if (focalLengths != null && focalLengths.isNotEmpty()) {
                    val eqFocal = focalLengths[0] * cropFactorVal
                    eqFocalLenStr = "%.0f mm".format(eqFocal)
                    
                    val fovRad = 2 * kotlin.math.atan((physicalSize.width / (2 * focalLengths[0])).toDouble())
                    val fovDeg = Math.toDegrees(fovRad)
                    fovStr = "%.1f° Horizontal".format(fovDeg)
                }
            }
            
            val colorFilter = chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
            val colorFilterStr = when (colorFilter) {
                CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB -> "RGGB"
                CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG -> "GRBG"
                CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG -> "GBRG"
                CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR -> "BGGR"
                CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB -> "RGB"
                6 -> "MONO"
                7 -> "NIR"
                null -> "N/A"
                else -> "Unknown ($colorFilter)"
            }
            
            val aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
            val aeModesStr = if (aeModes != null) {
                val names = mutableListOf<String>()
                if (aeModes.contains(CameraCharacteristics.CONTROL_AE_MODE_OFF)) names.add("Manual")
                if (aeModes.contains(CameraCharacteristics.CONTROL_AE_MODE_ON)) names.add("Auto")
                if (aeModes.contains(CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH)) names.add("Auto Flash")
                if (aeModes.contains(CameraCharacteristics.CONTROL_AE_MODE_ON_ALWAYS_FLASH)) names.add("Always Flash")
                if (aeModes.contains(CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE)) names.add("Redeye Flash")
                if (aeModes.contains(CameraCharacteristics.CONTROL_AE_MODE_ON_EXTERNAL_FLASH)) names.add("External Flash")
                if (names.isNotEmpty()) names.joinToString(", ") else "N/A"
            } else "N/A"
            
            val exposureRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val shutterStr = if (exposureRange != null) {
                val minSec = exposureRange.lower / 1_000_000_000.0
                val maxSec = exposureRange.upper / 1_000_000_000.0
                val minFormat = if (minSec < 1) "1/${(1 / minSec).toInt()}" else "%.1f".format(minSec)
                val maxFormat = if (maxSec < 1) "1/${(1 / maxSec).toInt()}" else "${maxSec.toInt()}s"
                "$minFormat - $maxFormat"
            } else "N/A"

            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            val hasStdOis = oisModes?.any { it != CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_OFF } ?: false

            val eisModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            val hasStdEis = eisModes?.any { it != CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_OFF } ?: false

            val vendorKeys = chars.keys.map { it.name }
            val oisVendorKey = vendorKeys.find { k -> k.contains("ois", ignoreCase = true) || k.contains("optical", ignoreCase = true) }
            val eisVendorKey = vendorKeys.find { k -> k.contains("eis", ignoreCase = true) || k.contains("ais", ignoreCase = true) || k.contains("stabiliz", ignoreCase = true) }

            val hasVendorOis = oisVendorKey != null
            val hasVendorEis = eisVendorKey != null

            val hasOis = hasStdOis || (hasVendorOis && facingStr.startsWith("Rear"))
            val hasEis = hasStdEis || (hasVendorEis && facingStr.startsWith("Rear"))

            fun extractVendorName(key: String?): String? {
                if (key == null) return null
                val lowerKey = key.lowercase()
                return when {
                    lowerKey.contains("transsion") -> "Transsion/Infinix Vendor"
                    lowerKey.contains("mediatek") -> "MediaTek Vendor"
                    lowerKey.contains("qcom") || lowerKey.contains("qualcomm") -> "Qualcomm Vendor"
                    lowerKey.contains("xiaomi") -> "Xiaomi Vendor"
                    lowerKey.contains("samsung") -> "Samsung Vendor"
                    else -> "${key.substringBefore(".")} Vendor"
                }
            }

            val oisNote = if (hasOis) {
                if (hasStdOis) "Standard Android API" else extractVendorName(oisVendorKey)
            } else null

            val eisNote = if (hasEis) {
                if (hasStdEis) "Standard Android API" else extractVendorName(eisVendorKey)
            } else null
            
            val aeLock = chars.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) ?: false
            val wbLock = chars.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) ?: false
            val maxFaces = chars.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT) ?: 0
            
            val faceModes = chars.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
            val faceDetectStr = if (faceModes != null) {
                val names = mutableListOf<String>()
                if (faceModes.contains(CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_OFF)) names.add("Off")
                if (faceModes.contains(CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE)) names.add("simple")
                if (faceModes.contains(CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_FULL)) names.add("Full")
                if (names.isNotEmpty()) names.joinToString(", ") else "N/A"
            } else "N/A"
            
            val apiLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            val apiLevelStr = when (apiLevel) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
                else -> "Unknown"
            }

            fun mapAfModes(modes: IntArray?): String {
                if (modes == null) return "N/A"
                val names = mutableListOf<String>()
                if (modes.contains(CameraCharacteristics.CONTROL_AF_MODE_OFF)) names.add("Manual")
                if (modes.contains(CameraCharacteristics.CONTROL_AF_MODE_AUTO)) names.add("Auto")
                if (modes.contains(CameraCharacteristics.CONTROL_AF_MODE_MACRO)) names.add("Macro")
                if (modes.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) names.add("Continuous video")
                if (modes.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) names.add("Continuous picture")
                if (modes.contains(CameraCharacteristics.CONTROL_AF_MODE_EDOF)) names.add("EDOF")
                return names.joinToString(", ").takeIf { it.isNotEmpty() } ?: "N/A"
            }
            
            fun mapAwbModes(modes: IntArray?): String {
                if (modes == null) return "N/A"
                val names = mutableListOf<String>()
                if (modes.contains(CameraCharacteristics.CONTROL_AWB_MODE_OFF)) names.add("Off")
                if (modes.contains(CameraCharacteristics.CONTROL_AWB_MODE_AUTO)) names.add("Auto")
                if (modes.contains(CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT)) names.add("Incandescent")
                if (modes.contains(CameraCharacteristics.CONTROL_AWB_MODE_FLUORESCENT)) names.add("Fluorescent")
                if (modes.contains(CameraCharacteristics.CONTROL_AWB_MODE_WARM_FLUORESCENT)) names.add("Warm Fluorescent")
                if (modes.contains(CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT)) names.add("Daylight")
                if (modes.contains(CameraCharacteristics.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)) names.add("Cloudy")
                if (modes.contains(CameraCharacteristics.CONTROL_AWB_MODE_TWILIGHT)) names.add("Twilight")
                if (modes.contains(CameraCharacteristics.CONTROL_AWB_MODE_SHADE)) names.add("Shade")
                return names.joinToString(", ").takeIf { it.isNotEmpty() } ?: "N/A"
            }
            
            fun mapSceneModes(modes: IntArray?): String {
                if (modes == null) return "N/A"
                val names = mutableListOf<String>()
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED)) names.add("Disabled")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_FACE_PRIORITY)) names.add("Face priority")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_ACTION)) names.add("Action")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_PORTRAIT)) names.add("Portrait")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_LANDSCAPE)) names.add("Landscape")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_NIGHT)) names.add("Night")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_NIGHT_PORTRAIT)) names.add("Night portrait")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_THEATRE)) names.add("Theatre")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_BEACH)) names.add("Beach")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_SNOW)) names.add("Snow")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_SUNSET)) names.add("Sunset")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_STEADYPHOTO)) names.add("Steady")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_FIREWORKS)) names.add("Fireworks")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_SPORTS)) names.add("Sports")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_PARTY)) names.add("Party")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_CANDLELIGHT)) names.add("Candlelight")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_BARCODE)) names.add("Barcode")
                if (modes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_HDR)) names.add("HDR")
                return names.joinToString(", ").takeIf { it.isNotEmpty() } ?: "N/A"
            }
            
            fun mapColorEffects(effects: IntArray?): String {
                if (effects == null) return "N/A"
                val names = mutableListOf<String>()
                if (effects.contains(CameraCharacteristics.CONTROL_EFFECT_MODE_OFF)) names.add("Off")
                if (effects.contains(CameraCharacteristics.CONTROL_EFFECT_MODE_MONO)) names.add("Mono")
                if (effects.contains(CameraCharacteristics.CONTROL_EFFECT_MODE_NEGATIVE)) names.add("Negative")
                if (effects.contains(CameraCharacteristics.CONTROL_EFFECT_MODE_SOLARIZE)) names.add("Solarize")
                if (effects.contains(CameraCharacteristics.CONTROL_EFFECT_MODE_SEPIA)) names.add("Sepia")
                if (effects.contains(CameraCharacteristics.CONTROL_EFFECT_MODE_POSTERIZE)) names.add("Posterize")
                if (effects.contains(CameraCharacteristics.CONTROL_EFFECT_MODE_WHITEBOARD)) names.add("Whiteboard")
                if (effects.contains(CameraCharacteristics.CONTROL_EFFECT_MODE_BLACKBOARD)) names.add("Blackboard")
                if (effects.contains(CameraCharacteristics.CONTROL_EFFECT_MODE_AQUA)) names.add("Aqua")
                return names.joinToString(", ").takeIf { it.isNotEmpty() } ?: "N/A"
            }
            
            val afModes = mapAfModes(chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES))
            val awbModes = mapAwbModes(chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES))
            val sceneModes = mapSceneModes(chars.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES))
            val colorEffects = mapColorEffects(chars.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS))

            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            var maxFps = 30
            val videoProfiles = if (map != null) {
                val sizes = map.getOutputSizes(android.media.MediaRecorder::class.java)
                
                val standardSizes = listOf(
                    android.util.Size(7680, 4320), // 8K
                    android.util.Size(3840, 2160), // 4K UHD
                    android.util.Size(2560, 1440), // 1440p
                    android.util.Size(1920, 1080), // 1080p
                    android.util.Size(1280, 720)   // 720p
                )
                
                val supportedStandards = sizes?.filter { size -> 
                    standardSizes.any { it.width == size.width && it.height == size.height }
                }?.sortedByDescending { it.width * it.height } ?: emptyList()
                
                if (supportedStandards.isNotEmpty()) {
                    supportedStandards.joinToString("\n") { size ->
                        val durRecorder = map.getOutputMinFrameDuration(android.media.MediaRecorder::class.java, size)
                        val durCodec = map.getOutputMinFrameDuration(android.media.MediaCodec::class.java, size)
                        val durSurface = map.getOutputMinFrameDuration(android.graphics.SurfaceTexture::class.java, size)
                        
                        val baseFps = if (durRecorder > 0) (1_000_000_000.0 / durRecorder).toInt() else 30
                        
                        val minDurList = listOf(durRecorder, durCodec, durSurface).filter { it > 0 }
                        val absoluteMinDur = if (minDurList.isNotEmpty()) minDurList.minOrNull()!! else 0L
                        val maxPossibleFps = if (absoluteMinDur > 0) (1_000_000_000.0 / absoluteMinDur).toInt() else baseFps
                        
                        if (baseFps > maxFps) maxFps = baseFps
                        if (maxPossibleFps > maxFps) maxFps = maxPossibleFps
                        
                        val name = when {
                            size.width == 7680 && size.height == 4320 -> "8K"
                            size.width == 3840 && size.height == 2160 -> "4K UHD"
                            size.width == 2560 && size.height == 1440 -> "2560x1440"
                            size.width == 1920 && size.height == 1080 -> "1080p"
                            size.width == 1280 && size.height == 720 -> "720p"
                            else -> "${size.width}x${size.height}"
                        }
                        
                        val highSpeedRanges = try {
                            map.getHighSpeedVideoFpsRangesFor(size)
                        } catch (e: Exception) { null }
                        
                        val hsFpsList = highSpeedRanges?.map { it.upper }?.distinct()?.filter { it > baseFps }?.toMutableList() ?: mutableListOf()
                        if (maxPossibleFps > baseFps && !hsFpsList.contains(maxPossibleFps)) {
                            hsFpsList.add(maxPossibleFps)
                        }
                        
                        // Check legacy CamcorderProfile for hidden high speed profiles
                        try {
                            val idInt = cameraId.toIntOrNull()
                            if (idInt != null) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    if (name == "720p" && android.media.CamcorderProfile.hasProfile(idInt, android.media.CamcorderProfile.QUALITY_HIGH_SPEED_720P)) {
                                        val profiles = android.media.CamcorderProfile.getAll(cameraId, android.media.CamcorderProfile.QUALITY_HIGH_SPEED_720P)
                                        profiles?.videoProfiles?.forEach { if (it.frameRate > baseFps) hsFpsList.add(it.frameRate) }
                                    }
                                    if (name == "1080p" && android.media.CamcorderProfile.hasProfile(idInt, android.media.CamcorderProfile.QUALITY_HIGH_SPEED_1080P)) {
                                        val profiles = android.media.CamcorderProfile.getAll(cameraId, android.media.CamcorderProfile.QUALITY_HIGH_SPEED_1080P)
                                        profiles?.videoProfiles?.forEach { if (it.frameRate > baseFps) hsFpsList.add(it.frameRate) }
                                    }
                                } else {
                                    if (name == "720p" && android.media.CamcorderProfile.hasProfile(idInt, android.media.CamcorderProfile.QUALITY_HIGH_SPEED_720P)) {
                                        val p = android.media.CamcorderProfile.get(idInt, android.media.CamcorderProfile.QUALITY_HIGH_SPEED_720P)
                                        if (p.videoFrameRate > baseFps) hsFpsList.add(p.videoFrameRate)
                                    }
                                    if (name == "1080p" && android.media.CamcorderProfile.hasProfile(idInt, android.media.CamcorderProfile.QUALITY_HIGH_SPEED_1080P)) {
                                        val p = android.media.CamcorderProfile.get(idInt, android.media.CamcorderProfile.QUALITY_HIGH_SPEED_1080P)
                                        if (p.videoFrameRate > baseFps) hsFpsList.add(p.videoFrameRate)
                                    }
                                }
                            }
                        } catch (e: Exception) {}
                        
                        val distinctHsFpsList = hsFpsList.distinct().sorted()
                        val fpsStr = if (distinctHsFpsList.isNotEmpty()) {
                            val hsFpsStr = distinctHsFpsList.joinToString(", ")
                            "@ $baseFps, $hsFpsStr fps"
                        } else {
                            "@ $baseFps fps"
                        }
                        
                        "$name $fpsStr"
                    }
                } else "N/A"
            } else "N/A"

            val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            val highSpeed = caps?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO) == true
            val hdrVideo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                caps?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT) == true
            } else false

            val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            val apertureStr = if (apertures != null && apertures.isNotEmpty()) "ƒ/${apertures[0]}" else "N/A"
            
            val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val isoStr = if (isoRange != null) "${isoRange.lower} - ${isoRange.upper}" else "N/A"
            
            val capsList = mutableListOf<String>()
            if (caps != null) {
                if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) capsList.add("Manual Sensor")
                if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)) capsList.add("Manual Post Processing")
                if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) capsList.add("RAW Mode")
                if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE)) capsList.add("Burst")
            }
                    val capsStr = if (capsList.isNotEmpty()) capsList.joinToString(", ") else "Standard"
            
            infoList.add(
                CameraHardwareInfo(
                    id = cameraId,
                    facing = facingStr,
                    megapixels = mpStr,
                    effectiveMegapixels = effectiveMpStr,
                    aperture = apertureStr,
                    focalLength = focalStr,
                    equivalentFocalLength = eqFocalLenStr,
                    resolution = resStr,
                    sensorSize = sensorStr,
                    pixelSize = pixelSizeStr,
                    shutterSpeedRange = shutterStr,
                    isoRange = isoStr,
                    flashSupport = hasFlash,
                    oisSupport = hasOis,
                    eisSupport = hasEis,
                    oisNote = oisNote,
                    eisNote = eisNote,
                    aeLock = aeLock,
                    wbLock = wbLock,
                    filterColorArrangement = colorFilterStr,
                    cropFactor = cropFactorStr,
                    fieldOfView = fovStr,
                    exposureModes = aeModesStr,
                    capabilities = capsStr,
                    afModes = afModes,
                    awbModes = awbModes,
                    sceneModes = sceneModes,
                    colorEffects = colorEffects,
                    maxFaceCount = maxFaces.toString(),
                    faceDetectMode = faceDetectStr,
                    camera2ApiLevel = apiLevelStr,
                    videoProfiles = videoProfiles,
                    maxFrameRate = "$maxFps fps",
                    highSpeedVideo = highSpeed,
                    hdrVideoSupport = hdrVideo
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    // Sort cameras: Rear first, then by MP descending
    infoList.sortWith(compareBy<CameraHardwareInfo> { 
        if (it.facing.startsWith("Rear")) 0 else 1 
    }.thenByDescending { 
        it.megapixels.replace(" MP", "").toFloatOrNull() ?: 0f
    })
    
    return infoList
}

@Composable
fun CameraInfoDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hardwareInfoList by remember { mutableStateOf<List<CameraHardwareInfo>>(emptyList()) }
    var selectedCamera by remember { mutableStateOf<CameraHardwareInfo?>(null) }
    
    LaunchedEffect(Unit) {
        hardwareInfoList = fetchCameraHardwareInfo(context)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            if (selectedCamera != null) {
                CameraDetailsDialog(
                    info = selectedCamera!!,
                    onBack = { selectedCamera = null }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Hardware Camera Info",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(hardwareInfoList) { info ->
                            CameraHardwareCard(info, onMoreClick = { selectedCamera = info })
                        }
                        
                        // Show video capture card globally at the bottom (using first camera's video profiles as representative since it's global conceptually for DevCheck, or we can use the back camera's info)
                        if (hardwareInfoList.isNotEmpty()) {
                            val rearCam = hardwareInfoList.find { it.facing.startsWith("Rear") } ?: hardwareInfoList.first()
                            item {
                                VideoCaptureCard(rearCam)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraHardwareCard(info: CameraHardwareInfo, onMoreClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = info.facing,
                color = Color.Green,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(info.megapixels, color = Color.Green, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(info.focalLength, color = Color.White, fontSize = 14.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row {
                if (info.flashSupport) {
                    Box(modifier = Modifier.border(1.dp, Color.Green, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("Flash", color = Color.Green, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (info.oisSupport) {
                    Box(modifier = Modifier.border(1.dp, Color.Green, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("OIS", color = Color.Green, fontSize = 12.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Effective megapixels", color = Color.LightGray, fontSize = 12.sp)
                    Text(info.effectiveMegapixels, color = Color.White, fontSize = 14.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Resolution", color = Color.LightGray, fontSize = 12.sp)
                    Text(info.resolution, color = Color.White, fontSize = 14.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sensor size", color = Color.LightGray, fontSize = 12.sp)
                    Text(info.sensorSize, color = Color.White, fontSize = 14.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pixel size", color = Color.LightGray, fontSize = 12.sp)
                    Text(info.pixelSize, color = Color.White, fontSize = 14.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("35mm equivalent focal length", color = Color.LightGray, fontSize = 12.sp)
                    Text(info.equivalentFocalLength, color = Color.White, fontSize = 14.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Shutter speed", color = Color.LightGray, fontSize = 12.sp)
                    Text(info.shutterSpeedRange, color = Color.White, fontSize = 14.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ISO sensitivity range", color = Color.LightGray, fontSize = 12.sp)
                    Text(info.isoRange, color = Color.White, fontSize = 14.sp)
                }
            }
        }
        
        Divider(color = Color.White.copy(alpha = 0.1f))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onMoreClick() }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("More", color = Color.Green, fontSize = 16.sp)
            Icon(Icons.Default.ChevronRight, contentDescription = "More", tint = Color.White)
        }
    }
}

@Composable
private fun VideoCaptureCard(info: CameraHardwareInfo) {
    // Video Capture Card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Video capture",
            color = Color.Green,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        InfoLabel("Profiles", info.videoProfiles)
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Max frame rate", color = Color.LightGray, fontSize = 12.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(info.maxFrameRate, color = Color.White, fontSize = 14.sp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        
        FeatureRow("High speed video", info.highSpeedVideo)
        FeatureRow("Electronic video stabilization", info.eisSupport)
        FeatureRow("HDR video support", info.hdrVideoSupport)
    }
}

@Composable
private fun CameraDetailsDialog(info: CameraHardwareInfo, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Green, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${info.facing}",
                color = Color.Green,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Divider(color = Color.Green)
        
        LazyColumn(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
            item { DetailRow("Megapixels", info.megapixels) }
            item { DetailRow("Effective megapixels", info.effectiveMegapixels) }
            item { DetailRow("Resolution", info.resolution) }
            item { DetailRow("Sensor size", info.sensorSize) }
            item { DetailRow("Pixel size", info.pixelSize) }
            item { DetailRow("Filter color arrangement", info.filterColorArrangement) }
            item { DetailRow("Aperture", info.aperture) }
            item { DetailRow("Focal length", info.focalLength.substringAfter("• ").trim()) }
            item { DetailRow("35mm equivalent focal length", info.equivalentFocalLength) }
            item { DetailRow("Crop factor", info.cropFactor) }
            item { DetailRow("Field of view", info.fieldOfView) }
            item { DetailRow("Shutter speed", info.shutterSpeedRange) }
            item { DetailRow("ISO sensitivity range", info.isoRange) }
            
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureRow("Flash", info.flashSupport)
                    FeatureRow("Electronic video stabilization", info.eisSupport, info.eisNote)
                    FeatureRow("Optical image stabilization", info.oisSupport, info.oisNote)
                    FeatureRow("AE lock", info.aeLock)
                    FeatureRow("WB lock", info.wbLock)
                }
            }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }
            
            item { DetailSection("Capabilities", info.capabilities) }
            item { DetailSection("Exposure modes", info.exposureModes) }
            item { DetailSection("Autofocus modes", info.afModes) }
            item { DetailSection("White balance modes", info.awbModes) }
            item { DetailSection("Scene modes", info.sceneModes) }
            
            item { DetailRow("Color effects", info.colorEffects) }
            item { DetailRow("Max face count", info.maxFaceCount) }
            item { DetailRow("Face detect mode", info.faceDetectMode) }
            item { DetailRow("Camera2 API support", info.camera2ApiLevel) }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.align(Alignment.End)) {
            Text("Back")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = Color.LightGray, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DetailSection(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = Color.LightGray, fontSize = 13.sp)
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            Text(value, color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun InfoLabel(label: String, value: String) {
    Column {
        Text(text = label, color = Color.LightGray, fontSize = 12.sp)
        Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FeatureRow(label: String, isSupported: Boolean, note: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(
            imageVector = if (isSupported) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isSupported) Color.Green else Color.Gray,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, color = Color.White, fontSize = 14.sp)
        if (note != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "($note)", color = Color(0xFFFFD700), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
