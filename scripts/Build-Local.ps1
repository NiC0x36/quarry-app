[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$GradleUserHome = (Join-Path (Split-Path $PSScriptRoot -Parent) '..\.build-tools\gradle'),
    [string[]]$Tasks = @('testDebugUnitTest', 'lintDebug', 'assembleDebug')
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) {
    throw 'Set JAVA_HOME to your JDK 21 installation or pass -JavaHome. This script does not install Java.'
}
if (-not $AndroidSdk) { $AndroidSdk = $env:ANDROID_SDK_ROOT }
if (-not $AndroidSdk -or -not (Test-Path -LiteralPath (Join-Path $AndroidSdk 'platforms\android-36\android.jar'))) {
    throw 'Set ANDROID_HOME to your Android SDK (platform 36), or pass -AndroidSdk. This script does not install the SDK.'
}
$env:JAVA_HOME = [IO.Path]::GetFullPath($JavaHome)
$env:ANDROID_HOME = [IO.Path]::GetFullPath($AndroidSdk)
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = [IO.Path]::GetFullPath($GradleUserHome)
$env:ANDROID_USER_HOME = Join-Path (Split-Path $env:GRADLE_USER_HOME -Parent) 'android-user'
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & .\gradlew.bat --no-daemon --console=plain --max-workers=2 @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }
