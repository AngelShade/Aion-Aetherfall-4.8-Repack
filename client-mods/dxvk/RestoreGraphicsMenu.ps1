param([Parameter(Mandatory=$true)][string]$ClientPath)
$ErrorActionPreference = 'Stop'
$client = (Resolve-Path -LiteralPath $ClientPath).Path
. (Join-Path $client 'Mods/Renderer/ClientOwnership.ps1')
$statePath = Join-Path $client 'Mods/GraphicsMenu/installed.json'
$state = Read-OwnershipJson $statePath
if ($state.restoredAt) { 'Graphics menu already restored.'; return }
Assert-OwnershipClosed
[AetherfallOwnership.Parts]::RestoreGraphics($client,(Get-GraphicsOwnership $client))
$state | Add-Member -Force NoteProperty restoredAt (Get-Date -Format o)
Write-OwnershipJson $statePath $state
'Graphics menu hooks, import, DLL and controls removed. Other mods, cursor and renderer choice preserved.'
