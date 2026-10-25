package com.minimal.tvlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.minimal.tvlauncher.data.AppRepository
import com.minimal.tvlauncher.model.AppInfo
import com.minimal.tvlauncher.network.NetworkSignalMonitor
import com.minimal.tvlauncher.network.NetworkStatus
import com.minimal.tvlauncher.ui.LauncherScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var appRepository: AppRepository
    private lateinit var networkSignalMonitor: NetworkSignalMonitor

    private var apps by mutableStateOf<List<AppInfo>>(emptyList())
    private var isLoading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appRepository = AppRepository(applicationContext)
        networkSignalMonitor = NetworkSignalMonitor(applicationContext)

        setContent {
            val networkStatus by networkSignalMonitor
                .observeNetworkStatus()
                .collectAsState(initial = NetworkStatus())

            LauncherScreen(
                apps = apps,
                isLoading = isLoading,
                networkStatus = networkStatus,
                onLaunchApp = { packageName ->
                    appRepository.launchApp(packageName)
                },
                onToggleAppSelection = { packageName ->
                    val updatedSelection = appRepository.toggleAppSelection(packageName)
                    apps = apps.map { app ->
                        app.copy(isSelected = updatedSelection.contains(app.packageName))
                    }
                },
                onOpenSettings = {
                    appRepository.openSystemSettings()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshInstalledApps()
    }

    private fun refreshInstalledApps() {
        lifecycleScope.launch {
            val loaded = appRepository.loadInstalledApps()
            apps = loaded
            isLoading = false
        }
    }
}
