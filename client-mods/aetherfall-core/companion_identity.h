// Gear rarity and combat rank for bots shown on native windows. The UI thread only reads
// this cache; one short-lived worker asks the server, so a slow server can't stall a frame.
namespace companion_identity {
constexpr ULONGLONG FRESH_MS=30000,RETRY_MS=3000;
constexpr size_t MAX_ASKED=24,MAX_CACHED=256;
struct Entry { std::string rarity,rank;ULONGLONG at=0; };
std::mutex lock;
std::map<std::string,Entry> cache;
std::set<std::string> wanted;
std::atomic<bool> running{false};
ULONGLONG last_fetch=0;
std::string escaped(const std::string& text){
    constexpr char hex[]="0123456789ABCDEF";std::string out;
    for(unsigned char c:text){
        if((c>='A' && c<='Z') || (c>='a' && c<='z') || (c>='0' && c<='9'))out+=char(c);
        else{out+='%';out+=hex[c>>4];out+=hex[c&15];}
    }
    return out;
}
struct Internet { HINTERNET handle=nullptr;~Internet(){if(handle)WinHttpCloseHandle(handle);} };
bool fetch(const std::string& id,const std::string& names,std::string& body){
    const auto endpoint=aetherfall_web::selected(id.c_str());if(!endpoint.valid)return false;
    Internet transport{WinHttpOpen(L"Aetherfall Identity/1",WINHTTP_ACCESS_TYPE_NO_PROXY,nullptr,nullptr,0)};if(!transport.handle)return false;
    WinHttpSetTimeouts(transport.handle,500,500,500,1000);
    Internet connection{aetherfall_web::connect(transport.handle,endpoint)};if(!connection.handle)return false;
    std::wstring path=L"/market/companions/identity?session_id="+std::wstring(id.begin(),id.end())+L"&names="+std::wstring(names.begin(),names.end());
    Internet query{aetherfall_web::request(connection.handle,endpoint,L"GET",path.c_str())};if(!query.handle)return false;
    if(!WinHttpSendRequest(query.handle,nullptr,0,nullptr,0,0,0) || !WinHttpReceiveResponse(query.handle,nullptr))return false;
    DWORD status=0,size=sizeof status;
    if(!WinHttpQueryHeaders(query.handle,WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER,WINHTTP_HEADER_NAME_BY_INDEX,&status,&size,WINHTTP_NO_HEADER_INDEX) || status!=200)return false;
    char buffer[1024];DWORD got=0;
    do{if(!WinHttpReadData(query.handle,buffer,sizeof buffer,&got) || body.size()+got>8192)return false;body.append(buffer,got);}while(got);
    return true;
}
void work(std::string id,std::vector<std::string> asked){
    std::string names,body;
    for(const auto& name:asked){if(!names.empty())names+=',';names+=escaped(name);}
    bool ok=fetch(id,names,body);
    std::map<std::string,Entry> answers;
    if(ok){
        size_t start=0;
        while(start<body.size()){
            size_t end=body.find('\n',start);if(end==std::string::npos)end=body.size();
            std::string line=body.substr(start,end-start);start=end+1;
            size_t a=line.find('|'),b=a==std::string::npos?a:line.find('|',a+1);
            if(b!=std::string::npos)answers[line.substr(0,a)]=Entry{line.substr(a+1,b-a-1),line.substr(b+1),0};
        }
    }
    {
        std::lock_guard<std::mutex> guard(lock);
        ULONGLONG now=GetTickCount64();
        if(ok){
            if(cache.size()>MAX_CACHED)cache.clear();
            // A name with no answer is a person or a monster. Remember that too, so it isn't asked again at once.
            for(const auto& name:asked){auto found=answers.find(name);Entry entry=found==answers.end()?Entry{}:found->second;entry.at=now;cache[name]=entry;}
        }
    }
    running=false;
}
int query(Ptr L,const char* name){
    if(!name || !*name || std::strlen(name)>64)return nil(L);
    std::string key(name),rarity,rank;
    ULONGLONG now=GetTickCount64();bool known=false;
    std::vector<std::string> asked;
    {
        std::lock_guard<std::mutex> guard(lock);
        auto found=cache.find(key);
        if(found!=cache.end()){known=!found->second.rarity.empty();rarity=found->second.rarity;rank=found->second.rank;}
        if(found==cache.end() || now-found->second.at>FRESH_MS)if(wanted.size()<MAX_ASKED)wanted.insert(key);
        if(!wanted.empty() && !running && now-last_fetch>=RETRY_MS){asked.assign(wanted.begin(),wanted.end());wanted.clear();last_fetch=now;running=true;}
    }
    if(!asked.empty()){
        std::string id=help_quest::session();
        if(id.empty())running=false;
        else{try{std::thread(work,id,asked).detach();}catch(const std::system_error&){running=false;}}
    }
    if(!known)return nil(L);
    push(L,rarity.c_str());return 1+push(L,rank.c_str());
}
}
