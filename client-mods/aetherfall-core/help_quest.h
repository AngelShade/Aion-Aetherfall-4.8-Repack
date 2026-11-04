// Names the one reusable help quest. Its native title row points at this text, which is
// fetched as the quest is added, before the journal and tracker build their rows.
namespace help_quest {
constexpr int QUEST=14996,TITLE=1801381,PROGRESS=1801382;
extern "C" __declspec(dllexport) volatile uintptr_t AetherfallHelpDiagnostics[3]={0,0,0};
wchar_t text[128]{},progressText[2048]{};
std::string appliedBody;
bool lastChanged=false;
struct Internet { HINTERNET handle=nullptr;~Internet(){if(handle)WinHttpCloseHandle(handle);} };
std::string session(){
    auto p=reinterpret_cast<const unsigned char*>(game())+0x130c8f0;bool present=false;std::string out;constexpr char hex[]="0123456789abcdef";
    for(int i=0;i<16;++i){present|=p[i]!=0;out+=hex[p[i]>>4];out+=hex[p[i]&15];}return present?out:"";
}
// The UI waits for this one call as a quest is accepted, so failure must be quick.
std::string fetch(const std::string& id){
    if(id.empty())return {};
    const auto endpoint=aetherfall_web::selected(id.c_str());if(!endpoint.valid)return {};
    Internet transport{WinHttpOpen(L"Aetherfall Help/1",WINHTTP_ACCESS_TYPE_NO_PROXY,nullptr,nullptr,0)};if(!transport.handle)return {};
    WinHttpSetTimeouts(transport.handle,200,200,200,400);
    Internet connection{aetherfall_web::connect(transport.handle,endpoint)};if(!connection.handle)return {};
    std::wstring path=L"/market/companions/help?format=journal&session_id="+std::wstring(id.begin(),id.end());
    Internet query{aetherfall_web::request(connection.handle,endpoint,L"GET",path.c_str())};if(!query.handle)return {};
    if(!WinHttpSendRequest(query.handle,nullptr,0,nullptr,0,0,0) || !WinHttpReceiveResponse(query.handle,nullptr))return {};
    DWORD status=0,size=sizeof status;
    if(!WinHttpQueryHeaders(query.handle,WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER,WINHTTP_HEADER_NAME_BY_INDEX,&status,&size,WINHTTP_NO_HEADER_INDEX) || status!=200)return {};
    std::string body;char buffer[1024];DWORD got=0;
    do{if(!WinHttpReadData(query.handle,buffer,sizeof buffer,&got) || body.size()+got>8192)return {};body.append(buffer,got);}while(got);
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
bool apply(const std::string& body){
    lastChanged=false;
    auto row=string_row(TITLE),progressRow=string_row(PROGRESS);size_t split=body.find('\n');
    if(!row || !progressRow || split==std::string::npos){AetherfallHelpDiagnostics[2]=1;return false;}
    if(body==appliedBody && field<const wchar_t*>(row,0xc)==text && field<const wchar_t*>(progressRow,0xc)==progressText)return true;
    int length=MultiByteToWideChar(CP_UTF8,0,body.data(),int(split),text,127);
    int progressLength=MultiByteToWideChar(CP_UTF8,0,body.data()+split+1,int(body.size()-split-1),progressText,2047);
    if(length<=0 || progressLength<=0){AetherfallHelpDiagnostics[2]=2;return false;}
    text[length]=0;
    const wchar_t* pointer=text;std::memcpy(row+0xc,&pointer,sizeof pointer);
    progressText[progressLength]=0;pointer=progressText;std::memcpy(progressRow+0xc,&pointer,sizeof pointer);
    appliedBody=body;lastChanged=true;
    ++AetherfallHelpDiagnostics[1];
    return true;
}
void added(int quest){
    if(quest!=QUEST)return;
    ++AetherfallHelpDiagnostics[0];apply(fetch(session()));
}
// The login quest list does not pass through accepted(). Watch this one native
// record's revision/session; fetch off-thread only when either changes. No UI scan
// or network request runs per draw, and a late response cannot cross a relog.
std::future<std::string> pending;
std::string requestedSession,lastSession;
unsigned lastProgress=0;
int lastCount=-1,index=-1;
bool dirty=false;
ULONGLONG retryAfter=0,keyRequested=0;
bool poll(){
    int count=global<int>(QUEST_COUNT);if(count<0 || count>quest_record_capacity)return false;
    std::string id=session();const auto* list=quest_records();
    if(count!=lastCount || id!=lastSession || (index>=0 && (index>=count || list[index].id!=QUEST))){
        lastCount=count;lastSession=id;index=-1;dirty=true;
        for(int i=0;i<count;++i)if(list[i].id==QUEST && list[i].status==3){index=i;break;}
    }
    if(index<0)return false;
    unsigned progress=quest_progress(list[index]);if(progress!=lastProgress){lastProgress=progress;dirty=true;}
    ULONGLONG now=GetTickCount64();
    if(id.empty()){
        if(now-keyRequested>10000){keyRequested=now;native<void(*)()>(0x3321c0)();}
        return false;
    }
    bool changed=false;
    if(pending.valid() && pending.wait_for(std::chrono::seconds(0))==std::future_status::ready){
        std::string body=pending.get();
        if(id==requestedSession){bool success=apply(body);changed=success && lastChanged;if(!success){dirty=true;retryAfter=now+5000;}}
    }
    if(dirty && !pending.valid() && now>=retryAfter){dirty=false;requestedSession=id;pending=std::async(std::launch::async,[id](){return fetch(id);});}
    return changed;
}
}
