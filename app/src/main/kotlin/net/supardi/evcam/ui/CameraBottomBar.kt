package net.supardi.evcam.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import net.supardi.evcam.logic.*

@Composable
fun CameraBottomBar(
    lastCapturedBitmap: Bitmap?,
    lastCapturedUri: Uri?,
    context: Context,
    cameraMode: CameraMode,
    isRecording: Boolean,
    isFrontCamera: Boolean,
    isProcessingHdr: Boolean = false,
    hdrProgress: Int = 0,
    // Photo mode
    onShutterTap: () -> Unit,
    onBurstStart: () -> Unit,
    onBurstEnd: () -> Unit,
    // Video mode
    onVideoTap: () -> Unit,
    onVideoTapStop: () -> Unit,
    onQuickRecordStart: () -> Unit,
    onQuickRecordStop: () -> Unit,
    onDragZoom: (Float) -> Unit,
    onSwitchCamera: () -> Unit,
    onThumbnailClick: () -> Unit
) {
    val imageLoader = remember(context) {
        ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
    }

    val cameraSwitchRotation by animateFloatAsState(
        targetValue = if (isFrontCamera) 180f else 0f,
        animationSpec = tween(300),
        label = "CameraSwitchRotation"
    )

    // Tracks whether we're in long-press state
    var isLongPressActive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()


    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val isVideoMedia = remember(lastCapturedUri, lastCapturedBitmap) {
            if (lastCapturedUri != null) {
                val type = try { context.contentResolver.getType(lastCapturedUri) } catch (e: Exception) { null }
                type?.startsWith("video/") == true || 
                lastCapturedUri.toString().contains("video", ignoreCase = true) || 
                lastCapturedUri.toString().endsWith(".mp4", ignoreCase = true)
            } else false
        }

        // ── Thumbnail ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray.copy(alpha = 0.5f))
                .clickable {
                    if (lastCapturedBitmap != null || lastCapturedUri != null) {
                        onThumbnailClick()
                    } else {
                        Toast.makeText(context, "No photos or videos captured yet", Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (lastCapturedBitmap != null) {
                Image(
                    bitmap = lastCapturedBitmap.asImageBitmap(),
                    contentDescription = "Gallery Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (lastCapturedUri != null) {
                AsyncImage(
                    model = lastCapturedUri,
                    imageLoader = imageLoader,
                    contentDescription = "Gallery Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
            }

            if (isVideoMedia && (lastCapturedBitmap != null || lastCapturedUri != null)) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video Badge",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // ── HDR shimmer + badge overlay ─────────────────────────────────
            if (isProcessingHdr) {
                val shimmerTranslate = rememberInfiniteTransition(label = "shimmer")
                val shimmerX by shimmerTranslate.animateFloat(
                    initialValue = -200f,
                    targetValue = 200f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "shimmerX"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                start = Offset(shimmerX - 100f, 0f),
                                end = Offset(shimmerX + 100f, 200f)
                            )
                        )
                )
                // Badge pojok kanan atas
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFFD600)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        text = if (hdrProgress > 0) "HDR $hdrProgress%" else "HDR+",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // ── Shutter ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (isRecording) Color.Red else Color.White)
                .pointerInput(cameraMode) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()

                        var isLongPress = false
                        var lastY = down.position.y

                        val longPressJob = scope.launch {
                            delay(400)
                            isLongPress = true
                            when (cameraMode) {
                                CameraMode.PHOTO -> onBurstStart()
                                CameraMode.VIDEO -> onQuickRecordStart()
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                            val change = event.changes.firstOrNull() ?: break

                            if (change.pressed) {
                                if (isLongPress) {
                                    val currentY = change.position.y
                                    val deltaY = lastY - currentY // Upward drag = positive zoom
                                    lastY = currentY
                                    if (kotlin.math.abs(deltaY) > 0.5f) {
                                        onDragZoom(deltaY)
                                    }
                                }
                                change.consume()
                            } else {
                                break
                            }
                        }

                        longPressJob.cancel()
                        if (isLongPress) {
                            when (cameraMode) {
                                CameraMode.PHOTO -> onBurstEnd()
                                CameraMode.VIDEO -> onQuickRecordStop()
                            }
                        } else {
                            when (cameraMode) {
                                CameraMode.PHOTO -> onShutterTap()
                                CameraMode.VIDEO -> if (isRecording) onVideoTapStop() else onVideoTap()
                            }
                        }
                    }
                },

            contentAlignment = Alignment.Center
        ) {
            // Show stop square icon when recording
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                )
            }
        }

        // ── Camera Switch ──────────────────────────────────────────────────────
        IconButton(
            onClick = onSwitchCamera,
            modifier = Modifier.size(64.dp).background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = Color.White,
                modifier = Modifier.size(32.dp).graphicsLayer { rotationZ = cameraSwitchRotation }
            )
        }
    }
}
