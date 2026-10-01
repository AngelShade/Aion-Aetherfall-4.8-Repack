param(
    [Parameter(Mandatory = $true)][string]$ClientPath,
    [Parameter(Mandatory = $true)][string]$PackagePath,
    [switch]$VerifyOnly
)
. (Join-Path $PSScriptRoot 'Common.ps1')
$clientRoot = (Resolve-Path -LiteralPath $ClientPath).Path
$packageRoot = (Resolve-Path -LiteralPath $PackagePath).Path
if (-not (Test-Path -LiteralPath (Join-Path $clientRoot 'bin64/aion.bin'))) {
    throw 'Expected Aion 4.8 NA client was not found.'
}
$manifest = Get-VisualManifest $packageRoot
if ($manifest.clientRoot -ne $clientRoot) { throw 'Package belongs to a different client.' }
$originalCount = 0
$stagedCount = 0
foreach ($entry in $manifest.files) {
    $hash = (Get-FileHash -LiteralPath (Join-Path $clientRoot $entry.path)).Hash
    if ($hash -eq $entry.original) { $originalCount++ }
    elseif ($hash -eq $entry.staged) { $stagedCount++ }
    else { throw "Client archive changed after preparation: $($entry.path). No archives changed." }
}
if ($VerifyOnly) {
    Write-Output "Verified all six archives. Original: $originalCount; installed: $stagedCount."
    return
}
Assert-AionClosed
$statePath = Join-Path $PSScriptRoot 'installed.json'
if ($stagedCount -eq $manifest.files.Count) {
    if (-not (Test-Path -LiteralPath $statePath)) { throw 'Installed files have no restore record.' }
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if ($state.packageId -ne $manifest.packageId -or $state.restored) {
        throw 'Installed files do not match their restore record.'
    }
    Write-Output 'Poeta and Sanctum visual pack is active.'
    return
}
if ($originalCount -ne $manifest.files.Count) {
    throw 'Mixed original/installed files detected. Restore the previous pack before proceeding.'
}
$backupRoot = Join-Path $clientRoot ('VisualOverhaul-backups/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backupRoot | Out-Null
foreach ($entry in $manifest.files) {
    $backupPath = Join-Path $backupRoot $entry.path
    New-Item -ItemType Directory -Path (Split-Path -Parent $backupPath) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $clientRoot $entry.path) -Destination $backupPath
    if ((Get-FileHash -LiteralPath $backupPath).Hash -ne $entry.original) {
        throw 'Backup verification failed; no archives changed.'
    }
}
$completed = @()
try {
    foreach ($entry in $manifest.files) {
        # Include the current entry in rollback even if its replacement fails.
        $completed += $entry
        $destination = Join-Path $clientRoot $entry.path
        Copy-VisualFile (Join-Path $packageRoot $entry.path) $destination
        if ((Get-FileHash -LiteralPath $destination).Hash -ne $entry.staged) {
            throw "Installed checksum failed: $($entry.path)"
        }
    }
    $state = @{ clientRoot = $clientRoot; packageId = $manifest.packageId;
        backupRoot = $backupRoot; files = $manifest.files; restored = $false;
        appliedAt = (Get-Date -Format 'o') }
    $state | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $statePath -Encoding UTF8
} catch {
    $failure = $_
    foreach ($entry in $completed) {
        Copy-VisualFile (Join-Path $backupRoot $entry.path) (Join-Path $clientRoot $entry.path)
    }
    throw $failure
}
Write-Output "Applied Poeta and Sanctum visual pack. Backups: $backupRoot"
