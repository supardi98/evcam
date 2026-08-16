package net.supardi.evcam.ui

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.supardi.evcam.AspectRatioMode
import net.supardi.evcam.CameraMode
import net.supardi.evcam.ColorFilterMode
import net.supardi.evcam.FocusState
import java.util.concurrent.TimeUnit

@Composable
fun CameraViewfinder(
    previewView: PreviewView,
    cameraMode: CameraMode,
    aspectRatio: AspectRatioMode,
    selectedFilter: ColorFilterMode,
    zoomAnim: Animatable<Float, AnimationVector1D>,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    isAeAfLocked: Boolean,
    isProMode: Boolean,
    isFocusAuto: Boolean,
    minExposureIndex: Int,
    maxExposureIndex: Int,
    exposureIndex: Int,
    isTransitioningRatio: Boolean,
    cameraControl: CameraControl?,
    coroutineScope: CoroutineScope,
    onZoomChange: (Float) -> Unit,
    onFocusStart: (Offset) -> Unit,
    onFocusSuccess: (Boolean) -> Unit,
    onAeAfLockToggle: (Boolean) -> Unit,
    onFocusAutoToggle: (Boolean) -> Unit,
    onExposureChange: (Int) -> Unit,
    onCameraModeChange: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var evScrollAnchorY by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (selectedFilter != ColorFilterMode.NORMAL) {
                        val paint = Paint().apply {
                            colorFilter = ColorFilter.colorMatrix(
                                ColorMatrix(selectedFilter.matrixValues)
                            )
                        }
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(size.toRect(), paint)
                            drawContent()
                            canvas.restore()
                        }
                    } else {
                        drawContent()
                    }
                },
            factory = { ctx ->
                previewView.apply {
                    val scaleGestureDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            val newZoom = (zoomAnim.value * detector.scaleFactor).coerceIn(minZoomRatio, maxZoomRatio)
                            coroutineScope.launch { zoomAnim.snapTo(newZoom) }
                            onZoomChange(newZoom)
                            return true
                        }
                    })

                    val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapUp(e: MotionEvent): Boolean {
                            if (isAeAfLocked) {
                                onAeAfLockToggle(false)
                                cameraControl?.cancelFocusAndMetering()
                            }
                            
                            if (isProMode && !isFocusAuto) {
                                return true
                            }
                            
                            onFocusAutoToggle(true)
                            val offset = Offset(e.x, e.y)
                            onFocusStart(offset)
                            
                            val factory = previewView.meteringPointFactory
                            val point = factory.createPoint(e.x, e.y)
                            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                .setAutoCancelDuration(2, TimeUnit.SECONDS)
                                .build()
                            
                            val future = cameraControl?.startFocusAndMetering(action)
                            future?.addListener({
                                try {
                                    val result = future.get()
                                    onFocusSuccess(result != null && result.isFocusSuccessful)
                                } catch (exc: Exception) {
                                    onFocusSuccess(false)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            return true
                        }

                        override fun onLongPress(e: MotionEvent) {
                            val factory = previewView.meteringPointFactory
                            val point = factory.createPoint(e.x, e.y)
                            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                .disableAutoCancel()
                                .build()
                            cameraControl?.startFocusAndMetering(action)
                            onAeAfLockToggle(true)
                            val offset = Offset(e.x, e.y)
                            onFocusStart(offset)
                            onFocusSuccess(true)
                        }

                        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                            if (e1 == null) return false
                            val diffX = e2.x - e1.x
                            val diffY = e2.y - e1.y
                            if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) && kotlin.math.abs(diffX) > 100 && kotlin.math.abs(velocityX) > 100) {
                                if (diffX < 0 && cameraMode == CameraMode.PHOTO) {
                                    onCameraModeChange(CameraMode.VIDEO)
                                    return true
                                } else if (diffX > 0 && cameraMode == CameraMode.VIDEO) {
                                    onCameraModeChange(CameraMode.PHOTO)
                                    return true
                                }
                            }
                            return false
                        }
                    })

                    setOnTouchListener { _, event ->
                        scaleGestureDetector.onTouchEvent(event)
                        if (!scaleGestureDetector.isInProgress) {
                            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                                evScrollAnchorY = event.y
                            } else if (event.actionMasked == MotionEvent.ACTION_MOVE && !isProMode) {
                                val deltaY = evScrollAnchorY - event.y
                                val scrollStepThreshold = 50f
                                val steps = (deltaY / scrollStepThreshold).toInt()
                                if (steps != 0) {
                                    val newIdx = (exposureIndex + steps).coerceIn(minExposureIndex, maxExposureIndex)
                                    if (newIdx != exposureIndex) {
                                        onExposureChange(newIdx)
                                        cameraControl?.setExposureCompensationIndex(newIdx)
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
            visible = isTransitioningRatio,
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
                    text = if (cameraMode == CameraMode.VIDEO) "16:9 VIDEO" else aspectRatio.label,
                    color = Color.Yellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }
    }
}
