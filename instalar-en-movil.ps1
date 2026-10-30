# Compila BlueWay y la instala en el móvil conectado por USB o por depuración inalámbrica.
# Uso: .\instalar-en-movil.ps1
$ErrorActionPreference = "Stop"
if (-not $env:JAVA_HOME -and (Test-Path "C:\Program Files\Java\jdk-21")) { $env:JAVA_HOME = "C:\Program Files\Java\jdk-21" }

$devices = @(adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" })
if ($devices.Count -eq 0) {
    Write-Host "No hay ningún móvil conectado. Activa la depuración USB y acepta el aviso del teléfono." -ForegroundColor Yellow
    exit 1
}

.\gradlew.bat :app:assembleRelease --console=plain
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell am start -n com.blueway.app/.MainActivity
