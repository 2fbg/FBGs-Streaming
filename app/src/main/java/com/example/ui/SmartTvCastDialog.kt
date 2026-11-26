package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConnectedTv
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.PlaylistItem
import com.example.data.service.DLNADevice
import com.example.data.service.LocalCastServer
import com.example.ui.theme.GoldPremium
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SmartTvCastDialog(
    playlistItem: PlaylistItem,
    playbackStatePlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    currentVolume: Float,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 0 = Smart Pair (DLNA), 1 = Navegador Web
    var activeTab by remember { mutableIntStateOf(0) }
    var castUrl by remember { mutableStateOf("") }
    var serverError by remember { mutableStateOf<String?>(null) }

    // Inicializa servidor HTTP local ANTES de ler a URL para garantir token de sessão válido
    LaunchedEffect(playlistItem.url, playlistItem.name) {
        val started = LocalCastServer.startServer(context, playlistItem)
        if (started != null) {
            castUrl = started
            serverError = null
        } else {
            castUrl = LocalCastServer.getCastUrl(context)
            serverError = "Não foi possível iniciar o servidor local na rede Wi-Fi."
        }
    }

    // Estados do Smart Pair (DLNA)
    var isScanning by remember { mutableStateOf(true) }
    var scanTrigger by remember { mutableIntStateOf(0) }
    var pairedDevice by remember { mutableStateOf(LocalCastServer.pairedDevice) }
    var connectingDeviceKey by remember { mutableStateOf<String?>(null) }
    var castErrorMessage by remember { mutableStateOf<String?>(null) }
    var useLocalProxy by remember { mutableStateOf(LocalCastServer.useLocalProxyForDlna) }
    var lastFailedDevice by remember { mutableStateOf<DLNADevice?>(null) }

    // Conexão manual por IP
    var manualTvIp by remember { mutableStateOf("") }
    var manualSearchError by remember { mutableStateOf<String?>(null) }
    var isCheckingManualIp by remember { mutableStateOf(false) }

    val discoveredDlnaList = remember { mutableStateListOf<DLNADevice>() }

    fun sendMediaToDevice(device: DLNADevice, preferProxy: Boolean) {
        val key = "${device.ipAddress}|${device.controlUrl}"
        connectingDeviceKey = key
        castErrorMessage = null
        lastFailedDevice = null
        LocalCastServer.useLocalProxyForDlna = preferProxy
        useLocalProxy = preferProxy

        val targetMediaUrl = if (preferProxy) {
            LocalCastServer.getStreamProxyUrl(context, playlistItem)
        } else {
            playlistItem.url
        }

        LocalCastServer.castUrlToDlna(
            controlUrl = device.controlUrl,
            streamUrl = targetMediaUrl,
            title = playlistItem.name,
            contentType = playlistItem.contentType
        ) { result ->
            coroutineScope.launch(Dispatchers.Main) {
                connectingDeviceKey = null
                if (result.success) {
                    LocalCastServer.pairedDevice = device
                    LocalCastServer.dlnaDeviceIp = device.ipAddress
                    pairedDevice = device
                    castErrorMessage = null
                    lastFailedDevice = null
                    Toast.makeText(
                        context,
                        "Transmitindo para ${device.friendlyName}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    lastFailedDevice = device
                    castErrorMessage = result.message
                        ?: "A TV não conseguiu abrir este formato de vídeo."
                }
            }
        }
    }

    // Descoberta SSDP real com MulticastLock (sem dispositivos falsos)
    LaunchedEffect(activeTab, scanTrigger) {
        if (activeTab == 0) {
            isScanning = true
            discoveredDlnaList.clear()
            LocalCastServer.discoverDlnaDevices(
                context = context,
                onDeviceDiscovered = { tvDevice ->
                    coroutineScope.launch(Dispatchers.Main) {
                        val exists = discoveredDlnaList.any {
                            it.ipAddress == tvDevice.ipAddress && it.controlUrl == tvDevice.controlUrl
                        }
                        if (!exists) {
                            discoveredDlnaList.add(tvDevice)
                        }
                    }
                },
                onScanComplete = {
                    coroutineScope.launch(Dispatchers.Main) {
                        isScanning = false
                    }
                }
            )
        }
    }

    val copyCastUrl: () -> Unit = {
        if (castUrl.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("MK21 Cast URL", castUrl))
            Toast.makeText(context, "Endereço copiado!", Toast.LENGTH_SHORT).show()
        }
    }

    val shareCastUrl: () -> Unit = {
        if (castUrl.isNotBlank()) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "MK21 Transmitir para TV")
                putExtra(Intent.EXTRA_TEXT, "Abra no navegador da Smart TV:\n$castUrl")
            }
            runCatching {
                context.startActivity(Intent.createChooser(sendIntent, "Enviar link para a TV"))
            }
        }
    }

    SafeDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101014)),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.94f)
                .navigationBarsPadding()
                .imePadding()
                .padding(vertical = 12.dp)
                .testTag("cast_dialog_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header fixo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(GoldPremium.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ConnectedTv,
                                contentDescription = "Transmitir para TV",
                                tint = GoldPremium,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Transmitir para TV",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = playlistItem.name,
                                color = Color(0xFF9CA3AF),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cast_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar diálogo de transmissão",
                            tint = Color(0xFF9CA3AF)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Abas (Smart Pair DLNA vs Navegador Web)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    CastTabButton(
                        label = "Smart Pair (DLNA)",
                        icon = Icons.Default.Tv,
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cast_tab_dlna")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    CastTabButton(
                        label = "Navegador Web",
                        icon = Icons.Default.Language,
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cast_tab_web")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Corpo rolável com altura segura para nunca cortar controles
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 540.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (serverError != null) {
                        CastWarningBanner(
                            title = "Aviso de rede local",
                            message = serverError!!
                        )
                    }

                    if (activeTab == 0) {
                        val activeTv = pairedDevice
                        if (activeTv != null) {
                            // PAINEL DA TV CONECTADA (real, sem textos falsos e com controles no topo)
                            ConnectedTvPanel(
                                device = activeTv,
                                playlistItem = playlistItem,
                                playbackStatePlaying = playbackStatePlaying,
                                currentVolume = currentVolume,
                                useLocalProxy = useLocalProxy,
                                isSending = connectingDeviceKey != null,
                                castUrl = castUrl,
                                onTogglePlayback = onTogglePlayback,
                                onSeek = onSeek,
                                onVolumeChange = onVolumeChange,
                                onSwitchDeliveryMode = { nextProxy ->
                                    sendMediaToDevice(activeTv, nextProxy)
                                },
                                onResend = {
                                    sendMediaToDevice(activeTv, useLocalProxy)
                                },
                                onCopyCastUrl = copyCastUrl,
                                onDisconnect = {
                                    LocalCastServer.disconnectDlna()
                                    pairedDevice = null
                                    castErrorMessage = null
                                }
                            )
                        } else {
                            // SELEÇÃO DE TV + MODO DE ENVIO + IP MANUAL
                            DeliveryModeSelector(
                                useLocalProxy = useLocalProxy,
                                onSelectProxy = {
                                    useLocalProxy = it
                                    LocalCastServer.useLocalProxyForDlna = it
                                }
                            )

                            if (castErrorMessage != null) {
                                CastErrorCard(
                                    message = castErrorMessage!!,
                                    canTryAlternateMode = lastFailedDevice != null,
                                    alternateModeLabel = if (useLocalProxy) "Tentar URL Direta" else "Tentar Proxy Local",
                                    onTryAlternateMode = {
                                        lastFailedDevice?.let { dev ->
                                            sendMediaToDevice(dev, !useLocalProxy)
                                        }
                                    },
                                    onOpenWebTab = { activeTab = 1 }
                                )
                            }

                            // Lista de Smart TVs encontradas na rede
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isScanning) {
                                        "BUSCANDO SMART TVS NA REDE..."
                                    } else if (discoveredDlnaList.isNotEmpty()) {
                                        "SMART TVS ENCONTRADAS (${discoveredDlnaList.size})"
                                    } else {
                                        "NENHUMA TV ENCONTRADA AUTOMATICAMENTE"
                                    },
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { scanTrigger++ },
                                    enabled = !isScanning,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("cast_rescan_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Rebuscar TVs",
                                        tint = if (isScanning) Color.Gray else GoldPremium,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Rebuscar",
                                        color = if (isScanning) Color.Gray else GoldPremium,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (isScanning) {
                                LinearProgressIndicator(
                                    color = GoldPremium,
                                    trackColor = Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }

                            if (discoveredDlnaList.isEmpty() && !isScanning) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Nenhuma Smart TV DLNA respondeu nesta rede.",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Confirme se a TV está ligada no mesmo Wi-Fi, digite o IP dela abaixo ou use a aba Navegador Web.",
                                            color = Color(0xFF9CA3AF),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    discoveredDlnaList.forEach { tvDevice ->
                                        val key = "${tvDevice.ipAddress}|${tvDevice.controlUrl}"
                                        val isConnecting = connectingDeviceKey == key
                                        val subtitleParts = buildList {
                                            add("IP: ${tvDevice.ipAddress}")
                                            if (tvDevice.manufacturer.isNotBlank()) {
                                                add(tvDevice.manufacturer)
                                            } else {
                                                add("UPnP AVTransport")
                                            }
                                        }
                                        Card(
                                            onClick = {
                                                if (connectingDeviceKey == null) {
                                                    sendMediaToDevice(tvDevice, useLocalProxy)
                                                }
                                            },
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF18181E)),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isConnecting) GoldPremium else GoldPremium.copy(alpha = 0.24f)
                                            ),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 60.dp)
                                                .testTag("cast_device_${tvDevice.ipAddress}")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .background(GoldPremium.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Tv,
                                                        contentDescription = null,
                                                        tint = GoldPremium,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = tvDevice.friendlyName,
                                                        color = Color.White,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = if (isConnecting) "Enviando transmissão para a TV..." else subtitleParts.joinToString(" • "),
                                                        color = if (isConnecting) GoldPremium else Color(0xFF9CA3AF),
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                if (isConnecting) {
                                                    CircularProgressIndicator(
                                                        color = GoldPremium,
                                                        strokeWidth = 2.dp,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                        contentDescription = "Conectar à TV",
                                                        tint = Color(0xFF9CA3AF),
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Conexão direta por IP manual
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "CONECTAR POR IP MANUAL",
                                        color = Color(0xFFD1D5DB),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = manualTvIp,
                                            onValueChange = {
                                                manualTvIp = it
                                                manualSearchError = null
                                            },
                                            placeholder = {
                                                Text("Ex: 192.168.3.79", color = Color.Gray, fontSize = 12.sp)
                                            },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = GoldPremium,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                cursorColor = GoldPremium
                                            ),
                                            textStyle = TextStyle(fontSize = 13.sp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("cast_manual_ip_input")
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                val ip = manualTvIp.trim()
                                                if (ip.isNotEmpty() && !isCheckingManualIp) {
                                                    isCheckingManualIp = true
                                                    manualSearchError = null
                                                    LocalCastServer.probeManualDevice(ip) { success, device ->
                                                        coroutineScope.launch(Dispatchers.Main) {
                                                            isCheckingManualIp = false
                                                            if (success && device != null) {
                                                                if (discoveredDlnaList.none { it.ipAddress == device.ipAddress && it.controlUrl == device.controlUrl }) {
                                                                    discoveredDlnaList.add(0, device)
                                                                }
                                                                sendMediaToDevice(device, useLocalProxy)
                                                            } else {
                                                                manualSearchError = "Nenhuma Smart TV DLNA encontrada em $ip. Verifique o IP ou use Navegador Web."
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = manualTvIp.isNotBlank() && !isCheckingManualIp,
                                            colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .height(52.dp)
                                                .testTag("cast_manual_connect_button")
                                        ) {
                                            if (isCheckingManualIp) {
                                                CircularProgressIndicator(
                                                    color = Color.Black,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = "Conectar",
                                                    color = Color.Black,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    if (manualSearchError != null) {
                                        Text(
                                            text = manualSearchError!!,
                                            color = Color(0xFFF87171),
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            modifier = Modifier.padding(top = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // ABA 1: ESPELHAR VIA NAVEGADOR WEB DA SMART TV
                        WebReceiverTabContent(
                            castUrl = castUrl,
                            playlistItem = playlistItem,
                            playbackStatePlaying = playbackStatePlaying,
                            currentVolume = currentVolume,
                            onCopyUrl = copyCastUrl,
                            onShareUrl = shareCastUrl,
                            onTogglePlayback = onTogglePlayback,
                            onSeek = onSeek,
                            onVolumeChange = onVolumeChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CastTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) GoldPremium else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.Black else Color(0xFF9CA3AF),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = if (selected) Color.Black else Color(0xFFD1D5DB),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DeliveryModeSelector(
    useLocalProxy: Boolean,
    onSelectProxy: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "MODO DE COMPATIBILIDADE",
            color = Color(0xFF9CA3AF),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = useLocalProxy,
                onClick = { onSelectProxy(true) },
                label = {
                    Text(
                        text = "Proxy Local (Recomendado)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GoldPremium.copy(alpha = 0.22f),
                    selectedLabelColor = GoldPremium,
                    containerColor = Color.White.copy(alpha = 0.04f),
                    labelColor = Color(0xFFD1D5DB)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = useLocalProxy,
                    borderColor = Color.White.copy(alpha = 0.12f),
                    selectedBorderColor = GoldPremium
                ),
                modifier = Modifier
                    .weight(1.3f)
                    .testTag("cast_mode_proxy")
            )
            FilterChip(
                selected = !useLocalProxy,
                onClick = { onSelectProxy(false) },
                label = {
                    Text(
                        text = "URL Direta",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GoldPremium.copy(alpha = 0.22f),
                    selectedLabelColor = GoldPremium,
                    containerColor = Color.White.copy(alpha = 0.04f),
                    labelColor = Color(0xFFD1D5DB)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = !useLocalProxy,
                    borderColor = Color.White.copy(alpha = 0.12f),
                    selectedBorderColor = GoldPremium
                ),
                modifier = Modifier
                    .weight(0.75f)
                    .testTag("cast_mode_direct")
            )
        }
    }
}

@Composable
private fun ConnectedTvPanel(
    device: DLNADevice,
    playlistItem: PlaylistItem,
    playbackStatePlaying: Boolean,
    currentVolume: Float,
    useLocalProxy: Boolean,
    isSending: Boolean,
    castUrl: String,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSwitchDeliveryMode: (Boolean) -> Unit,
    onResend: () -> Unit,
    onCopyCastUrl: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Banner de status real conectado
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF132618), RoundedCornerShape(14.dp))
                .border(BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.55f)), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CastConnected,
                contentDescription = "Conectado",
                tint = Color(0xFF4ADE80),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (useLocalProxy) "CONECTADO • PROXY LOCAL" else "CONECTADO • URL DIRETA",
                    color = Color(0xFF86EFAC),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = device.friendlyName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "IP ${device.ipAddress}",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp
                )
            }
            TextButton(
                onClick = onDisconnect,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("cast_disconnect_button")
            ) {
                Text(
                    text = "Desconectar",
                    color = Color(0xFFF87171),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Controle remoto da TV (sempre visível, sem corte!)
        RemotePlaybackDeck(
            title = playlistItem.name,
            playbackStatePlaying = playbackStatePlaying,
            currentVolume = currentVolume,
            onTogglePlayback = onTogglePlayback,
            onSeek = onSeek,
            onVolumeChange = onVolumeChange
        )

        // Ações rápidas caso a TV fique preta ou demore para abrir
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Não carregou na tela da TV?",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Algumas Smart TVs Samsung/LG exigem alternar entre Proxy Local e URL Direta conforme o canal.",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSwitchDeliveryMode(!useLocalProxy) },
                        enabled = !isSending,
                        border = BorderStroke(1.dp, GoldPremium.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("cast_switch_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = GoldPremium,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (useLocalProxy) "Usar Direta" else "Usar Proxy",
                            color = GoldPremium,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = onResend,
                        enabled = !isSending,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("cast_resend_button")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reenviar",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Link alternativo compacto para o navegador da TV
        if (castUrl.isNotBlank()) {
            CompactCastUrlBox(
                label = "Alternativa: abrir no navegador da TV",
                castUrl = castUrl,
                onCopy = onCopyCastUrl
            )
        }
    }
}

@Composable
private fun WebReceiverTabContent(
    castUrl: String,
    playlistItem: PlaylistItem,
    playbackStatePlaying: Boolean,
    currentVolume: Float,
    onCopyUrl: () -> Unit,
    onShareUrl: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Badge de servidor ativo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color(0xFF132618), RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.45f)), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF4ADE80), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SERVIDOR WEB RECEIVER ATIVO",
                color = Color(0xFF86EFAC),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
        }

        // Card de endereço com Copiar e Compartilhar
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF18181E)),
            border = BorderStroke(1.dp, GoldPremium.copy(alpha = 0.28f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Abra este endereço no navegador da sua Smart TV:",
                    color = Color(0xFFE5E7EB),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = GoldPremium,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = castUrl.ifBlank { "Iniciando servidor..." },
                        color = GoldPremium,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cast_web_url_text")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCopyUrl,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("cast_copy_url_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copiar Link",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = onShareUrl,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("cast_share_url_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Enviar",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = "O vídeo é retransmitido direto pelo celular com suporte a canais ao vivo (.ts / .m3u8) e filmes (.mp4). Mantenha o celular e a TV no mesmo Wi-Fi.",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        // Controle remoto também disponível na aba Navegador Web
        RemotePlaybackDeck(
            title = playlistItem.name,
            playbackStatePlaying = playbackStatePlaying,
            currentVolume = currentVolume,
            onTogglePlayback = onTogglePlayback,
            onSeek = onSeek,
            onVolumeChange = onVolumeChange
        )
    }
}

@Composable
private fun RemotePlaybackDeck(
    title: String,
    playbackStatePlaying: Boolean,
    currentVolume: Float,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17171D)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cast_remote_deck")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "CONTROLE REMOTO NA TV",
                color = Color(0xFF9CA3AF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = GoldPremium,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onVolumeChange((currentVolume - 0.1f).coerceIn(0f, 1f)) },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.07f), CircleShape)
                        .testTag("cast_vol_down_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                        contentDescription = "Diminuir volume da TV",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = { onSeek(-30) },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.07f), CircleShape)
                        .testTag("cast_rewind_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Voltar 30 segundos",
                        tint = Color(0xFFE5E7EB)
                    )
                }
                IconButton(
                    onClick = onTogglePlayback,
                    modifier = Modifier
                        .size(56.dp)
                        .background(GoldPremium, CircleShape)
                        .testTag("cast_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (playbackStatePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackStatePlaying) "Pausar na TV" else "Reproduzir na TV",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(
                    onClick = { onSeek(30) },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.07f), CircleShape)
                        .testTag("cast_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Avançar 30 segundos",
                        tint = Color(0xFFE5E7EB)
                    )
                }
                IconButton(
                    onClick = { onVolumeChange((currentVolume + 0.1f).coerceIn(0f, 1f)) },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.07f), CircleShape)
                        .testTag("cast_vol_up_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Aumentar volume da TV",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactCastUrlBox(
    label: String,
    castUrl: String,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF9CA3AF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = castUrl,
                color = GoldPremium,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copiar endereço",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CastErrorCard(
    message: String,
    canTryAlternateMode: Boolean,
    alternateModeLabel: String,
    onTryAlternateMode: () -> Unit,
    onOpenWebTab: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1416)),
        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cast_error_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Erro na transmissão",
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Falha ao carregar na TV",
                    color = Color(0xFFFCA5A5),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = message,
                color = Color(0xFFE5E7EB),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canTryAlternateMode) {
                    Button(
                        onClick = onTryAlternateMode,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = alternateModeLabel,
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                OutlinedButton(
                    onClick = onOpenWebTab,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "Usar Navegador",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CastWarningBanner(
    title: String,
    message: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2112)),
        border = BorderStroke(1.dp, GoldPremium.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                color = GoldPremium,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message,
                color = Color(0xFFE5E7EB),
                fontSize = 11.sp
            )
        }
    }
}
