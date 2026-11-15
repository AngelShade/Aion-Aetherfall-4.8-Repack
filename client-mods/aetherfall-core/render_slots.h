// Portrait render slots, raised from 8 to 80 (included by aetherfall_core.cpp before portrait_view.h).
// XRenderD3D9.dll (SHA256 2083cae8...) draws each UI3DView into the surface for its +0x608 slot.
// The renderer ([[XR+0xc36d78]+0x19d90]) builds exactly 8 slots in one loop (0x1a5274..0x1a5393),
// filling three 8-entry tables: +0x40c18 "$UI3DView_i" (shown), +0x40b98 "$UI3DViewPreMask_i",
// +0x40bd8 the "Textures/UI/v3_UI3DViewMask_i" file (only _1 ships). Three readers index the tables
// with no bounds check: 0x70477 (type 1) and 0x7048e (type 2, Game.dll begin/end 0x48d936/0x48d9e8)
// and the mask pass 0xaf62d. A slot past 7 read past the tables and crashed (1.4.13, XR+0x706a7).
// Each reader now goes through a relay: slots 0..7 read the stock tables exactly as before, 8..79
// read ours, anything else reads null, which every reader already skips. Slots 8..79 are made by
// the same native call, flags and names as the stock loop, with an automatic id (0, like the mask
// file); a '$' surface takes no video memory until its first draw sizes it. They have no mask
// file, and the mask pass skips a null mask as it skips a missing one. They are made after the
// stock loop (0x1a5399) and at install when the renderer is already up, and released just before
// the stock release loop (0x198e4f), the same way. Nothing is written unless every site holds its
// exact stock bytes; install runs on the UI thread, which also draws.
// 1.4.15: only slots a view asks for are made (the portraits use 8..15). 1.4.14 made all 144
// surfaces at once and the browser windows stopped opening; the game's browser keeps integer
// handles into the same texture list. Mods\AetherfallUI\render-slots-off disables the patch.
extern "C" __declspec(dllexport) volatile uintptr_t AetherfallRenderSlotsDiagnostics[4]={0,0,0,0};
// [0] 0 untried, 1 installed, 2 no XRenderD3D9, 3 bytes differ, 4 relay/protection failed, 5 switched off;
// [1] slot surfaces created; [2] slot surfaces released; [3] creations that returned null.
namespace render_slots {
constexpr int STOCK=8,LIMIT=80;
constexpr size_t GLOBAL=0xc36d78,RENDERER=0x19d90,CREATE=0x1a2ea0;
constexpr size_t VIEWS=0x40c18;  // stock "$UI3DView_i" table, non-null once the renderer is up
constexpr size_t READ_PREMASK=0x70477,READ_VIEW=0x7048e,READ_BACK=0x70533;
constexpr size_t MASK_PASS=0xaf62d,MASK_BACK=0xaf64f;
constexpr size_t CREATED=0x1a5399,CREATED_BACK=0x1a539e,RELEASE=0x198e4f,RELEASE_BACK=0x198e56;
struct Site { size_t rva; unsigned char size; unsigned char bytes[34]; };
constexpr Site SITES[]={
    {READ_PREMASK,23,{0x48,0x63,0x0e,0x48,0x8b,0x82,0x90,0x9d,0x01,0x00,0x48,0x8b,0xbc,0xc8,0x98,0x0b,0x04,0x00,0xe9,0xa5,0x00,0x00,0x00}},
    {READ_VIEW,23,{0x48,0x63,0x0e,0x48,0x8b,0x82,0x90,0x9d,0x01,0x00,0x48,0x8b,0xbc,0xc8,0x18,0x0c,0x04,0x00,0xe9,0x8e,0x00,0x00,0x00}},
    {MASK_PASS,34,{0x48,0x63,0x51,0x50,0x48,0x8b,0x05,0x40,0x77,0xb8,0x00,0x48,0x8b,0x88,0x90,0x9d,0x01,0x00,
                   0x48,0x8b,0xb4,0xd1,0x98,0x0b,0x04,0x00,0x48,0x8b,0xbc,0xd1,0xd8,0x0b,0x04,0x00}},
    {CREATED,5,{0x4c,0x89,0x64,0x24,0x48}},
    {RELEASE,7,{0x48,0x8d,0xbb,0xd8,0x0b,0x04,0x00}},
    {CREATE,8,{0x48,0x89,0x54,0x24,0x10,0x48,0x8b,0xc4}},  // called, not patched
};
Ptr view[LIMIT]{},premask[LIMIT]{};
bool wanted[LIMIT]{};  // slots a view has asked for; only these are made, and remade after a rebuild
unsigned char* xr=nullptr;
bool tried=false,installed=false;
using Create=Ptr(*)(Ptr,const char*,uint32_t,uint32_t,uint8_t,float,float,int,int,int64_t);
Ptr renderer(){
    if(!xr || !readable(xr+GLOBAL,8))return nullptr;
    auto g=*reinterpret_cast<unsigned char**>(xr+GLOBAL);
    if(!readable(g+RENDERER,8))return nullptr;
    Ptr r=*reinterpret_cast<Ptr*>(g+RENDERER);
    return r && readable(static_cast<unsigned char*>(r)+VIEWS,8*STOCK)?r:nullptr;
}
// Same arguments as the stock loop, except the automatic id: rdx name, r8d 0x204000, r9d 1, byte 0,
// size -1,-1 (sized by the first draw), id, index -1, 0.
void create(Ptr r){
    if(!r || !field<Ptr>(r,VIEWS))return;  // stock slots not built yet; the post-loop relay comes back
    auto make=reinterpret_cast<Create>(xr+CREATE);
    char name[40];
    for(int i=STOCK;i<LIMIT;++i){
        if(!wanted[i])continue;
        if(!view[i]){
            std::snprintf(name,sizeof name,"$UI3DView_%d",i);
            view[i]=make(r,name,0x204000,1,0,-1.0f,-1.0f,0,-1,0);
            ++AetherfallRenderSlotsDiagnostics[view[i]?1:3];
        }
        if(!premask[i]){
            std::snprintf(name,sizeof name,"$UI3DViewPreMask_%d",i);
            premask[i]=make(r,name,0x204000,1,0,-1.0f,-1.0f,0,-1,0);
            ++AetherfallRenderSlotsDiagnostics[premask[i]?1:3];
        }
    }
}
// The stock loop releases each slot by its virtual +8 with dl=1 and clears the entry.
void release(Ptr){
    for(int i=STOCK;i<LIMIT;++i)for(Ptr* entry:{&premask[i],&view[i]})if(Ptr p=*entry){
        *entry=nullptr;
        method<void(*)(Ptr,bool)>(p,8)(p,true);
        ++AetherfallRenderSlotsDiagnostics[2];
    }
}
struct Code {
    unsigned char* at;
    void raw(std::initializer_list<unsigned char> b){for(auto c:b)*at++=c;}
    void copy(const unsigned char* b,size_t n){std::memcpy(at,b,n);at+=n;}
    void imm64(const void* p){std::memcpy(at,&p,8);at+=8;}
    void jump(const unsigned char* to){int32_t d=int32_t(to-(at+5));*at++=0xe9;std::memcpy(at,&d,4);at+=4;}
    // Call fn(arg) with every volatile register, the flags and xmm0..5 kept. arg_mov is "mov rcx,<reg>".
    void call(unsigned char arg_mov,void(*fn)(Ptr)){
        raw({0x9c,0x50,0x51,0x52,0x41,0x50,0x41,0x51,0x41,0x52,0x41,0x53,0x53});   // pushfq, rax..r11, rbx
        raw({0x48,0x89,arg_mov});                                                   // mov rcx,<arg>
        raw({0x48,0x89,0xe3,0x48,0x83,0xe4,0xf0,0x48,0x81,0xec,0x80,0x00,0x00,0x00}); // mov rbx,rsp; and rsp,-16; sub rsp,0x80
        // movdqu [rsp+0x20+16n],xmmN (ModRM 0x44+8n), then the matching loads after the call.
        static const unsigned char XMM[]={0x44,0x4c,0x54,0x5c,0x64,0x6c},SLOT[]={0x20,0x30,0x40,0x50,0x60,0x70};
        for(int n=0;n<6;++n)raw({0xf3,0x0f,0x7f,XMM[n],0x24,SLOT[n]});
        raw({0x48,0xb8});imm64(reinterpret_cast<const void*>(fn));raw({0xff,0xd0});     // mov rax,fn; call rax
        for(int n=0;n<6;++n)raw({0xf3,0x0f,0x6f,XMM[n],0x24,SLOT[n]});
        raw({0x48,0x89,0xdc,0x5b,0x41,0x5b,0x41,0x5a,0x41,0x59,0x41,0x58,0x5a,0x59,0x58,0x9d}); // mov rsp,rbx; pop all; popfq
    }
};
unsigned char* near_block(){
    uintptr_t center=reinterpret_cast<uintptr_t>(xr),lower=center>0x60000000?center-0x60000000:0x10000,upper=center+0x60000000;
    MEMORY_BASIC_INFORMATION region{};
    for(uintptr_t cursor=lower;cursor<upper;){
        if(!VirtualQuery(reinterpret_cast<void*>(cursor),&region,sizeof region))break;
        uintptr_t begin=(reinterpret_cast<uintptr_t>(region.BaseAddress)+0xffff)&~uintptr_t(0xffff),end=reinterpret_cast<uintptr_t>(region.BaseAddress)+region.RegionSize;
        if(region.State==MEM_FREE && begin<end && end-begin>=0x10000)
            if(auto p=static_cast<unsigned char*>(VirtualAlloc(reinterpret_cast<void*>(begin),0x10000,MEM_RESERVE|MEM_COMMIT,PAGE_READWRITE)))return p;
        if(end<=cursor)break;cursor=end;
    }
    return nullptr;
}
// Slot reader relay for a 23-byte case block: movsxd rcx,[rsi]; mov rax,[rdx+0x19d90]; mov rdi,[table]; jmp back.
void reader(Code& c,const unsigned char* stock,Ptr* ours){
    c.copy(stock,10);                                 // movsxd rcx,[rsi]; mov rax,[rdx+0x19d90]
    c.raw({0x83,0xf9,STOCK,0x73,0x0d});               // cmp ecx,8; jae ours
    c.copy(stock+10,8);c.jump(xr+READ_BACK);          // mov rdi,[rax+rcx*8+table]
    c.raw({0x83,0xf9,LIMIT,0x73,0x13});               // ours: cmp ecx,80; jae none
    c.raw({0x48,0xbf});c.imm64(ours);c.raw({0x48,0x8b,0x3c,0xcf});c.jump(xr+READ_BACK);  // mov rdi,[ours+rcx*8]
    c.raw({0x31,0xff});c.jump(xr+READ_BACK);          // none: xor edi,edi
}
bool patch(size_t rva,size_t size,const unsigned char* to){
    auto site=xr+rva;DWORD old=0,unused=0;
    if(!VirtualProtect(site,size,PAGE_EXECUTE_READWRITE,&old))return false;
    int32_t d=int32_t(to-(site+5));site[0]=0xe9;std::memcpy(site+1,&d,4);
    std::memset(site+5,rva==RELEASE?0x90:0xcc,size-5);
    VirtualProtect(site,size,old,&unused);FlushInstructionCache(GetCurrentProcess(),site,size);
    return true;
}
// <client>\Mods\AetherfallUI\render-slots-off (the client folder is the parent of bin64, Game.dll's folder).
bool switched_off(){
    wchar_t path[MAX_PATH*2]{};
    constexpr DWORD capacity=DWORD(sizeof path/sizeof path[0]);
    DWORD n=GetModuleFileNameW(GetModuleHandleW(L"Game.dll"),path,capacity);
    if(!n || n>=capacity)return false;
    std::wstring p(path,n);
    for(int up=0;up<2;++up){auto cut=p.find_last_of(L"\\/");if(cut==std::wstring::npos)return false;p.resize(cut);}
    p+=L"\\Mods\\AetherfallUI\\render-slots-off";
    return GetFileAttributesW(p.c_str())!=INVALID_FILE_ATTRIBUTES;
}
bool install(){
    if(switched_off()){AetherfallRenderSlotsDiagnostics[0]=5;return false;}
    xr=reinterpret_cast<unsigned char*>(GetModuleHandleW(L"XRenderD3D9.dll"));
    if(!xr){AetherfallRenderSlotsDiagnostics[0]=2;return false;}
    for(const auto& s:SITES)if(!readable(xr+s.rva,s.size) || std::memcmp(xr+s.rva,s.bytes,s.size)){AetherfallRenderSlotsDiagnostics[0]=3;return false;}
    auto block=near_block();
    if(!block){AetherfallRenderSlotsDiagnostics[0]=4;return false;}
    Code c{block};
    unsigned char* premask_read=c.at;reader(c,SITES[0].bytes,premask);
    unsigned char* view_read=c.at;reader(c,SITES[1].bytes,view);
    unsigned char* mask_pass=c.at;
    const unsigned char* m=SITES[2].bytes;
    c.copy(m,4);                                                         // movsxd rdx,[rcx+0x50]
    c.raw({0x48,0xb8});c.imm64(xr+GLOBAL);c.raw({0x48,0x8b,0x00});      // mov rax,[XR+GLOBAL] (stock is rip-relative)
    c.copy(m+11,7);                                                      // mov rcx,[rax+0x19d90]
    c.raw({0x83,0xfa,STOCK,0x73,0x15});                                  // cmp edx,8; jae ours
    c.copy(m+18,16);c.jump(xr+MASK_BACK);                                // mov rsi,[premask]; mov rdi,[mask]
    c.raw({0x83,0xfa,LIMIT,0x73,0x15});                                  // ours: cmp edx,80; jae none
    c.raw({0x48,0xbe});c.imm64(premask);c.raw({0x48,0x8b,0x34,0xd6});   // mov rsi,[ours+rdx*8]
    c.raw({0x31,0xff});c.jump(xr+MASK_BACK);                             // no mask file: xor edi,edi
    c.raw({0x31,0xf6,0x31,0xff});c.jump(xr+MASK_BACK);                  // none: xor esi,esi; xor edi,edi
    unsigned char* created=c.at;
    c.call(0xf9,&create);c.copy(SITES[3].bytes,5);c.jump(xr+CREATED_BACK);   // mov rcx,rdi (renderer)
    unsigned char* released=c.at;
    c.call(0xd9,&release);c.copy(SITES[4].bytes,7);c.jump(xr+RELEASE_BACK);  // mov rcx,rbx (renderer)
    DWORD old=0;
    if(!VirtualProtect(block,0x10000,PAGE_EXECUTE_READ,&old)){VirtualFree(block,0,MEM_RELEASE);AetherfallRenderSlotsDiagnostics[0]=4;return false;}
    FlushInstructionCache(GetCurrentProcess(),block,size_t(c.at-block));
    // No slot is made here: a view asks for its own through ensure(), after the readers are in place.
    const std::pair<size_t,unsigned char*> order[]={{CREATED,created},{RELEASE,released},
        {READ_PREMASK,premask_read},{READ_VIEW,view_read},{MASK_PASS,mask_pass}};
    for(const auto& step:order){
        size_t size=0;for(const auto& s:SITES)if(s.rva==step.first)size=s.size;
        if(!patch(step.first,size,step.second)){AetherfallRenderSlotsDiagnostics[0]=4;return false;}
    }
    installed=true;AetherfallRenderSlotsDiagnostics[0]=1;return true;
}
// True once this slot (8..79) exists; a view may then be given it. Install is tried once; a missing
// slot (renderer not up yet, or released and not rebuilt) is retried at most every two seconds.
bool ensure(int slot){
    if(!tried){tried=true;install();}
    if(!installed || slot<STOCK || slot>=LIMIT)return false;
    wanted[slot]=true;
    static ULONGLONG retry[LIMIT]{};
    if((!view[slot] || !premask[slot]) && GetTickCount64()>=retry[slot]){retry[slot]=GetTickCount64()+2000;create(renderer());}
    return view[slot]!=nullptr;
}
}
