// The addon C++ wrapper factory (0x622450) does not support 3dview. A supported
// addon Container hosts a native XML-created Model instead. Use the COMPLETE native
// create/register/attach/read/initialize helper; never allocate or delete a UI node
// with our CRT. Native dialog teardown recursively unregisters/deletes descendants.
// Game.dll SHA256 9efbb3b2...: XML doc sizeof 0x280 (0x605b8,0x206c8e);
// ctor/parse-wide/dtor 0x8b0a0/0x8bca0/0x8b150, complete factory 0x58a190.
namespace portrait_view {
constexpr int HOST_TYPE=0x2008;
constexpr size_t DOC_CTOR=0x8b0a0,DOC_PARSE=0x8bca0,DOC_DTOR=0x8b150;
constexpr size_t CREATE_WIDGET=0x58a190,LOADER=0x1378d40,REMOVE_WIDGET=0x53d700;
constexpr wchar_t XML[]=L"<Widget type=\"3dview\" name=\"Model\" frame=\"0,0,1,1\" "
    L"camtable=\"portrait\" flag=\"transparent;not_active\" "
    L"h_pos_align=\"proportion\" v_pos_align=\"proportion\"/>";
// UI3DView renders into a surface named "$UI3DView_%d" from its +0x608 slot (Game.dll 0x48cdf1,
// string 0xc3eb30). The constructor leaves 0 (0x4864df); each stock dialog gives its own view a slot
// (1..7: 0x60fb30, 0x837c81, 0x834b73, 0x8037d3, 0x7915cf, 0x8063b3, 0x8fb82d). Views sharing a slot
// draw the same picture, so every host keeps its own fixed slot, clear of the stock ones: 8 upward,
// which exist only once render_slots has raised the renderer's limit (a slot past the stock 8
// crashed the renderer in 1.4.13). Without it a view keeps the slot it has.
// The view resolves its surface by name once and keeps it at +0x640 (lookup 0x48cd8c, released by
// the destructor 0x486831 through virtual +8 with dl=0), so a slot change drops that surface too.
constexpr size_t VIEW_SLOT=0x608,VIEW_SURFACE=0x640;
constexpr int SLOT_BASE=render_slots::STOCK;
static_assert(SLOT_BASE+8<=render_slots::LIMIT,"portrait slots inside the raised limit");
void assign(Ptr view,int own){
    if(field<int>(view,VIEW_SLOT)==own)return;
    if(Ptr surface=field<Ptr>(view,VIEW_SURFACE)){
        field<Ptr>(view,VIEW_SURFACE)=nullptr;
        method<void(*)(Ptr,bool)>(surface,8)(surface,false);
    }
    field<int>(view,VIEW_SLOT)=own;
}
constexpr const char* HOSTS[]={"AFHFPlayerPortrait","AFHFTargetPortrait","AFHFParty1Portrait","AFHFParty2Portrait",
    "AFHFParty3Portrait","AFHFParty4Portrait","AFHFParty5Portrait","AFHFParty6Portrait"};
int slot(Ptr host){
    const char* name=method<const char*(*)(Ptr)>(host,0xa8)(host);
    if(!name || !readable(name,1))return -1;
    for(int i=0;i<int(sizeof HOSTS/sizeof HOSTS[0]);++i)if(!std::strcmp(name,HOSTS[i]))return SLOT_BASE+i;
    return -1;
}
struct Document {
    alignas(8) unsigned char bytes[0x280]{};
    Document(){native<Ptr(*)(Ptr)>(DOC_CTOR)(bytes);}
    ~Document(){native<void(*)(Ptr)>(DOC_DTOR)(bytes);}
    Document(const Document&)=delete;
    Document& operator=(const Document&)=delete;
};
// No pointers retained across calls. A clear request never creates a child.
Ptr get(Ptr host,bool create,int& why){
    why=0;
    if(!readable(host,0x2a8)){why=10;return nullptr;}
    if(method<int(*)(Ptr)>(host,0x10)(host)!=HOST_TYPE){why=11;return nullptr;}
    int own=slot(host);
    if(own<0){why=16;return nullptr;}
    if(!render_slots::ensure(own))own=-1;  // patch off or slot not made: keep the native slot
    Ptr view=direct_child(host,"Model");
    if(view){
        if(!readable(view,VIEW3D_BYTES) || method<int(*)(Ptr)>(view,0x10)(view)!=VIEW3D ||
           field<Ptr>(view,0x290)!=host){why=12;return nullptr;}
        if(own>=0)assign(view,own);  // also moves a view created by 1.3.22..1.3.23 off slot 0
        return view;
    }
    if(!create)return nullptr;
    Ptr owner=method<Ptr(*)(Ptr)>(host,0x2d0)(host);
    if(!readable(owner,sizeof(Ptr))){why=13;return nullptr;}
    Document doc;
    if(!native<int(*)(Ptr,const wchar_t*)>(DOC_PARSE)(doc.bytes,XML) ||
       !field<Ptr>(doc.bytes,0)){why=14;return nullptr;}
    view=native<Ptr(*)(Ptr,Ptr,Ptr,Ptr)>(CREATE_WIDGET)(game()+LOADER,field<Ptr>(doc.bytes,0),owner,host);
    if(!view){why=15;return nullptr;}
    if(!readable(view,VIEW3D_BYTES) || method<int(*)(Ptr)>(view,0x10)(view)!=VIEW3D ||
       field<Ptr>(view,0x290)!=host || direct_child(host,"Model")!=view){
        native<void(*)(Ptr,Ptr)>(REMOVE_WIDGET)(owner,view);
        why=12;return nullptr;
    }
    if(own>=0)assign(view,own);  // before its first draw
    // Native position alignment enum4 stretches the child with host size deltas.
    // UI3DView's own post-SetRect callback updates its viewport. Only the host is pinned.
    Rect size=field<Rect>(host,0x50);
    Rect rect{0,0,size.w,size.h};
    method<void(*)(Ptr,const Rect*)>(view,0x1a8)(view,&rect);
    return view;
}
}
