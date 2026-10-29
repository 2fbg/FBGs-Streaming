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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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

    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    // Auto-scroll on new messages, streaming tokens, or keyboard popping up
    LaunchedEffect(messages.size, (uiState as? GeminiUiState.Streaming)?.text, imeBottom, inputText) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
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

                    // Dynamic suggestion chips based on currently playing, active catalog, and conversation context
                    val dynamicSuggestions = remember(currentPlaying, catalog, messages.size) {
                        val list = mutableListOf<String>()

                        // 1. Current playing context
                        if (currentPlaying != null) {
                            val cleanName = currentPlaying.name.take(22)
                            list.add("ℹ️ Analisar $cleanName")
                            list.add("🔄 Canais parecidos com $cleanName")
                        }

                        // 2. Dynamic conversation context
                        val lastBotMsg = messages.lastOrNull { it.sender == "gemini" }?.text?.lowercase() ?: ""
                        val lastUserMsg = messages.lastOrNull { it.sender == "user" }?.text?.lowercase() ?: ""
                        val contextText = "$lastBotMsg $lastUserMsg"

                        if (contextText.contains("futebol") || contextText.contains("jogo") || contextText.contains("partida") || contextText.contains("onde assistir")) {
                            list.add("⚽ Próximos jogos do Brasileirão")
                            list.add("⚽ Qual canal passa futebol agora?")
                            list.add("⚽ Jogos na Libertadores e Champions")
                        } else if (contextText.contains("filme") || contextText.contains("cinema") || contextText.contains("série") || contextText.contains("recomendar")) {
                            list.add("🍿 Top 5 filmes mais bem avaliados")
                            list.add("🎬 Melhores lançamentos de ação")
                            list.add("🍿 Séries populares para maratonar")
                        } else if (contextText.contains("fonte") || contextText.contains("servidor") || contextText.contains("trava")) {
                            list.add("⚡ Como trocar para o servidor mais rápido?")
                            list.add("📡 Qual a diferença entre as 7 Fontes?")
                            list.add("🔧 Dicas para evitar travamentos")
                        }

                        // 3. Dynamic sports channels extracted from active catalog
                        val sportsChannels = catalog.filter { item ->
                            val n = item.name.uppercase()
                            n.contains("PREMIERE") || n.contains("SPORTV") || n.contains("ESPN") || n.contains("TNT") || n.contains("CAZÉ") || n.contains("BANDSPORTS")
                        }.take(3)
                        for (ch in sportsChannels) {
                            val nameShort = ch.name.trim().take(18)
                            if (list.size < 8 && !list.any { it.contains(nameShort) }) {
                                list.add("⚽ Jogos no $nameShort")
                            }
                        }

                        // 4. Dynamic movie channels extracted from active catalog
                        val movieChannels = catalog.filter { item ->
                            val n = item.name.uppercase()
                            n.contains("TELECINE") || n.contains("HBO") || n.contains("MEGAPIX") || n.contains("PARAMOUNT") || n.contains("MAX")
                        }.take(2)
                        for (ch in movieChannels) {
                            val nameShort = ch.name.trim().take(18)
                            if (list.size < 8 && !list.any { it.contains(nameShort) }) {
                                list.add("🍿 Filmes no $nameShort")
                            }
                        }

                        // 5. Default rotating dynamic fallbacks
                        if (list.size < 6) {
                            list.add("⚽ Jogos de futebol de hoje")
                            list.add("🍿 Recomendar filmes em alta")
                            list.add("📺 Melhores canais 4K da lista")
                            list.add("📡 Status dos servidores MK21")
                        }

                        list.distinct().take(10)
                    }

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(dynamicSuggestions) { chipText ->
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
                            singleLine = false,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    val prompt = inputText.trim()
                                    if (prompt.isNotEmpty()) {
                                        viewModel.askGemini(prompt, currentPlaying, catalog, relevantCatalogFetcher)
                                        inputText = ""
                                    }
                                }
                            ),
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
