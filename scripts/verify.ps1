$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$dbPath = Join-Path $repoRoot "app\src\main\assets\curbrun.sqlite"
if (-not (Test-Path $dbPath)) {
    throw "Missing bundled curb database: $dbPath"
}

$dbSize = (Get-Item $dbPath).Length
if ($dbSize -lt 1MB) {
    throw "Bundled curb database is unexpectedly small: $dbSize bytes"
}

& py scripts\validate_curb_db.py --db $dbPath --report build\curb-db-validation.json
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& .\gradlew.bat :app:assembleDebug testDebugUnitTest :app:lintDebug --stacktrace
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$apkPath = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apkPath)) {
    throw "Missing debug APK after build: $apkPath"
}

$apk = Get-Item $apkPath
if ($apk.Length -lt 5MB) {
    throw "Debug APK is unexpectedly small: $($apk.Length) bytes"
}

Write-Host "CurbRun verification passed."
Write-Host "APK: $($apk.FullName)"
Write-Host "Size: $($apk.Length) bytes"
