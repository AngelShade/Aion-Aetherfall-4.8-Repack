"""Prove the deployed tick change is exactly removal of the unfinished feature hook."""
import argparse,hashlib,json,re,subprocess,zipfile
from pathlib import Path
import stage_companion_update as shared
from verify_runtime_linkage import verify

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--package',type=Path,required=True)
    p.add_argument('--checks',type=Path,required=True);a=p.parse_args();package=a.package.resolve()
    shared.validate_output(package);server=shared.ROOT/'target-deploy/game-server'
    m=json.loads((package/'manifest.json').read_text());payload=package/'libs/playerbot-recruitment-fix.jar'
    baseline=server/'libs/playerbot-recruitment-fix.jar';name=shared.PREFIX+'services/playerbot/PlayerBotSession'
    assert m['feature']=='playerbot-runtime-linkage-repair' and m['offlineOnly']
    for e in m['files']:assert shared.sha(server/e['path'])==e['original'] and shared.sha(package/e['path'])==e['installed'],e['path']
    before=shared.methods(baseline,name);after=shared.methods(payload,name);key='synchronized boolean tick();'
    assert before.keys()==after.keys() and {k for k in before if before[k]!=after[k]}=={key}
    index=next(i for i,l in enumerate(before[key]) if 'PlayerBotAppearance.tick:' in l)
    assert before[key][index-1].strip()=='aload_0'
    # Canonical branch destinations are instruction indices. Removing the load
    # and invoke shifts every later target by two, without changing control flow.
    def target(value):
        number=int(value);assert number not in (index-1,index);return str(number-2 if number>index else number)
    expected=[]
    for i,line in enumerate(before[key]):
        if i in (index-1,index):continue
        if re.match(r'^(if\w*|goto\w*|jsr\w*) ',line):line=re.sub(r' (?P<n>\d+)$',lambda v:' '+target(v['n']),line)
        elif re.match(r'^(default|-?\d+):\d+$',line):label,value=line.split(':');line=label+':'+target(value)
        elif re.match(r'^\d+\|\d+\|\d+\|',line):parts=line.split('|');line='|'.join([target(v) for v in parts[:3]]+parts[3:])
        expected.append(line)
    assert expected==after[key],'Bot tick changed beyond removing the uninstalled hook'
    with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(payload) as new:
        assert set(old.namelist())==set(new.namelist()) and new.testzip() is None
        retained=[n for n in old.namelist() if n!=name+'.class']
        assert all(old.read(n)==new.read(n) for n in retained)
        assert not any('PlayerBotAppearance' in n for n in new.namelist())
    # Demonstrate the new production gate actually detects the reported failure.
    try:verify(baseline,package/'regression-before')
    except RuntimeError as error:
        assert 'PlayerBotSession.tick()Z -> missing class '+shared.PREFIX+'services/playerbot/PlayerBotAppearance' in str(error)
        assert str(error).count('FAIL:')==1
    else:raise AssertionError('Missing-class regression was not reproduced')
    linkage=verify(payload,package/'regression-after')
    cp=';'.join(map(str,[a.checks,payload,server/'libs/*']));logs={}
    for test,count in [('PlayerBotTravelFormationCheck',679),('PlayerBotCastExecutionCheck',72),('PlayerBotEngineCheck',99),('PlayerBotStrategyCompositionCheck',49),('PlayerBotOffenseIntegrationCheck',44),('PlayerBotFollowSpeedCheck',6),('PlayerBotNavigationTrailCheck',17),('PlayerBotGroundNavigationCheck',57),('PlayerBotFormationCheck',314)]:
        result=subprocess.run(['java','-Xverify:all','-cp',cp,'com.aionemu.gameserver.services.playerbot.'+test],cwd=a.checks,capture_output=True,text=True)
        (package/(test+'.txt')).write_text(result.stdout+result.stderr,encoding='utf-8')
        assert result.returncode==0 and 'OK: '+str(count)+' ' in result.stdout,test+': '+result.stderr
        logs[test]=next(l for l in result.stdout.splitlines() if l.startswith('OK: '+str(count)+' '))
    report=dict(payloadSha256=shared.sha(payload),baselineSha256=shared.sha(baseline),changedDefinitions=1,changedMethods=1,
                onlyUninstalledHookRemoved=True,retainedJarEntries=len(retained),missingClassFailureReproduced=True,
                runtimeLinkage=linkage,effectiveRegressions=logs,installed=False,gameAcceptance='pending user testing')
    (package/'verification.json').write_text(json.dumps(report,indent=2));print('OK: exact tick correction, reproduced missing class, full runtime linkage and prior regressions')

if __name__=='__main__':main()
