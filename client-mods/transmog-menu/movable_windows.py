"""Release the three detached 4.8 NA windows from automatic UI alignment."""
import copy
import io
import zipfile
from codec import binary_xml, encode_binary_xml, read_pak, encode_pak

WINDOWS = {
    'inventory_dialog.xml': 'dlg_inventory',
    'player_info_dialog.xml': 'dlg_player_info',
    'warehouse_dialog.xml': 'dlg_warehouse',
}


def patch_layout(root):
    if root.get('type') not in WINDOWS.values():
        raise ValueError('Unexpected movable dialog type')
    # The native constructor's default alignment is 5 (unmanaged). XML left,
    # right and task opt into the stock automatic window arrangement instead.
    root.attrib.pop('align_type', None)
    flags = [s for s in root.get('flag', '').split(';') if s and s not in ('movable', 'not_movable')]
    if 'title' not in flags:
        raise ValueError('Expected a native draggable title bar')
    root.set('flag', ';'.join(flags + ['movable']))
    return root


def patch_archive(path, prefix=''):
    with read_pak(path) as archive:
        infos=archive.infolist();comment=archive.comment
        original={n.filename:archive.read(n.filename) for n in infos}
    if len(infos)!=len(original):raise ValueError('Duplicate UI archive entry')
    updated=dict(original)
    for name,kind in WINDOWS.items():
        entry=prefix+name
        root=binary_xml(original[entry])
        if root.get('type')!=kind:raise ValueError('Unexpected dialog '+entry)
        updated[entry]=encode_binary_xml(patch_layout(root))
    output=io.BytesIO()
    with zipfile.ZipFile(output,'w') as archive:
        archive.comment=comment
        for info in infos:archive.writestr(copy.copy(info),updated[info.filename])
    return bytes(encode_pak(output.getvalue()))
