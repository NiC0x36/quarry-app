[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$SdkRoot,
    [Parameter(Mandatory)][string]$JavaHome,
    [switch]$AcceptSdkLicenses
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $AcceptSdkLicenses) {
    throw 'Review https://developer.android.com/studio/terms and pass -AcceptSdkLicenses to install SDK packages.'
}
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) {
    throw 'JavaHome must point to an existing JDK 21 installation. This script never installs Java.'
}
$env:JAVA_HOME = [IO.Path]::GetFullPath($JavaHome)
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$SdkRoot = [IO.Path]::GetFullPath($SdkRoot)
$manager = Join-Path $SdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path -LiteralPath $manager)) {
    $zip = Join-Path $env:TEMP 'quarry-android-command-line-tools.zip'
    $unpack = Join-Path $env:TEMP 'quarry-android-command-line-tools'
    Write-Output 'Downloading official Android command-line tools.'
    Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' -OutFile $zip
    $expected = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
    if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $expected) {
        throw 'Android tools checksum mismatch. The archive was not extracted.'
    }
    Expand-Archive -LiteralPath $zip -DestinationPath $unpack -Force
    $target = Join-Path $SdkRoot 'cmdline-tools\latest'
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    Copy-Item -Path (Join-Path $unpack 'cmdline-tools\*') -Destination $target -Recurse -Force
}
Write-Output 'Installing Android platform 36, build-tools 36.0.0 and platform-tools.'
$output = @(1..12 | ForEach-Object { 'y' } | & $manager "--sdk_root=$SdkRoot" --install 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools' 2>&1)
$output | Select-Object -Last 12
if ($LASTEXITCODE -ne 0) { throw "SDK installation failed with exit code $LASTEXITCODE." }
Write-Output "Android SDK ready at $SdkRoot"
