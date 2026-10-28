package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ai.CopilotMessage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.GeminiAiViewModel
import com.example.ai.GeminiUiState
import com.example.data.model.PlaylistItem

@Composable
fun GeminiCopilotDialog(
    viewModel: GeminiAiViewModel = viewModel(),
    currentPlaying: PlaylistItem?,
    catalog: List<PlaylistItem>,
    relevantCatalogFetcher: (suspend (String) -> List<PlaylistItem>)? = null,
    onDismiss: () -> Unit,
    onSelectItem: ((PlaylistItem) -> Unit)? = null
) {
    val messages by viewModel.messages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll on new messages or streaming tokens
    LaunchedEffect(messages.size, (uiState as? GeminiUiState.Streaming)?.text) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

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
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFFE50914), Color(0xFFFF9800), Color(0xFF1E88E5))
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .testTag("gemini_copilot_dialog"),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0F111A)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        brush = Brush.radialGradient(
                                            listOf(Color(0xFFE50914), Color(0xFFFF6F00))
                                        ),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Gemini AI",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "MK21 AI Copilot",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Powered by Google Gemini API (gemini-3.8-flash)",
                                    fontSize = 10.sp,
                                    color = Color(0xFFFF9800),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { viewModel.clearHistory() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Limpar conversa",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.1f)
                    )

                    // Quick suggestion chips
                    // Quick suggestion chips
                    val suggestions = remember(currentPlaying) {
                        val list = mutableListOf(
                            "⚽ Onde assistir Palmeiras x Santos",
                            "⚽ Jogos de futebol de hoje",
                            "⚽ Flamengo e Brasileirão",
                            "🍿 Recomendar filmes em alta",
                            "📡 Como trocar as Fontes do canal",
                            "📺 Melhores canais 4K da lista"
                        )
                        if (currentPlaying != null) {
                            list.add(0, "ℹ️ Analisar ${currentPlaying.name}")
                        }
                        list
                    }

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(suggestions) { chipText ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1B1E2B),
                                border = BorderStroke(1.dp, Color(0xFFE50914).copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .clickable {
                                        if (chipText.startsWith("ℹ️") && currentPlaying != null) {
                                            viewModel.enrichContentSynopsis(currentPlaying)
                                        } else {
                                            viewModel.askGemini(chipText, currentPlaying, catalog, relevantCatalogFetcher)
                                        }
                                    }
                            ) {
                                Text(
                                    text = chipText,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Chat messages list
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages) { msg ->
                            val isUser = msg.sender == "user"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                if (!isUser) {
                                    Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = "Gemini",
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier
                                            .size(22.dp)
                                            .padding(top = 4.dp, end = 6.dp)
                                    )
                                }
                                Column(
                                    modifier = Modifier.widthIn(max = 300.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                color = if (isUser) Color(0xFFE50914) else Color(0xFF181B26),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isUser) Color.Transparent else Color.White.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = msg.text,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }

                                    if (!isUser && msg.suggestedItems.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "▶ Toque para assistir agora:",
                                            color = Color(0xFFFF9800),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            msg.suggestedItems.forEach { suggestedItem ->
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFF202434),
                                                    border = BorderStroke(1.dp, Color(0xFFE50914).copy(alpha = 0.5f)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            onSelectItem?.invoke(suggestedItem)
                                                            onDismiss()
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.PlayArrow,
                                                                contentDescription = "Assistir",
                                                                tint = Color(0xFFE50914),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = suggestedItem.name,
                                                                color = Color.White,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        Text(
                                                            text = suggestedItem.category,
                                                            color = Color.Gray,
                                                            fontSize = 10.sp,
                                                            maxLines = 1,
                                                            modifier = Modifier.padding(start = 6.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Streaming indicator
                        if (uiState is GeminiUiState.Streaming) {
                            val streamText = (uiState as GeminiUiState.Streaming).text
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Gerando...",
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier
                                            .size(22.dp)
                                            .padding(top = 4.dp, end = 6.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .widthIn(max = 280.dp)
                                            .background(
                                                color = Color(0xFF181B26),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = Color(0xFFFF9800).copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = streamText,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Loading spinner
                        if (uiState is GeminiUiState.Loading) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFFE50914),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Gemini está pensando...",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    // Input Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text("Pergunte ao Gemini sobre canais ou filmes...", fontSize = 12.sp, color = Color.Gray)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFE50914),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF07090F),
                                unfocusedContainerColor = Color(0xFF07090F)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gemini_prompt_input")
                        )

                        IconButton(
                            onClick = {
                                val prompt = inputText.trim()
                                if (prompt.isNotEmpty()) {
                                    viewModel.askGemini(prompt, currentPlaying, catalog, relevantCatalogFetcher)
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank() && uiState !is GeminiUiState.Loading,
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = if (inputText.isNotBlank()) Color(0xFFE50914) else Color(0xFF232733),
                                    shape = CircleShape
                                )
                                .testTag("gemini_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Enviar para Gemini",
                                tint = if (inputText.isNotBlank()) Color.White else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
