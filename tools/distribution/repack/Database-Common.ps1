$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSEdition -eq 'Desktop') { $env:PSModulePath = (Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\Modules') + ';' + $env:PSModulePath }
$script:RepackRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$script:DatabaseRoot = Join-Path $RepackRoot 'Server/database'
$script:DatabaseData = Join-Path $DatabaseRoot 'data'
$script:Credentials = Get-Content -LiteralPath (Join-Path $DatabaseRoot 'credentials.json') -Raw | ConvertFrom-Json
$script:DatabaseClient = Join-Path $DatabaseRoot 'runtime/bin/mariadb.exe'
$script:DatabaseAdmin = Join-Path $DatabaseRoot 'runtime/bin/mariadb-admin.exe'
$script:ClientOptions = Join-Path $DatabaseRoot 'local-client.ini'

function Test-DatabasePort {
    $connection = New-Object Net.Sockets.TcpClient
    try {
        $attempt = $connection.BeginConnect('127.0.0.1', [int]$Credentials.port, $null, $null)
        if (-not $attempt.AsyncWaitHandle.WaitOne(500)) { return $false }
        $connection.EndConnect($attempt)
        return $true
    } catch { return $false } finally { $connection.Dispose() }
}

# A leftover read-only file or one from another account would block a plain overwrite; replacing needs only folder rights.
function Write-DatabaseFile([string]$Path, [string]$Text) {
    Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
    [IO.File]::WriteAllText($Path, $Text, (New-Object Text.UTF8Encoding($false)))
}

function Write-ClientOptions {
    $settings = "[client]`r`nprotocol=tcp`r`nhost=127.0.0.1`r`nport=$($Credentials.port)`r`nuser=root`r`npassword=$($Credentials.rootPassword)`r`n"
    Write-DatabaseFile $ClientOptions $settings
}

function Invoke-DatabaseQuery([string]$Query) {
    $result = & $DatabaseClient "--defaults-extra-file=$ClientOptions" --batch --skip-column-names "--execute=$Query" 2>&1
    if ($LASTEXITCODE -ne 0) { throw ('Database query failed: ' + ($result -join ' ')) }
    return ($result -join "`n").Trim()
}

function Assert-OwnDatabase {
    if (-not (Test-DatabasePort)) { throw 'Start this repack database first with 1 - Start Database.cmd.' }
    Write-ClientOptions
    # Hex survives the console code page, which would otherwise garble a non-English install path.
    $hex = Invoke-DatabaseQuery 'SELECT HEX(CONVERT(@@datadir USING utf8mb4))'
    $bytes = [byte[]]@(for ($i = 0; $i -lt $hex.Length; $i += 2) { [Convert]::ToByte($hex.Substring($i, 2), 16) })
    $expected = [IO.Path]::GetFullPath($DatabaseData).TrimEnd('\','/')
    $actual = [IO.Path]::GetFullPath([Text.Encoding]::UTF8.GetString($bytes)).TrimEnd('\','/')
    if (-not $actual.Equals($expected, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'This port belongs to a different database. No commands will be sent to it.'
    }
}
