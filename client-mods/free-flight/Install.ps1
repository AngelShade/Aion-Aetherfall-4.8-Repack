param(
    [Parameter(Mandatory = $true)][string]$ClientPath
)

$ErrorActionPreference = 'Stop'
$clientRoot = (Resolve-Path -LiteralPath $ClientPath).Path
$launcherPath = Join-Path $clientRoot 'Aion Start.bat'
$helperPath = Join-Path $clientRoot 'Enable-FreeFlight.ps1'
if (-not (Test-Path -LiteralPath (Join-Path $clientRoot 'bin64/aion.bin'))) {
    throw 'The expected Aion 4.8 NA client was not found.'
}
$launcher = [System.IO.File]::ReadAllText($launcherPath)
$startPattern = '(?im)^start\s+""\s+"bin64\\aion\.bin"[^\r\n]*'
if ([System.Text.RegularExpressions.Regex]::Matches($launcher, $startPattern).Count -ne 1) {
    throw 'Unexpected launcher format; no files were changed.'
}
$backup = Join-Path $clientRoot ('FreeFlight-backups/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup | Out-Null
Copy-Item -LiteralPath $launcherPath -Destination (Join-Path $backup 'Aion Start.bat')
Copy-Item -LiteralPath (Join-Path $clientRoot 'system.cfg') -Destination (Join-Path $backup 'system.cfg')
if (Test-Path -LiteralPath $helperPath) {
    Copy-Item -LiteralPath $helperPath -Destination (Join-Path $backup 'Enable-FreeFlight.ps1')
}
if ($launcher -notmatch 'Enable-FreeFlight\.ps1') {
    $startup = @'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Enable-FreeFlight.ps1" -ClientPath "%~dp0."
if errorlevel 1 (
    echo Unable to apply the client flight setting.
    pause
    exit /b 1
)
'@
    $startup = ($startup -replace '\r?\n', "`r`n") + "`r`n"
    $match = [System.Text.RegularExpressions.Regex]::Match($launcher, $startPattern)
    $launcher = $launcher.Insert($match.Index, $startup)
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Enable-FreeFlight.ps1') -Destination $helperPath
[System.IO.File]::WriteAllText($launcherPath, $launcher, [System.Text.Encoding]::ASCII)
if ((Get-FileHash -LiteralPath $helperPath).Hash -ne (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'Enable-FreeFlight.ps1')).Hash) {
    throw 'Client startup helper verification failed.'
}
Write-Output "Installed client flight startup fix. Backup: $backup"
if (-not (Get-Process -Name 'aion.bin' -ErrorAction SilentlyContinue)) {
    & $helperPath -ClientPath $clientRoot
} else {
    Write-Output 'Aion is running. The flight setting will be applied when Aion Start.bat launches it next time.'
}
