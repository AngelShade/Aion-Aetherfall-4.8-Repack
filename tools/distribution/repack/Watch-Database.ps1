# Live view of the database log. Closing this window does not stop the database.
param([Parameter(Mandatory=$true)][int]$DatabaseProcessId)
$Host.UI.RawUI.WindowTitle = 'Aetherfall Database (safe to close; the database keeps running)'
$log = Join-Path $PSScriptRoot '..\Server\database\console-error.log'
$database = Get-Process -Id $DatabaseProcessId -ErrorAction SilentlyContinue
$file = $null
while (($null -ne $database) -and ($null -eq $file)) {
    try { $file = [IO.File]::Open($log, 'Open', 'Read', 'ReadWrite,Delete') } catch { Start-Sleep -Milliseconds 250 }
    if ($database.HasExited) { break }
}
if ($null -ne $file) {
    $reader = New-Object IO.StreamReader($file)
    while ($true) {
        $line = $reader.ReadLine()
        if ($null -ne $line) { $line; continue }
        if ($database.HasExited) { break }
        Start-Sleep -Milliseconds 500
    }
    $reader.Dispose()
}
'The database has stopped. This window closes in 10 seconds.'
Start-Sleep -Seconds 10
