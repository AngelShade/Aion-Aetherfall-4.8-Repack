"""Replay the original scenario's settings through their current owning DB.

This is test input translation, not an implementation of the replacement UI.
The monolith reference still receives its original clicks and remains immutable.
"""
import json
import re

from runtime import FIXTURES


class LegacyActions:
    def __init__(self, runtime):
        self.t = runtime
        self.tab, self.bar, self.confirm = 1, 1, None
        self.schema = json.loads((FIXTURES / 'options-schema.json').read_text())

    def call(self, owner, method, *args):
        return self.t.g.AF.Call(self.t.g.AF, owner, method, *args)

    def change(self, legacy, direction):
        db = self.t.g.AF.DB
        if legacy == 'abbar':
            self.bar = (self.bar - 1 + direction) % 4 + 1
            return
        owner = db.Owner(db, legacy)
        assert owner is not None, 'No reviewed migration for old setting: ' + legacy
        owner, key = owner
        rule = db.Rule(db, owner, key)
        value = db.GetLegacy(db, legacy)
        kind = rule[2]
        if kind == 'toggle':
            value = 1 - value
        elif kind == 'bool':
            value = not value
        elif kind in ('int', 'number'):
            value = max(rule[3], min(rule[4], value + direction * rule[5]))
        elif kind == 'choice':
            value = (value - 1 + direction) % len(rule[3]) + 1
        elif kind == 'enum':
            choices = list(rule[3].values())
            value = choices[(choices.index(value) + direction) % len(choices)]
        else:
            raise AssertionError('Unmapped old rule kind ' + str(kind))
        assert db.Set(db, owner, key, value) is not False or owner == 'Camera'
        if legacy == 'sepbar':
            # The old combined control intentionally becomes two independent
            # real writes. Their native consequences remain in the raw trace.
            assert db.Set(db, 'ActionBars', 'bar6.enabled', value) is not False
        elif legacy in ('ttx', 'tty'):
            # Old cursor offsets controlled both tooltip policies. The new
            # world/UI settings are independent, so replay both real writes.
            assert db.Set(db, 'Tooltip', 'ttuix' if legacy == 'ttx' else 'ttuiy', value) is not False

    def click(self, name):
        if name.startswith('Tab'):
            self.tab = int(name[3:])
            self.confirm = None
            return
        if name == 'Close':
            self.call('OptionsWindow', 'Hide')
            return
        match = re.fullmatch(r'Row(\d+)(Value|Less|More)', name)
        assert match, 'Unmapped old click ' + name
        row, part = int(match[1]), match[2]
        spec = next(item for item in self.schema[self.tab - 1]['rows'] if item['row'] == row)
        if spec['kind'] == 'action':
            action = spec['action']
            owners = {'alignmap': 'Minimap', 'alignmenu': 'Shortcuts',
                      'resetall': 'EditorRender', 'unlock': 'EditorRender' if self.tab == 10 else 'Tooltip'}
            owner = owners.get(action, 'Profiles')
            result = self.call(owner, 'OnAction', action, spec.get('arg'), self.confirm == action)
            self.confirm = action if result is not None and result['confirm'] else None
            return
        self.confirm = None
        if spec['kind'] == 'profile':
            self.call('Profiles', 'SelectProfile', -1 if part == 'Less' else 1)
            return
        key = spec.get('k') or (spec['base'] + str(self.bar))
        self.change(key, -1 if part == 'Less' else 1)
