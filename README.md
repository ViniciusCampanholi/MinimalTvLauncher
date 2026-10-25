# Minimal TV Launcher

Launcher minimalista para Android TV desenvolvido em **Kotlin** com **Jetpack Compose**, focado em simplicidade, desempenho, navegação 100% adaptada ao controle remoto (D-Pad) e integração direta com os recursos da TV.

## Funcionalidades Principais

- **Barra de Status Superior**:
  - **Hora e Data** atualizadas em tempo real (`pt-BR`) com ícones dedicados.
  - **Botão de Sinal / Wi-Fi**: Exibe o status em tempo real (Cabo Ethernet, nível de sinal Wi-Fi de 0 a 4 barras ou sem sinal) e, ao ser clicado, abre diretamente o painel de conexões Wi-Fi/Rede da Android TV.
  - **Gestão Direta de Dispositivos Bluetooth (`Fones / BT`)**:
    - Exibe no topo o ícone de fone de ouvido e o **nome do dispositivo conectado** (ex.: `Sala`).
    - Ao clicar em um dispositivo já pareado na lista, realiza a **conexão ou desconexão automática em segundo plano** (via `BluetoothDevice.connect()` / `disconnect()` + `BluetoothA2dp` / `BluetoothHeadset`), sem abrir novas janelas de configuração do sistema.
  - **Seleção de Aplicativos (`Apps`)**: Permite escolher quais aplicativos instalados (`LEANBACK_LAUNCHER` e `LAUNCHER`) aparecem na tela inicial e ativar/desativar a caixa do YouTube.
  - **Papéis de Parede Pré-definidos (Botão de Paleta)**:
    - 6 opções minimalistas renderizadas nativamente em Canvas: *Preto Puro (OLED)*, *Azul Meia-Noite*, *Aurora Esmeralda*, *Nebulosa Violeta*, *Brasa Âmbar* e *Cosmos Estrelado*.
  - **Protetor de Tela (Botão de Lua)**: Aciona imediatamente o protetor de tela nativo (Daydream / `Somnambulator`) do Android TV.
  - **Botão `Tornar Padrão`** e **Configurações do Sistema**.

- **Caixa Opcional de Recomendações do YouTube por Conta da TV**:
  - Detecta automaticamente as contas Google (`com.google`) já conectadas na Android TV.
  - Carrega feeds exclusivos da conta ativa (sem resultados genéricos) divididos em **3 abas**:
    1. **Recomendados**: Recomendações personalizadas da conta logada.
    2. **Inscrições**: Vídeos dos canais em que a conta está inscrita.
    3. **Envios Recentes**: Últimos vídeos enviados pelos canais inscritos e recomendações recentes ordenados cronologicamente, exibindo o tempo de publicação em cada card.
  - Suporta alternância rápida entre as contas da TV mantendo tokens e sessões isolados por conta.
  - Layout adaptativo (`compactMode` + `clipToBounds`) projetado para telas `1080p` (`960dp x 540dp`) sem sobreposição com os cards de aplicativos.

- **Inicialização Automática e Launcher Padrão Permanente**:
  - Possui `BootReceiver` (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED` e `QUICKBOOT_POWERON`) com `directBootAware="true"`.

## Como instalar e fixar como Launcher Padrão na TV

Você pode executar o script [`instalar_e_definir_padrao.bat`](file:///C:/desnv/AndroidStudioProjects/MinimalTvLauncher/instalar_e_definir_padrao.bat) ou rodar manualmente no PowerShell:

```powershell
# 1. Conectar na TV via ADB
C:\Users\vinic\AppData\Local\Android\Sdk\platform-tools\adb.exe connect 192.168.31.98:5555

# 2. Compilar e instalar o APK concedendo permissões (-g)
cd C:\desnv\AndroidStudioProjects\MinimalTvLauncher
.\gradlew.bat assembleDebug
C:\Users\vinic\AppData\Local\Android\Sdk\platform-tools\adb.exe -s 192.168.31.98:5555 install -r -g "C:\desnv\AndroidStudioProjects\MinimalTvLauncher\app\build\outputs\apk\debug\app-debug.apk"

# 3. Definir como atividade Home padrão do Android
C:\Users\vinic\AppData\Local\Android\Sdk\platform-tools\adb.exe -s 192.168.31.98:5555 shell cmd package set-home-activity com.minimal.tvlauncher/.MainActivity
```
