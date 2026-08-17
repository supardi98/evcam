package net.supardi.evcam.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.net.wifi.WifiManager
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class WebcamStreamServer(
    private val context: Context,
    val port: Int = 8080,
    var enableHttpMjpeg: Boolean = true,
    var enableHttpSnapshot: Boolean = true,
    var enableRtspStream: Boolean = false,
    var enableWebRtc: Boolean = false
) {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val threadPool = Executors.newCachedThreadPool()
    private val clients = ConcurrentHashMap.newKeySet<ClientHandler>()
    
    @Volatile
    private var latestFrameJpeg: ByteArray? = null

    var onClientCountChanged: ((Int) -> Unit)? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        threadPool.execute {
            try {
                serverSocket = ServerSocket(port)
                Log.d("EVCAM_STREAM", "Webcam HTTP Stream Server started on port $port")
                while (isRunning && serverSocket?.isClosed == false) {
                    val socket = serverSocket!!.accept()
                    val handler = ClientHandler(socket)
                    clients.add(handler)
                    onClientCountChanged?.invoke(clients.size)
                    threadPool.execute(handler)
                }
            } catch (e: Exception) {
                Log.e("EVCAM_STREAM", "Server socket exception", e)
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
            Log.e("EVCAM_STREAM", "Error closing server socket", e)
        }
        clients.forEach { it.close() }
        clients.clear()
        onClientCountChanged?.invoke(0)
    }

    fun pushYuvFrame(image: Image) {
        if (clients.isEmpty()) return
        try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 75, out)
            val jpegBytes = out.toByteArray()
            latestFrameJpeg = jpegBytes

            clients.forEach { client ->
                if (client.isMjpegStream) {
                    client.sendMjpegFrame(jpegBytes)
                }
            }
        } catch (e: Exception) {
            Log.e("EVCAM_STREAM", "Error compressing YUV to JPEG", e)
        }
    }

    private inner class ClientHandler(private val socket: Socket) : Runnable {
        var isMjpegStream = false
        private var outputStream: OutputStream? = null

        override fun run() {
            try {
                outputStream = socket.getOutputStream()
                val inputStream = socket.getInputStream()
                val reader = inputStream.bufferedReader()
                val requestLine = reader.readLine() ?: return

                Log.d("EVCAM_STREAM", "HTTP Request: $requestLine")

                when {
                    (requestLine.contains("GET /live.mjpeg") || requestLine.contains("GET /live") || requestLine.contains("GET /video")) -> {
                        if (!enableHttpMjpeg) {
                            val forbidden = "HTTP/1.1 403 Forbidden\r\n\r\nHTTP MJPEG Streaming protocol is disabled in settings"
                            outputStream?.write(forbidden.toByteArray())
                        } else {
                            isMjpegStream = true
                            val header = ("HTTP/1.1 200 OK\r\n" +
                                    "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                                    "Pragma: no-cache\r\n" +
                                    "Expires: -1\r\n" +
                                    "Access-Control-Allow-Origin: *\r\n" +
                                    "Content-Type: multipart/x-mixed-replace; boundary=--jpgboundary\r\n\r\n")
                            outputStream?.write(header.toByteArray())
                            outputStream?.flush()

                            // Keep thread alive while client is connected
                            while (isRunning && !socket.isClosed) {
                                Thread.sleep(500)
                            }
                        }
                    }
                    (requestLine.contains("GET /shot.jpg") || requestLine.contains("GET /snapshot")) -> {
                        if (!enableHttpSnapshot) {
                            val forbidden = "HTTP/1.1 403 Forbidden\r\n\r\nSnapshot protocol is disabled in settings"
                            outputStream?.write(forbidden.toByteArray())
                        } else {
                            val frame = latestFrameJpeg
                            if (frame != null) {
                                val header = ("HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: image/jpeg\r\n" +
                                        "Content-Length: ${frame.size}\r\n" +
                                        "Access-Control-Allow-Origin: *\r\n\r\n")
                                outputStream?.write(header.toByteArray())
                                outputStream?.write(frame)
                                outputStream?.flush()
                            } else {
                                val notFound = "HTTP/1.1 503 Service Unavailable\r\n\r\nNo frame captured yet"
                                outputStream?.write(notFound.toByteArray())
                            }
                        }
                    }
                    else -> {
                        // Serve Web Dashboard page
                        val html = getWebDashboardHtml()
                        val header = ("HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/html; charset=utf-8\r\n" +
                                "Content-Length: ${html.toByteArray().size}\r\n" +
                                "Access-Control-Allow-Origin: *\r\n\r\n")
                        outputStream?.write(header.toByteArray())
                        outputStream?.write(html.toByteArray())
                        outputStream?.flush()
                    }
                }

            } catch (e: Exception) {
                Log.d("EVCAM_STREAM", "Client disconnected: ${socket.inetAddress?.hostAddress}")
            } finally {
                close()
            }
        }

        fun sendMjpegFrame(jpeg: ByteArray) {
            try {
                val frameHeader = ("--jpgboundary\r\n" +
                        "Content-Type: image/jpeg\r\n" +
                        "Content-Length: ${jpeg.size}\r\n\r\n")
                outputStream?.write(frameHeader.toByteArray())
                outputStream?.write(jpeg)
                outputStream?.write("\r\n".toByteArray())
                outputStream?.flush()
            } catch (e: Exception) {
                close()
            }
        }

        fun close() {
            try {
                socket.close()
            } catch (e: Exception) {}
            clients.remove(this)
            onClientCountChanged?.invoke(clients.size)
        }
    }

    private fun getWebDashboardHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>EV Cam - IP Stream Webcam</title>
                <style>
                    body { background-color: #121212; color: #ffffff; font-family: system-ui, -apple-system, sans-serif; text-align: center; margin: 0; padding: 20px; }
                    h1 { color: #ffee58; margin-bottom: 5px; }
                    p { color: #b0bec5; font-size: 14px; }
                    .video-container { margin: 20px auto; max-width: 900px; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.8); background: #000; }
                    img { width: 100%; height: auto; display: block; }
                    .btn { display: inline-block; padding: 10px 20px; background: #ffee58; color: #000; text-decoration: none; font-weight: bold; border-radius: 20px; margin: 10px; }
                </style>
            </head>
            <body>
                <h1>📷 EV Cam Live Stream</h1>
                <p>High Performance HTTP MJPEG & IP Webcam Stream</p>
                <div class="video-container">
                    <img src="/live.mjpeg" alt="EV Cam Live Stream">
                </div>
                <div>
                    <a class="btn" href="/shot.jpg" target="_blank">Take Snapshot</a>
                    <a class="btn" href="/live.mjpeg" target="_blank">Direct MJPEG Stream</a>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        fun getLocalIpAddress(context: Context): String {
            try {
                // 1. Try WifiManager IP
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val wifiIp = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (wifiIp != 0) {
                    val ipStr = String.format(
                        java.util.Locale.US,
                        "%d.%d.%d.%d",
                        wifiIp and 0xff,
                        wifiIp shr 8 and 0xff,
                        wifiIp shr 16 and 0xff,
                        wifiIp shr 24 and 0xff
                    )
                    if (ipStr != "0.0.0.0" && ipStr != "127.0.0.1") {
                        return ipStr
                    }
                }

                // 2. Fallback: Search wlan0 or non-loopback IPv4 network interface
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                var fallbackIp: String? = null
                for (intf in interfaces) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            val host = addr.hostAddress ?: continue
                            if (intf.name.lowercase().contains("wlan")) {
                                return host
                            }
                            if (fallbackIp == null) fallbackIp = host
                        }
                    }
                }
                if (fallbackIp != null) return fallbackIp
            } catch (e: Exception) {
                Log.e("EVCAM_STREAM", "Error getting IP address", e)
            }
            return "127.0.0.1"
        }
    }

}
