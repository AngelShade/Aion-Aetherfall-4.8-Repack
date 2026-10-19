"""Build/sign the Aetherfall Lua HUD against the original installed addon archive."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as E

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE.parent / 'quest-red-journal'))
from prepare import extend_archive, parts, read_pak

PAK = 'Plugin/RelicCalc/RelicCalc.pak'
COHORT = [PAK, PAK + '.sig', 'Addon.key', 'bin32/bin32.pak.sig', 'Data/func_pet/func_pet.pak.sig']
PRESERVE = ['Pub.key', 'bin32/bin32.pak', 'Data/func_pet/func_pet.pak', 'bin64/Game.dll',
            'bin64/crysystem.dll', 'bin64/AionIconBridge.dll', 'bin64/AionIconBridge.index',
            'Data/ui/game/game.pak', 'L10N/enu/data/data.pak', 'Data/Quest/Quest.pak',
            'Textures/ui/ui.pak', 'Aetherfall Launcher.exe', 'Mods/GraphicsMenu/installed.json',
            'Mods/GraphicsMenu/package/manifest.json', 'Mods/CustomQuests/installed.json',
            'Mods/AetherfallUI/manifest.json', 'Mods/AetherfallUI/state.json']


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def xml():
    root = E.Element('Screen')

    def dialog(name, width, height):
        return E.SubElement(root, 'Dialog', name='AUI'+name, frame=f'0,0,{width},{height}',
                            preset='v5_dialog', flag='title;not_visible;not_movable',
                            title_height='22', title_offset='8,3')

    def widget(parent, kind, name, rect, preset=None, click=None):
        node = E.SubElement(parent, 'Widget', type=kind, name='AUI'+name, frame=rect, font='ui_default')
        if preset:
            node.set('preset', preset)
        if kind == 'button':
            node.set('style', 'pushbutton')
        else:
            node.set('flag', 'not_active')
        if click:
            E.SubElement(E.SubElement(node, 'Scripts'), 'OnClick').text = click
        return node

    for name in ['Player', 'Target'] + [f'Party{i}' for i in range(1, 6)]:
        width, height = (230, 92) if name.startswith('Party') else (260, 98)
        d = dialog(name, width, height)
        widget(d, 'button', name+'Name', f'6,0,{width-24},20', click=f"Aetherfall_Target('{name}');")
        widget(d, 'gauge', name+'HP', f'8,23,{width-28},17', 'v5_hp_gauge_party')
        widget(d, 'static', name+'Health', f'10,22,{width-32},19')
        widget(d, 'gauge', name+'MP', f'8,44,{width-28},12', 'v5_mp_gauge_party')
        widget(d, 'static', name+'Power', f'10,41,{width-32},19')
    for name in ['Top', 'Bottom']:
        d = dialog(name, 560, 64)
        widget(d, 'static', name+'Text', '8,3,536,28')
    d = dialog('Cast', 320, 70)
    widget(d, 'gauge', 'CastGauge', '8,8,296,20', 'v5_gauge_charge')
    widget(d, 'static', 'CastText', '10,8,290,24')
    d = dialog('Options', 600, 400)
    d.set('flag', 'title;close;not_visible')
    d.set('close_preset', 'v5_close')
    d.set('close_offset', '4,1')
    d.set('text', 'Aetherfall Interface')
    scripts = E.SubElement(d, 'Scripts')
    E.SubElement(scripts, 'OnLoad').text = 'Aetherfall_OnLoad();'
    E.SubElement(scripts, 'OnEvent').text = 'Aetherfall_OnEvent(event, ...);'
    for i, (name, title) in enumerate([('General', 'General'), ('UnitFrames', 'Unit Frames'), ('DataTexts', 'Data Texts'), ('Movers', 'Movers'), ('Profiles', 'Profiles')]):
        b = widget(d, 'button', 'Tab'+name, f'{8+i*114},8,110,28', 'v5_button', f"Aetherfall_Click('Tab{name}');")
        b.set('text', title)
    widget(d, 'static', 'OptionsInfo', '12,48,560,104')
    for names in [('Enabled','CastEnabled','Finish'), ('PlayerEnabled','TargetEnabled','PartyEnabled','Percent'),
                  ('DataEnabled','ResetCombat'), ('Unlock','Snap','ResetPositions'), ('DPS','Healer','Minimal')]:
        for i, name in enumerate(names):
            widget(d, 'button', name, f'12,{158+i*38},330,30', 'v5_button', f"Aetherfall_Click('{name}');")
    widget(d, 'static', 'OptionsStatus', '12,326,560,34')
    root.set('widget_cnt', str(sum(1 for n in root.iter() if n.tag in ('Dialog', 'Widget'))))
    E.indent(root, '  ')
    return E.tostring(root, encoding='utf-8', xml_declaration=True)


def build(client, output):
    if output.exists() or client == output or client in output.parents or ROOT == output or ROOT in output.parents:
        raise ValueError('Choose a fresh output folder outside source and runtime')
    # Refresh only the affected cohort and its shared signed/native dependencies.
    before = {rel: sha(client/rel) for rel in COHORT+PRESERVE}
    changes = {'Aetherfall.lua': (HERE/'Aetherfall.lua').read_bytes(), 'Aetherfall.xml': xml()}
    with read_pak(client/PAK) as old:
        toc = old.read('RelicCalc.toc').decode('utf-8-sig').replace('\r', '').splitlines()
        toc = [n for n in toc if n not in changes]
        toc += list(changes)
        changes['RelicCalc.toc'] = ('\r\n'.join(toc)+'\r\n').encode('utf-8')
        replacements = {n: data for n, data in changes.items() if n in old.namelist()}
        additions = {n: data for n, data in changes.items() if n not in old.namelist()}
        original = (client/PAK).read_bytes()
        payload = extend_archive(original, replacements, additions)
        target = output/PAK
        target.parent.mkdir(parents=True)
        target.write_bytes(payload)
        with read_pak(target) as new:
            assert new.testzip() is None
            for name, data in changes.items():
                assert new.read(name) == data
            old_parts, new_parts = parts(original)[0], parts(payload)[0]
            for name in old.namelist():
                if name not in changes:
                    assert old_parts[name][0] == new_parts[name][0], name
    subprocess.run(['java', str(HERE.parent/'transmog-menu/SignClientPackages.java'), str(client), str(output)], check=True)
    assert sha(output/'Pub.key') == before['Pub.key']
    (output/'Pub.key').unlink()  # The stock model key is verified and never deployed.
    subprocess.run(['java', str(HERE.parent/'season-pass/VerifySignatures.java'), str(output), str(client)], check=True)
    for rel, expected in before.items():
        assert sha(client/rel) == expected, f'Client changed during build: {rel}'
    manifest = dict(feature='aetherfall-hud', version='1.0.0', clientRoot=str(client),
                    files=[dict(path=rel, original=before[rel], staged=sha(output/rel)) for rel in COHORT],
                    preserved={rel: before[rel] for rel in PRESERVE}, archiveChanges=list(changes),
                    sources={p.name: sha(p) for p in [Path(__file__), HERE/'Aetherfall.lua']},
                    limitations=['Cast duration is not exposed: elapsed activity only',
                                 'DPS/HPS cannot be reliably attributed from these events',
                                 'Native HUD replacement/layout work is step 6, outside this delivery'])
    (output/'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print(f'Built/signed five-file HUD cohort; untouched compressed addon members preserved: {output}')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    build(a.client.resolve(), a.output.resolve())
