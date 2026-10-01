$ErrorActionPreference = 'Stop'
$fixture = Join-Path $PSScriptRoot ('test-workspace/native-cursor-' + [guid]::NewGuid().ToString('N'))
$source = (Join-Path $env:USERPROFILE 'Downloads\aion-4.8-na\Aion 4.8 NA')
foreach ($path in @('bin32/version.dll','bin64/version.dll','bin64/XRenderD3D9.dll','bin64/Game.dll')) {
    $target = Join-Path $fixture $path
    New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $source $path) -Destination $target
}
$latin1 = [Text.Encoding]::GetEncoding(28591)
function Encode([string]$Line) { $latin1.GetString([byte[]]@($latin1.GetBytes($Line) | ForEach-Object { $_ -bxor 255 })) }
$originalConfig = '-- [System-Configuration Ver1.0]' + "`r`n" + (Encode 's_MasterVolume = "0.91"') + "`r`n"
[IO.File]::WriteAllBytes((Join-Path $fixture 'system.cfg'),$latin1.GetBytes($originalConfig))
[IO.File]::WriteAllText((Join-Path $fixture 'SystemOptionGraphics.cfg'),"USE_MG = `"1`"`r`nMG_SHADOW = `"2`"`r`n")
[IO.File]::WriteAllText((Join-Path $fixture 'Aion Start.bat'),"@echo off`r`nstart `"`" `"bin64\aion.bin`" -ip:127.0.0.1`r`n")
function Get-Process { param($Name,$ErrorAction) }
function Assert([bool]$Condition,[string]$Message) { if (-not $Condition) { throw $Message } }
& (Join-Path $PSScriptRoot 'Install.ps1') -ClientPath $fixture
& (Join-Path $PSScriptRoot 'InstallNativeCursorPatch.ps1') -ClientPath $fixture
& (Join-Path $PSScriptRoot 'InstallNativeCursorPatch.ps1') -ClientPath $fixture
$state = Get-Content -LiteralPath (Join-Path $fixture 'DXVK/installed.json') -Raw | ConvertFrom-Json
Assert ($state.nativeCursorPatch.files.Count -eq 2) 'Expected two native cursor patches'
foreach ($entry in $state.nativeCursorPatch.files) {
    $installed = [IO.File]::ReadAllBytes((Join-Path $fixture $entry.path))
    $original = [IO.File]::ReadAllBytes($entry.backupPath)
    Assert ($installed[$entry.offset] -eq 0x90 -and $installed[$entry.offset+1] -eq 0x90) 'Cursor branch was not patched'
    $installed[$entry.offset] = $original[$entry.offset]
    $installed[$entry.offset+1] = $original[$entry.offset+1]
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $reconstructed = [BitConverter]::ToString($sha.ComputeHash($installed)).Replace('-','') } finally { $sha.Dispose() }
    Assert ($reconstructed -eq $entry.original) 'Patch changed bytes outside the intended cursor branch'
}
$gamePath = Join-Path $fixture 'bin64/Game.dll'
$gamePatched = [IO.File]::ReadAllBytes($gamePath)
$configPatched = [IO.File]::ReadAllBytes((Join-Path $fixture 'system.cfg'))
[IO.File]::WriteAllBytes($gamePath,([byte[]]($gamePatched + [byte]0)))
$rejected = $false
try { & (Join-Path $fixture 'DXVK/Restore.ps1') -ClientPath $fixture } catch { $rejected = $true }
Assert $rejected 'Restore did not reject later native changes'
Assert (Test-Path -LiteralPath (Join-Path $fixture 'bin64/d3d9.dll')) 'Rejected restore modified the renderer'
Assert ($latin1.GetString([IO.File]::ReadAllBytes((Join-Path $fixture 'system.cfg'))) -ceq $latin1.GetString($configPatched)) 'Rejected restore modified settings'
[IO.File]::WriteAllBytes($gamePath,$gamePatched)
& (Join-Path $fixture 'DXVK/Restore.ps1') -ClientPath $fixture
foreach ($entry in $state.nativeCursorPatch.files) {
    Assert ((Get-FileHash -LiteralPath (Join-Path $fixture $entry.path)).Hash -eq $entry.original) 'Native cursor restore was not byte exact'
}
Assert ($latin1.GetString([IO.File]::ReadAllBytes((Join-Path $fixture 'system.cfg'))) -ceq $originalConfig) 'Cursor config restore lost original settings'
'PASS: exactly four cursor instruction bytes changed; guarded restore rejects later edits and restores both native binaries and config exactly.'
