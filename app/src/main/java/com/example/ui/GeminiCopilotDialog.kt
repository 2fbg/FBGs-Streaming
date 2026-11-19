package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.CopilotEpgChannelMatch
import com.example.ai.CopilotEpgSearchResult
import com.example.ai.GeminiAiViewModel
import com.example.ai.GeminiUiState
import com.example.data.model.PlaylistItem
import kotlinx.coroutines.launch

@Composable
fun GeminiCopilotDialog(
    viewModel: GeminiAiViewModel = viewModel(),
    currentPlaying: PlaylistItem?,
    catalog: List<PlaylistItem>,
    relevantCatalogFetcher: (suspend (String) -> List<PlaylistItem>)? = null,
    epgSearcher: (suspend (String) -> CopilotEpgSearchResult?)? = null,
    onDismiss: () -> Unit,
    onSelectItem: ((PlaylistItem) -> Unit)? = null
) {
    val messages by viewModel.messages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current

    val imeBottom = WindowInsets.ime.getBottom(density)
    val isKeyboardOpen = imeBottom > 0
    val streamingText = (uiState as? GeminiUiState.Streaming)?.text
    val isStreaming = uiState is GeminiUiState.Streaming
    val loadingState = uiState as? GeminiUiState.Loading
    val isLoading = loadingState != null

    // Fecha o Copilot no botão/gesto Voltar do Android.
    BackHandler {
        keyboard?.hide()
        onDismiss()
    }

    // Considera "no fim" quando o penúltimo ou último item está visível (ou lista ainda vazia).
    val isAtBottom by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems == 0) {
                true
            } else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= (totalItems - 2).coerceAtLeast(0)
            }
        }
    }

    // Acompanha automaticamente o scroll ao receber mensagens, iniciar streaming ou abrir teclado.
    LaunchedEffect(messages.size, streamingText, isLoading, isKeyboardOpen) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems > 0 && (isAtBottom || isKeyboardOpen)) {
            val targetIndex = totalItems - 1
            if (isStreaming) {
                listState.scrollToItem(targetIndex)
            } else {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    val sendPrompt: (String) -> Unit = { rawPrompt ->
        val trimmed = rawPrompt.trim()
        if (trimmed.isNotEmpty()) {
            keyboard?.hide()
            viewModel.askGemini(
                prompt = trimmed,
                currentPlaying = currentPlaying,
                catalog = catalog,
                relevantCatalogFetcher = relevantCatalogFetcher,
                epgSearcher = epgSearcher
            )
        }
    }

    // Overlay in-window dentro da janela principal da Activity:
    // resolve o bug de Dialog/PhoneWindow no Android 14–16 (Xiaomi/HyperOS + SwiftKey + barra 3 botões),
    // garantindo que WindowInsets.ime.union(WindowInsets.navigationBars) sempre posicione o campo acima do teclado e da barra de navegação.
    val bottomSafeInsets = WindowInsets.ime.union(WindowInsets.navigationBars)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(50f)
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    keyboard?.hide()
                    onDismiss()
                }
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(bottomSafeInsets)
            .padding(
                horizontal = if (isKeyboardOpen) 8.dp else 12.dp,
                vertical = if (isKeyboardOpen) 4.dp else 10.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFFE50914), Color(0xFFFF9800), Color(0xFF1E88E5))
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .testTag("gemini_copilot_dialog"),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0F111A)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = if (isKeyboardOpen) 10.dp else 14.dp)
            ) {
                // Header com weight(1f) no título para nunca cortar os botões Limpar e Fechar (X)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
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
                                contentDescription = "MK21 AI Copilot",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MK21 AI Copilot",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Busca de times com EPG ao vivo • Gemini",
                                fontSize = 11.sp,
                                color = Color(0xFFFF9800),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = { viewModel.clearHistory() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("gemini_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Limpar conversa",
                                tint = Color(0xFFB0B7C6),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                keyboard?.hide()
                                onDismiss()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("gemini_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar Copilot",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = if (isKeyboardOpen) 6.dp else 10.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )

                // Barra única de sugestões rápidas (recolhe quando o teclado abre para maximizar espaço útil)
                val dynamicSuggestions = remember(currentPlaying, catalog, messages.size) {
                    val lastBotMsg = messages.lastOrNull { it.sender == "gemini" }?.text.orEmpty()
                    val lastUserMsg = messages.lastOrNull { it.sender == "user" }?.text.orEmpty()
                    suggestionChipsFor(
                        text = "$lastBotMsg $lastUserMsg",
                        catalog = catalog,
                        currentPlaying = currentPlaying
                    )
                }

                AnimatedVisibility(
                    visible = !isKeyboardOpen,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(dynamicSuggestions) { chipText ->
                            val isLiveChip = chipText.startsWith("🔴")
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isLiveChip) Color(0xFF2B1216) else Color(0xFF1B1E2B),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isLiveChip) Color(0xFFE50914).copy(alpha = 0.75f)
                                    else Color(0xFFE50914).copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .clickable {
                                        if (chipText.startsWith("ℹ️") && currentPlaying != null) {
                                            keyboard?.hide()
                                            viewModel.enrichContentSynopsis(currentPlaying)
                                        } else {
                                            sendPrompt(chipText)
                                        }
                                    }
                                    .testTag("gemini_suggestion_chip")
                            ) {
                                Text(
                                    text = chipText,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                // Lista de mensagens + botão flutuante "Ir para o fim"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 6.dp),
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
                                        contentDescription = "MK21 AI",
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier
                                            .size(22.dp)
                                            .padding(top = 4.dp, end = 6.dp)
                                    )
                                }
                                Column(
                                    modifier = Modifier.widthIn(max = 320.dp)
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
                                        MarkdownLiteText(text = msg.text)
                                    }

                                    // Cartões de canais confirmados no EPG real (NO AR AGORA / EM BREVE)
                                    if (!isUser && msg.epgMatches.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val liveCount = msg.epgMatches.count { it.isLiveNow }
                                        Text(
                                            text = if (liveCount > 0) {
                                                "📡 EPG ao vivo ($liveCount no ar agora) — toque para assistir:"
                                            } else {
                                                "📡 Grade EPG encontrada — toque para sintonizar:"
                                            },
                                            color = Color(0xFFFFC470),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            msg.epgMatches.forEachIndexed { index, epgMatch ->
                                                CopilotEpgMatchCard(
                                                    match = epgMatch,
                                                    index = index,
                                                    onPlay = {
                                                        keyboard?.hide()
                                                        onSelectItem?.invoke(epgMatch.item)
                                                        onDismiss()
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Sugestões rápidas de catálogo quando não há match direto no EPG
                                    if (!isUser && msg.suggestedItems.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "▶ Canais sugeridos na sua lista:",
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
                                                        .heightIn(min = 48.dp)
                                                        .clickable {
                                                            keyboard?.hide()
                                                            onSelectItem?.invoke(suggestedItem)
                                                            onDismiss()
                                                        }
                                                        .testTag("copilot_suggested_item")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.PlayArrow,
                                                                contentDescription = "Assistir ${suggestedItem.name}",
                                                                tint = Color(0xFFE50914),
                                                                modifier = Modifier.size(18.dp)
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
                                                            color = Color(0xFF9EA6B8),
                                                            fontSize = 10.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
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

                        // Streaming indicator com cursor piscante
                        if (streamingText != null) {
                            item(key = "streaming_bubble") {
                                val cursorTransition = rememberInfiniteTransition(label = "stream_cursor")
                                val cursorAlpha by cursorTransition.animateFloat(
                                    initialValue = 0.15f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "stream_cursor_alpha"
                                )
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
                                            .widthIn(max = 300.dp)
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
                                        MarkdownLiteText(
                                            text = streamingText,
                                            trailingCursorAlpha = cursorAlpha
                                        )
                                    }
                                }
                            }
                        }

                        // Indicador de carregamento com status dinâmico ("Consultando EPG ao vivo...")
                        if (loadingState != null) {
                            item(key = "typing_indicator") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier
                                            .size(22.dp)
                                            .padding(end = 6.dp)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .background(
                                                color = Color(0xFF181B26),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = Color.White.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        TypingDotsIndicator()
                                        Text(
                                            text = loadingState.statusLabel,
                                            color = Color(0xFFFFC470),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isAtBottom,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 8.dp, end = 4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF202434),
                            border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.6f)),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .clickable {
                                    coroutineScope.launch {
                                        val totalItems = listState.layoutInfo.totalItemsCount
                                        if (totalItems > 0) {
                                            listState.animateScrollToItem(totalItems - 1)
                                        }
                                    }
                                }
                                .testTag("gemini_scroll_to_bottom")
                        ) {
                            Text(
                                text = "⤓ Ir para o fim",
                                color = Color(0xFFFFC470),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Input Row sempre ancorado acima do teclado e acima da barra de navegação
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = "Busque um time (ex: Flamengo), jogo ou canal...",
                                fontSize = 12.sp,
                                color = Color(0xFF8E96A8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = false,
                        minLines = 1,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                val prompt = inputText.trim()
                                if (prompt.isNotEmpty() && !isLoading) {
                                    inputText = ""
                                    sendPrompt(prompt)
                                }
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE50914),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFE50914),
                            focusedContainerColor = Color(0xFF07090F),
                            unfocusedContainerColor = Color(0xFF07090F)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 120.dp)
                            .testTag("gemini_prompt_input")
                    )

                    IconButton(
                        onClick = {
                            val prompt = inputText.trim()
                            if (prompt.isNotEmpty()) {
                                inputText = ""
                                sendPrompt(prompt)
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (inputText.isNotBlank() && !isLoading) Color(0xFFE50914) else Color(0xFF232733),
                                shape = CircleShape
                            )
                            .testTag("gemini_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Enviar pergunta",
                            tint = if (inputText.isNotBlank() && !isLoading) Color.White else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CopilotEpgMatchCard(
    match: CopilotEpgChannelMatch,
    index: Int,
    onPlay: () -> Unit
) {
    val accentColor = if (match.isLiveNow) Color(0xFFE50914) else Color(0xFFFF9800)
    val containerColor = if (match.isLiveNow) Color(0xFF1F1620) else Color(0xFF1A1E2B)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (match.isLiveNow) 0.7f else 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .testTag("copilot_epg_card_$index")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Linha superior: selo NO AR AGORA / EM BREVE + horário + qualidade
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.65f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (match.isLiveNow) Icons.Default.LiveTv else Icons.Default.Schedule,
                                contentDescription = if (match.isLiveNow) "No ar agora" else "Em breve",
                                tint = accentColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (match.isLiveNow) "NO AR AGORA" else "EM BREVE",
                                color = if (match.isLiveNow) Color(0xFFFF6B6B) else Color(0xFFFFC470),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Text(
                        text = match.timeRange,
                        color = Color(0xFFB8C0D2),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (match.qualityBadge != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF282E42)
                    ) {
                        Text(
                            text = match.qualityBadge,
                            color = Color(0xFFFFC470),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Título real do programa no EPG
            Text(
                text = match.programTitle,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Barra de progresso ao vivo
            if (match.isLiveNow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { match.progressPercent.coerceIn(0.02f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFE50914),
                        trackColor = Color.White.copy(alpha = 0.14f)
                    )
                    Text(
                        text = "restam ${match.remainingMinutes} min",
                        color = Color(0xFF9EA6B8),
                        fontSize = 10.sp
                    )
                }
            }

            // Canal + botão Assistir com alvo de toque mínimo 48dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = match.item.name,
                        color = Color(0xFFE2E6F0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = match.item.category,
                        color = Color(0xFF8E96A8),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("copilot_epg_play_$index")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Assistir ${match.item.name}",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Assistir",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingDotsIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing_dots")
    val offsets = listOf(0, 150, 300)
    Row(
        modifier = modifier.semantics { contentDescription = "Gerando resposta" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        offsets.forEachIndexed { index, offsetMs ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(offsetMs)
                ),
                label = "dot_alpha_$index"
            )
            val scale by transition.animateFloat(
                initialValue = 0.75f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(offsetMs)
                ),
                label = "dot_scale_$index"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                        this.scaleX = scale
                        this.scaleY = scale
                    }
                    .background(Color(0xFFFF9800), CircleShape)
            )
        }
    }
}

private val INLINE_TOKEN_REGEX = Regex("""\*\*(.+?)\*\*|(https?://[^\s<>()]+)""")

@Composable
private fun MarkdownLiteText(
    text: String,
    trailingCursorAlpha: Float? = null
) {
    val baseStyled = remember(text) {
        buildMarkdownLiteAnnotated(text)
    }
    val finalAnnotated = if (trailingCursorAlpha != null) {
        remember(baseStyled, trailingCursorAlpha) {
            buildAnnotatedString {
                append(baseStyled)
                withStyle(
                    SpanStyle(
                        color = Color(0xFFFF9800).copy(alpha = trailingCursorAlpha),
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append("▍")
                }
            }
        }
    } else {
        baseStyled
    }

    Text(
        text = finalAnnotated,
        color = Color.White,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
}

private fun buildMarkdownLiteAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.split("\n")
    lines.forEachIndexed { index, line ->
        val trimmedStart = line.trimStart()
        val isBullet = trimmedStart.startsWith("- ") || trimmedStart.startsWith("• ")
        if (isBullet) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                appendInlineMarkdown(line)
            }
        } else {
            appendInlineMarkdown(line)
        }
        if (index < lines.lastIndex) {
            append("\n")
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(line: String) {
    var cursor = 0
    for (match in INLINE_TOKEN_REGEX.findAll(line)) {
        if (match.range.first > cursor) {
            append(line.substring(cursor, match.range.first))
        }
        val boldContent = match.groups[1]?.value
        val urlContent = match.groups[2]?.value
        if (boldContent != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFFC470))) {
                append(boldContent)
            }
        } else if (urlContent != null) {
            val cleanUrl = urlContent.trimEnd('.', ',', ';', ':', '!', '?')
            val trailing = urlContent.substring(cleanUrl.length)
            withStyle(
                SpanStyle(
                    color = Color(0xFF64B5F6),
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(cleanUrl)
            }
            if (trailing.isNotEmpty()) {
                append(trailing)
            }
        } else {
            append(match.value)
        }
        cursor = match.range.last + 1
    }
    if (cursor < line.length) {
        append(line.substring(cursor))
    }
}

internal fun suggestionChipsFor(
    text: String,
    catalog: List<PlaylistItem>,
    currentPlaying: PlaylistItem? = null
): List<String> {
    val list = mutableListOf<String>()

    // 1. Atalho principal de EPG ao vivo (sempre primeiro)
    list.add("🔴 Jogos passando agora no EPG")

    // 2. Contexto do item em reprodução atual
    if (currentPlaying != null) {
        val cleanName = currentPlaying.name.take(20)
        list.add("ℹ️ Analisar $cleanName")
    }

    // 3. Atalhos rápidos de times populares no EPG
    list.add("⚽ Flamengo agora")
    list.add("⚽ Palmeiras agora")
    list.add("⚽ Corinthians agora")
    list.add("⚽ São Paulo agora")

    // 4. Contexto dinâmico baseado no texto da conversa
    val contextText = text.lowercase()
    if (contextText.contains("filme") ||
        contextText.contains("cinema") ||
        contextText.contains("série") ||
        contextText.contains("serie") ||
        contextText.contains("sinopse")
    ) {
        list.add("🍿 Top 5 filmes da lista")
        list.add("🎬 Lançamentos de ação")
    } else if (contextText.contains("fonte") ||
        contextText.contains("servidor") ||
        contextText.contains("buffer") ||
        contextText.contains("trava")
    ) {
        list.add("⚡ Como trocar de fonte?")
        list.add("🔧 Evitar travamentos")
    }

    // 5. Canais de esportes ativos extraídos do catálogo
    val sportsChannels = catalog.filter { item ->
        val n = item.name.uppercase()
        n.contains("PREMIERE") || n.contains("SPORTV") || n.contains("ESPN") ||
            n.contains("TNT") || n.contains("CAZÉ") || n.contains("BANDSPORTS")
    }.take(2)
    for (ch in sportsChannels) {
        val nameShort = ch.name.trim().take(18)
        if (list.size < 9 && list.none { it.contains(nameShort) }) {
            list.add("📺 EPG no $nameShort")
        }
    }

    return list.distinct().take(8)
}
