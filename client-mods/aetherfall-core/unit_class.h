// Unit class exactly as the native emblems resolve it; nothing is inferred from names or colours.
// Target/target of target (Game.dll 0x8bb350..0x8bb57e): object ID -> handle map (0x1313000) ->
// entity; entity kinds 1 and 2 are characters whose creature component answers vtable +0x248 with
// the class. The target record's fallback (+0x9c) alone does not establish a character,
// so unresolved entities return none. Party (0x6e8348): the party getter 0x17d020 copies each member's
// class byte (+0x38) and name (+0x3f, from creature +0x46, 0x1a wide characters at 0x17a1f2).
// Class IDs are the protocol's PlayerClass values 0..16; names are STR_CLASS_NAME_* strings.
namespace unit_class {
constexpr size_t OBJECT_HANDLES=0x1313000,ENTITIES=0xeccdc8,PARTY_RECORD=0x17d020;
constexpr size_t PARTY_BYTES=0xcc98,PARTY_COUNTS=0x4c,PARTY_MEMBERS=0x50,GROUP_STRIDE=0x32f4,MEMBER_STRIDE=0x87e;
constexpr int NONE=0x11;
alignas(16) unsigned char party[PARTY_BYTES];

int name_id(int c){return c<12?240000+c:904314+(c-12);}
// The creature's class for a verified character, NONE for anything else.
int of_object(int id){
    if(id<=0)return NONE;
    Ptr head=global<Ptr>(OBJECT_HANDLES);
    if(!readable(head,0x28))return NONE;
    Ptr node=field<Ptr>(head,8),found=head;
    for(int guard=0;readable(node,0x28) && !field<char>(node,0x21) && guard<64;++guard){
        if(field<unsigned>(node,0x18)<unsigned(id))node=field<Ptr>(node,0x10);
        else{found=node;node=field<Ptr>(node,0);}
    }
    if(found==head || !readable(found,0x28) || unsigned(id)<field<unsigned>(found,0x18))return NONE;
    Ptr entities=global<Ptr>(ENTITIES);
    if(!readable(entities,sizeof(Ptr)))return NONE;
    Ptr entity=method<Ptr(*)(Ptr,uint16_t)>(entities,0x30)(entities,field<uint16_t>(found,0x1c));
    if(!readable(entity,sizeof(Ptr)))return NONE;
    Ptr components=method<Ptr(*)(Ptr)>(entity,0xc0)(entity);
    Ptr creature=nullptr;
    if(readable(components,sizeof(Ptr)))method<void(*)(Ptr,int,Ptr*)>(components,0x50)(components,1,&creature);
    if(!readable(creature,0x38) || field<int>(creature,0x30)!=id)return NONE;
    if(!field<int>(creature,0x34))return NONE;
    int kind=method<uint16_t(*)(Ptr)>(entity,0x318)(entity);
    if(kind!=1 && kind!=2)return NONE;
    int c=method<int(*)(Ptr)>(creature,0x248)(creature);
    return c>=0 && c<NONE?c:NONE;
}
// A party member by the name its row shows (UTF-8).
int of_member(const char* name){
    wchar_t want[32]{};
    if(!name || !*name || !MultiByteToWideChar(CP_UTF8,0,name,-1,want,31))return NONE;
    native<void(*)(Ptr,Ptr)>(PARTY_RECORD)(game()+INFO,party);
    for(int g=0;g<4;++g){
        int count=party[PARTY_COUNTS+g];  // empty groups (and no party: the getter only clears) count 0
        for(int m=0;m<count && m<6;++m){
            const unsigned char* member=party+PARTY_MEMBERS+size_t(g)*GROUP_STRIDE+size_t(m)*MEMBER_STRIDE;
            wchar_t have[26]{};std::memcpy(have,member+0x3f,25*sizeof(wchar_t));
            if(std::wcscmp(have,want))continue;
            int c=static_cast<signed char>(member[0x38]);
            return c>=0 && c<NONE?c:NONE;
        }
    }
    return NONE;
}
}
