param([string]$Version = 'v1.3.3')
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$env:APP_VERSION = $Version
if (Test-Path 'build/media-rescan.keystore') {
    $env:SIGNING_STORE_FILE = (Resolve-Path 'build/media-rescan.keystore').Path
    $env:SIGNING_STORE_PASSWORD = 'android'
    $env:SIGNING_KEY_ALIAS = 'mediarescan'
    $env:SIGNING_KEY_PASSWORD = 'android'
}
try {
    & .\gradlew.bat --no-daemon testDebugUnitTest lintRelease assembleRelease
    if ($LASTEXITCODE) { throw 'Gradle build failed' }
} finally {
    Remove-Item Env:SIGNING_STORE_PASSWORD,Env:SIGNING_KEY_PASSWORD -ErrorAction SilentlyContinue
}
