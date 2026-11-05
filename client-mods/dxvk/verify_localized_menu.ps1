param([Parameter(Mandatory=$true)][string]$ClientPath)
$ErrorActionPreference='Stop'
$client=(Resolve-Path -LiteralPath $ClientPath).Path
. (Join-Path $PSScriptRoot 'ClientOwnership.ps1')
$definition=Join-Path $client 'Mods/GraphicsMenu/ownership.json'
if (-not (Test-Path -LiteralPath $definition)) { $definition=Join-Path $PSScriptRoot 'ownership.json' }
$spec=Read-OwnershipJson $definition
if ($spec.ui.Count -ne 2) { throw 'Expected the base and active English Graphics entries.' }
[AetherfallOwnership.Parts]::VerifyGraphics($client,[IO.File]::ReadAllText($definition))
'PASS: base and English Screen page checkbox and status labels match their owned definitions.'
