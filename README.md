# Minimal TV Launcher

Launcher minimalista para Android TV desenvolvido em **Kotlin** com **Jetpack Compose**, focado em simplicidade, tela 100% preta (`#000000`) e zero recomendações ou anúncios.

## Funcionalidades
- **Tela Preta (OLED Friendly)**: Fundo `#000000` sem distrações ou banners promocionais.
- **Barra de Status Superior**:
  - **Hora e Data** atualizadas em tempo real com ícones dedicados.
  - **Ícone de Sinal**: Detecta automaticamente conexão via Cabo (Ethernet), nível de sinal Wi-Fi (0 a 4 barras) ou ausência de conexão.
  - **Botão de Seleção de Apps (`Apps`)**: Permite marcar/desmarcar manualmente quais aplicativos instalados aparecem na tela inicial.
  - **Ícone de Configurações**: Abre diretamente as configurações do sistema da Android TV.
- **Suporte a Controle Remoto (D-Pad)**:
  - Navegação fluida pelas setas direcionais do controle remoto com destaque visual de foco.
  - Pressione `OK` sobre um app para abri-lo (suporta apps de TV Leanback e apps comuns de Android instalados via APK).
  - Pressione e segure `OK` sobre qualquer app (ou clique no botão `Apps` no topo) para editar os aplicativos visíveis na tela inicial.

## Como instalar na sua Android TV via ADB

Você já possui o `platform-tools` em `C:\desnv\platform-tools`. Após gerar o APK pelo Android Studio (`Build > Build Bundle(s) / APK(s) > Build APK(s)`):

1. Na sua TV, ative as **Opções do desenvolvedor** e a **Depuração ADB (Depuração USB / Rede)**.
2. Conecte via ADB pelo terminal:
   ```powershell
   C:\desnv\platform-tools\adb.exe connect IP_DA_SUA_TV:5555
   ```
3. Instale o APK:
   ```powershell
   C:\desnv\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
   ```
4. *(Opcional)* Para definir como Launcher padrão caso o Android TV não exiba a janela de escolha ao apertar o botão Home, desative o launcher padrão de fábrica via ADB:
   ```powershell
   # Para Google TV / Android TV padrão:
   C:\desnv\platform-tools\adb.exe shell pm disable-user --user 0 com.google.android.tvlauncher
   C:\desnv\platform-tools\adb.exe shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
   ```
# MinimalTvLauncher
