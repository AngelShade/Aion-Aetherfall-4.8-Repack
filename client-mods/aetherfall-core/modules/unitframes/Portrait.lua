-- Live 3D portraits inside a painted border, left of the bars.
-- Every portrait is a canvas host (a passive container) whose native Model child Core creates: the player's
-- own body (source -1, core 1.3.23; also the player's own party row), the selected target's object ID, or a
-- party bot's cached look. Behind
-- the model sits the portrait background, an image of the backdrop colour with its own opacity; the painted
-- border is drawn over both.
-- Player fallback: when the canvas view cannot be built, the stock player frame's own 3dview ("portrait" in
-- basic_status_dialog, shipped 1x1 and hidden by this skin) is placed and shown instead. That view lies
-- under the canvas, so it has no background and the frame backdrop starts beside it (frame.body).
-- The target view uses the selected native creature's object ID, so NPCs, players and bots all work,
-- including duplicate NPC names and bots without growth records. Party bots keep their cached look.
-- Known bot gear quality selects the border; other targets use the existing neutral player border.
local K=AF.Skin
local D=AFCore and AFCore.DIALOG or {}
local ART='Textures/UI/AetherfallUnitFrames/border-'
local QUALITY={Poor='poor',Ordinary='ordinary',Good='good',Great='great',Excellent='excellent'}
-- The borders share one transparent opening: texels 40..215 of 256.
local OPEN,SPAN=40/256,176/256
local VIEW,HOST=0x2000,0x2008 -- stock player UI3DView; supported addon container owning a native Model
local RETRY=20    -- ticks between attempts while a model or a party bot's look is unavailable
local SELF=-1     -- Core's source for the player's own body
local FALLBACK=3  -- failed canvas attempts before the player uses the stock view
-- Core's target_portrait::Result codes, for the frame report only.
local REASON={'no selection','no entity','no components','no creature','other selection','no model',
    'unreadable look','mesh not built','invalid id','no portrait host','wrong host type','invalid native view',
    'no native owner','portrait XML rejected','native view creation failed','no render slot for this host',
    'no portrait camera for this creature'} -- 17: the game frames only player bodies; the bars keep the room
local canvas
-- The plugin dialog that holds the frame pool; its slot is found once and checked again before each placing.
local function pool()
    if canvas and (AFCore.Dialog(canvas))=='AFHFrames' then return canvas end
    canvas=nil
    for id=0x20e,0x221 do
        if (AFCore.Dialog(id))=='AFHFrames' then canvas=id;return id end
    end
end
local function path(frame) return (frame.group=='party' and 'AFHFParty/' or '')..frame.prefix..'Portrait' end
-- The player's stock view is in use (the canvas view could not be built this session).
local function stock(frame) return frame.unit=='player' and frame.portraitStock==true end
local function hideArt(frame) K.Hide(frame.widgets.PortraitBorder);K.Hide(frame.widgets.PortraitBG) end
local function release(frame)
    local held=frame.portraitHeld
    if not held then return end
    frame.portraitHeld,frame.portraitKey=nil,nil
    AFCore.Pin(held[1],nil,nil,nil,nil,nil,nil,held[2])
    AFCore.WidgetShown(held[1],held[2],false)
    -- Canvas bodies are ours to drop; the stock view keeps the player's.
    if held[3] then
        frame.portraitReady=nil
        if AFCore.Portrait then AFCore.Portrait(held[1],held[2]) end
    end
end
-- Target identity is the object ID, never the display name. Party portraits retain their existing source.
local function wanted(frame)
    local v=frame.values
    if frame.group=='targettarget' then return end
    if frame.settings.showPortrait~=1 or frame.portraitRefused or not v then return end
    if frame.group=='player' then return not frame.portraitStock and SELF or nil end
    if frame.group=='target' then return v.id end
    -- The player's own party row shows the player's own body, as the player frame does.
    if frame.group=='party' and v.name==AF.Native.Call('UnitName','player') then return SELF end
    if frame.group=='party' and QUALITY[v.rarity] then return v.name end
end
-- Build the body before the frame makes room for it; the room follows on the next refresh.
local function prepare(frame)
    local name=wanted(frame)
    if not name then frame.portraitReady,frame.portraitRetry=nil,nil;return end
    if frame.portraitReady==name then return end
    frame.portraitReady=nil
    local tick=AF:Call('Lifecycle','Ticks') or 0
    if frame.portraitRetry and frame.portraitRetry[1]==name and tick<frame.portraitRetry[2] then return end
    local dialog=AFCore.Portrait and pool()
    local ok,why,kind=false,'no frame canvas'
    if dialog then ok,why,kind=AFCore.Portrait(dialog,path(frame),name) end
    -- The frame report (afui-frames*.cfg) shows the last outcome; core 1.3.21 says why a target failed,
    -- 1.3.22 also why the view itself was not usable.
    frame.portraitTries=(frame.portraitTries or 0)+1
    frame.portraitStatus=ok and 'built' or 'not built: '..tostring(REASON[why] or why or 'unavailable')
        ..(kind and string.format(' 0x%x',kind) or '')
    if ok then frame.portraitReady,frame.portraitRetry,frame.portraitFailures=name,nil,nil
    else
        frame.portraitRetry={name,tick+RETRY}
        frame.portraitFailures=(frame.portraitFailures or 0)+1
        -- The player always has the stock view to fall back on.
        if frame.unit=='player' and frame.portraitFailures>=FALLBACK then
            frame.portraitStock,frame.portraitRetry,frame.nativeDirty=true,nil,true
            frame.portraitStatus=frame.portraitStatus..'; stock view'
        end
    end
end
local function update(frame)
    local object,p=frame.widgets.PortraitBorder,frame.portrait
    if not AFCore.Pin or not AFCore.WidgetShown then hideArt(frame);release(frame);return end
    local own=stock(frame)
    local source=not own and wanted(frame)
    if not own and not (p and source and frame.portraitReady==source) then
        hideArt(frame);release(frame);prepare(frame);return
    end
    if not p then hideArt(frame);release(frame);return end
    local dialog,widget=D.basic_status,'portrait'
    if not own then dialog,widget=pool(),path(frame) end
    -- Plugin units to native pixels, relative to the owning dialog's corner: the stock player frame for the
    -- stock view; the full-screen canvas for the rest, where a party row adds its container's screen corner.
    local s,rect=AF.Native.Scale(),frame.rect
    local ox,oy=own and -rect[1]*s or 0,own and -rect[2]*s or 0
    if frame.origin then ox,oy=frame.origin[1],frame.origin[2] end
    local x,y,size=ox+(p[1]+p[3]*OPEN)*s,oy+(p[2]+p[3]*OPEN)*s,p[3]*SPAN*s
    local key=tostring(dialog)..':'..math.floor(x+.5)..':'..math.floor(y+.5)..':'..math.floor(size+.5)
    if frame.portraitKey~=key then
        if frame.portraitHeld and frame.portraitHeld[1]~=dialog then release(frame) end
        local held=dialog and AFCore.Pin(dialog,x,y,0,false,size,size,widget,own and VIEW or HOST)
            and AFCore.WidgetShown(dialog,widget,true)
        if held then frame.portraitKey,frame.portraitHeld,frame.portraitStatus=key,{dialog,widget,not own},'shown'
        else
            frame.portraitHeld=frame.portraitHeld or (dialog and {dialog,widget,not own}) or nil
            release(frame);frame.portraitReady=nil;frame.nativeDirty=true
            if own then
                -- A client without the stock view: give the room back to the bars for good.
                frame.portraitRefused=true
            elseif frame.unit=='player' then
                -- The canvas view could not be placed: the stock view takes over on the next refresh.
                frame.portraitStock=true
                frame.portraitStatus='view not placed; stock view'
            else
                -- The canvas view ships with this plugin: try again later instead of for the whole session.
                frame.portraitStatus='view not placed'
                frame.portraitRetry={source,(AF:Call('Lifecycle','Ticks') or 0)+RETRY}
            end
            hideArt(frame);return
        end
    end
    -- Background behind a canvas model, in the border's opening. The stock view lies under the canvas.
    local back=frame.widgets.PortraitBG
    local opacity=(frame.settings.portraitOpacity or 0)/100
    if own or opacity<=0 then K.Hide(back)
    else
        K.Place(back,p[1]+p[3]*OPEN,p[2]+p[3]*OPEN,p[3]*SPAN,p[3]*SPAN)
        K.ImageColor(back,frame.appearance.backdrop,opacity);K.Show(back)
    end
    K.Place(object,p[1],p[2],p[3],p[3])
    K.Image(object,0,ART..(frame.unit=='player' and 'player' or QUALITY[frame.values.rarity] or 'player')..'.dds');K.Show(object)
end
-- The layout declares each view hidden; say so once more when the pool is bound, as the pool does for its images.
local function construct(frame)
    if frame.group=='targettarget' or not AFCore.WidgetShown then return end
    local dialog=pool()
    if dialog then AFCore.WidgetShown(dialog,path(frame),false) end
end
local function disable(frame) hideArt(frame);release(frame);frame.portraitReady,frame.portraitRetry=nil,nil end
-- Dirty input: a portrait turns ready, stock, refused or due for a retry without any unit value changing.
local function state(frame)
    if frame.group=='targettarget' then return '' end
    local retry,tick=frame.portraitRetry,AF:Call('Lifecycle','Ticks') or 0
    return table.concat({tostring(frame.portraitReady),tostring(frame.portraitStock),tostring(frame.portraitRefused),
        retry and (tick>=retry[2] and 'due' or 'wait') or '-',frame.portraitHeld and tostring(pool()) or '-'},'|')
end
AF:Call('UnitFrames','RegisterElement','Portrait',{Construct=construct,Update=update,Disable=disable,Key=state})
