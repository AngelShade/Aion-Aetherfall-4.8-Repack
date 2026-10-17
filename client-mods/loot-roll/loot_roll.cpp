#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <algorithm>
#include <initializer_list>
#include <cmath>
#include <cstdint>
#include <cstring>

// Aion 4.8 NA x64 native dlg_loot_roll. Called only on the native UI thread.
// Item icons/tooltips, countdown, packet writer and bid handling stay native.
namespace {
using Ptr=void*;
struct Rect { double x,y,w,h; };
unsigned char* game(){return reinterpret_cast<unsigned char*>(GetModuleHandleW(L"Game.dll"));}
template<class T>T method(Ptr p,size_t slot){return reinterpret_cast<T>((*reinterpret_cast<void***>(p))[slot/8]);}
template<class T>T& field(Ptr p,size_t offset){return *reinterpret_cast<T*>(static_cast<unsigned char*>(p)+offset);}
Ptr find(Ptr p,const char* name,int type=0x2001){return method<Ptr(*)(Ptr,const char*,int)>(p,0x338)(p,name,type);}
void text(Ptr p,const wchar_t* value){if(p)method<void(*)(Ptr,const wchar_t*)>(p,0x290)(p,value);}
void visible(Ptr p,bool value){if(p)method<void(*)(Ptr,int,int)>(p,value?0xd0:0xd8)(p,1,0);}
Ptr entry(int slot){
    auto g=game();if(!g || slot<0 || slot>=5)return nullptr;
    Ptr player=*reinterpret_cast<Ptr*>(g+0x12eda48);if(!player)return nullptr;
    Ptr sentinel=field<Ptr>(player,0xe68);if(!sentinel)return nullptr;
    Ptr cursor=field<Ptr>(sentinel,0);
    for(int count=0;cursor && cursor!=sentinel && count<512;++count,cursor=field<Ptr>(cursor,0)){
        Ptr item=field<Ptr>(cursor,0x10);
        if(item && field<int>(item,0x6e8)==slot)return item;
    }
    return nullptr;
}
void bounds(Ptr p,Rect r){if(p && std::memcmp(&field<Rect>(p,0x50),&r,sizeof r))method<void(*)(Ptr,const Rect*)>(p,0x1a8)(p,&r);}
struct Vote { int corpse=0,index=0,item=0;bool sent=false;int choice=0; } votes[5];
bool same(Vote& v,Ptr item){return v.corpse==field<int>(item,0x6dc) && v.index==field<int>(item,0x6d8) && v.item==field<int>(item,8);}
}

extern "C" __declspec(dllexport) void AionLootLayout(Ptr dialog){
    auto g=game();if(!g || !dialog)return;
    int slot=field<int>(dialog,0x340)-0x123;if(slot<0 || slot>=5)return;
    Ptr item=entry(slot);bool rolling=item && field<int>(item,0x6e0)==2;
    Ptr greed=find(dialog,"greed");
    text(find(dialog,"roll"),rolling?L"Need":L"Bid");text(find(dialog,"abandon"),L"Pass");
    if(!item)return;
    Vote& vote=votes[slot];
    if(!same(vote,item)){vote={field<int>(item,0x6dc),field<int>(item,0x6d8),field<int>(item,8),false,0};}
    visible(greed,rolling && !vote.sent);
    visible(find(dialog,"roll"),!rolling || !vote.sent);visible(find(dialog,"abandon"),!rolling || !vote.sent);
    Ptr choice=find(dialog,"your_choice",0x2022);visible(choice,rolling && vote.sent);
    if(vote.sent)text(choice,vote.choice==1?L"Your choice: Need":vote.choice==2?L"Your choice: Greed":L"Your choice: Pass");
    double scale=*reinterpret_cast<double*>(g+0x1378ec8),width=*reinterpret_cast<double*>(g+0x1378ea8),height=*reinterpret_cast<double*>(g+0x1378eb0);
    if(!std::isfinite(scale) || scale<=0 || scale>4 || !std::isfinite(width) || !std::isfinite(height))return;
    int count=0,row=0;
    for(int i=0;i<5;++i){
        Ptr peer=*reinterpret_cast<Ptr*>(g+0x13875c0+(0x123+i)*8);
        if(peer && (method<int(*)(Ptr)>(peer,0xb8)(peer)&1)) {if(i<slot)++row;++count;}
    }
    double h=108*scale,gap=5*scale,total=std::max(1,count)*(h+gap),top=std::max(8*scale,std::min(height*.24,height-total-12*scale));
    bounds(dialog,{std::max(0.0,width-350*scale),top+row*(h+gap),334*scale,h});
}

extern "C" __declspec(dllexport) int AionLootCommand(Ptr dialog,Ptr event){
    if(!dialog || !event)return 0;
    Ptr button=*static_cast<Ptr*>(event);if(!button)return 0;
    const char* name=method<const char*(*)(Ptr)>(button,0xa8)(button);if(!name)return 0;
    int choice=!std::strcmp(name,"roll")?1:!std::strcmp(name,"greed")?2:!std::strcmp(name,"abandon")?0:-1;
    if(choice<0)return 0;
    int slot=field<int>(dialog,0x340)-0x123;Ptr item=entry(slot);
    if(!item || field<int>(item,0x6e0)!=2)return 0; // Native bidding is unchanged.
    Vote& vote=votes[slot];if(same(vote,item) && vote.sent)return 1;
    // Exact native C_GROUP_ITEM_DIST item descriptor, copied as the native
    // Need and Pass senders do; the only new wire value is decision 2 (Greed).
    unsigned char descriptor[15]{};
    std::memcpy(descriptor,static_cast<unsigned char*>(item)+8,4);
    std::memcpy(descriptor+4,static_cast<unsigned char*>(item)+0x10,4);
    std::memcpy(descriptor+8,static_cast<unsigned char*>(item)+0xc,4);
    descriptor[12]=field<unsigned char>(item,0xbe);descriptor[13]=field<unsigned char>(item,0x6c9);descriptor[14]=field<unsigned char>(item,0xbf);
    vote={field<int>(item,0x6dc),field<int>(item,0x6d8),field<int>(item,8),true,choice};
    using Send=void(*)(Ptr,int,const void*,int,int,int,int64_t);
    reinterpret_cast<Send>(game()+0x347370)(game()+0x1313948,vote.index,descriptor,vote.corpse,2,choice,0);
    // Keep the result/countdown row visible, but disable local repeat choices.
    AionLootLayout(dialog);
    return 1;
}

BOOL WINAPI DllMain(HINSTANCE instance,DWORD reason,LPVOID){if(reason==DLL_PROCESS_ATTACH)DisableThreadLibraryCalls(instance);return TRUE;}
