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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import net.supardi.evcam.CameraMode
import net.supardi.evcam.fetchLatestMediaUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaItem(
    val uri: Uri,
    val isVideo: Boolean,
    val dateAdded: Long = 0L
)

data class MediaInfo(
    val fileName: String = "",
    val fileSize: String = "",
    val dateTime: String = "",
    val resolution: String = "",
    val duration: String = "",   // video only
    val mimeType: String = "",
    val aspectRatioStr: String = "" // e.g., "16:9", "4:3"
)

private fun getGcd(a: Int, b: Int): Int = if (b == 0) a else getGcd(b, a % b)

private fun fetchMediaInfo(context: Context, uri: Uri, isVideo: Boolean): MediaInfo {
    return try {
        val projection = if (isVideo) arrayOf(
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE
        ) else arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE
        )

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = try { cursor.getString(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DISPLAY_NAME else MediaStore.Images.Media.DISPLAY_NAME)) ?: "" } catch (e: Exception) { "" }
                val size = try { cursor.getLong(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.SIZE else MediaStore.Images.Media.SIZE)) } catch (e: Exception) { 0L }
                val date = try { cursor.getLong(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DATE_ADDED else MediaStore.Images.Media.DATE_ADDED)) } catch (e: Exception) { 0L }
                val width = try { cursor.getInt(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.WIDTH else MediaStore.Images.Media.WIDTH)) } catch (e: Exception) { 0 }
                val height = try { cursor.getInt(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.HEIGHT else MediaStore.Images.Media.HEIGHT)) } catch (e: Exception) { 0 }
                val mime = try { cursor.getString(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.MIME_TYPE else MediaStore.Images.Media.MIME_TYPE)) ?: "" } catch (e: Exception) { "" }
                val duration = if (isVideo) {
                    try {
                        val ms = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                        val s = ms / 1000
                        val m = s / 60
                        val h = m / 60
                        if (h > 0) "%d:%02d:%02d".format(h, m % 60, s % 60) else "%d:%02d".format(m, s % 60)
                    } catch (e: Exception) { "" }
                } else ""

                val sizeStr = when {
                    size >= 1_000_000 -> "%.1f MB".format(size / 1_000_000f)
                    size >= 1_000 -> "%.0f KB".format(size / 1_000f)
                    else -> "$size B"
                }
                val dateStr = if (date > 0) SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault()).format(Date(date * 1000)) else ""
                val resStr = if (width > 0 && height > 0) "${width} × ${height}" else ""
                val mimeShort = mime.substringAfterLast('/').uppercase()
                
                var aspect = ""
                if (width > 0 && height > 0) {
                    val factor = getGcd(width, height)
                    if (factor > 0) {
                        val wRatio = width / factor
                        val hRatio = height / factor
                        // Normalize orientation ratios to standard labels, e.g. 16:9, 4:3, 1:1
                        val maxR = kotlin.math.max(wRatio, hRatio)
                        val minR = kotlin.math.min(wRatio, hRatio)
                        aspect = if (maxR == 16 && minR == 9) "16:9"
                                 else if (maxR == 4 && minR == 3) "4:3"
                                 else if (maxR == 1 && minR == 1) "1:1"
                                 else "$wRatio:$hRatio"
                    }
                }

                MediaInfo(
                    fileName = name,
                    fileSize = sizeStr,
                    dateTime = dateStr,
                    resolution = resStr,
                    duration = duration,
                    mimeType = mimeShort,
                    aspectRatioStr = aspect
                )
            } else MediaInfo()
        } ?: MediaInfo()
    } catch (e: Exception) {
        MediaInfo()
    }
}


private fun fetchRecentMediaList(context: Context, limit: Int = 500): List<MediaItem> {
    val list = mutableListOf<MediaItem>()
    val contentResolver = context.contentResolver

    try {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(queryUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                list.add(MediaItem(uri = ContentUris.withAppendedId(queryUri, cursor.getLong(idCol)), isVideo = false, dateAdded = cursor.getLong(dateCol)))
            }
        }
    } catch (e: Exception) { e.printStackTrace() }

    try {
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED)
        val queryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(queryUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                list.add(MediaItem(uri = ContentUris.withAppendedId(queryUri, cursor.getLong(idCol)), isVideo = true, dateAdded = cursor.getLong(dateCol)))
            }
        }
    } catch (e: Exception) { e.printStackTrace() }

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
        ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
    }

    val mediaList = remember(lastCapturedUri, lastCapturedBitmap) {
        val fetched = fetchRecentMediaList(context, limit = 500).toMutableList()
        if (lastCapturedUri != null && fetched.none { it.uri == lastCapturedUri }) {
            fetched.add(0, MediaItem(uri = lastCapturedUri, isVideo = (cameraMode == CameraMode.VIDEO)))
        }
        fetched
    }

    val pageCount = if (mediaList.isNotEmpty()) mediaList.size else if (lastCapturedBitmap != null) 1 else 0
    val pagerState = rememberPagerState(initialPage = 0) { pageCount }
    val scope = rememberCoroutineScope()

    // Fetch info for current page
    val currentItem = mediaList.getOrNull(pagerState.currentPage)
    val currentInfo by produceState(initialValue = MediaInfo(), pagerState.currentPage, currentItem) {
        value = if (currentItem != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                fetchMediaInfo(context, currentItem.uri, currentItem.isVideo)
            }
        } else MediaInfo()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        // Full-screen backdrop:
        //   • horizontal swipe anywhere → navigate pager
        //   • tap (little/no drag) → dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .pointerInput(pageCount) {
                    detectTapGestures(
                        onTap = { onDismiss() }
                    )
                }
                .pointerInput(pageCount) {
                    var totalDragX = 0f
                    detectDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd = {
                            if (kotlin.math.abs(totalDragX) > 80 && pageCount > 1) {
                                val next = if (totalDragX < 0) {
                                    (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
                                } else {
                                    (pagerState.currentPage - 1).coerceAtLeast(0)
                                }
                                scope.launch { pagerState.animateScrollToPage(next) }
                            }
                        },
                        onDragCancel = { totalDragX = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount.x
                        }
                    )
                },

            contentAlignment = Alignment.Center
        ) {
            if (pageCount > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .wrapContentHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // Absorb clicks so they do not propagate to the backdrop's click/tap detector
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top bar — counter & close
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / $pageCount",
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    // HorizontalPager — driven programmatically from the full-screen swipe above
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = false,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).wrapContentHeight()
                    ) { page ->
                        val item = mediaList.getOrNull(page)
                        val isCurrentVideo = item?.isVideo == true || (page == 0 && cameraMode == CameraMode.VIDEO)

                        val openActiveMedia = {
                            val activeUri = item?.uri ?: lastCapturedUri ?: fetchLatestMediaUri(context)
                            if (activeUri != null) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(activeUri, if (isCurrentVideo) "video/*" else "image/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    })
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No Gallery or Video Player app found", Toast.LENGTH_SHORT).show()
                                }
                            } else Toast.makeText(context, "Media not found", Toast.LENGTH_SHORT).show()
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
                            if (page == 0 && lastCapturedBitmap != null) {
                                Image(
                                    bitmap = lastCapturedBitmap.asImageBitmap(),
                                    contentDescription = "Preview $page",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .pointerInput(Unit) { detectTapGestures(onTap = { openActiveMedia() }) }
                                )
                            } else if (item != null) {
                                AsyncImage(
                                    model = item.uri, imageLoader = imageLoader,
                                    contentDescription = "Preview $page",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .pointerInput(Unit) { detectTapGestures(onTap = { openActiveMedia() }) }
                                )
                            }
                            if (isCurrentVideo) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .pointerInput(Unit) { detectTapGestures(onTap = { openActiveMedia() }) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Yellow, modifier = Modifier.size(42.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Media info card
                    if (currentInfo.fileName.isNotEmpty() || currentInfo.fileSize.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (currentInfo.fileName.isNotEmpty()) {
                                MediaInfoRow(label = "File", value = currentInfo.fileName)
                            }
                            if (currentInfo.dateTime.isNotEmpty()) {
                                MediaInfoRow(label = "Date", value = currentInfo.dateTime)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (currentInfo.resolution.isNotEmpty()) {
                                    MediaInfoPill(label = currentInfo.resolution)
                                }
                                if (currentInfo.aspectRatioStr.isNotEmpty()) {
                                    MediaInfoPill(label = currentInfo.aspectRatioStr, highlight = true)
                                }
                                if (currentInfo.fileSize.isNotEmpty()) {
                                    MediaInfoPill(label = currentInfo.fileSize)
                                }

                                if (currentInfo.duration.isNotEmpty()) {
                                    MediaInfoPill(label = "⏱ ${currentInfo.duration}", highlight = true)
                                }
                                if (currentInfo.mimeType.isNotEmpty()) {
                                    MediaInfoPill(label = currentInfo.mimeType)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Swipe image to browse  •  Tap to open  •  Tap outside to dismiss",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.width(36.dp))
        Text(value, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
    }
}

@Composable
private fun MediaInfoPill(label: String, highlight: Boolean = false) {
    Text(
        text = label,
        color = if (highlight) Color.Yellow else Color.White,
        fontSize = 11.sp,
        fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
