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
    val aperture: String,
    val focalLength: String,
    val resolution: String,
    val sensorSize: String,
    val pixelSize: String,
    val shutterSpeedRange: String,
    val isoRange: String,
    val flashSupport: Boolean,
    val oisSupport: Boolean,
    val eisSupport: Boolean,
    val aeLock: Boolean,
    val wbLock: Boolean,
    val capabilities: String,
    val afModes: String,
    val awbModes: String,
    val sceneModes: String,
    val colorEffects: String,
    val maxFaceCount: String,
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
                CameraCharacteristics.LENS_FACING_FRONT -> "Front camera (ID: $cameraId)"
                CameraCharacteristics.LENS_FACING_BACK -> "Rear camera (ID: $cameraId)"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "External camera (ID: $cameraId)"
                else -> "Unknown camera (ID: $cameraId)"
            }
            
            val activeArrayRect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            val pixelArraySize = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            
            var resStr = "N/A"
            var mpStr = "N/A"
            var pixelSizeStr = "N/A"
            val physicalSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            
            val w = pixelArraySize?.width ?: activeArrayRect?.width() ?: 0
            val h = pixelArraySize?.height ?: activeArrayRect?.height() ?: 0
            
            if (w > 0 && h > 0) {
                resStr = "${w}x${h}"
                val mp = (w * h) / 1000000f
                mpStr = "%.1f MP".format(mp)
                if (physicalSize != null) {
                    val pixelSize = (physicalSize.width / w) * 1000f
                    pixelSizeStr = "%.2f µm".format(pixelSize)
                }
            }
            
            val sensorStr = if (physicalSize != null) "%.2f x %.2f mm".format(physicalSize.width, physicalSize.height) else "N/A"
            
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
            val hasOis = oisModes?.any { it != CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_OFF } ?: false
            val eisModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            val hasEis = eisModes?.any { it != CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_OFF } ?: false
            
            val aeLock = chars.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) ?: false
            val wbLock = chars.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) ?: false
            val maxFaces = chars.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT) ?: 0
            
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
                        val minDuration = map.getOutputMinFrameDuration(android.media.MediaRecorder::class.java, size)
                        val fps = if (minDuration > 0) (1_000_000_000.0 / minDuration).toInt() else 30
                        if (fps > maxFps) maxFps = fps
                        
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
                        
                        val hsFpsList = highSpeedRanges?.map { it.upper }?.distinct()?.filter { it > fps } ?: emptyList()
                        val fpsStr = if (hsFpsList.isNotEmpty()) {
                            val hsFpsStr = hsFpsList.joinToString(", ")
                            "@ $fps, $hsFpsStr fps"
                        } else {
                            "@ $fps fps"
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
            
            val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val focalStr = if (focalLengths != null && focalLengths.isNotEmpty()) "${focalLengths[0]} mm" else "N/A"
            
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
                    aperture = apertureStr,
                    focalLength = focalStr,
                    resolution = resStr,
                    sensorSize = sensorStr,
                    pixelSize = pixelSizeStr,
                    shutterSpeedRange = shutterStr,
                    isoRange = isoStr,
                    flashSupport = hasFlash,
                    oisSupport = hasOis,
                    eisSupport = hasEis,
                    aeLock = aeLock,
                    wbLock = wbLock,
                    capabilities = capsStr,
                    afModes = afModes,
                    awbModes = awbModes,
                    sceneModes = sceneModes,
                    colorEffects = colorEffects,
                    maxFaceCount = maxFaces.toString(),
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
                .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
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
            .background(Color.DarkGray, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${info.facing}",
                color = Color.Green,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = info.megapixels, color = Color.Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(text = "${info.aperture} • ${info.focalLength}", color = Color.White, fontSize = 14.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    InfoLabel("Resolution", info.resolution)
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoLabel("Sensor size", info.sensorSize)
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoLabel("ISO range", info.isoRange)
                }
                Column(modifier = Modifier.weight(1f)) {
                    InfoLabel("Pixel size", info.pixelSize)
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoLabel("Shutter speed", info.shutterSpeedRange)
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
            .background(Color.DarkGray, RoundedCornerShape(12.dp))
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
            item { DetailRow("Resolution", info.resolution) }
            item { DetailRow("Sensor size", info.sensorSize) }
            item { DetailRow("Pixel size", info.pixelSize) }
            item { DetailRow("Aperture", info.aperture) }
            item { DetailRow("Focal length", info.focalLength) }
            item { DetailRow("Shutter speed", info.shutterSpeedRange) }
            item { DetailRow("ISO sensitivity range", info.isoRange) }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }
            
            item { FeatureRow("Flash", info.flashSupport) }
            item { FeatureRow("Electronic video stabilization", info.eisSupport) }
            item { FeatureRow("Optical image stabilization", info.oisSupport) }
            item { FeatureRow("AE lock", info.aeLock) }
            item { FeatureRow("WB lock", info.wbLock) }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }
            
            item { DetailSection("Capabilities", info.capabilities) }
            item { DetailSection("Color effects", info.colorEffects) }
            item { DetailSection("Autofocus modes", info.afModes) }
            item { DetailSection("White balance modes", info.awbModes) }
            item { DetailSection("Scene modes", info.sceneModes) }
            
            item { DetailRow("Max face count", info.maxFaceCount) }
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
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(8.dp)
        ) {
            Text(value, color = Color.White, fontSize = 13.sp)
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
private fun FeatureRow(label: String, isSupported: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(
            imageVector = if (isSupported) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isSupported) Color.Green else Color.Gray,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, color = Color.White, fontSize = 14.sp)
    }
}
