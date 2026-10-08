"""Prepare or collect a bounded, read-only trace of the custom-window queues.

Preparation and analysis do not open a process. Capture is a separate, explicit
command for a user-authorized gameplay test; it never launches or injects into
Aion, calls native game functions, or changes installed files.
"""
import argparse
import ctypes as c
import hashlib
import json
import os
import re
from pathlib import Path
import struct
import time

DEV_ROOT = Path(os.environ.get('AION_DEV_ROOT',
    'D:/Proiecte/Project Restructure/Aion Development Workspace'))
COUNTERS = {'uiCommands': 0x137e288, 'browserCommands': 0x130b938,
            'browserEvents': 0x130b960}
ADDON_TREE = 0x137e1e8
TOKEN = 0x130c8f0
DIALOGS = 0x13875c0
UI_QUEUE = 0x137e270
GAME_STATE = 0x1312fd0
GUARDS = {0x629050: 0x20, 0x629190: 0x20, 0x623790: 0x30,
          0x12cc80: 0x20, 0x131ef0: 0x10, 0x61e580: 0x10}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def output_path(path):
    path = path.resolve()
    if not path.is_relative_to(DEV_ROOT.resolve()):
        raise ValueError('Write diagnostics only under AION_DEV_ROOT')
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def prepare(client, output):
    # Existing parser reads PE sections; it does not load the native DLL.
    import sys
    sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'browser-replacement'))
    from audit import PE
    client = client.resolve()
    dll = client / 'bin64/Game.dll'
    data = dll.read_bytes()
    pe = PE(data)
    original = client / 'bin64/game.dll.orig'
    if sha(original) != '5334cf2164468678e45fe1a5decf58a0fbc4fd7f22cfdcbb87d28edce8d2c11c':
        raise ValueError('Unsupported original client build')
    # Addresses above are derived from these stock queue producers/consumers.
    # Refuse subsequent changes to the relevant native code, except the two
    # existing, inventoried browser hooks which are captured as loaded guards.
    stock = original.read_bytes()
    for rva, length in list(GUARDS.items())[:4]:
        at = pe.offset(rva)
        if data[at:at+length] != stock[rva:rva+length]:
            raise ValueError(f'Queue implementation changed at {rva:x}')
    references = {0x629069: COUNTERS['uiCommands'],
                  0x12cd21: COUNTERS['browserCommands'],
                  0x131fe0: COUNTERS['browserEvents'], 0x626bd5: ADDON_TREE}
    for instruction, target in references.items():
        at = pe.offset(instruction)
        code = data[at:at+7]
        if code != stock[instruction:instruction+7] or instruction+7+struct.unpack('<i', code[3:])[0] != target:
            raise ValueError(f'Queue reference changed at {instruction:x}')
    guard_sizes = dict(GUARDS)
    guard_sizes.update({at: 7 for at in references})
    manifest = dict(client=str(client), gameSha256=sha(dll),
        guards={str(rva): data[pe.offset(rva):pe.offset(rva)+size].hex()
                for rva, size in guard_sizes.items()},
        status='prepared only; no process opened or client launched')
    output_path(output).write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    return manifest


class Process:
    def __init__(self, pid, manifest):
        self.k = c.WinDLL('kernel32', use_last_error=True)
        self.p = c.WinDLL('psapi', use_last_error=True)
        self.k.OpenProcess.argtypes = [c.c_ulong, c.c_int, c.c_ulong]
        self.k.OpenProcess.restype = c.c_void_p
        self.k.ReadProcessMemory.argtypes = [c.c_void_p, c.c_void_p, c.c_void_p,
                                             c.c_size_t, c.POINTER(c.c_size_t)]
        self.k.CloseHandle.argtypes = [c.c_void_p]
        self.p.EnumProcessModulesEx.argtypes = [c.c_void_p, c.POINTER(c.c_void_p),
                                                c.c_ulong, c.POINTER(c.c_ulong), c.c_ulong]
        self.p.GetModuleFileNameExW.argtypes = [c.c_void_p, c.c_void_p, c.c_wchar_p, c.c_ulong]
        self.handle = self.k.OpenProcess(0x410, False, pid)  # query + read only
        if not self.handle:
            raise c.WinError(c.get_last_error())
        try:
            modules = (c.c_void_p * 2048)()
            needed = c.c_ulong()
            if not self.p.EnumProcessModulesEx(self.handle, modules, c.sizeof(modules), c.byref(needed), 3):
                raise c.WinError(c.get_last_error())
            if needed.value > c.sizeof(modules):
                raise ValueError('Module list exceeds bounded capacity')
            paths = {}
            for base in modules[:needed.value // c.sizeof(c.c_void_p)]:
                name = c.create_unicode_buffer(32768)
                if self.p.GetModuleFileNameExW(self.handle, base, name, len(name)):
                    paths[Path(name.value).resolve()] = base
            client = Path(manifest['client'])
            if not any(p.name.lower() in ('aion.bin', 'aion.exe') and p.is_relative_to(client) for p in paths):
                raise ValueError('PID is not the selected Aion installation')
            dll = (client / 'bin64/Game.dll').resolve()
            if dll not in paths or sha(dll) != manifest['gameSha256']:
                raise ValueError('Client changed since preparation; refresh inventory and prepare again')
            self.base = paths[dll]
            for rva, expected in manifest['guards'].items():
                expected = bytes.fromhex(expected)
                if self.read(self.base + int(rva), len(expected)) != expected:
                    raise ValueError(f'Loaded queue/hook differs from disk at {int(rva):x}')
        except BaseException:
            self.close()
            raise

    def close(self):
        if self.handle:
            self.k.CloseHandle(self.handle)
            self.handle = None

    def read(self, address, size):
        data = c.create_string_buffer(size)
        got = c.c_size_t()
        if not self.k.ReadProcessMemory(self.handle, address, data, size, c.byref(got)) or got.value != size:
            raise OSError('Process memory became unavailable; capture stopped')
        return data.raw

    def u64(self, address):
        return struct.unpack('<Q', self.read(address, 8))[0]


def snapshot(process):
    b = process.base
    sample = {name: process.u64(b + rva) for name, rva in COUNTERS.items()}
    if any(n > 100000 for n in sample.values()):
        raise ValueError('Queue count outside known bounds; refusing an unsupported layout')
    # Only record whether a key exists. Never serialize the token or a URL/query.
    sample['sessionKeyPresent'] = any(process.read(b + TOKEN, 16))
    sample['gameState'] = struct.unpack('<I', process.read(b + GAME_STATE, 4))[0]
    sample['uiHeadCommand'] = None
    sample['uiHeadIndex'] = None
    sample['uiHeadWidget'] = None
    if sample['uiCommands']:
        table, capacity, index = struct.unpack('<QQQ', process.read(b + UI_QUEUE, 24))
        if table and 0 < capacity <= 131072 and index < capacity:
            command = process.u64(table + index * 8)
            if command:
                opcode = struct.unpack('<I', process.read(command, 4))[0]
                # Queue storage can advance between reads. Keep only bounded,
                # stable opcode metadata; never read command arguments/URLs.
                if (process.read(b + UI_QUEUE, 24) == struct.pack('<QQQ', table, capacity, index)
                        and opcode <= 0x5f):
                    sample['uiHeadCommand'] = opcode
                    sample['uiHeadIndex'] = index
                    if opcode in (18, 21):
                        arguments = process.u64(command + 0x10)
                        end = process.u64(command + 0x18)
                        if arguments and end - arguments >= 80:
                            def bounded_string(at):
                                length, size = struct.unpack('<QQ', process.read(at + 0x18, 16))
                                if not 0 < length <= 80 or not length <= size <= 4096:
                                    return ''
                                address = process.u64(at + 8) if size >= 16 else at + 8
                                return process.read(address, length).decode('ascii', errors='replace')
                            if bounded_string(arguments) == 'RelicCalc':
                                name = bounded_string(arguments + 40)
                                if re.fullmatch(r'(Wardrobe|Private|PlayerBotBar)[A-Za-z0-9_]*', name):
                                    sample['uiHeadWidget'] = name
    sample['visibleAddonSlots'] = [slot for slot in range(0x20e, 0x222)
        if (widget := process.u64(b + DIALOGS + slot * 8)) and process.u64(widget + 0x30) & 1]
    # The stock addon map uses a sentinel red-black tree and 40-byte strings.
    # Match only the exact known addon name; never serialize other addon names.
    head = process.u64(b + ADDON_TREE)
    sample['relicEvents'] = None
    if head:
        pending, visited = [process.u64(head + 8)], set()
        while pending:
            node = pending.pop()
            if not node or node == head:
                continue
            if node in visited or len(visited) >= 64:
                raise ValueError('Addon tree changed during capture or exceeds supported bounds')
            visited.add(node)
            if process.read(node + 0x49, 1) != b'\0':
                continue
            pending.extend((process.u64(node), process.u64(node + 0x10)))
            length, capacity = process.u64(node + 0x30), process.u64(node + 0x38)
            if length == len('RelicCalc') and length <= capacity <= 4096:
                address = process.u64(node + 0x20) if capacity >= 16 else node + 0x20
                if process.read(address, length) == b'RelicCalc':
                    addon = process.u64(node + 0x40)
                    sample['relicEvents'] = process.u64(addon + 0x28) if addon else None
                    if sample['relicEvents'] is not None and sample['relicEvents'] > 100000:
                        raise ValueError('Addon event count outside known bounds')
    return sample


def summary(samples):
    result = {}
    for name in (*COUNTERS, 'relicEvents'):
        values = [(s['ms'], s[name]) for s in samples if s.get(name) is not None]
        result[name] = dict(observed=len(values), peak=max((v for _, v in values), default=None))
        start = None
        longest = 0
        for ms, value in values:
            if value and start is None:
                start = ms
            if start is not None:
                longest = max(longest, ms - start)
                if not value:
                    start = None
        result[name]['longestNonemptyMs'] = longest
    result['interpretation'] = ('Queue samples locate a stalled stage, not its cause. '
        'Zero depth does not prove a handler is idle: it may already be executing.')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--prepare', type=Path, metavar='CLIENT')
    mode.add_argument('--capture', type=Path, metavar='MANIFEST')
    mode.add_argument('--analyze', type=Path, metavar='TRACE')
    parser.add_argument('--pid', type=int)
    parser.add_argument('--seconds', type=float, default=45)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.prepare:
        if not args.output:
            parser.error('--prepare requires --output')
        prepare(args.prepare, args.output)
        print('OK: read-only timing capture prepared; neither Aion nor GameServer was opened')
    elif args.analyze:
        print(json.dumps(summary(json.loads(args.analyze.read_text())['samples']), indent=2))
    else:
        if not args.pid or not args.output or not 1 <= args.seconds <= 120:
            parser.error('--capture requires --pid, --output and a duration of 1..120 seconds')
        output = output_path(args.output)
        manifest = json.loads(args.capture.read_text())
        process = Process(args.pid, manifest)
        samples, error = [], None
        started = time.monotonic()
        print('Capture started. Open one affected window now; no game functions will be called.', flush=True)
        try:
            while time.monotonic() - started < args.seconds:
                sample = snapshot(process)
                sample['ms'] = round((time.monotonic() - started) * 1000)
                samples.append(sample)
                time.sleep(.1)
        except (OSError, ValueError) as exc:
            error = str(exc)
        finally:
            process.close()
        output.write_text(json.dumps(dict(gameSha256=manifest['gameSha256'], samples=samples,
            error=error, summary=summary(samples)), indent=2), encoding='utf-8')
        print(('STOPPED: ' + error) if error else 'OK: timing capture saved')
        if error:
            raise SystemExit(1)


if __name__ == '__main__':
    main()
