// Expands the existing journal storage and checked-quest owner at normal startup.
// Native quest eligibility, packets, handlers and manually unchecked saves remain authoritative.
namespace quest_journal {
#include "quest_journal_data.h"
constexpr int CAPACITY=2048;
extern "C" __declspec(dllexport) volatile uintptr_t AetherfallJournalDiagnostics[4]={0,0,0,0};
struct Patch { size_t rva;std::vector<unsigned char> before,after; };
unsigned char* memory=nullptr;
bool installed=false;
constexpr size_t JOURNAL_RECT_CHANGED=0xcd07b8+0x480;
void frame_changed(Ptr self,const Rect* previous){
    native<void(*)(Ptr,const Rect*)>(0x4ae130)(self,previous);
    if(!previous || self!=dialog(0xb8) || !shown(self))return;
    const Rect current=field<Rect>(self,0x50);
    if(current.x==previous->x && current.y==previous->y && current.w==previous->w && current.h==previous->h)return;
    Ptr detail=dialog(0xb9);
    if(!detail || !shown(detail))return;
    // DlgQuestSummary's original OnVisible (0x852bb0) uses this same pairing:
    // right of Q, or left if the pair would exceed the viewport, at Q's height.
    Rect journal=screen_rect(self),present=screen_rect(detail),target=present;
    target.x=journal.x+journal.w;target.y=journal.y;
    if(target.x+target.w>global<double>(UI_WIDTH))target.x=journal.x-target.w;
    if(present.x==target.x && present.y==target.y)return;
    target=local_rect(detail,target);
    method<uintptr_t(*)(Ptr,const Rect*)>(detail,0x1a8)(detail,&target);
}
int npc_quests(Ptr,int npc,int* out,int capacity){
    int count=global<int>(QUEST_COUNT);
    if(!installed || !out || capacity<=0 || count<0 || count>CAPACITY)return 0;
    auto object=native<Ptr(*)(Ptr,int)>(0xa17ad0)(game()+0xed0be0,npc);
    const wchar_t* name=object?field<const wchar_t*>(object,0x58):nullptr;
    if(!name || !readable(name,sizeof(wchar_t)) || !*name)return 0;
    if(!native<int(*)(Ptr,const wchar_t*)>(0x98e10)(game()+0x12ee140,name))return 0;
    std::vector<int> matches(size_t(count),0);
    native<void(*)(Ptr,const wchar_t*,const QuestRecord*,int,int*)>(0x9c4d0)(game()+0x12ee140,name,quest_records(),count,matches.data());
    int written=0;
    for(int i=0;i<count && written<capacity;++i)if(matches[size_t(i)] && quest_records()[i].status!=4)out[written++]=quest_records()[i].id;
    return written;
}
bool checked_quest(int quest){
    auto first=global<const int*>(0x13715e8),last=global<const int*>(0x13715f0);
    return first && last>=first && last-first<=CAPACITY && readable(first,size_t(last-first)*sizeof(int))
        && std::find(first,last,quest)!=last;
}
int arrival_row(Ptr tracker,int kind,int quest,int forced){
    auto create=native<int(*)(Ptr,int,int,int)>(0x85bd20);
    if(kind!=5 || !installed || !checked_quest(quest))return create(tracker,kind,quest,forced);
    // Only the arrival owner's temporary-row request is redirected. Its next
    // history-row call, pending queue, effect selection and animation stay native.
    int result=create(tracker,0,quest,forced);
    if(!result)return create(tracker,kind,quest,forced);
    // Establish the checked top position and native relevant-tab destinations
    // before the original owner binds its effect. No effect has started yet.
    native<void(*)(Ptr,int)>(0x856bf0)(tracker,1);
    ++AetherfallJournalDiagnostics[3];
    return result;
}
void accepted(Ptr tracker,int quest){
    help_quest::added(quest);
    const auto* entry=active_quest(quest);
    bool checked=false;
    if(installed && entry){
        native<int(*)(Ptr,int,int)>(0x3f6120)(game()+0x13715e0,quest,1);
        checked=checked_quest(quest);
    }
    if(checked){
        ++AetherfallJournalDiagnostics[1];
    }
    // Native row selection, arrival queue, effect and animation stay native.
    // The destination now exists at the front of the native checked ordering.
    native<void(*)(Ptr,int)>(0x8590c0)(tracker,quest);
}
bool relative(intptr_t value){return value>=INT32_MIN && value<=INT32_MAX;}
unsigned char* allocate(){
    uintptr_t center=reinterpret_cast<uintptr_t>(game()),lower=center>0x50000000?center-0x50000000:0x10000,upper=center+0x50000000;
    MEMORY_BASIC_INFORMATION region{};
    for(uintptr_t cursor=lower;cursor<upper;){
        if(!VirtualQuery(reinterpret_cast<void*>(cursor),&region,sizeof region))break;
        uintptr_t begin=(reinterpret_cast<uintptr_t>(region.BaseAddress)+0xffff)&~uintptr_t(0xffff),end=reinterpret_cast<uintptr_t>(region.BaseAddress)+region.RegionSize;
        if(region.State==MEM_FREE && begin<end && end-begin>=0x20000){auto p=static_cast<unsigned char*>(VirtualAlloc(reinterpret_cast<void*>(begin),0x20000,MEM_RESERVE|MEM_COMMIT,PAGE_READWRITE));if(p)return p;}
        if(end<=cursor)break;cursor=end;
    }
    return nullptr;
}
bool install(){
    if(installed)return true;
    for(const auto& site:JOURNAL_SITES)if(std::memcmp(game()+site.rva,site.before,site.size)){AetherfallJournalDiagnostics[0]=2;return false;}
    const unsigned char helper[]={0x4c,0x8b,0xdc,0x48,0x81,0xec,0x88,0x04,0,0,0x4d,0x85,0xc0,0x49,0x89,0x6b,0xf0};
    const unsigned char favorite1[]={0x49,0x83,0xf9,0x0e,0x72,0x30},favorite2[]={0x48,0x83,0xf9,0x0e,0x73,0x7d};
    const unsigned char added[]={0xe8,0x06,0x63,0x46,0};
    const unsigned char row_added[]={0xe8,0x27,0x2b,0,0};
    uintptr_t frame_original=reinterpret_cast<uintptr_t>(game()+0x4ae130);
    if(std::memcmp(game()+0x3f7120,helper,sizeof helper) || std::memcmp(game()+0x3f616d,favorite1,6)
       || std::memcmp(game()+0x3f6394,favorite2,6) || std::memcmp(game()+0x3f2db5,added,5)
       || std::memcmp(game()+0x8591f4,row_added,5)
       || std::memcmp(game()+JOURNAL_RECT_CHANGED,&frame_original,sizeof frame_original)){
        AetherfallJournalDiagnostics[0]=2;return false;
    }
    auto block=allocate();if(!block){AetherfallJournalDiagnostics[0]=3;return false;}
    intptr_t primary=reinterpret_cast<intptr_t>(block+0x10000)-reinterpret_cast<intptr_t>(game());
    intptr_t secondary=primary+CAPACITY*16;
    std::vector<Patch> patches;
    Patch frame_patch{JOURNAL_RECT_CHANGED,std::vector<unsigned char>(sizeof(uintptr_t)),std::vector<unsigned char>(sizeof(uintptr_t))};
    uintptr_t frame_replacement=reinterpret_cast<uintptr_t>(&frame_changed);
    std::memcpy(frame_patch.before.data(),&frame_original,sizeof frame_original);
    std::memcpy(frame_patch.after.data(),&frame_replacement,sizeof frame_replacement);
    patches.push_back(std::move(frame_patch));
    const unsigned char mapOption[]={0x75,0x4b},mapAge[]={0x0f,0x83,0x64,0x03,0,0},headOption[]={0x75,0x39},headAge[]={0x41,0x83,0xf8,0x07,0x8b,0xc6,0x0f,0x92,0xc0};
    // Both real marker entry points already have native/server acceptance masks. Remove only old-quest display suppression.
    const size_t markerSites[]={0x3fe4d5,0x3fe53e,0x400ce2,0x400d3e};
    const unsigned char* markerBefore[]={mapOption,mapAge,headOption,headAge};
    const size_t markerSizes[]={2,6,2,9};
    for(int i=0;i<4;++i){
        if(std::memcmp(game()+markerSites[i],markerBefore[i],markerSizes[i])){VirtualFree(block,0,MEM_RELEASE);AetherfallJournalDiagnostics[0]=2;return false;}
        Patch patch{markerSites[i],{markerBefore[i],markerBefore[i]+markerSizes[i]},std::vector<unsigned char>(markerSizes[i],0x90)};
        if(i==0 || i==2){patch.after=patch.before;patch.after[0]=0xeb;}
        if(i==3){patch.after[0]=0xb8;int one=1;std::memcpy(patch.after.data()+1,&one,4);}
        patches.push_back(std::move(patch));
    }
    for(const auto& site:JOURNAL_SITES){
        Patch patch{site.rva,{site.before,site.before+site.size},{site.before,site.before+site.size}};
        intptr_t value=site.kind==0?primary+intptr_t(site.target)-0x1372760-intptr_t(site.rva+site.size)
            :site.kind==1?secondary+intptr_t(site.target)-0x137383c-intptr_t(site.rva+site.size)
            :site.kind==2?primary-0x13715e0:site.kind==3?secondary-0x13715e0:intptr_t(site.target);
        if(!relative(value)){VirtualFree(block,0,MEM_RELEASE);AetherfallJournalDiagnostics[0]=3;return false;}
        int32_t operand=int32_t(value);std::memcpy(patch.after.data()+site.offset,&operand,4);patches.push_back(std::move(patch));
    }
    auto jump=[&](size_t rva,const unsigned char* old,size_t size,unsigned char* target,bool call){
        Patch patch{rva,{old,old+size},std::vector<unsigned char>(size,0x90)};
        patch.after[0]=call?0xe8:0xe9;int32_t delta=int32_t(target-(game()+rva+5));std::memcpy(patch.after.data()+1,&delta,4);patches.push_back(std::move(patch));
    };
    auto relay=[&](size_t offset,const void* target){auto p=block+offset;p[0]=0xff;p[1]=0x25;std::memset(p+2,0,4);std::memcpy(p+6,&target,8);return p;};
    jump(0x3f7120,helper,sizeof helper,relay(0,reinterpret_cast<const void*>(&npc_quests)),false);
    jump(0x3f2db5,added,sizeof added,relay(16,reinterpret_cast<const void*>(&accepted)),true);
    jump(0x8591f4,row_added,sizeof row_added,relay(32,reinterpret_cast<const void*>(&arrival_row)),true);
    // Widen both native 14-favorite checks without growing an instruction in place.
    for(int i=0;i<2;++i){
        auto stub=block+64+i*32;stub[0]=i?0x48:0x49;stub[1]=0x81;stub[2]=0xf9;int limit=CAPACITY;std::memcpy(stub+3,&limit,4);
        stub[7]=0x0f;stub[8]=i?0x83:0x82;
        size_t taken=i?0x3f6417:0x3f61a3,fall=i?0x3f639a:0x3f6173;
        int32_t branch=int32_t(game()+taken-(stub+13));std::memcpy(stub+9,&branch,4);
        stub[13]=0xe9;branch=int32_t(game()+fall-(stub+18));std::memcpy(stub+14,&branch,4);
        jump(i?0x3f6394:0x3f616d,i?favorite2:favorite1,6,stub,false);
    }
    std::memcpy(block+0x10000,game()+0x1372760,266*16);
    std::memcpy(block+0x18000,game()+0x137383c,266*16);
    DWORD old=0;if(!VirtualProtect(block,0x10000,PAGE_EXECUTE_READ,&old)){VirtualFree(block,0,MEM_RELEASE);AetherfallJournalDiagnostics[0]=3;return false;}
    std::unordered_map<uintptr_t,DWORD> pages;
    for(const auto& patch:patches){
        uintptr_t first=reinterpret_cast<uintptr_t>(game()+patch.rva)&~uintptr_t(4095),last=reinterpret_cast<uintptr_t>(game()+patch.rva+patch.after.size()-1)&~uintptr_t(4095);
        for(uintptr_t page=first;page<=last;page+=4096)if(!pages.count(page)){
            DWORD prior=0;if(!VirtualProtect(reinterpret_cast<void*>(page),4096,PAGE_EXECUTE_READWRITE,&prior)){
                for(auto saved:pages){DWORD unused=0;VirtualProtect(reinterpret_cast<void*>(saved.first),4096,saved.second,&unused);}
                VirtualFree(block,0,MEM_RELEASE);AetherfallJournalDiagnostics[0]=3;return false;
            }pages.emplace(page,prior);
        }
    }
    memory=block;quest_record_override=reinterpret_cast<QuestRecord*>(block+0x10000);quest_record_capacity=CAPACITY;
    for(const auto& patch:patches)std::memcpy(game()+patch.rva,patch.after.data(),patch.after.size());
    for(auto page:pages){DWORD unused=0;VirtualProtect(reinterpret_cast<void*>(page.first),4096,page.second,&unused);FlushInstructionCache(GetCurrentProcess(),reinterpret_cast<void*>(page.first),4096);}
    FlushInstructionCache(GetCurrentProcess(),block,128);installed=true;AetherfallJournalDiagnostics[0]=1;AetherfallJournalDiagnostics[2]=patches.size();return true;
}
}
