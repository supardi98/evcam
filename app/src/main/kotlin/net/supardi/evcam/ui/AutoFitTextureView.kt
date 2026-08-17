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

        // Native 16:9 camera stream buffer height for container width w is w * 16 / 9 (e.g. 1080 * 16 / 9 = 1920)
        val native169Height = w * 16f / 9f

        var scaleX = 1f
        var scaleY = 1f

        if (h.toFloat() < native169Height) {
            // Container is shorter than 16:9 (e.g. 4:3 or 1:1): FIT_XY squeezed 1920 height down to h.
            // Scale Y by native169Height / h to UNDO FIT_XY squeezing and keep 100% un-distorted pixels!
            scaleY = native169Height / h.toFloat()
        } else if (h.toFloat() > native169Height) {
            // Container is taller than 16:9: scale X up to keep 100% un-distorted pixels
            scaleX = h.toFloat() / native169Height
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
