#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <filesystem>
#include <cstring>
#include <string>

// Verified Aion 4.8 NA x64 widget ABI. Installed by a hash-checked disk patch.
namespace {
HMODULE module;
using Ptr=void*;
template<class T>T method(Ptr object,size_t offset){return reinterpret_cast<T>((*reinterpret_cast<void***>(object))[offset/8]);}
std::wstring settings(){wchar_t path[32768];DWORD n=GetModuleFileNameW(module,path,32768);if(!n||n>=32768)return {};return (std::filesystem::path(path).parent_path().parent_path()/L"DXVK/renderer.ini").wstring();}
bool read(const wchar_t* key){auto path=settings();return !path.empty()&&GetPrivateProfileIntW(L"Renderer",key,1,path.c_str())!=0;}
Ptr find(Ptr dialog,const char* name){return dialog?method<Ptr(*)(Ptr,const char*)>(dialog,0x340)(dialog,name):nullptr;}
void text(Ptr widget,const wchar_t* value){if(widget)method<void(*)(Ptr,const wchar_t*)>(widget,0x290)(widget,value);}
int checked(Ptr widget){return widget?method<int(*)(Ptr)>(widget,0x5a8)(widget):0;}
void check(Ptr widget,int value){if(widget)method<void(*)(Ptr,int)>(widget,0x598)(widget,value);}
void status(Ptr dialog,bool saved=false){
    auto box=find(dialog,"cb_use_vulkan");if(!box)return;
    bool desired=checked(box)!=0,active=read(L"ActiveVulkan");
    const wchar_t* value=desired!=active?
        (saved?L"Saved. Exit Aion and reopen Aion Start.bat to switch renderer.":L"Click Apply or OK, then restart Aion to switch renderer."):
        (active?L"Current renderer: Vulkan (DXVK).":L"Current renderer: Direct3D 9. Vulkan post effects are off.");
    text(find(dialog,"st_vulkan_status"),value);
}
void load(Ptr dialog){auto box=find(dialog,"cb_use_vulkan");if(!box)return;check(box,read(L"Vulkan")?1:0);text(box,L"Use Vulkan (requires restart)");status(dialog);}
}
extern "C" __declspec(dllexport) void AionGraphicsLoad(Ptr dialog,int visible){if(visible)load(dialog);}
extern "C" __declspec(dllexport) int AionGraphicsClick(Ptr dialog,Ptr event){
    if(!dialog||!event)return 0;
    Ptr widget=*reinterpret_cast<Ptr*>(event);if(!widget)return 0;
    const char* name=method<const char*(*)(Ptr)>(widget,0xa8)(widget);if(!name)return 0;
    if(!std::strcmp(name,"cb_use_vulkan")){
        // Same video dirty flag used by native engine controls.
        *reinterpret_cast<int*>(static_cast<unsigned char*>(dialog)+0xf6c)=1;
        status(dialog);return 1;
    }
    if(!std::strcmp(name,"apply")||!std::strcmp(name,"ok")){
        auto box=find(dialog,"cb_use_vulkan");if(!box)return 0;
        auto path=settings();if(path.empty())return 0;
        if(WritePrivateProfileStringW(L"Renderer",L"Vulkan",checked(box)?L"1":L"0",path.c_str()))status(dialog,true);
        else text(find(dialog,"st_vulkan_status"),L"Could not save renderer choice. Check access to DXVK/renderer.ini.");
    }else if(!std::strcmp(name,"cancel"))load(dialog);
    return 0; // All existing native actions retain their original handler.
}
BOOL WINAPI DllMain(HINSTANCE instance,DWORD reason,LPVOID){if(reason==DLL_PROCESS_ATTACH){module=instance;DisableThreadLibraryCalls(instance);}return TRUE;}
