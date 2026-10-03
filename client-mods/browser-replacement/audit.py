"""Read-only inventory of the Aion browser ABI, including unpacked live imports."""
import argparse
import ctypes as c
import hashlib
import json
from pathlib import Path
import re
import struct


class PE:
    def __init__(self, data, mapped=False):
        self.data, self.mapped = data, mapped
        pe = self.u32(60)
        if data[pe:pe+4] != b'PE\0\0' or self.u16(pe+4) != 0x8664:
            raise ValueError('Expected x64 PE')
        self.optional = pe+24
        if self.u16(self.optional) != 0x20b:
            raise ValueError('Expected PE32+')
        self.image_size = self.u32(self.optional+56)
        table = self.optional+self.u16(pe+20)
        self.sections = [struct.unpack_from('<8sIIIIIIHHI', data, table+40*i)
                         for i in range(self.u16(pe+6))]

    def u16(self, offset): return struct.unpack_from('<H', self.data, offset)[0]
    def u32(self, offset): return struct.unpack_from('<I', self.data, offset)[0]
    def u64(self, offset): return struct.unpack_from('<Q', self.data, offset)[0]

    def offset(self, rva):
        if self.mapped or rva < self.u32(self.optional+60): return rva
        for _, _, address, size, raw, *_ in self.sections:
            if address <= rva < address+size: return raw+rva-address
        raise ValueError(f'Unmapped RVA {rva:x}')

    def string(self, rva):
        start = self.offset(rva)
        end = self.data.index(b'\0', start, start+4096)
        return self.data[start:end].decode('ascii')

    def exports(self):
        directory = self.u32(self.optional+112)
        if not directory: return {}
        at = self.offset(directory)
        count, functions, names, ordinals = struct.unpack_from('<IIII', self.data, at+24)
        result = {}
        for i in range(count):
            name = self.string(self.u32(self.offset(names)+4*i))
            ordinal = self.u16(self.offset(ordinals)+2*i)
            result[name] = self.u32(self.offset(functions)+4*ordinal)
        return result

    def imports(self):
        directory = self.u32(self.optional+120)
        if not directory: return {}
        result = {}
        at = self.offset(directory)
        while any(self.data[at:at+20]):
            lookup, _, _, name, iat = struct.unpack_from('<IIIII', self.data, at)
            symbols = []
            if lookup:
                offset = self.offset(lookup)
                for i in range(8192):
                    value = self.u64(offset+8*i)
                    if not value: break
                    symbols.append('#'+str(value&0xffff) if value>>63 else self.string(value+2))
            result[self.string(name)] = dict(symbols=symbols, iatRva=iat)
            at += 20
        return result


def live_bindings(pid, browser_exports):
    k = c.WinDLL('kernel32', use_last_error=True)
    k.OpenProcess.argtypes = [c.c_ulong, c.c_int, c.c_ulong]
    k.OpenProcess.restype = c.c_void_p
    k.ReadProcessMemory.argtypes = [c.c_void_p, c.c_void_p, c.c_void_p, c.c_size_t, c.POINTER(c.c_size_t)]
    k.CloseHandle.argtypes = [c.c_void_p]
    p = c.WinDLL('psapi', use_last_error=True)
    p.EnumProcessModulesEx.argtypes = [c.c_void_p, c.POINTER(c.c_void_p), c.c_ulong, c.POINTER(c.c_ulong), c.c_ulong]
    p.GetModuleBaseNameW.argtypes = [c.c_void_p, c.c_void_p, c.c_wchar_p, c.c_ulong]
    h = k.OpenProcess(0x410, False, pid)
    if not h: raise c.WinError(c.get_last_error())
    try:
        handles = (c.c_void_p*2048)()
        size = c.c_ulong()
        if not p.EnumProcessModulesEx(h, handles, c.sizeof(handles), c.byref(size), 3):
            raise c.WinError(c.get_last_error())
        modules = {}
        for base in handles[:size.value//c.sizeof(c.c_void_p)]:
            name = c.create_unicode_buffer(512)
            if p.GetModuleBaseNameW(h, base, name, 512): modules[name.value.lower()] = base
        if 'game.dll' not in modules or 'awesomium.dll' not in modules:
            raise ValueError('Live process has no Game.dll/Awesomium.dll pair')
        def read(address, length):
            buffer = c.create_string_buffer(length)
            got = c.c_size_t()
            if not k.ReadProcessMemory(h, address, buffer, length, c.byref(got)) or got.value != length:
                raise c.WinError(c.get_last_error())
            return buffer.raw
        head = read(modules['game.dll'], 4096)
        image_size = PE(head, mapped=True).image_size
        # Only image pages are read, not character/account data in the heap.
        chunks = []
        for offset in range(0, image_size, 4096):
            length = min(4096, image_size-offset)
            try: chunks.append(read(modules['game.dll']+offset, length))
            except OSError: chunks.append(bytes(length))
        data = b''.join(chunks)
        names = sorted({v.decode() for v in re.findall(rb'awe_[a-z0-9_]+', data)})
        live_browser_head = read(modules['awesomium.dll'], 4096)
        browser_size = PE(live_browser_head, mapped=True).image_size
        mapped_browser = read(modules['awesomium.dll'], browser_size)
        browser_exports = PE(mapped_browser, mapped=True).exports()
        references = {}
        for name, rva in browser_exports.items():
            address = struct.pack('<Q', modules['awesomium.dll']+rva)
            matches = [m.start() for m in re.finditer(re.escape(address), data)]
            if matches: references[name] = [hex(at) for at in matches]
        pointers = []
        for at in range(0, len(data)-7, 8):
            value = struct.unpack_from('<Q', data, at)[0]
            if modules['awesomium.dll'] <= value < modules['awesomium.dll']+browser_size:
                pointers.append(dict(gameRva=hex(at), browserRva=hex(value-modules['awesomium.dll'])))
        return dict(pid=pid, resolvedPointers=references, symbolStrings=names,
                    browserPointers=pointers, liveExports=browser_exports)
    finally:
        k.CloseHandle(h)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client', type=Path, required=True)
    parser.add_argument('--pid', type=int)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    bin_path = args.client.resolve()/'bin64'
    browser = (bin_path/'Awesomium.dll').read_bytes()
    exports = PE(browser).exports()
    report = dict(client=str(args.client.resolve()), awesomiumSha256=hashlib.sha256(browser).hexdigest(),
                  exports=exports, consumers={})
    for name in ('Game.dll', 'AionIconBridge.dll'):
        data = (bin_path/name).read_bytes()
        report['consumers'][name] = dict(sha256=hashlib.sha256(data).hexdigest(),
            imports=PE(data).imports(), symbolStrings=sorted({s.decode() for s in re.findall(rb'awe_[a-z0-9_]+', data)}))
    if args.pid: report['live'] = live_bindings(args.pid, exports)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2)+'\n')
    used = set(report.get('live', {}).get('resolvedPointers', {}))
    print('OK: read-only browser ABI inventory;', len(exports), 'exports;', len(used), 'live bindings')
    print('\n'.join(sorted(used)))


if __name__ == '__main__': main()
