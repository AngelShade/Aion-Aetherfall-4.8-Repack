-- Native companion orders. The server owns eligibility, targets and party slots.
PlayerBotBarState = { mode=0, ready=false }
local B=PlayerBotBarState
local function widget(name) return _G['PlayerBotBar'..name] end
local function visible(name,value) if value then widget(name):Show() else widget(name):Hide() end end
function PlayerBotBar_Layout()
    g_AddonName='RelicCalc'
    local x,y=PlayerBotBar:GetRect()
    local scale=GetUIScale() or 1
    -- Child frames start at the native dialog's content origin, which already
    -- includes its title and skin margins. Do not add the title a second time.
    PlayerBotBar:SetRect(x/scale,y/scale,B.mode==2 and 44 or 400,B.mode==2 and 52 or (B.mode==1 and 168 or 70))
    PlayerBotBar:SetText(B.mode==2 and '' or 'Companions')
    PlayerBotBar:SetTitleHeight(B.mode==2 and 10 or 22)
    visible('Icon',B.mode==2)
    for _,name in ipairs({'Attack','Follow','Stay','Summon','More','Hide'}) do visible(name,B.mode~=2) end
    for _,name in ipairs({'Guard','Passive','Manage','Circle','Box','Line','Spread','Hint'}) do visible(name,B.mode==1) end
    widget('More'):SetText(B.mode==1 and '-' or '+')
    widget('More'):SetTooltip(B.mode==1 and 'Collapse party commands' or 'Expand party commands')
    if B.loaded then widget('Mode'):SetText(tostring(B.mode)) end
end
function PlayerBotBar_Click(name)
    g_AddonName='RelicCalc'
    if name=='PlayerBotBarHide' then B.previous=B.mode;B.mode=2;PlayerBotBar_Layout();return end
    if name=='PlayerBotBarIcon' then B.mode=B.previous or 0;PlayerBotBar_Layout();return end
    if name=='PlayerBotBarMore' then B.mode=B.mode==1 and 0 or 1;PlayerBotBar_Layout();return end
    if name=='PlayerBotBarManage' then PrivateCompanions_Open();return end
    local command=({PlayerBotBarAttack='attack',PlayerBotBarFollow='follow',PlayerBotBarStay='stay',PlayerBotBarSummon='summon',PlayerBotBarGuard='guard',PlayerBotBarPassive='passive',PlayerBotBarCircle='formation_circle',PlayerBotBarBox='formation_box',PlayerBotBarLine='formation_line',PlayerBotBarSpread='formation_spread'})[name]
    if command and B.ready and widget('Tx'):GetText()=='' then widget('Tx'):SetText(command) end
end
function PlayerBotBar_Open()
    g_AddonName='RelicCalc'
    PlayerBotBar:Show();B.mode=B.mode==2 and (B.previous or 0) or B.mode;PlayerBotBar_Layout()
end
function PlayerBotBar_OnEvent(event)
    g_AddonName='RelicCalc'
    if event=='PLAYER_ENTERING_WORLD' then PlayerBotBar:Show();PlayerBotBar_Layout()
    elseif event=='ADDON_TIMER' then
        local meta=widget('Meta'):GetText()
        local width,height,mode,ready=meta:match('^(%d+)|(%d+)|(%d+)|(%d+)$')
        if width then
            if not B.loaded then B.loaded=true;B.mode=tonumber(mode);PlayerBotBar_Layout() end
            B.ready=ready=='1'
        else B.ready=false end
        for _,name in ipairs({'Attack','Follow','Stay','Summon','Guard','Passive','Circle','Box','Line','Spread'}) do widget(name):Enable(B.ready) end
        local result=widget('Rx'):GetText()
        if result~='' then widget('Rx'):SetText('');if result~='sent' then DEFAULT_CHAT_DIALOG:AddMessage(result,1,.7,.3) end end
        -- RelicCalc has one shared addon timer, already maintained by Wardrobe.
        -- Do not change its .05 second interval or reset its timer here.
    end
end
function PlayerBotBar_OnLoad()
    g_AddonName='RelicCalc'
    B.loaded=false;B.ready=false;widget('Mode'):SetText('');widget('Tx'):SetText('')
    PlayerBotBar:RegisterEvent('PLAYER_ENTERING_WORLD');PlayerBotBar:RegisterEvent('ADDON_TIMER')
    SlashCmdList['PLAYERBOTBAR']=PlayerBotBar_Open;SLASH_PLAYERBOTBAR1='/botbar'
    for _,name in ipairs({'Attack','Follow','Stay','Summon','Guard','Passive','Circle','Box','Line','Spread'}) do widget(name):Enable(false) end
    PlayerBotBar:Hide();PlayerBotBar_Layout()
    widget('Icon'):SetTooltip('Show companion commands. Drag the top grip to move this icon.')
    widget('Hide'):SetTooltip('Hide commands in a movable icon')
    widget('Attack'):SetTooltip('All your companions: attack your selected hostile PvE target')
    widget('Follow'):SetTooltip('All your companions: follow you')
    widget('Stay'):SetTooltip('All your companions: hold position; skills remain available')
    widget('Summon'):SetTooltip('Summon all your active companions near you while out of combat')
    widget('Guard'):SetTooltip('All your companions: guard this position')
    widget('Passive'):SetTooltip('All your companions: stop attacking and follow without casting')
    widget('Circle'):SetTooltip('Circle formation around you while following; regroup after combat')
    widget('Box'):SetTooltip('Box formation around you while following; regroup after combat')
    widget('Line'):SetTooltip('Companions on both sides; an odd companion covers the front')
    widget('Spread'):SetTooltip('A wider circle around you while following; regroup after combat')
    widget('Manage'):SetTooltip('Open the companion roster and individual settings')
end
