package net.supardi.evcam.logic


import android.content.ContentValues
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

import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import net.supardi.evcam.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executor

fun formatLocationElement(format: String, location: Location?, address: Address?): String {
    if (location == null) return "[Location]"
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

fun takePhoto(
    imageCapture: ImageCapture, 
    context: Context, 
    executor: Executor,
    flashMode: FlashMode,
    selectedFilter: ColorFilterMode,
    showWatermark: Boolean,
    watermarkElements: List<WatermarkElement>,
    liveLocation: Location?,
    liveAddress: Address?,
    enableGeotagging: Boolean,
    enableRawCapture: Boolean,
    aspectRatioMode: AspectRatioMode,
    onPhotoSaved: (Bitmap, Uri) -> Unit
) {
    if (enableRawCapture) {
        Toast.makeText(context, "RAW Capture is enabled (Saving as DNG is experimental)", Toast.LENGTH_SHORT).show()
    }
    
    val targetFlash = when (flashMode) {
        FlashMode.ON -> ImageCapture.FLASH_MODE_ON
        FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
    }
    imageCapture.flashMode = targetFlash

    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    var bitmap = image.toBitmap()
                    image.close()
                    
                    if (selectedFilter != ColorFilterMode.NORMAL) {
                        try {
                            val filteredBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(filteredBitmap)
                            val paint = Paint().apply {
                                colorFilter = android.graphics.ColorMatrixColorFilter(selectedFilter.matrixValues)
                            }
                            canvas.drawBitmap(bitmap, 0f, 0f, paint)
                            bitmap = filteredBitmap
                        } catch (e: Exception) {}
                    }
                    
                    if (aspectRatioMode == AspectRatioMode.RATIO_1_1) {
                        val size = Math.min(bitmap.width, bitmap.height)
                        val cropX = (bitmap.width - size) / 2
                        val cropY = (bitmap.height - size) / 2
                        bitmap = Bitmap.createBitmap(bitmap, cropX, cropY, size, size)
                    }
                    
                    var location: Location? = null
                    if (enableGeotagging && ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                        location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) 
                            ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    }
                    
                    val resultBitmap = if (showWatermark) {
                        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        val canvas = Canvas(mutableBitmap)
                        val paint = Paint().apply {
                            color = Color.WHITE
                            isAntiAlias = true
                            setShadowLayer(5f, 2f, 2f, Color.BLACK)
                        }
                        
                        val marginX = bitmap.width * 0.05f
                        val marginY = bitmap.height * 0.05f
                        val textSize = bitmap.height * 0.03f
                        val lineSpacing = textSize * 1.2f
                        paint.textSize = textSize
                        
                        WatermarkQuadrant.values().forEach { quadrant ->
                            val elements = watermarkElements.filter { it.quadrant == quadrant }
                            if (elements.isNotEmpty()) {
                                var currentY = when (quadrant) {
                                    WatermarkQuadrant.TOP_LEFT, WatermarkQuadrant.TOP_RIGHT -> marginY + textSize
                                    WatermarkQuadrant.BOTTOM_LEFT, WatermarkQuadrant.BOTTOM_RIGHT -> bitmap.height - marginY
                                }
                                
                                val quadrantElements = if (quadrant == WatermarkQuadrant.BOTTOM_LEFT || quadrant == WatermarkQuadrant.BOTTOM_RIGHT) elements.reversed() else elements
                                val maxTextWidth = (bitmap.width * 0.45f).toInt()
                                val textPaint = TextPaint(paint)
                                
                                quadrantElements.forEach { element ->
                                    val text = when (element.type) {
                                        WatermarkElementType.TEXT -> element.content
                                        WatermarkElementType.LOCATION -> formatLocationElement(element.content, location ?: liveLocation, liveAddress)
                                        WatermarkElementType.DATE -> formatDateElement(element.content)
                                    }
                                    
                                    textPaint.textSize = textSize * (element.size / 14f)
                                    
                                    val alignment = when (quadrant) {
                                        WatermarkQuadrant.TOP_LEFT, WatermarkQuadrant.BOTTOM_LEFT -> Layout.Alignment.ALIGN_NORMAL
                                        WatermarkQuadrant.TOP_RIGHT, WatermarkQuadrant.BOTTOM_RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                                    }
                                    
                                    val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxTextWidth).setAlignment(alignment).build()
                                    } else {
                                        @Suppress("DEPRECATION")
                                        StaticLayout(text, textPaint, maxTextWidth, alignment, 1.0f, 0.0f, false)
                                    }
                                    
                                    val currentX = when (quadrant) {
                                        WatermarkQuadrant.TOP_LEFT, WatermarkQuadrant.BOTTOM_LEFT -> marginX
                                        WatermarkQuadrant.TOP_RIGHT, WatermarkQuadrant.BOTTOM_RIGHT -> bitmap.width - marginX - maxTextWidth
                                    }
                                    
                                    if (quadrant == WatermarkQuadrant.BOTTOM_LEFT || quadrant == WatermarkQuadrant.BOTTOM_RIGHT) {
                                        currentY -= layout.height
                                    }
                                    
                                    canvas.save()
                                    canvas.translate(currentX, currentY)
                                    layout.draw(canvas)
                                    canvas.restore()
                                    
                                    if (quadrant == WatermarkQuadrant.TOP_LEFT || quadrant == WatermarkQuadrant.TOP_RIGHT) {
                                        currentY += layout.height + lineSpacing
                                    } else {
                                        currentY -= lineSpacing
                                    }
                                }
                            }
                        }
                        mutableBitmap
                    } else bitmap
                    
                    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/evcam")
                        }
                    }
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            resultBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                        }
                        if (enableGeotagging && location != null) {
                            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                                val exif = android.media.ExifInterface(pfd.fileDescriptor)
                                fun convertLocationToExifFormat(coord: Double): String {
                                    val absCoord = Math.abs(coord)
                                    val degree = absCoord.toInt()
                                    val minute = ((absCoord - degree) * 60).toInt()
                                    val second = (((absCoord - degree) * 60) - minute) * 60
                                    return "$degree/1,$minute/1,${(second * 1000).toInt()}/1000"
                                }
                                exif.setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE, convertLocationToExifFormat(location.latitude))
                                exif.setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE_REF, if (location.latitude > 0) "N" else "S")
                                exif.setAttribute(android.media.ExifInterface.TAG_GPS_LONGITUDE, convertLocationToExifFormat(location.longitude))
                                exif.setAttribute(android.media.ExifInterface.TAG_GPS_LONGITUDE_REF, if (location.longitude > 0) "E" else "W")
                                exif.saveAttributes()
                            }
                        }
                        
                        Handler(Looper.getMainLooper()).post {
                            onPhotoSaved(resultBitmap, uri)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Evcam", "Failed to save photo", e)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("Evcam", "Photo capture failed", exception)
            }
        }
    )
}

@android.annotation.SuppressLint("MissingPermission")
fun startVideoRecord(
    videoCapture: VideoCapture<Recorder>,
    context: Context,
    audioEnabled: Boolean,
    onEvent: (VideoRecordEvent) -> Unit
): Recording {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera")
        }
    }

    val mediaStoreOutputOptions = MediaStoreOutputOptions
        .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        .setContentValues(contentValues)
        .build()

    val pendingRecording = videoCapture.output
        .prepareRecording(context, mediaStoreOutputOptions)
        
    if (audioEnabled && ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        pendingRecording.withAudioEnabled()
    }

    return pendingRecording.start(ContextCompat.getMainExecutor(context), onEvent)
}
