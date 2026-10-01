param([Parameter(Mandatory=$true)][string]$ClientPath)
$ErrorActionPreference = 'Stop'
if (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue) { throw 'Exit Aion normally before switching renderers.' }
$client = (Resolve-Path -LiteralPath $ClientPath).Path
. (Join-Path $PSScriptRoot 'RendererCommon.ps1')
$state = Get-Content -LiteralPath (Join-Path $client 'DXVK/installed.json') -Raw | ConvertFrom-Json
if ($state.clientRoot -ne $client -or $state.restoredAt) { throw 'Expected an active DXVK installation.' }
$enabled = Get-RendererChoice $client
$changes = @()
foreach ($entry in $state.files) {
    if ($entry.path -notin @('bin32/d3d9.dll','bin64/d3d9.dll','dxvk.conf')) { throw 'Unexpected renderer file in installation record.' }
    $current = Resolve-RendererFile $client $entry.path
    if ((Get-FileHash -LiteralPath $current).Hash -ne $entry.installed) { throw "Renderer checksum mismatch: $($entry.path). No renderer files changed." }
    if ($entry.path -eq 'dxvk.conf') { continue }
    $destination = Join-Path $client $entry.path
    if (-not $enabled) { $destination = $destination -replace 'd3d9\.dll$','d3d9.dxvk-disabled.dll' }
    if ($current -ne $destination) { $changes += @{source=$current;target=$destination} }
}
# Preflight above covers both architectures before any moves. Move within each
# directory so an interrupted launch remains recoverable and never overwrites a DLL.
$done = @()
try {
    foreach ($change in $changes) {
        Move-Item -LiteralPath $change.source -Destination $change.target
        $done += $change
    }
    $value = [int]$enabled
    [IO.File]::WriteAllText((Join-Path $client 'DXVK/renderer.ini'),"[Renderer]`r`nVulkan=$value`r`nActiveVulkan=$value`r`n",[Text.Encoding]::ASCII)
    [IO.File]::WriteAllText((Join-Path $client 'DXVK/renderer-env.bat'),"@set `"AION_USE_VULKAN=$value`"`r`n",[Text.Encoding]::ASCII)
} catch {
    foreach ($change in $done) { Move-Item -LiteralPath $change.target -Destination $change.source }
    throw
}
if ($enabled) { 'Renderer selected: Vulkan (DXVK).' } else { 'Renderer selected: system Direct3D 9. Vulkan post effects are disabled.' }
