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

    fun configureTransform(w: Int = width, h: Int = height, mode: AspectRatioMode = AspectRatioMode.RATIO_16_9) {
        setTransform(null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
