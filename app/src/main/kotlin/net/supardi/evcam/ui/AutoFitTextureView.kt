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

        val viewAspect = w.toFloat() / h.toFloat()
        val sensorAspect = 3f / 4f // Native sensor aspect in portrait

        val matrix = android.graphics.Matrix()

        if (Math.abs(viewAspect - sensorAspect) < 0.01f) {
            setTransform(null)
            return
        }

        // TextureView implicitly stretches the native 4:3 camera buffer to completely fill the (w x h) view.
        // We need to scale either X or Y further to restore the 4:3 proportions, making the image overflow 
        // the view bounds (Center-Crop) exactly like our photo capture does.
        if (viewAspect < sensorAspect) {
            // View is taller than 4:3 (e.g., 16:9). Scale X up so it overflows left and right.
            val scaleX = (h * sensorAspect) / w
            matrix.setScale(scaleX, 1f, w / 2f, h / 2f)
        } else {
            // View is wider than 4:3 (e.g., 1:1). Scale Y up so it overflows top and bottom.
            val scaleY = (w / sensorAspect) / h
            matrix.setScale(1f, scaleY, w / 2f, h / 2f)
        }
        
        setTransform(matrix)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
