"""Exercise shipped lazy options, profiles and six-bar storage through Lua 5.1."""
import argparse
import gzip
import hashlib
import json
import re
from itertools import zip_longest
from pathlib import Path
import sys
import tempfile

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / 'trace'))
from runtime import FIXTURES, Recorder, Runtime, SOURCE


def call(t, method, *args):
    return t.g.AF.Call(t.g.AF, 'OptionsWindow', method, *args)


def cell(t, option):
    diagnostics = call(t, 'Diagnostics')
    for index, entry in diagnostics['cells'].items():
        if entry['id'] == option:
            return int(index)
    call(t, 'Select', diagnostics['page'], diagnostics['tab'], option)
    for index, entry in call(t, 'Diagnostics')['cells'].items():
        if entry['id'] == option:
            return int(index)
    raise AssertionError('Option is not rendered: ' + option)


def widget(t, option, suffix):
    return 'AFUIOptionsCell' + str(cell(t, option)) + suffix


def set_text(t, option, suffix, value):
    name = widget(t, option, suffix)
    obj = t.g[name]
    assert obj is not None, 'Missing real widget ' + name
    obj.SetText(obj, value)
    return name


def assert_modules(t):
    t.lua.execute("for _,name in ipairs(AF:Names()) do assert(not AF:IsFailed(name) and not AF:Error(name),name..': '..tostring(AF:Error(name))) end")


def scenarios(t):
    t.mark('options-cold-load-has-the-window-but-no-evaluated-trees')
    assert 'AFUIOptions' in t.widgets, 'the options dialog must be created with the plugin to receive input'
    assert t.g.TRACE_OPTIONS_FACTORY_EVAL == 0, 'option factories ran during plugin load'
    t.character = 'OptionsCharacter'
    t.event('PLAYER_ENTERING_WORLD'); t.ticks(45)
    # The dialog is created with the plugin (a later one gets no input in game); only its option trees are lazy.
    assert 'AFUIOptions' in t.widgets, 'the options dialog must exist from plugin load'
    assert t.g.TRACE_OPTIONS_FACTORY_EVAL == 0, 'login evaluated option trees'
    t.mark('options-first-request-opens-the-loaded-dialog')
    t.slash('/afui'); t.ticks(5)
    assert 'AFUIOptions' in t.widgets and call(t, 'Diagnostics')['open']
    assert t.g.TRACE_OPTIONS_FACTORY_EVAL > 0, 'first use did not evaluate providers'
    call(t, 'Select', 'Profiles')
    assert call(t, 'Diagnostics')['tab']=='Profile', 'Profiles did not open on its Profile tab'
    first_count = sum(1 for name in t.widgets if name.startswith('AFUIOptions'))
    call(t, 'Hide'); t.slash('/afui')
    assert sum(1 for name in t.widgets if name.startswith('AFUIOptions')) == first_count
    t.mark('options-every-shipped-page-and-tab')
    pages = t.g.AF.Config.Pages(t.g.AF.Config)
    assert len(pages) >= 15, 'missing shipped options pages'
    kinds = set()
    for _, page in pages.items():
        for _, tab in page['tabs'].items():
            call(t, 'Select', page['id'], tab['id'])
            d = call(t, 'Diagnostics')
            assert d['page'] == page['id'] and d['tab'] == tab['id']
            for _, entry in d['cells'].items():
                kinds.add(entry['kind'])
            assert_modules(t)
    t.mark('options-renderer-all-kinds-and-widths')
    t.lua.execute('''
        TRACE_OPTION_STATE={toggle=0,range=4,select='beta',input='before',color='#336699',executed=0,hidden=false,disabled=false,evaluated=0}
        local s=TRACE_OPTION_STATE
        AF.Config:Register('OptionsWindow',function()
            s.evaluated=s.evaluated+1
            local controls={
                    {id='probe.description',kind='description',label='Description',order=1,width='full'},
                    {id='probe.header',kind='header',label='Header',order=2},
                    {id='probe.toggle',kind='toggle',label='Toggle',order=3,width='half',get=function() return s.toggle end,set=function(v) s.toggle=v end},
                    {id='probe.range',kind='range',label='Range',order=4,width='half',min=0,max=10,step=2,get=function() return s.range end,set=function(v) s.range=v end},
                    {id='probe.select',kind='select',label='Select',order=5,width='third',choices={'Alpha','Beta','Gamma'},map={'alpha','beta','gamma'},get=function() return s.select end,set=function(v) s.select=v end},
                    {id='probe.input',kind='input',label='Input',order=6,width='third',get=function() return s.input end,set=function(v) s.input=v end},
                    {id='probe.color',kind='color',label='Color',order=7,width='third',get=function() return s.color end,set=function(v) s.color=v end},
                    {id='probe.execute',kind='execute',label='Execute',order=8,execute=function() s.executed=s.executed+1 end},
                    {id='probe.disabled',kind='toggle',label='Disabled',order=9,disabled=function() return true end,get=function() return 0 end,set=function() error('disabled option executed') end},
                    {id='probe.hidden',kind='toggle',label='Hidden',order=10,hidden=function() return true end,get=function() return 0 end},
                }
            local first={id='Controls',name='Controls',groups={{name='All controls',options=controls}}}
            local second={id='Other',name='Other tab',groups={{name='Nested group',options={{id='probe.other',kind='description',label='Second tab'}}}}}
            return {{id='TraceOptions',name='Trace options',category='Trace',desc='Renderer contract',order=999,tabs={first,second}}}
        end)
        assert(s.evaluated==0,'provider evaluated during registration')
    ''')
    call(t, 'Select', 'TraceOptions', 'Controls')
    state = t.g.TRACE_OPTION_STATE
    t.lua.execute('''
        local current=true
        local probe={owner='OptionsWindow',kind='toggle',get=function() return current end,set=function(value) current=value end}
        AF.Config:Change(probe,'More'); assert(current==false,'boolean toggle did not turn off')
        AF.Config:Change(probe,'More'); assert(current==true,'boolean toggle did not turn on')
    ''')
    assert state['evaluated'] == 1
    full_width = t.widgets[widget(t, 'probe.description', 'Desc')]['rect'][2]
    half_width = t.widgets[widget(t, 'probe.toggle', 'Desc')]['rect'][2]
    third_width = t.widgets[widget(t, 'probe.select', 'Label')]['rect'][2]
    assert full_width > half_width > third_width > 30, 'declared widths did not affect geometry'
    d = call(t, 'Diagnostics')
    assert all(entry['id'] != 'probe.hidden' for _, entry in d['cells'].items())
    t.option_click('Cell' + str(cell(t, 'probe.toggle')) + 'Value')
    assert state['toggle'] == 1
    t.option_click('Cell' + str(cell(t, 'probe.disabled')) + 'Value')
    t.option_click('Cell' + str(cell(t, 'probe.range')) + 'More')
    assert state['range'] == 6
    t.option_click('Cell' + str(cell(t, 'probe.range')) + 'Less')
    assert state['range'] == 4
    name = set_text(t, 'probe.range', 'Edit', '8')
    t.option_event('EDITBOX_RETURN', name)
    assert state['range'] == 8
    name = set_text(t, 'probe.range', 'Edit', 'bad')
    t.option_event('EDITBOX_RETURN', name)
    assert state['range'] == 8, 'invalid numeric input changed the setting'
    t.mark('options-slider-drag-uses-cursor-stream')
    track = widget(t, 'probe.range', 'Track')
    x, y, width, height = t.widgets[track]['rect']
    origin = t.widgets['AFUIOptions']['rect']
    x += origin[0]; y += origin[1]
    # A press on the track starts the drag from the polled cursor; no client press event is needed.
    t.pointer(x + width * .2, y + height / 2, True)
    t.pointer(x + width * .8, y + height / 2, True)
    t.pointer(x + width * .8, y + height / 2)
    assert state['range'] == 8, 'slider did not quantize the cursor position to its step'
    t.lua.execute('''
        local received
        AF.Events:On('OptionsWindow','TraceForward',function(_,event,name,value) received={event,name,value} end)
        AFHUD_Dispatch('TraceForward','ControlName',2)
        assert(received[1]=='TraceForward' and received[2]=='ControlName' and received[3]==2,'Compat dropped widget event arguments')
    ''')
    t.mark('options-dropdown-visible-arrow-and-native-click')
    value = widget(t, 'probe.select', 'Value')
    arrow = widget(t, 'probe.select', 'Arrow')
    vx,vy,vw,vh = t.widgets[value]['rect']
    ax,ay,aw,ah = t.widgets[arrow]['rect']
    assert vx <= ax < ax+aw <= vx+vw and vy <= ay < ay+ah <= vy+vh
    t.g.Widget_TriggerScript(value, 'OnClick')
    assert t.widgets['AFUIOptionsDropChoice3']['visible']
    t.g.Widget_TriggerScript('AFUIOptionsDropChoice3', 'OnClick')
    assert state['select'] == 'gamma'
    assert not t.widgets['AFUIOptionsDropChoice3']['visible']
    t.g.Widget_TriggerScript(value, 'OnClick')
    t.g.Widget_TriggerScript('AFUIOptionsDropChoice2', 'OnClick')
    assert state['select'] == 'beta'
    assert not t.widgets[widget(t, 'probe.select', 'Less')]['visible'], 'a dropdown shows no step arrows'
    name = set_text(t, 'probe.input', 'Edit', 'after')
    t.option_event('EDITBOX_RETURN', name)
    assert state['input'] == 'after'
    t.option_click('Cell' + str(cell(t, 'probe.execute')) + 'Value')
    assert state['executed'] == 1
    t.option_click('Cell' + str(cell(t, 'probe.color')) + 'Value')
    name = set_text(t, 'probe.color', 'Hex', '#ff8040')
    t.option_click('Cell' + str(cell(t, 'probe.color')) + 'HexApply')
    assert state['color'] == '#ff8040'
    set_text(t, 'probe.color', 'Hex', 'bad')
    t.option_click('Cell' + str(cell(t, 'probe.color')) + 'HexApply')
    assert state['color'] == '#ff8040'
    t.option_click('Cell' + str(cell(t, 'probe.color')) + 'RedLess')
    assert state['color'] != '#ff8040'
    red_track = widget(t, 'probe.color', 'RedTrack')
    x, y, width, height = t.widgets[red_track]['rect']
    origin = t.widgets['AFUIOptions']['rect']
    x += origin[0]; y += origin[1]
    t.pointer(x + width / 2, y + height / 2, True)
    t.pointer(x + width / 4, y + height / 2, True)
    t.pointer(x + width / 4, y + height / 2)
    assert state['color'].endswith('8040') and state['color'] != '#fe8040', 'RGB drag did not update only its channel'
    t.mark('options-repeated-refresh-sends-nothing')
    call(t, 'Refresh')
    touched, original = [], t.r.write
    def spy(kind, name, args=(), *rest):
        if kind == 'widget' and not str(name).startswith('Get'): touched.append((name, args[1] if len(args) > 1 else ''))
        return original(kind, name, args, *rest)
    t.r.write = spy
    for _ in range(3): call(t, 'Refresh')
    t.r.write = original
    assert not touched, 'refreshing an unchanged page touched widgets (this flickered in game): %r' % touched[:6]
    t.mark('options-enabling-a-bar-shows-it')
    sep = int(t.g.AFCore.DIALOG.shortcut_sep)
    t.g.AF.DB.Set(t.g.AF.DB, 'ActionBars', 'bar5.enabled', 0); t.ticks(2)
    t.dialogs[sep]['shown'] = False
    t.g.AF.DB.Set(t.g.AF.DB, 'ActionBars', 'bar5.enabled', 1); t.ticks(2)
    assert t.dialogs[sep]['shown'], 'switching Bar 5 on did not show it'
    call(t, 'Select', 'TraceOptions', 'Other')
    assert call(t, 'Diagnostics')['tab'] == 'Other'
    t.mark('options-search-deeplink-resize-and-reopen')
    results = t.g.AF.Config.Search(t.g.AF.Config, 'Button size')
    assert len(results) >= 4
    search = t.g.AFUIOptionsSearch
    search.SetText(search, 'Button size')
    t.option_event('GAME_UI_EDITBOX_CHANGED', 'AFUIOptionsSearch')
    t.ticks(1)
    assert call(t, 'Diagnostics')['query'] == 'Button size'
    t.option_click('SearchClear')
    t.slash('/afui Bar 3')
    assert call(t, 'Diagnostics')['page'] == 'Bar3'
    t.option_click('Reposition')
    root = t.widgets['AFUIOptions']['rect']
    x, y = root[0] + root[2] - 26, root[1] + 30
    t.pointer(x, y, True)
    t.pointer(x + 30, y + 20, True); t.pointer(x + 30, y + 20)
    assert t.g.AF.DB.GetWindow(t.g.AF.DB, 'AFUIOptions') is not None, 'options drag was not saved through CustomWindows'
    t.sw, t.sh, t.scale = 1920, 1080, 1.125
    t.ticks(25)
    call(t, 'Hide'); t.slash('/afui')
    rect = t.widgets['AFUIOptions']['rect']
    assert rect[0] >= 0 and rect[1] >= 0 and rect[0] + rect[2] <= t.sw + 1 and rect[1] + rect[3] <= t.sh + 1
    t.mark('options-compact-scale-two-bounds')
    t.sw, t.sh, t.scale = 1280, 720, 2
    t.ticks(25); call(t, 'Hide'); t.slash('/afui')
    call(t, 'Select', 'TraceOptions', 'Controls', 'probe.color')
    compact_root = t.widgets['AFUIOptions']['rect']
    assert compact_root[2] <= t.sw and compact_root[3] <= t.sh
    for _, control in call(t, 'Diagnostics')['cells'].items():
        name = widget(t, control['id'], 'Label')
        for part in ('Label', 'Edit', 'Apply', 'Hex', 'HexApply', 'RedMore', 'BlueMore'):
            obj = t.widgets.get(name[:-5] + part)
            if obj and obj['visible']:
                cx, cy, cw, ch = obj['rect']
                assert cx >= 0 and cy >= 0 and cx + cw <= compact_root[2] + 1 and cy + ch <= compact_root[3] + 1, (control['id'], part, obj['rect'])
    t.sw, t.sh, t.scale = 1280, 720, 1
    t.ticks(25); call(t, 'Hide'); t.slash('/afui')
    t.mark('data-text-third-slot-separator-is-functional')
    t.lua.execute("AF.DB:Set('HUD','dlsep3',' |'); AF:Call('HUD','Apply')")
    assert t.widgets['AFHTopLeft']['text'].endswith(' |'), 'last-slot separator had no visible effect'
    t.lua.execute("AF.DB:Set('HUD','dlsep3',''); AF:Call('HUD','Apply')")
    t.mark('profiles-actions-through-shipped-controls')
    call(t, 'Select', 'Profiles', 'Profile', 'newProfileName')
    set_text(t, 'newProfileName', 'Edit', 'UI profile')
    t.option_click('Cell' + str(cell(t, 'newProfileName')) + 'Apply')  # Apply creates and selects the profile
    assert t.g.AF.DB.ProfileName(t.g.AF.DB) == 'UI profile'
    call(t, 'Select', 'Profiles', 'Profile', 'resetProfile')
    t.option_click('Cell' + str(cell(t, 'resetProfile')) + 'Value')
    assert call(t, 'Diagnostics')['note'], 'destructive confirmation has no hint'
    t.option_click('Cell' + str(cell(t, 'resetProfile')) + 'Value')
    assert_modules(t)
    t.mark('target-resource-formats-use-native-entry-point')
    import xml.etree.ElementTree as ET
    import target_details
    t.g.LoadXMLWidget(t.table(target_details.overlay()))
    t.lua.execute("AF.DB:Set('TargetDetails','healthFormat','[percent]'); AF:Call('TargetDetails','Update')")
    assert t.labels[t.g.AFCore.DIALOG.target, 'container7/af_resource_hp'] == '73.8%'
    t.lua.execute("AF.DB:Set('TargetDetails','healthFormat','[current]/[max]')")
    profile_scenarios(t)
    legacy_scenarios(t)
    assert_modules(t)
    t.mark('options-final-save-exit')
    t.event('ADDON_EXIT')


def profile_scenarios(t):
    t.mark('profiles-crud-module-copy-and-scopes')
    t.lua.execute('''
        local DB=AF.DB
        assert(DB:ProfileCreate('Trace source'))
        DB:Set('ActionBars','bar1.rows',5); DB:Set('HUD','font',3)
        DB:SetPosition('Trace source.11',{.2,.3}); DB:SetBinding('Trace source.11','target_dialog')
        assert(DB:ProfileCreate('Trace destination'))
        DB:Set('ActionBars','bar1.rows',9); DB:Set('HUD','font',4)
        DB:SetPosition('Trace destination.11',{.8,.7})
        assert(DB:ProfileCopyModule('Trace source','ActionBars','Trace destination'))
        assert(DB:Get('ActionBars','bar1.rows')==5 and DB:Get('HUD','font')==4,'module copy leaked other module settings')
        assert(DB:GetPosition('Trace destination.11')[1]==.8,'module copy copied unrelated mover positions')
        assert(DB:ProfileCopy('Trace source','Trace destination'))
        assert(DB:Get('HUD','font')==3 and DB:GetPosition('Trace destination.11')[1]==.2,'full profile copy lost settings or positions')
        assert(DB:ProfileRename('Trace destination','Trace renamed'))
        assert(DB:ProfileName()=='Trace renamed')
        assert(not DB:ProfileDelete('Trace renamed'),'active profile deletion was allowed')
        assert(DB:ProfileRename('DPS','Trace built-in')); assert(DB:ProfileRename('Trace built-in','DPS'))
        assert(DB:ProfileDelete('User3')); assert(not DB:ProfileExists('User3'))
        assert(DB:ProfileCreate('User3')); assert(DB:ProfileSelect('Trace renamed'))
        assert(not DB:ProfileCreate('') and not DB:ProfileCreate('../bad') and not DB:ProfileCreate('Trace renamed'))
        assert(DB:SetModuleScope('HUD','character')); DB:Set('HUD','font',5)
        assert(DB:Scope('HUD','font')=='character')
        assert(not DB:ProfileCopyModule('Trace source','HUD'),'character-scoped module copy reported a fake success')
        for _,owner in ipairs(DB:ProfileModules()) do assert(owner~='HUD','module copy offered character-scoped settings') end
        assert(DB:ProfileSelect('DPS')); assert(DB:Get('HUD','font')==5,'profile switch changed character scope')
        assert(DB:SetModuleScope('HUD','profile'))
        assert(DB:ProfileDelete('Trace renamed')); assert(not DB:ProfileExists('Trace renamed'))
        assert(DB:ProfileSelect('Trace source'))
        assert(DB:ProfileSaveAccount('Shared trace')); assert(DB:Save())
        assert(DB:SetModuleScope('HUD','account')); DB:Set('HUD','font',6)
        assert(DB:Save()); assert(DB:Error()==nil and DB:SaveAt()==nil,'successful save retained an error/retry')
    ''')
    t.mark('profiles-six-independent-bars-and-sharing')
    t.lua.execute('''
        local DB=AF.DB
        for i=1,6 do
            DB:Set('ActionBars','bar'..i..'.enabled',i%2)
            DB:Set('ActionBars','bar'..i..'.opacity',i*11)
        end
        local code=DB:ProfileExport()
        assert(code:sub(1,5)=='AFUI2')
        DB:Set('ActionBars','bar6.enabled',0)
        assert(DB:ProfileImport(code))
        for i=1,6 do
            assert(DB:Get('ActionBars','bar'..i..'.enabled')==i%2)
            assert(DB:Get('ActionBars','bar'..i..'.opacity')==i*11)
        end
        local before=DB:ProfileSnapshot()
        for _,bad in ipairs({'','garbage','AFUI1!','AFUI1!4!x=1!0000',code..'x',string.rep('x',32769)}) do
            assert(not DB:ProfileImport(bad),'accepted invalid profile code')
            assert(DB:ProfileSnapshot()==before,'invalid import changed settings')
        end
        assert(DB:Save()); assert(DB:Error()==nil and DB:SaveAt()==nil,'successful save retained an error/retry')
    ''')
    t.mark('profiles-account-cross-character-copy')
    t.character = 'OptionsOther'; t.event('PLAYER_ENTERING_WORLD'); t.ticks(25)
    t.lua.execute('''
        local DB=AF.DB
        assert(DB:SetModuleScope('HUD','account'))
        assert(DB:Get('HUD','font')==6,'account scope did not survive character reload')
        local name=DB:ProfileName()
        assert(DB:ProfileUseAccount('Shared trace'))
        assert(DB:ProfileName()==name and DB:Get('ActionBars','bar1.rows')==5)
        local characters=DB:ProfileCharacters(); assert(#characters>=1)
        assert(not DB:ProfileCopyCharacter('../bad','Trace source'))
        local copied=false
        for _,entry in ipairs(characters) do
            if entry.label=='TraceServer:OptionsCharacter' then
                assert(DB:ProfileCopyCharacter(entry.file,'Trace source'));copied=true;break
            end
        end
        assert(copied,'source character missing from saved index')
        assert(DB:Get('ActionBars','bar1.rows')==5)
    ''')
    t.mark('profiles-cap-refuses-without-changing-current')
    t.lua.execute('''
        local DB=AF.DB
        local rejected=false
        for index=1,DB:ProfileLimit()+1 do
            local before,name=DB:ProfileSnapshot(),DB:ProfileName()
            local ok,why=DB:ProfileCreate('Capacity '..index)
            if not ok then
                assert(why=='profile storage full',tostring(why))
                assert(DB:ProfileSnapshot()==before and DB:ProfileName()==name,'full storage changed the active profile')
                rejected=true;break
            end
        end
        assert(rejected,'bounded profile storage never refused a write')
        assert(DB:Save())
    ''')


def legacy_scenarios(t):
    t.mark('profiles-afui1-disk-migrates-four-rows-six-visibility')
    identity = 'TraceServer:LegacyOptions'
    name = 'ui-' + hashlib.sha1(identity.encode()).hexdigest()
    t.files[name + '.0.cfg'] = ('AFUI1 10\nset.abbar=4\nset.abrow1=3\nset.abrow2=4\n'
        'set.abrow3=5\nset.abrow4=6\nset.absize1=30\nset.absize2=34\nset.absize3=38\n'
        'set.absize4=42\nset.altbar=0\nset.extrabar=1\nset.sepbar=0\nset.ttx=24\n'
        'set.tty=32\nset.opacitydirect=1\nset.tipcursor=1\nstore.User1=abrow1:7;sepbar:1\nEND\n')
    t.character = 'LegacyOptions'; t.event('PLAYER_ENTERING_WORLD'); t.ticks(25)
    t.lua.execute('''
        local DB=AF.DB
        for i=1,4 do
            assert(DB:Get('ActionBars','bar'..i..'.rows')==i+2,'legacy rows lost')
            assert(DB:Get('ActionBars','bar'..i..'.size')==26+i*4,'legacy button size lost')
        end
        assert(DB:Get('ActionBars','bar3.enabled')==0 and DB:Get('ActionBars','bar4.enabled')==1)
        assert(DB:Get('ActionBars','bar5.enabled')==0 and DB:Get('ActionBars','bar6.enabled')==0,'combined legacy sepbar was not expanded')
        assert(DB:ProfileSelect('User1'))
        assert(DB:Get('ActionBars','bar1.rows')==7)
        assert(DB:Get('ActionBars','bar5.enabled')==1 and DB:Get('ActionBars','bar6.enabled')==1,'stored legacy sepbar migration failed')
    ''')
    t.mark('profiles-afui1-master-opacity-migration')
    t.lua.execute(r'''
        local DB=AF.DB
        local disk='AFUI1 12\nset.alpha=50\nset.windowsalpha=80\nset.chatalpha=0\nset.barsalpha=60\nstore.User1=alpha:40;ttanchor:1\nEND\n'
        local seq,state=DB:StorageDecode(disk,'character')
        assert(seq==12 and state.values.windowsalpha==40 and state.values.chatalpha==0 and state.values.barsalpha==30,'legacy master opacity was not preserved')
        local stored=DB:SnapshotParse(state.stored.User1)
        assert(stored.windowsalpha==40 and stored.chatalpha==40 and stored.barsalpha==40,'stored legacy master opacity was not preserved')
    ''')
    t.mark('profiles-afui1-share-strict-valid-and-invalid')
    def share(body):
        total = 0
        for char in body.encode():
            total = (total * 31 + char) % 65521
        return f'AFUI1!{len(body)}!{body}!{total:04x}'
    t.g.TRACE_LEGACY_CODE = share('abrow1=9;absize1=44;sepbar=0')
    t.g.TRACE_INVALID_CODES = t.lua.table_from([
        share('unknown=1'), share('font=999'), share('font=1;font=2'),
        share('p11=4,5'), share('font=1;p11=0.1,0.2,invalid/binding'),
    ])
    t.lua.execute('''
        local DB=AF.DB
        assert(DB:ProfileImport(TRACE_LEGACY_CODE))
        assert(DB:Get('ActionBars','bar1.rows')==9 and DB:Get('ActionBars','bar1.size')==44)
        assert(DB:Get('ActionBars','bar5.enabled')==0 and DB:Get('ActionBars','bar6.enabled')==0)
        local before=DB:ProfileSnapshot()
        for _,code in ipairs(TRACE_INVALID_CODES) do
            assert(not DB:ProfileImport(code),'accepted malformed AFUI1 code')
            assert(DB:ProfileSnapshot()==before,'malformed AFUI1 import changed the profile')
        end
        assert(DB:Save())
    ''')
    t.mark('profiles-corrupt-newer-pair-falls-back-and-save-retries')
    name = 'ui-' + hashlib.sha1(b'TraceServer:PairFallback').hexdigest()
    t.files[name + '.0.cfg'] = 'AFUI1 8\nset.font=3\nset.sepbar=0\nEND\n'
    t.files[name + '.1.cfg'] = 'AFUI2 9 character\ns|profile|font|4\nCHECK 0000\nEND\n'
    t.character = 'PairFallback'; t.event('PLAYER_ENTERING_WORLD'); t.ticks(25)
    t.lua.execute('''
        local DB=AF.DB
        assert(DB:Get('HUD','font')==3,'corrupt newer file displaced the valid older slot')
        assert(DB:Get('ActionBars','bar5.enabled')==0 and DB:Get('ActionBars','bar6.enabled')==0)
        local save=AFCore.SaveFile
        AFCore.SaveFile=function() return false end
        DB:Dirty(); assert(not DB:Save() and DB:Error() and DB:SaveAt(),'failed save lost its retry/error')
        AFCore.SaveFile=save
        assert(DB:Save() and DB:Error()==nil and DB:SaveAt()==nil,'save retry failed')
    ''')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--record-reference', action='store_true', help='explicitly record the new options contract after reviewing all assertions')
    args = parser.parse_args()
    expected = FIXTURES / 'options-golden.jsonl.gz'
    output = args.output or Path(tempfile.mkdtemp(prefix='aetherfall-options-')) / 'options.jsonl.gz'
    if output.resolve() == expected.resolve():
        parser.error('write to a fresh external output; reviewed reference is promoted separately')
    recorder = Recorder(output)
    runtime = Runtime(args.client, recorder, current_resources=True)
    try:
        scenarios(runtime)
    finally:
        result = recorder.close()
    result['frameworkSha256'] = runtime.framework_hashes
    result['sourceSha256'] = {name: hashlib.sha256((SOURCE / name).read_bytes()).hexdigest()
                              for name in json.loads((SOURCE / 'load_order.json').read_text())
                              if (SOURCE / name).is_file()}
    output.with_suffix('.summary.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    if args.record_reference:
        expected.write_bytes(output.read_bytes())
        expected.with_suffix('.summary.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        print('PASS: new options reference recorded after assertions')
    else:
        if not expected.exists():
            raise SystemExit('Missing reviewed options golden; run explicit --record-reference after review')
        with gzip.open(expected, 'rb') as left, gzip.open(output, 'rb') as right:
            for index, (before, after) in enumerate(zip_longest(left, right), 1):
                if unversioned(before) != unversioned(after):
                    print('First options difference at record', index)
                    print('REFERENCE:', before)
                    print('CURRENT:', after)
                    raise SystemExit(1)
        print(f"PASS: options/profile contracts and {result['records']} ordered records")
    print('Options trace:', output)


# The release number shows in the window header and on the Information page; a version bump
# alone must not invalidate the reviewed reference. Only those three texts are normalised.
_VERSION_LABEL = re.compile(rb'("AFUIOptionsVersion",)"\d+\.\d+\.\d+"')
_VERSION_TEXT = re.compile(rb'(Aetherfall Interface (?:version )?)\d+\.\d+\.\d+')


def unversioned(line):
    if line is None:
        return None
    return _VERSION_TEXT.sub(rb'\1<version>', _VERSION_LABEL.sub(rb'\1"<version>"', line))


if __name__ == '__main__':
    main()
