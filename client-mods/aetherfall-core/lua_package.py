"""The interface Lua manifest, flat plugin names and lossless archive migration."""
import json
from pathlib import Path, PurePosixPath
import struct

GENERATED = frozenset(('AetherfallLayout.lua', 'AetherfallLazy.lua'))
RETIRED = frozenset(('AetherfallOptions.lua', 'AetherfallHUD.lua'))
HUD_XML = 'AetherfallHUD.xml'
REGISTRY = 'AetherfallRegistry.lua'


def load_order(root):
    """Return (source, archive member) pairs from the one ordered Lua list."""
    root = Path(root).resolve()
    rows = json.loads((root / 'load_order.json').read_text(encoding='utf-8-sig'))
    if not isinstance(rows, list) or not rows:
        raise ValueError('load_order.json must be a nonempty list of Lua paths')
    result, sources, members = [], set(), set()
    for source in rows:
        if (not isinstance(source, str) or not source or '\\' in source
                or source != source.strip() or any(ord(c) < 32 or ord(c) == 127 for c in source)):
            raise ValueError('Lua paths must be relative forward-slash paths')
        path = PurePosixPath(source)
        if (path.is_absolute() or ':' in source or any(p in ('', '.', '..') for p in source.split('/'))
                or path.suffix != '.lua' or source.casefold() in {n.casefold() for n in RETIRED}
                or '_'.join(path.parts).casefold() == REGISTRY.casefold()):
            raise ValueError('Invalid or retired Lua path: ' + source)
        member = '_'.join(path.parts)
        if source.casefold() in sources or member.casefold() in members:
            raise ValueError('Duplicate Lua source or flattened member: ' + source)
        resolved = (root / source).resolve()
        if not resolved.is_relative_to(root):
            raise ValueError('Lua source escapes the maintained folder: ' + source)
        if source not in GENERATED and not resolved.is_file():
            raise ValueError('Missing maintained Lua source: ' + source)
        sources.add(source.casefold())
        members.add(member.casefold())
        result.append((source, member))
    if rows[0] != 'AetherfallCore.lua' or not GENERATED.issubset(rows):
        raise ValueError('Lua order must start with AetherfallCore.lua and include both generated layouts')
    return result


def lua_payloads(root, order, installed=None):
    """Retain generated geometry on updates; every maintained module ships together."""
    result = {}
    for source, member in order:
        if source in GENERATED:
            if installed is not None:
                result[member] = installed.read(member)
        else:
            result[member] = (Path(root) / source).read_bytes()
    return result


def reconcile_toc(lines, members, registry=False):
    """Replace the owned Lua group, retaining unrelated entries in their order."""
    members = list(members)
    folded = [name.casefold() for name in members]
    if len(folded) != len(set(folded)) or any('/' in n or '\\' in n for n in members):
        raise ValueError('The plugin Lua group must have unique flat names')
    if not members or 'AetherfallCore.lua' not in members:
        raise ValueError('The Lua manifest must include AetherfallCore.lua')
    owned = set(folded) | {name.casefold() for name in RETIRED} | {REGISTRY.casefold()}
    registry = registry or any(n.strip().casefold() == REGISTRY.casefold() for n in lines)
    if registry:
        members.insert(members.index('AetherfallCore.lua') + 1, REGISTRY)
    anchors = owned | {HUD_XML.casefold()}
    anchor = next((i for i, n in enumerate(lines) if n.strip().casefold() in anchors), len(lines))
    result, xml_seen = [], False
    for i, name in enumerate(lines):
        if i == anchor:
            result.extend(members)
        key = name.strip().casefold()
        if key in owned:
            continue
        if key == HUD_XML.casefold():
            if xml_seen:
                continue
            xml_seen = True
        result.append(name)
    if anchor == len(lines):
        result.extend(members)
    if not xml_seen:
        result.append(HUD_XML)
    return result


def retired_members(names):
    retired = {name.casefold() for name in RETIRED}
    return {name for name in names if name.casefold() in retired}


def remove_members(payload, removals, parts):
    """Remove local/central records without recompressing any retained member.

    ``parts`` is the existing Aion PAK parser. Only central offsets and the EOCD
    counts/size/offset change; encryption, local headers and compressed bytes do not.
    """
    if not removals:
        return payload
    local, central, end, _ = parts(payload)
    unknown = set(removals) - set(local)
    if unknown:
        raise ValueError('Cannot remove missing archive members: ' + ', '.join(sorted(unknown)))
    names = [name for name in local if name not in removals]
    if len(names) > 0xffff:
        raise ValueError('ZIP64 archives are not supported')
    result, offsets = bytearray(), {}
    for name in names:
        offsets[name] = len(result)
        result.extend(local[name][0])
    central_start = len(result)
    for name in names:
        record = bytearray(central[name])
        struct.pack_into('<I', record, 42, offsets[name])
        result.extend(record)
    central_size = len(result) - central_start
    tail = bytearray(end)
    struct.pack_into('<HHII', tail, 8, len(names), len(names), central_size, central_start)
    result.extend(tail)
    return bytes(result)
