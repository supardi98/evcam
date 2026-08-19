package net.supardi.evcam.ui

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class ExifProperty(val label: String, val value: String)

@Composable
fun ExifInfoDialog(
    context: Context,
    uri: Uri,
    isVideo: Boolean,
    onDismiss: () -> Unit
) {
    var exifData by remember { mutableStateOf<List<ExifProperty>>(emptyList()) }

    LaunchedEffect(uri) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val list = mutableListOf<ExifProperty>()
            try {
                if (isVideo) {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    if (title != null) list.add(ExifProperty("Title", title))
                    
                    val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                    if (date != null) list.add(ExifProperty("Date", date))
                    
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    if (w != null && h != null) list.add(ExifProperty("Resolution", "${w}x${h}"))
                    
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    if (duration != null) list.add(ExifProperty("Duration (ms)", duration))
                    
                    val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    if (bitrate != null) list.add(ExifProperty("Bitrate", "${bitrate.toInt() / 1000} kbps"))
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        val fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                        if (fps != null) list.add(ExifProperty("FPS", fps))
                    }
                    
                    retriever.release()
                } else {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val exif = ExifInterface(stream)
                        
                        val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                        val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                        if (make != null || model != null) list.add(ExifProperty("Camera", "${make ?: ""} ${model ?: ""}".trim()))
                        
                        val aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                        if (aperture != null) list.add(ExifProperty("Aperture", "ƒ/$aperture"))
                        
                        val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                        if (focal != null) {
                            try {
                                val parts = focal.split("/")
                                val mm = parts[0].toFloat() / parts[1].toFloat()
                                list.add(ExifProperty("Focal Length", "%.2f mm".format(mm)))
                            } catch(e: Exception) {
                                list.add(ExifProperty("Focal Length", focal))
                            }
                        }
                        
                        @Suppress("DEPRECATION")
                        val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                        if (iso != null) list.add(ExifProperty("ISO", iso))
                        
                        val exposureStr = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                        if (exposureStr != null) {
                            val expDouble = exposureStr.toDoubleOrNull()
                            if (expDouble != null && expDouble > 0 && expDouble < 1.0) {
                                val denom = kotlin.math.round(1.0 / expDouble).toInt()
                                list.add(ExifProperty("Shutter Speed", "1/$denom s"))
                            } else {
                                list.add(ExifProperty("Shutter Speed", "${exposureStr} s"))
                            }
                        }
                        
                        val date = exif.getAttribute(ExifInterface.TAG_DATETIME)
                        if (date != null) list.add(ExifProperty("Date Time", date))
                        
                        val w = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
                        val h = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)
                        if (w != null && h != null) list.add(ExifProperty("Resolution", "${w}x${h}"))
                        
                        val flash = exif.getAttribute(ExifInterface.TAG_FLASH)
                        if (flash != null) list.add(ExifProperty("Flash", flash))
                        
                        val wb = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE)
                        if (wb != null) list.add(ExifProperty("White Balance", wb))
                        
                        val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
                        if (software != null) list.add(ExifProperty("Software", software))
                        
                        val latLong = exif.latLong
                        if (latLong != null && latLong.size == 2) {
                            list.add(ExifProperty("Location", String.format(java.util.Locale.US, "%.5f, %.5f", latLong[0], latLong[1])))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            exifData = list
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Media EXIF Info",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (exifData.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("No EXIF data found.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(exifData) { prop ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = prop.label, color = Color.LightGray, fontSize = 14.sp)
                                if (prop.label == "Location") {
                                    val localContext = androidx.compose.ui.platform.LocalContext.current
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = prop.value,
                                            color = Color(0xFF64B5F6),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                try {
                                                    val parts = prop.value.split(", ")
                                                    if (parts.size == 2) {
                                                        val geoUri = Uri.parse("geo:${parts[0]},${parts[1]}?q=${parts[0]},${parts[1]}")
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, geoUri)
                                                        localContext.startActivity(intent)
                                                    }
                                                } catch(e: Exception) { e.printStackTrace() }
                                            }
                                        )
                                        Box(
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .clickable {
                                                    val clipboardManager = localContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    val clip = android.content.ClipData.newPlainText("Location", prop.value)
                                                    clipboardManager.setPrimaryClip(clip)
                                                    android.widget.Toast.makeText(localContext, "Location copied", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                        ) {
                                            androidx.compose.material3.Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.ContentCopy,
                                                contentDescription = "Copy Location",
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Text(text = prop.value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}
