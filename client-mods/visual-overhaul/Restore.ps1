param([string]$ClientPath = (Split-Path -Parent $PSScriptRoot))
. (Join-Path $PSScriptRoot 'Common.ps1')
Assert-AionClosed
$clientRoot = (Resolve-Path -LiteralPath $ClientPath).Path
$modRoot = Join-Path $clientRoot 'VisualOverhaul'
$statePath = Join-Path $modRoot 'installed.json'
if (Test-Path -LiteralPath $statePath) {
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if ($state.clientRoot -ne $clientRoot) { throw 'Restore record belongs to another client.' }
    $backupRoot = (Resolve-Path -LiteralPath $state.backupRoot).Path
    $expectedBackupRoot = (Join-Path $clientRoot 'VisualOverhaul-backups').TrimEnd('\') + '\'
    if (-not $backupRoot.StartsWith($expectedBackupRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Unexpected archive backup directory.'
    }
    $package = Join-Path $modRoot ('packages/' + $state.packageId)
    $manifest = Get-VisualManifest $package
    # Preflight every current/backup file before restoring any archive.
    foreach ($entry in $manifest.files) {
        $hash = (Get-FileHash -LiteralPath (Join-Path $clientRoot $entry.path)).Hash
        if ($hash -ne $entry.staged -and $hash -ne $entry.original) {
            throw "Archive modified after visual install: $($entry.path). Restore stopped to preserve that edit."
        }
        if ((Get-FileHash -LiteralPath (Join-Path $backupRoot $entry.path)).Hash -ne $entry.original) {
            throw 'Original archive backup checksum failed.'
        }
    }
    foreach ($entry in $manifest.files) {
        $destination = Join-Path $clientRoot $entry.path
        Copy-VisualFile (Join-Path $backupRoot $entry.path) $destination
        if ((Get-FileHash -LiteralPath $destination).Hash -ne $entry.original) {
            throw 'Restored archive checksum failed.'
        }
    }
    $state.restored = $true
    $state | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $statePath -Encoding UTF8
}
$launcherPath = Join-Path $clientRoot 'Aion Start.bat'
$launcher = [System.IO.File]::ReadAllText($launcherPath)
$launcher = [regex]::Replace($launcher,
    '(?ms)^rem BEGIN VISUAL OVERHAUL\r?\n.*?^rem END VISUAL OVERHAUL\r?\n?', '')
[System.IO.File]::WriteAllText($launcherPath, $launcher, [System.Text.Encoding]::ASCII)
Write-Output 'Visual pack removed from startup; any installed visual archives were restored.'
