package net.supardi.evcam.logic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.pow

object HdrProcessor {

    suspend fun processHdrBurst(
        images: List<ByteArray>,
        onProgress: (Int) -> Unit
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (images.size < 3) {
            Log.e("HdrProcessor", "Not enough images for HDR: ${images.size}")
            return@withContext null
        }

        try {
            onProgress(10)
            
            // Decode the JPEGs
            val bitmaps = images.map { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            
            onProgress(30)
            
            val width = bitmaps[0].width
            val height = bitmaps[0].height
            val totalPixels = width * height

            // Sort bitmaps by brightness: [0] = dark, [1] = normal, [2] = bright
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

            val darkBmp = sortedBitmaps[0]
            val normBmp = sortedBitmaps[1]
            val brightBmp = sortedBitmaps[2]

            val darkPixels = IntArray(totalPixels)
            val normPixels = IntArray(totalPixels)
            val brightPixels = IntArray(totalPixels)
            val resultPixels = IntArray(totalPixels)

            onProgress(40)
            darkBmp.getPixels(darkPixels, 0, width, 0, 0, width, height)
            normBmp.getPixels(normPixels, 0, width, 0, 0, width, height)
            brightBmp.getPixels(brightPixels, 0, width, 0, 0, width, height)
            
            onProgress(50)

            val sigma = 50.0
            val twoSigmaSq = 2.0 * sigma * sigma
            val weightTable = FloatArray(256)
            for (i in 0..255) {
                weightTable[i] = exp(-((i - 128.0).pow(2)) / twoSigmaSq).toFloat()
            }

            val batchSize = totalPixels / 10
            for (i in 0 until totalPixels) {
                if (i % batchSize == 0) {
                    val progress = 50 + ((i.toFloat() / totalPixels) * 40).toInt()
                    onProgress(progress)
                }

                val pDark = darkPixels[i]
                val pNorm = normPixels[i]
                val pBright = brightPixels[i]

                val rD = (pDark shr 16) and 0xFF
                val gD = (pDark shr 8) and 0xFF
                val bD = pDark and 0xFF
                
                val rN = (pNorm shr 16) and 0xFF
                val gN = (pNorm shr 8) and 0xFF
                val bN = pNorm and 0xFF
                
                val rB = (pBright shr 16) and 0xFF
                val gB = (pBright shr 8) and 0xFF
                val bB = pBright and 0xFF

                val lumaD = (rD * 0.299f + gD * 0.587f + bD * 0.114f).toInt().coerceIn(0, 255)
                val lumaN = (rN * 0.299f + gN * 0.587f + bN * 0.114f).toInt().coerceIn(0, 255)
                val lumaB = (rB * 0.299f + gB * 0.587f + bB * 0.114f).toInt().coerceIn(0, 255)

                val wD = weightTable[lumaD] + 1e-5f
                val wN = weightTable[lumaN] + 1e-5f
                val wB = weightTable[lumaB] + 1e-5f
                
                var finalWd = wD
                var finalWn = wN * 1.5f 
                var finalWb = wB

                if (lumaN > 220) finalWd *= 3f
                if (lumaN < 40) finalWb *= 3f

                val sumW = finalWd + finalWn + finalWb

                val rFinal = ((rD * finalWd + rN * finalWn + rB * finalWb) / sumW).toInt().coerceIn(0, 255)
                val gFinal = ((gD * finalWd + gN * finalWn + gB * finalWb) / sumW).toInt().coerceIn(0, 255)
                val bFinal = ((bD * finalWd + bN * finalWn + bB * finalWb) / sumW).toInt().coerceIn(0, 255)

                resultPixels[i] = (0xFF shl 24) or (rFinal shl 16) or (gFinal shl 8) or bFinal
            }

            onProgress(95)
            val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            finalBitmap.setPixels(resultPixels, 0, width, 0, 0, width, height)
            
            bitmaps.forEach { it.recycle() }
            
            onProgress(100)
            finalBitmap
        } catch (e: Exception) {
            Log.e("HdrProcessor", "Error blending HDR", e)
            null
        }
    }
}
