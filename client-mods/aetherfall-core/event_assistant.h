// The World Event assistant (Guild Wars 2's event tracker): the player's current world event, read from the server's
// /market/companions/events/assistant (EventJournal.assistant) as key=value lines. The UI thread only reads this
// cache; one short-lived worker asks the server once a second, and only while the World Event quest (14979, the
// fourth help_quest slot) is in the journal, so nothing is requested while no event is near.
namespace event_assistant {
constexpr int QUEST=14979;
constexpr ULONGLONG POLL_MS=1000,LINGER_MS=25000;
struct Data {
    bool active=false;
    std::string chain,stage,objective,count,medal,icon,outcome;
    int group=0,progress=0,seconds=0,shield=0,map=0;
    float x=0,y=0,z=0;
    ULONGLONG at=0;
};
std::mutex lock;
Data data;
std::atomic<bool> running{false};
ULONGLONG lastAsk=0,questSeen=0;
// [0] answers applied, [1] refused answers, [2] requests started
extern "C" __declspec(dllexport) volatile uintptr_t AetherfallEventAssistantDiagnostics[3]={0,0,0};
struct Internet { HINTERNET handle=nullptr;~Internet(){if(handle)WinHttpCloseHandle(handle);} };
bool fetch(const std::string& id,std::string& body){
    const auto endpoint=aetherfall_web::selected(id.c_str());if(!endpoint.valid)return false;
    Internet transport{WinHttpOpen(L"Aetherfall Events/1",WINHTTP_ACCESS_TYPE_NO_PROXY,nullptr,nullptr,0)};if(!transport.handle)return false;
    WinHttpSetTimeouts(transport.handle,500,500,800,1500);
    Internet connection{aetherfall_web::connect(transport.handle,endpoint)};if(!connection.handle)return false;
    std::wstring path=L"/market/companions/events/assistant?session_id="+std::wstring(id.begin(),id.end());
    Internet query{aetherfall_web::request(connection.handle,endpoint,L"GET",path.c_str())};if(!query.handle)return false;
    if(!WinHttpSendRequest(query.handle,nullptr,0,nullptr,0,0,0) || !WinHttpReceiveResponse(query.handle,nullptr))return false;
    DWORD status=0,size=sizeof status;
    if(!WinHttpQueryHeaders(query.handle,WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER,WINHTTP_HEADER_NAME_BY_INDEX,&status,&size,WINHTTP_NO_HEADER_INDEX) || status!=200)return false;
    char buffer[1024];DWORD got=0;
    do{if(!WinHttpReadData(query.handle,buffer,sizeof buffer,&got) || body.size()+got>4096)return false;body.append(buffer,got);}while(got);
    return true;
}
int number(const std::string& v){try{return std::stoi(v);}catch(...){return 0;}}
float decimal(const std::string& v){try{float f=std::stof(v);return std::isfinite(f)?f:0.f;}catch(...){return 0.f;}}
// key=value lines; unknown keys are ignored so a newer server can add fields.
bool parse(const std::string& body,Data& out){
    std::istringstream lines(body);std::string line;bool any=false;
    while(std::getline(lines,line)){
        if(!line.empty() && line.back()=='\r')line.pop_back();
        size_t eq=line.find('=');if(eq==std::string::npos || eq==0)continue;
        std::string k=line.substr(0,eq),v=line.substr(eq+1);if(v.size()>240)v.resize(240);any=true;
        if(k=="active")out.active=v=="1";
        else if(k=="chain")out.chain=v;
        else if(k=="stage")out.stage=v;
        else if(k=="objective")out.objective=v;
        else if(k=="count")out.count=v;
        else if(k=="medal")out.medal=v;
        else if(k=="icon")out.icon=v;
        else if(k=="outcome")out.outcome=v;
        else if(k=="group")out.group=number(v);
        else if(k=="progress")out.progress=std::clamp(number(v),0,1000);
        else if(k=="seconds")out.seconds=std::max(0,number(v));
        else if(k=="shield")out.shield=number(v);
        else if(k=="map")out.map=number(v);
        else if(k=="x")out.x=decimal(v);
        else if(k=="y")out.y=decimal(v);
        else if(k=="z")out.z=decimal(v);
    }
    return any;
}
void work(std::string id){
    std::string body;Data next;
    bool ok=fetch(id,body) && parse(body,next);
    {
        std::lock_guard<std::mutex> guard(lock);
        if(ok){next.at=GetTickCount64();data=next;++AetherfallEventAssistantDiagnostics[0];}
        else ++AetherfallEventAssistantDiagnostics[1];
    }
    running=false;
}
// Native UI thread (AetherfallCodexTick). Asks only while the event quest is in the journal, and for a short while
// after it left, so the result line of an ended event still arrives.
void tick(){
    ULONGLONG now=GetTickCount64();
    if(active_quest(QUEST))questSeen=now;
    bool wanted=questSeen && now-questSeen<=LINGER_MS;
    if(!wanted){
        std::lock_guard<std::mutex> guard(lock);
        if(data.active)data=Data{};
        return;
    }
    if(running || now-lastAsk<POLL_MS)return;
    std::string id=help_quest::session();if(id.empty())return;
    lastAsk=now;running=true;++AetherfallEventAssistantDiagnostics[2];
    try{std::thread(work,id).detach();}catch(const std::system_error&){running=false;}
}
// Lua: active, chain, stage, objective, count, progress (0-1), seconds left, medal, icon, group, shield, outcome, map, x, y, z.
// The timer counts down between answers, so it moves every second without a request.
int query(Ptr L){
    Data d;{std::lock_guard<std::mutex> guard(lock);d=data;}
    if(!d.active)return boolean(L,false);
    ULONGLONG age=d.at?GetTickCount64()-d.at:0;
    int seconds=std::max(0,d.seconds-int(age/1000));
    boolean(L,true);push(L,d.chain.c_str());push(L,d.stage.c_str());push(L,d.objective.c_str());push(L,d.count.c_str());
    push(L,d.progress/1000.0);push(L,double(seconds));push(L,d.medal.c_str());push(L,d.icon.c_str());
    boolean(L,d.group!=0);boolean(L,d.shield!=0);push(L,d.outcome.c_str());
    push(L,double(d.map));push(L,double(d.x));push(L,double(d.y));push(L,double(d.z));
    return 16;
}
}
