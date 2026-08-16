package net.supardi.evcam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.supardi.evcam.logic.*


@Composable
fun WatermarkSettingsDialog(
    showWatermark: Boolean,
    onShowWatermarkChange: (Boolean) -> Unit,
    watermarkElements: List<WatermarkElement>,
    onWatermarkElementsChange: (List<WatermarkElement>) -> Unit,
    liveLocation: android.location.Location?,
    liveAddress: android.location.Address?,
    onDismiss: () -> Unit
) {
    var showAddMenu by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.wrapContentHeight()) {
                Text("Advanced Watermark", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Watermark", color = Color.White)
                    Switch(checked = showWatermark, onCheckedChange = onShowWatermarkChange)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (showWatermark) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(watermarkElements.size) { index ->
                            val element = watermarkElements[index]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(element.type.name, color = Color.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        // Edit content if it is a text element
                                        if (element.type == WatermarkElementType.TEXT) {
                                            var textVal by remember(element.content) { mutableStateOf(element.content) }
                                            BasicTextField(
                                                value = textVal,
                                                onValueChange = { newVal ->
                                                    textVal = newVal
                                                    val newList = watermarkElements.toMutableList()
                                                    newList[index] = element.copy(content = newVal)
                                                    onWatermarkElementsChange(newList)
                                                },
                                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                    .padding(8.dp)
                                            )
                                        } else {
                                            // Show format selector dropdown for Location / Date elements
                                            var formatExpanded by remember { mutableStateOf(false) }
                                            Box(
                                                modifier = Modifier
                                                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                    .clickable { formatExpanded = true }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Text(element.content, color = Color.White, fontSize = 12.sp)
                                                androidx.compose.material3.DropdownMenu(
                                                    expanded = formatExpanded,
                                                    onDismissRequest = { formatExpanded = false }
                                                ) {
                                                    val options = if (element.type == WatermarkElementType.DATE) {
                                                        listOf("yyyy/MM/dd HH:mm", "yyyy-MM-dd", "dd MMM yyyy", "HH:mm")
                                                    } else {
                                                        listOf("CITY", "CITY_COUNTRY", "FULL_ADDRESS", "DECIMAL_DEGREES", "DMS")
                                                    }
                                                    options.forEach { opt ->
                                                        androidx.compose.material3.DropdownMenuItem(
                                                            text = { Text(opt) },
                                                            onClick = {
                                                                val newList = watermarkElements.toMutableList()
                                                                newList[index] = element.copy(content = opt)
                                                                onWatermarkElementsChange(newList)
                                                                formatExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Size: ${element.size}", color = Color.Gray, fontSize = 10.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(modifier = Modifier.clickable { 
                                                val newList = watermarkElements.toMutableList()
                                                newList[index] = element.copy(size = (element.size - 1).coerceAtLeast(8))
                                                onWatermarkElementsChange(newList)
                                            }.background(Color.DarkGray, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) { Text("-", color = Color.White, fontSize = 12.sp) }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(modifier = Modifier.clickable { 
                                                val newList = watermarkElements.toMutableList()
                                                newList[index] = element.copy(size = (element.size + 1).coerceAtMost(48))
                                                onWatermarkElementsChange(newList)
                                            }.background(Color.DarkGray, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) { Text("+", color = Color.White, fontSize = 12.sp) }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // Quadrant selector
                                    var expanded by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.background(Color(0xFF333333), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp).clickable { expanded = true }) {
                                        Text(element.quadrant.name.replace("_", " "), color = Color.White, fontSize = 10.sp)
                                        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                            WatermarkQuadrant.values().forEach { q ->
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text(q.name) },
                                                    onClick = {
                                                        val newList = watermarkElements.toMutableList()
                                                        newList[index] = element.copy(quadrant = q)
                                                        onWatermarkElementsChange(newList)
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.material3.IconButton(
                                        onClick = {
                                            val newList = watermarkElements.toMutableList()
                                            newList.removeAt(index)
                                            onWatermarkElementsChange(newList)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            
                        }
                        
                        item {
                            Box {
                                Button(
                                    onClick = { showAddMenu = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                ) {
                                    Text("+ Add Element", color = Color.White)
                                }
                                androidx.compose.material3.DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                                    WatermarkElementType.values().forEach { type ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(type.name) },
                                            onClick = {
                                                val newList = watermarkElements.toMutableList()
                                                val content = if (type == WatermarkElementType.TEXT) "New Text" else type.name
                                                newList.add(WatermarkElement(java.util.UUID.randomUUID().toString(), type, content, WatermarkQuadrant.BOTTOM_LEFT))
                                                onWatermarkElementsChange(newList)
                                                showAddMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) {
                    Text("Done", color = Color.White)
                }
            }
        }
    }
}
