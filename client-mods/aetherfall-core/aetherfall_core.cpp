#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstddef>
#include <cstdlib>
#include <cstdio>
#include <cstring>
#include <cwchar>
#pragma comment(lib, "user32.lib")  // cursor and button state for addon dragging ("cursor")

// Aion 4.8 NA x64 Lua bridge for the Aetherfall Interface. Data commands read
// engine state that native windows already display and push Lua values. The
// only writes are the interface's own window placement: "move" (native SetRect,
// as the loot-roll bridge positions its dialogs), "pin" (keeps a moved dialog's or
// start-menu button's spot when the game re-places it; the start menu's own buttons and
// mouse-through gaps) and "setshown" (native show/hide). "save"/"read" store bounded
// settings files in Mods/AetherfallUI, because this client's Lua io library has no
// write. "command" runs one chat command the player could type (the Broker button's
// "/say .broker"), as the start-menu entries do. Nothing writes game state. Called only
// from the client's Lua states on the native UI thread. Quest target hooks also
// decorate native name/tooltip formatter outputs; actor names and quest state
// remain native and unchanged.
namespace {
using Ptr=void*;
constexpr char CORE_VERSION[]="1.2.77";  // checked against the normal builder's NATIVE_VERSION
constexpr uintptr_t PREFERRED=0x10000000;  // Game.dll has no relocations.
unsigned char* image=nullptr;
unsigned char* game(){return image;}
template<class T>T& field(Ptr p,size_t offset){return *reinterpret_cast<T*>(static_cast<unsigned char*>(p)+offset);}
template<class T>T& global(size_t rva){return *reinterpret_cast<T*>(game()+rva);}
template<class T>T method(Ptr p,size_t slot){return reinterpret_cast<T>((*reinterpret_cast<void***>(p))[slot/8]);}
template<class T>T native(size_t rva){return reinterpret_cast<T>(game()+rva);}

// Static addresses (Game.dll RVAs); evidence in docs/AETHERFALL_UI_ELVUI_MAP.md.
constexpr size_t LUA_TABLE_PREVIOUS=0xbad1d0, LUA_TABLE_DUPLICATE=0xbad1e0;  // two identical UnregisterPredefinedQuestIndicator rows
constexpr size_t LUA_DUPLICATE_FN=0x606ed0, LUA_NEWLSTR=0xd3070, LUA_GC_STEP=0xb9860;
constexpr size_t PLAYER=0x12eda48, ITEM_TREE=0xa98;  // bag inventory map (native GetInventoryItemCount 0x613420)
constexpr size_t EXP_MAX=0x1318e98, EXP_RECOVERABLE=0x1318ea0, EXP_CURRENT=0x1318ea8;
constexpr size_t REPOSE_CURRENT=0x1318f38, REPOSE_MAX=0x1318f40;
constexpr size_t INFO=0x1319618, PLAYER_AURAS=0x17ad30, TARGET_RECORD=0x17bd80, TARGET_TARGET_RECORD=0x17c4c0;
constexpr size_t SKILL_TEMPLATE=0xa65020, STRINGS_BEGIN=0xed13f0, STRINGS_END=0xed13f8;
constexpr size_t DIALOGS=0x13875c0, DIALOG_LIMIT=0x224, SYSTEM=0x1311088, PLUGIN_LAST=0x221;
constexpr size_t CHAT_COMMAND=0x628270;  // runs one plugin slash command (market-shortcut buttons)
// The chat box's own submit (Game.dll 0x680ab0: chat input dialog, wide text), as typing a line and
// pressing Enter: built-in commands such as /say (the server's ".broker"), then plugin slash commands.
constexpr size_t CHAT_SUBMIT=0x680ab0, CHAT_INPUT_ID=0xcf;
constexpr size_t CURSOR_X=0x1379478, CURSOR_Y=0x137947c;  // mouse in UI pixels (the dispatcher's hit point)
constexpr size_t UI_SCALE=0x1378ec8, UI_WIDTH=0x1378ea8, UI_HEIGHT=0x1378eb0;  // loot bridge layout globals
constexpr int KINAH=182400001, AURA_LIMIT=0x70;

#pragma pack(push,1)
struct Aura { int32_t first,skill,slot,time; int16_t level; };  // native 18-byte effect head
#pragma pack(pop)
static_assert(sizeof(Aura)==18,"native aura record");

// Lua 5.1 x64 layout used by the client's own registered functions
// (UnitHP/UnitName push by writing L->top directly).
struct Value { union { double n; Ptr p; int b; } v; int tt; int pad; };
static_assert(sizeof(Value)==16,"Lua TValue");
Value*& top(Ptr L){return field<Value*>(L,0x10);}
Value* arg(Ptr L,int i){Value* b=field<Value*>(L,0x18)+(i-1);return b<top(L)?b:nullptr;}
const char* text(Ptr L,int i){Value* a=arg(L,i);return a && a->tt==4?static_cast<const char*>(a->v.p)+0x18:nullptr;}
size_t text_length(Ptr L,int i){Value* a=arg(L,i);return a && a->tt==4?field<size_t>(a->v.p,0x10):0;}
double number(Ptr L,int i,double fallback=0){Value* a=arg(L,i);return a && a->tt==3?a->v.n:fallback;}
int push(Ptr L,double n){Value* t=top(L);t->v.p=nullptr;t->v.n=n;t->tt=3;top(L)=t+1;return 1;}
int nil(Ptr L){Value* t=top(L);t->v.p=nullptr;t->tt=0;top(L)=t+1;return 1;}
int boolean(Ptr L,bool b){Value* t=top(L);t->v.p=nullptr;t->v.b=b;t->tt=1;top(L)=t+1;return 1;}
int push(Ptr L,const char* s,size_t n){
    if(!s)return nil(L);
    auto g=field<unsigned char*>(L,0x20);
    if(field<size_t>(g,0x78)>=field<size_t>(g,0x70))native<void(*)(Ptr)>(LUA_GC_STEP)(L);
    Ptr string=native<Ptr(*)(Ptr,const char*,size_t)>(LUA_NEWLSTR)(L,s,n);
    Value* t=top(L);t->v.p=string;t->tt=4;top(L)=t+1;return 1;
}
int push(Ptr L,const char* s){return s?push(L,s,std::strlen(s)):nil(L);}
int push(Ptr L,const wchar_t* w,size_t limit=256){
    if(!w)return nil(L);
    char utf8[1024];int n=WideCharToMultiByte(CP_UTF8,0,w,int(wcsnlen(w,limit)),utf8,sizeof utf8,nullptr,nullptr);
    return n>0?push(L,utf8,size_t(n)):push(L,"",0);
}

bool readable(const void* p,size_t n){
    MEMORY_BASIC_INFORMATION m{};
    if(!p || !VirtualQuery(p,&m,sizeof m) || m.State!=MEM_COMMIT || (m.Protect&(PAGE_GUARD|PAGE_NOACCESS)))return false;
    return static_cast<const unsigned char*>(p)+n<=static_cast<unsigned char*>(m.BaseAddress)+m.RegionSize;
}

#include "quest_targets.h"
#include "camera_turn.h"

// Native string table: sorted 20-byte rows {int id, ..., const wchar_t* at +0xc}
// (the lookup GetUnlearnedSkillList inlines with bsearch and comparator 0xa873d0).
const wchar_t* localized(int id){
    auto begin=global<unsigned char*>(STRINGS_BEGIN),end=global<unsigned char*>(STRINGS_END);
    if(!id || !begin || end<=begin)return nullptr;
    size_t lo=0,hi=size_t(end-begin)/20;
    while(lo<hi){
        size_t mid=(lo+hi)/2;int key=*reinterpret_cast<int*>(begin+mid*20);
        if(key==id){auto s=*reinterpret_cast<const wchar_t**>(begin+mid*20+0xc);return readable(s,2)?s:nullptr;}
        if(key<id)lo=mid+1;else hi=mid;
    }
    return nullptr;
}
const wchar_t* skill_name(int skill){
    if(skill<=0)return nullptr;
    Ptr t=native<Ptr(*)(Ptr,int)>(SKILL_TEMPLATE)(nullptr,skill);
    return t?localized(field<int>(t,0)):nullptr;
}

// Engine snapshots, filled by the same getters the native HUD dialogs use.
alignas(16) unsigned char auras[0x800], record[0x1000];
size_t record_getter(const char* unit){
    return !unit?0:!std::strcmp(unit,"target")?TARGET_RECORD:!std::strcmp(unit,"targettarget")?TARGET_TARGET_RECORD:0;
}
bool unit_record(const char* unit){
    size_t getter=record_getter(unit);if(!getter)return false;
    std::memset(record,0,sizeof record);
    native<void(*)(Ptr,Ptr)>(getter)(game()+INFO,record);
    return field<int>(record,0)!=0;
}
int aura_list(const char* unit,const Aura*& list){
    list=nullptr;
    if(unit && !std::strcmp(unit,"player")){
        if(!global<Ptr>(PLAYER))return -1;
        std::memset(auras,0,sizeof auras);
        native<void(*)(Ptr,Ptr)>(PLAYER_AURAS)(game()+INFO,auras);
        int count=field<int>(auras,0x7e0);
        if(count<0 || count>AURA_LIMIT)return -1;
        list=reinterpret_cast<const Aura*>(auras);return count;
    }
    if(!unit_record(unit))return -1;
    int count=field<int>(record,0x884);
    if(count<0 || count>AURA_LIMIT)return -1;
    list=reinterpret_cast<const Aura*>(record+0xa4);return count;
}

// MSVC std::map<object id, Item*> of the bag inventory; Kinah is item 182400001.
// Mirrors GetInventoryItemCount: items flagged at +0x6bc are not counted.
long long kinah(){
    Ptr player=global<Ptr>(PLAYER);if(!player)return -1;
    Ptr head=field<Ptr>(player,ITEM_TREE);if(!head)return -1;
    Ptr node=field<Ptr>(head,0);
    for(int guard=0;node && node!=head && guard<8192;++guard){
        Ptr item=field<Ptr>(node,0x20);
        if(item && field<int>(item,0xc)==KINAH && !field<int>(item,0x6bc))return field<long long>(item,0x10);
        Ptr right=field<Ptr>(node,0x10);
        if(!field<char>(right,0x29)){node=right;while(!field<char>(field<Ptr>(node,0),0x29))node=field<Ptr>(node,0);}
        else{Ptr parent=field<Ptr>(node,8);while(!field<char>(parent,0x29) && node==field<Ptr>(parent,0x10)){node=parent;parent=field<Ptr>(node,8);}node=parent;}
    }
    return 0;
}

// Native dialogs/widgets: dialog table indexed by type id; widget lookup is the
// dialog's own recursive find(name, widget type) (vtable +0x338).
struct Rect { double x,y,w,h; };
constexpr int ITEMVIEW=0x2011,STATIC=0x2022,GAUGE=0x200e;
Ptr dialog(int id){return id>0 && id<=int(DIALOG_LIMIT)?global<Ptr>(DIALOGS+size_t(id)*8):nullptr;}
bool shown(Ptr w){return w && (method<int(*)(Ptr)>(w,0xb8)(w)&1);}
// The local shown flag does not include hidden parent containers/dialogs.
bool effectively_shown(Ptr w){
    for(int depth=0;w && depth<128;++depth){
        if(!shown(w))return false;
        w=field<Ptr>(w,0x290);
        if(!w)return true;
    }
    return false;
}
Rect screen_rect(Ptr w){
    Rect r=field<Rect>(w,0x50);
    Ptr parent=field<Ptr>(w,0x290);
    if(parent){
        // Same read-only conversion as the native widget's +0x58 rectangle
        // calculation (0x5aaaf0). Includes every container and native offset;
        // unlike that getter, does not update render caches or invoke painting.
        size_t helper=method<int(*)(Ptr)>(w,0xb8)(w)&0x40?0x5b4660:0x5b4580;
        native<void(*)(Ptr,double*,double*)>(helper)(parent,&r.x,&r.y);
    }
    return r;
}
// Invert the same parent/content/scroll conversion used by native painting.
// A screen position is not necessarily the object's stored +0x50 position.
Rect local_rect(Ptr w,Rect screen){
    if(!w)return screen;  // synthetic tooltip groups have no single widget
    Rect at=screen_rect(w),local=field<Rect>(w,0x50);
    screen.x-=at.x-local.x;screen.y-=at.y-local.y;
    return screen;
}
bool is_kind(const char* a,const char* b){return !std::strcmp(a,b);}
// Native FindWidget compares exact type ids (5ae37f..5ae384); zero is
// not a wildcard. Traverse the same child list for explicitly untyped lookups.
Ptr find_any(Ptr parent,const char* name,int depth,int& budget){
    if(!parent || depth>24 || --budget<0 || !readable(parent,0x2a8))return nullptr;
    Ptr head=field<Ptr>(parent,0x2a0);
    if(!head || !readable(head,0x18))return nullptr;
    Ptr node=field<Ptr>(head,0);
    for(int count=0;node && node!=head;++count){
        if(count>=512 || budget<=0 || !readable(node,0x18))return nullptr;
        Ptr entry=field<Ptr>(node,0x10);
        if(entry && readable(entry,0x2a8)){
            const char* found=method<const char*(*)(Ptr)>(entry,0xa8)(entry);
            if(found && !std::strcmp(found,name))return entry;
            if(Ptr result=find_any(entry,name,depth+1,budget))return result;
        }
        node=field<Ptr>(node,0);
    }
    return nullptr;
}
Ptr child(Ptr parent,int n);
Ptr direct_child(Ptr parent,const char* name){
    if(!parent || !readable(parent,0x2a8))return nullptr;
    Ptr head=field<Ptr>(parent,0x2a0);
    if(!head || !readable(head,0x18))return nullptr;
    Ptr node=field<Ptr>(head,0);Ptr result=nullptr;
    for(int count=0;node && node!=head && count<2048;++count){
        if(!readable(node,0x18))return nullptr;
        Ptr entry=field<Ptr>(node,0x10);
        if(entry && readable(entry,0x2a8)){
            const char* found=method<const char*(*)(Ptr)>(entry,0xa8)(entry);
            if(found && !std::strcmp(found,name)){
                if(result)return nullptr;
                result=entry;
            }
        }
        node=field<Ptr>(node,0);
    }
    return result;
}
// One resolver for geometry, placement, inspection and sparse hit testing.
// A plain name retains native recursive lookup. Paths select direct children;
// ordinal segments address unnamed containers without pretending they have names.
Ptr resolve_widget(Ptr parent,const char* path,int type){
    if(!parent || !path || !*path)return parent;
    if(path[0]!='#' && !std::strchr(path,'/')){
        if(type)return method<Ptr(*)(Ptr,const char*,int)>(parent,0x338)(parent,path,type);
        int budget=2048;return find_any(parent,path,0,budget);
    }
    for(int depth=0;depth<24 && *path;++depth){
        const char* slash=std::strchr(path,'/');size_t length=slash?size_t(slash-path):std::strlen(path);
        if(!length || length>=128)return nullptr;
        char segment[128]{};std::memcpy(segment,path,length);
        Ptr next=nullptr;
        if(segment[0]=='#'){
            char* end=nullptr;long index=std::strtol(segment+1,&end,10);
            if(end==segment+1 || *end || index<1 || index>2048)return nullptr;
            next=child(parent,int(index));
        }else next=direct_child(parent,segment);
        if(!next)return nullptr;
        parent=next;
        if(!slash)return !type || method<int(*)(Ptr)>(parent,0x10)(parent)==type?parent:nullptr;
        path=slash+1;if(!*path)return nullptr;
    }
    return nullptr;
}
Ptr widget(int id,const char* name,int type){
    Ptr d=dialog(id);if(!d || !name || !*name)return nullptr;
    return resolve_widget(d,name,type);
}

// Pinned windows. The game re-places some HUD dialogs itself before Lua hears of it: the
// target re-centres at the top of the screen on every target change (Game.dll 0x8bd2c8,
// 0x8bd349, 0x8bd3ad: x from the screen width, y from its group container), so a moved
// target was drawn there for a frame; the market-shortcut mod re-places start-menu buttons
// every frame. Dispatch through guarded native class slots while keeping each
// object's original class pointer: the maintained inventory layout and native code
// compare that pointer for concrete-class admission. Policies remain per object;
// other instances delegate unchanged to the saved native methods.
constexpr size_t SET_RECT=0x1a8, VTABLE_SLOTS=1024;  // the dialog vtables have about 620 slots
// The start menu (dlg_start, vtable RVA 0xcea9e8): +0x418 is its "point inside" test (Game.dll
// 0x53f054, the dispatcher skips a window and its buttons when it answers no) and +0x490 its
// button command handler (0x8b5ea0, which the market-shortcut mod extends).
constexpr size_t START_VTABLE=0xcea9e8, HIT_TEST=0x418, START_COMMAND=0x490;
constexpr int BUTTON=0x2001,CONTAINER=0x2008,FLYGAUGE=0x200d;  // widget types (vtable +0x10)
using SetRectFn=uintptr_t(*)(Ptr,const Rect*);
// Pin modes: 0 top-left at x,y; 1 top-right, 2 bottom-left, 3 bottom-right corner at x,y
// (tooltips grow away from an anchor whatever their size); 4 beside the mouse, x,y added (the
// tooltip's cursor anchor; it flips to the other side at the screen edge); 5 kept off screen.
// clamp keeps the window inside the screen; w,h
// (when above 0) replace the game's size. A widget's spot is relative to its dialog.
// Mode 6 (tooltips): decided when the game places it: the "ui" spot (umode, ux, uy) while the mouse
// is over a game window, else the world spot (wmode, x, y); a mode below 0 leaves it where the game put it.
struct Pin { int id; char widget[48]; int type; Ptr target; Ptr owner; double x,y,w,h; int mode; bool clamp; unsigned calls,retargets;
             int wmode,umode; double ux,uy; void** native_class; void** owner_class; };
bool pin_live(const Pin& p);
struct Table { void** native; void** original; void* copy[VTABLE_SLOTS+1]; int next; bool ready; size_t slots; };
Pin pins[192];int pin_count=0;
struct Refusal { int id; char widget[48]; const char* why; };
Refusal refusals[32];int refusal_next=0;
void refuse(int id,const char* name,const char* why){
    for(auto& r:refusals)if(r.id==id && !std::strcmp(r.widget,name)){r.why=why;return;}
    Refusal& r=refusals[refusal_next++%32];r.id=id;std::memcpy(r.widget,name,std::strlen(name)+1);r.why=why;
}
void accept(int id,const char* name){for(auto& r:refusals)if(r.id==id && !std::strcmp(r.widget,name))r.id=0;}
constexpr int TABLE_LIMIT=512;  // bounded cache for native HUD pins and backdrop classes across a session
Table tables[TABLE_LIMIT];int table_count=0;
constexpr size_t TABLE_BUCKETS=1024;
int table_buckets[TABLE_BUCKETS]{};
size_t table_bucket(void** vt){return (reinterpret_cast<uintptr_t>(vt)>>3)%TABLE_BUCKETS;}
constexpr size_t DRAW_SKIN=0x3c8,DRAW_SKIN_ABSOLUTE=0x3d0,DRAW_SKIN_BYTES=0x3d8,DRAW_SKIN_BYTES_ABSOLUTE=0x3e0;
using DrawSkinFn=void(*)(Ptr,int,const float*,float,float,Ptr);
using DrawSkinBytesFn=void(*)(Ptr,int,Ptr,unsigned char,unsigned char,unsigned char,unsigned char,float,float);
void backdrop_draw(Ptr,int,const float*,float,float,Ptr);
void backdrop_draw_absolute(Ptr,int,const float*,float,float,Ptr);
void backdrop_draw_bytes(Ptr,int,Ptr,unsigned char,unsigned char,unsigned char,unsigned char,float,float);
void backdrop_draw_bytes_absolute(Ptr,int,Ptr,unsigned char,unsigned char,unsigned char,unsigned char,float,float);
const char* hook_refusal="";  // why the last hook() failed (for pininfo)
bool gaps[DIALOG_LIMIT+1];  // dialogs whose empty space lets the mouse through (start menu)
Pin* find_pin(int id,const char* name){
    for(int i=0;i<pin_count;++i)if(pins[i].id==id && !std::strcmp(pins[i].widget,name))return &pins[i];
    return nullptr;
}
Table* own_table(Ptr d){
    void** vt=*static_cast<void***>(d);
    for(int link=table_buckets[table_bucket(vt)];link;link=tables[link-1].next)
        if(tables[link-1].native==vt)return &tables[link-1];
    return nullptr;
}
#include "tooltip_classes.h"
constexpr size_t TOOLTIP_CLASS_COUNT=sizeof TOOLTIP_CLASSES/sizeof TOOLTIP_CLASSES[0];
struct TooltipHook { void** slot; SetRectFn original; };
TooltipHook tooltip_hooks[TOOLTIP_CLASS_COUNT]{};
Pin tooltip_policy{};
bool tooltip_enabled=false;
unsigned tooltip_calls=0;
using HitTestFn=int(*)(Ptr,double,double,int,int);
const TooltipClass* tooltip_class(Ptr d){
    if(!d)return nullptr;
    void** vt=*static_cast<void***>(d);
    if(Table* t=own_table(d))vt=t->native;
    size_t rva=reinterpret_cast<uintptr_t>(vt)-reinterpret_cast<uintptr_t>(game());
    size_t lo=0,hi=TOOLTIP_CLASS_COUNT;
    while(lo<hi){size_t mid=(lo+hi)/2;if(TOOLTIP_CLASSES[mid].vtable<rva)lo=mid+1;else hi=mid;}
    return lo<TOOLTIP_CLASS_COUNT && TOOLTIP_CLASSES[lo].vtable==rva?&TOOLTIP_CLASSES[lo]:nullptr;
}
bool over_ui(Ptr self);
// The mouse in UI pixels. The engine's dispatcher copy (CURSOR_X/Y) comes from
// window mouse messages, which Windows keeps reporting from the last visible
// cursor position while the system cursor is hidden; only the version.dll camera
// fix corrects them. Read the real cursor, as "cursor" does for MoveUI dragging,
// and keep the engine copy when the game is not in front.
void cursor_ui(double& x,double& y){
    x=global<int>(CURSOR_X);y=global<int>(CURSOR_Y);
    HWND hwnd=GetForegroundWindow();DWORD pid=0;
    if(!hwnd || !GetWindowThreadProcessId(hwnd,&pid) || pid!=GetCurrentProcessId())return;
    POINT p{};RECT client{};
    if(!GetCursorPos(&p) || !ScreenToClient(hwnd,&p) || !GetClientRect(hwnd,&client) || client.right<=0 || client.bottom<=0)return;
    double width=global<double>(UI_WIDTH),height=global<double>(UI_HEIGHT);
    if(width<=0 || height<=0)return;
    x=p.x*width/client.right;y=p.y*height/client.bottom;
}
Rect placed(const Pin& given,Rect p){
    Pin pin=given;
    if(pin.mode==6){
        bool ui=over_ui(pin.target);
        int m=ui?pin.umode:pin.wmode;
        if(m<0)return p;  // native
        pin.mode=m;
        if(ui){pin.x=pin.ux;pin.y=pin.uy;}
    }
    if(pin.w>0 && pin.h>0){p.w=pin.w;p.h=pin.h;}
    double width=global<double>(UI_WIDTH),height=global<double>(UI_HEIGHT);
    if(pin.mode==4){
        double gap=16*global<double>(UI_SCALE),cx,cy;cursor_ui(cx,cy);
        p.x=cx+gap+pin.x;p.y=cy+gap+pin.y;
        if(width>0 && p.x+p.w>width)p.x=cx-gap-p.w-pin.x;
        if(height>0 && p.y+p.h>height)p.y=cy-gap-p.h-pin.y;
    }
    else if(pin.mode==5){p.x=-20000;p.y=-20000;}
    else if(pin.mode==7){}  // size only: the game, the player or a move keeps the position
    else{p.x=pin.mode&1?pin.x-p.w:pin.x;p.y=pin.mode&2?pin.y-p.h:pin.y;}
    // Minimise/focus transitions expose temporary viewport dimensions. They
    // must not drag a held bar to that temporary bottom/right edge.
    DWORD foreground=0;GetWindowThreadProcessId(GetForegroundWindow(),&foreground);
    if(pin.clamp && pin.mode!=5 && foreground==GetCurrentProcessId() && width>=800 && height>=600){
        p.x=std::max(0.0,std::min(width-p.w,p.x));p.y=std::max(0.0,std::min(height-p.h,p.y));
    }
    if(pin.widget[0] && pin.owner){
        // Widget pins are relative to their dialog's visible origin, including
        // widgets inside containers. Native parent offsets can change on focus
        // return; using x/y as raw local coordinates separates art and hit areas.
        // Subtract the owner origin before conversion so a parent SetRect that
        // is still laying out its children does not introduce its pending move.
        Rect owner=screen_rect(pin.owner);
        p.x+=owner.x;p.y+=owner.y;
    }
    return local_rect(pin.target,p);
}
uintptr_t pinned_set_rect(Ptr self,const Rect* r){
    Table* t=own_table(self);
    auto original=reinterpret_cast<SetRectFn>(t->original[SET_RECT/8]);
    for(int i=0;r && i<pin_count;++i){
        if(pins[i].target!=self || !pin_live(pins[i]))continue;
        ++pins[i].calls;
        Rect p=placed(pins[i],*r);
        return original(self,&p);
    }
    return original(self,r);
}
// DlgTargetBuffStatus recalculates its anchor INSIDE SetRect, through +0x478
// (Game.dll 0x654f60). Holding only the incoming SetRect loses to that later
// calculation. Keep its native layout, then apply the held canvas origin.
using AdjustRectFn=uintptr_t(*)(Ptr,Rect*);
constexpr size_t AURA_VTABLE=0xc7f668, ADJUST_RECT=0x478, AURA_ADJUST=0x654f60;
// These verified 4.8 classes overwrite x/y inside UIWidget::SetRect as well.
// Holding its input alone cannot override their later native anchor calculation.
size_t anchor_adjust(unsigned char* vt){
    if(vt==game()+AURA_VTABLE)return AURA_ADJUST;
    if(vt==game()+0xcea368)return 0x8bdf20; // DlgTargetAssist
    if(vt==game()+0xce9ce8)return 0x8bc630; // DlgTarget
    if(vt==game()+0xce6fb8)return 0x8a2d00; // DlgTargetDelay
    if(vt==game()+0xc69998)return 0x8a2f30; // DlgTargetAssistDelay
    return 0;
}
uintptr_t pinned_aura_adjust(Ptr self,Rect* r){
    Table* t=own_table(self);
    auto result=reinterpret_cast<AdjustRectFn>(t->original[ADJUST_RECT/8])(self,r);
    for(int i=0;r && i<pin_count;++i)if(pins[i].target==self && !*pins[i].widget && pin_live(pins[i])){
        *r=placed(pins[i],*r);break;
    }
    return result;
}
// The named tooltip_proto windows are layout templates. The visible outer window is
// a freshly-created DlgTooltip* (normally slots 0x1ec..0x1ee). Its native placement
// reaches SetRect at 0x59e700, before it is shown. Hook only these verified classes:
// a per-object hook installed on the next Lua tick misses the first rendered frame.
// Keeping the templates untouched also preserves their relative text/item layout.
bool tooltip_group_busy=false;
bool item_tooltip(const TooltipClass* cls){
    return cls && (cls->vtable==0xd0e508 || cls->vtable==0xd0ebe8 || cls->vtable==0xd0f2c8);
}
bool map_hover(){
    // DlgNewMap occupies native slot 0xe5 (map code 0x7d23aa etc.).
    // Require its verified class and real hit region; other windows using the
    // slot, a closed map, and world hovers must not change tooltip behavior.
    Ptr d=dialog(0xe5);
    if(!d)return false;
    void** vt=*static_cast<void***>(d);
    if(Table* t=own_table(d))vt=t->native;
    if(reinterpret_cast<uintptr_t>(vt)!=reinterpret_cast<uintptr_t>(game()+0xccfb88) || !shown(d))return false;
    double x,y;cursor_ui(x,y);
    const Rect& r=field<Rect>(d,0x50);
    return x>=r.x && y>=r.y && x<=r.x+r.w && y<=r.y+r.h
        && method<HitTestFn>(d,HIT_TEST)(d,x,y,0,0)!=0;
}
bool place_map_tooltips(Ptr self,const Rect& requested,uintptr_t& result){
    // Native text tooltips may coexist in 0x1ec and the alternate slot 0x1ef
    // (0x8e4fc0/0x8ec290). Applying the same cursor/mover position to each
    // independently collapses the map's hover panels. Lay out the visible UI
    // tooltip group once, including an incoming panel before its first Show.
    const auto current=tooltip_class(self);
    if(!current || current->context || item_tooltip(current) || !map_hover())return false;
    Pin policy=tooltip_policy;policy.mode=policy.umode;policy.x=policy.ux;policy.y=policy.uy;
    if(policy.mode<0 || policy.mode==5)return false;
    Ptr members[4]{};const TooltipClass* classes[4]{};Rect rects[4]{};
    int count=0,selected=-1;
    for(int id=0x1ec;id<=0x1ef;++id){
        Ptr d=dialog(id);const auto cls=tooltip_class(d);
        if(!cls || cls->context || item_tooltip(cls) || (d!=self && !shown(d)))continue;
        Rect r=d==self?requested:field<Rect>(d,0x50);
        if(!std::isfinite(r.w) || !std::isfinite(r.h) || r.w<=0 || r.h<=0)continue;
        if(d==self)selected=count;
        members[count]=d;classes[count]=cls;rects[count++]=r;
    }
    if(count<2 || selected<0)return false;
    const double width=global<double>(UI_WIDTH),height=global<double>(UI_HEIGHT);
    const double gap=5*global<double>(UI_SCALE);
    if(width<=0 || height<=0 || gap<=0)return false;
    double row=gap*(count-1),column=row,maxw=0,maxh=0;
    for(int i=0;i<count;++i){row+=rects[i].w;column+=rects[i].h;maxw=std::max(maxw,rects[i].w);maxh=std::max(maxh,rects[i].h);}
    // Prefer adjacent columns. At a narrow viewport stack vertically if that
    // fits; do not shrink native content or change the user's anchor policy.
    const bool vertical=row>width && column<=height;
    Rect group=rects[0];group.w=vertical?maxw:row;group.h=vertical?column:maxh;
    group=placed(policy,group);
    double x=group.x,y=group.y;
    for(int i=0;i<count;++i){
        rects[i].x=x;rects[i].y=y;
        if(vertical)y+=rects[i].h+gap;else x+=rects[i].w+gap;
    }
    tooltip_group_busy=true;
    for(int i=0;i<count;++i){
        auto original=tooltip_hooks[classes[i]-TOOLTIP_CLASSES].original;
        const uintptr_t placed_result=original(members[i],&rects[i]);
        if(i==selected)result=placed_result;
    }
    tooltip_group_busy=false;
    return true;
}
bool place_item_comparison(Ptr self,const Rect& requested,uintptr_t& result){
    // Native comparison layout (0x8ee150): primary slot 0x1ec, equipped items
    // 0x1ed/0x1ee, 5*scale horizontal gaps and 20*scale vertical inset. Anchor
    // this group once; anchoring each member separately collapses the columns.
    Ptr members[3]{};const TooltipClass* classes[3]{};Rect rects[3]{};
    int count=0,selected=-1;
    for(int id=0x1ec;id<=0x1ee;++id){
        Ptr d=dialog(id);const auto cls=tooltip_class(d);
        if(!item_tooltip(cls)){if(id==0x1ec)return false;continue;}
        Rect r=d==self?requested:field<Rect>(d,0x50);
        if(!std::isfinite(r.w) || !std::isfinite(r.h) || r.w<=0 || r.h<=0){if(id==0x1ec)return false;continue;}
        if(d==self)selected=count;
        members[count]=d;classes[count]=cls;rects[count++]=r;
    }
    if(count<2 || selected<0)return false;
    Pin policy=tooltip_policy;policy.mode=policy.umode;policy.x=policy.ux;policy.y=policy.uy;
    if(policy.mode<0 || policy.mode==5)return false;  // native layout or hidden
    const double width=global<double>(UI_WIDTH),height=global<double>(UI_HEIGHT);
    const double s=global<double>(UI_SCALE),gap=5*s,inset=20*s;
    double extra=0,tallest=rects[0].h;
    for(int i=1;i<count;++i){extra+=gap+rects[i].w;tallest=std::max(tallest,rects[i].h+inset);}
    Rect primary=placed(policy,rects[0]);
    bool right=true;
    if(width>0 && primary.x+primary.w+extra>width){
        right=primary.x-extra<0 && width-primary.x-primary.w>=primary.x;
    }
    if(policy.clamp && width>0){
        const double group_width=primary.w+extra;
        double left=right?primary.x:primary.x-extra;
        left=std::max(0.0,std::min(width-group_width,left));
        primary.x=right?left:left+extra;
    }
    if(policy.clamp && height>0)primary.y=std::max(0.0,std::min(height-tallest,primary.y));
    rects[0]=primary;
    double edge=right?primary.x+primary.w:primary.x;
    for(int i=1;i<count;++i){
        rects[i].x=right?edge+gap:edge-gap-rects[i].w;
        rects[i].y=primary.y+inset;
        edge=right?rects[i].x+rects[i].w:rects[i].x;
    }
    // Call native setters directly: no re-entry, no stale object cache, and every
    // column is settled before the placement that caused this pass can render.
    tooltip_group_busy=true;
    for(int i=0;i<count;++i){
        auto original=tooltip_hooks[classes[i]-TOOLTIP_CLASSES].original;
        uintptr_t placed_result=original(members[i],&rects[i]);
        if(i==selected)result=placed_result;
    }
    tooltip_group_busy=false;
    return true;
}
uintptr_t tooltip_set_rect(Ptr self,const Rect* requested){
    const TooltipClass* cls=tooltip_class(self);
    if(!cls)return 0;  // only guarded class slots can call this function
    auto original=tooltip_hooks[cls-TOOLTIP_CLASSES].original;
    if(!tooltip_enabled || !requested || tooltip_group_busy)return original(self,requested);
    uintptr_t group_result=0;
    if(item_tooltip(cls) && place_item_comparison(self,*requested,group_result)){
        ++tooltip_calls;return group_result;
    }
    if(place_map_tooltips(self,*requested,group_result)){
        ++tooltip_calls;return group_result;
    }
    Pin policy=tooltip_policy;policy.target=self;
    if(!cls->context){policy.mode=policy.umode;policy.x=policy.ux;policy.y=policy.uy;}
    ++tooltip_calls;
    Rect wanted=policy.mode<0?*requested:placed(policy,*requested);
    return original(self,&wanted);
}
bool tooltip_slot(void** slot,void* expected,void* replacement){
    if(*slot!=expected)return false;
    DWORD protection=0;
    if(!VirtualProtect(slot,sizeof(void*),PAGE_READWRITE,&protection))return false;
    *slot=replacement;
    DWORD ignored=0;VirtualProtect(slot,sizeof(void*),protection,&ignored);
    return true;
}
void release_tooltips(){
    tooltip_enabled=false;
    for(auto& h:tooltip_hooks){
        if(h.slot && tooltip_slot(h.slot,reinterpret_cast<void*>(&tooltip_set_rect),reinterpret_cast<void*>(h.original)))h={};
    }
    tooltip_policy=Pin{};
}
bool configure_tooltips(const Pin& policy){
    if(policy.wmode<0 && policy.umode<0){release_tooltips();return true;}
    // Lua repeats an unchanged policy on its hold pass. With every class slot
    // still ours, only a cursor-anchored tooltip needs re-placing (it follows
    // the mouse); the class checks and other re-placement ran when it changed.
    bool installed=tooltip_enabled;
    for(const auto& h:tooltip_hooks)installed=installed && h.slot && *h.slot==reinterpret_cast<void*>(&tooltip_set_rect);
    const Pin& p=tooltip_policy;
    if(installed && p.id==policy.id && p.wmode==policy.wmode && p.umode==policy.umode && p.x==policy.x && p.y==policy.y
       && p.ux==policy.ux && p.uy==policy.uy && p.clamp==policy.clamp){
        if(policy.wmode!=4 && policy.umode!=4)return true;
    }
    // Recheck the installed client's RTTI and original entry before any writes. A
    // changed class table is refused, rather than replacing another mod's hook.
    for(size_t i=0;i<TOOLTIP_CLASS_COUNT;++i){
        const auto& cls=TOOLTIP_CLASSES[i];
        auto vt=reinterpret_cast<void**>(game()+cls.vtable);
        auto locator=game()+cls.locator;
        auto type=game()+cls.type;
        if(!readable(vt-1,SET_RECT+2*sizeof(void*)) || vt[-1]!=locator || !readable(locator,20)
           || field<unsigned>(locator,0xc)!=cls.type || !readable(type+16,std::strlen(cls.name)+1)
           || std::strcmp(reinterpret_cast<const char*>(type+16),cls.name)
           || vt[SET_RECT/8]!=(tooltip_hooks[i].slot?reinterpret_cast<void*>(&tooltip_set_rect):game()+0x5ac890))return false;
    }
    for(size_t i=0;i<TOOLTIP_CLASS_COUNT;++i){
        auto& h=tooltip_hooks[i];if(h.slot)continue;
        auto slot=reinterpret_cast<void**>(game()+TOOLTIP_CLASSES[i].vtable+SET_RECT);
        h={slot,reinterpret_cast<SetRectFn>(*slot)};
        if(!tooltip_slot(slot,reinterpret_cast<void*>(h.original),reinterpret_cast<void*>(&tooltip_set_rect))){
            h={};release_tooltips();return false;
        }
    }
    tooltip_policy=policy;tooltip_enabled=true;
    // Also apply an option change to an already-visible tooltip. New instances are
    // caught by their class slot on their very first native placement.
    for(int id=0x1ec;id<=0x1ee;++id){
        Ptr d=dialog(id);
        if(!tooltip_class(d) || !shown(d))continue;
        Rect current=field<Rect>(d,0x50);
        tooltip_set_rect(d,&current);
    }
    return true;
}
// Whether the mouse is over a game window (not the world): any shown dialog that is not full-screen,
// not a tooltip and not the window being placed. Asked only when a tooltip is placed.
bool over_ui(Ptr self){
    double cx,cy;cursor_ui(cx,cy);
    double sw=global<double>(UI_WIDTH),sh=global<double>(UI_HEIGHT);
    for(int id=1;id<=int(PLUGIN_LAST);++id){
        Ptr d=dialog(id);
        if(!d || d==self || !shown(d) || tooltip_class(d))continue;
        const Rect& r=field<Rect>(d,0x50);
        if(r.w<8 || r.h<6 || r.w>=sw*.95 || r.h>=sh*.95)continue;
        if(cx<r.x || cx>r.x+r.w || cy<r.y || cy>r.y+r.h)continue;
        const char* name=method<const char*(*)(Ptr)>(d,0xa8)(d);
        if(name && readable(name,8) && !std::strncmp(name,"tooltip",7))continue;
        // Respect click-through regions, especially the spread-out start menu.
        if(method<HitTestFn>(d,HIT_TEST)(d,cx,cy,0,0))return true;
    }
    return false;
}
// Sparse native canvases take the mouse only over their pinned widgets, so the
// space between separated controls stays clickable in the world. Widgets retain
// their own native hover/click handlers (start menu, radar and subzone strip).
int start_hit_test(Ptr self,double x,double y,int a,int b){
    auto original=reinterpret_cast<HitTestFn>(own_table(self)->original[HIT_TEST/8]);
    int id=0;
    for(int i=1;i<=int(DIALOG_LIMIT) && !id;++i)if(gaps[i] && dialog(i)==self)id=i;
    if(!id)return original(self,x,y,a,b);
    for(int i=0;i<pin_count;++i){
        const Pin& p=pins[i];
        if(p.id!=id || !p.widget[0] || p.owner!=self || !pin_live(p)
           || !effectively_shown(p.target))continue;
        Rect r=screen_rect(p.target);
        if(x>=r.x && x<r.x+r.w && y>=r.y && y<r.y+r.h)return original(self,x,y,a,b);
    }
    return 0;
}
// One line through the chat box's own submit; false when the chat input is not the expected window.
bool chat_submit(const wchar_t* line){
    Ptr chat=dialog(int(CHAT_INPUT_ID));
    const char* name=chat?method<const char*(*)(Ptr)>(chat,0xa8)(chat):nullptr;
    if(!name || !readable(name,18) || std::strcmp(name,"chat_input_dialog"))return false;
    native<void(*)(Ptr,const wchar_t*)>(CHAT_SUBMIT)(chat,line);
    return true;
}
// Start-menu buttons of the interface: their commands, as the market-shortcut buttons run theirs.
using CommandFn=int(*)(Ptr,Ptr);
int start_command(Ptr self,Ptr event){
    auto original=reinterpret_cast<CommandFn>(own_table(self)->original[START_COMMAND/8]);
    Ptr button=event?*static_cast<Ptr*>(event):nullptr;
    const char* name=button?method<const char*(*)(Ptr)>(button,0xa8)(button):nullptr;
    const char* command=nullptr;
    if(name && !std::strcmp(name,"aetherfall_broker_button")){chat_submit(L"/say .broker");return 1;}
    if(name && !std::strcmp(name,"aetherfall_menu_button"))command="/afui";
    if(!command)return original(self,event);
    native<int(*)(const char*)>(CHAT_COMMAND)(command);
    return 1;
}
// A class vtable's length: consecutive readable entries that point into Game.dll, at most
// VTABLE_SLOTS. (Copying a fixed 8 KiB failed for classes whose vtable lies near the end of a
// memory region: the pin was refused and the window was only put back a tick later.)
size_t vtable_slots(void** vt){
    auto dos=reinterpret_cast<IMAGE_DOS_HEADER*>(game());
    auto nt=reinterpret_cast<IMAGE_NT_HEADERS64*>(game()+dos->e_lfanew);
    unsigned char* end=game()+nt->OptionalHeader.SizeOfImage;
    size_t n=0;
    for(;n<VTABLE_SLOTS;++n){
        if(!readable(vt+n,sizeof(void*)))break;
        auto v=static_cast<unsigned char*>(vt[n]);
        if(v<game() || v>=end)break;
    }
    return n;
}
bool hook(Ptr d){
    if(Table* known=own_table(d)){
        if(!known->ready)hook_refusal="native class slot installation was refused";
        return known->ready;
    }
    void** vt=*static_cast<void***>(d);
    auto low=reinterpret_cast<unsigned char*>(vt);
    if(low<game() || !readable(vt-1,sizeof(void*))){hook_refusal="class table not in Game.dll";return false;}
    size_t slots=vtable_slots(vt);
    bool start=low==game()+START_VTABLE;
    bool separated=low==game()+0xccee98 || low==game()+0xccf518;
    size_t adjust=anchor_adjust(low);
    if(table_count==TABLE_LIMIT){hook_refusal="too many window classes held";return false;}
    if(slots<=SET_RECT/8 || (start && slots<=START_COMMAND/8)){hook_refusal="class table too short";return false;}
    if(adjust && (slots<=ADJUST_RECT/8 || vt[ADJUST_RECT/8]!=game()+adjust)){
        hook_refusal="native frame anchor changed";return false;
    }
    if(separated && (slots<=HIT_TEST/8 || vt[HIT_TEST/8]!=game()+0x5af4a0)){
        hook_refusal="native minimap hit test changed";return false;
    }
    struct Redirect {size_t slot;void* replacement;};
    Redirect redirects[9]{};size_t count=0;
    auto add=[&](size_t slot,void* replacement){redirects[count++]={slot,replacement};};
    // Live tooltip classes place through the tooltip policy's own SetRect slot
    // (configure_tooltips). A backdrop or pin on one of them, e.g. a name-link
    // tooltip window, previously took that slot first; the policy then found an
    // unknown entry and refused every tooltip class, so no tooltip option applied.
    if(!tooltip_class(d))add(SET_RECT,reinterpret_cast<void*>(&pinned_set_rect));
    if(slots>DRAW_SKIN_ABSOLUTE/8 && vt[DRAW_SKIN/8]==game()+0x5aed00 && vt[DRAW_SKIN_ABSOLUTE/8]==game()+0x5aed90){
        add(DRAW_SKIN,reinterpret_cast<void*>(&backdrop_draw));
        add(DRAW_SKIN_ABSOLUTE,reinterpret_cast<void*>(&backdrop_draw_absolute));
    }
    if(slots>DRAW_SKIN_BYTES_ABSOLUTE/8 && vt[DRAW_SKIN_BYTES/8]==game()+0x5aee20 && vt[DRAW_SKIN_BYTES_ABSOLUTE/8]==game()+0x5aeee0){
        add(DRAW_SKIN_BYTES,reinterpret_cast<void*>(&backdrop_draw_bytes));
        add(DRAW_SKIN_BYTES_ABSOLUTE,reinterpret_cast<void*>(&backdrop_draw_bytes_absolute));
    }
    if(adjust)add(ADJUST_RECT,reinterpret_cast<void*>(&pinned_aura_adjust));
    if(start || separated)add(HIT_TEST,reinterpret_cast<void*>(&start_hit_test));
    if(start)add(START_COMMAND,reinterpret_cast<void*>(&start_command));
    Table* t=&tables[table_count];
    *t=Table{};t->native=vt;t->slots=slots;t->copy[0]=vt[-1];
    std::memcpy(t->copy+1,vt,slots*sizeof(void*));t->original=t->copy+1;
    size_t bucket=table_bucket(vt);t->next=table_buckets[bucket];
    table_buckets[bucket]=++table_count;
    // Publish saved native delegates before routing any slot to the bridge.
    // Installation and policy updates occur on the native UI thread.
    for(size_t i=0;i<count;++i){
        const auto& r=redirects[i];
        if(!tooltip_slot(vt+r.slot/8,t->original[r.slot/8],r.replacement)){
            bool restored=true;
            while(i){const auto& prior=redirects[--i];
                if(!tooltip_slot(vt+prior.slot/8,prior.replacement,t->original[prior.slot/8]))restored=false;
            }
            if(restored){table_buckets[bucket]=t->next;--table_count;*t=Table{};}
            hook_refusal="native class slot changed or is not writable";return false;
        }
    }
    t->ready=true;
    return true;
}
// Native class slots stay routed while the bridge is loaded. No selected policy
// means native pass-through, including new objects of the same class. Releasing
// a policy never changes an object's identity or writes into a retired object.
void unhook(Ptr d){
    (void)d;
}
// A dialog's children: std::list at +0x2a0 (node: next, prev, widget at +0x10), as find (+0x338) walks it.
Ptr child(Ptr parent,int n){
    if(!parent || n<1)return nullptr;
    auto head=field<unsigned char*>(parent,0x2a0);
    if(!head || !readable(head,0x18))return nullptr;
    auto node=*reinterpret_cast<unsigned char**>(head);
    for(int i=1;node && node!=head && i<=2048;++i,node=*reinterpret_cast<unsigned char**>(node)){
        if(!readable(node,0x18))return nullptr;
        if(i==n)return *reinterpret_cast<Ptr*>(node+0x10);
    }
    return nullptr;
}
// Widget by name, or "#n" (the dialog's n-th child: unnamed containers such as an action bar's),
// or "#n/name" (a named widget inside that child).
Ptr pin_target(int id,const char* name,int type){
    if(!*name)return dialog(id);
    return widget(id,name,type);
}
bool pin_live(const Pin& p){
    // Class slots also receive newly constructed/reused objects during logout.
    // A matching target address alone does not establish that its saved owner
    // still exists. Check the native slot before dereferencing either retained
    // pointer, then require both original classes and the current widget path.
    // In particular, never call screen_rect on the retired owner's heap data.
    if(!p.owner || dialog(p.id)!=p.owner || !p.owner_class || !p.native_class)return false;
    if(!readable(p.owner,sizeof(void*)) || *static_cast<void***>(p.owner)!=p.owner_class)return false;
    if(!readable(p.target,sizeof(void*)) || *static_cast<void***>(p.target)!=p.native_class)return false;
    return pin_target(p.id,p.widget,p.type)==p.target;
}
// Class hooks remain native pass-through after a policy is removed. Retiring a
// pin needs no lookup or access to an object the client may already have deleted.
void unpin(int index){
    pins[index]=pins[--pin_count];
}
int kind_type(const char* kind){
    if(!kind || is_kind(kind,"itemview"))return ITEMVIEW;
    if(is_kind(kind,"any"))return 0;  // "#n": the child itself
    if(is_kind(kind,"static"))return STATIC;
    if(is_kind(kind,"button"))return BUTTON;
    if(is_kind(kind,"container"))return CONTAINER;
    if(is_kind(kind,"flygauge"))return FLYGAUGE;
    if(is_kind(kind,"gauge"))return GAUGE;
    return -1;
}
int kind_arg(Ptr L,int index){
    Value* value=arg(L,index);
    if(value && value->tt==3){
        double n=value->v.n;
        return std::isfinite(n) && n>=0x2000 && n<=0x2100 && n==std::floor(n)?int(n):-1;
    }
    return kind_type(text(L,index));
}

// Callers select backdrop layers from the native catalogue. The setting is a
// render multiplier, not a write to the native widget's transient skin colour.
bool backdrop_background(Ptr w){
    if(!w || !readable(w,0x2a8))return false;
    int type=method<int(*)(Ptr)>(w,0x10)(w);
    if(type==BUTTON || type==ITEMVIEW || type==FLYGAUGE)return false;
    const char* name=method<const char*(*)(Ptr)>(w,0xa8)(w);
    if(!name || !readable(name,1))return false;
    size_t size=0;while(size<128 && readable(name+size,1) && name[size])++size;
    if(size==128)return false;
    return !std::strcmp(name,"bg") || !std::strcmp(name,"background") || !std::strcmp(name,"backdrop")
        || !std::strncmp(name,"bg_",3) || (size>=3 && !std::strcmp(name+size-3,"_bg"))
        || std::strstr(name,"_bg_") || (size>=11 && !std::strcmp(name+size-11,"_background"));
}
#include "backdrops.h"

bool is(const char* a,const char* b){return !std::strcmp(a,b);}

// Interface files: <client>/Mods/AetherfallUI/<name>, name [A-Za-z0-9_.-] ending in
// .cfg (settings, read and written) or exactly state.json (launcher state, read only).
bool mod_file(const char* name,bool writing,wchar_t (&path)[MAX_PATH]){
    if(!name)return false;
    size_t len=std::strlen(name);
    bool settings=len>=5 && len<=80 && !std::strcmp(name+len-4,".cfg");
    if(!(settings || (!writing && !std::strcmp(name,"state.json"))) || name[0]=='.' || std::strstr(name,".."))return false;
    for(size_t i=0;i<len;++i){
        char c=name[i];
        if(!((c>='a' && c<='z') || (c>='A' && c<='Z') || (c>='0' && c<='9') || c=='-' || c=='_' || c=='.'))return false;
    }
    wchar_t folder[MAX_PATH];
    DWORD got=GetModuleFileNameW(reinterpret_cast<HMODULE>(image),folder,MAX_PATH);
    if(!got || got>=MAX_PATH)return false;
    for(int up=0;up<2;++up){wchar_t* cut=std::wcsrchr(folder,L'\\');if(!cut)return false;*cut=0;}  // <client>\bin64\Game.dll
    wchar_t manifest[MAX_PATH];
    if(std::swprintf(manifest,MAX_PATH,L"%ls\\Mods\\AetherfallUI\\manifest.json",folder)<0 || GetFileAttributesW(manifest)==INVALID_FILE_ATTRIBUTES)return false;
    return std::swprintf(path,MAX_PATH,L"%ls\\Mods\\AetherfallUI\\%hs",folder,name)>=0;
}

// At most 64 KiB; nil when missing or unreadable.
int read_file(Ptr L,const char* name){
    wchar_t path[MAX_PATH];
    if(!mod_file(name,false,path))return nil(L);
    HANDLE f=CreateFileW(path,GENERIC_READ,FILE_SHARE_READ|FILE_SHARE_WRITE,nullptr,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,nullptr);
    if(f==INVALID_HANDLE_VALUE)return nil(L);
    static char buffer[65536];
    DWORD got=0;
    bool ok=ReadFile(f,buffer,sizeof buffer,&got,nullptr)!=0;
    LARGE_INTEGER size{};
    ok=ok && GetFileSizeEx(f,&size) && size.QuadPart==got;
    CloseHandle(f);
    return ok?push(L,buffer,size_t(got)):nil(L);
}

// At most 32 KiB, written to a temporary file and moved over the old one.
bool save_file(const char* name,const char* data,size_t n){
    if(!data || n>32768)return false;
    wchar_t target[MAX_PATH],temporary[MAX_PATH];
    if(!mod_file(name,true,target))return false;
    if(std::swprintf(temporary,MAX_PATH,L"%ls.tmp",target)<0)return false;
    HANDLE f=CreateFileW(temporary,GENERIC_WRITE,0,nullptr,CREATE_ALWAYS,FILE_ATTRIBUTE_NORMAL,nullptr);
    if(f==INVALID_HANDLE_VALUE)return false;
    DWORD written=0;
    bool ok=WriteFile(f,data,DWORD(n),&written,nullptr) && written==n && FlushFileBuffers(f);
    CloseHandle(f);
    if(ok)ok=MoveFileExW(temporary,target,MOVEFILE_REPLACE_EXISTING|MOVEFILE_WRITE_THROUGH)!=0;
    if(!ok)DeleteFileW(temporary);
    return ok;
}

int dispatch(Ptr L){
    const char* cmd=text(L,1);
    if(!cmd || is(cmd,"version")){
        char version[64];std::snprintf(version,sizeof version,"AetherfallCore %s",CORE_VERSION);
        push(L,1.0);return 1+push(L,version);  // protocol version, installed build
    }
    if(is(cmd,"money")){long long k=kinah();return k<0?nil(L):push(L,double(k));}
    if(is(cmd,"exp")){
        push(L,double(global<long long>(EXP_CURRENT)));push(L,double(global<long long>(EXP_MAX)));
        push(L,double(global<long long>(EXP_RECOVERABLE)));push(L,double(global<long long>(REPOSE_CURRENT)));
        return 4+push(L,double(global<long long>(REPOSE_MAX)));
    }
    if(is(cmd,"fps")){
        Ptr system=global<Ptr>(SYSTEM);if(!system)return nil(L);
        Ptr timer=method<Ptr(*)(Ptr)>(system,0x130)(system);if(!timer)return nil(L);
        float fps=method<float(*)(Ptr)>(timer,0x38)(timer);
        return std::isfinite(fps) && fps>=0 && fps<10000?push(L,double(fps)):nil(L);
    }
    if(is(cmd,"auras")){
        const Aura* list=nullptr;int count=aura_list(text(L,2),list);
        return count<0?nil(L):push(L,double(count));
    }
    if(is(cmd,"aura")){
        const Aura* list=nullptr;int count=aura_list(text(L,2),list),i=int(number(L,3,0));
        if(i<1 || i>count)return nil(L);
        const Aura& a=list[i-1];
        push(L,double(a.skill));push(L,skill_name(a.skill));push(L,double(a.level));push(L,double(a.slot));
        // Native UI 0x57f620 reads effect +0xc directly for its remaining-ms
        // label (0x4f71a0). This is not a GetTickCount deadline or a duration
        // received at first observation. The 18-byte record has no total duration.
        if(a.time>0)push(L,double(a.time)/1000.0);else nil(L);
        nil(L);  // original total duration is unavailable; never synthesize it
        push(L,double(a.time));push(L,double(a.first));return 8+push(L,a.time>0?2.0:0.0);
    }
    if(is(cmd,"playerid")){
        // Native actor identity is +0x30 (also copied by target-record 0x17bf2f).
        Ptr p=global<Ptr>(PLAYER);
        return p && readable(p,0x34)?push(L,double(field<int>(p,0x30))):nil(L);
    }
    if(is(cmd,"unit")){
        if(!unit_record(text(L,2)))return nil(L);
        push(L,double(field<int>(record,0)));push(L,reinterpret_cast<const wchar_t*>(record+4),64);
        push(L,double(field<int>(record,0x84)));push(L,double(field<int>(record,0x88)));push(L,double(field<int>(record,0x8c)));
        push(L,double(field<int>(record,0x94)));return 6+push(L,double(field<int>(record,0x98)));
    }
    if(is(cmd,"rect")){
        // Absolute native rectangle in screen pixels (+0x50 x,y,w,h); widget optional.
        int id=int(number(L,2,0));int type=kind_arg(L,4);
        Ptr d=dialog(id);
        Ptr w=text(L,3)?widget(id,text(L,3),type):d;
        if(!w || !d)return nil(L);
        Rect r=screen_rect(w);
        push(L,r.x);push(L,r.y);push(L,r.w);push(L,r.h);
        return 4+boolean(L,effectively_shown(d) && effectively_shown(w));
    }
    if(is(cmd,"widget")){
        // Read one live node (optionally its nth child), without opening windows,
        // invoking layout/painting, or retaining object pointers between calls.
        int id=int(number(L,2,0));Ptr owner=dialog(id);
        Ptr w=resolve_widget(owner,text(L,3)?text(L,3):"",0);
        if(arg(L,4) && arg(L,4)->tt==3){
            double index=number(L,4,0);
            if(!std::isfinite(index) || index<1 || index>2048 || index!=std::floor(index))return nil(L);
            w=child(w,int(index));
        }
        if(!w || !readable(w,0x2a8))return nil(L);
        const char* name=method<const char*(*)(Ptr)>(w,0xa8)(w);
        push(L,name?name:"");push(L,double(method<int(*)(Ptr)>(w,0x10)(w)));
        push(L,double(method<int(*)(Ptr)>(w,0xb8)(w)));
        Rect local=field<Rect>(w,0x50),screen=screen_rect(w);
        push(L,local.x);push(L,local.y);push(L,local.w);push(L,local.h);
        push(L,screen.x);push(L,screen.y);push(L,screen.w);push(L,screen.h);
        return 11+boolean(L,effectively_shown(w));
    }
    if(is(cmd,"scale")){
        double scale=global<double>(UI_SCALE),width=global<double>(UI_WIDTH),height=global<double>(UI_HEIGHT);
        push(L,scale);push(L,width);return 2+push(L,height);
    }
    if(is(cmd,"skill"))return push(L,skill_name(int(number(L,2,0))));
    if(is(cmd,"shown"))return boolean(L,shown(dialog(int(number(L,2,0)))));
    if(is(cmd,"dialog")){
        // Native dialog by type id: layout name, absolute pixel rect, shown.
        Ptr d=dialog(int(number(L,2,0)));if(!d)return nil(L);
        const char* name=method<const char*(*)(Ptr)>(d,0xa8)(d);
        push(L,name && readable(name,1)?name:"");
        Rect r=screen_rect(d);
        push(L,r.x);push(L,r.y);push(L,r.w);push(L,r.h);
        int flags=method<int(*)(Ptr)>(d,0xb8)(d);
        boolean(L,(flags&1)!=0);
        auto vt=reinterpret_cast<uintptr_t>(*static_cast<void**>(d));
        if(Table* t=own_table(d))vt=reinterpret_cast<uintptr_t>(t->native);
        push(L,double(vt-reinterpret_cast<uintptr_t>(game())));  // class (vtable RVA)
        push(L,double(reinterpret_cast<uintptr_t>(d)));  // instance identity, never persisted
        return 8+push(L,double(flags));  // native flags: movable is 0x8
    }
    if(is(cmd,"popupowner")){
        // UIPopupMenu::Init (536020) stores its owner's native dialog type
        // at +458; GetOwner (4b0900) resolves that through the dialog table.
        // Read only: no creation, placement, hooks or retained pointers.
        Ptr d=dialog(int(number(L,2,0)));if(!d || !readable(d,0x45c))return nil(L);
        void** vt=*static_cast<void***>(d);if(Table* t=own_table(d))vt=t->native;
        if(reinterpret_cast<unsigned char*>(vt)!=game()+0xc4ada8
           || vt[0x530/8]!=game()+0x4b0900)return nil(L);
        int owner=field<int>(d,0x458);
        return owner>0 && owner<=int(DIALOG_LIMIT) && dialog(owner)?push(L,double(owner)):nil(L);
    }
    if(is(cmd,"move")){
        // Interface placement of a native dialog: top-left in pixels, size kept
        // unless given. Same native SetRect (vtable +0x1a8) the engine uses.
        Ptr d=dialog(int(number(L,2,0)));Value* x=arg(L,3);Value* y=arg(L,4);
        if(!d || !x || !y || x->tt!=3 || y->tt!=3)return boolean(L,false);
        Rect r{x->v.n,y->v.n,number(L,5,field<double>(d,0x60)),number(L,6,field<double>(d,0x68))};
        // Target auras use a native 0x0 canvas; its child itemviews supply the
        // visible bounds. Moving that canvas must preserve its zero size.
        if(!std::isfinite(r.x) || !std::isfinite(r.y) || !std::isfinite(r.w) || !std::isfinite(r.h)
           || r.w<0 || r.h<0 || r.w>16384 || r.h>16384)return boolean(L,false);
        if(Pin* p=find_pin(int(number(L,2,0)),""))if(p->mode==0){p->x=r.x;p->y=r.y;}  // a dragged pinned window keeps its new spot
        r=local_rect(d,r);
        if(std::memcmp(&field<Rect>(d,0x50),&r,sizeof r))method<void(*)(Ptr,const Rect*)>(d,0x1a8)(d,&r);
        return boolean(L,true);
    }
    if(is(cmd,"backdrop")){
        double a=number(L,3,-1);
        if(!std::isfinite(a) || a<0 || a>1)return boolean(L,false);
        int budget=2048;
        Value* recursive=arg(L,6);
        return boolean(L,backdrop(int(number(L,2,0)),text(L,4)?text(L,4):"",kind_arg(L,5),float(a),0,budget,recursive && recursive->tt==1 && recursive->v.b));
    }
    if(is(cmd,"backdropprune")){prune_backdrops();return boolean(L,true);}
    if(is(cmd,"backdropclear")){clear_backdrops();return boolean(L,true);}
    if(is(cmd,"pin")){
        // pin(id, x, y[, mode, clamp, w, h, widget, kind]): the game's own re-placing of that dialog
        // (or of its widget, by name and kind) keeps this spot (pixels; modes at struct Pin), applied
        // at once. pin(id, nil, ..., widget): release it. pin(): release all.
        int id=int(number(L,2,0));Value* x=arg(L,3);Value* y=arg(L,4);
        const char* name=text(L,9)?text(L,9):"";
        if(id<=0){release_tooltips();while(pin_count)unpin(0);std::memset(gaps,0,sizeof gaps);return boolean(L,true);}
        if(std::strlen(name)>=sizeof pins[0].widget)return boolean(L,false);
        Pin* p=find_pin(id,name);
        if(!x || !y || x->tt!=3 || y->tt!=3){
            if(!*name && id==tooltip_policy.id)release_tooltips();
            if(p)unpin(int(p-pins));return boolean(L,true);
        }
        int type=kind_arg(L,10);
        Ptr target=pin_target(id,name,type);
        double w=number(L,7,0),h=number(L,8,0);
        if(!target || !std::isfinite(x->v.n) || !std::isfinite(y->v.n) || std::fabs(x->v.n)>16384 || std::fabs(y->v.n)>16384
           || !std::isfinite(w) || !std::isfinite(h) || w>16384 || h>16384)return boolean(L,false);
        if(!p){
            if(pin_count==int(sizeof pins/sizeof pins[0]))return boolean(L,false);
            p=&pins[pin_count++];*p=Pin{};p->id=id;std::memcpy(p->widget,name,std::strlen(name)+1);
        }
        // A target the game recreated replaces the old (deleted) one, which is never touched again.
        if(p->target && p->target!=target)++p->retargets;
        int mode=int(number(L,5,0));Value* clamp=arg(L,6);
        p->type=type;p->target=target;p->owner=dialog(id);p->x=x->v.n;p->y=y->v.n;p->w=w;p->h=h;
        p->native_class=*static_cast<void***>(target);p->owner_class=*static_cast<void***>(p->owner);
        p->mode=(mode>=0 && mode<=5) || (mode==7 && !*name)?mode:0;p->clamp=clamp && clamp->tt==1 && clamp->v.b;  // 7: dialog size only
        if(!hook(target)){refuse(id,name,hook_refusal);unpin(int(p-pins));return boolean(L,false);}
        accept(id,name);
        // Applied at once (also each tick, so a cursor-anchored tooltip follows the mouse).
        Rect current=field<Rect>(target,0x50),want=placed(*p,current);
        if(std::memcmp(&current,&want,sizeof want))method<void(*)(Ptr,const Rect*)>(target,SET_RECT)(target,&current);
        return boolean(L,true);
    }
    if(is(cmd,"tippin")){
        // tippin(id, wmode, wx, wy, umode, ux, uy, clamp): a tooltip held by where the mouse is when the
        // game places it (modes as for pin; below 0 = where the game puts it).
        // id 0 installs the class policy without waiting for a named prototype.
        // Fresh accounts may have only a skill template, or no template yet.
        int id=int(number(L,2,0));Ptr d=id?dialog(id):nullptr;
        if(id && !d)return boolean(L,false);
        const char* name=d?method<const char*(*)(Ptr)>(d,0xa8)(d):nullptr;
        if(!id || (name && readable(name,19) && !std::strncmp(name,"tooltip_proto",13))){
            // The shared item/world template supplies both policies. The skill
            // template is only an inner layout: it must never be moved or overwrite
            // the world policy with the skill's UI-only policy.
            if(name && !std::strncmp(name,"tooltip_proto_skill",19))return boolean(L,true);
            Pin policy{};policy.id=id;policy.mode=6;
            policy.wmode=int(number(L,3,-1));policy.x=number(L,4,0);policy.y=number(L,5,0);
            policy.umode=int(number(L,6,-1));policy.ux=number(L,7,0);policy.uy=number(L,8,0);
            Value* clamp=arg(L,9);policy.clamp=clamp && clamp->tt==1 && clamp->v.b;
            if(policy.wmode< -1 || policy.wmode>5 || policy.umode< -1 || policy.umode>5
               || !std::isfinite(policy.x) || !std::isfinite(policy.y) || !std::isfinite(policy.ux) || !std::isfinite(policy.uy))return boolean(L,false);
            bool ok=configure_tooltips(policy);
            if(ok)accept(id,"");else refuse(id,"","live tooltip class changed or could not be held");
            return boolean(L,ok);
        }
        Pin* p=find_pin(id,"");
        if(!p){
            if(pin_count==int(sizeof pins/sizeof pins[0]))return boolean(L,false);
            p=&pins[pin_count++];*p=Pin{};p->id=id;
        }
        if(p->target && p->target!=d)++p->retargets;
        p->target=d;p->owner=d;p->mode=6;p->type=0;
        p->native_class=p->owner_class=*static_cast<void***>(d);
        p->wmode=int(number(L,3,-1));p->x=number(L,4,0);p->y=number(L,5,0);
        p->umode=int(number(L,6,-1));p->ux=number(L,7,0);p->uy=number(L,8,0);
        Value* clamp=arg(L,9);p->clamp=clamp && clamp->tt==1 && clamp->v.b;
        if(!hook(d)){refuse(id,"",hook_refusal);unpin(int(p-pins));return boolean(L,false);}
        accept(id,"");
        Rect current=field<Rect>(d,0x50),want=placed(*p,current);
        if(std::memcmp(&current,&want,sizeof want))method<void(*)(Ptr,const Rect*)>(d,SET_RECT)(d,&current);
        return boolean(L,true);
    }
    if(is(cmd,"movable")){
        // movable(id, on): the native "movable" window flag (0x8, layout flag "movable"): drag by its frame.
        Ptr d=dialog(int(number(L,2,0)));Value* v=arg(L,3);if(!d || !v)return boolean(L,false);
        bool on=v->tt==1?v->v.b!=0:v->tt==3?v->v.n!=0:false;
        constexpr int MOVABLE=0x8;
        if(((method<int(*)(Ptr)>(d,0xb8)(d)&MOVABLE)!=0)!=on)method<void(*)(Ptr,int,int)>(d,on?0xd0:0xd8)(d,MOVABLE,0);
        return boolean(L,true);
    }
    if(is(cmd,"hittest")){
        // hittest(id, x, y): whether the dialog takes the mouse at that point (its +0x418, as the dispatcher asks).
        Ptr d=dialog(int(number(L,2,0)));if(!d)return nil(L);
        return boolean(L,method<int(*)(Ptr,double,double,int,int)>(d,HIT_TEST)(d,number(L,3,0),number(L,4,0),0,0)!=0);
    }
    if(is(cmd,"gaps")){
        // gaps(id, on): the dialog takes the mouse only over its pinned widgets (start menu).
        int id=int(number(L,2,0));Value* v=arg(L,3);
        if(id<=0 || id>int(DIALOG_LIMIT) || !v)return boolean(L,false);
        gaps[id]=v->tt==1?v->v.b!=0:v->tt==3?v->v.n!=0:false;
        return boolean(L,true);
    }
    if(is(cmd,"command")){
        // command(text): one line through the chat box, as typed (e.g. "/say .broker"). At most 120 characters.
        const char* c=text(L,2);size_t n=text_length(L,2);
        if(!c || n<2 || n>120 || c[0]!='/')return boolean(L,false);
        for(size_t i=0;i<n;++i)if(static_cast<unsigned char>(c[i])<0x20)return boolean(L,false);
        wchar_t line[128];
        int got=MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,c,int(n),line,127);
        if(got<=0)return boolean(L,false);
        line[got]=0;
        return boolean(L,chat_submit(line));
    }
    if(is(cmd,"pininfo")){
        // pininfo(id[, widget]): pinned, SetRect calls caught, times the game recreated it, shown.
        const char* name=text(L,3)?text(L,3):"";
        if(!*name && tooltip_enabled && int(number(L,2,0))==tooltip_policy.id){
            boolean(L,true);push(L,double(tooltip_calls));push(L,0.0);return 3+boolean(L,true);
        }
        Pin* p=find_pin(int(number(L,2,0)),name);
        if(!p){
            for(auto& r:refusals)if(r.id==int(number(L,2,0)) && !std::strcmp(r.widget,name)){boolean(L,false);push(L,r.why);return 2;}
            return boolean(L,false);
        }
        boolean(L,true);push(L,double(p->calls));push(L,double(p->retargets));
        return 3+boolean(L,p->target && shown(p->target));
    }
    if(is(cmd,"setshown")){
        Ptr d=dialog(int(number(L,2,0)));Value* v=arg(L,3);if(!d || !v)return boolean(L,false);
        bool on=v->tt==1?v->v.b!=0:v->tt==3?v->v.n!=0:false;
        if(shown(d)!=on)method<void(*)(Ptr,int,int)>(d,on?0xd0:0xd8)(d,1,0);
        return boolean(L,true);
    }
    if(is(cmd,"gauge")){
        int id=int(number(L,2,0));Ptr g=widget(id,text(L,3),GAUGE);if(!g)return nil(L);
        push(L,field<double>(g,0x370));push(L,field<double>(g,0x360));
        return 2+boolean(L,effectively_shown(dialog(id)) && effectively_shown(g));
    }
    if(is(cmd,"settext")){
        // The native target's own static text update calls vtable +0x290
        // with UTF-16 (8bc227); its paired getter is +0x298, as in "text".
        // Resolve af_resource labels afresh on the UI thread; no native
        // pointers survive a call, and a skin without these labels is harmless.
        const char* path=text(L,3);const char* value=text(L,4);
        size_t length=text_length(L,4);
        if(!path || !value || length>256)return boolean(L,false);
        const unsigned char setter_dispatch[]={0x48,0x8b,0x01,0xff,0x90,0x90,0x02,0x00,0x00};
        if(std::memcmp(game()+0x8bc227,setter_dispatch,sizeof setter_dispatch))return boolean(L,false);
        Ptr s=widget(int(number(L,2,0)),path,STATIC);
        if(!s)return boolean(L,false);
        wchar_t line[257]{};
        if(length && !MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,value,int(length),line,256))return boolean(L,false);
        const wchar_t* previous=method<const wchar_t*(*)(Ptr)>(s,0x298)(s);
        if(previous && readable(previous,sizeof line) && !std::wcsncmp(previous,line,257))return boolean(L,true);
        method<void(*)(Ptr,const wchar_t*)>(s,0x290)(s,line);
        return boolean(L,true);
    }
    if(is(cmd,"text")){
        Ptr s=widget(int(number(L,2,0)),text(L,3),STATIC);if(!s)return nil(L);
        return push(L,method<const wchar_t*(*)(Ptr)>(s,0x298)(s));
    }
    if(is(cmd,"cursor")){
        // Cursor for addon dragging: x, y in game-window client pixels, client width and
        // height, left button down, Shift down. Nil unless the game window is in front.
        HWND hwnd=GetForegroundWindow();DWORD pid=0;
        if(!hwnd || !GetWindowThreadProcessId(hwnd,&pid) || pid!=GetCurrentProcessId())return nil(L);
        POINT p{};RECT client{};
        if(!GetCursorPos(&p) || !ScreenToClient(hwnd,&p) || !GetClientRect(hwnd,&client))return nil(L);
        push(L,double(p.x));push(L,double(p.y));push(L,double(client.right));push(L,double(client.bottom));
        boolean(L,(GetAsyncKeyState(GetSystemMetrics(SM_SWAPBUTTON)?VK_RBUTTON:VK_LBUTTON)&0x8000)!=0);
        return 5+boolean(L,(GetAsyncKeyState(VK_SHIFT)&0x8000)!=0);
    }
    if(is(cmd,"save"))return boolean(L,save_file(text(L,2),text(L,3),text_length(L,3)));
    if(is(cmd,"read"))return read_file(L,text(L,2));
    if(is(cmd,"slot")){
        // Native itemview content (shortcut/buff slots): type, template id, id, count, cooldown gauge.
        char name[48];const char* prefix=text(L,4)?text(L,4):"shortcut";
        std::snprintf(name,sizeof name,"%.32s%d",prefix,int(number(L,3,0)));
        Ptr w=widget(int(number(L,2,0)),name,ITEMVIEW);if(!w)return nil(L);
        auto c=static_cast<unsigned char*>(w)+0x380;
        push(L,double(field<int>(c,0x98)));push(L,double(field<int>(c,0x9c)));push(L,double(field<int>(c,0x94)));
        push(L,double(field<long long>(c,0xa8)));
        Ptr cooldown=field<Ptr>(c,0x158);
        if(cooldown){push(L,field<double>(cooldown,0x370));push(L,field<double>(cooldown,0x360));}
        else{nil(L);nil(L);}
        return 6+boolean(L,effectively_shown(dialog(int(number(L,2,0)))) && effectively_shown(w));
    }
    return nil(L);
}

int AetherfallCore(Ptr L){return dispatch(L);}

// Register by renaming the exact duplicate luaL_Reg row in the writable table
// at 0xbacd60; the first row keeps UnregisterPredefinedQuestIndicator. Both
// Lua states that register this table (UI and plugins) receive the function.
// Game.dll is usually mapped away from its preferred base and its protector
// stub relocates these pointers itself when Game.dll's entry point runs, after
// this DllMain. Writing earlier would let that pass add the delta to our
// pointers, so a short-lived thread waits until the row holds its relocated
// value (long before the UI creates its Lua states), then renames it. Any other
// byte pattern keeps waiting; nothing is written unless the row matches exactly.
struct Reg { const char* name; Ptr fn; };
// Read-only diagnostics for external inspection: state 0 waiting, 1 registered,
// 2 timed out; image base seen; polls; last observed function pointer.
extern "C" __declspec(dllexport) volatile uintptr_t AetherfallCoreDiagnostics[4]={0,0,0,0};
bool try_install(){
    auto g=reinterpret_cast<unsigned char*>(GetModuleHandleW(L"Game.dll"));
    if(!g)return false;
    AetherfallCoreDiagnostics[1]=reinterpret_cast<uintptr_t>(g);
    auto previous=reinterpret_cast<Reg*>(g+LUA_TABLE_PREVIOUS),duplicate=reinterpret_cast<Reg*>(g+LUA_TABLE_DUPLICATE);
    if(!readable(previous,sizeof(Reg)*2))return false;
    AetherfallCoreDiagnostics[3]=reinterpret_cast<uintptr_t>(previous->fn);
    if(previous->fn!=g+LUA_DUPLICATE_FN || duplicate->fn!=previous->fn || duplicate->name!=previous->name)return false;
    if(!readable(previous->name,35) || std::strcmp(previous->name,"UnregisterPredefinedQuestIndicator"))return false;
    image=g;
    install_quest_targets();
    install_camera_turn();
    DWORD old=0;
    if(!VirtualProtect(duplicate,sizeof(Reg),PAGE_EXECUTE_READWRITE,&old))return false;
    duplicate->fn=reinterpret_cast<Ptr>(&AetherfallCore);duplicate->name="AetherfallCore";
    DWORD unused=0;VirtualProtect(duplicate,sizeof(Reg),old,&unused);
    return true;
}
DWORD WINAPI waiter(LPVOID){
    for(int poll=0;poll<120000;++poll){  // up to two minutes
        AetherfallCoreDiagnostics[2]=uintptr_t(poll);
        if(try_install()){AetherfallCoreDiagnostics[0]=1;return 0;}
        Sleep(1);
    }
    AetherfallCoreDiagnostics[0]=2;
    return 0;
}
}

// Imported by Game.dll so the loader maps this bridge. 1 once the Lua
// registration is applied, 0 while waiting, 2 when it timed out unapplied.
extern "C" __declspec(dllexport) int AetherfallCoreStatus(){return int(AetherfallCoreDiagnostics[0]);}

BOOL WINAPI DllMain(HINSTANCE instance,DWORD reason,LPVOID){
    if(reason==DLL_PROCESS_ATTACH){
        DisableThreadLibraryCalls(instance);
        HANDLE thread=CreateThread(nullptr,0,waiter,nullptr,0,nullptr);  // runs after the loader lock is released
        if(thread)CloseHandle(thread);
    }
    return TRUE;
}
