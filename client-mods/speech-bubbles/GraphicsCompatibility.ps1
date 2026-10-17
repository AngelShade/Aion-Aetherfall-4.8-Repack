function Test-SpeechClientRunning([string]$ClientRoot) {
 $prefix=[IO.Path]::GetFullPath($ClientRoot).TrimEnd('\')+'\'
 foreach($process in @(Get-Process -Name 'aion.bin','aion' -ErrorAction SilentlyContinue)) {
  if(-not $process.Path){throw 'Cannot verify the running Aion client path.'}
  if($process.Path.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)){return $true}
 }
 return $false
}
function Get-SpeechGraphicsPaths($Manifest) {
 if(-not $Manifest.graphicsCompatibility){return @()}
 $name=$Manifest.graphicsCompatibility.backupName
 if($name -notmatch '^service-menu-graphics-\d{8}-\d{6}-\d{6}$'){throw 'Unexpected graphics backup name.'}
 $base='Mods/Backups/Renderer/'+$name
 $paths=@('Mods/Renderer/installed.json','Mods/GraphicsMenu/installed.json','Mods/GraphicsMenu/package/manifest.json',
  'Mods/GraphicsMenu/package/bin64/Game.dll','Mods/GraphicsMenu/package/Data/ui/game/game.pak',
  "$base/bin64/Game.dll","$base/Data/ui/game/game.pak","$base/cursor-base/bin64/Game.dll")
 if($Manifest.graphicsCompatibility.localized){$paths+=@('Mods/GraphicsMenu/package/L10N/enu/Data/data.pak',"$base/L10N/enu/Data/data.pak")}
 return $paths
}
