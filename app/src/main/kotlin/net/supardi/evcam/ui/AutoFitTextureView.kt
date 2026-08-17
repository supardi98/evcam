package net.supardi.evcam.ui

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView

class AutoFitTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : TextureView(context, attrs, defStyle) {

    private var ratioWidth = 1080
    private var ratioHeight = 1920

    fun setAspectRatio(width: Int, height: Int) {
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("Size cannot be negative.")
        }
        ratioWidth = width
        ratioHeight = height
        requestLayout()
        configureTransform(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        configureTransform(w, h)
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        if (viewWidth == 0 || viewHeight == 0) return
        
        val matrix = android.graphics.Matrix()
        
        // Sensor preview buffer aspect ratio in portrait is 16:9 (1920 / 1080)
        val sensorAspect = 1920f / 1080f
        val viewAspect = viewHeight.toFloat() / viewWidth.toFloat()

        var scaleX = 1f
        var scaleY = 1f

        if (viewAspect > sensorAspect) {
            // View container is taller than 16:9: scale X to crop sides
            scaleX = viewAspect / sensorAspect
        } else {
            // View container is shorter than 16:9 (e.g. 4:3 or 1:1): scale Y to crop top/bottom
            scaleY = sensorAspect / viewAspect
        }

        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        setTransform(matrix)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
