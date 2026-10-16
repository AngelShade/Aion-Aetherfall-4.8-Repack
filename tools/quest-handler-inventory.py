"""Join JAXB quest metadata with cold compiled handlers and native prerequisite declarations.

Never instantiate a handler, register an event, start World, or change quest state.
Compilation availability is distinct from runtime registration and action coverage.
"""
import hashlib, io, json, re, struct, xml.etree.ElementTree as ET, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def class_info(data):
    stream = io.BytesIO(data)
    read = lambda n: stream.read(n)
    u1 = lambda: struct.unpack('>B', read(1))[0]
    u2 = lambda: struct.unpack('>H', read(2))[0]
    u4 = lambda: struct.unpack('>I', read(4))[0]
    assert u4() == 0xcafebabe
    u2(); u2()
    pool = [None] * u2(); index = 1
    while index < len(pool):
        tag = u1()
        if tag == 1: pool[index] = read(u2()).decode('utf-8', errors='replace')
        elif tag == 3: pool[index] = struct.unpack('>i', read(4))[0]
        elif tag == 4: read(4)
        elif tag in (5, 6): read(8); index += 1
        elif tag in (7, 8, 16, 19, 20): pool[index] = (tag, u2())
        elif tag in (9, 10, 11, 12, 17, 18): pool[index] = (tag, u2(), u2())
        elif tag == 15: read(3)
        else: raise AssertionError('Unknown constant pool tag ' + str(tag))
        index += 1
    flags, this, parent = u2(), u2(), u2()
    for _ in range(u2()): u2()
    def attributes():
        return [(pool[u2()], read(u4())) for _ in range(u2())]
    def members():
        rows = []
        for _ in range(u2()):
            access, name, descriptor = u2(), pool[u2()], pool[u2()]
            rows.append((access, name, descriptor, attributes()))
        return rows
    members(); methods = members()
    return dict(pool=pool, flags=flags, name=pool[pool[this][1]],
                parent=pool[pool[parent][1]] if parent else None, methods=methods)


def constructor_id(info, resolve=None):
    """Trace literal integer constructor delegation to the native quest base, without execution.

    Only the straight-line this/int argument prefix and invokespecial constructor calls
    are accepted. Fields, branches, arithmetic and computed quest IDs remain rejected.
    """
    constructor = next(m for m in info['methods'] if m[1:3] == ('<init>', '()V') and m[0] & 1)
    base = 'com/aionemu/gameserver/questEngine/handlers/AbstractQuestHandler'
    seen = set()

    def trace(current, method, arguments):
        key = (current.get('name'), method[2])
        assert key not in seen, 'Cyclic quest constructor delegation'
        seen.add(key)
        raw = dict(method[3])['Code']
        code = raw[8:8 + struct.unpack('>I', raw[4:8])[0]]
        assert code[0] == 0x2a, 'Constructor must begin with this'
        offset, values = 1, []
        while offset < len(code) and code[offset] != 0xb7:
            opcode = code[offset]; offset += 1
            if 0x02 <= opcode <= 0x08: value = opcode - 3
            elif opcode == 0x10:
                value = struct.unpack('>b', code[offset:offset + 1])[0]; offset += 1
            elif opcode == 0x11:
                value = struct.unpack('>h', code[offset:offset + 2])[0]; offset += 2
            elif opcode in (0x12, 0x13):
                width = 1 if opcode == 0x12 else 2
                index = int.from_bytes(code[offset:offset + width], 'big'); offset += width
                value = current['pool'][index]
            elif 0x1b <= opcode <= 0x1d:
                local = opcode - 0x1a
                assert local <= len(arguments), 'Unbound quest constructor argument'
                value = arguments[local - 1]
            elif opcode == 0x15:
                local = code[offset]; offset += 1
                assert 1 <= local <= len(arguments), 'Unbound quest constructor argument'
                value = arguments[local - 1]
            else: raise AssertionError('Review non-literal native quest constructor')
            assert type(value) is int, 'Constructor argument must be a literal integer'
            values.append(value)
        assert offset < len(code) and code[offset] == 0xb7
        call = current['pool'][int.from_bytes(code[offset + 1:offset + 3], 'big')]
        assert call[0] == 10
        owner = current['pool'][current['pool'][call[1]][1]]
        signature = current['pool'][call[2]]
        name, descriptor = (current['pool'][n] for n in signature[1:])
        assert name == '<init>' and re.fullmatch(r'\(I+\)V', descriptor)
        assert len(values) == len(descriptor) - 3
        assert owner == current['parent'] or owner == current.get('name'), 'Unrelated quest constructor'
        if owner == base or resolve is None:
            assert owner == current['parent'] and descriptor == '(I)V'
            assert values[0] > 0
            return values[0]
        target = current if owner == current.get('name') else resolve(owner)
        delegated = next(m for m in target['methods'] if m[1:3] == ('<init>', descriptor))
        return trace(target, delegated, values)

    return trace(info, constructor, ())


def enrich(review, build, coverage_path):
    review, build, coverage_path = map(Path, (review, build, coverage_path))
    coverage = json.loads(coverage_path.read_text())
    templates = {int(e.get('id')): e for e in ET.parse(ROOT / 'game-server/data/static_data/quest_data/quest_data.xml').getroot()}
    assert set(templates) == {q['id'] for q in coverage['quests']}, 'Native country/quest catalog differs; review exact merged declarations'
    custom = {}; source_inputs = {}; compiled_inputs = {}
    def native_base(info):
        parent = info['parent']; seen = set()
        while parent != 'com/aionemu/gameserver/questEngine/handlers/AbstractQuestHandler':
            assert parent and parent not in seen, 'Not a native quest handler inheritance chain'
            seen.add(parent)
            path = review / 'handler-classes' / (parent + '.class')
            assert path.is_file(), 'Review external/non-native quest superclass: ' + parent
            compiled_inputs[parent + '.class'] = sha(path)
            parent = class_info(path.read_bytes())['parent']
    for source in sorted((review / 'handler-source/data/handlers/quest').rglob('*.java')):
        text = source.read_text(encoding='utf-8-sig')
        name = re.search(r'public\s+(?:abstract\s+)?class\s+(\w+)', text)
        package = re.search(r'\bpackage\s+([\w.]+)\s*;', text)
        assert name and package, source
        class_path = Path(package[1].replace('.', '/')) / (name[1] + '.class')
        compiled = review / 'handler-classes' / class_path
        info = class_info(compiled.read_bytes())
        assert info['name'] == class_path.as_posix()[:-6]
        if info['flags'] & 0x400: continue
        assert info['flags'] & 1 and not info['flags'] & 0x200
        native_base(info)
        def resolve_constructor(owner):
            path = review / 'handler-classes' / (owner + '.class')
            assert path.is_file(), 'Review external quest constructor: ' + owner
            compiled_inputs[owner + '.class'] = sha(path)
            return class_info(path.read_bytes())
        quest = constructor_id(info, resolve_constructor)
        declared = re.search(r'int\s+_?questId\s*=\s*(\d+)', text) or re.search(r'(?:super|this)\(\s*(\d+)\s*[,)]', text)
        assert declared and quest == int(declared[1]), 'Compiled quest ID differs from maintained constructor'
        assert quest not in custom, 'Duplicate custom native handler'
        rel = source.relative_to(review / 'handler-source').as_posix()
        assert source.read_bytes() == (ROOT / 'game-server' / rel).read_bytes()
        source_inputs[rel] = sha(source); compiled_inputs[class_path.as_posix()] = sha(compiled)
        custom[quest] = dict(className=info['name'].replace('/', '.'), source=rel,
            registrationDeclarations=[dict(line=i, text=line.strip()) for i, line in enumerate(text.splitlines(), 1)
                if re.search(r'qe\.register|addOn(?:QuestStart|Talk|Kill)Event|isCompleteQuest|getQuestState\(', line)])
    model_classes = {q['model'] for q in coverage['quests'] if q['model'] != 'custom-or-no-XML'}
    native_models = {}
    with zipfile.ZipFile(build / 'game-server/game-server-4.8-SNAPSHOT.jar') as jar:
        for model in model_classes:
            info = class_info(jar.read('com/aionemu/gameserver/questEngine/handlers/models/' + model + '.class'))
            handlers = sorted({info['pool'][e[1]].replace('/', '.') for e in info['pool'] if isinstance(e, tuple) and e[0] == 7
                and isinstance(info['pool'][e[1]], str) and info['pool'][e[1]].startswith('com/aionemu/gameserver/questEngine/handlers/template/')})
            assert len(handlers) == 1, ('Review native model registration', model, handlers)
            assert handlers[0].replace('.', '/') + '.class' in jar.namelist()
            native_models[model] = handlers[0]
    counts = {}; edges = []; by_id = {}
    for row in coverage['quests']:
        quest = row['id']; model = row['model']
        assert not (quest in custom and model != 'custom-or-no-XML'), 'Native duplicate registration must be reviewed'
        availability = 'compiled-custom-handler' if quest in custom else 'native-XML-handler' if model in native_models else 'no-maintained-native-handler'
        counts[availability] = counts.get(availability, 0) + 1
        row['handlerAvailability'] = availability
        row['nativeHandler'] = custom.get(quest, {}).get('className', native_models.get(model, ''))
        row['registeredAtRuntime'] = 'not executed offline; runtime diagnostic checks QuestEngine'
        row['prerequisiteGroups'] = [ET.tostring(group, encoding='unicode').strip() for group in templates[quest].findall('start_conditions')]
        references = []
        for group in templates[quest].findall('start_conditions'):
            for condition in group:
                if condition.tag == 'finished':
                    references.append(dict(relation='finished', quest=int(condition.get('quest_id')), reward=int(condition.get('reward', '-1'))))
                elif condition.tag in {'acquired', 'unfinished', 'noacquired'}:
                    references.extend(dict(relation=condition.tag, quest=int(value)) for value in (condition.text or '').split())
        row['questReferences'] = references
        row['nativeActionGap'] = 'native handler unavailable' if availability == 'no-maintained-native-handler' else row['coverage']
        if availability == 'no-maintained-native-handler': row['coverage'] = 'native handler unavailable; bot actions cannot substitute quest authority'
        if quest in custom: row['nativeDeclarations'] = custom[quest]
        for ref in references: edges.append(dict(source=quest, **ref))
        by_id[quest] = row
    for row in coverage['quests']:
        row['unavailablePrerequisites'] = [ref for ref in row['questReferences'] if ref['relation'] in {'finished', 'acquired'}
            and (ref['quest'] not in by_id or by_id[ref['quest']]['handlerAvailability'] == 'no-maintained-native-handler')]
    coverage['handlerAvailability'] = counts
    coverage['questReferenceEdges'] = edges
    coverage['customHandlerSourceSha256'] = source_inputs
    coverage['customHandlerClassesSha256'] = compiled_inputs
    coverage['boundary'] = ('Exact cold compiled/source/native XML handler availability and declared prerequisite graph. '
        'No constructor, registration, World, actor, native event, quest progress or reward executed. '
        'Optional/negative/reward-group declarations retained verbatim; edges alone do not establish eligibility or complete chain support.')
    coverage_path.write_text(json.dumps(coverage, indent=2), encoding='utf-8')
    print('OK: cold native quest handler inventory:', counts, ';', len(edges), 'declared quest references; no handlers executed')
