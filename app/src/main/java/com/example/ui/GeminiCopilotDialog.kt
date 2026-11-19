package com.example.ui

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.viewmodel.compose.viewModel
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
    onDismiss: () -> Unit,
    onSelectItem: ((PlaylistItem) -> Unit)? = null
) {
    val messages by viewModel.messages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val streamingText = (uiState as? GeminiUiState.Streaming)?.text
    val isStreaming = uiState is GeminiUiState.Streaming
    val isLoading = uiState is GeminiUiState.Loading

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

    // Só acompanha automaticamente o scroll se o usuário já estiver no fim da conversa.
    LaunchedEffect(messages.size, streamingText, isLoading, imeBottom) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems > 0 && isAtBottom) {
            val targetIndex = totalItems - 1
            if (isStreaming) {
                // scrollToItem sem animação evita engasgos durante streaming token a token.
                listState.scrollToItem(targetIndex)
            } else {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    SafeDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        // Necessário dentro do Dialog para obter a Window própria do diálogo (DialogWindowProvider).
        val currentView = LocalView.current
        val dialogWindow = (currentView.parent as? DialogWindowProvider
            ?: currentView as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            @Suppress("DEPRECATION")
            dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            onDispose {
                dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .windowInsetsPadding(WindowInsets.statusBars)
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
                        .imePadding()
                        .navigationBarsPadding()
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
                                    text = "Powered by Google Gemini API (gemini-2.5-flash)",
                                    fontSize = 10.sp,
                                    color = Color(0xFFFF9800),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { viewModel.clearHistory() },
                                modifier = Modifier.size(48.dp)
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
                                modifier = Modifier.size(48.dp)
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

                    // Sugestões dinâmicas no topo baseadas no canal atual, catálogo e contexto
                    val dynamicSuggestions = remember(currentPlaying, catalog, messages.size) {
                        val lastBotMsg = messages.lastOrNull { it.sender == "gemini" }?.text.orEmpty()
                        val lastUserMsg = messages.lastOrNull { it.sender == "user" }?.text.orEmpty()
                        suggestionChipsFor(
                            text = "$lastBotMsg $lastUserMsg",
                            catalog = catalog,
                            currentPlaying = currentPlaying
                        )
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
                                        keyboard?.hide()
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

                    val lastMessage = messages.lastOrNull()
                    val showFollowUpChips = lastMessage != null &&
                        lastMessage.sender != "user" &&
                        !isStreaming &&
                        !isLoading
                    val followUpSuggestions = remember(lastMessage?.text, catalog) {
                        if (lastMessage != null) {
                            suggestionChipsFor(lastMessage.text, catalog).take(3)
                        } else {
                            emptyList()
                        }
                    }

                    // Chat messages list + botão flutuante "Ir para o fim"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
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
                                            MarkdownLiteText(text = msg.text)
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

                            // Sugestões de continuação após o último balão do bot
                            if (showFollowUpChips && followUpSuggestions.isNotEmpty()) {
                                item(key = "follow_up_chips") {
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 28.dp, top = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(followUpSuggestions) { chipText ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color(0xFF151924),
                                                border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.45f)),
                                                modifier = Modifier
                                                    .clickable {
                                                        keyboard?.hide()
                                                        viewModel.askGemini(chipText, currentPlaying, catalog, relevantCatalogFetcher)
                                                    }
                                            ) {
                                                Text(
                                                    text = chipText,
                                                    color = Color(0xFFFFC470),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
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
                                            MarkdownLiteText(
                                                text = streamingText,
                                                trailingCursorAlpha = cursorAlpha
                                            )
                                        }
                                    }
                                }
                            }

                            // Indicador de digitação animado (três pontos pulsantes)
                            if (isLoading) {
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
                                        Box(
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
                                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                        ) {
                                            TypingDotsIndicator()
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

                    // Input Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text("Pergunte ao Gemini sobre canais ou filmes...", fontSize = 12.sp, color = Color.Gray)
                            },
                            singleLine = false,
                            minLines = 1,
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    val prompt = inputText.trim()
                                    if (prompt.isNotEmpty()) {
                                        keyboard?.hide()
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
                                .heightIn(min = 48.dp, max = 132.dp)
                                .testTag("gemini_prompt_input")
                        )

                        IconButton(
                            onClick = {
                                val prompt = inputText.trim()
                                if (prompt.isNotEmpty()) {
                                    keyboard?.hide()
                                    viewModel.askGemini(prompt, currentPlaying, catalog, relevantCatalogFetcher)
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank() && !isLoading,
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

    // 1. Contexto do item em reprodução atual (quando fornecido no topo)
    if (currentPlaying != null) {
        val cleanName = currentPlaying.name.take(22)
        list.add("ℹ️ Analisar $cleanName")
        list.add("🔄 Canais parecidos com $cleanName")
    }

    // 2. Contexto dinâmico baseado no texto da conversa / resposta
    val contextText = text.lowercase()
    if (contextText.contains("futebol") ||
        contextText.contains("jogo") ||
        contextText.contains("partida") ||
        contextText.contains("campeonato") ||
        contextText.contains("onde assistir")
    ) {
        list.add("⚽ Próximos jogos do Brasileirão")
        list.add("⚽ Qual canal passa futebol agora?")
        list.add("⚽ Jogos na Libertadores e Champions")
    } else if (contextText.contains("filme") ||
        contextText.contains("cinema") ||
        contextText.contains("série") ||
        contextText.contains("serie") ||
        contextText.contains("sinopse") ||
        contextText.contains("recomendar")
    ) {
        list.add("🍿 Top 5 filmes mais bem avaliados")
        list.add("🎬 Melhores lançamentos de ação")
        list.add("🍿 Séries populares para maratonar")
    } else if (contextText.contains("fonte") ||
        contextText.contains("servidor") ||
        contextText.contains("buffer") ||
        contextText.contains("qualidade") ||
        contextText.contains("trava")
    ) {
        list.add("⚡ Como trocar para o servidor mais rápido?")
        list.add("📡 Qual a diferença entre as 7 Fontes?")
        list.add("🔧 Dicas para evitar travamentos")
    }

    // 3. Canais de esportes ativos extraídos do catálogo
    val sportsChannels = catalog.filter { item ->
        val n = item.name.uppercase()
        n.contains("PREMIERE") || n.contains("SPORTV") || n.contains("ESPN") ||
            n.contains("TNT") || n.contains("CAZÉ") || n.contains("BANDSPORTS")
    }.take(3)
    for (ch in sportsChannels) {
        val nameShort = ch.name.trim().take(18)
        if (list.size < 8 && list.none { it.contains(nameShort) }) {
            list.add("⚽ Jogos no $nameShort")
        }
    }

    // 4. Canais de filmes ativos extraídos do catálogo
    val movieChannels = catalog.filter { item ->
        val n = item.name.uppercase()
        n.contains("TELECINE") || n.contains("HBO") || n.contains("MEGAPIX") ||
            n.contains("PARAMOUNT") || n.contains("MAX")
    }.take(2)
    for (ch in movieChannels) {
        val nameShort = ch.name.trim().take(18)
        if (list.size < 8 && list.none { it.contains(nameShort) }) {
            list.add("🍿 Filmes no $nameShort")
        }
    }

    // 5. Sugestões padrão de apoio
    if (list.size < 6) {
        list.add("⚽ Jogos de futebol de hoje")
        list.add("🍿 Recomendar filmes em alta")
        list.add("📺 Melhores canais 4K da lista")
        list.add("📡 Status dos servidores MK21")
    }

    return list.distinct().take(10)
}
