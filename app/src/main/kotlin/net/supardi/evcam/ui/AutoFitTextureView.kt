package net.supardi.evcam.ui

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView
import net.supardi.evcam.logic.AspectRatioMode

class AutoFitTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : TextureView(context, attrs, defStyle) {

    private var currentMode = AspectRatioMode.RATIO_16_9

    fun setAspectRatio(width: Int, height: Int) {
        requestLayout()
        configureTransform()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        configureTransform(w, h, currentMode)
    }

    fun configureTransform(w: Int = width, h: Int = height, mode: AspectRatioMode = currentMode) {
        if (w == 0 || h == 0) return
        currentMode = mode

        val matrix = android.graphics.Matrix()
        
        // 4:3 mode uses 4:3 sensor stream aspect ratio (4/3 = 1.3333f).
        // 16:9 & 1:1 use 16:9 sensor stream aspect ratio (16/9 = 1.77778f).
        val sensorAspect = when (mode) {
            AspectRatioMode.RATIO_4_3 -> 4f / 3f
            else -> 16f / 9f
        }

        val viewAspect = h.toFloat() / w.toFloat()

        var scaleX = 1f
        var scaleY = 1f

        if (viewAspect < sensorAspect) {
            scaleY = sensorAspect / viewAspect
        } else if (viewAspect > sensorAspect) {
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
