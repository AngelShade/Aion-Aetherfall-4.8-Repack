-- Native companion orders. The server owns eligibility, targets and party slots.
PlayerBotBarState = { mode=0, ready=false }
local B=PlayerBotBarState
local function widget(name) return _G['PlayerBotBar'..name] end
local function visible(name,value) if value then widget(name):Show() else widget(name):Hide() end end
function PlayerBotBar_ApplyVisibility()
    local identity = tostring(GetServerName() or '') .. ':' .. tostring(UnitName('player') or '')
    local settingsReady = not AFUI or (AFUI.ready and AFUI.connected and AFUI.identity == identity)
    local allowed = settingsReady and not (AFUI and AFUI.Get('botbar') == 0)
    local show = allowed and B.loaded and B.ready and not B.waiting
    if B.shown == show then return end
    B.shown = show
    if show then PlayerBotBar:Show() else PlayerBotBar:Hide() end
end
local function rememberMode()
    if AFUI and AFUI.RememberCompanionMode then AFUI.RememberCompanionMode(B.mode) end
end
function PlayerBotBar_Layout()
    g_AddonName='RelicCalc'
    -- Child frames start at the native dialog's content origin, which already
    -- includes its title and skin margins. Do not add the title a second time.
    -- Resize in place: reading and rescaling the origin on every mode change
    -- makes the bar and icon drift at non-default UI scales.
    PlayerBotBar:SetSize(B.mode==2 and 44 or 400,B.mode==2 and 52 or (B.mode==1 and 240 or 70))
    PlayerBotBar:SetText(B.mode==2 and '' or 'Companions')
    PlayerBotBar:SetTitleHeight(B.mode==2 and 10 or 22)
    visible('Icon',B.mode==2)
    for _,name in ipairs({'Attack','Follow','Stay','Summon','More','Hide'}) do visible(name,B.mode~=2) end
    for _,name in ipairs({'Guard','Passive','Manage','Circle','Box','Line','Spread','Close','Normal','Far','Pull','Kill','CC','Clear','Hint'}) do visible(name,B.mode==1) end
    widget('More'):SetText(B.mode==1 and '-' or '+')
    widget('More'):SetTooltip(B.mode==1 and 'Collapse party commands' or 'Expand party commands')
    if B.loaded then widget('Mode'):SetText(tostring(B.mode)) end
end
function PlayerBotBar_Click(name)
    g_AddonName='RelicCalc'
    if name=='PlayerBotBarHide' then B.previous=B.mode;B.mode=2;PlayerBotBar_Layout();rememberMode();return end
    if name=='PlayerBotBarIcon' then B.mode=B.previous or 0;PlayerBotBar_Layout();rememberMode();return end
    if name=='PlayerBotBarMore' then B.mode=B.mode==1 and 0 or 1;PlayerBotBar_Layout();rememberMode();return end
    if name=='PlayerBotBarManage' then PrivateCompanions_Open();return end
    local command=({PlayerBotBarAttack='attack',PlayerBotBarFollow='follow',PlayerBotBarStay='stay',PlayerBotBarSummon='summon',PlayerBotBarGuard='guard',PlayerBotBarPassive='passive',PlayerBotBarCircle='formation_circle',PlayerBotBarBox='formation_box',PlayerBotBarLine='formation_line',PlayerBotBarSpread='formation_spread',PlayerBotBarClose='distance_close',PlayerBotBarNormal='distance_normal',PlayerBotBarFar='distance_far',PlayerBotBarPull='pull',PlayerBotBarKill='mark_kill',PlayerBotBarCC='mark_cc',PlayerBotBarClear='mark_clear'})[name]
    if command and B.ready and widget('Tx'):GetText()=='' then widget('Tx'):SetText(command) end
end
function PlayerBotBar_Open()
    g_AddonName='RelicCalc'
    B.openRequested=true
end
-- Find Group owns the native button and contained terms panel. Its own command
-- handler receives each click; Lua only binds a newly created native window.
local function hireTermsTick()
    B.hireTick=(B.hireTick or 0)+1
    if B.hireTick%5~=0 then return end
    if AetherfallCore then AetherfallCore('hireterms') end
end
-- Companion Recruitment pull. The browser's software renderer cannot animate a full window, so the
-- rendered frames play here as native textures, and the bridge puts the recruit's own 3D model on the
-- stage. The result is already saved; nothing here decides it.
local PULL_ROOT='Textures/UI/AetherfallSummon/'
local PULL_CHARGE,PULL_BURST,PULL_HOLD=38,33,96
local PULL_FLASH,PULL_SPARKS=12,24
local PULL_PANEL_IN=18               -- finite 0.9s native frame entrance, after the burst
local PULL_REVEAL=3                   -- frame of the burst, while the hall is still white
local PULL_XML=[==[<Dialog name="PlayerBotPull" frame="0,0,1280,960" flag="not_visible;not_movable">
<Widget type="image" name="PlayerBotPullStage" frame="0,0,1280,960" flag="not_active"/>
<Widget type="image" name="PlayerBotPullModel" frame="0,0,300,450" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullSparks" frame="0,0,1280,960" flag="not_active;not_visible"/>
<Widget type="container" name="PlayerBotPullResult" frame="0,0,430,760" flag="not_active;not_visible">
<Widget type="image" name="PlayerBotPullPanel" frame="0,0,430,760" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullTitle" frame="0,0,350,52" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullMedal" frame="0,0,76,76" flag="not_active;not_visible"/>
<Widget type="static" name="PlayerBotPullName" frame="0,0,280,44" text="" font="credit_team" flag="not_active;not_visible;truncate;not_multiline;not_wordwrap"/>
<Widget type="image" name="PlayerBotPullClass" frame="0,0,230,40" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullGearRow" frame="0,0,350,76" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullGearIcon" frame="0,0,58,58" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullGearLabel" frame="0,0,180,32" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullGear" frame="0,0,170,40" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullRankRow" frame="0,0,350,76" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullRankIcon" frame="0,0,58,58" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullRankLabel" frame="0,0,180,32" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullRank" frame="0,0,170,40" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullDivider" frame="0,0,320,70" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullNote" frame="0,0,340,88" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullPanelFx" frame="0,0,430,760" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullContinueFace" frame="0,0,340,66" flag="not_active;not_visible"/>
<Widget type="button" name="PlayerBotPullContinue" frame="0,0,340,66" text="" flag="not_visible"><Scripts><OnClick>PlayerBotPull_Finish();</OnClick></Scripts></Widget>
</Widget>
<Widget type="image" name="PlayerBotPullSkipFace" frame="0,0,180,44" flag="not_active;not_visible"/>
<Widget type="button" name="PlayerBotPullSkip" frame="0,0,180,44" text="" flag="not_visible"><Scripts><OnClick>PlayerBotPull_Finish();</OnClick></Scripts></Widget>
</Dialog>]==]
local PULL_PANEL={'Panel','Title','Medal','Name','Class','Divider','Note'}
local PULL_RESULTS={'GearRow','GearIcon','GearLabel','Gear','RankRow','RankIcon','RankLabel','Rank','ContinueFace'}
local function pullImage(widget,name) widget:SetUIImage(0,PULL_ROOT..name) end
local function pullVisible(name,value) local w=_G['PlayerBotPull'..name];if value then w:Show() else w:Hide() end end
local function pullFrame(clip,index) return string.format('%s-%03d.dds',clip,index) end
function PlayerBotPull_Finish()
    g_AddonName='RelicCalc'
    B.pull=nil;if _G.PlayerBotPull then PlayerBotPull:Hide() end
end
local function pullLayout()
    local scale=GetUIScale()
    local width,height=(B.screenWidth or 1920)/scale,(B.screenHeight or 1080)/scale
    local unit=height/1080;local left,top=math.min(width*.70,width-500*unit),130*unit
    local function place(part,x,y,w,h) _G['PlayerBotPull'..part]:SetRect(x*unit,y*unit,w*unit,h*unit) end
    PlayerBotPull:SetRect(0,0,width,height);PlayerBotPullStage:SetRect(0,0,width,height);PlayerBotPullSparks:SetRect(0,0,width,height)
    -- The owner's 1080p capture puts feet 120px above the view's bottom.
    -- Keep its size, centre x on the arena, and lower that bottom by .03h.
    PlayerBotPullModel:SetRect(width*.505-height*.27,height*.08,height*.54,height*.74)
    PlayerBotPullResult:SetRect(left,top,430*unit,760*unit)
    place('PanelFx',-24,-24,478,808)
    place('Panel',0,0,430,760);place('Title',40,112,350,52)
    place('Medal',45,185,76,76);place('Name',135,190,250,44);place('Class',135,232,235,40)
    for _,row in ipairs({{'Gear',300},{'Rank',400}}) do
        local part,y=row[1],row[2]
        place(part..'Row',40,y,350,80);place(part..'Icon',50,y+11,58,58)
        place(part..'Label',118,y+7,220,32);place(part,118,y+36,230,38)
    end
    place('Divider',60,500,310,70);place('Note',45,576,340,70);place('ContinueFace',45,654,340,66);place('Continue',45,654,340,66)
    PlayerBotPullSkip:SetRect(width-218*unit,height-68*unit,190*unit,44*unit)
    PlayerBotPullSkipFace:SetRect(width-218*unit,height-68*unit,190*unit,44*unit)
    if B.pull then
        B.pull.width=width;B.pull.height=height;B.pull.buttonKey=nil
        B.pull.panelLeft=left;B.pull.panelTop=top;B.pull.unit=unit
        B.pull.skipRect={(width-218*unit)*scale,(height-68*unit)*scale,190*unit*scale,44*unit*scale}
        B.pull.continueRect={(left+45*unit)*scale,(top+654*unit)*scale,340*unit*scale,66*unit*scale}
    end
end
local function pullButtonState(P)
    local finished=P.complete
    local part,clip,r=finished and 'Continue' or 'Skip',finished and 'continue' or 'skip',finished and P.continueRect or P.skipRect
    local x,y,down
    if AFCore and AFCore.Cursor then x,y,down=AFCore.Cursor() end
    local inside=x and r and x>=r[1] and y>=r[2] and x<r[1]+r[3] and y<r[2]+r[4]
    local state=inside and (down and 2 or 1) or 0
    local key=part..state
    -- Keep decoration in an inert image. A native button's hover/pressed
    -- substate otherwise selects an unset image and makes the face disappear.
    if key~=P.buttonKey then P.buttonKey=key;pullImage(_G['PlayerBotPull'..part..'Face'],clip..'-'..state..'.dds') end
    -- Actual clicks are owned by the existing native modal input path;
    -- the timer only updates the face. AFCore.Cursor's public wrapper returns
    -- x,y,down,shift (it normalizes the six native values internally).
end
local function pullStart(argument)
    g_AddonName='RelicCalc'
    local class,gear,rank,name=tostring(argument or ''):match('^(%S+) (%S+) (%S+) (.+)$')
    if not class or not _G.PlayerBotPull then return end
    B.pull={clip='charge',frame=0,warmup=2,fade=0}
    pullLayout();PlayerBotPullName:SetText(name)
    for _,part in ipairs(PULL_PANEL) do pullVisible(part,false) end
    for _,part in ipairs(PULL_RESULTS) do pullVisible(part,false) end
    for _,part in ipairs({'Result','Model','Sparks','PanelFx','Continue','Skip','SkipFace'}) do pullVisible(part,false) end
    for part,texture in pairs({Panel='panel',Title='title',Medal='medal-'..class,Class='class-'..class,GearRow='row',RankRow='row',GearIcon='gear-icon',RankIcon='rank-icon',GearLabel='gear-label',RankLabel='rank-label',Gear='value-'..gear,Rank='value-'..rank,Divider='divider',Note='note'}) do pullImage(_G['PlayerBotPull'..part],texture..'.dds') end
    pullImage(PlayerBotPullSkipFace,'skip-0.dds');pullImage(PlayerBotPullContinueFace,'continue-0.dds')
    -- Populate the hidden layout first, then let native queued image/rect calls
    -- drain before the one complete screen is shown and faded in.
    pullImage(PlayerBotPullStage,pullFrame('charge',0));PlayerBotPull:SetAlpha(0,true)
end
local function pullTick()
    B.pullWait=(B.pullWait or 0)+1
    if not B.pullBuilt then
        if B.pullWait<60 then return end
        B.pullBuilt=true;LoadXMLWidget(lxp.lom.parse(PULL_XML));return
    end
    local P=B.pull;if not P then return end
    if not B.ready or (AFUI and not AFUI.connected) then PlayerBotPull_Finish();return end
    if P.warmup then
        P.warmup=P.warmup-1
        if P.warmup<=0 then
            P.warmup=nil;PlayerBotPull:Show();pullVisible('SkipFace',true);pullVisible('Skip',true)
        end
        return
    end
    if PlayerBotPull:IsVisible()==0 then PlayerBotPull_Finish();return end
    if P.width~=(B.screenWidth or 1920)/GetUIScale() or P.height~=(B.screenHeight or 1080)/GetUIScale() then pullLayout() end
    if P.fade<6 then P.fade=P.fade+1;PlayerBotPull:SetAlpha(P.fade/6,true) end
    P.frame=P.frame+1
    if P.clip=='charge' then
        if P.frame>=PULL_CHARGE then P.clip='burst';P.frame=0 end
    elseif P.clip=='burst' then
        if P.frame==PULL_REVEAL then pullVisible('Model',true);pullVisible('Sparks',true);P.reveal=0 end
        if P.frame>=PULL_BURST then
            P.clip='hold';P.frame=0;P.panelFrame=0
            for _,part in ipairs(PULL_PANEL) do pullVisible(part,true) end
            for _,part in ipairs(PULL_RESULTS) do pullVisible(part,true) end
            PlayerBotPullResult:SetAlpha(0,true);pullVisible('Result',true);pullVisible('PanelFx',true)
        end
    elseif P.frame>=PULL_HOLD then P.frame=0 end
    pullImage(PlayerBotPullStage,pullFrame(P.clip,P.frame))
    if P.reveal then
        -- A wash of light covers the model's arrival, then the embers keep drifting over it.
        if P.reveal<PULL_FLASH then pullImage(PlayerBotPullSparks,pullFrame('flash',P.reveal))
        else pullImage(PlayerBotPullSparks,pullFrame('sparks',(P.reveal-PULL_FLASH)%PULL_SPARKS)) end
        P.reveal=P.reveal+1
    end
    if P.panelFrame then
        local t=P.panelFrame/(PULL_PANEL_IN-1);local ease=1-(1-t)^3
        PlayerBotPullResult:SetRect(P.panelLeft+(1-ease)*72*P.unit,P.panelTop,430*P.unit,760*P.unit)
        PlayerBotPullResult:SetAlpha(ease,true)
        pullImage(PlayerBotPullPanelFx,pullFrame('panel-in',P.panelFrame))
        P.panelFrame=P.panelFrame+1
        if P.panelFrame>=PULL_PANEL_IN then
            P.panelFrame=nil;P.complete=true;pullVisible('PanelFx',false)
            pullVisible('Continue',true);pullVisible('Skip',false);pullVisible('SkipFace',false)
        end
    end
    pullButtonState(P)
end
function PlayerBotBar_OnEvent(event)
    g_AddonName='RelicCalc'
    if event=='PLAYER_ENTERING_WORLD' then
        PlayerBotPull_Finish()
        -- Keep the dialog hidden until the native bridge supplies its saved
        -- mode and the current character's interface preference has loaded.
        B.waiting=true;B.ready=false;B.shown=nil;PlayerBotBar:Hide()
        B.matchId=nil
    elseif event=='ADDON_TIMER' then
        local meta=widget('Meta'):GetText()
        local width,height,mode,ready=meta:match('^(%d+)|(%d+)|(%d+)|(%d+)$')
        if width then
            if not B.loaded then B.loaded=true;B.mode=tonumber(mode);PlayerBotBar_Layout() end
            B.ready=ready=='1';B.screenWidth=tonumber(width);B.screenHeight=tonumber(height)
        else B.ready=false end
        if B.ready and (not AFUI or (AFUI.ready and AFUI.connected)) then B.waiting=false end
        if AFUI and AFUI.ready and (B.identity~=AFUI.identity or B.profile~=AFUI.Get('profile')) then
            B.identity=AFUI.identity;B.profile=AFUI.Get('profile')
            local saved=AFUI.Get('botbarmode')
            if saved and saved>=0 then B.mode=saved;PlayerBotBar_Layout() end
        end
        if B.openRequested and B.loaded and not B.waiting then
            if AFUI and AFUI.ready and AFUI.ShowCompanionBar then AFUI.ShowCompanionBar() end
            B.openRequested=nil;B.mode=B.mode==2 and (B.previous or 0) or B.mode;PlayerBotBar_Layout();rememberMode()
        end
        PlayerBotBar_ApplyVisibility()
        for _,name in ipairs({'Attack','Follow','Stay','Summon','Guard','Passive','Circle','Box','Line','Spread','Close','Normal','Far','Pull','Kill','CC','Clear'}) do widget(name):Enable(B.ready) end
        local result=widget('Rx'):GetText()
        if result~='' then widget('Rx'):SetText('');if result~='sent' then DEFAULT_CHAT_DIALOG:AddMessage(result,1,.7,.3) end end
        -- The terms are optional; a failure here must never stop the command bar.
        if not B.hireFailed and not pcall(hireTermsTick) then B.hireFailed=true end
        if not pcall(pullTick) then PlayerBotPull_Finish() end
        -- RelicCalc has one shared addon timer, already maintained by Wardrobe.
        -- Do not change its .05 second interval or reset its timer here.
    end
end
function PlayerBotBar_OnLoad()
    g_AddonName='RelicCalc'
    PlayerBotPull_Finish()
    B.loaded=false;B.ready=false;B.waiting=true;B.shown=nil;widget('Mode'):SetText('');widget('Tx'):SetText('')
    PlayerBotBar:RegisterEvent('PLAYER_ENTERING_WORLD');PlayerBotBar:RegisterEvent('ADDON_TIMER')
    SlashCmdList['PLAYERBOTBAR']=PlayerBotBar_Open;SLASH_PLAYERBOTBAR1='/botbar'
    SlashCmdList['PLAYERBOTPULL']=pullStart;SLASH_PLAYERBOTPULL1='/companionpull'
    SlashCmdList['PLAYERBOTPULLCLOSE']=PlayerBotPull_Finish;SLASH_PLAYERBOTPULLCLOSE1='/companionpullclose'
    for _,name in ipairs({'Attack','Follow','Stay','Summon','Guard','Passive','Circle','Box','Line','Spread','Close','Normal','Far','Pull','Kill','CC','Clear'}) do widget(name):Enable(false) end
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
    widget('Close'):SetTooltip('Set follow spacing to 2 m for all active private companions; keep attack spacing')
    widget('Normal'):SetTooltip('Set follow spacing to 4 m for all active private companions; keep attack spacing')
    widget('Far'):SetTooltip('Set follow spacing to 8 m for all active private companions; keep attack spacing')
    widget('Manage'):SetTooltip('Open the companion roster and individual settings')
    widget('Pull'):SetTooltip('Your tank pulls your selected monster back to the party')
    widget('Kill'):SetTooltip('Mark your selected monster: every companion focuses it')
    widget('CC'):SetTooltip('Mark your selected monster for sleep, paralysis or fear; nobody hits it')
    widget('Clear'):SetTooltip('Remove the kill and crowd-control marks')
end
