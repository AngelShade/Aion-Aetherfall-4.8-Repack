param([Parameter(Mandatory=$true)][string]$ClientPath,[Parameter(Mandatory=$true)][string]$OutputRoot)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($OutputRoot)
if (Test-Path -LiteralPath $root) { throw 'Use a fresh cursor fixture.' }
foreach ($relative in @('bin64/Game.dll','bin64/XRenderD3D9.dll','Mods/Renderer/installed.json','dxvk.conf')) {
    $target=Join-Path $root $relative;New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $ClientPath $relative) -Destination $target
}
foreach ($arch in @('32','64')) {
    $rel="bin$arch/d3d9.dll"
    if (-not (Test-Path -LiteralPath (Join-Path $ClientPath $rel))) { $rel="bin$arch/d3d9.dxvk-disabled.dll" }
    $target=Join-Path $root $rel;New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $ClientPath $rel) -Destination $target
}
foreach ($name in @('ClientOwnership.ps1','ClientOwnership.cs')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $root ('Mods/Renderer/'+$name)) }
. (Join-Path $root 'Mods/Renderer/ClientOwnership.ps1')
$helper=Join-Path $PSScriptRoot 'InstallNativeCursorPatch.ps1'
function Assert([bool]$Value,[string]$Message) { if (-not $Value) { throw $Message } }
$before=@{};foreach($rel in @('bin64/Game.dll','bin64/XRenderD3D9.dll')) {$before[$rel]=[IO.File]::ReadAllBytes((Join-Path $root $rel))}
& $helper -ClientPath $root -VerifyOnly
& $helper -ClientPath $root -Restore
foreach($pair in @(@('bin64/Game.dll',0x551c96,0x78,0x18),@('bin64/XRenderD3D9.dll',0x1202d7,0x7c,0x0c))) {
    $path=Join-Path $root $pair[0];$restored=[IO.File]::ReadAllBytes($path)
    Assert ($restored[$pair[1]] -eq $pair[2] -and $restored[$pair[1]+1] -eq $pair[3]) 'Native branch not restored'
    $restored[$pair[1]]=0x90;$restored[$pair[1]+1]=0x90
    Assert ([Linq.Enumerable]::SequenceEqual([byte[]]$restored,[byte[]]$before[$pair[0]])) 'Cursor restore touched other bytes'
}
& $helper -ClientPath $root
[AetherfallOwnership.Parts]::VerifyCursorOriginal($root)
& $helper -ClientPath $root -Reinstall
$record=Join-Path $root 'Mods/Renderer/installed.json';$stamp=(Get-Item -LiteralPath $record).LastWriteTimeUtc.Ticks
& $helper -ClientPath $root -VerifyOnly
Assert ((Get-Item -LiteralPath $record).LastWriteTimeUtc.Ticks -eq $stamp) 'Repeated check wrote its record'
foreach ($rel in $before.Keys) { Assert ([Linq.Enumerable]::SequenceEqual([IO.File]::ReadAllBytes((Join-Path $root $rel)),[byte[]]$before[$rel])) 'Cursor reinstall changed unrelated bytes' }
'PASS: four cursor bytes restored/reinstalled; all other native bytes exact; repeat is read-only.'
