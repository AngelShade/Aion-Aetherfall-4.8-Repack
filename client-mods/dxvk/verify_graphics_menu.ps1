param([Parameter(Mandatory=$true)][string]$ClientPath,[Parameter(Mandatory=$true)][string]$OutputRoot)
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $ClientPath).Path
$fixture = [IO.Path]::GetFullPath($OutputRoot)
if ($fixture -eq $source -or $fixture.StartsWith($source.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Fixture must be outside the client.' }
$marker=Join-Path $fixture '.graphics-ownership-fixture.json'
if (Test-Path -LiteralPath $fixture) {
    if (-not (Test-Path -LiteralPath $marker) -or (Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json).source -ne $source) { throw 'Use a fresh directory or this test marked fixture.' }
} else {
    New-Item -ItemType Directory -Path $fixture | Out-Null
    @{source=$source;kind='graphics-ownership-test'} | ConvertTo-Json | Set-Content -LiteralPath $marker
}
function Assert([bool]$Value,[string]$Message) { if (-not $Value) { throw $Message } }
function Copy-Relative([string]$From,[string]$Relative,[string]$As=$Relative) {
    $dest=Join-Path $fixture $As; New-Item -ItemType Directory -Path (Split-Path $dest -Parent) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $From $Relative) -Destination $dest
}
foreach ($rel in @('bin64/Game.dll','bin64/XRenderD3D9.dll','bin64/AionGraphicsMenu.dll','Data/ui/game/game.pak','L10N/enu/Data/data.pak','Mods/Renderer/installed.json','Mods/GraphicsMenu/installed.json','Mods/GraphicsMenu/package/manifest.json','DXVK/renderer.ini','system.cfg','SystemOptionGraphics.cfg','dxvk.conf')) { Copy-Relative $source $rel }
foreach ($arch in @('32','64')) {
    $rel="bin$arch/d3d9.dll"
    if (-not (Test-Path -LiteralPath (Join-Path $source $rel))) {$rel="bin$arch/d3d9.dxvk-disabled.dll"}
    Copy-Relative $source $rel
}
foreach ($name in @('ClientOwnership.ps1','ClientOwnership.cs','RendererCommon.ps1','SelectRenderer.ps1','QualityProfile.ps1','Restore.ps1')) { Copy-Relative $PSScriptRoot $name ('Mods/Renderer/'+$name) }
Copy-Relative $PSScriptRoot 'ApplyGraphicsMenu.ps1' 'Mods/GraphicsMenu/Install-GraphicsMenu.ps1'
Copy-Relative $PSScriptRoot 'RestoreGraphicsMenu.ps1' 'Mods/GraphicsMenu/RestoreGraphicsMenu.ps1'
Copy-Relative $PSScriptRoot 'ownership.json' 'Mods/GraphicsMenu/ownership.json'
Copy-Relative $PSScriptRoot 'InstallNativeCursorPatch.ps1' 'Mods/CursorFix/Install-CursorFix.ps1'
Copy-Relative $PSScriptRoot 'RendererCommon.ps1' 'Mods/CursorFix/RendererCommon.ps1'
Copy-Relative $source 'bin64/AionGraphicsMenu.dll' 'Mods/GraphicsMenu/package/bin64/AionGraphicsMenu.dll'
[IO.File]::WriteAllText((Join-Path $fixture 'Aion Start.bat'), '@echo off')
. (Join-Path $fixture 'Mods/Renderer/ClientOwnership.ps1')
function Edit-Widget([byte[]]$Raw,[string]$Entry,[string]$Name) {
    $pak=[AetherfallOwnership.OwnedPak]::new($Raw)
    $xml=[AetherfallOwnership.OwnedXml]::Decode($pak.Read($Entry))
    $nodes=@($xml.Descendants('Widget') | Where-Object {$_.Attribute('name').Value -eq $Name})
    Assert ($nodes.Count -eq 1) 'Fixture widget missing'
    $nodes[0].SetAttributeValue('text','DAMAGED')
    $changes=[Collections.Generic.Dictionary[string,byte[]]]::new()
    $changes.Add($Entry,[AetherfallOwnership.OwnedXml]::Encode($xml))
    return ,$pak.Build($changes)
}
function Edit-Other([byte[]]$Raw,[string]$Own) {
    $pak=[AetherfallOwnership.OwnedPak]::new($Raw)
    $name=@($pak.Names | Where-Object {$_ -ne $Own})[0]
    $changes=[Collections.Generic.Dictionary[string,byte[]]]::new()
    $changes.Add($name,[byte[]]($pak.Read($name)+[byte]65))
    return ,$pak.Build($changes)
}
function Assert-OtherKept([byte[]]$Before,[byte[]]$After,[string]$Own) {
    $a=[AetherfallOwnership.OwnedPak]::new($Before);$b=[AetherfallOwnership.OwnedPak]::new($After)
    foreach ($name in $a.Names) {
        if ($name -ne $Own) {
            Assert ([Linq.Enumerable]::SequenceEqual([byte[]]$a.Read($name),[byte[]]$b.Read($name))) ('Restore lost unrelated member '+$name)
        }
    }
}
$menu=Join-Path $fixture 'Mods/GraphicsMenu/Install-GraphicsMenu.ps1'
$cursor=Join-Path $fixture 'Mods/CursorFix/Install-CursorFix.ps1'
function Menu { & $menu -ClientPath $fixture -VerifyOnly }
function Cursor { & $cursor -ClientPath $fixture -VerifyOnly }
function Reject([scriptblock]$Action,[string]$Expected) {
    $errorText=$null;try { & $Action } catch { $errorText=$_.ToString() }
    Assert ($null -ne $errorText -and $errorText -match $Expected) ('Expected refusal: '+$Expected+'; got '+$errorText)
}
function Tamper([string]$Relative,[int]$Offset,[scriptblock]$Check,[string]$Message) {
    $path=Join-Path $fixture $Relative;$before=[IO.File]::ReadAllBytes($path);$bad=[byte[]]$before.Clone();$bad[$Offset]=$bad[$Offset] -bxor 1
    [IO.File]::WriteAllBytes($path,$bad);try { Reject $Check $Message } finally { [IO.File]::WriteAllBytes($path,$before) }
}
$spec=Read-OwnershipJson (Join-Path $fixture 'Mods/GraphicsMenu/ownership.json')
$game=Join-Path $fixture 'bin64/Game.dll'
$bytes=[IO.File]::ReadAllBytes($game);$bytes[0x2000]=$bytes[0x2000] -bxor 1;[IO.File]::WriteAllBytes($game,$bytes)
$legacyPath=Join-Path $fixture 'Mods/GraphicsMenu/installed.json'
$legacyHash=(Get-FileHash -LiteralPath $legacyPath).Hash
Tamper 'bin64/Game.dll' $spec.ranges[0].offset {Menu} 'Graphics menu hook/import bytes damaged'
Assert ((Get-FileHash -LiteralPath $legacyPath).Hash -eq $legacyHash) 'A damaged legacy install was migrated'
Menu; Cursor
$paths=@('Mods/GraphicsMenu/installed.json','Mods/GraphicsMenu/package/manifest.json','Mods/Renderer/installed.json')
$before=@($paths|ForEach-Object{ $p=Join-Path $fixture $_; "$((Get-FileHash -LiteralPath $p).Hash)|$((Get-Item -LiteralPath $p).LastWriteTimeUtc.Ticks)" })
Menu; Cursor
$after=@($paths|ForEach-Object{ $p=Join-Path $fixture $_; "$((Get-FileHash -LiteralPath $p).Hash)|$((Get-Item -LiteralPath $p).LastWriteTimeUtc.Ticks)" })
Assert (($before -join '|') -ceq ($after -join '|')) 'Second migration rewrote a record'
'PASS old record migration, unrelated Game.dll edit, second-run no-op'
Tamper 'bin64/Game.dll' $spec.ranges[0].offset {Menu} 'Graphics menu hook/import bytes damaged'
Tamper 'bin64/Game.dll' $spec.ranges[2].offset {Menu} 'Graphics menu hook/import bytes damaged'
Tamper 'bin64/AionGraphicsMenu.dll' 100 {Menu} 'Graphics menu DLL damaged'
Tamper 'bin64/Game.dll' 0x551c96 {Cursor} 'Native cursor instructions damaged'
Tamper 'bin64/XRenderD3D9.dll' 0x1202d7 {Cursor} 'Native cursor instructions damaged'
foreach ($u in $spec.ui) {
    $p=Join-Path $fixture $u.path;$original=[IO.File]::ReadAllBytes($p)
    foreach ($name in @('cb_use_vulkan','st_vulkan_status')) {
        [IO.File]::WriteAllBytes($p,(Edit-Widget $original $u.entry $name))
        try { Reject {Menu} ('Graphics menu control damaged: '+$name) } finally {[IO.File]::WriteAllBytes($p,$original)}
    }
    [IO.File]::WriteAllBytes($p,(Edit-Other $original $u.entry));Menu
}
'PASS owned hook, import, DLL, both UI widgets in both archives and both cursor tamper refusals; unrelated archive edits pass'
$native=[IO.File]::ReadAllBytes($game)
$uiBefore=@{};foreach($u in $spec.ui){$uiBefore[$u.path]=[IO.File]::ReadAllBytes((Join-Path $fixture $u.path))}
$choice=[IO.File]::ReadAllText((Join-Path $fixture 'DXVK/renderer.ini'))
& (Join-Path $fixture 'Mods/GraphicsMenu/RestoreGraphicsMenu.ps1') -ClientPath $fixture
$restored=[IO.File]::ReadAllBytes($game)
Assert ($restored[0x2000] -eq $native[0x2000]) 'Graphics restore lost unrelated native edit'
Assert ($restored[0x551c96] -eq 0x90) 'Graphics restore removed cursor'
foreach($u in $spec.ui){Assert-OtherKept $uiBefore[$u.path] ([IO.File]::ReadAllBytes((Join-Path $fixture $u.path))) $u.entry}
Assert ([IO.File]::ReadAllText((Join-Path $fixture 'DXVK/renderer.ini')) -ceq $choice) 'Restore lost renderer choice'
& $cursor -ClientPath $fixture -Restore
Assert ([IO.File]::ReadAllBytes($game)[0x2000] -eq $native[0x2000]) 'Cursor restore lost unrelated native edit'
Assert ([IO.File]::ReadAllBytes($game)[0x551c96] -eq 0x78) 'Cursor branch not restored'
'PASS own-parts Graphics and cursor restore retains unrelated native and archive edits and renderer choice'
& $cursor -ClientPath $fixture
[AetherfallOwnership.Parts]::VerifyCursorOriginal($fixture)
& $cursor -ClientPath $fixture -Reinstall
Remove-Item -LiteralPath (Join-Path $fixture 'Mods/GraphicsMenu/installed.json')
& $menu -ClientPath $fixture -VerifyOnly
& $menu -ClientPath $fixture
Menu;Cursor
Assert ([IO.File]::ReadAllBytes($game)[0x2000] -eq $native[0x2000]) 'Fresh installation lost native edit'
foreach($u in $spec.ui){Assert-OtherKept $uiBefore[$u.path] ([IO.File]::ReadAllBytes((Join-Path $fixture $u.path))) $u.entry}
'PASS fresh compact installation and checks; no shared package or restore images'
& (Join-Path $fixture 'Mods/Renderer/SelectRenderer.ps1') -ClientPath $fixture
[IO.File]::WriteAllText((Join-Path $fixture 'DXVK/renderer.ini'),"[Renderer]`r`nVulkan=0`r`nActiveVulkan=1`r`n")
& (Join-Path $fixture 'Mods/Renderer/SelectRenderer.ps1') -ClientPath $fixture
Cursor;Menu
[IO.File]::WriteAllText((Join-Path $fixture 'DXVK/renderer.ini'),"[Renderer]`r`nVulkan=1`r`nActiveVulkan=0`r`n")
& (Join-Path $fixture 'Mods/Renderer/SelectRenderer.ps1') -ClientPath $fixture
& (Join-Path $fixture 'Mods/Renderer/QualityProfile.ps1') -ClientPath $fixture -Startup
Menu;Cursor
'PASS Vulkan/Direct3D switches, HQ startup and independent checks'
