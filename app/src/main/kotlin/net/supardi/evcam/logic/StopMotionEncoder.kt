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
        width: Int,
        height: Int,
        outputPath: String,
        onProgress: (Float) -> Unit,
        onDone: (Boolean) -> Unit
    ) {
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
                        val inputBuf = codec.getInputBuffer(inputBufIdx)!!
                        inputBuf.clear()
                        val yuvBytes = bitmapToNv21(scaledBitmap)
                        inputBuf.put(yuvBytes)
                        val presentationTimeUs = index * frameDurationUs
                        codec.queueInputBuffer(inputBufIdx, 0, yuvBytes.size, presentationTimeUs, 0)
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

    private fun bitmapToNv21(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val nv21 = ByteArray(width * height * 3 / 2)
        val ySize = width * height
        var yIdx = 0
        var uvIdx = ySize

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = pixels[j * width + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                nv21[yIdx++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    nv21[uvIdx++] = v.coerceIn(0, 255).toByte()
                    nv21[uvIdx++] = u.coerceIn(0, 255).toByte()
                }
            }
        }
        return nv21
    }
}
