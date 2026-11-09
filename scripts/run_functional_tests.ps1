# Pin Vault - Automated Emulator Functional Test Runner
# Usage: .\scripts\run_functional_tests.ps1 [-Build] [-TargetTest <TestName>]

param(
    [switch]$Build = $false,
    [string]$TargetTest = ""
)

$ErrorActionPreference = "Stop"

# Ensure Android SDK platform-tools is on PATH
$sdkPlatformTools = "$env:LOCALAPPDATA\Android\Sdk\platform-tools"
if ((Test-Path $sdkPlatformTools) -and ($env:PATH -notlike "*$sdkPlatformTools*")) {
    $env:PATH = "$sdkPlatformTools;$env:PATH"
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Pin Vault Functional Test Suite Runner  " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Verify ADB and device connection
Write-Host "`n[1/4] Checking connected Android devices/emulators..." -ForegroundColor Yellow
$devices = @(adb devices | Select-String "\tdevice$")
if ($devices.Count -eq 0) {
    Write-Host "No connected emulator or device detected!" -ForegroundColor Red
    Write-Host "Please start the Android emulator first (e.g., Pixel_9_Pro)." -ForegroundColor Yellow
    exit 1
}
$emuDevice = $devices | Where-Object { $_.Line -match "^emulator-" } | Select-Object -First 1
if ($emuDevice) {
    $targetDevice = ($emuDevice.Line -split "`t")[0]
} else {
    $targetDevice = ($devices[0].Line -split "`t")[0]
}
Write-Host "Target device: $targetDevice" -ForegroundColor Green

# Wake device and dismiss keyguard
adb -s $targetDevice shell input keyevent 224 | Out-Null
adb -s $targetDevice shell wm dismiss-keyguard | Out-Null

# 2. Build APKs if requested
if ($Build) {
    Write-Host "`n[2/4] Building Debug & AndroidTest APKs..." -ForegroundColor Yellow
    & .\gradlew.bat assembleDebug assembleDebugAndroidTest --no-daemon
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Build failed!" -ForegroundColor Red
        exit $LASTEXITCODE
    }

    Write-Host "`n[3/4] Installing APKs on $targetDevice..." -ForegroundColor Yellow
    $buildDir = "$env:USERPROFILE\.gradle-builds\PinVault\app\outputs\apk"
    $debugApk = Get-ChildItem -Path "$buildDir\debug" -Filter "PinVault-v*-debug.apk" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $testApk = Get-ChildItem -Path "$buildDir\androidTest\debug" -Filter "PinVault-v*-debug-androidTest.apk" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($debugApk -and $testApk) {
        adb -s $targetDevice install -r $debugApk.FullName
        adb -s $targetDevice install -r $testApk.FullName
    } else {
        $localDebugApk = Get-ChildItem -Path "app\build\outputs\apk\debug" -Filter "*.apk" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        $localTestApk = Get-ChildItem -Path "app\build\outputs\apk\androidTest\debug" -Filter "*.apk" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        adb -s $targetDevice install -r $localDebugApk.FullName
        adb -s $targetDevice install -r $localTestApk.FullName
    }
} else {
    Write-Host "`n[2/4] Skipping build step (use -Build to recompile). Installing latest APKs if present..." -ForegroundColor Gray
}

# 3. Execute instrumented tests
Write-Host "`n[4/4] Executing Compose UI Functional Test Suite..." -ForegroundColor Yellow

$testClass = "eu.hughkennedy.pinvault.ui.VaultFunctionalTestSuite"
if ($TargetTest -ne "") {
    $testClass = "$testClass#$TargetTest"
}

Write-Host "Running: $testClass" -ForegroundColor Cyan
adb -s $targetDevice shell am instrument -w -r -e class $testClass eu.hughkennedy.pinvault.test/androidx.test.runner.AndroidJUnitRunner

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n==========================================" -ForegroundColor Green
    Write-Host "  All Functional Tests Completed!        " -ForegroundColor Green
    Write-Host "==========================================" -ForegroundColor Green
} else {
    Write-Host "`nTests finished with failures or warnings." -ForegroundColor Red
}
