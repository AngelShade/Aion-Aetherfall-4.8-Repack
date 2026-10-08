param(
    [string]$Modules = 'game-server',
    [string]$OutputRoot = '',
    [switch]$Online
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$devRoot = if ($env:AION_DEV_ROOT) { $env:AION_DEV_ROOT } else { 'D:/Proiecte/Project Restructure/Aion Development Workspace' }
$devRoot = [IO.Path]::GetFullPath($devRoot)
if (!$OutputRoot) { $OutputRoot = Join-Path $devRoot ('staging/target/components-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
$OutputRoot = [IO.Path]::GetFullPath($OutputRoot)
if (!$OutputRoot.StartsWith($devRoot.TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Build output must be inside the external development workspace.' }
if ($devRoot.StartsWith($repoRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Development root must be external to the source checkout.' }
New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null
$mavenArgs = @('-pl',$Modules,'-am','-Dmaven.test.skip=true','-Dassembly.skipAssembly=true',("-Daion.build.root=" + $OutputRoot.Replace('\','/')),'package')
if (!$Online) { $mavenArgs = @('-o') + $mavenArgs }
Push-Location $repoRoot
try {
    & mvn @mavenArgs *> (Join-Path $OutputRoot 'maven.log')
    $buildExit = $LASTEXITCODE
    Get-Content -LiteralPath (Join-Path $OutputRoot 'maven.log') -Tail 24
    if ($buildExit -ne 0) { throw "Maven failed ($buildExit); see $OutputRoot/maven.log" }
    Write-Output "OK: normal Maven component output: $OutputRoot"
} finally { Pop-Location }
