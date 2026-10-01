param(
    [Parameter(Mandatory = $true)][string]$ClientPath,
    [string]$PackagePath = (Join-Path $PSScriptRoot 'package-v2')
)
. (Join-Path $PSScriptRoot 'Common.ps1')
$clientRoot = (Resolve-Path -LiteralPath $ClientPath).Path
$packageRoot = (Resolve-Path -LiteralPath $PackagePath).Path
$manifest = Get-VisualManifest $packageRoot
if ($manifest.clientRoot -ne $clientRoot) { throw 'Package belongs to a different client.' }
$launcherPath = Join-Path $clientRoot 'Aion Start.bat'
$launcher = [System.IO.File]::ReadAllText($launcherPath)
$startPattern = '(?im)^start\s+""\s+"bin64\\aion\.bin"[^\r\n]*'
if ([regex]::Matches($launcher, $startPattern).Count -ne 1) { throw 'Unexpected Aion launcher.' }
foreach ($entry in $manifest.files) {
    $hash = (Get-FileHash -LiteralPath (Join-Path $clientRoot $entry.path)).Hash
    if ($hash -ne $entry.original -and $hash -ne $entry.staged) {
        throw "Client changed after preparation: $($entry.path). No files changed."
    }
}
$modRoot = Join-Path $clientRoot 'VisualOverhaul'
$stagedRoot = Join-Path $modRoot ('packages/' + $manifest.packageId)
New-Item -ItemType Directory -Path $stagedRoot -Force | Out-Null
foreach ($entry in $manifest.files) {
    $target = Join-Path $stagedRoot $entry.path
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $packageRoot $entry.path) -Destination $target -Force
}
Copy-Item -LiteralPath (Join-Path $packageRoot 'manifest.json') -Destination (Join-Path $stagedRoot 'manifest.json') -Force
$verified = Get-VisualManifest $stagedRoot
foreach ($name in @('Common.ps1','ApplyPackage.ps1','Restore.ps1')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $modRoot $name) -Force
}
$backupLauncher = Join-Path $modRoot 'launcher-before-visual-pass.bat'
if (-not (Test-Path -LiteralPath $backupLauncher)) {
    Copy-Item -LiteralPath $launcherPath -Destination $backupLauncher
}
$launcher = [regex]::Replace($launcher,
    '(?ms)^rem BEGIN VISUAL OVERHAUL\r?\n.*?^rem END VISUAL OVERHAUL\r?\n?', '')
$startup = @'
rem BEGIN VISUAL OVERHAUL
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0VisualOverhaul\ApplyPackage.ps1" -ClientPath "%~dp0." -PackagePath "%~dp0VisualOverhaul\packages\PACKAGE_ID"
if errorlevel 1 (
    echo Unable to apply the visual pack. See VisualOverhaul\Restore.ps1 to restore.
    pause
    exit /b 1
)
rem END VISUAL OVERHAUL
'@
$startup = ($startup.Replace('PACKAGE_ID', $manifest.packageId) -replace '\r?\n', "`r`n") + "`r`n"
$match = [regex]::Match($launcher, $startPattern)
$launcher = $launcher.Insert($match.Index, $startup)
[System.IO.File]::WriteAllText($launcherPath, $launcher, [System.Text.Encoding]::ASCII)
& (Join-Path $modRoot 'ApplyPackage.ps1') -ClientPath $clientRoot -PackagePath $stagedRoot -VerifyOnly
if (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue) {
    Write-Output 'Queued the visual pack for the next launch through Aion Start.bat. Current session remains open.'
} else {
    & (Join-Path $modRoot 'ApplyPackage.ps1') -ClientPath $clientRoot -PackagePath $stagedRoot
}
Write-Output "Launcher backup: $backupLauncher"
