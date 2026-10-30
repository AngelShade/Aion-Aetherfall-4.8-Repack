param(
    [string]$Modules = 'game-server',
    [string]$OutputRoot = '',
    [switch]$Online,
    [switch]$IncludeResources # Compatibility argument; complete resources are now always packaged.
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$workspacePointer = Join-Path $repoRoot '.aion-dev/workspace-root.txt'
$devRoot = if ($env:AION_DEV_ROOT) { $env:AION_DEV_ROOT }
    elseif (Test-Path -LiteralPath $workspacePointer -PathType Leaf) { (Get-Content -LiteralPath $workspacePointer -Raw).Trim() }
    else { Join-Path $env:USERPROFILE 'Aetherfall-build' }
$devRoot = [IO.Path]::GetFullPath($devRoot)
if (!$OutputRoot) { $OutputRoot = Join-Path $devRoot ('staging/target/components-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fffffff')) }
$OutputRoot = [IO.Path]::GetFullPath($OutputRoot)
if (!$OutputRoot.StartsWith($devRoot.TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Build output must be inside the external development workspace.' }
if ($devRoot.StartsWith($repoRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Development root must be external to the source checkout.' }
if (Test-Path -LiteralPath $OutputRoot) { throw 'Use a fresh build directory; reusing old compiled output is prohibited.' }
New-Item -ItemType Directory -Path $OutputRoot | Out-Null
& python (Join-Path $PSScriptRoot 'build-manifest.py') snapshot --build $OutputRoot
if ($LASTEXITCODE -ne 0) { throw 'Could not snapshot authoritative build inputs.' }
# Generate the read-only Codex catalog from the same source closure as this build.
# Keep generated data external; the normal assembly packages only the runtime TSV.
if (($Modules -split ',') -contains 'game-server') {
    & java (Join-Path $PSScriptRoot 'codex/ScriptedSpawnCatalog.java') (Join-Path $repoRoot 'game-server') (Join-Path $OutputRoot 'codex-data')
    if ($LASTEXITCODE -ne 0) { throw 'Could not generate the scripted spawn catalog.' }
}
$mavenArgs = @('-pl',$Modules,'-am','-Dmaven.test.skip=true','-Dassembly.skipAssembly=false',("-Daion.build.root=" + $OutputRoot.Replace('\','/')),'package')
if (!$Online) { $mavenArgs = @('-o') + $mavenArgs }
Push-Location $repoRoot
try {
    # Windows PowerShell turns native stderr into ErrorRecords. Java warnings
    # must reach the log without aborting before Maven's exit code is checked.
    $buildErrorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & mvn @mavenArgs *> (Join-Path $OutputRoot 'maven.log')
        $buildExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $buildErrorPreference }
    Get-Content -LiteralPath (Join-Path $OutputRoot 'maven.log') -Tail 24
    if ($buildExit -ne 0) { throw "Maven failed ($buildExit); see $OutputRoot/maven.log" }
    & python (Join-Path $PSScriptRoot 'build-manifest.py') finalize --build $OutputRoot
    if ($LASTEXITCODE -ne 0) { throw 'Source or resources changed during build; this output must not be delivered.' }
    Write-Output "OK: normal Maven component output: $OutputRoot"
} finally { Pop-Location }
