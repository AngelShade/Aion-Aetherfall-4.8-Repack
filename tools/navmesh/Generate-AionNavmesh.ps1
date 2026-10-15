[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$GeoDirectory,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$BuildRoot,
    [ValidateRange(1,8)][int]$Workers = 1,
    [ValidateRange(768,16384)][int]$HeapMB = 2048,
    [switch]$Plan
)

$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$geo = (Resolve-Path -LiteralPath $GeoDirectory).Path
$output = [IO.Path]::GetFullPath($OutputDirectory)
$build = [IO.Path]::GetFullPath($BuildRoot)
# Check before Maven writes anything; Java additionally resolves output links.
function Assert-ExternalDirectory([string]$Path) {
    $candidate = [IO.DirectoryInfo]::new($Path)
    while ($null -ne $candidate) {
        if (Test-Path -LiteralPath (Join-Path $candidate.FullName '.git')) {
            throw "Generated output must be outside a Git checkout: $Path"
        }
        if ($candidate.Exists -and ($candidate.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "Use a direct external directory, not a linked directory: $Path"
        }
        $candidate = $candidate.Parent
    }
    foreach ($protected in @($repo, $geo)) {
        $prefix = $protected.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
        if ($Path.Equals($protected, [StringComparison]::OrdinalIgnoreCase) -or $Path.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Generated output overlaps source/input: $Path"
        }
    }
}
Assert-ExternalDirectory $build
Assert-ExternalDirectory $output
if ($build.Equals($output, [StringComparison]::OrdinalIgnoreCase)) { throw 'Use distinct build and mesh output directories.' }

& mvn -f (Join-Path $PSScriptRoot 'pom.xml') "-Daion.build.root=$build" verify
if ($LASTEXITCODE -ne 0) { throw 'Navigation tool build failed.' }
$jar = Join-Path $build 'navmesh/aion-navmesh-1.0-SNAPSHOT.jar'
$generatorArgs = @("-Xmx${HeapMB}m", '-jar', $jar, '--geo', $geo, '--output', $output, '--workers', "$Workers")
if ($Plan) { $generatorArgs += '--plan' }
& java @generatorArgs
if ($LASTEXITCODE -ne 0) { throw 'Generation failed; completed tiles remain available for a verified resume.' }
if ($Plan) { return }

$checks = Join-Path $build 'navmesh/batch-checks'
New-Item -ItemType Directory -Path $checks -Force | Out-Null
& javac --release 17 -cp $jar -d $checks (Join-Path $PSScriptRoot 'verification/VerifyBatch.java') (Join-Path $PSScriptRoot 'verification/VerifyMap.java')
if ($LASTEXITCODE -ne 0) { throw 'Batch verifier compilation failed.' }
$cp = $checks + [IO.Path]::PathSeparator + $jar
& java "-Xmx${HeapMB}m" -cp $cp com.aionemu.tools.navmesh.VerifyBatch $geo $output (Join-Path $output 'verification.properties')
if ($LASTEXITCODE -ne 0) { throw 'Offline batch verification failed; do not install these meshes.' }
Write-Output 'All discovered maps generated and verified offline. Runtime integration/gameplay acceptance is separate; no installation was changed.'
