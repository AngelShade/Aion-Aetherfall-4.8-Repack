// Native 4.8 quest badges and tooltip counters. Included in the bridge namespace.
// Every formatter call remains on the native UI thread. No player state is written.
#include "quest_target_data.h"

extern "C" __declspec(dllexport) volatile uintptr_t AetherfallQuestDiagnostics[8]={0,0,0,0,0,0,0,0};
constexpr size_t QUEST_LIST=0x1372760,QUEST_COUNT=0x137275c,QUEST_LIMIT=0x10a;
struct QuestRecord { int id; unsigned char status; unsigned char packed[4]; unsigned char rest[7]; };
static_assert(sizeof(QuestRecord)==16,"native quest record");
unsigned quest_progress(const QuestRecord& q){unsigned v;std::memcpy(&v,q.packed,4);return v;}
const QuestRecord* active_quest(int id){
    int count=global<int>(QUEST_COUNT);
    if(count<0 || count>int(QUEST_LIMIT))return nullptr;
    const auto* list=reinterpret_cast<const QuestRecord*>(game()+QUEST_LIST);
    for(int i=0;i<count;++i)if(list[i].id==id && list[i].status==3)return list+i;
    return nullptr;
}
bool quest_attackable(int id){
    size_t lo=0,hi=sizeof QUEST_ATTACKABLE_NPCS/sizeof *QUEST_ATTACKABLE_NPCS;
    while(lo<hi){size_t mid=(lo+hi)/2;if(QUEST_ATTACKABLE_NPCS[mid].last<id)lo=mid+1;else hi=mid;}
    return lo<sizeof QUEST_ATTACKABLE_NPCS/sizeof *QUEST_ATTACKABLE_NPCS && QUEST_ATTACKABLE_NPCS[lo].first<=id;
}
Ptr quest_tree_find(Ptr head,int key){
    if(!readable(head,0x30))return nullptr;
    Ptr node=field<Ptr>(head,8),found=head;
    for(int limit=0;limit<64;++limit){
        if(!readable(node,0x30))return nullptr;
        if(field<unsigned char>(node,0x29))break;
        int current=field<int>(node,0x18);
        if(current>=key){found=node;node=field<Ptr>(node,0);}else node=field<Ptr>(node,0x10);
    }
    return found!=head && field<int>(found,0x18)==key?found:nullptr;
}
struct QuestDefinition { int id=0,type=-1,items[4]{},required[4]{}; };
QuestDefinition quest_definitions[QUEST_LIMIT]{};
int quest_definition_count=0;
unsigned quest_definition_next=0;
const QuestDefinition* quest_definition(int id){
    for(int i=0;i<quest_definition_count;++i)if(quest_definitions[i].id==id)return quest_definitions+i;
    Ptr node=quest_tree_find(global<Ptr>(0x12ee180),id);
    Ptr block=node?field<Ptr>(node,0x20):nullptr;
    // The client's own GetQuestInfo decompresses exactly this supported snapshot.
    if(!readable(block,8) || field<int>(block,4)!=0x3ac0)return nullptr;
    alignas(16) unsigned char decoded[0x3ac0]{};
    unsigned bytes=0;
    if(!native<int(*)(Ptr,Ptr,unsigned*)>(0x127950)(block,decoded,&bytes) || bytes!=sizeof decoded)return nullptr;
    int index=quest_definition_count<int(QUEST_LIMIT)?quest_definition_count++:int(quest_definition_next++%QUEST_LIMIT);
    auto& def=quest_definitions[index];def.id=id;def.type=field<int>(decoded,4);
    for(int i=0;i<4;++i){def.items[i]=field<int>(decoded,0x50+size_t(i)*4);def.required[i]=field<int>(decoded,0x60+size_t(i)*4);}
    return &def;
}
int quest_glyph(const QuestDefinition& def){
    // Exact 6c20a8 switch used by the native working-quest tooltip.
    static constexpr int glyphs[]={56,54,54,54,59,54,54,65,65,54,54,114,65};
    return def.type>=0 && def.type<int(sizeof glyphs/sizeof *glyphs)?glyphs[def.type]:0;
}
struct QuestMatches { Ptr unused=nullptr;Ptr* first=nullptr;Ptr* last=nullptr;Ptr* capacity=nullptr; };
bool quest_matches_npc(const QuestRecord& q,int npc){
    if(!global<Ptr>(0xed0e28))return false;
    QuestMatches matches;
    native<void(*)(Ptr,int,unsigned,QuestMatches*)>(0xa2a560)(game()+0xed0e18,q.id,quest_progress(q),&matches);
    bool result=false;
    uintptr_t first=reinterpret_cast<uintptr_t>(matches.first),last=reinterpret_cast<uintptr_t>(matches.last);
    if(last>=first && last-first<=4096*sizeof(Ptr) && (!first || readable(matches.first,last-first))){
        for(Ptr* entry=matches.first;entry && entry!=matches.last && !result;++entry){
            Ptr row=*entry;if(!readable(row,0x30))continue;
            int collect=field<int>(row,0x10);
            // Same native inventory-completion exclusion as 0xa75e00.
            if(collect>0 && native<int(*)(Ptr,int,int)>(0x3f8600)(game()+0x13715e0,q.id,collect))continue;
            Ptr begin=field<Ptr>(row,0x20),end=field<Ptr>(row,0x28);
            uintptr_t a=reinterpret_cast<uintptr_t>(begin),z=reinterpret_cast<uintptr_t>(end);
            if(z<a || z-a>4096*sizeof(int) || !readable(begin,z-a))continue;
            const int* ids=static_cast<const int*>(begin);
            for(size_t i=0;i<(z-a)/sizeof(int);++i)if(ids[i]==npc){result=true;break;}
        }
    }
    if(matches.first)global<void(*)(Ptr)>(0x130b830)(matches.first);
    return result;
}
uint64_t quest_hash(uint64_t hash,uint64_t v){return (hash^v)*1099511628211ULL;}
uint64_t quest_epoch=0,quest_fingerprint=0;
ULONGLONG quest_last_poll=0;
unsigned quest_budget=0;
bool quest_snapshot_valid=false;
bool quest_snapshot(){
    ULONGLONG now=GetTickCount64();if(quest_last_poll && now-quest_last_poll<100)return quest_snapshot_valid;
    quest_last_poll=now;quest_budget=4;quest_snapshot_valid=false;
    int count=global<int>(QUEST_COUNT);
    if(count<0 || count>int(QUEST_LIMIT)){++quest_epoch;return false;}
    uint64_t hash=quest_hash(1469598103934665603ULL,unsigned(global<int>(0x1313070)));
    hash=quest_hash(hash,unsigned(global<int>(0x1312fd0)));hash=quest_hash(hash,unsigned(count));
    const auto* list=reinterpret_cast<const QuestRecord*>(game()+QUEST_LIST);
    for(int i=0;i<count;++i){hash=quest_hash(hash,unsigned(list[i].id));hash=quest_hash(hash,list[i].status);hash=quest_hash(hash,quest_progress(list[i]));}
    // Collection objectives can change without a quest-stage packet. Include bag
    // template/count data, using the same native map traversal as the Kinah reader.
    Ptr player=global<Ptr>(PLAYER),head=player?field<Ptr>(player,ITEM_TREE):nullptr;
    if(readable(head,0x30)){
        Ptr node=field<Ptr>(head,0);
        for(int limit=0;node && node!=head && limit<8192;++limit){
            if(!readable(node,0x30))return false;
            Ptr item=field<Ptr>(node,0x20);
            if(readable(item,0x6c0) && !field<int>(item,0x6bc)){
                hash=quest_hash(hash,unsigned(field<int>(item,0xc)));hash=quest_hash(hash,uint64_t(field<long long>(item,0x10)));
            }
            Ptr right=field<Ptr>(node,0x10);if(!readable(right,0x30))return false;
            if(!field<unsigned char>(right,0x29)){
                node=right;
                for(int depth=0;depth<64;++depth){Ptr left=field<Ptr>(node,0);if(!readable(left,0x30))return false;if(field<unsigned char>(left,0x29))break;node=left;}
            }else{
                Ptr parent=field<Ptr>(node,8);int depth=0;
                while(readable(parent,0x30) && !field<unsigned char>(parent,0x29) && node==field<Ptr>(parent,0x10) && depth++<64){node=parent;parent=field<Ptr>(parent,8);}
                if(!readable(parent,0x30))return false;node=parent;
            }
        }
    }
    if(hash!=quest_fingerprint){quest_fingerprint=hash;++quest_epoch;}
    quest_snapshot_valid=true;return true;
}
struct QuestNpcBadge { int npc=0,glyph=0;uint64_t epoch=0; };
QuestNpcBadge quest_badges[256]{};
unsigned quest_badge_next=0;
int quest_badge(int npc){
    if(!quest_attackable(npc) || !quest_snapshot())return 0;
    QuestNpcBadge* slot=nullptr;
    for(auto& cached:quest_badges)if(cached.npc==npc){slot=&cached;break;}
    if(slot && slot->epoch==quest_epoch)return slot->glyph;
    // A changed objective immediately drops stale badges. Refresh a bounded
    // number of distinct mob templates per 100 ms, rather than every actor/frame.
    if(!quest_budget)return 0;--quest_budget;
    if(!slot)slot=&quest_badges[quest_badge_next++%256];
    *slot={npc,0,quest_epoch};
    int count=global<int>(QUEST_COUNT),priority=-1;
    const auto* list=reinterpret_cast<const QuestRecord*>(game()+QUEST_LIST);
    for(int i=0;i<count;++i){
        if(list[i].status!=3 || !quest_matches_npc(list[i],npc))continue;
        const QuestDefinition* def=quest_definition(list[i].id);if(!def)continue;
        int glyph=quest_glyph(*def),rank=glyph==56?4:glyph==65?3:glyph==114?2:glyph==59?1:0;
        if(glyph && rank>priority){priority=rank;slot->glyph=glyph;}
    }
    ++AetherfallQuestDiagnostics[2];return slot->glyph;
}
int quest_name_format(wchar_t* out,int cap,const wchar_t* format,const wchar_t* name){
    ++AetherfallQuestDiagnostics[1];
    auto original=native<int(*)(wchar_t*,int,const wchar_t*,const wchar_t*)>(0x1c2ca0);
    if(!name || cap<=0)return original(out,cap,format,name);
    // Guarded call 0x9633e5 passes exactly CharacterObject+0x46. Do not alter
    // the actor's actual name, target data, NPC interaction markers or labels.
    auto actor=reinterpret_cast<unsigned char*>(const_cast<wchar_t*>(name))-0x46;
    if(!readable(actor,0x100) || field<int>(actor,0x20)!=2)return original(out,cap,format,name);
    int glyph=quest_badge(field<int>(actor,0x34));
    const wchar_t* badge=glyph?native<const wchar_t*(*)(int)>(0x392330)(glyph):nullptr;
    if(!badge || !*badge)return original(out,cap,format,name);
    ++AetherfallQuestDiagnostics[3];AetherfallQuestDiagnostics[4]=uintptr_t(glyph);
    return native<int(*)(wchar_t*,int,const wchar_t*,const wchar_t*,const wchar_t*)>(0x1c2ca0)(out,cap,L"%s %s",badge,name);
}
struct QuestParameters { char names[60][32]{};wchar_t values[60][32]{}; };
static_assert(sizeof(QuestParameters)==0x1680,"native journal parameter buffer");
bool quest_parameters(const QuestRecord& q,QuestParameters& params){
    Ptr registry=global<Ptr>(0x12ee190);if(!readable(registry,0x18))return false;
    Ptr node=quest_tree_find(field<Ptr>(registry,8),q.id);if(!node)return false;
    Ptr callback=field<Ptr>(node,0x20);MEMORY_BASIC_INFORMATION memory{};
    if(!callback || !VirtualQuery(callback,&memory,sizeof memory) || memory.State!=MEM_COMMIT
       || !(memory.Protect&(PAGE_EXECUTE|PAGE_EXECUTE_READ|PAGE_EXECUTE_READWRITE|PAGE_EXECUTE_WRITECOPY)))return false;
    for(int i=0;i<60;++i)std::snprintf(params.names[i],32,"%d",i);
    reinterpret_cast<void(*)(unsigned,unsigned,QuestParameters*,int)>(callback)(q.status,quest_progress(q),&params,q.id);
    for(auto& value:params.values)value[31]=0;
    return true;
}
bool quest_integer(const wchar_t* value,int& result){
    if(!value || !*value)return false;
    unsigned n=0;
    for(size_t i=0;value[i];++i){if(i>=10 || value[i]<L'0' || value[i]>L'9')return false;unsigned digit=unsigned(value[i]-L'0');if(n>(0x7fffffffU-digit)/10)return false;n=n*10+digit;}
    result=int(n);return true;
}
long long quest_item_count(int id){
    Ptr player=global<Ptr>(PLAYER),head=player?field<Ptr>(player,ITEM_TREE):nullptr;
    if(!readable(head,0x30))return -1;
    long long total=0;Ptr node=field<Ptr>(head,0);
    for(int guard=0;node && node!=head && guard<8192;++guard){
        if(!readable(node,0x30))return -1;
        Ptr item=field<Ptr>(node,0x20);
        if(readable(item,0x6c0) && field<int>(item,0xc)==id && !field<int>(item,0x6bc))total+=field<long long>(item,0x10);
        Ptr right=field<Ptr>(node,0x10);if(!readable(right,0x30))return -1;
        if(!field<unsigned char>(right,0x29)){
            node=right;for(int depth=0;depth<64;++depth){Ptr left=field<Ptr>(node,0);if(!readable(left,0x30))return -1;if(field<unsigned char>(left,0x29))break;node=left;}
        }else{
            Ptr parent=field<Ptr>(node,8);int depth=0;
            while(readable(parent,0x30) && !field<unsigned char>(parent,0x29) && node==field<Ptr>(parent,0x10) && depth++<64){node=parent;parent=field<Ptr>(parent,8);}
            if(!readable(parent,0x30))return -1;node=parent;
        }
    }
    return total;
}
void quest_append_count(wchar_t* title,size_t capacity,int current,int required,bool& any){
    size_t length=wcsnlen(title,capacity);if(length>=capacity || required<=0 || current<0)return;
    wchar_t part[64]{};int written=swprintf_s(part,any?L", %d/%d":L" (%d/%d",std::min(current,required),required);
    if(written>0 && size_t(written)+2<capacity-length){std::memcpy(title+length,part,(size_t(written)+1)*sizeof(wchar_t));any=true;}
}
int quest_tooltip_format(wchar_t* out,int cap,const wchar_t* format,int kind,const wchar_t* icon,const wchar_t* title,int qid){
    ++AetherfallQuestDiagnostics[5];
    auto original=native<int(*)(wchar_t*,int,const wchar_t*,int,const wchar_t*,const wchar_t*)>(0x1c2ca0);
    const QuestRecord* q=active_quest(qid);if(!q || !title)return original(out,cap,format,kind,icon,title);
    wchar_t enriched[512]{};wcsncpy_s(enriched,title,_TRUNCATE);bool any=false,authored=false;
    unsigned progress=quest_progress(*q);
    for(const auto& hunt:QUEST_AUTHORED_HUNTS)if(hunt.quest==qid && unsigned(hunt.stage)==(progress&63)){
        quest_append_count(enriched,512,int((progress>>6)&63),hunt.required,any);authored=true;
    }
    if(!authored){
        QuestParameters params;
        if(quest_parameters(*q,params))for(const auto& quota:QUEST_QUOTAS)if(quota.quest==qid){
            int current=0,visible=1;
            if(quota.visible>=0 && (!quest_integer(params.values[quota.visible],visible) || !visible))continue;
            if(quest_integer(params.values[quota.param],current))quest_append_count(enriched,512,current,quota.required,any);
        }
    }
    if(!any){
        const QuestDefinition* def=quest_definition(qid);
        if(def)for(int i=0;i<4;++i)if(def->items[i]>0 && def->required[i]>0){
            long long count=quest_item_count(def->items[i]);if(count>=0)quest_append_count(enriched,512,int(std::min(count,static_cast<long long>(def->required[i]))),def->required[i],any);
        }
    }
    if(any){size_t length=wcsnlen(enriched,512);if(length<511){enriched[length]=L')';enriched[length+1]=0;}++AetherfallQuestDiagnostics[6];}
    return original(out,cap,format,kind,icon,any?enriched:title);
}
bool install_quest_targets(){
    constexpr size_t sites[]={0x9633e5,0x6c17ef};
    const void* targets[]={reinterpret_cast<const void*>(&quest_name_format),reinterpret_cast<const void*>(&quest_tooltip_format)};
    unsigned char expected[2][5]{};
    for(int i=0;i<2;++i){expected[i][0]=0xe8;int32_t displacement=int32_t(0x1c2ca0-sites[i]-5);std::memcpy(expected[i]+1,&displacement,4);
        if(std::memcmp(game()+sites[i],expected[i],5)){AetherfallQuestDiagnostics[0]=2;return false;}}
    // A near relay keeps each native call at five bytes, without changing any
    // adjacent instructions, imports, Game.dll files or graphics recovery images.
    uintptr_t center=reinterpret_cast<uintptr_t>(game()),lower=center>0x60000000?center-0x60000000:0x10000,upper=center+0x60000000;
    unsigned char* relay=nullptr;MEMORY_BASIC_INFORMATION region{};
    for(uintptr_t cursor=lower;cursor<upper;){
        if(!VirtualQuery(reinterpret_cast<void*>(cursor),&region,sizeof region))break;
        uintptr_t begin=(reinterpret_cast<uintptr_t>(region.BaseAddress)+0xffff)&~uintptr_t(0xffff),end=reinterpret_cast<uintptr_t>(region.BaseAddress)+region.RegionSize;
        if(region.State==MEM_FREE && begin<end && end-begin>=0x10000){relay=static_cast<unsigned char*>(VirtualAlloc(reinterpret_cast<void*>(begin),0x10000,MEM_RESERVE|MEM_COMMIT,PAGE_READWRITE));if(relay)break;}
        if(end<=cursor)break;cursor=end;
    }
    if(!relay){AetherfallQuestDiagnostics[0]=3;return false;}
    for(int i=0;i<2;++i){auto stub=relay+i*16;stub[0]=0xff;stub[1]=0x25;std::memset(stub+2,0,4);std::memcpy(stub+6,&targets[i],8);}
    DWORD prior=0,unused=0;
    if(!VirtualProtect(relay,0x10000,PAGE_EXECUTE_READ,&prior)){VirtualFree(relay,0,MEM_RELEASE);AetherfallQuestDiagnostics[0]=3;return false;}
    FlushInstructionCache(GetCurrentProcess(),relay,32);
    for(int i=0;i<2;++i){
        auto site=game()+sites[i];DWORD old=0;
        if(!VirtualProtect(site,5,PAGE_EXECUTE_READWRITE,&old)){
            if(i && VirtualProtect(game()+sites[0],5,PAGE_EXECUTE_READWRITE,&prior)){std::memcpy(game()+sites[0],expected[0],5);VirtualProtect(game()+sites[0],5,prior,&unused);FlushInstructionCache(GetCurrentProcess(),game()+sites[0],5);}
            VirtualFree(relay,0,MEM_RELEASE);AetherfallQuestDiagnostics[0]=3;return false;
        }
        int32_t displacement=int32_t(reinterpret_cast<intptr_t>(relay+i*16)-reinterpret_cast<intptr_t>(site)-5);
        site[0]=0xe8;std::memcpy(site+1,&displacement,4);VirtualProtect(site,5,old,&unused);FlushInstructionCache(GetCurrentProcess(),site,5);
    }
    AetherfallQuestDiagnostics[0]=1;return true;
}
