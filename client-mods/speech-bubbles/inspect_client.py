"""Read-only disassembly helper for the supported local Aion client."""
import struct,sys
from pathlib import Path
from capstone import Cs,CS_ARCH_X86,CS_MODE_64
CLIENT=(Path.home() / 'Downloads/aion-4.8-na/Aion 4.8 NA')
b=(CLIENT/'bin64/game.dll').read_bytes();pe=struct.unpack_from('<I',b,60)[0];opt=pe+24
base=struct.unpack_from('<Q',b,opt+24)[0]
table=opt+struct.unpack_from('<H',b,pe+20)[0]
sections=[struct.unpack_from('<8sIIIIIIHHI',b,table+i*40) for i in range(struct.unpack_from('<H',b,pe+6)[0])]
def offset(rva):
 for s in sections:
  if s[2]<=rva<s[2]+s[3]:return s[4]+rva-s[2]
 raise ValueError(hex(rva))
def rva(off):
 for s in sections:
  if s[4]<=off<s[4]+s[3]:return s[2]+off-s[4]
 raise ValueError(hex(off))
md=Cs(CS_ARCH_X86,CS_MODE_64)
def dump(start,size):
 for i in md.disasm(b[offset(start):offset(start)+size],start):print(f'{i.address:x}: {i.bytes.hex():28} {i.mnemonic} {i.op_str}')
if __name__=='__main__':dump(int(sys.argv[1],16),int(sys.argv[2],16))
