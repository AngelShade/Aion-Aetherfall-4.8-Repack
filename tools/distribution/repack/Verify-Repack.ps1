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
$count = 0
foreach ($entry in $manifest.files) {
    if ($entry.mutable) { continue }
    $path = [IO.Path]::GetFullPath((Join-Path $root $entry.path))
    if (-not $path.StartsWith($root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid manifest path.' }
    if (-not $verifiedHd.ContainsKey($entry.path) -and
        (-not (Test-Path -LiteralPath $path) -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $entry.sha256)) { throw "FAIL: $($entry.path)" }
    $count++
    if ($count % 5000 -eq 0) { Write-Host "Verified $count immutable files..." }
}
"PASS: $count immutable payload files verified for the selected texture profile. Mutable owner settings and runtime state are excluded."
