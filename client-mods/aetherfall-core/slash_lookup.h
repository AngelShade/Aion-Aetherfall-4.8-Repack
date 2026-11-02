// Slash-command lookup without Lua on the game thread (included by aetherfall_core.cpp).
// Game.dll 0x628270 asks IsSlashCmd whether a line is a plugin command, then queues it for
// the addon worker. That question runs Lua on the game thread while the worker runs the same
// state with no lock, and one collision overwrote Lua's shared nil, which disabled every
// plugin command and froze the worker. Answer from the worker's published list instead;
// the queued command still runs through the stock DoSlashCmd on the worker.
#include "slash_names.h"
extern "C" __declspec(dllexport) volatile uintptr_t AetherfallSlashDiagnostics[4]={0,0,0,0};
// [0] 1 installed, 2 site differs, 3 relay/protection failed; [1] list answers; [2] stock answers.
constexpr size_t SLASH_LOOKUP_SITE=0x62832a, IS_SLASH_COMMAND=0x6254a0;
using IsSlashFn=int(*)(Ptr,const char*);
int slash_lookup(Ptr self,const char* line){
    const slash_names::List* list=slash_names::current.load(std::memory_order_acquire);
    if(!list){  // Nothing published yet: the worker has not reached its first timer tick.
        ++AetherfallSlashDiagnostics[2];
        return native<IsSlashFn>(IS_SLASH_COMMAND)(self,line);
    }
    ++AetherfallSlashDiagnostics[1];
    return slash_names::known(*list,line)?1:0;
}
bool install_slash_lookup(){
    unsigned char expected[5]{0xe8};int32_t native_displacement=int32_t(intptr_t(IS_SLASH_COMMAND)-intptr_t(SLASH_LOOKUP_SITE)-5);
    std::memcpy(expected+1,&native_displacement,4);
    auto site=game()+SLASH_LOOKUP_SITE;
    if(std::memcmp(site,expected,5)){AetherfallSlashDiagnostics[0]=2;return false;}
    // Same near relay as the quest hooks: the native call stays five bytes.
    uintptr_t center=reinterpret_cast<uintptr_t>(game()),lower=center>0x60000000?center-0x60000000:0x10000,upper=center+0x60000000;
    unsigned char* relay=nullptr;MEMORY_BASIC_INFORMATION region{};
    for(uintptr_t cursor=lower;cursor<upper;){
        if(!VirtualQuery(reinterpret_cast<void*>(cursor),&region,sizeof region))break;
        uintptr_t begin=(reinterpret_cast<uintptr_t>(region.BaseAddress)+0xffff)&~uintptr_t(0xffff),end=reinterpret_cast<uintptr_t>(region.BaseAddress)+region.RegionSize;
        if(region.State==MEM_FREE && begin<end && end-begin>=0x10000){relay=static_cast<unsigned char*>(VirtualAlloc(reinterpret_cast<void*>(begin),0x10000,MEM_RESERVE|MEM_COMMIT,PAGE_READWRITE));if(relay)break;}
        if(end<=cursor)break;cursor=end;
    }
    if(!relay){AetherfallSlashDiagnostics[0]=3;return false;}
    const void* target=reinterpret_cast<const void*>(&slash_lookup);
    relay[0]=0xff;relay[1]=0x25;std::memset(relay+2,0,4);std::memcpy(relay+6,&target,8);
    DWORD prior=0,unused=0,old=0;
    if(!VirtualProtect(relay,0x10000,PAGE_EXECUTE_READ,&prior)){VirtualFree(relay,0,MEM_RELEASE);AetherfallSlashDiagnostics[0]=3;return false;}
    FlushInstructionCache(GetCurrentProcess(),relay,16);
    if(!VirtualProtect(site,5,PAGE_EXECUTE_READWRITE,&old)){VirtualFree(relay,0,MEM_RELEASE);AetherfallSlashDiagnostics[0]=3;return false;}
    int32_t displacement=int32_t(reinterpret_cast<intptr_t>(relay)-reinterpret_cast<intptr_t>(site)-5);
    site[0]=0xe8;std::memcpy(site+1,&displacement,4);VirtualProtect(site,5,old,&unused);FlushInstructionCache(GetCurrentProcess(),site,5);
    AetherfallSlashDiagnostics[0]=1;return true;
}
