# Minimal TV Launcher

Launcher minimalista para Android TV desenvolvido em **Kotlin** com **Jetpack Compose**, focado em simplicidade, tela 100% preta (`#000000`) e zero recomendações ou anúncios.

## Funcionalidades
- **Tela Preta (OLED Friendly)**: Fundo `#000000` sem distrações ou banners promocionais.
- **Barra de Status Superior**:
  - **Hora e Data** atualizadas em tempo real com ícones dedicados.
  - **Ícone de Sinal**: Detecta automaticamente conexão via Cabo (Ethernet), nível de sinal Wi-Fi (0 a 4 barras) ou ausência de conexão.
  - **Gestão Rápida de Bluetooth (`Fones / BT`)**:
    - Exibe no topo o ícone de fone de ouvido e o **nome do fone conectado** automaticamente quando ativo.
    - Ao clicar no botão `Fones / BT`, abre o painel de dispositivos pareados (fones de ouvido, caixas de som, controles de videogame) permitindo **conectar ou desconectar com um clique (`OK`)** ou abrir as configurações de pareamento Bluetooth da TV.
  - **Botão de Seleção de Apps (`Apps`)**: Permite marcar/desmarcar manualmente quais aplicativos instalados aparecem na tela inicial.
  - **Botão `Tornar Padrão`**: Aparece automaticamente caso o app ainda não seja o launcher padrão da TV.
  - **Ícone de Configurações**: Abre diretamente as configurações do sistema da Android TV.
- **Inicialização Automática e Launcher Padrão Permanente**:
  - Possui `BootReceiver` (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED` e `QUICKBOOT_POWERON`) com `directBootAware="true"`, garantindo que o launcher inicie automaticamente mesmo após desligar a TV da tomada ou reiniciar.

## Como instalar e fixar como Launcher Padrão na TV

Você pode dar dois cliques no script [instalar_e_definir_padrao.bat](file:///C:/desnv/AndroidStudioProjects/MinimalTvLauncher/instalar_e_definir_padrao.bat) ou rodar os comandos manualmente no PowerShell:

```powershell
# 1. Conectar na TV via ADB
C:\Users\vinic\AppData\Local\Android\Sdk\platform-tools\adb.exe connect 192.168.31.98:5555

# 2. Instalar o APK já concedendo as permissões de Bluetooth (-g)
C:\Users\vinic\AppData\Local\Android\Sdk\platform-tools\adb.exe -s 192.168.31.98:5555 install -r -g "C:\desnv\AndroidStudioProjects\MinimalTvLauncher\app\build\outputs\apk\debug\app-debug.apk"

# 3. Definir como atividade Home padrão do Android
C:\Users\vinic\AppData\Local\Android\Sdk\platform-tools\adb.exe -s 192.168.31.98:5555 shell cmd package set-home-activity com.minimal.tvlauncher/.MainActivity

# 4. (Recomendado em Google TV / Android TV) Desativar o launcher original para que a TV nunca volte para ele ao reiniciar:
C:\Users\vinic\AppData\Local\Android\Sdk\platform-tools\adb.exe -s 192.168.31.98:5555 shell pm disable-user --user 0 com.google.android.tvlauncher
C:\Users\vinic\AppData\Local\Android\Sdk\platform-tools\adb.exe -s 192.168.31.98:5555 shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
C:\Users\vinic\AppData\Local\Android\Sdk\platform-tools\adb.exe -s 192.168.31.98:5555 shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```
