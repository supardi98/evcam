package net.supardi.evcam.logic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File

object StopMotionEncoder {

    fun encode(
        frames: List<File>,
        fps: Int,
        originalWidth: Int,
        originalHeight: Int,
        outputPath: String,
        onProgress: (Float) -> Unit,
        onDone: (Boolean) -> Unit
    ) {
        val width = (originalWidth + 15) / 16 * 16
        val height = (originalHeight + 15) / 16 * 16

        Thread {
            try {
                val mime = MediaFormat.MIMETYPE_VIDEO_AVC
                val format = MediaFormat.createVideoFormat(mime, width, height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                    setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }

                val codec = MediaCodec.createEncoderByType(mime)
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()

                val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                var trackIndex = -1
                val bufferInfo = MediaCodec.BufferInfo()
                val frameDurationUs = 1_000_000L / fps

                frames.forEachIndexed { index, file ->
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
                    val scaledBitmap = if (bitmap.width != width || bitmap.height != height) {
                        Bitmap.createScaledBitmap(bitmap, width, height, true).also { bitmap.recycle() }
                    } else bitmap

                    val inputBufIdx = codec.dequeueInputBuffer(10_000)
                    if (inputBufIdx >= 0) {
                        val inputImage = codec.getInputImage(inputBufIdx)
                        if (inputImage != null) {
                            copyBitmapToImage(scaledBitmap, inputImage)
                            val presentationTimeUs = index * frameDurationUs
                            // The buffer size is the total size of planes. But queueInputBuffer needs the size of the whole buffer, or 0 to capacity.
                            // Better yet, just pass codec.getInputBuffer(inputBufIdx)!!.capacity() or similar. Wait, for COLOR_FormatYUV420Flexible, we can pass 0 for size?
                            // Actually, queueInputBuffer for video encoders using Image usually uses the size of the buffer or 0 doesn't matter if it's Image.
                            // But let's get the buffer and its capacity.
                            val inputBuf = codec.getInputBuffer(inputBufIdx)!!
                            codec.queueInputBuffer(inputBufIdx, 0, inputBuf.capacity(), presentationTimeUs, 0)
                        }
                        scaledBitmap.recycle()
                    }

                    trackIndex = drainEncoder(codec, bufferInfo, muxer, trackIndex)
                    onProgress((index + 1).toFloat() / frames.size)
                }

                // Signal end of stream
                val inputBufIdx = codec.dequeueInputBuffer(10_000)
                if (inputBufIdx >= 0) {
                    codec.queueInputBuffer(inputBufIdx, 0, 0, frames.size * frameDurationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                drainEncoder(codec, bufferInfo, muxer, trackIndex, endOfStream = true)

                muxer.stop()
                muxer.release()
                codec.stop()
                codec.release()

                onDone(true)
            } catch (e: Exception) {
                Log.e("StopMotionEncoder", "Encoding failed", e)
                onDone(false)
            }
        }.start()
    }

    private fun drainEncoder(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        currentTrackIndex: Int,
        endOfStream: Boolean = false
    ): Int {
        var trackIdx = currentTrackIndex
        while (true) {
            val outputBufIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    trackIdx = muxer.addTrack(newFormat)
                    muxer.start()
                }
                outputBufIdx >= 0 -> {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && trackIdx >= 0) {
                        val outputBuf = codec.getOutputBuffer(outputBufIdx)!!
                        outputBuf.position(bufferInfo.offset)
                        outputBuf.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIdx, outputBuf, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputBufIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return trackIdx
                }
                else -> {
                    if (!endOfStream) return trackIdx
                }
            }
        }
    }

    private fun copyBitmapToImage(bitmap: Bitmap, image: android.media.Image) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride

        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        for (j in 0 until height) {
            var yIdx = j * yRowStride
            var uIdx = (j / 2) * uRowStride
            var vIdx = (j / 2) * vRowStride

            for (i in 0 until width) {
                val pixel = pixels[j * width + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuffer.put(yIdx++, y.coerceIn(0, 255).toByte())

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    uBuffer.put(uIdx, u.coerceIn(0, 255).toByte())
                    vBuffer.put(vIdx, v.coerceIn(0, 255).toByte())
                }
                
                if (i % 2 == 1) { // Advance UV pointers every 2 pixels
                    uIdx += uPixelStride
                    vIdx += vPixelStride
                }
            }
        }
    }
}
