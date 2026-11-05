param([Parameter(Mandatory=$true)][string]$ClientPath,[Parameter(Mandatory=$true)][string]$OutputRoot)
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSEdition -ne 'Desktop' -or $PSVersionTable.PSVersion.Major -ne 5) {
    throw 'Run this check with powershell.exe 5.1.'
}
$source = (Resolve-Path -LiteralPath $ClientPath).Path
$output = [IO.Path]::GetFullPath($OutputRoot).TrimEnd('\')
if ($output -eq $source -or $output.StartsWith($source.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase) -or
    $output.StartsWith([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) {
    throw 'Use a fresh external output directory outside the source and client.'
}
if (Test-Path -LiteralPath $output) { throw 'Use a fresh output directory; existing proof is preserved.' }
$fixture = Join-Path $output ('client-'+[char]0x00e9+[char]0xd55c)
New-Item -ItemType Directory -Path $fixture -Force | Out-Null
$powershell = Join-Path $PSHOME 'powershell.exe'
. (Join-Path $PSScriptRoot 'ClientOwnership.ps1')
function Assert([bool]$Value,[string]$Message) { if (-not $Value) { throw $Message } }
function Copy-Relative([string]$From,[string]$Relative,[string]$As=$Relative) {
    $target = Join-Path $fixture $As
    New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $From $Relative) -Destination $target
}
$native = @('bin64/Game.dll','bin64/XRenderD3D9.dll','bin64/AionGraphicsMenu.dll','Data/ui/game/game.pak','L10N/enu/Data/data.pak')
$records = @('Mods/GraphicsMenu/installed.json','Mods/GraphicsMenu/package/manifest.json','Mods/Renderer/installed.json')
foreach ($rel in ($native + $records + @('dxvk.conf','DXVK/renderer.ini'))) { Copy-Relative $source $rel }
foreach ($arch in @('32','64')) {
    $rel = "bin$arch/d3d9.dll"
    if (-not (Test-Path -LiteralPath (Join-Path $source $rel))) { $rel = "bin$arch/d3d9.dxvk-disabled.dll" }
    Copy-Relative $source $rel
}
foreach ($name in @('ClientOwnership.ps1','ClientOwnership.cs','RendererCommon.ps1','SelectRenderer.ps1','VerifyActive.ps1')) {
    Copy-Relative $PSScriptRoot $name ('Mods/Renderer/'+$name)
}
Copy-Relative $PSScriptRoot 'ApplyGraphicsMenu.ps1' 'Mods/GraphicsMenu/Install-GraphicsMenu.ps1'
Copy-Relative $PSScriptRoot 'InstallNativeCursorPatch.ps1' 'Mods/CursorFix/Install-CursorFix.ps1'
Copy-Relative $PSScriptRoot 'RendererCommon.ps1' 'Mods/CursorFix/RendererCommon.ps1'
Copy-Relative $source 'Mods/GraphicsMenu/ownership.json'
$templates = @{}
foreach ($rel in $records) { $templates[$rel] = Read-OwnershipJson (Join-Path $fixture $rel) -Raw }
$watched = $records + @('Mods/GraphicsMenu/ownership.json')
function Snapshot {
    $snapshot = @{}
    foreach ($rel in $watched) {
        $path = Join-Path $fixture $rel
        $snapshot[$rel] = [pscustomobject]@{
            bytes=[Convert]::ToBase64String([IO.File]::ReadAllBytes($path))
            sha256=(Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant()
            length=(Get-Item -LiteralPath $path).Length
            ticks=(Get-Item -LiteralPath $path).LastWriteTimeUtc.Ticks
        }
    }
    return $snapshot
}
function Assert-Unchanged($Before,[string]$Label) {
    $after = Snapshot
    foreach ($rel in $watched) {
        Assert ($Before[$rel].bytes -ceq $after[$rel].bytes -and $Before[$rel].ticks -eq $after[$rel].ticks) "$Label changed $rel"
    }
}
$runs = New-Object 'Collections.Generic.List[object]'
function Invoke-Check([string]$Relative,[string]$Label,[switch]$Refuse) {
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $powershell
    $start.Arguments = '-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "'+(Join-Path $fixture $Relative)+'" -ClientPath "'+$fixture+'"'
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($start)
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    $code = $process.ExitCode
    $process.Dispose()
    $runs.Add([pscustomobject]@{label=$Label;script=$Relative;arguments=$start.Arguments;exitCode=$code;stdout=$stdout;stderr=$stderr})
    if ($Refuse) { Assert ($code -ne 0) "$Label accepted tampering" }
    else { Assert ($code -eq 0) "$Label failed: $stdout $stderr" }
}
function Launch([string]$Label) {
    Invoke-Check 'Mods/GraphicsMenu/Install-GraphicsMenu.ps1' ($Label+' Graphics')
    Invoke-Check 'Mods/CursorFix/Install-CursorFix.ps1' ($Label+' cursor')
}
function Write-Legacy([string]$Path,$Value,[string]$Encoding) {
    $text = $Value | ConvertTo-Json -Depth 30
    if ($Encoding -eq 'ansi') {
        # An ANSI JSON file can still carry Hangul: escape unrepresentable code units.
        $ansi = [Text.Encoding]::Default
        $text = [regex]::Replace($text,'[^\x00-\x7f]',[Text.RegularExpressions.MatchEvaluator]{
            param($match)
            if ($ansi.GetString($ansi.GetBytes($match.Value)) -ceq $match.Value) { return $match.Value }
            '\u{0:x4}' -f [int][char]$match.Value
        })
        $bytes = $ansi.GetBytes($text)
        $invalid = $false
        try { $null = [Text.UTF8Encoding]::new($false,$true).GetString($bytes) } catch [Text.DecoderFallbackException] { $invalid = $true }
        Assert $invalid 'ANSI fixture must exercise invalid UTF-8 fallback on this system code page.'
    } else {
        $utf8 = [Text.UTF8Encoding]::new(($Encoding -eq 'utf8-bom'))
        $bytes = [byte[]]($utf8.GetPreamble() + $utf8.GetBytes($text))
    }
    [IO.File]::WriteAllBytes($Path,$bytes)
    [IO.File]::SetLastWriteTimeUtc($Path,[DateTime]::UtcNow.AddDays(-1))
}
function Assert-Values([string]$Root) {
    foreach ($rel in $records) {
        $value = Read-OwnershipJson (Join-Path $fixture $rel)
        Assert ($value.clientRoot -ceq $Root) "Wrong clientRoot in $rel"
        Assert ($value.backupRoot -ceq (Join-Path $Root 'Mods/Backups/Renderer/encoding')) "Wrong backupRoot in $rel"
        Assert ($value.encodingProbe.nested.backupPath -ceq (Join-Path $Root 'Mods/Backups/Renderer/encoding/bin64/Game.dll')) "Wrong nested backupPath in $rel"
    }
}
$cases = New-Object 'Collections.Generic.List[object]'
foreach ($encoding in @('utf8-bom','utf8','ansi')) {
    foreach ($rel in $records) {
        $value = $templates[$rel] | ConvertFrom-Json
        $value | Add-Member -Force NoteProperty clientRoot $fixture
        $value | Add-Member -Force NoteProperty backupRoot (Join-Path $fixture 'Mods/Backups/Renderer/encoding')
        $value | Add-Member -Force NoteProperty encodingProbe ([pscustomobject]@{nested=[pscustomobject]@{backupPath=(Join-Path $fixture 'Mods/Backups/Renderer/encoding/bin64/Game.dll')}})
        $value | Add-Member -Force NoteProperty ownershipSchema 0
        if ($rel -like '*Renderer/installed.json') { $value.nativeCursorPatch | Add-Member -Force NoteProperty ownershipSchema 0 }
        Write-Legacy (Join-Path $fixture $rel) $value $encoding
    }
    Assert-Values $fixture
    $before = Snapshot
    Invoke-Check 'Mods/Renderer/SelectRenderer.ps1' ($encoding+' legacy renderer selection')
    Invoke-Check 'Mods/Renderer/VerifyActive.ps1' ($encoding+' legacy renderer verification')
    Assert-Unchanged $before ($encoding+' legacy renderer consumers')
    Launch ($encoding+' migration')
    Assert-Values $fixture
    $current = Snapshot
    foreach ($rel in $records) {
        Assert ($current[$rel].bytes -cne $before[$rel].bytes -and $current[$rel].ticks -ne $before[$rel].ticks) "No migration for $encoding $rel"
        Assert (-not ([IO.File]::ReadAllBytes((Join-Path $fixture $rel)) | Where-Object { $_ -gt 127 })) "Non-ASCII output: $rel"
        # The unchanged ANSI-style PowerShell consumers must also decode new records.
        $plain = Get-Content -LiteralPath (Join-Path $fixture $rel) -Raw | ConvertFrom-Json
        Assert ($plain.clientRoot -ceq $fixture) "Default decoder failed: $rel"
    }
    for ($launch=1; $launch -le 6; $launch++) {
        Launch ($encoding+' repeat '+$launch)
        Assert-Unchanged $current ($encoding+' repeat '+$launch)
        Assert-Values $fixture
    }
    $evidence = @{}
    foreach ($rel in $watched) { $evidence[$rel] = $current[$rel] | Select-Object sha256,length,ticks }
    $cases.Add([pscustomobject]@{encoding=$encoding;migratedOnce=$true;sixLaunchesReadOnly=$true;valuesCorrect=$true;records=$evidence})
    Write-Output "PASS: $encoding decoded, migrated once, six subsequent launches read-only."
}
# Actually move the scratch client, retaining the old root in all three records.
$oldRoot = $fixture
$moved = Join-Path $output ('moved-'+[char]0x00e9+[char]0xd55c)
foreach ($path in @($fixture,$moved)) {
    Assert ([IO.Path]::GetFullPath($path).StartsWith($output+'\',[StringComparison]::OrdinalIgnoreCase)) 'Move escapes output directory.'
}
Move-Item -LiteralPath $fixture -Destination $moved
$fixture = $moved
$before = Snapshot
Launch 'moved migration'
$current = Snapshot
foreach ($rel in @($records[0],$records[2])) { Assert ($current[$rel].bytes -cne $before[$rel].bytes) "Moved record not updated: $rel" }
foreach ($rel in @($records[0],$records[2])) {
    Assert ((Read-OwnershipJson (Join-Path $fixture $rel)).clientRoot -ceq $fixture) "Moved root incorrect: $rel"
}
# Historical backup paths remain exact; migration must not invent replacement backups.
foreach ($rel in $records) {
    $value = Read-OwnershipJson (Join-Path $fixture $rel)
    Assert ($value.backupRoot -ceq (Join-Path $oldRoot 'Mods/Backups/Renderer/encoding')) 'Moved backupRoot was corrupted'
    Assert ($value.encodingProbe.nested.backupPath -ceq (Join-Path $oldRoot 'Mods/Backups/Renderer/encoding/bin64/Game.dll')) 'Moved nested backupPath was corrupted'
}
for ($launch=1; $launch -le 6; $launch++) { Launch ('moved repeat '+$launch); Assert-Unchanged $current ('moved repeat '+$launch) }
Write-Output 'PASS: moved client updated once; six subsequent launches read-only.'
$spec = Read-OwnershipJson (Join-Path $fixture 'Mods/GraphicsMenu/ownership.json')
$hook = @($spec.ranges | Where-Object { $_.original })[0].offset
foreach ($tamper in @(
    @{name='hook';path='bin64/Game.dll';offset=[int]$hook;script='Mods/GraphicsMenu/Install-GraphicsMenu.ps1';message='Graphics menu hook'},
    @{name='cursor';path='bin64/XRenderD3D9.dll';offset=0x1202d7;script='Mods/CursorFix/Install-CursorFix.ps1';message='Native cursor instructions damaged'}
)) {
    $path = Join-Path $fixture $tamper.path
    $stream = [IO.File]::Open($path,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite)
    try { $stream.Position=$tamper.offset; $original=$stream.ReadByte(); $stream.Position=$tamper.offset; $stream.WriteByte([byte]($original -bxor 1)) } finally { $stream.Dispose() }
    try {
        Invoke-Check $tamper.script ($tamper.name+' tamper') -Refuse
        Assert ($runs[$runs.Count-1].stderr.Contains($tamper.message)) 'Refusal did not identify damaged owned bytes'
        Assert-Unchanged $current ($tamper.name+' refusal')
    } finally {
        $stream = [IO.File]::Open($path,[IO.FileMode]::Open,[IO.FileAccess]::Write)
        try { $stream.Position=$tamper.offset; $stream.WriteByte([byte]$original) } finally { $stream.Dispose() }
    }
    Invoke-Check $tamper.script ($tamper.name+' restored')
    Assert-Unchanged $current ($tamper.name+' repaired check')
}
foreach ($rel in $native) {
    Assert ((Get-FileHash -LiteralPath (Join-Path $fixture $rel)).Hash -eq (Get-FileHash -LiteralPath (Join-Path $source $rel)).Hash) "Scratch native file changed: $rel"
}
$mainFixture = $fixture
# Exercise the maintained portable preparer without updating any repack.
$package = Join-Path $output ('portable-'+[char]0x00e9+[char]0xd55c)
$fixture = Join-Path $package 'Client'
$control = Join-Path $package 'Control'
New-Item -ItemType Directory -Path $control -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../../tools/distribution/repack/Prepare-Client.ps1') -Destination $control
foreach ($rel in @('bin64/Game.dll','bin64/aion.bin','Mods/GraphicsMenu/ownership.json')) { Copy-Relative $source $rel }
foreach ($name in @('ClientOwnership.ps1','ClientOwnership.cs')) { Copy-Relative $PSScriptRoot $name ('Mods/Renderer/'+$name) }
foreach ($rel in $records) {
    $value = $templates[$rel] | ConvertFrom-Json
    $value | Add-Member -Force NoteProperty clientRoot $oldRoot
    $value | Add-Member -Force NoteProperty backupRoot (Join-Path $oldRoot 'Mods/Backups/Renderer/encoding')
    $value | Add-Member -Force NoteProperty encodingProbe ([pscustomobject]@{nested=[pscustomobject]@{backupPath=(Join-Path $oldRoot 'Mods/Backups/Renderer/encoding/bin64/Game.dll')};slashPath=$oldRoot.Replace('\','/')})
    $path = Join-Path $fixture $rel
    New-Item -ItemType Directory -Path (Split-Path $path -Parent) -Force | Out-Null
    Write-OwnershipJson $path $value
}
$iniPath = Join-Path $fixture 'Mods/GraphicsOverhaul/PostFX/ReShade.ini'
New-Item -ItemType Directory -Path (Split-Path $iniPath -Parent) -Force | Out-Null
[IO.File]::WriteAllText($iniPath,('TextureSearchPaths='+$oldRoot),[Text.UTF8Encoding]::new($false))
$portablePath = Join-Path $fixture 'Mods/portable-client.json'
$portable = [pscustomobject]@{preparedRoot=$oldRoot;gameDllSha256=(Get-FileHash -LiteralPath (Join-Path $fixture 'bin64/Game.dll')).Hash;aionBinSha256=(Get-FileHash -LiteralPath (Join-Path $fixture 'bin64/aion.bin')).Hash;metadataFiles=$records}
Write-Legacy $portablePath $portable 'utf8'
Invoke-Check '../Control/Prepare-Client.ps1' 'portable escaped paths migration'
Assert-Values $fixture
foreach ($rel in $records) {
    $value = Read-OwnershipJson (Join-Path $fixture $rel)
    Assert ($value.encodingProbe.slashPath -ceq $fixture.Replace('\','/')) 'Escaped forward-slash root was not rebased'
    Assert (-not ([IO.File]::ReadAllBytes((Join-Path $fixture $rel)) | Where-Object { $_ -gt 127 })) 'Portable metadata is not ASCII'
}
Assert ((Read-OwnershipJson $portablePath).preparedRoot -ceq $fixture) 'Portable root was not rebased'
$watched += @('Mods/portable-client.json','Mods/GraphicsOverhaul/PostFX/ReShade.ini')
$current = Snapshot
for ($launch=1; $launch -le 6; $launch++) { Invoke-Check '../Control/Prepare-Client.ps1' ('portable repeat '+$launch); Assert-Unchanged $current ('portable repeat '+$launch) }
Write-Output 'PASS: portable metadata rebased escaped paths once; six subsequent checks read-only.'
$proof = [pscustomobject]@{powershell=$PSVersionTable.PSVersion.ToString();ansiCodePage=[Text.Encoding]::Default.CodePage;source=$source;fixture=$mainFixture;portableFixture=$fixture;cases=@($cases.ToArray());movedOnceReadOnly=$true;portableOnceReadOnly=$true;tamperRefusals=2;nativeFilesUnchanged=$true;runs=@($runs.ToArray())}
Write-OwnershipJson (Join-Path $output 'proof.json') $proof
Write-Output ('PASS: own-hook/cursor tamper refused; native files unchanged; '+$runs.Count+' real powershell.exe invocations. Proof: '+(Join-Path $output 'proof.json'))
