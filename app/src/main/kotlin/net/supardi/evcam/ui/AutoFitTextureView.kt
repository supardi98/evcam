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
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        if (width == 0 || height == 0) {
            setMeasuredDimension(width, height)
            return
        }

        // Camera2 sensor preview stream buffer in portrait is 16:9 (1080 x 1920).
        // Ensure TextureView measures at native 16:9 aspect ratio (height = width * 16 / 9),
        // so Compose Box clipToBounds clips the top/bottom for 4:3 and 1:1 without ANY pixel stretching.
        val nativeHeight = width * 16 / 9
        if (height < nativeHeight) {
            setMeasuredDimension(width, nativeHeight)
        } else {
            setMeasuredDimension(width, height)
        }
    }
}
