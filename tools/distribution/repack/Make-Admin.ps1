param([Parameter(Mandatory=$true)][ValidatePattern('^(?:[0-9A-Fa-f]{2}){1,256}$')][string]$AccountNameHex)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Database-Common.ps1')
Assert-OwnDatabase
$where="name=CONVERT(0x$AccountNameHex USING utf8mb4)"
$count=Invoke-DatabaseQuery "SELECT COUNT(*) FROM aetherfall_ls.account_data WHERE $where"
if($count -ne '1'){throw 'Account not found. Log in once to create it, then log out and try again.'}
$accountId=Invoke-DatabaseQuery "SELECT id FROM aetherfall_ls.account_data WHERE $where"
if($accountId -notmatch '^\d+$'){throw 'Unexpected account ID. No access level was changed.'}
# Rank 9 is Admin in the maintained native administration configuration.
$null=Invoke-DatabaseQuery "UPDATE aetherfall_ls.account_data SET access_level=9 WHERE id=$accountId"
$verified=Invoke-DatabaseQuery "SELECT access_level FROM aetherfall_ls.account_data WHERE id=$accountId"
if($verified -ne '9'){throw 'The database did not confirm Admin access.'}
'Admin access is set. Log out fully and log back in to use it.'
