package net.supardi.evcam.logic

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object YuvToJpegConverter {
    fun yuv420ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        val ySize = yBuffer.remaining()
        val nv21 = ByteArray(width * height * 3 / 2)

        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride

        if (rowStride == width) {
            yBuffer.get(nv21, 0, ySize)
        } else {
            var outputOffset = 0
            for (row in 0 until height) {
                yBuffer.position(row * rowStride)
                yBuffer.get(nv21, outputOffset, width)
                outputOffset += width
            }
        }

        val chromaRowStride = vPlane.rowStride
        val chromaPixelStride = vPlane.pixelStride
        val chromaWidth = width / 2
        val chromaHeight = height / 2

        var chromaOffset = width * height
        if (chromaPixelStride == 2 && chromaRowStride == width && vBuffer.remaining() >= width * chromaHeight - 1) {
            // Semi-planar NV21 layout (V and U interlaced)
            val vSize = vBuffer.remaining()
            vBuffer.get(nv21, chromaOffset, vSize)
        } else {
            // Manual pixel-by-pixel copy for strided YUV_420_888 formats
            for (row in 0 until chromaHeight) {
                for (col in 0 until chromaWidth) {
                    val vIndex = row * chromaRowStride + col * chromaPixelStride
                    val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
                    if (vIndex < vBuffer.limit() && uIndex < uBuffer.limit()) {
                        nv21[chromaOffset++] = vBuffer.get(vIndex)
                        nv21[chromaOffset++] = uBuffer.get(uIndex)
                    }
                }
            }
        }
        return nv21
    }

    fun convertYuvToJpeg(image: Image, quality: Int = 75, rotationDegrees: Int = 0): ByteArray {
        val nv21 = yuv420ToNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), quality, out)
        val jpegBytes = out.toByteArray()

        if (rotationDegrees != 0) {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            if (bitmap != null) {
                val matrix = android.graphics.Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                val rotatedBitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                val rotatedOut = ByteArrayOutputStream()
                rotatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, rotatedOut)
                bitmap.recycle()
                rotatedBitmap.recycle()
                return rotatedOut.toByteArray()
            }
        }
        return jpegBytes
    }
}
