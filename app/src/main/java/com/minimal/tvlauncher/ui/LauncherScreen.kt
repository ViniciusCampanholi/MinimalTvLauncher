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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.SignalWifi0Bar
import androidx.compose.material.icons.outlined.SignalWifi4Bar
import androidx.compose.material.icons.outlined.SignalWifiOff
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
import com.minimal.tvlauncher.model.AppInfo
import com.minimal.tvlauncher.network.ConnectionType
import com.minimal.tvlauncher.network.NetworkStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LauncherScreen(
    apps: List<AppInfo>,
    isLoading: Boolean,
    networkStatus: NetworkStatus,
    onLaunchApp: (String) -> Unit,
    onToggleAppSelection: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var isSelectionMode by remember { mutableStateOf(false) }

    // Na tela inicial do Launcher, o botão Voltar fecha o seletor (se aberto) ou não faz nada
    BackHandler(enabled = true) {
        if (isSelectionMode) {
            isSelectionMode = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopStatusBar(
                networkStatus = networkStatus,
                isSelectionMode = isSelectionMode,
                onToggleSelectionMode = { isSelectionMode = !isSelectionMode },
                onOpenSettings = onOpenSettings
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (isSelectionMode) {
                AppSelectionContent(
                    allApps = apps,
                    onToggleApp = onToggleAppSelection,
                    onDone = { isSelectionMode = false }
                )
            } else {
                val selectedApps = remember(apps) { apps.filter { it.isSelected } }
                HomeAppsGrid(
                    selectedApps = selectedApps,
                    onLaunchApp = onLaunchApp,
                    onOpenSelector = { isSelectionMode = true }
                )
            }
        }
    }
}

@Composable
private fun TopStatusBar(
    networkStatus: NetworkStatus,
    isSelectionMode: Boolean,
    onToggleSelectionMode: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val locale = remember { Locale("pt", "BR") }
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
            // Hora
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

            // Data
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

        // Ícones de Sinal, Seleção de Apps e Configurações à direita
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SignalIndicator(networkStatus = networkStatus)

            FocusableIconButton(
                icon = if (isSelectionMode) Icons.Outlined.Close else Icons.Outlined.Apps,
                contentDescription = "Selecionar Aplicativos",
                label = if (isSelectionMode) "Concluir" else "Apps",
                onClick = onToggleSelectionMode
            )

            FocusableIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = "Configurações",
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun SignalIndicator(networkStatus: NetworkStatus) {
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

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF121212))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (networkStatus.type == ConnectionType.NONE) {
                Color.White.copy(alpha = 0.45f)
            } else {
                Color.White
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
            .focusable()
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
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeAppsGrid(
    selectedApps: List<AppInfo>,
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
                    .focusable()
                    .combinedClickable(onClick = onOpenSelector)
                    .padding(horizontal = 36.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Apps,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Selecionar aplicativos para a tela inicial",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Pressione OK no controle para escolher quais apps exibir",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(selectedApps, key = { it.packageName }) { app ->
                AppCard(
                    app = app,
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
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.06f else 1.0f, label = "cardScale")

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
            .focusable()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
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
                        modifier = Modifier.size(64.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = app.label,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppSelectionContent(
    allApps: List<AppInfo>,
    onToggleApp: (String) -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Escolher aplicativos da tela inicial",
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
                        .focusable()
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
