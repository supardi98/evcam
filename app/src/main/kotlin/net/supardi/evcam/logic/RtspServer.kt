package net.supardi.evcam.logic

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.io.OutputStream
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

    fun start() {
        if (isRunning) return
        isRunning = true
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
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e("EVCAM_RTSP", "Error closing RTSP server socket", e)
        }
        clients.forEach { it.close() }
        clients.clear()
    }

    private inner class RtspClientHandler(private val socket: Socket) : Runnable {
        private var outputStream: OutputStream? = null

        override fun run() {
            try {
                outputStream = socket.getOutputStream()
                val inputStream = socket.getInputStream()
                val reader = inputStream.bufferedReader()

                while (isRunning && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) continue

                    Log.d("EVCAM_RTSP", "RTSP Command: $line")

                    val tokens = line.split(" ")
                    if (tokens.size < 3) continue
                    val method = tokens[0]
                    val url = tokens[1]

                    // Extract CSeq
                    var cseq = "1"
                    var lineRead: String?
                    while (reader.readLine().also { lineRead = it } != null) {
                        if (lineRead.isNullOrEmpty()) break
                        if (lineRead!!.startsWith("CSeq:", ignoreCase = true)) {
                            cseq = lineRead!!.substring(5).trim()
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
                                    "a=control:track0\r\n"
                            val response = "RTSP/1.0 200 OK\r\n" +
                                    "CSeq: $cseq\r\n" +
                                    "Content-Type: application/sdp\r\n" +
                                    "Content-Length: ${sdp.length}\r\n\r\n" + sdp
                            outputStream?.write(response.toByteArray())
                            outputStream?.flush()
                        }
                        "SETUP" -> {
                            val response = "RTSP/1.0 200 OK\r\n" +
                                    "CSeq: $cseq\r\n" +
                                    "Transport: RTP/AVP;unicast;client_port=5000-5001;server_port=6000-6001\r\n" +
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
                Log.d("EVCAM_RTSP", "RTSP Client disconnected")
            } finally {
                close()
            }
        }

        fun close() {
            try {
                socket.close()
            } catch (e: Exception) {}
            clients.remove(this)
        }
    }
}
