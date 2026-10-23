"""Stage the Aetherfall Boons client text for the Profile window's Account tab.

Rewrites three archive members and preserves every other member byte for byte:
- L10N/enu/Data/data.pak  strings/client_strings_bm.xml  (account type, pack names and details)
- Data/USA/BM/BM.pak and Data/BM/BM.pak  client_bm_pack.xml  (packs 1-5 lose NCSoft's client-side
  unrestrict/bonus effects, so the tab only describes what the server grants)
"""
import argparse, json, os, sys, tempfile
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'client-mods/expanded-warehouse'))
sys.path.insert(0, str(ROOT / 'client-mods/bundle-opening'))
from codec import binary_xml, encode_binary_xml, read_pak  # noqa: E402
from prepare_client import parts, rebuild, sha  # noqa: E402

STRINGS = ('L10N/enu/Data/data.pak', 'strings/client_strings_bm.xml')
PACKS = [('Data/USA/BM/BM.pak', 'client_bm_pack.xml'), ('Data/BM/BM.pak', 'client_bm_pack.xml')]
ACCOUNT_TYPE = 'Aetherfall Daeva'
BOON_PACKS = range(1, 6)
# Client format strings: a literal percent sign is written %%.
TEXT = {
    'STR_F2P_PACK_PACK_1': "Shugo Trade Charter",
    'STR_F2P_PACK_PACK_1_DESC': "Earned with the Season Pass Premium tier, paid in Kinah. Until the season ends: +30%% XP from hunting, groups, quests, "
                                "gathering and crafting, +30%% gathering and crafting skill XP, 84.5%% of every Central Market sale returned instead of 65%%, "
                                "and Wardrobe appearance unlocks without a ticket.",
    'STR_F2P_PACK_PACK_2': "Grace of the Tower",
    'STR_F2P_PACK_PACK_2_DESC': "Earned with the Season Pass Advanced Premium tier. Until the season ends: +20%% item drops, Energy of Repose refills in "
                                "24 hours offline instead of 48, and +2,000 VT Central Market warehouse capacity.",
    'STR_F2P_PACK_PACK_3': "Aetherfall Renown",
    'STR_F2P_PACK_PACK_3_DESC': "Account Fame combines Combat (character levels from 10; 55+ counts double, 65 five times), Life "
                                "(gathering and crafting skills from 100: one fame per five skill points), and Legend "
                                "(one fame per ten completed quests and five per title). Daily mail pays 100 Kinah per fame point, "
                                "capped at 500,000 Combat, 250,000 Life and 250,000 Legend. Combat steps grant +10/20/30/40/50%% Abyss Points "
                                "at 200/500/1,000/2,000/3,000 fame. Life steps grant +50/100/200/300/500%% crafting skill XP and "
                                "+0/100/100/200/200%% gathering skill XP at 50/100/200/400/800 fame. Legend steps raise the Repose XP bonus "
                                "to 50/60/70/80/100%% at 50/100/200/300/400 fame. At 1,000 total fame, Central Market sales return "
                                "5%% more; at 2,500: +10%% item drops. Open Account Window for your current points and steps.",
    'STR_F2P_PACK_PACK_4': "Chronicle of Atreia",
    'STR_F2P_PACK_PACK_4_DESC': "Permanent account stats for each chapter written by any of your characters: the Ascension ceremony, entering the Abyss, "
                                "The Unfinished Entry, and 300 and 800 completed quests. Each chapter: +3%% max HP and MP, +2%% physical attack, "
                                "+20 magic boost, +3%% physical defence and +10 magic resist.",
    'STR_F2P_PACK_PACK_5': "Daeva Veteran's Coffer",
    'STR_F2P_PACK_PACK_5_DESC': "A level 65 Daeva on your account receives a coffer by mail for every month of account age: expansion tickets, "
                                "enchantment stones, crafting charms, mounts and more. The timer shows when the next coffer is due.",
}
ACCOUNT_STRINGS = ['STR_F2P_ACCOUNT_FULLSUBSCRIPTION', 'STR_F2P_ACCOUNT_TRIAL', 'STR_F2P_ACCOUNT_FREE', 'STR_F2P_ACCOUNT_BRONZE', 'STR_F2P_ACCOUNT_SILVER']


def member(raw, name):
    z = read_pak_bytes(raw)
    return z.read(name), {i.filename: i for i in z.infolist()}


def read_pak_bytes(raw):
    fd, path = tempfile.mkstemp(suffix='.pak')
    try:
        os.write(fd, raw)
        os.close(fd)
        z = read_pak(path)
        return z
    finally:
        os.remove(path)


def strings(payload):
    root = binary_xml(payload)
    wanted = dict(TEXT, **{name: ACCOUNT_TYPE for name in ACCOUNT_STRINGS})
    seen = set()
    for node in root:
        name = node.findtext('name')
        if name in wanted:
            node.find('body').text = wanted[name]
            seen.add(name)
    missing = set(wanted) - seen
    if missing:
        raise SystemExit('Missing client strings: ' + ', '.join(sorted(missing)))
    return encode_binary_xml(root)


def packs(payload):
    root = binary_xml(payload)
    found = set()
    for pack in root:
        pid = int(pack.findtext('id'))
        if pid in BOON_PACKS:
            for child in list(pack):
                if child.tag.startswith(('unrestrict', 'bonus')):
                    pack.remove(child)
            found.add(pid)
    if found != set(BOON_PACKS):
        raise SystemExit('Pack table lacks packs 1-5')
    return encode_binary_xml(root)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--client', required=True, type=Path)
    ap.add_argument('--output', required=True, type=Path)
    a = ap.parse_args()
    client, out = a.client.resolve(), a.output.resolve()
    if out.exists():
        raise SystemExit('Use a fresh external output directory')
    if ROOT in out.parents or client in out.parents:
        raise SystemExit('Output must be outside the source and the client')
    files = []
    for archive, name, change in [(*STRINGS, strings)] + [(p, n, packs) for p, n in PACKS]:
        raw = (client / archive).read_bytes()
        payload, infos = member(raw, name)
        new = change(payload)
        if binary_xml(new) is None:
            raise SystemExit('Encoding failed')
        built, _ = rebuild(raw, {name: new}, infos)
        old_parts, new_parts = parts(raw)[0], parts(built)[0]
        if list(old_parts) != list(new_parts) or any(old_parts[k][0] != new_parts[k][0] for k in old_parts if k != name):
            raise SystemExit('Unrelated members changed in ' + archive)
        check, _ = member(built, name)
        if check != new:
            raise SystemExit('Rebuilt member differs in ' + archive)
        target = out / 'files' / archive
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(built)
        files.append(dict(path=archive, member=name, original=sha(raw), installed=sha(built)))
        print(f'{archive}: {name} rewritten; {len(old_parts) - 1} other members preserved')
    (out / 'manifest.json').write_text(json.dumps(dict(feature='aetherfall-boons', client=str(client), files=files), indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
