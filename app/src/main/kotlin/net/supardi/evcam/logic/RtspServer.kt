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

    private fun stopH264Encoder() {
        isEncoderRunning = false
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null
        } catch (e: Exception) {}
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
                    clients.forEach { client ->
                        client.sendRtpNalUnit(h264Data, bufferInfo.presentationTimeUs)
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
        } catch (e: Exception) {
            Log.e("EVCAM_RTSP", "Error encoding YUV to H264 NAL", e)
        }
    }

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
                val inputStream = socket.getInputStream()
                val reader = inputStream.bufferedReader()

                while (isRunning && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) continue

                    Log.d("EVCAM_RTSP", "RTSP Request: $line")

                    val tokens = line.split(" ")
                    if (tokens.size < 3) continue
                    val method = tokens[0]

                    var cseq = "1"
                    var transportLine = ""
                    var lineRead: String?
                    while (reader.readLine().also { lineRead = it } != null) {
                        if (lineRead.isNullOrEmpty()) break
                        if (lineRead!!.startsWith("CSeq:", ignoreCase = true)) {
                            cseq = lineRead!!.substring(5).trim()
                        }
                        if (lineRead!!.startsWith("Transport:", ignoreCase = true)) {
                            transportLine = lineRead!!.substring(10).trim()
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
                            val sdp = "v=0\r\n" +
                                    "o=- 0 0 IN IP4 0.0.0.0\r\n" +
                                    "s=EV Cam RTSP Stream\r\n" +
                                    "c=IN IP4 0.0.0.0\r\n" +
                                    "t=0 0\r\n" +
                                    "m=video 0 RTP/AVP 96\r\n" +
                                    "a=rtpmap:96 H264/90000\r\n" +
                                    "a=fmtp:96 packetization-mode=1\r\n" +
                                    "a=control:track0\r\n"
                            val response = "RTSP/1.0 200 OK\r\n" +
                                    "CSeq: $cseq\r\n" +
                                    "Content-Type: application/sdp\r\n" +
                                    "Content-Length: ${sdp.length}\r\n\r\n" + sdp
                            outputStream?.write(response.toByteArray())
                            outputStream?.flush()
                        }
                        "SETUP" -> {
                            // Extract client_port
                            if (transportLine.contains("client_port=")) {
                                try {
                                    val portPart = transportLine.substringAfter("client_port=").substringBefore("-").substringBefore(";")
                                    clientRtpPort = portPart.toInt()
                                } catch (e: Exception) {}
                            }
                            val response = "RTSP/1.0 200 OK\r\n" +
                                    "CSeq: $cseq\r\n" +
                                    "Transport: RTP/AVP;unicast;client_port=$clientRtpPort-${clientRtpPort + 1};server_port=6000-6001\r\n" +
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

        fun sendRtpNalUnit(nalData: ByteArray, presentationTimeUs: Long) {
            try {
                val rtpHeader = ByteArray(12)
                rtpHeader[0] = 0x80.toByte() // Version 2
                rtpHeader[1] = 96.toByte() // Payload type H.264 (96)

                // Sequence number (2 bytes)
                sequenceNumber = (sequenceNumber + 1) and 0xFFFF
                rtpHeader[2] = (sequenceNumber shr 8).toByte()
                rtpHeader[3] = (sequenceNumber and 0xFF).toByte()

                // Timestamp (4 bytes at 90kHz clock)
                val rtpTimestamp = (presentationTimeUs * 90 / 1000).toInt()
                rtpHeader[4] = (rtpTimestamp shr 24).toByte()
                rtpHeader[5] = (rtpTimestamp shr 16).toByte()
                rtpHeader[6] = (rtpTimestamp shr 8).toByte()
                rtpHeader[7] = (rtpTimestamp and 0xFF).toByte()

                // SSRC (4 bytes)
                rtpHeader[8] = 0x12.toByte()
                rtpHeader[9] = 0x34.toByte()
                rtpHeader[10] = 0x56.toByte()
                rtpHeader[11] = 0x78.toByte()

                val packetData = ByteArray(rtpHeader.size + nalData.size)
                System.arraycopy(rtpHeader, 0, packetData, 0, rtpHeader.size)
                System.arraycopy(nalData, 0, packetData, rtpHeader.size, nalData.size)

                val packet = DatagramPacket(packetData, packetData.size, socket.inetAddress, clientRtpPort)
                datagramSocket?.send(packet)
            } catch (e: Exception) {
                Log.e("EVCAM_RTSP", "Error sending RTP packet over UDP", e)
            }
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
