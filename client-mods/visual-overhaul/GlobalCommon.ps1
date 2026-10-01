$ErrorActionPreference = 'Stop'
function Assert-ClientClosed {
    if (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue) {
        throw 'Exit Aion normally before applying or restoring the graphics package.'
    }
}
function Read-GlobalManifest([string]$PackagePath) {
    $manifest = Get-Content -LiteralPath (Join-Path $PackagePath 'manifest.json') -Raw | ConvertFrom-Json
    if ($manifest.schema -ne 1 -or $manifest.packageId -notmatch '^[a-f0-9]{16}$' -or $manifest.files.Count -lt 1) { throw 'Unsupported graphics manifest.' }
    $seen = @{}
    foreach ($entry in $manifest.files) {
        if ($entry.path -notmatch '^(Levels/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+\.pak|effects/effects_Textures\.pak)$' -or $seen.ContainsKey($entry.path) -or
            $entry.original -notmatch '^[a-f0-9]{64}$' -or $entry.staged -notmatch '^[a-f0-9]{64}$') { throw 'Unsupported archive path or hash.' }
        $seen[$entry.path] = $true
        if ((Get-FileHash -LiteralPath (Join-Path $PackagePath $entry.path)).Hash -ne $entry.staged) { throw "Package checksum failed: $($entry.path)" }
    }
    return $manifest
}
function Copy-GlobalArchive([string]$Source,[string]$Target) {
    $temporary = $Target + '.graphicsoverhaul-new'
    Copy-Item -LiteralPath $Source -Destination $temporary -Force
    Move-Item -LiteralPath $temporary -Destination $Target -Force
}
