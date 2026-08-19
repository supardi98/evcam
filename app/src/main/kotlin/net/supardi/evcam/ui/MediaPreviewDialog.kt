package net.supardi.evcam.ui

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Timer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
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
import net.supardi.evcam.logic.*
import net.supardi.evcam.logic.fetchLatestMediaUri


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
    val aspectRatioStr: String = "", // e.g., "16:9", "4:3"
    val mp: String = "",
    val fps: String = ""
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
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.ORIENTATION
        )

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = try { cursor.getString(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DISPLAY_NAME else MediaStore.Images.Media.DISPLAY_NAME)) ?: "" } catch (e: Exception) { "" }
                val size = try { cursor.getLong(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.SIZE else MediaStore.Images.Media.SIZE)) } catch (e: Exception) { 0L }
                val date = try { cursor.getLong(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.DATE_ADDED else MediaStore.Images.Media.DATE_ADDED)) } catch (e: Exception) { 0L }
                var width = try { cursor.getInt(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.WIDTH else MediaStore.Images.Media.WIDTH)) } catch (e: Exception) { 0 }
                var height = try { cursor.getInt(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.HEIGHT else MediaStore.Images.Media.HEIGHT)) } catch (e: Exception) { 0 }
                val mime = try { cursor.getString(cursor.getColumnIndexOrThrow(if (isVideo) MediaStore.Video.Media.MIME_TYPE else MediaStore.Images.Media.MIME_TYPE)) ?: "" } catch (e: Exception) { "" }
                
                // Read rotation to determine if we should swap width and height
                var rotation = 0
                var fps = ""
                try {
                    if (isVideo) {
                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, uri)
                            val rotStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                            if (rotStr != null) {
                                rotation = rotStr.toInt()
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val capFps = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                                if (!capFps.isNullOrEmpty()) fps = capFps
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            try { retriever.release() } catch (e: Exception) {}
                        }

                        if (fps.isEmpty() || fps == "0.000000" || fps == "0") {
                            val extractor = android.media.MediaExtractor()
                            try {
                                extractor.setDataSource(context, uri, null)
                                for (i in 0 until extractor.trackCount) {
                                    val format = extractor.getTrackFormat(i)
                                    val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                                    if (mime.startsWith("video/")) {
                                        if (format.containsKey(android.media.MediaFormat.KEY_FRAME_RATE)) {
                                            val fr = format.getInteger(android.media.MediaFormat.KEY_FRAME_RATE)
                                            if (fr > 0) fps = "$fr"
                                        }
                                        if (fps.isEmpty() || fps == "0") {
                                            var sampleCount = 0
                                            val buf = java.nio.ByteBuffer.allocate(1024)
                                            while (extractor.readSampleData(buf, 0) >= 0) {
                                                sampleCount++
                                                if (extractor.sampleTime > 1_000_000L) break
                                                extractor.advance()
                                            }
                                            if (sampleCount > 5) fps = "$sampleCount"
                                        }
                                        break
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                try { extractor.release() } catch (e: Exception) {}
                            }
                        }

                        if (fps.isNotEmpty()) {
                            try { fps = fps.toFloat().toInt().toString() } catch (e: Exception) {}
                        }
                    } else {
                        rotation = try {
                            cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION))
                        } catch (e: Exception) { 0 }
                    }
                } catch (e: Exception) {}


                // If portrait rotation, swap width and height
                if (rotation == 90 || rotation == 270) {
                    val temp = width
                    width = height
                    height = temp
                }

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
                
                val arStr = if (width > 0 && height > 0) {
                    val gcd = getGcd(width, height)
                    if (gcd > 0) "${width/gcd}:${height/gcd}" else ""
                } else ""

                val mpStr = if (width > 0 && height > 0) {
                    val mpFloat = (width * height) / 1000000f
                    if (mpFloat >= 0.1f) "%.1f MP".format(mpFloat) else ""
                } else ""

                MediaInfo(
                    fileName = name,
                    fileSize = sizeStr,
                    dateTime = dateStr,
                    resolution = resStr,
                    duration = duration,
                    mimeType = mimeShort,
                    aspectRatioStr = arStr,
                    mp = mpStr,
                    fps = if (fps.isNotEmpty()) "${fps} FPS" else ""
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MediaPreviewDialog(
    lastCapturedBitmap: Bitmap?,
    lastCapturedUri: Uri?,
    cameraMode: CameraMode,
    context: Context,
    onDismiss: () -> Unit,
    onMediaDeleted: (Uri) -> Unit = {}
) {
    val imageLoader = remember(context) {
        ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
    }

    val mediaList = remember { androidx.compose.runtime.mutableStateListOf<MediaItem>() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, lastCapturedUri, lastCapturedBitmap) {
        val refresh = {
            val fetched = fetchRecentMediaList(context, limit = 500).toMutableList()
            if (lastCapturedUri != null && fetched.none { it.uri == lastCapturedUri }) {
                if (net.supardi.evcam.logic.isUriValid(context, lastCapturedUri)) {
                    fetched.add(0, MediaItem(uri = lastCapturedUri, isVideo = (cameraMode == CameraMode.VIDEO)))
                }
            }
            mediaList.clear()
            mediaList.addAll(fetched)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refresh() // initial load
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val actualCount = if (mediaList.isNotEmpty()) mediaList.size else if (lastCapturedBitmap != null) 1 else 0
    val pageCount = actualCount
    val initialPage = 0
    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }
    val scope = rememberCoroutineScope()

    val currentActualIndex = if (actualCount > 0) pagerState.currentPage else 0

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteItem by remember { mutableStateOf<MediaItem?>(null) }
    
    var isReprocessingHdr by remember { mutableStateOf(false) }
    var reprocessProgress by remember { mutableIntStateOf(0) }
    var imageRefreshKey by remember { mutableStateOf(System.currentTimeMillis()) }

    // Shared post-delete navigation — called after item is confirmed deleted
    fun onItemDeleted(item: MediaItem) {
        // Also delete raw HDR components if applicable
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            context.contentResolver.query(item.uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    if (name != null && name.startsWith("IMG_HDR_")) {
                        val timestampStr = name.removePrefix("IMG_HDR_").substringBefore(".jpg")
                        val picsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                        if (picsDir != null && timestampStr.isNotEmpty()) {
                            for (i in 0..4) {
                                val f = java.io.File(picsDir, "IMG_HDR_${timestampStr}_EV${i}.jpg")
                                if (f.exists()) f.delete()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val idx = mediaList.indexOf(item)
        val removeIdx = if (idx >= 0) idx else currentActualIndex
        if (removeIdx < mediaList.size) mediaList.removeAt(removeIdx)
        onMediaDeleted(item.uri)  // notify parent to refresh thumbnail
        if (mediaList.isEmpty()) {
            onDismiss()
        } else {
            val next = removeIdx.coerceAtMost(mediaList.size - 1)
            scope.launch { pagerState.scrollToPage(next) }
        }
    }

    // Android 11+ delete permission launcher
    val deletePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDeleteItem?.let { item ->
                onItemDeleted(item)
                pendingDeleteItem = null
            }
        }
    }

    fun deleteCurrentItem() {
        val item = mediaList.getOrNull(currentActualIndex) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pending = MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri))
            pendingDeleteItem = item
            deletePermLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
        } else {
            try {
                context.contentResolver.delete(item.uri, null, null)
                onItemDeleted(item)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
            }
        }
    }


    var showGalleryGrid by remember { mutableStateOf(false) }
    var showExifDialog by remember { mutableStateOf(false) }

    // Fetch info for current page
    val currentItem = mediaList.getOrNull(currentActualIndex)
    val currentInfo by produceState(initialValue = MediaInfo(), currentActualIndex, currentItem) {
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
        // Full-screen backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .pointerInput(pageCount) {
                    detectTapGestures(
                        onTap = { onDismiss() }
                    )
                },

            contentAlignment = Alignment.Center
        ) {
            if (actualCount > 0) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = true,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val itemIndex = page
                    val item = mediaList.getOrNull(itemIndex)
                    val isCurrentVideo = item?.isVideo == true || (page == 0 && cameraMode == CameraMode.VIDEO)

                    val pageInfo by produceState(initialValue = MediaInfo(), item) {
                        value = if (item != null) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                fetchMediaInfo(context, item.uri, item.isVideo)
                            }
                        } else MediaInfo()
                    }
                    
                    var rawComponents by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<Uri>>(emptyList()) }
                    var selectedRawUri by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Uri?>(null) }
                    
                    androidx.compose.runtime.LaunchedEffect(pageInfo.fileName) {
                        if (pageInfo.fileName.startsWith("IMG_HDR_")) {
                            val timestampStr = pageInfo.fileName.removePrefix("IMG_HDR_").substringBefore(".jpg")
                            val picsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                            if (picsDir != null && timestampStr.isNotEmpty()) {
                                val uris = mutableListOf<Uri>()
                                for (i in 0..4) {
                                    val f = java.io.File(picsDir, "IMG_HDR_${timestampStr}_EV${i}.jpg")
                                    if (f.exists()) {
                                        uris.add(Uri.fromFile(f))
                                    }
                                }
                                rawComponents = uris
                            }
                        } else {
                            rawComponents = emptyList()
                            selectedRawUri = null
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val openActiveMedia = {
                            val activeUri = item?.uri ?: lastCapturedUri ?: fetchLatestMediaUri(context)
                            if (activeUri != null) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(activeUri, if (isCurrentVideo) "video/*" else "image/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    })
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No Gallery app found", Toast.LENGTH_SHORT).show()
                                }
                            } else Toast.makeText(context, "Media not found", Toast.LENGTH_SHORT).show()
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedRawUri != null) {
                                AsyncImage(
                                    model = selectedRawUri, imageLoader = imageLoader,
                                    contentDescription = "Preview Raw EV",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .pointerInput(item) { detectTapGestures(onTap = { openActiveMedia() }) }
                                )
                            } else if (page == 0 && lastCapturedBitmap != null) {
                                Image(
                                    bitmap = lastCapturedBitmap.asImageBitmap(),
                                    contentDescription = "Preview $page",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .pointerInput(item) { detectTapGestures(onTap = { openActiveMedia() }) }
                                )
                            } else if (item != null) {
                                AsyncImage(
                                    model = coil.request.ImageRequest.Builder(context)
                                        .data(item.uri)
                                        .memoryCacheKey(item.uri.toString() + "_" + imageRefreshKey)
                                        .diskCacheKey(item.uri.toString() + "_" + imageRefreshKey)
                                        .build(),
                                    imageLoader = imageLoader,
                                    contentDescription = "Preview $page",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .pointerInput(item) { detectTapGestures(onTap = { openActiveMedia() }) }
                                )
                            }
                            if (isCurrentVideo) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .pointerInput(item) { detectTapGestures(onTap = { openActiveMedia() }) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Yellow, modifier = Modifier.size(42.dp))
                                }
                            }
                        }

                        if (rawComponents.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Raw HDR Components (Tap to preview)", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Main thumbnail
                                Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).clickable { selectedRawUri = null }.border(1.dp, if (selectedRawUri == null) Color.Yellow else Color.Transparent, RoundedCornerShape(8.dp))) {
                                    AsyncImage(
                                        model = coil.request.ImageRequest.Builder(context)
                                            .data(item?.uri)
                                            .memoryCacheKey(item?.uri.toString() + "_" + imageRefreshKey)
                                            .diskCacheKey(item?.uri.toString() + "_" + imageRefreshKey)
                                            .build(),
                                        contentDescription = "Final", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    Text("Final", color = Color.White, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.6f)).fillMaxWidth(), textAlign = TextAlign.Center)
                                }
                                // Components
                                val evLabels = listOf("-4", "-2", "0", "+2", "+4")
                                rawComponents.forEachIndexed { idx, uri ->
                                    val isSelected = selectedRawUri == uri
                                    Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).clickable { selectedRawUri = uri }.border(1.dp, if (isSelected) Color.Yellow else Color.Transparent, RoundedCornerShape(8.dp))) {
                                        AsyncImage(model = uri, contentDescription = "EV", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        Text(evLabels.getOrNull(idx) ?: "", color = Color.White, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.6f)).fillMaxWidth(), textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            
                            // Sleek HDR Action Buttons
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            var showReHdrConfirm by remember { mutableStateOf(false) }
                            var showDeleteRawConfirm by remember { mutableStateOf(false) }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Re-process Button
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { showReHdrConfirm = true },
                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.Yellow),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Yellow.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Re-HDR", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                
                                // Hapus Raw Button
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { showDeleteRawConfirm = true },
                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Delete Raw", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                if (selectedRawUri != null) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = {
                                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                try {
                                                    val rawFile = java.io.File(selectedRawUri!!.path!!)
                                                    if (rawFile.exists()) {
                                                        val destName = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(Date()) + "_RAW_EXTRACT.jpg"
                                                        val values = android.content.ContentValues().apply {
                                                            put(MediaStore.Images.Media.DISPLAY_NAME, destName)
                                                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                                            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                                                        }
                                                        val destUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                                        if (destUri != null) {
                                                            context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                                                                java.io.FileInputStream(rawFile).use { inStream ->
                                                                    inStream.copyTo(outStream)
                                                                }
                                                            }
                                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                Toast.makeText(context, "Raw photo saved to gallery", Toast.LENGTH_SHORT).show()
                                                                val fetched = fetchRecentMediaList(context, limit = 500)
                                                                mediaList.clear()
                                                                mediaList.addAll(fetched)
                                                                
                                                                val newIndex = mediaList.indexOfFirst { it.uri == destUri }
                                                                if (newIndex >= 0) {
                                                                    pagerState.scrollToPage(newIndex)
                                                                }
                                                            }
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        },
                                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Save Extract", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            
                            // Re-HDR Confirm Dialog
                            if (showReHdrConfirm) {
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = { showReHdrConfirm = false },
                                    containerColor = Color(0xFF1E1E1E),
                                    title = { Text("Re-HDR Photo?", color = Color.White, fontWeight = FontWeight.Bold) },
                                    text = { Text("This will overwrite the current HDR photo using your latest Tuning Studio settings. Proceed?", color = Color.Gray) },
                                    confirmButton = {
                                        androidx.compose.material3.TextButton(onClick = {
                                            showReHdrConfirm = false
                                            val timestampStr = pageInfo.fileName.removePrefix("IMG_HDR_").substringBefore(".jpg")
                                            val picsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                                            if (picsDir != null && timestampStr.isNotEmpty() && item?.uri != null) {
                                                isReprocessingHdr = true
                                                reprocessProgress = 0
                                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                    try {
                                                        val bytesList = mutableListOf<ByteArray>()
                                                        for (i in 0..4) {
                                                            val f = java.io.File(picsDir, "IMG_HDR_${timestampStr}_EV${i}.jpg")
                                                            if (f.exists()) bytesList.add(f.readBytes())
                                                        }
                                                        if (bytesList.size == 5) {
                                                            val prefs = context.getSharedPreferences("evcam_prefs", Context.MODE_PRIVATE)
                                                            val params = net.supardi.evcam.logic.HdrParams(
                                                                exposednessSigma = prefs.getFloat("hdr_exposedness_sigma", 0.4f).toDouble(),
                                                                saturationBoost = prefs.getFloat("hdr_saturation_boost", 1.0f),
                                                                normalBias = prefs.getFloat("hdr_normal_bias", 1.5f),
                                                                contrastIntensity = prefs.getFloat("hdr_contrast_intensity", 1.0f)
                                                            )
                                                            val finalBmp = net.supardi.evcam.logic.HdrProcessor.processHdrBurst(bytesList, params, null) { progress ->
                                                                reprocessProgress = progress
                                                            }
                                                            
                                                            context.contentResolver.openOutputStream(item.uri, "wt")?.use { out ->
                                                                finalBmp?.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                                            }
                                                            
                                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                Toast.makeText(context, "HDR reprocessed successfully!", Toast.LENGTH_SHORT).show()
                                                                isReprocessingHdr = false
                                                                imageRefreshKey = System.currentTimeMillis()
                                                            }
                                                        } else {
                                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                Toast.makeText(context, "Raw components missing", Toast.LENGTH_SHORT).show()
                                                                isReprocessingHdr = false
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                            Toast.makeText(context, "Failed to re-process HDR", Toast.LENGTH_SHORT).show()
                                                            isReprocessingHdr = false
                                                        }
                                                    }
                                                }
                                            }
                                        }) {
                                            Text("Yes", color = Color.Yellow, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        androidx.compose.material3.TextButton(onClick = { showReHdrConfirm = false }) {
                                            Text("Cancel", color = Color.White)
                                        }
                                    }
                                )
                            }
                            
                            // Delete Raw Confirm Dialog
                            if (showDeleteRawConfirm) {
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = { showDeleteRawConfirm = false },
                                    containerColor = Color(0xFF1E1E1E),
                                    title = { Text("Delete Raw Components?", color = Color.White, fontWeight = FontWeight.Bold) },
                                    text = { Text("This will permanently delete the 5 hidden raw EV photos to save space. You will no longer be able to Re-HDR this photo later.", color = Color.Gray) },
                                    confirmButton = {
                                        androidx.compose.material3.TextButton(onClick = {
                                            showDeleteRawConfirm = false
                                            val timestampStr = pageInfo.fileName.removePrefix("IMG_HDR_").substringBefore(".jpg")
                                            val picsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                                            if (picsDir != null && timestampStr.isNotEmpty()) {
                                                for (i in 0..4) {
                                                    val f = java.io.File(picsDir, "IMG_HDR_${timestampStr}_EV${i}.jpg")
                                                    if (f.exists()) f.delete()
                                                }
                                            }
                                            rawComponents = emptyList()
                                            selectedRawUri = null
                                            Toast.makeText(context, "Raw components deleted", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Text("Delete", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        androidx.compose.material3.TextButton(onClick = { showDeleteRawConfirm = false }) {
                                            Text("Cancel", color = Color.White)
                                        }
                                    }
                                )
                            }

                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Media info card
                        if (pageInfo.fileName.isNotEmpty() || pageInfo.fileSize.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable { showExifDialog = true }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (pageInfo.fileName.isNotEmpty()) {
                                    MediaInfoRow(label = "File", value = pageInfo.fileName)
                                }
                                if (pageInfo.dateTime.isNotEmpty()) {
                                    MediaInfoRow(label = "Date", value = pageInfo.dateTime)
                                }
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (pageInfo.resolution.isNotEmpty()) {
                                        MediaInfoPill(label = pageInfo.resolution)
                                    }
                                    if (pageInfo.aspectRatioStr.isNotEmpty()) {
                                        MediaInfoPill(label = pageInfo.aspectRatioStr, highlight = true)
                                    }
                                    if (pageInfo.fileSize.isNotEmpty()) {
                                        MediaInfoPill(label = pageInfo.fileSize)
                                    }
                                    if (pageInfo.mp.isNotEmpty()) {
                                        MediaInfoPill(label = pageInfo.mp)
                                    }
                                    if (pageInfo.fps.isNotEmpty()) {
                                        MediaInfoPill(label = pageInfo.fps, highlight = true)
                                    }

                                    if (pageInfo.duration.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.Yellow.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Timer,
                                                contentDescription = null,
                                                tint = Color.Yellow,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = pageInfo.duration,
                                                color = Color.Yellow,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    if (pageInfo.mimeType.isNotEmpty()) {
                                        MediaInfoPill(label = pageInfo.mimeType)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Swipe anywhere to browse  •  Tap image to open",
                            color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                // Top bar overlay pinned to top
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var fastJumpDragAccum by remember { mutableFloatStateOf(0f) }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { showGalleryGrid = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .pointerInput(actualCount) {
                                detectHorizontalDragGestures(
                                    onDragStart = { fastJumpDragAccum = 0f },
                                    onHorizontalDrag = { _, dragAmount ->
                                        fastJumpDragAccum += dragAmount
                                        val steps = (fastJumpDragAccum / 30f).toInt()
                                        if (steps != 0) {
                                            fastJumpDragAccum -= steps * 30f
                                            val targetPage = pagerState.currentPage - steps * 5
                                            scope.launch { pagerState.scrollToPage(targetPage) }
                                        }
                                    }
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Gallery Grid",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${currentActualIndex + 1} / $actualCount",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Delete button
                        IconButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    deleteCurrentItem()
                                } else {
                                    showDeleteConfirm = true
                                }
                            },
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                        }
                        // Close button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            
            // HDR Reprocessing Overlay
            if (isReprocessingHdr) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.CircularProgressIndicator(color = Color.Yellow)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Reprocessing HDR... $reprocessProgress%", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xFF1E1E1E),
            title = {
                Text(
                    text = "Hapus ${if (mediaList.getOrNull(pagerState.currentPage)?.isVideo == true) "video" else "foto"} ini?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("File akan dihapus permanen dari perangkat.", color = Color.Gray)
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDeleteConfirm = false
                    deleteCurrentItem()
                }) {
                    Text("Hapus", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Batal", color = Color.White)
                }
            }
        )
    }

    // Gallery Grid Dialog
    if (showGalleryGrid && mediaList.isNotEmpty()) {
        // Filter state
        var galleryFilter by remember { mutableStateOf(0) } // 0=All, 1=Photos, 2=Videos

        // Filtered flat list: Pair(mediaItem, originalIndexInMediaList)
        val filteredPairs = remember(galleryFilter, mediaList.size) {
            mediaList.mapIndexed { i, item -> item to i }
                .filter { (item, _) ->
                    when (galleryFilter) {
                        1 -> !item.isVideo
                        2 -> item.isVideo
                        else -> true
                    }
                }
        }

        // Group by date label
        fun dateLabel(epochSec: Long): String {
            val cal = java.util.Calendar.getInstance()
            val today = cal.clone() as java.util.Calendar
            val itemCal = java.util.Calendar.getInstance().apply { timeInMillis = epochSec * 1000 }
            return when {
                today.get(java.util.Calendar.DAY_OF_YEAR) == itemCal.get(java.util.Calendar.DAY_OF_YEAR) &&
                today.get(java.util.Calendar.YEAR) == itemCal.get(java.util.Calendar.YEAR) -> "Today"
                today.get(java.util.Calendar.DAY_OF_YEAR) - 1 == itemCal.get(java.util.Calendar.DAY_OF_YEAR) &&
                today.get(java.util.Calendar.YEAR) == itemCal.get(java.util.Calendar.YEAR) -> "Yesterday"
                else -> java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH)
                    .format(java.util.Date(epochSec * 1000))
            }
        }

        // Build flat list: null = date header string, non-null = Pair<MediaItem, Int>
        data class GalleryRow(val header: String? = null, val entry: Pair<MediaItem, Int>? = null)
        val flatRows = remember(filteredPairs) {
            val result = mutableListOf<GalleryRow>()
            var lastLabel = ""
            filteredPairs.forEach { pair ->
                val label = dateLabel(pair.first.dateAdded)
                if (label != lastLabel) {
                    result.add(GalleryRow(header = label))
                    lastLabel = label
                }
                result.add(GalleryRow(entry = pair))
            }
            result
        }

        val gridState = rememberLazyGridState()
        LaunchedEffect(showGalleryGrid) {
            // Scroll to current item's approximate position in the flat list
            val currentUri = mediaList.getOrNull(pagerState.currentPage)?.uri
            val targetRow = flatRows.indexOfFirst { it.entry?.first?.uri == currentUri }
            if (targetRow >= 0) gridState.scrollToItem(targetRow)
        }

        Dialog(
            onDismissRequest = { showGalleryGrid = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0A))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Header ───────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All Media (${filteredPairs.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        IconButton(onClick = { showGalleryGrid = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    // ── Filter Tabs ───────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Photos", "Videos").forEachIndexed { idx, label ->
                            val selected = galleryFilter == idx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) Color.White else Color.White.copy(alpha = 0.1f))
                                    .clickable { galleryFilter = idx }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.Black else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // ── Grid with scrollbar ───────────────────────────────────
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = gridState,
                            contentPadding = PaddingValues(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            flatRows.forEachIndexed { rowIdx, row ->
                                if (row.header != null) {
                                    // Full-width date header
                                    item(span = { GridItemSpan(maxLineSpan) }, key = "header_$rowIdx") {
                                        Text(
                                            text = row.header,
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                } else if (row.entry != null) {
                                    val (item, originalIdx) = row.entry
                                    item(key = "item_${item.uri}") {
                                        val isSelected = originalIdx == currentActualIndex
                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .then(
                                                    if (isSelected) Modifier.border(2.dp, Color.Yellow, RoundedCornerShape(6.dp))
                                                    else Modifier
                                                )
                                                .clickable {
                                                    showGalleryGrid = false
                                                    scope.launch { pagerState.scrollToPage(originalIdx) }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = item.uri,
                                                imageLoader = imageLoader,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            if (item.isVideo) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(4.dp)
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Black.copy(alpha = 0.6f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                            if (isSelected) {
                                                Box(modifier = Modifier.fillMaxSize().background(Color.Yellow.copy(alpha = 0.15f)))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── Scrollbar indicator ───────────────────────────────
                        val totalItems = flatRows.size.coerceAtLeast(1)
                        val layoutInfo = gridState.layoutInfo
                        val visibleCount = layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                        val firstIdx = gridState.firstVisibleItemScrollOffset
                        
                        var scrollbarDragY by remember { mutableFloatStateOf(-1f) }
                        
                        val scrollFraction = if (scrollbarDragY >= 0f) {
                            scrollbarDragY
                        } else {
                            if (totalItems > visibleCount)
                                (gridState.firstVisibleItemIndex.toFloat() / (totalItems - visibleCount).toFloat()).coerceIn(0f, 1f)
                            else 0f
                        }

                        // Auto-hide logic
                        var isScrollbarVisible by remember { mutableStateOf(false) }
                        val isScrollInProgress = gridState.isScrollInProgress
                        LaunchedEffect(isScrollInProgress, scrollbarDragY) {
                            if (isScrollInProgress || scrollbarDragY >= 0f) {
                                isScrollbarVisible = true
                            } else {
                                delay(2000)
                                isScrollbarVisible = false
                            }
                        }
                        val scrollbarAlpha by animateFloatAsState(
                            targetValue = if (isScrollbarVisible) 1f else 0f,
                            animationSpec = tween(300)
                        )

                        if (totalItems > visibleCount) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(48.dp)
                                    .padding(vertical = 8.dp)
                                    .pointerInput(totalItems) {
                                        detectVerticalDragGestures(
                                            onDragStart = { offset ->
                                                val currentVisible = gridState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                                                val trackH = size.height.toFloat()
                                                val thumbH = 64.dp.toPx()
                                                val maxThumbTop = trackH - thumbH
                                                var rawFraction = (offset.y - thumbH/2) / maxThumbTop
                                                rawFraction = rawFraction.coerceIn(0f, 1f)
                                                scrollbarDragY = rawFraction
                                                val targetIdx = (rawFraction * (totalItems - currentVisible)).toInt().coerceIn(0, totalItems - 1)
                                                scope.launch { gridState.scrollToItem(targetIdx) }
                                            },
                                            onDragEnd = { scrollbarDragY = -1f },
                                            onDragCancel = { scrollbarDragY = -1f },
                                            onVerticalDrag = { change, _ ->
                                                change.consume()
                                                val currentVisible = gridState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                                                val trackH = size.height.toFloat()
                                                val thumbH = 64.dp.toPx()
                                                val maxThumbTop = trackH - thumbH
                                                var rawFraction = (change.position.y - thumbH/2) / maxThumbTop
                                                rawFraction = rawFraction.coerceIn(0f, 1f)
                                                scrollbarDragY = rawFraction
                                                val targetIdx = (rawFraction * (totalItems - currentVisible)).toInt().coerceIn(0, totalItems - 1)
                                                scope.launch { gridState.scrollToItem(targetIdx) }
                                            }
                                        )
                                    }
                            ) {
                                val densityValue = androidx.compose.ui.platform.LocalDensity.current.density
                                val trackH = maxHeight.value * densityValue
                                val thumbH = 64.dp.value * densityValue
                                val maxThumbTop = (trackH - thumbH).coerceAtLeast(0f)
                                val thumbTop = maxThumbTop * scrollFraction

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset { androidx.compose.ui.unit.IntOffset(x = -16, y = thumbTop.toInt()) }
                                        .width(32.dp)
                                        .height(64.dp)
                                        .alpha(scrollbarAlpha)
                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon(Icons.Filled.ArrowDropUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp).offset(y = 2.dp))
                                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp).offset(y = (-2).dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showExifDialog && currentItem != null) {
        ExifInfoDialog(context, currentItem.uri, currentItem.isVideo) { showExifDialog = false }
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
