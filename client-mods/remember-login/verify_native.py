"""Real compiled UI bridge and Windows vault tests, using synthetic credentials only."""
import ctypes as C
import os,subprocess,sys
from pathlib import Path
from prepare import compile_dll
ROOT=Path(__file__).resolve().parent
fixture=Path(os.environ.get('AION_DEV_ROOT','D:/Proiecte/Project Restructure/Aion Development Workspace'))/'staging/output/remember-login-native-check'
if '--restart' not in sys.argv:compile_dll(fixture,test=True)
dll=C.CDLL(str(fixture/'bin64/AionRememberLogin.dll'));p=C.c_void_p
for name,args,result in [('AionRememberLoad',[p],None),('AionRememberClick',[p,p],C.c_int),('AionRememberAction',[p,C.c_char_p],None),('AionRememberRefresh',[p],None),('AionRememberVisibility',[p,C.c_uint64],None),('AionRememberTestNotice',[p],None),('AionRememberTestDelete',[],C.c_int),('AionRememberTestBase',[p],None)]:
    f=getattr(dll,name);f.argtypes=args;f.restype=result
callbacks=[];buffers=[];widgets={};states={};texts={};names={};text_buffers={}
def callback(result,args,fn):
    cb=C.CFUNCTYPE(result,*args)(fn);callbacks.append(cb);return C.cast(cb,p).value
def set_text(a,t):
    texts[a]=t;text_buffers[a]=C.create_unicode_buffer(t)
def obj(name):
    memory=C.create_string_buffer(0x1000);vtable=(p*200)();buffers.extend([memory,vtable]);address=C.addressof(memory)
    C.cast(address,C.POINTER(p))[0]=C.addressof(vtable);names[address]=C.create_string_buffer(name.encode());widgets[name]=address;states[address]=0;set_text(address,'')
    vtable[0xa8//8]=callback(p,[p],lambda a:C.addressof(names[a]))
    vtable[0x340//8]=callback(p,[p,C.c_char_p],lambda a,n:widgets.get(n.decode(),0))
    vtable[0x598//8]=callback(None,[p,C.c_int],lambda a,v:states.__setitem__(a,v))
    vtable[0x5a8//8]=callback(C.c_int,[p],lambda a:states[a])
    vtable[0x290//8]=callback(None,[p,C.c_wchar_p],set_text)
    vtable[0x298//8]=callback(p,[p],lambda a:C.addressof(text_buffers[a]))
    return address
notice=obj('htmlview_notice');notice_loads=[]
notice_callback=C.CFUNCTYPE(None,p,C.c_char_p)(lambda widget,path:notice_loads.append((widget,path.decode())))
dll.AionRememberTestNotice(C.cast(notice_callback,p))
dialog=obj('login_dialog');box=obj('remember_login');account=obj('account');password=obj('password');status=obj('remember_login_status')
for name in ['ok','otp_check','unrelated']:obj(name)
game=C.create_string_buffer(0x13875d0+8);C.c_void_p.from_address(C.addressof(game)+0x13875d0).value=dialog;dll.AionRememberTestBase(C.addressof(game))
def click(name):return dll.AionRememberClick(dialog,C.byref(p(widgets[name])))
def submit():dll.AionRememberAction(None,b'login_auth_server')
def reset():set_text(account,'');set_text(password,'');states[box]=0;dll.AionRememberLoad(dialog)
checks=0
def expect(condition):
    global checks
    assert condition,(checks,texts[status],dll.AionRememberTestError());checks+=1
if '--restart' in sys.argv:
    reset();expect(states[box]==1 and texts[account]=='fixture_account' and texts[password]=='Synthetic-only-9!')
    print('OK: fresh process restored the synthetic login from Windows vault');sys.exit(0)
try:
    expect(dll.AionRememberTestDelete()==1)
    dll.AionRememberLoad(None);dll.AionRememberClick(None,None);dll.AionRememberAction(None,None)
    reset();expect(states[box]==0 and texts[account]==texts[password]=='')
    set_text(account,'fixture_account');set_text(password,'Synthetic-only-9!');submit();reset();expect(not states[box] and texts[password]=='')
    states[box]=1;expect(click('remember_login')==1 and 'when you log in' in texts[status])
    submit();expect('when you log in' in texts[status]) # empty fields must not be persisted
    set_text(account,'fixture_account');set_text(password,'Synthetic-only-9!');submit();expect('saved' in texts[status])
    reset();expect(states[box]==1 and texts[account]=='fixture_account' and texts[password]=='Synthetic-only-9!')
    subprocess.run([sys.executable,str(Path(__file__).resolve()),'--restart'],check=True)
    flags=C.c_uint64.from_address(dialog+0x30);flags.value=1
    # The initial native scene may clear fields after its setup callback.
    set_text(account,'');set_text(password,'');states[box]=0;dll.AionRememberRefresh(dialog)
    expect(states[box]==1 and texts[account]=='fixture_account' and texts[password]=='Synthetic-only-9!')
    expect(notice_loads==[(dialog,'native-login-notice')])
    for _ in range(50):dll.AionRememberRefresh(dialog)
    expect(len(notice_loads)==1)
    set_text(account,'editing_fixture');set_text(password,'Typing-only-4!')
    dll.AionRememberVisibility(dialog,1);dll.AionRememberRefresh(dialog)
    expect(texts[account]=='editing_fixture' and len(notice_loads)==1)
    C.c_uint64.from_address(widgets['unrelated']+0x30).value=1
    dll.AionRememberVisibility(widgets['unrelated'],0);dll.AionRememberRefresh(dialog)
    expect(texts[account]=='editing_fixture' and len(notice_loads)==1)
    flags.value=0;dll.AionRememberVisibility(dialog,1);dll.AionRememberRefresh(dialog)
    expect(texts[account]=='editing_fixture' and len(notice_loads)==1)
    flags.value=1;set_text(account,'');set_text(password,'');states[box]=0
    dll.AionRememberVisibility(dialog,0);dll.AionRememberRefresh(dialog)
    expect(states[box]==1 and texts[account]=='fixture_account' and texts[password]=='Synthetic-only-9!')
    expect(len(notice_loads)==2)
    for _ in range(5):
        flags.value=0;dll.AionRememberVisibility(dialog,1)
        flags.value=1;set_text(account,'');set_text(password,'');states[box]=0
        dll.AionRememberVisibility(dialog,0);dll.AionRememberRefresh(dialog)
        expect(states[box]==1 and texts[account]=='fixture_account')
    expect(len(notice_loads)==7)
    # Reused native Reset can clear fields with no visibility edge.
    set_text(account,'');set_text(password,'');states[box]=0
    dll.AionRememberVisibility(None,dialog);dll.AionRememberRefresh(dialog)
    expect(states[box]==1 and texts[account]=='fixture_account' and texts[password]=='Synthetic-only-9!')
    expect(len(notice_loads)==8)
    set_text(account,'typing_after_reset')
    dll.AionRememberVisibility(None,widgets['unrelated']);dll.AionRememberRefresh(dialog)
    expect(texts[account]=='typing_after_reset' and len(notice_loads)==8)

    set_text(account,'other_fixture');set_text(password,'Another-synthetic-7!');dll.AionRememberAction(None,b'new_account');reset();expect(texts[account]=='fixture_account')
    set_text(account,'other_fixture');set_text(password,'Another-synthetic-7!');submit();reset();expect(texts[account]=='other_fixture' and texts[password]=='Another-synthetic-7!')
    states[box]=0;expect(click('remember_login')==1);reset();expect(not states[box] and texts[account]==texts[password]=='')
    expect(click('otp_check')==click('ok')==click('unrelated')==0)
    for a,pw in [('','valid'),('a',''),('a'*65,'valid'),('a','x'*33)]:
        states[box]=1;set_text(account,a);set_text(password,pw);submit();reset();expect(not states[box])
    states[box]=1;set_text(account,'a'*64);set_text(password,'p'*32);submit();reset();expect(states[box]==1 and len(texts[account])==64 and len(texts[password])==32)
    # Every restore clears both fields first; a separate process also loads the
    # same DLL and independently retrieves the saved synthetic login above.
finally:
    assert dll.AionRememberTestDelete()==1
    set_text(account,'');set_text(password,'')
print('OK:',checks,'compiled native checks; login returns, one refresh per visibility transition, notice reload, typing preservation, opt-in, vault restore/update/delete, field bounds, action filtering; synthetic vault entry removed')
