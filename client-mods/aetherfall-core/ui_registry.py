"""Generate the shared UI catalogue from the exact installed/staged 4.8 resources.

Records declarations, variants, widget ancestry, presets and skins without opening
windows. Native dialog IDs come only from verified RTTI/GetType implementations;
resource-only definitions remain explicitly unmapped.
"""
import hashlib
import io
import json
from pathlib import Path
import re
import struct
import sys
import xml.etree.ElementTree as E

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'expanded-warehouse'))
from codec import binary_xml, read_pak


def sha(data):
    return hashlib.sha256(data).hexdigest()


def shortcut_content_bounds(client, skins):
    """Measure only the shop sprite needed by its artwork-sized MoveUI box."""
    from PIL import Image
    selected = [row for name in ('mkt_shop_up', 'af_shortcut_mkt_shop_up')
                for row in skins.get(name, [])]
    if not selected:
        return
    with read_pak(client / 'Textures/ui/ui.pak') as archive:
        names = {Path(name).name.lower(): name for name in archive.namelist()}
        images = {}
        for row in selected:
            attributes = row['attributes']
            texture = attributes.get('texture', '').replace('\\', '/')
            entry = names.get(Path(texture).name.lower() + '.dds')
            if not entry or not attributes.get('src_image'):
                raise ValueError('Shop sprite texture or rectangle is unavailable')
            if entry not in images:
                data = archive.read(entry)
                images[entry] = Image.open(io.BytesIO(data)).convert('RGBA'), sha(data)
            image, digest = images[entry]
            x, y, width, height = [int(value) for value in attributes['src_image'].split(',')]
            if width <= 0 or height <= 0 or x < 0 or y < 0 or x + width > image.width or y + height > image.height:
                raise ValueError('Shop sprite rectangle exceeds its atlas')
            bounds = image.getchannel('A').crop((x, y, x + width, y + height)).getbbox()
            if bounds is None:
                raise ValueError('Shop sprite is fully transparent')
            left, top, right, bottom = bounds
            row['contentBounds'] = [left / width, top / height, (right - left) / width, (bottom - top) / height]
            row['contentSource'] = dict(archive='Textures/ui/ui.pak', entry=entry, sha256=digest,
                                        rectangle=[x, y, width, height], alphaBounds=list(bounds))


def native_types(path):
    # PE parsing is build-time only. Both slots were verified against native
    # FindWidget and the dialog GetType dispatch, not inferred from XML names.
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'game-server/target/native-wardrobe-test-deps'))
    import pefile
    pe = pefile.PE(str(path), fast_load=True)
    blob, base = pe.get_memory_mapped_image(), pe.OPTIONAL_HEADER.ImageBase
    references = {}
    for section in pe.sections:
        # This shipped image merges code/RTTI into one RWX section. Validate
        # candidate vtables by content below instead of assuming .rdata names.
        if not section.Characteristics & 0x40000000:
            continue
        start = section.VirtualAddress
        end = min(len(blob) - 8, start + section.Misc_VirtualSize)
        for at in range((start + 7) & ~7, end, 8):
            value = struct.unpack_from('<Q', blob, at)[0] - base
            if 0 <= value < len(blob):
                references.setdefault(value, []).append(at)
    dialogs, widgets = {}, {}
    for match in re.finditer(rb'\.\?AV((?:Dlg|UI)[A-Za-z0-9_]+)@@\x00', blob):
        descriptor = match.start() - 16
        classname = match[1].decode('ascii')
        for ref in re.finditer(re.escape(struct.pack('<I', descriptor)), blob):
            # x64 complete-object locators place the type descriptor RVA at +12.
            locator = ref.start() - 12
            if locator < 0:
                continue
            signature = struct.unpack_from('<I', blob, locator)[0]
            if signature not in (0, 1):
                continue
            if signature == 1 and struct.unpack_from('<I', blob, locator + 20)[0] != locator:
                continue
            for at in references.get(locator, []):
                vtable = at + 8
                if not all(base <= value < base + len(blob) for value in struct.unpack_from('<4Q', blob, vtable)):
                    continue
                slot = 0x18 if classname.startswith('Dlg') else 0x10
                if vtable + slot + 8 > len(blob):
                    continue
                function = struct.unpack_from('<Q', blob, vtable + slot)[0] - base
                code = blob[function:function + 6] if function >= 0 else b''
                if len(code) != 6 or code[0] != 0xB8 or code[5] != 0xC3:
                    continue
                ident = struct.unpack_from('<I', code, 1)[0]
                if not (0 < ident <= 0x224 if classname.startswith('Dlg') else 0x2000 <= ident <= 0x2100):
                    continue
                key = re.sub('[^a-z0-9]', '', classname.lower())
                target = dialogs if classname.startswith('Dlg') else widgets
                row = dict(id=ident, nativeClass=classname, vtable=vtable, getType=function)
                rows = target.setdefault(key, [])
                if row not in rows:
                    rows.append(row)
    for key, expected in [('dlgradar', 14), ('dlgtarget', 11)]:
        if expected not in {row['id'] for row in dialogs.get(key, [])}:
            raise ValueError('Native dialog mapping did not verify ' + key)
    return dialogs, widgets


def parse(data):
    return binary_xml(data) if data and data[0] == 0x80 else E.fromstring(data)


def literal(value):
    if value is None:
        return 'nil'
    if isinstance(value, bool):
        return 'true' if value else 'false'
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, str):
        quoted = json.dumps(value, ensure_ascii=False)
        return re.sub(r'\\u([0-9a-f]{4})', lambda m: '\\%03d' % int(m[1], 16), quoted)
    if isinstance(value, list):
        return '{' + ','.join(map(literal, value)) + '}'
    return '{' + ','.join('[' + literal(k) + ']=' + literal(v) for k, v in sorted(value.items())) + '}'


def generate(client, output, plugin_members=None):
    client, output = Path(client), Path(output)
    layouts, presets, skins, sources, errors, normalizations = {}, {}, {}, {}, [], []
    dialogs, widget_types = native_types(client / 'bin64/Game.dll')
    sources['bin64/Game.dll'] = sha((client / 'bin64/Game.dll').read_bytes())
    archives = sorted(p.relative_to(client).as_posix() for p in (client / 'Data/ui').rglob('*.pak'))
    archives += ['L10N/enu/data/data.pak']
    archives += sorted(p.relative_to(client).as_posix() for p in (client / 'Plugin').glob('*/*.pak'))
    for relative in archives:
        staged = output / relative
        archive_path = staged if staged.is_file() else client / relative
        with read_pak(archive_path) as archive:
            for entry in archive.namelist():
                if not entry.lower().endswith('.xml'):
                    continue
                if relative.startswith('L10N/') and not entry.lower().startswith('ui/'):
                    continue
                data = (plugin_members or {}).get(entry) if relative == 'Plugin/RelicCalc/RelicCalc.pak' else None
                data = data if data is not None else archive.read(entry)
                key = relative + '|' + entry
                sources[key] = sha(data)
                try:
                    root = parse(data)
                except (ValueError, E.ParseError, IndexError) as exc:
                    # Shipped English Gatha has <preset> paired with </Preset>.
                    # Catalogue its declarations with that case-only correction,
                    # recording the malformed source; do not alter live assets.
                    text = data.decode('utf-16') if data.startswith((b'\xff\xfe', b'\xfe\xff')) else data.decode('utf-8-sig')
                    corrected = re.sub(r'<preset(?=[\s>])', '<Preset', text)
                    if corrected == text:
                        errors.append(dict(source=key, error=str(exc)[:160])); continue
                    try:
                        root = E.fromstring(corrected)
                    except E.ParseError:
                        errors.append(dict(source=key, error=str(exc)[:160])); continue
                    normalizations.append(dict(source=key, error=str(exc), catalogueOnly='preset opening-tag case'))
                roots = [root] if root.tag == 'Dialog' else list(root.iter('Dialog'))
                for index, dialog in enumerate(roots, 1):
                    name = dialog.get('name', '')
                    native = dialogs.get(re.sub('[^a-z0-9]', '', dialog.get('type', '').lower()), [])
                    ids = sorted(set(row['id'] for row in native))
                    nodes = []

                    def walk(node, path, parent):
                        # The ordinal counts widget children only, as the native
                        # list does. Skin/script declarations are not children.
                        ordinal = 0
                        for widget in node:
                            if widget.tag != 'Widget':
                                continue
                            ordinal += 1
                            selector = path + ('/' if path else '') + '#' + str(ordinal)
                            nodes.append(dict(path=selector, parent=parent, attributes=dict(widget.attrib),
                                              skins=[dict(n.attrib) for n in widget if n.tag == 'Skin']))
                            walk(widget, selector, selector)
                    walk(dialog, '', '')
                    layouts[key + '#' + str(index)] = dict(name=name, source=key, attributes=dict(dialog.attrib),
                                                           nativeIds=ids, widgets=nodes)
                for tag, target in [('Preset', presets), ('Skin', skins)]:
                    for node in root.iter(tag):
                        name = node.get('name')
                        if name and (tag != 'Skin' or node.get('texture')):
                            target.setdefault(name, []).append(dict(source=key, attributes=dict(node.attrib),
                                references=[dict(n.attrib) for n in node if n.tag in ('SkinRef', 'Widget', 'Skin')]))
    # Keep resource errors visible and fail preparation instead of shipping a
    # silently incomplete catalogue. Non-UI XML roots are valid and need no rows.
    if errors:
        (output / 'ui-registry-errors.json').write_text(json.dumps(errors, indent=2), encoding='utf-8')
        raise ValueError('UI XML could not be catalogued; see ui-registry-errors.json')
    shortcut_content_bounds(client, skins)
    report = dict(schema=1, layouts=len(layouts), widgets=sum(len(r['widgets']) for r in layouts.values()),
                  presets=len(presets), skins=len(skins), nativeDialogTypes=len(dialogs), nativeWidgetTypes=len(widget_types),
                  unmappedLayouts=sorted(k for k, r in layouts.items() if not r['nativeIds']), sources=sources,
                  normalizations=normalizations, gameStarted=False)
    (output / 'ui-registry-report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    lines = ['-- Generated from active 4.8 UI resources by ui_registry.py; do not edit.',
             'AFCore.UI={schema=1,layouts={},presets={},skins={},nativeTypes=' + literal(dialogs) +
             ',widgetTypes=' + literal(widget_types) + ',sources=' + literal(sources) + '};']
    for category, rows in [('layouts', layouts), ('presets', presets), ('skins', skins)]:
        # Each assignment owns its constants, avoiding Lua 5.1's per-function
        # constant limit as the client adds new UI definitions.
        for key, row in sorted(rows.items()):
            lines.append('(function() AFCore.UI.' + category + '[' + literal(key) + ']=' + literal(row) + ' end)();')
    return ('\n'.join(lines) + '\n').encode('utf-8')
