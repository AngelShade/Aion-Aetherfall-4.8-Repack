// Two allowlisted CryEngine camera variables, called only on the native UI thread.
// ShugoConsole identifies the same variables and updates their numeric/string
// representations. We use the game's console lookup; there is no memory scan,
// external process, console unlock or recurring enforcement.
struct CameraSetting {
    const char* key;
    const char* variable;
    double minimum,maximum;
    Ptr owner=nullptr;
    bool overridden=false;
    int original_int=0;
    float original_float=0;
    char original_text[32]{};
};
CameraSetting camera_settings[]={{"fov","g_minFov",60,170},{"distance","g_camMax",5,50}};

CameraSetting* camera_setting(const char* key){
    for(auto& setting:camera_settings)if(key && !std::strcmp(key,setting.key))return &setting;
    return nullptr;
}
bool camera_writable(Ptr p,size_t n){
    MEMORY_BASIC_INFORMATION m{};
    if(!p || !VirtualQuery(p,&m,sizeof m) || m.State!=MEM_COMMIT || (m.Protect&(PAGE_GUARD|PAGE_NOACCESS)))return false;
    DWORD access=m.Protect&0xff;
    return (access==PAGE_READWRITE || access==PAGE_WRITECOPY || access==PAGE_EXECUTE_READWRITE || access==PAGE_EXECUTE_WRITECOPY)
        && static_cast<unsigned char*>(p)+n<=static_cast<unsigned char*>(m.BaseAddress)+m.RegionSize;
}
Ptr camera_variable(CameraSetting& setting){
    // Game.dll's own chat-log lookup (0x67b4d7..0x67b50b) uses exactly
    // ISystem +0x138 -> IConsole +0xc0(name, include hidden=true).
    const unsigned char get_console[]={0xff,0x90,0x38,0x01,0x00,0x00};
    const unsigned char get_variable[]={0xff,0x90,0xc0,0x00,0x00,0x00};
    if(std::memcmp(game()+0x67b4f1,get_console,sizeof get_console)
       || std::memcmp(game()+0x67b50b,get_variable,sizeof get_variable))return nullptr;
    Ptr system=global<Ptr>(SYSTEM);
    if(!system || !readable(system,8) || !readable(field<Ptr>(system,0),0x140))return nullptr;
    Ptr console=method<Ptr(*)(Ptr)>(system,0x138)(system);
    if(!console || !readable(console,8) || !readable(field<Ptr>(console,0),0xc8))return nullptr;
    Ptr value=method<Ptr(*)(Ptr,const char*,bool)>(console,0xc0)(console,setting.variable,true);
    if(!camera_writable(value,0x1c0))return nullptr;
    auto cry=reinterpret_cast<unsigned char*>(GetModuleHandleW(L"CrySystem.dll"));
    if(!cry || field<Ptr>(value,0)!=cry+0x21bce8)return nullptr;
    // Current native CXConsoleVariable getters and local storage constructor:
    // int *at +0xa8 -> +0xb8; float *at +0xb0 -> +0xbc;
    // string *at +0xa0 -> +0xc0 (256 bytes); inline name at +9.
    const unsigned char get_int[]={0x48,0x8b,0x81,0xa8,0x00,0x00,0x00,0x8b,0x00,0xc3};
    const unsigned char get_float[]={0x48,0x8b,0x81,0xb0,0x00,0x00,0x00,0xf3,0x0f,0x10,0x00,0xc3};
    if(std::memcmp(cry+0x75500,get_int,sizeof get_int) || std::memcmp(cry+0x75510,get_float,sizeof get_float)
       || field<Ptr>(value,0xa8)!=static_cast<unsigned char*>(value)+0xb8
       || field<Ptr>(value,0xb0)!=static_cast<unsigned char*>(value)+0xbc
       || field<Ptr>(value,0xa0)!=static_cast<unsigned char*>(value)+0xc0
       || std::memcmp(static_cast<const char*>(value)+9,setting.variable,std::strlen(setting.variable)+1))return nullptr;
    float current=field<float>(value,0xbc);
    const char* current_text=static_cast<const char*>(value)+0xc0;
    if(!std::isfinite(current) || std::fabs(current)>1000000 || strnlen(current_text,32)==32)return nullptr;
    if(setting.owner!=value){setting.owner=value;setting.overridden=false;}
    return value;
}
bool set_camera_setting(CameraSetting& setting,Ptr value,double wanted){
    if(!std::isfinite(wanted) || (wanted!=-1 && (wanted<setting.minimum || wanted>setting.maximum || wanted!=std::floor(wanted))))return false;
    if(wanted==-1){
        if(!setting.overridden)return true;
        field<int>(value,0xb8)=setting.original_int;
        field<float>(value,0xbc)=setting.original_float;
        std::memcpy(static_cast<unsigned char*>(value)+0xc0,setting.original_text,std::strlen(setting.original_text)+1);
        setting.overridden=false;
        return true;
    }
    if(!setting.overridden){
        setting.original_int=field<int>(value,0xb8);
        setting.original_float=field<float>(value,0xbc);
        std::memcpy(setting.original_text,static_cast<unsigned char*>(value)+0xc0,32);
    }
    // Keep every native representation consistent, as ShugoConsole does.
    // No flags, callbacks, input/cursor state or unrelated variables change.
    char formatted[32]{};std::snprintf(formatted,sizeof formatted,"%.0f",wanted);
    field<int>(value,0xb8)=int(wanted);field<float>(value,0xbc)=float(wanted);
    std::memcpy(static_cast<unsigned char*>(value)+0xc0,formatted,std::strlen(formatted)+1);
    setting.overridden=true;
    return true;
}
