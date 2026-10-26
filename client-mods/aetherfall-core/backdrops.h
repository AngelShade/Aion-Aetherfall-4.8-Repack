// Backdrop-only native rendering policy. Included in the bridge namespace.
// Native SetAlpha/SetColor and animations retain their own state. The user
// multiplier is applied to a local copy of the skin colour at every draw.
struct Backdrop { int id,type; char path[256]; Ptr target,owner; void** native_class; float alpha; int next; };
constexpr int BACKDROP_LIMIT=1024;
Backdrop backdrops[BACKDROP_LIMIT]{};
int backdrop_count=0;
constexpr size_t BACKDROP_BUCKETS=2048;
int backdrop_buckets[BACKDROP_BUCKETS]{};
size_t backdrop_bucket(Ptr target){return (reinterpret_cast<uintptr_t>(target)>>4)%BACKDROP_BUCKETS;}
void index_backdrops(){
    std::memset(backdrop_buckets,0,sizeof backdrop_buckets);
    for(int i=0;i<backdrop_count;++i){
        auto slot=backdrop_bucket(backdrops[i].target);
        backdrops[i].next=backdrop_buckets[slot];backdrop_buckets[slot]=i+1;
    }
}

bool backdrop_live(const Backdrop& b){
    // Resolve the current instance before touching a retained target. Closing,
    // replacing or reusing a native object cannot transfer opacity to another
    // widget that happens to occupy the same address.
    if(dialog(b.id)!=b.owner || pin_target(b.id,b.path,b.type)!=b.target)return false;
    return *static_cast<void***>(b.target)==b.native_class;
}
void remove_backdrop(int index){
    Backdrop previous=backdrops[index];
    backdrops[index]=backdrops[--backdrop_count];
    index_backdrops();
    if(backdrop_live(previous))unhook(previous.target);
}
// Retiring a selection never touches its target (unhook is pass-through), so
// compact the table first and rebuild the draw index once.
void prune_backdrops(){
    int kept=0;
    for(int i=0;i<backdrop_count;++i)if(backdrop_live(backdrops[i]))backdrops[kept++]=backdrops[i];
    if(kept==backdrop_count)return;
    backdrop_count=kept;index_backdrops();
}
void clear_backdrops(){backdrop_count=0;index_backdrops();}

// Runs for every selected skin draw, every frame. backdrop_live's full path
// resolution runs when selections are made and pruned; a draw needs only the
// same owner dialog in its slot and the same object class at this address.
bool backdrop_current(const Backdrop& b,Ptr self){
    return b.target==self && dialog(b.id)==b.owner && *static_cast<void***>(self)==b.native_class;
}
const Backdrop* drawn_backdrop(Ptr self){
    for(int link=backdrop_buckets[backdrop_bucket(self)];link;link=backdrops[link-1].next){
        const auto& b=backdrops[link-1];
        if(backdrop_current(b,self))return &b;
    }
    return nullptr;
}
void draw_backdrop(Ptr self,size_t slot,int state,const float* colour,float x,float y,Ptr clip){
    Table* table=own_table(self);
    if(!table)return;  // called only through one of our guarded native class slots
    auto original=reinterpret_cast<DrawSkinFn>(table->original[slot/8]);
    // Native rendering does not scan the complete selected-widget catalogue.
    if(const auto* b=drawn_backdrop(self)){
        // Zero means no selected skin draw in every renderer mode. Children,
        // text and icons are separate native draws and remain visible.
        if(b->alpha==0)return;
        if(!colour){original(self,state,colour,x,y,clip);return;}
        float adjusted[4];std::memcpy(adjusted,colour,sizeof adjusted);
        // Preserve native fade/state alpha, RGB, geometry and clipping. Zero
        // stays zero; every positive setting only reduces the native opacity.
        adjusted[3]*=b->alpha;
        original(self,state,adjusted,x,y,clip);
        return;
    }
    original(self,state,colour,x,y,clip);
}
void backdrop_draw(Ptr self,int state,const float* colour,float x,float y,Ptr clip){
    draw_backdrop(self,DRAW_SKIN,state,colour,x,y,clip);
}
void backdrop_draw_absolute(Ptr self,int state,const float* colour,float x,float y,Ptr clip){
    draw_backdrop(self,DRAW_SKIN_ABSOLUTE,state,colour,x,y,clip);
}
void draw_backdrop_bytes(Ptr self,size_t slot,int state,Ptr bounds,unsigned char r,unsigned char g,unsigned char blue,unsigned char alpha,float angle,float depth){
    Table* table=own_table(self);
    if(!table)return;
    auto original=reinterpret_cast<DrawSkinBytesFn>(table->original[slot/8]);
    if(const auto* b=drawn_backdrop(self)){
        if(b->alpha==0)return;
        alpha=static_cast<unsigned char>(std::floor(float(alpha)*b->alpha+.5f));
    }
    original(self,state,bounds,r,g,blue,alpha,angle,depth);
}
void backdrop_draw_bytes(Ptr self,int state,Ptr bounds,unsigned char r,unsigned char g,unsigned char b,unsigned char a,float angle,float depth){
    draw_backdrop_bytes(self,DRAW_SKIN_BYTES,state,bounds,r,g,b,a,angle,depth);
}
void backdrop_draw_bytes_absolute(Ptr self,int state,Ptr bounds,unsigned char r,unsigned char g,unsigned char b,unsigned char a,float angle,float depth){
    draw_backdrop_bytes(self,DRAW_SKIN_BYTES_ABSOLUTE,state,bounds,r,g,b,a,angle,depth);
}

bool set_backdrop(int id,const char* path,int type,Ptr target,float alpha){
    Ptr owner=dialog(id);
    if(!owner || std::strlen(path)>=sizeof backdrops[0].path)return false;
    // Re-selecting an unchanged live widget is the common periodic case.
    for(int link=backdrop_buckets[backdrop_bucket(target)];link;link=backdrops[link-1].next){
        const auto& b=backdrops[link-1];
        if(b.id==id && b.alpha==alpha && backdrop_current(b,target) && !std::strcmp(b.path,path))return true;
    }
    int existing=-1;
    for(int i=0;i<backdrop_count;++i)
        if((backdrops[i].id==id && !std::strcmp(backdrops[i].path,path)) || backdrops[i].target==target){existing=i;break;}
    if(alpha==1){if(existing>=0)remove_backdrop(existing);return true;}
    Table* known=own_table(target);
    void** vt=known?known->original:*static_cast<void***>(target);
    // Both float-colour and byte-RGBA rendering paths are supported. UIDialog's
    // native Draw switches between them; omitting +3e0 leaves an opaque path.
    // custom renderers are refused rather than substituting a guessed ABI.
    // A held class measured its table once; do not query memory per slot again.
    if((known?known->slots:vtable_slots(vt))<=DRAW_SKIN_BYTES_ABSOLUTE/8 || vt[DRAW_SKIN/8]!=game()+0x5aed00 || vt[DRAW_SKIN_ABSOLUTE/8]!=game()+0x5aed90
       || vt[DRAW_SKIN_BYTES/8]!=game()+0x5aee20 || vt[DRAW_SKIN_BYTES_ABSOLUTE/8]!=game()+0x5aeee0)return false;
    const unsigned char prolog[]={0x48,0x83,0xec,0x58,0x48,0x8b,0x01};
    if(std::memcmp(game()+0x5aed00,prolog,sizeof prolog) || std::memcmp(game()+0x5aed90,prolog,sizeof prolog))return false;
    const unsigned char byte_prolog[]={0x48,0x83,0xec,0x78,0x48,0x8b,0x01};
    if(std::memcmp(game()+0x5aee20,byte_prolog,sizeof byte_prolog) || std::memcmp(game()+0x5aeee0,byte_prolog,sizeof byte_prolog))return false;
    if(existing<0){
        if(backdrop_count==BACKDROP_LIMIT)return false;
        existing=backdrop_count++;
    }
    auto& b=backdrops[existing];Ptr previous_target=b.target;
    // Recreated targets replace an address only; the old object is never
    // written. Its current owner/path must still resolve before unhooking.
    if(b.target && b.target!=target){Backdrop prior=b;b=Backdrop{};if(backdrop_live(prior))unhook(prior.target);}
    b.id=id;b.type=type;b.target=target;b.owner=owner;
    b.native_class=own_table(target)?own_table(target)->native:vt;b.alpha=alpha;
    std::memcpy(b.path,path,std::strlen(path)+1);
    if(previous_target!=target || existing==backdrop_count-1)index_backdrops();
    if(!hook(target)){remove_backdrop(existing);return false;}
    return true;
}
bool backdrop(int id,const char* path,int type,float alpha,int depth,int& budget,bool recursive=false){
    Ptr target=pin_target(id,path,type);
    if(!target || depth>24 || --budget<0 || !readable(target,0x2a8))return false;
    int native_type=method<int(*)(Ptr)>(target,0x10)(target);
    if(native_type==FLYGAUGE || native_type==BUTTON || (native_type==GAUGE && !backdrop_background(target)))return false;
    if(!set_backdrop(id,path,type,target,alpha))return false;
    if(!recursive)return true;
    Ptr head=field<Ptr>(target,0x2a0);
    if(!head || !readable(head,0x18))return true;
    Ptr node=field<Ptr>(head,0);
    for(int index=1;node && node!=head;++index){
        if(index>512 || !readable(node,0x18))return false;
        Ptr entry=field<Ptr>(node,0x10);
        if(entry && backdrop_background(entry)){
            char nested[256];
            int length=std::snprintf(nested,sizeof nested,"%s%s#%d",path,*path?"/":"",index);
            if(length<0 || size_t(length)>=sizeof nested || !backdrop(id,nested,0,alpha,depth+1,budget,true))return false;
        }
        node=field<Ptr>(node,0);
    }
    return node==head;
}
