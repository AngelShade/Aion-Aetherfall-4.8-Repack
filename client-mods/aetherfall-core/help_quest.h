// Names the one reusable help quest. Its native title row points at this text, which is
// fetched as the quest is added, before the journal and tracker build their rows.
namespace help_quest {
constexpr int QUEST=14996,TITLE=1801381;
extern "C" __declspec(dllexport) volatile uintptr_t AetherfallHelpDiagnostics[3]={0,0,0};
wchar_t text[128]{};
struct Internet { HINTERNET handle=nullptr;~Internet(){if(handle)WinHttpCloseHandle(handle);} };
std::string session(){
    auto p=reinterpret_cast<const unsigned char*>(game())+0x130c8f0;bool present=false;std::string out;constexpr char hex[]="0123456789abcdef";
    for(int i=0;i<16;++i){present|=p[i]!=0;out+=hex[p[i]>>4];out+=hex[p[i]&15];}return present?out:"";
}
// The UI waits for this one call as a quest is accepted, so failure must be quick.
std::string fetch(){
    std::string id=session();if(id.empty())return {};
    const auto endpoint=aetherfall_web::selected(id.c_str());if(!endpoint.valid)return {};
    Internet transport{WinHttpOpen(L"Aetherfall Help/1",WINHTTP_ACCESS_TYPE_NO_PROXY,nullptr,nullptr,0)};if(!transport.handle)return {};
    WinHttpSetTimeouts(transport.handle,200,200,200,400);
    Internet connection{aetherfall_web::connect(transport.handle,endpoint)};if(!connection.handle)return {};
    std::wstring path=L"/market/companions/help?session_id="+std::wstring(id.begin(),id.end());
    Internet query{aetherfall_web::request(connection.handle,endpoint,L"GET",path.c_str())};if(!query.handle)return {};
    if(!WinHttpSendRequest(query.handle,nullptr,0,nullptr,0,0,0) || !WinHttpReceiveResponse(query.handle,nullptr))return {};
    DWORD status=0,size=sizeof status;
    if(!WinHttpQueryHeaders(query.handle,WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER,WINHTTP_HEADER_NAME_BY_INDEX,&status,&size,WINHTTP_NO_HEADER_INDEX) || status!=200)return {};
    std::string body;char buffer[1024];DWORD got=0;
    do{if(!WinHttpReadData(query.handle,buffer,sizeof buffer,&got) || body.size()+got>1024)return {};body.append(buffer,got);}while(got);
    return body;
}
unsigned char* title_row(){
    auto begin=global<unsigned char*>(STRINGS_BEGIN),end=global<unsigned char*>(STRINGS_END);
    if(!begin || end<=begin)return nullptr;
    size_t lo=0,hi=size_t(end-begin)/20;
    while(lo<hi){
        size_t mid=(lo+hi)/2;int key=*reinterpret_cast<int*>(begin+mid*20);
        if(key==TITLE)return begin+mid*20;
        if(key<TITLE)lo=mid+1;else hi=mid;
    }
    return nullptr;
}
void added(int quest){
    if(quest!=QUEST)return;
    ++AetherfallHelpDiagnostics[0];
    auto row=title_row();std::string body=row?fetch():std::string{};
    int length=body.empty()?0:MultiByteToWideChar(CP_UTF8,0,body.data(),int(body.size()),text,127);
    if(length<=0){AetherfallHelpDiagnostics[2]=row?2:1;return;}
    text[length]=0;
    const wchar_t* pointer=text;std::memcpy(row+0xc,&pointer,sizeof pointer);
    ++AetherfallHelpDiagnostics[1];
}
}
