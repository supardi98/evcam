package net.supardi.evcam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import net.supardi.evcam.logic.*
import kotlin.math.abs


@Composable
fun GridOverlay(
    gridType: GridType,
    modifier: Modifier = Modifier
) {
    if (gridType == GridType.NONE) return
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val paintColor = Color.White.copy(alpha = 0.5f)
        val strokeWidth = 1.dp.toPx()
        
        when (gridType) {
            GridType.THIRDS -> {
                drawLine(paintColor, Offset(w / 3, 0f), Offset(w / 3, h), strokeWidth)
                drawLine(paintColor, Offset(w * 2 / 3, 0f), Offset(w * 2 / 3, h), strokeWidth)
                drawLine(paintColor, Offset(0f, h / 3), Offset(w, h / 3), strokeWidth)
                drawLine(paintColor, Offset(0f, h * 2 / 3), Offset(w, h * 2 / 3), strokeWidth)
            }
            GridType.FOURTHS -> {
                for (i in 1..3) {
                    drawLine(paintColor, Offset(w * i / 4, 0f), Offset(w * i / 4, h), strokeWidth)
                    drawLine(paintColor, Offset(0f, h * i / 4), Offset(w, h * i / 4), strokeWidth)
                }
            }
            GridType.GOLDEN_RATIO -> {
                drawLine(paintColor, Offset(w * 0.382f, 0f), Offset(w * 0.382f, h), strokeWidth)
                drawLine(paintColor, Offset(w * 0.618f, 0f), Offset(w * 0.618f, h), strokeWidth)
                drawLine(paintColor, Offset(0f, h * 0.382f), Offset(w, h * 0.382f), strokeWidth)
                drawLine(paintColor, Offset(0f, h * 0.618f), Offset(w, h * 0.618f), strokeWidth)
            }
            GridType.CROSSHAIR -> {
                val length = 20.dp.toPx()
                drawLine(paintColor, Offset(w / 2, h / 2 - length), Offset(w / 2, h / 2 + length), strokeWidth)
                drawLine(paintColor, Offset(w / 2 - length, h / 2), Offset(w / 2 + length, h / 2), strokeWidth)
            }
            GridType.NONE -> {}
        }
    }
}

@Composable
fun CinemaGuideOverlay(
    rotationDegrees: Float = 0f,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val normRot = (rotationDegrees % 360f + 360f) % 360f
        val isLandscape = (normRot in 45f..135f) || (normRot in 225f..315f)

        if (isLandscape) {
            // Held horizontally: Physical width = h, Physical height = w
            val targetPhysicalH = h * (9f / 21f)
            if (w > targetPhysicalH) {
                val barW = (w - targetPhysicalH) / 2f
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(barW, h)
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(w - barW, 0f),
                    size = androidx.compose.ui.geometry.Size(barW, h)
                )
            }
        } else {
            // Held vertically (Portrait)
            val targetH = w * (9f / 21f)
            if (h > targetH) {
                val barH = (h - targetH) / 2f
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(w, barH)
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.6f),
                    topLeft = Offset(0f, h - barH),
                    size = androidx.compose.ui.geometry.Size(w, barH)
                )
            }
        }
    }
}

@Composable
fun VirtualHorizonOverlay(
    deviceOrientation: DeviceOrientationData,
    modifier: Modifier = Modifier
) {
    val roll = deviceOrientation.roll
    val pitch = deviceOrientation.pitch
    val normalizedRoll = (roll % 90f + 90f) % 90f
    val isRollLevel = normalizedRoll < 2f || normalizedRoll > 88f
    val isPitchLevel = kotlin.math.abs(pitch) < 3f
    val isLevel = isRollLevel && isPitchLevel
    val levelColor = if (isLevel) Color(0xFF00FF00) else Color.White.copy(alpha = 0.6f)
    
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            
            val tickLength = 25.dp.toPx()
            val tickGap = 35.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = 0.4f),
                start = Offset(center.x - tickGap - tickLength, center.y),
                end = Offset(center.x - tickGap, center.y),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.4f),
                start = Offset(center.x + tickGap, center.y),
                end = Offset(center.x + tickGap + tickLength, center.y),
                strokeWidth = 2.dp.toPx()
            )
            
            val circleRadius = 14.dp.toPx()
            val spokeInner = 14.dp.toPx()
            val spokeOuter = 40.dp.toPx()
            
            withTransform({
                rotate(degrees = -roll, pivot = center)
            }) {
                drawCircle(
                    color = levelColor,
                    radius = circleRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawLine(levelColor, Offset(center.x - spokeOuter, center.y), Offset(center.x - spokeInner, center.y), 2.dp.toPx())
                drawLine(levelColor, Offset(center.x + spokeInner, center.y), Offset(center.x + spokeOuter, center.y), 2.dp.toPx())
                drawLine(levelColor, Offset(center.x, center.y - spokeOuter), Offset(center.x, center.y - spokeInner), 2.dp.toPx())
                drawLine(levelColor, Offset(center.x, center.y + spokeInner), Offset(center.x, center.y + spokeOuter), 2.dp.toPx())
            }
            
            val pitchFactor = (pitch / 45f).coerceIn(-1f, 1f)
            val bubbleOffset = Offset(
                x = center.x,
                y = center.y + pitchFactor * 80.dp.toPx()
            )
            
            drawCircle(
                color = if (isLevel) Color(0xFF00FF00) else Color.White.copy(alpha = 0.5f),
                radius = 10.dp.toPx(),
                center = bubbleOffset,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${kotlin.math.abs(roll).toInt()}°",
                color = levelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = (-30).dp)
            )
        }
    }
}

@Composable
fun AudioMeterOverlay(
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val numSegments = 10
    val activeSegments = (audioLevel * numSegments).toInt().coerceIn(0, numSegments)

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MIC", color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        for (i in (numSegments - 1) downTo 0) {
            val isActive = i < activeSegments
            val segColor = when {
                i >= 8 -> Color.Red
                i >= 6 -> Color.Yellow
                else -> Color.Green
            }
            val displayColor = if (isActive) segColor else segColor.copy(alpha = 0.2f)
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 4.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(displayColor)
            )
        }
    }
}
