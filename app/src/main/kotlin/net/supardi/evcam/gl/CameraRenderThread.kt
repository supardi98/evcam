package net.supardi.evcam.gl

import android.graphics.SurfaceTexture
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import net.supardi.evcam.logic.RotationSensorHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class CameraRenderThread(
    private val inputWidth: Int,
    private val inputHeight: Int,
    private val rotationSensorHelper: RotationSensorHelper
) : Thread("CameraRenderThread") {

    @Volatile private var isRunning = false
    @Volatile private var requestStop = false

    private val lock = Object()
    private var eglCore: EglCore? = null
    private var windowSurface: WindowSurface? = null
    private var textureProgram: Texture2dProgram? = null

    // OES Texture receiving frames from Camera2
    private var cameraIdTextureId = -1
    private var cameraSurfaceTexture: SurfaceTexture? = null
    var cameraSurface: Surface? = null
        private set

    @Volatile var frameAvailable = false
    private val transformMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // Output target (MediaCodec)
    @Volatile private var outputSurface: Surface? = null
    private var outputWidth = 0
    private var outputHeight = 0

    // Full rectangle buffers
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    init {
        val coords = floatArrayOf(
            -1.0f, -1.0f,   // 0 bottom left
             1.0f, -1.0f,   // 1 bottom right
            -1.0f,  1.0f,   // 2 top left
             1.0f,  1.0f    // 3 top right
        )
        val texCoords = floatArrayOf(
            0.0f, 0.0f,     // 0 bottom left
            1.0f, 0.0f,     // 1 bottom right
            0.0f, 1.0f,     // 2 top left
            1.0f, 1.0f      // 3 top right
        )

        vertexBuffer = ByteBuffer.allocateDirect(coords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(coords).position(0)

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        texCoordBuffer.put(texCoords).position(0)
    }

    fun setOutputSurface(surface: Surface?, width: Int, height: Int) {
        synchronized(lock) {
            outputSurface = surface
            outputWidth = width
            outputHeight = height
        }
    }

    fun stopRecording() {
        requestStop = true
        synchronized(lock) {
            lock.notifyAll()
        }
    }

    fun awaitSurfaceReady() {
        while (cameraSurface == null && !requestStop) {
            Thread.sleep(10)
        }
    }

    override fun run() {
        isRunning = true
        try {
            eglCore = EglCore(null, EglCore.FLAG_RECORDABLE)
            // Create a dummy surface just to have a current context to compile shaders
            // MediaCodec surface might not be ready yet. We use a 1x1 pbuffer
            val pbufferSurface = android.opengl.EGL14.eglCreatePbufferSurface(
                eglCore!!.eglDisplay, eglCore!!.eglConfig, intArrayOf(
                    android.opengl.EGL14.EGL_WIDTH, 1,
                    android.opengl.EGL14.EGL_HEIGHT, 1,
                    android.opengl.EGL14.EGL_NONE
                ), 0
            )
            eglCore!!.makeCurrent(pbufferSurface)

            textureProgram = Texture2dProgram()
            cameraIdTextureId = textureProgram!!.createTextureObject()

            cameraSurfaceTexture = SurfaceTexture(cameraIdTextureId)
            cameraSurfaceTexture!!.setDefaultBufferSize(inputWidth, inputHeight)
            cameraSurfaceTexture!!.setOnFrameAvailableListener {
                synchronized(lock) {
                    frameAvailable = true
                    lock.notifyAll()
                }
            }
            cameraSurface = Surface(cameraSurfaceTexture)

            while (!requestStop) {
                synchronized(lock) {
                    if (!frameAvailable && !requestStop) {
                        lock.wait(50)
                    }
                    if (frameAvailable) {
                        frameAvailable = false
                        cameraSurfaceTexture?.updateTexImage()
                        cameraSurfaceTexture?.getTransformMatrix(transformMatrix)
                        drawFrame()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CameraRenderThread", "Error in GL loop", e)
        } finally {
            release()
            isRunning = false
        }
    }

    private fun drawFrame() {
        var currentOutSurface: Surface?
        var outW: Int
        var outH: Int
        synchronized(lock) {
            currentOutSurface = outputSurface
            outW = outputWidth
            outH = outputHeight
        }

        if (currentOutSurface == null) return

        if (windowSurface == null) {
            windowSurface = WindowSurface(eglCore!!, currentOutSurface!!)
            windowSurface!!.makeCurrent()
        } else {
            windowSurface!!.makeCurrent()
        }

        android.opengl.GLES20.glViewport(0, 0, outW, outH)
        android.opengl.GLES20.glClear(android.opengl.GLES20.GL_COLOR_BUFFER_BIT)

        Matrix.setIdentityM(mvpMatrix, 0)
        
        // Apply Horizon Lock logic
        val horizonState = rotationSensorHelper.horizonState.value
        
        // Calculate the matrix
        // 1. Compensate for the 16:9 output surface (make coordinates square)
        Matrix.scaleM(mvpMatrix, 0, 1f, 16f / 9f, 1f)
        
        // 2. Scale by zoom factor
        Matrix.scaleM(mvpMatrix, 0, horizonState.zoomScale, horizonState.zoomScale, 1f)
        
        // 3. Rotate by Roll Angle
        // We negate rollAngle because OpenGL's rotateM is counter-clockwise, and we need the opposite of the UI's clockwise rotation
        Matrix.rotateM(mvpMatrix, 0, -horizonState.rollAngle, 0f, 0f, 1f)
        
        // 4. Compensate for the 9:16 input texture
        Matrix.scaleM(mvpMatrix, 0, 1f, 16f / 9f, 1f)

        textureProgram?.draw(
            mvpMatrix, vertexBuffer, 0, 4, 2, 8,
            transformMatrix, texCoordBuffer, cameraIdTextureId, 8
        )

        windowSurface?.setPresentationTime(cameraSurfaceTexture!!.timestamp)
        windowSurface?.swapBuffers()
    }

    private fun release() {
        cameraSurface?.release()
        cameraSurface = null
        cameraSurfaceTexture?.release()
        cameraSurfaceTexture = null
        
        windowSurface?.release()
        windowSurface = null
        
        textureProgram?.release()
        textureProgram = null
        
        eglCore?.release()
        eglCore = null
    }
}
