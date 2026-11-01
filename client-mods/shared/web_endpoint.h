// One server-owned endpoint, received over the native game session. No launcher
// URL, environment override, login-host inference or filesystem configuration.
#pragma once
#include <windows.h>
#include <winhttp.h>
#include <string>
#include <iterator>
#include <cwctype>
#pragma comment(lib, "winhttp.lib")
#ifdef AETHERFALL_WEB_OWNER
extern "C" __declspec(dllexport) int __cdecl AionWebConnection(const char*,wchar_t*,size_t,char*,size_t,int*,int*);
#endif
namespace aetherfall_web {
struct Endpoint {
    std::wstring host,origin;INTERNET_PORT port=0;bool secure=false,valid=false;
    std::string error="The server has not provided its window connection. Reconnect to the game and reopen this window.";
    int text_scale=100,flags=0;
};
inline std::wstring wide(const std::string& text){
    if(text.empty())return {};int n=MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,text.data(),int(text.size()),nullptr,0);
    std::wstring out(size_t(n),L'\0');if(n)MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,text.data(),int(text.size()),out.data(),n);return out;
}
inline std::string utf8(const std::wstring& text){
    if(text.empty())return {};int n=WideCharToMultiByte(CP_UTF8,0,text.data(),int(text.size()),nullptr,0,nullptr,nullptr);
    std::string out(size_t(n),'\0');if(n)WideCharToMultiByte(CP_UTF8,0,text.data(),int(text.size()),out.data(),n,nullptr,nullptr);return out;
}
inline Endpoint parse(const std::wstring& value){
    Endpoint out;
    if(value.empty() || value.size()>512 || value.find_first_of(L"\r\n\t ")!=std::wstring::npos)return out;
    URL_COMPONENTS parts{};parts.dwStructSize=sizeof parts;
    parts.dwHostNameLength=parts.dwUrlPathLength=parts.dwExtraInfoLength=parts.dwUserNameLength=parts.dwPasswordLength=DWORD(-1);
    if(!WinHttpCrackUrl(value.c_str(),DWORD(value.size()),0,&parts) ||
       (parts.nScheme!=INTERNET_SCHEME_HTTP && parts.nScheme!=INTERNET_SCHEME_HTTPS) ||
       !parts.dwHostNameLength || parts.dwUserNameLength || parts.dwPasswordLength || parts.dwExtraInfoLength ||
       (parts.dwUrlPathLength && std::wstring(parts.lpszUrlPath,parts.dwUrlPathLength)!=L"/"))return out;
    out.host.assign(parts.lpszHostName,parts.dwHostNameLength);out.port=parts.nPort;out.secure=parts.nScheme==INTERNET_SCHEME_HTTPS;
    if(out.host.front()==L'[' && out.host.back()==L']')out.host=out.host.substr(1,out.host.size()-2);
    for(auto& c:out.host)c=wchar_t(towlower(c));
    std::wstring authority=out.host.find(L':')!=std::wstring::npos?L"["+out.host+L"]":out.host;
    out.origin=(out.secure?L"https://":L"http://")+authority;
    if(out.port!=(out.secure?443:80))out.origin+=L":"+std::to_wstring(out.port);
    out.valid=out.port!=0;return out;
}
using Provider=int(__cdecl*)(const char*,wchar_t*,size_t,char*,size_t,int*,int*);
inline Provider provider(){
#ifdef AETHERFALL_WEB_OWNER
    return &::AionWebConnection;
#else
    static Provider value=[](){auto module=GetModuleHandleW(L"AionIconBridge.dll");return module?reinterpret_cast<Provider>(GetProcAddress(module,"AionWebConnection")):nullptr;}();
    return value;
#endif
}
inline Endpoint selected(const char* token=nullptr){
    Endpoint out;auto api=provider();if(!api)return out;
    wchar_t origin[1024]{};char error[1024]{};int scale=100,flags=0;
    bool ready=api(token,origin,std::size(origin),error,std::size(error),&scale,&flags)!=0;
    if(ready)out=parse(origin);
    if(error[0])out.error=error;out.text_scale=scale;out.flags=flags;return out;
}
inline std::string token(const std::string& query){
    size_t p=query.find("session_id=");if(p==std::string::npos)return {};p+=11;
    size_t end=query.find_first_of("&#",p);std::string value=query.substr(p,end==std::string::npos?end:end-p);
    if(value.size()!=32)return {};for(char c:value)if(!((c>='0'&&c<='9')||(c>='a'&&c<='f')))return {};return value;
}
inline bool private_path(const std::string& path){
    for(const char* root:{"/shop","/market","/journey"}){size_t n=std::char_traits<char>::length(root);
        if(path.compare(0,n,root)==0 && (path.size()==n || path[n]=='/' || path[n]=='?' || path[n]=='#'))return true;}
    return false;
}
inline std::string navigation(const std::string& url){
    constexpr char legacy[]="http://127.0.0.1:8091";constexpr size_t n=sizeof(legacy)-1;
    if(url.compare(0,n,legacy) || !private_path(url.substr(n)))return url;
    auto id=token(url);auto e=selected(id.empty()?nullptr:id.c_str());
    // Missing/invalid metadata stays in the existing window. Its normal shell
    // and message display are served by the same resource owner, without I/O.
    return e.valid?utf8(e.origin)+url.substr(n):url;
}
inline std::string resource_path(const std::string& url){
    auto e=selected();std::string prefix=e.valid?utf8(e.origin):"http://127.0.0.1:8091";
    if(url.compare(0,prefix.size(),prefix))prefix="http://127.0.0.1:8091";
    if(url.compare(0,prefix.size(),prefix) || url.size()<=prefix.size() || url[prefix.size()]!='/')return {};
    return url.substr(prefix.size());
}
inline bool route(const std::string& url,const std::string& path){
    auto e=selected();if(!e.valid)return false;auto expected=utf8(e.origin)+path;
    return url==expected || url.rfind(expected+(path.find('?')==std::string::npos?"?":"&"),0)==0;
}
inline std::string error_json(const std::string& message){
    std::string out="{\"error\":\"";for(unsigned char c:message){if(c=='"'||c=='\\')out+='\\';if(c>=32)out+=char(c);}return out+"\"}";
}
inline HINTERNET connect(HINTERNET session,const Endpoint& e){return e.valid?WinHttpConnect(session,e.host.c_str(),e.port,0):nullptr;}
inline HINTERNET request(HINTERNET connection,const Endpoint& e,const wchar_t* verb,const wchar_t* path){
    auto handle=WinHttpOpenRequest(connection,verb,path,nullptr,WINHTTP_NO_REFERER,WINHTTP_DEFAULT_ACCEPT_TYPES,e.secure?WINHTTP_FLAG_SECURE:0);
    if(handle){DWORD policy=WINHTTP_OPTION_REDIRECT_POLICY_NEVER;WinHttpSetOption(handle,WINHTTP_OPTION_REDIRECT_POLICY,&policy,sizeof policy);}return handle;
}
inline std::wstring form_headers(const Endpoint& e){return L"Content-Type: application/x-www-form-urlencoded\r\nOrigin: "+e.origin+L"\r\n";}
}
