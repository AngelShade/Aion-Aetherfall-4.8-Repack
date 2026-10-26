// Right-drag camera turn without Windows mouse hooks (included by aetherfall_core.cpp).
// Game.dll's window procedure turns the camera by its message-derived cursor delta
// (CURSOR_X/Y minus the previous message position, 0x29c53), then centres the cursor
// (0x13ed20) and saves the live position at this+0x1520. A mouse move that Windows
// delivers after that centring still carries the pre-centre position, so the next delta
// spans the whole way back to where the drag began: the camera snap. Once the game has
// centred the cursor (this+0x153c), turn by the live position minus that saved centre,
// which cannot be stale or out of order. The first turn of a drag stays native.
extern "C" __declspec(dllexport) volatile uintptr_t AetherfallCameraDiagnostics[4]={0,0,0,0};
// [0] 1 installed, 2 site differs, 3 relay/protection failed; [1] live turns; [2] native turns.
constexpr size_t CAMERA_TURN_SITE=0x29c8d, CAMERA_TURN=0x36b90;
constexpr size_t CAMERA_SAVED=0x1520, CAMERA_CENTRED=0x153c;
using CameraTurnFn=uintptr_t(*)(Ptr,float,float,bool,bool);
uintptr_t camera_turn(Ptr self,float dx,float dy,bool apply,bool flag){
    POINT live{};
    if(self && field<int>(self,CAMERA_CENTRED) && GetCursorPos(&live)){
        const POINT& centre=field<POINT>(self,CAMERA_SAVED);
        dx=float(live.x-centre.x);dy=float(live.y-centre.y);
        ++AetherfallCameraDiagnostics[1];
    }else ++AetherfallCameraDiagnostics[2];
    return native<CameraTurnFn>(CAMERA_TURN)(self,dx,dy,apply,flag);
}
bool install_camera_turn(){
    unsigned char expected[5]{0xe8};int32_t native_displacement=int32_t(CAMERA_TURN-CAMERA_TURN_SITE-5);
    std::memcpy(expected+1,&native_displacement,4);
    auto site=game()+CAMERA_TURN_SITE;
    if(std::memcmp(site,expected,5)){AetherfallCameraDiagnostics[0]=2;return false;}
    // Same near relay as the quest hooks: the native call stays five bytes.
    uintptr_t center=reinterpret_cast<uintptr_t>(game()),lower=center>0x60000000?center-0x60000000:0x10000,upper=center+0x60000000;
    unsigned char* relay=nullptr;MEMORY_BASIC_INFORMATION region{};
    for(uintptr_t cursor=lower;cursor<upper;){
        if(!VirtualQuery(reinterpret_cast<void*>(cursor),&region,sizeof region))break;
        uintptr_t begin=(reinterpret_cast<uintptr_t>(region.BaseAddress)+0xffff)&~uintptr_t(0xffff),end=reinterpret_cast<uintptr_t>(region.BaseAddress)+region.RegionSize;
        if(region.State==MEM_FREE && begin<end && end-begin>=0x10000){relay=static_cast<unsigned char*>(VirtualAlloc(reinterpret_cast<void*>(begin),0x10000,MEM_RESERVE|MEM_COMMIT,PAGE_READWRITE));if(relay)break;}
        if(end<=cursor)break;cursor=end;
    }
    if(!relay){AetherfallCameraDiagnostics[0]=3;return false;}
    const void* target=reinterpret_cast<const void*>(&camera_turn);
    relay[0]=0xff;relay[1]=0x25;std::memset(relay+2,0,4);std::memcpy(relay+6,&target,8);
    DWORD prior=0,unused=0,old=0;
    if(!VirtualProtect(relay,0x10000,PAGE_EXECUTE_READ,&prior)){VirtualFree(relay,0,MEM_RELEASE);AetherfallCameraDiagnostics[0]=3;return false;}
    FlushInstructionCache(GetCurrentProcess(),relay,16);
    if(!VirtualProtect(site,5,PAGE_EXECUTE_READWRITE,&old)){VirtualFree(relay,0,MEM_RELEASE);AetherfallCameraDiagnostics[0]=3;return false;}
    int32_t displacement=int32_t(reinterpret_cast<intptr_t>(relay)-reinterpret_cast<intptr_t>(site)-5);
    site[0]=0xe8;std::memcpy(site+1,&displacement,4);VirtualProtect(site,5,old,&unused);FlushInstructionCache(GetCurrentProcess(),site,5);
    AetherfallCameraDiagnostics[0]=1;return true;
}
