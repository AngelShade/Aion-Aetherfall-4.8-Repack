param(
    [string]$ClientPath = $PSScriptRoot
)

$ErrorActionPreference = 'Stop'
$clientRoot = (Resolve-Path -LiteralPath $ClientPath).Path
$configPath = Join-Path $clientRoot 'system.cfg'
if (-not (Test-Path -LiteralPath $configPath)) {
    throw 'Aion system.cfg was not found.'
}
if ((Test-Path -LiteralPath (Join-Path $clientRoot 'bin64/aion.bin')) -and
    (Get-Process -Name 'aion.bin' -ErrorAction SilentlyContinue)) {
    throw 'Close Aion before applying its startup flight setting.'
}

# Aion stores configuration lines as Latin-1 bytes XOR 255. Comments and
# line endings are plain text. Preserve every unrelated byte and setting.
$latin1 = [System.Text.Encoding]::GetEncoding(28591)
$original = [System.IO.File]::ReadAllBytes($configPath)
$raw = $latin1.GetString($original)
if (-not $raw.StartsWith('-- [System-Configuration Ver1.0]')) {
    throw 'Unsupported Aion configuration format; no files were changed.'
}
$parts = [System.Text.RegularExpressions.Regex]::Split($raw, '(\r\n|\n|\r)')
$encodedOption = $latin1.GetString([byte[]]@(
    $latin1.GetBytes('g_freefly = "1"') | ForEach-Object { $_ -bxor 255 }
))
$found = $false
for ($index = 0; $index -lt $parts.Length; $index += 2) {
    $line = $parts[$index]
    if ($line.Length -eq 0 -or $line.StartsWith('--')) { continue }
    $decoded = $latin1.GetString([byte[]]@(
        $latin1.GetBytes($line) | ForEach-Object { $_ -bxor 255 }
    ))
    if ($decoded -match '^\s*g_freefly\s*=') {
        $parts[$index] = $encodedOption
        $found = $true
    }
}
$updated = $parts -join ''
if (-not $found) {
    if (-not $updated.EndsWith("`n") -and -not $updated.EndsWith("`r")) {
        $updated += "`r`n"
    }
    $updated += $encodedOption + "`r`n"
}
$replacement = $latin1.GetBytes($updated)
if ($raw -cne $updated) {
    [System.IO.File]::WriteAllBytes($configPath, $replacement)
}
Write-Output 'Client free flight is enabled for this launch.'
