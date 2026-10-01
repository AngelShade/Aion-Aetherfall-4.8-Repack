param([Parameter(Mandatory = $true)][string]$ClientPath)
$ErrorActionPreference = 'Stop'
$clientRoot = (Resolve-Path -LiteralPath $ClientPath).Path
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'manifest.json') -Raw | ConvertFrom-Json
$modRoot = Join-Path $clientRoot 'DXVK'
$statePath = Join-Path $modRoot 'installed.json'
foreach ($patch in $manifest.compatibilityPatches) {
    if ((Get-FileHash -LiteralPath (Join-Path $clientRoot $patch.path)).Hash -ne $patch.sha256) {
        throw 'The verified Aion version DLL was changed; stop before installing DXVK.'
    }
}
foreach ($entry in $manifest.files) {
    if ((Get-FileHash -LiteralPath (Join-Path $PSScriptRoot ('payload/' + $entry.path))).Hash -ne $entry.sha256) {
        throw 'DXVK payload checksum failed.'
    }
}
if (Test-Path -LiteralPath $statePath) {
    throw 'DXVK already has an installation record. Use the restore script before changing versions.'
}
$running = Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue
# Adding new DLLs does not change a running process using the system renderer.
# Updating an existing wrapper must wait until the client is closed.
if ($running) {
    foreach ($path in @('bin32/d3d9.dll','bin64/d3d9.dll')) {
        if (Test-Path -LiteralPath (Join-Path $clientRoot $path)) {
            throw 'Close Aion before replacing an existing graphics wrapper.'
        }
    }
}
$launcherPath = Join-Path $clientRoot 'Aion Start.bat'
$launcher = [IO.File]::ReadAllText($launcherPath)
$pattern = '(?im)^start\s+""\s+"bin64\\aion\.bin"[^\r\n]*'
if ([regex]::Matches($launcher,$pattern).Count -ne 1 -or $launcher.Contains('BEGIN DXVK QUALITY')) {
    throw 'Unexpected Aion launcher format.'
}
$header = [regex]::Match($launcher,'(?im)^@echo off\r?\n')
if (-not $header.Success) { throw 'Expected launcher header is missing.' }
$backup = Join-Path $clientRoot ('DXVK-backups/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
Copy-Item -LiteralPath $launcherPath -Destination (Join-Path $backup 'Aion Start.bat')
$files = @()
foreach ($entry in $manifest.files) {
    $target = Join-Path $clientRoot $entry.path
    $original = $null
    if (Test-Path -LiteralPath $target) {
        $original = (Get-FileHash -LiteralPath $target).Hash
        $saved = Join-Path $backup $entry.path
        New-Item -ItemType Directory -Path (Split-Path $saved -Parent) -Force | Out-Null
        Copy-Item -LiteralPath $target -Destination $saved
        if ((Get-FileHash -LiteralPath $saved).Hash -ne $original) { throw 'Backup checksum failed.' }
    }
    $files += @{path=$entry.path;original=$original;installed=$entry.sha256}
}
New-Item -ItemType Directory -Path (Join-Path $modRoot 'logs') -Force | Out-Null
$completed = @()
try {
    foreach ($entry in $files) {
        $completed += $entry
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot ('payload/' + $entry.path)) -Destination (Join-Path $clientRoot $entry.path) -Force
        if ((Get-FileHash -LiteralPath (Join-Path $clientRoot $entry.path)).Hash -ne $entry.installed) {
            throw 'Installed DXVK checksum failed.'
        }
    }
    foreach ($name in @('QualityProfile.ps1','Restore.ps1','VerifyActive.ps1','RendererCommon.ps1','SelectRenderer.ps1')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $modRoot $name) -Force
    }
    $block = @'
rem BEGIN DXVK QUALITY
set "PSModulePath=%SystemRoot%\System32\WindowsPowerShell\v1.0\Modules;%PSModulePath%"
if exist "%~dp0DXVK\cursor-fix\InstallNativeCursorPatch.ps1" (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0DXVK\cursor-fix\InstallNativeCursorPatch.ps1" -ClientPath "%~dp0."
    if errorlevel 1 (
        echo Unable to apply the cursor compatibility patch.
        pause
        exit /b 1
    )
)
set "DXVK_CONFIG_FILE=%~dp0dxvk.conf"
set "DXVK_LOG_PATH=%~dp0DXVK\logs"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0DXVK\QualityProfile.ps1" -ClientPath "%~dp0."
if errorlevel 1 (
    echo Unable to apply graphics settings.
    pause
    exit /b 1
)
rem END DXVK QUALITY
'@
    $block = ($block -replace '\r?\n',"`r`n") + "`r`n"
    [IO.File]::WriteAllText($launcherPath,$launcher.Insert($header.Index + $header.Length,$block),[Text.Encoding]::ASCII)
    @{clientRoot=$clientRoot;version=$manifest.version;backupRoot=$backup;files=$files;
        installedAt=(Get-Date -Format 'o');activation='Requires client relaunch'} |
        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $statePath -Encoding UTF8
} catch {
    $failure = $_
    foreach ($entry in $completed) {
        $target = Join-Path $clientRoot $entry.path
        if ($null -eq $entry.original) { Remove-Item -LiteralPath $target -ErrorAction SilentlyContinue }
        else { Copy-Item -LiteralPath (Join-Path $backup $entry.path) -Destination $target -Force }
    }
    Copy-Item -LiteralPath (Join-Path $backup 'Aion Start.bat') -Destination $launcherPath -Force
    throw $failure
}
if (-not $running) { & (Join-Path $modRoot 'QualityProfile.ps1') -ClientPath $clientRoot }
else { 'Aion is running. Global graphics settings will apply on the next launch.' }
"Installed DXVK $($manifest.version) for bin32 and bin64. Backup: $backup"
