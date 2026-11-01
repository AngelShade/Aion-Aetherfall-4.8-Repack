// Intercept the normal window-message call before native shortcut/interaction dispatch.
// DlgDialog's late OnInput is bypassed by F's existing interaction binding.
// Choices still use the native UIButton click path; scan only on an eligible key-down.
namespace quest_dialog {
constexpr size_t MESSAGE_SITE=0x296ee,MESSAGE_OWNER=0x552c70;
using MessageFn=int(*)(Ptr,HWND,UINT,WPARAM,LPARAM,uintptr_t);
unsigned held=0;
bool installed=false;
extern "C" __declspec(dllexport) volatile uintptr_t AetherfallDialogDiagnostics[4]={0,0,0,0};
bool button_ready(Ptr button){
    return readable(button,0x298) && effectively_shown(button)
        && method<int(*)(Ptr)>(button,0x10)(button)==0x2001
        && (method<uintptr_t(*)(Ptr)>(button,0xb8)(button)&7)==7;
}
Ptr first_choice(Ptr html){
    if(!readable(html,0x298) || method<int(*)(Ptr)>(html,0x10)(html)!=0x2010)return nullptr;
    Ptr pending[256]{};int begin=0,end=1;pending[0]=html;
    Ptr best=nullptr;Rect best_rect{};
    while(begin<end){
        Ptr current=pending[begin++];if(!readable(current,0x298) || !effectively_shown(current))continue;
        if(button_ready(current)){
            const char* action=method<const char*(*)(Ptr)>(current,0x5f8)(current);
            if(action && readable(action,1) && *action){
                Rect rect=screen_rect(current);
                if(rect.w>0 && rect.h>0 && (!best || rect.y<best_rect.y || (rect.y==best_rect.y && rect.x<best_rect.x))){best=current;best_rect=rect;}
            }
        }
        int count=method<int(*)(Ptr)>(current,0x2f0)(current);
        if(count<0 || count>256)continue;
        for(int i=0;i<count && end<256;++i){Ptr child=method<Ptr(*)(Ptr,int)>(current,0x2e8)(current,i);if(child && child!=current)pending[end++]=child;}
    }
    return best;
}
unsigned key_bit(WPARAM key){return key=='F'?1:key=='1'?2:key==VK_RETURN?4:0;}
bool activate(WPARAM key){
    Ptr self=dialog(0xb3);
    if(!effectively_shown(self) || shown(dialog(CHAT_INPUT_ID)))return false;
    Ptr button=nullptr;
    if(key=='F')button=first_choice(field<Ptr>(self,0x530));
    else {
        for(const char* name:{"accept","accept_simple","ok"}){
            Ptr candidate=widget(0xb3,name,0x2001);
            if(button_ready(candidate)){button=candidate;break;}
        }
    }
    if(!button)return false;
    // Mirror UIButton::Click's root-modal guard before consuming input.
    Ptr root=method<Ptr(*)(Ptr)>(button,0x360)(button);
    if(!root || method<Ptr(*)(Ptr)>(root,0x538)(root))return false;
    // UIButton::Click (+0x600, 0x4a5630) rechecks modal ownership and native visible/active/enabled flags.
    method<void(*)(Ptr)>(button,0x600)(button);
    ++AetherfallDialogDiagnostics[key=='F'?1:2];return 1;
}
int message(Ptr manager,HWND window,UINT event,WPARAM key,LPARAM flags,uintptr_t result){
    unsigned bit=key_bit(key);
    if(event==WM_KILLFOCUS || event==WM_CANCELMODE)held=0;
    if(bit && (event==WM_KEYUP || event==WM_SYSKEYUP) && (held&bit)){held&=~bit;return 1;}
    if(bit && event==WM_KEYDOWN && window==GetForegroundWindow()){
        if(held&bit)return 1;
        if(!(uintptr_t(flags)&(uintptr_t(1)<<30)) && !(GetKeyState(VK_CONTROL)&0x8000)
           && !(GetKeyState(VK_MENU)&0x8000) && !(GetKeyState(VK_SHIFT)&0x8000) && activate(key)){
            held|=bit;++AetherfallDialogDiagnostics[3];return 1;
        }
    }
    return native<MessageFn>(MESSAGE_OWNER)(manager,window,event,key,flags,result);
}
bool install(){
    if(installed)return true;
    unsigned char expected[5]{0xe8};int32_t delta=int32_t(MESSAGE_OWNER-MESSAGE_SITE-5);std::memcpy(expected+1,&delta,4);
    auto site=game()+MESSAGE_SITE;
    if(std::memcmp(site,expected,5)){AetherfallDialogDiagnostics[0]=2;return false;}
    uintptr_t center=reinterpret_cast<uintptr_t>(game()),lower=center>0x60000000?center-0x60000000:0x10000,upper=center+0x60000000;
    unsigned char* relay=nullptr;MEMORY_BASIC_INFORMATION region{};
    for(uintptr_t cursor=lower;cursor<upper;){
        if(!VirtualQuery(reinterpret_cast<void*>(cursor),&region,sizeof region))break;
        uintptr_t begin=(reinterpret_cast<uintptr_t>(region.BaseAddress)+0xffff)&~uintptr_t(0xffff),end=reinterpret_cast<uintptr_t>(region.BaseAddress)+region.RegionSize;
        if(region.State==MEM_FREE && begin<end && end-begin>=0x10000){relay=static_cast<unsigned char*>(VirtualAlloc(reinterpret_cast<void*>(begin),0x10000,MEM_RESERVE|MEM_COMMIT,PAGE_READWRITE));if(relay)break;}
        if(end<=cursor)break;cursor=end;
    }
    if(!relay){AetherfallDialogDiagnostics[0]=3;return false;}
    const void* target=reinterpret_cast<const void*>(&message);relay[0]=0xff;relay[1]=0x25;std::memset(relay+2,0,4);std::memcpy(relay+6,&target,8);
    DWORD old=0,unused=0;
    if(!VirtualProtect(relay,0x10000,PAGE_EXECUTE_READ,&old)){VirtualFree(relay,0,MEM_RELEASE);AetherfallDialogDiagnostics[0]=3;return false;}
    FlushInstructionCache(GetCurrentProcess(),relay,16);
    if(!VirtualProtect(site,5,PAGE_EXECUTE_READWRITE,&old)){VirtualFree(relay,0,MEM_RELEASE);AetherfallDialogDiagnostics[0]=3;return false;}
    delta=int32_t(relay-(site+5));site[0]=0xe8;std::memcpy(site+1,&delta,4);VirtualProtect(site,5,old,&unused);FlushInstructionCache(GetCurrentProcess(),site,5);
    installed=true;AetherfallDialogDiagnostics[0]=1;return true;
}
}
