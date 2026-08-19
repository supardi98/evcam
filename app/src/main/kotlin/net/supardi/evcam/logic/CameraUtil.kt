package net.supardi.evcam.logic


import android.content.ContentValues
import androidx.exifinterface.media.ExifInterface
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Address
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.text.Layout
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.ContextCompat
import net.supardi.evcam.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executor

fun isUriValid(context: Context, uri: Uri): Boolean {
    Log.d("EVCAM", "Checking validity of URI via AFD: $uri")
    
    // Check IS_TRASHED for Android 11+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.IS_TRASHED), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.IS_TRASHED)
                    if (idx != -1) {
                        val isTrashed = cursor.getInt(idx)
                        Log.d("EVCAM", "IS_TRASHED = $isTrashed")
                        if (isTrashed == 1) return false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("EVCAM", "Error checking IS_TRASHED: ${e.message}")
        }
    }

    return try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            Log.d("EVCAM", "AFD length: ${afd.length}")
            afd.length > 0
        } ?: false
    } catch (e: Exception) {
        Log.e("EVCAM", "Failed to open AFD: ${e.message}")
        false
    }
}

fun formatLocationElement(format: String, location: Location?, address: Address?): String {
    if (location == null) return ""
    val fmt = if (format.isEmpty() || format == "LOCATION") "CITY" else format
    return when (fmt) {
        "CITY" -> address?.locality ?: address?.subAdminArea ?: "Unknown City"
        "CITY_COUNTRY" -> {
            val city = address?.locality ?: address?.subAdminArea ?: ""
            val country = address?.countryName ?: ""
            if (city.isNotEmpty() && country.isNotEmpty()) "$city, $country"
            else if (city.isNotEmpty()) city else if (country.isNotEmpty()) country else "Unknown Location"
        }
        "FULL_ADDRESS" -> address?.getAddressLine(0) ?: "Unknown Address"
        "DECIMAL_DEGREES" -> String.format(Locale.US, "%.5f°, %.5f°", location.latitude, location.longitude)
        "DMS" -> {
            fun toDms(deg: Double): String {
                val absVal = Math.abs(deg)
                val d = absVal.toInt()
                val m = ((absVal - d) * 60).toInt()
                val s = (((absVal - d) * 60.0) - m) * 60.0
                return "$d°$m'${String.format(Locale.US, "%.1f", s)}\""
            }
            val latDir = if (location.latitude >= 0) "N" else "S"
            val lngDir = if (location.longitude >= 0) "E" else "W"
            "${toDms(location.latitude)}$latDir, ${toDms(location.longitude)}$lngDir"
        }
        else -> "Lat: ${location.latitude}, Lng: ${location.longitude}"
    }
}

fun formatDateElement(format: String): String {
    val fmt = if (format.isEmpty() || format == "DATE") "yyyy/MM/dd HH:mm" else format
    return try {
        SimpleDateFormat(fmt, Locale.US).format(Date())
    } catch (e: Exception) {
        fmt
    }
}

/**
 * Writes a comprehensive set of EXIF tags to a saved image URI.
 * Should be called immediately after the bitmap has been flushed to the URI.
 */
fun writeExifTags(
    context: Context,
    uri: android.net.Uri,
    camera2Engine: Camera2Engine,
    timestamp: Long,
    location: android.location.Location?,
    enableGeotagging: Boolean,
    iso: Int?,
    shutterSpeedNs: Long?,
    isHdr: Boolean = false,
    rawExif: androidx.exifinterface.media.ExifInterface? = null
) {
    try {
        val pfd = context.contentResolver.openFileDescriptor(uri, "rw") ?: return
        pfd.use {
            val exif = androidx.exifinterface.media.ExifInterface(it.fileDescriptor)

            // ── Date / Time ──────────────────────────────────────────────────
            val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
            val dateStr = sdf.format(java.util.Date(timestamp))
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME, dateStr)
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)

            // ── Device / Software credit ──────────────────────────────────────
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE, android.os.Build.MANUFACTURER)
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL, android.os.Build.MODEL)
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_SOFTWARE,
                if (isHdr) "EVCam HDR" else "EVCam")

            // ── Orientation (always normal — we already rotated the bitmap) ──
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL.toString())

            // ── Color space ──────────────────────────────────────────────────
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_COLOR_SPACE, "1") // sRGB

            // ── Lens / sensor info from CameraCharacteristics ────────────────
            try {
                val cameraId = camera2Engine.cameraDevice?.id
                if (cameraId != null) {
                    val chars = camera2Engine.cameraManager.getCameraCharacteristics(cameraId)

                    // Focal length
                    val focalLengths = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    if (focalLengths != null && focalLengths.isNotEmpty()) {
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH,
                            focalLengths[0].toString())
                    }

                    // Aperture
                    val apertures = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                    if (apertures != null && apertures.isNotEmpty()) {
                        val fNumber = apertures[0]
                        val apexAperture = 2.0 * kotlin.math.log2(fNumber.toDouble())
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_APERTURE_VALUE, apexAperture.toString())
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER, fNumber.toString())
                    }

                    // Sensor physical size (for 35mm equivalent)
                    val sensorSize = chars.get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    if (sensorSize != null && focalLengths != null && focalLengths.isNotEmpty()) {
                        val sensorDiag = kotlin.math.sqrt(
                            (sensorSize.width * sensorSize.width + sensorSize.height * sensorSize.height).toDouble()
                        )
                        val cropFactor = 43.27 / sensorDiag
                        val equiv35mm = (focalLengths[0] * cropFactor).toInt()
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, equiv35mm.toString())
                    }
                }
            } catch (_: Exception) {}

            // ── Exposure info ────────────────────────────────────────────────
            if (iso != null && iso > 0) {
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ISO_SPEED_RATINGS, iso.toString())
            } else if (rawExif != null && rawExif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_ISO_SPEED_RATINGS) != null) {
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ISO_SPEED_RATINGS, rawExif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_ISO_SPEED_RATINGS))
            }

            if (shutterSpeedNs != null && shutterSpeedNs > 0) {
                val shutterSec = shutterSpeedNs / 1_000_000_000.0
                val denom = (1.0 / shutterSec).toInt().coerceAtLeast(1)
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME, "1/$denom")
            } else if (rawExif != null && rawExif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME) != null) {
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME, rawExif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME))
            }
            if (rawExif != null && rawExif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_WHITE_BALANCE) != null) {
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_WHITE_BALANCE, rawExif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_WHITE_BALANCE))
            } else {
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_WHITE_BALANCE, "0") // Fallback to Auto
            }

            if (rawExif != null && rawExif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_FLASH) != null) {
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_FLASH, rawExif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_FLASH))
            }

            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_METERING_MODE, "2")  // Center-weighted

            // ── GPS (only if geotagging is enabled) ──────────────────────────
            if (enableGeotagging && location != null) {
                exif.setLatLong(location.latitude, location.longitude)
                if (location.hasAltitude()) {
                    exif.setAltitude(location.altitude)
                }
                val gpsSdf = java.text.SimpleDateFormat("yyyy:MM:dd", java.util.Locale.US)
                val gpsTSdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                gpsSdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                gpsTSdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val d = java.util.Date(location.time.takeIf { it > 0 } ?: System.currentTimeMillis())
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_DATESTAMP, gpsSdf.format(d))
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_TIMESTAMP, gpsTSdf.format(d))
            }

            exif.saveAttributes()
        }
    } catch (e: Exception) {
        Log.e("EVCAM", "Failed to write EXIF", e)
    }
}

fun takePhoto(
    context: Context,
    camera2Engine: Camera2Engine,
    flashMode: FlashMode,
    selectedFilter: ColorFilterMode,
    showWatermark: Boolean,
    watermarkElements: List<WatermarkElement>,
    liveLocation: Location?,
    liveAddress: Address?,
    enableGeotagging: Boolean,
    enableRawCapture: Boolean,
    aspectRatioMode: AspectRatioMode,
    deviceRotation: Int,
    isFrontCamera: Boolean = false,
    mirrorSelfie: Boolean = true,
    customSceneMode: CustomSceneMode = CustomSceneMode.AUTO,
    isUltraMode: Boolean = false,
    isIsoAuto: Boolean = true,
    iso: Int = 100,
    isShutterAuto: Boolean = true,
    shutterSpeed: Long = 10000000L,
    onPhotoSaved: (Bitmap, Uri) -> Unit
) {

    camera2Engine.capturePhoto(
        isUltraMode = isUltraMode,
        activeCustomScene = customSceneMode,
        flashMode = flashMode,
        isIsoAuto = isIsoAuto,
        iso = iso,
        isShutterAuto = isShutterAuto,
        shutterSpeed = shutterSpeed
    ) { image ->
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        var bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        image.close()

        val exifInterface = try {
            androidx.exifinterface.media.ExifInterface(bytes.inputStream())
        } catch (e: Exception) { null }

        val exifOrientation = exifInterface?.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        ) ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL

        val rotationDegrees = when (exifOrientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        // Apply Mirror Selfie Flip for Front Camera
        if (isFrontCamera && mirrorSelfie) {
            val matrix = android.graphics.Matrix()
            matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        // Apply Aspect Ratio Crop

        val currentAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val targetAspect = when (aspectRatioMode) {
            AspectRatioMode.RATIO_1_1 -> 1f
            AspectRatioMode.RATIO_4_3 -> if (bitmap.width > bitmap.height) 4f / 3f else 3f / 4f
            AspectRatioMode.RATIO_16_9 -> if (bitmap.width > bitmap.height) 16f / 9f else 9f / 16f
        }

        if (kotlin.math.abs(currentAspect - targetAspect) > 0.02f) {
            var cropWidth = bitmap.width
            var cropHeight = bitmap.height

            if (currentAspect > targetAspect) {
                cropWidth = (bitmap.height * targetAspect).toInt()
            } else {
                cropHeight = (bitmap.width / targetAspect).toInt()
            }

            val cropX = ((bitmap.width - cropWidth) / 2).coerceAtLeast(0)
            val cropY = ((bitmap.height - cropHeight) / 2).coerceAtLeast(0)

            if (cropWidth > 0 && cropHeight > 0 && (cropX + cropWidth <= bitmap.width) && (cropY + cropHeight <= bitmap.height)) {
                bitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)
            }
        }

        if (selectedFilter != ColorFilterMode.NORMAL) {
            val filterMatrix = android.graphics.ColorMatrix(selectedFilter.matrixValues)
            val filteredBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
            val canvas = Canvas(filteredBitmap)
            val paint = Paint().apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(filterMatrix)
            }
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            bitmap = filteredBitmap
        }

        // Apply Computational HDR Software Tone Mapping when BACKLIGHT or NIGHT scene is active
        if (customSceneMode == CustomSceneMode.BACKLIGHT || customSceneMode == CustomSceneMode.NIGHT) {
            val hdrMatrix = android.graphics.ColorMatrix(floatArrayOf(
                1.15f, -0.05f, -0.05f, 0f, 10f,  // Lift shadows & preserve highlights
                -0.05f, 1.15f, -0.05f, 0f, 10f,
                -0.05f, -0.05f, 1.15f, 0f, 10f,
                0f,     0f,     0f,    1f, 0f
            ))
            val hdrBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
            val canvas = Canvas(hdrBitmap)
            val paint = Paint().apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(hdrMatrix)
            }
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            bitmap = hdrBitmap
        }

        if (showWatermark && watermarkElements.isNotEmpty()) {
            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                setShadowLayer(5f, 0f, 0f, Color.BLACK)
            }
            
            val padding = 32f
            
            WatermarkQuadrant.values().forEach { quadrant ->
                val elements = watermarkElements.filter { it.quadrant == quadrant }
                if (elements.isNotEmpty()) {
                    var yOffset = if (quadrant == WatermarkQuadrant.TOP_LEFT || quadrant == WatermarkQuadrant.TOP_RIGHT) padding else mutableBitmap.height - padding
                    val isBottom = quadrant == WatermarkQuadrant.BOTTOM_LEFT || quadrant == WatermarkQuadrant.BOTTOM_RIGHT
                    val isRight = quadrant == WatermarkQuadrant.TOP_RIGHT || quadrant == WatermarkQuadrant.BOTTOM_RIGHT

                    val drawElements = if (isBottom) elements.reversed() else elements

                    for (element in drawElements) {
                        paint.textSize = element.size * 4f
                        paint.textAlign = if (isRight) Paint.Align.RIGHT else Paint.Align.LEFT
                        
                        val text = when (element.type) {
                            WatermarkElementType.TEXT -> element.content
                            WatermarkElementType.LOCATION -> formatLocationElement(element.content, liveLocation, liveAddress)
                            WatermarkElementType.DATE -> formatDateElement(element.content)
                        }
                        
                        if (text.isEmpty()) continue
                        
                        if (isBottom) {
                            yOffset -= paint.descent() - paint.ascent()
                        }
                        
                        val x = if (isRight) mutableBitmap.width - padding else padding
                        canvas.drawText(text, x, yOffset - paint.ascent(), paint)
                        
                        if (!isBottom) {
                            yOffset += paint.descent() - paint.ascent()
                        }
                    }
                }
            }
            bitmap = mutableBitmap
        }

        val filename = "IMG_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/EVCam")
            }
        }
        Log.d("EVCAM", "Inserting image to MediaStore: $filename")
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        Log.d("EVCAM", "MediaStore insert result uri=$uri")
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                Log.d("EVCAM", "Compressed bitmap write result=$compressed")
            }
            // Write complete EXIF metadata
            val captureIso = if (!isIsoAuto) iso else null
            val captureShutter = if (!isShutterAuto) shutterSpeed else null
            writeExifTags(
                context, uri, camera2Engine,
                timestamp = System.currentTimeMillis(),
                location = liveLocation,
                enableGeotagging = enableGeotagging,
                iso = captureIso,
                shutterSpeedNs = captureShutter,
                isHdr = false,
                rawExif = exifInterface
            )
            Handler(Looper.getMainLooper()).post {
                Log.d("EVCAM", "Calling onPhotoSaved with uri=$uri")
                onPhotoSaved(bitmap, uri)
            }
        } else {
            Log.e("EVCAM", "MediaStore insert returned NULL uri!")
        }
    }
}

@android.annotation.SuppressLint("MissingPermission")
fun startVideoRecord(
    context: Context,
    camera2Engine: Camera2Engine,
    width: Int = 1920,
    height: Int = 1080,
    fps: Int = 30,
    audioEnabled: Boolean,
    recordSurface: android.view.Surface,
    useHighSpeed: Boolean = false,
    onMediaSaved: (android.graphics.Bitmap, android.net.Uri) -> Unit,
    onEvent: (Any) -> Unit
): VideoRecordController {
    // Use the output file already configured by CameraScreen's setupMediaRecorder call.
    val tempFile = camera2Engine.lastOutputFile
        ?: File(context.cacheDir, "temp_vid_${System.currentTimeMillis()}.mp4").absolutePath

    Log.d("EVCAM", "startVideoRecord called with file=$tempFile w=$width h=$height useHighSpeed=$useHighSpeed")

    if (useHighSpeed) {
        camera2Engine.startHighSpeedRecording(recordSurface, fps) {
            Log.d("EVCAM", "High speed video recording started event received")
            onEvent("Start")
        }
    } else {
        camera2Engine.startRecording(recordSurface) {
            Log.d("EVCAM", "Video recording started event received")
            onEvent("Start")
        }
    }

    return object : VideoRecordController {
        override fun stop() {
            camera2Engine.stopRecording {
                val fileObj = File(tempFile)
                Log.d("EVCAM", "stopRecording callback executed, tempFile exists=${fileObj.exists()}, size=${fileObj.length()}")
                
                val filename = "VID_${System.currentTimeMillis()}.mp4"
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/EVCam")
                    }
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    if (fileObj.exists() && fileObj.length() > 0) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            fileObj.inputStream().use { input ->
                                input.copyTo(out)
                            }
                        }
                    } else {
                        Log.e("EVCAM", "tempFile is empty or does not exist!")
                    }

                    // Generate video thumbnail for mini-preview
                    val thumbBitmap = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            android.media.ThumbnailUtils.createVideoThumbnail(fileObj, android.util.Size(300, 300), null)
                        } else {
                            @Suppress("DEPRECATION")
                            android.media.ThumbnailUtils.createVideoThumbnail(tempFile, MediaStore.Images.Thumbnails.MINI_KIND)
                        }
                    } catch (e: Exception) {
                        Log.e("EVCAM", "Failed creating video thumbnail", e)
                        null
                    }

                    if (fileObj.exists()) fileObj.delete()

                    Handler(Looper.getMainLooper()).post {
                        if (thumbBitmap != null) {
                            onMediaSaved(thumbBitmap, uri)
                        }
                        onEvent("Finalize")
                    }
                }
            }
        }
    }
}

fun fetchLatestMediaUri(context: android.content.Context): android.net.Uri? {
    try {
        val proj = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        var latestImgId = -1L
        var latestImgTime = -1L
        var latestVidId = -1L
        var latestVidTime = -1L

        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null, sortOrder)?.use { cursor ->
            if (cursor.moveToFirst()) {
                latestImgId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                latestImgTime = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED))
            }
        }

        context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, null, null, sortOrder)?.use { cursor ->
            if (cursor.moveToFirst()) {
                latestVidId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                latestVidTime = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED))
            }
        }

        if (latestVidTime > latestImgTime && latestVidId != -1L) {
            return android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, latestVidId)
        } else if (latestImgId != -1L) {
            return android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, latestImgId)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

@android.annotation.SuppressLint("MissingPermission")
fun takeComputationalHdrPhoto(
    context: Context,
    camera2Engine: Camera2Engine,
    flashMode: FlashMode,
    selectedFilter: ColorFilterMode,
    showWatermark: Boolean,
    watermarkElements: List<WatermarkElement>,
    liveLocation: Location?,
    liveAddress: Address?,
    enableGeotagging: Boolean,
    enableRawCapture: Boolean,
    aspectRatioMode: AspectRatioMode,
    deviceRotation: Int,
    isFrontCamera: Boolean = false,
    mirrorSelfie: Boolean = true,
    onProgress: (Int) -> Unit,
    onPhotoSaved: (Bitmap, Uri) -> Unit
) {
    camera2Engine.takeComputationalHdrBurst { bytesList ->
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val timestamp = System.currentTimeMillis()
            
            // Save the 5 raw components to app-specific external storage for viewing later
            val picsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            if (picsDir != null) {
                bytesList.forEachIndexed { index, bytes ->
                    val evFile = java.io.File(picsDir, "IMG_HDR_${timestamp}_EV${index}.jpg")
                    try {
                        java.io.FileOutputStream(evFile).use { fos ->
                            fos.write(bytes)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            val prefs = context.getSharedPreferences("evcam_prefs", Context.MODE_PRIVATE)
            val hdrParams = HdrParams(
                exposednessSigma = prefs.getFloat("hdr_exposedness_sigma", 0.4f).toDouble(),
                saturationBoost = prefs.getFloat("hdr_saturation_boost", 1.0f),
                normalBias = prefs.getFloat("hdr_normal_bias", 1.5f),
                contrastIntensity = prefs.getFloat("hdr_contrast_intensity", 1.0f)
            )

            val hdrBitmap = HdrProcessor.processHdrBurst(bytesList, hdrParams, null, onProgress)
            val rawExif = try {
                androidx.exifinterface.media.ExifInterface(bytesList[0].inputStream())
            } catch (e: Exception) { null }
            if (hdrBitmap != null) {
                // Read EXIF orientation from the first raw JPEG — this is the correct 0/90/180/270
                // rotation set by the camera driver, not the live tilt angle from the sensor.
                val exifRotation = try {
                    val exifOrientation = rawExif?.getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                    ) ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                    when (exifOrientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } catch (e: Exception) {
                    deviceRotation  // fallback
                }
                var bitmap = hdrBitmap
                if (exifRotation != 0) {
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(exifRotation.toFloat())
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                }

                if (isFrontCamera && mirrorSelfie) {
                    val matrix = android.graphics.Matrix()
                    matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                }

                val currentAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                var targetAspect = when (aspectRatioMode) {
                    AspectRatioMode.RATIO_4_3 -> 4f / 3f
                    AspectRatioMode.RATIO_16_9 -> 16f / 9f
                    AspectRatioMode.RATIO_1_1 -> 1f / 1f
                }
                
                if (bitmap.width < bitmap.height) {
                    targetAspect = 1f / targetAspect
                }

                if (kotlin.math.abs(currentAspect - targetAspect) > 0.02f) {
                    var cropWidth = bitmap.width
                    var cropHeight = bitmap.height

                    if (currentAspect > targetAspect) {
                        cropWidth = (bitmap.height * targetAspect).toInt()
                    } else {
                        cropHeight = (bitmap.width / targetAspect).toInt()
                    }

                    val cropX = ((bitmap.width - cropWidth) / 2).coerceAtLeast(0)
                    val cropY = ((bitmap.height - cropHeight) / 2).coerceAtLeast(0)

                    if (cropWidth > 0 && cropHeight > 0 && (cropX + cropWidth <= bitmap.width) && (cropY + cropHeight <= bitmap.height)) {
                        bitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)
                    }
                }

                val filename = "IMG_HDR_${timestamp}.jpg"
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/EVCam")
                    }
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }
                    // Write complete EXIF metadata for HDR photo
                    writeExifTags(
                        context, uri, camera2Engine,
                        timestamp = timestamp,
                        location = liveLocation,
                        enableGeotagging = enableGeotagging,
                        iso = null,
                        shutterSpeedNs = null,
                        isHdr = true,
                        rawExif = rawExif
                    )
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onPhotoSaved(bitmap, uri)
                    }
                }
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onProgress(-1) // Error
                }
            }
        }
    }
}

