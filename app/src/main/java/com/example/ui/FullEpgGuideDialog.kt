package com.example.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.PlaylistItem
import com.example.data.parser.XmltvProgramme
import com.example.data.service.AndroidEpgManager
import com.example.data.service.XmltvLoadState
import com.example.ui.theme.GoldPremium
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.SophisticatedRedStart
import com.example.viewmodel.AppViewModel
import java.text.Normalizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")

private fun normalizeGuideQuery(raw: String): String {
    if (raw.isBlank()) return ""
    val nfd = Normalizer.normalize(raw.trim().lowercase(), Normalizer.Form.NFD)
    return DIACRITICS_REGEX.replace(nfd, "")
}

/**
 * Guia de Programação completo (EPG XMLTV + Short EPG) estilo Smart TV / Saimo TV.
 * - Landscape / Tablet / Android TV: layout canônico 2 colunas (canais à esquerda + grade à direita).
 * - Portrait (celular): lista de canais com prévia "NO AR" e painel da grade ao selecionar o canal.
 */
@Composable
fun FullEpgGuideDialog(
    viewModel: AppViewModel,
    liveChannels: List<PlaylistItem>,
    onDismiss: () -> Unit,
    onPlayChannel: (PlaylistItem) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 720
    val scope = rememberCoroutineScope()

    val xmltvState by AndroidEpgManager.loadState.collectAsState()
    val xmltvIndex by AndroidEpgManager.xmltvIndex.collectAsState()

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todas") }
    var selectedChannel by remember(liveChannels) { mutableStateOf(liveChannels.firstOrNull()) }
    var showPortraitScheduleDetail by remember { mutableStateOf(false) }
    var shortEpgOverride by remember { mutableStateOf<Pair<String, List<XmltvProgramme>>?>(null) }
    var loadingChannelShortEpg by remember { mutableStateOf(false) }

    // Atualiza relógio de progresso a cada 30 segundos enquanto o guia estiver aberto
    LaunchedEffect(Unit) {
        viewModel.loadXmltvForActiveServer(force = false)
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val categories = remember(liveChannels) {
        val unique = liveChannels.map { it.category }.distinct().sorted()
        listOf("Todas") + unique
    }

    val filteredChannels = remember(liveChannels, selectedCategory, searchQuery, xmltvIndex, nowMs, shortEpgOverride) {
        val q = normalizeGuideQuery(searchQuery)
        liveChannels.filter { ch ->
            val catOk = selectedCategory == "Todas" || ch.category == selectedCategory
            if (!catOk) return@filter false
            if (q.isEmpty()) return@filter true
            if (normalizeGuideQuery(ch.name).contains(q) || normalizeGuideQuery(ch.category).contains(q)) {
                return@filter true
            }
            // Busca "No Ar Agora" (título e sinopse do programa atual no XMLTV/Short EPG/público)
            val (nowProg, _) = AndroidEpgManager.getNowAndNext(ch, nowMs)
            if (nowProg != null) {
                normalizeGuideQuery(nowProg.title).contains(q) ||
                    normalizeGuideQuery(nowProg.description).contains(q)
            } else {
                false
            }
        }
    }

    // Garante que o canal selecionado sempre pertença à lista filtrada no modo 2 colunas
    LaunchedEffect(filteredChannels) {
        if (filteredChannels.isNotEmpty() && filteredChannels.none { it.url == selectedChannel?.url }) {
            selectedChannel = filteredChannels.first()
        }
    }

    // Reforça via get_short_epg se o canal selecionado não tiver grade no XMLTV
    LaunchedEffect(selectedChannel?.url, xmltvIndex) {
        val ch = selectedChannel ?: return@LaunchedEffect
        val existing = AndroidEpgManager.getRealProgrammes(ch, nowMs)
        if (existing.isEmpty()) {
            loadingChannelShortEpg = true
            val fetched = viewModel.fetchShortEpgForChannel(ch)
            if (fetched.isNotEmpty()) {
                shortEpgOverride = ch.url to fetched
            }
            loadingChannelShortEpg = false
        } else {
            loadingChannelShortEpg = false
        }
    }

    SafeDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler {
            if (!isWideLayout && showPortraitScheduleDetail) {
                showPortraitScheduleDetail = false
            } else {
                onDismiss()
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07070A))
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("full_epg_guide_dialog"),
            color = Color(0xFF07070A)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header superior
                EpgGuideHeader(
                    xmltvState = xmltvState,
                    channelTotal = filteredChannels.size,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onRefresh = {
                        scope.launch {
                            viewModel.loadXmltvForActiveServer(force = true)
                            nowMs = System.currentTimeMillis()
                        }
                    },
                    onBack = {
                        if (!isWideLayout && showPortraitScheduleDetail) {
                            showPortraitScheduleDetail = false
                        } else {
                            onDismiss()
                        }
                    },
                    showingPortraitDetail = !isWideLayout && showPortraitScheduleDetail
                )

                // Chips horizontais de categoria
                if (!(!isWideLayout && showPortraitScheduleDetail) && categories.size > 2) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories.take(40)) { cat ->
                            val active = cat == selectedCategory
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) SophisticatedRedStart else Color(0xFF14141A))
                                    .border(
                                        1.dp,
                                        if (active) Color.Transparent else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (active) Color.White else Color(0xFFB8BCC8),
                                    fontSize = 12.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                if (filteredChannels.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = "Nenhum canal encontrado",
                                tint = Color.Gray,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Nenhum canal ao vivo encontrado para o filtro atual.",
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else if (isWideLayout) {
                    // Layout 2 colunas (Landscape / Tablet / TV)
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Coluna 1: Lista de canais (42%)
                        LazyColumn(
                            modifier = Modifier
                                .weight(0.42f)
                                .fillMaxHeight()
                                .background(Color(0xFF0A0A0F))
                                .testTag("epg_channels_column"),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                items = filteredChannels,
                                key = { idx, item -> "${item.id}_${item.url}_$idx" }
                            ) { idx, channel ->
                                val isSelected = channel.url == selectedChannel?.url
                                val (nowProg, _) = remember(channel.url, xmltvIndex, nowMs, shortEpgOverride) {
                                    AndroidEpgManager.getNowAndNext(channel, nowMs)
                                }
                                EpgChannelRowCard(
                                    lcn = idx + 1,
                                    channel = channel,
                                    nowProgramme = nowProg,
                                    nowMs = nowMs,
                                    isSelected = isSelected,
                                    onClick = { selectedChannel = channel },
                                    onPlay = { onPlayChannel(channel) }
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.08f))
                        )

                        // Coluna 2: Grade do canal selecionado (58%)
                        val activeCh = selectedChannel ?: filteredChannels.first()
                        val programmes = remember(activeCh.url, xmltvIndex, nowMs, shortEpgOverride) {
                            val fromManager = AndroidEpgManager.getRealProgrammes(activeCh, nowMs)
                            if (fromManager.isNotEmpty()) {
                                fromManager
                            } else if (shortEpgOverride?.first == activeCh.url) {
                                shortEpgOverride?.second.orEmpty()
                            } else {
                                emptyList()
                            }
                        }
                        EpgScheduleColumn(
                            modifier = Modifier
                                .weight(0.58f)
                                .fillMaxHeight(),
                            channel = activeCh,
                            programmes = programmes,
                            nowMs = nowMs,
                            isLoadingShortEpg = loadingChannelShortEpg,
                            onPlay = { onPlayChannel(activeCh) }
                        )
                    }
                } else {
                    // Layout Portrait: lista de canais ou grade detalhada do canal selecionado
                    if (!showPortraitScheduleDetail) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("epg_channels_column"),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                items = filteredChannels,
                                key = { idx, item -> "${item.id}_${item.url}_$idx" }
                            ) { idx, channel ->
                                val (nowProg, _) = remember(channel.url, xmltvIndex, nowMs, shortEpgOverride) {
                                    AndroidEpgManager.getNowAndNext(channel, nowMs)
                                }
                                EpgChannelRowCard(
                                    lcn = idx + 1,
                                    channel = channel,
                                    nowProgramme = nowProg,
                                    nowMs = nowMs,
                                    isSelected = channel.url == selectedChannel?.url,
                                    onClick = {
                                        selectedChannel = channel
                                        showPortraitScheduleDetail = true
                                    },
                                    onPlay = { onPlayChannel(channel) }
                                )
                            }
                        }
                    } else {
                        val activeCh = selectedChannel ?: filteredChannels.first()
                        val programmes = remember(activeCh.url, xmltvIndex, nowMs, shortEpgOverride) {
                            val fromManager = AndroidEpgManager.getRealProgrammes(activeCh, nowMs)
                            if (fromManager.isNotEmpty()) {
                                fromManager
                            } else if (shortEpgOverride?.first == activeCh.url) {
                                shortEpgOverride?.second.orEmpty()
                            } else {
                                emptyList()
                            }
                        }
                        EpgScheduleColumn(
                            modifier = Modifier.fillMaxSize(),
                            channel = activeCh,
                            programmes = programmes,
                            nowMs = nowMs,
                            isLoadingShortEpg = loadingChannelShortEpg,
                            onPlay = { onPlayChannel(activeCh) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgGuideHeader(
    xmltvState: XmltvLoadState,
    channelTotal: Int,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    showingPortraitDetail: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF14141B), Color(0xFF0B0B10))
                )
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("epg_guide_close_button")
            ) {
                Icon(
                    imageVector = if (showingPortraitDetail) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                    contentDescription = if (showingPortraitDetail) "Voltar à lista de canais" else "Fechar Guia de Programação",
                    tint = Color.White
                )
            }

            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = GoldPremium,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Guia de Programação (EPG)",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusLabel = when (xmltvState) {
                    is XmltvLoadState.Loading -> "Baixando grade XMLTV do servidor..."
                    is XmltvLoadState.Ready -> "XMLTV Real • ${xmltvState.channelCount} canais indexados • $channelTotal listados"
                    is XmltvLoadState.Error -> "${xmltvState.message} (usando Short EPG por canal)"
                    is XmltvLoadState.Idle -> "$channelTotal canais ao vivo"
                }
                Text(
                    text = statusLabel,
                    color = when (xmltvState) {
                        is XmltvLoadState.Ready -> Color(0xFF4ADE80)
                        is XmltvLoadState.Loading -> GoldPremium
                        else -> Color(0xFF9EA3B0)
                    },
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("epg_guide_refresh_button")
            ) {
                if (xmltvState is XmltvLoadState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = GoldPremium,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Atualizar XMLTV",
                        tint = Color.White
                    )
                }
            }
        }

        if (!showingPortraitDetail) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 4.dp)
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF060609), RoundedCornerShape(10.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .testTag("epg_guide_search_input"),
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    cursorBrush = SolidColor(GoldPremium),
                    decorationBox = { inner ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar canal no guia",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Buscar canal ou programa no ar agora...",
                                        color = Color.Gray,
                                        fontSize = 13.sp
                                    )
                                }
                                inner()
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchChange("") },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpar filtro",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EpgChannelRowCard(
    lcn: Int,
    channel: PlaylistItem,
    nowProgramme: XmltvProgramme?,
    nowMs: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1C1618) else Color(0xFF111116)
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) SophisticatedRedStart else Color.White.copy(alpha = 0.06f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Número LCN
            Text(
                text = lcn.toString().padStart(2, '0'),
                color = if (isSelected) GoldPremium else Color(0xFF7B8090),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.width(30.dp)
            )

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(enhancedLogoFallback(channel.logoUrl, channel.name))
                    .crossfade(true)
                    .build(),
                contentDescription = channel.name,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1B1B22)),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (nowProgramme != null) {
                    Text(
                        text = "NO AR: ${nowProgramme.title}",
                        color = Color(0xFFD7DBE6),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { nowProgramme.progressAt(nowMs) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = NetflixRed,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                } else {
                    Text(
                        text = channel.category,
                        color = Color(0xFF8B90A0),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onPlay,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(NetflixRed.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Assistir ${channel.name}",
                        tint = NetflixRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgScheduleColumn(
    modifier: Modifier = Modifier,
    channel: PlaylistItem,
    programmes: List<XmltvProgramme>,
    nowMs: Long,
    isLoadingShortEpg: Boolean,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    // Se não houver EPG real, exibe grade estimada sinalizada com transparência (sem inventar que é real)
    val fallbackSchedule = remember(channel.name, programmes) {
        if (programmes.isEmpty()) getChannelEpgSchedule(channel.name) else emptyList()
    }

    Column(
        modifier = modifier
            .background(Color(0xFF07070A))
            .padding(16.dp)
            .testTag("epg_schedule_column")
    ) {
        // Cabeçalho do canal selecionado + botão Assistir
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(enhancedLogoFallback(channel.logoUrl, channel.name))
                    .crossfade(true)
                    .build(),
                contentDescription = channel.name,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF15151C)),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (programmes.isNotEmpty()) {
                        "${channel.category} • EPG Real (${programmes.size} programas)"
                    } else {
                        "${channel.category} • Programação estimada (sem XMLTV para este canal)"
                    },
                    color = if (programmes.isNotEmpty()) Color(0xFF4ADE80) else Color(0xFF9EA3B0),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.testTag("epg_play_selected_channel_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Assistir",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(modifier = Modifier.height(10.dp))

        if (isLoadingShortEpg && programmes.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = GoldPremium,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Consultando EPG do canal no servidor...",
                    color = GoldPremium,
                    fontSize = 12.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (programmes.isNotEmpty()) {
                items(programmes) { prog ->
                    val isLive = prog.isLiveAt(nowMs)
                    val progress = prog.progressAt(nowMs)
                    val remMin = if (isLive) prog.remainingMinutesAt(nowMs) else 0
                    EpgProgrammeCard(
                        title = prog.title,
                        timeRange = prog.formattedRange(),
                        description = prog.description,
                        isLive = isLive,
                        progress = progress,
                        remainingLabel = if (isLive) "restam ${remMin} min" else null,
                        isReal = true
                    )
                }
            } else {
                items(fallbackSchedule) { prog ->
                    EpgProgrammeCard(
                        title = prog.title,
                        timeRange = prog.timeRange,
                        description = prog.description,
                        isLive = prog.isCurrent,
                        progress = prog.progressPercent,
                        remainingLabel = null,
                        isReal = prog.isReal
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgProgrammeCard(
    title: String,
    timeRange: String,
    description: String,
    isLive: Boolean,
    progress: Float,
    remainingLabel: String?,
    isReal: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isLive) Color(0xFF1A1214) else Color(0xFF111116)
        ),
        border = BorderStroke(
            width = if (isLive) 1.5.dp else 1.dp,
            color = if (isLive) NetflixRed else Color.White.copy(alpha = 0.06f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (isLive) GoldPremium else Color(0xFF8B90A0),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = timeRange,
                        color = if (isLive) GoldPremium else Color(0xFF9EA3B0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (remainingLabel != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• $remainingLabel",
                            color = Color(0xFFD7DBE6),
                            fontSize = 11.sp
                        )
                    }
                }

                if (isLive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NetflixRed)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "NO AR",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                } else if (!isReal) {
                    Text(
                        text = "Estimado",
                        color = Color(0xFF7B8090),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = Color(0xFFB5BAC6),
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isLive) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = NetflixRed,
                    trackColor = Color.White.copy(alpha = 0.14f)
                )
            }
        }
    }
}
