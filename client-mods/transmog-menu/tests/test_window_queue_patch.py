"""Interpret the generated hook offline; never execute or load native code."""
import struct
import sys
import unittest
from pathlib import Path
from capstone import Cs, CS_ARCH_X86, CS_MODE_64
from capstone.x86_const import X86_OP_REG, X86_OP_IMM, X86_OP_MEM
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from window_queue_patch import code, EPILOGUE, patch, SITE, ORIGINAL, HOOK_RVA, COUNTER_RVA


class Hook:
    def __init__(self):
        self.base, self.counter = 0x1000, 0x1800
        self.code = code(self.base, self.counter)
        self.memory = {}
        self.decoder = Cs(CS_ARCH_X86, CS_MODE_64)
        self.decoder.detail = True

    def write(self, address, value, size):
        for i,b in enumerate(value.to_bytes(size, 'little')): self.memory[address+i] = b

    def read(self, address, size):
        return sum(self.memory.get(address+i, 0) << (i*8) for i in range(size))

    def run(self, opcode=18, name=b'RelicCalc', capacity=15, valid=True):
        self.write(0x2000, opcode, 4)
        self.write(0x2010, 0x3000 if valid else 0, 8)
        self.write(0x2018, 0x3000+160, 8)
        self.write(0x3018, len(name), 8)
        self.write(0x3020, capacity, 8)
        for i,b in enumerate(name): self.memory[0x3008+i] = b
        registers = {'rbx':0x2000, 'rax':0, 'rdx':0, 'r10':0}
        pc, zero, below = self.base, False, False
        def register(name):return {'eax':'rax', 'edx':'rdx', 'al':'rax'}.get(name,name)
        def address(op, ins):
            assert not op.mem.index
            base = ins.reg_name(op.mem.base)
            return (ins.address+ins.size if base=='rip' else registers.get(base,0)) + op.mem.disp
        def value(op, ins):
            if op.type==X86_OP_IMM:return op.imm
            if op.type==X86_OP_MEM:return self.read(address(op,ins),op.size)
            return registers[register(ins.reg_name(op.reg))] & ((1 << (op.size*8))-1)
        def put(op, ins, v):
            v &= (1 << (op.size*8))-1
            if op.type==X86_OP_MEM:self.write(address(op,ins),v,op.size)
            else:
                name=ins.reg_name(op.reg);key=register(name)
                registers[key]=v if op.size!=1 else (registers[key]&~255)|v
        for _ in range(100):
            if pc==EPILOGUE:return registers['rax'] & 0xffffffff
            ins=next(self.decoder.disasm(self.code[pc-self.base:],pc,count=1))
            ops=ins.operands;pc+=ins.size;m=ins.mnemonic
            if m in ('mov','movabs','movzx'):put(ops[0],ins,value(ops[1],ins))
            elif m in ('cmp','test'):
                a,b=value(ops[0],ins),value(ops[1],ins)
                zero=(a==b) if m=='cmp' else (a&b)==0;below=a<b
            elif m in ('sub','and','xor'):
                a,b=value(ops[0],ins),value(ops[1],ins)
                v={'sub':lambda:a-b,'and':lambda:a&b,'xor':lambda:a^b}[m]()
                put(ops[0],ins,v);zero=v==0
            elif m=='inc':put(ops[0],ins,value(ops[0],ins)+1)
            elif m=='setne':put(ops[0],ins,int(not zero))
            elif m in ('jmp','je','jne','jb'):
                if m=='jmp' or m=='je' and zero or m=='jne' and not zero or m=='jb' and below:pc=ops[0].imm
            else:raise AssertionError('Unsupported instruction '+m)
        raise AssertionError('Unbounded hook')


class Checks(unittest.TestCase):
    def test_exact_native_batch_and_wrap(self):
        h=Hook()
        self.assertEqual([h.run() for _ in range(128)], [1]*63+[0]+[1]*63+[0])
        h.write(h.counter,0xffffffff,4)
        self.assertEqual(h.run(21),0)
        self.assertEqual(h.run(21),1)

    def test_other_addons_and_commands_keep_yield(self):
        for op in range(96):
            if op not in (18,21):self.assertEqual(Hook().run(op),0)
        for name in (b'',b'RelicCal',b'RelicCalcX',b'RelicCald',b'OtherName'):
            self.assertEqual(Hook().run(name=name),0)
        self.assertEqual(Hook().run(capacity=16),0)
        self.assertEqual(Hook().run(valid=False),0)

    def test_real_client_incremental_ownership(self):
        client=(Path.home() / 'Downloads/aion-4.8-na/Aion 4.8 NA/bin64')
        source=(client/'Game.dll').read_bytes();original=(client/'game.dll.orig').read_bytes()
        # After installation, reconstruct only our verified owned bytes in
        # memory to retain a meaningful incremental test against the new client.
        if source[SITE:SITE+7]!=ORIGINAL:
            expected=b'\xe9'+struct.pack('<i',HOOK_RVA-SITE-5)+b'\x90\x90'
            self.assertEqual(source[SITE:SITE+7],expected)
            generated=code(HOOK_RVA,COUNTER_RVA)
            self.assertEqual(source[HOOK_RVA:HOOK_RVA+len(generated)],generated)
            baseline=bytearray(source);baseline[SITE:SITE+7]=ORIGINAL
            baseline[HOOK_RVA:COUNTER_RVA+4]=bytes(COUNTER_RVA+4-HOOK_RVA)
            source=bytes(baseline)
        changed,meta=patch(source,original)
        self.assertEqual(len(source),len(changed))
        self.assertEqual(meta['hookBytes'],len(code(meta['hookRva'],meta['counterRva'])))
        self.assertEqual(changed[0x60daf0:0x60ffa5],source[0x60daf0:0x60ffa5])
        self.assertEqual(changed[0x60ffac:0x61040c],source[0x60ffac:0x61040c])
        with self.assertRaisesRegex(ValueError,'dispatcher changed'):patch(changed,original)


if __name__=='__main__':unittest.main()
