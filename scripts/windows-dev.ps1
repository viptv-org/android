# Dot-source this file from PowerShell to load this PC's private dev settings.
# Example: . .\scripts\windows-dev.ps1
$repoRoot = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $repoRoot '.env'
if (-not (Test-Path -LiteralPath $configPath)) {
    throw 'Missing private .env. See DEVELOPMENT.md; do not guess a server or device.'
}
foreach ($line in [IO.File]::ReadAllLines($configPath)) {
    if ($line -match '^\s*(?:export\s+)?([A-Z][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
    }
}
foreach ($name in @('JAVA_HOME', 'ANDROID_HOME', 'ANDROID_TV_AVD', 'ANDROID_TV_SERIAL', 'VIPTV_DEV_HOST', 'VIPTV_DEV_USER', 'VIPTV_DEV_SSH_PORT')) {
    if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) {
        throw "Missing $name in private .env."
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    throw 'Configured JDK is missing.'
}
if (-not (Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'))) {
    throw 'Configured Android SDK is missing.'
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:CARGO_TARGET_DIR = Join-Path $repoRoot 'vendor/core/target'
$toolPaths = @(
    (Join-Path $env:JAVA_HOME 'bin'),
    (Join-Path $env:ANDROID_HOME 'platform-tools'),
    (Join-Path $env:ANDROID_HOME 'emulator'),
    (Join-Path $env:USERPROFILE '.cargo/bin'),
    'C:\Program Files\Git\bin'
)
$env:PATH = (@($toolPaths + ($env:PATH -split ';') | Where-Object { $_ } | Select-Object -Unique) -join ';')
Write-Output 'Windows dev settings loaded from private .env (current shell only).'
