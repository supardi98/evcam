package net.supardi.evcam.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import net.supardi.evcam.CameraMode
import net.supardi.evcam.fetchLatestMediaUri

data class MediaItem(
    val uri: Uri,
    val isVideo: Boolean,
    val dateAdded: Long = 0L
)

private fun fetchRecentMediaList(context: Context, limit: Int = 30): List<MediaItem> {
    val list = mutableListOf<MediaItem>()
    val contentResolver = context.contentResolver

    // Query Images
    try {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(queryUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val uri = ContentUris.withAppendedId(queryUri, id)
                list.add(MediaItem(uri = uri, isVideo = false, dateAdded = dateAdded))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Query Videos
    try {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED
        )
        val queryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(queryUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val uri = ContentUris.withAppendedId(queryUri, id)
                list.add(MediaItem(uri = uri, isVideo = true, dateAdded = dateAdded))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return list.sortedByDescending { it.dateAdded }.take(limit)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaPreviewDialog(
    lastCapturedBitmap: Bitmap?,
    lastCapturedUri: Uri?,
    cameraMode: CameraMode,
    context: Context,
    onDismiss: () -> Unit
) {
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }

    val mediaList = remember(lastCapturedUri, lastCapturedBitmap) {
        val fetched = fetchRecentMediaList(context, limit = 30).toMutableList()
        if (lastCapturedUri != null && fetched.none { it.uri == lastCapturedUri }) {
            fetched.add(0, MediaItem(uri = lastCapturedUri, isVideo = (cameraMode == CameraMode.VIDEO)))
        }
        fetched
    }

    val pageCount = if (mediaList.isNotEmpty()) mediaList.size else if (lastCapturedBitmap != null) 1 else 0
    val pagerState = rememberPagerState(initialPage = 0) { pageCount }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = false) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar: Counter & Close Button (X) completely outside image area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 24.dp, end = 24.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentCountText = if (pageCount > 0) "${pagerState.currentPage + 1} / $pageCount" else "1 / 1"
                    Text(
                        text = currentCountText,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close, 
                            contentDescription = "Close", 
                            tint = Color.White, 
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Center Image Pager area: Pure borderless floating images / video preview
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val openActiveMedia = {
                        val activeItem = mediaList.getOrNull(pagerState.currentPage)
                        val activeUri = activeItem?.uri ?: lastCapturedUri ?: fetchLatestMediaUri(context)
                        val isVideo = activeItem?.isVideo ?: (cameraMode == CameraMode.VIDEO)
                        if (activeUri != null) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(activeUri, if (isVideo) "video/*" else "image/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Tidak ada aplikasi Galeri / Pemutar Video", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Media tidak ditemukan", Toast.LENGTH_SHORT).show()
                        }
                    }

                    if (pageCount > 0) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val item = mediaList.getOrNull(page)
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (page == 0 && lastCapturedBitmap != null) {
                                    Image(
                                        bitmap = lastCapturedBitmap.asImageBitmap(),
                                        contentDescription = "Preview $page",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { openActiveMedia() }
                                    )
                                } else if (item != null) {
                                    AsyncImage(
                                        model = item.uri,
                                        imageLoader = imageLoader,
                                        contentDescription = "Preview $page",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { openActiveMedia() }
                                    )
                                }

                                // If Video, show big prominent Play icon badge over the preview!
                                if (item?.isVideo == true || (page == 0 && cameraMode == CameraMode.VIDEO)) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .clickable { openActiveMedia() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Video",
                                            tint = Color.Yellow,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Subtitle hint at bottom
                val isCurrentVideo = mediaList.getOrNull(pagerState.currentPage)?.isVideo == true || (pagerState.currentPage == 0 && cameraMode == CameraMode.VIDEO)
                Text(
                    text = if (isCurrentVideo) "Ketuk tombol Putar / foto untuk memutar video" else "Ketuk foto untuk membuka di Galeri HP",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }
        }
    }
}
