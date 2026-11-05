# Shared launch/install/restore ownership checks. No Python or game process needed.
$ErrorActionPreference = 'Stop'
if (-not ('AetherfallOwnership.Parts' -as [type])) {
    if ($PSVersionTable.PSEdition -eq 'Core') {
        Add-Type -Path (Join-Path $PSScriptRoot 'ClientOwnership.cs')
    } else {
        Add-Type -Path (Join-Path $PSScriptRoot 'ClientOwnership.cs') -ReferencedAssemblies @('System.Core','System.Xml.Linq','System.Xml','System.IO.Compression','System.IO.Compression.FileSystem','System.Runtime.Serialization')
    }
}
function Read-OwnershipJson([string]$Path,[switch]$Raw) {
    $bytes = [IO.File]::ReadAllBytes($Path)
    try {
        # Windows PowerShell otherwise treats BOM-less UTF-8 as the ANSI code page.
        $text = [Text.UTF8Encoding]::new($false,$true).GetString($bytes).TrimStart([char]0xfeff)
    } catch [Text.DecoderFallbackException] {
        $ansi = if ($PSVersionTable.PSEdition -eq 'Desktop') { [Text.Encoding]::Default } else {
            [Text.Encoding]::GetEncoding([Globalization.CultureInfo]::CurrentCulture.TextInfo.ANSICodePage)
        }
        $text = $ansi.GetString($bytes)
    }
    if ($Raw) { return $text }
    $text | ConvertFrom-Json
}
function ConvertTo-OwnershipAscii([string]$Text) {
    # Escape UTF-16 code units, including each half of a surrogate pair.
    [regex]::Replace($Text,'[^\x00-\x7f]',[Text.RegularExpressions.MatchEvaluator]{
        param($match)
        '\u{0:x4}' -f [int][char]$match.Value
    })
}
function Write-OwnershipJson([string]$Path,$Value) {
    $text = ConvertTo-OwnershipAscii ($Value | ConvertTo-Json -Depth 30)
    [AetherfallOwnership.Parts]::Atomic($Path,[Text.Encoding]::ASCII.GetBytes($text + "`n"))
}
function Assert-OwnershipClosed {
    $all = @(Get-Process -ErrorAction Stop)
    if ($all | Where-Object { $_.ProcessName -match '^(?i:aion.*)$' }) { throw 'Exit Aion normally before changing its Graphics menu or cursor.' }
}
function Get-GraphicsOwnership([string]$Client) {
    $path = Join-Path $Client 'Mods/GraphicsMenu/ownership.json'
    if (-not (Test-Path -LiteralPath $path)) { throw 'Graphics ownership definition is missing. Repair the Graphics menu support files.' }
    Read-OwnershipJson $path -Raw
}
function Confirm-GraphicsOwnership([string]$Client) {
    [AetherfallOwnership.Parts]::VerifyGraphics($Client,(Get-GraphicsOwnership $Client))
}
function Update-GraphicsOwnershipRecord([string]$Client) {
    # Validate before migration: a corrupt owned part must never become a baseline.
    Confirm-GraphicsOwnership $Client
    $path = Join-Path $Client 'Mods/GraphicsMenu/installed.json'
    $state = if (Test-Path -LiteralPath $path) { Read-OwnershipJson $path } else { [pscustomobject]@{ installedAt=(Get-Date -Format o) } }
    if ($state.ownershipSchema -eq 2 -and $state.clientRoot -eq $Client -and -not $state.restoredAt) { return }
    $spec = (Get-GraphicsOwnership $Client) | ConvertFrom-Json
    $state.PSObject.Properties.Remove('restoredAt')
    $state | Add-Member -Force NoteProperty ownershipSchema 2
    $state | Add-Member -Force NoteProperty clientRoot $Client
    $state | Add-Member -Force NoteProperty files @([pscustomobject]@{path='bin64/AionGraphicsMenu.dll';original=$null;installed=$spec.dllHash})
    $state | Add-Member -Force NoteProperty ownership 'Mods/GraphicsMenu/ownership.json'
    $manifestPath = Join-Path $Client 'Mods/GraphicsMenu/package/manifest.json'
    $manifest = Read-OwnershipJson $manifestPath
    $manifest | Add-Member -Force NoteProperty ownershipSchema 2
    $manifest | Add-Member -Force NoteProperty files $state.files
    $manifest | Add-Member -Force NoteProperty ownership 'Mods/GraphicsMenu/ownership.json'
    Write-OwnershipJson $manifestPath $manifest
    Write-OwnershipJson $path $state
}
function Update-CursorOwnershipRecord([string]$Client,$State) {
    [AetherfallOwnership.Parts]::VerifyCursor($Client)
    if ($State.nativeCursorPatch.ownershipSchema -eq 2 -and $State.clientRoot -eq $Client) { return }
    $State | Add-Member -Force NoteProperty clientRoot $Client
    $patch = $State.nativeCursorPatch
    if (-not $patch) { $patch = [pscustomobject]@{ appliedAt=(Get-Date -Format o); architecture='x64' }; $State | Add-Member -Force NoteProperty nativeCursorPatch $patch }
    $patch | Add-Member -Force NoteProperty ownershipSchema 2
    $patch | Add-Member -Force NoteProperty files @(
        [pscustomobject]@{path='bin64/XRenderD3D9.dll';offset=0x1202d7;originalBytes='7c0c';replacement='9090'},
        [pscustomobject]@{path='bin64/Game.dll';offset=0x551c96;originalBytes='7818';replacement='9090'}
    )
    Write-OwnershipJson (Join-Path $Client 'Mods/Renderer/installed.json') $State
}
