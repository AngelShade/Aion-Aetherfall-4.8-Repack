"""Run the compiled menu bridge against a mock native widget ABI."""
import ctypes as C
from pathlib import Path
import shutil
ROOT=Path(__file__).resolve().parent
# Use a fixture client layout so the actual compiled bridge resolves its own
# settings path the same way it will inside bin64, without touching live files.
fixture=ROOT/'test-workspace'/'menu-bridge';(fixture/'bin64').mkdir(parents=True,exist_ok=True)
(fixture/'DXVK').mkdir(exist_ok=True)
dll=fixture/'bin64/AionGraphicsMenu.dll';shutil.copyfile(ROOT/'build/graphics-menu/AionGraphicsMenu.dll',dll)
settings=fixture/'DXVK/renderer.ini';settings.write_text('[Renderer]\nVulkan=1\nActiveVulkan=1\n',encoding='ascii')
module=C.CDLL(str(dll));p=C.c_void_p
module.AionGraphicsLoad.argtypes=[p,C.c_int];module.AionGraphicsLoad.restype=None
module.AionGraphicsClick.argtypes=[p,p];module.AionGraphicsClick.restype=C.c_int
callbacks=[];buffers=[];widgets={};names={};states={};texts={}
def callback(restype,args,fn):
    cb=C.CFUNCTYPE(restype,*args)(fn);callbacks.append(cb);return C.cast(cb,p).value
def obj(name):
    memory=C.create_string_buffer(0x1000);vtable=(p*200)();buffers.extend([memory,vtable])
    address=C.addressof(memory);C.cast(address,C.POINTER(p))[0]=C.addressof(vtable)
    text=C.create_string_buffer(name.encode()+b'\0');buffers.append(text);names[address]=text;widgets[name]=address;states[address]=0
    vtable[0xa8//8]=callback(p,[p],lambda a:C.addressof(names[a]))
    vtable[0x340//8]=callback(p,[p,C.c_char_p],lambda a,n:widgets.get(n.decode(),0))
    vtable[0x598//8]=callback(None,[p,C.c_int],lambda a,v:states.__setitem__(a,v))
    vtable[0x5a8//8]=callback(C.c_int,[p],lambda a:states[a])
    vtable[0x290//8]=callback(None,[p,C.c_wchar_p],lambda a,t:texts.__setitem__(a,t))
    return address
dialog=obj('dialog');box=obj('cb_use_vulkan');status=obj('st_vulkan_status')
for n in ['apply','ok','cancel','unrelated']:obj(n)
def click(name):return module.AionGraphicsClick(dialog,C.byref(p(widgets[name])))
module.AionGraphicsLoad(dialog,0);assert not texts
module.AionGraphicsLoad(dialog,1);assert states[box]==1 and 'Vulkan' in texts[status]
states[box]=0;before=settings.read_bytes();assert click('cb_use_vulkan')==1
assert before==settings.read_bytes() and C.c_int.from_address(dialog+0xf6c).value==1
assert 'Apply or OK' in texts[status]
assert click('cancel')==0 and states[box]==1 and settings.read_bytes()==before
states[box]=0;click('apply');assert 'Vulkan=0' in settings.read_text() and 'Saved.' in texts[status]
module.AionGraphicsLoad(dialog,1);assert states[box]==0
states[box]=1;click('ok');assert 'Vulkan=1' in settings.read_text()
before=settings.read_bytes();assert click('unrelated')==0 and before==settings.read_bytes()
assert 'Use Vulkan' in texts[box]
print('PASS compiled bridge: load, click, Apply, OK, Cancel, persistence, dirty flag, unrelated delegation')
