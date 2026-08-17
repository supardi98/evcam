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
        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
        
        val pW = ratioWidth.toFloat()
        val pH = ratioHeight.toFloat()
        val previewAspect = if (pW < pH) pW / pH else pH / pW

        var scaleX = 1f
        var scaleY = 1f

        if (viewAspect > previewAspect) {
            scaleY = viewAspect / previewAspect
        } else {
            scaleX = previewAspect / viewAspect
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
