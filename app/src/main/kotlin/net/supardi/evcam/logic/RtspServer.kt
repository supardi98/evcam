package net.supardi.evcam.logic

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.Image
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class RtspServer(
    val port: Int = 8554
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val threadPool = Executors.newCachedThreadPool()
    private val clients = ConcurrentHashMap.newKeySet<RtspClientHandler>()

    private var mediaCodec: MediaCodec? = null
    private var isEncoderRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        startH264Encoder()
        threadPool.execute {
            try {
                serverSocket = ServerSocket(port)
                Log.d("EVCAM_RTSP", "RTSP Server started on port $port")
                while (isRunning && serverSocket?.isClosed == false) {
                    val socket = serverSocket!!.accept()
                    val handler = RtspClientHandler(socket)
                    clients.add(handler)
                    threadPool.execute(handler)
                }
            } catch (e: Exception) {
                Log.e("EVCAM_RTSP", "RTSP Server socket exception", e)
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        stopH264Encoder()
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e("EVCAM_RTSP", "Error closing RTSP server socket", e)
        }
        clients.forEach { it.close() }
        clients.clear()
    }

    private fun startH264Encoder() {
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 640, 480)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 1000000)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec?.start()
            isEncoderRunning = true
            Log.d("EVCAM_RTSP", "H.264 MediaCodec Encoder started successfully")
        } catch (e: Exception) {
            Log.e("EVCAM_RTSP", "Failed to start H.264 MediaCodec encoder", e)
        }
    }

    fun requestKeyFrame() {
        try {
            val params = android.os.Bundle()
            params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            mediaCodec?.setParameters(params)
            Log.d("EVCAM_RTSP", "Requested instant H.264 I-Frame keyframe from MediaCodec")
        } catch (e: Exception) {
            Log.e("EVCAM_RTSP", "Failed to request sync keyframe", e)
        }
    }


    fun pushYuvFrame(image: Image) {
        if (!isEncoderRunning || clients.isEmpty()) return
        try {
            val codec = mediaCodec ?: return
            val inputIndex = codec.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
                inputBuffer.clear()

                val yBuffer = image.planes[0].buffer
                val uBuffer = image.planes[1].buffer
                val vBuffer = image.planes[2].buffer
                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()

                val bytes = ByteArray(ySize + uSize + vSize)
                yBuffer.get(bytes, 0, ySize)
                vBuffer.get(bytes, ySize, vSize)
                uBuffer.get(bytes, ySize + vSize, uSize)

                inputBuffer.put(bytes, 0, Math.min(bytes.size, inputBuffer.capacity()))
                codec.queueInputBuffer(inputIndex, 0, Math.min(bytes.size, inputBuffer.capacity()), System.nanoTime() / 1000, 0)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            while (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val h264Data = ByteArray(bufferInfo.size)
                    outputBuffer.get(h264Data)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        spsPpsBuffer = h264Data
                        Log.d("EVCAM_RTSP", "H.264 SPS/PPS Codec Config captured: ${h264Data.size} bytes")
                    } else {
                        val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                        clients.forEach { client ->
                            if (isKeyFrame && spsPpsBuffer != null) {
                                client.sendRtpNalUnit(spsPpsBuffer!!, bufferInfo.presentationTimeUs)
                            }
                            client.sendRtpNalUnit(h264Data, bufferInfo.presentationTimeUs)
                        }
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }

        } catch (e: Exception) {
            Log.e("EVCAM_RTSP", "Error encoding YUV to H264 NAL", e)
        }
    }

    @Volatile
    private var spsPpsBuffer: ByteArray? = null


    private inner class RtspClientHandler(private val socket: Socket) : Runnable {
        private var outputStream: OutputStream? = null
        private var clientRtpPort = 5000
        private var datagramSocket: DatagramSocket? = null
        private var sequenceNumber = 0
        private var timestamp = 0L

        override fun run() {
            try {
                outputStream = socket.getOutputStream()
                datagramSocket = DatagramSocket()
                val reader = socket.getInputStream().bufferedReader()

                while (isRunning && !socket.isClosed) {
                    val requestLine = reader.readLine() ?: break
                    if (requestLine.trim().isEmpty()) continue

                    Log.d("EVCAM_RTSP", "RTSP Request Line: $requestLine")

                    val tokens = requestLine.split(" ")
                    if (tokens.size < 3) continue
                    val method = tokens[0]

                    var cseq = "1"
                    var transportLine = ""

                    while (true) {
                        val header = reader.readLine()
                        if (header == null || header.isEmpty() || header == "\r") break
                        if (header.startsWith("CSeq:", ignoreCase = true)) {
                            cseq = header.substring(5).trim()
                        }
                        if (header.startsWith("Transport:", ignoreCase = true)) {
                            transportLine = header.substring(10).trim()
                        }
                    }







                    when (method) {
                        "OPTIONS" -> {
                            val response = "RTSP/1.0 200 OK\r\n" +
                                    "CSeq: $cseq\r\n" +
                                    "Public: OPTIONS, DESCRIBE, SETUP, TEARDOWN, PLAY\r\n\r\n"
                            outputStream?.write(response.toByteArray())
                            outputStream?.flush()
                        }
                        "DESCRIBE" -> {
                            val sprop = if (spsPpsBuffer != null) {
                                val b64 = android.util.Base64.encodeToString(spsPpsBuffer!!, android.util.Base64.NO_WRAP)
                                "a=fmtp:96 packetization-mode=1;sprop-parameter-sets=$b64\r\n"
                            } else {
                                "a=fmtp:96 packetization-mode=1\r\n"
                            }

                            val sdp = "v=0\r\n" +
                                    "o=- 0 0 IN IP4 0.0.0.0\r\n" +
                                    "s=EV Cam RTSP Stream\r\n" +
                                    "c=IN IP4 0.0.0.0\r\n" +
                                    "t=0 0\r\n" +
                                    "m=video 0 RTP/AVP 96\r\n" +
                                    "a=rtpmap:96 H264/90000\r\n" +
                                    sprop +
                                    "a=control:*\r\n"
                            val response = "RTSP/1.0 200 OK\r\n" +
                                    "CSeq: $cseq\r\n" +
                                    "Content-Type: application/sdp\r\n" +
                                    "Content-Length: ${sdp.length}\r\n\r\n" + sdp

                            outputStream?.write(response.toByteArray())
                            outputStream?.flush()
                        }
                        "SETUP" -> {
                            val transportResp = if (transportLine.isNotEmpty()) transportLine else "RTP/AVP;unicast;client_port=5000-5001;server_port=6000-6001"
                            val response = "RTSP/1.0 200 OK\r\n" +
                                    "CSeq: $cseq\r\n" +
                                    "Transport: $transportResp\r\n" +
                                    "Session: 12345678\r\n\r\n"
                            outputStream?.write(response.toByteArray())
                            outputStream?.flush()
                        }
                        "PLAY" -> {
                            val response = "RTSP/1.0 200 OK\r\n" +
                                    "CSeq: $cseq\r\n" +
                                    "Session: 12345678\r\n" +
                                    "Range: npt=0.000-\r\n\r\n"
                            outputStream?.write(response.toByteArray())
                            outputStream?.flush()
                            requestKeyFrame()
                        }
                        "TEARDOWN" -> {
                            val response = "RTSP/1.0 200 OK\r\n" +
                                    "CSeq: $cseq\r\n\r\n"
                            outputStream?.write(response.toByteArray())
                            outputStream?.flush()
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("EVCAM_RTSP", "RTSP Client disconnected: ${socket.inetAddress?.hostAddress}")
            } finally {
                close()
            }
        }

        fun sendRtpNalUnit(rawNalData: ByteArray, presentationTimeUs: Long) {
            try {
                // Strip Annex-B start codes (0x00000001 or 0x000001)
                var offset = 0
                if (rawNalData.size >= 4 && rawNalData[0] == 0.toByte() && rawNalData[1] == 0.toByte() && rawNalData[2] == 0.toByte() && rawNalData[3] == 1.toByte()) {
                    offset = 4
                } else if (rawNalData.size >= 3 && rawNalData[0] == 0.toByte() && rawNalData[1] == 0.toByte() && rawNalData[2] == 1.toByte()) {
                    offset = 3
                }

                val nalSize = rawNalData.size - offset
                if (nalSize <= 0) return

                val nalHeader = rawNalData[offset]
                val nalType = nalHeader.toInt() and 0x1F

                // Single NAL unit packet (<= 1400 bytes)
                if (nalSize <= 1400) {
                    val nalPayload = ByteArray(nalSize)
                    System.arraycopy(rawNalData, offset, nalPayload, 0, nalSize)
                    sendRtpPacket(nalPayload, presentationTimeUs, true)
                } else {
                    // FU-A Fragmentation Packet (> 1400 bytes)
                    val nri = (nalHeader.toInt() and 0x60).toByte()
                    val fuIndicator = (nri.toInt() or 28).toByte() // FU-A type 28

                    var nalOffset = offset + 1
                    var bytesRemaining = nalSize - 1
                    val maxChunkSize = 1350

                    var isFirst = true
                    while (bytesRemaining > 0) {
                        val chunkSize = Math.min(bytesRemaining, maxChunkSize)
                        val isLast = (bytesRemaining - chunkSize) == 0

                        var fuHeader = nalType.toByte()
                        if (isFirst) fuHeader = (fuHeader.toInt() or 0x80).toByte() // Start bit
                        if (isLast) fuHeader = (fuHeader.toInt() or 0x40).toByte() // End bit

                        val fuPayload = ByteArray(2 + chunkSize)
                        fuPayload[0] = fuIndicator
                        fuPayload[1] = fuHeader
                        System.arraycopy(rawNalData, nalOffset, fuPayload, 2, chunkSize)

                        sendRtpPacket(fuPayload, presentationTimeUs, isLast)

                        nalOffset += chunkSize
                        bytesRemaining -= chunkSize
                        isFirst = false
                    }
                }
            } catch (e: Exception) {
                Log.e("EVCAM_RTSP", "Error sending RTP packet", e)
            }
        }

        private fun sendRtpPacket(payload: ByteArray, presentationTimeUs: Long, isMarker: Boolean) {
            try {
                val rtpHeader = ByteArray(12)
                rtpHeader[0] = (0x80 or (if (isMarker) 0x80 else 0x00)).toByte()
                rtpHeader[1] = 96.toByte() // Payload type H.264

                sequenceNumber = (sequenceNumber + 1) and 0xFFFF
                rtpHeader[2] = (sequenceNumber shr 8).toByte()
                rtpHeader[3] = (sequenceNumber and 0xFF).toByte()

                val rtpTimestamp = (presentationTimeUs * 90 / 1000).toInt()
                rtpHeader[4] = (rtpTimestamp shr 24).toByte()
                rtpHeader[5] = (rtpTimestamp shr 16).toByte()
                rtpHeader[6] = (rtpTimestamp shr 8).toByte()
                rtpHeader[7] = (rtpTimestamp and 0xFF).toByte()

                rtpHeader[8] = 0x12.toByte()
                rtpHeader[9] = 0x34.toByte()
                rtpHeader[10] = 0x56.toByte()
                rtpHeader[11] = 0x78.toByte()

                val packetData = ByteArray(rtpHeader.size + payload.size)
                System.arraycopy(rtpHeader, 0, packetData, 0, rtpHeader.size)
                System.arraycopy(payload, 0, packetData, rtpHeader.size, payload.size)

                // 1. Send via UDP
                try {
                    val packet = DatagramPacket(packetData, packetData.size, socket.inetAddress, clientRtpPort)
                    datagramSocket?.send(packet)
                } catch (e: Exception) {}

                // 2. Send via TCP Interleaved ($ binary framing)
                try {
                    val tcpFrame = ByteArray(4 + packetData.size)
                    tcpFrame[0] = '$'.toByte()
                    tcpFrame[1] = 0.toByte()
                    val len = packetData.size
                    tcpFrame[2] = (len shr 8).toByte()
                    tcpFrame[3] = (len and 0xFF).toByte()
                    System.arraycopy(packetData, 0, tcpFrame, 4, packetData.size)
                    outputStream?.write(tcpFrame)
                    outputStream?.flush()
                } catch (e: Exception) {}
            } catch (e: Exception) {}
        }



        fun close() {
            try {
                datagramSocket?.close()
                socket.close()
            } catch (e: Exception) {}
            clients.remove(this)
        }
    }
}
