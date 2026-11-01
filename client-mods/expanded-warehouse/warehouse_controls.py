"""Native Warehouse accordion and deposit selection; runs only on UI actions."""
import struct
from xml.etree import ElementTree as ET
from patch_game_dll import Assembler
import warehouse_search as search

START, END = 0xe91000, 0xe93000
EVENT, DISPATCH, PLACE, LAYOUT, TARGET, INIT = START, 0xe91100, 0xe91300, 0xe91500, 0xe91c00, 0xe91f00
SHOW, SHOW_SITE = 0xe92000, 0x947c05
TEXT, LAST, SCALE, TWENTY_FIVE, LEGACY = 0xe92a00, 0xe92f00, 0xe92f08, 0xe92f10, 0xe92f18
WAREHOUSE = 0x13875c0 + 247 * 8
CHARACTER_BUTTON = 'warehouse_character_toggle'
NAMES = [CHARACTER_BUTTON, 'personal_container', 'account_container',
         'warehouse_character_query', 'warehouse_character_search', 'warehouse_character_clear']
STRINGS, string_data = {}, bytearray()
for name in NAMES:
    STRINGS[name] = TEXT + len(string_data)
    string_data.extend(name.encode('ascii') + b'\0')
MAP_SITES = (0x785f87, 0x78e88c)
INIT_SITE = 0x947154


def emit(a, data):
    a.emit(bytes.fromhex(data))


def find(a, name):
    emit(a, '4889d9488b03')  # dialog is held in rbx
    a.relative(bytes.fromhex('488d15'), STRINGS[name])
    emit(a, 'ff9040030000')


def place(a, y=-1, height=-1, visible=-1):
    a.emit(b'\xba' + struct.pack('<i', y))
    if height is not None:
        a.emit(bytes.fromhex('41b8') + struct.pack('<i', height))
    if visible is not None:
        a.emit(bytes.fromhex('41b9') + struct.pack('<i', visible))
    a.relative(b'\xe8', PLACE)


def place_code():
    a = Assembler(PLACE)
    emit(a, '4885c9')
    a.branch(bytes.fromhex('0f84'), 'return')
    emit(a, '4883ec5848894c244044894c2448')
    emit(a, 'f30f6f4150f30f7f442420f30f6f4160f30f7f442430')
    emit(a, '85d2')
    a.branch(bytes.fromhex('0f88'), 'height')
    emit(a, 'f20f2ac2')
    a.relative(bytes.fromhex('f20f5905'), SCALE)
    emit(a, 'f20f11442428')
    a.label('height')
    emit(a, '4585c0')
    a.branch(bytes.fromhex('0f88'), 'rect')
    emit(a, 'f2410f2ac0')
    a.relative(bytes.fromhex('f20f5905'), SCALE)
    emit(a, 'f20f11442438')
    a.label('rect')
    emit(a, '488b01488d542420ff90a8010000')
    emit(a, '448b4c24484585c9')
    a.branch(bytes.fromhex('0f88'), 'done')
    emit(a, '488b4c2440488b01ba0100000041b801000000')
    a.branch(bytes.fromhex('0f84'), 'hide')
    emit(a, 'ff90d0000000')
    a.branch(b'\xe9', 'done')
    a.label('hide')
    emit(a, 'ff90d8000000')
    a.label('done')
    emit(a, '4883c458')
    a.label('return')
    emit(a, 'c3')
    return a.finish()


def layout_code():
    a = Assembler(LAYOUT)
    emit(a, '535657415441554883ec304889cb')
    find(a, CHARACTER_BUTTON)
    emit(a, '4885c0')
    a.branch(bytes.fromhex('0f84'), 'done')
    emit(a, '4889c6f20f104668')
    a.relative(bytes.fromhex('f20f5e05'), TWENTY_FIVE)
    a.relative(bytes.fromhex('f20f1105'), SCALE)
    emit(a, '4889f1488b06ff90a80500004189c4')
    emit(a, '448bab9c050000bfA802000083bb3405000002')  # 680 logical height
    a.branch(bytes.fromhex('0f84'), 'root')
    emit(a, '4585e4')
    a.branch(bytes.fromhex('0f85'), 'account_height')
    emit(a, '81ef1a010000')  # character body is 282 pixels
    a.label('account_height')
    emit(a, '4585ed')
    a.branch(bytes.fromhex('0f85'), 'root')
    emit(a, '81ef14010000')  # account body is 276 pixels
    a.label('root')
    emit(a, '4889d94189f8')
    place(a, height=None)
    emit(a, '83bb3405000002')
    a.branch(bytes.fromhex('0f84'), 'done')
    find(a, 'personal_container')
    emit(a, '4889c1448d47bf')  # height minus native frame/footer allowance 65
    place(a, height=None)
    emit(a, '488b8b400500004589e1')
    place(a, visible=None)
    for name in NAMES[3:]:
        find(a, name)
        emit(a, '4889c14589e1')
        place(a, visible=None)
    emit(a, '488b8b60050000ba190000004585e4')
    a.branch(bytes.fromhex('0f84'), 'header')
    emit(a, 'ba33010000')  # 307
    a.label('header')
    emit(a, '41b8ffffffff41b9ffffffff')
    a.relative(b'\xe8', PLACE)
    find(a, 'account_container')
    emit(a, '4889c1ba340000004585e4')
    a.branch(bytes.fromhex('0f84'), 'account')
    emit(a, 'ba4e010000')  # 334
    a.label('account')
    emit(a, '41b8140100004589e9')
    a.relative(b'\xe8', PLACE)
    a.label('done')
    emit(a, '4883c430415d415c5f5e5bc3')
    return a.finish()


def dispatch_code(old_dispatch):
    a = Assembler(DISPATCH)
    emit(a, '53565741544883ec284889cb4889d7488b32')
    emit(a, '483bb360050000')
    a.branch(bytes.fromhex('0f84'), 'account')
    find(a, CHARACTER_BUTTON)
    emit(a, '4839f0')
    a.branch(bytes.fromhex('0f85'), 'fallback')
    emit(a, '4889c1488b00ff90a805000085c0')
    a.branch(bytes.fromhex('0f84'), 'layout')
    a.relative(bytes.fromhex('c705'), LAST)
    # c7 RIP displacement counts its immediate as well.
    a.code[-4:] = struct.pack('<i', LAST - a.base - len(a.code) - 4)
    emit(a, '01000000')
    a.branch(b'\xe9', 'layout')
    a.label('account')
    emit(a, '4889f1488b06ff90a805000089839c05000085c0')
    a.branch(bytes.fromhex('0f84'), 'layout')
    a.relative(bytes.fromhex('c705'), LAST)
    a.code[-4:] = struct.pack('<i', LAST - a.base - len(a.code) - 4)
    emit(a, '02000000')
    a.label('layout')
    emit(a, '4889d9')
    a.relative(b'\xe8', LAYOUT)
    emit(a, 'b801000000')
    a.branch(b'\xe9', 'done')
    a.label('fallback')
    emit(a, '4889d94889fa')
    a.relative(b'\xe8', old_dispatch)
    a.label('done')
    emit(a, '4883c428415c5f5e5bc3')
    return a.finish()


def init_code():
    a = Assembler(INIT)
    emit(a, '4883ec28488b01ff9098050000')  # original Account SetChecked
    find(a, CHARACTER_BUTTON)
    emit(a, '4885c0')
    a.branch(bytes.fromhex('0f84'), 'done')
    emit(a, '4889c1488b00ba01000000ff90980500004889d9')
    a.relative(b'\xe8', LAYOUT)
    a.label('done')
    emit(a, '4883c428c3')
    return a.finish()


def show_code():
    a = Assembler(SHOW)
    emit(a, '4889e9')  # Warehouse OnVisible retains the owner in rbp
    a.relative(b'\xe8', LAYOUT)
    a.relative(b'\xe9', 0x947ddd)
    return a.finish()


def target_code():
    a = Assembler(TARGET)
    emit(a, '53565741544883ec28')
    a.relative(bytes.fromhex('488b1d'), WAREHOUSE)
    emit(a, '4885db')
    a.branch(bytes.fromhex('0f84'), 'character')
    emit(a, '83bb3405000002')
    a.branch(bytes.fromhex('0f84'), 'legion')
    find(a, CHARACTER_BUTTON)
    emit(a, '4885c0')
    a.branch(bytes.fromhex('0f84'), 'character')
    emit(a, '4989c44889c1488b00ff90a805000089c6')
    emit(a, '8bbb9c05000085f6')
    a.branch(bytes.fromhex('0f84'), 'character_closed')
    emit(a, '85ff')
    a.branch(bytes.fromhex('0f84'), 'character')
    a.branch(b'\xe9', 'last')
    a.label('character_closed')
    emit(a, '85ff')
    a.branch(bytes.fromhex('0f85'), 'account')
    a.label('last')
    a.relative(bytes.fromhex('8b05'), LAST)
    emit(a, '83f802')
    a.branch(bytes.fromhex('0f85'), 'last_character')
    emit(a, '09fe')
    a.branch(bytes.fromhex('0f85'), 'account')
    emit(a, 'c7839c05000001000000488b8b60050000488b01ba01000000ff9098050000')
    emit(a, '4889d9')
    a.relative(b'\xe8', LAYOUT)
    a.branch(b'\xe9', 'account')
    a.label('last_character')
    emit(a, '09fe')
    a.branch(bytes.fromhex('0f85'), 'character')
    emit(a, '4c89e1498b0424ba01000000ff90980500004889d9')
    a.relative(b'\xe8', LAYOUT)
    a.label('character')
    emit(a, 'b801000000')
    a.branch(b'\xe9', 'remember')
    a.label('account')
    emit(a, 'b802000000')
    a.label('remember')
    a.relative(bytes.fromhex('8905'), LAST)
    a.branch(b'\xe9', 'done')
    a.label('legion')
    emit(a, 'b803000000')
    a.label('done')
    emit(a, '4883c428415c5f5e5bc3')
    return a.finish()


def patch_dll(payload):
    data = bytearray(payload)
    prior_event = search.EVENT_SITE+5+struct.unpack_from('<i',data,search.EVENT_SITE+1)[0]
    already = prior_event == EVENT
    if data[search.EVENT_SITE] != 0xe9 or (not already and not search.CODE <= prior_event < START):
        raise ValueError('Expected the installed native Warehouse Search/Clear wrapper')
    if already:
        old_dispatch=struct.unpack_from('<I',data,LEGACY)[0]
        if not search.CODE <= old_dispatch < START:
            raise ValueError('Warehouse Search/Clear fallback changed')
    else:
        old_dispatch = prior_event + 29 + struct.unpack_from('<i',data,prior_event+25)[0]
        if data[prior_event:prior_event+len(search._event(prior_event,old_dispatch))] != search._event(prior_event,old_dispatch):
            raise ValueError('Native Warehouse event wrapper changed')
    if not already and any(data[START:END]):
        raise ValueError('Warehouse control code region is occupied')
    blocks = [(EVENT,search._event(EVENT,DISPATCH)),(DISPATCH,dispatch_code(old_dispatch)),
              (PLACE,place_code()),(LAYOUT,layout_code()),(TARGET,target_code()),(INIT,init_code()),(SHOW,show_code()),
              (TEXT,bytes(string_data)),(LAST,struct.pack('<I',1)),(SCALE,struct.pack('<d',1)),
              (TWENTY_FIVE,struct.pack('<d',25)),(LEGACY,struct.pack('<I',old_dispatch))]
    for (base,code),(limit,_) in zip(blocks,blocks[1:]+[(END,b'')]):
        if base+len(code)>limit:
            raise ValueError('Warehouse control routine exceeds reserved region')
        if already and data[base:base+len(code)] != code:
            raise ValueError('Installed Warehouse control routine changed')
        data[base:base+len(code)] = code
    data[search.EVENT_SITE:search.EVENT_SITE+10] = b'\xe9'+struct.pack('<i',EVENT-search.EVENT_SITE-5)+b'\x90'*5
    init_new = b'\xe8'+struct.pack('<i',INIT-INIT_SITE-5)+b'\x90'
    if data[INIT_SITE:INIT_SITE+6] != (init_new if already else bytes.fromhex('ff9098050000')):
        raise ValueError('Warehouse initial button-state call changed')
    data[INIT_SITE:INIT_SITE+6] = b'\xe8'+struct.pack('<i',INIT-INIT_SITE-5)+b'\x90'
    if data[SHOW_SITE:SHOW_SITE+5] != b'\xe9'+struct.pack('<i',(SHOW if already else 0x947ddd)-SHOW_SITE-5):
        raise ValueError('Warehouse visible completion changed')
    data[SHOW_SITE:SHOW_SITE+5] = b'\xe9'+struct.pack('<i',SHOW-SHOW_SITE-5)
    for site in MAP_SITES:
        expected = bytes.fromhex('8b05')+struct.pack('<i',0x1315e20-site-6)+bytes.fromhex('418bdc85c0781483f8017e0a83f802750a8d5801eb05bb01000000')
        replacement=(b'\xe8'+struct.pack('<i',TARGET-site-5)+bytes.fromhex('89c3')).ljust(33,b'\x90')
        if data[site:site+33] != (replacement if already else expected):
            raise ValueError('Native Warehouse deposit mapping changed')
        data[site:site+33] = replacement
    return bytes(data)


def patch_layout(root):
    personal=root.find("Widget[@name='personal_container']")
    title=personal.find("Widget[@name='personal_title']")
    flags=[v for v in title.get('flag','').split(';') if v]
    title.set('flag',';'.join(dict.fromkeys(flags+['not_active'])))
    if personal.find(f"Widget[@name='{CHARACTER_BUTTON}']") is None:
        # Append to preserve every existing ordinal selector in saved UI paths.
        # The native arrow sits beside the existing live Warehouse/count label.
        personal.append(ET.Element('Widget',dict(type='button',name=CHARACTER_BUTTON,
            frame='550,0,25,25',preset='v5_inv_tab_button2')))
    return root


def validate_layout(root):
    expected={'personal_container':'0,0,584,615','personal_warehouse':'15,58,560,240',
              'account_title_button':'0,307,584,25','account_container':'0,334,584,276'}
    for name,frame in expected.items():
        node=root.find(f".//Widget[@name='{name}']")
        if node is None or node.get('frame') != frame:
            raise ValueError('Warehouse layout changed; review accordion geometry: '+name)


def money_hitbox(root):
    money=root.find(".//Widget[@name='money']")
    if money is None:
        raise ValueError('Native Kinah label missing')
    flags=[v for v in money.get('flag','').split(';') if v]
    money.set('flag',';'.join(dict.fromkeys(flags+['not_active'])))
    return root


def patch_archive(path, localized=False):
    from codec import read_pak, binary_xml, encode_binary_xml
    from prepare_client import rebuild, parts
    raw=path.read_bytes()
    prefix='ui/game/' if localized else ''
    changed={}
    with read_pak(path) as archive:
        infos={i.filename:i for i in archive.infolist()}
        for name in ['warehouse_dialog.xml','inventory_dialog.xml','inventory_dialog_new.xml']:
            entry=prefix+name
            root=binary_xml(archive.read(entry))
            money_hitbox(root)
            if name=='warehouse_dialog.xml':
                validate_layout(root)
                patch_layout(root)
            changed[entry]=encode_binary_xml(root)
    result,_=rebuild(raw,changed,infos)
    before,_,_,_=parts(raw)
    after,_,_,_=parts(result)
    if list(before)!=list(after) or any(before[n][0]!=after[n][0] for n in before if n not in changed):
        raise ValueError('Unrelated compressed UI entry changed')
    return result


def stage_catalogue(client, output):
    """Refresh the affected ordinal catalogue rows and their normal signed cohort."""
    import hashlib
    import importlib.util
    from pathlib import Path
    import subprocess
    import sys
    mods=Path(__file__).resolve().parent.parent
    sys.path.insert(0,str(mods/'aetherfall-core'))
    from codec import read_pak
    from ui_registry import refresh_layouts
    spec=importlib.util.spec_from_file_location('warehouse_plugin_archive',mods/'quest-red-journal/prepare.py')
    archive_tools=importlib.util.module_from_spec(spec);spec.loader.exec_module(archive_tools)
    relative='Plugin/RelicCalc/RelicCalc.pak'
    source=output/relative if (output/relative).exists() else client/relative
    entries={}
    for rel,prefix in [('Data/ui/game/game.pak',''),('L10N/enu/data/data.pak','ui/game/')]:
        entries[rel]=[prefix+n for n in ['warehouse_dialog.xml','inventory_dialog.xml','inventory_dialog_new.xml']]
    with read_pak(source) as archive:
        if 'AetherfallRegistry.lua' not in archive.namelist():
            raise ValueError('Expected the current native UI catalogue')
        registry,report=refresh_layouts(archive.read('AetherfallRegistry.lua'),output,entries)
    prior=source.read_bytes()
    payload=archive_tools.extend_archive(prior,{'AetherfallRegistry.lua':registry},{})
    before,after=archive_tools.parts(prior)[0],archive_tools.parts(payload)[0]
    if list(before)!=list(after) or any(before[n][0]!=after[n][0] for n in before if n!='AetherfallRegistry.lua'):
        raise ValueError('Unrelated signed plugin member changed')
    target=output/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(payload)
    subprocess.run(['java',str(mods/'transmog-menu/SignClientPackages.java'),str(client),str(output)],check=True)
    subprocess.run(['java',str(mods/'season-pass/VerifySignatures.java'),str(output),str(client)],check=True)
    if (output/'Pub.key').read_bytes() != (client/'Pub.key').read_bytes():
        raise ValueError('Native model key changed')
    return report
