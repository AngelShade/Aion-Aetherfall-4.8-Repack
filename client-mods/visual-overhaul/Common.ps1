$ErrorActionPreference = 'Stop'

function Assert-AionClosed {
    if (Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue) {
        throw 'Close Aion before applying or restoring the visual pack.'
    }
}

function Get-VisualManifest([string]$PackagePath) {
    $manifest = Get-Content -LiteralPath (Join-Path $PackagePath 'manifest.json') -Raw | ConvertFrom-Json
    $expected = @('Levels/lf1/lf1.pak', 'Levels/lc1/lc1.pak',
        'Levels/lf1/Level.pak', 'Levels/lc1/Level.pak',
        'Levels/common/Mesh_Textures_026.pak', 'effects/effects_Textures.pak')
    if ($manifest.files.Count -ne $expected.Count -or $manifest.packageId -notmatch '^[a-f0-9]{16}$') {
        throw 'Unexpected visual package manifest.'
    }
    $seen = @{}
    foreach ($entry in $manifest.files) {
        if ($expected -cnotcontains $entry.path -or $seen.ContainsKey($entry.path) -or
            $entry.original -notmatch '^[a-f0-9]{64}$' -or $entry.staged -notmatch '^[a-f0-9]{64}$') {
            throw 'Unexpected archive path or checksum in visual package.'
        }
        $seen[$entry.path] = $true
        if ((Get-FileHash -LiteralPath (Join-Path $PackagePath $entry.path)).Hash -ne $entry.staged) {
            throw "Staged archive checksum failed: $($entry.path)"
        }
    }
    return $manifest
}

function Copy-VisualFile([string]$Source, [string]$Destination) {
    $temporary = $Destination + '.visualpass-new'
    Copy-Item -LiteralPath $Source -Destination $temporary -Force
    Move-Item -LiteralPath $temporary -Destination $Destination -Force
}
