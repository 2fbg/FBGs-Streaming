package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.PlaylistItem
import com.example.data.parser.XmltvProgramme
import com.example.data.service.AndroidEpgManager
import com.example.data.service.XmltvLoadState
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.DarkCard
import com.example.ui.theme.GoldPremium
import com.example.ui.theme.NetflixRed
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCardBg
import com.example.ui.theme.SlateTextDark
import com.example.ui.theme.SlateTextLight
import com.example.ui.theme.SlateTextMuted
import com.example.ui.theme.SophisticatedBg
import com.example.ui.theme.SophisticatedRedStart
import com.example.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FILTER_ALL_DAYS = "__ALL__"

private sealed class ChannelEpgUiState {
    data object Loading : ChannelEpgUiState()
    data class Loaded(
        val programmes: List<XmltvProgramme>,
        val fromXmltv: Boolean
    ) : ChannelEpgUiState()
    data class Empty(val reason: String?) : ChannelEpgUiState()
}

/**
 * Modal dedicada para buscar e exibir a grade EPG completa (XMLTV multi-dia + fallback Short EPG)
 * de um canal ao vivo selecionado, reutilizando o motor XMLTV do [AndroidEpgManager].
 */
@Composable
fun ChannelFullEpgScheduleModal(
    channel: PlaylistItem,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onPlayChannel: ((PlaylistItem) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val xmltvLoadState by AndroidEpgManager.loadState.collectAsState()
    val xmltvIndex by AndroidEpgManager.xmltvIndex.collectAsState()

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedDayKey by remember(channel.url) { mutableStateOf<String?>(null) }
    var expandedProgrammeKey by remember(channel.url) { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var isFetchingChannel by remember(channel.url) { mutableStateOf(false) }
    var shortEpgFallback by remember(channel.url) { mutableStateOf<List<XmltvProgramme>>(emptyList()) }
    var hasAutoScrolled by remember(channel.url) { mutableStateOf(false) }

    val todayLabel = stringResource(R.string.channel_epg_day_today)
    val tomorrowLabel = stringResource(R.string.channel_epg_day_tomorrow)
    val yesterdayLabel = stringResource(R.string.channel_epg_day_yesterday)

    // Atualiza o relógio a cada 30s para manter barra de progresso e minutos restantes em tempo real
    LaunchedEffect(channel.url) {
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    // Busca XMLTV completo via AndroidEpgManager e reforça via Short EPG caso o canal não esteja no XMLTV
    LaunchedEffect(channel.url, refreshTrigger) {
        val forceReload = refreshTrigger > 0
        isFetchingChannel = true
        val index = viewModel.loadXmltvForActiveServerSuspend(force = forceReload)
        val tvgId = AndroidEpgManager.resolveTvgId(channel)
        val fromXmltv = index?.findProgrammes(channel.name, tvgId).orEmpty()
        if (fromXmltv.isEmpty()) {
            val fetchedShort = viewModel.fetchShortEpgForChannel(channel)
            shortEpgFallback = fetchedShort
        } else {
            shortEpgFallback = emptyList()
        }
        nowMs = System.currentTimeMillis()
        isFetchingChannel = false
    }

    // Resolve fonte de programas priorizando o índice XMLTV completo do AndroidEpgManager
    val xmltvProgrammes = remember(channel.url, xmltvIndex) {
        val tvgId = AndroidEpgManager.resolveTvgId(channel)
        xmltvIndex?.findProgrammes(channel.name, tvgId).orEmpty()
    }

    val allProgrammes = remember(channel.url, xmltvProgrammes, shortEpgFallback, nowMs) {
        when {
            xmltvProgrammes.isNotEmpty() -> xmltvProgrammes
            shortEpgFallback.isNotEmpty() -> shortEpgFallback
            else -> AndroidEpgManager.getRealProgrammes(channel, nowMs)
        }
    }

    val isFromXmltv = xmltvProgrammes.isNotEmpty()

    val uiState = remember(allProgrammes, isFromXmltv, isFetchingChannel, xmltvLoadState) {
        when {
            allProgrammes.isNotEmpty() -> ChannelEpgUiState.Loaded(allProgrammes, isFromXmltv)
            isFetchingChannel || xmltvLoadState is XmltvLoadState.Loading -> ChannelEpgUiState.Loading
            else -> ChannelEpgUiState.Empty(
                reason = (xmltvLoadState as? XmltvLoadState.Error)?.message
            )
        }
    }

    val dayGroups = remember(allProgrammes, nowMs, todayLabel, tomorrowLabel, yesterdayLabel) {
        ChannelEpgScheduleHelper.groupByDay(
            programmes = allProgrammes,
            nowMs = nowMs,
            todayLabel = todayLabel,
            tomorrowLabel = tomorrowLabel,
            yesterdayLabel = yesterdayLabel
        )
    }

    // Define o dia padrão (Hoje se disponível, senão primeiro dia ou Todos)
    LaunchedEffect(dayGroups) {
        if (dayGroups.isEmpty()) return@LaunchedEffect
        val validSelection = selectedDayKey == FILTER_ALL_DAYS ||
            dayGroups.any { it.dayKey == selectedDayKey }
        if (!validSelection) {
            selectedDayKey = dayGroups.firstOrNull { it.isToday }?.dayKey
                ?: dayGroups.first().dayKey
        }
    }

    val activeDayProgrammes = remember(allProgrammes, dayGroups, selectedDayKey) {
        if (selectedDayKey == null || selectedDayKey == FILTER_ALL_DAYS) {
            allProgrammes
        } else {
            dayGroups.firstOrNull { it.dayKey == selectedDayKey }?.programmes ?: allProgrammes
        }
    }

    val visibleProgrammes = remember(activeDayProgrammes, searchQuery) {
        ChannelEpgScheduleHelper.filterProgrammes(activeDayProgrammes, searchQuery)
    }

    val liveProgrammeIndex = remember(allProgrammes, nowMs) {
        allProgrammes.indexOfFirst { it.isLiveAt(nowMs) }
    }
    val liveProgramme = if (liveProgrammeIndex >= 0) allProgrammes[liveProgrammeIndex] else null
    val nextProgramme = remember(allProgrammes, liveProgrammeIndex, nowMs) {
        if (liveProgrammeIndex >= 0) {
            allProgrammes.getOrNull(liveProgrammeIndex + 1)
        } else {
            allProgrammes.firstOrNull { it.startMs > nowMs }
        }
    }

    // Rola suavemente até o programa "NO AR" na abertura inicial
    LaunchedEffect(visibleProgrammes, selectedDayKey, searchQuery) {
        if (!hasAutoScrolled && visibleProgrammes.isNotEmpty() && searchQuery.isBlank()) {
            val focusIdx = ChannelEpgScheduleHelper.findFocusIndex(visibleProgrammes, nowMs)
            if (focusIdx > 0) {
                listState.scrollToItem(focusIdx)
            }
            hasAutoScrolled = true
        }
    }

    SafeDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(onBack = onDismiss)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .fillMaxHeight(0.92f)
                    .testTag("channel_full_epg_modal"),
                shape = RoundedCornerShape(24.dp),
                color = SophisticatedBg,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Cabeçalho atmosférico com logo do canal, status XMLTV e ações
                    ChannelEpgModalHeader(
                        channel = channel,
                        uiState = uiState,
                        xmltvLoadState = xmltvLoadState,
                        isRefreshing = isFetchingChannel || xmltvLoadState is XmltvLoadState.Loading,
                        onRefresh = {
                            hasAutoScrolled = false
                            refreshTrigger += 1
                        },
                        onClose = onDismiss,
                        onPlayChannel = onPlayChannel
                    )

                    HorizontalDivider(color = SlateBorder.copy(alpha = 0.65f))

                    when (uiState) {
                        is ChannelEpgUiState.Loading -> {
                            ChannelEpgLoadingState(modifier = Modifier.weight(1f))
                        }
                        is ChannelEpgUiState.Empty -> {
                            ChannelEpgEmptyState(
                                title = stringResource(R.string.channel_epg_empty_title),
                                subtitle = uiState.reason
                                    ?: stringResource(R.string.channel_epg_empty_subtitle),
                                onRetry = {
                                    hasAutoScrolled = false
                                    refreshTrigger += 1
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        is ChannelEpgUiState.Loaded -> {
                            // Destaque do programa NO AR AGORA + próximo
                            if (liveProgramme != null) {
                                LiveNowHeroSpotlight(
                                    liveProgramme = liveProgramme,
                                    nextProgramme = nextProgramme,
                                    nowMs = nowMs
                                )
                            }

                            // Chips de seleção de dia + atalho "Agora"
                            if (dayGroups.size > 1 || liveProgramme != null) {
                                DaySelectorStrip(
                                    dayGroups = dayGroups,
                                    selectedDayKey = selectedDayKey,
                                    totalCount = allProgrammes.size,
                                    hasLiveNow = liveProgramme != null,
                                    onSelectDay = { dayKey ->
                                        selectedDayKey = dayKey
                                        scope.launch { listState.animateScrollToItem(0) }
                                    },
                                    onJumpToNow = {
                                        val todayKey = dayGroups.firstOrNull { it.isToday }?.dayKey
                                        if (todayKey != null) {
                                            selectedDayKey = todayKey
                                        } else {
                                            selectedDayKey = FILTER_ALL_DAYS
                                        }
                                        searchQuery = ""
                                        scope.launch {
                                            val targetList = dayGroups
                                                .firstOrNull { it.isToday }?.programmes
                                                ?: allProgrammes
                                            val idx = ChannelEpgScheduleHelper.findFocusIndex(targetList, nowMs)
                                            if (idx >= 0) {
                                                listState.animateScrollToItem(idx)
                                            }
                                        }
                                    }
                                )
                            }

                            // Barra de busca na grade do canal
                            ScheduleSearchBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it }
                            )

                            if (visibleProgrammes.isEmpty()) {
                                ChannelEpgEmptyState(
                                    title = stringResource(R.string.channel_epg_empty_filter_title),
                                    subtitle = stringResource(R.string.channel_epg_empty_filter_subtitle),
                                    onRetry = {
                                        searchQuery = ""
                                        selectedDayKey = FILTER_ALL_DAYS
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .testTag("channel_epg_programme_list"),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 8.dp,
                                        bottom = 20.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    itemsIndexed(
                                        items = visibleProgrammes,
                                        key = { index, prog -> "${prog.startMs}_${prog.stopMs}_$index" }
                                    ) { index, programme ->
                                        val itemKey = "${programme.startMs}_${programme.stopMs}_$index"
                                        val isExpanded = expandedProgrammeKey == itemKey
                                        ChannelEpgTimelineItem(
                                            programme = programme,
                                            nowMs = nowMs,
                                            isExpanded = isExpanded,
                                            onToggleExpand = {
                                                expandedProgrammeKey = if (isExpanded) null else itemKey
                                            },
                                            modifier = Modifier.testTag("channel_epg_item_$index")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelEpgModalHeader(
    channel: PlaylistItem,
    uiState: ChannelEpgUiState,
    xmltvLoadState: XmltvLoadState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    onPlayChannel: ((PlaylistItem) -> Unit)?
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SophisticatedRedStart.copy(alpha = 0.22f),
                        SlateCardBg,
                        SophisticatedBg
                    )
                )
            )
            .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo do canal com fallback nativo
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard)
                    .border(1.dp, GoldPremium.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                    .padding(5.dp),
                contentAlignment = Alignment.Center
            ) {
                val logoModel = enhancedLogoFallback(channel.logoUrl, channel.name)
                if (logoModel.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(logoModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = channel.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    NativeChannelBadge(channel.name)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = GoldPremium,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = stringResource(R.string.channel_epg_modal_title).uppercase(),
                        color = GoldPremium,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = channel.name,
                    color = SlateTextLight,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val (badgeText, badgeColor) = when {
                        isRefreshing -> stringResource(R.string.channel_epg_source_loading) to GoldPremium
                        uiState is ChannelEpgUiState.Loaded && uiState.fromXmltv -> {
                            "${stringResource(R.string.channel_epg_source_xmltv)} • " +
                                stringResource(R.string.channel_epg_programmes_count, uiState.programmes.size) to ActiveGreen
                        }
                        uiState is ChannelEpgUiState.Loaded -> {
                            "${stringResource(R.string.channel_epg_source_short)} • " +
                                stringResource(R.string.channel_epg_programmes_count, uiState.programmes.size) to GoldPremium
                        }
                        xmltvLoadState is XmltvLoadState.Error -> {
                            stringResource(R.string.channel_epg_source_unavailable) to SlateTextMuted
                        }
                        else -> stringResource(R.string.channel_epg_source_unavailable) to SlateTextMuted
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.16f))
                            .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (channel.category.isNotBlank()) {
                        Text(
                            text = channel.category,
                            color = SlateTextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("channel_epg_refresh_button")
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = GoldPremium,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.channel_epg_refresh_cd),
                        tint = SlateTextLight
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("channel_epg_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.channel_epg_close_cd),
                    tint = SlateTextLight
                )
            }
        }

        if (onPlayChannel != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { onPlayChannel(channel) },
                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(end = 6.dp)
                    .testTag("channel_epg_play_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.channel_epg_play_now),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun LiveNowHeroSpotlight(
    liveProgramme: XmltvProgramme,
    nextProgramme: XmltvProgramme?,
    nowMs: Long
) {
    val progress = liveProgramme.progressAt(nowMs)
    val remainingMin = liveProgramme.remainingMinutesAt(nowMs)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("channel_epg_now_spotlight"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            NetflixRed.copy(alpha = 0.20f),
                            DarkCard
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(NetflixRed)
                    )
                    Text(
                        text = stringResource(R.string.channel_epg_badge_now_playing),
                        color = NetflixRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "${liveProgramme.formattedRange()} • " +
                        stringResource(R.string.channel_epg_remaining_minutes, remainingMin),
                    color = GoldPremium,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = liveProgramme.title,
                color = SlateTextLight,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (liveProgramme.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = liveProgramme.description,
                    color = SlateTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = NetflixRed,
                trackColor = Color.White.copy(alpha = 0.14f)
            )

            if (nextProgramme != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.channel_epg_next_prefix),
                        color = GoldPremium,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${nextProgramme.formattedRange().substringBefore(" -")} • ${nextProgramme.title}",
                        color = SlateTextLight.copy(alpha = 0.88f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DaySelectorStrip(
    dayGroups: List<EpgDayGroup>,
    selectedDayKey: String?,
    totalCount: Int,
    hasLiveNow: Boolean,
    onSelectDay: (String) -> Unit,
    onJumpToNow: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("channel_epg_day_strip")
    ) {
        if (hasLiveNow) {
            item(key = "jump_now") {
                Box(
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NetflixRed.copy(alpha = 0.18f))
                        .border(1.dp, NetflixRed.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onJumpToNow)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("channel_epg_jump_now_chip"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NetflixRed)
                        )
                        Text(
                            text = stringResource(R.string.channel_epg_jump_now),
                            color = SlateTextLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        items(dayGroups, key = { it.dayKey }) { group ->
            val isSelected = selectedDayKey == group.dayKey
            Box(
                modifier = Modifier
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) SophisticatedRedStart else DarkCard)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color.Transparent else SlateBorder,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectDay(group.dayKey) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("channel_epg_day_chip_${group.dayKey}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${group.label} (${group.programmes.size})",
                    color = if (isSelected) Color.White else SlateTextMuted,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }

        if (dayGroups.size > 1) {
            item(key = FILTER_ALL_DAYS) {
                val isSelected = selectedDayKey == FILTER_ALL_DAYS
                Box(
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) SophisticatedRedStart else DarkCard)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color.Transparent else SlateBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectDay(FILTER_ALL_DAYS) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("channel_epg_day_chip_all"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${stringResource(R.string.channel_epg_filter_all)} ($totalCount)",
                        color = if (isSelected) Color.White else SlateTextMuted,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(44.dp)
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = SlateTextLight, fontSize = 13.sp),
            cursorBrush = SolidColor(GoldPremium),
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkCard)
                .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                .testTag("channel_epg_search_input"),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = SlateTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.channel_epg_search_placeholder),
                                color = SlateTextDark,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("channel_epg_search_clear")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.channel_epg_search_clear_cd),
                                tint = SlateTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ChannelEpgTimelineItem(
    programme: XmltvProgramme,
    nowMs: Long,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLive = programme.isLiveAt(nowMs)
    val isPast = programme.stopMs <= nowMs
    val progress = programme.progressAt(nowMs)
    val durationLabel = remember(programme.startMs, programme.stopMs) {
        ChannelEpgScheduleHelper.formatDurationMinutes(programme.startMs, programme.stopMs)
    }
    val hasDescription = programme.description.isNotBlank()

    val containerColor = when {
        isLive -> NetflixRed.copy(alpha = 0.14f)
        isPast -> SlateCardBg.copy(alpha = 0.55f)
        else -> DarkCard
    }
    val borderColor = when {
        isLive -> NetflixRed.copy(alpha = 0.75f)
        isPast -> SlateBorder.copy(alpha = 0.4f)
        else -> SlateBorder
    }

    Card(
        onClick = onToggleExpand,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isLive) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = when {
                            isLive -> GoldPremium
                            isPast -> SlateTextDark
                            else -> SlateTextMuted
                        },
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = programme.formattedRange(),
                        color = when {
                            isLive -> GoldPremium
                            isPast -> SlateTextDark
                            else -> SlateTextLight.copy(alpha = 0.85f)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (durationLabel.isNotEmpty()) {
                        Text(
                            text = "• $durationLabel",
                            color = SlateTextDark,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when {
                        isLive -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NetflixRed)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.channel_epg_badge_live),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        isPast -> {
                            Text(
                                text = stringResource(R.string.channel_epg_badge_past),
                                color = SlateTextDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        else -> {
                            Text(
                                text = stringResource(R.string.channel_epg_badge_upcoming),
                                color = GoldPremium.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (hasDescription) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = SlateTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = programme.title,
                    color = if (isPast) SlateTextMuted else SlateTextLight,
                    fontSize = 15.sp,
                    fontWeight = if (isLive) FontWeight.ExtraBold else FontWeight.Bold,
                    maxLines = if (isExpanded) 4 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (programme.category.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(GoldPremium.copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = programme.category,
                            color = GoldPremium,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            if (hasDescription) {
                AnimatedVisibility(
                    visible = isExpanded || isLive,
                    enter = fadeIn() + expandVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Text(
                        text = programme.description,
                        color = SlateTextMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = if (isExpanded) 12 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            if (isLive) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = NetflixRed,
                        trackColor = Color.White.copy(alpha = 0.14f)
                    )
                    Text(
                        text = stringResource(
                            R.string.channel_epg_remaining_minutes,
                            programme.remainingMinutesAt(nowMs)
                        ),
                        color = SlateTextLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelEpgLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("channel_epg_loading_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                color = GoldPremium,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = stringResource(R.string.channel_epg_loading_title),
                color = SlateTextLight,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.channel_epg_loading_subtitle),
                color = SlateTextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ChannelEpgEmptyState(
    title: String,
    subtitle: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp)
            .testTag("channel_epg_empty_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(DarkCard)
                    .border(1.dp, SlateBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = GoldPremium,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = title,
                color = SlateTextLight,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                color = SlateTextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("channel_epg_retry_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.channel_epg_retry),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
