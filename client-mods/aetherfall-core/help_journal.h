// The accepted help requests inside the native quest details (owner 8 Nov; one per wrapper slot): the
// server's card page in DlgQuestSummary's own HTML view, and three af_help_*
// buttons from its layout (client-mods/playerbots/prepare_help_journal.py).
// DlgQuestSummary (0xb9, vtable 0xcd0e78) keeps the shown quest at +0x528 and its
// "quest_summary" UIHTMLView (vtable 0xc42618) at +0x530; the native setter at
// 0x853220 writes both, then calls the view's SetText (+0x290 -> 0x4ce850).
// GetText (+0x298 -> 0x4cec00) returns the view's current wide text. Its buttons
// go through the generic command slot +0x490 (0x4ae9d0), as DlgTarget's do.
// Flag setters +0xd0/+0xd8: mask 1 is visible, mask 4 active (layout not_active).
namespace help_journal {
constexpr int SUMMARY=0xb9;
constexpr size_t SUMMARY_VTABLE=0xcd0e78,VIEW_VTABLE=0xc42618,QUEST_FIELD=0x528,VIEW_FIELD=0x530;
constexpr size_t SET_TEXT=0x290,GET_TEXT=0x298,SET_TEXT_FN=0x4ce850,GET_TEXT_FN=0x4cec00,GENERIC_COMMAND=0x4ae9d0;
// [0] pages drawn, [1] button presses, [2] last refusal (1 class, 2 view, 3 repeated redraw), [3] actions answered
extern "C" __declspec(dllexport) volatile uintptr_t AetherfallHelpJournalDiagnostics[4]={0,0,0,0};
struct State {
    Ptr owner=nullptr,view=nullptr,location=nullptr,track=nullptr,claim=nullptr;
    std::wstring source,native;   // the page we drew, and the view's text right after drawing it
    ULONGLONG windowStart=0,buttonsTried=0;int redraws=0;bool stuck=false,hooked=false;
} state;
std::future<std::string> pending;
// The details just opened: SetRect calls in this window are corrected to beside Q (help_journal_adjust).
ULONGLONG openedAt=0;
constexpr ULONGLONG OPENING_MS=400;
void flag(Ptr w,uint64_t mask,bool on){
    if(!w)return;
    bool set=(method<int(*)(Ptr)>(w,0xb8)(w)&int(mask))!=0;
    if(set!=on)method<void(*)(Ptr,uint64_t,int)>(w,on?0xd0:0xd8)(w,mask,0);
}
bool owned(Ptr w,Ptr d){return w && field<Ptr>(w,0x290)==d;}
// The view exists once the dialog's own init (0x852b30) ran; until then this stays null and is retried.
Ptr html_view(Ptr d){
    Ptr view=field<Ptr>(d,VIEW_FIELD);
    void** viewTable=view && readable(view,sizeof(void*))?*static_cast<void***>(view):nullptr;
    if(viewTable && reinterpret_cast<unsigned char*>(viewTable)==game()+VIEW_VTABLE
       && viewTable[SET_TEXT/8]==game()+SET_TEXT_FN && viewTable[GET_TEXT/8]==game()+GET_TEXT_FN)return view;
    if(view)AetherfallHelpJournalDiagnostics[2]=2;
    return nullptr;
}
void bind(Ptr d){
    state=State{};
    if(!d)return;
    void** vt=*static_cast<void***>(d);
    if(reinterpret_cast<unsigned char*>(vt)!=game()+SUMMARY_VTABLE || vt[START_COMMAND/8]!=game()+GENERIC_COMMAND){AetherfallHelpJournalDiagnostics[2]=1;return;}
    state.owner=d;state.view=html_view(d);
    // The class hook places the details beside Q as they open (help_journal_show) and answers the buttons.
    state.hooked=hook(d);
}
// The dialog object exists before its layout children load (play-test 8 Nov: the page drew, no buttons),
// so the buttons are looked up again while the help page shows, at most twice a second.
void buttons(Ptr d){
    if(state.location || !state.hooked)return;
    ULONGLONG now=GetTickCount64();if(now-state.buttonsTried<500)return;state.buttonsTried=now;
    // Without the layout package the page still draws; the buttons simply do not exist.
    Ptr location=resolve_widget(d,"af_help_location",BUTTON),track=resolve_widget(d,"af_help_track",BUTTON),claim=resolve_widget(d,"af_help_claim",BUTTON);
    if(owned(location,d) && owned(track,d) && owned(claim,d)){state.location=location;state.track=track;state.claim=claim;}
    else AetherfallHelpJournalDiagnostics[2]=4;
}
// Right of Q, or left when the pair would pass the screen edge, at Q's height: the rule of the original
// opener, quest_journal.h and the Lua follower (which then finds nothing to move).
// Where the details belong for a given width, in their local frame; false while Q is closed.
bool beside(Ptr detail,double width,Rect& local){
    Ptr journal=dialog(0xb8);
    if(!journal || !shown(journal))return false;
    Rect j=screen_rect(journal),target=screen_rect(detail);
    target.w=width;target.x=j.x+j.w;target.y=j.y;
    if(target.x+target.w>global<double>(UI_WIDTH))target.x=j.x-target.w;
    if(target.x<0)target.x=0;
    local=local_rect(detail,target);return true;
}
void place(Ptr detail){
    if(!shown(detail))return;
    const Rect current=field<Rect>(detail,0x50);Rect target{};
    if(!beside(detail,current.w,target) || (target.x==current.x && target.y==current.y))return;
    target.w=current.w;target.h=current.h;
    method<uintptr_t(*)(Ptr,const Rect*)>(detail,0x1a8)(detail,&target);
}
// The help slot of the quest the details show, or -1.
int slot(Ptr d){return d && d==state.owner && shown(d)?help_quest::slot_of(field<int>(d,QUEST_FIELD)):-1;}
bool ours(Ptr d){int i=slot(d);return i>=0 && !help_quest::slots[i].card_page.empty();}
std::wstring current(){
    auto text=method<const wchar_t*(*)(Ptr)>(state.view,GET_TEXT)(state.view);
    if(!text || !readable(text,sizeof(wchar_t)))return {};
    size_t n=0;while(n<65536 && readable(text+n,sizeof(wchar_t)) && text[n])++n;
    return std::wstring(text,n);
}
// Redraw only when the native setter replaced our page (another quest, reopening)
// or the server's card changed. A view that never keeps our text stops after a few tries.
void draw(){
    if(!state.view)state.view=html_view(state.owner);
    if(!state.view)return;
    int i=slot(state.owner);if(i<0)return;
    const std::wstring& page=help_quest::slots[i].card_page;
    if(page!=state.source)state.stuck=false;
    if(state.stuck || (page==state.source && current()==state.native))return;
    ULONGLONG now=GetTickCount64();
    if(now-state.windowStart>2000){state.windowStart=now;state.redraws=0;}
    if(++state.redraws>4){state.stuck=true;AetherfallHelpJournalDiagnostics[2]=3;return;}
    method<void(*)(Ptr,const wchar_t*)>(state.view,SET_TEXT)(state.view,page.c_str());
    state.source=page;state.native=current();
    ++AetherfallHelpJournalDiagnostics[0];
}
std::string post(const std::string& id,const std::string& op,int slotNumber){
    if(id.empty())return {};
    const auto endpoint=aetherfall_web::selected(id.c_str());if(!endpoint.valid)return {};
    help_quest::Internet transport{WinHttpOpen(L"Aetherfall Help/1",WINHTTP_ACCESS_TYPE_NO_PROXY,nullptr,nullptr,0)};if(!transport.handle)return {};
    WinHttpSetTimeouts(transport.handle,2000,2000,3000,5000);
    help_quest::Internet connection{aetherfall_web::connect(transport.handle,endpoint)};if(!connection.handle)return {};
    help_quest::Internet query{aetherfall_web::request(connection.handle,endpoint,L"POST",L"/market/companions/help/action")};if(!query.handle)return {};
    DWORD redirects=WINHTTP_OPTION_REDIRECT_POLICY_NEVER;WinHttpSetOption(query.handle,WINHTTP_OPTION_REDIRECT_POLICY,&redirects,sizeof(redirects));
    const std::wstring headers=aetherfall_web::form_headers(endpoint)+L"X-Aetherfall-Native: 1\r\n";
    std::string body="op="+op+"&slot="+std::to_string(slotNumber)+"&session_id="+id;
    if(!WinHttpSendRequest(query.handle,headers.c_str(),DWORD(-1),body.data(),DWORD(body.size()),DWORD(body.size()),0) || !WinHttpReceiveResponse(query.handle,nullptr))return {};
    std::string answer;char buffer[512];DWORD got=0;
    do{if(!WinHttpReadData(query.handle,buffer,sizeof buffer,&got) || answer.size()+got>4096)return answer;answer.append(buffer,got);}while(got);
    return answer;
}
// The server answers every action in chat; the journal refreshes from the next card.
int pendingSlot=-1;
void send(const char* op,int i){
    if(pending.valid())return;
    std::string id=help_quest::session(),name=op;pendingSlot=i;
    pending=std::async(std::launch::async,[id,name,i](){return post(id,name,i);});
}
void press(Ptr button){
    int i=slot(state.owner);if(i<0)return;
    const auto& s=help_quest::slots[i];
    ++AetherfallHelpJournalDiagnostics[1];
    if(button==state.location){if(s.card_map)codex::show_location(s.card_map,s.card_x,s.card_y,s.card_z);return;}
    if(button==state.track){
        if(active_quest(help_quest::QUESTS[i])){
            native<int(*)(Ptr,int,int)>(0x3f6120)(game()+0x13715e0,help_quest::QUESTS[i],1);
            if(Ptr tracker=dialog(0xba))native<void(*)(Ptr,int)>(0x856bf0)(tracker,1);
        }
        send("track",i);return;
    }
    if(button==state.claim){flag(state.claim,4,false);send("claim",i);}
}
void tick(){
    Ptr d=dialog(SUMMARY);
    if(d!=state.owner)bind(d);
    if(pending.valid() && pending.wait_for(std::chrono::seconds(0))==std::future_status::ready){
        pending.get();++AetherfallHelpJournalDiagnostics[3];help_quest::refresh(pendingSlot);pendingSlot=-1;
    }
    if(!state.owner)return;
    bool on=ours(d);
    if(on)buttons(d);
    flag(state.location,1,on);flag(state.track,1,on);flag(state.claim,1,on);
    if(!on)return;
    const auto& s=help_quest::slots[slot(d)];
    flag(state.location,4,s.card_map!=0);
    flag(state.claim,4,s.card_status=="COMPLETE" && !pending.valid());
    draw();
}
}
// DlgQuestSummary show (+0xd0, native 0x5ac3e0): opened beside Q in the same call, before it is drawn,
// instead of one frame at its old spot and then a jump (owner play-test 8 Nov).
void help_journal_show(Ptr self,uint64_t mask,int propagate){
    bool opening=(mask&1) && self==dialog(help_journal::SUMMARY);
    if(opening)help_journal::openedAt=GetTickCount64();
    reinterpret_cast<void(*)(Ptr,uint64_t,int)>(own_table(self)->original[0xd0/8])(self,mask,propagate);
    if(opening)help_journal::place(self);
}
// DlgQuestSummary OnVisible (+0x468, native 0x852bb0 places it; play-test: still one frame at the old spot).
void help_journal_visible(Ptr self,int visible){
    bool opening=visible && self==dialog(help_journal::SUMMARY);
    if(opening)help_journal::openedAt=GetTickCount64();
    reinterpret_cast<void(*)(Ptr,int)>(own_table(self)->original[0x468/8])(self,visible);
    if(opening)help_journal::place(self);
}
// Called from pinned_set_rect: while the details are opening, any position given to them becomes beside Q.
bool help_journal_adjust(Ptr self,Rect& r){
    if(!help_journal::openedAt || GetTickCount64()-help_journal::openedAt>help_journal::OPENING_MS || self!=dialog(help_journal::SUMMARY))return false;
    Rect target{};
    if(!help_journal::beside(self,r.w,target) || (target.x==r.x && target.y==r.y))return false;
    r.x=target.x;r.y=target.y;return true;
}
int help_journal_command(Ptr self,Ptr event){
    using namespace help_journal;
    Ptr button=event?*static_cast<Ptr*>(event):nullptr;
    if(button && self==state.owner && (button==state.location || button==state.track || button==state.claim) && ours(self) && effectively_shown(button)){
        press(button);return 1;
    }
    return reinterpret_cast<CommandFn>(own_table(self)->original[START_COMMAND/8])(self,event);
}
