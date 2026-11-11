local K=AF.Skin
local RARITY={Poor='#cad1d9',Ordinary='#80d48a',Good='#70b8ff',Great='#c28fff',Excellent='#ffbd59'}
local TIER={Poor=1,Ordinary=2,Good=3,Great=4,Excellent=5}
local STAR,STARS,STARGAP=9,5,1
local ON,OFF='Textures/UI/AetherfallUnitFrames/star-on.dds','Textures/UI/AetherfallUnitFrames/star-off.dds'
local function text(frame,name,value,x,width,color)
    local object,r=frame.widgets[name],frame.bars.header
    K.Place(object,x,r[2],math.max(1,width),r[4]);K.Font(object,frame.appearance.font)
    K.Text(object,color or frame.appearance.text);K.SetText(object,value)
    if value~='' then K.Show(object) else K.Hide(object) end
end
-- Rank as five star images: the star glyphs are missing from the game fonts.
local function stars(frame,tier,left)
    local r=frame.bars.header
    local y=r[2]+(r[4]-STAR)/2
    for i=1,STARS do
        local object=frame.widgets['Star'..i]
        if tier then
            K.Place(object,left+(i-1)*(STAR+STARGAP),y,STAR,STAR)
            K.Image(object,0,i<=tier and ON or OFF);K.Show(object)
        else K.Hide(object) end
    end
end
local function update(frame)
    local v,s,r=frame.values,frame.settings,frame.bars.header
    local level=s.showLevel==1 and AF.Native.Number(v.level) and AF.Tags:Format('[level]',v) or ''
    local bot=v.rarity and v.state=='' and s.showBot==1
    local tier=bot and s.showRank==1 and TIER[v.rank] or nil
    -- "Equip: Good" in the gear colour, the rank stars right after it, level on the far right.
    local right=r[1]+r[3]-(frame.reserve or 32)
    local row=tier and STARS*(STAR+STARGAP)-STARGAP or 0
    local label=(bot and s.showRarity==1) and AF.Tags:Format('[bot:equip]',v) or ''
    local textWidth=math.max(1,right-r[1]-(tier and row+4 or 0))
    text(frame,'Level',level,r[1]+r[3]-28,28)
    text(frame,'Identity',label,r[1],textWidth,RARITY[v.rarity])
    local used=label~='' and math.min(textWidth,(K.TextWidth(frame.widgets.Identity,label) or textWidth)+4) or 0
    stars(frame,tier,r[1]+used)
    local dead=s.stateColors=='custom' and s.deadColor or frame.appearance.dead
    local offline=s.stateColors=='custom' and s.offlineColor or frame.appearance.offline
    text(frame,'State',AF.Tags:Format('[state]',v),r[1],math.max(1,right-r[1]),v.unavailable and offline or v.dead and dead or nil)
end
AF:Call('UnitFrames','RegisterElement','Indicators',{Update=update})
