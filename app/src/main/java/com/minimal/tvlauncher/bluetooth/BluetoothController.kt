package com.minimal.tvlauncher.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.UUID

enum class BtDeviceCategory {
    HEADPHONES_AUDIO,
    GAMEPAD,
    OTHER
}

data class BtDeviceInfo(
    val name: String,
    val address: String,
    val isConnected: Boolean,
    val category: BtDeviceCategory,
    val rawDevice: BluetoothDevice
)

data class BluetoothUiState(
    val isEnabled: Boolean = false,
    val hasPermission: Boolean = true,
    val pairedDevices: List<BtDeviceInfo> = emptyList(),
    val connectedAudioDeviceName: String? = null,
    val connectingAddress: String? = null,
    val statusMessage: String? = null
)

@SuppressLint("MissingPermission")
class BluetoothController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var a2dpProfile: BluetoothProfile? = null
    private var headsetProfile: BluetoothProfile? = null
    private var hidProfile: BluetoothProfile? = null

    private val _state = MutableStateFlow(BluetoothUiState())
    val state: StateFlow<BluetoothUiState> = _state.asStateFlow()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/")
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProfile = proxy
                BluetoothProfile.HEADSET -> headsetProfile = proxy
                PROFILE_HID_HOST -> hidProfile = proxy
            }
            refreshState()
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProfile = null
                BluetoothProfile.HEADSET -> headsetProfile = null
                PROFILE_HID_HOST -> hidProfile = null
            }
            refreshState()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshState()
        }
    }

    private var isReceiverRegistered = false

    fun startMonitoring() {
        runCatching {
            adapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
            adapter?.getProfileProxy(context, profileListener, PROFILE_HID_HOST)
        }
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            }
            runCatching {
                context.registerReceiver(bluetoothReceiver, filter)
                isReceiverRegistered = true
            }
        }
        refreshState()
    }

    fun stopMonitoring() {
        if (isReceiverRegistered) {
            runCatching {
                context.unregisterReceiver(bluetoothReceiver)
                isReceiverRegistered = false
            }
        }
        runCatching {
            a2dpProfile?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
            headsetProfile?.let { adapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
            hidProfile?.let { adapter?.closeProfileProxy(PROFILE_HID_HOST, it) }
        }
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun refreshState(
        connectingAddress: String? = _state.value.connectingAddress,
        statusMessage: String? = _state.value.statusMessage
    ) {
        val hasPerm = hasBluetoothPermission()
        val isBtEnabled = adapter?.isEnabled == true

        if (!hasPerm || adapter == null || !isBtEnabled) {
            _state.value = BluetoothUiState(
                isEnabled = isBtEnabled,
                hasPermission = hasPerm,
                pairedDevices = emptyList(),
                connectedAudioDeviceName = null,
                connectingAddress = null,
                statusMessage = null
            )
            return
        }

        val bonded = runCatching { adapter?.bondedDevices ?: emptySet() }.getOrDefault(emptySet())
        val connectedProfileAddresses = buildSet {
            runCatching { a2dpProfile?.connectedDevices?.forEach { add(it.address) } }
            runCatching { headsetProfile?.connectedDevices?.forEach { add(it.address) } }
            runCatching { hidProfile?.connectedDevices?.forEach { add(it.address) } }
        }

        val deviceList = bonded.map { device ->
            val connected = connectedProfileAddresses.contains(device.address) ||
                isDeviceConnectedReflective(device)
            val category = classifyDevice(device)
            BtDeviceInfo(
                name = device.name?.takeIf { it.isNotBlank() } ?: device.address,
                address = device.address,
                isConnected = connected,
                category = category,
                rawDevice = device
            )
        }.sortedWith(
            compareByDescending<BtDeviceInfo> { it.isConnected }
                .thenBy { it.category.ordinal }
                .thenBy { it.name.lowercase() }
        )

        val connectedAudio = deviceList.firstOrNull {
            it.isConnected && it.category == BtDeviceCategory.HEADPHONES_AUDIO
        } ?: deviceList.firstOrNull { it.isConnected }

        _state.value = BluetoothUiState(
            isEnabled = true,
            hasPermission = true,
            pairedDevices = deviceList,
            connectedAudioDeviceName = connectedAudio?.name,
            connectingAddress = connectingAddress,
            statusMessage = statusMessage
        )
    }

    /**
     * Conecta ou desconecta automaticamente o dispositivo pareado direto em segundo plano,
     * aguardando o handshake A2DP/HID completar sem interrompê-lo.
     */
    fun toggleDeviceConnection(deviceInfo: BtDeviceInfo) {
        if (_state.value.connectingAddress != null) return

        val targetConnect = !deviceInfo.isConnected
        val actionLabel = if (targetConnect) {
            "Conectando a ${deviceInfo.name}... (aguarde até 8s)"
        } else {
            "Desconectando ${deviceInfo.name}..."
        }

        Log.d(TAG, "toggleDeviceConnection: ${deviceInfo.name} (${deviceInfo.address}) targetConnect=$targetConnect")

        refreshState(
            connectingAddress = deviceInfo.address,
            statusMessage = actionLabel
        )

        scope.launch {
            runCatching { adapter?.cancelDiscovery() }

            val device = deviceInfo.rawDevice
            val methodName = if (targetConnect) "connect" else "disconnect"

            // Escolhe o perfil principal adequado para o dispositivo (A2DP para fones/caixas, HID para controles)
            val profilesToTry = when (deviceInfo.category) {
                BtDeviceCategory.GAMEPAD -> listOfNotNull(hidProfile, a2dpProfile)
                else -> listOfNotNull(a2dpProfile, hidProfile, headsetProfile)
            }

            var profileStarted = false
            for (proxy in profilesToTry) {
                if (targetConnect) {
                    enableAutoConnectPolicy(proxy, device)
                }
                val ok = invokeProfileConnectOrDisconnect(proxy, methodName, device)
                Log.d(TAG, "invokeProfile ${proxy.javaClass.simpleName}.$methodName(${device.address}) -> $ok")
                if (ok) {
                    profileStarted = true
                    if (targetConnect) {
                        // Define também como dispositivo de áudio ativo no A2DP
                        setActiveAudioDevice(proxy, device)
                    }
                    break
                }
            }

            // Se o A2DP recusou o connect (por exemplo quando connectionPolicy == 0 FORBIDDEN na TV),
            // acordamos o link ACL via socket RFCOMM sem fechar imediatamente e tentamos novamente.
            if (targetConnect && !profileStarted && !isDeviceConnectingOrConnected(device)) {
                Log.d(TAG, "Profile connect returned false; waking ACL link via socket for ${device.address}")
                wakeUpDeviceViaSocket(device)
                for (proxy in profilesToTry) {
                    enableAutoConnectPolicy(proxy, device)
                    if (invokeProfileConnectOrDisconnect(proxy, "connect", device)) {
                        setActiveAudioDevice(proxy, device)
                        break
                    }
                }
            } else if (!targetConnect && !profileStarted) {
                invokeDeviceMethod(device, "disconnect")
            }

            // Aguarda até 10 segundos enquanto o A2dpStateMachine estiver em CONNECTING (1)
            // sem disparar comandos extras que abortem o handshake AVDTP!
            for (step in 1..20) {
                delay(500)
                val nowConnected = isDeviceCurrentlyConnected(device)
                val nowConnecting = isDeviceCurrentlyConnecting(device)
                if (nowConnected == targetConnect) {
                    if (targetConnect) {
                        a2dpProfile?.let { setActiveAudioDevice(it, device) }
                    }
                    break
                }
                // Se já saiu do estado CONNECTING e após 4 segundos continua desconectado, encerra a espera
                if (targetConnect && !nowConnecting && step >= 8) {
                    break
                }
            }

            val finalConnected = isDeviceCurrentlyConnected(device)
            val resultMsg = when {
                targetConnect && finalConnected -> "${deviceInfo.name} conectado com sucesso!"
                !targetConnect && !finalConnected -> "${deviceInfo.name} desconectado."
                targetConnect -> "Não foi possível conectar a ${deviceInfo.name}. Verifique se o fone está ligado e próximo da TV."
                else -> "Estado atualizado para ${deviceInfo.name}"
            }

            refreshState(
                connectingAddress = null,
                statusMessage = resultMsg
            )

            delay(4000)
            if (_state.value.statusMessage == resultMsg) {
                refreshState(connectingAddress = null, statusMessage = null)
            }
        }
    }

    private fun invokeProfileConnectOrDisconnect(
        proxy: BluetoothProfile,
        methodName: String,
        device: BluetoothDevice
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val bypassed = runCatching {
                HiddenApiBypass.invoke(
                    proxy.javaClass,
                    proxy,
                    methodName,
                    device
                ) as? Boolean ?: false
            }.onFailure {
                Log.w(TAG, "HiddenApiBypass $methodName failed: ${it.message}")
            }.getOrDefault(false)
            if (bypassed) return true
        }

        return runCatching {
            val method = proxy.javaClass.getDeclaredMethod(methodName, BluetoothDevice::class.java)
            method.isAccessible = true
            method.invoke(proxy, device) as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun enableAutoConnectPolicy(proxy: BluetoothProfile, device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                HiddenApiBypass.invoke(
                    proxy.javaClass,
                    proxy,
                    "setPriority",
                    device,
                    100 // PRIORITY_ON
                )
            }
            runCatching {
                HiddenApiBypass.invoke(
                    proxy.javaClass,
                    proxy,
                    "setConnectionPolicy",
                    device,
                    100 // CONNECTION_POLICY_ALLOWED
                )
            }
        }
    }

    private fun setActiveAudioDevice(proxy: BluetoothProfile, device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                HiddenApiBypass.invoke(
                    proxy.javaClass,
                    proxy,
                    "setActiveDevice",
                    device
                )
            }
        }
    }

    private fun wakeUpDeviceViaSocket(device: BluetoothDevice) {
        var socket: BluetoothSocket? = null
        val connected = runCatching {
            socket = device.createInsecureRfcommSocketToServiceRecord(UUID_HANDSFREE)
            socket?.connect()
            true
        }.getOrDefault(false)

        if (connected) {
            runCatching { Thread.sleep(600) }
            runCatching { socket?.close() }
            return
        } else {
            runCatching { socket?.close() }
        }
    }

    private fun invokeDeviceMethod(device: BluetoothDevice, methodName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.invoke(BluetoothDevice::class.java, device, methodName)
                true
            } else {
                val m = device.javaClass.getDeclaredMethod(methodName)
                m.isAccessible = true
                m.invoke(device)
                true
            }
        }.getOrDefault(false)
    }

    private fun isDeviceCurrentlyConnecting(device: BluetoothDevice): Boolean {
        return runCatching {
            a2dpProfile?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTING ||
                hidProfile?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTING ||
                headsetProfile?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTING
        }.getOrDefault(false)
    }

    private fun isDeviceConnectingOrConnected(device: BluetoothDevice): Boolean {
        return isDeviceCurrentlyConnecting(device) || isDeviceCurrentlyConnected(device)
    }

    private fun isDeviceCurrentlyConnected(device: BluetoothDevice): Boolean {
        val inProfiles = runCatching {
            a2dpProfile?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED ||
                hidProfile?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED ||
                headsetProfile?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
        }.getOrDefault(false)
        return inProfiles || isDeviceConnectedReflective(device)
    }

    fun openBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = runCatching { context.startActivity(intent) }.isSuccess
        if (!started) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }

    private fun isDeviceConnectedReflective(device: BluetoothDevice): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.invoke(BluetoothDevice::class.java, device, "isConnected") as? Boolean ?: false
            } else {
                val method = device.javaClass.getMethod("isConnected")
                method.invoke(device) as? Boolean ?: false
            }
        }.getOrDefault(false)
    }

    private fun classifyDevice(device: BluetoothDevice): BtDeviceCategory {
        val btClass = device.bluetoothClass ?: return BtDeviceCategory.OTHER
        val major = btClass.majorDeviceClass
        val devClass = btClass.deviceClass

        return when {
            major == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> {
                BtDeviceCategory.HEADPHONES_AUDIO
            }
            major == BluetoothClass.Device.Major.PERIPHERAL -> {
                BtDeviceCategory.GAMEPAD
            }
            else -> BtDeviceCategory.OTHER
        }
    }

    companion object {
        private const val TAG = "MinimalTvBT"
        private const val PROFILE_HID_HOST = 4
        private val UUID_HANDSFREE: UUID = UUID.fromString("0000111E-0000-1000-8000-00805F9B34FB")
    }
}
