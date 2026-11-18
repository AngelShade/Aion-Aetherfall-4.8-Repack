-- The World Event assistant: Guild Wars 2's event tracker for Aetherfall's world events (server module
-- services/dynamicevents). Shows the event around the player: icon, title, [Group Event], current part,
-- objective with its count, progress bar, time left and the player's medal so far. Reads only
-- AFCore.Event() (Core 1.3.31 event_assistant.h, which asks the server only while the World Event quest is
-- in the journal). Its pieces live on the passive unit-frame canvas AFHFrames (layout/event_assistant.py);
-- this module places, fills and hides only its own pieces and never hides the canvas.
local M=AF:NewModule('EventAssistant',{scope='profile',needs={'Lifecycle'},defaults={
    enabled={true,'bool'},
    -- Offsets from the automatic place (top right, below the minimap), in interface units. A MoveUI box is not used:
    -- the editor's boxes are part of the strictly compared interface state, and these two settings place it as well.
    offsetX={0,'int',-1600,1600},
    offsetY={0,'int',-1000,1000},
}})
local N,K=AF.Native,AF.Skin
local PREFIX='Textures/UI/AetherfallEvents/'
local WIDTH,HEIGHT=300,98
local PARTS={'Panel','BarBack','BarFill','Icon','Medal','Title','Group','Stage','Objective','Count','Timer'}
local S={widgets={},bound=false,shown=false,last=nil}
local MEDALS={G='medal-gold.dds',S='medal-silver.dds',B='medal-bronze.dds'}
local ICONS={kill=true,defend=true,escort=true,boss=true,retake=true,group=true,chest=true}

local function bind()
    if S.bound then return true end
    if not N.Widget('H','Frames') then return false end
    for _,part in ipairs(PARTS) do
        local widget=_G['AFHFEvent'..part]
        if not widget then return false end
        S.widgets[part]=widget
    end
    local w=S.widgets
    K.Image(w.Panel,0,PREFIX..'panel.dds')
    K.Image(w.BarBack,0,PREFIX..'bar-back.dds')
    K.Image(w.BarFill,0,PREFIX..'bar-fill.dds')
    K.Text(w.Title,'Gold');K.Text(w.Stage,'Ice');K.Text(w.Objective,'Mist');K.Text(w.Count,'Ice');K.Text(w.Timer,'Mist')
    K.Text(w.Group,'#e8a08e')
    -- Start hidden whatever the layout flags left, as UnitFrames does with its pieces.
    for _,widget in pairs(w) do K.Hide(widget) end
    S.shown=false
    S.bound=true
    return true
end

-- Where the panel sits: top right below the minimap, moved by the player's offsets, always fully on screen.
local function origin()
    local sw,sh=N.Screen()
    local x=sw-WIDTH-16+(tonumber(M.db.offsetX) or 0)
    local y=260+(tonumber(M.db.offsetY) or 0)
    return math.max(0,math.min(sw-WIDTH,x)),math.max(0,math.min(sh-HEIGHT,y))
end

local function hide()
    if not S.shown then return end
    for _,widget in pairs(S.widgets) do K.Hide(widget) end
    S.shown=false
end

local function clock(seconds)
    seconds=math.max(0,math.floor(seconds or 0))
    return string.format('%d:%02d',math.floor(seconds/60),seconds%60)
end

function M:Refresh()
    if not M.db.enabled or not AFCore.Event then hide();return end
    -- Bind once (a few global lookups until the layout exists; binding hides the pieces), then cheap: one cached read
    -- (no request, no native call) while no event runs, and nothing else happens.
    if not S.bound then bind() end
    local active,chain,stage,objective,count,progress,seconds,medal,icon,group,shield,outcome=AFCore.Event()
    if not active then hide();return end
    if not S.bound then return end
    if not AF:Call('Lifecycle','IsReady') or N.PlayerIdentity()~=AF:Call('Lifecycle','Identity') then hide();return end
    local canvas=N.Widget('H','Frames')
    -- The canvas is shared and passive; UnitFrames hides it only while loading or when switched off.
    if canvas and canvas:IsVisible()~=1 then canvas:Show() end
    local w=S.widgets
    local x,y=origin()
    K.Place(w.Panel,x,y,WIDTH,HEIGHT)
    K.Place(w.Icon,x+10,y+10,30,30)
    K.Place(w.Medal,x+WIDTH-34,y+8,26,26)
    K.Place(w.Title,x+48,y+8,WIDTH-90,16)
    K.Place(w.Stage,x+48,y+26,WIDTH-90,14)
    K.Place(w.Group,x+48,y+42,WIDTH-60,12)
    K.Place(w.Objective,x+12,y+56,WIDTH-24,14)
    K.Place(w.BarBack,x+12,y+72,WIDTH-24,10)
    local fill=math.max(0,math.min(1,tonumber(progress) or 0))
    K.Place(w.BarFill,x+12,y+72,math.max(1,math.floor((WIDTH-24)*fill+.5)),10)
    K.Place(w.Count,x+12,y+84,(WIDTH-24)/2,12)
    K.Place(w.Timer,x+WIDTH/2,y+84,(WIDTH-24)/2,12)
    K.Image(w.Icon,0,PREFIX..'icon-'..(ICONS[icon] and icon or 'kill')..'.dds')
    K.SetText(w.Title,chain or '')
    K.SetText(w.Stage,stage or '')
    K.SetText(w.Group,shield and 'Shielded - defeat the reinforcements' or group and '[Group Event]' or '')
    K.SetText(w.Objective,objective or '')
    K.SetText(w.Count,count or '')
    K.SetText(w.Timer,(outcome and outcome~='') and '' or clock(seconds)..' left')
    for _,part in ipairs({'Panel','BarBack','Icon','Title','Stage','Objective','Count','Timer'}) do K.Show(w[part]) end
    if fill>0 then K.Show(w.BarFill) else K.Hide(w.BarFill) end
    if (group or shield) then K.Show(w.Group) else K.Hide(w.Group) end
    if MEDALS[medal] then K.Image(w.Medal,0,PREFIX..MEDALS[medal]);K.Show(w.Medal) else K.Hide(w.Medal) end
    S.shown=true
end

-- Five times a second; the data itself changes at most once a second (event_assistant.h).
function M:OnEnable()
    if S.timer then AF.Events:Cancel(S.timer) end
    S.timer=M:Every(4,function() M:Refresh() end)
end
function M:OnDisable()
    if S.timer then AF.Events:Cancel(S.timer);S.timer=nil end
    hide()
end
function M:OnSetting() M:Refresh() end

