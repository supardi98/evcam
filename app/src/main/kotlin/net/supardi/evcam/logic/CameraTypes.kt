package net.supardi.evcam.logic

enum class CameraMode { PHOTO, VIDEO }
enum class FocusState { SEARCHING, SUCCESS, FAILED }
enum class GridType(val label: String) { NONE("Off"), THIRDS("3x3"), FOURTHS("4x4"), GOLDEN_RATIO("Phi"), CROSSHAIR("Center") }
enum class FlashMode { AUTO, ON, OFF }
enum class TimerMode(val seconds: Int) { OFF(0), SEC_3(3), SEC_10(10), SEC_15(15), SEC_20(20), PEACE(3) }
enum class AspectRatioMode(val value: Int, val label: String) { RATIO_4_3(0, "4:3"), RATIO_16_9(1, "16:9"), RATIO_1_1(2, "1:1") }
enum class VideoQualityMode(val quality: Int, val label: String) { SD(480, "480p"), HD(720, "720p"), FHD(1080, "1080p"), QHD(1440, "2K"), UHD(2160, "4K") }
enum class VideoFpsMode(val fps: Int, val label: String) { FPS_24(24, "24 FPS"), FPS_30(30, "30 FPS"), FPS_60(60, "60 FPS"), FPS_120(120, "120 FPS"), FPS_240(240, "240 FPS") }
enum class ImageFormatMode(val label: String) { JPEG("JPEG"), RAW("RAW+JPEG") }

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
