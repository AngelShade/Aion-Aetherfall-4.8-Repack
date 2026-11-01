// Find Group's own native children, command event and visibility lifecycle.
// DlgPartyMatch (type 0x14d, vtable 0xcd2ee8) +0x490 at 0x80f7f0 reads
// the clicked UIButton from event[0], just like the existing Codex owner.
// +0xd0/+0xd8 are UIWidget flag setters (uint64 mask, propagation int).
// The addon XML loader creates separate DlgAddon windows; it cannot parent a
// script button into a stock dialog. Native resource XML has no Scripts owner.
namespace hire_terms {
constexpr int DIALOG=0x14d;
struct State {
    Ptr owner=nullptr,help=nullptr,toggle=nullptr,panel=nullptr;
    Rect helpRect{};double screenWidth=0,screenHeight=0;
    bool layingOut=false,laidOut=false;
} state;
void show(Ptr w,bool on){if(w && shown(w)!=on)method<void(*)(Ptr,uint64_t,int)>(w,on?0xd0:0xd8)(w,1,0);}
void close(){show(state.panel,false);}
void set_rect(Ptr w,const Rect& r){
    const auto& old=field<Rect>(w,0x50);
    if(old.x!=r.x || old.y!=r.y || old.w!=r.w || old.h!=r.h)method<SetRectFn>(w,SET_RECT)(w,&r);
}
void layout(){
    if(!state.owner || !state.help || !state.toggle || !state.panel || state.layingOut)return;
    state.layingOut=true;
    // All native +0x50 rectangles are already scaled UI pixels. Help and the
    // new button have the SAME parent, so no title/content origin is added.
    const Rect help=field<Rect>(state.help,0x50);
    double s=help.w/61.0;
    if(!std::isfinite(s) || s<=0){state.layingOut=false;return;}
    // Help sits at the window's bottom left (frame x 3), so the button follows it.
    // Going left of Help puts it outside the dialog, where nothing draws it.
    set_rect(state.toggle,Rect{help.x+help.w+4*s,help.y,104*s,help.h});
    // Keep the box above the button row and wholly inside the native content.
    // Native screen_rect supplies the title/skin offset; use that same offset
    // to clamp to the viewport when the player drags near a screen edge.
    Rect owner=field<Rect>(state.owner,0x50),current=screen_rect(state.panel);
    Rect local=field<Rect>(state.panel,0x50);
    double originX=current.x-local.x,originY=current.y-local.y;
    double sw=global<double>(UI_WIDTH),sh=global<double>(UI_HEIGHT);
    double w=std::min(394*s,std::max(0.0,owner.w-14*s));
    double h=216*s;
    double x=help.x+help.w-w,y=help.y-6*s-h;
    double left=std::max(3*s,-originX),top=std::max(3*s,-originY);
    double right=std::min(owner.w-7*s,sw-originX),bottom=std::min(help.y-6*s,sh-originY);
    x=std::max(left,std::min(right-w,x));y=std::max(top,std::min(bottom-h,y));
    set_rect(state.panel,Rect{x,y,w,h});
    state.helpRect=help;state.screenWidth=sw;state.screenHeight=sh;state.laidOut=true;
    state.layingOut=false;
}
bool poll(){
    Ptr d=dialog(DIALOG);
    if(d!=state.owner){
        state=State{};state.owner=d;
        if(!d)return false;
        const char* name=method<const char*(*)(Ptr)>(d,0xa8)(d);
        if(!name || std::strcmp(name,"party_match_dialog"))return false;
        Ptr help=resolve_widget(d,"help",BUTTON);
        Ptr toggle=resolve_widget(d,"af_hire_details",BUTTON);
        Ptr panel=resolve_widget(d,"af_hire_terms",CONTAINER);
        if(!help || !toggle || !panel || field<Ptr>(help,0x290)!=d
           || field<Ptr>(toggle,0x290)!=d || field<Ptr>(panel,0x290)!=d || !hook(d))return false;
        state.help=help;state.toggle=toggle;state.panel=panel;close();layout();
    }
    if(!d || !state.help)return false;
    if(!shown(d))close();
    const Rect r=field<Rect>(state.help,0x50),old=state.helpRect;
    if(!state.laidOut || r.x!=old.x || r.y!=old.y || r.w!=old.w || r.h!=old.h
       || state.screenWidth!=global<double>(UI_WIDTH) || state.screenHeight!=global<double>(UI_HEIGHT))layout();
    return true;
}
}
void hire_frame_changed(Ptr self){if(self==hire_terms::state.owner)hire_terms::layout();}
void hire_show(Ptr self,uint64_t mask,int propagate){
    if(self==hire_terms::state.owner && (mask&1) && !shown(self)){
        hire_terms::close();hire_terms::state.laidOut=false;
    }
    reinterpret_cast<void(*)(Ptr,uint64_t,int)>(own_table(self)->original[0xd0/8])(self,mask,propagate);
    if(self==hire_terms::state.owner && (mask&1))hire_terms::layout();
}
void hire_hide(Ptr self,uint64_t mask,int propagate){
    if(self==hire_terms::state.owner && (mask&1))hire_terms::close();
    reinterpret_cast<void(*)(Ptr,uint64_t,int)>(own_table(self)->original[0xd8/8])(self,mask,propagate);
}
int hire_command(Ptr self,Ptr event){
    Ptr button=event?*static_cast<Ptr*>(event):nullptr;
    if(self==hire_terms::state.owner && button==hire_terms::state.toggle && effectively_shown(self)){
        hire_terms::layout();hire_terms::show(hire_terms::state.panel,!shown(hire_terms::state.panel));return 1;
    }
    return reinterpret_cast<CommandFn>(own_table(self)->original[START_COMMAND/8])(self,event);
}
