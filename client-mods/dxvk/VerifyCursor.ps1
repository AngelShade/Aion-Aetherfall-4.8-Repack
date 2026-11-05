param([Parameter(Mandatory=$true)][string]$ClientPath,[switch]$VerifyOnly)
$ErrorActionPreference = 'Stop'
$clientRoot = (Resolve-Path -LiteralPath $ClientPath).Path
$rendererPath = Join-Path $clientRoot 'bin64/XRenderD3D9.dll'
. (Join-Path $clientRoot 'Mods/Renderer/ClientOwnership.ps1')
[AetherfallOwnership.Parts]::VerifyCursor($clientRoot)
if ($VerifyOnly) { 'PASS: both owned native cursor instruction pairs are intact.'; return }
# Read-only live inspection remains opt-in by running without -VerifyOnly.
$game = Get-Process -Name 'aion.bin' -ErrorAction Stop | Where-Object {
    $_.Path -ieq (Join-Path $clientRoot 'bin64/aion.bin')
} | Select-Object -First 1
if (-not $game) { throw 'The expected 64-bit Aion client is not running.' }
$renderer = $game.Modules | Where-Object { $_.FileName -ieq $rendererPath } | Select-Object -First 1
if (-not $renderer) { throw 'Native renderer has not loaded yet.' }
if (-not ('AionCursorStateRead' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class AionCursorStateRead {
    [DllImport("kernel32.dll", SetLastError=true)] public static extern IntPtr OpenProcess(uint access, bool inherit, int id);
    [DllImport("kernel32.dll", SetLastError=true)] public static extern bool ReadProcessMemory(IntPtr process, IntPtr address, byte[] buffer, UIntPtr size, out UIntPtr read);
    [DllImport("kernel32.dll")] public static extern bool CloseHandle(IntPtr handle);
}
'@
}
$handle = [AionCursorStateRead]::OpenProcess(0x1010,$false,$game.Id)
if ($handle -eq [IntPtr]::Zero) { throw 'Could not open read-only renderer state.' }
$values = [ordered]@{}
try {
    foreach ($entry in @(@{Name='r_3dvision_cursor';Rva=0xC367DC;Expected=1},@{Name='r_3dvision_depth';Rva=0xC367E8;Expected=$null})) {
        $buffer = New-Object byte[] 4
        $read = [UIntPtr]::Zero
        if (-not [AionCursorStateRead]::ReadProcessMemory($handle,[IntPtr]($renderer.BaseAddress.ToInt64()+$entry.Rva),$buffer,[UIntPtr]4,[ref]$read) -or $read.ToUInt64() -ne 4) {
            throw 'Native cursor state read failed.'
        }
        $values[$entry.Name] = [BitConverter]::ToInt32($buffer,0)
        if ($null -ne $entry.Expected -and $values[$entry.Name] -ne $entry.Expected) { throw "Native $($entry.Name) is $($values[$entry.Name]); expected $($entry.Expected)." }
    }
    foreach ($entry in @(@{Name='XRenderD3D9.dll';Rva=0x120ED7},@{Name='Game.dll';Rva=0x551C96})) {
        $module = $game.Modules | Where-Object { $_.ModuleName -ieq $entry.Name } | Select-Object -First 1
        if (-not $module) { throw 'Native cursor module not loaded.' }
        $buffer = New-Object byte[] 2
        $read = [UIntPtr]::Zero
        if (-not [AionCursorStateRead]::ReadProcessMemory($handle,[IntPtr]($module.BaseAddress.ToInt64()+$entry.Rva),$buffer,[UIntPtr]2,[ref]$read) -or $read.ToUInt64() -ne 2 -or $buffer[0] -ne 0x90 -or $buffer[1] -ne 0x90) {
            throw "Native cursor patch is not active in $($entry.Name)."
        }
    }
} finally { [void][AionCursorStateRead]::CloseHandle($handle) }
$game.Refresh()
[pscustomobject]@{processId=$game.Id;responsive=$game.Responding;nativeCursorValues=$values;bothCursorInstructionPatchesActive=$true;visualConfirmation='Player check required'}
