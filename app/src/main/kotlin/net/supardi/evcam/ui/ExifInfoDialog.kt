package net.supardi.evcam.ui

import android.content.Context
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
                        
                        val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                        if (iso != null) list.add(ExifProperty("ISO", iso))
                        
                        val exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                        if (exposure != null) list.add(ExifProperty("Shutter Speed", "${exposure}s"))
                        
                        val date = exif.getAttribute(ExifInterface.TAG_DATETIME)
                        if (date != null) list.add(ExifProperty("Date Time", date))
                        
                        val w = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
                        val h = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)
                        if (w != null && h != null) list.add(ExifProperty("Resolution", "${w}x${h}"))
                        
                        val flash = exif.getAttribute(ExifInterface.TAG_FLASH)
                        if (flash != null) list.add(ExifProperty("Flash", flash))
                        
                        val wb = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE)
                        if (wb != null) list.add(ExifProperty("White Balance", wb))
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
                                Text(text = prop.value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
