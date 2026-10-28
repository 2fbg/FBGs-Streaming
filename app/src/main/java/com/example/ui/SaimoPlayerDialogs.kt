package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Theme colors matching the Saimo TV player bottom sheets
private val SaimoDarkBackground = Color(0xFF0F1221)
private val SaimoCardBackground = Color(0xFF14172B)
private val SaimoDividerColor = Color(0xFF1F2440)
private val SaimoAccentPurple = Color(0xFF6366F1)
private val SaimoPillHandle = Color(0xFF4B5175)

/**
 * Bottom Sheet Modal for selecting IPTV playback source ("Escolha a fonte").
 * Matches Saimo TV UI with Fonte 1, Fonte 2, Fonte 3 radio selectors.
 */
@Composable
fun SaimoSourcesDialog(
    sources: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(SaimoDarkBackground)
                    .padding(vertical = 16.dp)
            ) {
                // Drag handle pill
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(SaimoPillHandle)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Escolha a fonte",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = SaimoDividerColor, thickness = 1.dp)

                // List of sources
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    itemsIndexed(sources) { index, source ->
                        val isSelected = index == selectedIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectIndex(index)
                                    onDismiss()
                                }
                                .padding(horizontal = 24.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Custom Radio Button
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSelectIndex(index)
                                    onDismiss()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SaimoAccentPurple,
                                    unselectedColor = Color.Gray.copy(alpha = 0.6f)
                                )
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = source.first,
                                color = if (isSelected) Color.White else Color(0xFFD1D5DB),
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        if (index < sources.size - 1) {
                            HorizontalDivider(
                                color = SaimoDividerColor,
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bottom Sheet Modal for selecting resolution and super resolution filters
 * ("Qualidade e Super Resolução").
 * Matches Saimo TV UI options:
 * - Qualidade: Automática
 * - Qualidade: 1080p
 * - Super Resolução: Desligado
 * - Super Resolução: Nitidez HD (720p → FHD)
 * - Super Resolução: Ultra (FHD → 4K)
 */
@Composable
fun SaimoSuperResolutionDialog(
    currentQuality: String,
    onSelectQuality: (String) -> Unit,
    currentSuperResMode: String, // "off", "hd", "4k"
    onSelectSuperRes: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "quality_auto" to "Qualidade: Automática",
        "quality_1080p" to "Qualidade: 1080p",
        "res_off" to "Super Resolução: Desligado",
        "res_hd" to "Super Resolução: Nitidez HD (720p → FHD)",
        "res_4k" to "Super Resolução: Ultra (FHD → 4K)"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(SaimoDarkBackground)
                    .padding(vertical = 16.dp)
            ) {
                // Drag handle pill
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(SaimoPillHandle)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Qualidade e Super Resolução",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = SaimoDividerColor, thickness = 1.dp)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    itemsIndexed(options) { index, option ->
                        val (key, label) = option
                        val isSelected = when (key) {
                            "quality_auto" -> currentQuality == "auto"
                            "quality_1080p" -> currentQuality == "1080p"
                            "res_off" -> currentSuperResMode == "off"
                            "res_hd" -> currentSuperResMode == "hd"
                            "res_4k" -> currentSuperResMode == "4k"
                            else -> false
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (key) {
                                        "quality_auto" -> onSelectQuality("auto")
                                        "quality_1080p" -> onSelectQuality("1080p")
                                        "res_off" -> onSelectSuperRes("off")
                                        "res_hd" -> onSelectSuperRes("hd")
                                        "res_4k" -> onSelectSuperRes("4k")
                                    }
                                    onDismiss()
                                }
                                .padding(horizontal = 24.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    when (key) {
                                        "quality_auto" -> onSelectQuality("auto")
                                        "quality_1080p" -> onSelectQuality("1080p")
                                        "res_off" -> onSelectSuperRes("off")
                                        "res_hd" -> onSelectSuperRes("hd")
                                        "res_4k" -> onSelectSuperRes("4k")
                                    }
                                    onDismiss()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SaimoAccentPurple,
                                    unselectedColor = Color.Gray.copy(alpha = 0.6f)
                                )
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFFD1D5DB),
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        if (index < options.size - 1) {
                            HorizontalDivider(
                                color = SaimoDividerColor,
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
