local K=AF.Skin
local RARITY={Poor='#cad1d9',Ordinary='#80d48a',Good='#70b8ff',Great='#c28fff',Excellent='#ffbd59'}
local TIER={Poor=1,Ordinary=2,Good=3,Great=4,Excellent=5}
local STAR,STARS,STARGAP=9,5,1
local ON,OFF='Textures/UI/AetherfallUnitFrames/star-on.dds','Textures/UI/AetherfallUnitFrames/star-off.dds'
-- Painted gear-quality crests (layout/identity_crests.py): 64x32 art, drawn twice as wide as tall.
local CREST={Poor='poor',Ordinary='ordinary',Good='good',Great='great',Excellent='excellent'}
local CRESTMAX,CRESTGAP=16,3
-- Header layout (each unit's Header tab): every item keeps its automatic place and moves by its own
-- offset in interface pixels; all offsets 0 is the automatic layout. r is the header rectangle.
local function moved(r,dy) return dy==0 and r or {r[1],r[2]+dy,r[3],r[4]} end
local function crest(frame,r,rarity,x,limit)
    local object=frame.widgets.Crest
    local file=rarity and CREST[rarity]
    if not object then return 0 end
    if not file or limit<11 then K.Hide(object);return 0 end
    local height=math.min(r[4],CRESTMAX,(limit-CRESTGAP)/2)
    K.Place(object,x,r[2]+(r[4]-height)/2,height*2,height)
    K.Image(object,0,'Textures/UI/AetherfallUnitFrames/crest-'..file..'.dds');K.Show(object)
    return height*2+CRESTGAP
end
-- The game's own class emblem (Textures/ui/EMBLEM, the path native 0x8bb600 builds) for characters.
local ICONMAX=16
local function classIcon(frame,r,x,limit)
    local object,v=frame.widgets.ClassIcon,frame.values
    if not object then return 0 end
    local info=frame.settings.showClass==1 and v.classId and AF:Call('UnitFrames','ClassInfo',v.classId)
    if not info or limit<4 then K.Hide(object);return 0 end
    local size=math.min(r[4],ICONMAX,limit)
    K.Place(object,x,r[2]+(r[4]-size)/2,size,size)
    K.Image(object,0,'Textures/UI/Emblem/icon_emblem_'..info[2]);K.Show(object)
    return math.min(limit,size+CRESTGAP)
end
local function text(frame,r,name,value,x,width,color)
    local object=frame.widgets[name]
    local px=frame.appearance.fontPx or 0;local need=px>13 and px+3 or 0
    local h=math.max(r[4],need)
    K.Place(object,x,r[2]+(r[4]-h)/2,math.max(1,width),h);K.Font(object,frame.appearance.font)
    K.Text(object,color or frame.appearance.text);K.SetText(object,value)
    if value~='' and width>0 then K.Show(object) else K.Hide(object) end
end
-- Rank as five star images: the star glyphs are missing from the game fonts.
local function stars(frame,r,tier,left,size)
    local y=r[2]+(r[4]-size)/2
    for i=1,STARS do
        local object=frame.widgets['Star'..i]
        if tier and size>=3 then
            K.Place(object,left+(i-1)*(size+STARGAP),y,size,size)
            K.Image(object,0,i<=tier and ON or OFF);K.Show(object)
        else K.Hide(object) end
    end
end
-- The Codex loot chest (loot-codex/layout.py: containerN/af_codex_open) is a live native button at the
-- start of the stock name area. Moved by its offset through a pin from its natural spot (measured while
-- unpinned, relative to the dialog); offset 0 releases the pin. The header starts after it while it
-- covers the header's start.
local function chestWidget(frame)
    local path=frame.group=='target' and frame.native.rects and frame.native.rects[frame.rectIndex or 1]
    return path and path..'/af_codex_open' or nil
end
local function releaseChest(frame)
    if frame.chestHeld and AFCore.Pin then AFCore.Pin(frame.id,nil,nil,nil,nil,nil,nil,frame.chestHeld) end
    frame.chestHeld=nil
end
local function placeChest(frame,widget)
    if frame.chestHeld and frame.chestHeld~=widget then releaseChest(frame) end
    local N,s=AF.Native,frame.settings
    local dx,dy=N.Number(s.chestX) or 0,N.Number(s.chestY) or 0
    if dx==0 and dy==0 then releaseChest(frame);return end
    local d=N.Dialog(frame.id)
    local x,y=AFCore.Rect(frame.id,widget,'any')
    if not d or not N.Number(x) or not N.Number(y) then return end
    frame.chestBase=frame.chestBase or {}
    if not frame.chestHeld then frame.chestBase[widget]={x-d.x,y-d.y} end
    local base,scale=frame.chestBase[widget],N.Scale()
    if not base then return end
    local tx,ty=base[1]+dx*scale,base[2]+dy*scale
    -- Also re-pins after something released every pin (the live spot is back at the natural one).
    if math.abs(x-d.x-tx)>1 or math.abs(y-d.y-ty)>1 then
        if AFCore.Pin(frame.id,tx,ty,0,false,0,0,widget,'any') then frame.chestHeld=widget end
    end
end
local function chestEdge(frame,r)
    local widget=chestWidget(frame)
    if not widget or not AFCore.Rect then releaseChest(frame);return r[1] end
    if AFCore.Pin then placeChest(frame,widget) end
    local x,y,w,h,shown=AFCore.Rect(frame.id,widget,'any')
    local N,s=AF.Native,AF.Native.Scale()
    if not shown or not N.Number(x) or not N.Number(w) or w<=0 then return r[1] end
    x,y,w,h=x/s,y/s,w/s,h/s
    if y>r[2]+r[4] or y+h<r[2] or x+w<=r[1] or x>r[1]+r[3]*.5 then return r[1] end -- not at this header's start
    return math.min(r[1]+r[3]*.5,x+w+2)
end
local function offset(s,key) return AF.Native.Number(s[key]) or 0 end
local function update(frame)
    local v,s=frame.values,frame.settings
    local h=frame.bars.header;local x0=chestEdge(frame,h)
    local r={x0,h[2],math.max(1,h[1]+h[3]-x0),h[4]}
    local level=s.showLevel==1 and AF.Native.Number(v.level) and AF.Tags:Format('[level]',v) or ''
    -- Only target and party have bot-label settings in the UnitFrame schema.
    local bot=(frame.group=='target' or frame.group=='party') and v.rarity and v.state=='' and s.showBot==1
    local tier=bot and s.showRank==1 and TIER[v.rank] or nil
    -- "Equip: Good" in the gear colour, the rank stars right after it, level on the far right.
    local right=math.max(r[1],r[1]+r[3]-(frame.reserve or 32))
    local label=(bot and s.showRarity==1) and AF.Tags:Format('[bot:equip]',v) or ''
    local iconX,iconR=offset(s,'classIconX'),moved(r,offset(s,'classIconY'))
    local labelled=frame.group=='target' or frame.group=='party' -- the units that own gear offsets
    local gearX,gearR=labelled and offset(s,'gearX') or 0,moved(r,labelled and offset(s,'gearY') or 0)
    -- The class emblem leads the header; the crest leads the gear label and follows the Show gear toggle.
    local icon=classIcon(frame,iconR,r[1]+iconX,right-r[1])
    local available=math.max(0,right-r[1]-icon)
    local gear=label~='' and v.rarity or nil
    -- Keep all five rank marks together; compact gear to its quality crest before
    -- clipping text. At impossible widths class/level/flight take precedence.
    local gearMin=gear and available>=30 and 11 or 0
    local size=tier and math.min(STAR,math.floor((available-gearMin-(STARS-1)*STARGAP)/STARS)) or 0
    if size<3 then size=0 end
    local row=size>0 and STARS*size+(STARS-1)*STARGAP or 0
    local crestWidth=math.min(CRESTMAX*2+CRESTGAP,available-row)
    local lead=icon+crest(frame,gearR,gear,r[1]+icon+gearX,crestWidth)
    local textWidth=math.max(0,right-r[1]-lead-row)
    if textWidth<32 then label='' end
    text(frame,moved(r,offset(s,'levelY')),'Level',level,r[1]+r[3]-math.min(28,r[3])+offset(s,'levelX'),math.min(28,r[3]))
    text(frame,gearR,'Identity',label,r[1]+lead+gearX,textWidth,RARITY[v.rarity])
    local used=label~='' and math.min(textWidth,(K.TextWidth(frame.widgets.Identity,label) or textWidth)+4) or 0
    stars(frame,gearR,tier,r[1]+lead+used+gearX,size)
    local dead=s.stateColors=='custom' and s.deadColor or frame.appearance.dead
    local offline=s.stateColors=='custom' and s.offlineColor or frame.appearance.offline
    text(frame,moved(r,offset(s,'stateY')),'State',AF.Tags:Format('[state]',v),r[1]+icon+offset(s,'stateX'),
        math.max(0,right-r[1]-icon),v.unavailable and offline or v.dead and dead or nil)
end
-- One bounded snapshot 2 s after frames first draw (Mods/AetherfallUI/afui-frames.cfg): real canvas and part rects.
local N=AF.Native
local function localShown(widget) return widget and type(widget.flags)=='number' and math.floor(widget.flags)%2==1 end
local function dump(file)
    local lines={'AFUI frames '..tostring((N:Resource('lazy') or {}).version)..' scale='..tostring(N.Scale())}
    local canvasId
    for id=0x20e,0x221 do
        local name,x,y,w,h,shown,class,instance,flags=AFCore.Dialog(id)
        if name then lines[#lines+1]=string.format('slot 0x%x %s x=%s y=%s w=%s h=%s shown=%s flags=%s',id,name,tostring(x),tostring(y),tostring(w),tostring(h),tostring(shown),tostring(flags)) end
        if name=='AFHFrames' then canvasId=id end
    end
    local canvas=N.Widget('H','Frames')
    if canvas then lines[#lines+1]='canvas lua visible='..tostring(canvas:IsVisible())..' rect='..table.concat({tostring((canvas:GetRect()))},',') end
    for _,frame in ipairs(AF:Call('UnitFrames','Frames')) do
        lines[#lines+1]=string.format('%s owned=%s rect=%s values=%s',frame.unit,tostring(frame.owned),
            frame.rect and table.concat(frame.rect,',') or '-',frame.values and tostring(frame.values.name) or '-')
        if frame.unit=='target' or frame.unit=='targettarget' then
            local d=N.Dialog(frame.id)
            if d then lines[#lines+1]=string.format('  native dialog=%s,%s,%s,%s shown=%s container=%s',tostring(d.x),tostring(d.y),tostring(d.w),tostring(d.h),tostring(d.shown),tostring(frame.rectIndex)) end
        end
        if frame.values then lines[#lines+1]='  dp='..tostring(frame.values.dp)..'/'..tostring(frame.values.dpmax)..' identity='..tostring(frame.values.rankStars) end
        if frame.group~='targettarget' and frame.settings then
            lines[#lines+1]=string.format('  portrait setting=%s ready=%s status=%s tries=%s shown=%s refused=%s',
                tostring(frame.settings.showPortrait),tostring(frame.portraitReady),tostring(frame.portraitStatus),
                tostring(frame.portraitTries),tostring(frame.portraitHeld~=nil),tostring(frame.portraitRefused==true))
        end
        if frame.group=='party' and not frame.owned then lines[#lines+1]='  title='..tostring(AFCore.Text(frame.id,frame.native.title))..' name='..tostring(N.Call('UnitName',frame.unit)) end
        if frame.owned then
            local hidden={}
            for _,entry in ipairs(frame.capture or {}) do
                if not entry.backdrop then hidden[#hidden+1]=entry.path..'='..tostring(localShown(AFCore.Widget(frame.id,entry.path))) end
            end
            lines[#lines+1]='  native shown: '..table.concat(hidden,' ')
        end
        if frame.owned and canvasId then
            for _,part in ipairs({'Backdrop','HealthBG','HealthFill','HealthTextLeft','DPFill','DPTextCentre','Identity','Star1','Portrait'}) do
                local path=(frame.group=='party' and 'AFHFParty/' or '')..frame.prefix..part
                local node=AFCore.Widget(canvasId,path)
                local r=node and node.rect or {}
                lines[#lines+1]=string.format('  %s found=%s type=%s shown=%s flags=%s rect=%s,%s,%s,%s',part,tostring(node~=nil),tostring(node and node.type),tostring(node and node.shown),tostring(node and node.flags),tostring(r[1]),tostring(r[2]),tostring(r[3]),tostring(r[4]))
            end
        end
    end
    AF.DB:Write(file or 'afui-frames.cfg',table.concat(lines,'\n')..'\n')
end
AF:Call('UnitFrames','RegisterDump',dump)
-- Dirty input: the live Codex chest (appearance, geometry, our pin). Its native spot can change or be
-- reset (pins released, rebuilt row) without any unit value changing; a changed snapshot repaints the header.
local function snapshot(frame)
    local widget=chestWidget(frame)
    if not widget or not AFCore.Rect then return '' end
    local x,y,w,h,shown=AFCore.Rect(frame.id,widget,'any')
    return table.concat({widget,tostring(frame.chestHeld),tostring(shown),tostring(x),tostring(y),tostring(w),tostring(h)},'|')
end
AF:Call('UnitFrames','RegisterElement','Indicators',{Update=update,Disable=releaseChest,Key=snapshot})
