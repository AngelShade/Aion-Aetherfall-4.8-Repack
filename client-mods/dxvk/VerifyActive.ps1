param([string]$ClientPath = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
$clientRoot = (Resolve-Path -LiteralPath $ClientPath).Path
$state = Get-Content -LiteralPath (Join-Path $clientRoot 'DXVK/installed.json') -Raw | ConvertFrom-Json
. (Join-Path $PSScriptRoot 'RendererCommon.ps1')
$enabled = Get-RendererChoice $clientRoot
foreach ($entry in $state.files) {
    if ((Get-FileHash -LiteralPath (Resolve-RendererFile $clientRoot $entry.path)).Hash -ne $entry.installed) {
        throw "Installed file hash failed: $($entry.path)"
    }
}
'Installed DXVK files verified.'
"Next launch renderer: $(if ($enabled) {'Vulkan (DXVK)'} else {'Direct3D 9'})"
$processes = Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue
if (-not $processes) { 'Aion is closed. Launch Aion Start.bat to apply the selected renderer.'; return }
foreach ($process in $processes) {
    $loaded = @($process.Modules | Where-Object { $_.ModuleName -ieq 'd3d9.dll' })
    # Overlays can load the system d3d9.dll alongside the game's DXVK wrapper.
    $wrapper = @($loaded | Where-Object {
        $_.FileName -ieq (Join-Path $clientRoot 'bin64/d3d9.dll') -or
        $_.FileName -ieq (Join-Path $clientRoot 'bin32/d3d9.dll')
    })
    if ($wrapper.Count -gt 0) {
        "Loaded DXVK module: $($wrapper[0].FileName)"
        $log = Get-ChildItem -LiteralPath (Join-Path $clientRoot 'DXVK/logs') -Filter '*_d3d9.log' |
            Where-Object { $_.LastWriteTime -ge $process.StartTime } |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($log) {
            $contents = Get-Content -LiteralPath $log.FullName -Raw
            if ($contents.Contains('Presenter: Actual swapchain properties:')) {
                'Active DXVK rendering confirmed by the current game session Vulkan swapchain.'
            } else { 'DXVK is loaded; device/presentation initialization is still pending.' }
            $contents -split '\r?\n' | Where-Object {
                $_ -match 'DXVK:|Device name:|Found device:|d3d9.samplerAnisotropy|forceSamplerTypeSpecConstants|err:'
            }
        } else { 'No current-session DXVK log yet; initialization is still pending.' }
    } elseif ($loaded.Count -eq 0) {
        'No Direct3D 9 module loaded yet; renderer startup is still pending.'
    } else {
        "Current session uses $($loaded[0].FileName)."
        if ($enabled) { 'Relaunch through Aion Start.bat to activate Vulkan.' }
    }
}
