package net.supardi.evcam.ui

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView

class AutoFitTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : TextureView(context, attrs, defStyle) {

    fun setAspectRatio(width: Int, height: Int) {
        requestLayout()
        configureTransform()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        configureTransform(w, h)
    }

    fun configureTransform(w: Int = width, h: Int = height) {
        if (w == 0 || h == 0) return

        val matrix = android.graphics.Matrix()

        // Sensor aspect ratio in portrait (height / width = 1920 / 1080 = 16/9 = 1.7777778f)
        val sensorAspect = 16f / 9f
        val viewAspect = h.toFloat() / w.toFloat()

        var scaleX = 1f
        var scaleY = 1f

        if (viewAspect < sensorAspect) {
            // View is shorter than 16:9 (e.g. 4:3 or 1:1): FIT_XY compressed height, so scale Y up to restore 1:1 pixel aspect
            scaleY = sensorAspect / viewAspect
        } else if (viewAspect > sensorAspect) {
            // View is taller than 16:9: scale X up to restore 1:1 pixel aspect
            scaleX = viewAspect / sensorAspect
        }

        matrix.setScale(scaleX, scaleY, w / 2f, h / 2f)
        setTransform(matrix)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
