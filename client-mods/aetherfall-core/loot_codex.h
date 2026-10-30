// Native Codex controls in the world map (DlgNewMap, dialog 0xe5) and the creature
// chest on the target frame (DlgTarget, dialog 0xb). Read-only for game state:
// it shows/hides the panel's own widgets, selects a map/layer and pans the map
// exactly as native code does. Reference data is fetched off the UI thread;
// the existing Lua controller renders it into native children of the map.
// Included after hook(); codex_command is forward-declared before hook().
// Static evidence (Game.dll bc97b23f...):
//  - 0x7ef580 (DlgNewMap +0x490) and 0x4ae9d0 (DlgTarget +0x490) read the clicked
//    button from the event's first pointer; the map control is DlgNewMap+0x548.
//  - Where-links (0x6397f3..0x639916): layer = 0x1e4ff0(game+0x1325d90, xyz), then
//    0x7d65e0(control, world, layer); control world/layer at +0x38c/+0x390.
//  - 0x7f3f00(map, expanded) stores +0x738 and lays out both sizes: the expanded
//    dialog uses max_dlg_frame (932 wide, legend tab sheet at the left); the small
//    one keeps min_dlg_frame's 656 x 735, moves ui_container left by 276 and puts
//    map_container at 19,84. We use those same content positions inside the
//    native map/legend positions. Codex adds 276 px to BOTH frame templates;
//    their difference stays 276, so the left legend keeps its native behaviour.
//  - 0x7d47a0(control, x, y, float* world): local pixel -> world, from pan
//    +0x348/+0x340, drag +0x350/+0x358 and zoom +0x370. Drag release
//    (0x7d4ce2..0x7d4d1b) adds to the pan fields, then calls 0x7d5ba0.
//  - 0x7d7090(control, direction, steps, notify, x, y) clamps both pan axes
//    to the selected map at the current zoom before updating its scrollbars.
//    Zero steps preserves zoom; zero drag offsets commits just the requested pan.
namespace codex {
constexpr int MAP=0xe5,TARGET=0xb;
constexpr ULONGLONG MARKER_MS=120000;
constexpr size_t MARKS=48;  // af_codex_marker_1..48: every recorded spawn of the listed creature
struct Location {int map;float x,y,z;};
struct Mark {int map,layer;float x,y;};
struct Hover {Ptr widget;int item;};
enum class VisualKind { Reset,Icon,Tooltip,Forget };
struct Visual {VisualKind kind;Ptr widget=nullptr;std::string path;int value=0;};
struct State {
    Ptr owner=nullptr,panel=nullptr,marker=nullptr,ctrl=nullptr,toggle=nullptr,large=nullptr,small=nullptr;
    bool open=false,layingOut=false;double scale=1;
    Ptr market=nullptr;int marketId=0;
    std::unordered_map<std::string,Ptr> widgets;
    std::deque<std::string> events;
    int world=0,layer=0,shownWorld=0,shownLayer=0;float x=0,y=0;ULONGLONG expires=0;
    double panX=0,panY=0,dragX=0,dragY=0,width=0,height=0;int zoom=0;
    std::optional<Location> location;
    std::vector<Mark> marks;std::vector<Ptr> markWidgets;bool marksDirty=false;
    std::unordered_map<Ptr,std::string> iconPaths;
    std::vector<Hover> hovers;
    std::vector<Visual> visuals;
} state;
void clear_marks();
void clear_hovers();
void apply_visuals();
struct Response { unsigned epoch,sequence;std::string body; };
std::future<Response> pending;
unsigned epoch=0,queuedSequence=0;
std::string queuedQuery;
void show(Ptr p,bool on){if(p && shown(p)!=on)method<void(*)(Ptr,int,int)>(p,on?0xd0:0xd8)(p,1,0);}
void set_rect(Ptr widget,const Rect& r){
    const auto& old=field<Rect>(widget,0x50);
    if(old.x!=r.x || old.y!=r.y || old.w!=r.w || old.h!=r.h)method<SetRectFn>(widget,SET_RECT)(widget,&r);
}
void layout(){
    if(!state.owner || !state.large || !state.toggle || state.layingOut)return;
    state.layingOut=true;
    state.scale=field<Rect>(state.large,0x50).w/(state.open?1208:932);
    const Rect& owner=field<Rect>(state.owner,0x50);double s=state.scale;
    set_rect(state.toggle,Rect{owner.w-(state.open?306:30)*s,4*s,19*s,19*s});
    // Leave the native 32 px bottom frame untouched, plus 4 px clearance.
    // Owner height includes the 25 px title; panel starts 8 px below it.
    if(state.open)set_rect(state.panel,Rect{owner.w-278*s,8*s,270*s,owner.h-69*s});
    state.layingOut=false;
}
bool bind(Ptr d){
    if(d==state.owner)return state.panel && state.ctrl && state.marker && state.toggle && state.large && state.small;
    state=State{};state.owner=d;++epoch;queuedQuery.clear();
    state.panel=resolve_widget(d,"af_codex_panel",CONTAINER);
    state.marker=resolve_widget(d,"af_codex_marker",0);
    state.toggle=resolve_widget(d,"af_codex_open",BUTTON);
    state.large=resolve_widget(d,"max_dlg_frame",CONTAINER);state.small=resolve_widget(d,"min_dlg_frame",CONTAINER);
    if(state.large && state.small)state.scale=field<Rect>(state.large,0x50).w/932;
    state.ctrl=readable(d,0x550)?field<Ptr>(d,0x548):nullptr;
    return state.panel && state.ctrl && state.marker && state.toggle && state.large && state.small;
}
// Use the native size switch with its CURRENT legend selection. Extending both
// templates preserves its map offsets, left arrow and saved expansion choice.
void resize(bool on){
    double extra=(on?276:-276)*state.scale;
    for(Ptr frame:{state.large,state.small}){Rect r=field<Rect>(frame,0x50);r.w+=extra;set_rect(frame,r);}
    method<void(*)(Ptr,int)>(state.toggle,0x598)(state.toggle,on?1:0);
    native<void(*)(Ptr,int)>(0x7f3f00)(state.owner,field<int>(state.owner,0x738));
    layout();
}
void close(){
    if(!state.open)return;
    clear_hovers();state.visuals.clear();
    state.open=false;state.expires=0;state.location.reset();clear_marks();state.iconPaths.clear();
    ++epoch;queuedQuery.clear();state.events.clear();
    for(const char* name:{"menu","dismiss"}){auto found=state.widgets.find(name);if(found!=state.widgets.end())show(found->second,false);}
    show(state.panel,false);show(state.marker,false);
    resize(false);
}
// target: object id of the current target, or 0 for the item search.
void open(int target){
    Ptr d=dialog(MAP);if(!d || !bind(d))return;
    show(d,true);
    if(!state.open){
        state.open=true;resize(true);show(state.panel,true);
    }
    state.events.clear();state.events.push_back("open:"+std::to_string(target));
}
void clear_browser_marks(){
    state.expires=0;state.location.reset();show(state.marker,false);clear_marks();
    ++epoch;queuedQuery.clear();state.events.clear();
    if(state.open)state.events.push_back("clear-markers");
}
bool linked_market(){return state.marketId && dialog(state.marketId)==state.market;}
// UI-thread handoff. Hiding the host retains its browser document, history,
// applied filters and unsubmitted fields. No URL/session is copied or persisted.
bool browse(int kind,int id,int world){
    if(kind==3){if(state.owner && dialog(MAP)==state.owner)clear_browser_marks();return true;}
    if(kind<0 || kind>2 || (kind==1 && (id<=0 || id>=100000000)) || (kind==2 && (id<100000000 || id>=200000000)))return false;
    Ptr market=nullptr;int marketId=0;
    for(int i=0x20e;i<=0x221;++i){
        Ptr d=dialog(i);const char* name=d?method<const char*(*)(Ptr)>(d,0xa8)(d):nullptr;
        if(name && !std::strcmp(name,"PrivateWarehouse")){market=d;marketId=i;break;}
    }
    Ptr map=dialog(MAP);if(!market || !map || !bind(map))return false;
    state.market=market;state.marketId=marketId;
    open(0);clear_browser_marks();
    state.events.clear();state.events.push_back(kind==0?"open:0":"market:"+std::to_string(kind)+":"+std::to_string(id)+":"+std::to_string(world?1:0));
    show(market,false);return true;
}
bool return_market(){
    if(!linked_market())return false;
    Ptr market=state.market;close();show(market,true);return true;
}
// Invert the map's own affine local-to-world getter, including zoom and inverted
// maps, instead of maintaining a second projection formula.
struct Projection {double ox=0,oy=0,a=0,b=0,c=0,e=0,det=0;bool ok=false;};
Projection projection(){
    Projection p;float origin[4]{},right[4]{},down[4]{};
    auto world=native<void(*)(Ptr,double,double,float*)>(0x7d47a0);
    world(state.ctrl,0,0,origin);world(state.ctrl,64,0,right);world(state.ctrl,0,64,down);
    p.ox=origin[0];p.oy=origin[1];
    p.a=(right[0]-origin[0])/64.0;p.b=(down[0]-origin[0])/64.0;
    p.c=(right[1]-origin[1])/64.0;p.e=(down[1]-origin[1])/64.0;p.det=p.a*p.e-p.b*p.c;
    p.ok=std::isfinite(p.det) && std::abs(p.det)>=1e-12;return p;
}
bool project(const Projection& p,double wx,double wy,double& x,double& y){
    if(!p.ok)return false;
    x=((wx-p.ox)*p.e-p.b*(wy-p.oy))/p.det;
    y=(p.a*(wy-p.oy)-(wx-p.ox)*p.c)/p.det;
    return std::isfinite(x) && std::isfinite(y);
}
bool project(double& x,double& y){return project(projection(),state.x,state.y,x,y);}
void place(Ptr widget,const Projection& p,double wx,double wy,double size,bool wanted,const Rect& view){
    if(!widget)return;
    double x=0,y=0,half=size/2;
    bool visible=wanted && project(p,wx,wy,x,y) && x>=half && y>=half && x<view.w-half && y<view.h-half;
    if(visible){Rect r=local_rect(widget,Rect{view.x+x-half,view.y+y-half,size,size});set_rect(widget,r);}
    show(widget,visible);
}
void place_marker(){
    int world=field<int>(state.ctrl,0x38c),layer=field<int>(state.ctrl,0x390);
    Projection p=projection();Rect view=screen_rect(state.ctrl);
    // Every spawn of the listed creature on the shown map and layer, smaller than the selected one.
    for(size_t i=0;i<state.markWidgets.size();++i){
        bool wanted=i<state.marks.size() && state.marks[i].map==world && state.marks[i].layer==layer;
        place(state.markWidgets[i],p,wanted?state.marks[i].x:0,wanted?state.marks[i].y:0,12*state.scale,wanted,view);
    }
    place(state.marker,p,state.x,state.y,18*state.scale,state.expires && world==state.world && layer==state.layer,view);
}
void clear_marks(){
    state.marks.clear();for(Ptr w:state.markWidgets)show(w,false);
}
// Points arrive as "map,x,y,z;..." from the Lua page; layers use the native where-link lookup.
void set_marks(const char* text){
    clear_marks();if(!text)return;
    if(state.markWidgets.empty())for(size_t i=1;i<=MARKS;++i){
        std::string name="af_codex_marker_"+std::to_string(i);Ptr w=resolve_widget(state.owner,name.c_str(),0);
        if(!w)break;state.markWidgets.push_back(w);
    }
    const char* at=text;
    while(*at && state.marks.size()<state.markWidgets.size()){
        int map=0;float x=0,y=0,z=0;int used=0;
        if(sscanf_s(at,"%d,%f,%f,%f%n",&map,&x,&y,&z,&used)!=4)break;
        float position[3]{x,y,z};
        state.marks.push_back(Mark{map,native<int(*)(Ptr,const float*)>(0x1e4ff0)(game()+0x1325d90,position),x,y});
        at+=used;if(*at==';')++at;else break;
    }
    state.marksDirty=true;
}
// Consumed by the existing native UI event pump, outside the Lua/button callback.
void locate(const Location& location){
    int map=location.map;float x=location.x,y=location.y;
    float position[3]{x,y,location.z};
    int layer=native<int(*)(Ptr,const float*)>(0x1e4ff0)(game()+0x1325d90,position);
    if(field<int>(state.ctrl,0x38c)!=map || field<int>(state.ctrl,0x390)!=layer)
        native<void(*)(Ptr,int,int)>(0x7d65e0)(state.ctrl,map,layer);
    if(field<int>(state.ctrl,0x38c)!=map)return;
    state.world=map;state.layer=field<int>(state.ctrl,0x390);state.x=x;state.y=y;
    double px=0,py=0;
    if(project(px,py)){
        Rect r=screen_rect(state.ctrl);
        field<double>(state.ctrl,0x348)+=px-r.w/2;
        field<double>(state.ctrl,0x340)+=py-r.h/2;
        field<double>(state.ctrl,0x350)=0;field<double>(state.ctrl,0x358)=0;
        native<void(*)(Ptr,int,int,int,double,double)>(0x7d7090)(state.ctrl,0,0,0,0,0);
    }
    state.expires=GetTickCount64()+MARKER_MS;place_marker();state.marksDirty=false;
}
// Commands from the panel page: 0 close the panel, 3 queue one spawn selection.
void action(int command,int map,float x,float y,float z){
    if(!state.open)return;
    if(command==0)close();
    else if(command==3)state.location=Location{map,x,y,z};
}
// Browser event pump tick (native UI thread). Change-driven: dialog pointers and
// the map's pan/zoom/layer fields are compared; nothing is searched per tick.
void tick(){
    static Ptr target=nullptr,map=nullptr;
    Ptr next=dialog(TARGET);
    if(next!=target){target=next;if(target)hook(target);}
    next=dialog(MAP);
    if(next!=map){map=next;if(map!=state.owner){state=State{};++epoch;queuedQuery.clear();}if(map){hook(map);if(bind(map))layout();}}
    if(!map || map!=state.owner || !state.open)return;
    if(!shown(map) || global<Ptr>(PLAYER)==nullptr){close();return;}
    if(!state.visuals.empty())apply_visuals();
    if(state.location){Location location=*state.location;state.location.reset();locate(location);}
    if(state.expires && GetTickCount64()>=state.expires){state.expires=0;show(state.marker,false);}
    if(!state.expires && state.marks.empty())return;
    if(state.marksDirty){state.marksDirty=false;place_marker();}
    double px=field<double>(state.ctrl,0x348),py=field<double>(state.ctrl,0x340);
    double dx=field<double>(state.ctrl,0x350),dy=field<double>(state.ctrl,0x358);
    Rect r=screen_rect(state.ctrl);int zoom=field<int>(state.ctrl,0x370),world=field<int>(state.ctrl,0x38c),layer=field<int>(state.ctrl,0x390);
    if(px!=state.panX || py!=state.panY || dx!=state.dragX || dy!=state.dragY || zoom!=state.zoom || r.w!=state.width || r.h!=state.height || world!=state.shownWorld || layer!=state.shownLayer){
        state.panX=px;state.panY=py;state.dragX=dx;state.dragY=dy;state.zoom=zoom;state.width=r.w;state.height=r.h;state.shownWorld=world;state.shownLayer=layer;place_marker();
    }
}

// These widget operations are the ones used by the installed native Wardrobe:
// text +0x290/+0x298, image factory 0x4dc7b0 and image assignment +0x3b0.
// The factory's two zero offsets are doubles (xmm2/xmm3 at 0x61c3ac).
Ptr widget(const char* name){
    if(!state.open || !name)return nullptr;
    auto found=state.widgets.find(name);if(found!=state.widgets.end())return found->second;
    std::string full="af_codex_";full+=name;
    // Use the client's exact-type recursive lookup for the image and row
    // controls, as the native Wardrobe does. Do not cache a failed lookup.
    int type=!std::strncmp(name,"icon_",5)?0x2021:!std::strncmp(name,"row_",4)?BUTTON:0;
    Ptr result=resolve_widget(state.panel,full.c_str(),type);
    if(result)state.widgets.emplace(name,result);return result;
}
std::wstring wide(const std::string& s){
    int n=MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,s.data(),int(s.size()),nullptr,0);
    std::wstring out(size_t(n),0);if(n)MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,s.data(),int(s.size()),out.data(),n);return out;
}
void label(Ptr w,const char* value){if(w && value){auto line=wide(value);method<void(*)(Ptr,const wchar_t*)>(w,0x290)(w,line.c_str());}}
// Use the native factory's explicit source-size arguments, then transfer its
// picture to the widget. Native assignment/painting owns texture lifetime and
// loading; Codex adds no readiness gate, reload timer or per-frame icon policy.
Ptr picture(Ptr w,const char* path,int side){
    return native<Ptr(*)(const char*,const Rect*,double,double,uint64_t,uint64_t)>(0x4dc7b0)
        (path,&field<Rect>(w,0x50),double(side),double(side),0x30000a,1);
}
void forget(Ptr w){
    state.iconPaths.erase(w);
    state.hovers.erase(std::remove_if(state.hovers.begin(),state.hovers.end(),[&](const Hover& h){return h.widget==w;}),state.hovers.end());
}
void apply_icon(Ptr w,const char* path,int side){
    if(!path || !*path){forget(w);show(w,false);return;}
    auto previous=state.iconPaths.find(w);
    if(previous==state.iconPaths.end() || previous->second!=path){
        Ptr p=picture(w,path,side);if(!p)return;
        method<void(*)(Ptr,int,Ptr)>(w,0x3b0)(w,0,p);
        state.iconPaths[w]=path;
    }
    show(w,true);
}
// Game.dll's native wide string is 40 bytes (proxy, inline buffer, size,
// capacity), unlike this DLL's std::wstring. Use the game's own constructor,
// assignment and destructor, and its original URI parser directly on the row.
// No helper browser, guessed string layout, hover scan or pending queue.
struct NativeTipText {
    alignas(8) unsigned char data[40]{};
    NativeTipText(const wchar_t* value){
        global<void(*)(Ptr)>(0xb7fa28)(data);
        global<void(*)(Ptr,const wchar_t*)>(0xb7fa00)(data,value);
    }
    ~NativeTipText(){global<void(*)(Ptr)>(0xb7fa10)(data);}
};
void clear_hovers(){
    for(const auto& h:state.hovers)field<int>(h.widget,0x220)=0;
    state.hovers.clear();
}
void bind_tooltip(Ptr row,int item){
    std::wstring title=L"nc://aion.ItemInfo/ItemTooltip?item="+std::to_wstring(item)+L"&count=1&enchant_count=0&authorize_count=0";
    NativeTipText text(title.c_str());
    native<void(*)(Ptr,Ptr)>(0x131820)(static_cast<unsigned char*>(row)+0x220,text.data);
    field<int>(row,0x224)=0;
    state.hovers.push_back(Hover{row,item});
}
void apply_visuals(){
    // The normal addon image APIs execute native image creation from this UI
    // command phase, not while Lua builds its data response. One batch per
    // changed page; no image readiness tests or resource reloads during polling.
    auto jobs=std::move(state.visuals);state.visuals.clear();
    for(const auto& job:jobs){
        switch(job.kind){
        case VisualKind::Reset:clear_hovers();break;
        case VisualKind::Icon:apply_icon(job.widget,job.path.c_str(),job.value);break;
        case VisualKind::Tooltip:bind_tooltip(job.widget,job.value);break;
        case VisualKind::Forget:forget(job.widget);break;
        }
    }
}
struct Internet { HINTERNET handle=nullptr;~Internet(){if(handle)WinHttpCloseHandle(handle);} };
Response request(unsigned generation,unsigned sequence,std::string params){
    Response result{generation,sequence,"{\"error\":\"Codex could not connect. Use Search to retry.\"}"};
    Internet transport{WinHttpOpen(L"Aetherfall Codex/1",WINHTTP_ACCESS_TYPE_NO_PROXY,nullptr,nullptr,0)};
    if(!transport.handle)return result;
    WinHttpSetTimeouts(transport.handle,3000,3000,6000,12000);
    Internet connection{WinHttpConnect(transport.handle,L"127.0.0.1",8091,0)};if(!connection.handle)return result;
    std::wstring path=L"/market/codex/state?"+wide(params);
    Internet query{WinHttpOpenRequest(connection.handle,L"GET",path.c_str(),nullptr,WINHTTP_NO_REFERER,WINHTTP_DEFAULT_ACCEPT_TYPES,0)};
    if(!query.handle)return result;
    DWORD redirects=WINHTTP_OPTION_REDIRECT_POLICY_NEVER;WinHttpSetOption(query.handle,WINHTTP_OPTION_REDIRECT_POLICY,&redirects,sizeof redirects);
    if(!WinHttpSendRequest(query.handle,nullptr,0,nullptr,0,0,0) || !WinHttpReceiveResponse(query.handle,nullptr))return result;
    std::string body;char buffer[16384];DWORD got=0;
    do{
        if(!WinHttpReadData(query.handle,buffer,sizeof buffer,&got) || body.size()+got>1048576)return result;
        body.append(buffer,got);
    }while(got);
    if(!body.empty())result.body=std::move(body);return result;
}
int ui(Ptr L){
    const char* operation=text(L,2);if(!operation)return nil(L);
    if(!std::strcmp(operation,"poll")){
        if(state.owner && state.large && field<Rect>(state.large,0x50).w/(state.open?1208:932)!=state.scale)layout();
        std::string response;unsigned sequence=0;
        if(pending.valid() && pending.wait_for(std::chrono::seconds(0))==std::future_status::ready){
            auto result=pending.get();if(result.epoch==epoch && state.open){sequence=result.sequence;response=std::move(result.body);}
        }
        if(state.open && !queuedQuery.empty() && !pending.valid()){
            pending=std::async(std::launch::async,request,epoch,queuedSequence,std::move(queuedQuery));queuedQuery.clear();
        }
        boolean(L,state.open);
        if(state.events.empty())nil(L);else{push(L,state.events.front().c_str());state.events.pop_front();}
        push(L,double(sequence));if(response.empty())nil(L);else push(L,response.c_str(),response.size());
        push(L,state.scale);boolean(L,linked_market());return 6;
    }
    if(!state.open)return nil(L);
    if(!std::strcmp(operation,"market"))return boolean(L,return_market());
    if(!std::strcmp(operation,"request")){
        const char* params=text(L,4);if(!params || text_length(L,4)>2048)return boolean(L,false);
        queuedSequence=unsigned(number(L,3));queuedQuery=params;return boolean(L,true);
    }
    if(!std::strcmp(operation,"close")){close();return boolean(L,true);}
    if(!std::strcmp(operation,"clear")){state.expires=0;state.location.reset();show(state.marker,false);clear_marks();state.visuals.clear();state.visuals.push_back(Visual{VisualKind::Reset});return boolean(L,true);}
    if(!std::strcmp(operation,"rows")){state.visuals.clear();state.visuals.push_back(Visual{VisualKind::Reset});return boolean(L,true);}
    if(!std::strcmp(operation,"marks")){if(text_length(L,3)>8192)return boolean(L,false);set_marks(text(L,3));return boolean(L,true);}
    if(!std::strcmp(operation,"locate")){
        action(3,int(number(L,3)),float(number(L,4)),float(number(L,5)),float(number(L,6)));return boolean(L,true);
    }
    Ptr w=widget(text(L,3));if(!w)return nil(L);
    if(!std::strcmp(operation,"text")){label(w,text(L,4));return boolean(L,true);}
    if(!std::strcmp(operation,"read"))return push(L,method<const wchar_t*(*)(Ptr)>(w,0x298)(w),100);
    if(!std::strcmp(operation,"show")){
        bool on=number(L,4)!=0;
        if(!on)state.visuals.push_back(Visual{VisualKind::Forget,w});
        show(w,on);return boolean(L,true);
    }
    if(!std::strcmp(operation,"rect")){
        double s=state.scale;Rect r{number(L,4)*s,number(L,5)*s,number(L,6)*s,number(L,7)*s};set_rect(w,r);return boolean(L,true);
    }
    if(!std::strcmp(operation,"hover")){
        int item=int(number(L,4));
        if(item>=100000000 && item<200000000)state.visuals.push_back(Visual{VisualKind::Tooltip,w,{},item});
        return boolean(L,true);
    }
    if(!std::strcmp(operation,"icon")){const char* path=text(L,4);state.visuals.push_back(Visual{VisualKind::Icon,w,path?path:"",int(number(L,5,40))});return boolean(L,true);}
    return nil(L);
}
}
void codex_frame_changed(Ptr self){if(self==codex::state.owner)codex::layout();}
int codex_command(Ptr self,Ptr event){
    Ptr button=event?*static_cast<Ptr*>(event):nullptr;
    const char* name=button?method<const char*(*)(Ptr)>(button,0xa8)(button):nullptr;
    if(name && !std::strcmp(name,"af_codex_open")){
        if(self==dialog(codex::TARGET)){if(unit_record("target"))codex::open(field<int>(record,0));}
        else if(codex::state.open && self==codex::state.owner)codex::close();
        else codex::open(0);
        return 1;
    }
    if(name && !std::strncmp(name,"af_codex_",9)){
        if(!std::strcmp(name,"af_codex_close"))codex::close();
        else codex::state.events.emplace_back(name+9);
        return 1;
    }
    return reinterpret_cast<CommandFn>(own_table(self)->original[START_COMMAND/8])(self,event);
}
