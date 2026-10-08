"""Interpret the reset trampoline offline; no game code or DLL is executed."""
import hashlib,json,os,struct,sys,unittest
from pathlib import Path
from capstone import Cs,CS_ARCH_X86,CS_MODE_64
from capstone.x86_const import X86_OP_REG,X86_OP_IMM,X86_OP_MEM
from patch_reset import code,patch,relocate,HOOK,SAFE_HOOK,SITE,ORIGINAL
from patch_binary import layout,offset

class Checks(unittest.TestCase):
    def legacy_source(self):
        root=(Path.home() / 'Downloads/aion-4.8-na/Aion 4.8 NA')
        source=(root/'bin64/Game.dll').read_bytes()
        if source[SITE:SITE+6]==b'\xe9'+struct.pack('<i',HOOK-SITE-5)+b'\x90':return source,root
        archives=Path(os.environ.get('AION_DEV_ROOT','D:/Proiecte/Project Restructure/Aion Development Workspace'))/'archives/client'
        for path in sorted(archives.glob('remember-login-return-*/manifest.json'),reverse=True):
            receipt=json.loads(path.read_text())
            if receipt['feature']!='remember-login-reconnect-v3':continue
            entry=next(e for e in receipt['files'] if e['path']=='bin64/Game.dll')
            saved=(path.parent/entry['path']).read_bytes()
            self.assertEqual(hashlib.sha256(saved).hexdigest(),entry['original'])
            self.assertEqual(hashlib.sha256(source).hexdigest(),entry['staged'])
            return saved,path.parent
        self.fail('No hash-verified legacy installation or recovery receipt')

    def test_reset_callback_and_native_abi(self):
        for hook in [HOOK,SAFE_HOOK,0x190e4a0]:
            with self.subTest(hook=hex(hook)):self.check_abi(hook)

    def check_abi(self,hook):
        iat=0x190f2d8;payload=code(iat,hook);decoder=Cs(CS_ARCH_X86,CS_MODE_64);decoder.detail=True
        initial={name:n+100 for n,name in enumerate(['rax','rcx','rdx','r8','r9','r10','r11','rbx'])}
        initial.update(rsp=0x100008,**{'xmm'+str(n):int.from_bytes(bytes([n+1])*16,'little') for n in range(6)})
        registers=dict(initial);memory={};pc=hook;flags=0x246;calls=0
        def canonical(name):return {'ecx':'rcx'}.get(name,name)
        def address(op,ins):
            base=ins.reg_name(op.mem.base)
            return (ins.address+ins.size if base=='rip' else registers.get(base,0))+op.mem.disp
        def read(at,size):return sum(memory.get(at+i,0)<<(8*i) for i in range(size))
        def write(at,size,v):
            for i in range(size):memory[at+i]=(v>>(8*i))&255
        def get(op,ins):
            if op.type==X86_OP_IMM:return op.imm
            if op.type==X86_OP_MEM:return read(address(op,ins),op.size)
            return registers[canonical(ins.reg_name(op.reg))]
        def put(op,ins,v):
            if op.type==X86_OP_MEM:write(address(op,ins),op.size,v)
            else:registers[canonical(ins.reg_name(op.reg))]=v&((1<<(op.size*8))-1)
        for step in range(100):
            if pc==SITE+len(ORIGINAL):break
            ins=next(decoder.disasm(payload[pc-hook:],pc,count=1));pc+=ins.size;ops=ins.operands
            if ins.mnemonic in ('mov','movdqu'):put(ops[0],ins,get(ops[1],ins))
            elif ins.mnemonic in ('sub','add'):
                a,b=get(ops[0],ins),get(ops[1],ins);put(ops[0],ins,a-b if ins.mnemonic=='sub' else a+b)
            elif ins.mnemonic=='xor':put(ops[0],ins,get(ops[0],ins)^get(ops[1],ins))
            elif ins.mnemonic in ('push','pushfq'):
                registers['rsp']-=8;write(registers['rsp'],8,flags if ins.mnemonic=='pushfq' else get(ops[0],ins))
            elif ins.mnemonic in ('pop','popfq'):
                v=read(registers['rsp'],8);registers['rsp']+=8
                if ins.mnemonic=='popfq':flags=v
                else:put(ops[0],ins,v)
            elif ins.mnemonic=='call':
                self.assertEqual(address(ops[0],ins),iat)
                self.assertEqual(registers['rcx'],0);self.assertEqual(registers['rdx'],initial['rcx'])
                self.assertEqual(registers['rsp']%16,0);calls+=1
                for name in ['rax','rcx','rdx','r8','r9','r10','r11',*('xmm'+str(n) for n in range(6))]:registers[name]=0xbeef
                flags=0
            elif ins.mnemonic=='jmp':pc=ops[0].imm
            else:raise AssertionError(ins.mnemonic)
        else:raise AssertionError('Trampoline did not delegate')
        self.assertEqual(calls,1)
        for name in initial:
            if name!='rsp':self.assertEqual(registers[name],initial[name],name)
        self.assertEqual(registers['rsp'],initial['rsp']-0x28)
        self.assertEqual(read(initial['rsp']-8,8),initial['rbx'])

    def test_incremental_ownership_against_real_client(self):
        root=(Path.home() / 'Downloads/aion-4.8-na/Aion 4.8 NA')
        source,_=self.legacy_source();receipt=json.loads((root/'RememberLogin-backups/20261003-190013-967/manifest.json').read_text())
        # After installation, reconstruct only this repair's owned bytes in memory.
        # Every earlier mod remains in the cumulative client used by this check.
        iat=next(h['iat'] for h in receipt['hooks'] if h['export']=='AionRememberVisibility')
        expected=b'\xe9'+struct.pack('<i',HOOK-SITE-5)+b'\x90';payload=code(iat)
        if source[SITE:SITE+len(ORIGINAL)]==expected:
            self.assertEqual(source[HOOK:HOOK+len(payload)],payload)
            data=bytearray(source);data[SITE:SITE+len(ORIGINAL)]=ORIGINAL
            data[HOOK:HOOK+len(payload)]=bytes(len(payload));source=bytes(data)
        changed,meta=patch(source,receipt);_,_,_,table,sections=layout(source)
        self.assertEqual(len(source),len(changed))
        section_index=next(i for i,s in enumerate(sections) if s[0].rstrip(b'\0')==b'.rreturn')
        header=table+section_index*40+8
        allowed=set(range(SITE,SITE+len(ORIGINAL)))|set(range(offset(sections,SAFE_HOOK),offset(sections,SAFE_HOOK)+meta['size']))|set(range(header,header+4))
        self.assertTrue(all(i in allowed for i,(a,b) in enumerate(zip(source,changed)) if a!=b))
        with self.assertRaisesRegex(ValueError,'Reset changed'):patch(changed,receipt)

    def test_real_reconnect_runtime_storage_cannot_overwrite_relocated_code(self):
        root=(Path.home() / 'Downloads/aion-4.8-na/Aion 4.8 NA')
        source,_=self.legacy_source();receipt=json.loads((root/'RememberLogin-backups/20261003-190013-967/manifest.json').read_text())
        changed,record=relocate(source,receipt);sections=layout(changed)[-1]
        at=offset(sections,SAFE_HOOK);expected=code(record['iat'],SAFE_HOOK)
        self.assertEqual(changed[at:at+len(expected)],expected)
        self.assertEqual(changed[HOOK:HOOK+record['size']],bytes(record['size']))
        # The crash dump shows native runtime data at 144ec00 overwriting the
        # old trampoline; simulate those writes without executing native code.
        memory=bytearray(changed);memory[0x144ec00:0x144ec20]=bytes([0xa5])*32
        self.assertEqual(memory[at:at+len(expected)],expected)
        self.assertEqual(record['mode'],'native-reset-section')
        section=next(s for s in sections if s[0].rstrip(b'\0')==b'.rreturn')
        self.assertLessEqual(SAFE_HOOK+len(expected),section[2]+section[1])
        self.assertEqual(source[0x144e213:0x144e21a],changed[0x144e213:0x144e21a])
        self.assertEqual(source[0x144eb02:0x144eb09],changed[0x144eb02:0x144eb09])
        damaged=bytearray(source);damaged[HOOK]^=1
        with self.assertRaisesRegex(ValueError,'legacy Reset hook changed'):relocate(bytes(damaged),receipt)
        damaged=bytearray(source);damaged[offset(sections,SAFE_HOOK)]=1
        with self.assertRaisesRegex(ValueError,'section tail changed'):relocate(bytes(damaged),receipt)

    def test_restore_images_use_their_own_import_and_only_owned_bytes(self):
        from patch_reset import relocate_baseline
        source,recovery=self.legacy_source()
        state=json.loads((recovery/'DXVK/graphics-menu/installed.json').read_text())
        dx=json.loads((recovery/'DXVK/installed.json').read_text())
        files=[Path(state['backupRoot'])/'bin64/Game.dll',Path(next(e for e in dx['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll')['backupPath'])]
        for path in files:
            with self.subTest(path=str(path)):
                original=path.read_bytes();changed,record=relocate_baseline(original,0x190f2d8)
                self.assertEqual(record['iat'],0x190e2c0)
                self.assertEqual(record['hook'],0x190e4a0)
                self.assertEqual(len(changed),len(original))
                _,_,_,table,sections=layout(original)
                index=next(i for i,s in enumerate(sections) if s[0].rstrip(b'\0')==b'.rreturn')
                start=offset(sections,record['hook']);header=table+index*40+8
                allowed=set(range(SITE,SITE+6))|set(range(HOOK,HOOK+record['size']))|set(range(start,start+record['size']))|set(range(header,header+4))
                self.assertTrue(all(i in allowed for i,(a,b) in enumerate(zip(original,changed)) if a!=b))

if __name__=='__main__':unittest.main()
