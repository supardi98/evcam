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
        if (images.size < 5) {
            Log.e("HdrProcessor", "Not enough images for true HDR: ${images.size}")
            return@withContext null
        }

        try {
            onProgress(10)
            
            val options = BitmapFactory.Options().apply {
                inMutable = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
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

            val sigma = 50.0
            val twoSigmaSq = 2.0 * sigma * sigma
            val weightTable = FloatArray(256)
            for (i in 0..255) {
                weightTable[i] = exp(-((i - 128.0).pow(2)) / twoSigmaSq).toFloat()
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
                    val pN = rowN[x]
                    val rN = (pN shr 16) and 0xFF
                    val gN = (pN shr 8) and 0xFF
                    val bN = pN and 0xFF
                    val lumaN = (rN * 0.299f + gN * 0.587f + bN * 0.114f).toInt()

                    var sumR = 0f
                    var sumG = 0f
                    var sumB = 0f
                    var sumW = 0f

                    // Process each frame
                    val frames = arrayOf(rowD2[x], rowD1[x], rowN[x], rowB1[x], rowB2[x])
                    val baseWeights = arrayOf(0.8f, 1.2f, 2.0f, 1.2f, 0.8f) // Favor normal
                    
                    for (f in 0 until 5) {
                        val pF = frames[f]
                        val rF = (pF shr 16) and 0xFF
                        val gF = (pF shr 8) and 0xFF
                        val bF = pF and 0xFF
                        val lumaF = (rF * 0.299f + gF * 0.587f + bF * 0.114f).toInt()
                        
                        var wF = weightTable[lumaF] * baseWeights[f]
                        
                        // Ghosting reduction: if color difference from Normal is extreme, kill weight
                        if (f != 2) {
                            val colorDiff = kotlin.math.abs(rF - rN) + kotlin.math.abs(gF - gN) + kotlin.math.abs(bF - bN)
                            if (colorDiff > 120) {
                                wF *= 0.05f // heavy penalty for ghosts
                            }
                        }
                        
                        // Local contrast trick: boost midtones based on reference
                        if (f == 2) {
                            wF *= 1.5f
                        } else if (f < 2 && lumaN > 180) { // Recovery from darks
                            wF *= 4f
                        } else if (f > 2 && lumaN < 70) { // Recovery from brights
                            wF *= 4f
                        }

                        wF += 1e-5f
                        sumR += rF * wF
                        sumG += gF * wF
                        sumB += bF * wF
                        sumW += wF
                    }

                    // Clarity injection (boost contrast slightly)
                    var finalR = sumR / sumW
                    var finalG = sumG / sumW
                    var finalB = sumB / sumW
                    
                    val contrast = 1.15f
                    finalR = (((finalR / 255f) - 0.5f) * contrast + 0.5f) * 255f
                    finalG = (((finalG / 255f) - 0.5f) * contrast + 0.5f) * 255f
                    finalB = (((finalB / 255f) - 0.5f) * contrast + 0.5f) * 255f

                    val outR = finalR.toInt().coerceIn(0, 255)
                    val outG = finalG.toInt().coerceIn(0, 255)
                    val outB = finalB.toInt().coerceIn(0, 255)
                    
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
