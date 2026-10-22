$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSEdition -eq 'Desktop') { $env:PSModulePath = (Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/Modules') + ';' + $env:PSModulePath }
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$manifest = Get-Content -LiteralPath (Join-Path $root 'FILES.json') -Raw | ConvertFrom-Json
$optional=@{}
$hdRoot=Join-Path $root 'Client/Mods/HDTextures'
if(Test-Path -LiteralPath (Join-Path $hdRoot 'installed.json')) {
    $state=Get-Content -LiteralPath (Join-Path $hdRoot 'installed.json') -Raw | ConvertFrom-Json
    if($state.enabled) {
        & (Join-Path $root 'Control/HDTextures.ps1') -ClientPath (Join-Path $root 'Client') -Action Verify
        $component=Get-Content -LiteralPath (Join-Path $hdRoot 'component.json') -Raw | ConvertFrom-Json
        foreach($entry in $component.files){$optional['Client/'+$entry.path]=$entry.hdSha256}
        foreach($shared in $component.shared){$optional['Client/'+$shared.packagePath]=$optional['Client/'+$shared.path]}
    }
}
$count = 0
foreach ($entry in $manifest.files) {
    if ($entry.mutable) { continue }
    $path = [IO.Path]::GetFullPath((Join-Path $root $entry.path))
    if (-not $path.StartsWith($root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid manifest path.' }
    $expected=if($optional.ContainsKey($entry.path)){$optional[$entry.path]}else{$entry.sha256}
    if (-not (Test-Path -LiteralPath $path) -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $expected) { throw "FAIL: $($entry.path)" }
    $count++
    if ($count % 5000 -eq 0) { Write-Host "Verified $count immutable files..." }
}
"PASS: $count immutable payload files verified for the selected texture profile. Mutable owner settings and runtime state are excluded."
