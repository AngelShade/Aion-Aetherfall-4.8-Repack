"""Generate read-only quest/nameplate metadata from the exact active 4.8 client.

Counts come from native journal variables at runtime. Only the authored quotas
and attack-cursor NPC identities are extracted here; no player state is saved.
"""
import hashlib
import json
import re
import struct
from pathlib import Path
from html.parser import HTMLParser
import xml.etree.ElementTree as E


class SummarySteps(HTMLParser):
    """Native HTML tolerates legacy tag errors outside/between journal steps."""
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.steps = []
        self.text = None
        self.visible = -1

    def handle_starttag(self, tag, attrs):
        if tag == 'step':
            self.text, self.visible = [], -1
        elif tag == 'p' and self.text is not None:
            value = dict(attrs).get('visible', '')
            match = re.fullmatch(r'\[%([0-9]+)\]', value)
            if match:
                self.visible = int(match[1])

    def handle_data(self, text):
        if self.text is not None:
            self.text.append(text)

    def handle_endtag(self, tag):
        if tag == 'step' and self.text is not None:
            self.steps.append((''.join(self.text), self.visible))
            self.text = None


def generate(client, output, read_pak, decode_html):
    client, output = Path(client), Path(output)
    inputs = {}
    game = (client / 'bin64/Game.dll').read_bytes()
    for site in (0x9633e5, 0x6c17ef):
        expected = b'\xe8' + struct.pack('<i', 0x1c2ca0 - site - 5)
        if game[site:site + 5] != expected:
            raise ValueError('Unsupported native quest formatter call: ' + hex(site))
    if game[0x9633d0:0x9633d4] != bytes.fromhex('4d8d4f46'):
        raise ValueError('Native mob-name pointer contract differs')
    if game[0x6c16ee:0x6c16f3] != bytes.fromhex('44896c2430'):
        raise ValueError('Native tooltip quest-ID argument contract differs')
    inputs['bin64/Game.dll'] = hashlib.sha256(game).hexdigest()
    def archive(relative):
        path = client / relative
        inputs[relative] = hashlib.sha256(path.read_bytes()).hexdigest()
        return read_pak(path)

    from codec import binary_xml
    npcs = archive('Data/Npcs/Npcs.pak')
    attackable = set()
    for name in ('client_npcs_monster.xml', 'client_npcs_npc.xml'):
        for npc in binary_xml(npcs.read(name)):
            if (npc.findtext('cursor_type') or '').lower() == 'attack':
                attackable.add(int(npc.findtext('id')))
    ranges = []
    for ident in sorted(attackable):
        if ranges and ident == ranges[-1][1] + 1:
            ranges[-1][1] = ident
        else:
            ranges.append([ident, ident])

    english = archive('L10N/enu/data/data.pak')
    quotas = set()
    pattern = re.compile(r'\[%([0-9]+)\]\s*/\s*([0-9]+)')
    for name in english.namelist():
        match = re.search(r'(?:^|/)quest_q([0-9]+)\.html$', name, re.I)
        if not match:
            continue
        qid = int(match[1])
        text = decode_html(english.read(name), Path(name).name)
        for page in re.findall(r'<HtmlPage\b[^>]*\bname\s*=\s*[\"\']quest_summary[\"\'][^>]*>(.*?)</HtmlPage\s*>', text, re.I | re.S):
            parser = SummarySteps()
            parser.feed(page)
            for text, visible in parser.steps:
                for counter, required in pattern.findall(text):
                    counter, required = int(counter), int(required)
                    if 0 <= counter < 60 and -1 <= visible < 60 and 0 < required <= 0x7fffffff:
                        quotas.add((qid, counter, visible, required))

    # Explicit fallback for maintained data-driven hunts: their owning handler
    # increments native QuestVars SECTION_1 and advances SECTION_0 at the quota.
    # Never apply this layout to ordinary compiled retail quest scripts.
    custom = []
    arc_path = Path(__file__).resolve().parent.parent / 'custom-quests/writing-on-wall/arc.json'
    arc = json.loads(arc_path.read_text(encoding='utf-8'))
    quests = archive('Data/Quest/Quest.pak')
    registrations = {int(q.findtext('id')): q for q in binary_xml(quests.read('data_driven_quest.xml'))}
    for quest in arc['quests']:
        registration = registrations.get(quest['id'])
        if registration is None:
            continue
        owned = E.parse(arc_path.parent.parent / 'Quests' / (str(quest['id']) + '-' + quest['slug']) / 'registration.xml').getroot()
        # Whitespace between elements is not native data; compare leaf values.
        actual = [(n.tag, (n.text or '').strip()) for n in registration.iter() if not len(n)]
        expected = [(n.tag, (n.text or '').strip()) for n in owned.iter() if not len(n)]
        if actual != expected:
            raise ValueError('Installed data-driven hunt contract differs: ' + str(quest['id']))
        for stage, step in enumerate(quest['steps']):
            if step['type'] == 'hunt':
                row = (quest['id'], stage * 3 + 2, stage * 3, step['count'])
                if row not in quotas:
                    raise ValueError('Installed hunt quota differs from maintained authoring: ' + str(row))
                custom.append((quest['id'], stage, step['count'], stage * 3 + 2))

    for relative, digest in inputs.items():
        if hashlib.sha256((client / relative).read_bytes()).hexdigest() != digest:
            raise ValueError('Quest metadata input changed during preparation: ' + relative)

    lines = ['// Generated from the active original client by quest_targets.py.',
             'struct QuestNpcRange { int first,last; };',
             'constexpr QuestNpcRange QUEST_ATTACKABLE_NPCS[]={']
    lines += ['    {%d,%d},' % tuple(row) for row in ranges]
    lines += ['};', 'struct QuestQuota { int quest,param,visible,required; };',
              'constexpr QuestQuota QUEST_QUOTAS[]={']
    lines += ['    {%d,%d,%d,%d},' % row for row in sorted(quotas)]
    lines += ['};', 'struct QuestAuthoredHunt { int quest,stage,required,param; };',
              'constexpr QuestAuthoredHunt QUEST_AUTHORED_HUNTS[]={']
    lines += ['    {%d,%d,%d,%d},' % row for row in sorted(custom)]
    lines += ['};', '']
    (output / 'quest_target_data.h').write_text('\n'.join(lines), encoding='utf-8')
    report = dict(inputs=inputs, attackableNpcs=len(attackable), npcRanges=len(ranges),
                  journalQuotas=len(quotas), authoredHunts=len(custom),
                  authoringSha256=hashlib.sha256(arc_path.read_bytes()).hexdigest(),
                  authoredHuntRows=custom,
                  contract='Native name formatter and quest glyphs; native live quest variables/inventory; no saved player state')
    (output / 'quest-target-data.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    return report


def refresh_inventory(payload, dll):
    inventory = json.loads(payload.decode('utf-8-sig'))
    digest = hashlib.sha256(dll).hexdigest()
    def refresh(value):
        if isinstance(value, list):
            for entry in value:
                refresh(entry)
        elif isinstance(value, dict):
            if value.get('path', '').replace('\\', '/').lower() == 'bin64/aetherfallcore.dll':
                value['installedSha256'], value['bytes'] = digest, len(dll)
            for entry in value.values():
                if isinstance(entry, (list, dict)):
                    refresh(entry)
    refresh(inventory)
    inventory.setdefault('features', {})['AetherfallQuestTargets'] = [
        dict(path='bin64/AetherfallCore.dll', installedSha256=digest, bytes=len(dll))]
    return json.dumps(inventory, indent=2).encode('utf-8')
