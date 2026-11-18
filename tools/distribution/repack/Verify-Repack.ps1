$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSEdition -eq 'Desktop') { $env:PSModulePath = (Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/Modules') + ';' + $env:PSModulePath }
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$manifest = Get-Content -LiteralPath (Join-Path $root 'FILES.json') -Raw | ConvertFrom-Json
$verifiedHd=@{}
$hdRoot=Join-Path $root 'Client/Mods/HDTextures'
if(Test-Path -LiteralPath (Join-Path $hdRoot 'installed.json')) {
    $state=Get-Content -LiteralPath (Join-Path $hdRoot 'installed.json') -Raw | ConvertFrom-Json
    if($state.enabled) {
        & (Join-Path $root 'Control/HDTextures.ps1') -ClientPath (Join-Path $root 'Client') -Action Verify
        $component=Get-Content -LiteralPath (Join-Path $hdRoot 'component.json') -Raw | ConvertFrom-Json
        # The manager verifies the selected compatible version and the shared archives.
        foreach($entry in $component.files){$verifiedHd['Client/'+$entry.path]=$true}
        foreach($shared in $component.shared){$verifiedHd['Client/'+$shared.packagePath]=$true}
    }
}
# Files the launcher rewrites itself (interface theme variants, companion artwork index) match the exact
# SHA-256 it recorded after its own write; anything else that differs is still a failure.
$generated=@{}
$record=Join-Path $root 'Client/Mods/Launcher/generated-files.json'
if(Test-Path -LiteralPath $record) {
    try {
        $files=(Get-Content -LiteralPath $record -Raw | ConvertFrom-Json).files
        foreach($item in $files.PSObject.Properties){ if($item.Value -match '^[0-9a-fA-F]{64}$'){ $generated['Client/'+($item.Name.Replace('\','/'))]=$item.Value } }
    } catch { $generated=@{} }
}
$count = 0
foreach ($entry in $manifest.files) {
    if ($entry.mutable) { continue }
    $path = [IO.Path]::GetFullPath((Join-Path $root $entry.path))
    if (-not $path.StartsWith($root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid manifest path.' }
    if (-not $verifiedHd.ContainsKey($entry.path)) {
        $ok=$false
        if (Test-Path -LiteralPath $path) {
            $actual=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
            $ok=($actual -eq $entry.sha256) -or ($generated.ContainsKey($entry.path) -and $actual -eq $generated[$entry.path])
        } elseif ($entry.path -in @('Client/bin32/d3d9.dll','Client/bin64/d3d9.dll')) {
            # System Direct3D 9 selected: SelectRenderer.ps1 keeps the DXVK DLL as d3d9.dxvk-disabled.dll.
            $disabled=$path.Substring(0,$path.Length-'d3d9.dll'.Length)+'d3d9.dxvk-disabled.dll'
            $ok=(Test-Path -LiteralPath $disabled) -and ((Get-FileHash -LiteralPath $disabled -Algorithm SHA256).Hash -eq $entry.sha256)
        }
        if (-not $ok) { throw "FAIL: $($entry.path)" }
    }
    $count++
    if ($count % 5000 -eq 0) { Write-Host "Verified $count immutable files..." }
}
"PASS: $count immutable payload files verified for the selected texture profile. Mutable owner settings and runtime state are excluded."
