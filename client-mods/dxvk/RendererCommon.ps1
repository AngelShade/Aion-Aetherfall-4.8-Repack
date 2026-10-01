function Get-RendererChoice([string]$ClientRoot) {
    $path = Join-Path $ClientRoot 'DXVK/renderer.ini'
    if (-not (Test-Path -LiteralPath $path)) { return $true }
    $matches = [regex]::Matches([IO.File]::ReadAllText($path),'(?im)^Vulkan\s*=\s*([01])\s*$')
    if ($matches.Count -ne 1) { throw 'Invalid DXVK/renderer.ini: expected one Vulkan=0 or Vulkan=1 setting.' }
    return $matches[0].Groups[1].Value -eq '1'
}

function Resolve-RendererFile([string]$ClientRoot, [string]$RelativePath) {
    $active = Join-Path $ClientRoot $RelativePath
    if ($RelativePath -notin @('bin32/d3d9.dll','bin64/d3d9.dll')) { return $active }
    $parked = Join-Path $ClientRoot ($RelativePath -replace '/d3d9\.dll$','/d3d9.dxvk-disabled.dll')
    $hasActive = Test-Path -LiteralPath $active
    $hasParked = Test-Path -LiteralPath $parked
    if ($hasActive -eq $hasParked) { throw "Expected exactly one DXVK copy for $RelativePath." }
    if ($hasActive) { return $active }
    return $parked
}
