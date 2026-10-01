param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$SegoeFont = '',
    [ValidateSet('universal', 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64')]
    [string]$Abi = 'arm64-v8a',
    [switch]$SkipChecks
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) { throw 'Indica un JDK 17 mediante -JavaHome o JAVA_HOME.' }
if (-not $SdkRoot -or -not (Test-Path -LiteralPath (Join-Path $SdkRoot 'platforms\android-35'))) { throw 'Indica un Android SDK con API 35 mediante -SdkRoot o ANDROID_HOME.' }
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $SdkRoot
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp'
if ($SegoeFont) {
    if (-not (Test-Path -LiteralPath $SegoeFont)) { throw 'No se encontró el archivo Segoe indicado.' }
    New-Item -ItemType Directory -Force android/app/src/main/res/font | Out-Null
    Copy-Item -LiteralPath $SegoeFont -Destination android/app/src/main/res/font/segoe_semibold.ttf -Force
}
$taskArguments = @('-p', 'android', '--no-daemon', 'assembleDebug', '--console=plain')
if ($Abi -ne 'universal') { $taskArguments += "-PtuVidaAbi=$Abi" }
if (-not $SkipChecks) { $taskArguments += @('testDebugUnitTest', 'lintDebug') }
& ./android/gradlew.bat @taskArguments
if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación o una verificación.' }
New-Item -ItemType Directory -Force artifacts | Out-Null
Copy-Item -LiteralPath android/app/build/outputs/apk/debug/app-debug.apk -Destination artifacts/Tu-Vida-Android.apk -Force
Write-Output "APK listo: $(Join-Path $PSScriptRoot 'artifacts\Tu-Vida-Android.apk')"
