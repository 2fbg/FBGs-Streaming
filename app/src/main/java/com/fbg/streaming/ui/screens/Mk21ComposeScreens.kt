package com.fbg.streaming.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fbg.streaming.features.ParentalControlManager
import com.fbg.streaming.features.TvQrPairServer
import com.fbg.streaming.features.VoiceSearchManager
import com.fbg.streaming.update.Mk21UpdateManager
import com.fbg.streaming.update.UpdateInfo
import kotlinx.coroutines.launch
import java.security.MessageDigest

// Modelo de Partida/Evento Esportivo para a Central de Jogos (MatchSchedule)
data class SportMatchItem(
   val id: String,
   val championship: String,
   val homeTeam: String,
   val awayTeam: String,
   val startTime: String,
   val isLive: Boolean,
   val channelName: String,
   val streamUrl: String
)

/**
* 1. Central de Jogos e Agenda Esportiva (Inspirada na MatchScheduleActivity do uv_4.20.1.apk)
* Suporta navegação por Controle Remoto (D-Pad Focus) na Android TV e toque no Celular.
*/
@Composable
fun MatchScheduleScreen(
   matches: List<SportMatchItem>,
   onChannelSelected: (SportMatchItem) -> Unit,
   modifier: Modifier = Modifier
) {
   val leagues = remember(matches) {
       listOf("Todos", "Ao Vivo Agora") + matches.map { it.championship }.distinct()
   }
   var selectedFilter by remember { mutableStateOf("Todos") }

   val filteredMatches = remember(matches, selectedFilter) {
       when (selectedFilter) {
           "Todos" -> matches
           "Ao Vivo Agora" -> matches.filter { it.isLive }
           else -> matches.filter { it.championship == selectedFilter }
       }
   }

   Column(
       modifier = modifier
           .fillMaxSize()
           .background(Color(0xFF0B0F19))
           .padding(20.dp)
   ) {
       Row(
           modifier = Modifier.fillMaxWidth(),
           horizontalArrangement = Arrangement.SpaceBetween,
           verticalAlignment = Alignment.CenterVertically
       ) {
           Column {
               Text(
                   text = "Central de Jogos & Eventos Ao Vivo",
                   color = Color.White,
                   fontSize = 24.sp,
                   fontWeight = FontWeight.Bold
               )
               Text(
                   text = "Selecione a partida para abrir diretamente o canal da transmissão",
                   color = Color(0xFF94A3B8),
                   fontSize = 14.sp
               )
           }
       }

       Spacer(modifier = Modifier.height(16.dp))

       // Filtros de Campeonato com suporte a foco D-Pad
       LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
           items(leagues) { league ->
               var isFocused by remember { mutableStateOf(false) }
               val isSelected = selectedFilter == league
               val bgColor by animateColorAsState(
                   targetValue = when {
                       isFocused -> Color(0xFF3B82F6)
                       isSelected -> Color(0xFF1D4ED8)
                       else -> Color(0xFF1E293B)
                   },
                   label = "chipBg"
               )

               Surface(
                   color = bgColor,
                   shape = RoundedCornerShape(50),
                   border = if (isFocused) BorderStroke(2.dp, Color.White) else null,
                   modifier = Modifier
                       .onFocusChanged { isFocused = it.isFocused }
                       .focusable()
                       .clickable { selectedFilter = league }
               ) {
                   Text(
                       text = league,
                       color = Color.White,
                       fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                       modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                   )
               }
           }
       }

       Spacer(modifier = Modifier.height(20.dp))

       LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
           items(filteredMatches, key = { it.id }) { match ->
               MatchCardItem(match = match, onClick = { onChannelSelected(match) })
           }
       }
   }
}

@Composable
private fun MatchCardItem(
   match: SportMatchItem,
   onClick: () -> Unit
) {
   var isFocused by remember { mutableStateOf(false) }
   val borderColor = if (isFocused) Color(0xFF38BDF8) else Color(0xFF1E293B)
   val containerColor = if (isFocused) Color(0xFF1E293B) else Color(0xFF111827)

   Surface(
       color = containerColor,
       shape = RoundedCornerShape(14.dp),
       border = BorderStroke(if (isFocused) 2.5.dp else 1.dp, borderColor),
       modifier = Modifier
           .fillMaxWidth()
           .onFocusChanged { isFocused = it.isFocused }
           .focusable()
           .clickable { onClick() }
   ) {
       Row(
           modifier = Modifier
               .fillMaxWidth()
               .padding(horizontal = 20.dp, vertical = 16.dp),
           verticalAlignment = Alignment.CenterVertically,
           horizontalArrangement = Arrangement.SpaceBetween
       ) {
           Column(modifier = Modifier.weight(1f)) {
               Row(verticalAlignment = Alignment.CenterVertically) {
                   if (match.isLive) {
                       Box(
                           modifier = Modifier
                               .size(8.dp)
                               .clip(CircleShape)
                               .background(Color(0xFFEF4444))
                       )
                       Spacer(modifier = Modifier.width(6.dp))
                       Text(
                           text = "AO VIVO",
                           color = Color(0xFFEF4444),
                           fontSize = 12.sp,
                           fontWeight = FontWeight.ExtraBold
                       )
                       Spacer(modifier = Modifier.width(10.dp))
                   }
                   Text(
                       text = "${match.championship} • ${match.startTime}",
                       color = Color(0xFF94A3B8),
                       fontSize = 13.sp
                   )
               }

               Spacer(modifier = Modifier.height(6.dp))

               Text(
                   text = "${match.homeTeam}  vs  ${match.awayTeam}",
                   color = Color.White,
                   fontSize = 18.sp,
                   fontWeight = FontWeight.Bold,
                   maxLines = 1,
                   overflow = TextOverflow.Ellipsis
               )
           }

           Surface(
               color = Color(0xFF2563EB),
               shape = RoundedCornerShape(8.dp)
           ) {
               Text(
                   text = "Assistir em ${match.channelName}",
                   color = Color.White,
                   fontSize = 13.sp,
                   fontWeight = FontWeight.SemiBold,
                   modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
               )
           }
       }
   }
}

/**
* 2. Diálogo de Pareamento via QR Code na Smart TV / TV Box (Inspirado na DisplayQRCodeActivity)
* Sobe o TvQrPairServer automaticamente e exibe o endereço local para enviar listas pelo celular.
*/
@Composable
fun TvQrSetupDialog(
   onDismiss: () -> Unit,
   onPlaylistReceived: (name: String, url: String, user: String?, pass: String?) -> Unit
) {
   var pairingUrl by remember { mutableStateOf("Iniciando servidor local...") }
   var receivedSuccess by remember { mutableStateOf(false) }

   DisposableEffect(Unit) {
       val server = TvQrPairServer { name, url, user, pass ->
           onPlaylistReceived(name, url, user, pass)
           receivedSuccess = true
       }
       pairingUrl = server.startPairingServer()
       onDispose { server.stop() }
   }

   Dialog(onDismissRequest = onDismiss) {
       Surface(
           color = Color(0xFF0F172A),
           shape = RoundedCornerShape(20.dp),
           border = BorderStroke(1.dp, Color(0xFF334155)),
           modifier = Modifier.fillMaxWidth().padding(16.dp)
       ) {
           Column(
               modifier = Modifier.padding(24.dp),
               horizontalAlignment = Alignment.CenterHorizontally
           ) {
               Text(
                   text = "Pareamento Rápido via Celular",
                   color = Color.White,
                   fontSize = 20.sp,
                   fontWeight = FontWeight.Bold
               )
               Spacer(modifier = Modifier.height(8.dp))
               Text(
                   text = "Conecte seu celular no mesmo Wi-Fi da TV e acesse o endereço abaixo (ou escaneie o código):",
                   color = Color(0xFF94A3B8),
                   fontSize = 14.sp
               )
               Spacer(modifier = Modifier.height(20.dp))

               // Matriz visual representativa + URL direta em destaque para digitação rápida ou leitura
               QrMatrixPlaceholder(data = pairingUrl, modifier = Modifier.size(170.dp))

               Spacer(modifier = Modifier.height(16.dp))
               Surface(
                   color = Color(0xFF1E293B),
                   shape = RoundedCornerShape(10.dp)
               ) {
                   Text(
                       text = pairingUrl,
                       color = Color(0xFF38BDF8),
                       fontSize = 18.sp,
                       fontWeight = FontWeight.Bold,
                       modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                   )
               }

               if (receivedSuccess) {
                   Spacer(modifier = Modifier.height(12.dp))
                   Text(
                       text = "Lista recebida com sucesso! Carregando canais...",
                       color = Color(0xFF4ADE80),
                       fontWeight = FontWeight.Bold
                   )
               }

               Spacer(modifier = Modifier.height(20.dp))
               Button(onClick = onDismiss) {
                   Text("Fechar")
               }
           }
       }
   }
}

@Composable
private fun QrMatrixPlaceholder(data: String, modifier: Modifier = Modifier) {
   val hash = remember(data) {
       MessageDigest.getInstance("SHA-256").digest(data.toByteArray())
   }
   Canvas(
       modifier = modifier
           .clip(RoundedCornerShape(12.dp))
           .background(Color.White)
           .padding(12.dp)
   ) {
       val gridSize = 15
       val cellSize = size.width / gridSize
       for (row in 0 until gridSize) {
           for (col in 0 until gridSize) {
               val isFinderCorner = (row < 4 && col < 4) ||
                   (row < 4 && col >= gridSize - 4) ||
                   (row >= gridSize - 4 && col < 4)
               val bitIndex = (row * gridSize + col) % (hash.size * 8)
               val byteVal = hash[bitIndex / 8].toInt()
               val isDark = isFinderCorner || ((byteVal shr (bitIndex % 8)) and 1) == 1
               if (isDark) {
                   drawRect(
                       color = Color.Black,
                       topLeft = Offset(col * cellSize, row * cellSize),
                       size = Size(cellSize, cellSize)
                   )
               }
           }
       }
   }
}

/**
* 3. Diálogo de Busca por Voz (Inspirado na LiveVoiceSearchActivity)
*/
@Composable
fun VoiceSearchDialog(
   voiceSearchManager: VoiceSearchManager,
   onQueryConfirmed: (String) -> Unit,
   onDismiss: () -> Unit
) {
   val isListening by voiceSearchManager.isListening.collectAsState()
   val recognizedText by voiceSearchManager.queryFlow.collectAsState()

   LaunchedEffect(Unit) {
       voiceSearchManager.startVoiceSearch()
   }

   Dialog(onDismissRequest = {
       voiceSearchManager.release()
       onDismiss()
   }) {
       Surface(
           color = Color(0xFF0F172A),
           shape = RoundedCornerShape(18.dp),
           border = BorderStroke(1.dp, Color(0xFF3B82F6))
       ) {
           Column(
               modifier = Modifier.padding(24.dp),
               horizontalAlignment = Alignment.CenterHorizontally
           ) {
               Text(
                   text = if (isListening) "Ouvindo... Fale o nome do canal ou filme" else "Processando voz",
                   color = Color.White,
                   fontSize = 18.sp,
                   fontWeight = FontWeight.Bold
               )
               Spacer(modifier = Modifier.height(16.dp))
               Text(
                   text = recognizedText.ifBlank { "Ex: Premiere, ESPN, Série..." },
                   color = Color(0xFF38BDF8),
                   fontSize = 20.sp,
                   fontWeight = FontWeight.SemiBold
               )
               Spacer(modifier = Modifier.height(20.dp))
               Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                   OutlinedButton(onClick = { voiceSearchManager.startVoiceSearch() }) {
                       Text("Falar Novamente")
                   }
                   Button(
                       enabled = recognizedText.isNotBlank(),
                       onClick = {
                           voiceSearchManager.release()
                           onQueryConfirmed(recognizedText)
                       }
                   ) {
                       Text("Buscar Agora")
                   }
               }
           }
       }
   }
}

/**
* 4. Diálogo de Desbloqueio Parental por PIN (Inspirado na RestrictLevelSearchActivity)
*/
@Composable
fun ParentalPinDialog(
   parentalManager: ParentalControlManager,
   onUnlocked: () -> Unit,
   onDismiss: () -> Unit
) {
   var pinInput by remember { mutableStateOf("") }
   var errorMsg by remember { mutableStateOf<String?>(null) }
   val isConfiguringNewPin = remember { !parentalManager.isPinConfigured() }

   Dialog(onDismissRequest = onDismiss) {
       Surface(
           color = Color(0xFF0F172A),
           shape = RoundedCornerShape(16.dp),
           border = BorderStroke(1.dp, Color(0xFF334155))
       ) {
           Column(
               modifier = Modifier.padding(24.dp),
               horizontalAlignment = Alignment.CenterHorizontally
           ) {
               Text(
                   text = if (isConfiguringNewPin) "Definir PIN de Controle Parental" else "Conteúdo Protegido por PIN",
                   color = Color.White,
                   fontSize = 18.sp,
                   fontWeight = FontWeight.Bold
               )
               Spacer(modifier = Modifier.height(12.dp))
               OutlinedTextField(
                   value = pinInput,
                   onValueChange = { if (it.length <= 6) pinInput = it },
                   label = { Text("Digite o PIN de 4 a 6 dígitos") },
                   visualTransformation = PasswordVisualTransformation(),
                   keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                   singleLine = true
               )
               if (errorMsg != null) {
                   Spacer(modifier = Modifier.height(8.dp))
                   Text(text = errorMsg!!, color = Color(0xFFEF4444), fontSize = 13.sp)
               }
               Spacer(modifier = Modifier.height(16.dp))
               Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                   TextButton(onClick = onDismiss) { Text("Cancelar") }
                   Button(onClick = {
                       if (pinInput.length < 4) {
                           errorMsg = "O PIN deve ter pelo menos 4 dígitos."
                           return@Button
                       }
                       if (isConfiguringNewPin) {
                           parentalManager.setPin(pinInput)
                           onUnlocked()
                       } else if (parentalManager.verifyPin(pinInput)) {
                           onUnlocked()
                       } else {
                           errorMsg = "PIN incorreto. Tente novamente."
                       }
                   }) {
                       Text(if (isConfiguringNewPin) "Salvar PIN" else "Desbloquear")
                   }
               }
           }
       }
   }
}

/**
* 5. Diálogo de Atualização OTA Nativa via GitHub (Mk21UpdateManager)
*/
@Composable
fun OtaUpdateDialog(
   updateInfo: UpdateInfo,
   updateManager: Mk21UpdateManager,
   onDismiss: () -> Unit
) {
   val scope = rememberCoroutineScope()
   var isDownloading by remember { mutableStateOf(false) }
   var progress by remember { mutableIntStateOf(0) }
   var errorText by remember { mutableStateOf<String?>(null) }

   Dialog(onDismissRequest = { if (!isDownloading) onDismiss() }) {
       Surface(
           color = Color(0xFF0F172A),
           shape = RoundedCornerShape(18.dp),
           border = BorderStroke(1.dp, Color(0xFF2563EB))
       ) {
           Column(modifier = Modifier.padding(24.dp)) {
               Text(
                   text = "Nova Atualização Disponível (v${updateInfo.versionName})",
                   color = Color.White,
                   fontSize = 19.sp,
                   fontWeight = FontWeight.Bold
               )
               Spacer(modifier = Modifier.height(10.dp))
               Text(
                   text = updateInfo.changelog,
                   color = Color(0xFFCBD5E1),
                   fontSize = 14.sp
               )
               Spacer(modifier = Modifier.height(18.dp))

               if (isDownloading) {
                   LinearProgressIndicator(
                       progress = { progress / 100f },
                       modifier = Modifier.fillMaxWidth()
                   )
                   Spacer(modifier = Modifier.height(8.dp))
                   Text(
                       text = "Baixando e verificando assinatura SHA-256... $progress%",
                       color = Color(0xFF38BDF8),
                       fontSize = 13.sp
                   )
               }

               if (errorText != null) {
                   Spacer(modifier = Modifier.height(8.dp))
                   Text(text = errorText!!, color = Color(0xFFEF4444), fontSize = 13.sp)
               }

               Spacer(modifier = Modifier.height(18.dp))
               Row(
                   modifier = Modifier.fillMaxWidth(),
                   horizontalArrangement = Arrangement.End
               ) {
                   if (!isDownloading) {
                       TextButton(onClick = onDismiss) { Text("Depois") }
                       Spacer(modifier = Modifier.width(8.dp))
                       Button(onClick = {
                           isDownloading = true
                           errorText = null
                           scope.launch {
                               val ok = updateManager.downloadAndInstallApk(updateInfo) { p ->
                                   progress = p
                               }
                               if (!ok) {
                                   isDownloading = false
                                   errorText = "Falha ao baixar ou validar o pacote APK."
                               }
                           }
                       }) {
                           Text("Atualizar Agora")
                       }
                   }
               }
           }
       }
   }
}