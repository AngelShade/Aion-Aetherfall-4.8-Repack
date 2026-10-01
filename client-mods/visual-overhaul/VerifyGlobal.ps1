param([string]$ClientPath=(Split-Path -Parent $PSScriptRoot))
. (Join-Path $PSScriptRoot 'GlobalCommon.ps1')
$client = (Resolve-Path -LiteralPath $ClientPath).Path
$queue = Get-Content -LiteralPath (Join-Path $client 'GraphicsOverhaul/queued.json') -Raw | ConvertFrom-Json
& (Join-Path $PSScriptRoot 'ApplyGlobal.ps1') -ClientPath $client -PackagePath (Join-Path $client ('GraphicsOverhaul/packages/' + $queue.packageId)) -VerifyOnly
foreach ($process in @(Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue)) {
    $layer = @($process.Modules | Where-Object { $_.FileName -ieq (Join-Path $client 'GraphicsOverhaul/vulkan64/ReShade64.dll') })
    if ($layer.Count -eq 0) {
        $rendererConfig = Join-Path $client 'DXVK/renderer.ini'
        if ((Test-Path -LiteralPath $rendererConfig) -and [IO.File]::ReadAllText($rendererConfig) -match '(?im)^ActiveVulkan=0\s*$') {
            'Direct3D 9 was selected for this launch. Global textures and native environment changes remain installed; Vulkan effects are intentionally inactive.'
        } else { 'Current session has not loaded the new effects. Relaunch normally.' }
        continue
    }
    "Loaded Vulkan effects: $($layer[0].FileName)"
    $log = Get-Item -LiteralPath (Join-Path $client 'GraphicsOverhaul/PostFX/ReShade.log')
    if ($log.LastWriteTime -lt $process.StartTime) { throw 'Effects log belongs to an older session.' }
    $contents = Get-Content -LiteralPath $log.FullName -Raw
    $contents -split '\r?\n' | Where-Object { $_ -match 'compiled|runtime environment|ERROR|WARN' }
    if ($contents -match '\| ERROR\s*\|') { throw 'ReShade reported an error. Inspect the log.' }
    if ($contents -notmatch 'Successfully compiled.*AionPresentation.fx' -or $contents -notmatch 'Successfully compiled.*MartysMods_SMAA.fx') { 'Effects are loaded; shader compilation is still pending.' }
}
