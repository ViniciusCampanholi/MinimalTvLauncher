package com.minimal.tvlauncher.youtube

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YouTubeDeviceAccountSelector(
    deviceAccounts: List<String>,
    selectedAccount: String?,
    isAccountAuthenticated: (String) -> Boolean,
    onSelectAccount: (String) -> Unit,
    onOpenSystemAccountPicker: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Contas Google Conectadas nesta TV",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Selecione qual conta já logada no dispositivo deve fornecer Recomendações, Inscrições e Envios Recentes.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(deviceAccounts, key = { it }) { accountName ->
                var isFocused by remember { mutableStateOf(false) }
                val isSelected = accountName.equals(selectedAccount, ignoreCase = true)
                val isLinked = isAccountAuthenticated(accountName)
                val scale by animateFloatAsState(if (isFocused) 1.04f else 1.0f, label = "accScale")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(scale)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when {
                                isFocused -> Color(0xFF222222)
                                isSelected -> Color(0xFF161616)
                                else -> Color(0xFF0C0C0C)
                            }
                        )
                        .border(
                            width = if (isFocused) 2.5.dp else 1.dp,
                            color = when {
                                isFocused -> Color.White
                                isSelected -> Color.White.copy(alpha = 0.55f)
                                else -> Color.White.copy(alpha = 0.15f)
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .combinedClickable(onClick = { onSelectAccount(accountName) })
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = accountName,
                        tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = accountName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                isSelected && isLinked -> "Conta ativa • YouTube sincronizado"
                                isSelected -> "Conta ativa • Aguardando vínculo do YouTube"
                                isLinked -> "Sincronizada • Pressione OK para usar esta conta"
                                else -> "Pressione OK para usar esta conta"
                            },
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = if (isSelected) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            item {
                var isFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isFocused) Color(0xFF222222) else Color(0xFF0C0C0C))
                        .border(
                            width = if (isFocused) 2.5.dp else 1.dp,
                            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .combinedClickable(onClick = onOpenSystemAccountPicker)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Selecionar outra conta do sistema",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Abre o seletor nativo de contas já logadas na Android TV",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YouTubeWebLoginDialog(
    selectedAccount: String?,
    onAuthCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    var isLoadingPage by remember { mutableStateOf(true) }
    var closeFocused by remember { mutableStateOf(false) }

    val loginUrl = remember(selectedAccount) {
        val encodedEmail = Uri.encode(selectedAccount.orEmpty())
        if (!selectedAccount.isNullOrBlank()) {
            "https://accounts.google.com/AccountChooser?service=youtube&hl=pt-BR&Email=$encodedEmail&continue=https%3A%2F%2Fwww.youtube.com%2F"
        } else {
            "https://accounts.google.com/ServiceLogin?service=youtube&hl=pt-BR&continue=https%3A%2F%2Fwww.youtube.com%2F"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 42.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0A0A0A))
                .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!selectedAccount.isNullOrBlank()) {
                            "Sincronizar YouTube: $selectedAccount"
                        } else {
                            "Sincronizar Conta do YouTube"
                        },
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Confirme o acesso para carregar Recomendações, Inscrições e Envios Recentes desta conta.",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (closeFocused) Color.White else Color(0xFF181818))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        .onFocusChanged { closeFocused = it.isFocused }
                        .combinedClickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Fechar",
                        tint = if (closeFocused) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Fechar",
                        color = if (closeFocused) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            isFocusable = true
                            isFocusableInTouchMode = true
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
                                    isLoadingPage = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoadingPage = false
                                    val cookies = cookieManager.getCookie("https://www.youtube.com").orEmpty()
                                    if (cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID=")) {
                                        cookieManager.flush()
                                        onAuthCompleted()
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = false
                            }

                            loadUrl(loginUrl)
                            requestFocus()
                        }
                    }
                )

                if (isLoadingPage) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }
}
