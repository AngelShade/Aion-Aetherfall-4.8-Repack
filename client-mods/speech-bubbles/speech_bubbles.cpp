#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <cstring>
#include <cwchar>
#include <map>
#include <string>

// Read-only native diagnostics; no message text, account data, or file logging.
extern "C" __declspec(dllexport) volatile DWORD AionSpeechDiagnostics[13]={5,0,0,0,0,0,0,0,0,0,0,0,0};

// Aion 4.8 NA x64 only; installer verifies the complete client binary.
namespace {
using Ptr = void*;
unsigned char* game;
DWORD characterId, firstSeen, lastSync;
bool connected, acknowledged;
int ownStyle;
thread_local int drawingStyle;
std::map<DWORD,int> styles;
const wchar_t* labels[] = {L"Classic", L"Wings", L"Crystal", L"Cloud", L"Paws"};
const char* commands[] = {"speech_bubble_0", "speech_bubble_1", "speech_bubble_2", "speech_bubble_3", "speech_bubble_4"};
template<class T> T method(Ptr object, size_t off) {
    return reinterpret_cast<T>((*reinterpret_cast<void***>(object))[off/8]);
}
void init() {
    if (!game) game=reinterpret_cast<unsigned char*>(GetModuleHandleW(L"game.dll"));
}
bool inWorld() {
    init(); if (!game) return false;
    // Native SendChat's final socket/enable guard (33d960..33d982).
    // 13122f0 is g_gameReplay, not connection state; normal play has value 0.
    return *reinterpret_cast<Ptr*>(game+0x133bb30)
        && *reinterpret_cast<unsigned char*>(game+0x133bb38)!=0;
}
bool send(const wchar_t* command) {
    if (!inWorld()) return false;
    reinterpret_cast<void(*)(Ptr,int,const wchar_t*)>(game+0x33d790)(nullptr,0,command);
    return true;
}
void synchronize() {
    if (send(L".speechbubble sync")) lastSync=GetTickCount();
}
int choice(const char* name) {
    if (name) for (int i=0;i<5;i++) if (!std::strcmp(name,commands[i])) return i;
    return -1;
}
void refreshModel(Ptr model,int depth=0) {
    if (!model || depth>3) return;
    auto bytes=static_cast<unsigned char*>(model);
    int selected=choice(reinterpret_cast<const char*>(bytes+4));
    if (selected>=0) {
        *reinterpret_cast<int*>(bytes+0x1a0)=selected==ownStyle;
        *reinterpret_cast<int*>(bytes+0x19c)=selected!=ownStyle;
    }
    auto begin=*reinterpret_cast<Ptr**>(bytes+0x1b0);
    auto end=*reinterpret_cast<Ptr**>(bytes+0x1b8);
    if (begin && end>=begin && end-begin<64)
        for (auto entry=begin;entry!=end;++entry) refreshModel(*entry,depth+1);
}
void refreshWidgets(Ptr popup) {
    if (!popup) return;
    auto bytes=static_cast<unsigned char*>(popup);
    refreshModel(*reinterpret_cast<Ptr*>(bytes+0x598));
    // Native child list, also traversed by checkbox SetAlpha at 4a5117.
    auto head=*reinterpret_cast<Ptr*>(bytes+0x2a0);
    if (!head) return;
    auto node=*reinterpret_cast<Ptr*>(head);
    for (int count=0;node!=head && count<64;++count) {
        Ptr child=*reinterpret_cast<Ptr*>(static_cast<unsigned char*>(node)+0x10);
        if (child) {
            int selected=choice(method<const char*(*)(Ptr)>(child,0xa8)(child));
            if (selected>=0) {
                method<void(*)(Ptr,int)>(child,0x598)(child,selected==ownStyle);
                // Native popup factory uses flag 2 for enabled/disabled rows.
                method<void(*)(Ptr,int,int)>(child,selected==ownStyle?0xd8:0xd0)(child,2,0);
            }
        }
        node=*reinterpret_cast<Ptr*>(node);
    }
}
}

extern "C" __declspec(dllexport) void AionSpeechTick() {
    bool active=inWorld(); DWORD now=GetTickCount();
    if (active!=connected) {
        connected=active; firstSeen=now; lastSync=0; acknowledged=false;
        characterId=0; ownStyle=0; styles.clear();
    }
    if (active && !acknowledged && now-firstSeen>3000 && (!lastSync || now-lastSync>10000)) synchronize();
}

extern "C" __declspec(dllexport) const wchar_t* AionSpeechMessage(const wchar_t* name,int type,DWORD id,const wchar_t* message) {
    init();
    if (!name || (type!=0 && type!=3)) return name;
    const wchar_t prefix[]=L"~ASB1:";
    for (size_t i=0;i<6;i++) if (name[i]!=prefix[i]) return name;
    if (name[6]<L'0' || name[6]>L'4' || name[7]!=L':') return name;
    int style=name[6]-L'0';
    bool ack=message && !std::wcscmp(message,L"~ASB_ACK1~");
    if (id) {
        if (styles.size()>4096) styles.clear();
        styles[id]=style;
        if (ack) { characterId=id; ownStyle=style; acknowledged=true; }
        else if (id==characterId) ownStyle=style;
    }
    if (ack) ++AionSpeechDiagnostics[3];
    AionSpeechDiagnostics[8]=ownStyle; AionSpeechDiagnostics[9]=characterId;
    return ack ? nullptr : name+8;
}

extern "C" __declspec(dllexport) int AionSpeechSkin(Ptr bubble) {
    drawingStyle=0;
    if (!bubble) return 0;
    int original=*reinterpret_cast<int*>(static_cast<unsigned char*>(bubble)+0x10);
    if (original!=0 && original!=1) return original;
    DWORD id=*reinterpret_cast<DWORD*>(static_cast<unsigned char*>(bubble)+0x14);
    auto it=styles.find(id); int style=it==styles.end()?0:it->second;
    drawingStyle=style;
    const int skins[]={-1,2,1,3,4};
    int result=style>0 && style<=4 ? skins[style] : original;
    ++AionSpeechDiagnostics[4]; AionSpeechDiagnostics[5]=id;
    AionSpeechDiagnostics[6]=original; AionSpeechDiagnostics[7]=result;
    return result;
}

extern "C" __declspec(dllexport) Ptr AionSpeechArtwork(Ptr nativeSkin,double* rect) {
    // Only player normal/shout bubbles reach a custom style. Stock NPC, pet,
    // advert and emote resources remain intact. Registry owns the renderers;
    // lookup on every draw avoids keeping stale pointers after a UI reload.
    if (drawingStyle<1 || drawingStyle>3 || !rect) return nativeSkin;
    init(); if (!game) return nativeSkin;
    const wchar_t* names[]={L"asb_ornate",L"asb_amber",L"asb_thought"};
    Ptr resource=reinterpret_cast<Ptr(*)(Ptr,const wchar_t*)>(game+0x58c040)(game+0x1378d40,names[drawingStyle-1]);
    Ptr renderer=resource ? method<Ptr(*)(Ptr)>(resource,0x80)(resource) : nullptr;
    if (!renderer) { ++AionSpeechDiagnostics[11]; return nativeSkin; }
    const double minWidth[]={126,108,142}, extraSides[]={14,8,20};
    const double extraTop[]={14,8,22}, extraBottom[]={6,4,18}, minHeight[]={82,72,108};
    int i=drawingStyle-1;
    double added=extraSides[i]*2;
    if (rect[2]+added<minWidth[i]) added=minWidth[i]-rect[2];
    rect[0]-=added/2; rect[2]+=added;
    double height=rect[3]+extraTop[i]+extraBottom[i];
    double extra=height<minHeight[i] ? minHeight[i]-height : 0;
    rect[1]-=extraTop[i]+extra/2; rect[3]=height+extra;
    ++AionSpeechDiagnostics[10]; AionSpeechDiagnostics[12]=drawingStyle;
    return renderer;
}

extern "C" __declspec(dllexport) void AionSpeechMenuBuild(Ptr parent) {
    init(); if (!game || !parent) return;
    AionSpeechTick();
    if (!acknowledged) synchronize();
    using Add=Ptr(*)(Ptr,int,const char*,const wchar_t*,const float*,const wchar_t*,int,int,int,int,const wchar_t*);
    auto add=reinterpret_cast<Add>(game+0x534ea0);
    const float color[]={1,1,1,1};
    Ptr submenu=add(parent,2,"speech_bubble",L"Chat Bubble",color,nullptr,1,0,2,1,nullptr);
    if (!submenu) return;
    for (int i=0;i<5;i++) {
        // Native Font Size/Emotes path accepts wide captions directly.
        // Native item type 3 creates a checkbox. The renderer reads +1a0 and
        // calls the checkbox's SetChecked method (536a29..536ab8).
        Ptr item=add(submenu,3,commands[i],labels[i],color,nullptr,ownStyle!=i,0,2,1,nullptr);
        if (item) *reinterpret_cast<int*>(static_cast<unsigned char*>(item)+0x1a0)=ownStyle==i;
    }
}

extern "C" __declspec(dllexport) void AionSpeechMenuRefresh(Ptr,Ptr model) {
    refreshModel(model);
}

extern "C" __declspec(dllexport) int AionSpeechMenuCommand(Ptr popup,Ptr event) {
    if (!event) return 0;
    Ptr widget=*reinterpret_cast<Ptr*>(event); if (!widget) return 0;
    const char* name=method<const char*(*)(Ptr)>(widget,0xa8)(widget);
    if (!name) return 0;
    for (int i=0;i<5;i++) if (!std::strcmp(name,commands[i])) {
        ++AionSpeechDiagnostics[1];
        std::wstring command=L".speechbubble "+std::to_wstring(i);
        if (i!=ownStyle && send(command.c_str())) {
            ++AionSpeechDiagnostics[2];
            ownStyle=i;
            if (characterId) styles[characterId]=i;
        }
        AionSpeechDiagnostics[8]=ownStyle; AionSpeechDiagnostics[9]=characterId;
        refreshWidgets(popup);
        return 1;
    }
    return 0;
}

#ifdef ASB_TEST
extern "C" __declspec(dllexport) void AionSpeechTestGame(Ptr base) { game=static_cast<unsigned char*>(base); }
#endif
BOOL WINAPI DllMain(HINSTANCE instance,DWORD reason,LPVOID) {
    if (reason==DLL_PROCESS_ATTACH) DisableThreadLibraryCalls(instance);
    return TRUE;
}
