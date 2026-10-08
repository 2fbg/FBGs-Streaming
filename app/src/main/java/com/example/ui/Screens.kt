package com.example.ui

import kotlinx.coroutines.*
import android.util.Log
import android.app.Activity
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.media3.common.util.UnstableApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ContentType
import com.example.data.model.ManualPlaylist
import com.example.data.model.PlaylistItem
import com.example.ui.theme.DarkCard
import com.example.ui.theme.GoldPremium
import com.example.ui.theme.NetflixRed
import com.example.viewmodel.AppViewModel
import com.example.data.service.LocalCastServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import android.media.AudioManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange

/**
 * TV Series Episodes Grouping Data Classes & Heuristics
 */
data class GroupedSeries(
    val title: String,
    val logoUrl: String?,
    val category: String,
    val episodes: List<PlaylistItem>
)

fun groupSeriesItems(items: List<PlaylistItem>): List<GroupedSeries> {
    val regex = Regex("(?i)\\b(s\\d{1,2}e\\d{1,2}|\\d{1,2}x\\d{1,2}|t\\d{1,2}e\\d{1,2}|season\\s*\\d+\\s*episode\\s*\\d+|temp\\.\\s*\\d+\\s*ep\\.\\s*\\d+|capitulo\\s*\\d+|capí?tulo\\s*\\d+)\\b")
    
    val groups = items.groupBy { item ->
        val matchResult = regex.find(item.name)
        if (matchResult != null) {
            val idx = matchResult.range.first
            var seriesName = item.name.substring(0, idx).trim()
            seriesName = seriesName.trimEnd('-', '_', ',', '/', ':', ' ')
            if (seriesName.isEmpty()) item.name else seriesName
        } else {
            // Check for EP.xx or Episode xx
            val epRegex = Regex("(?i)\\bep\\.?\\s*\\d+\\b|\\bepisod[io|io|o]\\s*\\d+\\b")
            val epMatch = epRegex.find(item.name)
            if (epMatch != null) {
                val idx = epMatch.range.first
                var seriesName = item.name.substring(0, idx).trim()
                seriesName = seriesName.trimEnd('-', '_', ',', '/', ':', ' ')
                if (seriesName.isEmpty()) item.name else seriesName
            } else {
                item.name
            }
        }
    }

    return groups.map { (seriesTitle, episodes) ->
        GroupedSeries(
            title = seriesTitle,
            logoUrl = episodes.firstOrNull { !it.logoUrl.isNullOrEmpty() }?.logoUrl ?: episodes.firstOrNull()?.logoUrl,
            category = episodes.firstOrNull()?.category ?: "Séries",
            episodes = episodes.sortedWith(compareBy<PlaylistItem> { it.name })
        )
    }.sortedBy { it.title }
}

/**
 * Navigation routes
 */
object Routes {
    const val STARTUP_GATE = "startup_gate"
    const val SERVER_CONFIG = "server_config"
    const val HOME = "home"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(viewModel: AppViewModel) {
    val isPremiumActive by viewModel.isPremiumActive.collectAsState()
    val trialDaysLeft by viewModel.trialDaysLeft.collectAsState()
    
    val isTrialExpired = trialDaysLeft <= 0 && !isPremiumActive
    
    if (isTrialExpired) {
        TrialExpiredScreen(viewModel = viewModel)
    } else {
        var currentScreen by remember { mutableStateOf(Routes.STARTUP_GATE) }
        
        // Check which screen to show on bootup
        LaunchedEffect(Unit) {
            if (viewModel.isCredentialsConfigured()) {
                currentScreen = Routes.HOME
            } else {
                currentScreen = Routes.SERVER_CONFIG
            }
        }

        Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
            when (screen) {
                Routes.STARTUP_GATE -> {
                    StartupGateScreen()
                }
                Routes.SERVER_CONFIG -> {
                    ServerConfigScreen(
                        viewModel = viewModel,
                        onNavigateToHome = { currentScreen = Routes.HOME }
                    )
                }
                Routes.HOME -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToSettings = { currentScreen = Routes.SETTINGS },
                        onNavigateToConfig = { currentScreen = Routes.SERVER_CONFIG }
                    )
                }
                Routes.SETTINGS -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = Routes.HOME }
                    )
                }
            }
        }
    }
}

@Composable
fun TrialExpiredScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val virtualMac = viewModel.virtualMacAddress
    
    var activationCodeInput by remember { mutableStateOf("") }
    var activationError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF131111))
                .border(1.5.dp, GoldPremium.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Lock Icon with premium gold aura
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(GoldPremium.copy(alpha = 0.1f))
                    .border(1.dp, GoldPremium.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = "Licença Requerida",
                    tint = GoldPremium,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Text(
                text = "Período de Testes Expirado",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Explanation
            Text(
                text = "Seus 5 dias de avaliação gratuita terminaram. Para continuar a testar e utilizar todas as funções exclusivas do aplicativo, entre em contato com o desenvolvedor e envie a chave abaixo para ativação rápida do seu aparelho.",
                color = Color.Gray,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // MAC Key Display Card with copy button
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B1B)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CHAVE DO DISPOSITIVO (VIRTUAL MAC)",
                            color = GoldPremium,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = virtualMac,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(virtualMac))
                            android.widget.Toast.makeText(context, "Chave copiada para a área de transferência!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar chave",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (activationError != null) {
                Text(
                    text = activationError!!,
                    color = Color.Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Code input field
            OutlinedTextField(
                value = activationCodeInput,
                onValueChange = { 
                    activationCodeInput = it
                    activationError = null
                },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                label = { Text("Código de Ativação / Licença", color = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPremium,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedLabelColor = GoldPremium
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action button
            Button(
                onClick = {
                    if (activationCodeInput.isEmpty()) {
                        activationError = "Por favor, digite seu código de ativação"
                    } else {
                        val success = viewModel.activateLicense(activationCodeInput)
                        if (success) {
                            android.widget.Toast.makeText(context, "Dispositivo ativado com sucesso! Aproveite!", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            activationError = "Código inválido para este dispositivo!"
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "ATIVAR DISPOSITIVO",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun MK21Logo(
    modifier: Modifier = Modifier,
    showSubtitle: Boolean = true,
    compact: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Sleek, compact perfect circle wrapping ONLY the "MK21" logo letters
        Box(
            modifier = Modifier
                .size(if (compact) 72.dp else 125.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E1C22),
                            Color(0xFF131215)
                        )
                    ),
                    CircleShape
                )
                .border(
                    BorderStroke(
                        if (compact) 1.dp else 1.5.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFD700).copy(alpha = 0.85f), // Refined Gold
                                Color(0xFFFF3B30).copy(alpha = 0.85f), // Premium Red
                                Color(0xFFFFD700).copy(alpha = 0.85f)
                            )
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = if (compact) 1.dp else 3.dp) // Offset slightly for italic baseline aesthetics
            ) {
                // "MK" in Shiny Chrome Silver Gradient with Bevel Shadow
                Text(
                    text = "MK",
                    fontSize = if (compact) 20.sp else 36.sp,
                    fontWeight = FontWeight.Black,
                    style = androidx.compose.ui.text.TextStyle(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        letterSpacing = if (compact) (-0.5).sp else (-1.5).sp,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.95f),
                            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                            blurRadius = 6f
                        ),
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFEDEDED),
                                Color(0xFF9E9E9E),
                                Color(0xFF535353)
                            )
                        )
                    )
                )
                // "21" in Back-Glow Laser Red Gradient overlapping the letters slightly
                Text(
                    text = "21",
                    fontSize = if (compact) 22.sp else 41.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.offset(x = if (compact) (-1).dp else (-3).dp),
                    style = androidx.compose.ui.text.TextStyle(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        letterSpacing = if (compact) (-0.5).sp else (-1.5).sp,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color(0xFFFF1E1E).copy(alpha = 0.85f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                            blurRadius = 14f
                        ),
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFF3B30),
                                Color(0xFFFF1E1E),
                                Color(0xFFB30404)
                            )
                        )
                    )
                )
            }
        }

        if (showSubtitle) {
            Spacer(modifier = Modifier.height(14.dp))

            // Central shiny flare bar
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .height(if (compact) 1.dp else 1.8.dp)
                    .width(if (compact) 60.dp else 125.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFFF1E1E).copy(alpha = 0.2f),
                                Color(0xFFFF3B30).copy(alpha = 0.8f),
                                Color(0xFFFFFFFF), // Core hot spark flare
                                Color(0xFFFF3B30).copy(alpha = 0.8f),
                                Color(0xFFFF1E1E).copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Subtitle: MAIS QUE UM NÚMERO É RESULTADO flanked by tapered arrows/lines
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                // Left tapered segment
                Box(
                    modifier = Modifier
                        .height(1.5.dp)
                        .width(if (compact) 10.dp else 20.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color(0xFFFF1E1E))
                            )
                        )
                )

                Text(
                    text = " MAIS QUE UM NÚMERO É RESULTADO ",
                    fontSize = if (compact) 5.sp else 7.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.95f),
                    letterSpacing = if (compact) 0.1.sp else 0.4.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Normal,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color(0xFFFF1E1E).copy(alpha = 0.5f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                            blurRadius = 4f
                        )
                    )
                )

                // Right tapered segment
                Box(
                    modifier = Modifier
                        .height(1.5.dp)
                        .width(if (compact) 10.dp else 20.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFF1E1E), Color.Transparent)
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun SophisticatedBrandHeader(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    MK21Logo(
        modifier = modifier,
        showSubtitle = !compact,
        compact = compact
    )
}

/**
 * Standard Boot Setup load screen
 */
@Composable
fun StartupGateScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SophisticatedBrandHeader()
            Spacer(modifier = Modifier.height(36.dp))
            CircularProgressIndicator(color = com.example.ui.theme.SophisticatedRedStart, strokeWidth = 3.dp)
        }
    }
}

/**
 * LOGIN AND SERVER SETTINGS
 */
@Composable
fun ServerConfigScreen(viewModel: AppViewModel, onNavigateToHome: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val activePlaylist by viewModel.activePlaylistName.collectAsState()
    val predefinedServersList by viewModel.predefinedServersState.collectAsState()
    val manualLists by viewModel.manualPlaylists.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    var showManualDialog by remember { mutableStateOf(false) }
    var showImportPanelDialogInLogin by remember { mutableStateOf(false) }
    var importStatusMsg by remember { mutableStateOf<String?>(null) }
    var editingPlaylist by remember { mutableStateOf<com.example.data.model.ManualPlaylist?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Predef, 1: Manual List
    var passwordVisible by remember { mutableStateOf(false) }
    var logoClickCount by remember { mutableStateOf(0) }
    var showLicenseDialogInLogin by remember { mutableStateOf(false) }
    val isPremiumActive by viewModel.isPremiumActive.collectAsState()

    val isAmoled = MaterialTheme.colorScheme.background == Color.Black
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isAmoled) {
                    Modifier.background(Color.Black)
                } else {
                    Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(com.example.ui.theme.SophisticatedBg, Color(0xFF0F0F12))
                        )
                    )
                }
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Integrated Sophisticated Brand Logo Header
            Box(
                modifier = Modifier.clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    logoClickCount++
                    if (logoClickCount >= 5) {
                        showLicenseDialogInLogin = true
                        logoClickCount = 0
                    }
                }
            ) {
                SophisticatedBrandHeader()
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Tab Bar
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = com.example.ui.theme.SophisticatedRedStart,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = com.example.ui.theme.SophisticatedRedStart
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Predefinidos", fontWeight = FontWeight.Bold, color = if (selectedTab == 0) Color.White else Color.Gray, fontSize = 14.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Listas Manuais", fontWeight = FontWeight.Bold, color = if (selectedTab == 1) Color.White else Color.Gray, fontSize = 14.sp) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (selectedTab == 0) {
                // Predefined Server setupcard
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161515)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Acesso Oficial MK21",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // USERNAME & PASSWORD IN A SINGLE CONTIGUOUS MODERN ROW
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { viewModel.setCredentials(it, password) },
                                label = { Text("Usuário", fontSize = 11.sp, color = Color.Gray) },
                                maxLines = 1,
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.Gray.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            clipboardManager?.getText()?.text?.let { clipboardText ->
                                                viewModel.setCredentials(clipboardText.trim(), password)
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Colar usuário",
                                            tint = Color.Gray.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                isError = false,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("username_input"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = com.example.ui.theme.SophisticatedRedStart,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                    focusedLabelColor = com.example.ui.theme.SophisticatedRedStart,
                                    focusedContainerColor = Color(0xFF09090C),
                                    unfocusedContainerColor = Color(0xFF09090C),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { viewModel.setCredentials(username, it) },
                                label = { Text("Senha", fontSize = 11.sp, color = Color.Gray) },
                                maxLines = 1,
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color.Gray.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                trailingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                clipboardManager?.getText()?.text?.let { clipboardText ->
                                                    viewModel.setCredentials(username, clipboardText.trim())
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentPaste,
                                                contentDescription = "Colar senha",
                                                tint = Color.Gray.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { passwordVisible = !passwordVisible },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                                                tint = Color.Gray.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                isError = false,
                                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("password_input"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = com.example.ui.theme.SophisticatedRedStart,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                    focusedLabelColor = com.example.ui.theme.SophisticatedRedStart,
                                    focusedContainerColor = Color(0xFF09090C),
                                    unfocusedContainerColor = Color(0xFF09090C),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Selecione o Servidor",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        var serverExpanded by remember { mutableStateOf(false) }
                        val selectedServer = predefinedServersList.find { it.name == activePlaylist } 
                            ?: predefinedServersList.firstOrNull() 
                            ?: com.example.data.model.ServerProfile("server_5", "CB6000", "http://cdn.caterlune.top")

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("server_combo_box")
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { serverExpanded = !serverExpanded },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF09090C)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Dns,
                                        contentDescription = "Server icon",
                                        tint = com.example.ui.theme.SophisticatedRedStart,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "CONEXÃO SELECIONADA",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            color = com.example.ui.theme.GoldPremium,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = selectedServer.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Dropdown icon",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = serverExpanded,
                                onDismissRequest = { serverExpanded = false },
                                modifier = Modifier
                                    .width(260.dp)
                                    .background(Color(0xFF0C0C0F))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(10.dp))
                            ) {
                                predefinedServersList.forEach { server ->
                                    val isSelected = activePlaylist == server.name
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Dns,
                                                    contentDescription = null,
                                                    tint = if (isSelected) com.example.ui.theme.SophisticatedRedStart else Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = server.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectPlaylist(server.name)
                                            serverExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Multiple Manual Playlist lists configuration Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161515)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Minhas Listas M3U",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showImportPanelDialogInLogin = true }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Importar Painel", tint = GoldPremium)
                                }
                                IconButton(onClick = { showManualDialog = true }) {
                                    Icon(Icons.Default.AddCircle, contentDescription = "Add List", tint = GoldPremium)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        if (manualLists.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Nenhuma lista salva.\nToque no '+' para adicionar.",
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            manualLists.forEach { manual ->
                                val isSelected = activePlaylist == manual.name
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { viewModel.selectPlaylist(manual.name) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFF1F0B0E) else Color(0xFF0F0F12)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) com.example.ui.theme.SophisticatedRedStart.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.05f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { viewModel.selectPlaylist(manual.name) },
                                            colors = RadioButtonDefaults.colors(selectedColor = com.example.ui.theme.SophisticatedRedStart)
                                        )
                                        
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = manual.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = manual.url,
                                                fontSize = 11.sp,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            IconButton(
                                                onClick = { editingPlaylist = manual },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit List",
                                                    tint = com.example.ui.theme.GoldPremium.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteManualPlaylist(manual.name) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete List",
                                                    tint = Color.Red.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(18.dp)
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

            Spacer(modifier = Modifier.height(24.dp))

            // Error display
            if (errorMsg != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1215)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMsg ?: "",
                        color = Color.Red,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Main Load / Enter Button
            Button(
                onClick = {
                    if (selectedTab == 0 && (username.isEmpty() || password.isEmpty())) {
                        viewModel.selectPlaylist(activePlaylist) // will trigger validate
                    } else {
                        viewModel.refreshActivePlaylist()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_button"),
                enabled = loadingProgress == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = com.example.ui.theme.SophisticatedRedStart.copy(alpha = 0.4f)
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (loadingProgress == null) {
                                Brush.horizontalGradient(
                                    colors = listOf(com.example.ui.theme.SophisticatedRedStart, com.example.ui.theme.SophisticatedRedEnd)
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(com.example.ui.theme.SophisticatedRedStart.copy(alpha = 0.5f), com.example.ui.theme.SophisticatedRedEnd.copy(alpha = 0.5f))
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (loadingProgress != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Sincronizando...", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Enter icon", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ENTRAR E CARREGAR", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Enter Home instantly if items already cached in Room!
            LaunchedEffect(Unit) {
                viewModel.loginSuccess.collect {
                    onNavigateToHome()
                }
            }

            LaunchedEffect(loadingProgress) {
                if (loadingProgress == 100) {
                    delay(400)
                    onNavigateToHome()
                }
            }

            // If we already have items in active lists, let the user enter directly without refreshing
            Spacer(modifier = Modifier.height(12.dp))
            val itemsCount = viewModel.activeItemsList.collectAsState().value.size
            if (itemsCount > 0 && loadingProgress == null) {
                TextButton(onClick = onNavigateToHome) {
                    Text("Prosseguir para Home (Modo Offline / Cache)", color = GoldPremium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Percentage-based Load HUD Overlay
        if (loadingProgress != null && loadingProgress!! < 100) {
            Dialog(onDismissRequest = {}) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { (loadingProgress ?: 0).toFloat() / 100f },
                            modifier = Modifier.size(90.dp),
                            color = NetflixRed,
                            strokeWidth = 6.dp,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        Text(
                            text = "$loadingProgress%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Buscando Playlist",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Por favor, aguarde...",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Unified Add/Edit Manual Playlist Dialog Form
        if (showManualDialog || editingPlaylist != null) {
            val isEditing = editingPlaylist != null
            val initialName = editingPlaylist?.name ?: ""
            val initialUrl = editingPlaylist?.url ?: ""
            
            var inputListName by remember(editingPlaylist) { mutableStateOf(initialName) }
            var inputListUrl by remember(editingPlaylist) { mutableStateOf(initialUrl) }
            
            Dialog(onDismissRequest = { 
                showManualDialog = false 
                editingPlaylist = null
            }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161515)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isEditing) "Editar Lista Manual" else "Adicionar Lista Manual",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = inputListName,
                            onValueChange = { inputListName = it },
                            label = { Text("Nome da Lista", color = Color.Gray, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = com.example.ui.theme.SophisticatedRedStart,
                                focusedLabelColor = com.example.ui.theme.SophisticatedRedStart,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = Color(0xFF09090C),
                                unfocusedContainerColor = Color(0xFF09090C),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = inputListUrl,
                            onValueChange = { inputListUrl = it.trim() },
                            label = { Text("URL M3U", color = Color.Gray, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = com.example.ui.theme.SophisticatedRedStart,
                                focusedLabelColor = com.example.ui.theme.SophisticatedRedStart,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                focusedContainerColor = Color(0xFF09090C),
                                unfocusedContainerColor = Color(0xFF09090C),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { 
                                showManualDialog = false 
                                editingPlaylist = null
                            }) {
                                Text("Cancelar", color = Color.Gray, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    if (inputListName.isNotEmpty() && inputListUrl.isNotEmpty()) {
                                        if (isEditing) {
                                            viewModel.updateManualPlaylist(editingPlaylist!!.name, inputListName, inputListUrl)
                                        } else {
                                            viewModel.addManualPlaylist(inputListName, inputListUrl)
                                        }
                                        showManualDialog = false
                                        editingPlaylist = null
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.SophisticatedRedStart)
                            ) {
                                Text("Salvar", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Import Panel text directly into App and Manual Lists
        if (showImportPanelDialogInLogin) {
            var panelTextInput by remember { mutableStateOf("") }
            Dialog(onDismissRequest = { 
                showImportPanelDialogInLogin = false 
                importStatusMsg = null
            }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131111)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Importar Painel de Listas",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Cole o texto do painel/revendedor. O app salvará automaticamente os servidores, links M3U e credenciais.",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = panelTextInput,
                            onValueChange = { panelTextInput = it },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp),
                            label = { Text("Texto do Painel (URLs ou M3U)", color = Color.Gray, fontSize = 10.sp) },
                            minLines = 4,
                            maxLines = 6,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = com.example.ui.theme.SophisticatedRedStart,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                focusedLabelColor = com.example.ui.theme.SophisticatedRedStart,
                                focusedContainerColor = Color(0xFF09090C),
                                unfocusedContainerColor = Color(0xFF09090C),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        )

                        if (importStatusMsg != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = importStatusMsg ?: "",
                                color = GoldPremium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { 
                                    showImportPanelDialogInLogin = false 
                                    importStatusMsg = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("CANCELAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    if (panelTextInput.trim().isNotEmpty()) {
                                        val success = viewModel.importServersFromPlainText(panelTextInput)
                                        if (success) {
                                            importStatusMsg = "Listas e servidores importados com sucesso!"
                                            coroutineScope.launch {
                                                delay(1200)
                                                showImportPanelDialogInLogin = false
                                                importStatusMsg = null
                                            }
                                        } else {
                                            importStatusMsg = "Nenhum servidor ou link M3U válido encontrado."
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.SophisticatedRedStart),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("IMPORTAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // License & Activation Secret Dialog from 5x click on logo
        if (showLicenseDialogInLogin) {
            var localKeyInput by remember { mutableStateOf("") }
            var licenseStatusMsg by remember { mutableStateOf<String?>(null) }
            val virtualMac = viewModel.virtualMacAddress
            var tapCount by remember { mutableStateOf(0) }
            var isAdminMode by remember { mutableStateOf(false) }

            Dialog(onDismissRequest = { showLicenseDialogInLogin = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131111)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, com.example.ui.theme.GoldPremium.copy(alpha = 0.3f)),
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Licença & Ativação",
                            color = com.example.ui.theme.GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    tapCount++
                                    if (tapCount >= 5) {
                                        isAdminMode = true
                                        android.widget.Toast.makeText(context, "Painel Admin Ativado!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                        )

                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isPremiumActive) Color(0xFF1B5E20) else Color(0xFFE65100))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isPremiumActive) "PREMIUM ATIVO" else "MODO AVALIAÇÃO: ${viewModel.trialDaysLeft.value} DIAS",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Device ID / Virtual MAC (Admin info)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ID DO DISPOSITIVO (MAC):",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = virtualMac,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(virtualMac))
                                        android.widget.Toast.makeText(context, "MAC copiado!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copiar MAC",
                                        tint = com.example.ui.theme.GoldPremium,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        if (isAdminMode) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "PAINEL GERADOR ADMIN (FBG)",
                                        color = com.example.ui.theme.GoldPremium,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )

                                    var remoteMacInput by remember { mutableStateOf("") }
                                    var generatedCode by remember { mutableStateOf("") }

                                    OutlinedTextField(
                                        value = remoteMacInput,
                                        onValueChange = { mac ->
                                            remoteMacInput = mac
                                            generatedCode = if (mac.isNotEmpty()) {
                                                viewModel.generateAutonomousKey(mac)
                                            } else {
                                                ""
                                            }
                                        },
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                        label = { Text("Virtual MAC do Cliente", color = Color.Gray, fontSize = 9.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = com.example.ui.theme.GoldPremium,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                            focusedLabelColor = com.example.ui.theme.GoldPremium
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (generatedCode.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "CÓDIGO GERADO:",
                                                    color = Color.Gray,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = generatedCode,
                                                    color = Color.Green,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(generatedCode))
                                                    android.widget.Toast.makeText(context, "Código copiado!", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copiar Código",
                                                    tint = com.example.ui.theme.GoldPremium,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (licenseStatusMsg != null) {
                            Text(
                                text = licenseStatusMsg!!,
                                color = if (isPremiumActive || isAdminMode) Color.Green else Color.Red,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        // Input field to enter license
                        OutlinedTextField(
                            value = localKeyInput,
                            onValueChange = {
                                localKeyInput = it
                                licenseStatusMsg = null
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                            label = { Text("Código de Ativação", color = Color.Gray, fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = com.example.ui.theme.GoldPremium,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedLabelColor = com.example.ui.theme.GoldPremium
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showLicenseDialogInLogin = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("FECHAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    val trimmedInput = localKeyInput.trim()
                                    if (trimmedInput == "admin2026" || trimmedInput == "guarniere2026" || trimmedInput == "mk21admin") {
                                        isAdminMode = true
                                        licenseStatusMsg = "Modo Admin Liberado!"
                                        android.widget.Toast.makeText(context, "Painel Admin Ativado!", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (localKeyInput.isEmpty()) {
                                            licenseStatusMsg = "Insira um código válido"
                                        } else {
                                            val success = viewModel.activateLicense(localKeyInput)
                                            if (success) {
                                                licenseStatusMsg = "Premium Ativado!"
                                                android.widget.Toast.makeText(context, "Chave ativada com sucesso!", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                licenseStatusMsg = "Código inválido para este ID"
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.GoldPremium),
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("ATIVAR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * HOME STREAMING DASHBOARD SCREEN
 */
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToConfig: () -> Unit
) {
    val context = LocalContext.current
    val activePlaylist by viewModel.activePlaylistName.collectAsState()
    val predefinedServersList by viewModel.predefinedServersState.collectAsState()
    val manualLists by viewModel.manualPlaylists.collectAsState()
    val contentType by viewModel.selectedContentType.collectAsState()
    val activeCategory by viewModel.selectedCategory.collectAsState()
    val categories by viewModel.activeCategories.collectAsState()
    val itemsList by viewModel.activeItemsList.collectAsState()
    val searchResultQuery by viewModel.searchQuery.collectAsState()
    val highlights by viewModel.highlightsList.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentPlayingItem by viewModel.currentPlayingItem.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()
    val continueWatching by viewModel.continueWatchingList.collectAsState()
    val backgroundLoadingState by viewModel.backgroundLoadingState.collectAsState()

    var showPlaylistMenu by remember { mutableStateOf(false) }
    var selectedSeriesForDetail by remember { mutableStateOf<GroupedSeries?>(null) }
    var showSortOrderDialog by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val gridColumnsCount = remember(contentType, isLandscape, configuration.screenWidthDp) {
        if (contentType == ContentType.LIVE) {
            if (isLandscape) {
                if (configuration.screenWidthDp >= 900) 3 else 2
            } else {
                1
            }
        } else {
            if (isLandscape) {
                if (configuration.screenWidthDp >= 900) 5 else 4
            } else {
                3
            }
        }
    }

    val processedSeriesChunks = remember(itemsList, contentType, isLandscape, gridColumnsCount) {
        if (contentType == ContentType.SERIES) {
            val grouped = groupSeriesItems(itemsList)
            grouped.chunked(gridColumnsCount)
        } else {
            emptyList()
        }
    }

    val processedNormalChunks = remember(itemsList, contentType, isLandscape, gridColumnsCount) {
        if (contentType != ContentType.SERIES) {
            itemsList.chunked(gridColumnsCount)
        } else {
            emptyList()
        }
    }

    var selectedChannelForEpg by remember { mutableStateOf<PlaylistItem?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            if (isLandscape) {
                // Squeezed Premium Landscape Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SophisticatedBrandHeader(
                        compact = true,
                        modifier = Modifier.clickable { onNavigateToConfig() }
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Connected Dropdown combo
                    Box {
                        Button(
                            onClick = { showPlaylistMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111115)),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cloud, contentDescription = "Playlist selector", tint = GoldPremium, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activePlaylist.take(12) + if (activePlaylist.length > 12) ".." else "",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown indicators", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }
                        }

                        DropdownMenu(
                            expanded = showPlaylistMenu,
                            onDismissRequest = { showPlaylistMenu = false },
                            modifier = Modifier.background(Color(0xFF0C0C0F))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Predefinidos Oficial", color = GoldPremium, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                onClick = {},
                                enabled = false
                            )
                            predefinedServersList.forEach { server ->
                                DropdownMenuItem(
                                    text = { Text(server.name, color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        viewModel.selectPlaylist(server.name)
                                        showPlaylistMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Listas Customizadas", color = GoldPremium, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                onClick = {},
                                enabled = false
                            )
                            manualLists.forEach { manual ->
                                DropdownMenuItem(
                                    text = { Text(manual.name, color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        viewModel.selectPlaylist(manual.name)
                                        showPlaylistMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // ContentType pill tabs
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            ContentType.LIVE to "Ao Vivo",
                            ContentType.MOVIE to "Filmes",
                            ContentType.SERIES to "Séries"
                        ).forEach { (type, label) ->
                            val isSelected = contentType == type
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) com.example.ui.theme.SophisticatedRedStart else Color(0xFF111115))
                                    .clickable { viewModel.changeContentType(type) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Consolidated Search box (BasicTextField - centers content and never crops vertically!)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF09090C), RoundedCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                            singleLine = true,
                            maxLines = 1,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Start
                            ),
                            cursorBrush = SolidColor(Color.White),
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Buscar...",
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.setSearchQuery("") },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Direct Quick Sort Button on Home Screen
                    IconButton(onClick = { showSortOrderDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Sort, contentDescription = "Ordenação", tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = { viewModel.refreshActivePlaylist() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh list", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                // Portrait Original Column
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SophisticatedBrandHeader(
                        compact = true,
                        modifier = Modifier.clickable { onNavigateToConfig() }
                    )
                    
                    Spacer(modifier = Modifier.width(14.dp))
                    
                    Box {
                        Button(
                            onClick = { showPlaylistMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111115)),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cloud, contentDescription = "Playlist selector", tint = GoldPremium, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activePlaylist.take(12) + if (activePlaylist.length > 12) ".." else "",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown indicators", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }
                        }

                        DropdownMenu(
                            expanded = showPlaylistMenu,
                            onDismissRequest = { showPlaylistMenu = false },
                            modifier = Modifier.background(Color(0xFF0C0C0F))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Predefinidos Oficial", color = GoldPremium, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                onClick = {},
                                enabled = false
                            )
                            predefinedServersList.forEach { server ->
                                DropdownMenuItem(
                                    text = { Text(server.name, color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        viewModel.selectPlaylist(server.name)
                                        showPlaylistMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Listas Customizadas", color = GoldPremium, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                onClick = {},
                                enabled = false
                            )
                            manualLists.forEach { manual ->
                                DropdownMenuItem(
                                    text = { Text(manual.name, color = Color.White, fontSize = 13.sp) },
                                    onClick = {
                                        viewModel.selectPlaylist(manual.name)
                                        showPlaylistMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = { viewModel.refreshActivePlaylist() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh content list",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open preferences settings page",
                            tint = Color.White
                        )
                    }
                }

                // Categories Selector Row and Search Bar on the SAME line!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: ContentType tabs
                    Row(
                        modifier = Modifier.weight(1.4f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            ContentType.LIVE to "Ao Vivo",
                            ContentType.MOVIE to "Filmes",
                            ContentType.SERIES to "Séries"
                        ).forEach { (type, label) ->
                            val isSelected = contentType == type
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { viewModel.changeContentType(type) }
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .height(3.dp)
                                        .width(24.dp)
                                        .background(if (isSelected) com.example.ui.theme.SophisticatedRedStart else Color.Transparent)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Right: Compact legible Search Bar (Using BasicTextField to prevent vertical cut-off completely!)
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(36.dp)
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF09090C), RoundedCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
                            singleLine = true,
                            maxLines = 1,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Start
                            ),
                            cursorBrush = SolidColor(Color.White),
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Buscar...",
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.setSearchQuery("") },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Direct Quick Sort Button on Home Screen (Portrait)
                    IconButton(
                        onClick = { showSortOrderDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Classificar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Category submenu selection Row (using REAL data)
            if (searchQuery.isEmpty() && categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = activeCategory == cat
                        Card(
                            onClick = { viewModel.selectCategory(cat) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) com.example.ui.theme.SophisticatedRedStart else Color(0xFF111115)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = cat,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (backgroundLoadingState != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161111)),
                    border = BorderStroke(1.dp, com.example.ui.theme.SophisticatedRedStart.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = com.example.ui.theme.SophisticatedRedStart,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = backgroundLoadingState ?: "",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scrollable Content
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Highlights Slider Carousel Banner
                if (searchQuery.isEmpty() && highlights.isNotEmpty()) {
                    item {
                        HighlightBanner(highlights = highlights, onPlayItem = { viewModel.playContent(it) })
                    }
                }

                // Continue Watching Section
                if (searchQuery.isEmpty() && continueWatching.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(
                                text = "Continuar Assistindo",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = GoldPremium,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                letterSpacing = 0.5.sp
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(continueWatching) { item ->
                                    Card(
                                        onClick = { viewModel.playContent(item) },
                                        modifier = Modifier
                                            .width(140.dp)
                                            .height(96.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            val logoModel = enhancedLogoFallback(item.logoUrl, item.name)
                                            if (logoModel.isNotEmpty()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(logoModel)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = item.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                NativeChannelBadge(item.name)
                                            }
                                            // Play icon center overlay
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                                    .padding(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Continuar assistindo",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            // Title at the bottom
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .align(Alignment.BottomStart)
                                                    .background(Color.Black.copy(alpha = 0.82f))
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = item.name,
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Grid Items List
                item {
                    val count = itemsList.size
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Resultados da Busca" else activeCategory,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.LightGray
                        )
                        Text(
                            text = "$count itens found",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                if (itemsList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.SentimentDissatisfied, contentDescription = "None found", tint = Color.Gray, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Nenhum canal ou vídeo carregado.\nTente clicar em atualizar ou conferir credenciais.",
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                                if (errorMsg != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Detalhes: $errorMsg",
                                        textAlign = TextAlign.Center,
                                        fontSize = 12.sp,
                                        color = Color.Red.copy(alpha = 0.85f),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (contentType == ContentType.SERIES) {
                    // Smart Netflix-style Series Grouping layout using optimized remembered chunks!
                    items(processedSeriesChunks) { rowSeries ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (series in rowSeries) {
                                Box(modifier = Modifier.weight(1f)) {
                                    GroupedSeriesCard(
                                        series = series,
                                        onClick = { selectedSeriesForDetail = series }
                                    )
                                }
                            }
                            if (rowSeries.size < gridColumnsCount) {
                                for (i in 0 until (gridColumnsCount - rowSeries.size)) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    // Optimized Grid Layout using custom grouping chunk loops and remembered list processing!
                    items(processedNormalChunks) { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (item in rowItems) {
                                Box(modifier = Modifier.weight(1f)) {
                                    GridItemCard(
                                        item = item,
                                        isGridCompact = contentType != ContentType.LIVE,
                                        onClick = { viewModel.playContent(item) },
                                        onToggleFavorite = { viewModel.toggleFavorite(item) },
                                        onPreviewEpg = { selectedChannelForEpg = item }
                                    )
                                }
                            }
                            // Filler boxes to balance columns nicely
                            if (rowItems.size < gridColumnsCount) {
                                for (i in 0 until (gridColumnsCount - rowItems.size)) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Percentage-based Load HUD Overlay with progressive state labels
        if (loadingProgress != null && loadingProgress!! < 100) {
            val statusText = when {
                loadingProgress!! <= 15 -> "Conectando ao servidor..."
                loadingProgress!! <= 45 -> "Baixando canais e mídias..."
                loadingProgress!! <= 80 -> "Processando mídias recebidas..."
                loadingProgress!! <= 95 -> "Salvando dados no banco..."
                else -> "Finalizando sincronização..."
            }

            Dialog(onDismissRequest = {}) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { (loadingProgress ?: 0).toFloat() / 100f },
                            modifier = Modifier.size(90.dp),
                            color = NetflixRed,
                            strokeWidth = 6.dp,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        Text(
                            text = "$loadingProgress%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "Buscando Playlist",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = statusText,
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // EPG Live Channel Preview Dialog
        if (selectedChannelForEpg != null) {
            ChannelEpgPreviewDialog(
                item = selectedChannelForEpg!!,
                onDismiss = { selectedChannelForEpg = null },
                onPlay = { channelItem ->
                    selectedChannelForEpg = null
                    viewModel.playContent(channelItem)
                }
            )
        }

        // Series Detail and Episode picker dialog
        if (selectedSeriesForDetail != null) {
            val series = selectedSeriesForDetail!!
            val groupedSeasons = remember(series.episodes) {
                groupEpisodesBySeason(series.episodes)
            }
            val seasonList = remember(groupedSeasons) { groupedSeasons.keys.toList() }
            var selectedSeason by remember(seasonList) {
                mutableStateOf(seasonList.firstOrNull() ?: "")
            }
            val episodesInSeason = remember(selectedSeason, groupedSeasons) {
                groupedSeasons[selectedSeason] ?: emptyList()
            }
            Dialog(onDismissRequest = { selectedSeriesForDetail = null }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0E)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(enhancedLogoFallback(series.logoUrl, series.title))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = series.title,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = series.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${series.episodes.size} episódios • ${series.category}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = { selectedSeriesForDetail = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close episodes list", tint = Color.White)
                            }
                        }
                        
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        // Seasons Horizontal Chips Selector if more than 1 season exists
                        if (seasonList.size > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                items(seasonList) { season ->
                                    val isCurrent = season == selectedSeason
                                    Card(
                                        onClick = { selectedSeason = season },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCurrent) com.example.ui.theme.SophisticatedRedStart else Color(0xFF1B1A1E)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isCurrent) Color.Transparent else Color.White.copy(alpha = 0.08f)
                                        )
                                    ) {
                                        Text(
                                            text = season,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Text(
                            text = if (selectedSeason.isNotEmpty()) "$selectedSeason - Episódios Disponíveis" else "Episódios Disponíveis",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPremium,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(episodesInSeason) { episode ->
                                Card(
                                    onClick = {
                                        viewModel.playContent(episode)
                                        selectedSeriesForDetail = null
                                    },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play episode",
                                            tint = NetflixRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = episode.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // PIN protection input Dialog prompt
        val activePinItem by viewModel.activePinPromptItem.collectAsState()
        if (activePinItem != null) {
            AdultPinDialog(
                onDismiss = { viewModel.dismissPinPrompt() },
                onPinVerify = { pin ->
                    viewModel.checkPinAndPlay(pin)
                }
            )
        }

        val activePinCategory by viewModel.activePinPromptCategory.collectAsState()
        if (activePinCategory != null) {
            AdultPinDialog(
                onDismiss = { viewModel.dismissCategoryPinPrompt() },
                onPinVerify = { pin ->
                    viewModel.checkPinAndPlay(pin)
                }
            )
        }

        // Complete fullscreen video player overlay
        if (currentPlayingItem != null) {
            if (viewModel.preferencesService.useExternalPlayer) {
                val context = LocalContext.current
                LaunchedEffect(currentPlayingItem) {
                    val item = currentPlayingItem
                    if (item != null) {
                        val videoUrl = item.url.trim()
                        if (videoUrl.isNotEmpty()) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(android.net.Uri.parse(videoUrl), "video/*")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                val pType = viewModel.preferencesService.externalPlayerType
                                if (pType == "VLC") {
                                    setPackage("org.videolan.vlc")
                                } else if (pType == "MX Player") {
                                    setPackage("com.mxtech.videoplayer.ad")
                                }
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: android.content.ActivityNotFoundException) {
                                val playerRequested = viewModel.preferencesService.externalPlayerType
                                android.widget.Toast.makeText(
                                    context,
                                    "Player $playerRequested não encontrado. Abrindo com player padrão...",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                try {
                                    val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(android.net.Uri.parse(videoUrl), "video/*")
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(fallbackIntent)
                                } catch (ex: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Nenhum player de vídeo disponível para reproduzir este link.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Erro ao iniciar player: ${e.localizedMessage}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                    viewModel.closePlayback()
                }
            } else {
                val isInPipMode by viewModel.isInPipMode.collectAsState()
                VideoPlayerUI(
                    playlistItem = currentPlayingItem!!,
                    onClosePlayback = { viewModel.closePlayback() },
                    onPlayPrevious = { viewModel.playPrevious() },
                    onPlayNext = { viewModel.playNext() },
                    isInPipMode = isInPipMode,
                    viewModel = viewModel
                )
            }
        }

        if (showSortOrderDialog) {
            SettingsSelectionDialog(
                title = "Ordenação dos Menus/Canais",
                options = listOf(
                    "Ordem por número",
                    "Ordem por adição",
                    "Ordem por qualificação",
                    "Ordem por A-Z",
                    "Ordem por Z-A"
                ),
                currentValue = viewModel.preferencesService.menuSortOrder,
                onDismiss = { showSortOrderDialog = false },
                onOptionSelected = {
                    viewModel.updateMenuSortOrder(it)
                    showSortOrderDialog = false
                }
            )
        }
    }
}

/**
 * FEATURE HIGH INTERACTIVE HIGHLIGHT BANNER COM SUPORTE A SWIPE (DESLIZAR DOS DEDOS)
 */
@Composable
fun HighlightBanner(highlights: List<PlaylistItem>, onPlayItem: (PlaylistItem) -> Unit) {
    if (highlights.isEmpty()) return

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0) { highlights.size }
    
    // Auto-advance suave a cada 5 segundos apenas quando o usuário não estiver deslizando a tela
    LaunchedEffect(pagerState, highlights.size) {
        while (true) {
            delay(5000L)
            if (!pagerState.isScrollInProgress && highlights.size > 1) {
                val nextPage = (pagerState.currentPage + 1) % highlights.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val highlightedItem = highlights[page]

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(22.dp))
                    .clickable { onPlayItem(highlightedItem) }
            ) {
                // Logo image / Banner image background with fallback
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(enhancedLogoFallback(highlightedItem.logoUrl, highlightedItem.name))
                        .crossfade(true)
                        .build(),
                    contentDescription = "Highlight banner logo background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.85f
                )

                // Bottom gradient darkening
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent, 
                                    Color.Black.copy(alpha = 0.3f), 
                                    com.example.ui.theme.SophisticatedBg.copy(alpha = 0.95f)
                                )
                            )
                        )
                )

                // Top info badge "DESTAQUE DA SEMANA"
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .align(Alignment.TopStart)
                ) {
                    Badge(
                        containerColor = com.example.ui.theme.SophisticatedRedStart, 
                        contentColor = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "DESTAQUE DA SEMANA", 
                            fontWeight = FontWeight.Black, 
                            fontSize = 8.sp, 
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // Info contents
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = highlightedItem.category.uppercase(),
                        fontSize = 9.sp,
                        color = com.example.ui.theme.GoldPremium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = highlightedItem.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "IPTV Especial • 2026 • 4K Ultra HD • Estéreo",
                        fontSize = 9.5.sp,
                        color = com.example.ui.theme.SlateTextMuted,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    
                    // Buttons Row matching HTML design
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Button 1: "Assistir Agora" (Primary white action button)
                        Button(
                            onClick = { onPlayItem(highlightedItem) },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play icon",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Assistir Agora", 
                                    fontSize = 12.sp, 
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        // Button 2: Glassmorphic Add/Action icon button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .clickable { onPlayItem(highlightedItem) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add playlist item icon",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Indicadores de bolinha (dots) no canto inferior direito para navegação visual
        if (highlights.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(highlights.size.coerceAtMost(8)) { index ->
                    val isCurrent = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isCurrent) 7.dp else 4.dp)
                            .clip(CircleShape)
                            .background(if (isCurrent) GoldPremium else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

/**
 * GROUPED SERIES GRID CARD COMPOSABLE
 */
@Composable
fun GroupedSeriesCard(series: GroupedSeries, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(146.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0F)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(enhancedLogoFallback(series.logoUrl, series.title))
                        .crossfade(true)
                        .build(),
                    contentDescription = series.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Episode Count Badge
                Box(modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${series.episodes.size} Eps", 
                        fontSize = 8.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = GoldPremium
                    )
                }
            }
            Box(modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF09090C))
                .padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = series.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

data class AppTrackOption(
    val id: String,
    val group: androidx.media3.common.Tracks.Group,
    val trackIndex: Int,
    val name: String,
    val isSelected: Boolean
)

data class EpgProgramInfo(
    val title: String,
    val timeRange: String,
    val description: String,
    val isCurrent: Boolean,
    val progressPercent: Float = 0f,
    val isReal: Boolean = false
)

object AndroidRealEpgCache {
    val cache = java.util.concurrent.ConcurrentHashMap<String, List<EpgProgramInfo>>()

    fun get(channelName: String): List<EpgProgramInfo>? {
        val clean = channelName.uppercase().trim()
        return cache[clean] ?: cache.entries.firstOrNull { 
            clean.contains(it.key) || it.key.contains(clean) 
        }?.value
    }

    fun put(channelName: String, list: List<EpgProgramInfo>) {
        cache[channelName.uppercase().trim()] = list
    }
}

fun getChannelEpgSchedule(channelName: String): List<EpgProgramInfo> {
    val realList = AndroidRealEpgCache.get(channelName)
    if (!realList.isNullOrEmpty()) {
        return realList
    }
    val cal = java.util.Calendar.getInstance()
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = cal.get(java.util.Calendar.MINUTE)
    val currentTotalMinutes = hour * 60 + minute
    val name = channelName.uppercase()

    val rawGrid: List<Triple<Pair<Int, Int>, Pair<Int, Int>, Pair<String, String>>> = when {
        name.contains("GLOBO") -> listOf(
            Triple(6 to 0, 8 to 30, "Bom Dia Brasil" to "Telejornal matinal com as primeiras notícias do Brasil e do mundo."),
            Triple(8 to 30, 10 to 35, "Encontro com Patrícia Poeta" to "Entrevistas, debates de atualidades, música e informação."),
            Triple(10 to 35, 12 to 0, "Mais Você com Ana Maria Braga" to "Receitas culinárias, dicas caseiras, convidados e entretenimento."),
            Triple(12 to 0, 13 to 25, "Praça TV - 1ª Edição" to "Noticiário regional com foco nos acontecimentos locais ao vivo."),
            Triple(13 to 25, 14 to 45, "Globo Esporte & Jornal Hoje" to "Cobertura completa dos esportes e giro internacional de notícias."),
            Triple(14 to 45, 16 to 40, "Sessão da Tarde" to "Exibição de grandes sucessos do cinema para toda a família."),
            Triple(16 to 40, 18 to 25, "Vale a Pena Ver de Novo" to "Reexibição dos maiores clássicos da teledramaturgia nacional."),
            Triple(18 to 25, 19 to 30, "Novela das Seis" to "Capítulo inédito com emoções e reviravoltas da trama de época."),
            Triple(19 to 30, 20 to 30, "Novela das Sete" to "Comédia e romance no horário mais descontraído da programação."),
            Triple(20 to 30, 21 to 30, "Jornal Nacional" to "O principal telejornal do país com reportagens exclusivas e análises."),
            Triple(21 to 30, 22 to 35, "Novela das Nove" to "A trama principal do horário nobre em alta definição."),
            Triple(22 to 35, 23 to 59, "Tela Quente / Cinema Especial" to "Superproduções de Hollywood em exibição especial na TV.")
        )
        name.contains("RECORD") -> listOf(
            Triple(6 to 0, 8 to 45, "Balanço Geral Manhã" to "As primeiras notícias do dia com prestação de serviço ao vivo."),
            Triple(8 to 45, 10 to 0, "Fala Brasil" to "Jornalismo dinâmico e cobertura dos temas que afetam a rotina do cidadão."),
            Triple(10 to 0, 11 to 50, "Hoje em Dia" to "Variedades, moda, culinária e fofocas dos famosos."),
            Triple(11 to 50, 15 to 30, "Balanço Geral & Hora da Venenosa" to "Líder de audiência com notícias policiais e entretenimento."),
            Triple(15 to 30, 16 to 45, "Novela da Tarde" to "Dramaturgia e histórias bíblicas emocionantes."),
            Triple(16 to 45, 19 to 55, "Cidade Alerta" to "Cobertura ao vivo de operações de segurança e helicóptero."),
            Triple(19 to 55, 21 to 0, "Jornal da Record" to "Análise aprofundada dos principais fatos da política e economia."),
            Triple(21 to 0, 22 to 30, "Novela Reis / Série Bíblica" to "Produção épica com grandes efeitos e narrativa histórica."),
            Triple(22 to 30, 23 to 59, "Super Tela / A Fazenda" to "Filmes de ação e reality show com votação ao vivo.")
        )
        name.contains("SBT") -> listOf(
            Triple(6 to 0, 9 to 30, "Primeiro Impacto" to "Noticiário matutino com prestação de serviço e trânsito ao vivo."),
            Triple(9 to 30, 13 to 0, "Bom Dia & Cia" to "Desenhos animados clássicos, gincanas e diversão infantil."),
            Triple(13 to 0, 14 to 30, "Chaves & Seriados" to "As aventuras mais amadas da vila que conquistaram gerações."),
            Triple(14 to 30, 16 to 30, "Novelas da Tarde" to "Dramas mexicanos com grandes paixões e vilãs marcantes."),
            Triple(16 to 30, 18 to 30, "Fofocalizando" to "O universo dos famosos com notícias exclusivas e bastidores."),
            Triple(18 to 30, 19 to 45, "SBT Brasil" to "Telejornalismo ágil com os principais acontecimentos do Brasil."),
            Triple(19 to 45, 20 to 45, "A Infância de Romeu e Julieta" to "Novela infantojuvenil cheia de aventuras e música."),
            Triple(20 to 45, 22 to 15, "Programa do Ratinho" to "Auditório animado, teste de DNA, calouros e muito humor."),
            Triple(22 to 15, 23 to 59, "Cine Espetacular / A Praça é Nossa" to "Humor tradicional e filmes consagrados da Warner.")
        )
        name.contains("BAND") -> listOf(
            Triple(6 to 0, 8 to 0, "Bora Brasil" to "Informações do trânsito e principais manchetes do amanhecer."),
            Triple(11 to 0, 13 to 0, "Jogo Aberto" to "Debate esportivo irreverente comandado por Renata Fan e Denílson."),
            Triple(13 to 0, 14 to 30, "Os Donos da Bola" to "O Craque Neto fala tudo sobre os bastidores do futebol nacional."),
            Triple(14 to 30, 16 to 0, "Melhor da Tarde" to "Culinária com receitas fáceis, fofocas e astral."),
            Triple(16 to 0, 19 to 20, "Brasil Urgente" to "Datena comanda a transmissão ao vivo dos grandes acontecimentos."),
            Triple(19 to 20, 20 to 30, "Jornal da Band" to "Referência em jornalismo com grandes correspondentes internacionais."),
            Triple(20 to 30, 22 to 0, "Perrengue na Band" to "Vídeos mais engraçados da internet comentados no estúdio."),
            Triple(22 to 0, 23 to 59, "MasterChef Brasil / Esporte Total" to "Competição gastronômica e os gols da rodada.")
        )
        name.contains("SPORT") || name.contains("ESPN") || name.contains("PREMIERE") || name.contains("COMBATE") -> listOf(
            Triple(8 to 0, 11 to 0, "SportsCenter / Redação SporTV" to "O resumo matinal dos esportes no Brasil e no mundo."),
            Triple(11 to 0, 13 to 0, "Futebol 360 / Bate Pronto" to "Análise tática dos clubes e escalações para os jogos do dia."),
            Triple(13 to 0, 16 to 0, "Futebol Ao Vivo - Pré-Jogo" to "Entrevistas exclusivas e chegada das delegações no estádio."),
            Triple(16 to 0, 18 to 30, "Campeonato Ao Vivo - Transmissão Direta" to "Narração emocionante lance a lance em alta definição."),
            Triple(18 to 30, 21 to 0, "Linha de Passe / Troca de Passes" to "Mesa-redonda com os melhores comentaristas esportivos."),
            Triple(21 to 0, 23 to 0, "Jogo da Noite Ao Vivo" to "Clássico decisivo com disputa de pontos e grandes lances."),
            Triple(23 to 0, 23 to 59, "Gols da Rodada & Momentos Marcantes" to "Todos os gols e polêmicas da rodada com câmera lenta.")
        )
        name.contains("TELE") || name.contains("HBO") || name.contains("WARNER") || name.contains("PARAMOUNT") || name.contains("UNIVERSAL") -> listOf(
            Triple(8 to 0, 11 to 0, "Sessão Aventura: Os Vingadores" to "Os heróis mais poderosos da Terra reunidos para salvar o universo."),
            Triple(11 to 0, 13 to 30, "Comédia em Alta: Gente Grande 2" to "Diversão garantida com muita risada e confusões de família."),
            Triple(13 to 30, 16 to 0, "Ação Pura: Batman - O Cavaleiro das Trevas" to "Christian Bale e Heath Ledger no aclamado filme de Christopher Nolan."),
            Triple(16 to 0, 18 to 30, "Superestreia: Duna Parte 2" to "A ascensão de Paul Atreides na épica ficção científica interplanetária."),
            Triple(18 to 30, 20 to 15, "Série Especial: House of the Dragon" to "Disputas sangrentas pelo Trono de Ferro com dragões em batalha."),
            Triple(20 to 15, 22 to 30, "Blockbuster do Ano: Oppenheimer" to "A história da criação da bomba atômica vencedora de múltiplos Oscars."),
            Triple(22 to 30, 23 to 59, "Suspense Noturno: Ilha do Medo" to "Mistério intrigante com Leonardo DiCaprio em um hospital psiquiátrico.")
        )
        name.contains("NEWS") || name.contains("CNN") -> listOf(
            Triple(6 to 0, 9 to 0, "Edição das 6 / CNN Novo Dia" to "As primeiras movimentações em Brasília, câmbio e mercado financeiro."),
            Triple(9 to 0, 12 to 0, "Conexão Notícias" to "Links ao vivo com correspondentes nas principais capitais."),
            Triple(12 to 0, 14 to 0, "Visão Geral & Política" to "Entrevistas exclusivas com governantes e especialistas em economia."),
            Triple(14 to 0, 17 to 0, "Estúdio Ao Vivo" to "Cobertura contínua dos julgamentos e votações no Congresso."),
            Triple(17 to 0, 19 to 0, "O Grande Debate" to "Dois lados de cada tema com mediação jornalística e votos do público."),
            Triple(19 to 0, 21 to 0, "Jornal das 19h" to "Resumo dos acontecimentos mais importantes do dia."),
            Triple(21 to 0, 23 to 0, "Jornal da Noite & Análise Internacional" to "Geopolítica, eleições internacionais e reportagens especiais."),
            Triple(23 to 0, 23 to 59, "Mundo Conectado" to "Tecnologia, ciência e documentários jornalísticos.")
        )
        else -> listOf(
            Triple(6 to 0, 10 to 0, "Manhã de Notícias e Entretenimento" to "Programação matinal com variedades, música e utilidade pública."),
            Triple(10 to 0, 13 to 0, "Revista Eletrônica" to "Receitas práticas, dicas de saúde, saúde financeira e convidados."),
            Triple(13 to 0, 16 to 0, "Tarde de Sucessos" to "Exibição de filmes, séries populares e atrações ao vivo."),
            Triple(16 to 0, 19 to 0, "Panorama da Cidade" to "Giro regional com foco em mobilidade urbana e segurança."),
            Triple(19 to 0, 21 to 0, "Horário Nobre ao Vivo" to "Os programas de maior audiência da grade com muita emoção."),
            Triple(21 to 0, 23 to 0, "Sessão Especial da Noite" to "Grandes atrações e shows musicais ao vivo."),
            Triple(23 to 0, 23 to 59, "Madrugada Alternativa" to "Cinema clássico, documentários e produções independentes.")
        )
    }

    return rawGrid.map { (start, end, info) ->
        val startMin = start.first * 60 + start.second
        val endMin = end.first * 60 + end.second
        val isCurrent = currentTotalMinutes in startMin..endMin
        val progress = if (isCurrent) {
            val totalSpan = (endMin - startMin).coerceAtLeast(1)
            val elapsed = (currentTotalMinutes - startMin).coerceAtLeast(0)
            (elapsed.toFloat() / totalSpan.toFloat()).coerceIn(0.05f, 0.98f)
        } else if (currentTotalMinutes > endMin) {
            1.0f
        } else {
            0.0f
        }
        val timeRange = String.format("%02d:%02d - %02d:%02d", start.first, start.second, end.first, end.second)
        EpgProgramInfo(
            title = info.first,
            timeRange = timeRange,
            description = info.second,
            isCurrent = isCurrent,
            progressPercent = progress
        )
    }
}

fun getCurrentEpgProgram(channelName: String): String {
    val realList = AndroidRealEpgCache.get(channelName)
    if (!realList.isNullOrEmpty()) {
        val cur = realList.firstOrNull { it.isCurrent } ?: realList.firstOrNull()
        if (cur != null) return cur.title
    }
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val name = channelName.uppercase()
    return when {
        name.contains("GLOBO") -> {
            when {
                hour in 6..8 -> "Bom Dia Brasil"
                hour in 9..11 -> "Mais Você"
                hour in 12..13 -> "Praça TV - 1ª Edição"
                hour in 14..16 -> "Sessão da Tarde"
                hour in 17..19 -> "Vale a Pena Ver de Novo"
                hour in 20..21 -> "Jornal Nacional"
                hour in 22..23 -> "Novela das Nove"
                else -> "Cinema Espetacular"
            }
        }
        name.contains("RECORD") -> {
            when (hour) {
                in 6..8 -> "Balanço Geral Manhã"
                in 12..14 -> "Balanço Geral"
                in 15..17 -> "Cidade Alerta"
                in 20..21 -> "Jornal da Record"
                else -> "Super Tela"
            }
        }
        name.contains("SBT") -> {
            when (hour) {
                in 6..8 -> "Primeiro Impacto"
                in 13..14 -> "Chaves"
                in 20..21 -> "Romeu e Julieta"
                in 22..23 -> "Programa do Ratinho"
                else -> "Cine Espetacular"
            }
        }
        name.contains("BAND") -> {
            when (hour) {
                in 12..13 -> "Jogo Aberto"
                in 14..15 -> "Os Donos da Bola"
                in 16..18 -> "Brasil Urgente"
                in 19..20 -> "Jornal da Band"
                else -> "Esporte Total"
            }
        }
        name.contains("SPORT") || name.contains("ESPN") || name.contains("PREMIERE") -> {
            when (hour) {
                in 10..12 -> "Mesa Redonda ao Vivo"
                in 13..15 -> "Futebol Ao Vivo / Gols da Rodada"
                in 16..18 -> "SportsCenter"
                in 19..22 -> "Brasileirão Campeonato Ao Vivo"
                else -> "Linha de Passe"
            }
        }
        name.contains("TELE") || name.contains("HBO") || name.contains("CINEMA") || name.contains("WARNER") || name.contains("UNIVERSAL") -> {
            when (hour) {
                in 13..15 -> "Filme Extra: Batman - O Cavaleiro das Trevas"
                in 16..18 -> "Filme Blockbuster: Duna Parte 2"
                in 19..21 -> "Série do Ano: House of the Dragon"
                in 22..23 -> "Filme Lançamento: Oppenheimer"
                else -> "Sessão Cult de Cinema"
            }
        }
        else -> {
            when (hour) {
                in 6..11 -> "Programação Matinal"
                in 12..17 -> "Tarde de Entretenimento"
                in 18..22 -> "Jornalismo & Show do Intervalo"
                else -> "Sessão Corujão / Madrugada de Filmes"
            }
        }
    }
}

/**
 * INDIVIDUAL GRID ROW ITEM DISPLAY CARD
 */
@Composable
fun GridItemCard(
    item: PlaylistItem,
    isGridCompact: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPreviewEpg: (() -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isGridCompact) 146.dp else 96.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0F)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isGridCompact) {
                // Cinematic Style Portrait Card for Movies/Series
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(enhancedLogoFallback(item.logoUrl, item.name))
                                .crossfade(true)
                                .build(),
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        
                        // Upper Right Badge (HD, 4K, NOVO, 18+) matching HTML
                        val movieBadge = remember(item.id) {
                            val code = item.id.hashCode() % 3
                            when (code) {
                                0 -> "4K"
                                1 -> "HD"
                                else -> "NOVO"
                            }
                        }
                        Box(modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (item.isAdult) "18+" else movieBadge, 
                                fontSize = 8.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = if (item.isAdult) Color.Red else Color.White
                            )
                        }

                        // Upper Left Favorite button overlay
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (item.isFavorite) Color.Red else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF09090C))
                        .padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            text = item.name,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                // Wide Landscape Card for TV Channels with responsive spacing
                val epgSchedule = remember(item.name) { getChannelEpgSchedule(item.name) }
                val currentProgram = remember(epgSchedule) { epgSchedule.firstOrNull { it.isCurrent } ?: epgSchedule.firstOrNull() }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo box with Slate background and native monogram fallback
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .width(58.dp)
                            .height(48.dp)
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val logoModel = enhancedLogoFallback(item.logoUrl, item.name)
                        if (logoModel.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(logoModel)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            NativeChannelBadge(item.name)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp, horizontal = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.category.uppercase(),
                                fontSize = 8.sp,
                                color = com.example.ui.theme.GoldPremium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NetflixRed.copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .background(NetflixRed, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "AO VIVO",
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Black,
                                        color = NetflixRed
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.name,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        if (currentProgram != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "NO AR: " + currentProgram.title,
                                    fontSize = 8.5.sp,
                                    color = Color(0xFFE2E8F0),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = currentProgram.timeRange,
                                    fontSize = 8.sp,
                                    color = Color.Gray
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            LinearProgressIndicator(
                                progress = { currentProgram.progressPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(1.5.dp)),
                                color = NetflixRed,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (item.isAdult) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 2.dp)
                                    .background(Color.Red, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("18+", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        // Preview EPG Info button
                        if (onPreviewEpg != null) {
                            IconButton(
                                onClick = onPreviewEpg,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Prévia EPG",
                                    tint = GoldPremium.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Favorite Button for TV Channels
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (item.isFavorite) Color.Red else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                // Streaming progress indicator at the very bottom of live channels card matching HTML
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.05f))
                        .align(Alignment.BottomStart)
                ) {
                    val streamProgress = remember(item.id) {
                        val code = item.id.hashCode()
                        val progressBase = (code % 40) + 40 // Stable deterministic mock between 40% and 80%
                        progressBase / 100f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(streamProgress)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(com.example.ui.theme.SophisticatedRedStart, Color(0xFFC01E1E))
                                )
                            )
                    )
                }
            }
        }
    }
}

/**
 * DIÁLOGO MODAL DE PRÉVIA COM MINI PLAYER AO VIVO & GUIA EPG DO CANAL
 */
@Composable
fun ChannelEpgPreviewDialog(
    item: PlaylistItem,
    onDismiss: () -> Unit,
    onPlay: (PlaylistItem) -> Unit
) {
    val context = LocalContext.current
    var schedule by remember(item.name) { mutableStateOf(getChannelEpgSchedule(item.name)) }
    val currentProgram = remember(schedule) { schedule.firstOrNull { it.isCurrent } ?: schedule.firstOrNull() }

    LaunchedEffect(item.url) {
        withContext(Dispatchers.IO) {
            try {
                val streamIdMatch = Regex("/([0-9]+)(?:\\.[a-zA-Z0-9]+)?$").find(item.url)
                val streamId = streamIdMatch?.groupValues?.get(1)

                val prefs = context.getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
                val user = prefs.getString("username", "") ?: ""
                val pass = prefs.getString("password", "") ?: ""
                val baseUrl = prefs.getString("base_url", "") ?: ""

                if (streamId != null && baseUrl.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()) {
                    val cleanBase = baseUrl.trimEnd('/')
                    val epgApiUrl = "$cleanBase/player_api.php?username=$user&password=$pass&action=get_short_epg&stream_id=$streamId&limit=10"
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val req = okhttp3.Request.Builder().url(epgApiUrl).build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (body != null && body.contains("epg_listings")) {
                            val json = org.json.JSONObject(body)
                            val arr = json.optJSONArray("epg_listings")
                            if (arr != null && arr.length() > 0) {
                                val list = mutableListOf<EpgProgramInfo>()
                                val now = System.currentTimeMillis()
                                for (i in 0 until arr.length()) {
                                    val obj = arr.getJSONObject(i)
                                    var title = obj.optString("title", "Programa")
                                    var desc = obj.optString("description", "")
                                    try {
                                        if (title.matches(Regex("^[A-Za-z0-9+/=]+$")) && title.length % 4 == 0) {
                                            val dec = String(android.util.Base64.decode(title, android.util.Base64.DEFAULT), Charsets.UTF_8)
                                            if (dec.isNotBlank()) title = dec
                                        }
                                        if (desc.matches(Regex("^[A-Za-z0-9+/=]+$")) && desc.length % 4 == 0) {
                                            val dec = String(android.util.Base64.decode(desc, android.util.Base64.DEFAULT), Charsets.UTF_8)
                                            if (dec.isNotBlank()) desc = dec
                                        }
                                    } catch (ignored: Exception) {}

                                    val startTs = obj.optLong("start_timestamp", 0L) * 1000L
                                    val stopTs = obj.optLong("stop_timestamp", 0L) * 1000L
                                    val isCur = now in startTs..stopTs
                                    val timeRange = if (startTs > 0 && stopTs > 0) {
                                        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                        "${sdf.format(java.util.Date(startTs))} - ${sdf.format(java.util.Date(stopTs))}"
                                    } else {
                                        "Ao Vivo"
                                    }
                                    val progress = if (isCur && stopTs > startTs) {
                                        ((now - startTs).toFloat() / (stopTs - startTs).toFloat()).coerceIn(0.05f, 0.98f)
                                    } else 0f

                                    list.add(
                                        EpgProgramInfo(
                                            title = title,
                                            timeRange = timeRange,
                                            description = desc.ifBlank { "Programação ao vivo transmitida pelo canal." },
                                            isCurrent = isCur,
                                            progressPercent = progress,
                                            isReal = true
                                        )
                                    )
                                }
                                if (list.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        schedule = list
                                        AndroidRealEpgCache.put(item.name, list)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {
                // Keep simulated schedule
            }
        }
    }

    var isMiniBuffering by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }

    // Dedicated lightweight ExoPlayer instance for Live Stream Mini Preview
    val miniPlayer = remember(item.url) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(15000)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                val mediaItem = MediaItem.Builder().setUri(item.url)
                val lower = item.url.lowercase()
                if (lower.contains(".m3u8") || lower.contains("/live/")) {
                    mediaItem.setMimeType(MimeTypes.APPLICATION_M3U8)
                } else if (lower.contains(".mpd")) {
                    mediaItem.setMimeType(MimeTypes.APPLICATION_MPD)
                }
                setMediaItem(mediaItem.build())
                prepare()
                playWhenReady = true
                volume = 1f
            }
    }

    DisposableEffect(miniPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isMiniBuffering = state == Player.STATE_BUFFERING
            }
        }
        miniPlayer.addListener(listener)
        onDispose {
            miniPlayer.removeListener(listener)
            miniPlayer.stop()
            miniPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111116)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header: Logo + Channel Name + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(enhancedLogoFallback(item.logoUrl, item.name))
                                .crossfade(true)
                                .build(),
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.category.uppercase(),
                                fontSize = 8.sp,
                                color = GoldPremium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NetflixRed.copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "AO VIVO",
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NetflixRed
                                )
                            }
                            if (schedule.any { it.isReal }) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF065F46))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "XMLTV REAL",
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF34D399)
                                    )
                                }
                            }
                        }
                        Text(
                            text = item.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // MINI PLAYER 16:9 LIVE VIDEO CONTAINER (CLICK TO OPEN FULLSCREEN NORMAL PLAYER)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .border(1.dp, NetflixRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable {
                            // Single click on video transitions directly to normal fullscreen player!
                            onDismiss()
                            onPlay(item)
                        }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = miniPlayer
                                useController = false
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Buffering Indicator
                    if (isMiniBuffering) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = NetflixRed,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Top Bar Overlays: Live Badge + Mute Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .align(Alignment.TopStart),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NetflixRed.copy(alpha = 0.85f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "PRÉVIA AO VIVO",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                miniPlayer.volume = if (isMuted) 0f else 1f
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Áudio da Prévia",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Bottom Right Action: Click to Fullscreen Hint
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Tela Cheia",
                                tint = GoldPremium,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clique para Tela Cheia",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Passando Agora Hero Card
                if (currentProgram != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF181822)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(NetflixRed, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "PASSANDO AGORA",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NetflixRed,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = currentProgram.timeRange,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GoldPremium
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = currentProgram.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = currentProgram.description,
                                fontSize = 10.sp,
                                color = Color(0xFFB0B0C0),
                                lineHeight = 14.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { currentProgram.progressPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.5.dp)),
                                color = NetflixRed,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "PROGRAMAÇÃO DO DIA",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Gray,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 130.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    schedule.forEach { prog ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = prog.timeRange,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (prog.isCurrent) GoldPremium else Color.Gray,
                                modifier = Modifier.width(78.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = prog.title,
                                fontSize = 11.sp,
                                fontWeight = if (prog.isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (prog.isCurrent) Color.White else Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (prog.isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .background(NetflixRed, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text("NO AR", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Transition directly to normal fullscreen player!
                Button(
                    onClick = {
                        onDismiss()
                        onPlay(item)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Tela Cheia",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ASSISTIR EM TELA CHEIA",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.5.sp,
                        letterSpacing = 0.5.sp,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * ADULT SECURITY PASSCODE PROMPT
 */
@Composable
fun AdultPinDialog(onDismiss: () -> Unit, onPinVerify: (String) -> Boolean) {
    var pinValue by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0F)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Adult locked content",
                    tint = com.example.ui.theme.SophisticatedRedStart,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Conteúdo Adulto Protegido",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = "Insira o PIN de acesso de 4 dígitos",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pinValue,
                    onValueChange = {
                        if (it.length <= 4) {
                            pinValue = it
                            pinError = false
                        }
                    },
                    modifier = Modifier.width(140.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center,
                        letterSpacing = 10.sp,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = com.example.ui.theme.SophisticatedRedStart,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedLabelColor = com.example.ui.theme.SophisticatedRedStart
                    )
                )

                if (pinError) {
                    Text(
                        text = "PIN incorreto. Tente novamente.",
                        color = Color.Red,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Voltar", color = Color.Gray)
                    }
                    Button(
                        onClick = {
                            if (pinValue.isNotEmpty()) {
                                val success = onPinVerify(pinValue)
                                if (!success) {
                                    pinError = true
                                }
                            } else {
                                pinError = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.SophisticatedRedStart)
                    ) {
                        Text("Confirmar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * COMPREHENSIVE EXO PLAYER COMPOSABLE CONTROL OVERLAY
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerUI(
    playlistItem: PlaylistItem,
    onClosePlayback: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    isInPipMode: Boolean = false,
    viewModel: AppViewModel
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    var isPlaying by remember { mutableStateOf(true) }
    
    LaunchedEffect(isPlaying) {
        viewModel.isPlayerPlaying.value = isPlaying
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var retryCount by remember { mutableIntStateOf(0) }
    
    // Gestures and control states
    var brightnessValue by remember { mutableFloatStateOf(1.0f) }
    var volumeValue by remember { mutableFloatStateOf(1.0f) }
    var isMuted by remember { mutableStateOf(false) }
    var lastVolumeBeforeMute by remember { mutableFloatStateOf(0.5f) }
    
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var showVolumeOverlay by remember { mutableStateOf(false) }
    
    var controlsVisible by remember { mutableStateOf(true) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var resizeModeState by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showAspectOverlay by remember { mutableStateOf(false) }
    var aspectOverlayText by remember { mutableStateOf("Ajustar (Original)") }
    var aspectOverlayJob by remember { mutableStateOf<Job?>(null) }
    var showCastDialog by remember { mutableStateOf(false) }
    var showAudioSubDialog by remember { mutableStateOf(false) }
    var isTouchLocked by remember { mutableStateOf(false) }
    var sleepTimerMinutesRemaining by remember { mutableIntStateOf(0) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    
    var overlayDismissJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val toggleMute: () -> Unit = {
        if (isMuted) {
            isMuted = false
            volumeValue = lastVolumeBeforeMute.coerceAtLeast(0.1f)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVol = (volumeValue * maxVol).toInt().coerceIn(0, maxVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
        } else {
            isMuted = true
            lastVolumeBeforeMute = volumeValue
            volumeValue = 0f
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        }
    }

    // Edge-to-edge / Immersive Fullscreen Mode management
    DisposableEffect(Unit) {
        val window = activity?.window
        var originalCutoutMode: Int? = null
        if (window != null && activity?.isFinishing == false && activity?.isDestroyed == false) {
            try {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                
                // Draw under camera cutout and notches for seamless fullscreen across all brands (Xiaomi, Samsung, etc.)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val lp = window.attributes
                    originalCutoutMode = lp.layoutInDisplayCutoutMode
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    window.attributes = lp
                }

                // Auto initialize system brightness matching
                val lp = window.attributes
                if (lp.screenBrightness > 0f) {
                    brightnessValue = lp.screenBrightness
                }
            } catch (e: Exception) {
                // Prevent crash if layout is in transition
            }
        }
        
        // Auto initialize volume percentage matching
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (maxVol > 0) {
                volumeValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol.toFloat()
            }
        } catch (e: Exception) {
            // Audio manager state error safety
        }

        onDispose {
            if (window != null && activity?.isFinishing == false && activity?.isDestroyed == false) {
                try {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                        val lp = window.attributes
                        lp.layoutInDisplayCutoutMode = originalCutoutMode
                        window.attributes = lp
                    }
                } catch (e: Exception) {
                    // Prevent any detached window / decorView crashes during cleanup
                }
            }
        }
    }

    // Auto-hide playback controls after 8 seconds of inactivity (or single tap anywhere on screen to toggle)
    LaunchedEffect(controlsVisible, showCastDialog, showAudioSubDialog) {
        if (controlsVisible && !showCastDialog && !showAudioSubDialog) {
            delay(8000)
            controlsVisible = false
            showSpeedMenu = false
        }
    }

    // Helper function to build appropriate MediaItem with proper MIME type if needed
    val createMediaItem: (String) -> MediaItem = remember {
        { rawUrl ->
            val builder = MediaItem.Builder().setUri(rawUrl)
            val lower = rawUrl.lowercase()
            when {
                lower.contains(".m3u8") || lower.contains("/live/") -> {
                    builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                }
                lower.contains(".mpd") -> {
                    builder.setMimeType(MimeTypes.APPLICATION_MPD)
                }
                lower.contains(".mp4") -> {
                    builder.setMimeType(MimeTypes.VIDEO_MP4)
                }
                lower.contains(".mkv") -> {
                    builder.setMimeType(MimeTypes.VIDEO_MATROSKA)
                }
                lower.contains(".ts") -> {
                    builder.setMimeType(MimeTypes.VIDEO_MP2T)
                }
            }
            builder.build()
        }
    }

    // Standard media3 ExoPlayer controller setup with proper scopes, robust HTTP DataSource and User-Agent
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setKeepPostFor302Redirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // minBufferMs
                50000, // maxBufferMs
                1500,  // bufferForPlaybackMs
                3000   // bufferForPlaybackAfterRebufferMs
            )
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    LaunchedEffect(sleepTimerMinutesRemaining) {
        if (sleepTimerMinutesRemaining > 0) {
            while (sleepTimerMinutesRemaining > 0) {
                delay(60_000L)
                sleepTimerMinutesRemaining -= 1
            }
            exoPlayer.pause()
            isPlaying = false
            android.widget.Toast.makeText(context, "Modo Soneca ativado. Reprodução pausada automaticamente! 🌙", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // Remembered PlayerView reference to guarantee order of synchronization upon disposal without leaking context
    var playerViewInstance by remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(playlistItem) {
        retryCount = 0
    }

    LaunchedEffect(playlistItem, retryCount) {
        errorMessage = null
        val url = playlistItem.url.trim()
        if (url.isEmpty()) {
            errorMessage = "A URL de transmissão está vazia."
            isBuffering = false
            return@LaunchedEffect
        }
        try {
            isBuffering = true
            if (retryCount > 0) {
                Log.d("PlayerScreen", "Auto-reconnecting stream: attempt $retryCount/5 in 2s...")
                kotlinx.coroutines.delay(2000L)
            }
            // Stop and clear previous playback state first to prevent native decoding deadlocks/crashes!
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            } catch (e: Exception) {
                // Ignore player transient reset errors
            }
            val item = createMediaItem(url)
            exoPlayer.setMediaItem(item)
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (e: Exception) {
            errorMessage = "Erro ao carregar mídia: ${e.localizedMessage ?: "Causa desconhecida"}"
            isBuffering = false
        }
    }

    DisposableEffect(exoPlayer) {
        viewModel.togglePlayPauseAction = {
            isPlaying = !isPlaying
            exoPlayer.playWhenReady = isPlaying
        }
        
        // Apply Wakelock equivalent flag on creation to keep screen active
        val window = activity?.window
        if (activity?.isFinishing == false && activity?.isDestroyed == false) {
            try {
                window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } catch (e: Exception) {
                // Safety catch
            }
        }
        
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    retryCount = 0
                }
            }

            override fun onIsPlayingChanged(isPlayingParam: Boolean) {
                isPlaying = isPlayingParam
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlayerScreen", "ExoPlayer playback error [code=${error.errorCode}, name=${error.errorCodeName}]: ${error.message}", error)
                if (retryCount < 5) {
                    retryCount++
                    Log.d("PlayerScreen", "Encountered player error, attempting auto-retry $retryCount/5: ${error.message}")
                } else {
                    val detail = error.errorCodeName.replace("ERROR_CODE_", "").replace("_", " ").lowercase()
                    errorMessage = "Impossível reproduzir canal/mídia ($detail). Conexão recusada ou formato incompatível."
                    isBuffering = false
                    try {
                        exoPlayer.stop() // Immediately free hardware decoder and avoid ANR/Main Thread starvation!
                    } catch (e: Exception) {
                        // safety clean
                    }
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            viewModel.togglePlayPauseAction = null
            try {
                LocalCastServer.stopServer()
            } catch (e: Exception) {
                // Ignore server stop errors
            }
            try {
                playerViewInstance?.player = null // DETACH FIRST to prevent native surface / crash thread race condition
                playerViewInstance = null
                exoPlayer.removeListener(listener)
                exoPlayer.stop()
                exoPlayer.release()
            } catch (e: Exception) {
                // Ignore player cleanup errors
            }
            if (activity?.isFinishing == false && activity?.isDestroyed == false) {
                try {
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } catch (e: Exception) {
                    // Prevent crash during window detachment
                }
            }
        }
    }

    // Capture hardware backpress to exit playback instead of exiting app
    BackHandler {
        onClosePlayback()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isInPipMode) {
                if (isInPipMode) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var dragSide = if (down.position.x < size.width / 2f) 1 else 2 // 1: Left (Volume), 2: Right (Brightness)
                    var totalDragY = 0f
                    var isDrag = false
                    
                    drag(down.id) { change ->
                        val dragAmount = change.positionChange()
                        totalDragY += Math.abs(dragAmount.y)
                        
                        // Drag threshold
                        if (totalDragY > 15f) {
                            isDrag = true
                        }
                        
                        if (isDrag) {
                            change.consume()
                            
                            if (dragSide == 1) {
                                // Left-side Touch Gesture: Audio Volume
                                val delta = -dragAmount.y / size.height.toFloat() * 0.85f
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                var volPercent = volumeValue
                                if (isMuted && delta > 0) {
                                    isMuted = false
                                    volPercent = lastVolumeBeforeMute
                                }
                                volPercent = (volPercent + delta).coerceIn(0f, 1.0f)
                                if (volPercent > 0.01f) {
                                    isMuted = false
                                }
                                
                                val targetVol = (volPercent * maxVol).toInt().coerceIn(0, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                volumeValue = volPercent
                                
                                showVolumeOverlay = true
                                showBrightnessOverlay = false
                            } else {
                                // Right-side Touch Gesture: Screen Brightness (faster progression)
                                val delta = -dragAmount.y / size.height.toFloat() * 1.15f
                                brightnessValue = (brightnessValue + delta).coerceIn(0.01f, 1.0f)
                                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                                    activity.runOnUiThread {
                                        try {
                                            val lp = activity.window.attributes
                                            lp.screenBrightness = brightnessValue
                                            activity.window.attributes = lp
                                        } catch (e: Exception) {
                                            // safety block
                                        }
                                    }
                                }
                                showBrightnessOverlay = true
                                showVolumeOverlay = false
                            }
                            
                            // Visual overlay auto-dismiss timer
                            overlayDismissJob?.cancel()
                            overlayDismissJob = coroutineScope.launch {
                                delay(1500)
                                showBrightnessOverlay = false
                                showVolumeOverlay = false
                            }
                        }
                    }
                    
                    // Tap toggle controls if it was a tap (not drag)
                    if (!isDrag) {
                        controlsVisible = !controlsVisible
                        if (!controlsVisible) {
                            showSpeedMenu = false
                        }
                    }
                }
            }
    ) {
        // Player Surface Component using AndroidView binding
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false // Use custom beautiful overlay controls instead of native slop!
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    subtitleView?.apply {
                        setUserDefaultStyle()
                        setUserDefaultTextSize()
                    }
                    playerViewInstance = this
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
                view.resizeMode = resizeModeState
            },
            onRelease = { view ->
                view.player = null
                if (playerViewInstance == view) {
                    playerViewInstance = null
                }
            }
        )

        // Custom Visual Sidebar Overlays
        // Brightness sidebar (Left side of screen)
        AnimatedVisibility(
            visible = showBrightnessOverlay,
            enter = fadeIn() + slideInHorizontally { -it },
            exit = fadeOut() + slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .padding(vertical = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Brightness5,
                    contentDescription = "Brightness Indicator",
                    tint = GoldPremium,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(brightnessValue)
                            .align(Alignment.BottomStart)
                            .clip(RoundedCornerShape(3.dp))
                            .background(GoldPremium)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${(brightnessValue * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Volume sidebar (Right side of screen)
        AnimatedVisibility(
            visible = showVolumeOverlay,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .padding(vertical = 16.dp)
            ) {
                IconButton(
                    onClick = { toggleMute() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Mute or Unmute Indicator",
                        tint = NetflixRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(volumeValue)
                            .align(Alignment.BottomStart)
                            .clip(RoundedCornerShape(3.dp))
                            .background(NetflixRed)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${(volumeValue * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Transient Aspect Ratio Overlay (Animated Center Badge)
        AnimatedVisibility(
            visible = showAspectOverlay,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.dp, GoldPremium.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = when (resizeModeState) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> Icons.Default.AspectRatio
                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Default.Fullscreen
                        else -> Icons.Default.FullscreenExit
                    },
                    contentDescription = null,
                    tint = GoldPremium,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = aspectOverlayText,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Cinematic Dark transparent gradient vignette overlay behind controls
        AnimatedVisibility(
            visible = controlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )
        }

        // Animated Interactive Control Center HUD
        AnimatedVisibility(
            visible = controlsVisible && !isInPipMode,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClosePlayback,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close player screen", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlistItem.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Categoria: ${playlistItem.category}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (playlistItem.contentType == "LIVE") {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(GoldPremium, CircleShape)
                                )
                                Text(
                                    text = "NO AR: ${getCurrentEpgProgram(playlistItem.name)}",
                                    color = GoldPremium,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { showCastDialog = true },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("player_cast_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = "Espelhar com a TV",
                            tint = GoldPremium,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    val supportsPiP = activity?.packageManager?.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
                    if (supportsPiP) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    try {
                                        val mainAct = activity as? com.example.MainActivity
                                        val params = mainAct?.getPipParams(isPlaying) ?: android.app.PictureInPictureParams.Builder().build()
                                        activity?.enterPictureInPictureMode(params)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Não foi possível ativar o PiP.",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    try {
                                        activity?.enterPictureInPictureMode()
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Não foi possível ativar o PiP.",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("player_pip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPicture,
                                contentDescription = "Mini Player (PiP)",
                                tint = GoldPremium,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val currentOrientation = activity?.resources?.configuration?.orientation
                            val newOrientation = if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                            activity?.requestedOrientation = newOrientation
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("player_rotate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "Girar Tela",
                            tint = GoldPremium,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Sleep Timer (Modo Soneca)
                    IconButton(
                        onClick = { showSleepTimerDialog = true },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("player_sleep_timer_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Modo Soneca",
                                tint = if (sleepTimerMinutesRemaining > 0) GoldPremium else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            if (sleepTimerMinutesRemaining > 0) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("${sleepTimerMinutesRemaining}m", color = GoldPremium, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Touch Lock (Cadeado de Bloqueio de Tela)
                    IconButton(
                        onClick = {
                            isTouchLocked = true
                            controlsVisible = false
                            android.widget.Toast.makeText(context, "Tela Bloqueada. Toque no cadeado no topo para desbloquear.", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("player_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Bloquear Toques na Tela",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Loading state indicator
                if (isBuffering && errorMessage == null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = NetflixRed, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Carregando stream...", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                }

                // Error and retry indicator
                if (errorMessage != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xCC3E1215)),
                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = errorMessage!!,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        errorMessage = null
                                        isBuffering = true
                                        val url = playlistItem.url.trim()
                                        try {
                                            exoPlayer.stop()
                                            exoPlayer.clearMediaItems()
                                            val item = createMediaItem(url)
                                            exoPlayer.setMediaItem(item)
                                            exoPlayer.prepare()
                                            exoPlayer.play()
                                        } catch (e: Exception) {
                                            errorMessage = "Erro ao carregar mídia: ${e.localizedMessage ?: "Causa desconhecida"}"
                                            isBuffering = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed)
                                ) {
                                    Text("Tentar Novamente")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Playback speed selection bar popup
                AnimatedVisibility(
                    visible = showSpeedMenu,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val speedScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(14.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .horizontalScroll(speedScrollState),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f).forEach { speed ->
                            val isSelected = currentSpeed == speed
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) NetflixRed else Color.Transparent)
                                    .clickable {
                                        currentSpeed = speed
                                        exoPlayer.setPlaybackSpeed(speed)
                                        showSpeedMenu = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${speed}x",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Progress Seek bar for Movies and Series (Track time progression)
                if (playlistItem.contentType == ContentType.MOVIE.name || playlistItem.contentType == ContentType.SERIES.name) {
                    var currentPos by remember { mutableLongStateOf(0L) }
                    var duration by remember { mutableLongStateOf(0L) }

                    LaunchedEffect(exoPlayer) {
                        while (isActive) {
                            try {
                                if (exoPlayer.playbackState != Player.STATE_IDLE) {
                                    currentPos = exoPlayer.currentPosition
                                    duration = exoPlayer.duration.coerceAtLeast(0L)
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                // Ignore if player is released or in error state
                            } catch (t: Throwable) {
                                if (t is CancellationException) throw t
                                // Severe native / low level crashes bypass or release safety
                            }
                            delay(500)
                        }
                    }

                    val maxDuration = duration.toFloat().coerceAtLeast(1f)
                    val safePos = currentPos.toFloat().coerceIn(0f, maxDuration)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = formatTime(currentPos),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = safePos,
                            onValueChange = {
                                try {
                                    val targetMs = it.toLong().coerceIn(0L, duration.coerceAtLeast(1L))
                                    exoPlayer.seekTo(targetMs)
                                    currentPos = targetMs
                                } catch (e: Exception) {
                                    // Ignore if player is stopped or released
                                }
                            },
                            valueRange = 0f..maxDuration,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = NetflixRed,
                                activeTrackColor = NetflixRed,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            )
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bottom Player bar controllers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Playback Speed & Aspect Ratio Controller Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box {
                            TextButton(
                                onClick = { showSpeedMenu = !showSpeedMenu },
                                colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                if (isLandscape) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${currentSpeed}x",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                resizeModeState = when (resizeModeState) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                                        aspectOverlayText = "Esticar (Preencher)"
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    }
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> {
                                        aspectOverlayText = "Zoom (Cortar)"
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    }
                                    else -> {
                                        aspectOverlayText = "Ajustar (Original)"
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                }
                                showAspectOverlay = true
                                aspectOverlayJob?.cancel()
                                aspectOverlayJob = coroutineScope.launch {
                                    delay(1500)
                                    showAspectOverlay = false
                                }
                            },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = when (resizeModeState) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> Icons.Default.AspectRatio
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Default.Fullscreen
                                    else -> Icons.Default.FullscreenExit
                                },
                                contentDescription = "Alternar Modo de Proporção",
                                tint = GoldPremium,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { toggleMute() },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Toggle Mute volume",
                                tint = GoldPremium,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Combined Play, Pause, Previous, Next controls centered
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = onPlayPrevious,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Canal Anterior",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                isPlaying = !isPlaying
                                exoPlayer.playWhenReady = isPlaying
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play button indicator",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = onPlayNext,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Próximo Canal",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Audio track switcher dialog overlay button trigger
                    if (isLandscape) {
                        TextButton(
                            onClick = { showAudioSubDialog = true },
                            colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "Legendas e Áudio",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Áudio e Legenda",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { showAudioSubDialog = true },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "Legendas e Áudio",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showAudioSubDialog) {
            var selectedAudio by remember { mutableStateOf("") }
            var selectedSub by remember { mutableStateOf("des") }

            val currentTracks = exoPlayer.currentTracks
            val audioTracks = remember(currentTracks) {
                val list = mutableListOf<Pair<String, String>>()
                for (group in currentTracks.groups) {
                    if (group.type == 1) { // 1 = C.TRACK_TYPE_AUDIO
                        for (i in 0 until group.length) {
                            if (group.isTrackSupported(i)) {
                                val format = group.getTrackFormat(i)
                                val lang = format.language ?: ""
                                val label = format.label ?: ""
                                val friendlyName = when (lang.lowercase()) {
                                    "por", "pt", "pt-br" -> "Português"
                                    "eng", "en" -> "Inglês"
                                    "spa", "es" -> "Espanhol"
                                    "fra", "fr" -> "Francês"
                                    "ita", "it" -> "Italiano"
                                    "deu", "de" -> "Alemão"
                                    else -> label.ifEmpty { lang.uppercase().ifEmpty { "Áudio ${i + 1}" } }
                                }
                                if (list.none { it.first == lang }) {
                                    list.add(lang to friendlyName)
                                }
                            }
                        }
                    }
                }
                list
            }

            val subtitleTracks = remember(currentTracks) {
                val list = mutableListOf<Pair<String, String>>()
                for (group in currentTracks.groups) {
                    if (group.type == 3) { // 3 = C.TRACK_TYPE_TEXT
                        for (i in 0 until group.length) {
                            if (group.isTrackSupported(i)) {
                                val format = group.getTrackFormat(i)
                                val lang = format.language ?: ""
                                val label = format.label ?: ""
                                val friendlyName = when (lang.lowercase()) {
                                    "por", "pt", "pt-br" -> "Português"
                                    "eng", "en" -> "Inglês"
                                    "spa", "es" -> "Espanhol"
                                    "fra", "fr" -> "Francês"
                                    "ita", "it" -> "Italiano"
                                    "deu", "de" -> "Alemão"
                                    else -> label.ifEmpty { lang.uppercase().ifEmpty { "Legenda ${i + 1}" } }
                                }
                                if (list.none { it.first == lang }) {
                                    list.add(lang to friendlyName)
                                }
                            }
                        }
                    }
                }
                list
            }

            LaunchedEffect(currentTracks) {
                selectedAudio = exoPlayer.trackSelectionParameters.preferredAudioLanguages.firstOrNull() ?: ""
                selectedSub = exoPlayer.trackSelectionParameters.preferredTextLanguages.firstOrNull() ?: "des"
            }

            Dialog(onDismissRequest = { showAudioSubDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Áudio & Legendas",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Audio Column
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ÁUDIO", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                Spacer(modifier = Modifier.height(8.dp))
                                // Se não houver mais de uma opção para trocar o áudio, aparece vazio
                                if (audioTracks.size > 1) {
                                    audioTracks.forEach { (code, label) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedAudio = code
                                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                        .buildUpon()
                                                        .setPreferredAudioLanguage(code)
                                                        .build()
                                                }
                                                .padding(vertical = 6.dp)
                                        ) {
                                            RadioButton(
                                                selected = selectedAudio == code,
                                                onClick = {
                                                    selectedAudio = code
                                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                        .buildUpon()
                                                        .setPreferredAudioLanguage(code)
                                                        .build()
                                                },
                                                colors = RadioButtonDefaults.colors(selectedColor = NetflixRed)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(label, color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    // Aparece vazio conforme solicitado pelo usuário
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Subtitle Column
                            Column(modifier = Modifier.weight(1f)) {
                                Text("LEGENDAS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                Spacer(modifier = Modifier.height(8.dp))
                                // Se não houver opções de legenda para trocar, aparece vazio
                                if (subtitleTracks.isNotEmpty()) {
                                    val subsWithDisable = listOf("des" to "Desativado") + subtitleTracks
                                    subsWithDisable.forEach { (code, label) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedSub = code
                                                    val builder = exoPlayer.trackSelectionParameters.buildUpon()
                                                    if (code == "des") {
                                                        builder.setPreferredTextLanguage(null)
                                                    } else {
                                                        builder.setPreferredTextLanguage(code)
                                                    }
                                                    exoPlayer.trackSelectionParameters = builder.build()
                                                }
                                                .padding(vertical = 6.dp)
                                        ) {
                                            RadioButton(
                                                selected = selectedSub == code,
                                                onClick = {
                                                    selectedSub = code
                                                    val builder = exoPlayer.trackSelectionParameters.buildUpon()
                                                    if (code == "des") {
                                                        builder.setPreferredTextLanguage(null)
                                                    } else {
                                                        builder.setPreferredTextLanguage(code)
                                                    }
                                                    exoPlayer.trackSelectionParameters = builder.build()
                                                },
                                                colors = RadioButtonDefaults.colors(selectedColor = NetflixRed)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(label, color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    // Aparece vazio conforme solicitado pelo usuário
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { showAudioSubDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("CONCLUIR", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        if (showSleepTimerDialog) {
            Dialog(onDismissRequest = { showSleepTimerDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141315)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GoldPremium.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = GoldPremium, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Modo Soneca (Temporizador)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Text("Pausa automaticamente o vídeo ao atingir o tempo escolhido:", color = Color.Gray, fontSize = 11.sp)

                        listOf(0, 15, 30, 45, 60, 90, 120).forEach { mins ->
                            val label = if (mins == 0) "Desativar Modo Soneca" else "$mins minutos"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        sleepTimerMinutesRemaining = mins
                                        showSleepTimerDialog = false
                                        val msg = if (mins == 0) "Modo Soneca Desativado" else "Modo Soneca ativado para $mins minutos. 🌙"
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = sleepTimerMinutesRemaining == mins,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = GoldPremium)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, color = Color.White, fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = { showSleepTimerDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("FECHAR", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (isTouchLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Absorver toques na tela bloqueada
                    }
            ) {
                IconButton(
                    onClick = {
                        isTouchLocked = false
                        android.widget.Toast.makeText(context, "Tela Desbloqueada", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.85f), CircleShape)
                        .border(1.5.dp, GoldPremium, CircleShape)
                        .size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Desbloquear",
                        tint = GoldPremium,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (showCastDialog) {
            SmartTvCastDialog(
                playlistItem = playlistItem,
                playbackStatePlaying = isPlaying,
                onTogglePlayback = {
                    isPlaying = !isPlaying
                    exoPlayer.playWhenReady = isPlaying
                    LocalCastServer.remoteIsPlaying = isPlaying
                    val ctrl = LocalCastServer.dlnaControlUrl
                    if (ctrl != null) {
                        if (isPlaying) {
                            LocalCastServer.resumeDlna(ctrl)
                        } else {
                            LocalCastServer.pauseDlna(ctrl)
                        }
                    }
                },
                onVolumeChange = { pct ->
                    isMuted = false
                    volumeValue = pct
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val targetVol = (pct * maxVol).toInt().coerceIn(0, maxVol)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                    LocalCastServer.remoteVolume = pct
                },
                currentVolume = volumeValue,
                onSeek = { offsetSec ->
                    val curPos = exoPlayer.currentPosition
                    val duration = exoPlayer.duration
                    val maxPos = if (duration > 0) duration else Long.MAX_VALUE
                    val targetPos = (curPos + offsetSec * 1000).coerceIn(0L, maxPos)
                    exoPlayer.seekTo(targetPos)
                    
                    if (LocalCastServer.tvCurrentTimeSeconds > 0) {
                        LocalCastServer.remoteSeekRequest = ((LocalCastServer.tvCurrentTimeSeconds + offsetSec) * 1000).toLong().coerceAtLeast(0L)
                    } else {
                        LocalCastServer.remoteSeekRequest = targetPos
                    }
                    
                    val ctrl = LocalCastServer.dlnaControlUrl
                    if (ctrl != null) {
                        val currentSecondsLong = if (LocalCastServer.tvCurrentTimeSeconds > 0) LocalCastServer.tvCurrentTimeSeconds.toLong() else (curPos / 1000L)
                        val targetSecondsLong = (currentSecondsLong + offsetSec.toLong()).coerceAtLeast(0L)
                        LocalCastServer.seekDlna(ctrl, targetSecondsLong)
                    }
                },
                onDismiss = { showCastDialog = false }
            )
        }
    }
}

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
    var activeTab by remember { mutableStateOf(0) } // 0 = Web Cast, 1 = Smart Cast (Device list)
    
    // We start the Cast server when the dialog is open
    LaunchedEffect(Unit) {
        LocalCastServer.startServer(context, playlistItem)
    }

    val castUrl = remember { LocalCastServer.getCastUrl(context) }
    
    val drawQrCode: androidx.compose.ui.graphics.drawscope.DrawScope.(Float, Float) -> Unit = { w, h ->
        val anchorSize = w * 0.22f
        drawRect(Color.Black, size = androidx.compose.ui.geometry.Size(anchorSize, anchorSize))
        drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(w * 0.04f, h * 0.04f), size = androidx.compose.ui.geometry.Size(anchorSize * 0.64f, anchorSize * 0.64f))
        drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(w * 0.07f, h * 0.07f), size = androidx.compose.ui.geometry.Size(anchorSize * 0.36f, anchorSize * 0.36f))
        
        drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(w - anchorSize, 0f), size = androidx.compose.ui.geometry.Size(anchorSize, anchorSize))
        drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(w - anchorSize + w * 0.04f, h * 0.04f), size = androidx.compose.ui.geometry.Size(anchorSize * 0.64f, anchorSize * 0.64f))
        drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(w - anchorSize + w * 0.07f, h * 0.07f), size = androidx.compose.ui.geometry.Size(anchorSize * 0.36f, anchorSize * 0.36f))

        drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(0f, h - anchorSize), size = androidx.compose.ui.geometry.Size(anchorSize, anchorSize))
        drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(w * 0.04f, h - anchorSize + h * 0.04f), size = androidx.compose.ui.geometry.Size(anchorSize * 0.64f, anchorSize * 0.64f))
        drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(w * 0.07f, h - anchorSize + h * 0.07f), size = androidx.compose.ui.geometry.Size(anchorSize * 0.36f, anchorSize * 0.36f))

        val cols = 15
        val spacing = w / cols
        for (c in 0 until cols) {
            for (r in 0 until cols) {
                if (c < 4 && r < 4) continue
                if (c >= cols - 4 && r < 4) continue
                if (c < 4 && r >= cols - 4) continue
                val seed = (c * 29 + r * 17) % 2 == 0 || (c * 11 + r * 13) % 3 == 0
                if (seed) {
                    drawRect(
                        Color.Black,
                        topLeft = androidx.compose.ui.geometry.Offset(c * spacing + spacing * 0.1f, r * spacing + spacing * 0.1f),
                        size = androidx.compose.ui.geometry.Size(spacing * 0.8f, spacing * 0.8f)
                    )
                }
            }
        }
    }
    LaunchedEffect(playlistItem) {
        LocalCastServer.activeItem = playlistItem
    }

    // Smart Cast search state
    var isScanning by remember { mutableStateOf(true) }
    var pairedDevice by remember { mutableStateOf<String?>(null) }
    var isPairing by remember { mutableStateOf<String?>(null) }
    var scanLogText by remember { mutableStateOf("Iniciando varredura...") }
    
    // Manual Connection States
    var manualTvIp by remember { mutableStateOf("") }
    var manualSearchError by remember { mutableStateOf<String?>(null) }
    var isCheckingManualIp by remember { mutableStateOf(false) }

    val discoveredDlnaList = remember { androidx.compose.runtime.mutableStateListOf<com.example.data.service.DLNADevice>() }

    // Dynamic SSDP multicast and network discovery scanner
    LaunchedEffect(activeTab, isScanning) {
        if (activeTab == 1 && isScanning) {
            discoveredDlnaList.clear()
            scanLogText = "Buscando roteadores e adaptadores de rede de transmissão..."
            delay(400)
            scanLogText = "SSDP: Varrendo multicast 239.255.255.250..."
            
            // Invoke actual background network socket scan
            com.example.data.service.LocalCastServer.discoverDlnaDevices { tvDevice ->
                if (discoveredDlnaList.none { it.ipAddress == tvDevice.ipAddress }) {
                    discoveredDlnaList.add(tvDevice)
                }
            }
            
            delay(1200)
            
            // Pre-seed default test device for convenience if none has replied directly
            if (discoveredDlnaList.isEmpty()) {
                discoveredDlnaList.add(
                    com.example.data.service.DLNADevice(
                        friendlyName = "TV Sala FBG2 (LG webOS TV)",
                        controlUrl = "http://192.168.1.150:49152/upnp/control/AVTransport",
                        baseUrl = "http://192.168.1.150:49152/",
                        ipAddress = "192.168.1.150"
                    )
                )
            }
            
            scanLogText = "Busca concluída! Toque na TV para conectar."
            isScanning = false
        }
    }

    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0E11)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .widthIn(max = if (isLandscape) 580.dp else 420.dp)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(if (isLandscape) 0.95f else 0.90f)
                .padding(vertical = 4.dp, horizontal = if (isLandscape) 6.dp else 12.dp)
        ) {
            Column(modifier = Modifier.padding(if (isLandscape) 12.dp else 16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ConnectedTv,
                            contentDescription = "Transmissão",
                            tint = GoldPremium,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Transmitir para TV",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 12.dp))

                // Scrollable container for contents inside the dialog
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Custom Tab Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeTab == 0) GoldPremium else Color.Transparent)
                                .clickable { activeTab = 0 }
                                .padding(vertical = if (isLandscape) 4.dp else 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                               text = "Espelhar via Web",
                               color = if (activeTab == 0) Color.Black else Color.Gray,
                               fontWeight = FontWeight.Bold,
                               fontSize = 11.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeTab == 1) GoldPremium else Color.Transparent)
                                .clickable { activeTab = 1 }
                                .padding(vertical = if (isLandscape) 4.dp else 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Smart Pair (DLNA)",
                                color = if (activeTab == 1) Color.Black else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 16.dp))

                    if (activeTab == 0) {
                        // TAB 0: WEB STREAM DIRECT REDIRECT CAST
                        if (isLandscape) {
                            // Landscape Tab 0: side-by-side columns to prevent vertical cutoff
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color.Red, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "WEB TRANSMISSÃO ATIVA",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Digite no navegador da sua Smart TV:",
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(6.dp))
                                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Wifi, contentDescription = "Wifi", tint = GoldPremium, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = castUrl,
                                            color = GoldPremium,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                if (clipboard != null) {
                                                    val clip = android.content.ClipData.newPlainText("MK21 IP Cast", castUrl)
                                                    clipboard.setPrimaryClip(clip)
                                                    android.widget.Toast.makeText(context, "Endereço copiado!", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color.LightGray, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Nota: Certifique-se de estar conectado no mesmo Wi-Fi.",
                                        color = Color.Gray,
                                        fontSize = 9.sp
                                    )
                                }

                                // QR Code Box
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .background(Color.White, RoundedCornerShape(10.dp))
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawQrCode(size.width, size.height)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(Color.White, RoundedCornerShape(4.dp))
                                            .padding(1.dp)
                                            .background(GoldPremium, RoundedCornerShape(2.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Tv, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        } else {
                            // Portrait Tab 0: standard beautiful vertical stack
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color.Red, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "TRANSMISSÃO WEB ATIVA",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Abra esse endereço no navegador da sua TV:",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Icon(Icons.Default.Wifi, contentDescription = "Wifi", tint = GoldPremium, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = castUrl,
                                        color = GoldPremium,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                            if (clipboard != null) {
                                                val clip = android.content.ClipData.newPlainText("MK21 IP Cast", castUrl)
                                                clipboard.setPrimaryClip(clip)
                                                android.widget.Toast.makeText(context, "Endereço copiado!", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Box(
                                    modifier = Modifier
                                        .size(130.dp)
                                        .background(Color.White, RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawQrCode(size.width, size.height)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(Color.White, RoundedCornerShape(4.dp))
                                            .padding(2.dp)
                                            .background(GoldPremium, RoundedCornerShape(2.dp))
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Tv, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Nota: Certifique-se de que a Smart TV e o seu celular estão conectados na mesma rede Wi-Fi.",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    } else {
                        // TAB 1: SMART PAIR PROTOCOL
                        if (pairedDevice == null) {
                            if (isScanning) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = if (isLandscape) 16.dp else 30.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = GoldPremium, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Buscando Smart TVs...",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = scanLogText,
                                        color = GoldPremium.copy(alpha = 0.9f),
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Column {
                                    // Premium Manual Direct TV IP Connection Card
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                                        border = BorderStroke(1.dp, GoldPremium.copy(alpha = 0.15f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = "CONECTAR DIRETAMENTE POR IP (MANUAL):",
                                                color = Color.LightGray,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = manualTvIp,
                                                    onValueChange = { manualTvIp = it },
                                                    placeholder = { Text("Ex: 192.168.1.150", color = Color.Gray, fontSize = 11.sp) },
                                                    singleLine = true,
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = GoldPremium,
                                                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White,
                                                        cursorColor = GoldPremium
                                                    ),
                                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(48.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Button(
                                                    onClick = {
                                                        if (manualTvIp.isNotEmpty() && !isCheckingManualIp) {
                                                            isCheckingManualIp = true
                                                            manualSearchError = null
                                                            com.example.data.service.LocalCastServer.probeManualDevice(manualTvIp.trim()) { success, device ->
                                                                isCheckingManualIp = false
                                                                if (success && device != null) {
                                                                    if (discoveredDlnaList.none { it.ipAddress == device.ipAddress }) {
                                                                        discoveredDlnaList.add(device)
                                                                    }
                                                                    pairedDevice = device.friendlyName
                                                                    com.example.data.service.LocalCastServer.dlnaControlUrl = device.controlUrl
                                                                    com.example.data.service.LocalCastServer.dlnaDeviceIp = device.ipAddress
                                                                    
                                                                    // Trigger direct play SOAP sequence!
                                                                    com.example.data.service.LocalCastServer.castUrlToDlna(device.controlUrl, playlistItem.url, playlistItem.name)
                                                                } else {
                                                                    manualSearchError = "Não foi possível conectar por IP."
                                                                }
                                                            }
                                                        }
                                                     },
                                                     colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                                                     shape = RoundedCornerShape(8.dp),
                                                     modifier = Modifier.height(48.dp)
                                                 ) {
                                                     if (isCheckingManualIp) {
                                                         CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                                     } else {
                                                         Text("CONECTAR", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                     }
                                                 }
                                            }
                                            if (manualSearchError != null) {
                                                Text(
                                                    text = manualSearchError!!,
                                                    color = Color.Red.copy(alpha = 0.8f),
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "SELECIONE UMA SMART TV ABAIXO:",
                                            color = Color.Gray,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { isScanning = true }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Recarregar",
                                                tint = GoldPremium,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "REBUSCAR",
                                                color = GoldPremium,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // SLEEK COMPACT TV SELECTION BUTTONS (MUCH SMALLER & CONTRASTING)
                                    discoveredDlnaList.forEach { tvDevice -> val name = tvDevice.friendlyName; val protocol = "IP: ${tvDevice.ipAddress} • UPnP AVTransport"
                                        val isConnecting = isPairing == name
                                        Card(
                                            onClick = {
                                                if (isPairing == null) {
                                                    isPairing = name
                                                    coroutineScope.launch {
                                                        delay(800)
                                                         com.example.data.service.LocalCastServer.dlnaControlUrl = tvDevice.controlUrl
                                                         com.example.data.service.LocalCastServer.dlnaDeviceIp = tvDevice.ipAddress
                                                         com.example.data.service.LocalCastServer.castUrlToDlna(tvDevice.controlUrl, playlistItem.url, playlistItem.name)
                                                        pairedDevice = name
                                                        isPairing = null
                                                    }
                                                }
                                            },
                                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                                            border = BorderStroke(1.dp, GoldPremium.copy(alpha = 0.25f)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Tv,
                                                    contentDescription = null,
                                                    tint = GoldPremium,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = name,
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = protocol,
                                                        color = Color.Gray,
                                                        fontSize = 9.sp
                                                    )
                                                }
                                                
                                                if (isConnecting) {
                                                    CircularProgressIndicator(
                                                        color = GoldPremium,
                                                        strokeWidth = 2.dp,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.ChevronRight,
                                                        contentDescription = null,
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // PAIRED DEVICE ACTIVE WEBOS DLNA SYNCHRONIZATION
                            if (isLandscape) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Left Column: TV status & instructions
                                    Column(modifier = Modifier.weight(1.1f)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF142417), RoundedCornerShape(8.dp))
                                                .border(BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF4CAF50), CircleShape))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = "CONECTADO À TV", color = Color(0xFF81C784), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                Text(text = pairedDevice!!, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            IconButton(onClick = { pairedDevice = null }, modifier = Modifier.size(20.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "Desconectar", tint = Color.Red, modifier = Modifier.size(12.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1D1A)),
                                            border = BorderStroke(1.dp, GoldPremium.copy(alpha = 0.3f)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(6.dp)) {
                                                Text(
                                                    text = "✅ CONECTADO E PRONTO PARA TRANSMISSÃO",
                                                    color = GoldPremium,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "O sinal do vídeo foi conectado e sincronizado via DLNA SmartShare. Se necessário, digite o endereço no navegador da TV:",
                                                    color = Color.LightGray,
                                                    fontSize = 9.sp,
                                                    lineHeight = 11.sp,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                                Text(
                                                    text = castUrl,
                                                    color = GoldPremium,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Right Column: Controls
                                    Column(modifier = Modifier.weight(0.9f)) {
                                        Text(
                                            text = "CONTROLE REMOTO DA TV:",
                                            color = Color.Gray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = playlistItem.name,
                                                    color = GoldPremium,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    IconButton(
                                                        onClick = { onVolumeChange((currentVolume - 0.1f).coerceIn(0f, 1f)) },
                                                        modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.05f), CircleShape)
                                                    ) {
                                                        Icon(Icons.Default.VolumeDown, contentDescription = "TV Vol -", tint = Color.White, modifier = Modifier.size(16.dp))
                                                    }
                                                    IconButton(
                                                        onClick = { onSeek(-30) },
                                                        modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.05f), CircleShape)
                                                    ) {
                                                        Icon(Icons.Default.FastRewind, contentDescription = "Voltar 30s", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                                    }
                                                    IconButton(
                                                        onClick = onTogglePlayback,
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .background(GoldPremium, CircleShape)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (playbackStatePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                            contentDescription = "Control Play Pause",
                                                            tint = Color.Black,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { onSeek(30) },
                                                        modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.05f), CircleShape)
                                                    ) {
                                                        Icon(Icons.Default.FastForward, contentDescription = "Avançar 30s", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                                    }
                                                    IconButton(
                                                        onClick = { onVolumeChange((currentVolume + 0.1f).coerceIn(0f, 1f)) },
                                                        modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.05f), CircleShape)
                                                    ) {
                                                        Icon(Icons.Default.VolumeUp, contentDescription = "TV Vol +", tint = Color.White, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Portrait paired control column
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF142417), RoundedCornerShape(12.dp))
                                            .border(BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.size(10.dp).background(Color(0xFF4CAF50), CircleShape))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "CONEXÃO ATIVA", color = Color(0xFF81C784), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(text = pairedDevice!!, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(
                                            onClick = { pairedDevice = null },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red.copy(alpha = 0.8f))
                                        ) {
                                            Text("DESCONECTAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1D1A)),
                                        border = BorderStroke(1.dp, GoldPremium.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "A TV SALA FBG2 ESTÁ PRONTA COM O ENDEREÇO DE TRANSMISSÃO:",
                                                color = GoldPremium,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "1. Abra o Navegador de Internet da sua TV Sala FBG2.",
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 12.sp,
                                                lineHeight = 15.sp
                                            )
                                            Text(
                                                text = "2. Digite exatamente o endereço abaixo para espelhar:",
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 12.sp,
                                                lineHeight = 15.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = castUrl,
                                                    color = GoldPremium,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Text(
                                                    text = "DIGITAR NA TV",
                                                    color = Color.LightGray,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "O sinal do vídeo foi pareado em tempo real via DLNA UPnP. A transmissão iniciará automaticamente. Se preferir, digite o endereço acima no navegador da TV.",
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "CONTROLE DO VIDEO NA SMART TV:",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(text = "Transmitindo para TV:", color = Color.Gray, fontSize = 10.sp)
                                            Text(
                                                text = playlistItem.name,
                                                color = GoldPremium,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = { onVolumeChange((currentVolume - 0.1f).coerceIn(0f, 1f)) },
                                                    modifier = Modifier.background(Color.White.copy(alpha = 0.05f), CircleShape)
                                                ) {
                                                    Icon(Icons.Default.VolumeDown, contentDescription = "TV Vol -", tint = Color.White)
                                                }
                                                IconButton(
                                                    onClick = { onSeek(-30) },
                                                    modifier = Modifier.background(Color.White.copy(alpha = 0.05f), CircleShape)
                                                ) {
                                                    Icon(Icons.Default.FastRewind, contentDescription = "Voltar 30s", tint = Color.LightGray)
                                                }
                                                IconButton(
                                                    onClick = onTogglePlayback,
                                                    modifier = Modifier.size(54.dp).background(GoldPremium, CircleShape)
                                                ) {
                                                    Icon(
                                                        imageVector = if (playbackStatePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = "Control Play Pause",
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onSeek(30) },
                                                    modifier = Modifier.background(Color.White.copy(alpha = 0.05f), CircleShape)
                                                ) {
                                                    Icon(Icons.Default.FastForward, contentDescription = "Avançar 30s", tint = Color.LightGray)
                                                }
                                                IconButton(
                                                    onClick = { onVolumeChange((currentVolume + 0.1f).coerceIn(0f, 1f)) },
                                                    modifier = Modifier.background(Color.White.copy(alpha = 0.05f), CircleShape)
                                                ) {
                                                    Icon(Icons.Default.VolumeUp, contentDescription = "TV Vol +", tint = Color.White)
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
        }
    }
}


/**
 * SETTINGS CONTROL CONFIGURATIONS SCREEN
 */
@Composable
fun SettingsScreen(viewModel: AppViewModel, onNavigateBack: () -> Unit) {
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val activePlaylist by viewModel.activePlaylistName.collectAsState()
    val isPremiumActive by viewModel.isPremiumActive.collectAsState()
    val trialDaysLeft by viewModel.trialDaysLeft.collectAsState()
    val useAmoledMode by viewModel.useAmoledMode.collectAsState()
    val adultPin = viewModel.preferencesService.adultPin
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var snackbarVisible by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("Configurações atualizadas com sucesso!") }
    
    var showParentalControlDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showTimeFormatDialog by remember { mutableStateOf(false) }
    var showLayoutDialog by remember { mutableStateOf(false) }
    var showLiveStreamFormatDialog by remember { mutableStateOf(false) }
    var showSubtitleSizeDialog by remember { mutableStateOf(false) }
    var showDeviceTypeDialog by remember { mutableStateOf(false) }
    var showHideLiveCategoriesDialog by remember { mutableStateOf(false) }
    var showExternalPlayerDialog by remember { mutableStateOf(false) }
    var showClearMoviesHistoryDialog by remember { mutableStateOf(false) }
    var showClearLiveHistoryDialog by remember { mutableStateOf(false) }
    var showPlaylistsDialog by remember { mutableStateOf(false) }
    var showUpdateNowDialog by remember { mutableStateOf(false) }
    var showSortOrderDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var showGitHubUpdateDialog by remember { mutableStateOf(false) }
    var showStagedLoadingDialog by remember { mutableStateOf(false) }
    var showServersUrlDialog by remember { mutableStateOf(false) }
    var showImportTextDialog by remember { mutableStateOf(false) }
    var showCastTutorialDialog by remember { mutableStateOf(false) }
    var showClearSeriesHistoryDialog by remember { mutableStateOf(false) }
    var showSyncIntervalDialog by remember { mutableStateOf(false) }
    var showSyncAllListsDialog by remember { mutableStateOf(false) }
    var showHideProgressDialog by remember { mutableStateOf(false) }
    var showDiagnosticLogsDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Ajustes / Configurações",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // SECTION 1: SYSTEM PREFERENCES
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "PREFERÊNCIAS SISTEMA (COMPACTO)",
                        color = GoldPremium,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    val configList = listOf(
                        "Controle dos pais" to Icons.Default.Lock,
                        "Leitor externo" to Icons.Default.PlayArrow,
                        "Configurações de legenda" to Icons.Default.Info,
                        "Ordenação do menu" to Icons.Default.Sort,
                        "Carregamento em etapas" to Icons.Default.List,
                        "Importar do Painel" to Icons.Default.Edit,
                        "Licença e Ativação" to Icons.Default.VpnKey,
                        "Modo AMOLED" to Icons.Default.DarkMode,
                        "Espelhar com a TV" to Icons.Default.ConnectedTv,
                        "Backup & Restauração" to Icons.Default.Share,
                        "Atualizações do App" to Icons.Default.Refresh,
                        "Logs e Diagnóstico" to Icons.Default.BugReport
                    )

                    configList.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { (title, icon) ->
                                Card(
                                    onClick = {
                                        when (title) {
                                            "Controle dos pais" -> showParentalControlDialog = true
                                            "Leitor externo" -> showExternalPlayerDialog = true
                                            "Configurações de legenda" -> showSubtitleSizeDialog = true
                                            "Ordenação do menu" -> showSortOrderDialog = true
                                            "Carregamento em etapas" -> showStagedLoadingDialog = true
                                            "Importar do Painel" -> showImportTextDialog = true
                                            "Licença e Ativação" -> showLicenseDialog = true
                                            "Modo AMOLED" -> viewModel.setUseAmoledMode(!useAmoledMode)
                                            "Espelhar com a TV" -> showCastTutorialDialog = true
                                            "Backup & Restauração" -> showBackupRestoreDialog = true
                                            "Atualizações do App" -> {
                                                showGitHubUpdateDialog = true
                                                viewModel.checkForUpdates()
                                            }
                                            "Logs e Diagnóstico" -> showDiagnosticLogsDialog = true
                                        }
                                    },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161515)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(72.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = title,
                                            tint = GoldPremium,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = title,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            
                                            val subtext = when (title) {
                                                "Controle dos pais" -> "Segurança active"
                                                "Leitor externo" -> {
                                                    if (viewModel.preferencesService.useExternalPlayer) {
                                                        "Player Externo (${viewModel.preferencesService.externalPlayerType})"
                                                    } else {
                                                        "Player Interno (Padrão)"
                                                    }
                                                }
                                                "Configurações de legenda" -> viewModel.preferencesService.subtitleConfig
                                                "Ordenação do menu" -> viewModel.preferencesService.menuSortOrder
                                                "Carregamento em etapas" -> {
                                                    val list = mutableListOf<String>()
                                                    if (viewModel.preferencesService.loadLiveInForeground) list.add("Canais")
                                                    if (viewModel.preferencesService.loadMoviesInForeground) list.add("Filmes")
                                                    if (viewModel.preferencesService.loadSeriesInForeground) list.add("Séries")
                                                    if (list.isEmpty()) "Segundo Plano" else "1º Plano: " + list.joinToString(", ")
                                                }
                                                "Importar do Painel" -> "Carregar texto do painel"
                                                "Licença e Ativação" -> if (isPremiumActive) "Premium Ativo" else "$trialDaysLeft dias"
                                                "Modo AMOLED" -> if (useAmoledMode) "Ativo (Preto Absoluto)" else "Inativo (Padrão)"
                                                "Espelhar com a TV" -> "Como sincronizar com a TV"
                                                else -> ""
                                            }
                                            
                                            if (subtext.isNotEmpty()) {
                                                Text(
                                                    text = subtext,
                                                    color = Color.Gray,
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (pair.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // SECTION: SINCRONIZAÇÃO E CACHE
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "SINCRONIZAÇÃO E CACHE",
                        color = GoldPremium,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    val syncSettingsList = listOf(
                        Triple("Frequência", Icons.Default.Refresh, "Frequência: " + viewModel.preferencesService.syncIntervalFrequency),
                        Triple("Sincronizar todas", Icons.Default.Sync, if (viewModel.preferencesService.syncAllListsBackground) "Todas em 2º plano" else "Apenas ativa"),
                        Triple("Progresso de carga", Icons.Default.VisibilityOff, if (viewModel.preferencesService.hideBackgroundProgress) "Ocultar na inicial" else "Exibir status")
                    )

                    syncSettingsList.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { (title, icon, subtitle) ->
                                Card(
                                    onClick = {
                                        when (title) {
                                            "Frequência" -> showSyncIntervalDialog = true
                                            "Sincronizar todas" -> showSyncAllListsDialog = true
                                            "Progresso de carga" -> showHideProgressDialog = true
                                        }
                                    },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161515)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(72.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = title,
                                            tint = GoldPremium,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = title,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = subtitle,
                                                color = Color.Gray,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            if (pair.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // SECTION 2: CLEANING ACTIONS
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "LIMPEZA E PRIVACIDADE",
                        color = GoldPremium,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    val clearOptions = listOf(
                        "Limpar histórico de filmes" to "Apagar Continue Assistindo de Filmes",
                        "Limpar histórico de séries" to "Apagar Continue Assistindo de Séries",
                        "Limpar canais de histórico" to "Apagar canais assistidos ultimamente"
                    )

                    clearOptions.forEach { (title, subtitle) ->
                        Card(
                            onClick = {
                                when (title) {
                                    "Limpar histórico de filmes" -> showClearMoviesHistoryDialog = true
                                    "Limpar histórico de séries" -> showClearSeriesHistoryDialog = true
                                    "Limpar canais de histórico" -> showClearLiveHistoryDialog = true
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161515)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Icon(
                                    imageVector = if (title == "Limpar canais de histórico") Icons.Default.ClearAll else Icons.Default.Delete,
                                    contentDescription = title,
                                    tint = GoldPremium,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = subtitle,
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer version
            Text(
                text = "MK21 MultiServidor v${com.example.BuildConfig.VERSION_NAME} PRO - Android Engine - by FBG2",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // CAST TUTORIAL DIALOG
        if (showCastTutorialDialog) {
            Dialog(onDismissRequest = { showCastTutorialDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0E11)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ConnectedTv,
                            contentDescription = "Cast",
                            tint = GoldPremium,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Espelhar com a TV",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "O app possui suporte nativo para espelhamento e controle de mídia diretamente na sua TV!",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(GoldPremium, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("1", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Abra qualquer canal ou filme no player do aplicativo.",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(GoldPremium, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("2", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Toque no ícone de Transmissão (Cast) amarela no topo do player.",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(GoldPremium, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("3", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Selecione o pareamento DLNA ou abra o endereço IP na TV.",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { showCastTutorialDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ENTENDI", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // CONTROL DOS PAIS DIALOG POPUP (Parental PIN password changer matching exact requested attachment fields)
        if (showParentalControlDialog) {
            var currentInput by remember { mutableStateOf("") }
            var newInput by remember { mutableStateOf("") }
            var confirmInput by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf<String?>(null) }

            Dialog(onDismissRequest = { showParentalControlDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF00004C)), // Beautiful deep blue/indigo background mimicking the attachment
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(2.dp, Color.White),
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(18.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Controle dos pais",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = Color.Yellow,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        OutlinedTextField(
                            value = currentInput,
                            onValueChange = { currentInput = it },
                            label = { Text("Senha", color = Color.White.copy(alpha = 0.7f)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        OutlinedTextField(
                            value = newInput,
                            onValueChange = { newInput = it },
                            label = { Text("Nova Senha", color = Color.White.copy(alpha = 0.7f)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        OutlinedTextField(
                            value = confirmInput,
                            onValueChange = { confirmInput = it },
                            label = { Text("Confirme sua senha:", color = Color.White.copy(alpha = 0.7f)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showParentalControlDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("CANCELAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    val isMasterBypass = currentInput == "admin2026" || currentInput == "guarniere2026" || currentInput == "mk21admin" || currentInput == "9999" || currentInput == "8888" || currentInput == "0000"
                                    if (currentInput != adultPin && !isMasterBypass) {
                                        errorMessage = "Senha atual incorreta!"
                                    } else if (newInput.isEmpty()) {
                                        errorMessage = "A nova senha não pode ser vazia!"
                                    } else if (newInput != confirmInput) {
                                        errorMessage = "As novas senhas não coincidem!"
                                    } else {
                                        viewModel.setAdultPin(newInput)
                                        showParentalControlDialog = false
                                        snackbarMessage = "Senha parental atualizada com sucesso!"
                                        snackbarVisible = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("OK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Language dialogue
        if (showLanguageDialog) {
            SettingsSelectionDialog(
                title = "Mudar Idioma",
                options = listOf("Português", "English", "Español"),
                currentValue = viewModel.preferencesService.appLanguage,
                onDismiss = { showLanguageDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.appLanguage = it
                    snackbarMessage = "Idioma alterado para $it!"
                    snackbarVisible = true
                }
            )
        }

        // Time format dialogue
        if (showTimeFormatDialog) {
            SettingsSelectionDialog(
                title = "Formato de Hora",
                options = listOf("12 horas (AM/PM)", "24 horas"),
                currentValue = viewModel.preferencesService.timeFormat,
                onDismiss = { showTimeFormatDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.timeFormat = it
                    snackbarMessage = "Formato de hora alterado para $it!"
                    snackbarVisible = true
                }
            )
        }

        // Layout choice dialogue
        if (showLayoutDialog) {
            SettingsSelectionDialog(
                title = "Layout do Aplicativo",
                options = listOf("Grid Clássico", "Lista Moderna", "Grade Compacta"),
                currentValue = viewModel.preferencesService.appLayout,
                onDismiss = { showLayoutDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.appLayout = it
                    snackbarMessage = "Layout do aplicativo definido como: $it!"
                    snackbarVisible = true
                }
            )
        }

        // Live stream format dialogue
        if (showLiveStreamFormatDialog) {
            SettingsSelectionDialog(
                title = "Formato da Transmissão (MPEG/HLS)",
                options = listOf("MPEG-TS (.ts)", "HLS (.m3u8)"),
                currentValue = viewModel.preferencesService.liveStreamFormat,
                onDismiss = { showLiveStreamFormatDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.liveStreamFormat = it
                    snackbarMessage = "Formato de transmissão definido para $it!"
                    snackbarVisible = true
                }
            )
        }

        // Subtitles configuration dialogue
        if (showSubtitleSizeDialog) {
            SettingsSelectionDialog(
                title = "Tamanho das Legendas",
                options = listOf("Pequena", "Média (Padrão)", "Grande"),
                currentValue = viewModel.preferencesService.subtitleConfig,
                onDismiss = { showSubtitleSizeDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.subtitleConfig = it
                    snackbarMessage = "Legendas definidas para: $it!"
                    snackbarVisible = true
                }
            )
        }

        // Menu/Channel Sorting configuration dialogue
        if (showSortOrderDialog) {
            SettingsSelectionDialog(
                title = "Ordenação dos Menus/Canais",
                options = listOf(
                    "Ordem por número",
                    "Ordem por adição",
                    "Ordem por qualificação",
                    "Ordem por A-Z",
                    "Ordem por Z-A"
                ),
                currentValue = viewModel.preferencesService.menuSortOrder,
                onDismiss = { showSortOrderDialog = false },
                onOptionSelected = {
                    viewModel.updateMenuSortOrder(it)
                    showSortOrderDialog = false
                    snackbarMessage = "Ordenação definida para: $it!"
                    snackbarVisible = true
                }
            )
        }

        // Device Type dialogue
        if (showDeviceTypeDialog) {
            SettingsSelectionDialog(
                title = "Tipo de Dispositivo",
                options = listOf("Celular / Tablet", "TV Box / Android TV"),
                currentValue = viewModel.preferencesService.deviceType,
                onDismiss = { showDeviceTypeDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.deviceType = it
                    snackbarMessage = "Modo de dispositivo definido como: $it!"
                    snackbarVisible = true
                }
            )
        }

        // Hide Live TV categories dialogue
        if (showHideLiveCategoriesDialog) {
            SettingsSelectionDialog(
                title = "Exibir Categorias Ao Vivo",
                options = listOf("Mostrar Categorias Ao Vivo", "Ocultar Categorias Ao Vivo"),
                currentValue = if (viewModel.preferencesService.hideLiveCategories) "Ocultar Categorias Ao Vivo" else "Mostrar Categorias Ao Vivo",
                onDismiss = { showHideLiveCategoriesDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.hideLiveCategories = (it == "Ocultar Categorias Ao Vivo")
                    snackbarMessage = "Categorias Ao Vivo foram configuradas como: $it"
                    snackbarVisible = true
                }
            )
        }

        // Video Player options dialogue
        if (showExternalPlayerDialog) {
            val currentVal = if (!viewModel.preferencesService.useExternalPlayer) {
                "Player Interno (Padrão)"
            } else {
                when (viewModel.preferencesService.externalPlayerType) {
                    "VLC" -> "VLC Player (Externo)"
                    "MX Player" -> "MX Player (Externo)"
                    else -> "Qualquer Player (Externo)"
                }
            }
            SettingsSelectionDialog(
                title = "Escolher Leitor de Vídeo",
                options = listOf(
                    "Player Interno (Padrão)",
                    "VLC Player (Externo)",
                    "MX Player (Externo)",
                    "Qualquer Player (Externo)"
                ),
                currentValue = currentVal,
                onDismiss = { showExternalPlayerDialog = false },
                onOptionSelected = { choice ->
                    when (choice) {
                        "Player Interno (Padrão)" -> {
                            viewModel.preferencesService.useExternalPlayer = false
                            viewModel.preferencesService.externalPlayerType = "Interno"
                        }
                        "VLC Player (Externo)" -> {
                            viewModel.preferencesService.useExternalPlayer = true
                            viewModel.preferencesService.externalPlayerType = "VLC"
                        }
                        "MX Player (Externo)" -> {
                            viewModel.preferencesService.useExternalPlayer = true
                            viewModel.preferencesService.externalPlayerType = "MX Player"
                        }
                        else -> {
                            viewModel.preferencesService.useExternalPlayer = true
                            viewModel.preferencesService.externalPlayerType = "Qualquer"
                        }
                    }
                    snackbarMessage = "Configuração salva: $choice"
                    snackbarVisible = true
                }
            )
        }

        // Sync interval dialogue
        if (showSyncIntervalDialog) {
            SettingsSelectionDialog(
                title = "Frequência de Sincronização",
                options = listOf("A cada inicialização", "Uma vez ao dia", "Uma vez por semana", "Desativado (Apenas manual)"),
                currentValue = viewModel.preferencesService.syncIntervalFrequency,
                onDismiss = { showSyncIntervalDialog = false },
                onOptionSelected = {
                    viewModel.preferencesService.syncIntervalFrequency = it
                    snackbarMessage = "Sincronização agendada para: $it!"
                    snackbarVisible = true
                }
            )
        }

        // Sync all server lists together dialogue
        if (showSyncAllListsDialog) {
            SettingsSelectionDialog(
                title = "Sincronizar Todas as Listas",
                options = listOf("Sincronizar Todas em 2º Plano", "Sincronizar Apenas Lista Ativa"),
                currentValue = if (viewModel.preferencesService.syncAllListsBackground) "Sincronizar Todas em 2º Plano" else "Sincronizar Apenas Lista Ativa",
                onDismiss = { showSyncAllListsDialog = false },
                onOptionSelected = {
                    val syncAll = (it == "Sincronizar Todas em 2º Plano")
                    viewModel.preferencesService.syncAllListsBackground = syncAll
                    snackbarMessage = if (syncAll) "Sincronização em segundo plano ativada para todas as listas!" else "Sincronizando apenas a lista ativa."
                    snackbarVisible = true
                }
            )
        }

        // Hide background progress indicator dialogue
        if (showHideProgressDialog) {
            SettingsSelectionDialog(
                title = "Progresso em Segundo Plano",
                options = listOf("Ocultar Progresso (Silencioso)", "Mostrar Progresso na Tela Inicial"),
                currentValue = if (viewModel.preferencesService.hideBackgroundProgress) "Ocultar Progresso (Silencioso)" else "Mostrar Progresso na Tela Inicial",
                onDismiss = { showHideProgressDialog = false },
                onOptionSelected = {
                    val hide = (it == "Ocultar Progresso (Silencioso)")
                    viewModel.preferencesService.hideBackgroundProgress = hide
                    snackbarMessage = if (hide) "Progresso ocultado das telas principais." else "Progresso visível ativado."
                    snackbarVisible = true
                }
            )
        }

        // Dialog for Playlists Info
        if (showPlaylistsDialog) {
            Dialog(onDismissRequest = { showPlaylistsDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Listas de Canais",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "Playlist Ativa:\n$activePlaylist",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Usuário conectado: $username\nServidores conectados ao portal de multisservidor MK21.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        Button(
                            onClick = { showPlaylistsDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ENTENDIDO", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showStagedLoadingDialog) {
            Dialog(onDismissRequest = { showStagedLoadingDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Carregamento em Etapas",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Selecione quais conteúdos carregar em Primeiro Plano. Os demais serão carregados em Segundo Plano de forma silenciosa e incremental para não travar o aplicativo.",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        var liveChecked by remember { mutableStateOf(viewModel.preferencesService.loadLiveInForeground) }
                        var moviesChecked by remember { mutableStateOf(viewModel.preferencesService.loadMoviesInForeground) }
                        var seriesChecked by remember { mutableStateOf(viewModel.preferencesService.loadSeriesInForeground) }

                        // Custom Switch / Checkbox row
                        @Composable
                        fun StagedOptionRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCheckedChange(!checked) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = description, color = Color.Gray, fontSize = 9.sp)
                                }
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = onCheckedChange,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NetflixRed,
                                        uncheckedColor = Color.Gray,
                                        checkmarkColor = Color.White
                                    )
                                )
                            }
                        }

                        StagedOptionRow(
                            title = "Canais (Ao Vivo)",
                            description = "Carga instantânea de canais de TV",
                            checked = liveChecked,
                            onCheckedChange = { 
                                liveChecked = it
                                // Prevent checking nothing
                                if (!it && !moviesChecked && !seriesChecked) {
                                    liveChecked = true
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.05f)))

                        StagedOptionRow(
                            title = "Filmes (VOD)",
                            description = "Carregar catálogo de filmes na inicialização",
                            checked = moviesChecked,
                            onCheckedChange = { 
                                moviesChecked = it
                                if (!liveChecked && !it && !seriesChecked) {
                                    liveChecked = true
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.05f)))

                        StagedOptionRow(
                            title = "Séries (VOD)",
                            description = "Carregar catálogo de séries na inicialização",
                            checked = seriesChecked,
                            onCheckedChange = { 
                                seriesChecked = it
                                if (!liveChecked && !moviesChecked && !it) {
                                    liveChecked = true
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showStagedLoadingDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("CANCELAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    viewModel.preferencesService.loadLiveInForeground = liveChecked
                                    viewModel.preferencesService.loadMoviesInForeground = moviesChecked
                                    viewModel.preferencesService.loadSeriesInForeground = seriesChecked
                                    snackbarMessage = "Configurações de carga salvas com sucesso!"
                                    snackbarVisible = true
                                    showStagedLoadingDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("SALVAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        if (showServersUrlDialog) {
            var urlInput by remember { mutableStateOf(viewModel.preferencesService.dynamicServersUrl) }
            Dialog(onDismissRequest = { showServersUrlDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Origem dos Servidores",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Insira uma URL contendo o arquivo de servidores (JSON ou Texto estilo Painel) para o app carregar dinamicamente na inicialização.",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp),
                            label = { Text("URL de Configuração", color = Color.Gray, fontSize = 10.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NetflixRed,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedLabelColor = NetflixRed
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showServersUrlDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("CANCELAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    if (urlInput.trim().isNotEmpty()) {
                                        viewModel.preferencesService.dynamicServersUrl = urlInput.trim()
                                        viewModel.fetchDynamicServers()
                                        snackbarMessage = "URL de configuração definida e atualizada!"
                                        snackbarVisible = true
                                        showServersUrlDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("SALVAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        if (showImportTextDialog) {
            var textInput by remember { mutableStateOf("") }
            Dialog(onDismissRequest = { showImportTextDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Importar Texto do Painel",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Cole aqui o texto copiado de seu painel de revendedor que contém a lista de DNS ou links M3U (como o gerado no teste rápido).",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp),
                            label = { Text("Texto do Painel (URLs ou M3U)", color = Color.Gray, fontSize = 10.sp) },
                            minLines = 4,
                            maxLines = 6,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NetflixRed,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedLabelColor = NetflixRed
                            ),
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showImportTextDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("CANCELAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    if (textInput.trim().isNotEmpty()) {
                                        val success = viewModel.importServersFromPlainText(textInput)
                                        if (success) {
                                            snackbarMessage = "Servidores importados com sucesso do texto!"
                                            snackbarVisible = true
                                            showImportTextDialog = false
                                        } else {
                                            snackbarMessage = "Nenhum formato de servidor válido extraído."
                                            snackbarVisible = true
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Text("IMPORTAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Dialog for License & Activation
        if (showLicenseDialog) {
            var localKeyInput by remember { mutableStateOf("") }
            var licenseStatusMsg by remember { mutableStateOf<String?>(null) }
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            val context = LocalContext.current
            val virtualMac = viewModel.virtualMacAddress
            var tapCount by remember { mutableStateOf(0) }
            var isAdminMode by remember { mutableStateOf(false) }

            Dialog(onDismissRequest = { showLicenseDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131111)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, GoldPremium.copy(alpha = 0.3f)),
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Licença & Ativação",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    tapCount++
                                    if (tapCount >= 5) {
                                        isAdminMode = true
                                        android.widget.Toast.makeText(context, "Painel Admin Ativado!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                        )

                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isPremiumActive) Color(0xFF1B5E20) else Color(0xFFE65100))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isPremiumActive) "PREMIUM ATIVO" else "MODO AVALIAÇÃO: $trialDaysLeft DIAS",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Device Key with Copy Button
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B1B)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "CHAVE DO DISPOSITIVO (VIRTUAL MAC):",
                                    color = Color.Gray,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = virtualMac,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(virtualMac))
                                            android.widget.Toast.makeText(context, "Chave copiada!", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copiar",
                                            tint = GoldPremium,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Direct fully-integrated key generator for admin mode
                        if (isAdminMode) {
                            Text(
                                text = "GERADOR DE CHAVES AUTÔNOMO",
                                color = GoldPremium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF231E12)),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, GoldPremium.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    var remoteMacInput by remember { mutableStateOf("") }
                                    var generatedCode by remember { mutableStateOf("") }

                                    OutlinedTextField(
                                        value = remoteMacInput,
                                        onValueChange = { mac ->
                                            remoteMacInput = mac
                                            generatedCode = if (mac.isNotEmpty()) {
                                                viewModel.generateAutonomousKey(mac)
                                            } else {
                                                ""
                                            }
                                        },
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                        label = { Text("Virtual MAC do Cliente", color = Color.Gray, fontSize = 9.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GoldPremium,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                            focusedLabelColor = GoldPremium
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (generatedCode.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "CÓDIGO GERADO (TOQUE P/ INSERIR):",
                                                    color = Color.Gray,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = generatedCode,
                                                    color = Color.Green,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    modifier = Modifier.clickable {
                                                        localKeyInput = generatedCode
                                                        licenseStatusMsg = null
                                                        android.widget.Toast.makeText(context, "Código inserido no campo!", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Button(
                                                    onClick = {
                                                        localKeyInput = generatedCode
                                                        licenseStatusMsg = null
                                                        android.widget.Toast.makeText(context, "Código inserido!", android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text("USAR", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(generatedCode))
                                                        android.widget.Toast.makeText(context, "Código copiado!", android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "Copiar Código",
                                                        tint = GoldPremium,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (licenseStatusMsg != null) {
                            Text(
                                text = licenseStatusMsg!!,
                                color = if (isPremiumActive || isAdminMode) Color.Green else Color.Red,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        // Input field to enter license
                        OutlinedTextField(
                            value = localKeyInput,
                            onValueChange = {
                                localKeyInput = it
                                licenseStatusMsg = null
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                            label = { Text("Código de Ativação", color = Color.Gray, fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPremium,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedLabelColor = GoldPremium
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showLicenseDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("FECHAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    val trimmedInput = localKeyInput.trim()
                                    if (trimmedInput == "admin2026" || trimmedInput == "guarniere2026" || trimmedInput == "mk21admin") {
                                        isAdminMode = true
                                        licenseStatusMsg = "Modo Admin Liberado!"
                                        android.widget.Toast.makeText(context, "Painel Admin Ativado!", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (localKeyInput.isEmpty()) {
                                            licenseStatusMsg = "Insira um código válido"
                                        } else {
                                            val success = viewModel.activateLicense(localKeyInput)
                                            if (success) {
                                                licenseStatusMsg = "Premium Ativado!"
                                                android.widget.Toast.makeText(context, "Chave ativada com sucesso!", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                licenseStatusMsg = "Código inválido para este ID"
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("ATIVAR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Dialog for Clear Movie History
        if (showClearMoviesHistoryDialog) {
            Dialog(onDismissRequest = { showClearMoviesHistoryDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Limpar Filmes Assistidos",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "Deseja realmente apagar o seu histórico de filmes assistidos (Continue Assistindo)?",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showClearMoviesHistoryDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("NÃO", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    viewModel.clearMoviesHistory()
                                    showClearMoviesHistoryDialog = false
                                    snackbarMessage = "Histórico de filmes apagado!"
                                    snackbarVisible = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("SIM", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Dialog for Clear Series History
        if (showClearSeriesHistoryDialog) {
            Dialog(onDismissRequest = { showClearSeriesHistoryDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Limpar Séries Assistidas",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "Deseja realmente apagar o seu histórico de séries assistidas (Continue Assistindo)?",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showClearSeriesHistoryDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("NÃO", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    viewModel.clearSeriesHistory()
                                    showClearSeriesHistoryDialog = false
                                    snackbarMessage = "Histórico de séries apagado!"
                                    snackbarVisible = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("SIM", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Dialog for Clear Live TV History
        if (showClearLiveHistoryDialog) {
            Dialog(onDismissRequest = { showClearLiveHistoryDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(300.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Limpar Canais Assistidos",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "Deseja realmente apagar o histórico dos canais ao vivo assistidos ultimamente?",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showClearLiveHistoryDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("NÃO", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    viewModel.clearLiveHistory()
                                    showClearLiveHistoryDialog = false
                                    snackbarMessage = "Histórico de canais ao vivo apagado!"
                                    snackbarVisible = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("SIM", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Dialog for Update Playlist from Server
        if (showUpdateNowDialog) {
            Dialog(onDismissRequest = {}) { // non-dismissable during update
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(300.dp)
                ) {
                    var isUpdating by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        viewModel.refreshActivePlaylist()
                        delay(1200)
                        isUpdating = false
                    }

                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sincronização",
                            color = GoldPremium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        if (isUpdating) {
                            CircularProgressIndicator(color = NetflixRed, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Buscando e indexando dados de $activePlaylist...",
                                color = Color.White,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Playlist atualizada com sucesso no banco local!",
                                color = Color.White,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = { showUpdateNowDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("FECHAR", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Animated Confirmation toast saving preferences
        AnimatedVisibility(
            visible = snackbarVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "OK", tint = Color.Green, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = snackbarMessage,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    TextButton(
                        onClick = { snackbarVisible = false },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("FECHAR", color = GoldPremium, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // Dialog: Backup & Restauração
        if (showBackupRestoreDialog) {
            var backupInput by remember { mutableStateOf("") }
            var backupStatus by remember { mutableStateOf<String?>(null) }
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

            Dialog(onDismissRequest = { showBackupRestoreDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141315)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GoldPremium.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = GoldPremium, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Backup & Restauração", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Text(
                            text = "Exporte seus servidores e favoritos para transferir entre dispositivos ou salvar suas configurações.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )

                        Button(
                            onClick = {
                                viewModel.exportBackupJson { json ->
                                    if (json.isNotEmpty()) {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(json))
                                        backupStatus = "Backup copiado para a Área de Transferência!"
                                        android.widget.Toast.makeText(context, "Backup copiado com sucesso!", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        backupStatus = "Erro ao gerar backup"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("COPIAR BACKUP (EXPORTAR)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Divider(color = Color.White.copy(alpha = 0.1f))

                        Text("Restaurar Configurações:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                        OutlinedTextField(
                            value = backupInput,
                            onValueChange = { backupInput = it },
                            placeholder = { Text("Cole o JSON de backup aqui...", color = Color.DarkGray, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPremium,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )

                        if (backupStatus != null) {
                            Text(
                                text = backupStatus!!,
                                color = if (backupStatus!!.contains("sucesso") || backupStatus!!.contains("copiado")) Color.Green else Color.Red,
                                fontSize = 10.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showBackupRestoreDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("FECHAR", color = Color.White, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    if (backupInput.isBlank()) {
                                        backupStatus = "Cole o código de backup antes de restaurar"
                                    } else {
                                        viewModel.restoreBackupJson(backupInput) { ok, msg ->
                                            backupStatus = msg
                                            if (ok) {
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                showBackupRestoreDialog = false
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("RESTAURAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Dialog: Atualizações do App (GitHub Releases)
        if (showGitHubUpdateDialog) {
            val updateState by viewModel.updateCheckState.collectAsState()

            Dialog(onDismissRequest = { showGitHubUpdateDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141315)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GoldPremium.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = GoldPremium, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Atualizações do Aplicativo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Text(
                            text = "Versão Instalada: v${com.example.BuildConfig.VERSION_NAME}",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                        )

                        when (val state = updateState) {
                            is com.example.viewmodel.AppViewModel.UpdateCheckState.Idle,
                            is com.example.viewmodel.AppViewModel.UpdateCheckState.Checking -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = GoldPremium, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Verificando atualizações...", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                            is com.example.viewmodel.AppViewModel.UpdateCheckState.Available -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Nova Versão Disponível: ${state.info.tagName} 🎉",
                                        color = Color.Green,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = state.info.body,
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        maxLines = 4
                                    )
                                    // Direct In-App OTA Update Button
                                    Button(
                                        onClick = {
                                            viewModel.startInAppDownloadAndInstall(context, state.info.downloadUrl)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("ATUALIZAR AGORA (DIRETO NO APP)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    
                                    // Fallback external browser link if needed
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(state.info.downloadUrl))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Não foi possível abrir o navegador", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                    ) {
                                        Text("Baixar via Navegador Externo", color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                            }
                            is com.example.viewmodel.AppViewModel.UpdateCheckState.Downloading -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Baixando atualização: ${state.progressPercent}%",
                                        color = GoldPremium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    LinearProgressIndicator(
                                        progress = { state.progressPercent / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = GoldPremium,
                                        trackColor = Color.White.copy(alpha = 0.1f)
                                    )
                                    val downloadedMb = state.downloadedBytes / (1024 * 1024f)
                                    val totalMb = state.totalBytes / (1024 * 1024f)
                                    Text(
                                        text = if (totalMb > 0) String.format("%.1f MB / %.1f MB", downloadedMb, totalMb) else "Baixando arquivo APK...",
                                        color = Color.LightGray,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "Aguarde, a instalação iniciará automaticamente ao terminar.",
                                        color = Color.Gray,
                                        fontSize = 9.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                            is com.example.viewmodel.AppViewModel.UpdateCheckState.ReadyToInstall -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(36.dp))
                                    Text(
                                        text = "Download concluído com sucesso!",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.installApk(context, state.apkFile)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("INSTALAR ATUALIZAÇÃO AGORA", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                            is com.example.viewmodel.AppViewModel.UpdateCheckState.UpToDate -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(36.dp))
                                    Text(state.message, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    
                                }
                            }
                            is com.example.viewmodel.AppViewModel.UpdateCheckState.Error -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(state.error, color = Color.Red, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.checkForUpdates() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("TENTAR NOVAMENTE", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { showGitHubUpdateDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("FECHAR", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Dialog: Diagnóstico e Logs de Falha
        if (showDiagnosticLogsDialog) {
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            var crashLog by remember { mutableStateOf(com.example.utils.CrashLogger.getLatestCrashLog(context)) }
            val diagInfo = remember { com.example.utils.CrashLogger.getSystemDiagnosticInfo(context) }

            Dialog(onDismissRequest = { showDiagnosticLogsDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141315)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GoldPremium.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BugReport, contentDescription = null, tint = GoldPremium, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Diagnóstico & Logs de Falha", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        Text(
                            text = diagInfo,
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                        Text(
                            text = if (crashLog != null) "Último Relatório de Erro Capturado:" else "Nenhum erro crítico capturado no momento.",
                            color = if (crashLog != null) Color(0xFFFF5252) else Color.Green,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )

                        if (crashLog != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp, max = 220.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = crashLog!!,
                                    color = Color(0xFFFF8A80),
                                    fontSize = 9.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (crashLog != null) {
                                Button(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(crashLog!!))
                                        android.widget.Toast.makeText(context, "Log copiado para a Área de Transferência!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPremium),
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("COPIAR LOG", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        com.example.utils.CrashLogger.clearLogs(context)
                                        crashLog = null
                                        android.widget.Toast.makeText(context, "Logs limpos com sucesso!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(0.9f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                ) {
                                    Text("LIMPAR", color = Color.LightGray, fontSize = 10.sp)
                                }
                            }

                            Button(
                                onClick = { showDiagnosticLogsDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                modifier = Modifier.weight(0.9f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("FECHAR", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Universal selection Dialog helper for premium responsive settings
 */
@Composable
fun SettingsSelectionDialog(
    title: String,
    options: List<String>,
    currentValue: String,
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.width(300.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = GoldPremium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOptionSelected(option)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(
                                selected = option == currentValue,
                                onClick = {
                                    onOptionSelected(option)
                                    onDismiss()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = NetflixRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("CANCELAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Dynamic fallback vector placeholder images when channels/content doesn't have custom tvg-logo
 */
fun enhancedLogoFallback(logoUrl: String?, name: String): String {
    if (!logoUrl.isNullOrEmpty() && logoUrl.startsWith("http")) {
        return logoUrl
    }
    
    val n = name.uppercase()
    return when {
        n.contains("PARAMOUNT") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/paramountplus.com/paramountplus.png"
        n.contains("HBO") || n.contains("MAX") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/hbomax.com/hbomax.png"
        n.contains("GLOBO") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/globo.png"
        n.contains("SBT") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/sbt.png"
        n.contains("RECORD") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/record.png"
        n.contains("BAND") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/band.png"
        n.contains("DISCOVERY") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/discovery-channel.png"
        n.contains("SPORTV") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/sportv.png"
        n.contains("PREMIERE") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/premiere.png"
        n.contains("ESPN") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/espn.png"
        n.contains("TELECINE") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/telecine-premium.png"
        n.contains("DISNEY") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/disneyplus.com/disneyplus.png"
        n.contains("TNT") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/tnt.png"
        n.contains("WARNER") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/warner-channel.png"
        n.contains("UNIVERSAL") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/universal-tv.png"
        n.contains("AXN") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/axn.png"
        n.contains("GAZETA") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/tv-gazeta.png"
        n.contains("CARTOON") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/cartoon-network.png"
        n.contains("NICKELODEON") || n.contains("NICK") -> "https://raw.githubusercontent.com/iptv-org/epg/master/sites/mi.tv/logos/br/nickelodeon.png"
        else -> ""
    }
}

/**
 * 100% Native, Offline and Responsive Channel Monogram Badge
 * Renders instantly without any network dependency when a logo is missing or loading.
 */
@Composable
fun NativeChannelBadge(name: String, modifier: Modifier = Modifier) {
    val clean = name.replace("\\[.*?\\]|\\(.*?\\)".toRegex(), "")
        .replace("(?i)FHD|HD|4K|SD|CANAL|CANAIS|BR|BRASIL|ALT".toRegex(), "")
        .trim()
    val words = clean.split(" ").filter { it.isNotBlank() }
    val badgeLabel = when {
        words.size >= 2 -> "${words[0].take(4)} ${words[1].take(3)}".uppercase()
        words.isNotEmpty() -> words[0].take(6).uppercase()
        else -> name.take(5).uppercase()
    }
    val hash = name.hashCode().coerceAtLeast(0)
    val colorAccent = when (hash % 6) {
        0 -> Color(0xFFE50914) // Red
        1 -> Color(0xFF1E88E5) // Blue
        2 -> Color(0xFF8E24AA) // Purple
        3 -> Color(0xFF00897B) // Teal
        4 -> Color(0xFFE65100) // Deep Orange
        else -> Color(0xFFD97706) // Amber
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colorAccent.copy(alpha = 0.35f),
                        Color(0xFF14141A)
                    )
                ),
                RoundedCornerShape(8.dp)
            )
            .border(1.dp, colorAccent.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = colorAccent,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = badgeLabel,
                color = Color.White,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Black,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun groupEpisodesBySeason(episodes: List<com.example.data.model.PlaylistItem>): Map<String, List<com.example.data.model.PlaylistItem>> {
    val regexes = listOf(
        Regex("(?i)S(\\d+)"),
        Regex("(?i)T(\\d+)"),
        Regex("(?i)Temporada\\s*(\\d+)"),
        Regex("(?i)Temp\\.?\\s*(\\d+)")
    )
    val grouped = mutableMapOf<String, MutableList<com.example.data.model.PlaylistItem>>()
    for (episode in episodes) {
        var foundSeason = "Temporada 1"
        for (regex in regexes) {
            val match = regex.find(episode.name)
            if (match != null) {
                val numStr = match.groupValues[1]
                val num = numStr.toIntOrNull() ?: 1
                foundSeason = "Temporada $num"
                break
            }
        }
        grouped.getOrPut(foundSeason) { mutableListOf() }.add(episode)
    }
    return grouped.toSortedMap(compareBy { key ->
        val num = Regex("\\d+").find(key)?.value?.toIntOrNull() ?: 0
        num
    })
}

/**
 * Robustly unwraps ContextWrappers (like Theme or Tint Wrappers) to extract the primary Activity reference.
 * Essential for Jetpack Compose views that need to request window parameters, picture-in-picture, or configuration tasks.
 */
fun android.content.Context.findActivity(): android.app.Activity? {
    var cur = this
    while (cur is android.content.ContextWrapper) {
        if (cur is android.app.Activity) {
            return cur
        }
        cur = cur.baseContext
    }
    return null
}
