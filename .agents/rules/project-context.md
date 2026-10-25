---
description: Contexto arquitetural, ambiente da Android TV e decisões técnicas do projeto MinimalTvLauncher
---

# Contexto Técnico do Projeto MinimalTvLauncher

## 1. Localização e Repositório
- **Diretório do projeto**: `C:\desnv\AndroidStudioProjects\MinimalTvLauncher`
- **Repositório Git**: `https://github.com/ViniciusCampanholi/MinimalTvLauncher.git` (branch padrão de trabalho: `developer`)
- **ADB SDK local**: `C:\Users\vinic\AppData\Local\Android\Sdk\platform-tools\adb.exe`

## 2. Dispositivo Alvo (Android TV Real)
- **Endereço ADB**: `192.168.31.98:5555`
- **Hardware / Sistema**: TCL Smart TV (Android 11 / API 30)
- **Resolução e Densidade**: `1920x1080` com `density=2.0`, o que resulta em **`960dp x 540dp`** úteis no Jetpack Compose.
  - **Atenção ao Layout Vertical (`540dp`)**: Sempre utilizar `.clipToBounds()` na caixa `weight(1f)` da grade de aplicativos (`HomeAppsGrid`) e ativar o `compactMode` quando a caixa de recomendações do YouTube estiver visível para impedir sobreposição vertical.
  - **Captura de Screenshots via ADB no PowerShell**: Nunca usar redirecionamento `>` do PowerShell com `exec-out screencap -p` (corrompe o binário PNG para UTF-16LE). Sempre usar:
    `adb -s 192.168.31.98:5555 shell screencap -p /sdcard/screen.png` seguido de `adb -s 192.168.31.98:5555 pull /sdcard/screen.png <destino>`.

## 3. Arquitetura e Módulos Principais
- **`MainActivity.kt`**:
  - Gerencia o estado global de UI, permissões de runtime (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `GET_ACCOUNTS`, `READ_CONTACTS`), sincronização de contas Google da TV e polling assíncrono de autorização OAuth2 do YouTube.
- **`ui/LauncherScreen.kt` & `ui/WallpaperPresets.kt`**:
  - Interface 100% compatível com controle remoto (D-Pad focus, animações de escala, bordas de foco nítidas).
  - **Barra Superior (`TopStatusBar`)**: Data/Hora em tempo real (`pt-BR`), indicador de rede clicável (abre configurações de Wi-Fi/Rede), gestão de Bluetooth (`Fones / BT` exibindo o nome do fone conectado), seletor de apps (`Apps`), seletor de papéis de parede (`WallpaperPreset` com 6 temas renderizados em Canvas), botão de protetor de tela (`Bedtime`) e configurações do sistema.
- **`bluetooth/BluetoothController.kt`**:
  - Conecta e desconecta fones/caixas de som pareados diretamente em segundo plano (sem abrir telas de configuração do sistema).
  - **Detalhe crítico para Android 10/11 (API 29/30)**: Métodos `BluetoothA2dp.connect`, `BluetoothHeadset.connect` e `BluetoothHidDevice.connect` são `@hide` e bloqueados na hidden API blacklist para apps de terceiros (`NoSuchMethodException`), mas `BluetoothDevice.connect()` e `BluetoothDevice.disconnect()` funcionam via reflexão acompanhados de `setPriority(device, 100)` / `setConnectionPolicy(device, 100)` e fallback RFCOMM (`0000110b-0000-1000-8000-00805f9b34fb`).
- **`youtube/YouTubeRepository.kt` & `youtube/YouTubeAccountDialog.kt`**:
  - Identifica automaticamente as contas `com.google` logadas na Android TV via `AccountManager.getAccountsByType("com.google")`.
  - **Nunca exibir vídeos genéricos de busca**: As 3 abas (`Recomendados`, `Inscrições`, `Envios Recentes`) carregam exclusivamente dados da conta selecionada.
  - **Isolamento por conta**: Tokens OAuth2 (`yt_refresh_token_<email>`, `yt_access_token_<email>`) e cookies (`yt_cookies_<email>`) são armazenados separadamente por e-mail em `SharedPreferences`.
  - **Autenticação em Android TV**:
    - Não passar `activity` diretamente em `AccountManager.getAuthToken` (usar a sobrecarga com `notifyAuthFailure = false`) para não empilhar janelas `GrantCredentialsWithAclActivity` do GMS na TV.
    - Suporta o fluxo nativo de TV (`POST https://www.youtube.com/o/oauth2/device/code` e `POST https://www.youtube.com/o/oauth2/token` com `TVHTML5`), WebView pré-preenchido com `Email=<conta>` (`SAPISIDHASH` na API InnerTube `https://www.youtube.com/youtubei/v1/browse`) e YouTube Data API v3 + feed RSS cronológico de canais inscritos para a aba `Envios Recentes`.
- **`data/AppRepository.kt` & `boot/BootReceiver.kt`**:
  - Lista apps tanto com `CATEGORY_LEANBACK_LAUNCHER` quanto `CATEGORY_LAUNCHER`.
  - Abre Wi-Fi tentando a Activity do `com.android.tv.settings` (` ConnectivityActivity` / `NetworkActivity`) com fallback para `Settings.ACTION_WIFI_SETTINGS`.
  - Ativa o protetor de tela nativo (Daydream) via `IDreamManager.dream()` por reflexão ou `com.android.systemui.somnambulator.Somnambulator`.
