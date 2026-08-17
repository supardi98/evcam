package net.supardi.evcam.logic


import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import kotlin.math.abs

class ProAnalyzer(
    var enableHistogram: Boolean = false,
    var enableFocusPeaking: Boolean = false,
    private val onHistogramUpdate: (IntArray) -> Unit,
    private val onPeakingUpdate: (Bitmap?) -> Unit
) {

    private var cachedBitmap: Bitmap? = null
    private var pixels: IntArray? = null

    fun analyze(image: Image, rotationDegrees: Int) {
        try {
            if (!enableHistogram && !enableFocusPeaking) {
                return
            }

            val width = image.width
            val height = image.height
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            if (enableHistogram) {
                val histogram = IntArray(256)
                for (i in data.indices step 4) {
                    val pixel = data[i].toInt() and 0xFF
                    histogram[pixel]++
                }
                onHistogramUpdate(histogram)
            }

            if (enableFocusPeaking) {
                val isPortrait = rotationDegrees == 90 || rotationDegrees == 270
                val outWidth = if (isPortrait) height else width
                val outHeight = if (isPortrait) width else height

                if (cachedBitmap == null || cachedBitmap!!.width != outWidth || cachedBitmap!!.height != outHeight) {
                    cachedBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
                    pixels = IntArray(outWidth * outHeight)
                }
                val outPixels = pixels!!
                val threshold = 40 // Edge detection threshold
                val greenColor = Color.GREEN

                // Clear previous pixels
                java.util.Arrays.fill(outPixels, Color.TRANSPARENT)

                for (y in 0 until height - 2 step 2) {
                    for (x in 0 until width - 2 step 2) {
                        val idx = y * rowStride + x
                        val p = data[idx].toInt() and 0xFF
                        val px = data[idx + 2].toInt() and 0xFF
                        val py = data[idx + rowStride * 2].toInt() and 0xFF
                        
                        val diffX = abs(p - px)
                        val diffY = abs(p - py)
                        
                        if (diffX + diffY > threshold) {
                            val rx = when (rotationDegrees) {
                                90 -> y
                                180 -> width - 1 - x
                                270 -> height - 1 - y
                                else -> x
                            }
                            val ry = when (rotationDegrees) {
                                90 -> width - 1 - x
                                180 -> height - 1 - y
                                270 -> x
                                else -> y
                            }

                            
                            val pixelIdx = ry * outWidth + rx
                            if (pixelIdx >= 0 && pixelIdx < outPixels.size) {
                                outPixels[pixelIdx] = greenColor
                                if (rx + 1 < outWidth && pixelIdx + 1 < outPixels.size) outPixels[pixelIdx + 1] = greenColor
                                if (ry + 1 < outHeight && pixelIdx + outWidth < outPixels.size) outPixels[pixelIdx + outWidth] = greenColor
                                if (rx + 1 < outWidth && ry + 1 < outHeight && pixelIdx + outWidth + 1 < outPixels.size) outPixels[pixelIdx + outWidth + 1] = greenColor
                            }
                        }
                    }
                }
                cachedBitmap!!.setPixels(outPixels, 0, outWidth, 0, 0, outWidth, outHeight)
                onPeakingUpdate(cachedBitmap)
            } else {
                if (cachedBitmap != null) {
                    onPeakingUpdate(null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }
}
