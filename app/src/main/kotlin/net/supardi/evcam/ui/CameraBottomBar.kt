package net.supardi.evcam.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import net.supardi.evcam.CameraMode

@Composable
fun CameraBottomBar(
    lastCapturedBitmap: Bitmap?,
    lastCapturedUri: Uri?,
    context: Context,
    cameraMode: CameraMode,
    isRecording: Boolean,
    isFrontCamera: Boolean,
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
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
        }

        // ── Shutter ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (isRecording) Color.Red else Color.White)
                // Tap & long-press handler — works for both modes
                .pointerInput(cameraMode, isRecording) {
                    detectTapGestures(
                        onTap = {
                            // Single tap
                            when (cameraMode) {
                                CameraMode.PHOTO -> onShutterTap()
                                CameraMode.VIDEO -> if (isRecording) onVideoTapStop() else onVideoTap()
                            }
                        },
                        onLongPress = {
                            // Long press (500ms hold)
                            isLongPressActive = true
                            when (cameraMode) {
                                CameraMode.PHOTO -> onBurstStart()
                                CameraMode.VIDEO -> onQuickRecordStart()
                            }
                        },
                        onPress = {
                            // onPress is always called on finger down.
                            // Wait for release, then clean up long-press state.
                            try {
                                awaitRelease()
                            } finally {
                                if (isLongPressActive) {
                                    isLongPressActive = false
                                    when (cameraMode) {
                                        CameraMode.PHOTO -> onBurstEnd()
                                        CameraMode.VIDEO -> onQuickRecordStop()
                                    }
                                }
                            }
                        }
                    )
                }
                // Drag-to-zoom — only active during long-press (video quick-record)
                .pointerInput(isLongPressActive) {
                    if (isLongPressActive) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragZoom(-dragAmount.y)
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
