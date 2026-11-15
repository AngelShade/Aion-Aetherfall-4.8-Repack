-- Native companion orders. The server owns eligibility, targets and party slots.
PlayerBotBarState = { mode=0, ready=false }
local B=PlayerBotBarState
local function L(text) return AFTranslate and AFTranslate(text) or text end
local function widget(name) return _G['PlayerBotBar'..name] end
local function visible(name,value) if value then widget(name):Show() else widget(name):Hide() end end
local BAR_LABELS={
    Attack='Attack',Follow='Follow',Stay='Stay',Summon='Summon',Guard='Guard',Passive='Passive',
    Manage='Companions',Circle='Circle',Box='Box',Line='Line',Spread='Spread',Close='Close: 2 m',
    Normal='Normal: 4 m',Far='Far: 8 m',Pull='Pull',Kill='Kill mark',CC='CC mark',
    Clear='Clear marks',Hint='Travel slots by role; regroup after combat',
}
local function localizeBarLabels()
    -- The installed XML still supplies its English captions when no dictionary is present.
    for part,source in pairs(BAR_LABELS) do
        local caption=L(source)
        if caption~=source then widget(part):SetText(caption) end
    end
end
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
    PlayerBotBar:SetText(B.mode==2 and '' or L('Companions'))
    PlayerBotBar:SetTitleHeight(B.mode==2 and 10 or 22)
    visible('Icon',B.mode==2)
    for _,name in ipairs({'Attack','Follow','Stay','Summon','More','Hide'}) do visible(name,B.mode~=2) end
    for _,name in ipairs({'Guard','Passive','Manage','Circle','Box','Line','Spread','Close','Normal','Far','Pull','Kill','CC','Clear','Hint'}) do visible(name,B.mode==1) end
    widget('More'):SetText(B.mode==1 and '-' or '+')
    widget('More'):SetTooltip(L(B.mode==1 and 'Collapse party commands' or 'Expand party commands'))
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
local PULL_SHARED=18                  -- identical first .9s; no early rarity tell
local PULL_TIERS={{charge=PULL_CHARGE,burst=PULL_BURST},{charge=44,burst=33},{charge=56,burst=36},{charge=70,burst=38}}
local PULL_VALUE={POOR=1,ORDINARY=1,GOOD=2,GREAT=3,EXCELLENT=4}
local PULL_VALUE_NAMES={POOR='Poor',ORDINARY='Ordinary',GOOD='Good',GREAT='Great',EXCELLENT='Excellent'}
local PULL_CLASS_NAMES={
    TEMPLAR='Templar',GLADIATOR='Gladiator',ASSASSIN='Assassin',RANGER='Ranger',
    SORCERER='Sorcerer',SPIRIT_MASTER='Spiritmaster',CLERIC='Cleric',CHANTER='Chanter',
    GUNNER='Gunner',RIDER='Aethertech',BARD='Songweaver',
}
local PULL_STAMP=12                   -- finite .6s per roll, after the whole panel settles
local PULL_XML=[==[<Dialog name="PlayerBotPull" frame="0,0,1280,960" flag="not_visible;not_movable">
<Widget type="image" name="PlayerBotPullStage" frame="0,0,1280,960" flag="not_active"/>
<Widget type="image" name="PlayerBotPullModel" frame="0,0,300,450" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullSparks" frame="0,0,1280,960" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullFlourish" frame="0,0,1280,960" flag="not_active;not_visible"/>
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
<Widget type="image" name="PlayerBotPullGearStamp" frame="0,0,280,76" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullRankRow" frame="0,0,350,76" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullRankIcon" frame="0,0,58,58" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullRankLabel" frame="0,0,180,32" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullRank" frame="0,0,170,40" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullRankStamp" frame="0,0,280,76" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullDivider" frame="0,0,320,70" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullNote" frame="0,0,340,88" flag="not_active;not_visible"/>
<Widget type="static" name="PlayerBotPullTitleText" frame="0,0,350,52" font="credit_team" halign="center" valign="middle" text_shade="True" flag="not_active;not_visible;truncate;not_multiline;not_wordwrap"/>
<Widget type="static" name="PlayerBotPullClassText" frame="0,0,230,40" font="ui_default" valign="middle" text_color="0.75,0.81,0.91,1" flag="not_active;not_visible;truncate;not_multiline;not_wordwrap"/>
<Widget type="static" name="PlayerBotPullGearLabelText" frame="0,0,180,32" font="ui_default" valign="middle" flag="not_active;not_visible;truncate;not_multiline;not_wordwrap"/>
<Widget type="static" name="PlayerBotPullGearText" frame="0,0,170,40" font="ui_default" valign="middle" flag="not_active;not_visible;truncate;not_multiline;not_wordwrap"/>
<Widget type="static" name="PlayerBotPullRankLabelText" frame="0,0,180,32" font="ui_default" valign="middle" flag="not_active;not_visible;truncate;not_multiline;not_wordwrap"/>
<Widget type="static" name="PlayerBotPullRankText" frame="0,0,170,40" font="ui_default" valign="middle" flag="not_active;not_visible;truncate;not_multiline;not_wordwrap"/>
<Widget type="static" name="PlayerBotPullNoteText" frame="0,0,340,88" font="ui_default" halign="center" valign="middle" text_color="0.78,0.84,0.91,1" flag="not_active;not_visible;truncate;not_multiline;not_wordwrap"/>
<Widget type="image" name="PlayerBotPullPanelFx" frame="0,0,430,760" flag="not_active;not_visible"/>
<Widget type="image" name="PlayerBotPullContinueFace" frame="0,0,340,66" flag="not_active;not_visible"/>
<Widget type="button" name="PlayerBotPullContinue" frame="0,0,340,66" text="" font="ui_default" halign="center" valign="middle" flag="not_visible"><Scripts><OnClick>PlayerBotPull_Finish();</OnClick></Scripts></Widget>
</Widget>
<Widget type="image" name="PlayerBotPullSkipFace" frame="0,0,180,44" flag="not_active;not_visible"/>
<Widget type="button" name="PlayerBotPullSkip" frame="0,0,180,44" text="" font="ui_default" halign="center" valign="middle" flag="not_visible"><Scripts><OnClick>PlayerBotPull_Finish();</OnClick></Scripts></Widget>
</Dialog>]==]
local PULL_PANEL={'Panel','Title','Medal','Name','Class','Divider','Note'}
local PULL_RESULTS={'GearRow','GearIcon','GearLabel','Gear','RankRow','RankIcon','RankLabel','Rank','ContinueFace'}
local function pullImage(widget,name) widget:SetUIImage(0,PULL_ROOT..name) end
local function pullVisible(name,value) local w=_G['PlayerBotPull'..name];if value then w:Show() else w:Hide() end end
local function pullFrame(clip,index) return string.format('%s-%03d.dds',clip,index) end
local PULL_COLOURS={POOR='0.78,0.80,0.83,1',ORDINARY='0.93,0.90,0.81,1',
    GOOD='0.62,0.91,0.74,1',GREAT='0.49,0.90,0.93,1',EXCELLENT='0.83,0.64,1,1'}
local PULL_TIER_COLOURS={[1]='0.95,0.86,1,1',[2]='0.55,0.94,0.66,1',[3]='0.47,0.78,1,1',[4]='1,0.79,0.42,1'}
local function preparePullCaptions(P,class)
    local captions={Title='Companion recruited',Class=PULL_CLASS_NAMES[class],GearLabel='Auto-equip',
        Gear=PULL_VALUE_NAMES[P.gear],RankLabel='Combat rank',Rank=PULL_VALUE_NAMES[P.rank],
        Note='Your journey together begins.'}
    for part,source in pairs(captions) do
        local text=L(source)
        if text~=source then
            P.captions[part]=true
            local label=_G['PlayerBotPull'..part..'Text']
            label:SetText(text)
            local color=part=='Title' and PULL_TIER_COLOURS[P.tier] or PULL_COLOURS[part=='Gear' and P.gear or part=='Rank' and P.rank or '']
            if color then label:SetAttr('textColor',color) end
        end
    end
    for part,source in pairs({Continue='Continue',Skip='Skip reveal'}) do
        local text=L(source)
        P.captions[part]=text~=source
        _G['PlayerBotPull'..part]:SetText(P.captions[part] and text or '')
    end
end
local function showPullResult(P)
    for _,parts in ipairs({PULL_PANEL,PULL_RESULTS}) do
        for _,part in ipairs(parts) do
            pullVisible(P.captions[part] and part..'Text' or part,true)
        end
    end
end
function PlayerBotPull_Finish()
    g_AddonName='RelicCalc'
    B.pull=nil;if _G.PlayerBotPull then PlayerBotPull:Hide() end
    if _G.PlayerBotPullFlourish then
        for _,part in ipairs({'Sparks','Flourish','PanelFx','GearStamp','RankStamp'}) do pullVisible(part,false) end
    end
end
local function pullLayout()
    local scale=GetUIScale()
    local width,height=(B.screenWidth or 1920)/scale,(B.screenHeight or 1080)/scale
    local unit=height/1080;local left,top=math.min(width*.70,width-500*unit),130*unit
    local function place(part,x,y,w,h)
        _G['PlayerBotPull'..part]:SetRect(x*unit,y*unit,w*unit,h*unit)
        local label=_G['PlayerBotPull'..part..'Text']
        if label then label:SetRect(x*unit,y*unit,w*unit,h*unit) end
    end
    PlayerBotPull:SetRect(0,0,width,height);PlayerBotPullStage:SetRect(0,0,width,height);PlayerBotPullSparks:SetRect(0,0,width,height)
    PlayerBotPullFlourish:SetRect(0,0,width,height)
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
        local value=B.pull and B.pull[string.lower(part)] or 'POOR'
        local strength=({POOR=.64,ORDINARY=.76,GOOD=.9,GREAT=1.05,EXCELLENT=1.2})[value] or .64
        place(part..'Stamp',233-112*strength,y+55-32*strength,224*strength,64*strength)
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
    if key~=P.buttonKey then
        P.buttonKey=key
        local face=_G['PlayerBotPull'..part..'Face']
        if P.captions[part] then
            face:SetAlpha(state==2 and .65 or state==1 and 1 or .85)
        else
            pullImage(face,clip..'-'..state..'.dds')
        end
    end
    -- Actual clicks are owned by the existing native modal input path;
    -- the timer only updates the face. AFCore.Cursor's public wrapper returns
    -- x,y,down,shift (it normalizes the six native values internally).
end
local function pullStart(argument)
    g_AddonName='RelicCalc'
    local class,gear,rank,name=tostring(argument or ''):match('^(%S+) (%S+) (%S+) (.+)$')
    if not class or not _G.PlayerBotPull then return end
    local tier=math.max(PULL_VALUE[gear] or 1,PULL_VALUE[rank] or 1)
    B.pull={clip='charge',frame=0,warmup=2,fade=0,tier=tier,gear=gear,rank=rank,captions={},
        timing=PULL_TIERS[tier],double=gear=='EXCELLENT' and rank=='EXCELLENT'}
    pullLayout();PlayerBotPullName:SetText(name)
    for _,parts in ipairs({PULL_PANEL,PULL_RESULTS}) do
        for _,part in ipairs(parts) do
            pullVisible(part,false)
            if _G['PlayerBotPull'..part..'Text'] then pullVisible(part..'Text',false) end
        end
    end
    for _,part in ipairs({'Result','Model','Sparks','Flourish','PanelFx','GearStamp','RankStamp','Continue','Skip','SkipFace'}) do pullVisible(part,false) end
    preparePullCaptions(B.pull,class)
    for part,texture in pairs({Panel=tier>1 and 'tier'..tier..'-panel' or 'panel',Title=tier>1 and 'tier'..tier..'-title' or 'title',Medal='medal-'..class,Class='class-'..class,GearRow='row',RankRow='row',GearIcon='gear-icon',RankIcon='rank-icon',GearLabel='gear-label',RankLabel='rank-label',Gear='value-'..gear,Rank='value-'..rank,Divider='divider',Note='note'}) do pullImage(_G['PlayerBotPull'..part],texture..'.dds') end
    for part,clip in pairs({Skip='skip',Continue='continue'}) do
        local face=_G['PlayerBotPull'..part..'Face']
        pullImage(face,B.pull.captions[part] and 'row.dds' or clip..'-0.dds')
        face:SetAlpha(B.pull.captions[part] and .85 or 1)
    end
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
        if P.frame>=P.timing.charge then P.clip='burst';P.frame=0 end
    elseif P.clip=='burst' then
        if P.frame==PULL_REVEAL then pullVisible('Model',true);pullVisible('Sparks',true);P.reveal=0 end
        if P.frame>=P.timing.burst then
            P.clip='hold';P.frame=0;P.panelFrame=0
            showPullResult(P)
            PlayerBotPullResult:SetAlpha(0,true);pullVisible('Result',true);pullVisible('PanelFx',true)
        end
    elseif P.frame>=PULL_HOLD then P.frame=0 end
    local clip,index=P.clip,P.frame
    if P.tier>1 then
        if clip=='charge' and index>=PULL_SHARED then clip='tier'..P.tier..'-charge';index=index-PULL_SHARED
        elseif clip=='burst' then clip='tier'..P.tier..'-burst' end
    end
    pullImage(PlayerBotPullStage,pullFrame(clip,index))
    if P.reveal then
        -- A wash of light covers the model's arrival, then the embers keep drifting over it.
        if P.reveal<PULL_FLASH then pullImage(PlayerBotPullSparks,pullFrame(P.tier==4 and 'tier4-flash' or 'flash',P.reveal))
        else pullImage(PlayerBotPullSparks,pullFrame(P.tier>1 and 'tier'..P.tier..'-sparks' or 'sparks',(P.reveal-PULL_FLASH)%PULL_SPARKS)) end
        -- A double Excellent earns a brief solar coronation after the white wash.
        -- Eight shared-size frames, held for two ticks each; never another loop.
        if P.double and P.reveal>=PULL_FLASH and P.reveal<PULL_FLASH+16 then
            local n=math.floor((P.reveal-PULL_FLASH)/2)
            if n==0 and not P.flourishFrame then pullVisible('Flourish',true) end
            if n~=P.flourishFrame then P.flourishFrame=n;pullImage(PlayerBotPullFlourish,pullFrame('tier4-double',n)) end
        elseif P.double and P.reveal==PULL_FLASH+16 then pullVisible('Flourish',false) end
        P.reveal=P.reveal+1
    end
    if P.panelFrame then
        local t=P.panelFrame/(PULL_PANEL_IN-1);local ease=1-(1-t)^3
        PlayerBotPullResult:SetRect(P.panelLeft+(1-ease)*72*P.unit,P.panelTop,430*P.unit,760*P.unit)
        PlayerBotPullResult:SetAlpha(ease,true)
        pullImage(PlayerBotPullPanelFx,pullFrame('panel-in',P.panelFrame))
        P.panelFrame=P.panelFrame+1
        if P.panelFrame>=PULL_PANEL_IN then
            P.panelFrame=nil;P.complete=true;P.stampFrame=-1;pullVisible('PanelFx',false)
            pullVisible('Continue',true);pullVisible('Skip',false);pullVisible('SkipFace',false)
        end
    end
    if P.stampFrame then
        P.stampFrame=P.stampFrame+1
        for _,row in ipairs({{'Gear',P.gear,0},{'Rank',P.rank,14}}) do
            local n=P.stampFrame-row[3]
            if n>=0 and n<PULL_STAMP then
                if n==0 then pullVisible(row[1]..'Stamp',true) end
                pullImage(_G['PlayerBotPull'..row[1]..'Stamp'],pullFrame('stamp-tier'..(PULL_VALUE[row[2]] or 1),n))
            elseif n==PULL_STAMP then pullVisible(row[1]..'Stamp',false) end
        end
        if P.stampFrame>=26 then P.stampFrame=nil end
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
            if not B.loaded then B.loaded=true;B.mode=tonumber(mode);localizeBarLabels();PlayerBotBar_Layout() end
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
        if result~='' then widget('Rx'):SetText('');if result~='sent' then DEFAULT_CHAT_DIALOG:AddMessage(L(result),1,.7,.3) end end
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
    widget('Icon'):SetTooltip(L('Show companion commands. Drag the top grip to move this icon.'))
    widget('Hide'):SetTooltip(L('Hide commands in a movable icon'))
    widget('Attack'):SetTooltip(L('All your companions: attack your selected hostile PvE target'))
    widget('Follow'):SetTooltip(L('All your companions: follow you'))
    widget('Stay'):SetTooltip(L('All your companions: hold position; skills remain available'))
    widget('Summon'):SetTooltip(L('Summon all your active companions near you while out of combat'))
    widget('Guard'):SetTooltip(L('All your companions: guard this position'))
    widget('Passive'):SetTooltip(L('All your companions: stop attacking and follow without casting'))
    widget('Circle'):SetTooltip(L('Circle formation around you while following; regroup after combat'))
    widget('Box'):SetTooltip(L('Box formation around you while following; regroup after combat'))
    widget('Line'):SetTooltip(L('Companions on both sides; an odd companion covers the front'))
    widget('Spread'):SetTooltip(L('A wider circle around you while following; regroup after combat'))
    widget('Close'):SetTooltip(L('Set follow spacing to 2 m for all active private companions; keep attack spacing'))
    widget('Normal'):SetTooltip(L('Set follow spacing to 4 m for all active private companions; keep attack spacing'))
    widget('Far'):SetTooltip(L('Set follow spacing to 8 m for all active private companions; keep attack spacing'))
    widget('Manage'):SetTooltip(L('Open the companion roster and individual settings'))
    widget('Pull'):SetTooltip(L('Your tank pulls your selected monster back to the party'))
    widget('Kill'):SetTooltip(L('Mark your selected monster: every companion focuses it'))
    widget('CC'):SetTooltip(L('Mark your selected monster for sleep, paralysis or fear; nobody hits it'))
    widget('Clear'):SetTooltip(L('Remove the kill and crowd-control marks'))
end
