// Isolated native UI/transport contract. Never attaches to a live game.
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <algorithm>
#include <cassert>
#include <cmath>
#include <cstring>
#include <filesystem>
#include <iostream>
#include <string>
#include <unordered_map>
#include <vector>
using Ptr=void*;
HMODULE own_module=nullptr;
std::filesystem::path fixture_root;
ULONGLONG fixture_time=1000;
ULONGLONG fixture_clock(){return fixture_time;}
short fixture_mouse(int){return 0;}
DWORD fixture_module(HMODULE,wchar_t* output,DWORD size){auto p=(fixture_root/L"bin64/Fake.dll").wstring();assert(p.size()+1<size);std::wcscpy(output,p.c_str());return DWORD(p.size());}
#define GetTickCount64 fixture_clock
#define GetAsyncKeyState fixture_mouse
#define GetModuleFileNameW fixture_module
namespace wardrobe {
struct Rect{double x,y,w,h;};
struct Widget{void** vtable;Rect bounds;std::string name;std::string text;std::unordered_map<std::string,Widget*> children;};
unsigned char* memory=nullptr;
unsigned char* game(){return memory;}
template<class T>T method(Ptr p,size_t n){return reinterpret_cast<T>((*static_cast<void***>(p))[n/8]);}
Rect rect(Ptr p){return static_cast<Widget*>(p)->bounds;}
void rect(Ptr p,Rect r){static_cast<Widget*>(p)->bounds=r;}
Ptr lookup(Ptr p,const char* name,int){auto& children=static_cast<Widget*>(p)->children;auto it=children.find(name);return it==children.end()?nullptr:it->second;}
namespace native_ui {
std::string read(Ptr p,size_t limit){return p && static_cast<Widget*>(p)->text.size()<=limit?static_cast<Widget*>(p)->text:"";}
void write(Ptr p,const std::wstring& text){if(p)static_cast<Widget*>(p)->text=std::string(text.begin(),text.end());}
}
}
#include "playerbot_bar.h"
std::vector<std::wstring> packets;
void send(Ptr,int channel,const wchar_t* text){assert(channel==0);packets.emplace_back(text);}
const char* name(Ptr p){return static_cast<wardrobe::Widget*>(p)->name.c_str();}
int main(int argc,char** argv){
    assert(argc==2);fixture_root=std::filesystem::absolute(argv[1]);std::filesystem::create_directories(fixture_root/L"bin64");
    const auto ini=(fixture_root/L"PlayerBotBar.ini").wstring();
    WritePrivateProfileStringW(L"Bar",L"x",L"-200",ini.c_str());WritePrivateProfileStringW(L"Bar",L"y",L"9000",ini.c_str());WritePrivateProfileStringW(L"Bar",L"mode",L"2",ini.c_str());
    auto g=static_cast<unsigned char*>(VirtualAlloc(nullptr,0x1390000,MEM_COMMIT|MEM_RESERVE,PAGE_EXECUTE_READWRITE));assert(g);wardrobe::memory=g;
    *reinterpret_cast<double*>(g+0x1378ea8)=1280;*reinterpret_cast<double*>(g+0x1378eb0)=720;
    *reinterpret_cast<Ptr*>(g+0x133bb30)=g;g[0x133bb38]=1;
    unsigned char code[]={0x48,0xb8,0,0,0,0,0,0,0,0,0xff,0xe0};auto target=&send;std::memcpy(code+2,&target,8);std::memcpy(g+0x33d790,code,sizeof(code));
    void* vtable[22]{};vtable[0xa8/8]=reinterpret_cast<void*>(&name);
    wardrobe::Widget bar{vtable,{220,180,320,64},"PlayerBotBar"},tx{},rx{},meta{},mode{};
    bar.children={{"PlayerBotBarTx",&tx},{"PlayerBotBarRx",&rx},{"PlayerBotBarMeta",&meta},{"PlayerBotBarMode",&mode}};
    *reinterpret_cast<Ptr*>(g+0x13875c0+0x215*8)=&bar;
    auto tick=[](){fixture_time+=100;companion_bar::tick();};tick();
    assert(meta.text=="1280|720|2|1" && bar.bounds.x==0 && bar.bounds.y==656);
    mode.text="2";tick();assert(meta.text=="1280|720|2|1");
    for(const auto& command:{"attack","follow","stay","guard","passive","summon"}){
        fixture_time+=300;tx.text=command;tick();assert(tx.text.empty() && rx.text=="sent");
        assert(packets.back()==std::wstring(L".bot ")+std::wstring(command,command+std::strlen(command))+L" all");
    }
    for(const auto& shape:{"circle","box","line","spread"}){
        fixture_time+=300;tx.text=std::string("formation_")+shape;tick();assert(tx.text.empty() && rx.text=="sent");
        assert(packets.back()==std::wstring(L".bot formation ")+std::wstring(shape,shape+std::strlen(shape)));
    }
    auto count=packets.size();tx.text="attack";tick();assert(packets.size()==count && tx.text.empty() && rx.text!="sent");
    fixture_time+=300;tx.text="dismiss all";tick();assert(packets.size()==count && tx.text.empty());
    tx.text="attack";g[0x133bb38]=0;tick();assert(packets.size()==count && tx.text.empty() && meta.text=="1280|720|2|0");
    g[0x133bb38]=1;tick();assert(packets.size()==count && "No stale Attack on reconnect");
    bar.bounds={400,300,44,64};tick();fixture_time+=1200;tick();
    assert(GetPrivateProfileIntW(L"Bar",L"x",0,ini.c_str())==400 && GetPrivateProfileIntW(L"Bar",L"mode",0,ini.c_str())==2);
    *reinterpret_cast<double*>(g+0x1378ea8)=640;*reinterpret_cast<double*>(g+0x1378eb0)=480;bar.bounds={900,800,320,110};tick();assert(bar.bounds.x==320 && bar.bounds.y==370);
    VirtualFree(g,0,MEM_RELEASE);
    std::cout<<"OK: six party commands and four formation selections; throttling, disconnect clearing, invalid-command refusal, viewport clamps and persisted icon position.\n";
}
