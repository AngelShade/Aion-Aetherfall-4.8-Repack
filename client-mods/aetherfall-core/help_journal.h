// The accepted help requests inside the native quest details (owner 8 Nov; one per wrapper slot): the
// server's card page in DlgQuestSummary's own HTML view, laid out like a native quest page.
// Owner 9 Nov: the controls sit where a normal quest's do. Locate and Track are in-page buttons
// right under the objectives (the native "Locate" template: href, v5_quest_button, v3_quest_button);
// the view posts their click as event 0xaf1 to the summary's event slot +0x450 (native 0x852d00, which
// compares the button's ASCII href at +0x89c). Collect reward is Q's own get_reward button
// (DlgQuest 0xb8, vtable 0xcd07b8: button +0x590, selected quest +0x580, event 0x76c through +0x450
// -> 0x849c30; flag 2 is "enabled", which the native 0x84bcc0 sets from its claimable list).
// The three af_help_* layout buttons (prepare_help_journal.py) stay in the layout, always hidden.
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
constexpr size_t EVENT=0x450,JOURNAL_VTABLE=0xcd07b8;
constexpr int HTML_CLICK=0xaf1,BUTTON_CLICK=0x76c;
constexpr size_t HREF=0x89c,JOURNAL_REWARD=0x590,JOURNAL_SELECTED=0x580;
// [0] pages drawn, [1] button presses, [2] last refusal (1 class, 2 view, 3 repeated redraw), [3] actions answered
extern "C" __declspec(dllexport) volatile uintptr_t AetherfallHelpJournalDiagnostics[4]={0,0,0,0};
struct State {
    Ptr owner=nullptr,view=nullptr;
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
// The layout's af_help_* buttons (8 Nov) stay hidden; the page's own buttons replaced them. The dialog
// object exists before its layout children load, so this is retried, at most twice a second.
void hide_layout_buttons(Ptr d){
    if(!state.hooked || state.buttonsTried==~0ull)return;
    ULONGLONG now=GetTickCount64();if(now-state.buttonsTried<500)return;state.buttonsTried=now;
    Ptr found[3]={resolve_widget(d,"af_help_location",BUTTON),resolve_widget(d,"af_help_track",BUTTON),resolve_widget(d,"af_help_claim",BUTTON)};
    bool all=true;
    for(Ptr b:found){if(owned(b,d))flag(b,1,false);else all=false;}
    if(all)state.buttonsTried=~0ull;
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
// An in-page button of the help page: true when the href was one of ours.
bool press(const char* href){
    int i=slot(state.owner);if(i<0)return false;
    const auto& s=help_quest::slots[i];
    if(!std::strcmp(href,"af_help_location")){
        ++AetherfallHelpJournalDiagnostics[1];
        if(s.card_map)codex::show_location(s.card_map,s.card_x,s.card_y,s.card_z);
        return true;
    }
    if(!std::strcmp(href,"af_help_track")){
        ++AetherfallHelpJournalDiagnostics[1];
        if(active_quest(help_quest::QUESTS[i])){
            native<int(*)(Ptr,int,int)>(0x3f6120)(game()+0x13715e0,help_quest::QUESTS[i],1);
            if(Ptr tracker=dialog(0xba))native<void(*)(Ptr,int)>(0x856bf0)(tracker,1);
        }
        send("track",i);return true;
    }
    return false;
}
// Q's selection when it is a help quest: its slot, else -1.
int journal_slot(Ptr q){
    if(!q || q!=dialog(0xb8) || reinterpret_cast<unsigned char*>(*static_cast<void***>(q))!=game()+JOURNAL_VTABLE)return -1;
    int i=help_quest::slot_of(field<int>(q,JOURNAL_SELECTED));
    return i>=0 && !help_quest::slots[i].card_page.empty()?i:-1;
}
// Q's Collect reward: enabled for a finished help request, as the native enables it for claimable quests.
// The native 0x84bcc0 resets it on each selection; this puts it back the same tick.
void journal_reward(){
    Ptr q=dialog(0xb8);
    if(!q || !shown(q))return;
    // Q's class takes the event redirect when first hooked (here, or earlier by a pin or backdrop).
    static Ptr hookedJournal=nullptr;
    if(q!=hookedJournal){hookedJournal=q;hook(q);}
    int i=journal_slot(q);if(i<0)return;
    Ptr reward=field<Ptr>(q,JOURNAL_REWARD);if(!reward || !readable(reward,sizeof(void*)))return;
    flag(reward,2,help_quest::slots[i].card_status=="COMPLETE" && !pending.valid());
}
void tick(){
    Ptr d=dialog(SUMMARY);
    if(d!=state.owner)bind(d);
    if(pending.valid() && pending.wait_for(std::chrono::seconds(0))==std::future_status::ready){
        pending.get();++AetherfallHelpJournalDiagnostics[3];help_quest::refresh(pendingSlot);pendingSlot=-1;
    }
    journal_reward();
    if(!state.owner)return;
    hide_layout_buttons(d);
    if(ours(d))draw();
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
using EventFn=int(*)(Ptr,int,Ptr);
// DlgQuestSummary event slot (+0x450): an in-page af_help_* button of the help page; all else is native.
int help_journal_event(Ptr self,int id,Ptr event){
    using namespace help_journal;
    if(id==HTML_CLICK && event && self==state.owner && ours(self)){
        Ptr element=*static_cast<Ptr*>(event);
        auto at=element?static_cast<unsigned char*>(element)+HREF:nullptr;
        auto href=at && readable(at,24)?reinterpret_cast<const char*>(at):nullptr;
        if(href && !std::strncmp(href,"af_help_",8) && press(href))return 1;
    }
    return reinterpret_cast<EventFn>(own_table(self)->original[EVENT/8])(self,id,event);
}
// DlgQuest event slot (+0x450): Collect reward on a help quest claims it through the server.
int help_journal_quest_event(Ptr self,int id,Ptr event){
    using namespace help_journal;
    if(id==BUTTON_CLICK && event){
        Ptr button=*static_cast<Ptr*>(event);
        int i=button && button==field<Ptr>(self,JOURNAL_REWARD)?journal_slot(self):-1;
        if(i>=0){
            ++AetherfallHelpJournalDiagnostics[1];
            if(help_quest::slots[i].card_status=="COMPLETE"){flag(button,2,false);send("claim",i);}
            return 1;
        }
    }
    return reinterpret_cast<EventFn>(own_table(self)->original[EVENT/8])(self,id,event);
}
