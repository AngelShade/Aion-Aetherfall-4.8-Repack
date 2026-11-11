-- Character, focus and viewport state. Timer phases are semantic, not load order.
local M=AF:NewModule('Lifecycle',{scope='profile',needs={},defaults={}})
local S={ready=false,ticks=0}
local N=AF.Native
local D=AFCore and AFCore.DIALOG or {}
local dialog,screen,playerIdentity=N.Dialog,N.Screen,N.PlayerIdentity
function M:Ticks() return S.ticks end
function M:IsReady() return S.ready end
function M:IsConnected() return S.connected end
function M:IsSettled() return S.settled end
function M:IsUnlocked() return S.unlocked end
function M:IsHudOn() return S.hudOn end
function M:Identity() return S.identity end
function M:Entry() return S.entry end
local function recoverLayout(keepEditor,keepPins)
    AF:Send('PrepareRecoverEditor',keepEditor)
    S.settled=false;AF:Send('MarkPinsDirty')
    if not keepEditor and not keepPins then AF:Send('ReleaseLayoutPins') end
    AF:Send('ResetLayoutCaches',true)
    AF:Send('RecoverNativeWindows')
    S.sizeSince=S.ticks
    AF:Send('RecoverShownSnapshot')
    AF:Send('RecoverEditor',keepEditor)
    AF:Send('InvalidateHUD')
    AF:Send('BindCommands')
end
function M:Enter()
    local identity=playerIdentity();if not identity then return end
    if S.identity~=identity or not S.connected then S.entry=(S.entry or 0)+1 end
    if S.identity~=identity then
        AF:Send('DiscardEditor')
        if S.ready then AF.DB:Save() end
        -- The saved file is keyed by the identity read at this moment, as it always was.
        local ok,why=pcall(function() return AF.DB:Load((assert(playerIdentity(),'character is not connected'))) end)
        if not ok then S.loadError=tostring(why);AF.DB:Reset() end
        S.identity=identity;AF:Send('CharacterChanged')
        if AFCore.ClearBackdrops then AFCore.ClearBackdrops() end
    end
    S.ready=true
    AF:Send('ApplyCamera')
    AF:Enable()
end
function M:OnEvent(event,...)
    g_AddonName='RelicCalc'
    if event=='ADDON_TIMER' then
        S.ticks=S.ticks+1;AF.DB:Clock(S.ticks)
        if S.ticks%20==1 then AF:Send('BindCommands');AFCore.PublishSlashCommands() end
        local current=playerIdentity()
        if not current then
            if S.connected~=false then
                if AFCore.ClearBackdrops then AFCore.ClearBackdrops() end
                AF:Send('DiscardEditor')
                if S.ready and AF.DB:SaveAt() then AF.DB:Save() end
                S.connected,S.ready,S.settled=false,false,false
                AF:Send('DisconnectEditor')
                AF:Send('ResetObservations')
                AF:Send('HideOptions')
                AF:Send('ReleaseLayoutPins')
                AF:Disable()
            end
            S.ready,S.settled=false,false;return
        end
        if not S.ready or S.identity~=current or not S.connected then
            self:Enter();if not S.ready then return end
            S.connected,S.suspended=true,nil;recoverLayout()
        end
        local sw,sh=screen();local size=math.floor(sw)..'x'..math.floor(sh)..':'..tostring((AFCore.Scale()))
        local focused=AFCore.Cursor()~=nil
        if S.focused==true and not focused then
            S.settled=false;AF:Send('ClearEditorDrag')
        elseif S.focused==false and focused then recoverLayout(true) end
        S.focused=focused
        if S.lastSize~=size then
            S.lastSize,S.sizeSince=size,S.ticks;AF:Send('ClearEditorDrag',true)
        end
        local primary,chat=D.basic_status and dialog(D.basic_status),D.chat and dialog(D.chat)
        local worldShown=(primary and primary.shown) or (chat and chat.shown)
        if focused and S.worldShown==true and not worldShown then S.suspended=true end
        if focused and worldShown and S.suspended then S.suspended=nil;recoverLayout(false,true) end
        if focused then S.worldShown=worldShown and true or false end
        local zone=N.Call('GetZoneText')
        if zone=='' then zone=nil end  -- read between areas; not a zone change
        if focused and zone and S.zone and zone~=S.zone then recoverLayout(false,true) end
        S.zone=zone or S.zone
        S.settled=focused and sw>=800 and sh>=600 and S.ticks-(S.sizeSince or 0)>=20
        S.settled=S.settled and not S.suspended
        if S.settled then AF:Send('RestoreEditor') end
        for _,phase in ipairs(AF.Events.phases) do AF.Events:RunPhase(phase) end
        if S.ticks%20==0 then S.alive=S.ticks end
        AF.Events:Advance(S.ticks)
    elseif event=='USER_CHANGED_TARGET' or event=='GAME_UI_DIALOG_VISIBLE_CHANGED' then
        if S.ready and S.connected and S.settled and not S.suspended and playerIdentity()==S.identity
            and AFCore.Cursor()~=nil and S.hudOn then AF:Send('QueueVisibleSettings') end
    elseif event=='PLAYER_ENTERING_WORLD' then
        local previous=S.identity
        self:Enter();if playerIdentity() then S.connected=true end
        recoverLayout(false,previous~=nil and previous==S.identity)
    elseif event=='ADDON_EXIT' then
        AF:Send('DiscardEditor');if S.ready then AF.DB:Save() end
    end
end
function M:OnLoad()
    g_AddonName='RelicCalc'
    local top=N.Widget('H','Top')
    if top then for _,event in ipairs({'ADDON_EXIT','USER_CHANGED_TARGET','GAME_UI_DIALOG_VISIBLE_CHANGED'}) do top:RegisterEvent(event) end end
    AF:Send('BindCommands')
end
function M:Diagnostics() return AF.Copy(S) end
M:Listen('HudChanged',function(_,on) S.hudOn=on end)
M:Listen('EditorStateChanged',function(_,on) S.unlocked=on end)
for _,event in ipairs({'ADDON_TIMER','USER_CHANGED_TARGET','GAME_UI_DIALOG_VISIBLE_CHANGED','PLAYER_ENTERING_WORLD','ADDON_EXIT'}) do M:On(event,M.OnEvent,0) end
M:Phase('save',function()
    if not AF.DB:HasDraft() and AF.DB:SaveAt() and S.ticks>=AF.DB:SaveAt() then AF.DB:Save() end
end)
