package com.minimal.tvlauncher.youtube

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.coroutines.resume

enum class YouTubeFeedTab(val label: String, val browseId: String) {
    RECOMMENDED("Recomendados", "FEwhat_to_watch"),
    SUBSCRIPTIONS("Inscrições", "FEsubscriptions"),
    RECENT_UPLOADS("Envios Recentes", "FEwhat_to_watch_recent")
}

data class YouTubeDeviceCodeInfo(
    val accountName: String,
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val intervalSeconds: Int = 5
)

data class YouTubeVideoItem(
    val id: String,
    val title: String,
    val channelName: String,
    val durationText: String,
    val thumbnailUrl: String,
    val thumbnail: ImageBitmap? = null,
    val intentUri: String? = null,
    val publishedEpochMs: Long = 0L,
    val publishedText: String = ""
)

class YouTubeRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val accountManager = AccountManager.get(context)
    private val thumbnailCache = LruCache<String, ImageBitmap>(48)

    fun isYouTubeBoxEnabled(): Boolean {
        return prefs.getBoolean(KEY_YOUTUBE_BOX_ENABLED, false)
    }

    fun setYouTubeBoxEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_YOUTUBE_BOX_ENABLED, enabled).apply()
    }

    fun hasAccountsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.GET_ACCOUNTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun getDeviceGoogleAccounts(): List<String> {
        val accounts = runCatching {
            accountManager.getAccountsByType("com.google").map { it.name }
        }.getOrDefault(emptyList())

        val savedAccount = prefs.getString(KEY_SELECTED_ACCOUNT, null)
        return if (savedAccount != null && !accounts.contains(savedAccount)) {
            listOf(savedAccount) + accounts
        } else {
            accounts
        }
    }

    fun getSelectedAccountName(): String? {
        val saved = prefs.getString(KEY_SELECTED_ACCOUNT, null)
        val deviceAccounts = getDeviceGoogleAccounts()
        return when {
            saved != null && deviceAccounts.contains(saved) -> saved
            saved != null -> saved
            deviceAccounts.isNotEmpty() -> {
                val first = deviceAccounts.first()
                setSelectedAccountName(first)
                first
            }
            else -> null
        }
    }

    fun setSelectedAccountName(accountName: String?) {
        val previous = prefs.getString(KEY_SELECTED_ACCOUNT, null)
        prefs.edit().putString(KEY_SELECTED_ACCOUNT, accountName).apply()
        if (previous != null && !previous.equals(accountName, ignoreCase = true)) {
            saveCurrentCookiesForAccount(previous)
            restoreCookiesForAccount(accountName)
        } else if (accountName != null) {
            restoreCookiesForAccount(accountName)
        }
    }

    fun isAccountAuthenticated(accountName: String?): Boolean {
        if (accountName.isNullOrBlank()) return false
        val key = normalizeAccountKey(accountName)
        val hasRefreshToken = !prefs.getString("${KEY_REFRESH_TOKEN_PREFIX}$key", null).isNullOrBlank()
        if (hasRefreshToken) return true

        val savedCookies = prefs.getString("${KEY_COOKIES_PREFIX}$key", null).orEmpty()
        if (savedCookies.contains("SAPISID=") || savedCookies.contains("__Secure-3PAPISID=")) {
            return true
        }

        val currentSelected = prefs.getString(KEY_SELECTED_ACCOUNT, null)
        if (currentSelected != null && currentSelected.equals(accountName, ignoreCase = true)) {
            val liveCookies = runCatching {
                CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
            }.getOrDefault("")
            if (liveCookies.contains("SAPISID=") || liveCookies.contains("__Secure-3PAPISID=")) {
                saveCurrentCookiesForAccount(accountName)
                return true
            }
        }
        return false
    }

    fun saveCurrentCookiesForAccount(accountName: String?) {
        if (accountName.isNullOrBlank()) return
        val key = normalizeAccountKey(accountName)
        val cookies = runCatching {
            CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
        }.getOrDefault("")
        if (cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID=")) {
            prefs.edit().putString("${KEY_COOKIES_PREFIX}$key", cookies).apply()
        }
    }

    private fun restoreCookiesForAccount(accountName: String?) {
        runCatching {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
            if (!accountName.isNullOrBlank()) {
                val key = normalizeAccountKey(accountName)
                val saved = prefs.getString("${KEY_COOKIES_PREFIX}$key", null).orEmpty()
                if (saved.isNotBlank()) {
                    saved.split(";").forEach { pair ->
                        val trimmed = pair.trim()
                        if (trimmed.isNotEmpty()) {
                            cm.setCookie("https://www.youtube.com", "$trimmed; Domain=.youtube.com; Path=/; Secure")
                        }
                    }
                    cm.flush()
                }
            }
        }
    }

    private fun normalizeAccountKey(accountName: String): String {
        return accountName.trim().lowercase(Locale.ROOT)
    }

    /**
     * Inicia o fluxo OAuth2 de TV do YouTube para vincular a conta Google selecionada na TV
     * quando o Android TV bloqueia tokens de terceiros no AccountManager.
     */
    suspend fun startTvDeviceAuth(accountName: String): YouTubeDeviceCodeInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val deviceId = prefs.getString(KEY_TV_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_TV_DEVICE_ID, it).apply()
            }
            val url = URL("https://www.youtube.com/o/oauth2/device/code")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().apply {
                put("client_id", YT_TV_CLIENT_ID)
                put("scope", "http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content")
                put("device_id", deviceId)
                put("device_model", "Android TV")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            val responseStr = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
            val json = JSONObject(responseStr)
            val deviceCode = json.optString("device_code")
            val userCode = json.optString("user_code")
            val verificationUrl = json.optString("verification_url").ifBlank { "https://www.google.com/device" }
            val interval = json.optInt("interval", 5).coerceAtLeast(4)

            if (deviceCode.isNotBlank() && userCode.isNotBlank()) {
                YouTubeDeviceCodeInfo(
                    accountName = accountName,
                    deviceCode = deviceCode,
                    userCode = userCode,
                    verificationUrl = verificationUrl,
                    intervalSeconds = interval
                )
            } else {
                null
            }
        }.getOrNull()
    }

    /**
     * Verifica se o usuário já autorizou o código da TV para a conta selecionada.
     */
    suspend fun pollTvDeviceAuthToken(codeInfo: YouTubeDeviceCodeInfo): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://www.youtube.com/o/oauth2/token")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().apply {
                put("client_id", YT_TV_CLIENT_ID)
                put("client_secret", YT_TV_CLIENT_SECRET)
                put("code", codeInfo.deviceCode)
                put("grant_type", "http://oauth.net/grant_type/device/1.0")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseStr = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use {
                it.readText()
            }
            val json = JSONObject(responseStr)
            val accessToken = json.optString("access_token")
            val refreshToken = json.optString("refresh_token")
            val expiresIn = json.optLong("expires_in", 3600L)

            if (accessToken.isNotBlank()) {
                val key = normalizeAccountKey(codeInfo.accountName)
                val expiryMs = System.currentTimeMillis() + (expiresIn - 60L).coerceAtLeast(60L) * 1000L
                prefs.edit().apply {
                    putString("${KEY_ACCESS_TOKEN_PREFIX}$key", accessToken)
                    putLong("${KEY_TOKEN_EXPIRY_PREFIX}$key", expiryMs)
                    if (refreshToken.isNotBlank()) {
                        putString("${KEY_REFRESH_TOKEN_PREFIX}$key", refreshToken)
                    }
                }.apply()
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private suspend fun getValidOAuthTokenForAccount(
        activity: Activity,
        accountName: String
    ): String? = withContext(Dispatchers.IO) {
        val key = normalizeAccountKey(accountName)
        val cachedAccess = prefs.getString("${KEY_ACCESS_TOKEN_PREFIX}$key", null)
        val expiry = prefs.getLong("${KEY_TOKEN_EXPIRY_PREFIX}$key", 0L)
        if (!cachedAccess.isNullOrBlank() && System.currentTimeMillis() < expiry) {
            return@withContext cachedAccess
        }

        val refreshToken = prefs.getString("${KEY_REFRESH_TOKEN_PREFIX}$key", null)
        if (!refreshToken.isNullOrBlank()) {
            val refreshed = refreshTvOAuthToken(accountName, refreshToken)
            if (!refreshed.isNullOrBlank()) {
                return@withContext refreshed
            }
        }

        authenticateDeviceAccountSilently(activity, accountName)
    }

    private fun refreshTvOAuthToken(accountName: String, refreshToken: String): String? {
        return runCatching {
            val url = URL("https://www.youtube.com/o/oauth2/token")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().apply {
                put("client_id", YT_TV_CLIENT_ID)
                put("client_secret", YT_TV_CLIENT_SECRET)
                put("refresh_token", refreshToken)
                put("grant_type", "refresh_token")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            val responseStr = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
            val json = JSONObject(responseStr)
            val accessToken = json.optString("access_token")
            val expiresIn = json.optLong("expires_in", 3600L)
            if (accessToken.isNotBlank()) {
                val key = normalizeAccountKey(accountName)
                val expiryMs = System.currentTimeMillis() + (expiresIn - 60L).coerceAtLeast(60L) * 1000L
                prefs.edit()
                    .putString("${KEY_ACCESS_TOKEN_PREFIX}$key", accessToken)
                    .putLong("${KEY_TOKEN_EXPIRY_PREFIX}$key", expiryMs)
                    .apply()
                accessToken
            } else {
                null
            }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun authenticateDeviceAccountSilently(
        activity: Activity,
        accountName: String
    ): String? = withContext(Dispatchers.IO) {
        val account = runCatching {
            accountManager.getAccountsByType("com.google")
                .firstOrNull { it.name.equals(accountName, ignoreCase = true) }
                ?: Account(accountName, "com.google")
        }.getOrDefault(Account(accountName, "com.google"))

        restoreCookiesForAccount(accountName)

        val currentCookies = runCatching {
            CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
        }.getOrDefault("")

        if (!currentCookies.contains("SAPISID=") && !currentCookies.contains("__Secure-3PAPISID=")) {
            val webLoginUrl = requestAccountAuthTokenSilently(
                account = account,
                authTokenType = "weblogin:service=youtube&continue=https://www.youtube.com"
            )
            if (!webLoginUrl.isNullOrBlank() && webLoginUrl.startsWith("http")) {
                applyWebLoginUrlSilently(activity, webLoginUrl)
                saveCurrentCookiesForAccount(accountName)
            }
        }

        requestAccountAuthTokenSilently(
            account = account,
            authTokenType = "oauth2:https://www.googleapis.com/auth/youtube.readonly"
        )
    }

    private suspend fun requestAccountAuthTokenSilently(
        account: Account,
        authTokenType: String
    ): String? = withTimeoutOrNull(6_000L) {
        suspendCancellableCoroutine { cont ->
            runCatching {
                accountManager.getAuthToken(
                    account,
                    authTokenType,
                    null,
                    false,
                    { future ->
                        val token = runCatching {
                            val bundle = future.result
                            bundle.getString(AccountManager.KEY_AUTHTOKEN)
                        }.getOrNull()
                        if (cont.isActive) cont.resume(token)
                    },
                    null
                )
            }.onFailure {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun applyWebLoginUrlSilently(
        activity: Activity,
        webLoginUrl: String
    ) = withTimeoutOrNull(8_000L) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val webView = WebView(activity.applicationContext)
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        CookieManager.getInstance().flush()
                        val cookies = CookieManager.getInstance()
                            .getCookie("https://www.youtube.com").orEmpty()
                        val isYouTubeHost = url?.contains("youtube.com") == true &&
                            !url.contains("accounts.youtube.com")
                        if ((isYouTubeHost || cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID=")) &&
                            cont.isActive
                        ) {
                            webView.destroy()
                            cont.resume(Unit)
                        }
                    }
                }

                cont.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post {
                        runCatching { webView.destroy() }
                    }
                }

                webView.loadUrl(webLoginUrl)
            }
        }
    }

    /**
     * Carrega recomendações, inscrições ou envios recentes EXCLUSIVAMENTE da conta Google selecionada.
     * Não utiliza buscas genéricas para garantir fidelidade total à conta logada.
     */
    suspend fun loadRecommendations(
        activity: Activity,
        tab: YouTubeFeedTab = YouTubeFeedTab.RECOMMENDED
    ): List<YouTubeVideoItem> = withContext(Dispatchers.IO) {
        val selectedAccount = getSelectedAccountName() ?: return@withContext emptyList()
        restoreCookiesForAccount(selectedAccount)

        val oauthToken = getValidOAuthTokenForAccount(activity, selectedAccount)
        val hasCookies = runCatching {
            val c = CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
            c.contains("SAPISID=") || c.contains("__Secure-3PAPISID=")
        }.getOrDefault(false)

        if (oauthToken.isNullOrBlank() && !hasCookies) {
            return@withContext emptyList()
        }

        val collected = mutableListOf<YouTubeVideoItem>()
        val seenIds = hashSetOf<String>()

        fun addUnique(items: List<YouTubeVideoItem>) {
            for (item in items) {
                if (item.id.isNotBlank() && seenIds.add(item.id)) {
                    collected.add(item)
                    if (collected.size >= 14) break
                }
            }
        }

        when (tab) {
            YouTubeFeedTab.RECOMMENDED -> {
                // 1. InnerTube FEwhat_to_watch autenticado (TVHTML5 / WEB)
                addUnique(fetchYouTubeInnerTubeBrowse("FEwhat_to_watch", oauthToken))
                // 2. OAuth Home Activities da conta
                if (collected.size < 6 && !oauthToken.isNullOrBlank()) {
                    addUnique(fetchHomeActivitiesViaOAuth(oauthToken))
                }
                // 3. WebView / Web autenticado com cookies da conta
                if (collected.size < 6 && hasCookies) {
                    addUnique(fetchYouTubeFeedFromWeb("https://www.youtube.com/?hl=pt-BR&gl=BR"))
                }
                if (collected.size < 6 && hasCookies) {
                    addUnique(fetchViaHeadlessWebView(activity, "https://www.youtube.com"))
                }
            }

            YouTubeFeedTab.SUBSCRIPTIONS -> {
                // 1. InnerTube FEsubscriptions autenticado (ordem padrão de inscrições da conta)
                addUnique(fetchYouTubeInnerTubeBrowse("FEsubscriptions", oauthToken))
                // 2. Canais inscritos via OAuth + RSS
                if (collected.size < 6 && !oauthToken.isNullOrBlank()) {
                    addUnique(fetchSubscriptionFeedViaOAuthAndRss(oauthToken, sortByNewest = false))
                }
                // 3. WebView / Web de inscrições autenticado com cookies da conta
                if (collected.size < 6 && hasCookies) {
                    addUnique(fetchYouTubeFeedFromWeb("https://www.youtube.com/feed/subscriptions?hl=pt-BR&gl=BR"))
                }
                if (collected.size < 6 && hasCookies) {
                    addUnique(fetchViaHeadlessWebView(activity, "https://www.youtube.com/feed/subscriptions"))
                }
            }

            YouTubeFeedTab.RECENT_UPLOADS -> {
                // 1. Chip "Enviados recentemente" (Recent Uploads) do feed personalizado da conta no InnerTube
                addUnique(fetchYouTubeInnerTubeRecentUploadsChip(oauthToken))
                // 2. Envios mais recentes em ordem estritamente cronológica das inscrições da conta via OAuth + RSS
                if (!oauthToken.isNullOrBlank()) {
                    addUnique(fetchSubscriptionFeedViaOAuthAndRss(oauthToken, sortByNewest = true))
                }
                // 3. Se usar cookies, busca os vídeos mais recentes das inscrições ordenados por tempo de envio
                if (collected.size < 8) {
                    val subVideos = fetchYouTubeInnerTubeBrowse("FEsubscriptions", oauthToken)
                        .ifEmpty {
                            if (hasCookies) {
                                fetchYouTubeFeedFromWeb("https://www.youtube.com/feed/subscriptions?hl=pt-BR&gl=BR")
                            } else {
                                emptyList()
                            }
                        }
                    addUnique(sortVideosByRecency(subVideos))
                }
            }
        }

        attachThumbnails(collected.take(12))
    }

    /**
     * Busca a aba/chip "Enviados recentemente" ("Recently uploaded") dentro do feed FEwhat_to_watch da conta logada.
     */
    private fun fetchYouTubeInnerTubeRecentUploadsChip(oauthToken: String?): List<YouTubeVideoItem> {
        val homeRoot = executeInnerTubeBrowseRequest(
            browseId = "FEwhat_to_watch",
            continuation = null,
            oauthToken = oauthToken,
            useTvClient = false
        ) ?: return emptyList()

        val chipTokens = mutableListOf<Pair<String, String>>()
        collectChipContinuationTokens(homeRoot, chipTokens)

        val recentContinuation = chipTokens.firstOrNull { (label, _) ->
            label.contains("Enviados recentemente", ignoreCase = true) ||
                label.contains("Recent", ignoreCase = true) ||
                label.contains("Novos", ignoreCase = true)
        }?.second ?: chipTokens.lastOrNull()?.second

        if (!recentContinuation.isNullOrBlank()) {
            val contRoot = executeInnerTubeBrowseRequest(
                browseId = null,
                continuation = recentContinuation,
                oauthToken = oauthToken,
                useTvClient = false
            )
            if (contRoot != null) {
                val videos = mutableListOf<YouTubeVideoItem>()
                collectVideoRenderers(contRoot, videos, hashSetOf())
                if (videos.isNotEmpty()) return videos
            }
        }

        val homeVideos = mutableListOf<YouTubeVideoItem>()
        collectVideoRenderers(homeRoot, homeVideos, hashSetOf())
        return sortVideosByRecency(homeVideos)
    }

    private fun collectChipContinuationTokens(
        node: Any?,
        out: MutableList<Pair<String, String>>
    ) {
        if (node == null || out.size >= 20) return
        when (node) {
            is JSONObject -> {
                if (node.has("chipCloudChipRenderer")) {
                    val chip = node.optJSONObject("chipCloudChipRenderer")
                    val text = extractText(chip?.optJSONObject("text"))
                    val token = chip?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("continuationCommand")
                        ?.optString("token")
                        ?.ifBlank {
                            chip.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("browseEndpoint")
                                ?.optString("params").orEmpty()
                        }.orEmpty()
                    if (text.isNotBlank() && token.isNotBlank()) {
                        out.add(text to token)
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    collectChipContinuationTokens(node.opt(keys.next()), out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectChipContinuationTokens(node.opt(i), out)
                }
            }
        }
    }

    private fun sortVideosByRecency(videos: List<YouTubeVideoItem>): List<YouTubeVideoItem> {
        if (videos.isEmpty()) return emptyList()
        return videos.sortedWith(
            compareByDescending<YouTubeVideoItem> { it.publishedEpochMs }
                .thenBy { estimateAgeMinutes(it.publishedText) }
        )
    }

    private fun estimateAgeMinutes(publishedText: String): Long {
        if (publishedText.isBlank()) return Long.MAX_VALUE / 2
        val lower = publishedText.lowercase(Locale.ROOT)
        val num = Regex("(\\d+)").find(lower)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 1L
        return when {
            lower.contains("seg") || lower.contains("sec") || lower.contains("agora") -> 0L
            lower.contains("min") -> num
            lower.contains("hora") || lower.contains("hour") || lower.contains(" h") -> num * 60L
            lower.contains("dia") || lower.contains("day") -> num * 1440L
            lower.contains("sem") || lower.contains("week") -> num * 10080L
            lower.contains("mês") || lower.contains("mes") || lower.contains("month") -> num * 43200L
            lower.contains("ano") || lower.contains("year") -> num * 525600L
            else -> Long.MAX_VALUE / 3
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchViaHeadlessWebView(
        activity: Activity,
        targetUrl: String
    ): List<YouTubeVideoItem> = withTimeoutOrNull(10_000L) {
        val rawJson = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<String?> { cont ->
                val webView = WebView(activity.applicationContext)
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        CookieManager.getInstance().flush()
                        val isYouTubePage = url?.contains("youtube.com") == true &&
                            !url.contains("accounts.youtube.com") &&
                            !url.contains("accounts.google.com")
                        if (isYouTubePage && view != null) {
                            view.evaluateJavascript(
                                "(function(){ return window.ytInitialData ? JSON.stringify(window.ytInitialData) : ''; })();"
                            ) { result ->
                                if (cont.isActive) {
                                    val unescaped = runCatching {
                                        if (!result.isNullOrBlank() && result != "null" && result != "\"\"") {
                                            JSONTokener(result).nextValue()?.toString()
                                        } else {
                                            null
                                        }
                                    }.getOrNull()
                                    webView.destroy()
                                    cont.resume(unescaped)
                                }
                            }
                        }
                    }
                }

                cont.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post {
                        runCatching { webView.destroy() }
                    }
                }

                webView.loadUrl(targetUrl)
            }
        }

        if (rawJson.isNullOrBlank()) return@withTimeoutOrNull emptyList()
        val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return@withTimeoutOrNull emptyList()
        val videos = mutableListOf<YouTubeVideoItem>()
        collectVideoRenderers(root, videos, hashSetOf())
        videos
    } ?: emptyList()

    /**
     * Usa o token OAuth2 da conta Google logada na TV para buscar os canais inscritos do usuário
     * e carrega os vídeos mais recentes de cada canal via feed RSS oficial do YouTube.
     */
    private suspend fun fetchSubscriptionFeedViaOAuthAndRss(
        oauthToken: String,
        sortByNewest: Boolean
    ): List<YouTubeVideoItem> = coroutineScope {
        val channelIds = runCatching {
            val url = URL("https://www.googleapis.com/youtube/v3/subscriptions?part=snippet&mine=true&maxResults=15")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Authorization", "Bearer $oauthToken")
                setRequestProperty("Accept", "application/json")
            }
            val jsonStr = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
            val root = JSONObject(jsonStr)
            val items = root.optJSONArray("items") ?: JSONArray()
            buildList {
                for (i in 0 until items.length()) {
                    val snippet = items.optJSONObject(i)?.optJSONObject("snippet")
                    val resourceId = snippet?.optJSONObject("resourceId")
                    val channelId = resourceId?.optString("channelId").orEmpty()
                    if (channelId.isNotBlank()) add(channelId)
                }
            }
        }.getOrDefault(emptyList())

        if (channelIds.isEmpty()) return@coroutineScope emptyList()

        val allVideos = channelIds.map { channelId ->
            async(Dispatchers.IO) {
                fetchChannelRssVideos(channelId).take(if (sortByNewest) 3 else 2)
            }
        }.awaitAll().flatten()

        if (sortByNewest) {
            allVideos.sortedByDescending { it.publishedEpochMs }
        } else {
            allVideos
        }
    }

    private fun fetchHomeActivitiesViaOAuth(oauthToken: String): List<YouTubeVideoItem> {
        return runCatching {
            val url = URL("https://www.googleapis.com/youtube/v3/activities?part=snippet,contentDetails&mine=true&maxResults=14")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Authorization", "Bearer $oauthToken")
                setRequestProperty("Accept", "application/json")
            }
            val jsonStr = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
            val root = JSONObject(jsonStr)
            val items = root.optJSONArray("items") ?: JSONArray()
            val out = mutableListOf<YouTubeVideoItem>()
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val snippet = obj.optJSONObject("snippet") ?: continue
                val contentDetails = obj.optJSONObject("contentDetails")
                val videoId = contentDetails?.optJSONObject("upload")?.optString("videoId")
                    ?.ifBlank { contentDetails.optJSONObject("recommendation")?.optJSONObject("resourceId")?.optString("videoId") }
                    ?.ifBlank { contentDetails.optJSONObject("playlistItem")?.optJSONObject("resourceId")?.optString("videoId") }
                    .orEmpty()

                if (videoId.isBlank()) continue
                val title = snippet.optString("title").ifBlank { continue }
                val channel = snippet.optString("channelTitle").ifBlank { "YouTube" }
                val publishedAt = snippet.optString("publishedAt")
                out.add(
                    YouTubeVideoItem(
                        id = videoId,
                        title = title,
                        channelName = channel,
                        durationText = "",
                        thumbnailUrl = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
                        publishedEpochMs = parseIsoDateMs(publishedAt)
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    private fun fetchChannelRssVideos(channelId: String): List<YouTubeVideoItem> {
        return runCatching {
            val url = URL("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            val xml = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            val videos = mutableListOf<YouTubeVideoItem>()
            var inEntry = false
            var videoId = ""
            var title = ""
            var authorName = ""
            var published = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "entry" -> {
                                inEntry = true
                                videoId = ""
                                title = ""
                                authorName = ""
                                published = ""
                            }
                            "yt:videoId" -> if (inEntry) videoId = parser.nextText().trim()
                            "title" -> if (inEntry && title.isEmpty()) title = parser.nextText().trim()
                            "name" -> if (inEntry && authorName.isEmpty()) authorName = parser.nextText().trim()
                            "published" -> if (inEntry && published.isEmpty()) published = parser.nextText().trim()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "entry" && inEntry) {
                            if (videoId.isNotBlank() && title.isNotBlank()) {
                                videos.add(
                                    YouTubeVideoItem(
                                        id = videoId,
                                        title = title,
                                        channelName = authorName.ifBlank { "YouTube" },
                                        durationText = "",
                                        thumbnailUrl = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
                                        publishedEpochMs = parseIsoDateMs(published)
                                    )
                                )
                            }
                            inEntry = false
                        }
                    }
                }
                eventType = parser.next()
            }
            videos
        }.getOrDefault(emptyList())
    }

    private fun parseIsoDateMs(iso: String): Long {
        if (iso.isBlank()) return 0L
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(iso)?.time
            }.getOrNull()
            if (parsed != null && parsed > 0L) return parsed
        }
        return 0L
    }

    private fun fetchYouTubeInnerTubeBrowse(
        browseId: String,
        oauthToken: String?
    ): List<YouTubeVideoItem> {
        val seenIds = hashSetOf<String>()
        val videos = mutableListOf<YouTubeVideoItem>()

        // Tenta com cliente TVHTML5 se houver token OAuth de TV, e também WEB (com OAuth ou Cookies SAPISID)
        if (!oauthToken.isNullOrBlank()) {
            executeInnerTubeBrowseRequest(
                browseId = browseId,
                continuation = null,
                oauthToken = oauthToken,
                useTvClient = true
            )?.let { root ->
                collectVideoRenderers(root, videos, seenIds)
            }
        }

        if (videos.size < 6) {
            executeInnerTubeBrowseRequest(
                browseId = browseId,
                continuation = null,
                oauthToken = oauthToken,
                useTvClient = false
            )?.let { root ->
                collectVideoRenderers(root, videos, seenIds)
            }
        }

        return videos
    }

    private fun executeInnerTubeBrowseRequest(
        browseId: String?,
        continuation: String?,
        oauthToken: String?,
        useTvClient: Boolean
    ): JSONObject? {
        val cookies = runCatching {
            CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
        }.getOrDefault("")

        if (oauthToken.isNullOrBlank() &&
            !cookies.contains("SAPISID=") &&
            !cookies.contains("__Secure-3PAPISID=")
        ) {
            return null
        }

        return runCatching {
            val url = URL("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9")
                setRequestProperty("Origin", "https://www.youtube.com")
                if (!oauthToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $oauthToken")
                } else if (cookies.isNotBlank()) {
                    setRequestProperty("Cookie", cookies)
                    extractSapisid(cookies)?.let { sapisid ->
                        setRequestProperty("Authorization", buildSapisidHashHeader(sapisid))
                    }
                }
            }

            val payload = JSONObject().apply {
                if (!browseId.isNullOrBlank()) {
                    put("browseId", browseId)
                }
                if (!continuation.isNullOrBlank()) {
                    put("continuation", continuation)
                }
                put(
                    "context",
                    JSONObject().apply {
                        put(
                            "client",
                            JSONObject().apply {
                                if (useTvClient) {
                                    put("clientName", "TVHTML5")
                                    put("clientVersion", "7.20250201.16.00")
                                } else {
                                    put("clientName", "WEB")
                                    put("clientVersion", "2.20250201.00.00")
                                }
                                put("hl", "pt")
                                put("gl", "BR")
                            }
                        )
                    }
                )
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            val responseStr = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }

            JSONObject(responseStr)
        }.getOrNull()
    }

    private fun fetchYouTubeFeedFromWeb(urlString: String): List<YouTubeVideoItem> {
        val cookies = runCatching {
            CookieManager.getInstance().getCookie("https://www.youtube.com") ?: ""
        }.getOrDefault("")

        if (!cookies.contains("SAPISID=") && !cookies.contains("__Secure-3PAPISID=")) {
            return emptyList()
        }

        return runCatching {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                )
                setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8")
                setRequestProperty("Cookie", cookies)
                extractSapisid(cookies)?.let { sapisid ->
                    setRequestProperty("Authorization", buildSapisidHashHeader(sapisid))
                    setRequestProperty("Origin", "https://www.youtube.com")
                }
            }

            val html = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }

            parseYtInitialData(html)
        }.getOrDefault(emptyList())
    }

    private fun parseYtInitialData(html: String): List<YouTubeVideoItem> {
        val markers = listOf("var ytInitialData = ", "window[\"ytInitialData\"] = ", "ytInitialData = ")
        var jsonStart = -1
        for (marker in markers) {
            val idx = html.indexOf(marker)
            if (idx != -1) {
                jsonStart = idx + marker.length
                break
            }
        }
        if (jsonStart == -1) return emptyList()

        val endIdx = html.indexOf(";</script>", jsonStart)
        if (endIdx == -1) return emptyList()

        val jsonStr = html.substring(jsonStart, endIdx).trim()
        val root = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return emptyList()

        val videos = mutableListOf<YouTubeVideoItem>()
        collectVideoRenderers(root, videos, hashSetOf())
        return videos
    }

    private fun collectVideoRenderers(
        node: Any?,
        out: MutableList<YouTubeVideoItem>,
        seenIds: HashSet<String>
    ) {
        if (out.size >= 18 || node == null) return

        when (node) {
            is JSONObject -> {
                // 1. Novo modelo do YouTube 2025/2026: lockupViewModel
                if (node.has("lockupViewModel")) {
                    parseLockupViewModel(node.optJSONObject("lockupViewModel"))?.let { item ->
                        if (seenIds.add(item.id)) {
                            out.add(item)
                        }
                    }
                }
                // 2. Modelo do cliente de TV (TVHTML5): tileRenderer
                if (node.has("tileRenderer")) {
                    parseTileRenderer(node.optJSONObject("tileRenderer"))?.let { item ->
                        if (seenIds.add(item.id)) {
                            out.add(item)
                        }
                    }
                }
                // 3. Modelos clássicos e de grade: videoRenderer, gridVideoRenderer, compactVideoRenderer, videoWithContextRenderer
                for (rendererKey in listOf(
                    "videoRenderer",
                    "gridVideoRenderer",
                    "compactVideoRenderer",
                    "videoWithContextRenderer"
                )) {
                    if (node.has(rendererKey)) {
                        parseVideoRenderer(node.optJSONObject(rendererKey))?.let { item ->
                            if (seenIds.add(item.id)) {
                                out.add(item)
                            }
                        }
                    }
                }

                val keys = node.keys()
                while (keys.hasNext() && out.size < 18) {
                    val key = keys.next()
                    collectVideoRenderers(node.opt(key), out, seenIds)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    if (out.size >= 18) break
                    collectVideoRenderers(node.opt(i), out, seenIds)
                }
            }
        }
    }

    /**
     * Analisa o formato `tileRenderer` retornado pelo cliente `TVHTML5` do YouTube.
     */
    private fun parseTileRenderer(tile: JSONObject?): YouTubeVideoItem? {
        if (tile == null) return null
        val videoId = tile.optJSONObject("onSelectCommand")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it.length == 11 }
            ?: tile.optString("contentId").takeIf { it.isNotBlank() && it.length == 11 }
            ?: return null

        val metadata = tile.optJSONObject("metadata")?.optJSONObject("tileMetadataRenderer")
        val title = extractText(metadata?.optJSONObject("title"))
            .ifBlank { extractText(tile.optJSONObject("header")?.optJSONObject("tileHeaderRenderer")?.optJSONObject("title")) }
            .takeIf { it.isNotBlank() } ?: return null

        var channelName = "YouTube"
        var publishedText = ""
        val lines = metadata?.optJSONArray("lines")
        if (lines != null && lines.length() > 0) {
            val firstLine = extractText(lines.optJSONObject(0)?.optJSONObject("lineRenderer"))
                .ifBlank {
                    val items = lines.optJSONObject(0)?.optJSONObject("lineRenderer")?.optJSONArray("items")
                    items?.optJSONObject(0)?.optJSONObject("lineItemRenderer")?.let { extractText(it.optJSONObject("text")) }.orEmpty()
                }
            if (firstLine.isNotBlank()) channelName = firstLine
            if (lines.length() > 1) {
                publishedText = extractText(lines.optJSONObject(1)?.optJSONObject("lineRenderer"))
            }
        }

        val duration = extractText(
            tile.optJSONObject("header")
                ?.optJSONObject("tileHeaderRenderer")
                ?.optJSONObject("thumbnailOverlays")
                ?.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                ?.optJSONObject("text")
        )

        return YouTubeVideoItem(
            id = videoId,
            title = title,
            channelName = channelName,
            durationText = duration,
            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
            publishedText = publishedText
        )
    }

    /**
     * Analisa o formato `lockupViewModel` introduzido pelo YouTube na Home e Inscrições.
     */
    private fun parseLockupViewModel(lockup: JSONObject?): YouTubeVideoItem? {
        if (lockup == null) return null
        val contentType = lockup.optString("contentType")
        if (contentType.isNotBlank() &&
            !contentType.contains("VIDEO", ignoreCase = true)
        ) {
            return null
        }

        val videoId = lockup.optString("contentId").takeIf { it.isNotBlank() && it.length == 11 }
            ?: return null

        val metadataVM = lockup.optJSONObject("metadata")
            ?.optJSONObject("lockupMetadataViewModel")

        val title = metadataVM?.optJSONObject("title")?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: return null

        var channelName = "YouTube"
        var publishedText = ""
        val metadataRows = metadataVM.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows")

        if (metadataRows != null && metadataRows.length() > 0) {
            val firstRowParts = metadataRows.optJSONObject(0)?.optJSONArray("metadataParts")
            val candidateChannel = firstRowParts?.optJSONObject(0)
                ?.optJSONObject("text")
                ?.optString("content")
                .orEmpty()
            if (candidateChannel.isNotBlank()) {
                channelName = candidateChannel
            }
            if (metadataRows.length() > 1) {
                val secondRowParts = metadataRows.optJSONObject(1)?.optJSONArray("metadataParts")
                if (secondRowParts != null && secondRowParts.length() > 0) {
                    publishedText = secondRowParts.optJSONObject(secondRowParts.length() - 1)
                        ?.optJSONObject("text")
                        ?.optString("content")
                        .orEmpty()
                }
            }
        }

        var duration = ""
        val overlays = lockup.optJSONObject("contentImage")
            ?.optJSONObject("thumbnailViewModel")
            ?.optJSONArray("overlays")
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val badges = overlays.optJSONObject(i)
                    ?.optJSONObject("thumbnailOverlayBadgeViewModel")
                    ?.optJSONArray("thumbnailBadges") ?: continue
                for (j in 0 until badges.length()) {
                    val badgeText = badges.optJSONObject(j)
                        ?.optJSONObject("thumbnailBadgeViewModel")
                        ?.optString("text").orEmpty()
                    if (badgeText.contains(":")) {
                        duration = badgeText
                        break
                    }
                }
            }
        }

        return YouTubeVideoItem(
            id = videoId,
            title = title,
            channelName = channelName,
            durationText = duration,
            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
            publishedText = publishedText
        )
    }

    private fun parseVideoRenderer(vr: JSONObject?): YouTubeVideoItem? {
        if (vr == null) return null
        val videoId = vr.optString("videoId").takeIf { it.isNotBlank() } ?: return null

        val title = extractText(vr.optJSONObject("title"))
            .ifBlank { extractText(vr.optJSONObject("headline")) }
            .takeIf { it.isNotBlank() } ?: return null

        val ownerText = extractText(vr.optJSONObject("ownerText"))
            .ifBlank { extractText(vr.optJSONObject("longBylineText")) }
            .ifBlank { extractText(vr.optJSONObject("shortBylineText")) }
            .ifBlank { "YouTube" }

        val lengthText = extractText(vr.optJSONObject("lengthText"))
        val publishedText = extractText(vr.optJSONObject("publishedTimeText"))
        val thumbUrl = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"

        return YouTubeVideoItem(
            id = videoId,
            title = title,
            channelName = ownerText,
            durationText = lengthText,
            thumbnailUrl = thumbUrl,
            publishedText = publishedText
        )
    }

    private fun extractText(obj: JSONObject?): String {
        if (obj == null) return ""
        val simple = obj.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val content = obj.optString("content")
        if (content.isNotBlank()) return content
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString()
    }

    fun openVideoInYouTubeApp(video: YouTubeVideoItem) {
        if (!video.intentUri.isNullOrBlank()) {
            val parsedIntent = runCatching {
                Intent.parseUri(video.intentUri, Intent.URI_INTENT_SCHEME)
            }.getOrNull()
            if (parsedIntent != null) {
                parsedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(parsedIntent) }.isSuccess) return
            }
        }

        val watchUri = Uri.parse("https://www.youtube.com/watch?v=${video.id}")
        val tvPackages = listOf(
            "com.google.android.youtube.tv",
            "com.liskovsoft.smarttubetv.beta",
            "com.liskovsoft.smarttubetv",
            "org.smarttube.stable",
            "com.google.android.youtube"
        )

        for (pkg in tvPackages) {
            val intent = Intent(Intent.ACTION_VIEW, watchUri).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }

        val genericIntent = Intent(Intent.ACTION_VIEW, watchUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(genericIntent) }
    }

    private suspend fun attachThumbnails(items: List<YouTubeVideoItem>): List<YouTubeVideoItem> =
        coroutineScope {
            items.map { item ->
                async {
                    val bmp = downloadImageBitmap(item.thumbnailUrl)
                    item.copy(thumbnail = bmp)
                }
            }.awaitAll()
        }

    private fun downloadImageBitmap(urlStr: String): ImageBitmap? {
        if (urlStr.isBlank()) return null
        synchronized(thumbnailCache) {
            thumbnailCache.get(urlStr)?.let { return it }
        }
        return runCatching {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            val boundsOpts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)

            var sampleSize = 1
            while (boundsOpts.outWidth / (sampleSize * 2) >= 320 &&
                boundsOpts.outHeight / (sampleSize * 2) >= 180
            ) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
            val imageBitmap = decoded.asImageBitmap()
            synchronized(thumbnailCache) {
                thumbnailCache.put(urlStr, imageBitmap)
            }
            imageBitmap
        }.getOrNull()
    }

    private fun extractSapisid(cookieHeader: String): String? {
        val parts = cookieHeader.split(";")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("SAPISID=")) {
                return trimmed.removePrefix("SAPISID=")
            }
            if (trimmed.startsWith("__Secure-3PAPISID=")) {
                return trimmed.removePrefix("__Secure-3PAPISID=")
            }
        }
        return null
    }

    private fun buildSapisidHashHeader(sapisid: String): String {
        val timestamp = System.currentTimeMillis() / 1000
        val origin = "https://www.youtube.com"
        val input = "$timestamp $sapisid $origin"
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$hex"
    }

    companion object {
        private const val PREFS_NAME = "minimal_tv_launcher_prefs"
        private const val KEY_YOUTUBE_BOX_ENABLED = "youtube_box_enabled"
        private const val KEY_SELECTED_ACCOUNT = "youtube_selected_google_account"
        private const val KEY_TV_DEVICE_ID = "youtube_tv_device_id"
        private const val KEY_REFRESH_TOKEN_PREFIX = "yt_refresh_token_"
        private const val KEY_ACCESS_TOKEN_PREFIX = "yt_access_token_"
        private const val KEY_TOKEN_EXPIRY_PREFIX = "yt_token_expiry_"
        private const val KEY_COOKIES_PREFIX = "yt_cookies_"

        private const val YT_TV_CLIENT_ID =
            "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
        private const val YT_TV_CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT"
    }
}
