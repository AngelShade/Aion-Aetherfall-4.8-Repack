// Runs on the existing native UI tick. No new Game.dll hook or network protocol.
namespace companion_bar {
using namespace wardrobe;
const wchar_t* order(const std::string& name) {
    if(name=="attack")return L".bot attack all";
    if(name=="follow")return L".bot follow all";
    if(name=="stay")return L".bot stay all";
    if(name=="guard")return L".bot guard all";
    if(name=="passive")return L".bot passive all";
    if(name=="summon")return L".bot summon all";
    if(name=="formation_circle")return L".bot formation circle";
    if(name=="formation_box")return L".bot formation box";
    if(name=="formation_line")return L".bot formation line";
    if(name=="formation_spread")return L".bot formation spread";
    return nullptr;
}
struct Saved { int x=220,y=180,mode=0; } saved,observed;
bool loaded=false;
Ptr previous_bar=nullptr;
ULONGLONG next_poll=0,changed_at=0,last_send=0;
std::wstring config_path;
void tick() {
    auto g=game();if(!g)return;
    auto now=GetTickCount64();if(now<next_poll)return;next_poll=now+100;
    Ptr bar=nullptr;
    // Resolve on each tick; addon dialogs can be destroyed/recreated at logout.
    for(int id=0x20e;id<=0x221;++id){
        Ptr candidate=*reinterpret_cast<Ptr*>(g+0x13875c0+id*8);if(!candidate)continue;
        const char* name=method<const char*(__cdecl*)(Ptr)>(candidate,0xa8)(candidate);
        if(name && !std::strcmp(name,"PlayerBotBar")){bar=candidate;break;}
    }
    if(!bar)return;
    if(bar!=previous_bar){previous_bar=bar;loaded=false;}
    if(!loaded){
        wchar_t path[32768];DWORD length=GetModuleFileNameW(own_module,path,DWORD(std::size(path)));
        if(!length || length>=std::size(path))return;
        config_path=(std::filesystem::path(path).parent_path().parent_path()/L"PlayerBotBar.ini").wstring();
        saved.x=int(GetPrivateProfileIntW(L"Bar",L"x",220,config_path.c_str()));
        saved.y=int(GetPrivateProfileIntW(L"Bar",L"y",180,config_path.c_str()));
        saved.mode=int(GetPrivateProfileIntW(L"Bar",L"mode",0,config_path.c_str()));
        if(saved.mode<0 || saved.mode>2)saved.mode=0;
        auto r=rect(bar);r.x=saved.x;r.y=saved.y;rect(bar,r);loaded=true;observed=saved;
    }
    const double width=*reinterpret_cast<double*>(g+0x1378ea8),height=*reinterpret_cast<double*>(g+0x1378eb0);
    if(!std::isfinite(width)||!std::isfinite(height)||width<640||height<480||width>16384||height>16384)return;
    auto r=rect(bar);
    if(!std::isfinite(r.x)||!std::isfinite(r.y)||!std::isfinite(r.w)||!std::isfinite(r.h))return;
    // Keep title and icon reachable after a scale/resolution change. Let the
    // native title drag own movement while the mouse is down.
    if(!(GetAsyncKeyState(VK_LBUTTON)&0x8000)){
        auto old=r;r.x=(std::max)(0.0,(std::min)(r.x,width-r.w));r.y=(std::max)(0.0,(std::min)(r.y,height-r.h));
        if(old.x!=r.x||old.y!=r.y)rect(bar,r);
    }
    auto find=[&](const char* name){return lookup(bar,name,0x200b);};
    auto mode=native_ui::read(find("PlayerBotBarMode"),1);
    const int current_mode=mode=="1"?1:mode=="2"?2:0;
    const bool connected=*reinterpret_cast<Ptr*>(g+0x133bb30) && *reinterpret_cast<unsigned char*>(g+0x133bb38)!=0;
    // Seed mode once; Lua reads this before publishing its selected mode.
    const int published_mode=mode.empty()?saved.mode:current_mode;
    native_ui::write(find("PlayerBotBarMeta"),std::to_wstring(int(width))+L"|"+std::to_wstring(int(height))+L"|"+std::to_wstring(published_mode)+L"|"+(connected?L"1":L"0"));
    auto tx=find("PlayerBotBarTx");auto request=native_ui::read(tx,16);
    if(!request.empty()){
        // Clear before sending: no retry of a stale Attack after reconnect.
        native_ui::write(tx,L"");const auto command=order(request);
        const wchar_t* response=L"Companion command unavailable. Re-enter the game and try again.";
        if(command && connected && now-last_send>=250){
            reinterpret_cast<void(__cdecl*)(Ptr,int,const wchar_t*)>(g+0x33d790)(nullptr,0,command);
            last_send=now;response=L"sent";
        }else if(command && connected)response=L"Please wait a moment before another companion order.";
        native_ui::write(find("PlayerBotBarRx"),response);
    }
    Saved current{int(r.x),int(r.y),current_mode};
    if(current.x!=observed.x||current.y!=observed.y||current.mode!=observed.mode){observed=current;changed_at=now;}
    if(changed_at && now-changed_at>=1000 && !(GetAsyncKeyState(VK_LBUTTON)&0x8000)){
        WritePrivateProfileStringW(L"Bar",L"x",std::to_wstring(observed.x).c_str(),config_path.c_str());
        WritePrivateProfileStringW(L"Bar",L"y",std::to_wstring(observed.y).c_str(),config_path.c_str());
        WritePrivateProfileStringW(L"Bar",L"mode",std::to_wstring(observed.mode).c_str(),config_path.c_str());
        saved=observed;changed_at=0;
    }
}
}
