"""Verify the supported native journal ABI and emit exact startup patch metadata.

No live process access. The native bridge applies the verified instructions during
normal client startup, keeping Game.dll and its graphics/cursor recovery cohort.
"""
import hashlib
import json
import struct
from pathlib import Path

HERE = Path(__file__).resolve().parent
CAPACITY = 2048  # 1,000 ordinary quests plus native campaign/locked records.
FIXED = ((0x3f616d, 6), (0x3f6394, 6), (0x3f7120, 17), (0x3f2db5, 5),
         (0x9c4d0, 8), (0x3f6120, 8), (0x3f6350, 8), (0xc98f78 + 0x438, 8))
# DlgQuest / DlgQuestOther virtual getters (+0x670/+0x678) are leaf functions:
# they have no .pdata unwind entries and must not disappear from the relocation
# metadata when references are collected from exception-table function bounds.
NATIVE_VIEWS = {
    0x851b27: ('488d0d320cb200', 'primary'),
    0x851b58: ('488d05010cb200', 'primary'),
    0x851cb7: ('488d0d7e1bb200', 'secondary'),
    0x851ce8: ('488d054d1bb200', 'secondary'),
}
# Native view = {count, records, auxiliaryMap}, not {count, begin, end}.
# DlgQuest::Refresh (0x849940) traverses auxiliaryMap's tree at +8. These
# objects happen to follow the old arrays, but remain owned by the manager.
NATIVE_AUXILIARY_MAPS = {
    0x851b1b: '488d05de1cb200',
    0x851b39: '488d05c01cb200',
    0x851cab: '488d052e2cb200',
    0x851cc9: '488d05102cb200',
    0x7fecf6: '488d05034bb700',
}


def generate(client, output):
    import pefile
    path = client / 'bin64/Game.dll'
    raw = path.read_bytes()
    pe = pefile.PE(data=raw, fast_load=True)
    data = pe.get_memory_mapped_image()
    sites = json.loads((HERE / 'quest_journal_sites.json').read_text())
    indexed = {row['rva']: row for row in sites}
    if len(indexed) != len(sites):
        raise ValueError('Duplicate native quest journal relocation')
    for at, (before, kind) in NATIVE_VIEWS.items():
        row = indexed.get(at)
        if not row or row['before'] != before or row['kind'] != kind or row['offset'] != 3:
            raise ValueError(f'Native quest-window getter relocation is missing or changed at {at:#x}')
    for at in NATIVE_AUXILIARY_MAPS:
        if at in indexed:
            raise ValueError(f'Native quest auxiliary-map pointer must not be relocated at {at:#x}')
    rows = []
    kinds = {'primary': 0, 'secondary': 1, 'primary-relative': 2, 'secondary-relative': 3, 'capacity': 4}
    for row in sites:
        at, old = row['rva'], bytes.fromhex(row['before'])
        if data[at:at + len(old)] != old:
            raise ValueError(f'Native quest journal instruction changed at {at:#x}')
        if row['offset'] + 4 > len(old):
            raise ValueError('Quest journal operand exceeds its instruction')
        padded = old + bytes(16 - len(old))
        rows.append('    {0x%x,%d,%d,%d,0x%x,{%s}},' %
                    (at, len(old), row['offset'], kinds[row['kind']], row['target'], ','.join('0x%02x' % v for v in padded)))
    checks = {
        # Native Windows-message owner runs before shortcut filtering (0x16f710)
        # and F's normal interact action. The old dialog OnInput hook ran too late.
        0x296ee: 'e87d955200',
        0x552c70: '488bc44881ec88000000488958f8488970e8',
        0x3f616d: '4983f90e7230', 0x3f6394: '4883f90e737d', 0x63bf70: '4883f90e722a',
        # Tracker row fill: htmltext SetText (call [rax+0x290]) and the quest id still in [rbx] after it.
        0x85b608: 'ff9090020000', 0x85b619: '448b1b',
        # Tracker tab limits (0x854d10): checked tab trim branch + stored limit 14, history trim branch + limit 7.
        0x85526b: '0f8ef7010000', 0x855468: '41c785280500000e000000',
        # Tracker text queue: vector at +0x670 (begin +0x678, end +0x680) filled by the native insert 0x823e0
        # (0x85c651) and refilled then emptied by 0x85b230 (0x85b72d); help_quest::requeue uses the same insert.
        0x85c651: '488b95800600004c8d4c2420488d8d7006000041b801000000e8715d82ff', 0x823e0: '488bc44881ec9800000048c7442458fe',
        0x85b72d: '4d8b8780060000498b8f78060000',
        0x855499: '0f8ee9010000', 0x855688: '41c7853805000007000000',
        0x3f7120: '4c8bdc4881ec880400004d85c049896bf0',
        0x3f2db5: 'e806634600', 0xc98f78 + 0x438: struct.pack('<Q', 0x104adce0).hex(),
        0x3fe4d5: '754b', 0x3fe53e: '0f8364030000', 0x400ce2: '7539', 0x400d3e: '4183f8078bc60f92c0',
        0xcd07b8 + 0x670: struct.pack('<Q', 0x10851b10).hex(),
        0xcd07b8 + 0x678: struct.pack('<Q', 0x10851b50).hex(),
        0xc5ae68 + 0x670: struct.pack('<Q', 0x10851ca0).hex(),
        0xc5ae68 + 0x678: struct.pack('<Q', 0x10851ce0).hex(),
        0x8590c0: '4883ec5848895c2460c781dc07000001000000',
        0x856bf0: '4883ec58f6c20248896c24684c89742440',
        0xcd9828 + 0x5a8: struct.pack('<Q', 0x10856bf0).hex(),
        0x8591f4: 'e8272b0000',
        0x859207: 'e8142b0000',
        0xcd07b8 + 0x480: struct.pack('<Q', 0x104ae130).hex(),
        0x4ae130: '4883ec4848895c246048897c2468',
        0x852bb0: '488bc44883ec7885d24889581848897820',
        **NATIVE_AUXILIARY_MAPS,
    }
    for at, expected in checks.items():
        if data[at:at + len(bytes.fromhex(expected))].hex() != expected:
            raise ValueError(f'Quest journal hook ABI differs at {at:#x}')
    lines = ['// Generated from exact supported Game.dll instructions; see quest_journal.py.',
             'struct JournalSite { size_t rva; unsigned size,offset,kind; size_t target; unsigned char before[16]; };',
             'constexpr JournalSite JOURNAL_SITES[]={', *rows, '};']
    (output / 'quest_journal_data.h').write_text('\n'.join(lines) + '\n')
    return dict(inputs={'bin64/Game.dll': hashlib.sha256(raw).hexdigest()}, capacity=CAPACITY,
                relocatedInstructions=len(sites), checkedHooks=len(checks), nativeFavorites=True,
                fixedScratchReplaced=True, existingUncheckedQuestsPreserved=True,
                nativeLeafViewAccessors=4, nativeAuxiliaryMapReferencesPreserved=5,
                nativeQuestArrivalNotification=True, nativeCheckedDestinationBeforeArrival=True,
                nativeTrackerUpdateOwner='0x856bf0', nativeArrivalDestinationHook='0x8591f4',
                nativeHistoryRowCallPreserved=True, animationControllerChanged=False,
                questDetailFollowsJournal='native OnRectChanged; original OnVisible placement retained')
