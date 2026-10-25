"""Connect the native Account list selection to its existing BM long description.

No new benefit data or global tooltip hook: the first name cell's native user-data
slot carries the existing localized long-description pointer. Live Account rows
come from the native 12-entry pack array, not the separate Legion/security buff map.
"""
import hashlib
import struct
from account_binary import Assembler

RENDER, RENDER_END = 0x144e9c0, 0x144eb00
EVENT, EVENT_END = 0x144ef20, 0x144f000
PACK_ARRAY = 0x1392f48  # 40-byte entries: name at -8, long description at +0
LEGACY_RENDER_SHA256 = '6ed03d421d8c3fc8bfbe56a17b51e87dbfa3ca7e19c13a67ee2502b1e18b14a0'


def render_code():
    a = Assembler(RENDER)
    def emit(code): a.emit(bytes.fromhex(code))
    def jump(op, label): a.branch(bytes.fromhex(op), label)
    # rcx = Profile, rdx = the original HTML details widget.
    emit('5356574883ec204889d6')
    emit('4885f6'); jump('0f84', 'done')
    emit('488bb9b80800004885ff'); jump('0f84', 'empty')
    emit('8b974c03000083faff'); jump('0f85', 'find')
    # Select the first real native row on opening. Do not emit a nested change.
    emit('488b87f8040000483b8700050000'); jump('0f84', 'empty')
    emit('4885c0'); jump('0f84', 'empty')
    emit('8b104889f94531c0')
    a.relative(b'\xe8', 0x50eab0)
    emit('8b974c030000')
    a.label('find')
    emit('488b87f80400004c8b87000500004885c0'); jump('0f84', 'empty')
    a.label('row')
    emit('4c39c0'); jump('0f83', 'empty')
    emit('3910'); jump('0f84', 'found')
    emit('4805d0000000'); jump('e9', 'row')
    a.label('found')
    emit('488b48184885c9'); jump('0f84', 'empty')
    emit('483b4820'); jump('0f83', 'empty')
    # Native name-cell user data now holds this row's existing localized text.
    emit('488b91880000004885d2'); jump('0f85', 'text')
    a.label('empty')
    # Existing native empty localized string; never keep stale boon details.
    a.relative(bytes.fromhex('488b15'), 0x12eda20)
    a.label('text')
    emit('4889f1488b06ff9090020000')
    a.label('done')
    emit('4883c4205f5e5bc3')
    return a.finish()


def event_code():
    a = Assembler(EVENT)
    # This is called only by the Profile's existing Account event handler slot.
    a.emit(bytes.fromhex('81fa79050000'))
    a.branch(bytes.fromhex('0f85'), 'original')
    a.emit(bytes.fromhex('4d85c0'))
    a.branch(bytes.fromhex('0f84'), 'original')
    a.emit(bytes.fromhex('498b00483b81b8080000'))
    a.branch(bytes.fromhex('0f85'), 'original')
    a.emit(bytes.fromhex('4883ec28488b91c0080000'))
    a.relative(b'\xe8', RENDER)
    a.emit(bytes.fromhex('b8010000004883c428c3'))
    a.label('original')
    a.relative(b'\xe9', 0x837960)
    a.label('insert')
    a.emit(bytes.fromhex('4c8b4500'))  # first-cell data = native pack long description
    a.relative(b'\xe9', 0x50b7d0)
    return a.finish(), EVENT + a.labels['insert']


def patch(source):
    data = bytearray(source)
    pe = struct.unpack_from('<I', data, 60)[0]
    if (data[pe:pe+4] != b'PE\0\0' or struct.unpack_from('<H', data, pe+4)[0] != 0x8664
            or struct.unpack_from('<I', data, pe+8)[0] != 0x561c7ce5
            or struct.unpack_from('<Q', data, pe+48)[0] != 0x10000000):
        raise ValueError('Unsupported native Account client build')
    renderer = render_code().ljust(RENDER_END - RENDER, b'\0')
    event, insert = event_code()
    # Each edit records the stock input and the prior installed (failed) version.
    # Accept complete known cohorts only; reject partial or unrelated changes.
    legacy_event = event[:-9] + bytes.fromhex('448b4500') + event[-5:]
    edits = [
        (EVENT, bytes(len(event)), legacy_event, event),
        # Native selection was explicitly disabled by the retail Account panel.
        (0x836697, bytes.fromhex('89b040050000'), bytes.fromhex('89a840050000'), bytes.fromhex('89a840050000')),
        # This is the actual pack-array tooltip branch, observed on all four rows.
        (0x836c7a, bytes.fromhex('4c8b4500'), bytes.fromhex('4c8b4500'), bytes.fromhex('4c8b45f8')),
        # Restore the separate retail Legion/security buff path changed by v1.
        (0x8370c6, bytes.fromhex('448b4114'), bytes.fromhex('448b4110'), bytes.fromhex('448b4114')),
    ]
    for site, opcode, stock, prior, target in [
        (0x8369af, b'\xe9', 0x8372e0, RENDER, RENDER),
        (0x8340ff, b'\xe8', 0x837960, EVENT, EVENT),
        (0x836b07, b'\xe8', 0x50b7d0, 0x50b7d0, insert),
        (0x836ed6, b'\xe8', 0x50b7d0, insert, 0x50b7d0),
    ]:
        def branch(t): return opcode + struct.pack('<i', t - site - 5)
        edits.append((site, branch(stock), branch(prior), branch(target)))
    if len(render_code()) > RENDER_END - RENDER or len(event) > EVENT_END - EVENT:
        raise ValueError('Account details exceeds its verified unused padding')
    current_renderer = bytes(data[RENDER:RENDER_END])
    cohorts = [(1, current_renderer == bytes(len(renderer))),
               (2, hashlib.sha256(current_renderer).hexdigest() == LEGACY_RENDER_SHA256),
               (3, current_renderer == renderer)]
    def matches_cohort(which):
        return all(data[entry[0]:entry[0]+len(entry[-1])] == entry[which] for entry in edits)
    matches = [which for which, valid in cohorts if valid and matches_cohort(which)]
    if not matches:
        raise ValueError('Unknown or partial native Account details cohort')
    data[RENDER:RENDER_END] = renderer
    for at, _, _, new in edits:
        data[at:at+len(new)] = new
    return bytes(data)
