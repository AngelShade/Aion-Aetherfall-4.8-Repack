param(
    [Parameter(Mandatory = $true)][string]$ClientPath,
    [Parameter(Mandatory = $true)][string]$Dll,
    [Parameter(Mandatory = $true)][string]$BackupRoot,
    [ValidateSet('corrected', 'upstream', 'off')][string]$Mode = 'corrected'
)
$ErrorActionPreference = 'Stop'
$client = (Resolve-Path -LiteralPath $ClientPath).Path
$target = Join-Path $client 'bin64/version.dll'
$ini = Join-Path $client 'bin64/mods.ini'
# Official 1.5.1 x64 release; anything else must be restored first.
$official = '031d71541d1e2f70b301cf54b7636ddcdbe711582513d265c801b5d9cb3ece97'
if (Get-Process -Name 'aion.bin', 'aion' -ErrorAction SilentlyContinue) { throw 'Close Aion before replacing version.dll.' }
$current = (Get-FileHash -LiteralPath $target).Hash.ToLower()
if ($current -ne $official) { throw "Installed version.dll is not the verified 1.5.1 release ($current)." }
$backup = Join-Path ([IO.Path]::GetFullPath($BackupRoot)) ('camera-fix-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path (Join-Path $backup 'bin64') | Out-Null
Copy-Item -LiteralPath $target -Destination (Join-Path $backup 'bin64/version.dll')
if (Test-Path -LiteralPath $ini) { Copy-Item -LiteralPath $ini -Destination (Join-Path $backup 'bin64/mods.ini') }

Copy-Item -LiteralPath $Dll -Destination $target
$installed = (Get-FileHash -LiteralPath $target).Hash.ToLower()
if ($installed -ne (Get-FileHash -LiteralPath $Dll).Hash.ToLower()) { throw 'Copied version.dll hash mismatch.' }
# Only the [CameraFix] Mode key is written; other sections are kept.
Add-Type -Namespace Native -Name Ini -MemberDefinition '[DllImport("kernel32.dll", CharSet = CharSet.Unicode)] public static extern bool WritePrivateProfileString(string s, string k, string v, string f);'
if (-not [Native.Ini]::WritePrivateProfileString('CameraFix', 'Mode', $Mode, $ini)) { throw 'Could not write mods.ini.' }
[ordered]@{ previous = $current; installed = $installed; mode = $Mode; backup = $backup } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backup 'receipt.json')
"Installed camera fix ($Mode). version.dll $installed. Backup: $backup"
