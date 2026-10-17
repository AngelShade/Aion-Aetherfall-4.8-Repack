// Included by the existing native login module. Only the launcher's private,
// validated session activates this gate; ordinary client starts pass through.
namespace startup {
volatile LONG state=0, patching=0;
HANDLE releaseEvent=nullptr, parentProcess=nullptr, trace=INVALID_HANDLE_VALUE;
HWINEVENTHOOK events=nullptr;
HHOOK creation=nullptr;
ULONGLONG began=0;
HWND renderer=nullptr;
WNDPROC previous=nullptr;
LONG_PTR originalTaskbar=0;
UINT releaseMessage=0;
constexpr LONG_PTR taskbarMask=WS_EX_APPWINDOW|WS_EX_TOOLWINDOW;

void log(const char* text){
    if(trace==INVALID_HANDLE_VALUE)return;
    char line[384]{};
    int count=sprintf_s(line,"%llu ms | %s\r\n",GetTickCount64()-began,text);
    DWORD written=0;if(count>0)WriteFile(trace,line,DWORD(count),&written,nullptr);
}
bool holding(){return InterlockedCompareExchange(&state,0,0)==1;}
bool mainWindow(HWND window){
    if(!window)return false;
    DWORD pid=0;GetWindowThreadProcessId(window,&pid);
    if(pid!=GetCurrentProcessId())return false;
    wchar_t name[64]{};GetClassNameW(window,name,64);
    return !wcscmp(name,L"AIONClientWndClass1.0");
}
bool gated(HWND window){return holding()&&mainWindow(window);}

// Call the real USER32 imports in this module. Only references in the named
// game modules below are redirected; no system DLL code is modified.
BOOL WINAPI show(HWND window,int command){
    if(gated(window)&&command!=SW_HIDE)return FALSE;
    return ShowWindow(window,command);
}
BOOL WINAPI showAsync(HWND window,int command){
    if(gated(window)&&command!=SW_HIDE)return TRUE;
    return ShowWindowAsync(window,command);
}
BOOL WINAPI position(HWND window,HWND after,int x,int y,int width,int height,UINT flags){
    if(gated(window)){
        flags=(flags&~SWP_SHOWWINDOW)|SWP_HIDEWINDOW|SWP_NOACTIVATE|SWP_NOZORDER;
    }
    return SetWindowPos(window,after,x,y,width,height,flags);
}
BOOL WINAPI visible(HWND window){
    // Keep the native update/render loop alive while its actual HWND is hidden.
    // Login readiness is reported by that loop, not by a timer or window handle.
    return gated(window)?TRUE:IsWindowVisible(window);
}
BOOL WINAPI foreground(HWND window){return gated(window)?FALSE:SetForegroundWindow(window);}

void patchReferences(){
    if(!holding()||InterlockedCompareExchange(&patching,1,0))return;
    struct Redirect {ULONG_PTR source,destination;};
    Redirect redirects[]={
        {reinterpret_cast<ULONG_PTR>(&ShowWindow),reinterpret_cast<ULONG_PTR>(&show)},
        {reinterpret_cast<ULONG_PTR>(&ShowWindowAsync),reinterpret_cast<ULONG_PTR>(&showAsync)},
        {reinterpret_cast<ULONG_PTR>(&SetWindowPos),reinterpret_cast<ULONG_PTR>(&position)},
        {reinterpret_cast<ULONG_PTR>(&IsWindowVisible),reinterpret_cast<ULONG_PTR>(&visible)},
        {reinterpret_cast<ULONG_PTR>(&SetForegroundWindow),reinterpret_cast<ULONG_PTR>(&foreground)}};
    HANDLE snapshot=CreateToolhelp32Snapshot(TH32CS_SNAPMODULE,GetCurrentProcessId());
    if(snapshot==INVALID_HANDLE_VALUE){InterlockedExchange(&patching,0);return;}
    MODULEENTRY32W entry{};entry.dwSize=sizeof entry;unsigned changed=0;
    for(BOOL more=Module32FirstW(snapshot,&entry);more;more=Module32NextW(snapshot,&entry)){
        if(_wcsicmp(entry.szModule,L"aion.bin")&&_wcsicmp(entry.szModule,L"crysystem.dll")&&
           _wcsicmp(entry.szModule,L"Game.dll")&&_wcsicmp(entry.szModule,L"XRenderD3D9.dll")&&
           _wcsicmp(entry.szModule,L"d3d9.dll"))continue;
        // The packed client restores native imports into writable image sections
        // without putting every restored thunk in the PE import directory.
        auto base=entry.modBaseAddr;
        auto dos=reinterpret_cast<IMAGE_DOS_HEADER*>(base);
        if(dos->e_magic!=IMAGE_DOS_SIGNATURE||dos->e_lfanew<=0||DWORD(dos->e_lfanew)>entry.modBaseSize-sizeof(IMAGE_NT_HEADERS64))continue;
        auto nt=reinterpret_cast<IMAGE_NT_HEADERS64*>(base+dos->e_lfanew);
        if(nt->Signature!=IMAGE_NT_SIGNATURE||nt->OptionalHeader.Magic!=IMAGE_NT_OPTIONAL_HDR64_MAGIC)continue;
        auto section=IMAGE_FIRST_SECTION(nt);
        for(unsigned i=0;i<nt->FileHeader.NumberOfSections;++i){
            auto& s=section[i];
            if(!(s.Characteristics&IMAGE_SCN_MEM_WRITE)||s.VirtualAddress>=entry.modBaseSize)continue;
            SIZE_T size=s.Misc.VirtualSize;
            if(size>entry.modBaseSize-s.VirtualAddress)size=entry.modBaseSize-s.VirtualAddress;
            auto start=reinterpret_cast<ULONG_PTR>(base+s.VirtualAddress);
            auto end=start+size;
            ULONG_PTR readableUntil=0;
            for(auto address=(start+7)&~ULONG_PTR(7);address+8<=end;address+=8){
                if(address>=readableUntil){
                    MEMORY_BASIC_INFORMATION memory{};
                    if(!VirtualQuery(reinterpret_cast<void*>(address),&memory,sizeof memory))break;
                    readableUntil=reinterpret_cast<ULONG_PTR>(memory.BaseAddress)+memory.RegionSize;
                    if(memory.State!=MEM_COMMIT||memory.Protect&(PAGE_NOACCESS|PAGE_GUARD)){
                        address=readableUntil-8;continue;
                    }
                }
                auto slot=reinterpret_cast<ULONG_PTR*>(address);
                auto value=*slot;
                for(auto& redirect:redirects)if(value==redirect.source){
                    DWORD protect=0;
                    if(VirtualProtect(slot,sizeof *slot,PAGE_EXECUTE_READWRITE,&protect)){
                        InterlockedCompareExchangePointer(reinterpret_cast<void* volatile*>(slot),
                            reinterpret_cast<void*>(redirect.destination),reinterpret_cast<void*>(redirect.source));
                        DWORD ignored=0;VirtualProtect(slot,sizeof *slot,protect,&ignored);++changed;
                    }
                    break;
                }
            }
        }
    }
    CloseHandle(snapshot);InterlockedExchange(&patching,0);
    if(changed){char line[96]{};sprintf_s(line,"Guarded %u native window API references.",changed);log(line);}
}

LRESULT CALLBACK windowProcedure(HWND window,UINT message,WPARAM wparam,LPARAM lparam){
    WNDPROC original=previous;
    if(message==releaseMessage&&releaseMessage){
        LONG_PTR style=GetWindowLongPtrW(window,GWL_EXSTYLE);
        SetWindowLongPtrW(window,GWL_EXSTYLE,(style&~taskbarMask)|originalTaskbar);
        // This runs on the game's UI thread after the real login draw signal.
        SetWindowPos(window,nullptr,0,0,0,0,SWP_NOMOVE|SWP_NOSIZE|SWP_NOZORDER|SWP_NOACTIVATE|SWP_FRAMECHANGED);
        ShowWindow(window,SW_SHOWNOACTIVATE);
        if(reinterpret_cast<WNDPROC>(GetWindowLongPtrW(window,GWLP_WNDPROC))==windowProcedure)
            SetWindowLongPtrW(window,GWLP_WNDPROC,reinterpret_cast<LONG_PTR>(original));
        log("Login renderer revealed on its UI thread.");return 0;
    }
    if(message==WM_WINDOWPOSCHANGING&&holding()){
        LRESULT result=CallWindowProcW(original,window,message,wparam,lparam);
        auto pos=reinterpret_cast<WINDOWPOS*>(lparam);
        pos->flags=(pos->flags&~SWP_SHOWWINDOW)|SWP_HIDEWINDOW|SWP_NOACTIVATE|SWP_NOZORDER;
        return result;
    }
    if(message==WM_STYLECHANGING&&holding()){
        LRESULT result=CallWindowProcW(original,window,message,wparam,lparam);
        auto style=reinterpret_cast<STYLESTRUCT*>(lparam);
        if(static_cast<int>(wparam)==GWL_STYLE)style->styleNew&=~WS_VISIBLE;
        if(static_cast<int>(wparam)==GWL_EXSTYLE)style->styleNew=(style->styleNew&~WS_EX_APPWINDOW)|WS_EX_TOOLWINDOW;
        return result;
    }
    return CallWindowProcW(original,window,message,wparam,lparam);
}
void guard(HWND window){
    if(!gated(window)||renderer==window)return;
    // CBT reaches here before WM_NCCREATE and before WS_VISIBLE can be shown.
    originalTaskbar=GetWindowLongPtrW(window,GWL_EXSTYLE)&taskbarMask;
    previous=reinterpret_cast<WNDPROC>(GetWindowLongPtrW(window,GWLP_WNDPROC));
    if(!previous)return;
    renderer=window;
    SetWindowLongPtrW(window,GWLP_WNDPROC,reinterpret_cast<LONG_PTR>(&windowProcedure));
    SetWindowLongPtrW(window,GWL_STYLE,GetWindowLongPtrW(window,GWL_STYLE)&~WS_VISIBLE);
    SetWindowLongPtrW(window,GWL_EXSTYLE,(GetWindowLongPtrW(window,GWL_EXSTYLE)&~taskbarMask)|WS_EX_TOOLWINDOW);
    log("Renderer creation guarded; native loading stays hidden.");
    patchReferences();
}
LRESULT CALLBACK creating(int code,WPARAM wparam,LPARAM lparam){
    if(code==HCBT_CREATEWND&&holding())guard(reinterpret_cast<HWND>(wparam));
    if(code==HCBT_ACTIVATE&&gated(reinterpret_cast<HWND>(wparam)))return 1;
    return CallNextHookEx(creation,code,wparam,lparam);
}
void CALLBACK windowEvent(HWINEVENTHOOK,DWORD event,HWND window,LONG object,LONG child,DWORD,DWORD){
    if(object!=OBJID_WINDOW||child!=CHILDID_SELF||!gated(window))return;
    guard(window);
    if(event==EVENT_OBJECT_SHOW){ShowWindow(window,SW_HIDE);patchReferences();}
}
DWORD WINAPI watch(void*){
    HANDLE waits[]={releaseEvent,parentProcess};
    DWORD result=WaitForMultipleObjects(2,waits,FALSE,245000);
    InterlockedExchange(&state,2);
    log(result==WAIT_OBJECT_0?"Launcher released the login handoff.":
        result==WAIT_OBJECT_0+1?"Launcher exited; restoring the client.":"Startup deadline reached; restoring the client.");
    if(events)UnhookWinEvent(events);
    if(creation)UnhookWindowsHookEx(creation);
    HWND window=renderer;
    if(window&&IsWindow(window))PostMessageW(window,releaseMessage,0,0);
    CloseHandle(releaseEvent);CloseHandle(parentProcess);
    // Trace stays open for the UI-thread acknowledgement and closes at process exit.
    return 0;
}
bool begin(){
    LONG existing=InterlockedCompareExchange(&state,0,0);
    if(existing)return existing==1;
    wchar_t name[112]{},parentText[24]{};
    DWORD count=GetEnvironmentVariableW(L"AETHERFALL_STARTUP_GATE",name,112);
    const wchar_t prefix[]=L"Local\\AetherfallLoginReady-";constexpr size_t prefixSize=_countof(prefix)-1;
    if(count!=prefixSize+32+5||wcsncmp(name,prefix,prefixSize)||wcscmp(name+prefixSize+32,L"-Show"))return false;
    for(size_t i=prefixSize;i<prefixSize+32;++i)
        if(!((name[i]>=L'0'&&name[i]<=L'9')||(name[i]>=L'a'&&name[i]<=L'f')))return false;
    count=GetEnvironmentVariableW(L"AETHERFALL_LAUNCHER_PID",parentText,24);
    if(!count||count>=24)return false;
    wchar_t* tail=nullptr;unsigned long parentId=wcstoul(parentText,&tail,10);
    if(!parentId||!tail||*tail||parentId==GetCurrentProcessId())return false;
    releaseEvent=OpenEventW(SYNCHRONIZE,FALSE,name);parentProcess=OpenProcess(SYNCHRONIZE,FALSE,parentId);
    if(!releaseEvent||!parentProcess){if(releaseEvent)CloseHandle(releaseEvent);if(parentProcess)CloseHandle(parentProcess);return false;}
    began=GetTickCount64();wchar_t path[32768]{};
    count=GetEnvironmentVariableW(L"AETHERFALL_STARTUP_TRACE",path,_countof(path));
    if(count&&count<_countof(path))trace=CreateFileW(path,FILE_APPEND_DATA,FILE_SHARE_READ|FILE_SHARE_WRITE,nullptr,OPEN_ALWAYS,FILE_ATTRIBUTE_NORMAL,nullptr);
    releaseMessage=RegisterWindowMessageW(L"Aetherfall.NativeLoginPresentation.Release.v2");
    InterlockedExchange(&state,1);
    creation=SetWindowsHookExW(WH_CBT,creating,nullptr,GetCurrentThreadId());
    events=SetWinEventHook(EVENT_OBJECT_CREATE,EVENT_OBJECT_SHOW,module,windowEvent,GetCurrentProcessId(),0,WINEVENT_INCONTEXT);
    if(!creation||!events||!releaseMessage){
        InterlockedExchange(&state,2);
        if(creation)UnhookWindowsHookEx(creation);if(events)UnhookWinEvent(events);
        CloseHandle(releaseEvent);CloseHandle(parentProcess);
        log("Native gate unavailable; ordinary startup retained.");return false;
    }
    patchReferences();
    HANDLE worker=CreateThread(nullptr,0,watch,nullptr,0,nullptr);
    if(!worker){
        // Any installation failure is visible and falls back to normal startup.
        InterlockedExchange(&state,2);
        if(creation)UnhookWindowsHookEx(creation);if(events)UnhookWinEvent(events);
        CloseHandle(releaseEvent);CloseHandle(parentProcess);
        log("Native gate unavailable; ordinary startup retained.");return false;
    }
    CloseHandle(worker);log("Native gate active before the splash routine; splash skipped.");return true;
}
}
