package net.supardi.evcam.ui

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import net.supardi.evcam.ui.AutoFitTextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.supardi.evcam.logic.*
import java.util.concurrent.TimeUnit


@Composable
fun CameraViewfinder(
    previewView: AutoFitTextureView,
    uiState: CameraUiState,
    coroutineScope: CoroutineScope,
    camera2Engine: Camera2Engine,
    modifier: Modifier = Modifier
) {
    var evScrollAnchorY by remember { mutableFloatStateOf(0f) }
    var isMultiTouch by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.configureTransform()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val filter = uiState.selectedFilter
                    if (filter == ColorFilterMode.NORMAL) {
                        view.setRenderEffect(null)
                    } else {
                        val cm = android.graphics.ColorMatrix(filter.matrixValues)
                        val renderEffect = android.graphics.RenderEffect.createColorFilterEffect(
                            android.graphics.ColorMatrixColorFilter(cm)
                        )
                        view.setRenderEffect(renderEffect)
                    }
                }
            },
            factory = { ctx ->
                previewView.apply {
                    val scaleGestureDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            uiState.showZoomSlider = true
                            val newZoom = (uiState.zoomAnim.value * detector.scaleFactor).coerceIn(uiState.minZoomRatio, uiState.maxZoomRatio)
                            coroutineScope.launch { uiState.zoomAnim.snapTo(newZoom) }
                            uiState.currentZoom = newZoom
                            return true
                        }
                    })

                    val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapUp(e: MotionEvent): Boolean {
                            if (uiState.isAeAfLocked) {
                                uiState.isAeAfLocked = false
                            }
                            
                            if (uiState.isProMode && !uiState.isFocusAuto) {
                                return true
                            }
                            
                            uiState.isFocusAuto = true
                            uiState.focusOffset = Offset(e.x, e.y)
                            uiState.showFocusBox = true
                            uiState.showZoomSlider = true
                            uiState.showBrightnessSlider = true
                            uiState.focusState = FocusState.SEARCHING
                            camera2Engine.focusAt(e.x, e.y, previewView.width.toFloat(), previewView.height.toFloat())
                            return true
                        }

                        override fun onLongPress(e: MotionEvent) {
                            uiState.isAeAfLocked = true
                            uiState.focusOffset = Offset(e.x, e.y)
                            uiState.showFocusBox = true
                            uiState.focusState = FocusState.SUCCESS
                        }

                        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                            if (e1 == null) return false
                            val diffX = e2.x - e1.x
                            val diffY = e2.y - e1.y
                            if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) && kotlin.math.abs(diffX) > 100 && kotlin.math.abs(velocityX) > 100) {
                                if (diffX < 0 && uiState.cameraMode == CameraMode.PHOTO) {
                                    if (!uiState.isRecording) uiState.cameraMode = CameraMode.VIDEO
                                    return true
                                } else if (diffX > 0 && uiState.cameraMode == CameraMode.VIDEO) {
                                    if (!uiState.isRecording) uiState.cameraMode = CameraMode.PHOTO
                                    return true
                                }
                            }
                            return false
                        }
                    })

                    setOnTouchListener { _, event ->
                        if (event.pointerCount > 1) {
                            isMultiTouch = true
                        }
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                            isMultiTouch = false
                            evScrollAnchorY = event.y
                        }

                        scaleGestureDetector.onTouchEvent(event)

                        if (!scaleGestureDetector.isInProgress && !isMultiTouch && event.pointerCount == 1) {
                            if (event.actionMasked == MotionEvent.ACTION_MOVE && !uiState.isProMode) {
                                val deltaY = evScrollAnchorY - event.y
                                val scrollStepThreshold = 80f
                                val steps = (deltaY / scrollStepThreshold).toInt()
                                if (steps != 0) {
                                    val newIdx = (uiState.exposureIndex + steps).coerceIn(uiState.minExposureIndex, uiState.maxExposureIndex)
                                    if (newIdx != uiState.exposureIndex) {
                                        uiState.exposureIndex = newIdx
                                        uiState.showBrightnessSlider = true
                                    }
                                    evScrollAnchorY = event.y
                                }
                            }
                            gestureDetector.onTouchEvent(event)
                        }
                        true
                    }
                }
            }
        )


        AnimatedVisibility(
            visible = uiState.isTransitioningRatio,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (uiState.cameraMode == CameraMode.VIDEO) "16:9 VIDEO" else uiState.aspectRatio.label,
                    color = Color.Yellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }
    }
}
