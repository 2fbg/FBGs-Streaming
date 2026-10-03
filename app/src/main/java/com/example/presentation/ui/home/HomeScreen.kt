package com.example.presentation.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ContentType
import com.example.data.model.PlaylistItem
import com.example.data.model.ServerProfile
import com.example.presentation.viewmodel.AppViewModel

/**
 * Tela Principal do MK21 Player com navegação de servidores, canais, filmes e séries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToPlayer: (PlaylistItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val servers by viewModel.servers.collectAsState()
    val activeServers by viewModel.activeServers.collectAsState()
    val activeServerName by viewModel.activePlaylistName.collectAsState()
    val itemsList by viewModel.activeItemsList.collectAsState()
    val categories by viewModel.activeCategories.collectAsState()
    val selectedContentType by viewModel.selectedContentType.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val useExternalPlayer by viewModel.useExternalPlayer.collectAsState()
    val preferredPlayer by viewModel.preferredExternalPlayer.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showServerSelectorDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.testTag("home_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "21",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MK21 Player",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    // Seletor do Servidor Ativo (Pill Button)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showServerSelectorDialog = true }
                            .padding(end = 8.dp)
                            .testTag("active_server_selector_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeServerName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Botão de Configurações
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configurações"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Abas de Tipo de Conteúdo (Ao Vivo, Filmes, Séries, Favoritos)
            TabRow(
                selectedTabIndex = when (selectedContentType) {
                    ContentType.LIVE -> 0
                    ContentType.MOVIE -> 1
                    ContentType.SERIES -> 2
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedContentType == ContentType.LIVE,
                    onClick = { viewModel.setContentType(ContentType.LIVE) },
                    text = { Text("Ao Vivo") },
                    icon = { Icon(Icons.Default.LiveTv, contentDescription = null) },
                    modifier = Modifier.testTag("tab_live")
                )
                Tab(
                    selected = selectedContentType == ContentType.MOVIE,
                    onClick = { viewModel.setContentType(ContentType.MOVIE) },
                    text = { Text("Filmes") },
                    icon = { Icon(Icons.Default.Movie, contentDescription = null) },
                    modifier = Modifier.testTag("tab_movies")
                )
                Tab(
                    selected = selectedContentType == ContentType.SERIES,
                    onClick = { viewModel.setContentType(ContentType.SERIES) },
                    text = { Text("Séries") },
                    icon = { Icon(Icons.Default.Tv, contentDescription = null) },
                    modifier = Modifier.testTag("tab_series")
                )
            }

            // Campo de Busca
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Buscar canais, filmes ou séries...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_field"),
                shape = RoundedCornerShape(12.dp)
            )

            // Categorias em Rolagem Horizontal
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = category == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setSelectedCategory(category) },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Indicador de Player Externo Ativo
            if (useExternalPlayer) {
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Player Externo: $preferredPlayer",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Lista / Grade de Canais ou Filmes
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (itemsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Nenhum canal encontrado para $activeServerName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Toque em Configurações para gerenciar ou carregar novos servidores.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateToSettings) {
                            Text("Ajustes & Servidores")
                        }
                    }
                }
            } else {
                val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val layoutInfo = gridState.layoutInfo
                        val total = layoutInfo.totalItemsCount
                        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        total > 0 && lastVisible >= total - 8
                    }
                }
                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) {
                        viewModel.loadMoreItems()
                    }
                }

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(itemsList, key = { it.id }) { item ->
                        ChannelCard(
                            item = item,
                            onClick = {
                                viewModel.onChannelSelected(item, onNavigateToPlayer)
                            },
                            onToggleFavorite = {
                                viewModel.toggleFavorite(item.id, !item.isFavorite)
                            }
                        )
                    }
                }
            }
        }
    }

    // Diálogo para Trocar de Servidor Ativo
    if (showServerSelectorDialog) {
        AlertDialog(
            onDismissRequest = { showServerSelectorDialog = false },
            title = {
                Text(
                    text = "Selecionar Servidor Ativo",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeServers) { srv ->
                        val isCurrent = srv.name == activeServerName
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setActivePlaylistName(srv.name)
                                    showServerSelectorDialog = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = srv.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = srv.baseUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Ativo",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerSelectorDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}

@Composable
private fun ChannelCard(
    item: PlaylistItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("channel_card_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Imagem do Logo ou Placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.logoUrl)
                            .size(140, 140)
                            .precision(coil.size.Precision.INEXACT)
                            .allowRgb565(true)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .crossfade(150)
                            .build(),
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                } else {
                    Icon(
                        imageVector = when (item.contentType) {
                            "MOVIE" -> Icons.Default.Movie
                            "SERIES" -> Icons.Default.Tv
                            else -> Icons.Default.LiveTv
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Botão de Favorito no topo
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (item.isFavorite) Color.Red else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Título e Categoria
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
