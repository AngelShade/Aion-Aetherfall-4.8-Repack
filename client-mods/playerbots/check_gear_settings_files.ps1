param([Parameter(Mandatory=$true)][string]$Classpath,[Parameter(Mandatory=$true)][string]$Output)
$ErrorActionPreference='Stop'
throw 'The native metadata DAO supersedes gear-file replacement checks. Run PlayerBotMetadataCheck for current production behavior; historical packages retain the original lock fixture.'
$botOutput=[IO.Path]::GetFullPath($Output)
New-Item -ItemType Directory -Force -Path $botOutput | Out-Null
$botWork=Join-Path $botOutput 'file-lock-fixture'
if(Test-Path -LiteralPath $botWork){throw 'Fixture directory already exists'}
New-Item -ItemType Directory -Path $botWork | Out-Null
$botSettings=Join-Path $botWork 'config/playerbots'
New-Item -ItemType Directory -Path $botSettings -Force | Out-Null
$botFile=Join-Path $botSettings 'gear-character-1999999702.properties'
[IO.File]::WriteAllText($botFile,"committed=unchanged`n")
$botLock=$null;$botProcess=$null
function Wait-File([string]$name) {
 $botDeadline=[DateTime]::UtcNow.AddSeconds(10)
 while(!(Test-Path -LiteralPath (Join-Path $botWork $name))) {
  if($botProcess.HasExited){throw "File check exited unexpectedly: $($botProcess.ExitCode)"}
  if([DateTime]::UtcNow -gt $botDeadline){throw "File check timeout: $name"}
  Start-Sleep -Milliseconds 10
 }
}
try {
 $botLock=[IO.File]::Open($botFile,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
 $botArguments=@('-Xverify:all','-cp',('"'+$Classpath+'"'),'com.aionemu.gameserver.services.playerbot.PlayerBotGearSettingsCheck','locked-file',('"'+$botWork+'"'))
 $botProcess=Start-Process -FilePath (Get-Command java).Source -ArgumentList $botArguments -WorkingDirectory $botWork -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $botOutput 'locked-file-output.txt') -RedirectStandardError (Join-Path $botOutput 'locked-file-errors.txt')
 Wait-File 'ready';[IO.File]::WriteAllText((Join-Path $botWork 'go'),'go');Wait-File 'denied'
 $botLock.Dispose();$botLock=$null
 [IO.File]::WriteAllText((Join-Path $botWork 'released'),'released')
 if(!$botProcess.WaitForExit(10000)){throw 'Isolated file check completion timeout'}
 if($botProcess.ExitCode -ne 0){throw "Isolated file check failed: $($botProcess.ExitCode)"}
 Get-Content -LiteralPath (Join-Path $botOutput 'locked-file-output.txt')
}finally {
 if($botLock){$botLock.Dispose()}
 if($botProcess -and !$botProcess.HasExited){Stop-Process -Id $botProcess.Id}
 # Remove only individually known fixture files; preserve output as evidence.
 foreach($botName in @('ready','go','denied','released')){Remove-Item -LiteralPath (Join-Path $botWork $botName) -ErrorAction SilentlyContinue}
 Remove-Item -LiteralPath $botFile -ErrorAction SilentlyContinue
}
