package net.supardi.evcam.logic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.pow

data class HdrParams(
    val exposednessSigma: Double = 0.4,
    val saturationBoost: Float = 1.0f,
    val normalBias: Float = 1.5f,
    val contrastIntensity: Float = 1.0f
)

object HdrProcessor {

    suspend fun processHdrBurst(
        images: List<ByteArray>,
        params: HdrParams = HdrParams(),
        downscaleWidth: Int? = null,
        onProgress: (Int) -> Unit
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (images.size < 5) {
            Log.e("HdrProcessor", "Not enough images for true HDR: ${images.size}")
            return@withContext null
        }

        try {
            onProgress(10)

            val options = BitmapFactory.Options().apply {
                inMutable = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
                if (downscaleWidth != null) {
                    inJustDecodeBounds = true
                    BitmapFactory.decodeByteArray(images[0], 0, images[0].size, this)
                    inSampleSize = (outWidth / downscaleWidth).coerceAtLeast(1)
                    inJustDecodeBounds = false
                }
            }
            
            val bitmaps = images.map { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
            
            onProgress(20)
            
            val width = bitmaps[0].width
            val height = bitmaps[0].height

            // Sort bitmaps by brightness
            val sortedBitmaps = bitmaps.sortedBy { bmp ->
                var lumaSum = 0L
                val samplePixels = IntArray(1000)
                bmp.getPixels(samplePixels, 0, 100, width / 2, height / 2, 100, 10)
                for (p in samplePixels) {
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    lumaSum += (r * 0.299 + g * 0.587 + b * 0.114).toLong()
                }
                lumaSum
            }

            // [0]=Darkest (-4), [1]=Dark (-2), [2]=Normal (0), [3]=Bright (+2), [4]=Brightest (+4)
            val bmpD2 = sortedBitmaps[0]
            val bmpD1 = sortedBitmaps[1]
            val bmpN = sortedBitmaps[2]
            val bmpB1 = sortedBitmaps[3]
            val bmpB2 = sortedBitmaps[4]

            onProgress(30)
            
            val rowD2 = IntArray(width)
            val rowD1 = IntArray(width)
            val rowN = IntArray(width)
            val rowB1 = IntArray(width)
            val rowB2 = IntArray(width)
            val rowResult = IntArray(width)

            val exposednessTable = FloatArray(256)
            for (i in 0..255) {
                val v = (i / 255.0) - 0.5
                exposednessTable[i] = exp(-(v * v) / (2.0 * params.exposednessSigma * params.exposednessSigma)).toFloat()
            }

            val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            for (y in 0 until height) {
                if (y % 100 == 0) {
                    val progress = 30 + ((y.toFloat() / height) * 60).toInt()
                    onProgress(progress)
                }

                bmpD2.getPixels(rowD2, 0, width, 0, y, width, 1)
                bmpD1.getPixels(rowD1, 0, width, 0, y, width, 1)
                bmpN.getPixels(rowN, 0, width, 0, y, width, 1)
                bmpB1.getPixels(rowB1, 0, width, 0, y, width, 1)
                bmpB2.getPixels(rowB2, 0, width, 0, y, width, 1)

                for (x in 0 until width) {
                    val frames = arrayOf(rowD2[x], rowD1[x], rowN[x], rowB1[x], rowB2[x])
                    
                    // Base weights for -4, -2, 0, +2, +4
                    val baseWeights = arrayOf(0.7f, 1.0f, params.normalBias, 1.0f, 0.7f)
                    
                    // Reference luma from the normal frame
                    val pN = frames[2]
                    val rN = (pN shr 16) and 0xFF
                    val gN = (pN shr 8) and 0xFF
                    val bN = pN and 0xFF
                    val lumaN = (rN * 0.299f + gN * 0.587f + bN * 0.114f).toInt()

                    var sumR = 0f
                    var sumG = 0f
                    var sumB = 0f
                    var sumW = 0f

                    for (f in 0 until 5) {
                        val pF = frames[f]
                        val r = (pF shr 16) and 0xFF
                        val g = (pF shr 8) and 0xFF
                        val b = pF and 0xFF
                        val luma = (r * 0.299f + g * 0.587f + b * 0.114f).toInt().coerceIn(0, 255)

                        var wF = exposednessTable[luma]
                        if (f == 2) wF *= params.normalBias
                        wF += 1e-5f

                        sumR += r * wF
                        sumG += g * wF
                        sumB += b * wF
                        sumW += wF
                    }

                    var finalR = sumR / sumW
                    var finalG = sumG / sumW
                    var finalB = sumB / sumW

                    // Color Saturation
                    val lumaF = finalR * 0.299f + finalG * 0.587f + finalB * 0.114f
                    finalR = lumaF + (finalR - lumaF) * params.saturationBoost
                    finalG = lumaF + (finalG - lumaF) * params.saturationBoost
                    finalB = lumaF + (finalB - lumaF) * params.saturationBoost

                    // Smooth S-Curve Contrast
                    fun applyContrast(v: Float): Float {
                        val norm = (v / 255f).coerceIn(0f, 1f)
                        val curve = if (norm < 0.5f) 2f * norm * norm else 1f - 2f * (1f - norm) * (1f - norm)
                        val interpolated = norm + (curve - norm) * params.contrastIntensity
                        return interpolated * 255f
                    }

                    val outR = applyContrast(finalR).toInt().coerceIn(0, 255)
                    val outG = applyContrast(finalG).toInt().coerceIn(0, 255)
                    val outB = applyContrast(finalB).toInt().coerceIn(0, 255)

                    rowResult[x] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
                }
                finalBitmap.setPixels(rowResult, 0, width, 0, y, width, 1)
            }

            onProgress(95)
            bitmaps.forEach { it.recycle() }
            
            onProgress(100)
            finalBitmap
        } catch (e: Exception) {
            Log.e("HdrProcessor", "Error blending HDR", e)
            null
        }
    }
}
