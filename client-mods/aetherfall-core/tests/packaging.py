"""Synthetic manifest/TOC/PAK migration checks; never reads or changes a client."""
import importlib.util
import io
import json
from pathlib import Path
import sys
import struct
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
from lua_package import (GENERATED, HUD_XML, REGISTRY, load_order, lua_payloads,
                         reconcile_toc, remove_members, retired_members)
import prepare
import options_skin
from layout import unitframes

spec = importlib.util.spec_from_file_location('packaging_archive_helper', HERE.parent / 'quest-red-journal/prepare.py')
archive_helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(archive_helper)


class Packaging(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.sources = ['AetherfallCore.lua', 'AetherfallLayout.lua', 'AetherfallLazy.lua',
                        'core/Engine.lua', 'modules/Placement.lua', 'compat/Compat.lua']
        for name in self.sources:
            if name not in GENERATED:
                path = self.root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(('-- ' + name).encode())
        self.manifest(self.sources)

    def manifest(self, rows):
        (self.root / 'load_order.json').write_text(json.dumps(rows), encoding='utf-8')

    def encrypted_archive(self, entries, version):
        raw = io.BytesIO()
        with zipfile.ZipFile(raw, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            archive.comment = b'preserved synthetic archive comment'
            for name, data in entries.items():
                archive.writestr(name, data)
        return archive_helper.encrypted_zip(raw.getvalue(), version)

    def open_archive(self, data):
        path = self.root / 'synthetic.pak'
        path.write_bytes(data)
        return archive_helper.read_pak(path)

    def test_manifest_flattens_and_rejects_unsafe_or_ambiguous_names(self):
        self.assertEqual(load_order(self.root)[3], ('core/Engine.lua', 'core_Engine.lua'))
        (self.root / 'core_Engine.lua').write_text('-- collision', encoding='utf-8')
        invalid = [[], {}, ['AetherfallCore.lua', 5], self.sources + ['core/Engine.lua'],
                   self.sources + ['core_Engine.lua'], self.sources + ['CORE/Engine.lua'],
                   self.sources + ['../escape.lua'], self.sources + ['/absolute.lua'],
                   self.sources + ['C:/absolute.lua'], self.sources + ['core\\Engine.lua'],
                   self.sources + ['core/./Engine.lua'], self.sources + ['core//Engine.lua'],
                   self.sources + ['core/Injected\nEntry.lua'], self.sources + [' core/Engine.lua'],
                   self.sources + ['AetherfallHUD.lua'], self.sources + ['aetherfalloptions.lua'],
                   self.sources + [REGISTRY], self.sources + ['missing.lua'],
                   self.sources[1:]]
        for rows in invalid:
            with self.subTest(rows=rows), self.assertRaises(ValueError):
                self.manifest(rows)
                load_order(self.root)

    def test_toc_migration_keeps_unrelated_entries_and_generated_registry_order(self):
        members = [member for _, member in load_order(self.root)]
        old = ['; owner comment', 'WardrobeNative.lua', 'AetherfallCore.lua', REGISTRY,
               'AetherfallLayout.lua', 'AetherfallLazy.lua', 'CodexNative.lua',
               'AetherfallHUD.lua', 'AetherfallOptions.lua', HUD_XML, 'PrivateMenus.xml']
        result = reconcile_toc(old, members)
        desired = members.copy()
        desired.insert(1, REGISTRY)
        self.assertEqual(result, old[:2] + desired + ['CodexNative.lua', HUD_XML, 'PrivateMenus.xml'])
        self.assertEqual(reconcile_toc(result, members), result)
        self.assertEqual(reconcile_toc(['Other.lua'], members), ['Other.lua'] + members + [HUD_XML])

    def test_retained_and_full_migration_preserve_unrelated_compressed_members(self):
        order = load_order(self.root)
        members = [member for _, member in order]
        for version in (1, 2):
            for retained in (False, True):
                with self.subTest(version=version, retained=retained):
                    old_toc = ['WardrobeNative.lua', 'AetherfallCore.lua', REGISTRY,
                               'AetherfallLayout.lua', 'AetherfallLazy.lua', 'CodexNative.lua',
                               'AetherfallHUD.lua', 'AetherfallOptions.lua', HUD_XML, 'Other.xml']
                    entries = {'RelicCalc.toc': ('\r\n'.join(old_toc) + '\r\n').encode(),
                               'WardrobeNative.lua': b'-- installed shared timer owner',
                               'Other.xml': b'<Screen/>', 'Texture.dds': bytes(range(256)) * 7,
                               'CodexNative.lua': b'-- installed Codex controller',
                               'AetherfallCore.lua': b'-- old bridge', REGISTRY: b'-- installed registry',
                               'AetherfallLayout.lua': b'-- installed native positions',
                               'AetherfallLazy.lua': b'-- installed lazy windows',
                               'AetherfallHUD.lua': b'-- retired HUD',
                               'AetherfallOptions.lua': b'-- retired options',
                               HUD_XML: b'<Screen widget_cnt="1"><Dialog name="AFHTop"/></Screen>'}
                    source = self.encrypted_archive(entries, version)
                    with self.open_archive(source) as old:
                        changes = lua_payloads(self.root, order, installed=old if retained else None)
                        if not retained:
                            for name in GENERATED:
                                changes[name] = ('-- freshly generated ' + name).encode()
                        changes['RelicCalc.toc'] = ('\r\n'.join(reconcile_toc(old_toc, members)) + '\r\n').encode()
                        changes = {n: d for n, d in changes.items() if n not in entries or entries[n] != d}
                        replacements = {n: d for n, d in changes.items() if n in entries}
                        additions = {n: d for n, d in changes.items() if n not in entries}
                        retired = retired_members(old.namelist())
                    payload = archive_helper.extend_archive(source, replacements, additions)
                    payload = remove_members(payload, retired, archive_helper.parts)
                    before = archive_helper.parts(source)[0]
                    after, _, end, actual_version = archive_helper.parts(payload)
                    self.assertEqual(actual_version, version)
                    self.assertTrue(end.endswith(b'preserved synthetic archive comment'))
                    with self.open_archive(payload) as new:
                        self.assertIsNone(new.testzip())
                        self.assertFalse(retired_members(new.namelist()))
                        self.assertTrue(set(members) <= set(new.namelist()))
                        for name, data in changes.items():
                            self.assertEqual(new.read(name), data)
                        self.assertEqual(new.read(HUD_XML), entries[HUD_XML])
                        for name in entries:
                            if name not in changes and name not in retired:
                                self.assertEqual(before[name][0], after[name][0], name)
                        if retained:
                            for name in GENERATED:
                                self.assertEqual(new.read(name), entries[name])
                    self.assertEqual(remove_members(payload, set(), archive_helper.parts), payload)

    def test_unknown_removals_fail(self):
        source = self.encrypted_archive({'Keep.lua': b'-- keep'}, 1)
        with self.assertRaises(ValueError):
            remove_members(source, {'missing.lua'}, archive_helper.parts)

    def test_options_layout_migration_is_idempotent_and_lossless(self):
        before = (b'<Screen widget_cnt="6">\n<!-- owner spacing -->\n'
                  b'<Dialog name="OwnerWindow" frame="1,2,3,4"><Widget type="static" name="Keep" text="A &amp; B"/></Dialog>\n'
                  b'<Dialog name="AFUIOptions" frame="0,0,1,1"><Widget type="static" name="Old"/></Dialog>\n'
                  b'<Dialog name="AFUIMovers" frame="0,0,10,10"/>\n</Screen>')
        expected_owner = before.split(b'<Dialog name="OwnerWindow"')[1].split(b'</Dialog>')[0]
        result = prepare.update_options_xml(before)
        self.assertEqual(result.count(b'name="AFUIOptions"'), 1)
        self.assertNotIn(b'name="Old"', result)
        self.assertLess(result.index(b'name="AFUIOptions"'), result.index(b'name="AFUIMovers"'))
        self.assertIn(expected_owner, result)
        self.assertIn(b'<Dialog name="AFUIMovers" frame="0,0,10,10"/>', result)
        self.assertIn(b'<!-- owner spacing -->', result)
        self.assertEqual(prepare.update_options_xml(result), result)
        root = ET.fromstring(result)
        self.assertEqual(int(root.attrib['widget_cnt']), sum(1 for n in root.iter() if n.tag in ('Dialog', 'Widget')))
        absent = before.replace(b'<Dialog name="AFUIOptions" frame="0,0,1,1"><Widget type="static" name="Old"/></Dialog>\n', b'')
        installed = prepare.update_options_xml(absent)
        self.assertLess(installed.index(b'name="AFUIOptions"'), installed.index(b'name="AFUIMovers"'))
        with self.assertRaises(ValueError):
            prepare.update_options_xml(before.replace(b'</Screen>', b'<Dialog name="AFUIOptions"/></Screen>'))
        eager, lazy = ET.fromstring(prepare.hud_xml()), ET.fromstring(prepare.lazy_xml())
        # A dialog created after startup is drawn but never receives input in this client.
        self.assertIn('AFUIOptions', [node.attrib['name'] for node in eager])
        self.assertIn('AFUIMovers', [node.attrib['name'] for node in eager])
        self.assertEqual([node.attrib['name'] for node in lazy], [])
        self.assertNotIn(b"{'AFUIOptions',", prepare.lazy_lua())
        members = [member for _, member in load_order(HERE)]
        self.assertEqual(members.count('AetherfallLazy.lua'), 1)
        self.assertLess(members.index('AetherfallLazy.lua'), members.index('modules_OptionsWindow.lua'))

    def test_options_xml_uses_only_proven_plugin_vocabulary(self):
        root = next(d for d in ET.fromstring(prepare.hud_xml()) if d.attrib['name'] == 'AFUIOptions')
        widget_attrs = {'type','style','name','frame','preset','flag','text','font','tooltip','HAlign'}
        dialog_attrs = {'name','frame','text','preset','flag','title_height','title_offset',
                        'close_preset','close_offset','shadow_offset'}
        kinds = {'static','image','button','editbox','combobox','spin','container','scrollable','htmlview'}
        names = set()
        for node in root.iter():
            if node.tag == 'Widget':
                self.assertTrue(set(node.attrib) <= widget_attrs, node.attrib)
                self.assertIn(node.attrib['type'], kinds)
                self.assertTrue(node.attrib['name'].startswith('AFUIOptions'))
                self.assertNotIn(node.attrib['name'], names)
                names.add(node.attrib['name'])
            elif node.tag == 'Dialog':
                self.assertTrue(set(node.attrib) <= dialog_attrs, node.attrib)
            else:
                self.assertIn(node.tag, {'Screen','Scripts','OnLoad','OnClick','OnEvent'})
        self.assertGreater(len(names), 100)

    def test_skin_textures_are_valid_palette_dds_and_include_control_states(self):
        assets = options_skin.assets()
        prefix = options_skin.PREFIX + '/'
        for suffix in ('button','button-over','button-down','button-selected','button-disabled',
                       'check-on','check-off','fill-deep','fill-aether','line','line-aether','thumb'):
            self.assertIn(prefix + suffix + '.dds', assets)
        for name, payload in assets.items():
            self.assertTrue(name.startswith(prefix) and name.endswith('.dds'))
            self.assertEqual(payload[:4], b'DDS ')
            header = struct.unpack('<31I', payload[4:128])
            height, width = header[2], header[3]
            self.assertGreater(width, 0); self.assertGreater(height, 0)
            self.assertEqual(len(payload), 128 + width * height * 4)
        deep = assets[prefix + 'fill-deep.dds']
        r, g, b = options_skin.rgb('deep')
        self.assertEqual(deep[128:132], bytes((b, g, r, 255)))
        self.assertNotEqual(assets[prefix + 'check-on.dds'], assets[prefix + 'check-off.dds'])

    def test_unitframes_startup_pool_is_passive_flat_and_bounded(self):
        eager, lazy = ET.fromstring(prepare.hud_xml()), ET.fromstring(prepare.lazy_xml())
        dialogs = [node for node in eager if node.get('name') == 'AFHFrames']
        self.assertEqual(len(dialogs), 1)
        self.assertFalse(any(node.get('name') == 'AFHFrames' for node in lazy))
        root = dialogs[0]
        # The attributes of the 1.2.94 bot label window, which drew over native frames in game.
        self.assertEqual(root.attrib, dict(name='AFHFrames', frame='0,0,3840,2160',
                         flag='transparent;not_visible;not_movable;not_active'))
        names = {node.get('name') for node in root}
        self.assertEqual(len(root), 9 * 38)
        self.assertEqual(len(names), len(root))
        frames = ('Player', 'Target', 'TargetTarget') + tuple(f'Party{i}' for i in range(1, 7))
        parts = {'Backdrop', 'HealthBG', 'HealthFill', 'PowerBG', 'PowerFill', 'DPBG', 'DPFill',
                 'HealthTextLeft', 'HealthTextCentre', 'HealthTextRight',
                 'PowerTextLeft', 'PowerTextCentre', 'PowerTextRight', 'Level', 'Identity', 'State', 'DPTextCentre',
                 'Star1', 'Star2', 'Star3', 'Star4', 'Star5'}
        parts.update(part + 'Edge' + edge for part in ('Backdrop', 'HealthBG', 'PowerBG', 'DPBG')
                     for edge in ('Top', 'Bottom', 'Left', 'Right'))
        self.assertEqual(names, {'AFHF' + frame + part for frame in frames for part in parts})
        for node in root:
            self.assertEqual(node.tag, 'Widget')
            self.assertEqual(len(node), 0, 'all controls must be direct dialog children')
            self.assertIn(node.get('type'), ('image', 'static'))
            self.assertTrue({'not_active', 'not_visible'} <= set(node.get('flag').split(';')))
            self.assertEqual(node.get('frame'), '0,0,1,1')
        self.assertEqual(prepare.BUDGET, 6)
        self.assertEqual(len(eager), prepare.BUDGET)

    def test_unitframes_retained_migrations_are_idempotent_and_preserve_other_dialog_bytes(self):
        owner = (b'<Dialog name="OwnerWindow" frame="1,2,3,4">\n '
                 b'<Widget type="static" name="Keep" text="A &amp; B"/>\n</Dialog>')
        target = b'<Dialog name="AFHTarget" frame="5,6,7,8"><Widget type="static" name="NativeReadout"/></Dialog>'
        before = (b'<Screen widget_cnt="9">\n<!-- owner spacing -->\n' + owner + b'\n' + target + b'\n'
                  b'<Dialog name="AFUIOptions" frame="0,0,1,1"><Widget type="static" name="OldOptions"/></Dialog>\n'
                  b'<Dialog name="AFUIMovers" frame="0,0,1,1"><Widget type="image" name="AFUIEditorPanel"/></Dialog>\n</Screen>')
        for options in (False, True):
            for movers in (False, True):
                with self.subTest(options=options, movers=movers):
                    result = prepare.update_retained_ui_xml(before, options=options, movers=movers)
                    self.assertIn(owner, result)
                    self.assertIn(target, result)
                    self.assertIn(b'<!-- owner spacing -->', result)
                    self.assertEqual(result.count(b'name="AFHFrames"'), 1)
                    self.assertEqual(prepare.update_retained_ui_xml(result, options=options, movers=movers), result)
                    root = ET.fromstring(result)
                    self.assertEqual(int(root.get('widget_cnt')),
                                     sum(1 for node in root.iter() if node.tag in ('Dialog', 'Widget')))
                    if not options:
                        self.assertIn(b'<Dialog name="AFUIOptions" frame="0,0,1,1"><Widget type="static" name="OldOptions"/></Dialog>', result)
                    if not movers:
                        self.assertIn(b'<Dialog name="AFUIMovers" frame="0,0,1,1"><Widget type="image" name="AFUIEditorPanel"/></Dialog>', result)
        stale = before.replace(b'</Screen>', b'<Dialog name="AFHFrames"><Widget name="OldFrame"/></Dialog></Screen>')
        repaired = unitframes.update_xml(stale)
        self.assertNotIn(b'OldFrame', repaired)
        self.assertIn(owner, repaired)
        self.assertEqual(unitframes.update_xml(repaired), repaired)
        for malformed in (stale.replace(b'</Screen>', b'<Dialog name="AFHFrames"/></Screen>'),
                          before.replace(b' widget_cnt="9"', b'')):
            with self.assertRaises(ValueError):
                unitframes.update_xml(malformed)

    def test_unitframes_palette_matches_runtime_rgb444_names_and_channels(self):
        assets = unitframes.assets()
        stars = {f'{unitframes.PREFIX}/star-{state}.dds' for state in ('on', 'off')}
        self.assertTrue(stars <= set(assets))
        for name in stars:
            self.assertEqual(struct.unpack('<31I', assets.pop(name)[4:128])[2:4], (32, 32))
        self.assertEqual(len(assets), 4096)
        self.assertEqual(sum(map(len, assets.values())), 4096 * (128 + 4 * 4 * 4))
        for value in range(4096):
            payload = assets[f'{unitframes.PREFIX}/solid-{value:03x}.dds']
            self.assertEqual(payload[:4], b'DDS ')
            header = struct.unpack('<31I', payload[4:128])
            self.assertEqual((header[2], header[3]), (4, 4))
            self.assertEqual(payload[128:], bytes(((value & 15) * 17, ((value >> 4) & 15) * 17,
                                                  (value >> 8) * 17, 255)) * 16)

    def test_unitframes_archive_migration_keeps_unrelated_compressed_members(self):
        xml = (b'<Screen widget_cnt="4"><Dialog name="AFHTop"><Widget name="Owner"/></Dialog>'
               b'<Dialog name="AFHTarget"><Widget name="NativeReadout"/></Dialog></Screen>')
        for version in (1, 2):
            with self.subTest(version=version):
                entries = {HUD_XML: xml, 'PrivateMenus.xml': b'<Screen/>',
                           'WardrobeNative.lua': b'-- installed shared timer',
                           'OwnerTexture.dds': bytes(range(256)) * 7}
                source = self.encrypted_archive(entries, version)
                changed = prepare.update_retained_ui_xml(xml)
                payload = archive_helper.extend_archive(source, {HUD_XML: changed}, {})
                before = archive_helper.parts(source)[0]
                after, _, end, actual_version = archive_helper.parts(payload)
                self.assertEqual(actual_version, version)
                self.assertTrue(end.endswith(b'preserved synthetic archive comment'))
                for name in entries.keys() - {HUD_XML}:
                    self.assertEqual(before[name][0], after[name][0], name)
                with self.open_archive(payload) as new:
                    self.assertIsNone(new.testzip())
                    self.assertEqual(new.read(HUD_XML), changed)
                    self.assertEqual(prepare.update_retained_ui_xml(new.read(HUD_XML)), changed)


if __name__ == '__main__':
    unittest.main()
