param(
    [string]$LibraryDirectory = "",
    [string]$JdkDirectory = ""
)

$ErrorActionPreference = "Stop"
$botRepository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "../.."))
$botOutput = Join-Path $botRepository ("target/playerbots-validation/check-" + [guid]::NewGuid().ToString("N"))
$botClasspath = ""
Push-Location -LiteralPath $botRepository
try {
    if (!$LibraryDirectory) { $LibraryDirectory = Join-Path $botRepository "target-deploy/game-server/libs" }
    $LibraryDirectory = [IO.Path]::GetFullPath($LibraryDirectory)
    if (!(Test-Path -LiteralPath $LibraryDirectory)) { throw "Server dependency directory does not exist: $LibraryDirectory" }
    if ($JdkDirectory) {
        $botJavac = Join-Path $JdkDirectory "bin/javac.exe"
        $botJava = Join-Path $JdkDirectory "bin/java.exe"
    } else {
        $botJavac = (Get-Command javac -ErrorAction Stop).Source
        $botJava = Join-Path (Split-Path $botJavac) "java.exe"
    }
    if (!(Test-Path -LiteralPath $botJava)) { throw "Matching Java runtime missing: $botJava" }
    foreach ($botHashLine in Get-Content -LiteralPath "third-party/playerbots/SHA256SUMS") {
        if ($botHashLine -notmatch '^([a-f0-9]{64})\s+(.+)$') { throw "Malformed upstream hash entry" }
        $botExpectedHash = $Matches[1]
        $botReferencePath = Join-Path "third-party/playerbots" $Matches[2]
        if ((Get-FileHash -LiteralPath $botReferencePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $botExpectedHash) {
            throw "Upstream reference changed: $botReferencePath"
        }
    }
    $botScopeReferences = Get-Content -LiteralPath "third-party/playerbots/scope-references/manifest.json" -Raw | ConvertFrom-Json
    if ($botScopeReferences.pin -ne '037c01418b5d01506917a3db9b44fd56ac5f965c') { throw "Scope source revision changed" }
    foreach ($botScopeReference in $botScopeReferences.references) {
        $botScopePath = Join-Path "third-party/playerbots/scope-references" $botScopeReference.path
        if ((Get-FileHash -LiteralPath $botScopePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $botScopeReference.sha256) {
            throw "Scope reference changed: $botScopePath"
        }
    }
    New-Item -ItemType Directory -Path $botOutput -Force | Out-Null
    $botClassDirectory = Join-Path $botOutput "classes"
    New-Item -ItemType Directory -Path $botClassDirectory | Out-Null
    $botSources = @(rg --files game-server/src commons/src -g '*.java')
    if ($LASTEXITCODE -ne 0 -or !$botSources.Count) { throw "Cannot enumerate Java sources" }
    $botSources += "game-server/data/handlers/playercommands/Bot.java"
    $botSources += @(rg --files game-server/test/com/aionemu/gameserver/services/playerbot -g '*.java')
    if ($LASTEXITCODE -ne 0) { throw "Cannot enumerate companion checks" }
    $botArguments = Join-Path $botOutput "sources.args"
    $botSources | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' } | Set-Content -LiteralPath $botArguments -Encoding utf8
    & $botJavac --release 25 -encoding UTF-8 -cp "$LibraryDirectory/*" -d $botClassDirectory "@$botArguments"
    if ($LASTEXITCODE -ne 0) { throw "Full server source and companion command compilation failed" }
    $botClasspath = "$botClassDirectory;$LibraryDirectory/*"
    foreach ($botCheck in @("PlayerBotEngineCheck", "PlayerBotCombatQuestCheck", "PlayerBotEncounterCheck", "PlayerBotPlanningCheck", "PlayerBotSkillsCheck", "PlayerBotRecruitmentCheck", "PlayerBotInventoryCheck", "PlayerBotCompanionCheck", "PlayerBotQuestSyncCheck", "PlayerBotGearPolicyCheck", "PlayerBotGenerationCheck", "PlayerBotPartyBehaviorCheck", "PlayerBotFormationCheck", "PlayerBotFormationLayoutCheck", "PlayerBotPositionCheck", "PlayerBotFollowSpeedCheck", "PlayerBotConversationCheck", "PlayerBotTankCheck", "PlayerBotTemporaryCheck", "PlayerBotPresetsCheck", "PlayerBotRosterRemovalCheck", "PlayerBotTargetValuesCheck", "PlayerBotHealingCheck", "PlayerBotEnemyUtilityCheck", "PlayerBotDefenseCheck", "PlayerBotOffenseCheck", "PlayerBotTargetStrategiesCheck", "PlayerBotQuestRoutesCheck", "PlayerBotRevivalCheck")) {
        & $botJava -Xmx2g -cp $botClasspath "com.aionemu.gameserver.services.playerbot.$botCheck"
        if ($LASTEXITCODE -ne 0) { throw "$botCheck failed" }
    }
    & $botJava -Xmx512m -cp $botClasspath "com.aionemu.gameserver.services.playerbot.PlayerBotCustodyCheck"
    if ($LASTEXITCODE -ne 0) { throw "PlayerBotCustodyCheck failed" }
    & $botJava -Xmx512m -cp $botClasspath "com.aionemu.gameserver.services.playerbot.PlayerBotTankPositionCheck"
    if ($LASTEXITCODE -ne 0) { throw "PlayerBotTankPositionCheck failed" }
    & $botJava -Xmx1g -cp $botClasspath "com.aionemu.gameserver.services.playerbot.PlayerBotSpacingCheck"
    if ($LASTEXITCODE -ne 0) { throw "PlayerBotSpacingCheck failed" }
    & $botJava -Xmx1g -cp $botClasspath "com.aionemu.gameserver.services.playerbot.PlayerBotOffenseIntegrationCheck"
    if ($LASTEXITCODE -ne 0) { throw "PlayerBotOffenseIntegrationCheck failed" }
    & $botJava -Xmx1g -cp $botClasspath "com.aionemu.gameserver.services.playerbot.PlayerBotQuestObjectivesCheck"
    if ($LASTEXITCODE -ne 0) { throw "PlayerBotQuestObjectivesCheck failed" }
    & $botJava -Xmx1g -cp $botClasspath "com.aionemu.gameserver.services.playerbot.PlayerBotStrategyCompositionCheck"
    if ($LASTEXITCODE -ne 0) { throw "PlayerBotStrategyCompositionCheck failed" }
    Push-Location -LiteralPath (Join-Path $botRepository "game-server")
    try {
        & $botJava -Xmx1g -cp $botClasspath "com.aionemu.gameserver.services.playerbot.PlayerBotPanelCheck"
        if ($LASTEXITCODE -ne 0) { throw "PlayerBotPanelCheck failed" }
    } finally { Pop-Location }
    Write-Output "OK: playerbot references, full source compilation, command handler and offline behavioral checks"
    Write-Output "Live database, geodata and client gameplay are separate acceptance checks in docs/PLAYERBOTS.md."
} catch {
    Write-Output "FAIL: $($_.Exception.Message)"
    exit 1
} finally {
    Pop-Location
}
