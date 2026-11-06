package com.example.presentation.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ServerProfile
import com.example.data.utils.ParsedServer
import com.example.presentation.viewmodel.AppViewModel

/**
 * Tela dedicada para manutenção em massa de servidores IPTV.
 * Permite colar texto formatado (WhatsApp), visualizar prévias com Regex,
 * ativar/desativar servidores e exclusão em massa com checkboxes de seleção múltipla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkServerManagementScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBack()
    }

    val servers by viewModel.servers.collectAsState()
    val dynamicUrl by viewModel.dynamicServersUrl.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var pastedText by remember { mutableStateOf("") }
    var parsedPreview by remember { mutableStateOf<List<ParsedServer>>(emptyList()) }
    var selectedServerIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Importar Texto, 1: Servidores Atuais, 2: Nuvem (JSON)

    Scaffold(
        modifier = modifier.testTag("bulk_server_management_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Manutenção de Servidores",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
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
            // Abas de navegação interna
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Importar Texto") },
                    icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                    modifier = Modifier.testTag("tab_import_text")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Cadastrados (${servers.size})") },
                    icon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    modifier = Modifier.testTag("tab_servers_list")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Nuvem (JSON)") },
                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                    modifier = Modifier.testTag("tab_cloud_json")
                )
            }

            when (selectedTab) {
                0 -> {
                    // Aba 0: Importar via texto colado (formato WhatsApp)
                    ImportTextSection(
                        pastedText = pastedText,
                        onTextChanged = {
                            pastedText = it
                            if (it.isBlank()) parsedPreview = emptyList()
                        },
                        parsedPreview = parsedPreview,
                        onAnalyze = {
                            parsedPreview = viewModel.previewServersFromText(pastedText)
                        },
                        onSave = {
                            viewModel.savePreviewedServers(parsedPreview)
                            parsedPreview = emptyList()
                            pastedText = ""
                            selectedTab = 1 // Vai para a lista de servidores
                        },
                        onPasteExample = {
                            pastedText = """
🟢 *Link VLOG(M3U):* http://lumixx21.org/get.php?username=demo-user&password=demo-pass&type=m3u_plus&output=mpegts
⚪ *Link LUB TV (M3U):* http://pottermax.sbs/get.php?username=demo-user&password=demo-pass&type=m3u_plus&output=mpegts
🔴 *Link CINELON21 (M3U):* http://powerex.lat/get.php?username=demo-user&password=demo-pass&type=m3u_plus&output=mpegts
                            """.trimIndent()
                            parsedPreview = viewModel.previewServersFromText(pastedText)
                        }
                    )
                }
                1 -> {
                    // Aba 1: Gerenciar servidores existentes (exclusão em massa e toggle ativo/inativo)
                    ManageServersListSection(
                        servers = servers,
                        selectedServerIds = selectedServerIds,
                        onToggleSelection = { id ->
                            selectedServerIds = if (selectedServerIds.contains(id)) {
                                selectedServerIds - id
                            } else {
                                selectedServerIds + id
                            }
                        },
                        onSelectAll = {
                            selectedServerIds = servers.map { it.id }.toSet()
                        },
                        onClearSelection = {
                            selectedServerIds = emptySet()
                        },
                        onToggleActive = { id, active ->
                            viewModel.toggleServerActive(id, active)
                        },
                        onDeleteSelected = {
                            showDeleteConfirmDialog = true
                        }
                    )
                }
                2 -> {
                    // Aba 2: Lista Dinâmica configurável via URL JSON
                    DynamicCloudSection(
                        currentUrl = dynamicUrl,
                        isLoading = isLoading,
                        onUpdateUrl = { viewModel.updateDynamicServersUrl(it) },
                        onSyncNow = { viewModel.loadDynamicServers() }
                    )
                }
            }
        }
    }

    // Diálogo de confirmação de exclusão em massa
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Excluir Servidores", fontWeight = FontWeight.Bold) },
            text = {
                Text("Deseja realmente remover os ${selectedServerIds.size} servidores selecionados?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteServers(selectedServerIds.toList())
                        selectedServerIds = emptySet()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_bulk_delete_button")
                ) {
                    Text("Excluir Definitivamente")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun ImportTextSection(
    pastedText: String,
    onTextChanged: (String) -> Unit,
    parsedPreview: List<ParsedServer>,
    onAnalyze: () -> Unit,
    onSave: () -> Unit,
    onPasteExample: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Importação Inteligente via Texto",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Cole mensagens completas recebidas pelo WhatsApp contendo links com emojis, parâmetros M3U e domínios. Nosso Regex extrairá os servidores automaticamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = pastedText,
                onValueChange = onTextChanged,
                label = { Text("Cole o texto dos servidores aqui") },
                placeholder = { Text("Ex: 🟢 *Link VLOG(M3U):* http://lumixx21.org/get.php?...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .testTag("pasted_text_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPasteExample,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("paste_example_button")
                ) {
                    Text("Exemplo WhatsApp")
                }

                Button(
                    onClick = onAnalyze,
                    enabled = pastedText.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("analyze_text_button")
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Analisar")
                }
            }
        }

        if (parsedPreview.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Servidores Detectados (${parsedPreview.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("save_parsed_servers_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvar Todos (${parsedPreview.size})")
                    }
                }
            }

            items(parsedPreview) { srv ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = srv.name.take(2).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = srv.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = srv.baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (srv.username != null) {
                                Text(
                                    text = "Credenciais detectadas: user=${srv.username}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageServersListSection(
    servers: List<ServerProfile>,
    selectedServerIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: (String) -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onDeleteSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Barra de ferramentas de seleção em massa
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = servers.isNotEmpty() && selectedServerIds.size == servers.size,
                    onCheckedChange = { isChecked ->
                        if (isChecked) onSelectAll() else onClearSelection("")
                    },
                    modifier = Modifier.testTag("select_all_checkbox")
                )
                Text(
                    text = if (selectedServerIds.isEmpty()) "Selecionar Todos" else "${selectedServerIds.size} selecionados",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedVisibility(visible = selectedServerIds.isNotEmpty()) {
                Button(
                    onClick = onDeleteSelected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("delete_selected_servers_button")
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Excluir (${selectedServerIds.size})")
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        if (servers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhum servidor cadastrado. Importe colando uma lista de texto na aba 'Importar Texto' ou baixe da nuvem.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(servers, key = { it.id }) { server ->
                    val isSelected = selectedServerIds.contains(server.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_item_${server.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSelection(server.id) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleSelection(server.id) },
                                modifier = Modifier.testTag("server_checkbox_${server.id}")
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = server.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (server.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (server.isActive) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (server.isActive) "ATIVO" else "INATIVO",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (server.isActive) Color(0xFF4CAF50) else Color.Gray
                                        )
                                    }
                                }
                                Text(
                                    text = server.baseUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Switch para ativar/desativar sem excluir
                            Switch(
                                checked = server.isActive,
                                onCheckedChange = { isChecked ->
                                    onToggleActive(server.id, isChecked)
                                },
                                modifier = Modifier.testTag("server_toggle_active_${server.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicCloudSection(
    currentUrl: String,
    isLoading: Boolean,
    onUpdateUrl: (String) -> Unit,
    onSyncNow: () -> Unit
) {
    var urlText by remember(currentUrl) { mutableStateOf(currentUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Lista Dinâmica de Servidores (ZERO ATUALIZAÇÃO)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Configure o endpoint remoto (arquivo JSON hospedado no GitHub, Pastebin ou seu próprio domínio). Novos servidores serão adicionados sem você precisar atualizar o app APK!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            label = { Text("URL do JSON Dinâmico") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dynamic_servers_url_input"),
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                if (urlText != currentUrl) {
                    IconButton(onClick = { onUpdateUrl(urlText) }) {
                        Icon(Icons.Default.Save, contentDescription = "Salvar URL")
                    }
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    onUpdateUrl(urlText)
                    onSyncNow()
                },
                enabled = !isLoading && urlText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sync_cloud_servers_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Baixando Servidores...")
                } else {
                    Icon(Icons.Default.CloudSync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sincronizar Servidores da Nuvem Agora")
                }
            }
        }
    }
}
