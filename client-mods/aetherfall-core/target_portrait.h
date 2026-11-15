// Target portraits use the selected creature already received by the client, not a name lookup.
// Game.dll 0x17be0b..0x17be97 resolves the selection's entity and creature component (type 1).
// The player snapshot at 0x17a41c..0x17a507 asks the same component list for type 2 (a player
// character) and copies its model (+0x34) and 66 look bytes (+0x24c); 0x17a70e copies its visible
// equipment (+0x1240, 0x170 bytes); the stock portrait (0x60fcc6..0x60fd2e) applies both to a
// UI3DView. A player target therefore takes its body from type 2, exactly as the stock portrait
// does; an NPC has no type 2 and keeps its creature's template ID (0x487fcd..0x488175).
namespace target_portrait {
constexpr size_t SELECTED=0x1313092,ENTITIES=0xeccdc8,LOOK=0x24c,GEAR=0x1240,GEAR_BYTES=0x170;
constexpr size_t VIEW3D_GEAR=0x4896d0;
// The object the stock snapshot (0x17a3c8) and the party getter (0x17d044) ask, at +0x1e0, for the
// player's own entity.
constexpr size_t WORLD=0x130c5d0;
// Why a portrait was not built; returned to Lua for the frame report (0 = built).
enum Result{READY=0,NO_SELECTION=1,NO_ENTITY=2,NO_COMPONENTS=3,NO_CREATURE=4,OTHER_SELECTION=5,
            NO_MODEL=6,UNREADABLE_LOOK=7,MESH_NOT_BUILT=8,NO_CAMERA=17};
// UI_ModelCam.xml's "portrait" map frames only the player bodies (lm/lf/dm/df.cgf) and a few
// transformation forms; any other creature gets the view's untuned default camera and shows a
// fragment (a Kerubiel Bandit showed only its flame). Such a creature gets no portrait (NO_CAMERA):
// nothing is built, and the frame gives the room back to the bars.

// Build the view's body from one creature or character component. character: it answered as type 2.
int apply(Ptr view,Ptr source,bool character){
    int model=field<int>(source,0x34);
    if(model<=0)return NO_MODEL;
    // Without a character component, the four player body models still identify a player.
    bool player=character || (model>=100000 && model<=100003);
    if(!player)return NO_CAMERA;
    std::array<uint8_t,0x60> body{};
    std::array<uint8_t,GEAR_BYTES> gear{};
    if(player){
        // Player bodies need their real face, hair, proportions and visible gear. NPC bodies resolve
        // their own mesh from the native NPC template; do not apply player morphs to a monster.
        if(!readable(static_cast<unsigned char*>(source)+LOOK,0x42) ||
           !readable(static_cast<unsigned char*>(source)+GEAR,GEAR_BYTES))return UNREADABLE_LOOK;
        std::memcpy(body.data(),static_cast<unsigned char*>(source)+LOOK,0x42);
        body[0x3e]=0;  // The stock portrait clears this leg-length byte at 0x60fcbe; actor data stays untouched.
        std::memcpy(gear.data(),static_cast<unsigned char*>(source)+GEAR,GEAR_BYTES);
    }
    native<void(*)(Ptr,int,float,const uint8_t*)>(VIEW3D_MODEL)(view,model,1.0f,player?body.data():nullptr);
    if(!field<Ptr>(view,0x470) && !field<Ptr>(view,0x478))return MESH_NOT_BUILT;
    if(player)native<void(*)(Ptr,const uint8_t*,int,int,bool,bool)>(VIEW3D_GEAR)(view,gear.data(),model,0,false,false);
    return READY;
}

int show(Ptr view,int expected){
    auto handle=global<uint16_t>(SELECTED);
    Ptr entities=global<Ptr>(ENTITIES);
    if(!handle || !readable(entities,sizeof(Ptr)))return NO_SELECTION;
    Ptr entity=method<Ptr(*)(Ptr,uint16_t)>(entities,0x30)(entities,handle);
    if(!readable(entity,sizeof(Ptr)))return NO_ENTITY;
    Ptr components=method<Ptr(*)(Ptr)>(entity,0xc0)(entity);
    if(!readable(components,sizeof(Ptr)))return NO_COMPONENTS;
    Ptr creature=nullptr;
    method<void(*)(Ptr,int,Ptr*)>(components,0x50)(components,1,&creature);
    if(!readable(creature,0x38))return NO_CREATURE;
    if(field<int>(creature,0x30)!=expected)return OTHER_SELECTION;
    // The stock snapshot's source: the player-character component, when there is one.
    Ptr character=nullptr;
    method<void(*)(Ptr,int,Ptr*)>(components,0x50)(components,2,&character);
    bool own=readable(character,0x38) && field<int>(character,0x30)==expected;
    return apply(view,own?character:creature,own);
}

// The player's own body, from the stock snapshot's source: no selection is involved.
int show_self(Ptr view){
    Ptr world=game()+WORLD;
    if(!readable(world,sizeof(Ptr)))return NO_ENTITY;
    Ptr entity=method<Ptr(*)(Ptr)>(world,0x1e0)(world);
    if(!readable(entity,sizeof(Ptr)))return NO_ENTITY;
    Ptr components=method<Ptr(*)(Ptr)>(entity,0xc0)(entity);
    if(!readable(components,sizeof(Ptr)))return NO_COMPONENTS;
    Ptr character=nullptr;
    method<void(*)(Ptr,int,Ptr*)>(components,0x50)(components,2,&character);
    if(!readable(character,0x38))return NO_CREATURE;
    return apply(view,character,true);
}
}
