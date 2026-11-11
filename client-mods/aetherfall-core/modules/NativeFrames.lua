-- Registered feature adapters keep the editor independent of frame types.
local M=AF:NewModule('NativeFrames',{scope='profile',needs={'Placement','Shortcuts','Tooltip','Minimap','HUD','TargetDetails','Lifecycle'},defaults={}})
local S={}
local N=AF.Native
local D=AFCore and AFCore.DIALOG or {}
local call,n,scale,screen=N.Call,N.Number,N.Scale,N.Screen
local dialog,nativeMovable,identity=N.Dialog,N.Movable,N.Identity
local featureLayout,featureWidget=N.Layout,N.LayoutWidget
local function get(key) return M.db[key] end
local function key(id) return AF.Movers:Key(id) end
local function position(id,d) return AF.Movers:Get(id,d) end
local function remember(...) return AF.Movers:Remember(...) end
local function design(id) return AF:Call('Placement','Design',id) end
local function designRef(id) return AF:Call('Placement','DesignRef',id) end
local function defaultSpot(id,d) return AF:Call('Placement','DefaultSpot',id,d) end
local PLUGIN_FIRST=0x20e
local function movable(d, sw, sh)
    return d and nativeMovable(d) and d.shown and d.w > 0 and d.h > 0 and d.w < sw * .95 and d.h < sh * .95
end
local FRAMES = {
    {'gm_indicator', 'Daily Quest / System Notifications'}, {'help_indicator', 'Tutorial Notifications'},
    {'basic_status', 'Player'}, {'target', 'Target'}, {'target_assist', 'Target of Target'}, {'party', 'Party'},
    {'radar', 'Minimap'}, {'start', 'Menu'}, {'shortcut', 'Action Bar 1'}, {'shortcut_ctrl', 'Action Bar 2'},
    {'shortcut_alt', 'Action Bar 3'}, {'shortcut_extra', 'Action Bar 4'}, {'shortcut_sep', 'Action Bar 5'},
    {'shortcut_sep2', 'Action Bar 6'},
    {'chat', 'Chat'}, {'chat_input', 'Chat Input'}, {'exp_bar', 'Experience Bar'}, {'buff_status', 'Buffs'},
    {'debuff_status', 'Debuffs'}, {'target_buff', 'Target Auras'}, {'skill_delay', 'Cast Bar'},
    {'target_delay', 'Target Cast Bar'},
    {'target_assist_delay', 'Target of Target Cast Bar'}, {'pet', 'Spirit Bar'},
}
-- The builder derives previews from the same active UI catalogue as the core.
-- Unmapped native types remain resource definitions until actually discovered.
local SKIP_MOVERS = {party_buff = true, new_map = true, subzone_name = true}  -- the native area strip follows the minimap
local OTHER_LABELS = {timer = 'Countdown Timer', quest_indicator = 'Quest Tracker', subzone_name = 'Zone Name', help_indicator = 'Notifications',
                      move_state = 'Movement', gm_indicator = 'GM Indicator', event_shortcut = 'Event Bar',
                      skill_penalty = 'Skill Penalty', tooltip = 'Tooltip', tooltip_skill = 'Skill Tooltip',
                      general_delay = 'Progress Bar', enchant_delay = 'Enchant Bar', target_assist_delay = 'Target of Target Cast Bar',
                      pet_command = 'Spirit Commands'}
-- Always offered (by layout name, the id is not fixed): the notification icons ("Daily quest has arrived").
-- pet_command has a size only in the alternate HUD, where it holds the spirit's commands and toggle.
local NAMED_MOVERS = {help_indicator = true, general_delay = true, enchant_delay = true, target_assist_delay = true, pet_command = true}
local FRAME_CATALOG=setmetatable({}, {__index=function(_,id) return AF:Call('Placement','CatalogRef',id) end})
local function moverCandidates()
    local sw, sh = screen(); local list, seen = {}, {}
    for _, f in ipairs(FRAMES) do
        local id = D[f[1]]
        local d = id and dialog(id)
        local ref = id and designRef(id)
        -- The native notification canvas can be absent/empty until the first
        -- tutorial or daily quest. Its verified type is still selectable.
        if f[1] == 'help_indicator' and not ref then
            ref = {360, 800, 505, 47, 'left', 'top', 'help_indicator_dialog'}
        elseif f[1] == 'gm_indicator' and not ref then
            ref = {950, 845, 166, 137, 'left', 'top', 'gm_indicator_dialog'}
        end
        if not d and ref then
            local x, y = design(id); local s = n((AFCore.Scale())) or 1
            x, y = x or ref[1] * s, y or ref[2] * s
            d = {name = ref[7], x = x, y = y, w = ref[3] * s, h = ref[4] * s, shown = false, virtual = true}
        end
        if d and ref and d.name ~= ref[7] then d = nil end
        if d and f[1] == 'shortcut_sep2' and d.name ~= 'quickbar_dialog_sep' then d = nil end
        if d and f[1] == 'start' and AF:Call('Shortcuts','Managed') then seen[id] = true; d = nil end  -- each button has its own mover
        if d then list[#list + 1] = {id, d, f[2]}; seen[id] = true end
    end
    for id = 1, PLUGIN_FIRST - 1 do
        local ref = not seen[id] and FRAME_CATALOG[id]
        if ref then
            local d = dialog(id)
            if not d then
                local s = scale()
                local x = ref[5] == 'center' and sw/2 + (ref[1]-640)*s or ref[5] == 'right' and sw-(1280-ref[1])*s or ref[1]*s
                local y = ref[6] == 'bottom' and sh-(960-ref[2])*s or ref[6] == 'middle' and sh/2+(ref[2]-480)*s or ref[2]*s
                d = {name=ref[7], x=math.max(0,math.min(sw-ref[3]*s,x)), y=math.max(0,math.min(sh-ref[4]*s,y)), w=ref[3]*s, h=ref[4]*s, virtual=true, shown=false}
            end
            if d.name == ref[7] then
                list[#list+1] = {id, d, OTHER_LABELS[d.name:gsub('_dialog$', '')] or d.name:gsub('_dialog$', ''):gsub('_',' ')}
                seen[id] = true
            end
        end
    end
    for id = 1, PLUGIN_FIRST - 1 do
        local d = not seen[id] and dialog(id)
        local name = d and tostring(d.name):gsub('_dialog$', '')
        local wanted = d and (movable(d, sw, sh) or (NAMED_MOVERS[name] and d.w >= 8 and d.h >= 6 and d.w < sw * .95 and d.h < sh * .95)
                              or (nativeMovable(d) and S.seen[id] == d.name and d.w > 0 and d.h > 0 and d.w < sw * .6 and d.h < sh * .6))
        -- Unnamed windows are pop-ups (submenus, context menus): they open where they are needed.
        if wanted and name ~= '' and not SKIP_MOVERS[name] and not AF:Call('Tooltip','IsTooltip',d) then
            list[#list + 1] = {id, d, OTHER_LABELS[name] or (name:gsub('_', ' '))}
        end
    end
    return list
end
-- Where a frame is, or for a hidden frame where it will appear (saved spot, else layout spot).
local function auraBounds(id, d)
    if id ~= D.target_buff then return end
    local x0, y0, x1, y1 = math.huge, math.huge, -math.huge, -math.huge
    local first
    for _, prefix in ipairs({'buff_item', 'debuff_item'}) do
        for i = 1, 20 do
            local x, y, wd, ht, shown = AFCore.Rect(id, prefix .. i, 'itemview')
            if x and wd > 0 and ht > 0 then
                if not first then first = {x - d.x, y - d.y, wd, ht} end
                local typ, template = AFCore.Slot(id, i, prefix)
                if shown and typ and typ > 0 and template and template > 0 then
                    x0, y0, x1, y1 = math.min(x0, x - d.x), math.min(y0, y - d.y), math.max(x1, x - d.x + wd), math.max(y1, y - d.y + ht)
                end
            end
        end
    end
    S.auraFootprint = S.auraFootprint or {}
    if x0 < math.huge then
        S.auraFootprint[id] = {x0, y0, x1 - x0, y1 - y0}
        return x0, y0, x1 - x0, y1 - y0
    end
    local f = (not d.shown and S.auraFootprint[id]) or first
    -- The active English target-aura layout has 22px itemviews at 1,1,
    -- including before the native zero-sized canvas has been created.
    f = f or {scale(), scale(), 22 * scale(), 22 * scale()}
    return unpack(f)
end
-- GM/system notifications contain the daily-quest icon, unlike the separate
-- tutorial canvas. Match the shown icons instead of the canvas's empty space.
local NOTIFICATION_BUTTONS = {'poll_button', 'petition_button1', 'petition_button2', 'indun_reg_button', 'indun_wait_button',
    'party_match_button', 'daily_quest_button', 'predefined_addon_button', 'housing_mode_button', 'powermap_button',
    'housing_return_button', 'beginner_server_button', 'litedown_info_button', 'gatha_start_button', 'account_info_button',
    'new_skill_button', 'quest_reward_button', 'gold_vendor_button', 'login_event_button'}
local function notificationBounds(id, d)
    if id ~= D.gm_indicator then return end
    local x0, y0, x1, y1 = math.huge, math.huge, -math.huge, -math.huge
    for _, name in ipairs(NOTIFICATION_BUTTONS) do
        local x, y, wd, ht, shown = AFCore.Rect(id, name, 'button')
        if shown and x and wd > 0 and ht > 0 then
            x0, y0, x1, y1 = math.min(x0, x - d.x), math.min(y0, y - d.y), math.max(x1, x - d.x + wd), math.max(y1, y - d.y + ht)
        end
    end
    if x0 < math.huge then return x0, y0, x1 - x0, y1 - y0 end
end
local function targetBounds(id,d)
    if id~=D.target and id~=D.target_assist then return end
    local layout=featureLayout(d.name,id)
    local fallback
    for _,node in pairs(layout and layout.paths or {})do
        local parent=layout.paths[node.parent]
        if node.attributes.name=='bg' and parent and parent.parent==''
            and (parent.attributes.name=='container' or tostring(parent.attributes.name):match('^container%d+$'))
            and #node.rect==4 and #parent.rect==4 then
            local s=scale()
            fallback=fallback or {(parent.rect[1]+node.rect[1])*s,(parent.rect[2]+node.rect[2])*s,node.rect[3]*s,node.rect[4]*s}
            local x,y,width,height,visible=AFCore.Rect(id,node.path,'any')
            if d.shown and visible and x and width>0 and height>0 then return x-d.x,y-d.y,width,height end
        end
    end
    if fallback then return unpack(fallback)end
end
local function frameRect(id, d)
    local s = scale(); local sw, sh = screen()
    local x, y = d.x, d.y
    if not d.shown then
        local p = position(id, d)
        if p then x, y = p[1] * sw, p[2] * sh else local dx, dy = design(id); if dx then x, y = dx, dy end end
    end
    local ox, oy, wd, ht = auraBounds(id, d)
    if not ox then ox, oy, wd, ht = notificationBounds(id, d) end
    if not ox then ox, oy, wd, ht = targetBounds(id,d)end
    if ox then return x + ox, y + oy, wd, ht, ox, oy end
    if id == D.radar then
        local r = AF:Call('Minimap','Parts')[id]
        return r[1],r[2],r[3],r[4]+(AF:Call('HUD','HasMinimapInfo') and 18*s or 0),0,0
    end
    return x, y, math.max(s, d.w), math.max(s, d.h), 0, 0
end
S.seen={}
function M:Candidates(emit)
    for _,c in ipairs(moverCandidates()) do
        local id,d,label=c[1],c[2],c[3]
        AF:Call('Placement','Default',id,d)
        local x,y,w,h,ox,oy=frameRect(id,d)
        emit({id=id,x=x,y=y,w=w,h=h,ox=ox,oy=oy,label=label,name=d.name,exact=true,virtual=d.virtual,shown=d.shown},true)
    end
end
function M:Bounds(id,d) return frameRect(id,d) end
function M:Category(mv)
    if tostring(mv.label):match('^Action Bar') then return 'Action bars' end
    return (AF:Call('Placement','IsHud',mv.id) or AF:Call('Placement','IsReanchored',mv.id)) and 'HUD' or 'Windows'
end
function M:Settings(mv)
    for i, name in ipairs({'shortcut','shortcut_ctrl','shortcut_alt','shortcut_extra','shortcut_sep','shortcut_sep2'}) do
        if mv.id == D[name] then return 'Bar' .. i end
    end
    local own=({[D.basic_status or -1]='UnitFramePlayer',[D.target or -1]='UnitFrameTarget',
        [D.target_assist or -1]='UnitFrameTargetoftarget',[D.party or -1]='UnitFrameParty',
        [D.chat or -1]='Chat',[D.chat_input or -1]='Chat',[D.exp_bar or -1]='General',[D.start or -1]='Shortcuts'})[mv.id]
    return own or (self:Category(mv)=='Action bars' and 'Action Bars') or (mv.id==D.radar and 'Minimap') or 'Movers'
end
function M:Move(mv,tx,ty,sw,sh)
    local rx,ry=tx-(mv.ox or 0),ty-(mv.oy or 0)
    local d=mv.id<PLUGIN_FIRST and dialog(mv.id)
    local k=key(mv.id);local previous,binding,saveAt=AF.Movers:GetKey(k),AF.Movers:GetBinding(k),AF.DB:SaveAt()
    remember(mv.id,d or (mv.name and {name=mv.name}),rx,ry,sw,sh)
    local placed=true
    if mv.id==D.radar then AF:Send('HoldLayout')
    elseif d then
        if AF:Call('Placement','IsReanchored',mv.id) then placed=AFCore.Pin(mv.id,rx,ry)
        else placed=AFCore.Move(mv.id,rx,ry) end
    end
    if not placed then
        AF.Movers:SetKey(k,previous);AF.Movers:SetBinding(k,binding);AF.DB:SetSaveAt(saveAt)
        if not S.moveError then DisplayChatMessage(0,'Aetherfall: this window could not be moved; its saved position was kept.',.91,.63,.56) end
        S.moveError=mv.label;return false
    end
    if d and AF:Call('Placement','IsReanchored',mv.id) then AF:Send('NativePinHeld',mv.id) end
    if mv.id==D.target or mv.id==D.target_assist then AF:Send('HoldLayout') end
end
function M:AfterMove(mv)
    if mv.id==D.target then AF:Call('TargetDetails','Update') end
end
function M:Reset(mv,sw,sh)
    local def=AF:Call('Placement','Default',mv.id)
    if mv.id==D.radar then AF:Send('HoldLayout')
    elseif def then
        local x,y=def[1]*sw,def[2]*sh;AFCore.Move(mv.id,x,y)
        AF:Send('MoverGeometry',mv.id,x+(mv.ox or 0),y+(mv.oy or 0),mv.w,mv.h,nil,nil,false,true)
    end
end
function M:NoteShown()
    AF:Call('Tooltip','Find')
    for id=1,PLUGIN_FIRST-1 do
        local d=not S.seen[id] and dialog(id)
        if d and d.shown and nativeMovable(d) and d.w>0 and d.h>0 then S.seen[id]=d.name end
    end
end
function M:Diagnostics() return AF.Copy(S) end
AF.Movers:Register('native',{owner='NativeFrames',order=10,first=1,last=PLUGIN_FIRST-1,candidates='Candidates',bounds='Bounds',move='Move',afterMove='AfterMove',reset='Reset',classify='Category',settings='Settings'})
M:Phase('discover',function(self) if AF:Call('Lifecycle','IsSettled') and AF:Call('Lifecycle','Ticks')%40==0 then self:NoteShown() end end)
