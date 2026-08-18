package net.supardi.evcam.gl

import android.opengl.EGL14
import android.opengl.EGLSurface
import android.view.Surface

class WindowSurface(private val eglCore: EglCore, surface: Any, private val releaseSurface: Boolean = false) {
    private var eglSurface: EGLSurface = eglCore.createWindowSurface(surface)
    private var surfaceObj: Surface? = null

    init {
        if (surface is Surface) {
            surfaceObj = surface
        }
    }

    fun release() {
        if (eglSurface !== EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglCore.eglDisplay, eglSurface)
            eglSurface = EGL14.EGL_NO_SURFACE
        }
        if (surfaceObj != null) {
            if (releaseSurface) {
                surfaceObj!!.release()
            }
            surfaceObj = null
        }
    }

    fun makeCurrent() {
        eglCore.makeCurrent(eglSurface)
    }

    fun swapBuffers(): Boolean {
        return eglCore.swapBuffers(eglSurface)
    }

    fun setPresentationTime(nsecs: Long) {
        eglCore.setPresentationTime(eglSurface, nsecs)
    }
}
