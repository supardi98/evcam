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

    fun setAspectRatio(width: Int, height: Int) {
        requestLayout()
        configureTransform()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        configureTransform(w, h)
    }

    fun configureTransform(w: Int = width, h: Int = height, mode: AspectRatioMode = AspectRatioMode.RATIO_16_9) {
        if (w == 0 || h == 0) return

        val matrix = android.graphics.Matrix()

        // Camera2 portrait stream buffer dimensions (1080 x 1920)
        val previewWidth = 1080
        val previewHeight = 1920

        val scaleX = w.toFloat() / previewWidth.toFloat()
        val scaleY = h.toFloat() / previewHeight.toFloat()

        // For CENTER_CROP without pixel distortion, use uniform max scale
        val scale = Math.max(scaleX, scaleY)

        val scaledWidth = previewWidth * scale
        val scaledHeight = previewHeight * scale

        // Scale relative to view center (w / 2, h / 2)
        matrix.setScale(scaledWidth / w.toFloat(), scaledHeight / h.toFloat(), w / 2f, h / 2f)
        setTransform(matrix)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
