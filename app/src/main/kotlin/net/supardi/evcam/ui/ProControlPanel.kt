package net.supardi.evcam.ui

import android.hardware.camera2.CaptureRequest
import net.supardi.evcam.logic.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.foundation.shape.RoundedCornerShape


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun ProControlPanel(
    iso: Float,
    minIso: Float,
    maxIso: Float,
    isIsoAuto: Boolean,
    onIsoChange: (Float) -> Unit,
    onIsoAutoToggle: () -> Unit,
    shutterSpeed: Float,
    minShutterSpeed: Float = 100000f,
    maxShutterSpeed: Float = 1000000000f,
    isShutterAuto: Boolean,
    onShutterChange: (Float) -> Unit,
    onShutterAutoToggle: () -> Unit,
    focusDistance: Float,
    maxFocusDistance: Float = 10f,
    isFocusAuto: Boolean,
    onFocusChange: (Float) -> Unit,
    onFocusAutoToggle: () -> Unit,
    whiteBalance: Int,
    onWhiteBalanceChange: (Int) -> Unit,
    onClose: () -> Unit,
    isHdrEnabled: Boolean = false,
    isNightModeEnabled: Boolean = false,
    hasManualFocusSupport: Boolean = true,
    activeCustomScene: CustomSceneMode = CustomSceneMode.AUTO
) {

    val isSceneLocked = activeCustomScene != CustomSceneMode.AUTO || isHdrEnabled || isNightModeEnabled
    val sceneName = if (activeCustomScene != CustomSceneMode.AUTO) activeCustomScene.label else if (isHdrEnabled) "HDR" else "Night"

    val isIsoLocked = activeCustomScene.lockIso
    val isShutterLocked = activeCustomScene.lockShutter
    val isFocusLocked = activeCustomScene.lockFocus
    val isWbLocked = activeCustomScene.lockWhiteBalance

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) {
                detectTapGestures { }
            }
            .padding(16.dp)

    ) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MANUAL CONTROLS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Icon(
                imageVector = Icons.Default.Close, 
                contentDescription = "Close panel", 
                tint = Color.White,
                modifier = Modifier.size(20.dp).clickable { onClose() }
            )
        }

        val lockedParams = mutableListOf<String>()
        if (isIsoLocked) lockedParams.add("ISO")
        if (isShutterLocked) lockedParams.add("Shutter")
        if (isFocusLocked) lockedParams.add("Focus")
        if (isWbLocked) lockedParams.add("WB")
        val lockedParamsText = if (lockedParams.isNotEmpty()) lockedParams.joinToString(" & ") else "Parameters"

        // ── Info banner when any parameter is locked by scene mode ────────────────
        AnimatedVisibility(
            visible = isSceneLocked && lockedParams.isNotEmpty(),
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF9800).copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$lockedParamsText are automatically controlled by $sceneName mode",
                    color = Color(0xFFFF9800),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }

        // ── ISO row: hidden when locked by active scene ───────────────────────
        AnimatedVisibility(
            visible = !isIsoLocked,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            ProSliderRow(
                label = "ISO",
                minLabel = "${minIso.toInt()}",
                maxLabel = "${maxIso.toInt()}",
                value = iso,
                valueRange = minIso..maxIso,
                onValueChange = onIsoChange,
                isAuto = isIsoAuto,
                displayValueText = if (isIsoAuto) "AUTO" else "${iso.toInt()}",
                onAutoToggle = onIsoAutoToggle
            )
        }




        // ── Shutter row: hidden when locked by active scene ───────────────────
        AnimatedVisibility(
            visible = !isShutterLocked,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            val minLog = kotlin.math.ln(minShutterSpeed.toDouble())
            val maxLog = kotlin.math.ln(maxShutterSpeed.toDouble())
            val currentLog = kotlin.math.ln(shutterSpeed.coerceIn(minShutterSpeed, maxShutterSpeed).toDouble())
            // Inverted slider position: 0f (left) = maxShutterSpeed (30s), 1f (right) = minShutterSpeed (1/10000)
            val sliderPos = (1f - ((currentLog - minLog) / (maxLog - minLog))).toFloat().coerceIn(0f, 1f)

            val leftLabel = run {
                val sec = maxShutterSpeed / 1_000_000_000f
                if (sec >= 0.95f) "${sec.toInt()}s" else "1/${(1_000_000_000L / maxShutterSpeed.toLong().coerceAtLeast(1))}"
            }
            val rightLabel = run {
                val sec = minShutterSpeed / 1_000_000_000f
                if (sec >= 0.95f) "${sec.toInt()}s" else "1/${(1_000_000_000L / minShutterSpeed.toLong().coerceAtLeast(1))}"
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                Text("SHT", color = Color.Gray, modifier = Modifier.width(36.dp), fontSize = 12.sp)
                Text(leftLabel, color = Color.Gray.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.width(50.dp), textAlign = TextAlign.Start)
                Slider(
                    value = sliderPos,
                    onValueChange = { pos ->
                        val logVal = minLog + (1f - pos) * (maxLog - minLog)
                        val valNs = kotlin.math.exp(logVal).toFloat()
                        onShutterChange(valNs)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                )
                Text(rightLabel, color = Color.Gray.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.width(50.dp), textAlign = TextAlign.End)
                Text(
                    text = if (isShutterAuto) "AUTO" else {
                        val seconds = shutterSpeed / 1_000_000_000f
                        if (seconds >= 0.95f) {
                            String.format(Locale.US, "%.0fs", seconds)
                        } else {
                            val denominator = (1_000_000_000L / shutterSpeed.toLong().coerceAtLeast(1)).coerceAtLeast(1)
                            "1/$denominator"
                        }
                    },
                    color = if (isShutterAuto) Color.Yellow else Color.White,
                    modifier = Modifier.width(55.dp).clickable { onShutterAutoToggle() },
                    textAlign = TextAlign.End,
                    fontSize = 12.sp
                )
            }
        }





        // ── Focus row: hidden when hardware lacks manual focus or locked by scene ───────
        AnimatedVisibility(
            visible = hasManualFocusSupport && !isFocusLocked,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            ProSliderRow(
                label = "FOC",
                minLabel = "0.0",
                maxLabel = String.format(Locale.US, "%.1f", maxFocusDistance),
                value = focusDistance,
                valueRange = 0f..maxFocusDistance,
                onValueChange = onFocusChange,
                isAuto = isFocusAuto,
                displayValueText = if (isFocusAuto) "AUTO" else String.format(Locale.US, "%.1f", focusDistance),
                onAutoToggle = onFocusAutoToggle
            )
        }




        
        Spacer(modifier = Modifier.height(8.dp))
        
        // ── AWB Presets Row ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = !isWbLocked,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                Text("AWB", color = Color.Gray, modifier = Modifier.width(40.dp), fontSize = 12.sp)
                val scrollState = rememberScrollState()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                        .horizontalScroll(scrollState)
                ) {

                    val awbModes = listOf(
                        "AUTO" to CaptureRequest.CONTROL_AWB_MODE_AUTO,
                        "DAY" to CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
                        "CLD" to CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
                        "INC" to CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
                        "FLU" to CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
                    )
                    awbModes.forEach { (label, mode) ->
                        AwbChip(
                            label = label,
                            isSelected = whiteBalance == mode,
                            onClick = { onWhiteBalanceChange(mode) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProSliderRow(
    label: String,
    minLabel: String,
    maxLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    isAuto: Boolean,
    displayValueText: String,
    onAutoToggle: () -> Unit
) {

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
        Text(label, color = Color.Gray, modifier = Modifier.width(36.dp), fontSize = 12.sp)
        Text(minLabel, color = Color.Gray.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.width(50.dp), textAlign = TextAlign.Start)
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
        )
        Text(maxLabel, color = Color.Gray.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.width(50.dp), textAlign = TextAlign.End)
        Text(
            text = displayValueText,
            color = if (isAuto) Color.Yellow else Color.White,
            modifier = Modifier.width(55.dp).clickable { onAutoToggle() },
            textAlign = TextAlign.End,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun AwbChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = if (isSelected) Color.Black else Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.2f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        fontSize = 12.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
    )
}





