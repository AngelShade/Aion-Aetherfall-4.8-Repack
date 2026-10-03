param([Parameter(Mandatory=$true)][string]$ClientPath,[Parameter(Mandatory=$true)][string]$PackagePath,[switch]$VerifyOnly)
. (Join-Path $PSScriptRoot 'GlobalCommon.ps1')
$client = (Resolve-Path -LiteralPath $ClientPath).Path
$package = (Resolve-Path -LiteralPath $PackagePath).Path
$manifest = Read-GlobalManifest $package
if ($manifest.clientRoot -ne $client) { throw 'Package belongs to another client.' }
$original = 0; $installed = 0
foreach ($entry in $manifest.files) {
    $hash = (Get-FileHash -LiteralPath (Join-Path $client $entry.path)).Hash
    if ($hash -eq $entry.original) { $original++ }
    elseif ($hash -eq $entry.staged) { $installed++ }
    else { throw "Archive changed after preparation: $($entry.path). No files changed." }
}
if ($VerifyOnly) { "Verified $($manifest.files.Count) graphics archives: $original previous / $installed upgraded."; return }
$statePath = Join-Path $PSScriptRoot 'installed.json'
if ($installed -eq $manifest.files.Count) {
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if ($state.packageId -ne $manifest.packageId -or $state.restored) { throw 'Graphics restore record mismatch.' }
    'Broad graphics package verified.'; return
}
if ($original -ne $manifest.files.Count) { throw 'Mixed archive states detected. No files changed.' }
Assert-ClientClosed
$backup = Join-Path $client ('GraphicsOverhaul-backups/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
foreach ($entry in $manifest.files) {
    $target = Join-Path $backup $entry.path
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $client $entry.path) -Destination $target
    if ((Get-FileHash -LiteralPath $target).Hash -ne $entry.original) { throw 'Backup verification failed. No archives changed.' }
}
$completed = @()
try {
    foreach ($entry in $manifest.files) {
        $completed += $entry
        $target = Join-Path $client $entry.path
        Copy-GlobalArchive (Join-Path $package $entry.path) $target
        if ((Get-FileHash -LiteralPath $target).Hash -ne $entry.staged) { throw "Installed checksum failed: $($entry.path)" }
    }
    @{clientRoot=$client; packageId=$manifest.packageId; backupRoot=$backup; restored=$false; appliedAt=(Get-Date -Format 'o')} |
        ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
} catch {
    $failure = $_
    foreach ($entry in $completed) { Copy-GlobalArchive (Join-Path $backup $entry.path) (Join-Path $client $entry.path) }
    throw $failure
}
"Applied $($manifest.files.Count) graphics archives. Verified backups: $backup"
