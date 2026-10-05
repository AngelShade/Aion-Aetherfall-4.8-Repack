param([Parameter(Mandatory=$true)][string]$Classes,[string]$OverrideJar="")
$ErrorActionPreference='Stop'
$careRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$careWork=Join-Path $careRoot ('target/playerbots-settings-files/windows-lock-'+[guid]::NewGuid().ToString('N'))
$careFile=Join-Path $careWork 'config/playerbots/care-character-1999999701.properties'
$careSignals=Join-Path $careWork 'signals'
New-Item -ItemType Directory -Path (Split-Path $careFile),$careSignals -Force | Out-Null
[IO.File]::WriteAllText($careFile,"fixture=preserved`n")
$careCp=[IO.Path]::GetFullPath($Classes)+';'+(Join-Path $careRoot 'target-deploy/game-server/libs/*')
if($OverrideJar){$careCp=[IO.Path]::GetFullPath($OverrideJar)+';'+(Join-Path $careRoot 'target-deploy/game-server/libs/game-server-4.8-SNAPSHOT.jar')+';'+$careCp}
$careInfo=[Diagnostics.ProcessStartInfo]::new((Get-Command java).Source)
$careInfo.WorkingDirectory=$careWork
$careInfo.UseShellExecute=$false
$careInfo.CreateNoWindow=$true
$careInfo.RedirectStandardOutput=$true
$careInfo.RedirectStandardError=$true
foreach($careArg in @('-Xverify:all','-cp',$careCp,'com.aionemu.gameserver.services.playerbot.PlayerBotSettingsFilesCheck','native-lock',$careSignals)){$careInfo.ArgumentList.Add($careArg)}
$careStream=[IO.File]::Open($careFile,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
$careProcess=$null
try {
 $careProcess=[Diagnostics.Process]::Start($careInfo)
 $careOut=$careProcess.StandardOutput.ReadToEndAsync();$careErr=$careProcess.StandardError.ReadToEndAsync()
 $careTimer=[Diagnostics.Stopwatch]::StartNew()
 while(!(Test-Path -LiteralPath (Join-Path $careSignals 'ready'))){if($careTimer.ElapsedMilliseconds -gt 20000 -or $careProcess.HasExited){throw 'Native care fixture did not become ready'};Start-Sleep -Milliseconds 10}
 [IO.File]::WriteAllText((Join-Path $careSignals 'go'),'go')
 while(!(Test-Path -LiteralPath (Join-Path $careSignals 'denied'))){if($careTimer.ElapsedMilliseconds -gt 25000 -or $careProcess.HasExited){throw 'Windows deny-delete fixture failed'};Start-Sleep -Milliseconds 10}
 $careStream.Dispose();$careStream=$null
 [IO.File]::WriteAllText((Join-Path $careSignals 'released'),'released')
 if(!$careProcess.WaitForExit(15000)){throw 'Native unlock recovery timed out'}
 $careOutput=$careOut.GetAwaiter().GetResult();$careErrors=$careErr.GetAwaiter().GetResult()
 [IO.File]::WriteAllText((Join-Path $careWork 'result.txt'),$careOutput+$careErrors)
 Write-Output $careOutput
 if($careProcess.ExitCode -ne 0){Write-Output $careErrors;throw 'Native Windows lock/recovery check failed'}
 if((Get-ChildItem -LiteralPath (Split-Path $careFile) -Filter '*.tmp').Count -ne 0){throw 'Care temporary file was not cleaned up'}
 Write-Output "OK: real Windows deny-delete sharing violation, committed-file preservation, temporary cleanup and unlocked production save; fixture: $careWork"
} finally {
 if($careStream){$careStream.Dispose()}
 if($careProcess -and !$careProcess.HasExited){$careProcess.Kill();$careProcess.WaitForExit()}
 if($careProcess){$careProcess.Dispose()}
}
