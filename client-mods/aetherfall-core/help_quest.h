// Names the reusable help quests, one per help slot (owner 8 Nov: several requests at once):
// 14996, 14998 and 14999. Each wrapper's native title and progress rows point at text from
// that slot's server card, fetched as the quest is added and whenever its record changes.
namespace help_quest {
constexpr int SLOTS=3;
constexpr int QUESTS[SLOTS]={14996,14998,14999},TITLES[SLOTS]={1801381,1801385,1801387},PROGRESSES[SLOTS]={1801382,1801386,1801388};
constexpr int QUEST=QUESTS[0];
// [0] quests added, [1] cards applied, [2] last refusal (1 rows/format, 2 conversion)
extern "C" __declspec(dllexport) volatile uintptr_t AetherfallHelpDiagnostics[3]={0,0,0};
struct Slot {
    wchar_t text[128]{},progressText[2048]{};
    std::string appliedBody;
    // The card (format=card2): status, map point, the tracker lines and the details page
    // that help_journal.h draws into the native quest details.
    std::wstring card_page,tracker;
    std::string card_status;
    int card_map=0;float card_x=0,card_y=0,card_z=0;
    bool lastChanged=false,dirty=false;
    std::future<std::string> pending;
    std::string requestedSession;
    unsigned lastProgress=0;
    int index=-1;
    ULONGLONG retryAfter=0;
};
Slot slots[SLOTS];
int slot_of(int quest){for(int i=0;i<SLOTS;++i)if(QUESTS[i]==quest)return i;return -1;}
struct Internet { HINTERNET handle=nullptr;~Internet(){if(handle)WinHttpCloseHandle(handle);} };
std::string session(){
    auto p=reinterpret_cast<const unsigned char*>(game())+0x130c8f0;bool present=false;std::string out;constexpr char hex[]="0123456789abcdef";
    for(int i=0;i<16;++i){present|=p[i]!=0;out+=hex[p[i]>>4];out+=hex[p[i]&15];}return present?out:"";
}
// quick: the UI waits for this call as a quest is accepted, so failure must be fast; the
// background refresh may wait a little longer for a busy server.
std::string fetch(const std::string& id,int slot,bool quick){
    if(id.empty())return {};
    const auto endpoint=aetherfall_web::selected(id.c_str());if(!endpoint.valid)return {};
    Internet transport{WinHttpOpen(L"Aetherfall Help/1",WINHTTP_ACCESS_TYPE_NO_PROXY,nullptr,nullptr,0)};if(!transport.handle)return {};
    if(quick)WinHttpSetTimeouts(transport.handle,200,200,200,400);else WinHttpSetTimeouts(transport.handle,1000,1000,2000,2000);
    Internet connection{aetherfall_web::connect(transport.handle,endpoint)};if(!connection.handle)return {};
    std::wstring path=L"/market/companions/help?format=card2&slot="+std::to_wstring(slot)+L"&session_id="+std::wstring(id.begin(),id.end());
    Internet query{aetherfall_web::request(connection.handle,endpoint,L"GET",path.c_str())};if(!query.handle)return {};
    if(!WinHttpSendRequest(query.handle,nullptr,0,nullptr,0,0,0) || !WinHttpReceiveResponse(query.handle,nullptr))return {};
    DWORD status=0,size=sizeof status;
    if(!WinHttpQueryHeaders(query.handle,WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER,WINHTTP_HEADER_NAME_BY_INDEX,&status,&size,WINHTTP_NO_HEADER_INDEX) || status!=200)return {};
    std::string body;char buffer[1024];DWORD got=0;
    do{if(!WinHttpReadData(query.handle,buffer,sizeof buffer,&got) || body.size()+got>16384)return {};body.append(buffer,got);}while(got);
    return body;
}
unsigned char* string_row(int key){
    auto begin=global<unsigned char*>(STRINGS_BEGIN),end=global<unsigned char*>(STRINGS_END);
    if(!begin || end<=begin)return nullptr;
    size_t lo=0,hi=size_t(end-begin)/20;
    while(lo<hi){
        size_t mid=(lo+hi)/2;int current=*reinterpret_cast<int*>(begin+mid*20);
        if(current==key)return begin+mid*20;
        if(current<key)lo=mid+1;else hi=mid;
    }
    return nullptr;
}
std::wstring wide(const char* data,size_t length){
    int size=MultiByteToWideChar(CP_UTF8,0,data,int(length),nullptr,0);
    if(size<=0 || size>=8192)return {};
    std::wstring out(size_t(size),L'\0');MultiByteToWideChar(CP_UTF8,0,data,int(length),out.data(),size);return out;
}
// Lines: title, step, "STATUS map x y z", tracker lines, page.
bool apply_card(int i,const std::string& body){
    auto& s=slots[i];s.lastChanged=false;
    auto row=string_row(TITLES[i]),progressRow=string_row(PROGRESSES[i]);size_t split=body.find('\n');
    if(!row || !progressRow || split==std::string::npos){AetherfallHelpDiagnostics[2]=1;return false;}
    if(body==s.appliedBody && field<const wchar_t*>(row,0xc)==s.text && field<const wchar_t*>(progressRow,0xc)==s.progressText)return true;
    size_t second=body.find('\n',split+1),third=second==std::string::npos?second:body.find('\n',second+1);
    size_t fourth=third==std::string::npos?third:body.find('\n',third+1);
    size_t stepEnd=second==std::string::npos?body.size():second;
    int length=MultiByteToWideChar(CP_UTF8,0,body.data(),int(split),s.text,127);
    int progressLength=MultiByteToWideChar(CP_UTF8,0,body.data()+split+1,int(stepEnd-split-1),s.progressText,2047);
    if(length<=0 || progressLength<=0){AetherfallHelpDiagnostics[2]=2;return false;}
    s.text[length]=0;
    const wchar_t* pointer=s.text;std::memcpy(row+0xc,&pointer,sizeof pointer);
    s.progressText[progressLength]=0;pointer=s.progressText;std::memcpy(progressRow+0xc,&pointer,sizeof pointer);
    s.card_page.clear();s.tracker.clear();s.card_status.clear();s.card_map=0;s.card_x=s.card_y=s.card_z=0;
    if(fourth!=std::string::npos){
        std::string state=body.substr(second+1,third-second-1);
        int world=0;float x=0,y=0,z=0;
        std::istringstream fields(state);std::string name;
        if(fields>>name>>world>>x>>y>>z && name.size()<16 && std::isfinite(x) && std::isfinite(y) && std::isfinite(z)){
            s.card_status=name;s.card_map=world;s.card_x=x;s.card_y=y;s.card_z=z;
            s.tracker=wide(body.data()+third+1,fourth-third-1);
            s.card_page=wide(body.data()+fourth+1,body.size()-fourth-1);
        }
    }
    s.appliedBody=body;s.lastChanged=true;
    ++AetherfallHelpDiagnostics[1];
    return true;
}
void added(int quest){
    int i=slot_of(quest);if(i<0)return;
    ++AetherfallHelpDiagnostics[0];apply_card(i,fetch(session(),i,true));
}
// The tracker refills a row's text only for quests in its text queue (vector at tracker+0x670: begin +0x678,
// end +0x680; refilled and emptied by 0x85b230, which the refresh 0x856bf0 runs). A help card that changed after
// its quest update would otherwise leave the old counters (owner 8 Nov: 0/10 in the tracker, 2/10 in the journal).
// Same insert the native queue owner uses (0x823e0 from 0x85c66a); quest_journal.py verifies both sites.
void requeue(int quest){
    Ptr tracker=dialog(0xba);if(!tracker)return;
    auto begin=field<int*>(tracker,0x678),end=field<int*>(tracker,0x680);
    if(!begin){if(end)return;}
    else{if(end<begin || end-begin>4096 || !readable(begin,size_t(end-begin)*sizeof(int)))return;for(auto p=begin;p<end;++p)if(*p==quest)return;}
    int value=quest;
    native<void(*)(unsigned char*,int*,size_t,const int*)>(0x823e0)(static_cast<unsigned char*>(tracker)+0x670,end,1,&value);
}
// After a journal button the card is read again at once.
void refresh(int i){if(i>=0 && i<SLOTS){slots[i].dirty=true;slots[i].retryAfter=0;}}
// Tracker rows (quest_journal.h redirects the row-fill SetText at 0x85b608 here with the
// row's quest id): a help row gets its slot's counter lines, every other row its own text.
uintptr_t row_text(Ptr widget,const wchar_t* text,int quest){
    int i=slot_of(quest);
    const wchar_t* shown=i>=0 && !slots[i].tracker.empty()?slots[i].tracker.c_str():text;
    return method<uintptr_t(*)(Ptr,const wchar_t*)>(widget,0x290)(widget,shown);
}
// The login quest list does not pass through accepted(). Watch these native records'
// revisions and the session; fetch off-thread only when one changes. No UI scan or network
// request runs per draw, and a late response cannot cross a relog.
std::string lastSession;
int lastCount=-1;
ULONGLONG keyRequested=0;
bool poll(){
    int count=global<int>(QUEST_COUNT);if(count<0 || count>quest_record_capacity)return false;
    std::string id=session();const auto* list=quest_records();
    bool rescan=count!=lastCount || id!=lastSession;
    for(int i=0;i<SLOTS && !rescan;++i)if(slots[i].index>=0 && (slots[i].index>=count || list[slots[i].index].id!=QUESTS[i]))rescan=true;
    if(rescan){
        lastCount=count;lastSession=id;
        for(auto& s:slots){s.index=-1;s.dirty=true;}
        for(int k=0;k<count;++k){int i=slot_of(list[k].id);if(i>=0 && list[k].status==3 && slots[i].index<0)slots[i].index=k;}
    }
    bool any=false;for(auto& s:slots)any|=s.index>=0;
    if(!any)return false;
    ULONGLONG now=GetTickCount64();
    if(id.empty()){
        if(now-keyRequested>10000){keyRequested=now;native<void(*)()>(0x3321c0)();}
        return false;
    }
    bool changed=false;
    for(int i=0;i<SLOTS;++i){
        auto& s=slots[i];if(s.index<0)continue;
        unsigned progress=quest_progress(list[s.index]);if(progress!=s.lastProgress){s.lastProgress=progress;s.dirty=true;}
        if(s.pending.valid() && s.pending.wait_for(std::chrono::seconds(0))==std::future_status::ready){
            std::string body=s.pending.get();
            if(id==s.requestedSession){
                bool success=apply_card(i,body);
                if(success && s.lastChanged){changed=true;requeue(QUESTS[i]);}
                if(!success){s.dirty=true;s.retryAfter=now+5000;}
            }
        }
        if(s.dirty && !s.pending.valid() && now>=s.retryAfter){s.dirty=false;s.requestedSession=id;s.pending=std::async(std::launch::async,[id,i](){return fetch(id,i,false);});}
    }
    return changed;
}
}
