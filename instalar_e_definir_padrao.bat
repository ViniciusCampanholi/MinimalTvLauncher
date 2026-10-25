@echo off
chcp 65001 >nul
echo ========================================================
echo   Minimal TV Launcher - Instalador e Launcher Padrao
echo ========================================================
echo.

set ADB="C:\Users\vinic\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set APK="C:\desnv\AndroidStudioProjects\MinimalTvLauncher\app\build\outputs\apk\debug\app-debug.apk"
set DEFAULT_IP=192.168.31.98

set /p TV_IP="Digite o IP da sua Android TV [Padrao: %DEFAULT_IP%]: "
if "%TV_IP%"=="" set TV_IP=%DEFAULT_IP%

echo Conectando na TV em %TV_IP%:5555...
%ADB% connect %TV_IP%:5555

echo.
echo [1/3] Instalando o Minimal TV Launcher (ja concedendo permissoes de Bluetooth)...
%ADB% -s %TV_IP%:5555 install -r -g %APK%

echo.
echo [2/3] Definindo o Minimal TV Launcher como Home Padrao do sistema...
%ADB% -s %TV_IP%:5555 shell cmd package set-home-activity com.minimal.tvlauncher/.MainActivity

echo.
set /p DISABLE_STOCK="Deseja desativar o launcher original da Google TV/Android TV para fixar definitivamente mesmo ao desligar a TV? (S/N): "
if /I "%DISABLE_STOCK%"=="S" (
    echo [3/3] Desativando launcher original de fabrica...
    %ADB% -s %TV_IP%:5555 shell pm disable-user --user 0 com.google.android.tvlauncher 2>nul
    %ADB% -s %TV_IP%:5555 shell pm disable-user --user 0 com.google.android.apps.tv.launcherx 2>nul
    %ADB% -s %TV_IP%:5555 shell pm disable-user --user 0 com.google.android.tungsten.setupwraith 2>nul
)

echo.
echo Abrindo o Minimal TV Launcher na TV...
%ADB% -s %TV_IP%:5555 shell am start -n com.minimal.tvlauncher/.MainActivity

echo.
echo Concluido!
pause
