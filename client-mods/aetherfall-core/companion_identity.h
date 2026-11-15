// Gear rarity and combat rank for bots shown on native windows. The UI thread only reads
// this cache; one short-lived worker asks the server, so a slow server can't stall a frame.
namespace companion_identity {
constexpr ULONGLONG FRESH_MS=30000,RETRY_MS=3000;
constexpr size_t MAX_ASKED=24,MAX_CACHED=256;
// model and look (optional): the body id and the 66 appearance bytes a native 3D portrait is built from.
constexpr size_t LOOK_BYTES=0x42;
// seen: when the server last answered this name as a bot. A refresh that comes back without it within
// KEEP_MS keeps the known bot: the party portrait otherwise dropped and returned with each refresh.
constexpr ULONGLONG KEEP_MS=120000;
struct Entry { std::string rarity,rank;ULONGLONG at=0;int model=0;std::string look;ULONGLONG seen=0; };
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
    std::wstring path=L"/market/companions/identity?session_id="+std::wstring(id.begin(),id.end())+L"&look=1&names="+std::wstring(names.begin(),names.end());
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
            // name|rarity|rank, then |model|look when the server knows the bot's appearance.
            std::vector<std::string> part;
            for(size_t from=0;part.size()<5;){
                size_t bar=line.find('|',from);
                part.push_back(line.substr(from,bar==std::string::npos?bar:bar-from));
                if(bar==std::string::npos)break;
                from=bar+1;
            }
            if(part.size()<3)continue;
            Entry entry{part[1],part[2],0};
            if(part.size()==5 && part[4].size()==LOOK_BYTES*2 && part[3].size()==6){
                int model=0;std::string look;bool valid=true;
                for(char c:part[3]){valid=valid && c>='0' && c<='9';model=model*10+(c-'0');}
                for(size_t i=0;valid && i<part[4].size();i+=2){
                    int value=0;
                    for(size_t k=i;k<i+2;++k){
                        char c=part[4][k];
                        int digit=c>='0' && c<='9'?c-'0':c>='a' && c<='f'?c-'a'+10:-1;
                        valid=valid && digit>=0;value=value*16+digit;
                    }
                    look.push_back(char(value));
                }
                if(valid && model>=100000 && model<=100003){entry.model=model;entry.look=std::move(look);}
            }
            answers[part[0]]=std::move(entry);
        }
    }
    {
        std::lock_guard<std::mutex> guard(lock);
        ULONGLONG now=GetTickCount64();
        if(ok){
            // Full: drop names not refreshed for a minute first, then other non-bots; never a recently seen bot.
            if(cache.size()>MAX_CACHED){
                for(auto it=cache.begin();it!=cache.end();)it=now-it->second.at>60000 && now-it->second.seen>=KEEP_MS?cache.erase(it):std::next(it);
                for(auto it=cache.begin();cache.size()>MAX_CACHED && it!=cache.end();)it=it->second.rarity.empty()?cache.erase(it):std::next(it);
            }
            // A name with no answer is a person or a monster. Remember that too, so it isn't asked again at once.
            for(const auto& name:asked){
                auto found=answers.find(name);Entry entry=found==answers.end()?Entry{}:found->second;entry.at=now;
                Entry& old=cache[name];
                if(!entry.rarity.empty())entry.seen=now;
                else if(!old.rarity.empty() && now-old.seen<KEEP_MS){entry=old;entry.at=now;}  // a gap, not a dismissal
                if(!entry.rarity.empty() && !entry.model && old.model && old.rarity==entry.rarity){entry.model=old.model;entry.look=old.look;}
                old=entry;
            }
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
// The cached look of a bot, for a portrait. Never asks the server: query() above already does.
bool look(const char* name,int& model,std::array<uint8_t,0x60>& bytes){
    if(!name || !*name || std::strlen(name)>64)return false;
    std::lock_guard<std::mutex> guard(lock);
    auto found=cache.find(name);
    if(found==cache.end() || found->second.look.size()!=LOOK_BYTES || !found->second.model)return false;
    // The stock portrait passes the 66 bytes and leaves the seven values after them clear (Game.dll 0x60fc05).
    bytes.fill(0);std::memcpy(bytes.data(),found->second.look.data(),LOOK_BYTES);
    model=found->second.model;return true;
}
}
