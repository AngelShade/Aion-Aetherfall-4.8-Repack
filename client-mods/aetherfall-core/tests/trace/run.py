"""Record the frozen monolith or compare current source with its exact trace."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import tempfile
from collections import Counter
from runtime import FIXTURES, HERE, Runtime, Recorder
from runtime import frozen
from coverage import report


def scenarios(t, smoke=False):
    t.mark('cold-load-disconnected')
    t.ticks(3)
    t.character='TraceCharacter'
    t.mark('world-entry-settle')
    t.event('PLAYER_ENTERING_WORLD')
    t.ticks(45)
    t.slash('/afui')
    assert t.g.AFUI.ready and t.g.AFUI.settled
    if smoke:return
    t.mark('target-details-self-bot-human-absent-party-identity')
    original=t.units['target']
    t.units['target']=(7001,'TraceCharacter',65,31000,42000,5000,8000)
    t.event('USER_CHANGED_TARGET');t.ticks(5)
    t.units['target']=(7010,'NativePlayer',45,2000,3000,None,None)
    t.labels[t.g.AFCore.DIALOG.party,'ctn_member1/title']='NativePlayer'
    t.event('USER_CHANGED_TARGET');t.ticks(5)
    t.units.pop('target');t.event('USER_CHANGED_TARGET');t.ticks(5)
    t.units['target']=original
    t.identities['TraceBot']=('Poor','Excellent')
    t.auras['target']=[]
    t.event('USER_CHANGED_TARGET');t.ticks(5)
    t.identities['TraceBot']=('Excellent','Great')
    t.mark('all-options-all-values')
    schema=json.loads((FIXTURES/'options-schema.json').read_text())
    choices={'font':8,'theme':5,'ttanchor':3,'ttui':3,'ttgrow':4,'abbar':4,'camfov':24,'camdist':47}
    bounds={'windowsalpha':(0,100,10),'chatalpha':(0,100,10),'barsalpha':(0,100,10),'ttx':(-64,64,8),'tty':(-64,64,8),'mapsize':(120,320,10),'menusize':(24,48,2),'menucols':(1,14,1),'menugap':(0,16,2),'guidesize':(16,128,16),'grid':(2,32,2),'abrow':(1,12,1),'absize':(28,56,2)}
    for tab in schema:
        t.click('Tab'+str(tab['tab']))
        for row in tab['rows']:
            base='Row'+str(row['row'])
            kind=row['kind']
            if kind=='toggle':
                for suffix in ('Value','Value','Less','More'): t.click(base+suffix)
            elif kind in ('number','barnumber'):
                key=row.get('k',row.get('base'))
                lo,hi,step=bounds[key]
                cycles=(hi-lo)//step+2
                for _ in range(4 if kind=='barnumber' else 1):
                    if kind=='barnumber': t.click('Row4More')
                    for _ in range(cycles): t.click(base+'Less')
                    for _ in range(cycles): t.click(base+'More')
                    t.click(base+'Value')
            elif kind in ('choice','data','profile'):
                cycles=11 if kind=='data' else 6 if kind=='profile' else choices[row['k']]
                for _ in range(cycles): t.click(base+'Value')
                for _ in range(cycles): t.click(base+'Less')
                t.click(base+'More')
            elif kind=='action':
                # Explicit action cases below retain deterministic editor state.
                if row['action'] not in ('unlock','import','export'):
                    t.click(base+'Value')
                    if row['action']=='resetall': t.click(base+'Value')
    t.mark('camera-abi-unavailable-available')
    t.click('Tab9');t.camera_available=False
    t.click('Row1More');t.click('Row2More')
    t.camera_available=True
    t.click('Row1More');t.click('Row2More')
    t.mark('profiles-presets-user-slots-export-import')
    t.click('Tab11')
    for row in range(2,8): t.click(f'Row{row}Value')
    t.click('Row8Value')
    code=t.widgets['AFUICode']['text']
    t.click('Row9Value')
    t.g.AFUICode.SetText(t.g.AFUICode,'');t.click('Row9Value')
    def share(body):
        total=0
        for char in body.encode(): total=(total*31+char)%65521
        return f'AFUI1!{len(body)}!{body}!{total:04x}'
    malformed=['','garbage','AFUI1!','AFUI1!4!x=1!0000',share('unknown=1'),share('font=999'),share('p11=4,5'),share('font=1;p11=0.1,0.2,target_dialog')]
    for value in malformed:
        t.g.AFUICode.SetText(t.g.AFUICode,value);t.click('Row9Value')
    t.g.AFUICode.SetText(t.g.AFUICode,code);t.click('Row9Value')
    saved_export=t.files.pop('afui-export.cfg',None)
    t.g.AFUICode.SetText(t.g.AFUICode,'');t.click('Row9Value')
    if saved_export is not None:t.files['afui-export.cfg']=saved_export
    t.mark('tooltip-mover-policy-modes')
    t.click('Tab6')
    for _ in range(3):
        t.click('Row2Value')
        for _ in range(3): t.click('Row3Value');t.ticks(1)
    for _ in range(4): t.click('Row4Value');t.ticks(1)
    t.click('Row1Value');t.ticks(1);t.click('Row1Value')
    # Make both tooltip movers visible through their normal option controls.
    for _ in range(3):
        if t.g.AFUI.Get('ttanchor')==1:break
        t.click('Row2Value')
    for _ in range(3):
        if t.g.AFUI.Get('ttui')==2:break
        t.click('Row3Value')
    assert t.g.AFUI.Get('ttanchor')==1 and t.g.AFUI.Get('ttui')==2
    t.click('Row8Value');t.ticks(2)
    if t.g.AFUI.unlocked:t.editor('Cancel')
    t.mark('movers-categories-selection-drag-snap-nudge-origin-xy')
    t.click('Tab10');t.click('Row1Value');t.ticks(5)
    assert t.g.AFUI.unlocked
    # Visit all five filters and selector directions, including hidden windows.
    for _ in range(5):
        t.editor('Next');t.editor('Previous');t.editor('Next');t.editor('Filter')
    t.editor('Restore');t.editor('Next')
    for action in ('Grid','Grid','Snap','Snap','Sticky','Sticky','Less','More','Step'):
        t.editor(action)
    t.mouse[3]=True;t.editor('Step');t.mouse[3]=False
    for _ in range(9): t.editor('Origin')
    t.g.AFUIEditorX.SetText(t.g.AFUIEditorX,'bad')
    t.editor('Apply')
    t.g.AFUIEditorX.SetText(t.g.AFUIEditorX,'123')
    t.g.AFUIEditorY.SetText(t.g.AFUIEditorY,'234')
    t.editor('Apply')
    for action in ('Left','Up','Down','Right'):
        t.editor(action);t.mouse[3]=True;t.editor(action);t.mouse[3]=False
    t.editor('CenterH');t.editor('CenterV');t.editor('Reset')
    t.editor('Hide');t.editor('Restore');t.editor('Next')
    # Select and drag a box away from the editor panel using the cursor entry point.
    def drag_box():
        for _,mv in t.g.AFUI.movers.items():
            if mv.slot and not mv.editorHidden and (mv.x>850 or mv.y>400) and mv.w>2 and mv.h>2:
                x,y=mv.x+1,mv.y+1
                t.pointer(x,y);t.pointer(x,y,True);t.pointer(x+37,y+29,True);t.pointer(x+37,y+29)
                return
        raise AssertionError('No draggable mover outside editor panel')
    drag_box()
    # Snap-to-frame path and Shift-click reset use the same public cursor stream.
    t.editor('Snap');drag_box();t.editor('Snap')
    for _,mv in t.g.AFUI.movers.items():
        if mv.slot and (mv.x>850 or mv.y>400):
            t.pointer(mv.x+1,mv.y+1);t.pointer(mv.x+1,mv.y+1,True,True);t.pointer(mv.x+1,mv.y+1);break
    # Drag the editor's own header, then cancel and repeat with save/lock.
    ex,ey=t.widgets['AFUIEditorTitle']['rect'][:2]
    t.pointer(ex+4,ey+4);t.pointer(ex+4,ey+4,True);t.pointer(ex+30,ey+20,True);t.pointer(ex+30,ey+20)
    t.editor('ResetAll');t.editor('ResetAll');t.editor('Cancel')
    t.slash('/moveui');t.editor('Next');t.editor('Right');t.editor('Save')
    t.slash('/moveui');t.slash('/moveui lock')
    t.slash('/moveui');t.editor('Next');t.editor('Settings')
    t.mark('profile-share-and-save-as-with-mover-positions')
    t.click('Tab11');t.click('Row8Value');t.click('Row9Value')
    t.click('Row5Value');t.click('Row8Value');t.click('Row9Value')
    t.mark('native-window-title-drag-and-reopen')
    # The mapped native map uses its ordinary title-bar controller.
    native=next((d for d in t.dialogs.values() if d['name']=='new_map_dialog'),None)
    if native is not None:
        native['shown']=True;t.ticks(2)
        x,y=native['x']+20,native['y']+10
        t.pointer(x,y,True)
        native['x']+=25;native['y']+=18
        t.pointer(x+25,y+18,True);t.pointer(x+25,y+18)
        native['shown']=False;t.event('GAME_UI_DIALOG_VISIBLE_CHANGED');t.ticks(1)
        native['shown']=True;t.event('GAME_UI_DIALOG_VISIBLE_CHANGED');t.ticks(2)
    t.mark('custom-windows-open-drag-reopen-fit')
    for name in ('PrivateCompanions','PrivateAccount','PrivateCashShop','PrivateWarehouse','PrivateJourney','PrivateWardrobe'):
        t.g[name+'_Open']();t.ticks(22)
        d=next(d for d in t.dialogs.values() if d['name']==name)
        t.pointer(d['x']+10,d['y']+10,True)
        d['x']+=45;d['y']+=30
        t.pointer(d['x']+10,d['y']+10,True);t.pointer(d['x']+10,d['y']+10)
        t.g[name].Hide(t.g[name]);t.ticks(1);t.g[name+'_Open']();t.ticks(1)
        t.g[name].Hide(t.g[name])
    t.mark('focus-loss-regain-resize-loading-zone')
    t.focused=False;t.ticks(3);t.focused=True;t.ticks(25)
    t.sw,t.sh=1920,1080;t.ticks(25)
    t.scale=1.125;t.ticks(25)
    ids=(t.g.AFCore.DIALOG.basic_status,t.g.AFCore.DIALOG.chat)
    for ident in ids:t.dialogs[ident]['shown']=False
    t.ticks(3)
    for ident in ids:t.dialogs[ident]['shown']=True
    t.ticks(25)
    t.zone='Heiron';t.event('PLAYER_ENTERING_WORLD');t.ticks(25)
    t.mark('native-dialog-instance-replacement-and-visibility')
    ident=t.g.AFCore.DIALOG.target
    t.dialogs[ident]['instance']+=50000
    t.event('GAME_UI_DIALOG_VISIBLE_CHANGED');t.event('USER_CHANGED_TARGET');t.ticks(5)
    t.mark('logout-character-switch-relog')
    t.event('ADDON_EXIT');t.character=None;t.ticks(2)
    t.character='TraceOther';t.event('PLAYER_ENTERING_WORLD');t.ticks(25)
    t.character='TraceCharacter';t.event('PLAYER_ENTERING_WORLD');t.ticks(25)
    t.mark('synthetic-legacy-empty-corrupt-existing-saves')
    saves=[('Legacy','AFUI1 10\nset.alpha=50\nset.windowsalpha=80\nset.chatalpha=0\nset.barsalpha=60\nset.ttanchor=1\nstore.User1=alpha:40;ttanchor:1\nDPS.11=0.2,0.3\nbind.DPS.11=target_dialog\nEND\n'),('Corrupt','AFUI1 20\nset.font=999\nset.ttanchor=garbage\nset.camfov=-10\nset.opacitydirect=1\nset.tipcursor=1\nEND\n'),('Empty',''),('Truncated','AFUI1 99\nset.font=4\n')]
    for name,data in saves:
        t.character=None;t.ticks(1)
        identity='TraceServer:'+name
        file='ui-'+hashlib.sha1(identity.encode()).hexdigest()
        t.files[file+'.0.cfg']=data
        t.files[file+'.1.cfg']='AFUI1 9\nset.theme=2\nEND\n' if name=='Legacy' else ''
        t.character=name;t.event('PLAYER_ENTERING_WORLD');t.ticks(25)
        t.slash('/afui');t.click('Tab11');t.click('Row1Value');t.ticks(21)
    t.character='TraceCharacter';t.event('PLAYER_ENTERING_WORLD');t.ticks(25)
    t.click('Tab11');t.click('Row2Value');t.click('Close')
    t.mark('idle-minute-20hz')
    before=Counter(t.r.counts)
    t.ticks(1200)
    t.idle_counts=dict(sorted((Counter(t.r.counts)-before).items()))
    t.mark('final-exit')
    t.event('ADDON_EXIT')
    errors={k:t.g.AFUI[k] for k in ('eventError','pinError','enforceError','loadError','slashError') if t.g.AFUI[k]}
    if t.g.AFHUD.error:errors['HUD']=t.g.AFHUD.error
    assert not errors,errors


def compare(expected, actual):
    with gzip.open(expected,'rt',encoding='utf-8') as left,gzip.open(actual,'rt',encoding='utf-8') as right:
        for line in range(1,10**9):
            a,b=left.readline(),right.readline()
            if a!=b:
                print(f'First difference at record {line}:')
                print('REFERENCE:',a.rstrip()[:3000]);print('CURRENT:  ',b.rstrip()[:3000])
                return False
            if not a: return True


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--record-reference',action='store_true')
    parser.add_argument('--output',type=Path)
    parser.add_argument('--smoke',action='store_true',help='short cold-load/world/options trace for refactor iteration')
    args=parser.parse_args()
    expected=FIXTURES/('golden-smoke.jsonl.gz' if args.smoke else 'golden.jsonl.gz')
    output=args.output or (expected.with_name(expected.name+'.pending') if args.record_reference else Path(tempfile.gettempdir())/'aetherfall-current-trace.jsonl.gz')
    if not args.record_reference and output.resolve()==expected.resolve():
        parser.error('Current source output must not overwrite its reference')
    r=Recorder(output)
    t=Runtime(args.client,r,reference=args.record_reference)
    if not args.record_reference:
        provenance=json.loads(expected.with_suffix('.summary.json').read_text())
        assert provenance['frameworkSha256']==t.framework_hashes,'Client framework differs from the reference environment'
    scenarios(t, args.smoke)
    result=r.close()
    result['frameworkSha256']=t.framework_hashes
    result['coverage']=t.coverage()
    result['idleMinuteCalls']=getattr(t,'idle_counts',{})
    result['sourceSha256']=json.loads((FIXTURES/'source-sha256.json').read_text())
    result['lineCoverage']=report({name:frozen(name) for name in ('AetherfallOptions.lua','AetherfallHUD.lua')},result['coverage']) if args.record_reference else {}
    result['coverageExclusions']=json.loads((FIXTURES/'coverage-exclusions.json').read_text())
    result['harnessSha256']={name:hashlib.sha256((HERE/name).read_bytes()).hexdigest() for name in ('runtime.py','run.py','coverage.py','fixtures/options-schema.json')}
    if args.record_reference and not args.output:
        output.replace(expected);output=expected
    output.with_suffix('.summary.json').write_text(json.dumps(result,indent=2)+'\n')
    print(f"{result['records']} records; sha256 {result['sha256']}; {output.stat().st_size} compressed bytes")
    if not args.record_reference and not compare(expected,output): raise SystemExit(1)
    print('PASS: frozen reference recorded' if args.record_reference else 'PASS: exact ordered trace identical')


if __name__=='__main__': main()
