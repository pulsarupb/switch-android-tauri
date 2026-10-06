# Android build environment for this repo (Windows / PowerShell).
# Usage:  . .\scripts\android-env.ps1
# Then:   bun run android:build   (or)   bunx tauri android build --debug
#
# These paths are machine-specific. Override by setting the env vars before
# sourcing this file.

if (-not $env:JAVA_HOME) {
    $candidates = @(
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\Android\Android Studio1\jbr"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { $env:JAVA_HOME = $candidate; break }
    }
}

if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

if (-not $env:NDK_HOME) {
    $ndkRoot = Join-Path $env:ANDROID_HOME "ndk"
    if (Test-Path $ndkRoot) {
        $latest = Get-ChildItem $ndkRoot -Directory | Sort-Object Name -Descending | Select-Object -First 1
        if ($latest) { $env:NDK_HOME = $latest.FullName }
    }
}

if ($env:JAVA_HOME) {
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}

Write-Host "JAVA_HOME    = $env:JAVA_HOME"
Write-Host "ANDROID_HOME = $env:ANDROID_HOME"
Write-Host "NDK_HOME     = $env:NDK_HOME"
