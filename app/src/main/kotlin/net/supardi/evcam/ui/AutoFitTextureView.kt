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

        // 16:9 portrait preview buffer height for width w (e.g. 1080 * 16 / 9 = 1920)
        val targetHeight = w * 16f / 9f

        // FIT_XY compresses height 1920 down to h (1440 for 4:3, 1080 for 1:1).
        // scaleY = targetHeight / h UNDOES FIT_XY compression completely so pixels stay 1:1 un-stretched!
        val scaleY = targetHeight / h.toFloat()

        matrix.setScale(1f, scaleY, w / 2f, h / 2f)
        setTransform(matrix)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
