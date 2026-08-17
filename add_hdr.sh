#!/bin/bash
cat << 'INNER_EOF' >> app/src/main/kotlin/net/supardi/evcam/logic/CameraUtil.kt

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
            val hdrBitmap = HdrProcessor.processHdrBurst(bytesList, onProgress)
            if (hdrBitmap != null) {
                // For simplicity, we just save the HDR bitmap directly and apply aspect ratio
                // Rotation from deviceRotation:
                val rotationDegrees = deviceRotation
                var bitmap = hdrBitmap
                if (rotationDegrees != 0) {
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(rotationDegrees.toFloat())
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                }

                if (isFrontCamera && mirrorSelfie) {
                    val matrix = android.graphics.Matrix()
                    matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                }

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

                val filename = "IMG_HDR_${System.currentTimeMillis()}.jpg"
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
INNER_EOF
bash add_hdr.sh
