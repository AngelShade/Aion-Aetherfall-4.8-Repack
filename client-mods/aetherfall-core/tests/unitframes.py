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


def sample(ident, name, health=750, maximum=1000, power=180, power_max=300, level=65):
    return (ident, name, level, health, maximum, power, power_max)


class UnitFrameRuntime(Runtime):
    """Opt-in fixtures; the immutable monolith's Runtime defaults stay intact."""

    def __init__(self, client, recorder, **kwargs):
        self.frame_units = {}
        self.native_types = {}
        self.drop_target_hides = False
        self.dp = (1200, 4000)
        super().__init__(client, recorder, current_resources=True, **kwargs)
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
        options.set(group + '.font', 1)
        assert t.widgets[prefix + 'HealthTextLeft']['font'][0] == 'ui_default'
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


def scenarios(t):
    t.mark('unitframes-cold-disconnected-and-player-only-login')
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

    t.mark('unitframes-target-and-targettarget-change-and-clear')
    t.frame_units['target'] = sample(7110, 'ChangedTarget', health=300)
    t.frame_units['targettarget'] = sample(7111, 'ChangedVictim', health=110)
    t.event('USER_CHANGED_TARGET'); t.ticks(5)
    assert 'ChangedTarget' in text(t, 'target', 'HealthTextLeft')
    assert 'ChangedVictim' in text(t, 'targettarget', 'HealthTextLeft')
    assert abs(fill_ratio(t, 'target') - .3) < .02
    assert abs(fill_ratio(t, 'targettarget') - .11) < .02
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
    # Return this slot to a human before exact native fallback checks. Identity
    # legitimately substitutes native bot names while custom frames are off.
    t.identities.pop('NativeBot')
    t.event('PARTY_MEMBERS_CHANGED'); t.ticks(5)
    assert not text(t, 'party3', 'Identity')

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
    exercised = options.exercise_every_setting()
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

    t.mark('unitframes-passive-dialog-does-not-capture-input')
    sys.path.insert(0, str(SOURCE))
    import prepare
    dialog = next(node for node in ET.fromstring(prepare.hud_xml()) if node.get('name') == 'AFHFrames')
    assert 'not_active' in dialog.get('flag', '').split(';')
    assert 'transparent' in dialog.get('flag', '').split(';')
    for node in dialog.findall('Widget'):
        assert 'not_active' in node.get('flag', '').split(';'), node.get('name')
        assert node.get('type') in ('image', 'static'), 'unit frame gained an input control'
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
    recorder = Recorder(output)
    runtime = UnitFrameRuntime(client, recorder)
    try:
        exercised = scenarios(runtime)
    finally:
        result = recorder.close()
    result['frameworkSha256'] = runtime.framework_hashes
    result['settingsExercised'] = exercised
    result['sourceSha256'] = {name: hashlib.sha256((SOURCE / name).read_bytes()).hexdigest()
                              for name in json.loads((SOURCE / 'load_order.json').read_text())
                              if (SOURCE / name).is_file()}
    output.with_suffix('.summary.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(f"PASS: UnitFrames lifecycle, native restoration, options and {result['records']} ordered records")
    print('UnitFrames trace:', output)


if __name__ == '__main__':
    main()
