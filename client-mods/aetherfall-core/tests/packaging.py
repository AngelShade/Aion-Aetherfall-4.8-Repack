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


if __name__ == '__main__':
    unittest.main()
