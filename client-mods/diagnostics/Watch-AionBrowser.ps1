param(
    [string]$OutputPath = (Join-Path $PSScriptRoot 'aion-browser-exit.jsonl'),
    [int]$TimeoutMinutes = 30
)
$ErrorActionPreference = 'Stop'
$taskProcesses = @('aion.bin', 'brave.exe', 'AwesomiumProcess.exe')
$taskPrefix = 'AionBrowser-' + [guid]::NewGuid().ToString('N')
$taskDeadline = (Get-Date).AddMinutes($TimeoutMinutes)
$taskSeenAion = $false
$taskExitDeadline = $null
$taskPrevious = @{}
function Write-TaskRecord($record) {
    $record | ConvertTo-Json -Compress | Add-Content -LiteralPath $OutputPath -Encoding utf8
}
try {
    try {
        Register-CimIndicationEvent -Query 'SELECT * FROM Win32_ProcessStartTrace' -SourceIdentifier ($taskPrefix + '-start') | Out-Null
        Register-CimIndicationEvent -Query 'SELECT * FROM Win32_ProcessStopTrace' -SourceIdentifier ($taskPrefix + '-stop') | Out-Null
        $taskUseEvents = $true
    } catch {
        $taskUseEvents = $false
        Write-TaskRecord @{time=(Get-Date).ToString('o');event='polling-mode'}
    }
    foreach ($process in $(if ($taskUseEvents) { Get-CimInstance Win32_Process } else { @() })) {
        if ($taskProcesses -contains $process.Name) {
            Write-TaskRecord @{time=(Get-Date).ToString('o');event='initial';name=$process.Name;pid=$process.ProcessId;parent=$process.ParentProcessId}
            if ($process.Name -eq 'aion.bin') { $taskSeenAion = $true }
        }
    }
    while ((Get-Date) -lt $taskDeadline -and ($null -eq $taskExitDeadline -or (Get-Date) -lt $taskExitDeadline)) {
        if (-not $taskUseEvents) {
            $taskCurrent = @{}
            foreach ($process in Get-Process -Name aion.bin,brave,AwesomiumProcess -ErrorAction SilentlyContinue) {
                $taskCurrent[$process.Id] = $process
                if (-not $taskPrevious.ContainsKey($process.Id)) {
                    Write-TaskRecord @{time=(Get-Date).ToString('o');event='start-or-initial';name=$process.ProcessName;pid=$process.Id}
                }
                if ($process.ProcessName -eq 'aion.bin') { $taskSeenAion = $true }
            }
            foreach ($taskPid in @($taskPrevious.Keys)) {
                if (-not $taskCurrent.ContainsKey($taskPid)) {
                    $taskStopped = $taskPrevious[$taskPid]
                    $taskExitStatus = $null
                    try { $taskExitStatus = $taskStopped.ExitCode } catch {}
                    Write-TaskRecord @{time=(Get-Date).ToString('o');event='stop';name=$taskStopped.ProcessName;pid=$taskPid;exitStatus=$taskExitStatus}
                    if ($taskStopped.ProcessName -eq 'aion.bin') { $taskExitDeadline = (Get-Date).AddSeconds(15) }
                }
            }
            $taskPrevious = $taskCurrent
            Start-Sleep -Milliseconds 250
            continue
        }
        Wait-Event -Timeout 1 | Out-Null
        foreach ($item in Get-Event | Where-Object { $_.SourceIdentifier.StartsWith($taskPrefix) }) {
            $process = $item.SourceEventArgs.NewEvent
            if ($taskProcesses -contains $process.ProcessName) {
                $kind = if ($item.SourceIdentifier.EndsWith('-start')) { 'start' } else { 'stop' }
                Write-TaskRecord @{time=$item.TimeGenerated.ToString('o');event=$kind;name=$process.ProcessName;pid=$process.ProcessID;parent=$process.ParentProcessID;exitStatus=$process.ExitStatus}
                if ($process.ProcessName -eq 'aion.bin') {
                    $taskSeenAion = $true
                    if ($kind -eq 'stop') { $taskExitDeadline = (Get-Date).AddSeconds(15) }
                }
            }
            Remove-Event -EventIdentifier $item.EventIdentifier
        }
    }
    Write-TaskRecord @{time=(Get-Date).ToString('o');event='monitor-finished';sawAion=$taskSeenAion}
} finally {
    Get-EventSubscriber | Where-Object { $_.SourceIdentifier.StartsWith($taskPrefix) } | Unregister-Event
    Get-Event | Where-Object { $_.SourceIdentifier.StartsWith($taskPrefix) } | Remove-Event
}
