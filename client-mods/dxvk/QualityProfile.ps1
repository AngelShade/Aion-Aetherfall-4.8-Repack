param(
    [string]$ClientPath = (Split-Path -Parent $PSScriptRoot),
    [switch]$Restore
)
$ErrorActionPreference = 'Stop'
if (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue) {
    throw 'Close Aion before applying or restoring its graphics settings.'
}
$clientRoot = (Resolve-Path -LiteralPath $ClientPath).Path
$stateRoot = Join-Path $clientRoot 'DXVK'
$baselineRoot = Join-Path $stateRoot 'profile-original'
$latin1 = [Text.Encoding]::GetEncoding(28591)
$profiles = [ordered]@{
    'system.cfg' = [ordered]@{
        'g_cfg_video_MRT_GENERAL' = '"-2"'
        'g_cfg_video_MRT_SHADOW' = '"4"'
        'r_mrt_ssao' = '"1"'
        'r_mrt_ssaoresolution' = '"1"'
        'r_mrt_sharpen' = '"0.12"'
        # Used with the version-checked native cursor patch on this client.
        'r_3dvision_cursor' = '"1"'
    }
    'SystemOptionGraphics.cfg' = [ordered]@{
        'USE_MG' = '"1"'
        'MG_GENERAL' = '"-2"'
        'MG_SHADOW' = '"4"'
    }
}

function Decode-Line([string]$Line, [bool]$Encoded) {
    if (-not $Encoded -or $Line.StartsWith('--') -or $Line.Length -eq 0) { return $Line }
    return $latin1.GetString([byte[]]@($latin1.GetBytes($Line) | ForEach-Object { $_ -bxor 255 }))
}

function Encode-Line([string]$Line, [bool]$Encoded) {
    if (-not $Encoded) { return $Line }
    return $latin1.GetString([byte[]]@($latin1.GetBytes($Line) | ForEach-Object { $_ -bxor 255 }))
}

function Patch-Config([byte[]]$Bytes, [bool]$Encoded, [Collections.IDictionary]$Values) {
    $raw = $latin1.GetString($Bytes)
    if ($Encoded -and -not $raw.StartsWith('-- [System-Configuration Ver1.0]')) {
        throw 'Unsupported Aion system.cfg format.'
    }
    $parts = [regex]::Split($raw, '(\r\n|\n|\r)')
    $seen = @{}
    for ($i = 0; $i -lt $parts.Length; $i += 2) {
        $line = Decode-Line $parts[$i] $Encoded
        if ($line -match '^\s*([A-Za-z0-9_]+)\s*=\s*(.*?)\s*$' -and $Values.Contains($Matches[1])) {
            $key = $Matches[1]
            if ($null -eq $Values[$key]) {
                $parts[$i] = ''
                if ($i + 1 -lt $parts.Length) { $parts[$i + 1] = '' }
            } else {
                $parts[$i] = Encode-Line ($key + ' = ' + $Values[$key]) $Encoded
            }
            $seen[$key] = $true
        }
    }
    $result = $parts -join ''
    foreach ($key in $Values.Keys) {
        if (-not $seen.ContainsKey($key) -and $null -ne $Values[$key]) {
            if (-not $result.EndsWith("`n") -and -not $result.EndsWith("`r")) { $result += "`r`n" }
            $result += (Encode-Line ($key + ' = ' + $Values[$key]) $Encoded) + "`r`n"
        }
    }
    return ,($latin1.GetBytes($result))
}

# Prepare all replacements before writing either config.
$pending = @()
foreach ($name in $profiles.Keys) {
    $config = Join-Path $clientRoot $name
    $baseline = Join-Path $baselineRoot $name
    $encoded = $name -eq 'system.cfg'
    $values = $profiles[$name]
    $current = [IO.File]::ReadAllBytes($config)
    if ($Restore) {
        if (-not (Test-Path -LiteralPath $baseline)) { continue }
        $values = [ordered]@{}
        foreach ($key in $profiles[$name].Keys) { $values[$key] = $null }
        $original = $latin1.GetString([IO.File]::ReadAllBytes($baseline))
        foreach ($line in [regex]::Split($original, '\r\n|\n|\r')) {
            $decoded = Decode-Line $line $encoded
            if ($decoded -match '^\s*([A-Za-z0-9_]+)\s*=\s*(.*?)\s*$' -and $values.Contains($Matches[1])) {
                $values[$Matches[1]] = $Matches[2]
            }
        }
    }
    $replacement = Patch-Config $current $encoded $values
    $pending += @{Path=$config; Baseline=$baseline; Before=$current; After=$replacement}
}
if (-not $Restore) {
    New-Item -ItemType Directory -Path $baselineRoot -Force | Out-Null
    foreach ($entry in $pending) {
        if (-not (Test-Path -LiteralPath $entry.Baseline)) {
            [IO.File]::WriteAllBytes($entry.Baseline, $entry.Before)
        }
    }
}
try {
    foreach ($entry in $pending) { [IO.File]::WriteAllBytes($entry.Path, $entry.After) }
} catch {
    foreach ($entry in $pending) { [IO.File]::WriteAllBytes($entry.Path, $entry.Before) }
    throw
}
if ($Restore) { 'Previous managed graphics settings restored; other settings retained.' }
else { 'Global High Quality profile applied: shadow level 4, native AO, subtle sharpening and UI cursor rendering.' }
