"""Exercise unit frames through shipped widget scripts and mutable native data.

The client framework is read-only. These scenarios have no golden promotion
switch: assertions inspect the new surface while the original trace comparison
continues to own every original native-state boundary.
"""
import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
if os.environ.get('AION_LUA_TEST_DEPS'):
    sys.path.insert(0, os.environ['AION_LUA_TEST_DEPS'])
sys.path.insert(0, str(HERE / 'trace'))
from runtime import Recorder, Runtime, SOURCE

UNITS = ('player', 'target', 'targettarget', *(f'party{i}' for i in range(1, 7)))
PREFIXES = dict(zip(UNITS, ('AFHFPlayer', 'AFHFTarget', 'AFHFTargetTarget',
                            *(f'AFHFParty{i}' for i in range(1, 7)))))

class FocusedRecorder(Recorder):
    """Keep ordered inputs/mutations; count repetitive read-only boundary calls."""

    def __init__(self, path):
        super().__init__(path)
        self.reads = 0

    def write(self, kind, name, args=(), result=()):
        reads = {'dialog', 'rect', 'widget', 'text', 'shown', 'unit', 'unitclass', 'playerid',
                 'gauge', 'scale', 'screen', 'slots', 'identity', 'readfile', 'pininfo',
                 'Dialog', 'Rect', 'Widget', 'Text', 'Shown', 'Unit', 'UnitClass', 'PlayerID',
                 'Gauge', 'Scale', 'Screen', 'ReadFile', 'PinInfo'}
        if ((kind in ('bridge', 'AFCore') and name in reads)
                or (kind in ('widget', 'stock') and name.startswith(('Get', 'Unit')))):
            self.counts[kind + ':' + name] += 1
            self.reads += 1
            return
        super().write(kind, name, args, result)

    def close(self):
        return {**super().close(), 'readOnlyCallsCountedNotExpanded': self.reads}


def sample(ident, name, health=750, maximum=1000, power=180, power_max=300, level=65):
    return (ident, name, level, health, maximum, power, power_max)


class UnitFrameRuntime(Runtime):
    """Opt-in fixtures; the immutable monolith's Runtime defaults stay intact."""

    def __init__(self, client, recorder, **kwargs):
        self.frame_units = {}
        self.native_types = {}
        self.drop_target_hides = False
        self.dp = (1200, 4000)
        self.portrait_pin, self.portrait_shown, self.portrait_view = None, False, True
        self.player_body = True  # Core 1.3.23 builds the player's own body (source -1) in its canvas view.
        # Bot portraits: names whose look the server sent, and per 3D view its pin, shown bit and body.
        self.looks, self.view_pins, self.view_shown, self.view_models = set(), {}, {}, {}
        self.target_models = {}  # Native models available by selected object ID, independently of bot identity.
        self.classes = {}  # Native class (id, name) by object ID (player/target units) or by party member name.
        self.party_attached, self.party_layouts = False, []
        super().__init__(client, recorder, current_resources=True, **kwargs)
        assert self.g.AFCore.Portrait and self.g.AFCore.UnitClass and self.g.AFCore.PartyFrames
        self.lua.execute('debug.sethook()')  # This focused behavior trace does not consume line coverage.
        # Use the stock function's real public shape. Lua wrappers retain Lua
        # type(function), as AF.Native.Call checks it before invoking the API.
        wrap = self.lua.eval('function(fn) return function(...) return fn(...) end end')
        for name, field in (('UnitLevel', 2), ('UnitHP', 3), ('UnitHPMax', 4),
                            ('UnitMP', 5), ('UnitMPMax', 6)):
            self.g[name] = wrap(lambda unit='player', *rest, name=name, field=field:
                                self.stock(name, (unit, *rest), self.value(unit, field)))
        self.g.UnitName = wrap(lambda unit='player', *rest:
                               self.stock('UnitName', (unit, *rest),
                                          self.character if unit == 'player' else self.value(unit, 1)))
        self.g.GetNumPartyMembers = wrap(lambda *args:
            self.stock('GetNumPartyMembers', args,
                       sum(unit in self.frame_units for unit in UNITS if unit.startswith('party'))))
        self.g.UnitClass = wrap(lambda unit='player', *rest:
            self.stock('UnitClass', (unit, *rest), 'Gladiator' if unit in self.frame_units else None))
        self.g.UnitIsDead = wrap(lambda unit='player', *rest:
            self.stock('UnitIsDead', (unit, *rest),
                       bool(self.frame_units.get(unit) and self.frame_units[unit][3] <= 0)))

    def value(self, unit, field):
        row = self.frame_units.get(str(unit))
        return row[field] if row else None

    def widget(self, api, *args):
        if (self.drop_target_hides and api == 'SetWidgetVisible' and args[2] == 0
                and str(args[1]).startswith('AFHFTarget')):
            self.r.write('widget-dropped', api, args)
            return None
        result = super().widget(api, *args)
        if len(args) > 1 and api != 'AddNewWidget':
            state, values = self.widgets[str(args[1])], args[2:]
            if api == 'SetWidgetAlpha':
                state['alpha'] = values[0]
            elif api == 'SetWidgetFont':
                state['font'] = tuple(values)
            elif api in ('SetWidgetUIImage', 'SetWidgetUIImageById'):
                state.setdefault('images', {})[values[0]] = tuple(values[1:])
        return result

    def _bridge(self, command, *args):
        if command == 'partyframes':
            self.party_attached = bool(args[0])
            if self.party_attached and len(args) > 1 and args[1]:
                dialog = self.dialogs[int(self.g.AFCore.DIALOG.party)]
                self.party_layouts.append(tuple(self.rect(dialog, 'ctn_member' + str(i))[:4] for i in range(1, 7)))
            return True
        # The stock player frame's hidden 3D portrait view (basic_status_dialog/portrait, type 0x2000).
        if command == 'pin' and len(args) > 7 and args[7] == 'portrait':
            if args[1] is None:
                self.portrait_pin = None
                return True
            if not self.portrait_view or args[0] != self.g.AFCore.DIALOG.basic_status or args[8] != 0x2000:
                return False
            self.portrait_pin = tuple(args[1:7])
            return True
        if command == 'widgetshown' and args[1] == 'portrait':
            self.portrait_shown = bool(args[2])
            return True
        if command in ('pin', 'widgetshown', 'portrait') and len(args) > 1:
            name = str(args[7] if command == 'pin' and len(args) > 7 else args[1])
            if name.startswith('AFHF') and name.endswith('Portrait'):
                dialog = self.dialogs.get(args[0])
                assert dialog and dialog['name'] == 'AFHFrames', 'a canvas portrait view lives in the frame canvas'
                assert name.startswith('AFHFParty/AFHFParty') or name in ('AFHFTargetPortrait', 'AFHFPlayerPortrait'), name
                host_name = name.rsplit('/', 1)[-1]
                host = self.widgets[host_name]
                assert host.get('nativeCreated') and host.get('nativeKind') == 'container', name
                assert host.get('parent') == ('AFHFParty' if name.startswith('AFHFParty/') else 'AFHFrames'), name
                assert 'not_active' in host.get('flags', ()), 'portrait host must remain passive'
                assert self.g[host_name] is not None, 'real framework must return and attach a host wrapper'
                if command == 'pin':
                    if args[1] is None:
                        self.view_pins.pop(name, None)
                    else:
                        assert args[8] == 0x2008
                        self.view_pins[name] = tuple(args[1:7])
                    return True
                if command == 'widgetshown':
                    self.view_shown[name] = bool(args[2])
                    self.widgets[name.rsplit('/', 1)[-1]]['visible'] = int(bool(args[2]))
                    return True
                who = args[2] if len(args) > 2 else None
                if who is None:
                    self.view_models.pop(name, None)
                    return True
                host['nativeModel'] = {'name': 'Model', 'owner': host_name, 'camera': 'portrait',
                                       'align': 'proportion', 'passive': True}
                if isinstance(who, (int, float)) and who == -1:
                    assert name == 'AFHFPlayerPortrait', 'only the player asks for its own body'
                    if not self.player_body:
                        self.view_models.pop(name, None)
                        return False, 2  # no entity
                    self.view_models[name] = who
                    return True
                if isinstance(who, (int, float)):
                    assert name != 'AFHFPlayerPortrait', 'the player view never shows a selection'
                    target = self.frame_units.get('target')
                    # Core 1.3.27: only player bodies (100000..100003) have a portrait camera (NO_CAMERA=17).
                    if not target or target[0] != who or not 100000 <= self.target_models.get(who, 0) <= 100003:
                        self.view_models.pop(name, None)
                        return False
                    self.view_models[name] = who
                    return True
                if who not in self.looks:
                    return False
                self.view_models[name] = who
                return True
        if command == 'unitclass':
            if args[0] == 'party':
                return self.classes.get(args[1])
            row = self.frame_units.get(args[0])
            return self.classes.get(row[0]) if row else None
        if command == 'unit':
            return self.frame_units.get(args[0])
        if command == 'playerid':
            row = self.frame_units.get('player')
            return row[0] if row else None
        if command == 'gauge' and args[0] == self.g.AFCore.DIALOG.basic_status:
            if 'dp' in str(args[1]).lower():
                return (*self.dp, bool(self.character))
        if command == 'widget':
            dialog = self.dialogs.get(args[0])
            path, node = self.node(dialog, args[1], args[2] if len(args) > 2 else None)
            if not node:
                return None
            kind = {'image': 'uistaticimage', 'static': 'uistatic', 'gauge': 'uigauge',
                    'flygauge': 'uiflygauge', 'container': 'uicontainer',
                    'containerex': 'uicontainerex', 'staticex': 'uistaticex',
                    'button': 'uibutton', 'dialog': 'uidialog'}.get(node['kind'])
            catalog = self.g.AFCore.UI.widgetTypes[kind] if kind else None
            numeric = catalog[1]['id'] if catalog else 0
            numeric = self.native_types.get((args[0], path), numeric)
            # Local shown bit and effective visibility deliberately differ when
            # a parent is hidden. Restoration must preserve the local bit.
            return node['name'], numeric, int(bool(node['shown'])), *node['rect'], *self.rect(dialog, path)
        return super()._bridge(command, *args)

    def event(self, event):
        self.r.write('input', 'widget-event', ('AFHTop', event))
        return self.g.Widget_TriggerScript('AFHTop', 'OnEvent', event)

    def click_widget(self, name):
        assert name in self.widgets and self.widgets[name]['visible'], 'Not a visible input: ' + name
        self.r.write('input', 'widget-click', (name,))
        return self.g.Widget_TriggerScript(name, 'OnClick')

    def option_input(self, name, text):
        assert self.widgets[name]['visible'], 'Not a visible input: ' + name
        self.g[name].SetText(self.g[name], str(text))
        self.r.write('input', 'widget-event', ('AFUIOptions', 'GAME_UI_EDITBOX_ENTER', name))
        self.g.Widget_TriggerScript('AFUIOptions', 'OnEvent', 'GAME_UI_EDITBOX_ENTER', name)


def healthy(t):
    for _, name in t.g.AF.Names(t.g.AF).items():
        assert not t.g.AF.IsFailed(t.g.AF, name), name + ' failed'
        assert not t.g.AF.Error(t.g.AF, name), name + ': ' + str(t.g.AF.Error(t.g.AF, name))


def visible(t, unit):
    return bool(t.widgets[PREFIXES[unit] + 'Backdrop']['visible'])


def text(t, unit, suffix):
    return t.widgets[PREFIXES[unit] + suffix]['text']


def fill_ratio(t, unit, kind='Health'):
    prefix = PREFIXES[unit] + kind
    foreground = t.widgets[prefix + 'Fill']
    background = t.widgets[prefix + 'BG']
    return foreground['rect'][2] / background['rect'][2] if foreground['visible'] else 0


def tile(t, unit, kind):
    return t.widgets[PREFIXES[unit] + kind + 'Fill']['images'][0][0]


def native_visibility(t):
    """Exact local shown states, including native widgets hidden before takeover."""
    return {(ident, path): bool(node['shown'])
            for ident, dialog in t.dialogs.items() if ident < 0x20e
            for path, node in dialog['nodes'].items()}


def assert_native(t, expected, context):
    actual = native_visibility(t)
    changed = [(key, value, actual.get(key)) for key, value in expected.items()
               if actual.get(key) != value]
    assert not changed, context + ': native widget state changed: ' + repr(changed[:8])


@contextmanager
def mutations(t, prefix='AFHF'):
    calls, original = [], t.r.write
    def spy(kind, name, args=(), *rest):
        if kind == 'widget' and not name.startswith('Get'):
            widget = str(args[2]) if name == 'AddNewWidget' and len(args) > 2 else str(args[1]) if len(args) > 1 else ''
            if widget.startswith(prefix):
                calls.append((name, widget, tuple(args[2:])))
        elif kind == 'bridge' and name == 'widgetshown':
            calls.append((name, *args))
        return original(kind, name, args, *rest)
    t.r.write = spy
    try:
        yield calls
    finally:
        t.r.write = original


class Options:
    """Read Core metadata, then deliver actual generated widget input scripts."""

    def __init__(self, t):
        self.t, self.entries = t, {}
        t.slash('/afui Unit Frames')
        pages = t.g.AF.Config.Pages(t.g.AF.Config)
        for _, page in pages.items():
            for _, tab in page['tabs'].items():
                for _, group in (tab['groups'] or t.lua.table()).items():
                    for _, option in (group['options'] or t.lua.table()).items():
                        if option['owner'] == 'UnitFrames' and option['key']:
                                    self.entries[option['key']] = (page, tab, option)
        assert self.entries, 'UnitFrames has no shipped setting controls'

    def get(self, key):
        return self.t.g.AF.DB.Get(self.t.g.AF.DB, 'UnitFrames', key)

    def cell(self, key):
        page, tab, option = self.entries[key]
        self.t.slash('/afui ' + page['id'] + ' ' + tab['id'])
        for _ in range(20):
            for index in range(1, 17):
                # A check box shows its label beside it (Desc); other controls above them (Label).
                for part in ('Label', 'Desc'):
                    label = self.t.widgets['AFUIOptionsCell' + str(index) + part]
                    if label['visible'] and label['text'] == option['label']:
                        return 'AFUIOptionsCell' + str(index), option
            next_button = self.t.widgets['AFUIOptionsNext']
            assert next_button['visible'], 'Option is not rendered: ' + key
            self.t.click_widget('AFUIOptionsNext')
        raise AssertionError('Option could not be reached: ' + key)

    def set(self, key, value):
        base, option = self.cell(key)
        before, kind = self.get(key), option['kind']
        if before == value:
            return
        if kind == 'toggle':
            self.t.click_widget(base + 'Check')
        elif kind in ('input', 'range'):
            self.t.option_input(base + 'Edit', value)
        elif kind == 'color':
            # The swatch toggles its editor; open it only if it is closed, then find the
            # cell again, since an expanded colour can move to another cell or page.
            if not self.t.widgets[base + 'Hex']['visible']:
                self.t.click_widget(base + 'Value')
                base, option = self.cell(key)
            self.t.option_input(base + 'Hex', value)
            self.t.click_widget(base + 'HexApply')
        elif kind == 'select':
            values = self.t.g.AF.Config.Values(self.t.g.AF.Config, option)
            offset = next(int(index) for index, candidate in values.items() if candidate == value)
            current = next(int(index) for index, candidate in values.items() if candidate == before)
            self.t.click_widget(base + 'Value')
            distance = (offset - 1) // 8 - (current - 1) // 8
            for _ in range(abs(distance)):
                self.t.click_widget('AFUIOptionsDropNext' if distance > 0 else 'AFUIOptionsDropPrev')
            self.t.click_widget('AFUIOptionsDropChoice' + str((offset - 1) % 8 + 1))
        else:
            raise AssertionError('Unsupported UnitFrames control ' + kind)
        assert self.get(key) == value, (key, before, value, self.get(key))
        self.t.ticks(5)
        healthy(self.t)

    def exercise_every_setting(self):
        exercised = []
        for key in sorted(self.entries):
            _, _, option = self.entries[key]
            original, kind = self.get(key), option['kind']
            if kind == 'toggle':
                values = [not original if isinstance(original, bool) else 1 - original]
            elif kind == 'range':
                rule = self.t.g.AF.DB.Rule(self.t.g.AF.DB, 'UnitFrames', key)
                values = [rule[3], rule[4]]
            elif kind == 'input':
                values = ['[name] [current]/[max] [percent]']
            elif kind == 'color':
                values = ['#db792e']
            elif kind == 'select':
                values = [value for _, value in self.t.g.AF.Config.Values(self.t.g.AF.Config, option).items()]
            else:
                raise AssertionError('Untested setting kind: ' + kind)
            for value in values:
                self.set(key, value)
            self.set(key, original)
            exercised.append(key)
        return exercised


def size_ids(t):
    """Legacy saved sizes 1..8 load through every decoder untouched and render normalized; 9..15 are real."""
    font = lambda: t.widgets['AFHFPlayerHealthTextLeft']['font'][0]
    ev = lambda code: t.lua.eval('function() ' + code + ' end')()
    name = 'ui-' + hashlib.sha1(b'TraceServer:FontLegacy').hexdigest()
    t.files[name + '.0.cfg'] = 'AFUI1 10\nset.ufFont=8\nset.ufPlayerFont=5\nstore.Old=ufFont:8;ufPlayerFont:5\nEND\n'
    t.character = 'FontLegacy'; t.frame_units['player'] = sample(9101, t.character)
    t.event('PLAYER_ENTERING_WORLD'); t.ticks(45)  # AFUI1 storage decoder, then the normal load
    assert ev("return AF.DB:Get('UnitFrames','ufFont')==8 and AF.DB:Get('UnitFrames','player.font')==5"), 'legacy save rewritten or rejected'
    assert font() == 'large', font()  # legacy 5 -> Medium
    ev("AF.DB:Set('UnitFrames','player.font',0)"); t.ticks(5)
    assert font() == 'ui_default' and ev("return AF.DB:Get('UnitFrames','ufFont')")== 8  # shared legacy 8 -> Standard
    ev("AF.DB:Set('UnitFrames','player.font',3)"); t.ticks(5)
    assert font() == 'v3_option'  # legacy 3 -> Standard (no shadow)
    ev("assert(AF:Call('Profiles','Choose','Old').note:find('Selected'))"); t.ticks(5)  # legacy store.* snapshot
    assert ev("return AF.DB:Get('UnitFrames','ufFont')==8 and AF.DB:Get('UnitFrames','player.font')==5"), 'legacy snapshot rewritten'
    assert font() == 'large'
    ev("AF.DB:Set('UnitFrames','ufFont',13);AF.DB:Set('UnitFrames','player.font',0)"); t.ticks(5)
    assert font() == 'v3_chat_normal' and ev("return AF.DB:Get('UnitFrames','ufFont')") == 13
    assert ev("return AF.DB:SnapshotParse('S2;ufFont:8;ufPlayerFont:5')~=nil"), 'S2 snapshot with legacy sizes rejected'
    ev("AF.DB:Set('UnitFrames','ufFont',15);AF.DB:Set('UnitFrames','player.font',9)")
    ev("code=AF.DB:ProfileExport('profile');AF.DB:Set('UnitFrames','ufFont',10);AF.DB:Set('UnitFrames','player.font',0)")
    ev("AF:Call('Profiles','Set','code',code);local r=AF:Call('Profiles','Import');assert(r.note:find('Imported'),r.note)")
    assert ev("return AF.DB:Get('UnitFrames','ufFont')==15 and AF.DB:Get('UnitFrames','player.font')==9"), 'AFUI2 round trip changed sizes'
    t.ticks(5); assert font() == 'v3_shortcut'
    ev("local b='ufFont=8;ufPlayerFont=5';local c='AFUI1!'..#b..'!'..b..'!'..AF.DB._internal.checksum(b);AF:Call('Profiles','Set','code',c);local r=AF:Call('Profiles','Import');assert(r.note:find('Imported'),r.note)")  # AFUI1 code decoder
    assert ev("return AF.DB:Get('UnitFrames','ufFont')==8 and AF.DB:Get('UnitFrames','player.font')==5"), 'AFUI1 import changed sizes'
    t.ticks(5); assert font() == 'large'


def visual_settings(t, options):
    """Setting writes must change the shipped geometry, labels and skin too."""
    color_keys = ('ufFixedColor', 'ufPowerColor', 'ufDeadColor', 'ufOfflineColor',
                  'player.healthColor', 'player.powerColor', 'player.healthMode', 'player.powerMode',
                  'party.stateColors', 'party.deadColor', 'party.offlineColor')
    color_original = {key: options.get(key) for key in color_keys}
    for group, unit in (('player', 'player'), ('target', 'target'),
                        ('targettarget', 'targettarget'), ('party', 'party3')):
        prefix = PREFIXES[unit]
        original = {key: options.get(group + '.' + key) for key in
                    ('healthHeight', 'spacing', 'border', 'backdropOpacity', 'font', 'showLevel')}
        options.set(group + '.healthHeight', 8)
        small = t.widgets[prefix + 'HealthBG']['rect'][3]
        options.set(group + '.healthHeight', 40)
        assert t.widgets[prefix + 'HealthBG']['rect'][3] > small
        options.set(group + '.healthHeight', original['healthHeight'])
        options.set(group + '.spacing', 8)
        health, power = t.widgets[prefix + 'HealthBG']['rect'], t.widgets[prefix + 'PowerBG']['rect']
        assert abs(power[1] - health[1] - health[3] - 8) < .01
        options.set(group + '.spacing', original['spacing'])
        options.set(group + '.border', 0)
        assert not t.widgets[prefix + 'HealthBGEdgeTop']['visible']
        options.set(group + '.border', original['border'])
        options.set(group + '.backdropOpacity', 0)
        assert t.widgets[prefix + 'Backdrop']['alpha'] == 0
        options.set(group + '.backdropOpacity', original['backdropOpacity'])
        for number, shader in ((9, 'v3_shortcut'), (12, 'large'), (15, 'v3_chat_large')):
            options.set(group + '.font', number)
            assert t.widgets[prefix + 'HealthTextLeft']['font'][0] == shader, (group, number)
        options.set(group + '.font', original['font'])
        options.set(group + '.showLevel', 0)
        assert not t.widgets[prefix + 'Level']['visible']
        options.set(group + '.showLevel', original['showLevel'])
        for resource in ('health', 'power'):
            for slot in ('Left', 'Centre', 'Right'):
                key = group + '.' + resource + slot
                before = options.get(key)
                value = resource + slot
                options.set(key, value)
                assert text(t, unit, resource.title() + 'Text' + slot) == value
                options.set(key, before)
    options.set('player.healthMode', 'fixed')
    options.set('ufFixedColor', '#ff0000')
    assert tile(t, 'player', 'Health').endswith('solid-f00.dds')
    options.set('player.healthMode', 'custom')
    options.set('player.healthColor', '#00ff00')
    assert tile(t, 'player', 'Health').endswith('solid-0f0.dds')
    options.set('player.powerMode', 'shared')
    options.set('ufPowerColor', '#0000ff')
    assert tile(t, 'player', 'Power').endswith('solid-00f.dds')
    options.set('player.powerMode', 'custom')
    options.set('player.powerColor', '#ff00ff')
    assert tile(t, 'player', 'Power').endswith('solid-f0f.dds')
    row = t.frame_units['party3']
    t.frame_units['party3'] = sample(row[0], row[1], health=0)
    t.ticks(5)
    options.set('party.stateColors', 'shared')
    options.set('ufDeadColor', '#ff0000')
    shared = t.widgets['AFHFParty3State']['attr']['textColor']
    options.set('party.stateColors', 'custom')
    options.set('party.deadColor', '#00ff00')
    assert t.widgets['AFHFParty3State']['attr']['textColor'] != shared
    assert text(t, 'party3', 'State') == 'Dead'
    t.frame_units['party3'] = sample(row[0], row[1], health=None, maximum=None)
    t.ticks(5)
    options.set('party.stateColors', 'shared')
    options.set('ufOfflineColor', '#0000ff')
    shared = t.widgets['AFHFParty3State']['attr']['textColor']
    options.set('party.stateColors', 'custom')
    options.set('party.offlineColor', '#ff00ff')
    assert t.widgets['AFHFParty3State']['attr']['textColor'] != shared
    assert text(t, 'party3', 'State') == 'Unavailable'
    t.frame_units['party3'] = row; t.ticks(5)
    for key, value in color_original.items():
        options.set(key, value)
    options.set('player.showDP', 0)
    assert not t.widgets['AFHFPlayerDPBG']['visible']
    options.set('player.showDP', 1)
    assert t.widgets['AFHFPlayerDPBG']['visible']
    # Player portrait: the player's own body in its canvas view (Core 1.3.23), inside the painted border's
    # opening, left of the bars, over a backdrop-coloured background with its own opacity.
    view, border = 'AFHFPlayerPortrait', t.widgets['AFHFPlayerPortraitBorder']
    back, backdrop = t.widgets['AFHFPlayerPortraitBG'], t.widgets['AFHFPlayerBackdrop']
    t.ticks(5)
    assert border['visible'] and border['images'][0][-1].endswith('/border-player.dds'), 'player portrait border is missing: %r' % (border,)
    assert t.view_models.get(view) == -1 and t.view_shown.get(view) and view in t.view_pins, 'the player body was not built and shown'
    assert not t.portrait_shown and t.portrait_pin is None, 'the stock view stays hidden while the canvas view works'
    x, y, mode, clamp, width, height = t.view_pins[view]
    assert mode == 0 and not clamp and width == height and width > 8 and x > 0 and y > 0, t.view_pins[view]
    assert t.widgets['AFHFPlayerHealthBG']['rect'][0] >= border['rect'][0] + border['rect'][2], 'the bars must start after the portrait'
    assert not t.widgets['AFHFTargetPortraitBorder']['visible'] and not t.widgets['AFHFParty3PortraitBorder']['visible']
    # The model draws over the canvas backdrop, so Backdrop opacity cannot dim or hide it: the backdrop
    # covers the whole frame, and the portrait background is the portrait's own setting.
    options.set('player.backdropOpacity', 100)
    assert backdrop['visible'] and backdrop['rect'][0] <= border['rect'][0] + 1, 'the canvas backdrop should span the frame'
    assert t.view_models.get(view) == -1 and t.view_shown.get(view), 'backdrop opacity removed the model'
    assert back['visible'] and back['images'][0][-1].endswith('.dds') and abs(back['alpha'] - 0.7) < 1e-6, back
    assert border['rect'][0] < back['rect'][0] and back['rect'][0] + back['rect'][2] < border['rect'][0] + border['rect'][2], 'background must sit in the opening'
    options.set('player.portraitOpacity', 25)
    assert back['visible'] and abs(back['alpha'] - 0.25) < 1e-6
    options.set('player.portraitOpacity', 0)
    assert not back['visible'] and t.view_shown.get(view), '0% shows only the model'
    options.set('player.portraitOpacity', 70)
    # The stock DP gauges the game re-shows are parked off screen while the frame owns the dialog.
    stock = t.g.AFCore.DIALOG.basic_status
    dp_parts = ('dp_gauge_1', 'dp_gauge_2', 'dp_gauge_3', 'dp_gauge_text', 'dp_level')
    assert all(t.pins.get((stock, part), (None,) * 4)[3] == 5 for part in dp_parts), 'stock DP gauges are not parked'
    wide = t.widgets['AFHFPlayerHealthBG']['rect'][2]
    options.set('player.showPortrait', 0); t.ticks(2)
    assert not border['visible'] and not back['visible'] and view not in t.view_models and view not in t.view_pins, 'turning the portrait off must release the view'
    assert t.widgets['AFHFPlayerHealthBG']['rect'][2] > wide, 'the bars must take the portrait room back'
    options.set('player.showPortrait', 1); t.ticks(5)
    assert border['visible'] and t.view_models.get(view) == -1 and t.view_shown.get(view)
    # Fallback: without a canvas body after three bounded attempts, the stock view is used, and the
    # backdrop (drawn over it) starts beside the border at any opacity.
    t.mark('player-portrait-stock-fallback')
    t.player_body = False
    options.set('player.showPortrait', 0); t.ticks(2)
    options.set('player.showPortrait', 1); t.ticks(80)
    assert t.portrait_shown and t.portrait_pin and view not in t.view_models, 'the stock fallback view was not shown'
    assert border['visible'] and not back['visible'], 'the stock view has no canvas background'
    assert backdrop['rect'][0] >= border['rect'][0] + border['rect'][2], 'the backdrop covers the stock player model: %r' % (backdrop['rect'],)
    options.set('player.showPortrait', 0)
    assert not border['visible'] and not t.portrait_shown and t.portrait_pin is None, 'turning the portrait off must release the stock view'
    options.set('player.showPortrait', 1)
    assert border['visible'] and t.portrait_shown and t.portrait_pin
    t.player_body = True
    options.set('player.backdropOpacity', 95)
    options.set('player.enabled', 0); t.ticks(5)
    assert not any((stock, part) in t.pins for part in dp_parts), 'a released player frame must give the stock DP gauges back'
    options.set('player.enabled', 1); t.ticks(5)
    assert all((stock, part) in t.pins for part in dp_parts)


def target_portraits(t):
    """Use the real target-change events and controls, not a direct Portrait element call."""
    original = t.frame_units['target']
    view, border = 'AFHFTargetPortrait', t.widgets['AFHFTargetPortraitBorder']
    cases = ((8101, 'Village Guard', 203004), (8102, 'Village Guard', 203005),
             (8103, 'OtherPlayer', 100001), (8104, 'BotWithoutGrowth', 100002),
             (8105, 'RankedBot', 100003))
    t.identities['RankedBot'] = ('Good', 'Great')
    bars_without_portrait = None
    for ident, name, model in cases:
        t.mark('target-portrait-' + name + '-' + str(ident))
        t.target_models[ident] = model
        t.frame_units['target'] = sample(ident, name)
        t.event('USER_CHANGED_TARGET'); t.ticks(5)
        if not 100000 <= model <= 100003:
            # A creature without a player body has no portrait camera: no body, no border, full-width bars.
            assert view not in t.view_models and not t.view_shown.get(view) and not border['visible'], (name, t.view_models)
            bars_without_portrait = t.widgets['AFHFTargetHealthBG']['rect'][0]
            continue
        assert bars_without_portrait is None or t.widgets['AFHFTargetHealthBG']['rect'][0] > bars_without_portrait
        assert t.view_models.get(view) == ident and t.view_shown.get(view), (name, t.view_models)
        assert border['visible'], 'missing target portrait border: ' + name
        suffix = 'good' if name == 'RankedBot' else 'player'
        assert border['images'][0][-1].endswith('/border-' + suffix + '.dds')
        assert t.widgets['AFHFTargetHealthBG']['rect'][0] >= border['rect'][0] + border['rect'][2]
        # Already prepared portraits must not reload on unchanged refreshes or health damage.
        builds = t.r.counts['bridge:portrait']
        t.frame_units['target'] = sample(ident, name, health=330)
        t.event('UNIT_ATTACK_DAMAGE'); t.ticks(25)
        assert t.r.counts['bridge:portrait'] == builds, 'unchanged target reloaded its model'

    t.mark('target-portrait-toggle-through-shipped-checkbox')
    options = Options(t)
    narrow = t.widgets['AFHFTargetHealthBG']['rect'][2]
    options.set('target.showPortrait', 0); t.ticks(5)
    assert not border['visible'] and view not in t.view_models and view not in t.view_pins
    assert not t.view_shown.get(view)
    assert t.widgets['AFHFTargetHealthBG']['rect'][2] > narrow
    options.set('target.showPortrait', 1); t.ticks(5)
    assert border['visible'] and t.view_models.get(view) == 8105
    t.click_widget('AFUIOptionsClose')

    t.mark('target-portrait-unavailable-model-clears-previous-body-and-retries')
    t.frame_units['target'] = sample(8106, 'WaitingForNativeModel')
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert not border['visible'] and view not in t.view_models and view not in t.view_pins
    wide = t.widgets['AFHFTargetHealthBG']['rect'][2]
    t.target_models[8106] = 100000  # the native body arrives later
    t.ticks(30)
    assert border['visible'] and t.view_models.get(view) == 8106
    assert t.widgets['AFHFTargetHealthBG']['rect'][2] < wide
    # Both the real selection and the frame disappear; no old body survives deselection.
    t.mark('target-portrait-clear-and-reselect')
    t.frame_units.pop('target')
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert not border['visible'] and view not in t.view_models and view not in t.view_pins
    assert not t.view_shown.get(view)
    t.frame_units['target'] = sample(8106, 'WaitingForNativeModel')
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert border['visible'] and t.view_models.get(view) == 8106
    t.target_models.clear()
    t.identities.pop('RankedBot')
    t.frame_units['target'] = original
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert not border['visible'] and view not in t.view_models and view not in t.view_pins
    healthy(t)


def classes_and_size(t):
    """Native class for a character target and party member; frame and bar size through the shipped controls."""
    t.mark('unitframes-native-class-tag-icon-and-size')
    original = t.frame_units['target']
    t.classes[8201] = (1, 'Gladiator')
    t.frame_units['target'] = sample(8201, 'ClassedPlayer')
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    options = Options(t)
    options.set('target.healthCentre', '[class]'); t.ticks(5)
    assert text(t, 'target', 'HealthTextCentre') == 'Gladiator', text(t, 'target', 'HealthTextCentre')
    icon = t.widgets['AFHFTargetClassIcon']
    assert icon['visible'] and icon['images'][0][-1].endswith('/icon_emblem_fighter'), icon
    t.classes[8201] = (16, 'Певец')
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert text(t, 'target', 'HealthTextCentre') == 'Певец'
    assert icon['images'][0][-1].endswith('/icon_emblem_bard')
    assert tile(t, 'target', 'Health') != tile(t, 'player', 'Health'), 'numeric class ID must choose localized class color'
    options.set('target.showClass', 0); t.ticks(5)
    assert not icon['visible']
    options.set('target.showClass', 1); t.ticks(5)
    # An NPC has no class: no icon and an empty [class].
    t.frame_units['target'] = sample(8202, 'ClassedPlayer')  # Same name, different native NPC identity.
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert not icon['visible'] and text(t, 'target', 'HealthTextCentre') == ''
    options.set('target.healthCentre', '')
    # Party members by name.
    member = t.frame_units['party3']
    t.classes[member[1]] = (13, 'Aethertech')
    t.event('PARTY_MEMBERS_CHANGED'); t.ticks(5)
    party_icon = t.widgets['AFHFParty3ClassIcon']
    assert party_icon['visible'] and party_icon['images'][0][-1].endswith('/icon_emblem_rider'), party_icon
    t.frame_units['target'] = sample(8203, member[1])
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert not icon['visible'] and text(t, 'target', 'HealthTextCentre') == '', 'party name cache leaked into NPC target'
    old_assist = t.frame_units['targettarget']
    t.classes[8204] = (10, 'Лекарь')
    t.frame_units['targettarget'] = sample(8204, 'ClassedPlayer')
    options.set('targettarget.healthCentre', '[class]'); t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert text(t, 'targettarget', 'HealthTextCentre') == 'Лекарь'
    assert t.widgets['AFHFTargetTargetClassIcon']['visible']
    options.set('targettarget.healthCentre', '')
    t.frame_units['targettarget'] = old_assist
    t.classes.pop(member[1])
    # Whole frame and bar widths, as shares of the native frame and of the bar area.
    t.frame_units['target'] = original
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    backdrop, health, power = (t.widgets['AFHFTarget' + part] for part in ('Backdrop', 'HealthBG', 'PowerBG'))
    width, height, bar = backdrop['rect'][2], backdrop['rect'][3], health['rect'][2]
    options.set('target.frameWidth', 150); options.set('target.frameHeight', 120); t.ticks(5)
    assert abs(backdrop['rect'][2] - width * 1.5) < 2 and abs(backdrop['rect'][3] - height * 1.2) < 2, backdrop['rect']
    wide = health['rect'][2]
    assert wide > bar
    options.set('target.healthWidth', 50); t.ticks(5)
    assert abs(health['rect'][2] - wide / 2) < 2 and power['rect'][2] > health['rect'][2]
    for key, value in (('target.healthWidth', 100), ('target.frameWidth', 100), ('target.frameHeight', 100)):
        options.set(key, value)
    t.ticks(5)
    assert abs(backdrop['rect'][2] - width) < 1 and abs(health['rect'][2] - bar) < 1
    for group, unit in (('player', 'player'), ('target', 'target'), ('targettarget', 'targettarget'), ('party', 'party3')):
        for key in ('frameWidth', 'frameHeight', 'healthWidth', 'powerWidth'):
            assert options.get(group + '.' + key) == 100, 'old profiles must default to100%'
        prefix = PREFIXES[unit]
        old = tuple(t.widgets[prefix + 'Backdrop']['rect'])
        options.set(group + '.frameWidth', 50); options.set(group + '.frameHeight', 50)
        small = tuple(t.widgets[prefix + 'Backdrop']['rect'])
        options.set(group + '.frameWidth', 300); options.set(group + '.frameHeight', 300)
        large = tuple(t.widgets[prefix + 'Backdrop']['rect'])
        assert abs(large[2] - small[2] * 6) < 2 and abs(large[3] - small[3] * 6) < 2
        for key, part in (('healthWidth', 'HealthBG'), ('powerWidth', 'PowerBG'), *([('dpWidth', 'DPBG')] if group == 'player' else [])):
            w = t.widgets[prefix + part]['rect'][2]
            options.set(group + '.' + key, 50)
            assert abs(t.widgets[prefix + part]['rect'][2] - w/2) < 2
            options.set(group + '.' + key, 100)
        options.set(group + '.frameWidth', 100); options.set(group + '.frameHeight', 100)
        assert abs(t.widgets[prefix + 'Backdrop']['rect'][2] - old[2]) < 1
    # A resized party row includes fixed-size voice art and native selection dimensions.
    layouts = len(t.party_layouts)
    options.set('party.frameWidth', 150)
    assert len(t.party_layouts) == layouts+1, 'one changed batch must invoke one explicit native aura refresh'
    options.set('party.frameHeight', 150)
    assert len(t.party_layouts) == layouts+2
    party = t.dialogs[int(t.g.AFCore.DIALOG.party)]
    rows = [t.rect(party, 'ctn_member' + str(i)) for i in range(1, 7)]
    assert all(rows[i+1][1] >= rows[i][1]+rows[i][3] for i in range(5))
    assert t.rect(party, 'ctn_member3/#1')[2:4] == (26*t.scale, 22*t.scale)
    flight = t.rect(party, 'ctn_member3/fly_gauge')
    assert flight[2:4] == (18*t.scale, 18*t.scale)
    assert flight[0]+flight[2] <= rows[2][0]+300*t.scale
    assert t.rect(party, 'select')[2:4] == (300*t.scale, 69*t.scale)
    layouts = len(t.party_layouts)
    t.ticks(8)
    assert len(t.party_layouts) == layouts, 'unchanged holds replayed native aura refresh'
    options.set('party.frameWidth', 100); options.set('party.frameHeight', 100)
    # Header layout: each top-row item moves by its own offset through the shipped Header tab; 0 restores it.
    level = t.widgets['AFHFTargetLevel']['rect']
    options.set('target.levelX', -12); options.set('target.levelY', 3)
    moved = t.widgets['AFHFTargetLevel']['rect']
    assert abs(moved[0] - (level[0] - 12)) < .01 and abs(moved[1] - (level[1] + 3)) < .01, (level, moved)
    options.set('target.levelX', 0); options.set('target.levelY', 0)
    assert t.widgets['AFHFTargetLevel']['rect'] == level
    t.click_widget('AFUIOptionsClose')
    t.classes.clear()
    healthy(t)


def scenarios(t):
    t.mark('unitframes-cold-disconnected-and-player-only-login')
    t.lua.execute("""
        local db=AF.DB
        local original=db:ProfileSnapshot()
        -- Synthetic old saved snapshot: deliberately no new geometry keys.
        db._internal.stored.LegacyGeometryFixture='S2;ufPlayerShowPortrait:0;ufPlayerShowDP:0;ufPlayerHealthLeft:Saved text'
        assert(db:ProfileRestore('LegacyGeometryFixture'))
        for _,group in ipairs({'player','target','targettarget','party'})do
            for _,key in ipairs({'frameWidth','frameHeight','healthWidth','powerWidth'})do
                assert(db:Get('UnitFrames',group..'.'..key)==100)
            end
        end
        assert(db:Get('UnitFrames','player.dpWidth')==100)
        assert(db:Get('UnitFrames','player.showPortrait')==0 and db:Get('UnitFrames','player.showDP')==0)
        assert(db:Get('UnitFrames','player.healthLeft')=='Saved text')
        db._internal.stored.LegacyGeometryFixture=original
        assert(db:ProfileRestore('LegacyGeometryFixture'))
        db._internal.stored.LegacyGeometryFixture=nil
    """)
    # Real shipped CreateWidget queues the unsupported request but returns nil;
    # LoadChildWidget therefore never attaches/configures a 3dview wrapper.
    host = t.g.AFHFrames
    host.LoadChildWidget(host, t.table(ET.fromstring(
        '<Widget type="3dview" name="RejectedPortraitProbe" frame="0,0,10,10" camtable="portrait" flag="not_active;not_visible"/>')))
    t.drain_widgets()
    rejected = t.widgets['RejectedPortraitProbe']
    assert t.g.RejectedPortraitProbe is None
    assert rejected.get('nativeCreated') is False and not rejected.get('parent') and 'flags' not in rejected
    for unit in ('target', *(f'party{i}' for i in range(1, 7))):
        state = t.widgets[PREFIXES[unit] + 'Portrait']
        assert state.get('nativeKind') == 'container' and state.get('parent')
        assert {'transparent', 'not_active', 'not_visible'} <= state['flags']
    assert all(not visible(t, unit) for unit in UNITS)
    t.ticks(3)
    assert all(not visible(t, unit) for unit in UNITS)
    # Seed a mixture of local shown states. A child beneath a hidden panel may
    # still be locally shown; effective visibility cannot be its restore value.
    native_ids = [int(t.g.AFCore.DIALOG[name]) for name in ('basic_status', 'target', 'target_assist', 'party')]
    for ident in native_ids:
        visuals = [node for node in t.dialogs[ident]['nodes'].values()
                   if node['kind'] in ('gauge', 'static', 'image')]
        assert visuals, 'Missing catalogue fixtures for native unit frame ' + str(ident)
        visuals[0]['shown'] = False
    original = native_visibility(t)
    t.character = 'FrameCharacter'
    t.frame_units['player'] = sample(7001, t.character)
    t.event('PLAYER_ENTERING_WORLD'); t.ticks(45)
    assert visible(t, 'player'), 'player frame is absent after login'
    assert all(not visible(t, unit) for unit in UNITS if unit != 'player')
    healthy(t)

    t.mark('unitframes-target-targettarget-and-all-six-party-slots')
    t.frame_units['target'] = sample(7100, 'NativeTarget', health=640)
    t.frame_units['targettarget'] = sample(7101, 'TargetVictim', health=420)
    for index in range(1, 7):
        unit = 'party' + str(index)
        t.frame_units[unit] = sample(7200 + index, 'Human' + str(index), health=1000 - index * 70)
        t.labels[t.g.AFCore.DIALOG.party, 'ctn_member' + str(index) + '/title'] = 'Human' + str(index)
    t.event('USER_CHANGED_TARGET'); t.event('PARTY_MEMBERS_CHANGED')
    # Events are coalesced into one refresh on the next 50 ms tick, not the periodic interval.
    t.ticks(1)
    assert all(visible(t, unit) for unit in UNITS), 'native unit events did not refresh on the next tick'
    t.ticks(5)
    assert all(visible(t, unit) for unit in UNITS), 'a populated unit frame is missing'
    for unit in UNITS:
        assert abs(fill_ratio(t, unit) - t.frame_units[unit][3] / t.frame_units[unit][4]) < .02, unit
    assert any(native_visibility(t)[key] != value for key, value in original.items()), 'native visuals were never replaced'
    for key, value in native_visibility(t).items():
        if value != original[key]:
            assert t.dialogs[key[0]]['nodes'][key[1]]['kind'] in ('gauge', 'static', 'image'), 'native input or container was hidden'

    target_portraits(t)
    classes_and_size(t)

    t.mark('unitframes-target-and-targettarget-change-and-clear')
    t.frame_units['target'] = sample(7110, 'ChangedTarget', health=300)
    t.frame_units['targettarget'] = sample(7111, 'ChangedVictim', health=110)
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert 'ChangedTarget' in text(t, 'target', 'HealthTextLeft')
    assert 'ChangedVictim' in text(t, 'targettarget', 'HealthTextLeft')
    assert abs(fill_ratio(t, 'target') - .3) < .02
    assert abs(fill_ratio(t, 'targettarget') - .11) < .02

    t.mark('unitframes-companion-as-target-of-target-keeps-refreshing')
    t.identities['AssistCompanion'] = ('Good', 'Great')
    t.frame_units['targettarget'] = sample(7112, 'AssistCompanion', health=820)
    t.event('USER_CHANGED_TARGET'); t.ticks(20)
    assert 'AssistCompanion' in text(t, 'targettarget', 'HealthTextLeft')
    assert not text(t, 'targettarget', 'Identity'), 'Target of Target drew a bot label'
    assert not t.widgets['AFHFTargetTargetStar1']['visible']
    assert not t.widgets['AFHFTargetTargetCrest']['visible']
    for unit in UNITS:
        row = t.frame_units[unit]
        t.frame_units[unit] = sample(row[0], row[1], health=370, power=75)
    t.dp = (1000, 4000)
    t.ticks(5)
    for unit in UNITS:
        assert visible(t, unit), unit
        assert abs(fill_ratio(t, unit) - .37) < .02, unit
        assert abs(fill_ratio(t, unit, 'Power') - .25) < .02, unit
    assert abs(fill_ratio(t, 'player', 'DP') - .25) < .02
    healthy(t)

    target, assist = t.frame_units.pop('target'), t.frame_units.pop('targettarget')
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert not visible(t, 'target') and not visible(t, 'targettarget')
    t.frame_units.update(target=target, targettarget=assist)
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert visible(t, 'target') and visible(t, 'targettarget')

    t.mark('unitframes-clear-with-unapplied-widget-hides')
    t.frame_units.pop('target')
    t.frame_units.pop('targettarget')
    t.drop_target_hides = True
    t.event('USER_CHANGED_TARGET')
    t.drop_target_hides = False
    t.ticks(5)
    assert not any(state['visible'] for name, state in t.widgets.items()
                   if name.startswith('AFHFTarget')), 'target artwork survived a cleared selection'
    assert visible(t, 'player') and visible(t, 'party1')
    t.frame_units.update(target=target, targettarget=assist)
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert visible(t, 'target') and visible(t, 'targettarget')

    t.mark('unitframes-stale-assist-record-without-a-primary-selection')
    t.frame_units.pop('target')
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert not visible(t, 'target') and not visible(t, 'targettarget')
    t.frame_units['target'] = target
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert visible(t, 'target') and visible(t, 'targettarget')

    t.mark('unitframes-human-bot-party-join-leave-and-identity')
    t.frame_units['party3'] = sample(7303, 'NativeBot', health=510)
    t.labels[t.g.AFCore.DIALOG.party, 'ctn_member3/title'] = 'NativeBot'
    t.identities['NativeBot'] = ('Excellent', 'Great')
    t.event('PARTY_MEMBERS_CHANGED')
    assert text(t, 'party3', 'Identity'), 'party event did not refresh bot identity immediately'
    t.ticks(5)
    assert 'NativeBot' in text(t, 'party3', 'HealthTextLeft')
    assert text(t, 'party3', 'Identity'), 'verified bot rarity/rank is missing'
    assert not text(t, 'party2', 'Identity'), 'a human was labeled as a bot'
    crest = t.widgets['AFHFParty3Crest']
    assert crest['visible'] and crest['images'][0][-1].endswith('/crest-excellent.dds'), 'bot gear crest is missing: %r' % (crest,)
    assert not t.widgets['AFHFParty2Crest']['visible'], 'a human was given a gear crest'
    # No look from the server: no portrait, and the bars keep the whole row.
    view, border = 'AFHFParty/AFHFParty3Portrait', t.widgets['AFHFParty3PortraitBorder']
    t.ticks(30)
    assert not border['visible'] and view not in t.view_models and view not in t.view_pins
    wide = t.widgets['AFHFParty3HealthBG']['rect'][2]
    # The look arrives: the bot's body is built in the row's own 3D view, inside its gear-quality border.
    t.looks.add('NativeBot'); t.ticks(30)
    assert t.view_models.get(view) == 'NativeBot' and t.view_shown.get(view), 'the bot portrait was not built and shown'
    assert border['visible'] and border['images'][0][-1].endswith('/border-excellent.dds'), border
    x, y, mode, clamp, width, height = t.view_pins[view]
    assert mode == 0 and not clamp and width == height and width > 4, t.view_pins[view]
    assert t.widgets['AFHFParty3HealthBG']['rect'][2] < wide, 'the bars must make room for the portrait'
    assert t.widgets['AFHFParty3HealthBG']['rect'][0] >= border['rect'][0] + border['rect'][2]
    assert not t.widgets['AFHFParty2PortraitBorder']['visible'] and 'AFHFParty/AFHFParty2Portrait' not in t.view_models, 'a human was given a portrait'
    options = Options(t)
    options.set('party.showPortrait', 0)
    assert not border['visible'] and view not in t.view_models and view not in t.view_pins and not t.view_shown[view], 'the toggle must release the view'
    assert t.widgets['AFHFParty3HealthBG']['rect'][2] == wide
    options.set('party.showPortrait', 1); t.ticks(30)
    assert border['visible'] and t.view_models.get(view) == 'NativeBot'
    t.frame_units.pop('party6'); t.labels[t.g.AFCore.DIALOG.party, 'ctn_member6/title'] = ''
    t.event('PARTY_MEMBERS_CHANGED')
    assert not visible(t, 'party6'), 'party event did not hide a departed member immediately'
    t.ticks(5)
    assert not visible(t, 'party6'), 'departed party member remains visible'
    t.frame_units['party6'] = sample(7306, 'NewHuman')
    t.labels[t.g.AFCore.DIALOG.party, 'ctn_member6/title'] = 'NewHuman'
    t.event('PARTY_MEMBERS_CHANGED')
    assert visible(t, 'party6'), 'party event did not show a joining member immediately'
    t.ticks(5)
    assert visible(t, 'party6') and 'NewHuman' in text(t, 'party6', 'HealthTextLeft')
    # A stale or rearranged native slot may still show another player. Never
    # replace it until stock party data and the real slot title agree.
    t.labels[t.g.AFCore.DIALOG.party, 'ctn_member4/title'] = 'DifferentHuman'
    t.ticks(5)
    assert not visible(t, 'party4'), 'a mismatched native party row was replaced'
    t.labels[t.g.AFCore.DIALOG.party, 'ctn_member4/title'] = 'Human4'
    t.ticks(5); assert visible(t, 'party4')
    # The game shortens a long name in its row title ("»Admin«Bab..."): that row is still the member's.
    t.frame_units['party6'] = sample(7306, '»Admin«Babyyera')
    for shortened in ('»Admin«Bab...', '»Admin«Bab…'):
        t.labels[t.g.AFCore.DIALOG.party, 'ctn_member6/title'] = shortened
        t.event('PARTY_MEMBERS_CHANGED'); t.ticks(5)
        assert visible(t, 'party6'), 'a shortened native title kept the row stock: ' + shortened
    # A shortened title of someone else, or a too-short stem, never matches.
    for other in ('»Admin«Bob...', 'B...'):
        t.labels[t.g.AFCore.DIALOG.party, 'ctn_member6/title'] = other
        t.event('PARTY_MEMBERS_CHANGED'); t.ticks(5)
        assert not visible(t, 'party6'), 'a different shortened title was replaced: ' + other
    t.frame_units['party6'] = sample(7306, 'NewHuman')
    t.labels[t.g.AFCore.DIALOG.party, 'ctn_member6/title'] = 'NewHuman'
    t.event('PARTY_MEMBERS_CHANGED'); t.ticks(5); assert visible(t, 'party6')
    # Return this slot to a human before exact native fallback checks. Identity
    # legitimately substitutes native bot names while custom frames are off.
    t.identities.pop('NativeBot')
    t.event('PARTY_MEMBERS_CHANGED'); t.ticks(5)
    assert not text(t, 'party3', 'Identity')
    party_views = [name for name in (*t.view_models, *t.view_pins) if name.startswith('AFHFParty/')]
    assert not t.widgets['AFHFParty3PortraitBorder']['visible'] and not party_views, 'a row that became a person kept a portrait'

    t.mark('unitframes-resource-damage-death-and-recovery')
    for unit in UNITS:
        row = t.frame_units[unit]
        t.frame_units[unit] = sample(row[0], row[1], health=250, power=75)
    t.dp = (1000, 4000)
    t.event('UNIT_ATTACK_DAMAGE')
    assert abs(fill_ratio(t, 'player') - .25) < .02, 'damage event did not refresh before the timer'
    t.ticks(5)
    for unit in UNITS:
        assert abs(fill_ratio(t, unit) - .25) < .02, unit
        assert abs(fill_ratio(t, unit, 'Power') - .25) < .02, unit
    assert abs(fill_ratio(t, 'player', 'DP') - .25) < .02
    for unit in ('player', 'target', 'party1'):
        row = t.frame_units[unit]
        t.frame_units[unit] = sample(row[0], row[1], health=0, power=0)
    t.event('UNIT_SKILL_DAMAGE')
    assert fill_ratio(t, 'target') == 0, 'skill damage event did not refresh before the timer'
    t.ticks(5)
    for unit in ('player', 'target', 'party1'):
        assert fill_ratio(t, unit) == 0 and 'dead' in text(t, unit, 'State').lower(), unit
        row = t.frame_units[unit]
        t.frame_units[unit] = sample(row[0], row[1])
    t.ticks(5)
    assert all('dead' not in text(t, unit, 'State').lower() for unit in ('player', 'target', 'party1'))

    options = Options(t)
    t.mark('unitframes-disable-each-unit-and-restore-exact-native-widgets')
    for group, dialog in zip(('player', 'target', 'targettarget', 'party'), native_ids):
        options.set(group + '.enabled', 0)
        assert_native(t, {key: value for key, value in original.items() if key[0] == dialog}, group + ' disabled')
        affected = [unit for unit in UNITS if unit.startswith('party')] if group == 'party' else [group]
        assert all(not visible(t, unit) for unit in affected)
        options.set(group + '.enabled', 1)
        assert all(visible(t, unit) for unit in affected)
    t.mark('unitframes-module-disable-restores-exact-native-widgets')
    managed = [key for key, value in native_visibility(t).items() if value != original[key]]
    options.set('ufEnabled', 0)
    assert all(not visible(t, unit) for unit in UNITS)
    assert_native(t, original, 'module disabled')
    t.mark('unitframes-unsupported-native-type-is-never-partially-hidden')
    target_dialog = int(t.g.AFCore.DIALOG.target)
    refused = next(key for key in managed if key[0] == target_dialog)
    t.native_types[refused] = 3
    options.set('ufEnabled', 1)
    assert not visible(t, 'target')
    assert_native(t, {key: value for key, value in original.items() if key[0] == target_dialog}, 'unsupported native type')
    t.native_types.clear(); t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert all(visible(t, unit) for unit in UNITS)

    t.mark('unitframes-every-shipped-setting-through-real-controls')
    real_exercised = options.exercise_every_setting()
    declared = set(t.lua.eval('''function()
        local result={}
        for _,old in AF.DB:Settings() do
            local owner,key=AF.DB:Owner(old)
            if owner=='UnitFrames' then result[key]=true end
        end
        return result
    end''')().keys())
    assert declared == set(real_exercised), 'UnitFrames settings lack controls: ' + repr(declared - set(real_exercised))
    t.settings_exercised = real_exercised

    t.mark('unitframes-shipped-control-metadata-and-focused-behavior')
    # The unchanged settings already have historical exhaustive evidence. Avoid
    # repeating hundreds of page flips; current geometry/class paths run above.
    exercised = sorted(options.entries)
    declared = set(t.lua.eval('''function()
        local result={}
        for _,old in AF.DB:Settings() do
            local owner,key=AF.DB:Owner(old)
            if owner=='UnitFrames' then result[key]=true end
        end
        return result
    end''')().keys())
    assert declared == set(exercised), 'UnitFrames settings lack controls: ' + repr(declared - set(exercised))
    t.mark('unitframes-options-change-rendered-elements')
    visual_settings(t, options)
    t.click_widget('AFUIOptionsClose')

    t.mark('unitframes-unchanged-timer-refresh-does-not-mutate-widgets')
    t.ticks(25)
    with mutations(t) as changed:
        t.ticks(25)
    assert not changed, 'unchanged unit-frame refresh mutated widgets: ' + repr(changed[:8])

    t.mark('unitframes-idle-refresh-skips-painting-and-every-dirty-input-repaints')
    paints = lambda: t.lua.eval("function() return AF:Call('UnitFrames','Diagnostics').paints end")()
    t.ticks(25)
    idle = paints()
    # A target whose portrait model is unavailable legitimately repaints at its 20-tick retry deadline only.
    assert paints() - idle <= 6, 'idle refreshes repaint far beyond the portrait retry deadlines'
    portrait = t.lua.eval("function() return AF.DB:Get('UnitFrames','target.showPortrait') end")()
    t.lua.execute("AF.DB:Set('UnitFrames','target.showPortrait',0)"); t.ticks(25)
    idle = paints(); t.ticks(50)
    assert paints() == idle, 'idle refreshes still repainted every frame'
    # Identical values, changed geometry: the native dialog moves.
    player_id = int(t.g.AFCore.DIALOG.basic_status)
    moved = t.dialogs[player_id]
    left = t.widgets['AFHFPlayerBackdrop']['rect'][0]
    moved['x'] += 37; t.ticks(25)
    assert paints() > idle and t.widgets['AFHFPlayerBackdrop']['rect'][0] != left, 'a moved native frame was not redrawn'
    moved['x'] -= 37; t.ticks(25)
    assert t.widgets['AFHFPlayerBackdrop']['rect'][0] == left
    # Native shown bit flips back on with no value change: suppress still runs and nothing repaints.
    t.ticks(25); idle = paints()
    target_dialog = int(t.g.AFCore.DIALOG.target)
    flipped = [key for key, value in native_visibility(t).items()
               if key[0] == target_dialog and value != original[key]]
    assert flipped, 'no captured native target widget to flip'
    for key in flipped:
        t.dialogs[key[0]]['nodes'][key[1]]['shown'] = True
    t.ticks(25)
    assert all(not native_visibility(t)[key] for key in flipped), 'a re-shown native widget stayed visible'
    assert paints() == idle, 'suppression must not repaint the frame'
    # Rarity, then rank, change with every unit value identical.
    name = t.frame_units['target'][1]
    t.identities[name] = ('Good', 'Great'); t.ticks(110)
    crest = t.widgets['AFHFTargetCrest']; star = t.widgets['AFHFTargetStar4']
    assert crest['visible'] and crest['images'][0][-1].endswith('/crest-good.dds'), crest
    assert star['images'][0][-1].endswith('/star-on.dds'), star
    t.identities[name] = ('Excellent', 'Great'); t.ticks(110)
    assert crest['images'][0][-1].endswith('/crest-excellent.dds'), 'a rarity-only change froze the header'
    t.identities[name] = ('Excellent', 'Poor'); t.ticks(110)
    assert star['images'][0][-1].endswith('/star-off.dds'), 'a rank-only change froze the header'
    t.identities.pop(name); t.ticks(110)
    # A setting changes the profile revision.
    before, height = paints(), t.widgets['AFHFPlayerHealthBG']['rect'][3]
    t.lua.execute("AF.DB:Set('UnitFrames','player.healthHeight',24)"); t.ticks(5)
    assert paints() > before and t.widgets['AFHFPlayerHealthBG']['rect'][3] != height, 'a setting did not repaint'
    t.lua.execute("AF.DB:Set('UnitFrames','player.healthHeight',16)"); t.ticks(5)
    assert t.widgets['AFHFPlayerHealthBG']['rect'][3] == height
    # The game rebuilds the native frame (new instance): the frame is taken over and painted again.
    before = paints()
    for (ident, path), shown in original.items():  # a fresh native instance starts with its stock widgets
        if ident == player_id:
            moved['nodes'][path]['shown'] = shown
    moved['instance'] += 1; t.ticks(110)
    assert paints() > before and visible(t, 'player'), 'a rebuilt native frame was not repainted'
    t.ticks(25); idle = paints(); t.ticks(50)
    assert paints() == idle, 'idle refreshes repainted after the rebuild'
    t.lua.execute("AF.DB:Set('UnitFrames','target.showPortrait',%s)" % portrait); t.ticks(25)
    # Real profile entry point (what the Profiles window buttons call): a profile that differs only in
    # health height, with every resource identical, must repaint on the next refresh.
    t.ticks(25); height = t.widgets['AFHFPlayerHealthBG']['rect'][3]
    t.lua.execute("""
        local db=AF.DB
        local current=db:ProfileName()
        assert(db:ProfileCreate('PerfOtherLook'))
        db:Set('UnitFrames','player.healthHeight',26)
        AF:Call('Profiles','Choose',current)
    """)
    t.ticks(5)
    assert t.widgets['AFHFPlayerHealthBG']['rect'][3] == height, 'profile select did not repaint the frame'
    t.lua.execute("""
        AF:Call('Profiles','Set','source','PerfOtherLook')
        local r=AF:Call('Profiles','CopyProfile')
        assert(r.note:find('Copied'),r.note)
    """)
    t.ticks(5)
    assert t.widgets['AFHFPlayerHealthBG']['rect'][3] != height, 'profile copy did not repaint the frame'
    t.lua.execute("AF:Call('Profiles','ResetCurrent')"); t.ticks(5)
    assert t.widgets['AFHFPlayerHealthBG']['rect'][3] == height, 'profile reset did not repaint the frame'

    t.mark('unitframes-passive-dialog-does-not-capture-input')
    sys.path.insert(0, str(SOURCE))
    import prepare
    dialog = next(node for node in ET.fromstring(prepare.hud_xml()) if node.get('name') == 'AFHFrames')
    assert 'not_active' in dialog.get('flag', '').split(';')
    assert 'transparent' in dialog.get('flag', '').split(';')
    for node in dialog.iter('Widget'):
        assert 'not_active' in node.get('flag', '').split(';'), node.get('name')
        kinds = ('container',) if node.get('name') == 'AFHFParty' else ('image', 'static')
        if node.get('name').endswith('Portrait'):
            kinds = ('container',)  # Core owns the native Model child under this supported host.
        assert node.get('type') in kinds, 'unit frame gained an input control'
        assert node.find('Scripts/OnClick') is None, node.get('name')

    t.mark('unitframes-interface-disable-restores-exact-native-widgets')
    t.files['state.json'] = '{"Desired":"native","Applied":"native"}'
    t.ticks(105)
    assert all(not visible(t, unit) for unit in UNITS)
    assert_native(t, original, 'interface disabled')
    t.files['state.json'] = '{"Desired":"layout","Applied":"layout"}'
    t.ticks(105)
    assert all(visible(t, unit) for unit in UNITS)

    t.mark('unitframes-logout-relog-character-switch-restores-native-state')
    t.event('ADDON_EXIT')
    assert all(not visible(t, unit) for unit in UNITS)
    assert_native(t, original, 'logout event')
    t.character = None; t.frame_units.clear(); t.ticks(2)
    assert all(not visible(t, unit) for unit in UNITS)
    t.character = 'SecondFrameCharacter'
    t.frame_units['player'] = sample(9001, t.character, health=320)
    t.event('PLAYER_ENTERING_WORLD'); t.ticks(45)
    assert visible(t, 'player') and abs(fill_ratio(t, 'player') - .32) < .02
    assert all(not visible(t, unit) for unit in UNITS if unit != 'player')
    t.character = 'FrameCharacter'
    t.frame_units['player'] = sample(7001, t.character, health=810)
    t.event('PLAYER_ENTERING_WORLD'); t.ticks(45)
    assert visible(t, 'player') and abs(fill_ratio(t, 'player') - .81) < .02
    assert all(not visible(t, unit) for unit in UNITS if unit != 'player')
    size_ids(t)
    t.event('ADDON_EXIT'); t.character = None; t.ticks(2)
    assert_native(t, original, 'final disconnect')
    healthy(t)
    return exercised


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    client = args.client.resolve(strict=True)
    output = args.output or Path(tempfile.mkdtemp(prefix='aetherfall-unitframes-')) / 'unitframes.jsonl.gz'
    if output.resolve().is_relative_to(SOURCE) or output.resolve().is_relative_to(client):
        parser.error('Use a fresh external output outside source and client')
    if output.exists():
        parser.error('The focused trace output must be a fresh file')
    recorder = FocusedRecorder(output)
    runtime = UnitFrameRuntime(client, recorder)
    try:
        exercised = scenarios(runtime)
    finally:
        result = recorder.close()
    result['frameworkSha256'] = runtime.framework_hashes
    result['settingsWithLabeledControls'] = exercised
    result['settingsExercised'] = runtime.settings_exercised
    result['sourceSha256'] = {name: hashlib.sha256((SOURCE / name).read_bytes()).hexdigest()
                              for name in json.loads((SOURCE / 'load_order.json').read_text())
                              if (SOURCE / name).is_file()}
    output.with_suffix('.summary.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(f"PASS: UnitFrames lifecycle, native restoration, options and {result['records']} ordered records")
    print('UnitFrames trace:', output)


if __name__ == '__main__':
    main()
