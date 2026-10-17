param(
    [Parameter(Mandatory=$true)][string]$PackagePath,
    [Parameter(Mandatory=$true)][string]$OutputPath,
    [Parameter(Mandatory=$true)][string]$ToolsPath,
    [string]$SevenZip = 'C:\Program Files\7-Zip\7z.exe'
)
$ErrorActionPreference = 'Stop'
$package = (Resolve-Path -LiteralPath $PackagePath).Path
$tools = (Resolve-Path -LiteralPath $ToolsPath).Path
if (-not (Test-Path -LiteralPath (Join-Path $package 'REPACK.json'))) { throw 'Choose the portable repack folder.' }
if (-not (Test-Path -LiteralPath $SevenZip)) { throw 'Install 7-Zip or supply -SevenZip.' }
& python (Join-Path $PSScriptRoot 'pack.py') '--package' $package '--output' ([IO.Path]::GetFullPath($OutputPath)) '--tools' $tools '--sevenzip' $SevenZip
if ($LASTEXITCODE -ne 0) { throw 'Packing stopped; existing output is retained for diagnosis.' }
Write-Host ('Upload files are ready in '+$OutputPath) -ForegroundColor Green
