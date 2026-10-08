"""Interpret built x64 navigation bytes offline; never load or execute native code."""
import argparse
import json
from pathlib import Path
import struct
import sys

from capstone import Cs, CS_ARCH_X86, CS_MODE_64
from capstone.x86 import X86_OP_REG, X86_OP_IMM, X86_OP_MEM

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import patch_game_dll as p
from prepare_marketplace_session import ROUTES, RANGES, sha


class Cpu:
    def __init__(self, dll, url, token, view=7):
        self.mem = {}
        for start, end in RANGES:
            self.put(start, dll[start:end])
        self.put(p.MARKET_AUTH_RVA, b'\xe9'+struct.pack('<i', p.MARKET_AUTH_HOOK_RVA-p.MARKET_AUTH_RVA-5))
        self.put(0x100000, url.encode()+b'\0')
        self.put(0x200000+0x10, struct.pack('<Q', 0x210000))
        self.put(0x210000+0x340, struct.pack('<i', view))
        self.put(p.NATIVE_SECURITY_TOKEN_RVA, token)
        self.put(0x300000-0x200, bytes(0x400))
        self.regs = dict(rcx=0x200000, rdx=0x100000, rsp=0x300008)
        self.zf = self.cf = self.sf = False
        self.decoder = Cs(CS_ARCH_X86, CS_MODE_64)
        self.decoder.detail = True

    def put(self, address, data):
        self.mem.update({address+i: b for i, b in enumerate(data)})

    def read(self, address, size):
        return int.from_bytes(bytes(self.mem[address+i] for i in range(size)), 'little')

    def reg(self, name):
        if not name:
            return '', 8
        aliases = {'eax': ('rax', 4), 'al': ('rax', 1), 'ecx': ('rcx', 4), 'edx': ('rdx', 4)}
        if name in aliases:
            return aliases[name]
        if name.startswith('r') and name[-1:] in ('d', 'b') and name[1:-1].isdigit():
            return name[:-1], 4 if name[-1]=='d' else 1
        return name, 8

    def getreg(self, name):
        name, size = self.reg(name)
        return self.regs.get(name, 0) & ((1 << (8*size))-1)

    def setreg(self, name, value):
        name, size = self.reg(name)
        mask = (1 << (8*size))-1
        self.regs[name] = (self.regs.get(name, 0) & ~mask if size == 1 else 0) | (value & mask)

    def address(self, ins, op):
        m = op.mem
        base = ins.address+ins.size if ins.reg_name(m.base)=='rip' else self.getreg(ins.reg_name(m.base))
        return base+self.getreg(ins.reg_name(m.index))*m.scale+m.disp

    def value(self, ins, op):
        if op.type == X86_OP_IMM:
            return op.imm
        if op.type == X86_OP_REG:
            return self.getreg(ins.reg_name(op.reg))
        if op.type == X86_OP_MEM:
            return self.read(self.address(ins, op), op.size)
        raise AssertionError('Unsupported operand')

    def write(self, ins, op, value):
        if op.type == X86_OP_REG:
            self.setreg(ins.reg_name(op.reg), value)
        else:
            self.put(self.address(ins, op), (value & ((1 << (8*op.size))-1)).to_bytes(op.size, 'little'))

    def run(self, *, callback=False):
        pc = p.BROWSER_HOOK_RVA
        if callback:
            pc = p.MARKET_AUTH_HOOK_RVA
            self.regs.update(rcx=0x100000, rdx=7, r8=1)
        for _ in range(10000):
            if pc in (p.MARKET_AUTH_RVA+len(p.MARKET_AUTH_ORIGINAL), p.BROWSER_AUTH_RVA+len(p.BROWSER_PROLOGUE)):
                return 'pending', self.getreg('rcx'), self.getreg('rdx')
            code = bytes(self.mem.get(pc+i, 0) for i in range(15))
            ins = next(self.decoder.disasm(code, pc, count=1))
            pc += ins.size
            ops, name = ins.operands, ins.mnemonic
            if name in ('mov', 'movabs', 'movzx'):
                self.write(ins, ops[0], self.value(ins, ops[1]))
            elif name == 'lea':
                self.write(ins, ops[0], self.address(ins, ops[1]))
            elif name in ('cmp', 'test', 'xor', 'or', 'and', 'add', 'sub', 'inc', 'shr'):
                a = self.value(ins, ops[0])
                b = self.value(ins, ops[1]) if len(ops)>1 else 1
                v = {'cmp': lambda: a-b, 'test': lambda: a&b, 'xor': lambda: a^b,
                     'or': lambda: a|b, 'and': lambda: a&b, 'add': lambda: a+b,
                     'sub': lambda: a-b, 'inc': lambda: a+1, 'shr': lambda: a>>b}[name]()
                mask = (1 << (8*ops[0].size))-1
                self.zf, self.cf, self.sf = (v & mask)==0, a<b, bool(v & (1 << (8*ops[0].size-1)))
                if name not in ('cmp', 'test'):
                    self.write(ins, ops[0], v)
            elif name in ('jmp', 'je', 'jne', 'jb', 'js'):
                if {'jmp': True, 'je': self.zf, 'jne': not self.zf, 'jb': self.cf, 'js': self.sf}[name]:
                    pc = self.value(ins, ops[0])
                    if pc == p.BROWSER_LOAD_RVA:
                        return self.navigation()
            elif name == 'call':
                target = self.value(ins, ops[0])
                if target == p.BROWSER_LOAD_RVA:
                    assert self.getreg('rsp')%16 == 0, 'Native navigation stack is misaligned'
                    return self.navigation()
                if target == 0xb544b0:
                    self.setreg('rax', 0)
                elif target == p.NATIVE_REQUEST_TOKEN_RVA:
                    pass  # Journey's public shell requests its token asynchronously.
                else:
                    raise AssertionError('Unexpected native call: '+hex(target))
            elif name == 'ret':
                return 'return', None, None
            else:
                raise AssertionError((ins.address, name, ins.op_str))
        raise AssertionError('Unbounded navigation loop')

    def navigation(self):
        address = self.getreg('r8')
        url = bytearray()
        while self.read(address, 1):
            url.append(self.read(address, 1))
            address += 1
        assert self.getreg('rcx') == p.BROWSER_MANAGER_RVA
        return 'load', url.decode(), self.getreg('edx')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads((args.package/'manifest.json').read_text())
    root = Path(manifest['clientRoot'])
    old, dll = (root/'bin64/Game.dll').read_bytes(), (args.package/'bin64/Game.dll').read_bytes()
    assert len(old)==len(dll)
    assert all(a==b or any(start<=i<end for start,end in RANGES) for i,(a,b) in enumerate(zip(old,dll)))
    checks = 0
    for route in ROUTES:
        for token in (b'ABCDEFGHIJKLMNOP', bytes(range(16)), bytes(range(240,256))):
            assert Cpu(dll, route, token).run() == ('load', route+'?session_id='+token.hex(), 7)
            assert Cpu(dll, route, token).run(callback=True) == ('load', route+'?session_id='+token.hex(), 7)
            checks += 2
        assert Cpu(dll, route, bytes(16)).run()[0] == ('load' if route.endswith('/journey') else 'pending')
        checks += 1
        for bad in (route+'x', route+'?x=1', route[:-1], route.replace('127.0.0.1','127.0.0.2')):
            assert Cpu(dll, bad, b'ABCDEFGHIJKLMNOP').run()[0] == 'pending'
            assert Cpu(dll, bad, b'ABCDEFGHIJKLMNOP').run(callback=True) == ('pending', 0x100000, 7)
            checks += 2
        assert Cpu(dll, route, b'ABCDEFGHIJKLMNOP', view=-1).run()[0] == 'return'
        checks += 1
    for bad in ('', 'h', 'http://127.0.0.1:8091/', 'http://example.invalid/'):
        assert Cpu(dll, bad, b'ABCDEFGHIJKLMNOP').run()[0] == 'pending'
        assert Cpu(dll, bad, b'ABCDEFGHIJKLMNOP').run(callback=True) == ('pending', 0x100000, 7)
        checks += 2
    for entry in manifest['files']:
        assert sha(args.package/entry['path']) == entry['staged']
    print('OK:', checks, 'interpreted x64 route/token/short-input/view checks; exact two-cave DLL scope and package hashes. No native code or game process executed.')


if __name__ == '__main__':
    main()
