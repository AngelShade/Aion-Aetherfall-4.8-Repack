#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <wincred.h>
#include <cwchar>
#include <cstring>
#include <cstdint>
#pragma comment(lib,"advapi32.lib")
#pragma comment(lib,"user32.lib")

// Aion 4.8 NA x64. All widget access happens on the native UI thread.
namespace {
HMODULE module;
using Ptr=void*;
Ptr pendingRestore=nullptr;
#ifdef REMEMBER_TEST
unsigned char* testGame;
DWORD testVaultError=0;
using NoticeCallback=void(*)(Ptr,const char*);
NoticeCallback testNotice;
#endif
template<class T>T method(Ptr object,size_t offset){return reinterpret_cast<T>((*reinterpret_cast<void***>(object))[offset/8]);}
Ptr find(Ptr dialog,const char* name){return dialog?method<Ptr(*)(Ptr,const char*)>(dialog,0x340)(dialog,name):nullptr;}
void text(Ptr widget,const wchar_t* value){if(widget)method<void(*)(Ptr,const wchar_t*)>(widget,0x290)(widget,value);}
void check(Ptr box,int value){if(box)method<void(*)(Ptr,int)>(box,0x598)(box,value);}
bool checked(Ptr box){return box&&method<int(*)(Ptr)>(box,0x5a8)(box)!=0;}
void status(Ptr dialog,const wchar_t* value){text(find(dialog,"remember_login_status"),value);}
// Separate each client installation; never read unrelated Windows credentials.
bool target(wchar_t (&out)[96]){
    wchar_t path[32768];DWORD n=GetModuleFileNameW(module,path,32768);
    if(!n||n>=32768)return false;
    CharLowerBuffW(path,n);uint64_t hash=14695981039346656037ULL;
    for(DWORD i=0;i<n;++i){hash^=uint16_t(path[i]);hash*=1099511628211ULL;}
    swprintf_s(out,L"Aetherfall/Aion48/RememberLogin/%016llx",static_cast<unsigned long long>(hash));
    return true;
}
struct Secret {
    wchar_t account[65]{},password[33]{};
    ~Secret(){SecureZeroMemory(this,sizeof *this);}
};
bool bounded(const wchar_t* source,wchar_t* destination,size_t capacity){
    if(!source)return false;
    size_t n=wcsnlen_s(source,capacity);
    if(!n||n>=capacity)return false;
    memcpy(destination,source,(n+1)*sizeof(wchar_t));return true;
}
bool fields(Ptr dialog,Secret& secret){
    auto a=find(dialog,"account"),p=find(dialog,"password");
    return a&&p&&bounded(method<const wchar_t*(*)(Ptr)>(a,0x298)(a),secret.account,65)
        &&bounded(method<const wchar_t*(*)(Ptr)>(p,0x298)(p),secret.password,33);
}
bool erase(){
    wchar_t name[96];if(!target(name))return false;
    return CredDeleteW(name,CRED_TYPE_GENERIC,0)||GetLastError()==ERROR_NOT_FOUND;
}
bool save(const Secret& secret){
    wchar_t name[96];if(!target(name))return false;
    CREDENTIALW c{};c.Type=CRED_TYPE_GENERIC;c.TargetName=name;
    c.UserName=const_cast<wchar_t*>(secret.account);
    c.CredentialBlobSize=DWORD(wcslen(secret.password)*sizeof(wchar_t));
    c.CredentialBlob=reinterpret_cast<BYTE*>(const_cast<wchar_t*>(secret.password));
    c.Persist=CRED_PERSIST_LOCAL_MACHINE;
    bool ok=CredWriteW(&c,0)!=FALSE;
#ifdef REMEMBER_TEST
    testVaultError=ok?0:GetLastError();
#endif
    return ok;
}
// 0 = absent, 1 = restored, -1 = unavailable/invalid. No plaintext file fallback.
int load(Secret& secret){
    wchar_t name[96];if(!target(name))return -1;
    PCREDENTIALW c=nullptr;
    if(!CredReadW(name,CRED_TYPE_GENERIC,0,&c))return GetLastError()==ERROR_NOT_FOUND?0:-1;
    bool valid=c->Type==CRED_TYPE_GENERIC&&c->CredentialBlob&&c->CredentialBlobSize>=2
        &&c->CredentialBlobSize<=64&&c->CredentialBlobSize%2==0
        &&bounded(c->UserName,secret.account,65);
    if(valid){
        memcpy(secret.password,c->CredentialBlob,c->CredentialBlobSize);
        size_t n=c->CredentialBlobSize/sizeof(wchar_t);
        valid=wcsnlen_s(secret.password,33)==n; // reject embedded NULs
    }
    if(c->CredentialBlob)SecureZeroMemory(c->CredentialBlob,c->CredentialBlobSize);
    CredFree(c);return valid?1:-1;
}
void remember(Ptr dialog){
    auto box=find(dialog,"remember_login");if(!box)return;
    if(!checked(box)){
        if(erase())status(dialog,L"");
        else {check(box,1);status(dialog,L"Could not remove the saved login.");}
        return;
    }
    Secret secret;
    if(!fields(dialog,secret))return;
    status(dialog,save(secret)?L"Login saved on this PC.":L"Could not save the login on this PC.");
}
}
void restoreLogin(Ptr dialog){
    auto box=find(dialog,"remember_login");if(!box)return;
    Secret secret;int result=load(secret);check(box,result==1?1:0);
    if(result==1){text(find(dialog,"account"),secret.account);text(find(dialog,"password"),secret.password);}
    status(dialog,result<0?L"Saved login could not be read.":result==1?L"Login saved on this PC.":L"");
}
extern "C" __declspec(dllexport) void AionRememberLoad(Ptr dialog){
    restoreLogin(dialog);pendingRestore=dialog;
}
unsigned char* gameBase(){
#ifdef REMEMBER_TEST
    return testGame;
#else
    static auto base=reinterpret_cast<unsigned char*>(GetModuleHandleW(L"Game.dll"));return base;
#endif
}
extern "C" __declspec(dllexport) void AionRememberVisibility(Ptr dialog,uint64_t previous){
    auto game=gameBase();
    if(!game||!dialog||dialog!=*reinterpret_cast<Ptr*>(game+0x13875d0))return;
    if(!(previous&1) && (*reinterpret_cast<uint64_t*>(static_cast<unsigned char*>(dialog)+0x30)&1))pendingRestore=dialog;
}
extern "C" __declspec(dllexport) void AionRememberRefresh(Ptr dialog){
    auto game=gameBase();
    if(pendingRestore!=dialog||!game||!dialog||!(*reinterpret_cast<uint64_t*>(static_cast<unsigned char*>(dialog)+0x30)&1))return;
    pendingRestore=nullptr;restoreLogin(dialog);
    auto notice=find(dialog,"htmlview_notice");
    if(notice){
#ifdef REMEMBER_TEST
        if(testNotice)testNotice(notice,"ui/loginnotice.xml");
#else
        reinterpret_cast<void(*)(Ptr,const char*)>(game+0x4ce620)(notice,"ui/loginnotice.xml");
#endif
    }
}
extern "C" __declspec(dllexport) int AionRememberClick(Ptr dialog,Ptr event){
    if(!dialog||!event)return 0;
    Ptr widget=*static_cast<Ptr*>(event);if(!widget)return 0;
    const char* name=method<const char*(*)(Ptr)>(widget,0xa8)(widget);
    if(!name||strcmp(name,"remember_login"))return 0;
    // Native UIButton toggles its checkbox state before dispatching the event.
    if(checked(widget))status(dialog,L"Your login will be saved when you log in.");
    else if(erase())status(dialog,L"");
    else {check(widget,1);status(dialog,L"Could not remove the saved login.");}
    return 1;
}
extern "C" __declspec(dllexport) void AionRememberAction(Ptr,const char* action){
    if(!action||strcmp(action,"login_auth_server"))return;
    auto game=reinterpret_cast<unsigned char*>(GetModuleHandleW(L"Game.dll"));
#ifdef REMEMBER_TEST
    game=testGame;
#endif
    if(game)remember(*reinterpret_cast<Ptr*>(game+0x13875d0));
}
#ifdef REMEMBER_TEST
extern "C" __declspec(dllexport) void AionRememberTestNotice(NoticeCallback notice){testNotice=notice;}
extern "C" __declspec(dllexport) void AionRememberTestSubmit(Ptr dialog){remember(dialog);}
extern "C" __declspec(dllexport) void AionRememberTestBase(unsigned char* base){testGame=base;}
extern "C" __declspec(dllexport) int AionRememberTestDelete(){return erase()?1:0;}
extern "C" __declspec(dllexport) DWORD AionRememberTestError(){return testVaultError;}
extern "C" __declspec(dllexport) int AionRememberTestTarget(wchar_t* out){wchar_t name[96];if(!target(name))return 0;wcscpy_s(out,96,name);return 1;}
#endif
BOOL WINAPI DllMain(HINSTANCE instance,DWORD reason,LPVOID){if(reason==DLL_PROCESS_ATTACH){module=instance;DisableThreadLibraryCalls(instance);}return TRUE;}
