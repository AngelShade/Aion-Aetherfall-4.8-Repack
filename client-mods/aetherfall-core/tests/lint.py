"""Token-aware checks for the interface's module ownership and dependency rules."""
import argparse
import ast
from dataclasses import dataclass
from pathlib import Path
import re
import tempfile

SOURCE = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class Token:
    kind: str
    value: str
    line: int


def tokens(source):
    """Keep literal values for dependency declarations, never scan their contents as code."""
    result, pos, line = [], 0, 1
    while pos < len(source):
        c = source[pos]
        if c.isspace():
            line += c == '\n'
            pos += 1
            continue
        comment = source.startswith('--', pos)
        start = pos + 2 if comment else pos
        long = re.match(r'\[(=*)\[', source[start:])
        if long:
            close = ']' + long[1] + ']'
            begin = start + long.end()
            end = source.find(close, begin)
            if end < 0:
                raise ValueError(f'unterminated long string/comment at line {line}')
            text = source[begin:end]
            if not comment:
                result.append(Token('string', text, line))
            end += len(close)
            line += source[pos:end].count('\n')
            pos = end
            continue
        if comment:
            end = source.find('\n', pos)
            pos = len(source) if end < 0 else end
            continue
        if c in ('"', "'"):
            start, quote, value = pos, c, []
            pos += 1
            while pos < len(source) and source[pos] != quote:
                if source[pos] == '\\':
                    pos += 1
                    if pos == len(source):
                        break
                value.append(source[pos])
                pos += 1
            if pos == len(source):
                raise ValueError(f'unterminated string at line {line}')
            result.append(Token('string', ''.join(value), line))
            pos += 1
            line += source[start:pos].count('\n')
            continue
        match = re.match(r'[A-Za-z_][A-Za-z_0-9]*', source[pos:])
        if match:
            result.append(Token('name', match[0], line))
            pos += match.end()
        else:
            result.append(Token('symbol', c, line))
            pos += 1
    return result


def matching(ts, start, left='(', right=')'):
    depth = 0
    for i in range(start, len(ts)):
        if ts[i].kind == 'symbol' and ts[i].value == left:
            depth += 1
        elif ts[i].kind == 'symbol' and ts[i].value == right:
            depth -= 1
            if depth == 0:
                return i
    raise ValueError(f'unclosed {left} at line {ts[start].line}')


def sequence(ts, index, values):
    return [t.value for t in ts[index:index + len(values)]] == values


def module_spec(ts):
    registrations = []
    for i in range(len(ts) - 5):
        if sequence(ts, i, ['AF', ':', 'NewModule', '(']) and ts[i + 4].kind == 'string':
            registrations.append(i)
    if len(registrations) != 1:
        raise ValueError('a module must register exactly once with a literal name')
    i = registrations[0]
    owner, needs = ts[i + 4].value, set()
    end = matching(ts, i + 3)
    depth = 0
    for p in range(i + 5, end):
        token = ts[p]
        if sequence(ts, p, ['needs', '=', '{']) and depth == 1:
            close = matching(ts, p + 2, '{', '}')
            for item in ts[p + 3:close]:
                if item.kind == 'string':
                    needs.add(item.value)
                elif item.value != ',':
                    raise ValueError(f'needs must contain literal module names at line {item.line}')
        if token.kind == 'symbol':
            depth += token.value == '{'
            depth -= token.value == '}'
    return owner, needs


def core_api(files):
    """Derive exported methods/fields, so an invented Core data field is rejected."""
    api = {'': set()}
    for path in files:
        ts = tokens(path.read_text(encoding='utf-8-sig'))
        aliases = {'AF': ''}
        for i in range(len(ts) - 4):
            if sequence(ts, i, ['AF', '.']) and sequence(ts, i + 3, ['=']):
                namespace = ts[i + 2].value
                api[''].add(namespace)
                api.setdefault(namespace, set())
                if ts[i + 4].kind == 'name':
                    aliases[ts[i + 4].value] = namespace
                elif ts[i + 4].value == '{':
                    end = matching(ts, i + 4, '{', '}')
                    depth = 1
                    for p in range(i + 5, end):
                        if depth == 1 and ts[p].kind == 'name' and sequence(ts, p + 1, ['=']):
                            api[namespace].add(ts[p].value)
                        if ts[p].kind == 'symbol':
                            depth += ts[p].value == '{'
                            depth -= ts[p].value == '}'
        for i in range(len(ts) - 3):
            if ts[i].value in aliases and ts[i + 1].value in ('.', ':') and ts[i + 2].kind == 'name':
                method = ts[i + 2].value
                if (i > 0 and ts[i - 1].value == 'function') or ts[i + 3].value == '=':
                    api[aliases[ts[i].value]].add(method)
    return api


def inspect(path, api, module_names, element_owner=None, element_needs=()):
    text = path.read_text(encoding='utf-8-sig')
    ts = tokens(text)
    errors = []

    def fail(token, message):
        errors.append((token.line, message))

    if len(text.splitlines()) > 600:
        errors.append((1, f'{len(text.splitlines())} lines exceeds the 600-line limit'))
    owner, needs = None, set()
    if element_owner:
        owner, needs = element_owner, set(element_needs)
        registered = False
        for i, token in enumerate(ts):
            if sequence(ts, i, ['AF', ':', 'NewModule']):
                fail(token, 'element providers may not register a feature module')
            if (sequence(ts, i, ['AF', ':', 'Call', '(']) and i + 6 < len(ts)
                    and ts[i + 4].kind == 'string' and ts[i + 4].value == owner
                    and ts[i + 5].value == ',' and ts[i + 6].kind == 'string'
                    and ts[i + 6].value in ('RegisterElement', 'RegisterNative')):
                registered = True
        if not registered:
            errors.append((1, 'UnitFrames provider must register with its owning module'))
    elif path.parent.name == 'modules':
        owner, needs = module_spec(ts)
        for dependency in needs:
            if dependency not in module_names:
                errors.append((1, f'missing declared dependency {dependency}'))
    elif path.parent.name in ('options', 'datatexts', 'tags'):
        # Data providers are Core clients too, but have no feature table to
        # reach into. Dependency-bearing methods belong in the owning module.
        owner = '@' + path.parent.name
        for i, token in enumerate(ts):
            if token.value in ('CreateWidget', 'LoadXMLWidget', 'AddNewWidget'):
                fail(token, 'declarative providers may not construct widgets')
            if sequence(ts, i, ['AF', ':', 'NewModule']):
                fail(token, 'providers may not register feature modules')
            if token.value in ('Every', 'After', 'Phase', 'On') and i and ts[i - 1].value == ':':
                fail(token, 'providers may not create event/timer subscriptions')
        if path.parent.name == 'options':
            registrations = [i for i in range(len(ts) - 4)
                             if sequence(ts, i, ['AF', '.', 'Config', ':'])
                             and ts[i + 4].value in ('Register', 'RegisterLazy')]
            if not registrations:
                errors.append((1, 'option file must register a lazy Config provider'))
            for i in registrations:
                end = matching(ts, i + 5)
                if ts[i + 6].kind == 'string':
                    owner = ts[i + 6].value
                    if owner not in module_names:
                        fail(ts[i + 6], 'option provider owner is not a feature module')
                if not any(token.value == 'function' for token in ts[i + 6:end]):
                    fail(ts[i], 'option tree must be a deferred factory')
    core_aliases, module_aliases, bridge_aliases = {'AF': ''}, {}, {'AFCore'}
    is_db = path.parent.name == 'core' and path.name == 'DB.lua'
    for i, token in enumerate(ts):
        if token.kind == 'name' and (token.value == 'U' or token.value in ('AFUI', 'AFHUD')
                                     or token.value.startswith(('AFUI_', 'AFHUD_'))):
            fail(token, 'legacy/shared identifier ' + token.value)
        if (sequence(ts, i, ['local']) and i + 3 < len(ts) and ts[i + 1].kind == 'name'
                and ts[i + 2].value == '=' and ts[i + 3].value in bridge_aliases
                and (i + 4 >= len(ts) or ts[i + 4].value not in ('.', ':', '[', '('))):
            bridge_aliases.add(ts[i + 1].value)
        if (not is_db and token.value in bridge_aliases and i + 2 < len(ts)
                and ts[i + 1].value in ('.', ':')):
            if ts[i + 2].value in ('SaveFile', 'ReadFile'):
                fail(token, 'only DB may read or save bridge files')
        if (not is_db and token.value in bridge_aliases and i + 2 < len(ts) and ts[i + 1].value == '['
                and ts[i + 2].kind == 'string' and ts[i + 2].value in ('SaveFile', 'ReadFile')):
            fail(token, 'only DB may read or save bridge files')
        if (sequence(ts, i, ['_G', '[']) and i + 2 < len(ts) and ts[i + 2].kind == 'string'
                and (ts[i + 2].value in ('AFUI', 'AFHUD') or ts[i + 2].value.startswith(('AFUI_', 'AFHUD_')))):
            fail(token, 'legacy global lookup ' + ts[i + 2].value)
        if (token.value in ('rawget', 'rawset') and sequence(ts, i + 1, ['(', '_G', ','])
                and i + 4 < len(ts) and ts[i + 4].kind == 'string'
                and (ts[i + 4].value in ('AFUI', 'AFHUD') or ts[i + 4].value.startswith(('AFUI_', 'AFHUD_')))):
            fail(token, 'legacy global lookup ' + ts[i + 4].value)
        if not owner:
            continue
        # Local aliases retain their namespace, including aliases of aliases.
        if (sequence(ts, i, ['local']) and i + 3 < len(ts) and ts[i + 1].kind == 'name'
                and ts[i + 2].value == '=' and ts[i + 3].value in core_aliases):
            source = core_aliases[ts[i + 3].value]
            after = i + 4
            if after + 1 < len(ts) and ts[after].value == '.' and not source:
                source = ts[after + 1].value
                after += 2
            if after >= len(ts) or ts[after].value not in ('.', ':', '(', '['):
                core_aliases[ts[i + 1].value] = source
        if (token.value in core_aliases and i + 2 < len(ts) and ts[i + 1].value in ('.', ':')
                and ts[i + 2].kind == 'name'):
            namespace = core_aliases[token.value]
            key = ts[i + 2].value
            if key.startswith('_') or key not in api.get(namespace, set()):
                fail(token, f'Core-private or unknown access {token.value}{ts[i + 1].value}{key}')
        if (token.value in core_aliases and not core_aliases[token.value] and i + 5 < len(ts)
                and ts[i + 1].value in ('.', ':') and ts[i + 2].value in ('Call', 'GetModule')
                and ts[i + 3].value == '('):
            argument = i + 4
            if ts[i + 1].value == '.':
                if not (i + 6 < len(ts) and ts[i + 4].value in core_aliases
                        and not core_aliases[ts[i + 4].value] and ts[i + 5].value == ','):
                    fail(token, 'dot-form Core calls must pass AF as their first argument')
                    continue
                argument += 2
            name = ts[argument]
            if name.kind != 'string':
                fail(name, 'cross-module calls require a literal declared dependency')
                continue
            if name.value != owner and name.value not in needs:
                fail(name, 'undeclared dependency ' + name.value)
            end = matching(ts, i + 3)
            if ts[i + 2].value == 'GetModule':
                if i >= 3 and ts[i - 1].value == '=' and ts[i - 2].kind == 'name':
                    module_aliases[ts[i - 2].value] = name.value
                if end + 2 < len(ts) and ts[end + 1].value in ('.', '['):
                    after = end + 3 if ts[end + 1].value == '.' else end + 4
                    if after >= len(ts) or ts[after].value != '(':
                        fail(ts[end + 1], 'module interfaces expose methods only')
        if token.value in module_aliases and i + 2 < len(ts) and ts[i + 1].value in ('.', '['):
            after = i + 3 if ts[i + 1].value == '.' else i + 4
            if after >= len(ts) or ts[after].value != '(':
                fail(token, 'cross-module data access through ' + token.value)
        if token.value in module_names and token.value != owner and i + 1 < len(ts) and ts[i + 1].value in ('.', '['):
            fail(token, 'direct cross-module table access ' + token.value)
    return errors


def inspect_layout(path):
    """Keep geometry generators valid and styling in the shared Skin service."""
    text = path.read_text(encoding='utf-8-sig')
    errors = []
    if len(text.splitlines()) > 600:
        errors.append((1, f'{len(text.splitlines())} lines exceeds the 600-line limit'))
    tree = ast.parse(text, filename=str(path))
    style = {'color', 'colour', 'texture', 'image', 'alpha', 'skin'}
    fallbacks = {'font': {'ui_combo', 'ui_default'},
                 'preset': {'v5_dialog', 'v5_button', 'v5_editbox', 'v5_staticbox', 'v5_staticbox3',
                            'v5_hud_pet_command_s1'}}  # the mover overlay's base: a self-painted window that takes input
    allowed = {'static', 'image', 'button', 'editbox', 'combobox', 'spin',
               'container', 'scrollable', 'htmlview'}
    for node in ast.walk(tree):
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id == 'widget':
            if node.args and isinstance(node.args[0], ast.Constant):
                if str(node.args[0].value).lower() not in allowed:
                    errors.append((node.lineno, 'unproven plugin widget type ' + str(node.args[0].value)))
        if isinstance(node, ast.Dict):
            for key, value in zip(node.keys, node.values):
                if not isinstance(key, ast.Constant) or not isinstance(key.value, str):
                    continue
                if key.value.lower() in style:
                    errors.append((key.lineno, 'layout styling belongs in AF.Skin: ' + key.value))
                if key.value.lower() in fallbacks and isinstance(value, ast.Constant):
                    if value.value not in fallbacks[key.value.lower()]:
                        errors.append((value.lineno, 'unreviewed native style fallback ' + str(value.value)))
                if key.value == 'type' and isinstance(value, ast.Constant) and isinstance(value.value, str):
                    if value.value.lower() not in allowed:
                        errors.append((value.lineno, 'unproven plugin widget type ' + value.value))
        if isinstance(node, ast.keyword) and node.arg and node.arg.lower() in style:
            errors.append((node.lineno, 'layout styling belongs in AF.Skin: ' + node.arg))
        if isinstance(node, ast.keyword) and node.arg in fallbacks and isinstance(node.value, ast.Constant):
            if node.value.value not in fallbacks[node.arg]:
                errors.append((node.lineno, 'unreviewed native style fallback ' + str(node.value.value)))
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and node.func.attr == 'set':
            if node.args and isinstance(node.args[0], ast.Constant) and node.args[0].value in style:
                errors.append((node.lineno, 'layout styling belongs in AF.Skin: ' + node.args[0].value))
        if isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Subscript) and isinstance(target.slice, ast.Constant):
                    key = target.slice.value
                    if key in style:
                        errors.append((node.lineno, 'layout styling belongs in AF.Skin: ' + key))
                    if key in fallbacks:
                        values = [node.value]
                        if isinstance(node.value, ast.IfExp):
                            values = [node.value.body, node.value.orelse]
                        for value in values:
                            if isinstance(value, ast.Constant) and value.value not in fallbacks[key]:
                                errors.append((value.lineno, 'unreviewed native style fallback ' + str(value.value)))
    return errors


def lint(root=SOURCE):
    core = sorted((root / 'core').glob('*.lua'))
    modules = sorted((root / 'modules').glob('*.lua'))
    api = core_api(core)
    names, errors, specs = set(), [], {}
    for path in modules:
        try:
            owner, needs = module_spec(tokens(path.read_text(encoding='utf-8-sig')))
            if owner in names:
                errors.append((path, 1, 'duplicate module ' + owner))
            names.add(owner)
            specs[owner] = needs
        except ValueError as error:
            errors.append((path, 1, str(error)))
    providers = [path for directory in ('options', 'datatexts', 'tags')
                 for path in sorted((root / directory).glob('*.lua'))]
    elements = sorted((root / 'modules/unitframes').glob('*.lua'))
    layouts = sorted((root / 'layout').glob('*.py'))
    for path in core + modules + providers:
        try:
            errors.extend((path, line, message) for line, message in inspect(path, api, names))
        except ValueError as error:
            errors.append((path, 1, str(error)))
    for path in elements:
        if 'UnitFrames' not in names:
            errors.append((path, 1, 'UnitFrames provider has no owning module'))
        try:
            errors.extend((path, line, message) for line, message in
                          inspect(path, api, names, 'UnitFrames', specs.get('UnitFrames', ())))
        except ValueError as error:
            errors.append((path, 1, str(error)))
    for path in layouts:
        try:
            errors.extend((path, line, message) for line, message in inspect_layout(path))
        except SyntaxError as error:
            errors.append((path, error.lineno or 1, str(error)))
    return core + modules + elements + providers + layouts, errors


def self_test():
    """Prove that the checks reject ownership breaches and ignore widget literals."""
    with tempfile.TemporaryDirectory(prefix='aetherfall-lint-') as temporary:
        root = Path(temporary)
        (root / 'core').mkdir()
        (root / 'modules').mkdir()
        core = root / 'core/Engine.lua'
        core.write_text('''AF={} local D={} AF.DB=D
            function AF:NewModule() end
            function AF:Call() end
            function AF:GetModule() end
            function D:Set() end
        ''', encoding='utf-8')
        api = core_api([core])
        path = root / 'modules/Self.lua'
        header = "local M=AF:NewModule('Self',{needs={'Dependency'}})\n"
        valid = ["local caption='AFUIOptions U AFHUD_Update' -- AFUI.Get('x')\nM.value=1",
                 "local caption=[=[AFUI U]=] --[=[ AFHUD U ]=]\nM.value=1",
                 "local other=AF:GetModule('Dependency'); other:Run()",
                 "AF:Call('Dependency','Run'); local DB=AF.DB; DB:Set('Self','key',1)"]
        invalid = ['U.value=1', "AFUI.Get('x')", 'AFHUD_Update()', "_G['AFUI'].Get('x')",
                   "AFCore.ReadFile('x.cfg')", "AFCore['SaveFile']('x.cfg','x')",
                   'local DB=AF.DB; return DB.secret', "AF:Call('Missing','Run')",
                   "local other=AF:GetModule('Dependency'); return other.db",
                   "return AF:GetModule('Dependency').db", 'return Dependency.db',
                   "local Core=AF; Core:Call('Missing','Run')", "AF.Call(AF,'Missing','Run')",
                   "local C=AFCore; C.ReadFile('x.cfg')", "rawget(_G,'AFUI')"]
        for body in valid:
            path.write_text(header + body, encoding='utf-8')
            assert not inspect(path, api, {'Self', 'Dependency'}), body
        for body in invalid:
            path.write_text(header + body, encoding='utf-8')
            assert inspect(path, api, {'Self', 'Dependency'}), body
        element = root / 'modules/unitframes/Health.lua'
        element.parent.mkdir()
        registration = "AF:Call('UnitFrames','RegisterElement','Health',{})\n"
        element.write_text(registration, encoding='utf-8')
        assert not inspect(element, api, {'UnitFrames', 'Dependency'}, 'UnitFrames', {'Dependency'})
        for body in ("AF:NewModule('Extra')", "AF:Call('Missing','Run')", 'return Dependency.db',
                     "AFCore.SaveFile('state.cfg','private')", 'local DB=AF.DB; return DB.secret',
                     'AFUI.Click(\'Value\')'):
            element.write_text(registration + body, encoding='utf-8')
            assert inspect(element, api, {'UnitFrames', 'Dependency'}, 'UnitFrames', {'Dependency'}), body
        element.write_text('local unused={}', encoding='utf-8')
        assert inspect(element, api, {'UnitFrames'}, 'UnitFrames')
        options = root / 'options/Self.lua'
        options.parent.mkdir()
        options.write_text("AF.Config:Register('Self',function() return {} end)", encoding='utf-8')
        provider_api = dict(api, Config={'Register', 'RegisterLazy'})
        api[''].add('Config')
        assert not inspect(options, provider_api, {'Self'})
        for body in ("AF.Config:Register('Self',{})", "CreateWidget('Static','Option')",
                     "AF:NewModule('Extra')", "local M={}; M:Every(1,function() end)"):
            options.write_text(body, encoding='utf-8')
            assert inspect(options, provider_api, {'Self'}), body
        layout = root / 'geometry.py'
        layout.write_text("node={'type':'spin','frame':'0,0,40,20'}", encoding='utf-8')
        assert not inspect_layout(layout)
        for body in ("node={'type':'gauge'}", "node={'font':'fixed'}", "node.set('color','red')"):
            layout.write_text(body, encoding='utf-8')
            assert inspect_layout(layout), body


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=SOURCE)
    args = parser.parse_args()
    self_test()
    files, errors = lint(args.source)
    for path, line, message in errors:
        print(f'{path.relative_to(args.source)}:{line}: {message}')
    if not files:
        raise SystemExit('No Core/module files found')
    if errors:
        raise SystemExit(1)
    print(f'PASS: dependency/ownership lint ({len(files)} Core/module/provider/layout files, 600-line cap)')


if __name__ == '__main__':
    main()
