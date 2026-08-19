package net.supardi.evcam.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.supardi.evcam.logic.HdrParams
import net.supardi.evcam.logic.HdrProcessor
import java.io.File

class HdrTuningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    HdrTuningScreen()
                }
            }
        }
    }
}

@Composable
fun HdrTuningScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("evcam_prefs", Context.MODE_PRIVATE) }

    // State for HDR Params
    var sigma by remember { mutableStateOf(prefs.getFloat("hdr_exposedness_sigma", 0.4f)) }
    var saturation by remember { mutableStateOf(prefs.getFloat("hdr_saturation_boost", 1.0f)) }
    var bias by remember { mutableStateOf(prefs.getFloat("hdr_normal_bias", 1.5f)) }
    var contrast by remember { mutableStateOf(prefs.getFloat("hdr_contrast_intensity", 1.0f)) }

    // State for Image Picker
    var availableBursts by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedBurst by remember { mutableStateOf<String?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Load available bursts on start
    LaunchedEffect(Unit) {
        val picsDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (picsDir != null && picsDir.exists()) {
            val files = picsDir.listFiles { _, name -> name.startsWith("IMG_HDR_") && name.endsWith("_EV0.jpg") }
            if (files != null) {
                val bursts = files.map { it.name.removePrefix("IMG_HDR_").substringBefore("_EV0.jpg") }
                availableBursts = bursts.sortedDescending()
                if (bursts.isNotEmpty()) {
                    selectedBurst = availableBursts.first()
                }
            }
        }
    }

    // Function to re-process the preview
    fun updatePreview() {
        if (selectedBurst == null) return
        val picsDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return
        
        scope.launch {
            isProcessing = true
            val bytesList = mutableListOf<ByteArray>()
            try {
                withContext(Dispatchers.IO) {
                    for (i in 0..4) {
                        val file = File(picsDir, "IMG_HDR_${selectedBurst}_EV${i}.jpg")
                        if (file.exists()) {
                            bytesList.add(file.readBytes())
                        }
                    }
                }
                
                if (bytesList.size == 5) {
                    val params = HdrParams(
                        exposednessSigma = sigma.toDouble(),
                        saturationBoost = saturation,
                        normalBias = bias,
                        contrastIntensity = contrast
                    )
                    // Downscale to 1080p width for faster live preview
                    val bmp = HdrProcessor.processHdrBurst(bytesList, params, downscaleWidth = 1080) { _ -> }
                    previewBitmap = bmp
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isProcessing = false
            }
        }
    }

    // Process initially and on parameter drag completion
    LaunchedEffect(selectedBurst) {
        if (selectedBurst != null) {
            updatePreview()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("HDR+ Tuning Studio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = {
                    prefs.edit()
                        .putFloat("hdr_exposedness_sigma", sigma)
                        .putFloat("hdr_saturation_boost", saturation)
                        .putFloat("hdr_normal_bias", bias)
                        .putFloat("hdr_contrast_intensity", contrast)
                        .apply()
                    Toast.makeText(context, "HDR Settings Saved", Toast.LENGTH_SHORT).show()
                    (context as? ComponentActivity)?.finish()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow, contentColor = Color.Black)
            ) {
                Text("Save & Exit")
            }
        }

        // Preview Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap!!.asImageBitmap(),
                    contentDescription = "Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else if (!isProcessing) {
                Text("Select an HDR burst below", color = Color.Gray)
            }
            
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.Yellow)
                }
            }
        }

        // Burst Picker
        if (availableBursts.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableBursts) { burstTs ->
                    val isSelected = burstTs == selectedBurst
                    val f = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "IMG_HDR_${burstTs}_EV2.jpg")
                    if (f.exists()) {
                        val bmp = BitmapFactory.decodeFile(f.absolutePath)
                        if (bmp != null) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color.Yellow else Color.Transparent)
                                    .padding(if (isSelected) 2.dp else 0.dp)
                                    .clickable { selectedBurst = burstTs }
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sliders
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(16.dp)
        ) {
            // Sigma (Exposedness)
            Text("Exposure Blend Sigma: ${"%.2f".format(sigma)}", color = Color.White, fontSize = 12.sp)
            Slider(
                value = sigma,
                onValueChange = { sigma = it },
                onValueChangeFinished = { updatePreview() },
                valueRange = 0.05f..1.0f
            )

            // Saturation
            Text("Saturation Boost: ${"%.1f".format(saturation)}x", color = Color.White, fontSize = 12.sp)
            Slider(
                value = saturation,
                onValueChange = { saturation = it },
                onValueChangeFinished = { updatePreview() },
                valueRange = 0.5f..3.0f
            )

            // Bias
            Text("Normal Frame Bias: ${"%.1f".format(bias)}x", color = Color.White, fontSize = 12.sp)
            Slider(
                value = bias,
                onValueChange = { bias = it },
                onValueChangeFinished = { updatePreview() },
                valueRange = 1.0f..3.0f
            )

            // Contrast
            Text("Global Contrast (S-Curve): ${"%.2f".format(contrast)}", color = Color.White, fontSize = 12.sp)
            Slider(
                value = contrast,
                onValueChange = { contrast = it },
                onValueChangeFinished = { updatePreview() },
                valueRange = 0.0f..2.0f
            )
        }
    }
}
