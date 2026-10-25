package com.minimal.tvlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.SignalWifi0Bar
import androidx.compose.material.icons.outlined.SignalWifi4Bar
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Wifi1Bar
import androidx.compose.material.icons.outlined.Wifi2Bar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimal.tvlauncher.bluetooth.BluetoothUiState
import com.minimal.tvlauncher.bluetooth.BtDeviceCategory
import com.minimal.tvlauncher.bluetooth.BtDeviceInfo
import com.minimal.tvlauncher.model.AppInfo
import com.minimal.tvlauncher.network.ConnectionType
import com.minimal.tvlauncher.network.NetworkStatus
import com.minimal.tvlauncher.youtube.YouTubeDeviceAccountSelector
import com.minimal.tvlauncher.youtube.YouTubeDeviceCodeInfo
import com.minimal.tvlauncher.youtube.YouTubeFeedTab
import com.minimal.tvlauncher.youtube.YouTubeVideoItem
import com.minimal.tvlauncher.youtube.YouTubeWebLoginDialog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ActivePanel {
    HOME,
    APP_SELECTION,
    BLUETOOTH,
    WALLPAPER_SELECTION,
    YOUTUBE_ACCOUNTS
}

@Composable
fun LauncherScreen(
    apps: List<AppInfo>,
    isLoading: Boolean,
    networkStatus: NetworkStatus,
    bluetoothState: BluetoothUiState,
    isDefaultLauncher: Boolean,
    selectedWallpaper: WallpaperPreset,
    isYouTubeBoxEnabled: Boolean,
    isYouTubeLoading: Boolean,
    deviceGoogleAccounts: List<String>,
    selectedYouTubeAccount: String?,
    selectedYouTubeTab: YouTubeFeedTab,
    youTubeVideos: List<YouTubeVideoItem>,
    youTubeDeviceCodeInfo: YouTubeDeviceCodeInfo?,
    showYouTubeWebLogin: Boolean,
    isYouTubeAccountAuthenticated: (String) -> Boolean,
    onLaunchApp: (String) -> Unit,
    onToggleAppSelection: (String) -> Unit,
    onSelectWallpaper: (WallpaperPreset) -> Unit,
    onStartScreensaver: () -> Unit,
    onToggleYouTubeBox: () -> Unit,
    onSelectYouTubeTab: (YouTubeFeedTab) -> Unit,
    onRefreshYouTube: () -> Unit,
    onOpenYouTubeVideo: (YouTubeVideoItem) -> Unit,
    onSelectYouTubeDeviceAccount: (String) -> Unit,
    onOpenSystemAccountPicker: () -> Unit,
    onOpenYouTubeWebLogin: () -> Unit,
    onDismissYouTubeWebLogin: () -> Unit,
    onYouTubeWebAuthCompleted: () -> Unit,
    onToggleBluetoothDevice: (BtDeviceInfo) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var activePanel by remember { mutableStateOf(ActivePanel.HOME) }

    // Na tela inicial do Launcher, o botão Voltar fecha o painel aberto ou permanece na Home
    BackHandler(enabled = true) {
        if (activePanel != ActivePanel.HOME) {
            activePanel = ActivePanel.HOME
        }
    }

    if (showYouTubeWebLogin) {
        YouTubeWebLoginDialog(
            selectedAccount = selectedYouTubeAccount,
            onAuthCompleted = onYouTubeWebAuthCompleted,
            onDismiss = onDismissYouTubeWebLogin
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        WallpaperBackground(
            preset = selectedWallpaper,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 44.dp, vertical = 18.dp)
        ) {
            TopStatusBar(
                networkStatus = networkStatus,
                bluetoothState = bluetoothState,
                activePanel = activePanel,
                isDefaultLauncher = isDefaultLauncher,
                onOpenWifiSettings = onOpenWifiSettings,
                onStartScreensaver = onStartScreensaver,
                onToggleWallpaperPanel = {
                    activePanel = if (activePanel == ActivePanel.WALLPAPER_SELECTION) {
                        ActivePanel.HOME
                    } else {
                        ActivePanel.WALLPAPER_SELECTION
                    }
                },
                onToggleBluetoothPanel = {
                    if (!bluetoothState.hasPermission) {
                        onRequestBluetoothPermission()
                    }
                    activePanel = if (activePanel == ActivePanel.BLUETOOTH) {
                        ActivePanel.HOME
                    } else {
                        ActivePanel.BLUETOOTH
                    }
                },
                onToggleSelectionMode = {
                    activePanel = if (activePanel == ActivePanel.APP_SELECTION) {
                        ActivePanel.HOME
                    } else {
                        ActivePanel.APP_SELECTION
                    }
                },
                onSetDefaultLauncher = onSetDefaultLauncher,
                onOpenSettings = onOpenSettings
            )

            Spacer(modifier = Modifier.height(14.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                activePanel == ActivePanel.YOUTUBE_ACCOUNTS -> {
                    YouTubeDeviceAccountSelector(
                        deviceAccounts = deviceGoogleAccounts,
                        selectedAccount = selectedYouTubeAccount,
                        isAccountAuthenticated = isYouTubeAccountAuthenticated,
                        onSelectAccount = { accountName ->
                            activePanel = ActivePanel.HOME
                            onSelectYouTubeDeviceAccount(accountName)
                        },
                        onOpenSystemAccountPicker = {
                            activePanel = ActivePanel.HOME
                            onOpenSystemAccountPicker()
                        }
                    )
                }
                activePanel == ActivePanel.WALLPAPER_SELECTION -> {
                    WallpaperSelectionContent(
                        selectedWallpaper = selectedWallpaper,
                        onSelectWallpaper = onSelectWallpaper
                    )
                }
                activePanel == ActivePanel.BLUETOOTH -> {
                    BluetoothPanelContent(
                        bluetoothState = bluetoothState,
                        onToggleDevice = onToggleBluetoothDevice,
                        onOpenSystemBluetooth = onOpenBluetoothSettings,
                        onRequestPermission = onRequestBluetoothPermission
                    )
                }
                activePanel == ActivePanel.APP_SELECTION -> {
                    AppSelectionContent(
                        allApps = apps,
                        isYouTubeBoxEnabled = isYouTubeBoxEnabled,
                        onToggleYouTubeBox = onToggleYouTubeBox,
                        onOpenWallpaperSelector = { activePanel = ActivePanel.WALLPAPER_SELECTION },
                        onToggleApp = onToggleAppSelection
                    )
                }
                else -> {
                    val selectedApps = remember(apps) { apps.filter { it.isSelected } }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clipToBounds()
                        ) {
                            HomeAppsGrid(
                                selectedApps = selectedApps,
                                compactMode = isYouTubeBoxEnabled,
                                onLaunchApp = onLaunchApp,
                                onOpenSelector = { activePanel = ActivePanel.APP_SELECTION }
                            )
                        }

                        if (isYouTubeBoxEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            YouTubeRecommendationsBox(
                                videos = youTubeVideos,
                                isLoading = isYouTubeLoading,
                                selectedAccount = selectedYouTubeAccount,
                                isAccountLinked = selectedYouTubeAccount != null &&
                                    isYouTubeAccountAuthenticated(selectedYouTubeAccount),
                                deviceCodeInfo = youTubeDeviceCodeInfo,
                                selectedTab = selectedYouTubeTab,
                                onSelectTab = onSelectYouTubeTab,
                                onRefresh = onRefreshYouTube,
                                onOpenWebLogin = onOpenYouTubeWebLogin,
                                onSwitchDeviceAccount = {
                                    if (deviceGoogleAccounts.isNotEmpty()) {
                                        activePanel = ActivePanel.YOUTUBE_ACCOUNTS
                                    } else {
                                        onOpenSystemAccountPicker()
                                    }
                                },
                                onHideBox = onToggleYouTubeBox,
                                onClickVideo = onOpenYouTubeVideo
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopStatusBar(
    networkStatus: NetworkStatus,
    bluetoothState: BluetoothUiState,
    activePanel: ActivePanel,
    isDefaultLauncher: Boolean,
    onOpenWifiSettings: () -> Unit,
    onStartScreensaver: () -> Unit,
    onToggleWallpaperPanel: () -> Unit,
    onToggleBluetoothPanel: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val locale = remember { Locale.forLanguageTag("pt-BR") }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", locale) }
    val dateFormatter = remember { SimpleDateFormat("EEE, dd 'de' MMM", locale) }

    var currentTime by remember { mutableStateOf(timeFormatter.format(Date())) }
    var currentDate by remember {
        mutableStateOf(
            dateFormatter.format(Date()).replaceFirstChar { it.uppercase() }
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            currentTime = timeFormatter.format(now)
            currentDate = dateFormatter.format(now).replaceFirstChar { it.uppercase() }
            delay(1_000L)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ícones de Data e Hora à esquerda
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = "Hora",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = "Data",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = currentDate,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        // Ícones de Sinal, Bluetooth (Fones/Controles), Apps, Launcher Padrão e Configurações à direita
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SignalIndicator(
                networkStatus = networkStatus,
                onClick = onOpenWifiSettings
            )

            val btIcon = when {
                !bluetoothState.isEnabled -> Icons.Outlined.BluetoothDisabled
                bluetoothState.connectedAudioDeviceName != null -> Icons.Outlined.Headphones
                bluetoothState.pairedDevices.any { it.isConnected } -> Icons.Outlined.BluetoothConnected
                else -> Icons.Outlined.Bluetooth
            }

            val btLabel = when {
                activePanel == ActivePanel.BLUETOOTH -> "Fechar BT"
                bluetoothState.connectedAudioDeviceName != null -> bluetoothState.connectedAudioDeviceName
                else -> "Fones / BT"
            }

            FocusableIconButton(
                icon = if (activePanel == ActivePanel.BLUETOOTH) Icons.Outlined.Close else btIcon,
                contentDescription = "Dispositivos Bluetooth",
                label = btLabel,
                onClick = onToggleBluetoothPanel
            )

            FocusableIconButton(
                icon = if (activePanel == ActivePanel.APP_SELECTION) Icons.Outlined.Close else Icons.Outlined.Apps,
                contentDescription = "Selecionar Aplicativos",
                label = if (activePanel == ActivePanel.APP_SELECTION) "Concluir" else "Apps",
                onClick = onToggleSelectionMode
            )

            FocusableIconButton(
                icon = if (activePanel == ActivePanel.WALLPAPER_SELECTION) Icons.Outlined.Close else Icons.Outlined.Palette,
                contentDescription = "Papel de Parede",
                label = if (activePanel == ActivePanel.WALLPAPER_SELECTION) "Fechar" else null,
                onClick = onToggleWallpaperPanel
            )

            FocusableIconButton(
                icon = Icons.Outlined.Bedtime,
                contentDescription = "Ativar Protetor de Tela",
                onClick = onStartScreensaver
            )

            if (!isDefaultLauncher) {
                FocusableIconButton(
                    icon = Icons.Outlined.Home,
                    contentDescription = "Definir como Launcher Padrão",
                    label = "Tornar Padrão",
                    onClick = onSetDefaultLauncher
                )
            }

            FocusableIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = "Configurações",
                onClick = onOpenSettings
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YouTubeRecommendationsBox(
    videos: List<YouTubeVideoItem>,
    isLoading: Boolean,
    selectedAccount: String?,
    isAccountLinked: Boolean,
    deviceCodeInfo: YouTubeDeviceCodeInfo?,
    selectedTab: YouTubeFeedTab,
    onSelectTab: (YouTubeFeedTab) -> Unit,
    onRefresh: () -> Unit,
    onOpenWebLogin: () -> Unit,
    onSwitchDeviceAccount: () -> Unit,
    onHideBox: () -> Unit,
    onClickVideo: (YouTubeVideoItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0A0A))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SmartDisplay,
                    contentDescription = "YouTube",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "YouTube",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.width(4.dp))

                YouTubeFeedTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    FocusablePillButton(
                        text = tab.label,
                        isSelected = isSelected,
                        onClick = { onSelectTab(tab) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FocusableIconButton(
                    icon = Icons.Outlined.AccountCircle,
                    contentDescription = "Conta Google da TV",
                    label = selectedAccount ?: "Escolher Conta da TV",
                    onClick = onSwitchDeviceAccount
                )

                FocusableIconButton(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = "Atualizar Recomendações",
                    onClick = onRefresh
                )

                FocusableIconButton(
                    icon = Icons.Outlined.VisibilityOff,
                    contentDescription = "Ocultar Caixa do YouTube",
                    onClick = onHideBox
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 2.dp
                )
            }
        } else if (videos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (selectedAccount == null) {
                        Text(
                            text = "Selecione uma conta Google já logada na TV para carregar suas recomendações.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FocusableIconButton(
                            icon = Icons.Outlined.AccountCircle,
                            contentDescription = "Selecionar Conta da TV",
                            label = "Selecionar Conta Google da TV",
                            onClick = onSwitchDeviceAccount
                        )
                    } else if (!isAccountLinked) {
                        val codePrompt = if (deviceCodeInfo != null) {
                            "Para exibir o feed exclusivo de $selectedAccount, acesse ${deviceCodeInfo.verificationUrl.removePrefix("https://")} e digite o código ${deviceCodeInfo.userCode}, ou sincronize na TV:"
                        } else {
                            "Sincronize o YouTube da conta $selectedAccount para carregar Recomendações, Inscrições e Envios Recentes:"
                        }
                        Text(
                            text = codePrompt,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (deviceCodeInfo != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1E1E))
                                        .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Código: ${deviceCodeInfo.userCode}",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            FocusableIconButton(
                                icon = Icons.Outlined.SmartDisplay,
                                contentDescription = "Vincular na TV",
                                label = "Vincular na TV",
                                onClick = onOpenWebLogin
                            )
                            FocusableIconButton(
                                icon = Icons.Outlined.AccountCircle,
                                contentDescription = "Trocar Conta da TV",
                                label = "Trocar Conta",
                                onClick = onSwitchDeviceAccount
                            )
                        }
                    } else {
                        Text(
                            text = "Nenhum vídeo encontrado em \"${selectedTab.label}\" para $selectedAccount.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FocusableIconButton(
                                icon = Icons.Outlined.Refresh,
                                contentDescription = "Atualizar",
                                label = "Atualizar",
                                onClick = onRefresh
                            )
                            FocusableIconButton(
                                icon = Icons.Outlined.SmartDisplay,
                                contentDescription = "Ressincronizar Conta",
                                label = "Ressincronizar Conta",
                                onClick = onOpenWebLogin
                            )
                        }
                    }
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                items(videos, key = { it.id }) { video ->
                    YouTubeVideoCard(
                        video = video,
                        onClick = { onClickVideo(video) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusablePillButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                when {
                    isFocused -> Color.White
                    isSelected -> Color(0xFF262626)
                    else -> Color(0xFF141414)
                }
            )
            .border(
                width = 1.dp,
                color = if (isFocused || isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                shape = CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YouTubeVideoCard(
    video: YouTubeVideoItem,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.04f else 1.0f, label = "ytCardScale")

    Column(
        modifier = Modifier
            .width(196.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color(0xFF1F1F1F) else Color(0xFF121212))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .combinedClickable(onClick = onClick)
            .padding(7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (video.thumbnail != null) {
                Image(
                    bitmap = video.thumbnail,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PlayCircleOutline,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }

            if (video.durationText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = video.durationText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = video.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        val subtitle = if (video.publishedText.isNotBlank()) {
            "${video.channelName} • ${video.publishedText}"
        } else {
            video.channelName
        }

        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SignalIndicator(
    networkStatus: NetworkStatus,
    onClick: () -> Unit
) {
    val (icon, description) = when (networkStatus.type) {
        ConnectionType.ETHERNET -> Icons.Outlined.SettingsEthernet to "Cabo"
        ConnectionType.WIFI -> {
            val wifiIcon = when (networkStatus.signalLevel) {
                0 -> Icons.Outlined.SignalWifi0Bar
                1 -> Icons.Outlined.Wifi1Bar
                2 -> Icons.Outlined.Wifi2Bar
                3 -> Icons.Outlined.Wifi
                else -> Icons.Outlined.SignalWifi4Bar
            }
            wifiIcon to "Wi-Fi"
        }
        ConnectionType.OTHER -> Icons.Outlined.SignalWifi4Bar to "Conectado"
        ConnectionType.NONE -> Icons.Outlined.SignalWifiOff to "Sem sinal"
    }

    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1.0f, label = "wifiBtnScale")

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(if (isFocused) Color.White else Color(0xFF141414))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.2f),
                shape = CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = when {
                isFocused -> Color.Black
                networkStatus.type == ConnectionType.NONE -> Color.White.copy(alpha = 0.45f)
                else -> Color.White
            },
            modifier = Modifier.size(22.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusableIconButton(
    icon: ImageVector,
    contentDescription: String,
    label: String? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1.0f, label = "btnScale")

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(if (isFocused) Color.White else Color(0xFF141414))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.2f),
                shape = CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .combinedClickable(onClick = onClick)
            .padding(horizontal = if (label != null) 16.dp else 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(22.dp)
        )
        if (label != null) {
            Text(
                text = label,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BluetoothPanelContent(
    bluetoothState: BluetoothUiState,
    onToggleDevice: (BtDeviceInfo) -> Unit,
    onOpenSystemBluetooth: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Fones de Ouvido e Dispositivos Bluetooth",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bluetoothState.statusMessage
                        ?: "Selecione um fone ou controle pareado para conectar/desconectar diretamente.",
                    color = if (bluetoothState.statusMessage != null) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.6f)
                    },
                    fontSize = 14.sp
                )
            }

            FocusableIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = "Configurações Bluetooth da TV",
                label = "Parear novo / Abrir Bluetooth da TV",
                onClick = onOpenSystemBluetooth
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!bluetoothState.hasPermission) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                FocusableIconButton(
                    icon = Icons.Outlined.Bluetooth,
                    contentDescription = "Permitir Bluetooth",
                    label = "Autorizar acesso aos dispositivos Bluetooth",
                    onClick = onRequestPermission
                )
            }
        } else if (bluetoothState.pairedDevices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Headphones,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (bluetoothState.isEnabled) {
                            "Nenhum fone ou dispositivo pareado encontrado."
                        } else {
                            "O Bluetooth da TV está desativado."
                        },
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FocusableIconButton(
                        icon = Icons.Outlined.Bluetooth,
                        contentDescription = "Abrir Configurações Bluetooth",
                        label = "Abrir configurações de Bluetooth da TV",
                        onClick = onOpenSystemBluetooth
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(bluetoothState.pairedDevices, key = { it.address }) { device ->
                    var isFocused by remember { mutableStateOf(false) }
                    val isBusy = bluetoothState.connectingAddress == device.address

                    val deviceIcon = when (device.category) {
                        BtDeviceCategory.HEADPHONES_AUDIO -> Icons.Outlined.Headphones
                        BtDeviceCategory.GAMEPAD -> Icons.Outlined.SportsEsports
                        BtDeviceCategory.OTHER -> Icons.Outlined.Bluetooth
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when {
                                    isFocused -> Color(0xFF222222)
                                    device.isConnected -> Color(0xFF161616)
                                    else -> Color(0xFF0C0C0C)
                                }
                            )
                            .border(
                                width = if (isFocused) 2.5.dp else 1.dp,
                                color = when {
                                    isFocused -> Color.White
                                    device.isConnected -> Color.White.copy(alpha = 0.55f)
                                    else -> Color.White.copy(alpha = 0.15f)
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                            .onFocusChanged { isFocused = it.isFocused }
                            .combinedClickable(
                                onClick = { onToggleDevice(device) }
                            )
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = deviceIcon,
                            contentDescription = device.name,
                            tint = if (device.isConnected) Color.White else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = if (device.isConnected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = when {
                                    isBusy && device.isConnected -> "Desconectando..."
                                    isBusy && !device.isConnected -> "Conectando automaticamente..."
                                    device.isConnected -> "Conectado • Pressione OK para desconectar"
                                    else -> "Pareado • Pressione OK para conectar"
                                },
                                color = if (device.isConnected || isBusy) {
                                    Color.White.copy(alpha = 0.85f)
                                } else {
                                    Color.White.copy(alpha = 0.5f)
                                },
                                fontSize = 12.sp
                            )
                        }

                        if (isBusy) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (device.isConnected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Conectado",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeAppsGrid(
    selectedApps: List<AppInfo>,
    compactMode: Boolean,
    onLaunchApp: (String) -> Unit,
    onOpenSelector: () -> Unit
) {
    if (selectedApps.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            var isFocused by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isFocused) Color(0xFF1A1A1A) else Color(0xFF0D0D0D))
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) Color.White else Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .combinedClickable(onClick = onOpenSelector)
                    .padding(horizontal = 36.dp, vertical = if (compactMode) 18.dp else 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Apps,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (compactMode) 32.dp else 40.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Selecionar aplicativos para a tela inicial",
                    color = Color.White,
                    fontSize = if (compactMode) 16.sp else 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pressione OK no controle para escolher quais apps exibir",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
        }
    } else {
        val spacing = if (compactMode) 16.dp else 24.dp
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = if (compactMode) 165.dp else 180.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            items(selectedApps, key = { it.packageName }) { app ->
                AppCard(
                    app = app,
                    compactMode = compactMode,
                    onClick = { onLaunchApp(app.packageName) },
                    onLongClick = onOpenSelector
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCard(
    app: AppInfo,
    compactMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f, label = "cardScale")

    Column(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color(0xFF1C1C1C) else Color(0xFF0D0D0D))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(if (compactMode) 14.dp else 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compactMode) 64.dp else 84.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                app.banner != null -> {
                    Image(
                        bitmap = app.banner,
                        contentDescription = app.label,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                app.icon != null -> {
                    Image(
                        bitmap = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier.size(if (compactMode) 50.dp else 64.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(if (compactMode) 8.dp else 14.dp))

        Text(
            text = app.label,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = if (compactMode) 14.sp else 15.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperSelectionContent(
    selectedWallpaper: WallpaperPreset,
    onSelectWallpaper: (WallpaperPreset) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Papéis de Parede Pré-definidos",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Escolha um fundo minimalista para a sua TV. Pressione OK para aplicar e Voltar para sair.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(WallpaperPreset.entries, key = { it.id }) { preset ->
                var isFocused by remember { mutableStateOf(false) }
                val isSelected = preset == selectedWallpaper
                val scale by animateFloatAsState(if (isFocused) 1.04f else 1.0f, label = "wpScale")

                Column(
                    modifier = Modifier
                        .scale(scale)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isFocused) Color(0xFF1F1F1F) else Color(0xFF0E0E0E))
                        .border(
                            width = if (isFocused) 2.5.dp else 1.dp,
                            color = when {
                                isFocused -> Color.White
                                isSelected -> Color.White.copy(alpha = 0.6f)
                                else -> Color.White.copy(alpha = 0.15f)
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .combinedClickable(onClick = { onSelectWallpaper(preset) })
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    ) {
                        WallpaperBackground(
                            preset = preset,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Selecionado",
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = preset.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = preset.subtitle,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppSelectionContent(
    allApps: List<AppInfo>,
    isYouTubeBoxEnabled: Boolean,
    onToggleYouTubeBox: () -> Unit,
    onOpenWallpaperSelector: () -> Unit,
    onToggleApp: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Personalizar Tela Inicial",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pressione OK sobre um aplicativo para mostrar ou ocultar. Pressione Voltar para concluir.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusableIconButton(
                    icon = Icons.Outlined.Palette,
                    contentDescription = "Escolher Papel de Parede",
                    label = "Papel de Parede",
                    onClick = onOpenWallpaperSelector
                )

                FocusableIconButton(
                    icon = if (isYouTubeBoxEnabled) Icons.Outlined.CheckCircle else Icons.Outlined.SmartDisplay,
                    contentDescription = "Alternar Caixa de Recomendações do YouTube",
                    label = if (isYouTubeBoxEnabled) {
                        "Caixa do YouTube: Ativada"
                    } else {
                        "Caixa do YouTube: Desativada"
                    },
                    onClick = onToggleYouTubeBox
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(allApps, key = { it.packageName }) { app ->
                var isFocused by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isFocused -> Color(0xFF222222)
                                app.isSelected -> Color(0xFF141414)
                                else -> Color(0xFF0A0A0A)
                            }
                        )
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = when {
                                isFocused -> Color.White
                                app.isSelected -> Color.White.copy(alpha = 0.4f)
                                else -> Color.White.copy(alpha = 0.12f)
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .combinedClickable(onClick = { onToggleApp(app.packageName) })
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app.icon != null) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(modifier = Modifier.size(40.dp))
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Text(
                        text = app.label,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = if (app.isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Icon(
                        imageVector = if (app.isSelected) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = if (app.isSelected) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
