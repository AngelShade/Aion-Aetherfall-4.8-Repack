"""Execute the installed 4.8 warehouse packet consumers and admission instructions offline (Unicorn)."""
import argparse
from functools import lru_cache
import hashlib
import json
from pathlib import Path
import struct
import pefile
from unicorn import Uc, UC_ARCH_X86, UC_MODE_64
from unicorn.x86_const import *
from warehouse_patch import patch_capacity_dll, CAPACITY_SITE, CAPACITY_CAVE

BASE, MANAGER, DATA, STACK = 0x10000000, 0x50000000, 0x60000000, 0x70000000

@lru_cache(maxsize=2)
def mapped(blob):
    return pefile.PE(data=blob,fast_load=True).get_memory_mapped_image()

def machine(blob):
    image = mapped(blob)
    u = Uc(UC_ARCH_X86, UC_MODE_64)
    u.mem_map(BASE, (len(image)+4095)&~4095)
    u.mem_write(BASE, image)
    for address in (MANAGER, DATA, STACK):
        u.mem_map(address, 0x10000)
    u.mem_write(0x112EDA48, struct.pack('<Q', MANAGER))
    u.reg_write(UC_X86_REG_RSP, STACK+0x8000)
    return u

def capacity(blob, kind, expansions, packet):
    u = machine(blob)
    bank = MANAGER+0xFF8+(kind-1)*0x90
    u.mem_write(bank+0x34, struct.pack('<I',kind))
    if packet == 'warehouse-info':
        u.mem_write(DATA, struct.pack('<BBBBBH', kind,0,expansions,0,0,0))
        u.reg_write(UC_X86_REG_R12, DATA)
        u.reg_write(UC_X86_REG_RSI, BASE)
        u.emu_start(BASE+0x377FCF, BASE+0x45FA95, count=250)
    else:
        u.mem_write(DATA, struct.pack('<BBIBBB',0,kind,72,expansions,0,0))
        u.reg_write(UC_X86_REG_RCX, DATA)
        u.emu_start(BASE+0x374F93, BASE+0x45FA95, count=200)
    assert u.reg_read(UC_X86_REG_RIP)==BASE+0x45FA95
    return struct.unpack('<I',u.mem_read(bank+0x30,4))[0]

def free_slots(blob, kind, limit, count):
    u = machine(blob)
    bank=MANAGER+0xFF8+(kind-1)*0x90
    u.mem_write(bank+0x10,struct.pack('<I',count))
    u.mem_write(bank+0x30,struct.pack('<I',limit))
    u.reg_write(UC_X86_REG_RCX,MANAGER)
    u.reg_write(UC_X86_REG_RDX,kind)
    u.reg_write(UC_X86_REG_R8,0)
    u.emu_start(BASE+0x44A370,BASE+0x44A3FA,count=100)
    assert u.reg_read(UC_X86_REG_RIP)==BASE+0x44A3FA
    return u.reg_read(UC_X86_REG_EAX)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--dll',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();before=a.dll.read_bytes();after=patch_capacity_dll(before)
    assert after!=before,'Use the pre-repair installed DLL to reproduce the defect'
    result=[]
    for packet in ('warehouse-info','cube-update'):
        for kind in (1,2):
            for earned in (0,6,11):
                old=capacity(before,kind,earned,packet);new=capacity(after,kind,earned,packet)
                assert old==(24+8*earned if kind==1 else 16)
                assert new==(360 if kind==1 else 540)
                assert free_slots(before,kind,old,old)==0
                assert free_slots(after,kind,new,old)==new-old
                assert free_slots(after,kind,new,new)==0
                assert free_slots(after,kind,new,new-1)==1
                result.append(dict(packet=packet,storage=kind,earned=earned,before=old,after=new))
    # The native incoming-item slot check excludes an otherwise valid high slot
    # at 0x45fe26/2a; the UI's expanded list alone cannot change that comparison.
    for kind in (1,2):
        for limit,slot,accepted in [(72,72,False),(16,16,False),(360 if kind==1 else 540,359 if kind==1 else 539,True)]:
            u=machine(after);u.reg_write(UC_X86_REG_RSI,MANAGER);u.reg_write(UC_X86_REG_R10,slot)
            u.mem_write(MANAGER+0x30,struct.pack('<I',limit))
            u.emu_start(BASE+0x45FE26,BASE+0x45FE30,count=2)
            assert (u.reg_read(UC_X86_REG_RIP)==BASE+0x45FE30)==accepted
    for kind,limit in ((0,288),(3,56)):
        u=machine(after);u.reg_write(UC_X86_REG_RCX,DATA);u.reg_write(UC_X86_REG_RDX,limit)
        u.mem_write(DATA+0x34,struct.pack('<I',kind))
        u.emu_start(BASE+CAPACITY_SITE,BASE+0x45FA95,count=100)
        assert struct.unpack('<I',u.mem_read(DATA+0x30,4))[0]==limit
    assert patch_capacity_dll(after)==after
    a.output.write_text(json.dumps(dict(beforeSha256=hashlib.sha256(before).hexdigest(),afterSha256=hashlib.sha256(after).hexdigest(),cases=result,checks=81),indent=2))
    print('OK: 81 native warehouse capacity/admission checks; reproduced 24/72/112 and 16, repaired 360/540')

if __name__=='__main__':main()
