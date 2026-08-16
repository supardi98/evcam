package net.supardi.evcam.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import coil.compose.AsyncImage
import net.supardi.evcam.CameraMode
import net.supardi.evcam.fetchLatestMediaUri

private fun fetchRecentMediaList(context: Context, limit: Int = 30): List<Uri> {
    val list = mutableListOf<Uri>()
    try {
        val projection = arrayOf(android.provider.MediaStore.Images.Media._ID)
        val sortOrder = "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"
        val queryUri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(queryUri, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && list.size < limit) {
                val id = cursor.getLong(idColumn)
                list.add(android.content.ContentUris.withAppendedId(queryUri, id))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
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
    val mediaList = remember(lastCapturedUri, lastCapturedBitmap) {
        val fetched = fetchRecentMediaList(context, limit = 30).toMutableList()
        if (lastCapturedUri != null && !fetched.contains(lastCapturedUri)) {
            fetched.add(0, lastCapturedUri)
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

                // Center Image Pager area: Pure borderless floating images (Tap photo to open in Gallery)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val openGallery = {
                        val activeUri = mediaList.getOrNull(pagerState.currentPage) ?: lastCapturedUri ?: fetchLatestMediaUri(context)
                        if (activeUri != null) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(activeUri, if (cameraMode == CameraMode.VIDEO) "video/*" else "image/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No Gallery app found", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "No Gallery item found", Toast.LENGTH_SHORT).show()
                        }
                    }

                    if (pageCount > 0) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val itemUri = mediaList.getOrNull(page)
                            if (page == 0 && lastCapturedBitmap != null) {
                                Image(
                                    bitmap = lastCapturedBitmap.asImageBitmap(),
                                    contentDescription = "Preview $page",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { openGallery() }
                                )
                            } else if (itemUri != null) {
                                AsyncImage(
                                    model = itemUri,
                                    contentDescription = "Preview $page",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { openGallery() }
                                )
                            }
                        }
                    }
                }

                // Subtitle hint at bottom
                Text(
                    text = "Ketuk foto untuk membuka di Galeri HP",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }
        }
    }
}
