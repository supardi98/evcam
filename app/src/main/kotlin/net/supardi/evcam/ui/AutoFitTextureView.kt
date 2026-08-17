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
        if (viewWidth == 0 || viewHeight == 0 || ratioWidth == 0 || ratioHeight == 0) return
        
        val matrix = android.graphics.Matrix()
        
        // Target aspect ratio in portrait (height / width)
        val targetRatio = if (ratioWidth < ratioHeight) {
            ratioHeight.toFloat() / ratioWidth.toFloat()
        } else {
            ratioWidth.toFloat() / ratioHeight.toFloat()
        }

        val viewRatio = viewHeight.toFloat() / viewWidth.toFloat()

        var scaleX = 1f
        var scaleY = 1f

        if (viewRatio > targetRatio) {
            // View container is taller than target preview aspect ratio:
            // Scale X to crop sides so content isn't stretched vertically.
            scaleX = viewRatio / targetRatio
        } else {
            // View container is wider than target preview aspect ratio:
            // Scale Y to crop top/bottom so content isn't stretched horizontally.
            scaleY = targetRatio / viewRatio
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
