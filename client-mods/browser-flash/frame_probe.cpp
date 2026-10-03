// Passive diagnostics for the publisher's exact Awesomium 4.8 NA build.
// Calls original functions once, returns their results unchanged, and never
// records URLs, HTML, account data or pixel contents. Nine alpha bytes per frame
// identify an empty surface without dumping screenshots or changing rendering.
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <intrin.h>
#include <array>
#include <mutex>
#include <fstream>
#include <filesystem>
#include <cstring>
#include <algorithm>
#include <stdexcept>
#include <string>

namespace {
using P=void*;
using Render=P(__cdecl*)(P);
using Copy=void(__cdecl*)(P,unsigned char*,int,int,bool,bool);
using Resize=void(__cdecl*)(P,int,int,bool,int);
Render original_render;
Copy original_copy;
Resize original_resize;
P(__cdecl* pixels)(P);
int(__cdecl* width)(P);
int(__cdecl* height)(P);
int(__cdecl* stride)(P);
HMODULE self;
std::once_flag once;
std::mutex mutex;
std::filesystem::path logfile;
LARGE_INTEGER frequency;
ULONGLONG start=0,last_write=0;
bool enabled=false;
struct Row {
    P view=nullptr,buffer=nullptr;
    uintptr_t render_caller=0,copy_caller=0,resize_caller=0;
    unsigned long long renders=0,copies=0,nulls=0,blank_sources=0,blank_copies=0,resizes=0;
    int w=0,h=0,pitch=0,depth=0,min_alpha=255;
    unsigned long long render_us=0,copy_us=0,max_render_us=0,max_copy_us=0;
};
std::array<Row,32> rows;
unsigned overflow=0;
Row* row_for(P view,P buffer) {
    for(auto& r:rows)if((view&&r.view==view)||(buffer&&r.buffer==buffer))return &r;
    for(auto& r:rows)if(!r.view&&!r.buffer){r.view=view;r.buffer=buffer;return &r;}
    ++overflow;return nullptr;
}
uintptr_t caller_rva(P caller){
    HMODULE module=GetModuleHandleW(L"Game.dll");
    auto p=reinterpret_cast<uintptr_t>(caller),base=reinterpret_cast<uintptr_t>(module);
    // Report only Game.dll-relative addresses, never addresses from other modules.
    auto dos=reinterpret_cast<IMAGE_DOS_HEADER*>(module);
    if(!module||dos->e_magic!=IMAGE_DOS_SIGNATURE)return 0;
    auto pe=reinterpret_cast<IMAGE_NT_HEADERS64*>(base+dos->e_lfanew);
    return p>=base&&p<base+pe->OptionalHeader.SizeOfImage?p-base:0;
}
unsigned long long elapsed(LARGE_INTEGER a,LARGE_INTEGER b){return (b.QuadPart-a.QuadPart)*1000000/frequency.QuadPart;}
int alpha(const unsigned char* p,int w,int h,int pitch,int depth){
    if(!p||w<1||h<1||w>8192||h>8192||depth!=4||pitch<w*4||pitch>65536)return -1;
    int maximum=0;
    for(int y:{h/8,h/2,h*7/8})for(int x:{w/8,w/2,w*7/8})
        maximum=std::max(maximum,int(p[size_t(y)*pitch+size_t(x)*4+3]));
    return maximum;
}
P __cdecl render(P view){
    P caller=_ReturnAddress();LARGE_INTEGER a,b;QueryPerformanceCounter(&a);
    P result=original_render(view);QueryPerformanceCounter(&b);
    int w=0,h=0,pitch=0,opacity=-1;
    if(result){w=width(result);h=height(result);pitch=stride(result);opacity=alpha(static_cast<unsigned char*>(pixels(result)),w,h,pitch,4);}
    std::lock_guard<std::mutex> lock(mutex);if(auto r=row_for(view,nullptr)){
        r->buffer=result;r->render_caller=caller_rva(caller);++r->renders;
        r->nulls+=!result;r->blank_sources+=opacity==0;
        if(opacity>=0)r->min_alpha=std::min(r->min_alpha,opacity);
        r->w=w;r->h=h;auto us=elapsed(a,b);r->render_us+=us;r->max_render_us=std::max(r->max_render_us,us);
    }
    return result;
}
void __cdecl copy(P buffer,unsigned char* destination,int pitch,int depth,bool rgba,bool flip){
    P caller=_ReturnAddress();LARGE_INTEGER a,b;QueryPerformanceCounter(&a);
    original_copy(buffer,destination,pitch,depth,rgba,flip);QueryPerformanceCounter(&b);
    int w=width(buffer),h=height(buffer),opacity=alpha(destination,w,h,pitch,depth);
    std::lock_guard<std::mutex> lock(mutex);if(auto r=row_for(nullptr,buffer)){
        r->copy_caller=caller_rva(caller);++r->copies;r->blank_copies+=opacity==0;
        r->w=w;r->h=h;r->pitch=pitch;r->depth=depth;
        if(opacity>=0)r->min_alpha=std::min(r->min_alpha,opacity);
        auto us=elapsed(a,b);r->copy_us+=us;r->max_copy_us=std::max(r->max_copy_us,us);
    }
}
void __cdecl resize(P view,int w,int h,bool wait,int timeout){
    P caller=_ReturnAddress();original_resize(view,w,h,wait,timeout);
    std::lock_guard<std::mutex> lock(mutex);if(auto r=row_for(view,nullptr)){
        ++r->resizes;r->resize_caller=caller_rva(caller);r->w=w;r->h=h;
    }
}
void jump(unsigned char* p,P target){const unsigned char code[]={0xff,0x25,0,0,0,0};std::memcpy(p,code,6);std::memcpy(p+6,&target,8);}
struct Hook {const char* name;const unsigned char* bytes;size_t length;P destination,target=nullptr,original=nullptr;};
void prepare(Hook& h,HMODULE module){
    h.target=reinterpret_cast<P>(GetProcAddress(module,h.name));
    if(!h.target||std::memcmp(h.target,h.bytes,h.length))throw std::runtime_error("Unsupported Awesomium prologue");
    h.original=VirtualAlloc(nullptr,h.length+14,MEM_COMMIT|MEM_RESERVE,PAGE_EXECUTE_READWRITE);
    if(!h.original)throw std::runtime_error("Allocation");
    std::memcpy(h.original,h.target,h.length);jump(static_cast<unsigned char*>(h.original)+h.length,static_cast<unsigned char*>(h.target)+h.length);
    FlushInstructionCache(GetCurrentProcess(),h.original,h.length+14);
}
void activate(Hook& h){
    DWORD old;if(!VirtualProtect(h.target,h.length,PAGE_EXECUTE_READWRITE,&old))throw std::runtime_error("Protection");
    jump(static_cast<unsigned char*>(h.target),h.destination);
    std::memset(static_cast<unsigned char*>(h.target)+14,0x90,h.length-14);
    DWORD ignored;VirtualProtect(h.target,h.length,old,&ignored);FlushInstructionCache(GetCurrentProcess(),h.target,h.length);
}
void write(const char* status){
    std::ofstream out(logfile,std::ios::app);out<<"elapsed_ms="<<GetTickCount64()-start<<" status="<<status<<" overflow="<<overflow<<'\n';
    for(size_t i=0;i<rows.size();++i){const auto& r=rows[i];if(!r.view&&!r.buffer)continue;
        out<<"surface="<<i<<" renders="<<r.renders<<" copies="<<r.copies<<" nulls="<<r.nulls
           <<" blank_source="<<r.blank_sources<<" blank_copy="<<r.blank_copies<<" resizes="<<r.resizes
           <<" size="<<r.w<<'x'<<r.h<<" pitch="<<r.pitch<<" depth="<<r.depth<<" min_alpha="<<r.min_alpha
           <<" render_us="<<r.render_us<<" max_render_us="<<r.max_render_us<<" copy_us="<<r.copy_us<<" max_copy_us="<<r.max_copy_us
           <<" render_caller_rva="<<std::hex<<r.render_caller<<" copy_caller_rva="<<r.copy_caller<<" resize_caller_rva="<<r.resize_caller<<std::dec<<'\n';
    }
}
}
extern "C" __declspec(dllexport) int __cdecl AionBrowserProbeInitialize(){
    std::call_once(once,[]{try{
        wchar_t path[32768];auto n=GetModuleFileNameW(self,path,32768);if(!n||n==32768)return;
        auto root=std::filesystem::path(path).parent_path().parent_path();std::filesystem::create_directories(root/L"Logs");
        logfile=root/L"Logs"/(L"BrowserFrames."+std::to_wstring(GetCurrentProcessId())+L".log");
        start=last_write=GetTickCount64();QueryPerformanceFrequency(&frequency);
        auto module=GetModuleHandleW(L"Awesomium.dll");if(!module)return;
        pixels=reinterpret_cast<decltype(pixels)>(GetProcAddress(module,"awe_renderbuffer_get_buffer"));
        width=reinterpret_cast<decltype(width)>(GetProcAddress(module,"awe_renderbuffer_get_width"));
        height=reinterpret_cast<decltype(height)>(GetProcAddress(module,"awe_renderbuffer_get_height"));
        stride=reinterpret_cast<decltype(stride)>(GetProcAddress(module,"awe_renderbuffer_get_rowspan"));
        if(!pixels||!width||!height||!stride)return;
        static const unsigned char render_bytes[]={0x48,0x89,0x4c,0x24,0x08,0x48,0x83,0xec,0x28,0x48,0x8b,0x44,0x24,0x30};
        static const unsigned char copy_bytes[]={0x44,0x89,0x4c,0x24,0x20,0x44,0x89,0x44,0x24,0x18,0x48,0x89,0x54,0x24,0x10};
        static const unsigned char resize_bytes[]={0x44,0x88,0x4c,0x24,0x20,0x44,0x89,0x44,0x24,0x18,0x89,0x54,0x24,0x10};
        std::array<Hook,3> hooks{{{"awe_webview_render",render_bytes,sizeof(render_bytes),reinterpret_cast<P>(render)},
          {"awe_renderbuffer_copy_to",copy_bytes,sizeof(copy_bytes),reinterpret_cast<P>(copy)},
          {"awe_webview_resize",resize_bytes,sizeof(resize_bytes),reinterpret_cast<P>(resize)}}};
        // Validate all functions before applying any hook. No RIP-relative or
        // branching instructions occur in these exact displaced prologues.
        for(auto& h:hooks)prepare(h,module);
        original_render=reinterpret_cast<Render>(hooks[0].original);original_copy=reinterpret_cast<Copy>(hooks[1].original);original_resize=reinterpret_cast<Resize>(hooks[2].original);
        for(auto& h:hooks)activate(h);
        enabled=true;write("passive-probe-enabled");
    }catch(...){if(!logfile.empty())write("probe-initialization-failed");}});
    return enabled?1:0;
}
extern "C" __declspec(dllexport) void __cdecl AionBrowserProbeTick(){
    if(!enabled)return;auto now=GetTickCount64();
    // File writes happen on the native UI tick, never inside render/upload.
    // Bound recording to 30 minutes and at most one summary per ten seconds.
    if(now-start>1800000||now-last_write<10000)return;
    last_write=now;try{std::lock_guard<std::mutex> lock(mutex);write("sample");}catch(...){}
}
BOOL WINAPI DllMain(HINSTANCE module,DWORD reason,LPVOID){if(reason==DLL_PROCESS_ATTACH){self=module;DisableThreadLibraryCalls(module);}return TRUE;}
