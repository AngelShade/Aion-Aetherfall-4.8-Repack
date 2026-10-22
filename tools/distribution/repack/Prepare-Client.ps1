param([Parameter(Mandatory=$true)][string]$ClientPath)
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSEdition -eq 'Desktop') { $env:PSModulePath = (Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/Modules') + ';' + $env:PSModulePath }
$client = (Resolve-Path -LiteralPath $ClientPath).Path
$package = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $client.Equals((Join-Path $package 'Client'),[StringComparison]::OrdinalIgnoreCase)) { throw 'Only this repack client may be prepared.' }
$statePath = Join-Path $client 'Mods/portable-client.json'
$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
if ((Get-FileHash -LiteralPath (Join-Path $client 'bin64/Game.dll')).Hash -ne $state.gameDllSha256) { throw 'Client binary changed after packaging. No ownership records were changed.' }
if ((Get-FileHash -LiteralPath (Join-Path $client 'bin64/aion.bin')).Hash -ne $state.aionBinSha256) { throw 'Client startup binary changed after packaging.' }
$old = [string]$state.preparedRoot
function Ensure-HDTextures {
    $component=Join-Path $client 'Mods/HDTextures/component.json'
    if(Test-Path -LiteralPath $component){ & (Join-Path $package 'Control/HDTextures.ps1') -ClientPath $client -Action Ensure }
}
if ($old.Equals($client,[StringComparison]::OrdinalIgnoreCase)) { Ensure-HDTextures; 'Portable client paths verified.'; return }
$encodedOld = (ConvertTo-Json -InputObject $old -Compress).Trim('"')
$encodedNew = (ConvertTo-Json -InputObject $client -Compress).Trim('"')
$utf8 = New-Object Text.UTF8Encoding($false)
function Text-Hash([string]$Text) {
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($hasher.ComputeHash($utf8.GetBytes($Text)))).Replace('-','').ToLowerInvariant() }
    finally { $hasher.Dispose() }
}
$documents = @{}
foreach ($relative in $state.metadataFiles) {
    $path = [IO.Path]::GetFullPath((Join-Path $client $relative))
    if (-not $path.StartsWith($client.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid portable metadata path.' }
    $text = [IO.File]::ReadAllText($path)
    $updated = $text.Replace($encodedOld,$encodedNew).Replace($old.Replace('\','/'),$client.Replace('\','/'))
    $null = $updated | ConvertFrom-Json
    $documents[$path] = @{ Before=[IO.File]::ReadAllBytes($path); Text=$updated; Aliases=@((Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant()) }
}
# Texture restore states refer to the hashes of active and backed-up manifests.
# Rebase those references together with the paths, including chained restores.
$settled = $false
for ($round=0; $round -lt 20; $round++) {
    $mapping = @{}
    foreach ($document in $documents.Values) {
        $hash = Text-Hash $document.Text
        if ($document.Aliases -notcontains $hash) { $document.Aliases += $hash }
        foreach ($alias in $document.Aliases) { $mapping[$alias] = $hash }
    }
    $changed = $false
    foreach ($document in $documents.Values) {
        $updated = [regex]::Replace($document.Text,'(?<=")[a-fA-F0-9]{64}(?=")', [Text.RegularExpressions.MatchEvaluator]{
            param($match)
            $key = $match.Value.ToLowerInvariant()
            if ($mapping.ContainsKey($key)) { return $mapping[$key] }
            return $match.Value
        })
        if ($updated -cne $document.Text) { $document.Text = $updated; $changed = $true }
    }
    if (-not $changed) { $settled = $true; break }
}
if (-not $settled) { throw 'Portable restore metadata could not be reconciled. No files were changed.' }
foreach ($document in $documents.Values) { $null = $document.Text | ConvertFrom-Json }
$iniPath = Join-Path $client 'Mods/GraphicsOverhaul/PostFX/ReShade.ini'
$iniBefore = [IO.File]::ReadAllBytes($iniPath)
$ini = [IO.File]::ReadAllText($iniPath).Replace($old,$client).Replace($old.Replace('\','/'),$client.Replace('\','/'))
$stateBefore = [IO.File]::ReadAllBytes($statePath)
$written = New-Object 'System.Collections.Generic.List[string]'
try {
    foreach ($path in $documents.Keys) {
        $written.Add($path)
        [IO.File]::WriteAllText($path,$documents[$path].Text,$utf8)
    }
    [IO.File]::WriteAllText($iniPath,$ini,$utf8)
    $state.preparedRoot = $client
    [IO.File]::WriteAllText($statePath,($state | ConvertTo-Json -Depth 32),$utf8)
} catch {
    foreach ($path in $written) { [IO.File]::WriteAllBytes($path,$documents[$path].Before) }
    [IO.File]::WriteAllBytes($iniPath,$iniBefore)
    [IO.File]::WriteAllBytes($statePath,$stateBefore)
    throw
}
'Portable client ownership, shader paths and restore hashes prepared for this folder.'
Ensure-HDTextures
