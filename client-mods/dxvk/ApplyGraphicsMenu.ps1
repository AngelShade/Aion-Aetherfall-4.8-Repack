param([Parameter(Mandatory=$true)][string]$ClientPath,[switch]$VerifyOnly,[switch]$Reinstall)
$ErrorActionPreference = 'Stop'
$client = (Resolve-Path -LiteralPath $ClientPath).Path
. (Join-Path $client 'Mods/Renderer/ClientOwnership.ps1')
$statePath = Join-Path $client 'Mods/GraphicsMenu/installed.json'
$state = if (Test-Path -LiteralPath $statePath) { Read-OwnershipJson $statePath } else { $null }
if ($state -and $state.restoredAt) {
    if (-not $Reinstall) { 'Graphics menu has been restored; launch continues without its controls.'; return }
    $state=$null
}
if ($state) {
    Update-GraphicsOwnershipRecord $client
    'Graphics menu DLL, import, hook instructions and Screen controls verified.'
    return
}
# A fresh client/repack can already carry the native files. Validate them before
# creating its first record; no whole-file package or recovery image is needed.
$present = Test-Path -LiteralPath (Join-Path $client 'bin64/AionGraphicsMenu.dll')
if ($present) { Update-GraphicsOwnershipRecord $client; 'Graphics menu ownership initialized and verified.'; return }
if (-not $VerifyOnly) { Assert-OwnershipClosed }
[AetherfallOwnership.Parts]::VerifyCursor($client)
$payload = [IO.File]::ReadAllBytes((Join-Path $client 'Mods/GraphicsMenu/package/bin64/AionGraphicsMenu.dll'))
$spec = [AetherfallOwnership.Parts]::InstallGraphics($client,(Get-GraphicsOwnership $client),$payload,(-not $VerifyOnly))
if ($VerifyOnly) { 'Graphics menu compact payload and owned installation sites verified; queued.'; return }
Write-OwnershipJson (Join-Path $client 'Mods/GraphicsMenu/ownership.json') ($spec | ConvertFrom-Json)
Update-GraphicsOwnershipRecord $client
'Graphics menu installed; other native hooks, archive entries and renderer choices preserved.'
