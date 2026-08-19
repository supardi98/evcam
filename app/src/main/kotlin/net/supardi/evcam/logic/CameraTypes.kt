package net.supardi.evcam.logic

enum class CameraMode { PHOTO, VIDEO }
enum class FocusState { TAP_INITIAL, SEARCHING, SUCCESS, FAILED }
enum class GridType(val label: String) { NONE("Off"), THIRDS("3x3"), FOURTHS("4x4"), GOLDEN_RATIO("Phi"), CROSSHAIR("Center") }
enum class FlashMode { AUTO, ON, OFF }
enum class TimerMode(val seconds: Int) { OFF(0), SEC_3(3), SEC_10(10), SEC_15(15), SEC_20(20), PEACE(3) }
enum class AspectRatioMode(val value: Int, val label: String) { RATIO_4_3(0, "4:3"), RATIO_16_9(1, "16:9"), RATIO_1_1(2, "1:1") }
enum class VideoQualityMode(val quality: Int, val label: String) { SD(480, "480p"), HD(720, "720p"), FHD(1080, "1080p"), QHD(1440, "2K"), UHD(2160, "4K") }
enum class PhotoQualityMode(val label: String) { ULTRA("Ultra"), MAX("Max"), HIGH("High"), MEDIUM("Med"), LOW("Low") }
enum class VideoFpsMode(val fps: Int, val label: String) { FPS_24(24, "24 FPS"), FPS_30(30, "30 FPS"), FPS_60(60, "60 FPS"), FPS_120(120, "120 FPS"), FPS_240(240, "240 FPS") }
enum class ImageFormatMode(val label: String) { JPEG("JPEG"), RAW("RAW+JPEG") }

enum class CustomSceneMode(
    val label: String,
    val description: String,
    val lockIso: Boolean = false,
    val lockShutter: Boolean = false,
    val lockFocus: Boolean = false,
    val lockWhiteBalance: Boolean = false,
    val lockEv: Boolean = false,
    val supportPhoto: Boolean = true,
    val supportVideo: Boolean = true
) {
    AUTO("Auto / Normal", "Standard camera defaults"),
    HORIZON_LOCK("Horizon Lock", "Real-time auto-crop and level stabilization", supportPhoto = false, supportVideo = true),
    SLOW_MOTION("Slow Motion", "Requires 120fps or 240fps selected", supportPhoto = false, supportVideo = true),
    COMPUTATIONAL_HDR("HDR+ Pro", "Software multi-frame stacking.", lockEv = true, supportVideo = false),
    PORTRAIT("Portrait Soft", "Warm skin tone with softer contrast", supportVideo = false),
    NIGHT("Night Boost", "Longer exposure for low-light conditions", lockIso = true, lockShutter = true, lockEv = true, supportVideo = false),
    MACRO("Macro Close-Up", "Locks focus to minimum distance", lockFocus = true),
    LANDSCAPE("Vibrant Landscape", "Vibrant color boost with -0.3 EV", lockEv = true, supportVideo = false),
    ACTION("Action / Sports", "Fast shutter speed (1/500s) to freeze objects", lockShutter = true),
    BACKLIGHT("HDR Backlight", "Balances exposure against strong light", lockIso = true, lockShutter = true, lockEv = true),
    SUNSET("Golden Sunset", "Warm sunset tone with +0.7 EV gain boost", lockWhiteBalance = true, lockEv = true, supportVideo = false),
    DOCUMENT("Document B&W", "High contrast black and white for documents", lockEv = true, supportVideo = false),
    CANDLELIGHT("Warm Candlelight", "Ultra-warm 2700K Kelvin color tone", lockWhiteBalance = true, supportVideo = false),
    SNOW_BEACH("Snow / Beach", "+1.0 EV compensation for bright scenes", lockEv = true),
    ASTRO_LONG_EXP("Astro / Long Exposure", "Extended shutter exposure (10s - 30s)", lockIso = true, lockShutter = true, lockFocus = true, lockEv = true, supportVideo = false),
    FIREWORKS("Fireworks Trails", "Long exposure (2s) for light trails", lockIso = true, lockShutter = true, lockEv = true, supportVideo = false),
    FOOD("Food / Culinary", "Enhances warm colors (+0.3 EV)", lockEv = true, supportVideo = false),
    CONCERT("Concert / Stage", "Reduces exposure (-1.5 EV)", lockEv = true),
    WATERFALL("Silky Waterfall", "Slow shutter (1/4s) for smooth water", lockIso = true, lockShutter = true, lockEv = true, supportVideo = false)
}

enum class ColorFilterMode(val label: String, val matrixValues: FloatArray) {
    NORMAL("Normal", floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    MONO("B&W", floatArrayOf(
        0.33f, 0.33f, 0.33f, 0f, 0f,
        0.33f, 0.33f, 0.33f, 0f, 0f,
        0.33f, 0.33f, 0.33f, 0f, 0f,
        0f,    0f,    0f,    1f, 0f
    )),
    WARM("Warm", floatArrayOf(
        1.1f, 0f,   0f,   0f, 0f,
        0f,   0.9f, 0f,   0f, 0f,
        0f,   0f,   0.8f, 0f, 0f,
        0f,   0f,   0f,   1f, 0f
    )),
    COLD("Cool", floatArrayOf(
        0.8f, 0f,   0f,   0f, 0f,
        0f,   0.9f, 0f,   0f, 0f,
        0f,   0f,   1.2f, 0f, 0f,
        0f,   0f,   0f,   1f, 0f
    )),
    VINTAGE("Vintage", floatArrayOf(
        0.9f, 0.4f, 0.2f, 0f, 0f,
        0.3f, 0.8f, 0.1f, 0f, 0f,
        0.2f, 0.3f, 0.5f, 0f, 0f,
        0f,   0f,   0f,   1f, 0f
    ))
}

enum class LocationFormat(val label: String) { CITY("City Only"), CITY_COUNTRY("City, Country"), FULL_ADDRESS("Full Address"), COORDINATES("Lat/Lng") }
enum class WatermarkElementType { TEXT, LOCATION, DATE }
enum class WatermarkQuadrant { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

interface VideoRecordController {
    fun stop()
}
