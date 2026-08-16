package net.supardi.evcam.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
    onThumbnailClick: () -> Unit,
    onShutterTap: () -> Unit,
    onBurstStart: () -> Unit,
    onBurstEnd: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray.copy(alpha = 0.5f))
                .clickable { 
                    if (lastCapturedBitmap != null || lastCapturedUri != null) {
                        onThumbnailClick()
                    } else {
                        Toast.makeText(context, "Belum ada foto atau video yang diambil", Toast.LENGTH_SHORT).show()
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
                Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (isRecording) Color.Red else Color.White)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onShutterTap() },
                        onLongPress = {
                            if (cameraMode == CameraMode.PHOTO) {
                                onBurstStart()
                            } else {
                                onShutterTap()
                            }
                        },
                        onPress = {
                            try {
                                tryAwaitRelease()
                            } finally {
                                onBurstEnd()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {}

        IconButton(
            onClick = onSwitchCamera,
            modifier = Modifier.size(64.dp).background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
