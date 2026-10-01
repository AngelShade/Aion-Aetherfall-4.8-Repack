// Native model views sit above the browser, so DOM mouse listeners cannot
// reliably receive their input. Capture only this preview's window messages;
// all model changes still run in wardrobe::tick, never in the window callback.
namespace mouse {
std::mutex mutex;
HWND window=nullptr;
WNDPROC prior=nullptr;
Rect bounds{};
bool active=false;
int dragging=0;
POINT anchor{};
float rotation=0,pan_x=0,pan_y=0,wheel=0;
float scale=1,offset_x=0,offset_z=0;
float clamp(float n,float lo,float hi){return (std::max)(lo,(std::min)(hi,n));}
bool inside(POINT p){return p.x>=bounds.x && p.y>=bounds.y && p.x<bounds.x+bounds.w && p.y<bounds.y+bounds.h;}
LRESULT CALLBACK receive(HWND hwnd,UINT message,WPARAM wparam,LPARAM lparam){
    POINT point{static_cast<short>(LOWORD(lparam)),static_cast<short>(HIWORD(lparam))};
    if(message==WM_MOUSEWHEEL)ScreenToClient(hwnd,&point);
    bool consume=false,capture=false,release=false;WNDPROC forward;
    {
        std::lock_guard<std::mutex> lock(mutex);forward=prior;
        if(active && (message==WM_LBUTTONDOWN || message==WM_RBUTTONDOWN) && inside(point)){
            dragging=message==WM_LBUTTONDOWN?1:2;anchor=point;capture=consume=true;
        }else if(message==WM_MOUSEMOVE && dragging){
            if(active){
                float dx=clamp(float(point.x-anchor.x),-200,200),dy=clamp(float(point.y-anchor.y),-200,200);
                if(dragging==1)rotation=clamp(rotation+dx,-1000,1000);
                else{pan_x=clamp(pan_x+dx,-1000,1000);pan_y=clamp(pan_y+dy,-1000,1000);}
                anchor=point;consume=true;
            }else{dragging=0;release=true;}
        }else if((message==WM_LBUTTONUP && dragging==1) || (message==WM_RBUTTONUP && dragging==2)){
            dragging=0;release=consume=true;
        }else if(active && message==WM_MOUSEWHEEL && inside(point)){
            wheel=clamp(wheel+float(static_cast<short>(HIWORD(wparam)))/WHEEL_DELTA,-20,20);consume=true;
        }else if(message==WM_KILLFOCUS || message==WM_CANCELMODE || message==WM_CAPTURECHANGED){
            dragging=0;
            if(message!=WM_CAPTURECHANGED)rotation=pan_x=pan_y=wheel=0;
            release=message!=WM_CAPTURECHANGED;
        }
    }
    // SetCapture/ReleaseCapture can synchronously dispatch another message.
    if(capture)SetCapture(hwnd);
    if(release && GetCapture()==hwnd)ReleaseCapture();
    LRESULT result=consume?0:(forward?CallWindowProcW(forward,hwnd,message,wparam,lparam):DefWindowProcW(hwnd,message,wparam,lparam));
    if(message==WM_NCDESTROY){std::lock_guard<std::mutex> lock(mutex);if(window==hwnd){window=nullptr;prior=nullptr;active=false;dragging=0;}}
    return result;
}
BOOL CALLBACK find_window(HWND candidate,LPARAM value){
    DWORD pid=0;GetWindowThreadProcessId(candidate,&pid);RECT r{};
    if(pid==GetCurrentProcessId() && IsWindowVisible(candidate) && GetClientRect(candidate,&r) && r.right>=640 && r.bottom>=480){
        *reinterpret_cast<HWND*>(value)=candidate;return FALSE;
    }return TRUE;
}
void install(){
    if(window)return;
    HWND candidate=nullptr;EnumWindows(find_window,reinterpret_cast<LPARAM>(&candidate));if(!candidate)return;
    std::lock_guard<std::mutex> lock(mutex);
    SetLastError(0);auto previous=SetWindowLongPtrW(candidate,GWLP_WNDPROC,reinterpret_cast<LONG_PTR>(receive));
    if(previous){window=candidate;prior=reinterpret_cast<WNDPROC>(previous);log("Wardrobe: native preview mouse controls attached");}
}
void stop(){
    HWND captured;
    {std::lock_guard<std::mutex> lock(mutex);active=false;dragging=0;rotation=pan_x=pan_y=wheel=0;captured=window;}
    if(captured && GetCapture()==captured)ReleaseCapture();
    scale=1;offset_x=offset_z=0;
}
void restore(const Moved& model){
    field<std::array<float,3>>(model.widget,0x6e8)=model.camera;
    field<std::array<float,3>>(model.widget,0x8bc)=model.camera;
    field<std::array<float,3>>(model.widget,0x654)=model.angle;
    field<int>(model.widget,0x610)=1;
}
void reset(){stop();for(const auto& model:moved)restore(model);}
void apply(float dx,float px,float py,float steps){
    if(!paper || modal_hidden || moved.empty())return;
    scale=clamp(scale*std::pow(0.88f,steps),0.35f,2.5f);
    offset_x=clamp(offset_x-px*0.004f*scale,-2,2);
    offset_z=clamp(offset_z+py*0.004f*scale,-2,2);
    for(const auto& model:moved){
        Ptr widget=model.widget;
        // Same angle function used by the stock Left/Right preview controls.
        if(dx)reinterpret_cast<void(__cdecl*)(Ptr,float,float,float)>(game()+0x487480)(widget,dx*0.45f,1,1);
        auto camera=model.camera;camera[0]+=offset_x;camera[1]*=scale;camera[2]+=offset_z;
        if(field<std::array<float,3>>(widget,0x6e8)!=camera || dx){
            field<std::array<float,3>>(widget,0x6e8)=camera;
            field<std::array<float,3>>(widget,0x8bc)=camera;
            field<int>(widget,0x610)=1;
        }
    }
}
void tick(){
    if(!paper || !host)return;
    install();Rect hit{};bool enabled=false;
    if(!modal_hidden && (field<uint64_t>(host,0x30)&1))for(const auto& model:moved){
        if(field<uint64_t>(model.widget,0x30)&1){method<void(__cdecl*)(Ptr,Rect*)>(model.widget,0x58)(model.widget,&hit);enabled=true;break;}
    }
    float dx,px,py,steps;
    {
        std::lock_guard<std::mutex> lock(mutex);bounds=hit;active=enabled && window;
        dx=rotation;px=pan_x;py=pan_y;steps=wheel;rotation=pan_x=pan_y=wheel=0;
    }
    if(dx || px || py || steps || scale!=1 || offset_x || offset_z)apply(dx,px,py,steps);
}
}
