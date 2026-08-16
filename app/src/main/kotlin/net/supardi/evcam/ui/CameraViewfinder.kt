package net.supardi.evcam.ui

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
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
import net.supardi.evcam.logic.*
import java.util.concurrent.TimeUnit


@Composable
fun CameraViewfinder(
    previewView: PreviewView,
    uiState: CameraUiState,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    var evScrollAnchorY by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (uiState.selectedFilter != ColorFilterMode.NORMAL) {
                        val paint = Paint().apply {
                            colorFilter = ColorFilter.colorMatrix(
                                ColorMatrix(uiState.selectedFilter.matrixValues)
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
                                uiState.cameraControl?.cancelFocusAndMetering()
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
                            
                            val factory = previewView.meteringPointFactory
                            val point = factory.createPoint(e.x, e.y)
                            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                .setAutoCancelDuration(2, TimeUnit.SECONDS)
                                .build()
                            
                            val future = uiState.cameraControl?.startFocusAndMetering(action)
                            future?.addListener({
                                try {
                                    val result = future.get()
                                    uiState.focusState = if (result != null && result.isFocusSuccessful) FocusState.SUCCESS else FocusState.FAILED
                                } catch (exc: Exception) {
                                    uiState.focusState = FocusState.FAILED
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
                            uiState.cameraControl?.startFocusAndMetering(action)
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
                        scaleGestureDetector.onTouchEvent(event)
                        if (!scaleGestureDetector.isInProgress) {
                            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                                evScrollAnchorY = event.y
                            } else if (event.actionMasked == MotionEvent.ACTION_MOVE && !uiState.isProMode) {
                                val deltaY = evScrollAnchorY - event.y
                                val scrollStepThreshold = 50f
                                val steps = (deltaY / scrollStepThreshold).toInt()
                                if (steps != 0) {
                                    val newIdx = (uiState.exposureIndex + steps).coerceIn(uiState.minExposureIndex, uiState.maxExposureIndex)
                                    if (newIdx != uiState.exposureIndex) {
                                        uiState.exposureIndex = newIdx
                                        uiState.cameraControl?.setExposureCompensationIndex(newIdx)
                                        uiState.showBrightnessSlider = true
                                        if (newIdx != 0) {
                                            uiState.isProMode = true
                                        }
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
