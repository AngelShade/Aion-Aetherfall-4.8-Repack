"""Check the bounded cumulative JAR and exercise its effective offline adapters."""
import argparse,importlib,json,subprocess,zipfile
from pathlib import Path
import stage_companion_update as shared

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package',type=Path,required=True)
    parser.add_argument('--classes',type=Path,required=True)
    parser.add_argument('--baseline',type=Path,required=True)
    parser.add_argument('--expected-method-count',type=int,default=6)
    parser.add_argument('--scope-stager',choices=['stage_strategy_continuation','stage_offense_gates_update','stage_quest_arbitration_update','stage_strategy_composition_update','stage_tank_position_update'],default='stage_strategy_continuation')
    args=parser.parse_args();package=args.package.resolve();classes=args.classes.resolve();baseline=args.baseline.resolve()
    manifest=json.loads((package/'manifest.json').read_text())
    payload=package/'libs/playerbot-recruitment-fix.jar'
    jar_entry=next(e for e in manifest['files'] if e['path']=='libs/playerbot-recruitment-fix.jar')
    assert shared.sha(baseline)==jar_entry['original'],'Wrong cumulative baseline'
    assert shared.sha(payload)==jar_entry['installed'],'Package changed since review'
    selected={e['path']:set(e['methods']) for e in manifest['incrementalChangedMethods']}
    additions=set(manifest['newClasses']);preserved=0
    changed_count=sum(map(len,selected.values()))
    assert changed_count==args.expected_method_count,'Unexpected existing-method count'
    scope=importlib.import_module(args.scope_stager)
    assert all(shared.method_name(k) in scope.base.SCOPES[name[:-6]] or name.endswith('PlayerBotSession.class') and shared.method_name(k).startswith('lambda$tick$') for name,allowed in selected.items() for k in allowed),'Unreviewed strategy method'
    with zipfile.ZipFile(baseline) as before,zipfile.ZipFile(payload) as after:
        assert after.testzip() is None
        # A selected class may still live in the base JAR: adding its bounded override is not a new Java schema.
        assert set(after.namelist())==set(before.namelist())|additions|set(selected)
        for name in before.namelist():
            if name not in selected:
                assert before.read(name)==after.read(name),'Unrelated entry changed: '+name;preserved+=1
        for name,allowed in selected.items():
            effective_before=str(baseline)+';'+str(shared.ROOT/'target-deploy/game-server/libs/game-server-4.8-SNAPSHOT.jar')
            old=shared.methods(effective_before,name[:-6]);new=shared.methods(payload,name[:-6]);compiled=shared.methods(classes,name[:-6])
            assert old.keys()==new.keys(),'Existing schema changed: '+name
            assert {k for k in old if old[k]!=new[k]}==allowed,'Unreviewed method changed: '+name
            assert all(new[k]==compiled[k] for k in allowed),'Selected compiled method disconnected: '+name
        for source in (classes/(shared.PREFIX+'services/playerbot')).glob('*.class'):
            if source.stem.split('$')[0] in scope.base.HELPERS:
                name=source.relative_to(classes).as_posix()
                assert after.read(name)==source.read_bytes(),'Helper disconnected: '+name
    assert all(shared.sha(package/e['path'])==e['installed'] for e in manifest['files'])
    assert all(e['original']==e['installed'] for e in manifest['files'] if e is not jar_entry),'Unexpected media/launcher/command change'
    checks=(['PlayerBotTankPositionCheck','PlayerBotTankCheck','PlayerBotEncounterCheck','PlayerBotPositionCheck','PlayerBotCustodyCheck'] if args.scope_stager=='stage_tank_position_update'
        else ['PlayerBotOffenseCheck','PlayerBotOffenseIntegrationCheck'] if args.scope_stager=='stage_offense_gates_update'
        else ['PlayerBotEngineCheck','PlayerBotStrategyCompositionCheck','PlayerBotOffenseIntegrationCheck','PlayerBotConversationCheck','PlayerBotQuestObjectivesCheck','PlayerBotPositionCheck'] if args.scope_stager=='stage_strategy_composition_update'
        else ['PlayerBotConversationCheck','PlayerBotQuestRoutesCheck','PlayerBotQuestObjectivesCheck','PlayerBotPartyBehaviorCheck'] if args.scope_stager=='stage_quest_arbitration_update'
        else ['PlayerBotEncounterCheck','PlayerBotOffenseCheck','PlayerBotTargetStrategiesCheck','PlayerBotQuestRoutesCheck'])
    output=package/'effective-checks';output.mkdir(exist_ok=True)
    libraries=shared.ROOT/'target-deploy/game-server/libs';cp=str(payload)+';'+str(libraries/'*')
    tests=shared.ROOT/'game-server/test/com/aionemu/gameserver/services/playerbot'
    subprocess.run(['javac','--release','25','-encoding','UTF-8','-cp',cp,'-d',str(output),*[str(tests/(c+'.java')) for c in checks]],check=True)
    for check in checks:
        subprocess.run(['java','-Xverify:all','-cp',str(payload)+';'+str(output)+';'+str(libraries/'*'),
            'com.aionemu.gameserver.services.playerbot.'+check],check=True)
    (package/'verification.json').write_text(json.dumps(dict(preservedEntries=preserved,changedMethods=changed_count,newClasses=len(additions),
        effectiveOfflineChecks=checks,nativeRuntime='pending; this check performs no runtime attach or server lifecycle change'),indent=2))
    print(f'OK: {preserved} earlier cumulative entries retained byte-for-byte; {changed_count} bounded methods and {len(additions)} new helper classes verified')
if __name__=='__main__':main()
