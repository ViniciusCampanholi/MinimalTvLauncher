package com.minimal.tvlauncher.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class ConnectionType {
    NONE,
    ETHERNET,
    WIFI,
    OTHER
}

data class NetworkStatus(
    val type: ConnectionType = ConnectionType.NONE,
    val signalLevel: Int = 0 // 0..4 para Wi-Fi
)

class NetworkSignalMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun observeNetworkStatus(): Flow<NetworkStatus> = callbackFlow {
        fun getCurrentStatus(): NetworkStatus {
            val activeNetwork = connectivityManager.activeNetwork ?: return NetworkStatus()
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
                ?: return NetworkStatus()

            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    NetworkStatus(type = ConnectionType.ETHERNET, signalLevel = 4)
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    val level = getWifiSignalLevel(caps)
                    NetworkStatus(type = ConnectionType.WIFI, signalLevel = level)
                }
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> {
                    NetworkStatus(type = ConnectionType.OTHER, signalLevel = 4)
                }
                else -> NetworkStatus()
            }
        }

        trySend(getCurrentStatus())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getCurrentStatus())
            }

            override fun onLost(network: Network) {
                trySend(getCurrentStatus())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(getCurrentStatus())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        runCatching {
            connectivityManager.registerNetworkCallback(request, callback)
        }

        awaitClose {
            runCatching {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }
    }.distinctUntilChanged()

    @Suppress("DEPRECATION")
    private fun getWifiSignalLevel(caps: NetworkCapabilities): Int {
        val rssi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiInfo = caps.transportInfo as? WifiInfo
            wifiInfo?.rssi ?: wifiManager?.connectionInfo?.rssi ?: -100
        } else {
            wifiManager?.connectionInfo?.rssi ?: -100
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wifiManager?.calculateSignalLevel(rssi)?.coerceIn(0, 4) ?: 0
        } else {
            WifiManager.calculateSignalLevel(rssi, 5).coerceIn(0, 4)
        }
    }
}
