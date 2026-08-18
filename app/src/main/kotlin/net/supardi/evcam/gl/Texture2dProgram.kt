package net.supardi.evcam.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import java.nio.FloatBuffer

class Texture2dProgram {
    private var programHandle: Int = 0
    private var uMVPMatrixLoc: Int = 0
    private var uTexMatrixLoc: Int = 0
    private var aPositionLoc: Int = 0
    private var aTextureCoordLoc: Int = 0

    init {
        programHandle = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (programHandle == 0) {
            throw RuntimeException("Unable to create program")
        }
        
        aPositionLoc = GLES20.glGetAttribLocation(programHandle, "aPosition")
        aTextureCoordLoc = GLES20.glGetAttribLocation(programHandle, "aTextureCoord")
        uMVPMatrixLoc = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix")
        uTexMatrixLoc = GLES20.glGetUniformLocation(programHandle, "uTexMatrix")
    }

    fun release() {
        GLES20.glDeleteProgram(programHandle)
        programHandle = -1
    }

    fun createTextureObject(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        checkGlError("glGenTextures")

        val texId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        checkGlError("glBindTexture $texId")

        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        checkGlError("glTexParameter")

        return texId
    }

    fun draw(
        mvpMatrix: FloatArray, vertexBuffer: FloatBuffer, firstVertex: Int,
        vertexCount: Int, coordsPerVertex: Int, vertexStride: Int,
        texMatrix: FloatArray, texBuffer: FloatBuffer, textureId: Int, texStride: Int
    ) {
        checkGlError("draw start")
        
        GLES20.glUseProgram(programHandle)
        checkGlError("glUseProgram")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0)
        checkGlError("glUniformMatrix4fv uMVPMatrixLoc")

        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)
        checkGlError("glUniformMatrix4fv uTexMatrixLoc")

        GLES20.glEnableVertexAttribArray(aPositionLoc)
        checkGlError("glEnableVertexAttribArray aPositionLoc")
        GLES20.glVertexAttribPointer(
            aPositionLoc, coordsPerVertex,
            GLES20.GL_FLOAT, false, vertexStride, vertexBuffer
        )
        checkGlError("glVertexAttribPointer aPositionLoc")

        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        checkGlError("glEnableVertexAttribArray aTextureCoordLoc")
        GLES20.glVertexAttribPointer(
            aTextureCoordLoc, 2,
            GLES20.GL_FLOAT, false, texStride, texBuffer
        )
        checkGlError("glVertexAttribPointer aTextureCoordLoc")

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount)
        checkGlError("glDrawArrays")

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextureCoordLoc)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glUseProgram(0)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0
        val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) return 0

        var program = GLES20.glCreateProgram()
        checkGlError("glCreateProgram")
        if (program == 0) {
            Log.e(TAG, "Could not create program")
        }
        GLES20.glAttachShader(program, vertexShader)
        checkGlError("glAttachShader")
        GLES20.glAttachShader(program, pixelShader)
        checkGlError("glAttachShader")
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ")
            Log.e(TAG, GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            program = 0
        }
        return program
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        checkGlError("glCreateShader type=$shaderType")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader $shaderType:")
            Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            shader = 0
        }
        return shader
    }

    private fun checkGlError(op: String) {
        var error: Int
        while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError $error")
            throw RuntimeException("$op: glError $error")
        }
    }

    companion object {
        private const val TAG = "Texture2dProgram"

        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """
    }
}
