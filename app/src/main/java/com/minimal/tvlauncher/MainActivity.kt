package com.minimal.tvlauncher

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.minimal.tvlauncher.bluetooth.BluetoothController
import com.minimal.tvlauncher.data.AppRepository
import com.minimal.tvlauncher.model.AppInfo
import com.minimal.tvlauncher.network.NetworkSignalMonitor
import com.minimal.tvlauncher.network.NetworkStatus
import com.minimal.tvlauncher.ui.LauncherScreen
import com.minimal.tvlauncher.ui.WallpaperPreset
import com.minimal.tvlauncher.youtube.YouTubeDeviceCodeInfo
import com.minimal.tvlauncher.youtube.YouTubeFeedTab
import com.minimal.tvlauncher.youtube.YouTubeRepository
import com.minimal.tvlauncher.youtube.YouTubeVideoItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var appRepository: AppRepository
    private lateinit var networkSignalMonitor: NetworkSignalMonitor
    private lateinit var bluetoothController: BluetoothController
    private lateinit var youTubeRepository: YouTubeRepository

    private var apps by mutableStateOf<List<AppInfo>>(emptyList())
    private var isLoading by mutableStateOf(true)
    private var isDefaultLauncher by mutableStateOf(false)
    private var selectedWallpaper by mutableStateOf(WallpaperPreset.PURE_BLACK)

    private var isYouTubeBoxEnabled by mutableStateOf(false)
    private var isYouTubeLoading by mutableStateOf(false)
    private var deviceGoogleAccounts by mutableStateOf<List<String>>(emptyList())
    private var selectedYouTubeAccount by mutableStateOf<String?>(null)
    private var selectedYouTubeTab by mutableStateOf(YouTubeFeedTab.RECOMMENDED)
    private var youTubeVideos by mutableStateOf<List<YouTubeVideoItem>>(emptyList())
    private var youTubeDeviceCodeInfo by mutableStateOf<YouTubeDeviceCodeInfo?>(null)
    private var showYouTubeWebLogin by mutableStateOf(false)

    private var youTubeLoadJob: Job? = null
    private var deviceAuthPollingJob: Job? = null

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        bluetoothController.refreshState()
        syncDeviceGoogleAccounts()
        if (isYouTubeBoxEnabled) {
            refreshYouTubeRecommendations()
        }
    }

    private val accountPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val chosenName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!chosenName.isNullOrBlank()) {
                youTubeRepository.setSelectedAccountName(chosenName)
                syncDeviceGoogleAccounts()
                refreshYouTubeRecommendations()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appRepository = AppRepository(applicationContext)
        networkSignalMonitor = NetworkSignalMonitor(applicationContext)
        bluetoothController = BluetoothController(applicationContext)
        youTubeRepository = YouTubeRepository(applicationContext)

        selectedWallpaper = appRepository.getSelectedWallpaper()
        isYouTubeBoxEnabled = youTubeRepository.isYouTubeBoxEnabled()
        syncDeviceGoogleAccounts()

        val requestedRuntimePerms = requestRuntimePermissionsIfNeeded()
        bluetoothController.startMonitoring()

        if (isYouTubeBoxEnabled && !requestedRuntimePerms) {
            refreshYouTubeRecommendations()
        }

        setContent {
            val networkStatus by networkSignalMonitor
                .observeNetworkStatus()
                .collectAsState(initial = NetworkStatus())

            val bluetoothState by bluetoothController
                .state
                .collectAsState()

            LauncherScreen(
                apps = apps,
                isLoading = isLoading,
                networkStatus = networkStatus,
                bluetoothState = bluetoothState,
                isDefaultLauncher = isDefaultLauncher,
                selectedWallpaper = selectedWallpaper,
                isYouTubeBoxEnabled = isYouTubeBoxEnabled,
                isYouTubeLoading = isYouTubeLoading,
                deviceGoogleAccounts = deviceGoogleAccounts,
                selectedYouTubeAccount = selectedYouTubeAccount,
                selectedYouTubeTab = selectedYouTubeTab,
                youTubeVideos = youTubeVideos,
                youTubeDeviceCodeInfo = youTubeDeviceCodeInfo,
                showYouTubeWebLogin = showYouTubeWebLogin,
                isYouTubeAccountAuthenticated = { accountName ->
                    youTubeRepository.isAccountAuthenticated(accountName)
                },
                onLaunchApp = { packageName ->
                    appRepository.launchApp(packageName)
                },
                onToggleAppSelection = { packageName ->
                    val updatedSelection = appRepository.toggleAppSelection(packageName)
                    apps = apps.map { app ->
                        app.copy(isSelected = updatedSelection.contains(app.packageName))
                    }
                },
                onSelectWallpaper = { preset ->
                    selectedWallpaper = preset
                    appRepository.setSelectedWallpaper(preset)
                },
                onStartScreensaver = {
                    appRepository.startScreensaver()
                },
                onToggleYouTubeBox = {
                    val newValue = !isYouTubeBoxEnabled
                    isYouTubeBoxEnabled = newValue
                    youTubeRepository.setYouTubeBoxEnabled(newValue)
                    if (newValue) {
                        syncDeviceGoogleAccounts()
                        if (selectedYouTubeAccount == null && deviceGoogleAccounts.isEmpty()) {
                            openSystemAccountPicker()
                        } else {
                            refreshYouTubeRecommendations()
                        }
                    } else {
                        deviceAuthPollingJob?.cancel()
                    }
                },
                onSelectYouTubeTab = { tab ->
                    if (selectedYouTubeTab != tab) {
                        selectedYouTubeTab = tab
                        refreshYouTubeRecommendations(tab)
                    }
                },
                onRefreshYouTube = {
                    refreshYouTubeRecommendations()
                },
                onOpenYouTubeVideo = { video ->
                    youTubeRepository.openVideoInYouTubeApp(video)
                },
                onSelectYouTubeDeviceAccount = { accountName ->
                    deviceAuthPollingJob?.cancel()
                    youTubeDeviceCodeInfo = null
                    youTubeRepository.setSelectedAccountName(accountName)
                    selectedYouTubeAccount = accountName
                    refreshYouTubeRecommendations()
                },
                onOpenSystemAccountPicker = {
                    openSystemAccountPicker()
                },
                onOpenYouTubeWebLogin = {
                    showYouTubeWebLogin = true
                },
                onDismissYouTubeWebLogin = {
                    showYouTubeWebLogin = false
                },
                onYouTubeWebAuthCompleted = {
                    val activeAcc = selectedYouTubeAccount
                    if (!activeAcc.isNullOrBlank()) {
                        youTubeRepository.saveCurrentCookiesForAccount(activeAcc)
                    }
                    showYouTubeWebLogin = false
                    deviceAuthPollingJob?.cancel()
                    youTubeDeviceCodeInfo = null
                    refreshYouTubeRecommendations()
                },
                onToggleBluetoothDevice = { device ->
                    bluetoothController.toggleDeviceConnection(device)
                },
                onOpenBluetoothSettings = {
                    bluetoothController.openBluetoothSettings()
                },
                onRequestBluetoothPermission = {
                    requestRuntimePermissionsIfNeeded()
                },
                onSetDefaultLauncher = {
                    appRepository.promptSetDefaultLauncher(this@MainActivity)
                },
                onOpenWifiSettings = {
                    appRepository.openWifiSettings()
                },
                onOpenSettings = {
                    appRepository.openSystemSettings()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isDefaultLauncher = appRepository.isDefaultLauncher()
        syncDeviceGoogleAccounts()
        bluetoothController.refreshState()
        refreshInstalledApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        deviceAuthPollingJob?.cancel()
        bluetoothController.stopMonitoring()
    }

    private fun syncDeviceGoogleAccounts() {
        deviceGoogleAccounts = youTubeRepository.getDeviceGoogleAccounts()
        selectedYouTubeAccount = youTubeRepository.getSelectedAccountName()
    }

    @Suppress("DEPRECATION")
    private fun openSystemAccountPicker() {
        val intent = AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google"),
            false,
            null,
            null,
            null,
            null
        )
        runCatching { accountPickerLauncher.launch(intent) }
    }

    private fun refreshYouTubeRecommendations(tab: YouTubeFeedTab = selectedYouTubeTab) {
        youTubeLoadJob?.cancel()
        youTubeLoadJob = lifecycleScope.launch {
            isYouTubeLoading = true
            syncDeviceGoogleAccounts()
            val loadedVideos = youTubeRepository.loadRecommendations(this@MainActivity, tab)
            youTubeVideos = loadedVideos
            isYouTubeLoading = false

            val currentAcc = selectedYouTubeAccount
            if (loadedVideos.isEmpty() && !currentAcc.isNullOrBlank() && !youTubeRepository.isAccountAuthenticated(currentAcc)) {
                startDeviceAuthPolling(currentAcc)
            } else if (loadedVideos.isNotEmpty()) {
                deviceAuthPollingJob?.cancel()
                youTubeDeviceCodeInfo = null
            }
        }
    }

    private fun startDeviceAuthPolling(accountName: String) {
        val existingCode = youTubeDeviceCodeInfo
        if (deviceAuthPollingJob?.isActive == true &&
            existingCode != null &&
            existingCode.accountName.equals(accountName, ignoreCase = true)
        ) {
            return
        }

        deviceAuthPollingJob?.cancel()
        deviceAuthPollingJob = lifecycleScope.launch {
            val codeInfo = youTubeRepository.startTvDeviceAuth(accountName) ?: return@launch
            youTubeDeviceCodeInfo = codeInfo
            val intervalMs = (codeInfo.intervalSeconds.coerceAtLeast(5)) * 1000L

            while (isActive && isYouTubeBoxEnabled && selectedYouTubeAccount.equals(accountName, ignoreCase = true)) {
                delay(intervalMs)
                val authorized = youTubeRepository.pollTvDeviceAuthToken(codeInfo)
                if (authorized) {
                    youTubeDeviceCodeInfo = null
                    refreshYouTubeRecommendations()
                    break
                }
            }
        }
    }

    private fun requestRuntimePermissionsIfNeeded(): Boolean {
        val permissionsToRequest = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !bluetoothController.hasBluetoothPermission()
            ) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (!youTubeRepository.hasAccountsPermission()) {
                add(Manifest.permission.GET_ACCOUNTS)
                add(Manifest.permission.READ_CONTACTS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
            return true
        }
        return false
    }

    private fun refreshInstalledApps() {
        lifecycleScope.launch {
            val loaded = appRepository.loadInstalledApps()
            apps = loaded
            isLoading = false
        }
    }
}
