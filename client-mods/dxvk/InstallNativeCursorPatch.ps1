param([Parameter(Mandatory=$true)][string]$ClientPath,[switch]$VerifyOnly,[switch]$Restore,[switch]$Reinstall)
$ErrorActionPreference = 'Stop'
$clientRoot = (Resolve-Path -LiteralPath $ClientPath).Path
$statePath = Join-Path $clientRoot 'Mods/Renderer/installed.json'
. (Join-Path $PSScriptRoot 'RendererCommon.ps1')
. (Join-Path $clientRoot 'Mods/Renderer/ClientOwnership.ps1')
$state = Read-OwnershipJson $statePath
if ($state.restoredAt) { 'Renderer was restored; CursorFix is inactive.'; return }
if ($state.nativeCursorPatch.restoredAt -and -not $Reinstall) {
    [AetherfallOwnership.Parts]::VerifyCursorOriginal($clientRoot)
    'CursorFix remains restored; use -Reinstall to enable it again.'
    return
}
foreach ($entry in $state.files) {
    if (@('bin32/d3d9.dll','bin64/d3d9.dll','dxvk.conf') -cnotcontains $entry.path) { throw 'Unexpected installed renderer file path.' }
    if ((Get-FileHash -LiteralPath (Resolve-RendererFile $clientRoot $entry.path)).Hash -ne $entry.installed) { throw 'Installed DXVK files changed.' }
}
if ($Restore) {
    Assert-OwnershipClosed
    [AetherfallOwnership.Parts]::VerifyCursor($clientRoot)
    [AetherfallOwnership.Parts]::Cursor($clientRoot,$true)
    $state.nativeCursorPatch | Add-Member -Force NoteProperty restoredAt (Get-Date -Format o)
    Write-OwnershipJson $statePath $state
    'Cursor gates restored; all other native instructions and graphics settings preserved.'
    return
}
if ($state.nativeCursorPatch -and -not $Reinstall) {
    Update-CursorOwnershipRecord $clientRoot $state
    'Native cursor instruction bytes verified (four bytes).'
    return
}
if ($VerifyOnly) { throw 'Native cursor patch is not installed.' }
Assert-OwnershipClosed
if (Test-Path -LiteralPath (Join-Path $clientRoot 'bin64/game.dll.patched')) { throw 'Resolve the pending Game.dll update before installing CursorFix.' }
[AetherfallOwnership.Parts]::Cursor($clientRoot,$false)
if ($state.nativeCursorPatch) {
    $state.nativeCursorPatch.PSObject.Properties.Remove('restoredAt')
    $state.nativeCursorPatch | Add-Member -Force NoteProperty ownershipSchema 0
}
Update-CursorOwnershipRecord $clientRoot $state
'Native cursor patch installed (four instruction bytes); unrelated native changes preserved.'
